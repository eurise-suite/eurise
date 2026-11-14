/*
 * Eurise Office: il guscio Android dei comandi della Suite (scheda Suite, «Cerca un comando»).
 * Copiato in org.libreoffice.androidlib da office/app/eurise_patch.py; LOActivity passa qui i messaggi «EURISE {json}»
 * dell'interfaccia web (office/app/web/Control.Eurise.js) e restituisce le risposte con euriseReply.
 *
 * Tutto resta sul telefono: il testo scelto va alle app Eurise firmate con la stessa chiave (permesso della suite) —
 * Da fare e Note all'app Eurise, la traduzione a Eurise Leggi, la dettatura a Eurise Voce — o a Calendario con
 * l'intent standard. Solo «Cerca» sul web o con un assistente apre una pagina esterna, e lo dice con le tacche.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.libreoffice.androidlib;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.provider.CalendarContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Locale;

final class EuriseSuite {
    private static final String TAG = "EuriseSuite";

    // Accordi della suite (core/suite: HubBridge, TranslateContract; voice: DictateActivity).
    private static final String ACTION_TASK = "eu.eurise.action.KEYBOARD_TASK";
    private static final String ACTION_NOTE = "eu.eurise.action.KEYBOARD_NOTE";
    private static final String ACTION_TRANSLATE = "eu.eurise.action.TRANSLATE";
    private static final String ACTION_LANGUAGES = "eu.eurise.action.TRANSLATE_LANGUAGES";
    private static final String ACTION_DICTATION = "eu.eurise.action.DICTATION_RESULT";
    private static final int MSG_TRANSLATE = 1;
    private static final int MSG_LANGUAGES = 2;
    private static final int MAX_CHARS = 20_000;

    private static BroadcastReceiver dictationReceiver;
    private static LOActivity dictationActivity;
    private static int dictationId;

    private EuriseSuite() {}

    /** Nomi delle app e permesso: la prova (.debug, chiave di prova) parla solo con le app di prova, e viceversa. */
    private static boolean debug(Context c) {
        return (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    private static String pkg(Context c, String base) { return debug(c) ? base + ".debug" : base; }
    private static String permission(Context c) {
        return debug(c) ? "eu.eurise.permission.SUITE_DEBUG" : "eu.eurise.permission.SUITE";
    }
    private static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }
    private static boolean it() { return Locale.getDefault().getLanguage().equals("it"); }
    private static String t(String it, String en) { return it() ? it : en; }

    /**
     * I file di configurazione del motore nascono con il pacchetto dell'app di Collabora
     * (/data/data/org.libreoffice.androidapp): fontconfig non trova la sua cache e lo scrive su stderr. Qui si rimettono
     * le cartelle vere di Office prima di avviare il motore.
     */
    static void fixPaths(Context c, String dataDir) {
        File conf = new File(dataDir, "etc/fonts/fonts.conf");
        if (!conf.isFile())
            return;
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream in = new java.io.FileInputStream(conf)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0)
                    bytes.write(buffer, 0, n);
            }
            String text = bytes.toString("UTF-8");
            String fixed = text.replace("/data/data/org.libreoffice.androidapp", dataDir);
            if (!fixed.equals(text)) {
                try (java.io.OutputStream out = new java.io.FileOutputStream(conf)) {
                    out.write(fixed.getBytes("UTF-8"));
                }
                Log.i(TAG, "fonts.conf: cartelle di Office");
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "fonts.conf non aggiornato: " + e);
        }
    }

    /**
     * Copia di lavoro di un documento arrivato come content://: stesso nome del file, in una cartella unica della cache
     * (due documenti con lo stesso nome non si pestano). Senza un nome leggibile resta il file temporaneo di Collabora.
     */
    static File workingCopy(Context c, Uri uri, String suffix) throws IOException {
        String name = null;
        try (Cursor cursor = c.getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst())
                name = cursor.getString(0);
        } catch (RuntimeException e) {
            Log.i(TAG, "nome del documento non leggibile: " + e);
        }
        if (name != null)
            name = name.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name == null || name.isEmpty() || name.startsWith("."))
            return File.createTempFile("LibreOffice", suffix, c.getCacheDir());
        if (suffix != null && !name.toLowerCase(Locale.ROOT).endsWith(suffix))
            name = name + suffix;
        File dir = new File(new File(c.getCacheDir(), "documenti"), UUID.randomUUID().toString());
        if (!dir.mkdirs())
            return File.createTempFile("LibreOffice", suffix, c.getCacheDir());
        return new File(dir, name);
    }

    static void handle(LOActivity activity, String json) {
        try {
            JSONObject m = new JSONObject(json);
            int id = m.optInt("id");
            String action = m.optString("action");
            String text = m.optString("text").trim();
            String title = m.optString("title");
            switch (action) {
                case "dafare": toHub(activity, id, ACTION_TASK, withSource(text, title), t("Aggiunto a Da fare", "Added to To-do")); break;
                case "note": toHub(activity, id, ACTION_NOTE, withSource(text, title), t("Salvato in Note", "Saved to Notes")); break;
                // Viaggio non ha ancora un ricevitore suo nell'app Eurise: le spese arrivano in Da fare, segnate.
                case "viaggio": toHub(activity, id, ACTION_TASK, t("Viaggio · ", "Trip · ") + withSource(text, title), t("In Da fare, con il segno Viaggio", "In To-do, marked Trip")); break;
                case "evento": event(activity, id, text, title); break;
                case "detta": dictate(activity, id); break;
                case "lingue": languages(activity, id); break;
                case "traduci": translate(activity, id, text, m.optString("from"), m.optString("to")); break;
                case "cerca": search(activity, id, m.optString("where"), m.optString("query")); break;
                case "pdf": pdf(activity, id, m.optString("how"), m.optString("name"), m.optString("options")); break;
                case "aspetto": appearance(activity, id); break;
                default: reply(activity, id, null, null);
            }
        } catch (JSONException e) {
            Log.w(TAG, "messaggio non valido", e);
        }
    }

    /**
     * L'accento delle icone che Office manda con il documento (Engine.kt, extra ACCENT_LIGHT e ACCENT_DARK, «#rrggbb»):
     * l'interfaccia web lo usa per i dettagli delle icone Eurise. Senza, risponde vuoto e restano quelle di base.
     */
    private static void appearance(LOActivity a, int id) {
        JSONObject r = new JSONObject();
        try {
            Intent i = a.getIntent();
            String light = i != null ? i.getStringExtra("eu.eurise.extra.ACCENT_LIGHT") : null;
            String dark = i != null ? i.getStringExtra("eu.eurise.extra.ACCENT_DARK") : null;
            if (light != null && light.matches("#[0-9A-Fa-f]{6}")) r.put("light", light);
            if (dark != null && dark.matches("#[0-9A-Fa-f]{6}")) r.put("dark", dark);
        } catch (JSONException ignored) {
        }
        reply(a, id, null, r);
    }

    private static String withSource(String text, String title) {
        if (text.isEmpty()) return title.isEmpty() ? "" : t("Dal documento «", "From the document «") + title + "»";
        return title.isEmpty() ? text : text + "\n\n" + t("Da «", "From «") + title + "»";
    }

    private static void reply(LOActivity a, int id, String notice, JSONObject extra) {
        try {
            JSONObject r = extra != null ? extra : new JSONObject();
            r.put("id", id);
            if (notice != null) r.put("notice", notice);
            a.euriseReply(r.toString());
        } catch (JSONException ignored) {
        }
    }

    // --- Da fare, Note, Viaggio: all'app Eurise (ricevitore con il permesso della suite) ---

    private static void toHub(LOActivity a, int id, String action, String text, String done) {
        String hub = pkg(a, "eu.eurise.hub");
        if (!installed(a, hub)) { reply(a, id, t("Serve l'app Eurise", "The Eurise app is needed"), null); return; }
        if (text.isEmpty()) { reply(a, id, t("Scegli prima il testo", "Select some text first"), null); return; }
        a.sendBroadcast(new Intent(action).setPackage(hub).putExtra(Intent.EXTRA_TEXT, text), permission(a));
        reply(a, id, done, null);
    }

    // --- PDF: la copia di «Prima di condividere» (tavola OffPdfCondividi) ---

    /**
     * Esporta una copia del documento in PDF con le [options] scelte (JSON del filtro di esportazione: peso, note,
     * password), poi la condivide o la passa a «Salva in…» di Eurise File. Il file aperto non cambia.
     */
    private static void pdf(LOActivity a, int id, String how, String name, String options) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                java.io.File dir = new java.io.File(a.getCacheDir(), "condividi");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("cartella");
                String safe = name.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
                if (safe.isEmpty() || safe.equals(".pdf")) safe = t("Documento.pdf", "Document.pdf");
                java.io.File out = new java.io.File(dir, safe);
                if (out.exists() && !out.delete()) Log.w(TAG, "copia vecchia non tolta");
                a.saveAs(out.toURI().toString(), "pdf", options.isEmpty() ? null : options);
                if (!out.exists() || out.length() == 0) { reply(a, id, t("Copia non riuscita", "Copy failed"), null); return; }
                Uri uri = androidx.core.content.FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", out);
                String hub = pkg(a, "eu.eurise.hub");
                Intent i;
                if ("salva".equals(how) && installed(a, hub)) {
                    i = new Intent("eu.eurise.action.SAVE_IN").setClassName(hub, "eu.eurise.core.files.FilesActivity")
                            .setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(Intent.EXTRA_TITLE, safe)
                            .putExtra("eu.eurise.extra.KIND", "office")
                            .putExtra("eu.eurise.extra.FROM", "Office");
                } else {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(android.content.ClipData.newRawUri(safe, uri));
                    i = Intent.createChooser(send, t("Condividi la copia", "Share the copy"));
                }
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.setClipData(android.content.ClipData.newRawUri(safe, uri));
                a.startActivity(i);
                reply(a, id, null, null);
            } catch (Exception e) {
                Log.w(TAG, "copia PDF", e);
                reply(a, id, t("Copia non riuscita", "Copy failed"), null);
            }
        });
    }

    // --- Evento: il modulo del calendario con titolo e descrizione già scritti ---

    private static void event(LOActivity a, int id, String text, String title) {
        String first = text.isEmpty() ? title : text.split("\n", 2)[0];
        if (first.length() > 80) first = first.substring(0, 80).trim() + "…";
        Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, first)
                .putExtra(CalendarContract.Events.DESCRIPTION, withSource(text, title));
        try { a.startActivity(i); reply(a, id, null, null); }
        catch (Exception e) { reply(a, id, t("Nessun calendario sul telefono", "No calendar on this phone"), null); }
    }

    // --- Detta: Eurise Voce trascrive sul telefono e rimanda il testo qui ---

    private static void dictate(LOActivity a, int id) {
        String voice = pkg(a, "eu.eurise.voice");
        if (!installed(a, voice)) { reply(a, id, t("Serve Eurise Voce", "Eurise Voice is needed"), null); return; }
        dictationActivity = a;
        dictationId = id;
        if (dictationReceiver == null) {
            dictationReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    LOActivity target = dictationActivity;
                    if (target == null || text == null) return;
                    try { reply(target, dictationId, null, new JSONObject().put("text", text)); }
                    catch (JSONException ignored) {}
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_DICTATION);
            Context app = a.getApplicationContext();
            if (Build.VERSION.SDK_INT >= 33)
                app.registerReceiver(dictationReceiver, filter, permission(a), null, Context.RECEIVER_EXPORTED);
            else
                app.registerReceiver(dictationReceiver, filter, permission(a), null);
        }
        Intent i = new Intent().setClassName(voice, "eu.eurise.voice.DictateActivity")
                .putExtra("reply_to", a.getPackageName())
                .putExtra("language", Locale.getDefault().getLanguage())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { a.startActivity(i); } catch (Exception e) { reply(a, id, t("Eurise Voce non risponde", "Eurise Voice did not answer"), null); }
    }

    /** Da chiamare in onDestroy dell'attività: la dettatura non deve scrivere in un documento chiuso. */
    static void detach(LOActivity a) {
        if (dictationActivity == a) dictationActivity = null;
    }

    // --- Traduci: servizio di traduzione di Eurise Leggi, sul telefono ---

    private interface Answer { void on(Bundle data); }

    private static void leggi(LOActivity a, int id, int what, Bundle data, Answer answer) {
        final String read = pkg(a, "eu.eurise.read");
        if (!installed(a, read)) { reply(a, id, t("Serve Eurise Leggi per tradurre", "Eurise Read is needed to translate"), null); return; }
        final ServiceConnection[] holder = new ServiceConnection[1];
        final Handler main = new Handler(Looper.getMainLooper());
        final Messenger replyTo = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
            if (holder[0] != null) { try { a.unbindService(holder[0]); } catch (Exception ignored) {} holder[0] = null; }
            answer.on(msg.getData());
            return true;
        }));
        holder[0] = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                Message msg = Message.obtain(null, what);
                msg.setData(data);
                msg.replyTo = replyTo;
                try { new Messenger(service).send(msg); }
                catch (Exception e) { reply(a, id, t("Traduzione non riuscita", "Translation failed"), null); }
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        boolean ok;
        try { ok = a.bindService(new Intent(ACTION_TRANSLATE).setPackage(read), holder[0], Context.BIND_AUTO_CREATE); }
        catch (Exception e) { ok = false; }
        if (!ok) { holder[0] = null; reply(a, id, t("Eurise Leggi non risponde", "Eurise Read did not answer"), null); return; }
        main.postDelayed(() -> {
            if (holder[0] != null) {
                try { a.unbindService(holder[0]); } catch (Exception ignored) {}
                holder[0] = null;
                reply(a, id, t("Traduzione troppo lenta, riprova", "Translation took too long, try again"), null);
            }
        }, 60_000);
    }

    private static void languages(LOActivity a, int id) {
        leggi(a, id, MSG_LANGUAGES, new Bundle(), data -> {
            ArrayList<String> list = data.getStringArrayList("languages");
            if (list == null || list.size() < 2) {
                openLanguages(a);
                reply(a, id, t("Scarica almeno una lingua in Eurise Leggi", "Download a language in Eurise Read first"), null);
                return;
            }
            try { reply(a, id, null, new JSONObject().put("languages", new JSONArray(list))); }
            catch (JSONException ignored) {}
        });
    }

    private static void translate(LOActivity a, int id, String text, String from, String to) {
        if (text.isEmpty() || from.equals(to)) { reply(a, id, null, null); return; }
        if (text.length() > MAX_CHARS) { reply(a, id, t("Testo troppo lungo: tradurlo a pezzi", "Text too long: translate it in parts"), null); return; }
        Bundle b = new Bundle();
        b.putString("text", text);
        b.putString("from", from);
        b.putString("to", to);
        leggi(a, id, MSG_TRANSLATE, b, data -> {
            if ("no_model".equals(data.getString("error"))) {
                openLanguages(a);
                reply(a, id, t("Manca una lingua: si scarica in Eurise Leggi", "A language is missing: download it in Eurise Read"), null);
                return;
            }
            String result = data.getString("result");
            if (result == null) { reply(a, id, t("Traduzione non riuscita", "Translation failed"), null); return; }
            try { reply(a, id, t("Tradotto sul telefono", "Translated on the phone"), new JSONObject().put("text", result)); }
            catch (JSONException ignored) {}
        });
    }

    private static void openLanguages(LOActivity a) {
        try { a.startActivity(new Intent(ACTION_LANGUAGES).setPackage(pkg(a, "eu.eurise.read")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) {}
    }

    // --- Cerca: nella suite (Scrivania), sul web o con un assistente (riquadro dell'app Eurise) ---

    private static String address(String where, String q) {
        String e = Uri.encode(q);
        switch (where) {
            case "engine:brave": return "https://search.brave.com/search?q=" + e;
            case "engine:qwant": return "https://www.qwant.com/?q=" + e;
            case "engine:ecosia": return "https://www.ecosia.org/search?q=" + e;
            case "engine:ddg": return "https://duckduckgo.com/?q=" + e;
            case "ai:duck": return "https://duck.ai/?q=" + e;
            case "ai:lechat": return "https://chat.mistral.ai/chat?q=" + e;
            default: return null;
        }
    }

    private static String label(String where) {
        switch (where) {
            case "engine:brave": return "Brave Search";
            case "engine:qwant": return "Qwant";
            case "engine:ecosia": return "Ecosia";
            case "engine:ddg": return "DuckDuckGo";
            case "ai:duck": return "Duck.ai";
            case "ai:lechat": return "Le Chat";
            default: return "";
        }
    }

    private static void search(LOActivity a, int id, String where, String query) {
        String hub = pkg(a, "eu.eurise.hub");
        if (where.equals("suite")) {
            try {
                a.startActivity(new Intent().setComponent(new ComponentName(hub, "eu.eurise.hub.LauncherActivity"))
                        .putExtra("eu.eurise.extra.SEARCH", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                reply(a, id, null, null);
            } catch (Exception e) { reply(a, id, t("Serve l'app Eurise", "The Eurise app is needed"), null); }
            return;
        }
        String url = address(where, query);
        if (url == null) { reply(a, id, t("Non ancora disponibile", "Not available yet"), null); return; }
        Intent popup = new Intent().setComponent(new ComponentName(hub, "eu.eurise.hub.web.WebPopupActivity"))
                .putExtra("eu.eurise.extra.URL", url)
                .putExtra("eu.eurise.extra.TITLE", label(where))
                .putExtra("eu.eurise.extra.QUERY", query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (where.startsWith("ai:")) popup.putExtra("eu.eurise.extra.KIND", "ai");
        try { a.startActivity(popup); reply(a, id, null, null); return; } catch (Exception ignored) {}
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); reply(a, id, null, null); }
        catch (Exception e) { reply(a, id, t("Nessun browser sul telefono", "No browser on this phone"), null); }
    }
}
