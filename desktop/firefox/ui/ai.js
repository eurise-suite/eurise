/*
 * Le AI per «Chiedi all'AI» della nuova scheda: le stesse del Browser Eurise sul telefono (Settings.AIS), nello stesso
 * ordine. Le europee e le anonime prima; i servizi chiusi in fondo con due tacchette su dieci (CLAUDE.md, 12 novembre).
 * Nessuna è accesa di base: la domanda esce solo verso il servizio scelto e solo quando la si fa.
 */
const AIS = [
  { id: "lechat", name: "Le Chat", sub: "Mistral · Francia, UE · conserva le conversazioni", url: "https://chat.mistral.ai/chat?q=%s", ticks: 5 },
  { id: "duck", name: "Duck.ai", sub: "DuckDuckGo · USA, anonima: i modelli non sanno chi sei", url: "https://duckduckgo.com/?q=%s&ia=chat", ticks: 6 },
  { id: "claude", name: "Claude", sub: "Anthropic · USA · addestra sulle chat se non lo spegni; le tiene fino a 5 anni", url: "https://claude.ai/new?q=%s", ticks: 2, closed: true },
  { id: "chatgpt", name: "ChatGPT", sub: "OpenAI · USA · conserva le conversazioni", url: "https://chatgpt.com/?q=%s", ticks: 2, closed: true },
  { id: "copilot", name: "Copilot", sub: "Microsoft · USA · conserva le conversazioni", url: "https://copilot.microsoft.com/?q=%s", ticks: 2, closed: true },
  { id: "gemini", name: "Gemini", sub: "Google · USA · conserva le conversazioni", url: "https://gemini.google.com/app?q=%s", ticks: 2, closed: true },
];
function aiById(id) { return AIS.find((a) => a.id === id) || null; }

/** Le 10 tacchette della privacy: verdi da 7, gialle da 4, rosse sotto. */
function ticksHtml(n) {
  const cls = n >= 7 ? "g" : n >= 4 ? "y" : "r";
  return `<span class="ticks" title="${n} su 10">${Array.from({ length: 10 }, (_, i) => `<i class="${i < n ? cls : ""}"></i>`).join("")}</span>`;
}
