/*
 * La nuova scheda Eurise in Firefox (tavola PcFirefoxScheda, approvata il 16 novembre 2026).
 * Tutto ciò che compare viene da questo computer (cronologia, download, siti fissati, Leggi dopo) o dal telefono
 * attraverso il Ponte sulla rete di casa («Dal telefono», «Per oggi»). Mentre si scrive non esce nulla: niente
 * suggerimenti dal motore di ricerca; la domanda va al motore o all'AI solo con Invio.
 */
"use strict";
const $ = (s) => document.querySelector(s);
const msg = (m) => browser.runtime.sendMessage(m);
let toastTimer = 0;
function toast(t, ms = 5000) {
  $("#toast").textContent = t;
  clearTimeout(toastTimer);
  if (t) toastTimer = setTimeout(() => { $("#toast").textContent = ""; }, ms);
}
const svg = (d) => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${d}"/></svg>`;
const PATHS = {
  phone: ICONS.phone, calendar: ICONS.calendar,
  later: "M6 3.5h12v17l-6-4-6 4z",
  download: "M12 4v11M7 10l5 5 5-5M5 20h14",
  check: "M20 6 9 17l-5-5",
  plus: "M12 5v14M5 12h14",
};
let settings = {};

// --- Saluto ---

function greet() {
  const d = new Date();
  const day = d.toLocaleDateString("it-IT", { weekday: "long", day: "numeric", month: "long" });
  const h = d.getHours();
  const hi = h < 5 ? "buonanotte" : h < 13 ? "buongiorno" : h < 18 ? "buon pomeriggio" : "buonasera";
  $("#hello").textContent = day.charAt(0).toUpperCase() + day.slice(1) + " · " + hi;
}

// --- Ricerca: Cerca, Chiedi all'AI, Nella cronologia ---

let mode = "web";
let engine = "";

