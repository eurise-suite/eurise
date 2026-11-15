"use strict";
const $ = (s) => document.querySelector(s);
const DEFAULTS = { theme: "auto", passwords: false, menus: true, protection: true };

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
  $("#protection").setAttribute("aria-checked", String(s.protection));
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
$("#pwdYes").addEventListener("click", () => { $("#pwdExplain").close(); save({ passwords: true }); });
$("#pwdNo").addEventListener("click", () => $("#pwdExplain").close());

ticks($("#pwdTicks"), 9);
ticks($("#ponteTicks"), 10);
ticks($("#protTicks"), 10);
render();
browser.runtime.sendMessage({ op: "hello" }).then((h) => statusLine($("#status"), h));
