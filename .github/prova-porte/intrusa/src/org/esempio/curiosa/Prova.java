package org.esempio.curiosa;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * L'app «curiosa» della prova delle porte d'ingresso (.github/workflows/prova-porte.yml, docs/SICUREZZA.md).
 *
 * Fa ciò che farebbe un'app qualunque installata sul telefono: si prende da sé un permesso con il nome di quello della
 * suite (come permesso normale) e bussa ai fornitori di dati e al Ponte. Per ogni porta scrive nel log una riga
 * «EURISE-PROVA porta=APERTA|CHIUSA dettagli»; lo script dell'emulatore le legge.
 *
 * Lo stesso codice fa anche da «amica» (altro manifesto, chiave della suite): lì le porte devono restare aperte.
 * L'extra «suffisso» sceglie le app da provare («.debug» se manca).
 */
public class Prova extends Activity {
    private static final String TAG = "EURISE-PROVA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String suffix = getIntent().hasExtra("suffisso") ? getIntent().getStringExtra("suffisso") : ".debug";
        new Thread(() -> {
            call("segreti", "content://eu.eurise.hub" + suffix + ".secrets", "get", "chat-recovery");
            secrets(suffix);
            call("accessi", "content://eu.eurise.password" + suffix + ".logins", "status", null);
            call("sms", "content://eu.eurise.messages" + suffix + ".persone", "recent", null);
            call("tastiera", "content://eu.eurise.keyboard" + suffix + ".eurise.backup", "apply", null);
            apri("backup", "content://eu.eurise.password" + suffix + ".eurise.backup/export");
            call("aspetto", "content://eu.eurise.hub" + suffix + ".appearance", "hand", null);
            call("giornata", "content://eu.eurise.hub" + suffix + ".postazione", "today", null);
            call("rimanda", "content://eu.eurise.hub" + suffix + ".postazione", "postpone", null);
            query("codici", "content://eu.eurise.password" + suffix + ".codes/otp?package=org.esempio.curiosa");
            ponte(suffix);
            Log.i(TAG, "fine");
            runOnUiThread(this::finish);
        }).start();
    }

    /** Un fornitore risponde (anche con un Bundle vuoto): la porta è aperta. Eccezione o null: chiusa. */
    private void call(String porta, String uri, String method, String arg) {
        try {
            Bundle b = getContentResolver().call(Uri.parse(uri), method, arg, null);
            if (b == null) Log.i(TAG, porta + "=CHIUSA risposta vuota");
            else Log.i(TAG, porta + "=APERTA chiavi=" + b.keySet());
        } catch (SecurityException e) {
            Log.i(TAG, porta + "=CHIUSA " + e.getClass().getSimpleName());
        } catch (Exception e) {
            Log.i(TAG, porta + "=CHIUSA " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /**
     * Affida un segreto all'app Eurise e lo rilegge («affida»), e rilegge quello che lo script ha messo nel vecchio file
     * in chiaro («migra»). Lo script controlla poi che nei file dell'app Eurise non resti nulla in chiaro.
     */
    private void secrets(String suffix) {
        Uri u = Uri.parse("content://eu.eurise.hub" + suffix + ".secrets");
        try {
            Bundle v = new Bundle();
            v.putString("value", "EURISE-SEGRETO-NUOVO");
            getContentResolver().call(u, "put", "prova-porte", v);
            Bundle b = getContentResolver().call(u, "get", "prova-porte", null);
            Bundle old = getContentResolver().call(u, "get", "prova-vecchio", null);
            Log.i(TAG, "affida=" + ("EURISE-SEGRETO-NUOVO".equals(b == null ? null : b.getString("value")) ? "UGUALE" : "DIVERSO"));
            Log.i(TAG, "migra=" + ("EURISE-SEGRETO-VECCHIO".equals(old == null ? null : old.getString("value")) ? "UGUALE" : "DIVERSO"));
        } catch (Exception e) {
            Log.i(TAG, "affida=CHIUSA " + e.getClass().getSimpleName());
            Log.i(TAG, "migra=CHIUSA " + e.getClass().getSimpleName());
        }
    }

    /** Prova a leggere un file da un fornitore (lo zip del backup): se arriva anche un solo byte, la porta è aperta. */
    private void apri(String porta, String uri) {
        try (java.io.InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
            if (in == null) { Log.i(TAG, porta + "=CHIUSA risposta vuota"); return; }
            int n = in.read(new byte[64]);
            Log.i(TAG, porta + (n > 0 ? "=APERTA byte letti" : "=CHIUSA niente da leggere"));
        } catch (SecurityException e) {
            Log.i(TAG, porta + "=CHIUSA " + e.getClass().getSimpleName());
        } catch (Exception e) {
            Log.i(TAG, porta + "=CHIUSA " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private void query(String porta, String uri) {
        try (Cursor c = getContentResolver().query(Uri.parse(uri), null, null, null, null)) {
            if (c == null) Log.i(TAG, porta + "=CHIUSA risposta vuota");
            else Log.i(TAG, porta + "=APERTA righe=" + c.getCount());
        } catch (SecurityException e) {
            Log.i(TAG, porta + "=CHIUSA " + e.getClass().getSimpleName());
        } catch (Exception e) {
            Log.i(TAG, porta + "=CHIUSA " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /**
     * Il Ponte: un codice di collegamento che punta a un «computer» qui sul telefono (questa app, in ascolto su
     * 127.0.0.1). Se il Ponte prova a collegarsi, la porta è aperta: senza che l'utente abbia inquadrato nulla.
     */
    private void ponte(String suffix) {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(20_000);
            byte[] key = new byte[32];
            byte[] secret = new byte[16];
            for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
            for (int i = 0; i < secret.length; i++) secret[i] = (byte) (i + 7);
            int flags = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;
            String code = "eurise-ponte://collega?v=1&i=intrusa1&n=Intrusa"
                    + "&k=" + Base64.encodeToString(key, flags)
                    + "&s=" + Base64.encodeToString(secret, flags)
                    + "&h=127.0.0.1&p=" + server.getLocalPort();
            Intent i = new Intent("eu.eurise.action.PONTE_PAIR")
                    .setComponent(new ComponentName("eu.eurise.hub" + suffix, "eu.eurise.feature.ponte.PonteActivity"))
                    .putExtra(Intent.EXTRA_TEXT, code)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> {
                try { startActivity(i); } catch (Exception e) { Log.i(TAG, "ponte-avvio " + e); }
            });
            try (Socket s = server.accept()) {
                Log.i(TAG, "ponte=APERTA il telefono si collega al computer dell'intrusa");
            } catch (SocketTimeoutException e) {
                Log.i(TAG, "ponte=CHIUSA nessun collegamento in 20 secondi");
            }
        } catch (Exception e) {
            Log.i(TAG, "ponte=ERRORE " + e);
        }
    }
}
