"use strict";
const $ = (s) => document.querySelector(s);
const toast = (t) => { $("#toast").textContent = t; setTimeout(() => { if ($("#toast").textContent === t) $("#toast").textContent = ""; }, 5000); };
const KIND_ICON = { handoff: "tabs", clip: "copy", notify: "bell", dict: "mic" };

async function send(what, ...args) {
  toast("Mando…");
  const r = await browser.runtime.sendMessage({ op: "send", what, args });
  toast(r.text);
  return r.ok;
}
async function currentTab() { return (await browser.tabs.query({ active: true, currentWindow: true }))[0]; }

$("#toNote").addEventListener("click", async () => { const t = $("#text").value.trim(); if (t && await send("note", t)) $("#text").value = ""; });
$("#toTask").addEventListener("click", async () => { const t = $("#text").value.trim(); if (t && await send("task", t)) $("#text").value = ""; });
$("#toClip").addEventListener("click", async () => { const t = $("#text").value.trim(); if (t && await send("clip", t)) $("#text").value = ""; });
$("#tabPhone").addEventListener("click", async () => { const t = await currentTab(); send("tab", { url: t.url, title: t.title }); });
$("#tabNote").addEventListener("click", async () => {
  // Con qualcosa scritto qui va quello; altrimenti la scheda aperta (come nella tavola).
  const text = $("#text").value.trim();
  if (text) { if (await send("note", text)) $("#text").value = ""; return; }
  const t = await currentTab(); send("note", "", t.title, t.url);
});

$("#dictate").addEventListener("click", async () => {
  const b = $("#dictate");
  b.disabled = true;
  toast("Guarda il telefono: tocca «Detta per il computer» e parla.");
  const r = await browser.runtime.sendMessage({ op: "dictate", into: "page" });
  b.disabled = false;
  if (!r.ok) { toast(r.text); return; }
  if (r.inserted) { toast("Scritto nella pagina."); return; }
  const box = $("#text");
  box.value = (box.value ? box.value + " " : "") + r.text;
  box.focus();
  toast("Arrivato: mandalo a Note, Attività o appunti.");
});

function untilText(start, day) {
  if (!start) return "tutto il giorno";
  const [h, m] = start.split(":").map(Number);
  const d = day ? new Date(day + "T00:00") : new Date();
  d.setHours(h, m, 0, 0);
  const mins = Math.round((d - Date.now()) / 60000);
  if (mins <= 0) return "adesso";
  if (mins < 60) return "tra " + mins + " minuti";
  const hrs = Math.floor(mins / 60);
  return "tra " + hrs + (hrs === 1 ? " ora" : " ore");
}

async function refreshToday() {
  const box = $("#today");
  const t = await browser.runtime.sendMessage({ op: "today" });
  if (!t.ok) {
    box.innerHTML = `<div class="empty">${t.off
      ? "Sul telefono: Eurise › Telefono e computer › accendi «Oggi sul computer»."
      : "Compare quando il telefono è collegato sul Wi-Fi di casa."}</div>`;
    return;
  }
  const rows = t.events.map((e) =>
    `<div class="row">${icon("calendar")}<span class="t"><b>${esc((e.start ? e.start + " " : "") + e.title)}</b><span>${esc([e.place, untilText(e.start, e.day)].filter(Boolean).join(" · "))}</span></span></div>`);
  if (t.tasksOpen) {
    const names = t.tasks.slice(0, 3).map((x) => x.title).join(" · ") + (t.tasksOpen > 3 ? " · …" : "");
    rows.push(`<div class="row">${icon("task")}<span class="t"><b>${t.tasksOpen === 1 ? "1 cosa da fare" : t.tasksOpen + " cose da fare"}</b><span>${esc(names)}</span></span></div>`);
  }
  box.innerHTML = rows.length ? rows.join("") : `<div class="empty">Niente in agenda per oggi, nessuna cosa da fare.</div>`;
}

function ago(ms) {
  const m = Math.round((Date.now() - ms) / 60000);
  if (m < 1) return "adesso";
  if (m < 60) return m + " min fa";
  const h = Math.round(m / 60);
  return h < 24 ? h + " h fa" : new Date(ms).toLocaleDateString("it-IT", { day: "numeric", month: "short" });
}

async function refresh() {
  const h = await browser.runtime.sendMessage({ op: "hello" });
  statusLine($("#status"), h);
  const r = await browser.runtime.sendMessage({ op: "recent" });
  const items = (r.lines || "").split("\n").filter(Boolean).map((l) => { try { return JSON.parse(l); } catch (e) { return null; } }).filter(Boolean).slice(0, 8);
  const box = $("#recent");
  if (!items.length) { box.innerHTML = `<div class="empty">${r.ok === "1" ? "Ancora niente: «Invia al computer» dal telefono e compare qui." : "Accendi il Ponte (eurise-ponte) per vedere cosa arriva dal telefono."}</div>`; return; }
  box.innerHTML = items.map((it, i) =>
    `<div class="row${it.url ? " link" : ""}" data-i="${i}">${icon(KIND_ICON[it.k] || "phone")}<span class="t"><b>${esc(it.title)}</b><span>${esc(ago(+it.at))} · ${esc(it.sub)}</span></span></div>`).join("");
  box.querySelectorAll(".link").forEach((el) => el.addEventListener("click", () => {
    const it = items[+el.dataset.i];
    if (it.url && /^https?:/.test(it.url)) browser.tabs.create({ url: it.url });
  }));
}

refresh();
refreshToday();
setInterval(() => { if (!document.hidden) refresh(); }, 5000);
// Oggi si chiede al telefono ogni due minuti, solo con il pannello aperto e in vista.
setInterval(() => { if (!document.hidden) refreshToday(); }, 120000);
document.addEventListener("visibilitychange", () => { if (!document.hidden) refreshToday(); });
