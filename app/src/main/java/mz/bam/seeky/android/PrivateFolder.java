package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import java.io.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pasta privada: ficheiros movidos para a área interna do Seeky, invisível às outras aplicações,
 * à galeria e ao computador, e aberta só depois de confirmar a impressão digital, rosto, PIN ou
 * padrão do telefone. Atenção: desinstalar o Seeky apaga a pasta privada (aviso no painel).
 */
final class PrivateFolder {
    static final int ADD = 7140;

    private PrivateFolder() {}

    static File dir(Context c) { return new File(c.getFilesDir(), "privado"); }
    static File index(Context c) { return new File(dir(c), "index.json"); }

    static synchronized JSONArray load(Context c) {
        File f = index(c);
        if (!f.isFile()) return new JSONArray();
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            PdfTools.copy(in, b);
            return new JSONArray(b.toString("UTF-8"));
        } catch (Exception e) { return new JSONArray(); }
    }

    static synchronized void save(Context c, JSONArray list) throws IOException {
        File d = dir(c);
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Não foi possível criar a pasta privada.");
        File tmp = new File(d, "index.json.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) { out.write(list.toString().getBytes("UTF-8")); }
        if (!tmp.renameTo(index(c))) throw new IOException("Não foi possível guardar a pasta privada.");
    }

    /** Copia para dentro e devolve o registo; o chamador decide apagar o original. */
    static JSONObject store(Context c, InputStream in, String name, String origin, long expected) throws Exception {
        String id = UUID.randomUUID().toString();
        File folder = new File(dir(c), id);
        if (!folder.mkdirs()) throw new IOException("Sem espaço na pasta privada.");
        File target = new File(folder, WifiTransfer.cleanName(name));
        long copied = 0;
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) != -1; ) { out.write(buffer, 0, n); copied += n; }
            out.getFD().sync();
        } catch (Exception e) { delete(folder); throw e; }
        if (expected >= 0 && copied != expected) { delete(folder); throw new IOException("A cópia ficou incompleta; o original foi mantido."); }
        JSONObject item = new JSONObject().put("id", id).put("name", target.getName()).put("origin", origin == null ? "" : origin)
                .put("size", copied).put("added", System.currentTimeMillis());
        JSONArray list = load(c);
        list.put(item);
        save(c, list);
        return item;
    }

    static File file(Context c, JSONObject item) { return new File(new File(dir(c), item.optString("id")), item.optString("name")); }

    static void remove(Context c, String id) throws IOException {
        JSONArray list = load(c), out = new JSONArray();
        for (int i = 0; i < list.length(); i++) if (!id.equals(list.optJSONObject(i).optString("id"))) out.put(list.optJSONObject(i));
        save(c, out);
        delete(new File(dir(c), id));
    }

    static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }

    // ---- interface ----

    static void show(MainActivity a) {
        if (!AppLock.deviceSecure(a)) {
            a.dialog().setTitle("Pasta privada").setMessage("Para proteger a pasta privada, defina primeiro um bloqueio de ecrã no Android (PIN, padrão, impressão digital ou rosto).")
                    .setPositiveButton("Abrir definições", (d, w) -> { try { a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); } catch (Exception e) { a.showError(e); } })
                    .setNegativeButton("Fechar", null).show();
            return;
        }
        Auth.confirm(a, "Abrir a pasta privada", () -> panel(a));
    }

    static void panel(MainActivity a) {
        delete(new File(a.getCacheDir(), "private-open"));
        JSONArray list = load(a);
        long total = 0;
        for (int i = 0; i < list.length(); i++) total += list.optJSONObject(i).optLong("size");
        UiPanel p = new UiPanel(a, "Pasta privada", list.length() + " " + AppLanguage.text(a, "ficheiros") + " · " + Rules.bytes(total), "lock");
        p.action("add", "Adicionar ficheiros", "Escolha fotos, vídeos ou documentos para esconder", false, () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try { a.startActivityForResult(i, ADD); } catch (Exception e) { a.showError(e); }
        });
        if (list.length() > 0) p.section("Ficheiros");
        for (int i = list.length() - 1; i >= 0; i--) {
            JSONObject item = list.optJSONObject(i);
            p.action(Icons.category(Rules.category(item.optString("name"), null)), item.optString("name"),
                    Rules.bytes(item.optLong("size")) + " · " + java.text.DateFormat.getDateInstance().format(new Date(item.optLong("added"))), false, () -> itemMenu(a, item));
        }
        p.note("Os ficheiros ficam só dentro do Seeky: não aparecem na galeria, noutras aplicações nem no computador. Se desinstalar o Seeky, a pasta privada é apagada — restaure antes o que quiser manter.");
        p.show();
    }

    static void itemMenu(MainActivity a, JSONObject item) {
        a.dialog().setTitle(item.optString("name")).setItems(new String[]{"Abrir", "Restaurar para o armazenamento", "Apagar definitivamente"}, (d, w) -> {
            if (w == 0) openItem(a, item);
            else if (w == 1) restore(a, item);
            else a.dialog().setTitle("Apagar definitivamente?").setMessage(item.optString("name") + "\n\n" + AppLanguage.text(a, "Esta acção não pode ser anulada."))
                        .setPositiveButton("Apagar", (x, y) -> { try { remove(a, item.optString("id")); panel(a); } catch (Exception e) { a.showError(e); } })
                        .setNegativeButton("Cancelar", null).show();
        }).setNegativeButton("Fechar", null).show();
    }

    /** Abre uma cópia temporária com a aplicação escolhida; a cópia é apagada na próxima abertura do painel. */
    static void openItem(MainActivity a, JSONObject item) {
        try {
            File src = file(a, item);
            String token = UUID.randomUUID().toString().replace("-", "");
            File folder = new File(new File(a.getCacheDir(), "private-open"), token);
            if (!folder.mkdirs()) throw new IOException("Sem espaço temporário.");
            File copy = new File(folder, src.getName());
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(copy)) { PdfTools.copy(in, out); }
            Uri uri = Uri.parse("content://" + a.getPackageName() + ".scan/open/" + token + "/" + Uri.encode(src.getName()));
            String mime = StorageAccess.mime(copy);
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(view, AppLanguage.text(a, "Abrir com")));
        } catch (Exception e) { a.showError(e); }
    }

    static void restore(MainActivity a, JSONObject item) {
        PdfTools.work.execute(() -> {
            try {
                File src = file(a, item);
                File origin = new File(item.optString("origin"));
                File parent = origin.getParentFile();
                File dir = StorageAccess.all(a) && parent != null && (parent.isDirectory() || parent.mkdirs()) && parent.canWrite() ? parent
                        : new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky/Restaurados");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta de destino.");
                File target = WifiTransfer.unique(dir, src.getName());
                try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(target)) { PdfTools.copy(in, out); }
                if (target.length() != src.length()) { target.delete(); throw new IOException("A cópia ficou incompleta; o ficheiro continua na pasta privada."); }
                remove(a, item.optString("id"));
                android.media.MediaScannerConnection.scanFile(a.getApplicationContext(), new String[]{target.getPath()}, null, null);
                a.ui(() -> { a.toast(AppLanguage.text(a, "Restaurado em") + " " + dir.getPath()); panel(a); });
            } catch (Exception e) { a.ui(() -> a.showError(e)); }
        });
    }

    /** Menu do ficheiro do catálogo: mover para a pasta privada. */
    static void move(MainActivity a, Catalog.Entry e) {
        a.dialog().setTitle("Mover para a pasta privada").setMessage(e.name + "\n\n" + AppLanguage.text(a, "O ficheiro deixa de aparecer na galeria e noutras aplicações. Pode restaurá-lo quando quiser."))
                .setPositiveButton("Mover", (d, w) -> PdfTools.work.execute(() -> {
                    try {
                        Uri uri = Uri.parse(e.uri);
                        File original = e.path == null ? null : new File(e.path);
                        try (InputStream in = original != null && original.isFile() ? new FileInputStream(original) : a.getContentResolver().openInputStream(uri)) {
                            if (in == null) throw new IOException("Sem acesso ao ficheiro.");
                            store(a, in, e.name, e.path, original != null && original.isFile() ? original.length() : -1);
                        }
                        boolean gone = original != null && original.isFile() ? original.delete() : deleteDocument(a, uri);
                        if (gone && original != null) a.db.removePath(original);
                        a.ui(() -> {
                            a.toast(gone ? "Movido para a pasta privada." : "Copiado para a pasta privada; não foi possível apagar o original.");
                            a.dashboardLoaded = false;
                            a.render();
                        });
                    } catch (Exception ex) { a.ui(() -> a.showError(ex)); }
                })).setNegativeButton("Cancelar", null).show();
    }

    static boolean result(MainActivity a, int request, int result, Intent data) {
        if (request != ADD) return false;
        if (result != Activity.RESULT_OK || data == null) return true;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        a.toast("A mover para a pasta privada…");
        PdfTools.work.execute(() -> {
            int moved = 0, kept = 0;
            for (Uri uri : uris) {
                try (InputStream in = a.getContentResolver().openInputStream(uri)) {
                    if (in == null) continue;
                    store(a, in, PdfTools.baseName(a, uri) + extension(a, uri), "", -1);
                    if (deleteDocument(a, uri)) moved++; else kept++;
                } catch (Exception e) { AppDiagnostics.record(a, "private-add", e); }
            }
            final int m = moved, k = kept;
            a.ui(() -> {
                a.toast(k == 0 ? m + " " + AppLanguage.text(a, "ficheiros movidos.") : (m + k) + " " + AppLanguage.text(a, "ficheiros copiados; apague os originais na galeria se quiser."));
                panel(a);
            });
        });
        return true;
    }

    static String extension(Context c, Uri uri) {
        String name = "";
        try (android.database.Cursor cur = c.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) name = cur.getString(0);
        } catch (Exception ignored) {}
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    static boolean deleteDocument(Context c, Uri uri) {
        try { return DocumentsContract.isDocumentUri(c, uri) && DocumentsContract.deleteDocument(c.getContentResolver(), uri); } catch (Exception e) { return false; }
    }
}
