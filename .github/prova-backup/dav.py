#!/usr/bin/env python3
"""WebDAV finto per la prova del backup (area Backup, 21 novembre 2026; docs/stato/BACKUP.md).

Uso: python3 dav.py <cartella> <porta> <certificato.pem> <chiave.pem>

Solo quello che usa Eurise per il backup (core/cloud WebDav): PROPFIND con Depth 1, MKCOL, PUT, GET, DELETE, MOVE, con
utente «prova» e password «segreta». HTTPS con un certificato fatto al volo per 10.0.2.2 (l'indirizzo del computer visto
dall'emulatore): la sonda lo riconosce, come un server vero con il suo certificato. Se accanto alla cartella c'è il file
«guasta», ogni GET restituisce il file con un byte cambiato: così si prova che un backup che non si verifica non tocca
quelli vecchi.
"""
import base64
import email.utils
import os
import shutil
import ssl
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.abspath(sys.argv[1])
PORT = int(sys.argv[2])
AUTH = "Basic " + base64.b64encode(b"prova:segreta").decode()
BROKEN = os.path.join(os.path.dirname(ROOT), "guasta")


def local(url_path: str) -> str:
    rel = urllib.parse.unquote(urllib.parse.urlparse(url_path).path).strip("/")
    full = os.path.abspath(os.path.join(ROOT, rel))
    if full != ROOT and not full.startswith(ROOT + os.sep):
        raise PermissionError(url_path)
    return full


class Dav(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("dav: " + (fmt % args) + "\n")

    def reply(self, code: int, body: bytes = b"", ctype: str = "text/plain"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body and self.command != "HEAD":
            self.wfile.write(body)

    def body(self) -> bytes:
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n) if n else b""

    def allowed(self) -> bool:
        if self.headers.get("Authorization") == AUTH:
            return True
        self.body()
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="prova"')
        self.send_header("Content-Length", "0")
        self.end_headers()
        return False

    def entry(self, href: str, path: str) -> str:
        st = os.stat(path)
        folder = os.path.isdir(path)
        props = "<d:resourcetype>%s</d:resourcetype>" % ("<d:collection/>" if folder else "")
        if not folder:
            props += "<d:getcontentlength>%d</d:getcontentlength>" % st.st_size
            props += '<d:getetag>"%d-%d"</d:getetag>' % (st.st_mtime_ns, st.st_size)
        props += "<d:getlastmodified>%s</d:getlastmodified>" % email.utils.formatdate(st.st_mtime, usegmt=True)
        return "<d:response><d:href>%s</d:href><d:propstat><d:prop>%s</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>" % (
            urllib.parse.quote(href), props)

    def do_PROPFIND(self):
        if not self.allowed():
            return
        self.body()
        path = local(self.path)
        if not os.path.exists(path):
            return self.reply(404)
        href = urllib.parse.urlparse(self.path).path
        parts = [self.entry(href, path)]
        if os.path.isdir(path) and self.headers.get("Depth", "1") != "0":
            base = href if href.endswith("/") else href + "/"
            for name in sorted(os.listdir(path)):
                child = os.path.join(path, name)
                parts.append(self.entry(base + name + ("/" if os.path.isdir(child) else ""), child))
        xml = '<?xml version="1.0" encoding="utf-8"?><d:multistatus xmlns:d="DAV:">' + "".join(parts) + "</d:multistatus>"
        self.reply(207, xml.encode(), "application/xml; charset=utf-8")

    def do_MKCOL(self):
        if not self.allowed():
            return
        self.body()
        path = local(self.path)
        if os.path.exists(path):
            return self.reply(405)
        os.makedirs(path)
        self.reply(201)

    def do_PUT(self):
        if not self.allowed():
            return
        path = local(self.path)
        data = self.body()
        existed = os.path.exists(path)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        self.reply(204 if existed else 201)

    def do_GET(self):
        if not self.allowed():
            return
        path = local(self.path)
        if not os.path.isfile(path):
            return self.reply(404)
        with open(path, "rb") as f:
            data = bytearray(f.read())
        if os.path.exists(BROKEN) and len(data) > 100:
            data[len(data) // 2] ^= 0x55
        self.reply(200, bytes(data), "application/octet-stream")

    def do_DELETE(self):
        if not self.allowed():
            return
        self.body()
        path = local(self.path)
        if not os.path.exists(path):
            return self.reply(404)
        shutil.rmtree(path) if os.path.isdir(path) else os.remove(path)
        self.reply(204)

    def do_MOVE(self):
        if not self.allowed():
            return
        self.body()
        src = local(self.path)
        dst = local(self.headers.get("Destination", ""))
        if not os.path.exists(src):
            return self.reply(404)
        existed = os.path.exists(dst)
        if existed and self.headers.get("Overwrite", "T") == "F":
            return self.reply(412)
        os.replace(src, dst)
        self.reply(204 if existed else 201)


os.makedirs(ROOT, exist_ok=True)
server = ThreadingHTTPServer(("0.0.0.0", PORT), Dav)
ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
ctx.load_cert_chain(sys.argv[3], sys.argv[4])
server.socket = ctx.wrap_socket(server.socket, server_side=True)
print(f"dav: in ascolto su {PORT}, cartella {ROOT}", flush=True)
server.serve_forever()
