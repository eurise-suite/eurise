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
$("#tabNote").addEventListener("click", async () => { const t = await currentTab(); send("note", "", t.title, t.url); });

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
setInterval(() => { if (!document.hidden) refresh(); }, 5000);
