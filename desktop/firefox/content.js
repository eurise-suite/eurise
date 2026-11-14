/*
 * Eurise per Firefox, nella pagina: le proposte di Eurise Password sui moduli di accesso (tavola PcFirefox).
 * Si accende nelle Impostazioni dell'estensione (spenta di base). La pagina viene solo guardata qui, sul computer:
 * al telefono parte il nome del sito, e solo quando si tocca «Chiedi al telefono». La password arriva dal telefono
 * dopo l'impronta, si scrive nel campo e non resta da nessuna parte.
 */
(function () {
  "use strict";
  if (window.top !== window) return;

  let settings = null;
  let box = null;
  let target = null;

  function visible(el) {
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== "hidden";
  }

  /** Il campo del nome utente che sta prima della password nello stesso modulo (o nella pagina). */
  function userFieldFor(pwd) {
    const scope = pwd.form || document;
    const inputs = Array.from(scope.querySelectorAll("input")).filter(visible);
    const i = inputs.indexOf(pwd);
    for (let j = i - 1; j >= 0; j--) {
      const t = (inputs[j].type || "text").toLowerCase();
      if (["text", "email", "tel", ""].includes(t)) return inputs[j];
    }
    return null;
  }

  function loginFields() {
    const pwd = Array.from(document.querySelectorAll('input[type="password"]')).filter(visible)[0];
    if (!pwd) return null;
    return { pwd, user: userFieldFor(pwd) };
  }

  function setValue(el, value) {
    if (!el) return;
    const proto = Object.getPrototypeOf(el);
    const setter = Object.getOwnPropertyDescriptor(proto, "value");
    if (setter && setter.set) setter.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function close() { if (box) { box.remove(); box = null; } }

  function show(anchor) {
    close();
    const f = loginFields();
    if (!f) return;
    target = f;
    const host = document.createElement("div");
    host.style.cssText = "position:absolute;z-index:2147483647;";
    const r = anchor.getBoundingClientRect();
    host.style.left = (window.scrollX + r.left) + "px";
    host.style.top = (window.scrollY + r.bottom + 6) + "px";
    const root = host.attachShadow({ mode: "closed" });
    const night = matchMedia("(prefers-color-scheme: dark)").matches;
    root.innerHTML = `
      <style>
        .b{font:13px "Plus Jakarta Sans",system-ui,sans-serif;width:${Math.max(280, Math.min(380, r.width))}px;border-radius:14px;
           background:${night ? "#1D2535" : "#FFFFFF"};color:${night ? "#E6E9F0" : "#1E1D1B"};
           box-shadow:0 10px 30px rgba(0,0,0,.22);padding:10px;display:flex;flex-direction:column;gap:6px}
        .h{display:flex;align-items:center;gap:8px;font-size:11.5px;font-weight:700;letter-spacing:.04em;color:${night ? "#A9B4C8" : "#56564F"}}
        .h span{flex:1}
        .x{border:0;background:none;color:inherit;cursor:pointer;font-size:15px;padding:0 4px}
        .o{display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:10px;background:${night ? "#2B3A5C" : "#EFECF1"};cursor:pointer;border:0;color:inherit;text-align:left;font:inherit}
        .o strong{flex:1;font-size:13.5px}
        .o em{font-style:normal;font-size:12px;font-weight:700;color:${night ? "#C8B3D4" : "#6B4A7A"}}
        .n{font-size:12px;line-height:1.4;color:${night ? "#A9B4C8" : "#3A3A35"}}
      </style>
      <div class="b" role="dialog" aria-label="Eurise Password">
        <div class="h"><svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" aria-hidden="true"><path d="M3.8 12 a4.2 4.2 0 1 0 8.4 0 a4.2 4.2 0 1 0 -8.4 0 M12.2 12 H21 M18 12 v3 M15.4 12 v2.2"/></svg><span>EURISE PASSWORD</span><button class="x" aria-label="Chiudi">×</button></div>
        <button class="o" type="button"><strong>Chiedi al telefono · ${location.hostname.replace(/^www\./, "")}</strong><em>Chiedi</em></button>
        <div class="n">Il telefono chiede l'impronta, poi compila qui. Al telefono va solo il nome del sito.</div>
      </div>`;
    root.querySelector(".x").addEventListener("click", close);
    const note = root.querySelector(".n");
    const btn = root.querySelector(".o");
    btn.addEventListener("click", async () => {
      btn.disabled = true;
      btn.querySelector("em").textContent = "…";
      note.textContent = "Guarda il telefono: tocca la notifica «Password per " + location.hostname + "» e approva.";
      const r = await browser.runtime.sendMessage({ op: "password" });
      if (r && r.ok) {
        if (target.user && r.username) setValue(target.user, r.username);
        setValue(target.pwd, r.password);
        note.textContent = "Compilato.";
        setTimeout(close, 900);
      } else {
        btn.disabled = false;
        btn.querySelector("em").textContent = "Riprova";
        note.textContent = (r && r.text) || "Non riuscito.";
      }
    });
    document.body.appendChild(host);
    box = host;
  }

  document.addEventListener("focusin", async (e) => {
    const el = e.target;
    if (!(el instanceof HTMLInputElement)) return;
    if (settings === null) settings = await browser.runtime.sendMessage({ op: "settings" }).catch(() => ({}));
    if (!settings || !settings.passwords) return;
    const f = loginFields();
    if (!f || (el !== f.pwd && el !== f.user)) return;
    if (box) return;
    show(f.user || f.pwd);
  }, true);

  document.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); }, true);
})();
