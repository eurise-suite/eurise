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
 * - backup: semina note, attività e un'impostazione, fa il backup cifrato (con le parole della Tastiera), lo lascia
 *   in cache/prova.eurb e controlla che nei file dell'app non resti niente in chiaro;
 * - ripristino: a dati cancellati e senza Tastiera, la frase sbagliata non apre, il codice di recupero sì; il
 *   contenuto è quello giusto; poi prepara e applica il ripristino e controlla che tutto sia tornato;
 * - consegna: con la Tastiera di nuovo installata, le sue parole in attesa (sigillate) le arrivano.
 */
public class Sonda extends Instrumentation {
    private static final String FRASE = "girasole lento sotto la pioggia 1987";
    private static final String CODICE = "K7QMR2XAHPN9DW4TZB6SMC3E";
    private static final String PKG = "eu.eurise.core.cloud.backup.";

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
        byte[] zip = (byte[]) call(obj("BackupArchive"), "create", ctx, now);
        byte[] file = (byte[]) call(obj("BackupCrypto"), "encrypt", keys, now, zip);
        check(startsWith(file, "EURISEB1"), "il file non comincia con l'intestazione EURISEB1");
        check(indexOf(file, "Prova backup".getBytes("UTF-8")) < 0, "nel file cifrato si legge «Prova backup»");
        note("backup: " + file.length + " byte, zip in memoria " + zip.length);

        Object contents = call(obj("BackupArchive"), "contents", zip, now);
        note("contenuto: note=" + get(contents, "getNotes") + " attività=" + get(contents, "getTasks") +
            " tastiera=" + get(contents, "getKeyboard") + " impostazioni=" + get(contents, "getSettings"));
        check(Integer.valueOf(3).equals(get(contents, "getNotes")), "il manifesto non conta 3 note");
        check(Integer.valueOf(2).equals(get(contents, "getTasks")), "il manifesto non conta 2 attività");
        check((Boolean) get(contents, "getKeyboard"), "le parole della Tastiera non sono nel backup");

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
        Object parsed = call(obj("BackupCrypto"), "parse", (Object) file);
        Object header = get(parsed, "getHeader");
        long createdAt = (Long) get(parsed, "getCreatedAt");

        try {
            call(obj("BackupCrypto"), "unlock", header, "frase sbagliata qualsiasi");
            throw new AssertionError("la frase sbagliata ha aperto il backup");
        } catch (InvocationTargetException e) {
            check(e.getCause() != null && e.getCause().getClass().getSimpleName().equals("WrongSecret"), "frase sbagliata: errore inatteso " + e.getCause());
            note("frase sbagliata: rifiutata");
        }
        // Con il codice di recupero scritto come lo si copia da carta: minuscolo, a gruppi.
        byte[] key = (byte[]) call(obj("BackupCrypto"), "unlock", header, "k7qm-r2xa-hpn9-dw4t-zb6s-mc3e");
        byte[] keyPhrase = (byte[]) call(obj("BackupCrypto"), "unlock", header, FRASE);
        check(java.util.Arrays.equals(key, keyPhrase), "frase e codice non aprono la stessa chiave");
        byte[] zip = (byte[]) call(obj("BackupCrypto"), "decrypt", parsed, key);
        note("aperto con il codice di recupero e con la frase");

        Object contents = call(obj("BackupArchive"), "contents", zip, createdAt);
        check(Integer.valueOf(3).equals(get(contents, "getNotes")), "contenuto: non 3 note");
        Object phone = call(obj("BackupArchive"), "onPhone", ctx);
        check((Boolean) get(phone, "getEmpty"), "dopo la cancellazione il telefono non risulta vuoto");
        note("telefono vuoto prima del ripristino");

        call(obj("BackupArchive"), "stage", ctx, zip);
        // La Tastiera non c'è: le sue parole aspettano, sigillate (non uno zip leggibile).
        check((Boolean) call(obj("KeyboardBackup"), "waiting", ctx), "le parole della Tastiera non sono in attesa");
        File sealed = new File(ctx.getFilesDir(), "keyboard_restore.sealed");
        check(sealed.exists() && !startsWith(readAll(sealed), "PK"), "lo zip della Tastiera in attesa non è sigillato");
        check(!new File(new File(ctx.getFilesDir(), "restore_pending"), "apps/keyboard.zip").exists(), "lo zip della Tastiera è nella cartella d'attesa in chiaro");
        note("parole della Tastiera in attesa, sigillate");

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
        check((Boolean) call(obj("KeyboardBackup"), "waiting", ctx), "prima della consegna non c'è niente in attesa");
        call(obj("KeyboardBackup"), "deliverPending", ctx);
        check(!(Boolean) call(obj("KeyboardBackup"), "waiting", ctx), "le parole sono ancora in attesa dopo la consegna");
        note("parole consegnate alla Tastiera");
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

    /** Il metodo per nome (anche con il suffisso dei membri «internal» di Kotlin) e numero di argomenti. */
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if ((m.getName().equals(name) || m.getName().startsWith(name + "$")) && m.getParameterTypes().length == args.length && !m.getName().endsWith("$default")) {
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "/" + args.length);
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
