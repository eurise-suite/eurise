#pragma once

// Eurise Mappe: cielo e colline in fondo alla mappa in prospettiva (tavola MapCieloColline).
//
// In guida la mappa si inclina di più (ScreenBase::SetEuriseSky) e il suo bordo lontano scende sotto il bordo alto dello
// schermo; sopra resta il cielo. L'immagine (cielo dal meteo, colline della stagione) la disegna l'app in Java
// (EuriseSky.java, stessi colori di EuriseLandscape) e arriva qui come pixel RGBA: il motore la stende sopra il bordo
// della mappa con il programma ScreenQuad che c'è già, senza shader nuovi.
//
// Come si stende: la riga kHorizonV dell'immagine cade sul bordo lontano della mappa; sopra va fino al bordo alto
// dello schermo, sotto copre la striscia lontana e i due cunei ai lati, dove la mappa (che si stringe in lontananza)
// non arriva al bordo dello schermo.

#include "drape/pointers.hpp"

#include <cstdint>
#include <vector>

class ScreenBase;

namespace dp
{
class GraphicsContext;
class Viewport;
}  // namespace dp

namespace gpu
{
class ProgramManager;
}  // namespace gpu

namespace df
{
/// Riga dell'immagine che cade sul bordo lontano della mappa (0 in alto, 1 in basso).
float constexpr kEuriseSkyHorizonV = 0.8f;

/// Nuova immagine del cielo (righe dall'alto, 4 byte RGBA per pixel, alfa non premoltiplicato); vuota = niente cielo.
/// Si chiama da qualsiasi thread: il disegno la prende al fotogramma dopo.
void EuriseSkySetImage(std::vector<uint8_t> && rgba, uint32_t width, uint32_t height);

/// Disegna cielo e colline sopra il bordo lontano della mappa, se c'è un'immagine e la mappa è in prospettiva con il
/// bordo dentro lo schermo. Solo dal thread del disegno (FrontendRenderer::RenderScene).
void EuriseSkyRender(ref_ptr<dp::GraphicsContext> context, ref_ptr<gpu::ProgramManager> mng, ScreenBase const & screen);

/// Scritte e segni della mappa solo sotto l'orizzonte: lontano, al bordo del piano, CoMaps ne disegna ancora qualcuno che
/// sporgerebbe nel cielo. Se il cielo c'è (fotogramma in corso) restringe il taglio dello schermo alla parte sotto le
/// colline e dice true; dopo il disegno delle scritte si rimette il taglio pieno con viewport.Apply(context).
bool EuriseSkyClipLabels(ref_ptr<dp::GraphicsContext> context, dp::Viewport const & viewport);

/// Il contesto grafico se ne va: texture e vertici si rifanno al prossimo disegno.
void EuriseSkyReset();
}  // namespace df
