package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ferramentas de PDF: juntar, extrair páginas, rodar, comprimir e converter fotos em PDF.
 * Os originais nunca são alterados: cada operação cria um PDF novo, guardado em
 * Download/Seeky/PDF (com acesso geral) ou no local escolhido pelo utilizador.
 */
final class PdfTools {
    static final int MERGE = 7101, IMAGES = 7102, EXTRACT = 7103, ROTATE = 7104, COMPRESS = 7105, SAVE = 7110;
    static final ExecutorService work = Executors.newSingleThreadExecutor();
    static File pendingSave;

    private PdfTools() {}

    // ---- operações (testáveis sem interface) ----

    static void init(Context c) { PDFBoxResourceLoader.init(c.getApplicationContext()); }

    static int pages(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) { return doc.getNumberOfPages(); }
    }

    static void merge(List<File> inputs, File out) throws IOException {
        PDFMergerUtility m = new PDFMergerUtility();
        for (File f : inputs) m.addSource(f);
        try (OutputStream o = new FileOutputStream(out)) {
            m.setDestinationStream(o);
            m.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        }
    }

    /** Páginas {@code from}..{@code to}, numeradas a partir de 1, inclusive. */
    static void extract(File in, int from, int to, File out) throws IOException {
        try (PDDocument src = PDDocument.load(in); PDDocument dst = new PDDocument()) {
            int n = src.getNumberOfPages();
            if (from < 1 || to < from || to > n) throw new IOException("Intervalo inválido: o documento tem " + n + " páginas.");
            for (int i = from - 1; i < to; i++) dst.importPage(src.getPage(i));
            dst.save(out);
        }
    }

    static void rotate(File in, int degrees, File out) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            for (PDPage page : doc.getPages()) page.setRotation(((page.getRotation() + degrees) % 360 + 360) % 360);
            doc.save(out);
        }
    }

    /** Volta a codificar as imagens grandes em JPEG; devolve {antes, depois, imagens}. */
    static long[] compress(File in, int quality, int maxSide, File out) throws IOException {
        int changed = 0;
        try (PDDocument doc = PDDocument.load(in)) {
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res == null) continue;
                List<COSName> names = new ArrayList<>();
                for (COSName name : res.getXObjectNames()) names.add(name);
                for (COSName name : names) {
                    PDXObject x = res.getXObject(name);
                    if (!(x instanceof PDImageXObject)) continue;
                    PDImageXObject img = (PDImageXObject) x;
                    if (img.isStencil() || img.getSoftMask() != null || img.getBitsPerComponent() == 1) continue;
                    if ((long) img.getWidth() * img.getHeight() < 160000) continue;
                    Bitmap bmp = img.getImage();
                    if (bmp == null) continue;
                    Bitmap scaled = fit(bmp, maxSide);
                    res.put(name, JPEGFactory.createFromImage(doc, scaled, quality / 100f));
                    if (scaled != bmp) scaled.recycle();
                    bmp.recycle();
                    changed++;
                }
            }
            doc.save(out);
        }
        return new long[]{in.length(), out.length(), changed};
    }

    /** Uma página por imagem, A4 na orientação da imagem, com margem. */
    static void images(List<File> images, File out) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (File f : images) {
                Bitmap bmp = decode(f, 2400);
                if (bmp == null) continue;
                boolean landscape = bmp.getWidth() > bmp.getHeight();
                PDRectangle size = landscape ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4;
                PDPage page = new PDPage(size);
                doc.addPage(page);
                PDImageXObject img = JPEGFactory.createFromImage(doc, bmp, .85f);
                float margin = 18, w = size.getWidth() - 2 * margin, h = size.getHeight() - 2 * margin;
                float scale = Math.min(w / bmp.getWidth(), h / bmp.getHeight());
                float iw = bmp.getWidth() * scale, ih = bmp.getHeight() * scale;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(img, (size.getWidth() - iw) / 2, (size.getHeight() - ih) / 2, iw, ih);
                }
                bmp.recycle();
            }
            if (doc.getNumberOfPages() == 0) throw new IOException("Nenhuma imagem legível.");
            doc.save(out);
        }
    }

    static Bitmap fit(Bitmap bmp, int maxSide) {
        int w = bmp.getWidth(), h = bmp.getHeight(), side = Math.max(w, h);
        if (side <= maxSide) return bmp;
        float s = maxSide / (float) side;
        return Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
    }

    /** Descodifica com redução prévia (poupa memória) e aplica a rotação EXIF da câmara. */
    static Bitmap decode(File f, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), d);
        if (bmp == null) return null;
        int rotation = 0;
        try {
            int orientation = new android.media.ExifInterface(f.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            rotation = orientation == 6 ? 90 : orientation == 3 ? 180 : orientation == 8 ? 270 : 0;
        } catch (Exception ignored) {}
        Bitmap scaled = fit(bmp, maxSide);
        if (scaled != bmp) bmp.recycle();
        if (rotation == 0) return scaled;
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap turned = Bitmap.createBitmap(scaled, 0, 0, scaled.getWidth(), scaled.getHeight(), m, true);
        if (turned != scaled) scaled.recycle();
        return turned;
    }

    static String message(Exception e) {
        if (e instanceof InvalidPasswordException) return "Este PDF está protegido com palavra-passe.";
        return e.getMessage() == null ? "A operação falhou." : e.getMessage();
    }

    // ---- interface ----

    static void show(MainActivity a) {
        UiPanel p = new UiPanel(a, "Ferramentas de PDF", AppLanguage.text(a, "Os originais nunca são alterados"), "pdf");
        p.action("copy", "Juntar PDFs", "Vários PDFs num só, pela ordem escolhida", false, () -> pick(a, MERGE, "application/pdf", true));
        p.action("image", "Fotos para PDF", "Uma página por foto, em tamanho A4", false, () -> pick(a, IMAGES, "image/*", true));
        p.action("scan", "Digitalizar documento", "Câmara com recorte e correcção da folha", false, () -> ScanActivity.open(a));
        p.action("pdf", "Extrair páginas", "Guardar só um intervalo de páginas", false, () -> pick(a, EXTRACT, "application/pdf", false));
        p.action("rotate", "Rodar páginas", "Todas as páginas 90° ou 180°", false, () -> pick(a, ROTATE, "application/pdf", false));
        p.action("storage", "Comprimir PDF", "Reduz o tamanho das imagens", false, () -> pick(a, COMPRESS, "application/pdf", false));
        p.show();
    }

    /** Ferramentas para um PDF do catálogo (menu do ficheiro). */
    static void forFile(MainActivity a, Catalog.Entry e) {
        Uri uri = Uri.parse(e.uri);
        UiPanel p = new UiPanel(a, "Ferramentas de PDF", e.name, "pdf");
        p.action("pdf", "Extrair páginas", "Guardar só um intervalo de páginas", false, () -> prepare(a, EXTRACT, Collections.singletonList(uri)));
        p.action("rotate", "Rodar páginas", "Todas as páginas 90° ou 180°", false, () -> prepare(a, ROTATE, Collections.singletonList(uri)));
        p.action("storage", "Comprimir PDF", "Reduz o tamanho das imagens", false, () -> prepare(a, COMPRESS, Collections.singletonList(uri)));
        p.action("copy", "Juntar com outros PDFs", "Este primeiro, depois os que escolher", false, () -> { first = uri; pick(a, MERGE, "application/pdf", true); });
        p.show();
    }

    static Uri first;

    static void pick(Activity a, int request, String type, boolean multiple) {
        if (request != MERGE || !multiple) first = null;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type);
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { a.startActivityForResult(i, request); } catch (Exception e) { PlusUi.toast(a, "Não há um selector de ficheiros disponível."); }
    }

    static boolean result(MainActivity a, int request, int result, Intent data) {
        if (request == SAVE) {
            File f = pendingSave;
            pendingSave = null;
            if (result == Activity.RESULT_OK && data != null && data.getData() != null && f != null) {
                Uri target = data.getData();
                work.execute(() -> {
                    try (InputStream in = new FileInputStream(f); OutputStream out = a.getContentResolver().openOutputStream(target)) {
                        if (out == null) throw new IOException("Sem acesso ao destino.");
                        copy(in, out);
                        a.ui(() -> a.toast("PDF guardado."));
                    } catch (Exception e) { a.ui(() -> a.showError(e)); }
                    finally { f.delete(); }
                });
            } else if (f != null) f.delete();
            return true;
        }
        if (request < MERGE || request > COMPRESS) return false;
        if (result != Activity.RESULT_OK || data == null) { first = null; return true; }
        List<Uri> uris = new ArrayList<>();
        if (request == MERGE && first != null) uris.add(first);
        first = null;
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return true;
        if (request == MERGE && uris.size() < 2) { a.toast("Escolha pelo menos dois PDFs."); return true; }
        prepare(a, request, uris);
        return true;
    }

    /** Copia as entradas para a cache (o pdfbox trabalha melhor com ficheiros) e pede os detalhes. */
    static void prepare(MainActivity a, int request, List<Uri> uris) {
        a.toast("A preparar…");
        work.execute(() -> {
            try {
                init(a);
                File dir = workDir(a);
                List<File> files = new ArrayList<>();
                for (int i = 0; i < uris.size(); i++) {
                    File f = new File(dir, "in-" + i);
                    try (InputStream in = a.getContentResolver().openInputStream(uris.get(i)); OutputStream out = new FileOutputStream(f)) {
                        if (in == null) throw new IOException("Sem acesso a um dos ficheiros.");
                        copy(in, out);
                    }
                    files.add(f);
                }
                String base = baseName(a, uris.get(0));
                if (request == MERGE) run(a, () -> { File out = new File(dir, "out.pdf"); merge(files, out); return out; }, base + " (junto).pdf");
                else if (request == IMAGES) run(a, () -> { File out = new File(dir, "out.pdf"); images(files, out); return out; }, (uris.size() == 1 ? base : "Fotos") + ".pdf");
                else if (request == EXTRACT) { int n = pages(files.get(0)); a.ui(() -> askRange(a, files.get(0), n, base)); }
                else if (request == ROTATE) a.ui(() -> askRotation(a, files.get(0), base));
                else if (request == COMPRESS) a.ui(() -> askLevel(a, files.get(0), base));
            } catch (Exception e) { a.ui(() -> a.toast(message(e))); }
        });
    }

    interface Job { File run() throws Exception; }

    static void run(MainActivity a, Job job, String name) {
        work.execute(() -> {
            try {
                File out = job.run();
                deliver(a, out, name);
            } catch (Throwable e) { a.ui(() -> a.toast(e instanceof Exception ? message((Exception) e) : "O ficheiro é demasiado grande para esta operação.")); }
        });
    }

    static void askRange(MainActivity a, File pdf, int pages, String base) {
        LinearLayout box = new LinearLayout(a);
        box.setPadding(a.dp(20), a.dp(8), a.dp(20), 0);
        EditText from = number(a, "De", "1"), to = number(a, "Até", String.valueOf(pages));
        box.addView(from, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(to, new LinearLayout.LayoutParams(0, -2, 1));
        a.dialog().setTitle("Extrair páginas").setMessage(AppLanguage.text(a, "O documento tem") + " " + pages + " " + AppLanguage.text(a, "páginas."))
                .setView(box).setPositiveButton("Extrair", (d, w) -> {
                    int f = parse(from, 1), t = parse(to, pages);
                    run(a, () -> { File out = new File(pdf.getParentFile(), "out.pdf"); extract(pdf, f, t, out); return out; }, base + " (p. " + f + "-" + t + ").pdf");
                }).setNegativeButton("Cancelar", null).show();
    }

    static void askRotation(MainActivity a, File pdf, String base) {
        int[] degrees = {90, 180, 270};
        a.dialog().setTitle("Rodar páginas").setItems(new String[]{"90° para a direita", "180°", "90° para a esquerda"}, (d, w) ->
                run(a, () -> { File out = new File(pdf.getParentFile(), "out.pdf"); rotate(pdf, degrees[w], out); return out; }, base + " (rodado).pdf"))
                .setNegativeButton("Cancelar", null).show();
    }

    static void askLevel(MainActivity a, File pdf, String base) {
        int[][] levels = {{50, 1200}, {70, 1600}, {85, 2200}};
        a.dialog().setTitle("Comprimir PDF").setItems(new String[]{"Forte: ficheiro mais pequeno", "Média: equilíbrio", "Leve: melhor qualidade"}, (d, w) ->
                work.execute(() -> {
                    try {
                        File out = new File(pdf.getParentFile(), "out.pdf");
                        long[] r = compress(pdf, levels[w][0], levels[w][1], out);
                        if (r[2] == 0 || r[1] >= r[0]) { a.ui(() -> a.toast("Este PDF já está optimizado: não há imagens para reduzir.")); return; }
                        a.ui(() -> a.toast(Rules.bytes(r[0]) + " → " + Rules.bytes(r[1])));
                        deliver(a, out, base + " (comprimido).pdf");
                    } catch (Throwable e) { a.ui(() -> a.toast(e instanceof Exception ? message((Exception) e) : "O ficheiro é demasiado grande para esta operação.")); }
                })).setNegativeButton("Cancelar", null).show();
    }

    /** Com acesso geral grava em Download/Seeky/PDF; sem ele pergunta onde guardar. */
    static void deliver(MainActivity a, File out, String name) throws IOException {
        if (StorageAccess.all(a)) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky/PDF");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar Download/Seeky/PDF.");
            File target = WifiTransfer.unique(dir, WifiTransfer.cleanName(name));
            try (InputStream in = new FileInputStream(out); OutputStream o = new FileOutputStream(target)) { copy(in, o); }
            out.delete();
            android.media.MediaScannerConnection.scanFile(a.getApplicationContext(), new String[]{target.getPath()}, new String[]{"application/pdf"}, null);
            a.ui(() -> a.dialog().setTitle("PDF criado").setMessage(AppLanguage.text(a, "Guardado em") + " Download/Seeky/PDF/" + target.getName())
                    .setPositiveButton("Abrir", (d, w) -> a.open(StorageAccess.entry(target, Environment.getExternalStorageDirectory()), false))
                    .setNegativeButton("Fechar", null).show());
        } else {
            File keep = new File(a.getCacheDir(), "pdf-save-" + System.currentTimeMillis() + ".pdf");
            if (!out.renameTo(keep)) throw new IOException("Não foi possível preparar o PDF.");
            a.ui(() -> {
                pendingSave = keep;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE, name);
                try { a.startActivityForResult(i, SAVE); } catch (Exception e) { a.showError(e); }
            });
        }
    }

    static File workDir(Context c) throws IOException {
        File dir = new File(c.getCacheDir(), "pdf-tools");
        if (dir.isDirectory()) { File[] old = dir.listFiles(); if (old != null) for (File f : old) f.delete(); }
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Sem espaço temporário.");
        return dir;
    }

    static String baseName(Context c, Uri uri) {
        String name = "Documento";
        try (android.database.Cursor cur = c.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) name = cur.getString(0);
        } catch (Exception ignored) {
            if (uri.getLastPathSegment() != null) name = uri.getLastPathSegment();
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    static EditText number(Context c, String hint, String value) {
        EditText e = new EditText(c);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setHint(AppLanguage.text(c, hint));
        e.setText(value);
        return e;
    }

    static int parse(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return fallback; }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
    }
}
