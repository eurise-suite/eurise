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

  function mainText() {
    const main = document.querySelector("article") || document.querySelector("main") || document.body;
    if (!main) return "";
    return String(main.innerText || "").replace(/[ \t]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").slice(0, 20000);
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
  });
})();
