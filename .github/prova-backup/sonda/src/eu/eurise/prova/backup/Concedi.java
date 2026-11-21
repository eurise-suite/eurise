package eu.eurise.prova.backup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

/**
 * Concede all'app Eurise di prova l'albero della {@link Cartella} finta, con il permesso che dura (come il selettore di
 * Android quando l'utente sceglie una cartella). Lo manda la prova: «am broadcast -n eu.eurise.prova.backup/.Concedi».
 */
public class Concedi extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Uri tree = DocumentsContract.buildTreeDocumentUri(Cartella.AUTHORITY, Cartella.ROOT);
        context.grantUriPermission("eu.eurise.hub.debug", tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        setResultData(tree.toString());
    }
}
