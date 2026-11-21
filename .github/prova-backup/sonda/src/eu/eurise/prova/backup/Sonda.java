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
 *   contenuto è quello giusto; poi prepara e applica il ripristino e controlla che tutto sia tornato;
 * - consegna: con la Tastiera di nuovo installata, i dati in attesa (sigillati) arrivano a Tastiera, Password e Galleria.
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
            else if ("consegna".equals(fase)) consegna(ctx);
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

        call(obj("BackupArchive"), "applyPending", ctx);
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
