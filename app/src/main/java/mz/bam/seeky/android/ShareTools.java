package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Partilhar a partir dos leitores (foto, música, vídeo, documento): o próprio ficheiro, uma
 * imagem gerada (página de PDF, foto editada) ou texto. Ficheiros locais passam pelo fornecedor
 * do Seeky só de leitura; cópias temporárias ficam em cache/private-open e são apagadas depois.
 */
final class ShareTools {
    private ShareTools() {}

    /** URI que outra aplicação consegue ler (file:// passa a content:// do Seeky). */
    static Uri readable(Activity a, Uri uri, String name, String mime) {
        if (!"file".equals(uri.getScheme())) return uri;
        Catalog.Entry e = new Catalog.Entry();
        e.uri = uri.toString();
        e.name = name;
        e.mime = mime;
        return StorageAccess.shareUri(a, e);
    }

    static void file(Activity a, Uri uri, String name, String mime) {
        try { send(a, readable(a, uri, name, mime), name, mime == null || mime.isEmpty() ? "*/*" : mime); }
        catch (Exception e) { PlusUi.toast(a, "Não foi possível partilhar este ficheiro."); }
    }

    static void send(Activity a, Uri shared, String name, String mime) {
        Intent i = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, shared)
                .putExtra(Intent.EXTRA_TITLE, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri(name, shared));
        try { a.startActivity(Intent.createChooser(i, AppLanguage.text(a, "Partilhar") + " " + name)); }
        catch (Exception e) { PlusUi.toast(a, "Não há aplicações para partilhar."); }
    }

    static void text(Activity a, String text) {
        if (text == null || text.trim().isEmpty()) { PlusUi.toast(a, "Nada para partilhar."); return; }
        try { a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.trim()), AppLanguage.text(a, "Partilhar"))); }
        catch (Exception e) { PlusUi.toast(a, "Não há aplicações para partilhar."); }
    }

    /** Pasta temporária única servida pelo ScanProvider (rota open/&lt;token&gt;/&lt;nome&gt;, só leitura). */
    static File temp(Activity a, String name) throws IOException {
        File base = new File(a.getCacheDir(), "private-open");
        File[] old = base.listFiles();
        long now = System.currentTimeMillis();
        if (old != null) for (File f : old) if (now - f.lastModified() > 6 * 3600000L) PrivateFolder.delete(f);
        String token = UUID.randomUUID().toString().replace("-", "");
        File dir = new File(base, token);
        if (!dir.mkdirs()) throw new IOException("Sem espaço temporário.");
        return new File(dir, WifiTransfer.cleanName(name));
    }

    static Uri tempUri(Activity a, File f) {
        return Uri.parse("content://" + a.getPackageName() + ".scan/open/" + f.getParentFile().getName() + "/" + Uri.encode(f.getName()));
    }

    static void bitmap(Activity a, Bitmap b, String name, boolean png) {
        try {
            File f = temp(a, name);
            try (OutputStream out = new FileOutputStream(f)) { b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 94, out); }
            send(a, tempUri(a, f), f.getName(), png ? "image/png" : "image/jpeg");
        } catch (Exception e) { PlusUi.toast(a, "Não foi possível preparar a imagem."); }
    }

    static void copyAndSend(Activity a, InputStream in, String name, String mime) throws IOException {
        File f = temp(a, name);
        try (OutputStream out = new FileOutputStream(f)) { PdfTools.copy(in, out); }
        send(a, tempUri(a, f), f.getName(), mime);
    }
}
