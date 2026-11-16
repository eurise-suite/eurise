/* Le icone della suite (stesso tratto di EuriseIcons), per popup e pannello. */
const ICONS = {
  phone: "M8 3 h8 a1.5 1.5 0 0 1 1.5 1.5 v15 A1.5 1.5 0 0 1 16 21 H8 a1.5 1.5 0 0 1 -1.5-1.5 v-15 A1.5 1.5 0 0 1 8 3 z M11 18 h2",
  note: "M6.5 3.5 h8 l4 4 v11 a2 2 0 0 1 -2 2 h-10 a2 2 0 0 1 -2-2 v-13 a2 2 0 0 1 2-2 z M8 11 h8 M8 14.5 h8",
  task: "M4 6.5 l2 2 3.5-3.5 M12 7 h8 M4 13.5 l2 2 3.5-3.5 M12 14 h8 M12 19.5 h8",
  calendar: "M6 5 h12 a2 2 0 0 1 2 2 v11 a2 2 0 0 1 -2 2 H6 a2 2 0 0 1 -2-2 V7 a2 2 0 0 1 2-2 z M4 10 h16 M8.5 3 v4 M15.5 3 v4",
  file: "M6.5 3.5 h7 l5 5 v10 a2 2 0 0 1 -2 2 h-10 a2 2 0 0 1 -2-2 v-13 a2 2 0 0 1 2-2 z M13.5 3.5 v5 h5",
  copy: "M9 4 h6 v3 H9 z M7 5.5 H5.5 V20 h13 V5.5 H17",
  tabs: "M4 7 h11 v11 H4 z M8 4 h12 v11",
  bell: "M6 16 V11 a6 6 0 0 1 12 0 v5 l1.5 2 h-15 z M10 20.5 h4",
  mic: "M9 4.6 a3 3 0 0 1 6 0 V11 a3 3 0 0 1 -6 0 Z M5.5 11 a6.5 6.5 0 0 0 13 0 M12 17.5 V21",
  later: "M6 3.5h12v17l-6-4-6 4z",
  side: "M4 5 h16 v14 H4 z M14 5 v14",
  gear: "M12 9 a3 3 0 1 0 0 6 a3 3 0 1 0 0 -6 M12 3 v2.5 M12 18.5 V21 M3 12 h2.5 M18.5 12 H21 M5.6 5.6 l1.8 1.8 M16.6 16.6 l1.8 1.8 M5.6 18.4 l1.8-1.8 M16.6 7.4 l1.8-1.8",
};
function icon(name) {
  return `<span class="dot"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICONS[name] || ICONS.note}"/></svg></span>`;
}
function esc(s) { return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])); }

/** Lo stato del Ponte in una riga: collegato, in coda, non acceso, non installato. */
function statusLine(el, h) {
  el.className = "status";
  if (!h || h.missing) { el.classList.add("off"); el.innerHTML = "<i></i>Ponte Eurise non installato · vedi Impostazioni"; return; }
  if (!h.devices.length) { el.classList.add("off"); el.innerHTML = "<i></i>Nessun telefono collegato · eurise-ponte collega"; return; }
  if (!h.running) { el.classList.add("wait"); el.innerHTML = "<i></i>Ponte spento · quello che mandi resta in coda"; return; }
  if (h.live && h.live.length) { el.classList.add("on"); el.innerHTML = "<i></i>" + esc(h.live.join(", ")) + " collegato"; return; }
  el.classList.add("wait"); el.innerHTML = "<i></i>" + esc(h.devices.join(", ")) + " · fuori dalla rete di casa: in coda";
}