function setMode(m) {
  mode = m;
  document.querySelectorAll("[data-mode]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === m)));
  const ai = aiById(settings.ai);
  $("#q").placeholder = m === "ai" ? (ai ? "Fai una domanda a " + ai.name : "Fai una domanda all'AI")
    : m === "history" ? "Cerca nelle pagine che hai visto su questo computer"
    : (engine ? "Cerca con " + engine + " o scrivi un indirizzo" : "Cerca o scrivi un indirizzo");
  $("#aiPick").hidden = !(m === "ai" && !ai);
  $("#q").focus();
  showHistory();
}

/** «eurise.eu», «localhost:8080», «https://…»: si apre; il resto si cerca. */
function asAddress(text) {
  const t = text.trim();
  if (/^(https?|ftp):\/\//i.test(t)) return t;
  if (/\s/.test(t)) return null;
  if (/^localhost(:\d+)?(\/.*)?$/i.test(t) || /^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/.*)?$/.test(t)) return "http://" + t;
  if (/^[\w-]+(\.[\w-]+)*\.[a-z]{2,}(:\d+)?(\/.*)?$/i.test(t)) return "https://" + t;
  return null;
}

async function go(url) {
  const tab = await browser.tabs.getCurrent();
  browser.tabs.update(tab.id, { url });
}

$("#search").addEventListener("submit", async (e) => {
  e.preventDefault();
  const text = $("#q").value.trim();
  if (!text) return;
  if (mode === "history") { const first = $("#results a"); if (first) first.click(); return; }
  if (mode === "ai") {
    const ai = aiById(settings.ai);
    if (!ai) { $("#aiPick").hidden = false; return; }
    return go(ai.url.replace("%s", encodeURIComponent(text)));
  }
  const addr = asAddress(text);
  if (addr) return go(addr);
  // Il motore scelto in Firefox (Brave Search se l'utente l'ha accettato all'installazione).
  const tab = await browser.tabs.getCurrent();
  browser.search.search({ query: text, tabId: tab.id }).catch(() => go("https://search.brave.com/search?q=" + encodeURIComponent(text)));
});

document.querySelectorAll("[data-mode]").forEach((b) => {
  b.addEventListener("click", () => setMode(b.dataset.mode));
  if (b.dataset.mode === "ai") {
    // Tasto destro o pressione lunga: l'elenco delle AI con la stellina sulla preferita.
    b.addEventListener("contextmenu", (e) => { e.preventDefault(); mode = "ai"; setMode("ai"); $("#aiPick").hidden = false; });
    let hold = 0;
    b.addEventListener("pointerdown", () => { hold = setTimeout(() => { setMode("ai"); $("#aiPick").hidden = false; }, 550); });
    for (const ev of ["pointerup", "pointerleave"]) b.addEventListener(ev, () => clearTimeout(hold));
  }
});

function renderAiList() {
  $("#aiList").innerHTML = AIS.map((a) => `<button type="button" class="action" data-ai="${a.id}">
      <span class="star2" aria-hidden="true">${settings.ai === a.id ? "★" : "☆"}</span>
      <span class="t"><b>${esc(a.name)} ${ticksHtml(a.ticks)}</b><span>${esc(a.sub)}${a.closed ? " · servizio chiuso, meglio le prime due" : ""}</span></span></button>`).join("")
    + `<button type="button" class="action" data-ai=""><span class="star2" aria-hidden="true">${settings.ai ? "☆" : "★"}</span><span class="t"><b>Nessuna</b><span>«Chiedi all'AI» non manda niente</span></span></button>`;
}
$("#aiList").addEventListener("click", async (e) => {
  const b = e.target.closest("[data-ai]");
  if (!b) return;
  const a = aiById(b.dataset.ai);
  if (a && a.closed && !confirm(a.name + ": " + a.sub + ".\n\nLe domande restano sui server negli Stati Uniti. Le alternative europee o anonime (Le Chat, Duck.ai) sono in cima all'elenco.\nSi cambia qui o nelle Impostazioni di Eurise, senza conferme.\n\nUsarla lo stesso?")) return;
  settings.ai = b.dataset.ai;
  await browser.storage.local.set({ ai: settings.ai });
  renderAiList();
  $("#aiPick").hidden = true;
  setMode(settings.ai ? "ai" : "web");
  if (settings.ai) toast("Chiedi all'AI: " + a.name + ". La domanda parte solo con Invio.");
});

// Nella cronologia: solo su questo computer, mentre si scrive.
let histTimer = 0;
async function showHistory() {
  const box = $("#results");
  const text = $("#q").value.trim();
  if (mode !== "history" || !text) { box.hidden = true; box.innerHTML = ""; return; }
  const items = await browser.history.search({ text, startTime: 0, maxResults: 8 });
  box.hidden = false;
  box.innerHTML = items.length
    ? items.map((h) => `<li><a href="${esc(h.url)}"><b>${esc(h.title || h.url)}</b><span>${esc(h.url)}</span></a></li>`).join("")
    : `<li class="empty">Niente nella cronologia per «${esc(text)}».</li>`;
}
$("#q").addEventListener("input", () => { clearTimeout(histTimer); histTimer = setTimeout(showHistory, 120); });
$("#q").addEventListener("keydown", (e) => {
  if (e.key === "ArrowDown" && mode === "history") { const a = $("#results a"); if (a) { e.preventDefault(); a.focus(); } }
});
$("#results").addEventListener("keydown", (e) => {
  const a = e.target.closest("li");
  if (!a) return;
  if (e.key === "ArrowDown" && a.nextElementSibling) { e.preventDefault(); a.nextElementSibling.querySelector("a").focus(); }
  if (e.key === "ArrowUp") { e.preventDefault(); (a.previousElementSibling ? a.previousElementSibling.querySelector("a") : $("#q")).focus(); }
});

// --- Riprendi da dove eri: dal telefono, Leggi dopo, l'ultimo download ---

function ago(ms) {
  const m = Math.round((Date.now() - ms) / 60000);
  if (m < 1) return "adesso";
  if (m < 60) return m + " min fa";
  const h = Math.round(m / 60);
  return h < 24 ? h + " h fa" : new Date(ms).toLocaleDateString("it-IT", { day: "numeric", month: "short" });
}
function host(url) { try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return url; } }

