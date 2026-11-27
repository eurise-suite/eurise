"""Centro di un elemento della schermata (testo o descrizione) da un dump di uiautomator: «primo» = più in alto."""
import re
import sys

xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
testo, quale = sys.argv[2], sys.argv[3]
trovati = []
for m in re.finditer(r"<node [^>]*>", xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == testo) or (d and d.group(1) == testo):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            trovati.append(((y1 + y2) // 2, (x1 + x2) // 2))
trovati.sort()
if trovati:
    y, x = trovati[0] if quale == "primo" else trovati[-1]
    print(x, y)
