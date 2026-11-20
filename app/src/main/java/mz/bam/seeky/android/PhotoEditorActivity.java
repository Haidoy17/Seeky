package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Editor de fotografias aberto a partir do visualizador de imagens.
 *
 * <ul>
 *   <li><b>Recortar</b>: proporções (Livre, Original, 1:1, 4:3, 3:4, 16:9, 9:16), rodar, espelhar,
 *   endireitar ±45° com grelha e perspectiva vertical/horizontal;</li>
 *   <li><b>Ajustar</b>: brilho, contraste, saturação, calor, matiz, realces, sombras, nitidez, vinheta e grão,
 *   mais Automático;</li>
 *   <li><b>Filtros</b>: 12 filtros com miniatura e intensidade;</li>
 *   <li><b>Desenhar</b>: pincel e marcador com cores e espessura;</li>
 *   <li><b>Texto</b>: escrever, arrastar, cor, tamanho e fundo.</li>
 * </ul>
 * Desfazer/refazer, comparar com o original (manter premido) e guardar como cópia, substituir
 * o original ou partilhar. O original nunca é alterado sem confirmação.
 */
public final class PhotoEditorActivity extends Activity {
    static final int CROP = 0, ADJUST = 1, FILTERS = 2, DRAW = 3, TEXT = 4;
    static final String[] TABS = {"Recortar", "Ajustar", "Filtros", "Desenhar", "Texto"};
    static final String[] TAB_ICONS = {"fit", "tune", "palette", "edit", "type"};
    static final String[] RATIOS = {"Livre", "Original", "1:1", "4:3", "3:4", "16:9", "9:16"};
    static final float[] RATIO_VALUES = {0, -1, 1, 4 / 3f, 3 / 4f, 16 / 9f, 9 / 16f};
    static final int[] COLORS = {Color.WHITE, Color.BLACK, 0xffe53935, 0xfffb8c00, 0xfffdd835, 0xff43a047, 0xff1e88e5, 0xff8e24aa};

    Uri uri;
    String name, mime;
    File source;
    Bitmap preview, small, rendered;
    PhotoEdit.State state = new PhotoEdit.State();
    final List<PhotoEdit.State> undo = new ArrayList<>(), redo = new ArrayList<>();
    int tab = ADJUST, adjusting = PhotoEdit.BRIGHTNESS, color = Color.WHITE, generation;
    float ratio, brush = .012f;
    boolean marker, comparing;
    PhotoEdit.Label selected;
    AppPalette palette;
    int accent, onAccent;
    EditorView view;
    LinearLayout tools, tabs;
    ImageButtonLike undoButton, redoButton;
    ProgressBar busy;
    final ExecutorService work = Executors.newSingleThreadExecutor();
    final Handler main = new Handler(Looper.getMainLooper());
    final String hold = "photo-" + UUID.randomUUID();