async function renderResume() {
  const cards = [];
  const [recent, later, downloads] = await Promise.all([
    msg({ op: "recent" }).catch(() => ({})),
    msg({ op: "later" }).catch(() => []),
    browser.downloads.search({ orderBy: ["-startTime"], limit: 10, state: "complete" }).catch(() => []),
  ]);
  const fromPhone = (recent.lines || "").split("\n").filter(Boolean).map((l) => { try { return JSON.parse(l); } catch (e) { return null; } })
    .find((it) => it && /^https?:/.test(it.url || "") && Date.now() - +it.at < 24 * 3600000);
  if (fromPhone) cards.push({ icon: PATHS.phone, label: "Dal telefono · " + ago(+fromPhone.at), title: fromPhone.title || host(fromPhone.url), open: () => go(fromPhone.url) });
  if (later.length) {
    const l = later[0];
    cards.push({ icon: PATHS.later, label: "Leggi dopo · " + later.length, title: l.title || host(l.url), open: async () => { await msg({ op: "laterTake", url: l.url }); go(l.url); } });
  }
  const dl = downloads.find((d) => d.exists !== false);
  if (dl) {
    const name = dl.filename.split(/[\\/]/).pop();
    cards.push({ icon: PATHS.download, label: "Download · " + ago(Date.parse(dl.endTime || dl.startTime)), title: name, open: () => browser.downloads.open(dl.id).catch(() => browser.downloads.show(dl.id)) });
  }
  const box = $("#resume");
  box.innerHTML = cards.map((c, i) => `<a href="#" data-i="${i}"><small>${svg(c.icon)}${esc(c.label)}</small><b>${esc(c.title)}</b></a>`).join("");
  box.querySelectorAll("a").forEach((a) => a.addEventListener("click", (e) => { e.preventDefault(); cards[+a.dataset.i].open(); }));
}

// --- Siti fissati: otto al massimo, la lettera nella tinta della suite (nessuna icona scaricata dai siti) ---

const TINTS = [["#DDE3F0", "#1F2A44"], ["#F3E2CF", "#7A4A2A"], ["#DCE7DF", "#2F6B45"], ["#E8E1F2", "#5A4BA0"],
  ["#F6E0DC", "#9A3B2E"], ["#E3EDF0", "#2F6F73"], ["#ECECE7", "#3A3A35"], ["#F2ECD6", "#85672B"]];
function tint(s) { let h = 0; for (const c of s) h = (h * 31 + c.charCodeAt(0)) >>> 0; return TINTS[h % TINTS.length]; }

async function renderPins() {
  const { pinned } = await browser.storage.local.get({ pinned: [] });
  const box = $("#pins");
  box.innerHTML = pinned.map((p, i) => {
    const [b, f] = tint(host(p.url));
    const name = p.title || host(p.url);
    return `<a href="${esc(p.url)}" title="${esc(p.url)}"><span class="letter" style="--tb:${b};--tf:${f}" aria-hidden="true">${esc(name.charAt(0).toUpperCase())}</span><span>${esc(name)}</span>`
      + `<button type="button" class="x" data-rm="${i}" aria-label="Togli ${esc(name)}">×</button></a>`;
  }).join("") + (pinned.length < 8 ? `<button type="button" class="add" id="pinAdd" aria-label="Fissa un sito">${svg(PATHS.plus)}Fissa</button>` : "");
  box.querySelectorAll("[data-rm]").forEach((x) => x.addEventListener("click", async (e) => {
    e.preventDefault(); e.stopPropagation();
    const i = +x.dataset.rm;
    const gone = pinned.splice(i, 1)[0];
    await browser.storage.local.set({ pinned });
    renderPins();
    toast("Tolto " + (gone.title || host(gone.url)) + ".");
  }));
  const add = $("#pinAdd");
  if (add) add.addEventListener("click", () => { $("#pinForm").hidden = false; $("#pinUrl").focus(); });
}
$("#pinForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const url = asAddress($("#pinUrl").value) || "";
  if (!url) { toast("Scrivi un indirizzo, per esempio eurise.eu"); return; }
  const { pinned } = await browser.storage.local.get({ pinned: [] });
  if (!pinned.some((p) => p.url === url)) {
    const word = host(url).split(".").slice(-2, -1)[0] || host(url);
    pinned.push({ url, title: $("#pinName").value.trim() || word.charAt(0).toUpperCase() + word.slice(1) });
  }
  await browser.storage.local.set({ pinned: pinned.slice(0, 8) });
  $("#pinForm").reset(); $("#pinForm").hidden = true;
  renderPins();
});
$("#pinCancel").addEventListener("click", () => { $("#pinForm").reset(); $("#pinForm").hidden = true; });

