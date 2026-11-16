/*
 * Eurise per Firefox (tavola PcFirefox): il cuore dell'estensione.
 * - Parla con il Ponte Eurise del computer (eurise-ponte) attraverso il native messaging: tutto ciò che va al telefono
 *   passa da lì, cifrato sulla rete di casa. Niente server, niente servizi esterni.
 * - Applica l'aspetto Eurise a Firefox (Perla di giorno, Notte di sera o con il tema scuro del sistema).
 * - Mette le voci Eurise nel menu del tasto destro.
 * Messaggi dalle pagine dell'estensione e dalle pagine web: {op: "...", ...}; risposta {ok, text, ...}.
 */
"use strict";

const HOST = "eu.eurise.ponte";
// Spenti di base (CLAUDE.md, 14 novembre): le proposte di password leggono i moduli delle pagine, si accendono nelle Impostazioni.
// La protezione non fa uscire dati (li trattiene): accesa di base, si spegne nelle Impostazioni e Firefox torna com'era.
// L'AI della nuova scheda: nessuna di base (la domanda esce solo verso il servizio scelto, solo quando la si fa).
const DEFAULTS = { theme: "auto", passwords: false, menus: true, protection: true, ai: "" };

// --- Ponte (native messaging) ---

let port = null;
let seq = 0;
const waiting = new Map();
let lastHello = { ok: false, running: false, devices: [], text: "" };

function connect() {
  if (port) return port;
  try {
    port = browser.runtime.connectNative(HOST);
  } catch (e) {
    port = null;
    return null;
  }
  port.onMessage.addListener((m) => {
    const w = waiting.get(m.id);
    if (!w) return;
    waiting.delete(m.id);
    w.resolve(m);
  });
  port.onDisconnect.addListener((p) => {
    const why = (p && p.error && p.error.message) || "Ponte non raggiungibile";
    for (const w of waiting.values()) w.resolve({ ok: "0", lines: why, missing: "1" });
    waiting.clear();
    port = null;
  });
  return port;
}

/** Una richiesta al Ponte: t è il tipo (note, task, event, clip, handoff, file-part, pwd-ask, status…). */
function ponte(t, fields = {}, timeoutMs = 20000) {
  const p = connect();
  if (!p) return Promise.resolve({ ok: "0", lines: "Ponte Eurise non installato", missing: "1" });
  const id = String(++seq);
  const msg = { id, t };
  for (const [k, v] of Object.entries(fields)) if (v !== undefined && v !== null && v !== "") msg["f." + k] = String(v);
  return new Promise((resolve) => {
    waiting.set(id, { resolve });
    setTimeout(() => { if (waiting.delete(id)) resolve({ ok: "0", lines: "Il Ponte non risponde" }); }, timeoutMs);
    try { p.postMessage(msg); } catch (e) { waiting.delete(id); resolve({ ok: "0", lines: String(e) }); }
  });
}

async function hello() {
  const r = await ponte("hello", {}, 6000);
  const devices = (r.devices || "").split("\n").filter(Boolean);
  const live = (r.lines || "").split("\n").filter(Boolean).map((l) => { try { return JSON.parse(l); } catch (e) { return null; } }).filter(Boolean);
  lastHello = {
    ok: r.ok === "1", missing: r.missing === "1", running: r.running === "1", devices,
    live: live.filter((d) => d.live === "1").map((d) => d.name), text: r.lines || "",
  };
  return lastHello;
}

/** Il riassunto per l'utente: «Fairphone: arrivato», «In coda…», o l'errore. */
function said(r) {
  if (r.missing === "1") return "Il Ponte Eurise non è installato su questo computer: vedi Impostazioni dell'estensione.";
  return (r.lines || "").trim() || (r.ok === "1" ? "Fatto" : "Non riuscito");
}

function notify(title, text) {
  browser.notifications.create({ type: "basic", iconUrl: browser.runtime.getURL("icons/eurise.svg"), title, message: text }).catch(() => {});
}

// --- Cosa si può mandare ---

const send = {
  tab: (tab) => ponte("handoff", { url: tab.url, title: tab.title }),
  link: (url, title) => ponte("handoff", { url, title }),
  note: (text, title, url) => ponte("note", { text: [title, text, url].filter(Boolean).join("\n") }),
  task: (text) => ponte("task", { text }),
  clip: (text) => ponte("clip", { text }),
  event: (e) => ponte("event", e),
};

