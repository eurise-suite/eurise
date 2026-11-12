// Eurise: sfondo dell'estensione. Per ora una cosa sola: «Immagini sì/no» (Strumenti della pagina). Con le immagini
// spente le richieste di immagini si fermano prima di partire: le pagine pesano meno e si caricano prima.
// L'app lo dice alla pagina, la pagina lo dice qui; il valore vale per tutte le schede.
(function () {
  "use strict";
  let imagesOn = true;
  // Il valore resta tra un avvio e l'altro, così anche la prima pagina lo rispetta.
  browser.storage.local.get("imagesOn").then(function (v) { if (v && v.imagesOn === false) imagesOn = false; }, function () {});

  browser.runtime.onMessage.addListener(function (message) {
    if (message && message.type === "images") {
      imagesOn = !!message.on;
      browser.storage.local.set({ imagesOn: imagesOn }).catch(function () {});
    }
  });

  browser.webRequest.onBeforeRequest.addListener(
    function () { return imagesOn ? {} : { cancel: true }; },
    { urls: ["<all_urls>"], types: ["image", "imageset"] },
    ["blocking"]
  );
})();
