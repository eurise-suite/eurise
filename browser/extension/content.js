// Eurise: estensione comune del Browser (nucleo condiviso, vedi docs/PIATTAFORME.md).
// Sul telefono parla con l'app Eurise Browser attraverso GeckoView ("browser"); sul computer, con Firefox, parlerà
// con l'app Eurise del computer (native messaging). Legge la pagina solo quando l'app lo chiede, e non manda nulla
// altrove.
(function () {
  "use strict";
  let port;
  try {
    port = browser.runtime.connectNative("browser");
  } catch (e) {
    return;
  }

  // Il testo principale: l'articolo, senza menu, piè di pagina, colonne laterali e moduli (vista Lettura, Ascolta).
  function mainText() {
    const main = document.querySelector("article") || document.querySelector("main") || document.querySelector("[role=main]") || document.body;
    if (!main) return "";
    const copy = main.cloneNode(true);
    copy.querySelectorAll("nav, header, footer, aside, form, script, style, noscript, button, [role=navigation], [aria-hidden=true]")
      .forEach(function (n) { n.remove(); });
    const blocks = [];
    copy.querySelectorAll("h1, h2, h3, p, li, blockquote, pre").forEach(function (n) {
      const t = String(n.textContent || "").replace(/\s+/g, " ").trim();
      if (t.length > 1) blocks.push(/^H[123]$/.test(n.tagName) ? "# " + t : t);
    });
    const text = blocks.length > 2 ? blocks.join("\n\n") : String(copy.textContent || "").replace(/\s+\n/g, "\n");
    return text.replace(/\n{3,}/g, "\n\n").slice(0, 60000);
  }

  // Pagine scure: per i siti senza un loro tema scuro, colori invertiti (immagini e video restano naturali).
  const DARK_ID = "eurise-dark";
  function setDark(on) {
    const old = document.getElementById(DARK_ID);
    if (!on) { if (old) old.remove(); return; }
    if (old) return;
    const bg = getComputedStyle(document.body || document.documentElement).backgroundColor;
    const m = /rgba?\((\d+), (\d+), (\d+)/.exec(bg || "");
    const alreadyDark = m && (Number(m[1]) + Number(m[2]) + Number(m[3])) / 3 < 90;
    if (alreadyDark) return;
    const style = document.createElement("style");
    style.id = DARK_ID;
    style.textContent = "html{filter:invert(0.9) hue-rotate(180deg);background:#fff}" +
      "img,video,picture,canvas,svg image,iframe,[style*='background-image']{filter:invert(1) hue-rotate(180deg)}";
    (document.head || document.documentElement).appendChild(style);
  }

  function pageInfo() {
    const meta = document.querySelector('meta[name="description"], meta[property="og:description"]');
    return {
      type: "page",
      url: location.href,
      title: document.title || "",
      selection: String(window.getSelection() || "").trim().slice(0, 4000),
      description: meta ? String(meta.content || "").slice(0, 500) : "",
      text: mainText(),
    };
  }

  port.onMessage.addListener(function (message) {
    if (message && message.type === "page") port.postMessage(pageInfo());
    if (message && message.type === "dark") setDark(!!message.on);
  });
})();