/** Un documento a pezzi da 512 KB (il Ponte lo ricompone e lo manda al telefono, che lo mette in Download/Eurise). */
async function sendFile(name, mime, buffer) {
  const bytes = new Uint8Array(buffer);
  if (bytes.length > 50 * 1024 * 1024) return { ok: "0", lines: "Il file supera 50 MB." };
  const PART = 512 * 1024;
  const parts = Math.max(1, Math.ceil(bytes.length / PART));
  const fid = crypto.randomUUID();
  let last = null;
  for (let i = 0; i < parts; i++) {
    const chunk = bytes.subarray(i * PART, Math.min(bytes.length, (i + 1) * PART));
    let bin = "";
    for (let j = 0; j < chunk.length; j += 0x8000) bin += String.fromCharCode.apply(null, chunk.subarray(j, j + 0x8000));
    last = await ponte("file-part", { fid, part: i, parts, name, mime: mime || "application/octet-stream", data: btoa(bin) }, 60000);
    if (last.ok !== "1") return last;
  }
  return last;
}

/** Un file da un link (tasto destro su un link a un PDF, un'immagine…): lo scarica Firefox, con i cookie del sito. */
async function sendUrlAsFile(url) {
  const res = await fetch(url, { credentials: "include" });
  if (!res.ok) return { ok: "0", lines: "Il sito ha risposto " + res.status };
  const buf = await res.arrayBuffer();
  const cd = res.headers.get("content-disposition") || "";
  const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd);
  const name = decodeURIComponent((m && m[1]) || new URL(url).pathname.split("/").pop() || "documento");
  return sendFile(name, (res.headers.get("content-type") || "").split(";")[0], buf);
}

// --- Password dal telefono (Eurise Password approva con l'impronta) ---

async function askPassword(site) {
  const ask = crypto.randomUUID();
  const r = await ponte("pwd-ask", { site, ask });
  if (r.ok !== "1" || !/arrivato/.test(r.lines || "")) return { ok: false, text: r.ok === "1" ? "Il telefono non è collegato: aprilo sul Wi-Fi di casa." : said(r) };
  const until = Date.now() + 120000;
  while (Date.now() < until) {
    await new Promise((res) => setTimeout(res, 1500));
    const w = await ponte("pwd-wait", { ask }, 5000);
    const line = (w.lines || "").trim();
    if (line) {
      try {
        const g = JSON.parse(line);
        return { ok: true, username: g["username"] || "", password: g["password"] || "" };
      } catch (e) { /* riprova */ }
    }
  }
  return { ok: false, text: "Nessuna risposta dal telefono in due minuti." };
}

// --- Detta dal telefono: il telefono ascolta, il testo torna qui ---

async function askDictation() {
  const ask = crypto.randomUUID();
  const r = await ponte("dict-ask", { ask });
  if (r.ok !== "1" || !/arrivato/.test(r.lines || "")) return { ok: false, text: r.ok === "1" ? "Il telefono non è collegato: aprilo sul Wi-Fi di casa." : said(r) };
  const until = Date.now() + 120000;
  while (Date.now() < until) {
    await new Promise((res) => setTimeout(res, 1200));
    const w = await ponte("dict-wait", { ask }, 5000);
    const line = (w.lines || "").trim();
    if (line) { try { return { ok: true, text: JSON.parse(line)["text"] || "" }; } catch (e) { /* riprova */ } }
  }
  return { ok: false, text: "Nessuna dettatura dal telefono in due minuti." };
}

/** Detta e scrive nel campo dove si stava scrivendo nella scheda aperta; se non c'è un campo, restituisce il testo. */
async function dictateIntoPage() {
  const r = await askDictation();
  if (!r.ok) return r;
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  const put = tab ? await browser.tabs.sendMessage(tab.id, { op: "insert", text: r.text }).catch(() => false) : false;
  return { ok: true, text: r.text, inserted: !!put };
}

// --- Oggi dal telefono ---