    static void open(Activity a, Uri uri, String name, String mime) {
        try { a.startActivity(new Intent(a, PhotoEditorActivity.class).setDataAndType(uri, mime).putExtra("name", name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
        catch (Exception e) { PlusUi.toast(a, "Não foi possível abrir o editor."); }
    }

    // ---- ciclo de vida ----

    @Override protected void attachBaseContext(Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        name = getIntent().getStringExtra("name");
        if (name == null) name = "Foto.jpg";
        mime = getIntent().getType() == null ? "image/jpeg" : getIntent().getType();
        palette = AppPalette.of(this);
        accent = darkAccent(palette.accent);
        onAccent = AppPalette.contrast(Color.WHITE, accent) >= 4.5 ? Color.WHITE : 0xff111111;
        getWindow().setStatusBarColor(0xff0e0e0e);
        getWindow().setNavigationBarColor(0xff0e0e0e);
        build();
        busy.setVisibility(View.VISIBLE);
        work.execute(() -> {
            try {
                File dir = new File(getCacheDir(), "photo-editor");
                PrivateFolder.delete(dir);
                dir.mkdirs();
                File f = new File(dir, "source");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
                    if (in == null) throw new IOException();
                    PdfTools.copy(in, out);
                }
                Bitmap p = PdfTools.decode(f, 1400);
                if (p == null) throw new IOException();
                Bitmap s = PdfTools.fit(p, 360);
                main.post(() -> { source = f; preview = p; small = s == p ? Bitmap.createBitmap(p) : s; showTab(tab); });
            } catch (Throwable e) {
                main.post(() -> { PlusUi.toast(this, "Não foi possível abrir esta imagem para edição."); finish(); });
            }
        });
    }

    @Override protected void onStart() { super.onStart(); QuickSearchService.holdOverlay(hold, true); UiPanel.visibility(this, true); }
    @Override protected void onStop() { QuickSearchService.holdOverlay(hold, false); UiPanel.visibility(this, false); super.onStop(); }
    @Override protected void onDestroy() { QuickSearchService.holdOverlay(hold, false); UiPanel.dismissAll(this); work.shutdownNow(); super.onDestroy(); }

    @Override public void onBackPressed() {
        if (!state.changed()) { super.onBackPressed(); return; }
        new UiDialog.Builder(this).setTitle("Descartar alterações?").setMessage(AppLanguage.text(this, "As edições ainda não foram guardadas."))
                .setPositiveButton("Descartar", (d, w) -> finish()).setNegativeButton("Continuar a editar", null).show();
    }

    // ---- histórico ----

    void commit() {
        undo.add(state.copy());
        if (undo.size() > 40) undo.remove(0);
        redo.clear();
        refreshHistory();
    }

    /** Guarda o estado actual no histórico antes de uma alteração. */
    void before() { commit(); }

    void undo() {
        if (undo.isEmpty()) return;
        redo.add(state.copy());
        state = undo.remove(undo.size() - 1);
        selected = null;
        refreshHistory();
        showTab(tab);
    }

    void redo() {
        if (redo.isEmpty()) return;
        undo.add(state.copy());
        state = redo.remove(redo.size() - 1);
        selected = null;
        refreshHistory();
        showTab(tab);
    }

    void refreshHistory() {
        if (undoButton != null) undoButton.setEnabledLook(!undo.isEmpty());
        if (redoButton != null) redoButton.setEnabledLook(!redo.isEmpty());
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

    GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    /** Botão de ícone que mostra o estado activo/inactivo. */
    final class ImageButtonLike extends FrameLayout {
        final Icons icon;
        ImageButtonLike(String symbol, String description, View.OnClickListener click) {
            super(PhotoEditorActivity.this);
            icon = new Icons(PhotoEditorActivity.this, symbol, Color.WHITE);
            addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            setBackground(round(0xff232323, 22));
            setContentDescription(AppLanguage.text(PhotoEditorActivity.this, description));
            setOnClickListener(click);
        }
        void setEnabledLook(boolean on) { setAlpha(on ? 1f : .35f); setEnabled(on); }
    }

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff0e0e0e);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(10), dp(10), dp(6));
        top.addView(new ImageButtonLike("close", "Fechar", v -> onBackPressed()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("Editar foto", 17, Color.WHITE, true);
        title.setPadding(dp(12), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        undoButton = new ImageButtonLike("back", "Desfazer", v -> undo());
        redoButton = new ImageButtonLike("arrow", "Refazer", v -> redo());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(44), dp(44));
        hp.rightMargin = dp(6);
        top.addView(undoButton, hp);
        top.addView(redoButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView save = label("Guardar", 14, onAccent, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(18), 0, dp(18), 0);
        save.setBackground(round(accent, 22));
        save.setOnClickListener(v -> savePanel());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(44));
        sp.leftMargin = dp(10);
        top.addView(save, sp);
        root.addView(top);
        refreshHistory();

        FrameLayout stage = new FrameLayout(this);
        view = new EditorView(this);
        stage.addView(view, new FrameLayout.LayoutParams(-1, -1));
        busy = new ProgressBar(this);
        busy.setVisibility(View.GONE);
        stage.addView(busy, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        TextView hint = label("Mantenha premido para ver o original", 11, 0x99ffffff, false);
        hint.setPadding(dp(10), dp(4), dp(10), dp(4));
        hint.setBackground(round(0x66000000, 10));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = dp(6);
        stage.addView(hint, hl);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setBackgroundColor(0xff151515);
        tools.setPadding(0, dp(6), 0, dp(4));
        root.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        tabs = new LinearLayout(this);
        tabs.setBackgroundColor(0xff151515);
        tabs.setPadding(dp(6), dp(2), dp(6), dp(10));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    void buildTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            boolean on = i == tab;
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(8), 0, dp(6));
            b.setBackground(round(on ? (accent & 0xffffff) | 0x30000000 : Color.TRANSPARENT, 14));
            b.addView(new Icons(this, TAB_ICONS[i], on ? accent : Color.WHITE), new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView l = label(TABS[i], 11, on ? accent : 0xffcfcfcf, on);
            l.setPadding(0, dp(3), 0, 0);
            l.setMaxLines(1);
            b.addView(l, new LinearLayout.LayoutParams(-2, -2));
            b.setOnClickListener(v -> showTab(t));
            tabs.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void showTab(int t) {
        tab = t;
        if (t != TEXT) selected = null;
        buildTabs();
        tools.removeAllViews();
        if (preview == null) return;
        if (t == CROP) cropTools();
        else if (t == ADJUST) adjustTools();
        else if (t == FILTERS) filterTools();
        else if (t == DRAW) drawTools();
        else textTools();
        refresh(false);
    }

    // ---- ferramentas ----

    HorizontalScrollView strip(View... items) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(10), dp(4), dp(10), dp(4));
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (View v : items) {
            LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams ? (LinearLayout.LayoutParams) v.getLayoutParams() : new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            lp.gravity = Gravity.CENTER_VERTICAL;
            row.addView(v, lp);
        }
        scroll.addView(row);
        return scroll;
    }

    TextView chip(String text, boolean on, View.OnClickListener click) {
        TextView t = label(text, 13, on ? onAccent : Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(round(on ? accent : 0xff262626, 16));
        t.setOnClickListener(click);
        return t;
    }

    LinearLayout tool(String symbol, String text, boolean on, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setBackground(round(on ? (accent & 0xffffff) | 0x30000000 : Color.TRANSPARENT, 12));
        b.addView(new Icons(this, symbol, on ? accent : Color.WHITE), new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView l = label(text, 11, on ? accent : 0xffdddddd, false);
        l.setPadding(0, dp(3), 0, 0);
        l.setMaxLines(1);
        l.setGravity(Gravity.CENTER);
        b.addView(l, new LinearLayout.LayoutParams(-2, -2));
        b.setOnClickListener(click);
        return b;
    }

    interface Change { void set(int value); }

    /** Linha com nome, barra e valor; grava no histórico ao largar. */
    View slider(String name, int value, int min, int max, Change change) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(16), 0, dp(16), 0);
        TextView l = label(name, 12, 0xffdddddd, false);
        line.addView(l, new LinearLayout.LayoutParams(dp(96), -2));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        TextView v = label(String.valueOf(value), 12, Color.WHITE, true);
        v.setGravity(Gravity.END);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { v.setText(String.valueOf(p + min)); if (user) { change.set(p + min); refresh(true); } }
            public void onStartTrackingTouch(SeekBar s) { before(); }
            public void onStopTrackingTouch(SeekBar s) { refresh(false); }
        });
        line.addView(bar, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(v, new LinearLayout.LayoutParams(dp(40), -2));
        return line;
    }

    void cropTools() {
        View[] ratios = new View[RATIOS.length];
        for (int i = 0; i < RATIOS.length; i++) {
            final int r = i;
            float value = RATIO_VALUES[i] < 0 ? frameRatio() : RATIO_VALUES[i];
            ratios[i] = chip(RATIOS[i], Math.abs(ratio - value) < .001f && (i != 0 || ratio == 0), v -> { before(); ratio = RATIO_VALUES[r] < 0 ? frameRatio() : RATIO_VALUES[r]; applyRatio(); showTab(CROP); });
        }
        tools.addView(strip(ratios));
        tools.addView(slider("Endireitar °", Math.round(state.straighten), -45, 45, v -> state.straighten = v));
        tools.addView(slider("Persp. vertical", Math.round(state.keystoneV * 100), -100, 100, v -> state.keystoneV = v / 100f));
        tools.addView(slider("Persp. horizontal", Math.round(state.keystoneH * 100), -100, 100, v -> state.keystoneH = v / 100f));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(6), 0, dp(6), 0);
        row.addView(tool("rotate", "Rodar", false, v -> { before(); state.rotate = (state.rotate + 1) % 4; state.crop = new float[]{0, 0, 1, 1}; ratio = 0; showTab(CROP); }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tool("filters", "Espelhar ↔", false, v -> { before(); state.flipH = !state.flipH; refresh(false); }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tool("filters", "Espelhar ↕", false, v -> { before(); state.flipV = !state.flipV; refresh(false); }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tool("refresh", "Repor", false, v -> { before(); state.rotate = 0; state.flipH = state.flipV = false; state.straighten = state.keystoneV = state.keystoneH = 0; state.crop = new float[]{0, 0, 1, 1}; ratio = 0; showTab(CROP); }), new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(row);
    }

    float frameRatio() {
        if (preview == null) return 1;
        int[] f = PhotoEdit.frame(preview.getWidth(), preview.getHeight(), state);
        return f[0] / (float) f[1];
    }

    /** Ajusta o recorte actual à proporção escolhida, centrado. */
    void applyRatio() {
        if (ratio <= 0 || preview == null) return;
        int[] f = PhotoEdit.frame(preview.getWidth(), preview.getHeight(), state);
        float fw = f[0], fh = f[1];
        float cw = (state.crop[2] - state.crop[0]) * fw, ch = (state.crop[3] - state.crop[1]) * fh;
        float cx = (state.crop[0] + state.crop[2]) / 2 * fw, cy = (state.crop[1] + state.crop[3]) / 2 * fh;
        if (cw / ch > ratio) cw = ch * ratio; else ch = cw / ratio;
        float scale = Math.min(1, Math.min(fw / cw, fh / ch));
        cw *= scale; ch *= scale;
        cx = Math.max(cw / 2, Math.min(fw - cw / 2, cx));
        cy = Math.max(ch / 2, Math.min(fh - ch / 2, cy));
        state.crop = new float[]{(cx - cw / 2) / fw, (cy - ch / 2) / fh, (cx + cw / 2) / fw, (cy + ch / 2) / fh};
    }

    void adjustTools() {
        View[] items = new View[PhotoEdit.ADJUST.length + 1];
        items[0] = tool("star", "Automático", false, v -> { before(); PhotoEdit.auto(small, state); showTab(ADJUST); });
        for (int i = 0; i < PhotoEdit.ADJUST.length; i++) {
            final int k = i;
            int value = state.adjust[i];
            LinearLayout t = tool(PhotoEdit.ADJUST_ICONS[i], PhotoEdit.ADJUST[i] + (value != 0 ? " " + (value > 0 ? "+" : "") + value : ""), adjusting == i, v -> { adjusting = k; showTab(ADJUST); });
            items[i + 1] = t;
        }
        tools.addView(strip(items));
        int min = PhotoEdit.ONLY_POSITIVE[adjusting] ? 0 : -100;
        tools.addView(slider(PhotoEdit.ADJUST[adjusting], state.adjust[adjusting], min, 100, v -> state.adjust[adjusting] = v));
    }

    void filterTools() {
        View[] items = new View[PhotoEdit.FILTERS.length];
        for (int i = 0; i < PhotoEdit.FILTERS.length; i++) {
            final int k = i;
            boolean on = state.filter == i;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable frame = round(0xff262626, 12);
            if (on) frame.setStroke(dp(2.5f), accent);
            thumb.setBackground(frame);
            thumb.setPadding(dp(3), dp(3), dp(3), dp(3));
            thumb.setClipToOutline(true);
            chip.addView(thumb, new LinearLayout.LayoutParams(dp(64), dp(64)));
            TextView l = label(PhotoEdit.FILTERS[i], 11, on ? accent : 0xffdddddd, on);
            l.setPadding(0, dp(4), 0, 0);
            l.setMaxLines(1);
            chip.addView(l);
            chip.setOnClickListener(v -> { before(); state.filter = k; if (state.intensity == 0) state.intensity = 100; showTab(FILTERS); });
            items[i] = chip;
            work.execute(() -> {
                Bitmap b = PdfTools.fit(small, 150);
                Bitmap c = b.copy(Bitmap.Config.ARGB_8888, true);
                int w = c.getWidth(), h = c.getHeight();
                int[] px = new int[w * h];
                c.getPixels(px, 0, w, 0, 0, w, h);
                PhotoEdit.filter(px, k, 100);
                c.setPixels(px, 0, w, 0, 0, w, h);
                main.post(() -> thumb.setImageBitmap(c));
            });
        }
        tools.addView(strip(items));
        if (state.filter != 0) tools.addView(slider("Intensidade", state.intensity, 0, 100, v -> state.intensity = v));
    }

    void drawTools() {
        View[] swatches = new View[COLORS.length + 2];
        swatches[0] = chip("Pincel", !marker, v -> { marker = false; showTab(DRAW); });
        swatches[1] = chip("Marcador", marker, v -> { marker = true; showTab(DRAW); });
        for (int i = 0; i < COLORS.length; i++) swatches[i + 2] = swatch(COLORS[i], color == COLORS[i], c -> { color = c; showTab(DRAW); });
        tools.addView(strip(swatches));
        tools.addView(slider("Espessura", Math.round(brush * 1000), 2, 60, v -> brush = v / 1000f));
        LinearLayout row = new LinearLayout(this);
        row.addView(tool("back", "Apagar último", false, v -> { if (!state.strokes.isEmpty()) { before(); state.strokes.remove(state.strokes.size() - 1); refresh(false); } }), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tool("trash", "Limpar desenho", false, v -> { if (!state.strokes.isEmpty()) { before(); state.strokes.clear(); refresh(false); } }), new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(row);
    }

    interface ColorPick { void pick(int c); }

    View swatch(int c, boolean on, ColorPick pick) {
        View v = new View(this);
        GradientDrawable g = round(c, 16);
        g.setStroke(dp(on ? 3 : 1), on ? accent : 0x55ffffff);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        v.setMinimumWidth(dp(32));
        v.setMinimumHeight(dp(32));
        v.setOnClickListener(x -> pick.pick(c));
        return v;
    }

    void textTools() {
        View[] swatches = new View[COLORS.length + 1];
        swatches[0] = chip("+ Texto", false, v -> askText(null, .5f, .5f));
        for (int i = 0; i < COLORS.length; i++) swatches[i + 1] = swatch(COLORS[i], selected != null ? selected.color == COLORS[i] : color == COLORS[i], c -> {
            color = c;
            if (selected != null) { before(); selected.color = c; }
            showTab(TEXT);
        });
        tools.addView(strip(swatches));
        if (selected != null) {
            tools.addView(slider("Tamanho", Math.round(selected.size * 1000), 20, 200, v -> selected.size = v / 1000f));
            LinearLayout row = new LinearLayout(this);
            row.addView(tool("edit", "Alterar", false, v -> askText(selected, selected.x, selected.y)), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(tool("grid", selected.box ? "Sem fundo" : "Com fundo", selected.box, v -> { before(); selected.box = !selected.box; showTab(TEXT); }), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(tool("trash", "Apagar", false, v -> { before(); state.labels.remove(selected); selected = null; showTab(TEXT); }), new LinearLayout.LayoutParams(0, -2, 1));
            tools.addView(row);
        } else {
            TextView hint = label("Toque na foto para escrever; arraste um texto para o mover.", 12, 0xffbbbbbb, false);
            hint.setPadding(dp(16), dp(6), dp(16), dp(8));
            tools.addView(hint);
        }
    }

    void askText(PhotoEdit.Label existing, float x, float y) {
        EditText input = new EditText(this);
        input.setHint(AppLanguage.text(this, "Escreva aqui"));
        input.setText(existing == null ? "" : existing.text);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        new UiDialog.Builder(this).setTitle(existing == null ? "Adicionar texto" : "Alterar texto").setView(box).setPositiveButton("Aplicar", (d, w) -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            before();
            if (existing != null) existing.text = t;
            else {
                PhotoEdit.Label l = new PhotoEdit.Label();
                l.text = t;
                l.color = color;
                l.x = x;
                l.y = y;
                state.labels.add(l);
                selected = l;
            }
            showTab(TEXT);
        }).setNegativeButton("Cancelar", null).show();
    }

    // ---- pré-visualização ----

    /** Volta a desenhar a pré-visualização; {@code fast} durante o arrastar das barras. */
    void refresh(boolean fast) {
        if (preview == null) return;
        final int gen = ++generation;
        final PhotoEdit.State s = state.copy();
        final boolean crop = tab != CROP;
        final Bitmap src = fast ? small : preview;
        busy.setVisibility(fast ? View.GONE : View.VISIBLE);
        work.execute(() -> {
            Bitmap out;
            try { out = PhotoEdit.render(src, s, crop, fast ? 700 : 1400, false); } catch (Throwable e) { out = null; }
            final Bitmap result = out;
            main.post(() -> {
                if (gen != generation || isFinishing()) return;
                busy.setVisibility(View.GONE);
                if (result != null) { rendered = result; view.invalidate(); }
            });
        });
    }

    // ---- guardar e partilhar ----

    boolean png() { return mime.contains("png") || name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"); }

    String editedName() {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + " (editada)" + (png() ? ".png" : ".jpg");
    }

    File originalFile() {
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return null;
        File f = new File(uri.getPath());
        return f.isFile() && StorageAccess.all(this) && f.canWrite() ? f : null;
    }

    void savePanel() {
        if (preview == null) return;
        UiPanel p = new UiPanel(this, "Guardar foto", AppLanguage.text(this, state.changed() ? "Escolha como guardar" : "Ainda não há alterações"), "image");
        p.action("copy", "Guardar como cópia", editedName(), false, () -> save(false, false));
        File original = originalFile();
        if (original != null) p.action("refresh", "Substituir o original", AppLanguage.text(this, "Pede confirmação antes"), false, () ->
                new UiDialog.Builder(this).setTitle("Substituir o original?").setMessage(AppLanguage.text(this, "A foto original será substituída pela versão editada. Esta acção não pode ser anulada."))
                        .setPositiveButton("Substituir", (d, w) -> save(true, false)).setNegativeButton("Cancelar", null).show());
        p.action("share", "Partilhar", "WhatsApp, e-mail, Drive… sem guardar", false, () -> save(false, true));
        p.note(AppLanguage.text(this, "A foto é guardada em alta resolução (até 3200 px no lado maior)."));
        p.show();
    }

    /** Desenha na resolução final; se faltar memória, tenta de novo mais pequeno. */
    Bitmap full(PhotoEdit.State s) throws IOException {
        for (int side : new int[]{3200, 2200, 1400}) {
            try {
                Bitmap src = PdfTools.decode(source, side);
                if (src == null) throw new IOException("Imagem indisponível.");
                Bitmap out = PhotoEdit.render(src, s, true, side, true);
                if (out != src) src.recycle();
                return out;
            } catch (OutOfMemoryError e) { System.gc(); }
        }
        throw new IOException("Memória insuficiente para esta foto.");
    }

    void save(boolean replace, boolean share) {
        busy.setVisibility(View.VISIBLE);
        final PhotoEdit.State s = state.copy();
        work.execute(() -> {
            try {
                Bitmap out = full(s);
                if (share) {
                    main.post(() -> { busy.setVisibility(View.GONE); ShareTools.bitmap(this, out, editedName(), png()); });
                    return;
                }
                String where = replace ? replaceOriginal(out) : saveCopy(out);
                main.post(() -> {
                    busy.setVisibility(View.GONE);
                    undo.clear();
                    redo.clear();
                    state = new PhotoEdit.State();
                    new UiDialog.Builder(this).setTitle("Foto guardada").setMessage(AppLanguage.text(this, "Guardada em") + " " + where)
                            .setPositiveButton("Concluir", (d, w) -> finish())
                            .setNeutralButton("Partilhar", (d, w) -> ShareTools.bitmap(this, out, editedName(), png()))
                            .setNegativeButton("Continuar a editar", (d, w) -> showTab(tab)).show();
                });
            } catch (Exception e) {
                main.post(() -> { busy.setVisibility(View.GONE); new UiDialog.Builder(this).setTitle("Não foi possível guardar").setMessage(e.getMessage() == null ? "" : e.getMessage()).setPositiveButton("Fechar", null).show(); });
            }
        });
    }

    void write(Bitmap b, OutputStream out) { b.compress(png() ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 94, out); }

    String replaceOriginal(Bitmap b) throws IOException {
        File f = originalFile();
        if (f == null) throw new IOException("Sem autorização para alterar o original.");
        File tmp = new File(f.getParentFile(), "." + f.getName() + ".seeky");
        try (OutputStream out = new FileOutputStream(tmp)) { write(b, out); }
        if (tmp.length() == 0 || !tmp.renameTo(f)) { tmp.delete(); throw new IOException("Não foi possível substituir o original."); }
        android.media.MediaScannerConnection.scanFile(getApplicationContext(), new String[]{f.getPath()}, null, null);
        return f.getParent();
    }

    /** Ao lado do original (com acesso geral) ou em Imagens/Seeky. */
    String saveCopy(Bitmap b) throws IOException {
        File original = "file".equals(uri.getScheme()) && uri.getPath() != null ? new File(uri.getPath()) : null;
        File dir = original != null && StorageAccess.all(this) && original.getParentFile() != null && original.getParentFile().canWrite() ? original.getParentFile()
                : StorageAccess.all(this) ? new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Seeky") : null;
        if (dir != null) {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta.");
            File target = WifiTransfer.unique(dir, editedName());
            try (OutputStream out = new FileOutputStream(target)) { write(b, out); }
            android.media.MediaScannerConnection.scanFile(getApplicationContext(), new String[]{target.getPath()}, null, null);
            return target.getPath();
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, editedName());
        v.put(MediaStore.MediaColumns.MIME_TYPE, png() ? "image/png" : "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Seeky");
        Uri target = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (target == null) throw new IOException("Active o acesso aos ficheiros para guardar a foto.");
        try (OutputStream out = getContentResolver().openOutputStream(target)) { if (out == null) throw new IOException(); write(b, out); }
        return "Imagens/Seeky";
    }

    // ---- palco ----

    /** Mostra a pré-visualização e trata recorte, desenho, texto e comparação. */
    final class EditorView extends View {
        final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG), shade = new Paint(), line = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(Paint.ANTI_ALIAS_FLAG),
                corner = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), select = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF frame = new RectF();
        int drag = -1;
        float lastX, lastY, downX, downY;
        PhotoEdit.Stroke current;
        boolean moved;
        final Runnable compare = () -> { comparing = true; invalidate(); };

        EditorView(Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            shade.setColor(0xaa000000);
            line.setColor(Color.WHITE);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1.5f * d);
            grid.setColor(0x80ffffff);
            grid.setStrokeWidth(d);
            corner.setColor(Color.WHITE);
            corner.setStyle(Paint.Style.STROKE);
            corner.setStrokeWidth(4 * d);
            corner.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            select.setColor(accent);
            select.setStyle(Paint.Style.STROKE);
            select.setStrokeWidth(2 * d);
            select.setPathEffect(new DashPathEffect(new float[]{8 * d, 6 * d}, 0));
        }

        @Override protected void onDraw(Canvas c) {
            Bitmap b = comparing && preview != null ? preview : rendered;
            if (b == null) return;
            float pad = 16 * getResources().getDisplayMetrics().density;
            float s = Math.min((getWidth() - 2 * pad) / b.getWidth(), (getHeight() - 2 * pad) / b.getHeight());
            float w = b.getWidth() * s, h = b.getHeight() * s;
            frame.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
            c.drawBitmap(b, null, frame, image);
            if (comparing) return;
            if (tab == CROP) drawCrop(c);
            else {
                PhotoEdit.draw(c, state, frame);
                if (current != null) PhotoEdit.stroke(c, current, frame, stroke);
                if (tab == TEXT && selected != null) c.drawRect(PhotoEdit.labelBounds(selected, frame), select);
            }
        }

        RectF cropRect() {
            float[] k = state.crop;
            return new RectF(frame.left + k[0] * frame.width(), frame.top + k[1] * frame.height(), frame.left + k[2] * frame.width(), frame.top + k[3] * frame.height());
        }

        void drawCrop(Canvas c) {
            RectF r = cropRect();
            c.drawRect(frame.left, frame.top, frame.right, r.top, shade);
            c.drawRect(frame.left, r.bottom, frame.right, frame.bottom, shade);
            c.drawRect(frame.left, r.top, r.left, r.bottom, shade);
            c.drawRect(r.right, r.top, frame.right, r.bottom, shade);
            c.drawRect(r, line);
            for (int i = 1; i < 3; i++) {
                float x = r.left + r.width() * i / 3, y = r.top + r.height() * i / 3;
                c.drawLine(x, r.top, x, r.bottom, grid);
                c.drawLine(r.left, y, r.right, y, grid);
            }
            float L = 18 * getResources().getDisplayMetrics().density;
            float[][] pts = {{r.left, r.top, 1, 1}, {r.right, r.top, -1, 1}, {r.right, r.bottom, -1, -1}, {r.left, r.bottom, 1, -1}};
            for (float[] p : pts) { c.drawLine(p[0], p[1], p[0] + L * p[2], p[1], corner); c.drawLine(p[0], p[1], p[0], p[1] + L * p[3], corner); }
        }

        float nx(float x) { return Math.max(0, Math.min(1, (x - frame.left) / Math.max(1, frame.width()))); }
        float ny(float y) { return Math.max(0, Math.min(1, (y - frame.top) / Math.max(1, frame.height()))); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (rendered == null) return false;
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = x; downY = lastY = y; moved = false;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if (tab == CROP) { drag = cropHandle(x, y); if (drag >= 0) before(); }
                    else if (tab == DRAW) {
                        before();
                        current = new PhotoEdit.Stroke();
                        current.color = color;
                        current.width = brush;
                        current.marker = marker;
                        current.add(nx(x), ny(y));
                        invalidate();
                    } else if (tab == TEXT) {
                        selected = null;
                        for (int i = state.labels.size() - 1; i >= 0; i--) if (PhotoEdit.labelBounds(state.labels.get(i), frame).contains(x, y)) { selected = state.labels.get(i); break; }
                        if (selected != null) before();
                        invalidate();
                    } else postDelayed(compare, 300);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(x - downX, y - downY) > 8 * getResources().getDisplayMetrics().density) { moved = true; removeCallbacks(compare); }
                    if (tab == CROP && drag >= 0) moveCrop(x - lastX, y - lastY);
                    else if (tab == DRAW && current != null) current.add(nx(x), ny(y));
                    else if (tab == TEXT && selected != null && moved) { selected.x = nx(x); selected.y = ny(y); }
                    lastX = x; lastY = y;
                    invalidate();
                    return true;
                default:
                    removeCallbacks(compare);
                    if (comparing) { comparing = false; invalidate(); }
                    if (tab == CROP && drag >= 0) { drag = -1; invalidate(); }
                    else if (tab == DRAW && current != null) { state.strokes.add(current); current = null; invalidate(); }
                    else if (tab == TEXT) {
                        if (selected == null && !moved && e.getActionMasked() == MotionEvent.ACTION_UP && frame.contains(x, y)) askText(null, nx(x), ny(y));
                        else showTab(TEXT);
                    }
                    return true;
            }
        }

        /** 0–3 cantos, 4–7 lados, 8 interior, -1 nada. */
        int cropHandle(float x, float y) {
            RectF r = cropRect();
            float t = 32 * getResources().getDisplayMetrics().density;
            float[][] corners = {{r.left, r.top}, {r.right, r.top}, {r.right, r.bottom}, {r.left, r.bottom}};
            for (int i = 0; i < 4; i++) if (Math.hypot(x - corners[i][0], y - corners[i][1]) < t) return i;
            if (Math.abs(y - r.top) < t * .7f && x > r.left && x < r.right) return 4;
            if (Math.abs(x - r.right) < t * .7f && y > r.top && y < r.bottom) return 5;
            if (Math.abs(y - r.bottom) < t * .7f && x > r.left && x < r.right) return 6;
            if (Math.abs(x - r.left) < t * .7f && y > r.top && y < r.bottom) return 7;
            return r.contains(x, y) ? 8 : -1;
        }

        void moveCrop(float dx, float dy) {
            float[] k = state.crop.clone();
            float nx = dx / frame.width(), ny = dy / frame.height(), min = .06f;
            if (drag == 8) {
                float w = k[2] - k[0], h = k[3] - k[1];
                k[0] = Math.max(0, Math.min(1 - w, k[0] + nx));
                k[1] = Math.max(0, Math.min(1 - h, k[1] + ny));
                k[2] = k[0] + w;
                k[3] = k[1] + h;
            } else {
                boolean left = drag == 0 || drag == 3 || drag == 7, right = drag == 1 || drag == 2 || drag == 5;
                boolean top = drag == 0 || drag == 1 || drag == 4, bottom = drag == 2 || drag == 3 || drag == 6;
                if (left) k[0] = Math.max(0, Math.min(k[2] - min, k[0] + nx));
                if (right) k[2] = Math.min(1, Math.max(k[0] + min, k[2] + nx));
                if (top) k[1] = Math.max(0, Math.min(k[3] - min, k[1] + ny));
                if (bottom) k[3] = Math.min(1, Math.max(k[1] + min, k[3] + ny));
                if (ratio > 0) {
                    float fr = frame.width() / Math.max(1, frame.height());
                    float w = k[2] - k[0], h = (w * fr) / ratio;
                    if (top && !bottom) k[1] = Math.max(0, k[3] - h); else k[3] = Math.min(1, k[1] + h);
                    float realH = k[3] - k[1];
                    if (Math.abs(realH - h) > .001f) { w = realH * ratio / fr; if (left && !right) k[0] = k[2] - w; else k[2] = k[0] + w; }
                }
            }
            state.crop = k;
        }
    }

    /** Cor de destaque legível sobre fundo escuro: aclara as cores escuras dos temas claros. */
    static int darkAccent(int accent) {
        int c = accent;
        for (int i = 0; i < 10 && AppPalette.contrast(c, 0xff151515) < 4.5; i++) c = ThemeColors.blend(c, Color.WHITE, .2f);
        return c;
    }
}
