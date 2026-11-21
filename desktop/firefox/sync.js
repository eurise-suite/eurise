/*
 * Telefono e computer (tavola BrwSincro, 21 novembre 2026): Firefox con Eurise e il Browser Eurise del telefono.
 * - Segnalibri: una cartella «Eurise» nei segnalibri di Firefox (in Altri segnalibri) è la raccolta in comune con il
 *   telefono, con le sue sottocartelle. Eurise tocca solo quella cartella, mai gli altri segnalibri di Firefox.
 * - Siti fissati della nuova scheda e Leggi dopo: gli stessi del telefono.
 * - Schede aperte: quelle del telefono si vedono nel pannello Eurise, «Dal telefono»; quelle di qui vanno al telefono.
 * Si manda lo stato di qui; il telefono unisce (a tre vie: ricorda l'ultima volta) e risponde con il risultato, che qui
 * si applica così com'è. Passa solo dal Ponte sulla rete di casa, cifrato. Spenta di base: si accende nelle Impostazioni
 * di Eurise, e servono anche gli interruttori del telefono (Browser › Impostazioni › Telefono e computer).
 * Usa ponte() di background.js (caricato dopo: le funzioni qui si chiamano solo più tardi).
 */
"use strict";

const SYNC_ROOT_NAME = "Eurise";
const SYNC_EVERY_MS = 5 * 60000;
const SYNC_MAX = 2000;
let syncBusy = false;
let syncApplyingUntil = 0;
let syncSoon = null;

const isWeb = (u) => /^https?:\/\//.test(u || "");

async function syncSettings() {
  return browser.storage.local.get({ sync: false, syncRoot: "", syncLast: null, phoneTabs: null });
}

/** La cartella «Eurise» in Altri segnalibri: la stessa ogni volta (id ricordato), creata alla prima sincronizzazione. */
async function syncRoot() {
  const { syncRoot: id } = await syncSettings();
  if (id) {
    const found = await browser.bookmarks.get(id).catch(() => null);
    if (found && found[0] && found[0].type === "folder") return found[0].id;
  }
  const made = await browser.bookmarks.create({ parentId: "unfiled_____", title: SYNC_ROOT_NAME, type: "folder" });
  await browser.storage.local.set({ syncRoot: made.id });
  return made.id;
}

/** Tutti i segnalibri sotto la cartella, con la cartella come «A › B» (come sul telefono). */
async function syncReadBookmarks(rootId) {
  const [tree] = await browser.bookmarks.getSubTree(rootId);
  const out = [];
  const nodes = new Map();
  const walk = (node, path) => {
    for (const c of node.children || []) {
      if (c.type === "folder") walk(c, path.concat(c.title));
      else if (c.type === "bookmark" && isWeb(c.url)) {
        if (!nodes.has(c.url)) { nodes.set(c.url, c); out.push({ u: c.url, t: c.title || c.url, f: path.join(" › ") }); }
      }
    }
  };
  walk(tree, []);
  return { items: out, nodes, tree };
}

/** Rende la cartella uguale all'elenco unito: crea, rinomina, sposta, toglie. Le cartelle nuove si creano al bisogno. */
async function syncApplyBookmarks(rootId, merged) {
  syncApplyingUntil = Date.now() + 60000;
  try {
    const { nodes, tree } = await syncReadBookmarks(rootId);
    const folders = new Map([["", rootId]]);
    const index = (node, path) => {
      for (const c of node.children || []) if (c.type === "folder") { const p = path ? path + " › " + c.title : c.title; if (!folders.has(p)) folders.set(p, c.id); index(c, p); }
    };
    index(tree, "");
    const folderFor = async (path) => {
      if (folders.has(path)) return folders.get(path);
      const parts = path.split(" › ");
      const parent = await folderFor(parts.slice(0, -1).join(" › "));
      const made = await browser.bookmarks.create({ parentId: parent, title: parts[parts.length - 1], type: "folder" });
      folders.set(path, made.id);
      return made.id;
    };
    const keep = new Set();
    for (const b of merged.slice(0, SYNC_MAX)) {
      if (!isWeb(b.u)) continue;
      keep.add(b.u);
      const parentId = await folderFor(b.f || "");
      const n = nodes.get(b.u);
      if (!n) { await browser.bookmarks.create({ parentId, title: b.t || b.u, url: b.u }); continue; }
      if ((n.title || "") !== (b.t || "")) await browser.bookmarks.update(n.id, { title: b.t || b.u });
      if (n.parentId !== parentId) await browser.bookmarks.move(n.id, { parentId });
    }
    // Tolti dall'altra parte (o da qui prima): via, ma solo dentro la cartella Eurise. I doppioni della stessa pagina restano.
    for (const [url, n] of nodes) if (!keep.has(url)) await browser.bookmarks.remove(n.id).catch(() => {});
  } finally {
    syncApplyingUntil = Date.now() + 3000;
  }
}

function syncParse(text) {
  try { const a = JSON.parse(text || "[]"); return Array.isArray(a) ? a.filter((x) => x && isWeb(x.u)) : []; } catch (e) { return []; }
}

/**
 * Una sincronizzazione. Restituisce {ok, off?, offline?, at, on}. Non parte se è spenta, se manca il permesso dei
 * segnalibri o se ce n'è già una in corso.
 */