/** Una riga «today» del Ponte in {age, events, tasks…}; null se illeggibile. */
function parseToday(line) {
  try {
    const f = JSON.parse(line);
    if (f.off === "1") return { ok: false, off: true, age: +f.age || 0 };
    const events = (f.events || "").split("\n").filter(Boolean).map((l) => { const [start, end, title, place, day] = l.split("\t"); return { start, end, title, place, day }; });
    const tasks = (f.tasks || "").split("\n").filter(Boolean).map((l) => { const [title, due] = l.split("\t"); return { title, due }; });
    return { ok: true, events, tasks, tasksOpen: +f.tasksOpen || 0, day: f.day, age: +f.age || 0 };
  } catch (e) { return null; }
}

/** Chiede al telefono il riassunto di oggi e lo legge appena arriva (al massimo 5 secondi). */
async function today() {
  const asked = await ponte("today-ask", {}, 6000);
  if (asked.ok !== "1" || !/arrivato/.test(asked.lines || "")) return { ok: false, offline: true };
  lastTodayAsk = Date.now();
  for (let i = 0; i < 8; i++) {
    await new Promise((res) => setTimeout(res, 600));
    const r = await ponte("today", {}, 4000);
    const t = parseToday((r.lines || "").trim());
    if (!t || t.age > 10) continue;
    if (t.ok) todayMemo = { at: Date.now(), t };
    return t;
  }
  return { ok: false, offline: true };
}

let lastTodayAsk = 0;
let todayMemo = null;

/**
 * Per la nuova scheda: prima l'ultimo «Oggi» rimasto nel Ponte (TodayCache, dieci minuti) o in memoria; al telefono si
 * richiede al massimo ogni due minuti, così aprire tante schede non lo sveglia ogni volta.
 */
async function todayForNewTab() {
  const r = await ponte("today", {}, 4000);
  const cached = parseToday((r.lines || "").trim());
  if (cached && cached.ok) todayMemo = { at: Date.now() - cached.age * 1000, t: cached };
  if (cached && cached.age <= 120) return cached;
  if (Date.now() - lastTodayAsk < 120000) return todayMemo ? todayMemo.t : cached || { ok: false, offline: true };
  lastTodayAsk = Date.now();
  const fresh = await today();
  return fresh.ok || fresh.off ? fresh : (todayMemo && Date.now() - todayMemo.at < 6 * 3600000 ? todayMemo.t : fresh);
}

// --- Leggi dopo (sul computer, come sul telefono): pagine da riprendere dalla nuova scheda ---

async function laterList() { return (await browser.storage.local.get({ later: [] })).later; }

async function laterAdd(url, title) {
  if (!/^https?:/.test(url || "")) return { ok: false, text: "Questa pagina non si può salvare." };
  const later = (await laterList()).filter((x) => x.url !== url);
  later.unshift({ url, title: title || url, at: Date.now() });
  await browser.storage.local.set({ later: later.slice(0, 100) });
  return { ok: true, text: "In Leggi dopo: la ritrovi nella nuova scheda." };
}

/** Apre il primo «Leggi dopo» nella scheda data e lo toglie dall'elenco (letto). */
async function laterTake(url) {
  const later = await laterList();
  await browser.storage.local.set({ later: later.filter((x) => x.url !== url) });
}

// --- Tracciatori fermati: si contano quelli che Firefox ferma (NS_ERROR_TRACKING_URI e simili), per settimana ---

const trackers = { week: "", n: 0, dirty: false };

/** Il lunedì della settimana, «2026-11-16»: la chiave del conteggio. */
function weekKey(d = new Date()) {
  const m = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7));
  return m.getFullYear() + "-" + pad(m.getMonth() + 1) + "-" + pad(m.getDate());
}

browser.storage.local.get({ trackers: { week: "", n: 0 } }).then(({ trackers: t }) => {
  // Quelli contati prima che il disco rispondesse si sommano a quelli salvati.
  if (t.week !== weekKey()) return;
  if (trackers.week !== t.week) { trackers.week = t.week; trackers.n = 0; }
  trackers.n += t.n;
});

if (browser.webRequest) {
  browser.webRequest.onErrorOccurred.addListener((d) => {
    if (!/TRACKING|FINGERPRINTING|CRYPTOMINING/.test(d.error || "")) return;
    const w = weekKey();
    if (trackers.week !== w) { trackers.week = w; trackers.n = 0; }
    trackers.n++;
    trackers.dirty = true;
  }, { urls: ["<all_urls>"] });
}
// Si scrive su disco al massimo ogni 30 secondi, non a ogni richiesta fermata.
setInterval(() => {
  if (!trackers.dirty) return;
  trackers.dirty = false;
  browser.storage.local.set({ trackers: { week: trackers.week, n: trackers.n } });
}, 30000);

