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

  // L'icona del sito, solo nella pagina principale: l'indirizzo della migliore dichiarata dalla pagina (o /favicon.ico).
  // L'app la scarica dallo stesso sito, senza cookie, e la tiene sul telefono per schede e segnalibri.
  function siteIcon() {
    if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
    let best = null, bestScore = -1;
    document.querySelectorAll('link[rel~="icon"], link[rel="shortcut icon"], link[rel~="apple-touch-icon"]').forEach(function (l) {
      if (!l.href) return;
      const size = parseInt(String(l.getAttribute("sizes") || "").split(/[x ]/)[0], 10) || (/apple-touch/.test(l.rel) ? 180 : 16);
      const svg = /svg/.test(l.type || "") || /\.svg(\?|$)/.test(l.href);
      // Si preferisce un'icona tra 32 e 192 px; le svg non si leggono sul telefono.
      const score = svg ? -1 : (size >= 32 && size <= 192 ? 1000 - Math.abs(size - 64) : size);
      if (score > bestScore) { best = l.href; bestScore = score; }
    });
    port.postMessage({ type: "icon", host: location.hostname, url: best || (location.origin + "/favicon.ico") });
  }
  try { siteIcon(); } catch (e) { /* niente */ }

  port.onMessage.addListener(function (message) {
    if (message && message.type === "page") port.postMessage(pageInfo());
    if (message && message.type === "dark") setDark(!!message.on);
    // Immagini sì/no: lo sa lo sfondo dell'estensione, che le ferma prima che si scarichino.
    if (message && message.type === "images") {
      try { browser.runtime.sendMessage({ type: "images", on: !!message.on }); } catch (e) { /* niente */ }
    }
  });
})();
