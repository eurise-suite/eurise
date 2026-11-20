"""Aggiunge a verification-metadata.xml le impronte dei file che Gradle chiede solo in verifica (librerie-verificate.yml).

Uso: python3 impronte_mancanti.py <verification-metadata.xml> <log della verifica strict>

Gradle, scrivendo le impronte, a volte non registra un file che poi chiede verificando (per esempio il `.module` di una
BOM). Qui si leggono le righe «failed verification: FILE (gruppo:nome:versione)», si scarica il file da Maven Central,
se ne calcola l'SHA-256 e lo si aggiunge al componente. Come il resto delle impronte, vale la prima volta che si vede
(chi aggiorna una libreria lo rivede nel commit).
"""
import hashlib
import re
import sys
import urllib.request

RIGA = re.compile(r"failed verification: (\S+) \(([^:()\s]+):([^:()\s]+):([^)\s]+)\)")


def sha256(url):
    req = urllib.request.Request(url, headers={"User-Agent": "eurise-impronte"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return hashlib.sha256(r.read()).hexdigest()


def main(meta_path, log_path):
    meta = open(meta_path, encoding="utf-8").read()
    mancanti = sorted(set(RIGA.findall(open(log_path, encoding="utf-8", errors="replace").read())))
    aggiunti = 0
    for nome_file, gruppo, nome, versione in mancanti:
        url = "https://repo1.maven.org/maven2/%s/%s/%s/%s" % (gruppo.replace(".", "/"), nome, versione, nome_file)
        try:
            impronta = sha256(url)
        except Exception as e:  # noqa: BLE001 — si riferisce e si va avanti
            print("non scaricato %s: %s" % (url, e))
            continue
        artefatto = (
            '         <artifact name="%s">\n'
            '            <sha256 value="%s" origin="Aggiunto da librerie-verificate (chiesto solo in verifica)"/>\n'
            "         </artifact>\n" % (nome_file, impronta)
        )
        testa = '<component group="%s" name="%s" version="%s">\n' % (gruppo, nome, versione)
        if testa in meta:
            i = meta.index(testa) + len(testa)
            if ('<artifact name="%s">' % nome_file) in meta[i:meta.index("</component>", i)]:
                print("già presente: %s" % nome_file)
                continue
            meta = meta[:i] + artefatto + meta[i:]
        else:
            componente = "      " + testa + artefatto + "      </component>\n"
            meta = meta.replace("   </components>", componente + "   </components>", 1)
        aggiunti += 1
        print("aggiunto %s:%s:%s %s %s" % (gruppo, nome, versione, nome_file, impronta))
    open(meta_path, "w", encoding="utf-8").write(meta)
    print("impronte aggiunte: %d" % aggiunti)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
