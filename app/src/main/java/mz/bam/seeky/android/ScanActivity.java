package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Digitalizar documento, em três passos:
 * <ol>
 *   <li><b>Recortar</b> — folha detectada automaticamente, cantos arrastáveis com lupa, "Tudo" e "Rodar";</li>
 *   <li><b>Melhorar</b> — efeitos com pré-visualização (Documento, Melhorar, Sem sombras, Cinzento, Preto e branco,
 *   Vívido, Original), brilho, contraste, nitidez e rotação;</li>
 *   <li><b>Páginas</b> — mais páginas pela câmara ou pela galeria (várias de uma vez), reordenar, editar, apagar e
 *   exportar para PDF (A4, Carta ou tamanho da imagem) ou imagens JPG, em três qualidades, com texto por OCR.</li>
 * </ol>
 */
public final class ScanActivity extends Activity {
    static final int CAMERA = 1, GALLERY = 2, SAVE = 3, OPEN = 7120;
    static final int CROP = 0, EDIT = 1, PAGES = 2;
    static final int[][] QUALITY = {{2200, 92}, {1600, 82}, {1200, 68}};
    static final String[] QUALITY_NAMES = {"Alta", "Média", "Compacta"}, PAGE_NAMES = {"A4", "Carta", "Da foto"}, FORMATS = {"PDF", "Imagens JPG"};

    /** Uma página: fotografia original, cantos e definições (o resultado é sempre recalculado). */
    static final class Page {
        File photo;
        float[] corners;
        ScanFilters.Settings settings;
        Bitmap thumb;
        JSONObject json() throws Exception {
            JSONArray c = new JSONArray();
            for (float f : corners) c.put((double) f);
            return new JSONObject().put("photo", photo.getPath()).put("corners", c).put("settings", settings.encode());
        }
        static Page from(JSONObject o) {
            Page p = new Page();
            p.photo = new File(o.optString("photo"));
            JSONArray c = o.optJSONArray("corners");
            p.corners = new float[8];
            for (int i = 0; i < 8 && c != null; i++) p.corners[i] = (float) c.optDouble(i);
            p.settings = ScanFilters.Settings.decode(o.optString("settings"));
            return p;
        }
    }

    final List<Page> pages = new ArrayList<>();
    final ExecutorService work = Executors.newSingleThreadExecutor();
    final Handler main = new Handler(Looper.getMainLooper());
    final String hold = "scan-" + UUID.randomUUID();
    Page current;
    int editing = -1, state = PAGES, shots, generation, format, pageSize, quality = 1;
    Bitmap photo, preview;
    File shot, pendingOutput;
    String pendingName = "", pendingMime = "application/pdf";
    AppPalette palette;
    int accent, onAccent;
    TextView title, subtitle, badge;
    FrameLayout stage;
    CropView crop;
    ImageView previewView;
    ScrollView pagesView;
    LinearLayout tools, bottom;
    ProgressBar busy;

    static void open(MainActivity a) {
        try { a.startActivityForResult(new Intent(a, ScanActivity.class), OPEN); } catch (Exception e) { a.showError(e); }
    }

    /** Resultado na MainActivity: abre o PDF guardado no armazenamento. */
    static boolean result(MainActivity a, int request, int result, Intent data) {
        if (request != OPEN) return false;
        if (result == RESULT_OK && data != null && data.getStringExtra("path") != null) {
            File f = new File(data.getStringExtra("path"));
            if (f.isFile()) a.open(StorageAccess.entry(f, Environment.getExternalStorageDirectory()), false);
        }
        return true;
    }