// --- Date nel testo scelto: «20 novembre alle 15:30», «20/11», «domani alle 9» ---

const MONTHS = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"];

function pad(n) { return String(n).padStart(2, "0"); }

/** Prima data e ora trovate nel testo (italiano), come {date: "2026-11-20", time: "15:30"|""}; null se niente. */
function findDate(text, now = new Date()) {
  const t = (text || "").toLowerCase();
  let d = null;
  let m = new RegExp("\\b(\\d{1,2})\\s+(" + MONTHS.join("|") + ")(?:\\s+(\\d{4}))?").exec(t);
  if (m) d = new Date(m[3] ? +m[3] : now.getFullYear(), MONTHS.indexOf(m[2]), +m[1]);
  if (!d && (m = /\b(\d{1,2})[\/.](\d{1,2})(?:[\/.](\d{2,4}))?(?!\d)/.exec(t)) && +m[2] >= 1 && +m[2] <= 12 && +m[1] >= 1 && +m[1] <= 31) {
    const y = m[3] ? (m[3].length === 2 ? 2000 + +m[3] : +m[3]) : now.getFullYear();
    d = new Date(y, +m[2] - 1, +m[1]);
  } else if (!d) m = null;
  if (!d && /\bdopodomani\b/.test(t)) d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 2);
  else if (!d && /\bdomani\b/.test(t)) d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  else if (!d && /\boggi\b/.test(t)) d = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (!d || isNaN(d)) return null;
  // Una data già passata senza anno è dell'anno prossimo.
  if (!m || !m[3]) { const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()); if (d < today) d.setFullYear(d.getFullYear() + 1); }
  const tm = /\b(?:alle|ore|h)\s*(\d{1,2})(?:[:.](\d{2}))?\b/.exec(t) || /\b(\d{1,2})[:.](\d{2})\b/.exec(t);
  const time = tm && +tm[1] < 24 ? pad(+tm[1]) + ":" + pad(tm[2] ? +tm[2] : 0) : "";
  return { date: d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()), time };
}

// --- Aspetto Eurise ---

const PERLA = {
  frame: "#EDEBE6", frame_inactive: "#F1EFEA", tab_background_text: "#56564F",
  tab_selected: "#FFFFFF", tab_text: "#1F2A44", tab_line: "#3E5C8A", tab_loading: "#3E5C8A",
  toolbar: "#FFFFFF", toolbar_text: "#1E1D1B", toolbar_field: "#F4F4F1", toolbar_field_text: "#1E1D1B",
  toolbar_field_border_focus: "#3E5C8A", toolbar_field_highlight: "#D6DDE8", toolbar_top_separator: "#E2E2DC",
  toolbar_bottom_separator: "#E2E2DC", icons: "#3A3A35", icons_attention: "#3E5C8A",
  popup: "#FFFFFF", popup_text: "#1E1D1B", popup_border: "#E2E2DC", popup_highlight: "#EAEEF3", popup_highlight_text: "#1F2A44",
  sidebar: "#F4F4F1", sidebar_text: "#1E1D1B", sidebar_border: "#E2E2DC", sidebar_highlight: "#3E5C8A", sidebar_highlight_text: "#FFFFFF",
  ntp_background: "#F4F4F1", ntp_text: "#1E1D1B", button_background_hover: "#EAEEF3", button_background_active: "#D6DDE8",
};
const NOTTE = {
  frame: "#141A26", frame_inactive: "#171E2B", tab_background_text: "#A9B4C8",
  tab_selected: "#26324A", tab_text: "#FFFFFF", tab_line: "#8FB4F0", tab_loading: "#8FB4F0",
  toolbar: "#1D2535", toolbar_text: "#E6E9F0", toolbar_field: "#141A26", toolbar_field_text: "#E6E9F0",
  toolbar_field_border_focus: "#8FB4F0", toolbar_field_highlight: "#3E5C8A", toolbar_top_separator: "#2A3448",
  toolbar_bottom_separator: "#2A3448", icons: "#C9D2E3", icons_attention: "#8FB4F0",
  popup: "#1D2535", popup_text: "#E6E9F0", popup_border: "#2A3448", popup_highlight: "#2B3A5C", popup_highlight_text: "#FFFFFF",
  sidebar: "#171E2B", sidebar_text: "#E6E9F0", sidebar_border: "#2A3448", sidebar_highlight: "#8FB4F0", sidebar_highlight_text: "#141A26",
  ntp_background: "#141A26", ntp_text: "#E6E9F0", button_background_hover: "#2B3A5C", button_background_active: "#3E5C8A",
};

