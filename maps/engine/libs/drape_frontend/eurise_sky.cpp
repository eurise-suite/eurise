#include "drape_frontend/eurise_sky.hpp"

#include "drape_frontend/render_state_extension.hpp"

#include "shaders/program_manager.hpp"
#include "shaders/program_params.hpp"
#include "shaders/programs.hpp"

#include "drape/graphics_context.hpp"
#include "drape/hw_texture.hpp"
#include "drape/mesh_object.hpp"
#include "drape/render_state.hpp"

#include <atomic>
#include "drape/texture.hpp"
#include "drape/viewport.hpp"

#include "geometry/screenbase.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <mutex>
#include <utility>

namespace df
{
namespace
{
// Sotto il bordo lontano le colline coprono ancora un poco di mappa (frazione dell'altezza dello schermo): lì la mappa
// lontana è un groviglio di linee sottili, e il taglio netto della tavola cade dentro le colline.
double constexpr kHillsBelowEdge = 0.015;

/// Una texture semplice dai pixel dell'immagine: non è un atlante, non ha risorse da cercare.
class SkyTexture : public dp::Texture
{
public:
  ref_ptr<ResourceInfo> FindResource(Key const &, bool & newResource) override
  {
    newResource = false;
    return nullptr;
  }
};

/// L'immagine che arriva da Java (qualsiasi thread).
struct Pending
{
  std::mutex m_mutex;
  std::vector<uint8_t> m_rgba;
  uint32_t m_width = 0;
  uint32_t m_height = 0;
  uint64_t m_version = 0;
};

Pending & GetPending()
{
  static Pending pending;
  return pending;
}

/// Ciò che vive nel contesto grafico (solo il thread del disegno).
struct Gpu
{
  drape_ptr<SkyTexture> m_texture;
  drape_ptr<dp::MeshObject> m_mesh;
  uint64_t m_version = 0;
  std::vector<float> m_vertices;
  /// Dove finiscono le colline in questo fotogramma (pixel dall'alto) e l'altezza dello schermo; -1 = niente cielo.
  double m_clipY = -1.0;
  double m_clipH = 0.0;
};

Gpu & GetGpu()
{
  static Gpu gpu;
  return gpu;
}

size_t constexpr kVertexCount = 24;  // 8 triangoli: il cielo sopra il bordo, tre spicchi di colline sotto.
size_t constexpr kFloatsPerVertex = 4;  // posizione e coordinate della texture

/// Prende l'immagine nuova, se c'è; false se non c'è niente da disegnare.
bool SyncTexture(ref_ptr<dp::GraphicsContext> context)
{
  auto & gpu = GetGpu();
  auto & pending = GetPending();
  std::lock_guard lock(pending.m_mutex);
  if (pending.m_version != gpu.m_version)
  {
    gpu.m_version = pending.m_version;
    gpu.m_texture.reset();
    if (!pending.m_rgba.empty())
    {
      dp::Texture::Params p;
      p.m_allocator = dp::GetDefaultAllocator(context);
      p.m_format = dp::TextureFormat::RGBA8;
      p.m_width = pending.m_width;
      p.m_height = pending.m_height;
      gpu.m_texture = make_unique_dp<SkyTexture>();
      gpu.m_texture->Create(context, p, make_ref(pending.m_rgba.data()));
    }
  }
  return gpu.m_texture != nullptr;
}

void Push(std::vector<float> & out, double x, double y, double w, double h, double v, bool vulkan)
{
  out.push_back(static_cast<float>(2.0 * x / w - 1.0));
  float const ndcY = static_cast<float>(1.0 - 2.0 * y / h);
  // Come ScreenQuadRenderer: con Vulkan l'alto dello schermo è y = -1.
  out.push_back(vulkan ? -ndcY : ndcY);
  out.push_back(static_cast<float>(x / w));
  out.push_back(static_cast<float>(v));
}
}  // namespace

void EuriseSkySetImage(std::vector<uint8_t> && rgba, uint32_t width, uint32_t height)
{
  auto & pending = GetPending();
  std::lock_guard lock(pending.m_mutex);
  if (rgba.size() < static_cast<size_t>(width) * height * 4 || width == 0 || height == 0)
  {
    rgba.clear();
    width = height = 0;
  }
  pending.m_rgba = std::move(rgba);
  pending.m_width = width;
  pending.m_height = height;
  ++pending.m_version;
}

void EuriseSkyRender(ref_ptr<dp::GraphicsContext> context, ref_ptr<gpu::ProgramManager> mng, ScreenBase const & screen)
{
  GetGpu().m_clipY = -1.0;
  if (!screen.isPerspective() || !SyncTexture(context))
    return;

  // Il bordo lontano della mappa è la riga in alto del piano (y = 0 in pixel 2D); i suoi spigoli e quelli vicini, in 3D.
  m2::RectD const & plane = screen.PixelRect();
  m2::RectD const & view = screen.PixelRectIn3d();
  double const w = view.SizeX(), h = view.SizeY();
  if (w <= 1.0 || h <= 1.0)
    return;
  m2::PointD const farL = screen.PtoP3d(m2::PointD(plane.minX(), plane.minY()));
  m2::PointD const farR = screen.PtoP3d(m2::PointD(plane.maxX(), plane.minY()));
  m2::PointD const nearL = screen.PtoP3d(m2::PointD(plane.minX(), plane.maxY()));
  m2::PointD const nearR = screen.PtoP3d(m2::PointD(plane.maxX(), plane.maxY()));
  if (screen.IsReverseProjection3d(m2::PointD(plane.minX(), plane.minY())))
    return;

  double const edge = farL.y;
  if (!std::isfinite(edge) || edge < 1.0 || edge > h * 0.6)
    return;  // il bordo è sopra lo schermo (vista poco inclinata): niente cielo

  // Ai lati la mappa si stringe: sotto gli spigoli lontani restano due cunei vuoti, che le colline coprono.
  auto const wedge = [edge](m2::PointD const & far, m2::PointD const & near, double sideX)
  {
    if (std::fabs(near.x - far.x) < 1e-6)
      return edge;
    double const t = (sideX - far.x) / (near.x - far.x);
    if (t <= 0.0)
      return edge;  // lo spigolo è già fuori dallo schermo
    return std::max(edge, far.y + std::min(t, 1.0) * (near.y - far.y));
  };
  double const margin = h * kHillsBelowEdge;
  double const xl = std::clamp(farL.x, 0.0, w), xr = std::clamp(farR.x, 0.0, w);
  double const bl = wedge(farL, nearL, 0.0) + margin;
  double const br = wedge(farR, nearR, w) + margin;
  double const low = edge + margin;
  double const bottom = std::max({bl, br, low});

  auto const v = [&](double y)
  {
    if (y <= edge)
      return kEuriseSkyHorizonV * y / edge;
    return kEuriseSkyHorizonV + (1.0 - kEuriseSkyHorizonV) * (y - edge) / (bottom - edge);
  };

  bool const vulkan = context->GetApiVersion() == dp::ApiVersion::Vulkan;
  std::vector<float> vx;
  vx.reserve(kVertexCount * kFloatsPerVertex);
  auto const tri = [&](double x1, double y1, double x2, double y2, double x3, double y3)
  {
    Push(vx, x1, y1, w, h, v(y1), vulkan);
    Push(vx, x2, y2, w, h, v(y2), vulkan);
    Push(vx, x3, y3, w, h, v(y3), vulkan);
  };
  // Il cielo, dal bordo alto dello schermo al bordo della mappa.
  tri(0, 0, w, 0, 0, edge);
  tri(w, 0, w, edge, 0, edge);
  // Le colline: sinistra (fino allo spigolo), centro, destra.
  tri(0, edge, xl, edge, 0, bl);
  tri(xl, edge, xl, low, 0, bl);
  tri(xl, edge, xr, edge, xl, low);
  tri(xr, edge, xr, low, xl, low);
  tri(xr, edge, w, edge, xr, low);
  tri(w, edge, w, br, xr, low);

  auto & gpu = GetGpu();
  gpu.m_clipY = low;
  gpu.m_clipH = h;
  if (!gpu.m_mesh)
  {
    gpu.m_mesh = make_unique_dp<dp::MeshObject>(context, dp::MeshObject::DrawPrimitive::Triangles, "EuriseSky");
    gpu.m_vertices = vx;
    gpu.m_mesh->SetBuffer(0 /* bufferInd */, std::move(vx), sizeof(float) * kFloatsPerVertex);
    gpu.m_mesh->SetAttribute("a_pos", 0 /* bufferInd */, 0 /* offset */, 2 /* componentsCount */);
    gpu.m_mesh->SetAttribute("a_tcoord", 0 /* bufferInd */, sizeof(float) * 2 /* offset */, 2 /* componentsCount */);
  }
  else if (gpu.m_mesh->IsInitialized() && vx != gpu.m_vertices)
  {
    gpu.m_mesh->UpdateBuffer(context, 0 /* bufferInd */, vx);
    gpu.m_vertices = std::move(vx);
  }

  dp::RenderState state = CreateRenderState(gpu::Program::ScreenQuad, DepthLayer::GeometryLayer);
  state.SetDepthTestEnabled(false);
  state.SetBlending(dp::Blending(true));
  state.SetTexture("u_colorTex", make_ref(gpu.m_texture));

  gpu::ScreenQuadProgramParams params;
  params.m_opacity = 1.0f;
  params.m_invertV = 0.0f;

  auto program = mng->GetProgram(gpu::Program::ScreenQuad);
  gpu.m_mesh->Render(context, program, state, mng->GetParamsSetter(), params);
}

bool EuriseSkyClipLabels(ref_ptr<dp::GraphicsContext> context, dp::Viewport const & viewport)
{
  auto const & gpu = GetGpu();
  if (gpu.m_clipY < 0.0 || gpu.m_clipH <= 1.0)
    return false;
  double const scale = viewport.GetHeight() / gpu.m_clipH;
  auto const top = static_cast<uint32_t>(std::clamp(gpu.m_clipY * scale, 0.0, double(viewport.GetHeight())));
  uint32_t const height = viewport.GetHeight() - top;
  // OpenGL conta le righe dal basso, Vulkan dall'alto.
  if (context->GetApiVersion() == dp::ApiVersion::Vulkan)
    context->SetScissor(viewport.GetX0(), viewport.GetY0() + top, viewport.GetWidth(), height);
  else
    context->SetScissor(viewport.GetX0(), viewport.GetY0(), viewport.GetWidth(), height);
  return true;
}

void EuriseSkyReset()
{
  auto & gpu = GetGpu();
  gpu.m_mesh.reset();
  gpu.m_texture.reset();
  gpu.m_vertices.clear();
  gpu.m_version = 0;  // al contesto nuovo la texture si rifà dall'immagine che resta in Pending
}
namespace
{
std::atomic<bool> g_perspectiveWish{true};
}  // namespace

void EuriseSetPerspectiveWish(bool on)
{
  g_perspectiveWish = on;
}

bool EuriseTakePerspectiveWish()
{
  return g_perspectiveWish.exchange(true);
}
}  // namespace df