    // ---- ciclo de vida ----

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        palette = AppPalette.of(this);
        accent = darkAccent(palette.accent);
        onAccent = AppPalette.contrast(Color.WHITE, accent) >= 4.5 ? Color.WHITE : 0xff111111;
        getWindow().setStatusBarColor(0xff101010);
        getWindow().setNavigationBarColor(0xff101010);
        buildLayout();
        File dir = ScanProvider.dir(this);
        if (saved == null) {
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            show(PAGES);
            capture();
        } else restore(saved);
    }

    @Override protected void onStart() { super.onStart(); QuickSearchService.holdOverlay(hold, true); UiPanel.visibility(this, true); }
    @Override protected void onStop() { QuickSearchService.holdOverlay(hold, false); UiPanel.visibility(this, false); super.onStop(); }

    @Override protected void onDestroy() {
        QuickSearchService.holdOverlay(hold, false);
        UiPanel.dismissAll(this);
        work.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        try {
            JSONArray list = new JSONArray();
            for (Page p : pages) list.put(p.json());
            out.putString("pages", list.toString());
            if (current != null) out.putString("current", current.json().toString());
        } catch (Exception ignored) {}
        out.putInt("state", state);
        out.putInt("editing", editing);
        out.putInt("shots", shots);
        if (shot != null) out.putString("shot", shot.getPath());
    }

    void restore(Bundle saved) {
        shots = saved.getInt("shots");
        editing = saved.getInt("editing", -1);
        if (saved.getString("shot") != null) shot = new File(saved.getString("shot"));
        try {
            JSONArray list = new JSONArray(saved.getString("pages", "[]"));
            for (int i = 0; i < list.length(); i++) { Page p = Page.from(list.getJSONObject(i)); if (p.photo.isFile()) pages.add(p); }
            if (saved.getString("current") != null) { current = Page.from(new JSONObject(saved.getString("current"))); if (!current.photo.isFile()) current = null; }
        } catch (Exception ignored) {}
        int s = saved.getInt("state", PAGES);
        if (current != null && (s == CROP || s == EDIT)) { photo = PdfTools.decode(current.photo, 2000); show(s); }
        else show(PAGES);
    }

    @Override public void onBackPressed() {
        if (state == CROP || state == EDIT) {
            current = null;
            editing = -1;
            if (pages.isEmpty()) confirmDiscard(); else show(PAGES);
        } else if (!pages.isEmpty()) confirmDiscard();
        else super.onBackPressed();
    }

    void confirmDiscard() {
        if (pages.isEmpty() && current == null) { finish(); return; }
        new UiDialog.Builder(this).setTitle("Descartar digitalização?").setMessage(AppLanguage.text(this, "As páginas ainda não foram guardadas."))
                .setPositiveButton("Descartar", (d, w) -> finish()).setNegativeButton("Continuar", null).show();
    }

    // ---- interface ----

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(AppLanguage.text(this, text));
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(ThemeStyle.BOLD);
        return t;
    }

    GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff101010);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(12), dp(14), dp(8));
        FrameLayout close = iconButton("close", v -> onBackPressed());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(8), 0, 0, 0);
        title = label("", 17, Color.WHITE, true);
        subtitle = label("", 12, 0xffb5b5b5, false);
        subtitle.setMaxLines(2);
        heading.addView(title);
        heading.addView(subtitle);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        badge = label("", 12, Color.WHITE, true);
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        badge.setBackground(round(0xff2c2c2c, 16));
        badge.setOnClickListener(v -> { if (state != PAGES) { current = null; editing = -1; show(PAGES); } });
        top.addView(badge);
        root.addView(top);

        stage = new FrameLayout(this);
        crop = new CropView(this, accent);
        previewView = new ImageView(this);
        previewView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewView.setPadding(dp(16), dp(8), dp(16), dp(8));
        pagesView = new ScrollView(this);
        stage.addView(crop, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(pagesView, new FrameLayout.LayoutParams(-1, -1));
        busy = new ProgressBar(this);
        busy.setVisibility(View.GONE);
        stage.addView(busy, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        root.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(14));
        bottom.setBackgroundColor(0xff181818);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    FrameLayout iconButton(String symbol, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(round(0xff242424, 22));
        f.addView(new Icons(this, symbol, Color.WHITE), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setOnClickListener(click);
        return f;
    }

    /** Botão de ferramenta: ícone por cima e etiqueta curta (nunca corta o texto). */
    LinearLayout tool(String symbol, String text, boolean active, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(8), dp(4), dp(8));
        b.setBackground(round(active ? (accent & 0xffffff) | 0x33000000 : Color.TRANSPARENT, 14));
        b.addView(new Icons(this, symbol, active ? accent : Color.WHITE), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView t = label(text, 11, active ? accent : 0xffdddddd, false);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(4), 0, 0);
        b.addView(t, new LinearLayout.LayoutParams(-2, -2));
        b.setOnClickListener(click);
        return b;
    }

    TextView primary(String text, View.OnClickListener click) {
        TextView t = label(text, 15, onAccent, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(accent, 24));
        t.setPadding(dp(18), 0, dp(18), 0);
        t.setMaxLines(1);
        t.setOnClickListener(click);
        return t;
    }

    void addTools(View... views) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(8), dp(4), dp(8), dp(4));
        for (View v : views) row.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(row);
    }

    void setBottom(View... secondary) {
        bottom.removeAllViews();
        for (View v : secondary) {
            if (v instanceof TextView && ((TextView) v).getBackground() != null) bottom.addView(v, new LinearLayout.LayoutParams(0, dp(48), 1.6f));
            else bottom.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void show(int s) {
        state = s;
        crop.setVisibility(s == CROP ? View.VISIBLE : View.GONE);
        previewView.setVisibility(s == EDIT ? View.VISIBLE : View.GONE);
        pagesView.setVisibility(s == PAGES ? View.VISIBLE : View.GONE);
        tools.removeAllViews();
        badge.setText(pages.size() + " " + AppLanguage.text(this, pages.size() == 1 ? "página" : "páginas"));
        badge.setVisibility(s == PAGES ? View.GONE : View.VISIBLE);
        int number = editing >= 0 ? editing + 1 : pages.size() + 1;
        if (s == CROP) {
            title.setText(AppLanguage.text(this, "Recortar") + " · " + AppLanguage.text(this, "página") + " " + number);
            subtitle.setText(AppLanguage.text(this, "Arraste os cantos até às pontas da folha. A lupa ajuda a acertar."));
            crop.set(photo, current == null ? null : current.corners);
            addTools(tool("scan", "Detectar", false, v -> { if (photo != null) { current.corners = detect(photo); crop.set(photo, current.corners); } }),
                    tool("fit", "Tudo", false, v -> { if (photo != null) { current.corners = full(photo); crop.set(photo, current.corners); } }),
                    tool("rotate", "Rodar foto", false, v -> rotatePhoto()));
            setBottom(tool("camera", "Repetir", false, v -> { current = null; capture(); }),
                    tool("image", "Galeria", false, v -> gallery()),
                    primary("Seguinte", v -> show(EDIT)));
        } else if (s == EDIT) {
            title.setText(AppLanguage.text(this, "Melhorar") + " · " + AppLanguage.text(this, "página") + " " + number);
            subtitle.setText(AppLanguage.text(this, ScanFilters.NAMES[current.settings.filter]) + " · " + AppLanguage.text(this, "toque num efeito para o aplicar"));
            buildEditTools();
            setBottom(tool("edit", "Recortar", false, v -> show(CROP)),
                    tool("rotate", "Rodar", false, v -> { current.settings.rotation = (current.settings.rotation + 90) % 360; refreshPreview(); }),
                    primary(editing >= 0 ? "Guardar" : "Adicionar página", v -> keepPage()));
            refreshPreview();
        } else {
            title.setText(pages.isEmpty() ? AppLanguage.text(this, "Digitalizar documento") : pages.size() + " " + AppLanguage.text(this, pages.size() == 1 ? "página" : "páginas"));
            subtitle.setText(AppLanguage.text(this, pages.isEmpty() ? "Fotografe uma folha ou escolha fotos da galeria." : "Toque numa página para editar, mover ou apagar."));
            buildPages();
            if (pages.isEmpty()) setBottom(tool("camera", "Câmara", false, v -> capture()), tool("image", "Galeria", false, v -> gallery()));
            else setBottom(tool("camera", "+ Câmara", false, v -> capture()), tool("image", "+ Galeria", false, v -> gallery()), primary("Exportar", v -> exportPanel()));
        }
    }

    // ---- passo 2: efeitos e ajustes ----

    LinearLayout adjustments;

    void buildEditTools() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(dp(10), dp(6), dp(10), dp(4));
        Bitmap sample = photo == null ? null : warp(photo, current.corners, 180);
        for (int f = 0; f < ScanFilters.NAMES.length; f++) {
            final int filter = f;
            boolean on = current.settings.filter == f;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            chip.setPadding(dp(4), dp(4), dp(4), dp(4));
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable frame = round(0xff2a2a2a, 10);
            if (on) frame.setStroke(dp(2.5f), accent);
            thumb.setBackground(frame);
            thumb.setPadding(dp(3), dp(3), dp(3), dp(3));
            if (sample != null) {
                ScanFilters.Settings s = current.settings.copy();
                s.filter = f;
                s.rotation = 0;
                s.sharpness = 0;
                thumb.setImageBitmap(ScanFilters.apply(sample, s));
            }
            chip.addView(thumb, new LinearLayout.LayoutParams(dp(58), dp(74)));
            TextView name = label(ScanFilters.NAMES[f], 10, on ? accent : 0xffdddddd, on);
            name.setPadding(0, dp(4), 0, 0);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            chip.addView(name, new LinearLayout.LayoutParams(dp(66), -2));
            chip.setOnClickListener(v -> {
                current.settings.filter = filter;
                getSharedPreferences("seeky", 0).edit().putInt("scan_filter", filter).apply();
                show(EDIT);
            });
            strip.addView(chip, new LinearLayout.LayoutParams(dp(74), -2));
        }
        scroll.addView(strip);
        tools.addView(scroll);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(8), 0, dp(8), dp(2));
        boolean open = adjustments != null && adjustments.getVisibility() == View.VISIBLE;
        adjustments = new LinearLayout(this);
        adjustments.setOrientation(LinearLayout.VERTICAL);
        adjustments.setPadding(dp(18), dp(2), dp(18), dp(4));
        adjustments.setVisibility(open ? View.VISIBLE : View.GONE);
        slider("Brilho", current.settings.brightness, -100, 100, v -> current.settings.brightness = v);
        slider("Contraste", current.settings.contrast, -100, 100, v -> current.settings.contrast = v);
        slider("Nitidez", current.settings.sharpness, 0, 100, v -> current.settings.sharpness = v);
        row.addView(tool("tune", "Ajustes", open, v -> { adjustments.setVisibility(adjustments.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tool("refresh", "Repor", false, v -> { int f = current.settings.filter; current.settings = new ScanFilters.Settings(); current.settings.filter = f; show(EDIT); }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tool("star", "Automático", false, v -> { current.settings.filter = ScanFilters.DOCUMENT; current.settings.brightness = 0; current.settings.contrast = 10; current.settings.sharpness = 40; show(EDIT); }), new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(adjustments);
        tools.addView(row);
    }

    interface IntSetter { void set(int v); }

    void slider(String name, int value, int min, int max, IntSetter setter) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = label(name, 12, 0xffdddddd, false);
        line.addView(l, new LinearLayout.LayoutParams(dp(78), -2));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        TextView v = label(String.valueOf(value), 12, Color.WHITE, true);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { v.setText(String.valueOf(p + min)); if (user) setter.set(p + min); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { refreshPreview(); }
        });
        line.addView(bar, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(v, new LinearLayout.LayoutParams(dp(36), -2));
        adjustments.addView(line);
    }

    void refreshPreview() {
        if (photo == null || current == null) return;
        final int gen = ++generation;
        final ScanFilters.Settings s = current.settings.copy();
        final float[] q = current.corners.clone();
        final Bitmap src = photo;
        busy.setVisibility(View.VISIBLE);
        work.execute(() -> {
            Bitmap out;
            try { out = ScanFilters.render(src, q, s, 1100); } catch (Throwable e) { out = null; }
            final Bitmap result = out;
            main.post(() -> {
                if (gen != generation || isFinishing()) return;
                busy.setVisibility(View.GONE);
                if (result != null) { preview = result; previewView.setImageBitmap(result); }
            });
        });
    }

    void keepPage() {
        Page p = current;
        if (p == null) return;
        p.thumb = null;
        if (editing >= 0 && editing < pages.size()) pages.set(editing, p); else pages.add(p);
        current = null;
        editing = -1;
        photo = null;
        show(PAGES);
    }

    void rotatePhoto() {
        if (photo == null || current == null) return;
        Matrix m = new Matrix();
        m.postRotate(90);
        Bitmap turned = Bitmap.createBitmap(photo, 0, 0, photo.getWidth(), photo.getHeight(), m, true);
        try (OutputStream out = new FileOutputStream(current.photo)) { turned.compress(Bitmap.CompressFormat.JPEG, 94, out); } catch (IOException ignored) {}
        photo = turned;
        current.corners = detect(photo);
        crop.set(photo, current.corners);
    }

    // ---- passo 3: páginas ----

    void buildPages() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(8), dp(14), dp(16));
        if (pages.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(80), dp(24), dp(24));
            FrameLayout icon = new FrameLayout(this);
            icon.setBackground(round((accent & 0xffffff) | 0x33000000, 36));
            icon.addView(new Icons(this, "scan", accent), new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            empty.addView(icon, new LinearLayout.LayoutParams(dp(88), dp(88)));
            TextView t = label("Nenhuma página ainda", 18, Color.WHITE, true);
            t.setPadding(0, dp(16), 0, dp(6));
            empty.addView(t);
            TextView d = label("Use a câmara para fotografar a folha, ou escolha várias fotos da galeria. O Seeky encontra a folha, endireita-a e limpa as sombras.", 13, 0xffb5b5b5, false);
            d.setGravity(Gravity.CENTER);
            empty.addView(d);
            list.addView(empty);
        }
        LinearLayout row = null;
        for (int i = 0; i < pages.size(); i++) {
            if (i % 2 == 0) { row = new LinearLayout(this); list.addView(row); }
            final int index = i;
            Page p = pages.get(i);
            FrameLayout cell = new FrameLayout(this);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setBackground(round(0xff222222, 12));
            img.setPadding(dp(6), dp(6), dp(6), dp(6));
            cell.addView(img, new FrameLayout.LayoutParams(-1, dp(220)));
            TextView number = label(String.valueOf(i + 1), 12, onAccent, true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(round(accent, 12));
            cell.addView(number, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.START));
            TextView effect = label(ScanFilters.NAMES[p.settings.filter], 10, Color.WHITE, false);
            effect.setPadding(dp(8), dp(3), dp(8), dp(3));
            effect.setBackground(round(0xaa000000, 10));
            FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            ep.setMargins(0, 0, dp(12), dp(12));
            cell.addView(effect, ep);
            cell.setOnClickListener(v -> pageMenu(index));
            row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            if (p.thumb != null) img.setImageBitmap(p.thumb);
            else work.execute(() -> {
                Bitmap src = PdfTools.decode(p.photo, 900);
                if (src == null) return;
                Bitmap t = ScanFilters.render(src, scale(p.corners, p.photo, src), p.settings, 420);
                src.recycle();
                p.thumb = t;
                main.post(() -> img.setImageBitmap(t));
            });
        }
        if (pages.size() % 2 == 1 && row != null) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        pagesView.removeAllViews();
        pagesView.addView(list);
    }

    /** Cantos guardados na escala da fotografia completa (2000 px) → escala de outra descodificação. */
    static float[] scale(float[] corners, File photo, Bitmap decoded) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photo.getPath(), o);
        int full = Math.min(2000, Math.max(o.outWidth, o.outHeight));
        float s = Math.max(decoded.getWidth(), decoded.getHeight()) / (float) Math.max(1, full);
        float[] out = new float[8];
        for (int i = 0; i < 8; i++) out[i] = corners[i] * s;
        return out;
    }

    void pageMenu(int index) {
        UiPanel p = new UiPanel(this, AppLanguage.text(this, "Página") + " " + (index + 1), AppLanguage.text(this, ScanFilters.NAMES[pages.get(index).settings.filter]), "pdf");
        p.action("edit", "Efeitos e ajustes", "Mudar o efeito, o brilho, o contraste ou a nitidez", false, () -> editPage(index, EDIT));
        p.action("fit", "Recortar de novo", "Ajustar os cantos da folha", false, () -> editPage(index, CROP));
        if (index > 0) p.action("arrow", "Mover para trás", AppLanguage.text(this, "Passa a ser a página") + " " + index, false, () -> { Collections.swap(pages, index, index - 1); show(PAGES); });
        if (index < pages.size() - 1) p.action("arrow", "Mover para a frente", AppLanguage.text(this, "Passa a ser a página") + " " + (index + 2), false, () -> { Collections.swap(pages, index, index + 1); show(PAGES); });
        p.action("copy", "Duplicar página", "Útil para guardar com outro efeito", false, () -> {
            Page src = pages.get(index), copy = new Page();
            copy.photo = src.photo; copy.corners = src.corners.clone(); copy.settings = src.settings.copy();
            pages.add(index + 1, copy);
            show(PAGES);
        });
        p.danger("Apagar página", () -> { pages.remove(index); show(PAGES); });
        p.show();
    }

    void editPage(int index, int target) {
        Page src = pages.get(index);
        current = new Page();
        current.photo = src.photo;
        current.corners = src.corners.clone();
        current.settings = src.settings.copy();
        editing = index;
        photo = PdfTools.decode(current.photo, 2000);
        show(target);
    }

    // ---- câmara e galeria ----

    ScanFilters.Settings defaults() {
        ScanFilters.Settings s = new ScanFilters.Settings();
        s.filter = getSharedPreferences("seeky", 0).getInt("scan_filter", ScanFilters.DOCUMENT);
        return s;
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
        catch (ActivityNotFoundException | SecurityException e) { PlusUi.toast(this, "Não há aplicação de câmara. Escolha fotos da galeria."); gallery(); }
    }

    void gallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, GALLERY); } catch (Exception e) { PlusUi.toast(this, "Não há galeria disponível."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE) { saved(result, data); return; }
        if (result != RESULT_OK) { if (state != PAGES && current == null) show(PAGES); return; }
        if (request == CAMERA) {
            if (shot == null || shot.length() == 0) { PlusUi.toast(this, "A câmara não devolveu a fotografia. Tente a galeria."); return; }
            Page p = newPage(shot);
            if (p == null) { PlusUi.toast(this, "Não foi possível abrir a fotografia."); return; }
            current = p;
            editing = -1;
            show(CROP);
        } else if (request == GALLERY && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            importImages(uris);
        }
    }

    /** Normaliza a fotografia (rotação EXIF, até 2000 px) e detecta a folha. */
    Page newPage(File source) {
        Bitmap b = PdfTools.decode(source, 2000);
        if (b == null) return null;
        File stored = new File(ScanProvider.dir(this), "photo_" + UUID.randomUUID() + ".jpg");
        try (OutputStream out = new FileOutputStream(stored)) { b.compress(Bitmap.CompressFormat.JPEG, 94, out); } catch (IOException e) { return null; }
        if (!source.equals(stored)) source.delete();
        Page p = new Page();
        p.photo = stored;
        p.corners = detect(b);
        p.settings = defaults();
        photo = b;
        return p;
    }

    void importImages(List<Uri> uris) {
        if (uris.isEmpty()) return;
        if (uris.size() == 1 && pages.isEmpty() && state != CROP) {
            File f = copyIn(uris.get(0));
            Page p = f == null ? null : newPage(f);
            if (p == null) { PlusUi.toast(this, "Não foi possível abrir a imagem."); return; }
            current = p;
            editing = -1;
            show(CROP);
            return;
        }
        busy.setVisibility(View.VISIBLE);
        PlusUi.toast(this, AppLanguage.text(this, "A preparar") + " " + uris.size() + " " + AppLanguage.text(this, "imagens…"));
        work.execute(() -> {
            List<Page> added = new ArrayList<>();
            for (Uri u : uris) {
                File f = copyIn(u);
                if (f == null) continue;
                Bitmap b = PdfTools.decode(f, 2000);
                if (b == null) { f.delete(); continue; }
                File stored = new File(ScanProvider.dir(this), "photo_" + UUID.randomUUID() + ".jpg");
                try (OutputStream out = new FileOutputStream(stored)) { b.compress(Bitmap.CompressFormat.JPEG, 94, out); } catch (IOException e) { continue; }
                f.delete();
                Page p = new Page();
                p.photo = stored;
                p.corners = detect(b);
                p.settings = defaults();
                b.recycle();
                added.add(p);
            }
            main.post(() -> {
                busy.setVisibility(View.GONE);
                if (current != null && state != PAGES) { if (editing >= 0 && editing < pages.size()) pages.set(editing, current); else pages.add(current); current = null; photo = null; editing = -1; }
                pages.addAll(added);
                PlusUi.toast(this, added.size() + " " + AppLanguage.text(this, "páginas adicionadas. Toque numa para ajustar."));
                show(PAGES);
            });
        });
    }

    File copyIn(Uri uri) {
        File f = new File(ScanProvider.dir(this), "shot_" + (++shots) + ".jpg");
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
            if (in == null) return null;
            PdfTools.copy(in, out);
            return f;
        } catch (Exception e) { f.delete(); return null; }
    }

    // ---- exportação ----

    void exportPanel() {
        if (pages.isEmpty()) return;
        UiPanel p = new UiPanel(this, "Exportar digitalização", pages.size() + " " + AppLanguage.text(this, pages.size() == 1 ? "página" : "páginas"), "pdf");
        p.section("Nome do ficheiro");
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText("Digitalização " + new SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.ROOT).format(new Date()));
        name.setTextColor(p.ink);
        p.content.addView(name);
        p.section("Formato");
        p.content.addView(choices(p, FORMATS, format, v -> format = v));
        p.section("Tamanho da página (PDF)");
        p.content.addView(choices(p, PAGE_NAMES, pageSize, v -> pageSize = v));
        p.section("Qualidade");
        p.content.addView(choices(p, QUALITY_NAMES, quality, v -> quality = v));
        p.note(StorageAccess.all(this) ? "Guardado em Download/Seeky/Digitalizações." : "Vai escolher onde guardar.");
        p.action("check", "Guardar", "Com as opções escolhidas", false, () -> export(WifiTransfer.cleanName(name.getText().toString().trim().isEmpty() ? "Digitalização" : name.getText().toString().trim()), false));
        p.action("share", "Guardar e partilhar", "WhatsApp, e-mail, Drive…", false, () -> export(WifiTransfer.cleanName(name.getText().toString().trim().isEmpty() ? "Digitalização" : name.getText().toString().trim()), true));
        p.action("type", "Extrair texto (OCR)", "Reconhece o texto das páginas para copiar ou partilhar", false, this::ocr);
        p.show();
    }

    LinearLayout choices(UiPanel p, String[] names, int selected, IntSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, 0, 0, dp(4));
        List<TextView> all = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            TextView t = label(names[i], 13, i == selected ? onAccent : p.ink, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(6), dp(10), dp(6), dp(10));
            t.setMaxLines(1);
            t.setBackground(round(i == selected ? accent : palette.tint, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = i < names.length - 1 ? dp(6) : 0;
            row.addView(t, lp);
            all.add(t);
            t.setOnClickListener(v -> {
                setter.set(index);
                for (int k = 0; k < all.size(); k++) {
                    all.get(k).setTextColor(k == index ? onAccent : p.ink);
                    all.get(k).setBackground(round(k == index ? accent : palette.tint, 12));
                }
            });
        }
        return row;
    }

    /** Desenha as páginas na qualidade escolhida e grava PDF ou JPG. */
    void export(String name, boolean share) {
        busy.setVisibility(View.VISIBLE);
        PlusUi.toast(this, "A criar…");
        final int fmt = format, size = pageSize, q = quality;
        final List<Page> list = new ArrayList<>(pages);
        work.execute(() -> {
            try {
                File dir = new File(ScanProvider.dir(this), "export");
                PrivateFolder.delete(dir);
                dir.mkdirs();
                List<File> images = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    Page p = list.get(i);
                    Bitmap src = PdfTools.decode(p.photo, 2000);
                    if (src == null) continue;
                    Bitmap page = ScanFilters.render(src, scale(p.corners, p.photo, src), p.settings, QUALITY[q][0]);
                    src.recycle();
                    File f = new File(dir, String.format(Locale.ROOT, "%s - %02d.jpg", name, i + 1));
                    try (OutputStream out = new FileOutputStream(f)) { page.compress(Bitmap.CompressFormat.JPEG, QUALITY[q][1], out); }
                    page.recycle();
                    images.add(f);
                }
                if (images.isEmpty()) throw new IOException("Nenhuma página legível.");
                if (fmt == 0) {
                    File pdf = new File(dir, name + ".pdf");
                    PdfTools.init(this);
                    PdfTools.images(images, pdf, size, QUALITY[q][1] / 100f, size == 2 ? 0 : 12);
                    deliver(Collections.singletonList(pdf), "application/pdf", share);
                } else deliver(images, "image/jpeg", share);
            } catch (Throwable e) {
                main.post(() -> { busy.setVisibility(View.GONE); new UiDialog.Builder(this).setTitle("Não foi possível exportar").setMessage(e.getMessage() == null ? AppLanguage.text(this, "Liberte espaço e tente novamente.") : e.getMessage()).setPositiveButton("Fechar", null).show(); });
            }
        });
    }

    void deliver(List<File> files, String mime, boolean share) throws IOException {
        if (StorageAccess.all(this)) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky/Digitalizações");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar Download/Seeky/Digitalizações.");
            List<File> saved = new ArrayList<>();
            for (File f : files) {
                File target = WifiTransfer.unique(dir, f.getName());
                try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(target)) { PdfTools.copy(in, out); }
                saved.add(target);
            }
            String[] paths = new String[saved.size()];
            for (int i = 0; i < saved.size(); i++) paths[i] = saved.get(i).getPath();
            android.media.MediaScannerConnection.scanFile(getApplicationContext(), paths, null, null);
            main.post(() -> { busy.setVisibility(View.GONE); done(saved, mime, share); });
        } else if (files.size() == 1) {
            main.post(() -> {
                busy.setVisibility(View.GONE);
                pendingOutput = files.get(0);
                pendingMime = mime;
                pendingName = files.get(0).getName();
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, pendingName);
                try { startActivityForResult(i, SAVE); } catch (Exception e) { PlusUi.toast(this, "Não foi possível guardar."); }
            });
        } else {
            // Sem acesso geral: as imagens vão para Imagens/Seeky pela MediaStore (Android 10+).
            List<Uri> uris = new ArrayList<>();
            for (File f : files) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                if (android.os.Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Seeky");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (u == null) throw new IOException("Active o acesso aos ficheiros para guardar várias imagens.");
                try (InputStream in = new FileInputStream(f); OutputStream out = getContentResolver().openOutputStream(u)) { if (out == null) throw new IOException(); PdfTools.copy(in, out); }
                uris.add(u);
            }
            main.post(() -> {
                busy.setVisibility(View.GONE);
                PlusUi.toast(this, AppLanguage.text(this, "Guardado em") + " Imagens/Seeky");
                if (share) shareUris(uris, mime);
                else finish();
            });
        }
    }

    void done(List<File> saved, String mime, boolean share) {
        if (share) {
            List<Uri> uris = new ArrayList<>();
            for (File f : saved) uris.add(Uri.parse("content://" + getPackageName() + ".files/file/" + Uri.encode(f.getPath())));
            shareUris(uris, mime);
        }
        UiDialog.Builder b = new UiDialog.Builder(this);
        b.setTitle("Digitalização guardada").setMessage(AppLanguage.text(this, "Guardado em") + " Download/Seeky/Digitalizações\n\n" + saved.get(0).getName() + (saved.size() > 1 ? " +" + (saved.size() - 1) : ""));
        if (mime.equals("application/pdf")) b.setPositiveButton("Abrir", (d, w) -> { setResult(RESULT_OK, new Intent().putExtra("path", saved.get(0).getPath())); finish(); });
        else b.setPositiveButton("Concluir", (d, w) -> finish());
        b.setNegativeButton("Continuar a digitalizar", null).show();
    }

    void shareUris(List<Uri> uris, String mime) {
        Intent send = uris.size() == 1 ? new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.get(0))
                : new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
        send.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip = ClipData.newRawUri("", uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        send.setClipData(clip);
        try { startActivity(Intent.createChooser(send, AppLanguage.text(this, "Partilhar"))); } catch (Exception e) { PlusUi.toast(this, "Não há aplicações para partilhar."); }
    }

    void saved(int result, Intent data) {
        if (result != RESULT_OK || data == null || data.getData() == null || pendingOutput == null) return;
        try (InputStream in = new FileInputStream(pendingOutput); OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException();
            PdfTools.copy(in, out);
            PlusUi.toast(this, "Guardado.");
            finish();
        } catch (IOException e) { PlusUi.toast(this, "Não foi possível guardar."); }
    }

    void ocr() {
        busy.setVisibility(View.VISIBLE);
        PlusUi.toast(this, "A reconhecer o texto…");
        final List<Page> list = new ArrayList<>(pages);
        work.execute(() -> {
            StringBuilder text = new StringBuilder();
            String error = null;
            try {
                OcrEngine engine = OcrEngine.create();
                for (int i = 0; i < list.size(); i++) {
                    Page p = list.get(i);
                    Bitmap src = PdfTools.decode(p.photo, 2000);
                    if (src == null) continue;
                    ScanFilters.Settings s = p.settings.copy();
                    if (s.filter == ScanFilters.ORIGINAL || s.filter == ScanFilters.VIVID) s.filter = ScanFilters.DOCUMENT;
                    Bitmap page = ScanFilters.render(src, scale(p.corners, p.photo, src), s, 1800);
                    src.recycle();
                    final int n = i + 1;
                    String t = engine.recognise(this, page, "por", () -> isFinishing(), state -> main.post(() -> subtitle.setText(AppLanguage.text(this, "Página") + " " + n + " · " + state)));
                    page.recycle();
                    if (list.size() > 1) text.append("— ").append(AppLanguage.text(this, "Página")).append(' ').append(n).append(" —\n");
                    text.append(t == null ? "" : t.trim()).append("\n\n");
                }
            } catch (Throwable e) { error = e.getMessage() == null ? AppLanguage.text(this, "O reconhecimento falhou.") : e.getMessage(); }
            final String result = text.toString().trim(), fail = error;
            main.post(() -> {
                busy.setVisibility(View.GONE);
                show(state);
                if (fail != null && result.isEmpty()) { new UiDialog.Builder(this).setTitle("Extrair texto").setMessage(fail).setPositiveButton("Fechar", null).show(); return; }
                new UiDialog.Builder(this).setTitle("Texto reconhecido").setMessage(result.isEmpty() ? AppLanguage.text(this, "Não foi encontrado texto.") : result)
                        .setPositiveButton("Copiar", (d, w) -> {
                            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Seeky", result));
                            PlusUi.toast(this, "Texto copiado.");
                        })
                        .setNeutralButton("Partilhar", (d, w) -> {
                            try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result), AppLanguage.text(this, "Partilhar"))); } catch (Exception ignored) {}
                        }).setNegativeButton("Fechar", null).show();
            });
        });
    }

    // ---- imagem: detecção e perspectiva ----

    static float[] full(Bitmap b) { return new float[]{0, 0, b.getWidth(), 0, b.getWidth(), b.getHeight(), 0, b.getHeight()}; }

    static float[] inset(int w, int h) {
        float mx = w * .04f, my = h * .04f;
        return new float[]{mx, my, w - mx, my, w - mx, h - my, mx, h - my};
    }

    /**
     * Encontra a folha comparando três candidatos — região clara, região escura e região com cor
     * diferente da borda da fotografia — e escolhe a que mais se parece com uma folha: área
     * razoável, forma rectangular e pouco contacto com as margens. Sem candidato, margem de 4 %.
     */
    static float[] detect(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        float scale = Math.min(1f, 220f / Math.max(w, h));
        int sw = Math.max(8, Math.round(w * scale)), sh = Math.max(8, Math.round(h * scale));
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        if (small != src) small.recycle();
        int n = px.length;
        int[] lum = new int[n], hist = new int[256];
        for (int i = 0; i < n; i++) { lum[i] = ScanFilters.luma(px[i]); hist[lum[i]]++; }
        int t = otsu(hist, n);
        long br = 0, bg = 0, bb = 0;
        int border = 0;
        for (int i = 0; i < n; i++) {
            int x = i % sw, y = i / sw;
            if (x < 2 || y < 2 || x >= sw - 2 || y >= sh - 2) { br += Color.red(px[i]); bg += Color.green(px[i]); bb += Color.blue(px[i]); border++; }
        }
        int mr = (int) (br / border), mg = (int) (bg / border), mb = (int) (bb / border);
        int[] dist = new int[n], dh = new int[256];
        for (int i = 0; i < n; i++) {
            int d = Math.min(255, (Math.abs(Color.red(px[i]) - mr) + Math.abs(Color.green(px[i]) - mg) + Math.abs(Color.blue(px[i]) - mb)) / 2);
            dist[i] = d;
            dh[d]++;
        }
        int td = Math.max(18, otsu(dh, n));
        boolean[][] masks = new boolean[3][n];
        for (int i = 0; i < n; i++) { masks[0][i] = lum[i] > t; masks[1][i] = lum[i] <= t; masks[2][i] = dist[i] > td; }
        float best = 0;
        int[] corners = null;
        for (boolean[] mask : masks) {
            int[] c = component(mask, sw, sh);
            if (c == null) continue;
            float area = c[4] / (float) n;
            if (area < .12f || area > .97f) continue;
            float quad = quadArea(c, sw);
            float fill = quad <= 0 ? 0 : Math.min(1f, c[4] / quad);
            float touch = c[5] / (float) (2 * (sw + sh));
            float score = fill * fill * (float) Math.sqrt(area) * (1f - Math.min(.9f, touch * 3));
            if (score > best) { best = score; corners = c; }
        }
        if (corners == null || best < .2f) return inset(w, h);
        float[] out = new float[8];
        for (int k = 0; k < 4; k++) {
            out[k * 2] = Math.min(w, (corners[k] % sw + .5f) / scale);
            out[k * 2 + 1] = Math.min(h, (corners[k] / sw + .5f) / scale);
        }
        return out;
    }

    /** Maior região ligada da máscara: {tl, tr, br, bl, área, píxeis na margem}. */
    static int[] component(boolean[] mask, int sw, int sh) {
        int n = mask.length;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int[] best = null;
        for (int start = 0; start < n; start++) {
            if (seen[start] || !mask[start]) continue;
            int head = 0, tail = 0, count = 0, edge = 0;
            int minSum = Integer.MAX_VALUE, maxSum = Integer.MIN_VALUE, minDiff = Integer.MAX_VALUE, maxDiff = Integer.MIN_VALUE, tl = 0, br = 0, bl = 0, tr = 0;
            queue[tail++] = start;
            seen[start] = true;
            while (head < tail) {
                int i = queue[head++], x = i % sw, y = i / sw;
                count++;
                if (x == 0 || y == 0 || x == sw - 1 || y == sh - 1) edge++;
                int sum = x + y, diff = x - y;
                if (sum < minSum) { minSum = sum; tl = i; }
                if (sum > maxSum) { maxSum = sum; br = i; }
                if (diff < minDiff) { minDiff = diff; bl = i; }
                if (diff > maxDiff) { maxDiff = diff; tr = i; }
                if (x > 0 && !seen[i - 1] && mask[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1; }
                if (x < sw - 1 && !seen[i + 1] && mask[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1; }
                if (y > 0 && !seen[i - sw] && mask[i - sw]) { seen[i - sw] = true; queue[tail++] = i - sw; }
                if (y < sh - 1 && !seen[i + sw] && mask[i + sw]) { seen[i + sw] = true; queue[tail++] = i + sw; }
            }
            if (best == null || count > best[4]) best = new int[]{tl, tr, br, bl, count, edge};
        }
        return best;
    }

    static float quadArea(int[] c, int sw) {
        float a = 0;
        for (int k = 0; k < 4; k++) {
            int p = c[k], q = c[(k + 1) % 4];
            a += (p % sw) * (q / sw) - (q % sw) * (p / sw);
        }
        return Math.abs(a) / 2f;
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

    /** Endireita o quadrilátero (sup. esq., sup. dir., inf. dir., inf. esq.) com o lado maior até {@code maxSide}. */
    static Bitmap warp(Bitmap src, float[] q, int maxSide) {
        float top = dist(q, 0, 1), bottom = dist(q, 3, 2), left = dist(q, 0, 3), right = dist(q, 1, 2);
        float w = Math.max(top, bottom), h = Math.max(left, right);
        float s = Math.min(maxSide / Math.max(1f, Math.max(w, h)), 1.6f);
        int ow = Math.max(16, Math.round(w * s)), oh = Math.max(16, Math.round(h * s));
        Matrix m = new Matrix();
        m.setPolyToPoly(q, 0, new float[]{0, 0, ow, 0, ow, oh, 0, oh}, 0, 4);
        Bitmap out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    static float dist(float[] q, int a, int b) { return (float) Math.hypot(q[a * 2] - q[b * 2], q[a * 2 + 1] - q[b * 2 + 1]); }

    /** Fotografia com quatro cantos arrastáveis, contorno, linhas a meio e lupa ao arrastar. */
    static final class CropView extends View {
        Bitmap bitmap;
        float[] corners;
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), handle = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                shade = new Paint(Paint.ANTI_ALIAS_FLAG), guide = new Paint(Paint.ANTI_ALIAS_FLAG), lens = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Matrix toView = new Matrix(), toImage = new Matrix();
        int dragging = -1;
        float touchX, touchY;

        CropView(Context c, int accent) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            line.setColor(accent);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.5f * d);
            handle.setColor(accent);
            ring.setColor(Color.WHITE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2 * d);
            shade.setColor(0x99000000);
            guide.setColor(0x66ffffff);
            guide.setStrokeWidth(d);
        }

        void set(Bitmap b, float[] q) { bitmap = b; corners = q; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            if (bitmap == null || corners == null) return;
            float d = getResources().getDisplayMetrics().density, pad = 22 * d;
            float s = Math.min((getWidth() - 2 * pad) / bitmap.getWidth(), (getHeight() - 2 * pad) / bitmap.getHeight());
            toView.reset();
            toView.postScale(s, s);
            toView.postTranslate((getWidth() - bitmap.getWidth() * s) / 2, (getHeight() - bitmap.getHeight() * s) / 2);
            toView.invert(toImage);
            canvas.drawBitmap(bitmap, toView, lens);
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
            for (float f : new float[]{1 / 3f, 2 / 3f}) {
                canvas.drawLine(v[0] + (v[6] - v[0]) * f, v[1] + (v[7] - v[1]) * f, v[2] + (v[4] - v[2]) * f, v[3] + (v[5] - v[3]) * f, guide);
                canvas.drawLine(v[0] + (v[2] - v[0]) * f, v[1] + (v[3] - v[1]) * f, v[6] + (v[4] - v[6]) * f, v[7] + (v[5] - v[7]) * f, guide);
            }
            canvas.drawPath(quad, line);
            for (int i = 0; i < 4; i++) {
                canvas.drawCircle(v[i * 2], v[i * 2 + 1], (dragging == i ? 15 : 12) * d, handle);
                canvas.drawCircle(v[i * 2], v[i * 2 + 1], (dragging == i ? 15 : 12) * d, ring);
            }
            if (dragging >= 0) drawLens(canvas, v, d);
        }

        /** Lupa 2,5× no canto oposto ao dedo, com mira no ponto exacto. */
        void drawLens(Canvas canvas, float[] v, float d) {
            float r = 52 * d, cx = touchX < getWidth() / 2f ? getWidth() - r - 14 * d : r + 14 * d, cy = r + 14 * d;
            canvas.save();
            Path circle = new Path();
            circle.addCircle(cx, cy, r, Path.Direction.CW);
            canvas.clipPath(circle);
            canvas.drawColor(Color.BLACK);
            Matrix zoom = new Matrix(toView);
            float px = v[dragging * 2], py = v[dragging * 2 + 1];
            zoom.postTranslate(-px, -py);
            zoom.postScale(2.5f, 2.5f);
            zoom.postTranslate(cx, cy);
            canvas.drawBitmap(bitmap, zoom, lens);
            canvas.restore();
            canvas.drawCircle(cx, cy, r, ring);
            canvas.drawLine(cx - 10 * d, cy, cx + 10 * d, cy, line);
            canvas.drawLine(cx, cy - 10 * d, cx, cy + 10 * d, line);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (bitmap == null || corners == null) return false;
            float[] pt = {e.getX(), e.getY()};
            touchX = e.getX();
            touchY = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float[] v = corners.clone();
                    toView.mapPoints(v);
                    float best = 56 * getResources().getDisplayMetrics().density;
                    dragging = -1;
                    for (int i = 0; i < 4; i++) {
                        float dd = (float) Math.hypot(v[i * 2] - pt[0], v[i * 2 + 1] - pt[1]);
                        if (dd < best) { best = dd; dragging = i; }
                    }
                    if (dragging >= 0) getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
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
                    invalidate();
                    return true;
            }
        }
    }

    /** Cor de destaque legível sobre fundo escuro: aclara as cores escuras dos temas claros. */
    static int darkAccent(int accent) {
        int c = accent;
        for (int i = 0; i < 10 && AppPalette.contrast(c, 0xff151515) < 4.5; i++) c = ThemeColors.blend(c, Color.WHITE, .2f);
        return c;
    }
}