async function syncNow() {
  const s = await syncSettings();
  if (!s.sync) return { ok: false, off: true };
  if (syncBusy) return { ok: false, busy: true };
  syncBusy = true;
  try {
    const canBookmarks = await browser.permissions.contains({ permissions: ["bookmarks"] });
    const fields = { ask: crypto.randomUUID() };
    let rootId = null;
    const { pinned, later } = await browser.storage.local.get({ pinned: [], later: [] });
    if (canBookmarks) {
      rootId = await syncRoot();
      const { items } = await syncReadBookmarks(rootId);
      // I siti fissati sono segnalibri «fissati», come sul telefono.
      const byUrl = new Map(items.map((b) => [b.u, b]));
      for (const p of pinned) {
        if (!isWeb(p.url)) continue;
        const b = byUrl.get(p.url);
        if (b) b.p = 1; else { const nb = { u: p.url, t: p.title || p.url, p: 1 }; items.push(nb); byUrl.set(p.url, nb); }
      }
      fields.bm = JSON.stringify(items.slice(0, SYNC_MAX));
      fields.later = JSON.stringify(later.filter((x) => isWeb(x.url)).map((x) => ({ u: x.url, t: x.title || x.url })));
    }
    const tabs = await browser.tabs.query({});
    fields.tabs = JSON.stringify(tabs.filter((t) => !t.incognito && isWeb(t.url)).slice(0, 100).map((t) => ({ u: t.url, t: t.title || t.url })));

    const asked = await ponte("sync-ask", fields, 8000);
    if (asked.ok !== "1" || !/arrivato/.test(asked.lines || "")) return { ok: false, offline: true };
    let r = null;
    for (let i = 0; i < 16 && !r; i++) {
      await new Promise((res) => setTimeout(res, 700));
      const w = await ponte("sync-wait", { ask: fields.ask }, 5000);
      const line = (w.lines || "").trim();
      if (line) { try { r = JSON.parse(line); } catch (e) { /* riprova */ } }
    }
    if (!r) return { ok: false, offline: true };
    const at = Date.now();
    if (r.off === "1") {
      await browser.storage.local.set({ syncLast: { at, on: "" }, phoneTabs: null });
      return { ok: true, at, on: "" };
    }
    if (rootId && r.bm !== undefined) {
      const merged = syncParse(r.bm);
      await syncApplyBookmarks(rootId, merged);
      const nextPinned = merged.filter((b) => b.p === 1).slice(0, 8).map((b) => ({ url: b.u, title: b.t || b.u }));
      await browser.storage.local.set({ pinned: nextPinned });
    }
    if (rootId && r.later !== undefined) {
      const was = new Map(later.map((x) => [x.url, x]));
      const next = syncParse(r.later).slice(0, 100).map((x) => ({ url: x.u, title: x.t || x.u, at: (was.get(x.u) || {}).at || at }));
      await browser.storage.local.set({ later: next });
    }
    await browser.storage.local.set({
      syncLast: { at, on: r.on || "" },
      phoneTabs: r.tabs !== undefined ? { at, tabs: syncParse(r.tabs).map((x) => ({ url: x.u, title: x.t || x.u })) } : null,
    });
    return { ok: true, at, on: r.on || "" };
  } catch (e) {
    return { ok: false, text: String(e) };
  } finally {
    syncBusy = false;
  }
}

/** Fra poco (i cambi ai segnalibri arrivano a raffica): una sola sincronizzazione dopo 15 secondi di calma. */
function syncLater() {
  if (Date.now() < syncApplyingUntil) return;
  clearTimeout(syncSoon);
  syncSoon = setTimeout(() => { syncNow(); }, 15000);
}

let syncListening = false;

/** Gli ascolti dei segnalibri: solo quando il permesso c'è (chiesto accendendo la sincronizzazione). */
function syncListen() {
  if (syncListening || !browser.bookmarks) return;
  syncListening = true;
  for (const ev of ["onCreated", "onRemoved", "onChanged", "onMoved"]) {
    if (browser.bookmarks[ev]) browser.bookmarks[ev].addListener(syncLater);
  }
}

function syncStart() {
  setInterval(() => { syncNow(); }, SYNC_EVERY_MS);
  setTimeout(() => { syncNow(); }, 20000);
  syncListen();
  browser.storage.onChanged.addListener((ch, area) => {
    if (area === "local" && (ch.pinned || ch.later) && Date.now() >= syncApplyingUntil) syncLater();
  });
}

/** Le richieste delle pagine dell'estensione (Impostazioni, pannello): stato, accendi, spegni, adesso. */
async function syncOp(m) {
  switch (m.op) {
    case "syncState": {
      const s = await syncSettings();
      return { on: s.sync, last: s.syncLast, phoneTabs: s.phoneTabs, bookmarks: await browser.permissions.contains({ permissions: ["bookmarks"] }) };
    }
    case "syncOn": {
      // Il permesso dei segnalibri l'ha già chiesto la pagina delle Impostazioni (serve il tocco dell'utente).
      await browser.storage.local.set({ sync: true });
      syncListen();
      return syncNow();
    }
    case "syncOff":
      // Spegnere non chiede niente: ciò che c'è resta, non si sincronizza più; le schede del telefono si dimenticano.
      await browser.storage.local.set({ sync: false, phoneTabs: null });
      return { ok: true };
    case "syncNow": return syncNow();
  }
  return null;
}