const dark = window.matchMedia("(prefers-color-scheme: dark)");

async function applyTheme() {
  const { theme } = await browser.storage.local.get(DEFAULTS);
  if (theme === "firefox") { browser.theme.reset(); return; }
  const night = theme === "notte" || (theme === "auto" && dark.matches);
  browser.theme.update({ colors: night ? NOTTE : PERLA, properties: { color_scheme: night ? "dark" : "light", content_color_scheme: "auto" } });
}
dark.addEventListener("change", applyTheme);

// --- Menu del tasto destro ---

async function buildMenus() {
  await browser.menus.removeAll();
  const { menus } = await browser.storage.local.get(DEFAULTS);
  if (!menus) return;
  const icons = { "16": "icons/eurise.svg" };
  browser.menus.create({ id: "eurise", title: "Eurise", contexts: ["page", "link", "selection", "image", "tab"], icons });
  const add = (id, title, contexts) => browser.menus.create({ id, parentId: "eurise", title, contexts });
  add("tab-phone", "Apri sul telefono", ["page", "tab"]);
  add("page-later", "Leggi dopo", ["page", "tab"]);
  add("link-later", "Il link in Leggi dopo", ["link"]);
  add("link-phone", "Apri il link sul telefono", ["link"]);
  add("link-file", "Manda il file al telefono", ["link", "image"]);
  add("sel-note", "In Note", ["selection"]);
  add("page-note", "La pagina in Note", ["page"]);
  add("sel-task", "Da fare", ["selection"]);
  add("sel-event", "Appuntamento in Calendario…", ["selection"]);
  add("sel-clip", "Negli appunti del telefono", ["selection"]);
}

browser.menus.onClicked.addListener(async (info, tab) => {
  let r = null;
  const sel = (info.selectionText || "").trim();
  switch (info.menuItemId) {
    case "tab-phone": r = await send.tab(tab); break;
    case "page-later": notify("Eurise", (await laterAdd(tab.url, tab.title)).text); return;
    case "link-later": notify("Eurise", (await laterAdd(info.linkUrl, info.linkText)).text); return;
    case "link-phone": r = await send.link(info.linkUrl, info.linkText); break;
    case "link-file": r = await sendUrlAsFile(info.linkUrl || info.srcUrl).catch((e) => ({ ok: "0", lines: String(e) })); break;
    case "sel-note": r = await send.note(sel, tab.title, tab.url); break;
    case "page-note": r = await send.note("", tab.title, tab.url); break;
    case "sel-task": r = await send.task(sel); break;
    case "sel-clip": r = await send.clip(sel); break;
    case "sel-event":
      // Il modulo dell'appuntamento, già compilato con la frase e la data trovata.
      await browser.storage.session.set({ draftEvent: { text: sel, title: tab.title, url: tab.url, found: findDate(sel) } }).catch(() => {});
      browser.browserAction.openPopup().catch(() => notify("Eurise", "Apri il pulsante Eurise per completare l'appuntamento."));
      return;
  }
  if (r) notify("Eurise", said(r));
});

