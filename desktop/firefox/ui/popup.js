"use strict";
const $ = (s) => document.querySelector(s);
const toast = (t) => { $("#toast").textContent = t; };
const iso = (x) => x.getFullYear() + "-" + String(x.getMonth() + 1).padStart(2, "0") + "-" + String(x.getDate()).padStart(2, "0");
let tab = null;
let selection = "";

const ACTIONS = {
  phone: ["phone", "Apri sul telefono", "questa scheda, in Eurise Browser"],
  note: ["note", "In Note", "la pagina o la frase scelta"],
  task: ["task", "Da fare", "come si parla: «chiamare Anna domani alle 10»"],
  event: ["calendar", "Appuntamento", "in Eurise Calendario"],
  file: ["file", "Documento al telefono", "un file del computer, in Download/Eurise"],
  dict: ["mic", "Detta dal telefono", "parli al telefono, il testo va nel campo della pagina"],
};

for (const b of document.querySelectorAll("[data-act]")) {
  const [ic, title, sub] = ACTIONS[b.dataset.act];
  b.innerHTML = `${icon(ic)}<span class="t"><b>${esc(title)}</b><span>${esc(sub)}</span></span>`;
}

function showForm(id) {
  $("#menu").hidden = !!id;
  for (const f of document.querySelectorAll("form")) f.hidden = f.id !== id;
  const first = id && $("#" + id).querySelector("input, textarea");
  if (first) first.focus();
}
for (const b of document.querySelectorAll(".back")) b.addEventListener("click", () => { showForm(null); toast(""); });

async function send(what, ...args) {
  toast("Mando…");
  const r = await browser.runtime.sendMessage({ op: "send", what, args });
  toast(r.text);
  if (r.ok) setTimeout(() => window.close(), 1400);
}

$("#menu").addEventListener("click", async (e) => {
  const b = e.target.closest("[data-act]");
  if (!b) return;
  switch (b.dataset.act) {
    case "phone": return send("tab", { url: tab.url, title: tab.title });
    case "note":
      $("#noteTitle").value = tab.title || "";
      $("#noteText").value = selection;
      return showForm("noteForm");
    case "task":
      $("#taskText").value = selection;
      return showForm("taskForm");
    case "event": return openEvent({ text: selection, title: tab.title, url: tab.url, found: await browser.runtime.sendMessage({ op: "findDate", text: selection }) });
    case "file": return showForm("fileForm");
    case "dict": {
      toast("Guarda il telefono: tocca «Detta per il computer» e parla.");
      const r = await browser.runtime.sendMessage({ op: "dictate", into: "page" });
      toast(!r.ok ? r.text : r.inserted ? "Scritto nella pagina." : "Arrivato: «" + r.text.slice(0, 60) + "» (nessun campo nella pagina).");
      if (r.ok && r.inserted) setTimeout(() => window.close(), 900);
      return;
    }
  }
});

$("#taskForm").addEventListener("submit", (e) => { e.preventDefault(); const t = $("#taskText").value.trim(); if (t) send("task", t); });
$("#noteForm").addEventListener("submit", (e) => {
  e.preventDefault();
  send("note", $("#noteText").value.trim(), $("#noteTitle").value.trim(), $("#noteUrl").checked ? tab.url : "");
});

function openEvent(d) {
  const today = new Date();
  $("#evTitle").value = (d.text || "").split("\n")[0].slice(0, 120) || d.title || "";
  $("#evDate").value = (d.found && d.found.date) || iso(today);
  if (d.found && d.found.time) {
    $("#evStart").value = d.found.time;
    const [h, m] = d.found.time.split(":").map(Number);
    $("#evEnd").value = String((h + 1) % 24).padStart(2, "0") + ":" + String(m).padStart(2, "0");
  }
  $("#evNotes").value = [d.text && d.text.length > 120 ? d.text : "", d.url].filter(Boolean).join("\n");
  showForm("eventForm");
}
$("#evAllDay").addEventListener("change", () => { $("#evTimes").hidden = $("#evAllDay").checked; });
$("#eventForm").addEventListener("submit", (e) => {
  e.preventDefault();
  const date = $("#evDate").value;
  const allDay = $("#evAllDay").checked;
  const ev = { title: $("#evTitle").value.trim(), start: allDay ? date : date + "T" + $("#evStart").value, place: $("#evPlace").value.trim(), notes: $("#evNotes").value.trim() };
  if (!allDay && $("#evEnd").value) {
    // Fine prima dell'inizio: finisce il giorno dopo (riunione a cavallo della mezzanotte).
    let endDate = date;
    if ($("#evEnd").value <= $("#evStart").value) { const d = new Date(date + "T00:00"); d.setDate(d.getDate() + 1); endDate = iso(d); }
    ev.end = endDate + "T" + $("#evEnd").value;
  }
  send("event", ev);
});

$("#fileForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const files = Array.from($("#fileInput").files);
  if (!files.length) return;
  for (const f of files) {
    toast("Mando " + f.name + "…");
    const r = await browser.runtime.sendMessage({ op: "file", name: f.name, mime: f.type, buffer: await f.arrayBuffer() });
    toast(r.text);
    if (!r.ok) return;
  }
  setTimeout(() => window.close(), 1600);
});

$("#side").addEventListener("click", () => { browser.sidebarAction.open(); window.close(); });
$("#opts").addEventListener("click", () => { browser.runtime.openOptionsPage(); window.close(); });

(async () => {
  [tab] = await browser.tabs.query({ active: true, currentWindow: true });
  selection = await browser.tabs.executeScript(tab.id, { code: "String(getSelection()).trim()" }).then((r) => r[0] || "").catch(() => "");
  const draft = await browser.runtime.sendMessage({ op: "draft" });
  if (draft) openEvent(draft);
  statusLine($("#status"), await browser.runtime.sendMessage({ op: "hello" }));
})();

// Protezione Eurise: accesa di base, si spegne qui con un tocco (e si riaccende uguale); Firefox torna com'era.
async function showProtection() {
  const { protection } = await browser.storage.local.get({ protection: true });
  $("#protection").setAttribute("aria-checked", String(protection));
  $("#protSub").textContent = protection ? "tracciatori bloccati, solo HTTPS" : "spenta: Firefox com'era";
}
$("#protection").addEventListener("click", async () => {
  const { protection } = await browser.storage.local.get({ protection: true });
  await browser.storage.local.set({ protection: !protection });
  await browser.runtime.sendMessage({ op: "applySettings" });
  showProtection();
});
showProtection();
