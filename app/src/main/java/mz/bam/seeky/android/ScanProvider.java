package mz.bam.seeky.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Dá à aplicação da câmara um único ficheiro onde gravar a fotografia da digitalização.
 * Não exportado: o acesso só existe através da autorização temporária dada no Intent da câmara,
 * e apenas para nomes {@code shot_N.jpg} dentro de cache/scan.
 */
public final class ScanProvider extends ContentProvider {
    static Uri uri(android.content.Context c, String name) { return Uri.parse("content://" + c.getPackageName() + ".scan/" + name); }

    static File dir(android.content.Context c) { return new File(c.getCacheDir(), "scan"); }

    @Override public boolean onCreate() { return true; }

    /** Cópia temporária de um ficheiro da pasta privada, só de leitura: open/&lt;token&gt;/&lt;nome&gt;. */
    File privateCopy(Uri uri) throws FileNotFoundException {
        java.util.List<String> parts = uri.getPathSegments();
        if (getContext() == null || parts.size() != 3 || !parts.get(0).equals("open") || !parts.get(1).matches("[a-f0-9]{32}")) throw new FileNotFoundException();
        try {
            File base = new File(getContext().getCacheDir(), "private-open").getCanonicalFile();
            File f = new File(new File(base, parts.get(1)), parts.get(2)).getCanonicalFile();
            if (!DiskWalker.inside(base, f) || !f.isFile()) throw new FileNotFoundException();
            return f;
        } catch (java.io.IOException e) { throw new FileNotFoundException(); }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri.getPathSegments().size() == 3) {
            if (!"r".equals(mode)) throw new FileNotFoundException("Só leitura");
            return ParcelFileDescriptor.open(privateCopy(uri), ParcelFileDescriptor.MODE_READ_ONLY);
        }
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("shot_[0-9]{1,6}\\.jpg") || getContext() == null) throw new FileNotFoundException();
        File dir = dir(getContext());
        if (!dir.isDirectory() && !dir.mkdirs()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(dir, name), ParcelFileDescriptor.parseMode(mode.contains("w") ? "rwt" : "r"));
    }

    @Override public String getType(Uri uri) {
        if (uri.getPathSegments().size() == 3) try { return StorageAccess.mime(privateCopy(uri)); } catch (Exception e) { return "application/octet-stream"; }
        return "image/jpeg";
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        if (uri.getPathSegments().size() != 3) return null;
        try {
            File f = privateCopy(uri);
            android.database.MatrixCursor c = new android.database.MatrixCursor(new String[]{android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