browser.commands.onCommand.addListener(async (name) => {
  if (name === "dictate") {
    notify("Eurise", "Parla al telefono: tocca la notifica «Detta per il computer».");
    const r = await dictateIntoPage();
    if (!r.ok || !r.inserted) notify("Eurise", r.ok ? "Dettatura arrivata: «" + r.text.slice(0, 80) + "» (nessun campo dove scriverla)." : r.text);
    return;
  }
  if (name !== "send-tab") return;
  const [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  if (tab) notify("Eurise", said(await send.tab(tab)));
});

// --- Messaggi da popup, pannello, impostazioni e pagine ---

browser.runtime.onMessage.addListener((m, sender) => {
  switch (m.op) {
    case "hello": return hello();
    case "recent": return ponte("recent", {}, 6000);
    case "send": {
      const fn = send[m.what];
      return fn ? fn(...(m.args || [])).then((r) => ({ ok: r.ok === "1", text: said(r) })) : Promise.resolve({ ok: false, text: "?" });
    }
    case "dictate": return m.into === "page" ? dictateIntoPage() : askDictation();
    case "today": return today();
    case "todayNewTab": return todayForNewTab();
    case "later": return laterList();
    case "laterAdd": return laterAdd(m.url, m.title);
    case "laterTake": return laterTake(m.url);
    case "trackers": return Promise.resolve(trackers.week === weekKey() ? trackers.n : 0);
    case "pwdFor": {
      // Solo dalle pagine dell'estensione (la nuova scheda), mai dagli script nelle pagine web, e solo se acceso.
      if (!sender.url || !sender.url.startsWith(browser.runtime.getURL(""))) return Promise.resolve({ ok: false, text: "?" });
      return browser.storage.local.get(DEFAULTS).then((s) => s.passwords ? askPassword(m.site) : { ok: false, off: true, text: "Password dal telefono è spenta: si accende nelle Impostazioni di Eurise." });
    }
    case "file": return sendFile(m.name, m.mime, m.buffer).then((r) => ({ ok: r.ok === "1", text: said(r) }));
    case "findDate": return Promise.resolve(findDate(m.text));
    case "draft": return browser.storage.session.get("draftEvent").then((d) => { browser.storage.session.remove("draftEvent"); return d.draftEvent || null; }).catch(() => null);
    case "password": {
      // Solo dalla pagina stessa, per il suo sito: mai per un altro dominio.
      const host = sender.tab && sender.url ? new URL(sender.url).hostname : "";
      return host ? askPassword(host) : Promise.resolve({ ok: false, text: "?" });
    }
    case "settings": return browser.storage.local.get(DEFAULTS);
    case "applySettings": return applyTheme().then(buildMenus).then(applyProtection).then(() => ({ ok: true }));
    case "protection": return protectionState();
  }
  return false;
});

browser.runtime.onInstalled.addListener(async (d) => {
  if (d.reason === "install") browser.runtime.openOptionsPage();
});

// --- Impostazioni Eurise per Firefox: la protezione del Browser Eurise del telefono (BrwProtezione) ---

/**
 * Le impostazioni che Eurise cambia in Firefox, con il valore Eurise. Si applicano solo quelle che questa versione di
 * Firefox conosce; spegnendo la protezione si tolgono tutte (clear) e Firefox torna alle sue o a quelle dell'utente.
 */
const PROTECTION = [
  ["websites", "trackingProtectionMode", "always", "Tracciatori bloccati anche nelle finestre normali"],
  ["websites", "cookieConfig", { behavior: "reject_trackers_and_partition_foreign" }, "Cookie dei tracciatori rifiutati, gli altri chiusi per sito"],
  ["network", "httpsOnlyMode", "always", "Solo HTTPS: i siti in chiaro chiedono prima di aprirsi"],
  ["websites", "hyperlinkAuditingEnabled", false, "Niente «ping» di tracciamento sui link"],
  ["network", "networkPredictionEnabled", false, "Niente collegamenti anticipati ai siti dei link"],
];

function setting(group, name) {
  return browser.privacy && browser.privacy[group] && browser.privacy[group][name];
}

async function applyProtection() {
  const { protection } = await browser.storage.local.get(DEFAULTS);
  for (const [group, name, value] of PROTECTION) {
    const s = setting(group, name);
    if (!s) continue;
    try { protection ? await s.set({ value }) : await s.clear({}); } catch (e) { console.warn("Eurise:", name, e); }
  }
}

/** Per le Impostazioni: ogni voce con «tenuta da Eurise», «decisa da un'altra estensione» o «non disponibile». */
async function protectionState() {
  const out = [];
  for (const [group, name, , label] of PROTECTION) {
    const s = setting(group, name);
    if (!s) { out.push({ label, state: "missing" }); continue; }
    try {
      const d = await s.get({});
      out.push({ label, state: d.levelOfControl === "controlled_by_this_extension" ? "eurise" : d.levelOfControl === "controlled_by_other_extensions" ? "other" : "firefox" });
    } catch (e) { out.push({ label, state: "missing" }); }
  }
  return out;
}

applyTheme();
buildMenus();
applyProtection();