// --- Per oggi, dalla suite (dal telefono, via Ponte) ---

/** «tra 1 h 48», «tra 12 min», «adesso», o vuoto per i giorni interi. */
function until(start, day) {
  if (!start) return "";
  const [h, m] = start.split(":").map(Number);
  const d = day ? new Date(day + "T00:00") : new Date();
  d.setHours(h, m, 0, 0);
  const mins = Math.round((d - Date.now()) / 60000);
  if (mins <= 0) return "adesso";
  if (mins < 60) return "tra " + mins + " min";
  return "tra " + Math.floor(mins / 60) + " h" + (mins % 60 ? " " + pad2(mins % 60) : "");
}
function pad2(n) { return String(n).padStart(2, "0"); }
function linkOf(place) {
  const p = (place || "").trim();
  if (/^https?:\/\//.test(p)) return p;
  return /^[\w-]+(\.[\w-]+)+\/\S*$/.test(p) ? "https://" + p : null;
}

function renderToday(t) {
  const box = $("#today");
  if (!t || !t.ok) {
    box.innerHTML = `<li class="empty">${t && t.off
      ? "Sul telefono: Eurise › Telefono e computer › accendi «Oggi sul computer»."
      : "Appuntamenti e cose da fare compaiono qui quando il telefono è sul Wi-Fi di casa."}</li>`;
    return;
  }
  const rows = [];
  for (const e of t.events.slice(0, 4)) {
    const url = linkOf(e.place);
    rows.push({ cls: "cal", icon: ICONS.calendar, title: e.title + (e.start ? " alle " + e.start : "") + (url ? " · entra" : ""),
      sub: [e.place, "dal Calendario"].filter(Boolean).join(" · "), when: until(e.start, e.day), url });
  }
  for (const k of t.tasks.slice(0, 3)) rows.push({ cls: "task", icon: PATHS.check, title: k.title, sub: ["Da fare", k.due].filter(Boolean).join(" · ") });
  if (t.tasksOpen > 3) rows.push({ cls: "task", icon: PATHS.check, title: "Altre " + (t.tasksOpen - 3) + " cose da fare", sub: "su Eurise Attività" });
  box.innerHTML = rows.length ? rows.map((r, i) => `<li${r.url ? ` class="link" data-i="${i}" style="cursor:pointer"` : ""}>`
    + `<span class="ic ${r.cls}" aria-hidden="true">${svg(r.icon)}</span>`
    + `<span class="t"><b>${esc(r.title)}</b><span>${esc(r.sub)}</span></span>`
    + (r.when ? `<span class="when">${esc(r.when)}</span>` : "") + `</li>`).join("")
    : `<li class="empty">Niente in agenda per oggi, nessuna cosa da fare.</li>`;
  box.querySelectorAll(".link").forEach((li) => li.addEventListener("click", () => go(rows[+li.dataset.i].url)));
}

async function refreshToday() {
  // L'ultimo arrivato si mostra subito; il resto lo decide il fondo (al telefono al massimo ogni due minuti).
  try {
    const { todayLast } = await browser.storage.session.get("todayLast");
    if (todayLast && todayLast.day === new Date().toDateString()) renderToday(todayLast.t);
  } catch (e) { /* storage.session c'è da Firefox 115 */ }
  const t = await msg({ op: "todayNewTab" }).catch(() => null);
  renderToday(t);
  if (t && t.ok) browser.storage.session.set({ todayLast: { day: new Date().toDateString(), t } }).catch(() => {});
}

// --- Strumenti: Traduci, Password, Detta dal telefono, In Note ---

$("#translate").addEventListener("click", async () => {
  // La traduzione di Firefox, sul computer: nessun testo esce.
  const text = $("#q").value.trim();
  const url = "about:translations" + (text ? "#trg=it&text=" + encodeURIComponent(text) : "");
  try { await browser.tabs.create({ url }); } catch (e) {
    toast("Firefox non lascia aprire la sua pagina di traduzione da un'estensione: scrivi about:translations nella barra. Per una pagina: Traduci nel menu di Firefox.", 9000);
  }
});

$("#password").addEventListener("click", async () => {
  if (!settings.passwords) {
    toast("«Password dal telefono» è spenta: la accendi nelle Impostazioni di Eurise.", 7000);
    browser.runtime.openOptionsPage();
    return;
  }
  $("#pwd").hidden = false; $("#pwdOut").hidden = true;
  const typed = $("#q").value.trim();
  $("#pwdSite").value = typed && asAddress(typed) ? host(asAddress(typed)) : "";
  $("#pwdSite").focus();
});
let pwdClear = 0;
function closePwd() { $("#pwd").hidden = true; $("#pwdOut").innerHTML = ""; clearTimeout(pwdClear); }
$("#pwdClose").addEventListener("click", closePwd);
$("#pwdForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const site = host(asAddress($("#pwdSite").value) || $("#pwdSite").value.trim());
  if (!site) return;
  toast("Guarda il telefono: approva con l'impronta in Eurise Password.", 120000);
  const r = await msg({ op: "pwdFor", site });
  if (!r.ok) { toast(r.text); return; }
  toast("");
  const out = $("#pwdOut");
  out.hidden = false;
  out.innerHTML = `<span class="who"><b>${esc(site)}</b> · ${esc(r.username || "senza nome utente")}</span>`
    + (r.username ? `<button type="button" id="cpUser">Copia nome</button>` : "") + `<button type="button" id="cpPwd" class="strong">Copia password</button>`;
  const user = r.username;
  let secret = r.password;
  if (user) $("#cpUser").addEventListener("click", () => navigator.clipboard.writeText(user).then(() => toast("Nome utente copiato.")));
  $("#cpPwd").addEventListener("click", async () => {
    await navigator.clipboard.writeText(secret);
    toast("Password copiata: tra 30 secondi la tolgo dagli appunti.");
    setTimeout(() => navigator.clipboard.writeText("").catch(() => {}), 30000);
  });
  // La password non resta nella pagina: dopo un minuto si dimentica.
  pwdClear = setTimeout(() => { secret = ""; closePwd(); }, 60000);
});

