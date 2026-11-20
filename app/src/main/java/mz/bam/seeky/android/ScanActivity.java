package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Digitalizar documento: fotografia pela câmara (ou galeria), detecção automática da folha,
 * cantos ajustáveis à mão, correcção de perspectiva, filtro "Documento" (preto e branco nítido)
 * e várias páginas num só PDF.
 */
public final class ScanActivity extends Activity {
    static final int CAMERA = 1, GALLERY = 2, SAVE = 3, OPEN = 7120;
    static final String[] FILTERS = {"Documento", "Cores", "Original"};

    final List<File> pages = new ArrayList<>();
    File shot, pendingPdf;
    Bitmap photo;
    int filter, shots;
    CropView crop;
    TextView title, filterButton;

    static void open(MainActivity a) {
        try { a.startActivityForResult(new Intent(a, ScanActivity.class), OPEN); } catch (Exception e) { a.showError(e); }
    }

    /** Resultado na MainActivity: abre o PDF guardado. */
    static boolean result(MainActivity a, int request, int result, Intent data) {
        if (request != OPEN) return false;
        if (result == RESULT_OK && data != null && data.getStringExtra("path") != null) {
            File f = new File(data.getStringExtra("path"));
            if (f.isFile()) a.open(StorageAccess.entry(f, Environment.getExternalStorageDirectory()), false);
        }
        return true;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppPalette p = AppPalette.of(this);
        getWindow().setStatusBarColor(0xff111111);
        getWindow().setNavigationBarColor(0xff111111);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff111111);
        title = label("", 17, Color.WHITE);
        title.setTypeface(ThemeStyle.BOLD);
        title.setPadding(dp(18), dp(16), dp(18), dp(4));
        root.addView(title);
        TextView hint = label(AppLanguage.text(this, "Arraste os cantos até às pontas da folha."), 13, 0xffbbbbbb);
        hint.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(hint);
        crop = new CropView(this, p.accent);
        root.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(10), dp(10), dp(10), dp(14));
        bar.addView(button(AppLanguage.text(this, "Refazer"), false, v -> retake()), new LinearLayout.LayoutParams(0, dp(46), 1));
        filterButton = button("", false, v -> { filter = (filter + 1) % FILTERS.length; updateTitle(); });
        bar.addView(filterButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        bar.addView(button(AppLanguage.text(this, "+ Página"), false, v -> { if (keep()) capture(); }), new LinearLayout.LayoutParams(0, dp(46), 1));
        bar.addView(button(AppLanguage.text(this, "Concluir"), true, v -> finishScan()), new LinearLayout.LayoutParams(0, dp(46), 1.2f));
        root.addView(bar);
        setContentView(root);
        updateTitle();
        File dir = ScanProvider.dir(this);
        if (state == null) {
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            capture();
        } else {
            shots = state.getInt("shots");
            filter = state.getInt("filter");
            for (String s : state.getStringArrayList("pages") == null ? new ArrayList<String>() : state.getStringArrayList("pages")) pages.add(new File(s));
            if (state.getString("shot") != null) { shot = new File(state.getString("shot")); if (shot.isFile()) load(); }
            updateTitle();
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("shots", shots);
        out.putInt("filter", filter);
        ArrayList<String> list = new ArrayList<>();
        for (File f : pages) list.add(f.getPath());
        out.putStringArrayList("pages", list);
        if (shot != null) out.putString("shot", shot.getPath());
    }

    void updateTitle() {
        title.setText(AppLanguage.text(this, "Digitalizar") + " · " + AppLanguage.text(this, "página") + " " + (pages.size() + 1));
        filterButton.setText(AppLanguage.text(this, FILTERS[filter]));
    }

    void capture() {
        File dir = ScanProvider.dir(this);
        dir.mkdirs();
        shot = new File(dir, "shot_" + (++shots) + ".jpg");
        Uri out = ScanProvider.uri(this, shot.getName());
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, out)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("", out));
        try { startActivityForResult(i, CAMERA); }
        catch (ActivityNotFoundException | SecurityException e) { gallery(); }
    }

    void gallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try { startActivityForResult(i, GALLERY); } catch (Exception e) { PlusUi.toast(this, "Não há câmara nem galeria disponível."); finish(); }
    }

    void retake() { photo = null; crop.set(null, null); capture(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE) { saved(result, data); return; }
        if (result != RESULT_OK) {
            if (photo == null && pages.isEmpty()) finish();
            return;
        }
        if (request == GALLERY && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData()); OutputStream out = new FileOutputStream(shot)) {
                if (in == null) throw new IOException();
                PdfTools.copy(in, out);
            } catch (Exception e) { PlusUi.toast(this, "Não foi possível abrir a imagem."); return; }
        }
        load();
    }

    void load() {
        photo = shot != null && shot.length() > 0 ? PdfTools.decode(shot, 2000) : null;
        if (photo == null) { PlusUi.toast(this, "A câmara não devolveu a fotografia. Tente a galeria."); gallery(); return; }
        crop.set(photo, detect(photo));
    }

    /** Guarda a página actual (recortada e filtrada). */
    boolean keep() {
        if (photo == null) return !pages.isEmpty();
        Bitmap page = warp(photo, crop.corners, filter);
        File f = new File(ScanProvider.dir(this), "page_" + pages.size() + ".jpg");
        try (OutputStream out = new FileOutputStream(f)) { page.compress(Bitmap.CompressFormat.JPEG, 88, out); }
        catch (IOException e) { PlusUi.toast(this, "Sem espaço para guardar a página."); return false; }
        finally { page.recycle(); }
        pages.add(f);
        photo.recycle();
        photo = null;
        crop.set(null, null);
        updateTitle();
        return true;
    }

    void finishScan() {
        if (!keep() || pages.isEmpty()) return;
        PlusUi.toast(this, "A criar o PDF…");
        String name = "Digitalização " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(new Date()) + ".pdf";
        new Thread(() -> {
            try {
                File pdf = new File(ScanProvider.dir(this), "scan.pdf");
                PdfTools.init(this);
                PdfTools.images(pages, pdf);
                if (StorageAccess.all(this)) {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky/Digitalizações");
                    dir.mkdirs();
                    File target = WifiTransfer.unique(dir, name);
                    try (InputStream in = new FileInputStream(pdf); OutputStream out = new FileOutputStream(target)) { PdfTools.copy(in, out); }
                    android.media.MediaScannerConnection.scanFile(getApplicationContext(), new String[]{target.getPath()}, new String[]{"application/pdf"}, null);
                    runOnUiThread(() -> {
                        PlusUi.toast(this, AppLanguage.text(this, "Guardado em") + " Download/Seeky/Digitalizações");
                        setResult(RESULT_OK, new Intent().putExtra("path", target.getPath()));
                        finish();
                    });
                } else runOnUiThread(() -> {
                    pendingPdf = pdf;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE, name);
                    try { startActivityForResult(i, SAVE); } catch (Exception e) { PlusUi.toast(this, "Não foi possível guardar."); }
                });
            } catch (Throwable e) { runOnUiThread(() -> PlusUi.toast(this, "Não foi possível criar o PDF.")); }
        }, "seeky-scan").start();
    }

    void saved(int result, Intent data) {
        if (result != RESULT_OK || data == null || data.getData() == null || pendingPdf == null) return;
        try (InputStream in = new FileInputStream(pendingPdf); OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException();
            PdfTools.copy(in, out);
            PlusUi.toast(this, "PDF guardado.");
            finish();
        } catch (IOException e) { PlusUi.toast(this, "Não foi possível guardar."); }
    }

    @Override protected void onDestroy() {
        if (photo != null) photo.recycle();
        super.onDestroy();
    }

    // ---- imagem ----

    /**
     * Encontra a folha: limiar de Otsu na luminância reduzida, maior região clara ligada e os
     * quatro extremos (x+y e x−y). Sem folha clara reconhecível, devolve uma margem de 4 %.
     */
    static float[] detect(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        float scale = Math.min(1f, 200f / Math.max(w, h));
        int sw = Math.max(8, Math.round(w * scale)), sh = Math.max(8, Math.round(h * scale));
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        if (small != src) small.recycle();
        int[] lum = new int[px.length], hist = new int[256];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], l = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            lum[i] = l;
            hist[l]++;
        }
        int threshold = otsu(hist, px.length);
        boolean[] seen = new boolean[px.length];
        int best = 0;
        int[] bestCorners = null;
        int[] queue = new int[px.length];
        for (int start = 0; start < px.length; start++) {
            if (seen[start] || lum[start] <= threshold) continue;
            int head = 0, tail = 0, count = 0;
            int minSum = Integer.MAX_VALUE, maxSum = Integer.MIN_VALUE, minDiff = Integer.MAX_VALUE, maxDiff = Integer.MIN_VALUE;
            int tl = 0, br = 0, bl = 0, tr = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int i = queue[head++], x = i % sw, y = i / sw;
                count++;
                int sum = x + y, diff = x - y;
                if (sum < minSum) { minSum = sum; tl = i; }
                if (sum > maxSum) { maxSum = sum; br = i; }
                if (diff < minDiff) { minDiff = diff; bl = i; }
                if (diff > maxDiff) { maxDiff = diff; tr = i; }
                int[] next = {x > 0 ? i - 1 : -1, x < sw - 1 ? i + 1 : -1, y > 0 ? i - sw : -1, y < sh - 1 ? i + sw : -1};
                for (int n : next) if (n >= 0 && !seen[n] && lum[n] > threshold) { seen[n] = true; queue[tail++] = n; }
            }
            if (count > best) { best = count; bestCorners = new int[]{tl, tr, br, bl}; }
        }
        float area = best / (float) px.length;
        if (bestCorners == null || area < .15f || area > .97f) {
            float mx = w * .04f, my = h * .04f;
            return new float[]{mx, my, w - mx, my, w - mx, h - my, mx, h - my};
        }
        float[] out = new float[8];
        for (int k = 0; k < 4; k++) {
            out[k * 2] = Math.min(w, (bestCorners[k] % sw + .5f) / scale);
            out[k * 2 + 1] = Math.min(h, (bestCorners[k] / sw + .5f) / scale);
        }
        return out;
    }

    static int otsu(int[] hist, int total) {
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];
        double sumB = 0, best = -1;
        int wB = 0, threshold = 127;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += t * (double) hist[t];
            double mB = sumB / wB, mF = (sum - sumB) / wF, between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) { best = between; threshold = t; }
        }
        return threshold;
    }

    /** Endireita o quadrilátero (cantos: superior esquerdo, superior direito, inferior direito, inferior esquerdo). */
    static Bitmap warp(Bitmap src, float[] q, int filter) {
        float top = dist(q, 0, 1), bottom = dist(q, 3, 2), left = dist(q, 0, 3), right = dist(q, 1, 2);
        float w = Math.max(top, bottom), h = Math.max(left, right);
        float s = Math.min(1f, 2480f / Math.max(w, h));
        int ow = Math.max(16, Math.round(w * s)), oh = Math.max(16, Math.round(h * s));
        Matrix m = new Matrix();
        m.setPolyToPoly(q, 0, new float[]{0, 0, ow, 0, ow, oh, 0, oh}, 0, 4);
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        ColorMatrix filterMatrix = filter(filter);
        if (filterMatrix != null) p.setColorFilter(new ColorMatrixColorFilter(filterMatrix));
        c.drawBitmap(src, m, p);
        return out;
    }

    static ColorMatrix filter(int kind) {
        if (kind == 2) return null;
        ColorMatrix m = new ColorMatrix();
        if (kind == 0) m.setSaturation(0);
        float contrast = kind == 0 ? 1.6f : 1.15f, shift = (1 - contrast) * 128 + (kind == 0 ? 28 : 6);
        m.postConcat(new ColorMatrix(new float[]{contrast, 0, 0, 0, shift, 0, contrast, 0, 0, shift, 0, 0, contrast, 0, shift, 0, 0, 0, 1, 0}));
        return m;
    }

    static float dist(float[] q, int a, int b) { return (float) Math.hypot(q[a * 2] - q[b * 2], q[a * 2 + 1] - q[b * 2 + 1]); }

    // ---- interface ----

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    TextView button(String text, boolean primary, View.OnClickListener click) {
        AppPalette p = AppPalette.of(this);
        TextView b = label(text, 13, primary ? p.onAccent : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(ThemeStyle.BOLD);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(primary ? p.accent : 0xff2a2a2a);
        bg.setCornerRadius(dp(14));
        b.setBackground(new android.graphics.drawable.InsetDrawable(bg, dp(3), 0, dp(3), 0));
        b.setOnClickListener(click);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setMaxLines(1);
        return b;
    }

    /** Mostra a fotografia e quatro cantos arrastáveis. */
    static final class CropView extends View {
        Bitmap bitmap;
        float[] corners;
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), handle = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix toView = new Matrix(), toImage = new Matrix();
        int dragging = -1;

        CropView(Context c, int accent) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            line.setColor(accent);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.5f * d);
            handle.setColor(accent);
            shade.setColor(0x88000000);
        }

        void set(Bitmap b, float[] q) { bitmap = b; corners = q; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            if (bitmap == null || corners == null) return;
            float d = getResources().getDisplayMetrics().density, pad = 18 * d;
            float s = Math.min((getWidth() - 2 * pad) / bitmap.getWidth(), (getHeight() - 2 * pad) / bitmap.getHeight());
            toView.reset();
            toView.postScale(s, s);
            toView.postTranslate((getWidth() - bitmap.getWidth() * s) / 2, (getHeight() - bitmap.getHeight() * s) / 2);
            toView.invert(toImage);
            canvas.drawBitmap(bitmap, toView, null);
            float[] v = corners.clone();
            toView.mapPoints(v);
            Path quad = new Path();
            quad.moveTo(v[0], v[1]);
            for (int i = 1; i < 4; i++) quad.lineTo(v[i * 2], v[i * 2 + 1]);
            quad.close();
            Path outside = new Path();
            outside.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            outside.op(quad, Path.Op.DIFFERENCE);
            canvas.drawPath(outside, shade);
            canvas.drawPath(quad, line);
            for (int i = 0; i < 4; i++) canvas.drawCircle(v[i * 2], v[i * 2 + 1], 11 * d, handle);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (bitmap == null || corners == null) return false;
            float[] pt = {e.getX(), e.getY()};
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float[] v = corners.clone();
                    toView.mapPoints(v);
                    float best = 48 * getResources().getDisplayMetrics().density;
                    dragging = -1;
                    for (int i = 0; i < 4; i++) {
                        float dd = (float) Math.hypot(v[i * 2] - pt[0], v[i * 2 + 1] - pt[1]);
                        if (dd < best) { best = dd; dragging = i; }
                    }
                    return dragging >= 0;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging < 0) return false;
                    toImage.mapPoints(pt);
                    corners[dragging * 2] = Math.max(0, Math.min(bitmap.getWidth(), pt[0]));
                    corners[dragging * 2 + 1] = Math.max(0, Math.min(bitmap.getHeight(), pt[1]));
                    invalidate();
                    return true;
                default:
                    dragging = -1;
                    return true;
            }
        }
    }
}
