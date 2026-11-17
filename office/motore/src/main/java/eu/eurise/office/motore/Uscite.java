package eu.eurise.office.motore;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.util.List;

/**
 * Le ultime uscite del processo del motore, come le registra Android (ApplicationExitInfo, da Android 11), per il
 * rapporto del problema di Eurise Office (tavole OffRapporto e OffRapportoVedi). Office non può leggerle da sé: sono
 * di un'altra app. Solo app della suite (permesso di firma, controllato qui); nessun dato dei documenti, solo il motivo,
 * l'ora, la memoria usata e la descrizione di Android.
 */
public final class Uscite extends ContentProvider {
    public static final String METHOD = "uscite";

    @Override public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null || !METHOD.equals(method)) return null;
        if (context.checkCallingPermission("eu.eurise.permission.SUITE") != PackageManager.PERMISSION_GRANTED
                && context.checkCallingPermission("eu.eurise.permission.SUITE_DEBUG") != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Eurise: solo le app della suite");
        }
        Bundle out = new Bundle();
        if (Build.VERSION.SDK_INT < 30) return out;
        ActivityManager am = context.getSystemService(ActivityManager.class);
        List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(null, 0, 8);
        int n = exits.size();
        long[] times = new long[n];
        int[] reasons = new int[n];
        int[] statuses = new int[n];
        long[] pss = new long[n];
        String[] descriptions = new String[n];
        for (int i = 0; i < n; i++) {
            ApplicationExitInfo e = exits.get(i);
            times[i] = e.getTimestamp();
            reasons[i] = e.getReason();
            statuses[i] = e.getStatus();
            pss[i] = e.getPss();
            String d = e.getDescription();
            descriptions[i] = d == null ? "" : d;
        }
        out.putLongArray("tempi", times);
        out.putIntArray("motivi", reasons);
        out.putIntArray("stati", statuses);
        out.putLongArray("memoria", pss);
        out.putStringArray("descrizioni", descriptions);
        out.putString("versione", versione(context));
        return out;
    }

    private static String versione(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
