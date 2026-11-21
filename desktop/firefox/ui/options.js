"use strict";
const $ = (s) => document.querySelector(s);
const DEFAULTS = { theme: "auto", passwords: false, menus: true, protection: true, ai: "" };

/** Le 10 tacchette della privacy (regola fissa della suite): verdi fino a 7, gialle fino a 4, rosse sotto. */
function ticks(el, n) {
  const cls = n >= 7 ? "g" : n >= 4 ? "y" : "r";
  el.innerHTML = Array.from({ length: 10 }, (_, i) => `<i class="${i < n ? cls : ""}"></i>`).join("");
  el.title = n + " su 10";
}

async function save(patch) {
  await browser.storage.local.set(patch);
  await browser.runtime.sendMessage({ op: "applySettings" });
  render();
}

async function render() {
  const s = await browser.storage.local.get(DEFAULTS);
  document.querySelectorAll("#theme button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.v === s.theme)));
  $("#passwords").setAttribute("aria-checked", String(s.passwords));
  $("#menus").setAttribute("aria-checked", String(s.menus));
  renderSync();
  $("#protection").setAttribute("aria-checked", String(s.protection));
  $("#aiSeg").innerHTML = [{ id: "", name: "Nessuna" }, ...AIS].map((a) =>
    `<button data-ai="${a.id}" aria-pressed="${s.ai === a.id}">${esc(a.name)}${a.ticks ? " " + ticksHtml(a.ticks) : ""}</button>`).join("");
  const cur = aiById(s.ai);
  $("#aiNote").textContent = cur ? cur.name + ": " + cur.sub + "." : "«Chiedi all'AI» non manda niente finché non ne scegli una.";
  const states = await browser.runtime.sendMessage({ op: "protection" });
  const word = { eurise: "attiva", firefox: "come Firefox", other: "decisa da un'altra estensione", missing: "non c'è in questa versione di Firefox" };
  $("#protList").replaceChildren(...(states || []).map((x) => {
    const li = document.createElement("li");
    li.textContent = x.label + " · " + word[x.state];
    return li;
  }));
}

$("#theme").addEventListener("click", (e) => { const b = e.target.closest("button"); if (b) save({ theme: b.dataset.v }); });
$("#protection").addEventListener("click", async () => { const s = await browser.storage.local.get(DEFAULTS); save({ protection: !s.protection }); });
$("#menus").addEventListener("click", async () => { const s = await browser.storage.local.get(DEFAULTS); save({ menus: !s.menus }); });
$("#passwords").addEventListener("click", async () => {
  const s = await browser.storage.local.get(DEFAULTS);
  if (s.passwords) save({ passwords: false }); else $("#pwdExplain").showModal();
});
let aiPending = "";
$("#aiSeg").addEventListener("click", (e) => {
  const b = e.target.closest("[data-ai]");
  if (!b) return;
  const a = aiById(b.dataset.ai);
  // Spegnere non chiede conferme; accendere mostra prima cosa succede (CLAUDE.md, privacy sempre chiara).
  if (!a) { save({ ai: "" }); return; }
  aiPending = a.id;
  $("#aiTitle").textContent = "Chiedi all'AI con " + a.name;
  $("#aiWhere").textContent = a.sub + ".";
  $("#aiClosed").hidden = !a.closed;
  $("#aiExplain").showModal();
});
$("#aiYes").addEventListener("click", () => { $("#aiExplain").close(); save({ ai: aiPending }); });
$("#aiNo").addEventListener("click", () => $("#aiExplain").close());
$("#pwdYes").addEventListener("click", () => { $("#pwdExplain").close(); save({ passwords: true }); });
$("#pwdNo").addEventListener("click", () => $("#pwdExplain").close());

// --- Telefono e computer (sync.js) ---

async function renderSync() {
  const st = await browser.runtime.sendMessage({ op: "syncState" });
  $("#sync").setAttribute("aria-checked", String(!!(st && st.on)));
  const note = $("#syncNote");
  if (!st || !st.on) { note.textContent = ""; return; }
  if (!st.last) { note.textContent = "In attesa del telefono: apri Eurise sul telefono, sul Wi-Fi di casa."; return; }
  const when = new Date(st.last.at).toLocaleString("it-IT", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
  note.textContent = st.last.on
    ? "Ultima sincronizzazione: " + when + " (sul telefono: " + st.last.on.replace("bm", "segnalibri").replace("tabs", "schede").replace(",", ", ") + ")."
    : "Ultima volta " + when + ": sul telefono è spenta (Browser › Impostazioni › Telefono e computer).";
}

$("#sync").addEventListener("click", async () => {
  const st = await browser.runtime.sendMessage({ op: "syncState" });
  if (st && st.on) { await browser.runtime.sendMessage({ op: "syncOff" }); renderSync(); } else $("#syncExplain").showModal();
});
$("#syncNo").addEventListener("click", () => $("#syncExplain").close());
$("#syncYes").addEventListener("click", async () => {
  // Il permesso si chiede qui, sul tocco: Firefox lo vuole da un gesto dell'utente.
  const granted = await browser.permissions.request({ permissions: ["bookmarks"] }).catch(() => false);
  $("#syncExplain").close();
  $("#syncNote").textContent = "Sincronizzo con il telefono…";
  await browser.runtime.sendMessage({ op: "syncOn" });
  await renderSync();
  if (!granted) $("#syncNote").textContent += " Senza il permesso dei segnalibri passano solo le schede aperte.";
});

ticks($("#syncTicks"), 9);
ticks($("#pwdTicks"), 9);
ticks($("#ponteTicks"), 10);
ticks($("#protTicks"), 10);
render();
browser.runtime.sendMessage({ op: "hello" }).then((h) => statusLine($("#status"), h));
