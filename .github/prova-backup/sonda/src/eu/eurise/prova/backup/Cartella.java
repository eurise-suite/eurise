package eu.eurise.prova.backup;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Una «cartella» finta per la prova del backup (destinazione «cartella o chiavetta»): un fornitore di documenti vero di
 * Android, piatto, sopra la cartella files/cartella della sonda. Eurise ci arriva come a quella di una chiavetta o di
 * un'app cloud, con il permesso sull'albero che le concede {@link Concedi}, come farebbe il selettore di Android.
 * Si guarda da fuori con «run-as eu.eurise.prova.backup ls files/cartella».
 */
public class Cartella extends DocumentsProvider {
    static final String AUTHORITY = "eu.eurise.prova.backup.cartella";
    static final String ROOT = "cartella";

    private static final String[] ROOT_COLS = {Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS};
    private static final String[] DOC_COLS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS};

    private File dir() {
        File d = new File(getContext().getFilesDir(), ROOT);
        d.mkdirs();
        return d;
    }

    private File file(String docId) throws FileNotFoundException {
        if (ROOT.equals(docId)) return dir();
        if (!docId.startsWith(ROOT + "/")) throw new FileNotFoundException(docId);
        String name = docId.substring(ROOT.length() + 1);
        if (name.isEmpty() || name.contains("/") || name.contains("..")) throw new FileNotFoundException(docId);
        return new File(dir(), name);
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : ROOT_COLS);
        c.newRow().add(Root.COLUMN_ROOT_ID, ROOT).add(Root.COLUMN_DOCUMENT_ID, ROOT).add(Root.COLUMN_TITLE, "Cartella di prova")
            .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        return c;
    }

    private void row(MatrixCursor c, String docId, File f) {
        boolean folder = f.isDirectory();
        c.newRow().add(Document.COLUMN_DOCUMENT_ID, docId).add(Document.COLUMN_DISPLAY_NAME, folder ? ROOT : f.getName())
            .add(Document.COLUMN_MIME_TYPE, folder ? Document.MIME_TYPE_DIR : "application/octet-stream")
            .add(Document.COLUMN_SIZE, folder ? null : f.length()).add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, folder ? Document.FLAG_DIR_SUPPORTS_CREATE
                : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLS);
        File f = file(documentId);
        if (!f.exists()) throw new FileNotFoundException(documentId);
        row(c, documentId, f);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(projection != null ? projection : DOC_COLS);
        if (!ROOT.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        File[] list = dir().listFiles();
        if (list != null) for (File f : list) if (f.isFile()) row(c, ROOT + "/" + f.getName(), f);
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        if (!ROOT.equals(parentDocumentId)) throw new FileNotFoundException(parentDocumentId);
        File f = new File(dir(), displayName);
        for (int i = 1; f.exists(); i++) f = new File(dir(), displayName + " (" + i + ")");
        try {
            if (!f.createNewFile()) throw new FileNotFoundException(displayName);
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        return ROOT + "/" + f.getName();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        if (!file(documentId).delete()) throw new FileNotFoundException(documentId);
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File from = file(documentId);
        File to = file(ROOT + "/" + displayName);
        to.delete();
        if (!from.renameTo(to)) throw new FileNotFoundException(documentId);
        return ROOT + "/" + displayName;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return ROOT.equals(parentDocumentId) && documentId.startsWith(ROOT + "/");
    }
}