$("#dictate").addEventListener("click", async () => {
  const b = $("#dictate");
  b.disabled = true;
  toast("Guarda il telefono: tocca «Detta per il computer» e parla.", 120000);
  const r = await msg({ op: "dictate", into: "here" });
  b.disabled = false;
  if (!r.ok) { toast(r.text); return; }
  const q = $("#q");
  q.value = (q.value ? q.value + " " : "") + r.text;
  q.focus();
  toast("Arrivato: Invio per cercare, oppure «In Note».");
});

$("#toNote").addEventListener("click", async () => {
  const text = $("#q").value.trim();
  if (!text) { toast("Scrivi la nota nel campo qui sopra, poi «In Note»."); $("#q").focus(); return; }
  toast("Mando…");
  const r = await msg({ op: "send", what: "note", args: [text] });
  toast(r.text);
  if (r.ok) $("#q").value = "";
});

// --- Tracciatori fermati questa settimana (contati da Firefox su questo computer) ---

async function renderTrackers() {
  const n = await msg({ op: "trackers" }).catch(() => 0);
  const el = $("#trackers");
  el.hidden = !n;
  el.lastElementChild.textContent = "Questa settimana " + String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ".") + (n === 1 ? " tracciatore fermato" : " tracciatori fermati");
}

// --- Avvio ---

(async () => {
  greet();
  settings = await msg({ op: "settings" });
  const engines = await browser.search.get().catch(() => []);
  const def = engines.find((x) => x.isDefault);
  engine = def ? def.name : "";
  renderAiList();
  setMode("web");
  renderPins();
  renderResume();
  renderTrackers();
  refreshToday();
})();
browser.storage.onChanged.addListener((ch) => {
  if (ch.pinned) renderPins();
  if (ch.later) renderResume();
  if (ch.ai || ch.passwords) msg({ op: "settings" }).then((s) => { settings = s; renderAiList(); if (mode === "ai") setMode("ai"); });
});
document.addEventListener("visibilitychange", () => { if (!document.hidden) { greet(); renderResume(); renderTrackers(); refreshToday(); } });
