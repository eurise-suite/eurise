#!/usr/bin/env python3
"""Adatta l'app Android di Collabora Online a Eurise Office (vedi office/app/EURISE.md).

Uso: python3 office/app/eurise_patch.py <cartella android di online> <keystore di debug di Eurise>

Dopo ./configure di online: pacchetto eu.eurise.office (con .debug per la prova, come le altre app della suite),
firma con la chiave di prova della suite, guscio dei comandi della Suite (android/EuriseSuite.java agganciato a
LOActivity: messaggi «EURISE {json}» dall'interfaccia, risposte con euriseReply), permesso della suite e visibilità
delle app Eurise nel manifest. L'interfaccia web la adatta eurise_web.py prima di make. Ogni sostituzione deve
riuscire: se Collabora cambia quelle righe, lo script si ferma e lo dice.
"""
import re
import shutil
import sys
from pathlib import Path

android = Path(sys.argv[1]).resolve()
keystore = Path(sys.argv[2]).resolve()
ours = Path(__file__).resolve().parent

settings = android / "app" / "appSettings.gradle"
if not settings.exists():
    sys.exit(f"eurise_patch: manca {settings}: ./configure di online non è passato?")
s = settings.read_text()
s, n = re.subn(r"applicationId '[^']*'", "applicationId 'eu.eurise.office'", s)
if n != 1:
    sys.exit("eurise_patch: non trovo applicationId in appSettings.gradle")
settings.write_text(s)

build = android / "app" / "build.gradle"
b = build.read_text()
old = "    buildTypes {\n        debug {\n"
if "euriseDebug {" in b:
    old = None
elif old not in b:
    sys.exit("eurise_patch: non trovo buildTypes.debug in app/build.gradle")
b = b if old is None else b.replace(old, """    signingConfigs {
        euriseDebug {
            storeFile file('eurise-debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }

    buildTypes {
        debug {
            signingConfig signingConfigs.euriseDebug
""", 1)
build.write_text(b)
shutil.copy(keystore, android / "app" / "eurise-debug.keystore")



def patch(path: Path, old: str, new: str, what: str) -> None:
    text = path.read_text()
    if new in text:
        return
    if old not in text:
        sys.exit(f"eurise_patch: non trovo {what} in {path.name}")
    path.write_text(text.replace(old, new, 1))


# Guscio dei comandi della Suite.
androidlib = android / "lib" / "src" / "main" / "java" / "org" / "libreoffice" / "androidlib"
shutil.copy(ours / "android" / "EuriseSuite.java", androidlib / "EuriseSuite.java")
lo = androidlib / "LOActivity.java"
patch(lo, '            case "REQUESTFILECOPY": {\n',
      '            case "EURISE":\n'
      '                EuriseSuite.handle(this, messageAndParam.length > 1 ? messageAndParam[1] : "{}");\n'
      '                return false;\n'
      '            case "REQUESTFILECOPY": {\n', "il caso REQUESTFILECOPY")
patch(lo, '    /** True if the App is running under ChromeOS. */\n',
      '    /** Eurise: risposta del guscio all\'interfaccia web (window.euriseOffice.reply), sul thread principale. */\n'
      '    void euriseReply(final String json) {\n'
      '        getMainHandler().post(() -> {\n'
      '            if (mWebView != null)\n'
      '                mWebView.evaluateJavascript("window.euriseOffice && window.euriseOffice.reply(" + JSONObject.quote(json) + ")", null);\n'
      '        });\n'
      '    }\n\n'
      '    /** True if the App is running under ChromeOS. */\n', "il commento di isChromeOS")
patch(lo, '    protected void onDestroy() {\n',
      '    protected void onDestroy() {\n        EuriseSuite.detach(this);\n', "onDestroy")

# Manifest: permesso della suite (solo quello di prova: l'APK è firmato con la chiave di prova) e app Eurise visibili.
manifest = android / "app" / "src" / "main" / "AndroidManifest.xml"
patch(manifest, '    <uses-permission android:name="android.permission.INTERNET" />\n',
      '    <uses-permission android:name="android.permission.INTERNET" />\n'
      '    <!-- Eurise: Da fare, Note, traduzione e dettatura passano solo tra app Eurise con la stessa firma. -->\n'
      '    <permission android:name="eu.eurise.permission.SUITE_DEBUG" android:protectionLevel="signature" />\n'
      '    <uses-permission android:name="eu.eurise.permission.SUITE_DEBUG" />\n'
      '    <queries>\n'
      + "".join(f'        <package android:name="eu.eurise.{p}.debug" />\n' for p in ("hub", "read", "voice", "browser"))
      + '        <intent><action android:name="android.intent.action.INSERT" /><data android:mimeType="vnd.android.cursor.dir/event" /></intent>\n'
      '        <intent><action android:name="android.intent.action.VIEW" /><data android:scheme="https" /></intent>\n'
      '    </queries>\n', "il permesso INTERNET")

print("eurise_patch: eu.eurise.office, chiave di prova della suite, guscio della Suite")
