package eu.eurise.prova.backup;

import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prova vera del backup (area Backup, 21 novembre 2026). Gira dentro l'app Eurise di prova (stessa chiave) e usa il
 * codice vero del backup, per riflessione: niente librerie di test. Tre fasi, chiamate da
 * .github/scripts/prova-backup-emulatore.sh con «-e fase …»:
 * - backup: semina note, attività e un'impostazione, fa il backup cifrato a pezzi (con Tastiera, Password e Galleria),
 *   lo lascia in cache/prova.eurb e controlla che nei file dell'app non resti niente in chiaro;
 * - ripristino: a dati cancellati e senza Tastiera, la frase sbagliata non apre, il codice di recupero sì; il
 *   contenuto è quello giusto; poi prepara il ripristino, che prende il suo posto al riavvio di Eurise;
 * - riavvio: dopo che Eurise è ripartita davvero, note, attività e impostazione sono tornate;
 * - consegna: con la Tastiera di nuovo installata, i dati in attesa (sigillati) arrivano a Tastiera, Password e Galleria;
 * - gestore-*: il backup come lo fa l'app (BackupManager), sul telefono «ripristinato»: chiave nel Keystore, backup
 *   automatico con Wi-Fi e carica verso una cartella vera di Android ({@link Cartella}), poi verso un WebDAV finto
 *   (.github/prova-backup/dav.py) con la regola dei cinque conservati, un backup che non si verifica, lo spegnimento.
 */
public class Sonda extends Instrumentation {
    private static final String FRASE = "girasole lento sotto la pioggia 1987";
    private static final String CODICE = "K7QMR2XAHPN9DW4TZB6SMC3E";
    private static final String PKG = "eu.eurise.core.cloud.backup.";
    private static final String[] APPS = {"keyboard", "password", "gallery", "messages", "mail", "office", "browser"};

    private String fase;
    private final StringBuilder log = new StringBuilder();

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        fase = arguments.getString("fase", "");
        start();
    }

    @Override
    public void onStart() {
        Bundle out = new Bundle();
        try {
            Context ctx = getTargetContext();
            if ("backup".equals(fase)) backup(ctx);
            else if ("ripristino".equals(fase)) ripristino(ctx);
            else if ("riavvio".equals(fase)) riavvio(ctx);
            else if ("consegna".equals(fase)) consegna(ctx);
            else if ("gestore-inizio".equals(fase)) gestoreInizio(ctx);
            else if ("gestore-cartella".equals(fase)) gestoreCartella(ctx);
            else if ("gestore-dav".equals(fase)) gestoreDav(ctx);
            else if ("gestore-guasto".equals(fase)) gestoreGuasto(ctx);
            else if ("gestore-fine".equals(fase)) gestoreFine(ctx);
            else throw new IllegalStateException("fase sconosciuta: " + fase);
            out.putString("esito", "ok");
        } catch (Throwable t) {
            Throwable c = t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            out.putString("esito", "ERRORE " + c.getClass().getSimpleName() + ": " + c.getMessage());
        }
        out.putString("dettagli", log.toString().replace('\n', '|'));
        finish(0, out);
    }

    private void note(String s) { log.append(s).append('\n'); }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    // ---------------------------------------------------------------- fasi

    private void backup(Context ctx) throws Exception {
        File db = ctx.getDatabasePath("eurise.db");
        check(db.exists(), "il database di Eurise non c'è (l'app non è partita?)");
        SQLiteDatabase d = SQLiteDatabase.openDatabase(db.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            for (int i = 1; i <= 3; i++) insert(d, "notes", "Prova backup " + i);
            for (int i = 1; i <= 2; i++) insert(d, "tasks", "Prova backup " + i);
        } finally {
            d.close();
        }
        SharedPreferences p = ctx.getSharedPreferences("eurise_prova", Context.MODE_PRIVATE);
        check(p.edit().putString("segno", "42").commit(), "impostazione di prova non scritta");

        Object phone = call(obj("BackupArchive"), "onPhone", ctx);
        note("sul telefono: " + get(phone, "getNotes") + " note, " + get(phone, "getTasks") + " attività");
        check(!(Boolean) get(phone, "getEmpty"), "il telefono risulta vuoto dopo la semina");

        long now = System.currentTimeMillis();
        Object keys = call(obj("BackupCrypto"), "setup", FRASE.toCharArray(), CODICE, 600_000);
        // Il formato a pezzi (versione 2), come lo fa il backup vero: lo zip si cifra mentre lo si scrive.
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        java.io.OutputStream enc = (java.io.OutputStream) call(obj("BackupCrypto"), "encryptingStream", keys, now, sink);
        call(obj("BackupArchive"), "create", ctx, now, enc);
        enc.close();
        byte[] file = sink.toByteArray();
        check(startsWith(file, "EURISEB1") && file[8] == 2, "il file non è un backup EURISEB1 a pezzi (versione 2)");
        check(indexOf(file, "Prova backup".getBytes("UTF-8")) < 0, "nel file cifrato si legge «Prova backup»");
        check(indexOf(file, "finta-cassaforte".getBytes("UTF-8")) < 0, "nel file cifrato si legge la cassaforte di prova");
        note("backup: " + file.length + " byte, a pezzi");

        Object contents = contentsOf(file, FRASE);
        Object apps = get(contents, "getApps");
        note("contenuto: note=" + get(contents, "getNotes") + " attività=" + get(contents, "getTasks") + " app=" + apps);
        check(Integer.valueOf(3).equals(get(contents, "getNotes")), "il manifesto non conta 3 note");
        check(Integer.valueOf(2).equals(get(contents, "getTasks")), "il manifesto non conta 2 attività");
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) apps;
        for (String k : APPS) check(map.containsKey(k), "nel backup manca l'app " + k);
        check(String.valueOf(map.get("password")).contains("cassaforte"), "la riga di Password non dice della cassaforte");

        try (FileOutputStream o = new FileOutputStream(new File(ctx.getCacheDir(), "prova.eurb"))) { o.write(file); }
        List<String> plain = plainZips(new File(ctx.getApplicationInfo().dataDir));
        check(plain.isEmpty(), "zip in chiaro nei file dell'app: " + plain);
        note("nessuno zip in chiaro nei file di Eurise");
    }

    private void ripristino(Context ctx) throws Exception {
        File in = new File(ctx.getCacheDir(), "prova.eurb");
        check(in.exists(), "manca cache/prova.eurb");
        byte[] file = readAll(in);
        in.delete();
        Object header = call(obj("BackupCrypto"), "readHeader", new java.io.ByteArrayInputStream(file));
        Object keyHeader = get(header, "getHeader");

        try {
            call(obj("BackupCrypto"), "unlock", keyHeader, "frase sbagliata qualsiasi");
            throw new AssertionError("la frase sbagliata ha aperto il backup");
        } catch (InvocationTargetException e) {
            check(e.getCause() != null && e.getCause().getClass().getSimpleName().equals("WrongSecret"), "frase sbagliata: errore inatteso " + e.getCause());
            note("frase sbagliata: rifiutata");
        }
        // Con il codice di recupero scritto come lo si copia da carta: minuscolo, a gruppi.
        byte[] key = (byte[]) call(obj("BackupCrypto"), "unlock", keyHeader, "k7qm-r2xa-hpn9-dw4t-zb6s-mc3e");
        byte[] keyPhrase = (byte[]) call(obj("BackupCrypto"), "unlock", keyHeader, FRASE);
        check(java.util.Arrays.equals(key, keyPhrase), "frase e codice non aprono la stessa chiave");
        note("aperto con il codice di recupero e con la frase");

        Object contents = contentsOf(file, "K7QM R2XA HPN9 DW4T ZB6S MC3E");
        check(Integer.valueOf(3).equals(get(contents, "getNotes")), "contenuto: non 3 note");
        Object phone = call(obj("BackupArchive"), "onPhone", ctx);
        check((Boolean) get(phone, "getEmpty"), "dopo la cancellazione il telefono non risulta vuoto");
        note("telefono vuoto prima del ripristino");

        java.io.ByteArrayInputStream in2 = new java.io.ByteArrayInputStream(file);
        Object h2 = call(obj("BackupCrypto"), "readHeader", in2);
        java.io.InputStream chiaro = (java.io.InputStream) call(obj("BackupCrypto"), "decryptingStream", in2, h2, key);
        call(obj("BackupArchive"), "stage", ctx, chiaro);
        // Le app separate aspettano i loro dati, sigillati (non zip leggibili), finché non li si consegna.
        java.util.List<?> waiting = (java.util.List<?>) call(obj("SuiteApps"), "waiting", ctx);
        note("in attesa: " + waiting);
        for (String k : APPS) {
            check(waiting.contains(k), "i dati di " + k + " non sono in attesa");
            File sealed = new File(new File(ctx.getFilesDir(), "apps_waiting"), k + ".sealed");
            check(sealed.exists() && !startsWith(readAll(sealed), "PK"), "i dati di " + k + " in attesa non sono sigillati");
        }
        check(!new File(new File(ctx.getFilesDir(), "restore_pending"), "apps").exists(), "dati delle app nella cartella d'attesa in chiaro");
        note("dati delle app in attesa, sigillati: " + waiting.size());
        check((Boolean) call(obj("BackupArchive"), "hasPending", ctx), "il ripristino non aspetta il riavvio");
        note("ripristino pronto: prende il suo posto quando Eurise riparte");
    }

    /** Dopo che Eurise è ripartita davvero (la prova la apre e la chiude): il ripristino ha preso il suo posto. */
    private void riavvio(Context ctx) throws Exception {
        check(!new File(ctx.getFilesDir(), "restore_pending").exists(), "la cartella d'attesa del ripristino è rimasta");
        SQLiteDatabase d = SQLiteDatabase.openDatabase(ctx.getDatabasePath("eurise.db").getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            check(count(d, "SELECT COUNT(*) FROM notes WHERE title LIKE 'Prova backup%'") == 3, "dopo il ripristino non ci sono 3 note");
            check(count(d, "SELECT COUNT(*) FROM tasks WHERE title LIKE 'Prova backup%'") == 2, "dopo il ripristino non ci sono 2 attività");
        } finally {
            d.close();
        }
        check("42".equals(ctx.getSharedPreferences("eurise_prova", Context.MODE_PRIVATE).getString("segno", null)), "l'impostazione di prova non è tornata");
        check(ctx.getSharedPreferences("backup", Context.MODE_PRIVATE).getLong("restored_at", 0) > 0, "manca il segno del ripristino (Bentornato)");
        note("note, attività e impostazione tornate; Bentornato segnato");
        List<String> plain = plainZips(new File(ctx.getApplicationInfo().dataDir));
        check(plain.isEmpty(), "zip in chiaro dopo il ripristino: " + plain);
    }

    private void consegna(Context ctx) throws Exception {
        check(!((java.util.List<?>) call(obj("SuiteApps"), "waiting", ctx)).isEmpty(), "prima della consegna non c'è niente in attesa");
        call(obj("SuiteApps"), "deliverPending", ctx);
        java.util.List<?> left = (java.util.List<?>) call(obj("SuiteApps"), "waiting", ctx);
        // Messaggi non è l'app SMS predefinita sull'emulatore: i suoi dati devono restare in attesa, sigillati.
        check(left.size() == 1 && left.contains("messages"), "dopo la consegna aspetta: " + left + " (doveva restare solo messages)");
        File sealed = new File(new File(ctx.getFilesDir(), "apps_waiting"), "messages.sealed");
        check(sealed.exists() && !startsWith(readAll(sealed), "PK"), "i dati di Messaggi in attesa non sono sigillati");
        note("consegnati a tutte; Messaggi aspetta di essere l'app SMS predefinita");
    }

    // ---------------------------------------------------------------- il backup come lo fa l'app

    private static final String DAV = "https://10.0.2.2:8443/";

    private Object manager(Context ctx) throws Exception {
        Object companion = Class.forName(PKG + "BackupManager", true, getTargetContext().getClassLoader()).getField("Companion").get(null);
        return call(companion, "get", ctx);
    }

    private Object state(Object manager) throws Exception {
        return get(get(manager, "getState"), "getValue");
    }

    private Object tree() {
        return android.provider.DocumentsContract.buildTreeDocumentUri("eu.eurise.prova.backup.cartella", "cartella").toString();
    }

    private Object folderPlace() throws Exception {
        return Class.forName(PKG + "BackupPlace$Folder", true, getTargetContext().getClassLoader()).getConstructor(String.class).newInstance(tree());
    }

    /** Il lavoro pianificato del backup, come lo vede WorkManager. */
    private Object work(Context ctx) throws Exception {
        ClassLoader cl = getTargetContext().getClassLoader();
        Class<?> wm = Class.forName("androidx.work.WorkManager", true, cl);
        Object m = wm.getMethod("getInstance", Context.class).invoke(null, ctx);
        Object future = wm.getMethod("getWorkInfosForUniqueWork", String.class).invoke(m, "eurise-backup");
        List<?> infos = (List<?>) java.util.concurrent.Future.class.getMethod("get").invoke(future);
        return infos.isEmpty() ? null : infos.get(infos.size() - 1);
    }

    private void gestoreInizio(Context ctx) throws Exception {
        Object m = manager(ctx);
        check(!(Boolean) get(state(m), "getConfigured"), "il backup risulta già acceso");
        scall(m, "setup", FRASE, CODICE);
        check((Boolean) get(state(m), "getConfigured"), "dopo l'accensione il backup non risulta acceso");
        // La chiave dei dati sta chiusa da una chiave del Keystore, che non si può leggere.
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        check(ks.containsAlias("eurise-backup"), "nel Keystore manca la chiave del backup");
        check(((javax.crypto.SecretKey) ks.getKey("eurise-backup", null)).getEncoded() == null, "la chiave del Keystore si può leggere");
        String sealed = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE).getString("key", "");
        check(sealed.contains(":") && android.util.Base64.decode(sealed.substring(sealed.indexOf(':') + 1), 0).length == 48, "la chiave dei dati non è sigillata (AES-GCM)");
        note("chiave dei dati sigillata dal Keystore (non leggibile)");

        // La cartella concessa come dal selettore.
        android.net.Uri treeUri = android.net.Uri.parse((String) tree());
        int granted = ctx.checkUriPermission(treeUri, android.os.Process.myPid(), android.os.Process.myUid(), android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        note("permesso sulla cartella prima di sceglierla: " + (granted == 0 ? "sì" : "no"));
        call(m, "setPlace", folderPlace());
        Object daily = Class.forName(PKG + "BackupFrequency", true, getTargetContext().getClassLoader()).getField("Daily").get(null);
        call(m, "setFrequency", daily);

        Object info = work(ctx);
        check(info != null, "il backup automatico non è in coda");
        Object constraints = get(info, "getConstraints");
        String state = String.valueOf(get(info, "getState"));
        String network = String.valueOf(get(constraints, "getRequiredNetworkType"));
        boolean charging = (Boolean) get(constraints, "requiresCharging");
        note("backup automatico: " + state + ", rete " + network + ", in carica " + charging);
        check("ENQUEUED".equals(state), "il backup automatico non è in attesa: " + state);
        check("UNMETERED".equals(network) && charging, "il backup automatico non chiede Wi-Fi e carica");

        // Il permesso sulla cartella deve restare anche dopo un riavvio del telefono (persistente).
        boolean kept = false;
        for (android.content.UriPermission u : ctx.getContentResolver().getPersistedUriPermissions())
            kept |= u.getUri().equals(treeUri) && u.isWritePermission();
        if (!kept) {
            try {
                ctx.getContentResolver().takePersistableUriPermission(treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                note("permesso persistente preso solo al secondo tentativo");
            } catch (Exception e) {
                note("permesso persistente rifiutato: " + e.getMessage());
            }
        }
        check(kept, "il permesso sulla cartella non è rimasto (persistente)");
    }

    private void gestoreCartella(Context ctx) throws Exception {
        Object m = manager(ctx);
        Object s = state(m);
        check(get(s, "getLastAt") != null && get(s, "getLastError") == null, "il backup automatico non risulta fatto: " + get(s, "getLastError"));
        List<?> files = (List<?>) scall(m, "list", folderPlace());
        note("nella cartella: " + files.size() + " backup");
        check(files.size() == 1, "nella cartella non c'è un backup solo");
        String name = (String) get(files.get(0), "getName");
        check(name.endsWith("-r.eurisebk"), "il nome non è quello di un backup recente: " + name);
        Object opened = scall(m, "open", folderPlace(), name, FRASE);
        Object contents = get(opened, "getContents");
        check(Integer.valueOf(3).equals(get(contents, "getNotes")), "il backup della cartella non ha le 3 note");
        call(m, "discard", opened);
        note("dalla cartella si riapre con la frase: 3 note");
    }

    /** Un client HTTPS che riconosce il certificato del WebDAV finto (cache/prova-dav.pem, messo dalla prova). */
    private Object davTarget(Context ctx) throws Exception {
        ClassLoader cl = getTargetContext().getClassLoader();
        java.security.cert.Certificate cert;
        try (InputStream in = new FileInputStream(new File(ctx.getCacheDir(), "prova-dav.pem"))) {
            cert = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        java.security.KeyStore ks = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("prova", cert);
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        javax.net.ssl.X509TrustManager tm = (javax.net.ssl.X509TrustManager) tmf.getTrustManagers()[0];
        javax.net.ssl.SSLContext tls = javax.net.ssl.SSLContext.getInstance("TLS");
        tls.init(null, new javax.net.ssl.TrustManager[]{tm}, null);
        Class<?> builderClass = Class.forName("okhttp3.OkHttpClient$Builder", true, cl);
        Object builder = builderClass.getConstructor().newInstance();
        builderClass.getMethod("sslSocketFactory", javax.net.ssl.SSLSocketFactory.class, javax.net.ssl.X509TrustManager.class).invoke(builder, tls.getSocketFactory(), tm);
        Object client = builderClass.getMethod("build").invoke(builder);
        Class<?> davClass = Class.forName("eu.eurise.core.cloud.WebDav", true, cl);
        Object dav = davClass.getConstructor(String.class, String.class, String.class, Class.forName("okhttp3.OkHttpClient", true, cl))
            .newInstance(DAV, "prova", "segreta", client);
        return Class.forName(PKG + "DavTarget", true, cl).getConstructor(davClass, String.class).newInstance(dav, "Eurise/Backup");
    }

    private void gestoreDav(Context ctx) throws Exception {
        Object m = manager(ctx);
        Object target = davTarget(ctx);
        Object file = scall(m, "backupTo", target);
        String name = (String) get(file, "getName");
        note("su WebDAV: " + name);
        // Riscaricato e aperto con la frase: la chiave dei dati è quella che il Keystore tiene sigillata.
        File back = new File(ctx.getCacheDir(), "prova-dav.eurb");
        check((Boolean) scall(target, "readTo", name, back), "il backup non si riscarica dal WebDAV");
        byte[] bytes = readAll(back);
        back.delete();
        Object header = call(obj("BackupCrypto"), "readHeader", new java.io.ByteArrayInputStream(bytes));
        byte[] key = (byte[]) call(obj("BackupCrypto"), "unlock", get(header, "getHeader"), FRASE);
        check(Integer.valueOf(3).equals(get(contentsOf(bytes, FRASE), "getNotes")), "il backup su WebDAV non ha le 3 note");
        String prefs = new String(readAll(new File(new File(ctx.getApplicationInfo().dataDir, "shared_prefs"), "backup.xml")), "UTF-8");
        check(!prefs.contains(android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)), "la chiave dei dati è in chiaro nelle impostazioni");
        note("riaperto con la frase; la chiave dei dati non è in chiaro sul telefono");
    }

    private void gestoreGuasto(Context ctx) throws Exception {
        Object m = manager(ctx);
        try {
            scall(m, "backupTo", davTarget(ctx));
            throw new AssertionError("un backup che non si verifica è stato dato per buono");
        } catch (Exception e) {
            Throwable c = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            if (c instanceof AssertionError) throw (AssertionError) c;
            note("backup non verificato: " + c.getClass().getSimpleName());
        }
        check(get(state(m), "getLastError") != null, "l'errore non è segnato (la Scrivania non lo direbbe)");
    }

    private void gestoreFine(Context ctx) throws Exception {
        Object m = manager(ctx);
        call(m, "turnOff");
        check(!(Boolean) get(state(m), "getConfigured"), "il backup risulta ancora acceso");
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        check(!ks.containsAlias("eurise-backup"), "spento il backup, la chiave resta nel Keystore");
        Object info = work(ctx);
        String state = info == null ? "nessuno" : String.valueOf(get(info, "getState"));
        check(info == null || "CANCELLED".equals(state), "spento il backup, il lavoro automatico resta: " + state);
        note("spento: chiave tolta dal Keystore, lavoro automatico " + state);
    }

    /**
     * Una funzione «suspend» di Kotlin chiamata da qui: le si passa una Continuation fatta al volo e, se si sospende, si
     * aspetta che riprenda. Il risultato si toglie da kotlin.Result; un errore torna come eccezione.
     */
    private static Object scall(Object target, String name, Object... args) throws Exception {
        ClassLoader cl = target.getClass().getClassLoader();
        Class<?> contType = Class.forName("kotlin.coroutines.Continuation", true, cl);
        Object empty = Class.forName("kotlin.coroutines.EmptyCoroutineContext", true, cl).getField("INSTANCE").get(null);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Object[] box = new Object[1];
        Object cont = java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{contType}, (proxy, method, a) -> {
            switch (method.getName()) {
                case "getContext": return empty;
                case "resumeWith": box[0] = a[0]; done.countDown(); return null;
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                default: return "sonda";
            }
        });
        StringBuilder seen = new StringBuilder();
        for (Method m : target.getClass().getMethods()) {
            // Kotlin cambia il nome: «nome$modulo» per le funzioni internal, «nome-xxxx» per quelle che restituiscono Result.
            String n = m.getName();
            if (n.startsWith(name)) seen.append(' ').append(n).append('/').append(m.getParameterTypes().length);
            if (!(n.equals(name) || n.startsWith(name + "$") || n.startsWith(name + "-")) || n.endsWith("$default")) continue;
            Class<?>[] types = m.getParameterTypes();
            if (types.length != args.length + 1 || types[args.length] != contType) continue;
            boolean fits = true;
            for (int i = 0; i < args.length && fits; i++) fits = args[i] == null || box(types[i]).isInstance(args[i]);
            if (!fits) continue;
            Object[] full = java.util.Arrays.copyOf(args, args.length + 1);
            full[args.length] = cont;
            Object r = m.invoke(target, full);
            if (r != null && r.getClass().getName().equals("kotlin.coroutines.intrinsics.CoroutineSingletons") && "COROUTINE_SUSPENDED".equals(r.toString())) {
                check(done.await(10, java.util.concurrent.TimeUnit.MINUTES), name + ": non finisce");
                r = box[0];
            }
            return unwrap(r);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length + " (suspend); ci sono:" + seen);
    }

    /** Toglie kotlin.Result (anche annidato, come per Result<T> restituito da una funzione suspend). */
    private static Object unwrap(Object r) throws Exception {
        while (r != null) {
            String n = r.getClass().getName();
            if (n.equals("kotlin.Result$Failure")) {
                java.lang.reflect.Field f = r.getClass().getDeclaredField("exception");
                f.setAccessible(true);
                Throwable t = (Throwable) f.get(r);
                if (t instanceof Exception) throw (Exception) t;
                throw new RuntimeException(t);
            }
            if (!n.equals("kotlin.Result")) return r;
            java.lang.reflect.Field f = r.getClass().getDeclaredField("value");
            f.setAccessible(true);
            r = f.get(r);
        }
        return null;
    }

    /** Il contenuto di un backup cifrato, letto a pezzi come fa l'app. */
    private Object contentsOf(byte[] file, String secret) throws Exception {
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(file);
        Object header = call(obj("BackupCrypto"), "readHeader", in);
        byte[] key = (byte[]) call(obj("BackupCrypto"), "unlock", get(header, "getHeader"), secret);
        java.io.InputStream plain = (java.io.InputStream) call(obj("BackupCrypto"), "decryptingStream", in, header, key);
        return call(obj("BackupArchive"), "contents", plain, (Long) get(header, "getCreatedAt"));
    }

    // ---------------------------------------------------------------- aiuti

    /** Una riga con i soli campi obbligatori riempiti e il titolo; l'identificativo se è testo. */
    private static void insert(SQLiteDatabase d, String table, String title) {
        ContentValues v = new ContentValues();
        try (Cursor c = d.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (c.moveToNext()) {
                String name = c.getString(1);
                String type = c.getString(2) == null ? "" : c.getString(2).toUpperCase();
                boolean notNull = c.getInt(3) == 1;
                boolean hasDefault = !c.isNull(4);
                boolean pk = c.getInt(5) > 0;
                if (name.equals("title")) { v.put(name, title); continue; }
                if (pk && type.contains("INT")) continue;
                if (pk) { v.put(name, UUID.randomUUID().toString()); continue; }
                if (!notNull || hasDefault) continue;
                if (type.contains("INT")) v.put(name, name.endsWith("_at") ? System.currentTimeMillis() : 0L);
                else if (type.contains("REAL")) v.put(name, 0.0);
                else if (type.contains("BLOB")) v.put(name, new byte[0]);
                else v.put(name, "");
            }
        }
        d.insertOrThrow(table, null, v);
    }

    private static int count(SQLiteDatabase d, String sql) {
        try (Cursor c = d.rawQuery(sql, null)) { c.moveToFirst(); return c.getInt(0); }
    }

    /** I file che cominciano come uno zip (PK\3\4) sotto [dir]: nel backup non ne deve restare nessuno. */
    private static List<String> plainZips(File dir) {
        List<String> found = new ArrayList<>();
        File[] list = dir.listFiles();
        if (list == null) return found;
        for (File f : list) {
            if (f.isDirectory()) { found.addAll(plainZips(f)); continue; }
            try (InputStream in = new FileInputStream(f)) {
                byte[] b = new byte[4];
                if (in.read(b) == 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) found.add(f.getPath());
            } catch (Exception ignored) {
            }
        }
        return found;
    }

    private Object obj(String name) throws Exception {
        Class<?> c = Class.forName(PKG + name, true, getTargetContext().getClassLoader());
        return c.getField("INSTANCE").get(null);
    }

    /** Il metodo per nome (anche con il suffisso dei membri «internal» di Kotlin) e tipi degli argomenti. */
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!(m.getName().equals(name) || m.getName().startsWith(name + "$")) || m.getName().endsWith("$default")) continue;
            Class<?>[] types = m.getParameterTypes();
            if (types.length != args.length) continue;
            boolean fits = true;
            for (int i = 0; i < types.length && fits; i++) fits = args[i] == null || box(types[i]).isInstance(args[i]);
            if (fits) return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length);
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == boolean.class) return Boolean.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return Short.class;
    }

    private static Object get(Object target, String getter) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if ((m.getName().equals(getter) || m.getName().startsWith(getter + "$")) && m.getParameterTypes().length == 0) return m.invoke(target);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + getter);
    }

    private static byte[] readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) { int r = in.read(b, off, b.length - off); if (r < 0) break; off += r; }
            return b;
        }
    }

    private static boolean startsWith(byte[] b, String prefix) {
        byte[] p = prefix.getBytes();
        if (b.length < p.length) return false;
        for (int i = 0; i < p.length; i++) if (b[i] != p[i]) return false;
        return true;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
