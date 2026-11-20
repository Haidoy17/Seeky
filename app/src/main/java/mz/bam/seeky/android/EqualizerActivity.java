package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.util.Locale;
import java.util.Map;

/**
 * Equalizador ao estilo Poweramp: curva de resposta em tempo real, 10 bandas verticais com
 * pré-amplificação, predefinições (incluindo as suas), botões rotativos de graves, agudos,
 * virtualização e volume extra, volume do sistema, balanço e limitador.
 */
public final class EqualizerActivity extends Activity {
    static final int BG = 0xff0f1014, PANEL = 0xff17191f, LINE = 0xff2a2d36, TEXT = 0xffe8e8ee, MUTED = 0xff9a9cab;

    AudioEffects.Settings s;
    int accent;
    EqView eq;
    LinearLayout presets;
    TextView status;
    Switch power;
    final Handler main = new Handler(Looper.getMainLooper());
    final Runnable push = () -> AudioEffects.save(this, s);

    static void open(Context c) {
        try { c.startActivity(new Intent(c, EqualizerActivity.class).addFlags(c instanceof Activity ? 0 : Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { AppDiagnostics.record(c, "equalizer-open", e); }
    }

    @Override protected void attachBaseContext(Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(AppLanguage.text(this, text));
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(ThemeStyle.BOLD);
        return t;
    }

    GradientDrawable round(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }

    /** Guarda e aplica com um pequeno atraso (arrastar não satura o áudio). */
    void changed(boolean custom) {
        if (custom) { s.preset = "Personalizado"; buildPresets(); }
        main.removeCallbacks(push);
        main.postDelayed(push, 40);
        eq.invalidate();
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        s = AudioEffects.load(this);
        accent = ScanActivity.darkAccent(AppPalette.of(this).accent);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(24));
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = new FrameLayout(this);
        back.setBackground(round(PANEL, 22));
        back.addView(new Icons(this, "back", TEXT), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        back.setContentDescription(AppLanguage.text(this, "Voltar"));
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = label("Equalizador", 20, TEXT, true);
        title.setPadding(dp(12), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        power = new Switch(this);
        power.setChecked(s.enabled);
        power.setText(AppLanguage.text(this, s.enabled ? "Ligado" : "Desligado"));
        power.setTextColor(TEXT);
        power.setThumbTintList(android.content.res.ColorStateList.valueOf(s.enabled ? accent : MUTED));
        power.setOnCheckedChangeListener((b, on) -> {
            s.enabled = on;
            power.setText(AppLanguage.text(this, on ? "Ligado" : "Desligado"));
            power.setThumbTintList(android.content.res.ColorStateList.valueOf(on ? accent : MUTED));
            eq.setAlpha(on ? 1f : .45f);
            changed(false);
        });
        top.addView(power);
        root.addView(top);

        HorizontalScrollView ps = new HorizontalScrollView(this);
        ps.setHorizontalScrollBarEnabled(false);
        presets = new LinearLayout(this);
        presets.setPadding(0, dp(14), 0, dp(10));
        ps.addView(presets);
        root.addView(ps);
        buildPresets();

        eq = new EqView(this);
        eq.setAlpha(s.enabled ? 1f : .45f);
        root.addView(eq, new LinearLayout.LayoutParams(-1, dp(300)));

        LinearLayout knobs = new LinearLayout(this);
        knobs.setPadding(0, dp(16), 0, dp(6));
        knobs.addView(new Knob(this, "Graves", 0, 100, s.bass, v -> s.bass = v, v -> "+" + Math.round(AudioEffects.bassDb(v)) + " dB"), new LinearLayout.LayoutParams(0, dp(118), 1));
        knobs.addView(new Knob(this, "Agudos", -100, 100, Math.round(s.treble / 12f * 100), v -> s.treble = v * 12f / 100f, v -> (v >= 0 ? "+" : "") + Math.round(v * 12f / 100f) + " dB"), new LinearLayout.LayoutParams(0, dp(118), 1));
        knobs.addView(new Knob(this, "Virtualização", 0, 100, s.virtualizer, v -> s.virtualizer = v, v -> v + "%"), new LinearLayout.LayoutParams(0, dp(118), 1));
        knobs.addView(new Knob(this, "Volume extra", 0, 100, s.boost, v -> s.boost = v, v -> "+" + Math.round(v * .12f) + " dB"), new LinearLayout.LayoutParams(0, dp(118), 1));
        root.addView(knobs);

        root.addView(section("Volume e balanço"));
        AudioManager am = getSystemService(AudioManager.class);
        int max = am == null ? 15 : am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        root.addView(slider("Volume", 0, max, am == null ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC), v -> { if (am != null) am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); }, v -> Math.round(v * 100f / Math.max(1, max)) + "%"));
        root.addView(slider("Balanço", -100, 100, s.balance, v -> { s.balance = v; changed(false); }, v -> v == 0 ? "E = D" : v < 0 ? "E " + (-v) + "%" : "D " + v + "%"));

        root.addView(section("Opções"));
        Switch limiter = new Switch(this);
        limiter.setText(AppLanguage.text(this, "Limitador (evita distorção com volume alto)"));
        limiter.setTextColor(TEXT);
        limiter.setChecked(s.limiter);
        limiter.setPadding(0, dp(6), 0, dp(6));
        limiter.setOnCheckedChangeListener((b, on) -> { s.limiter = on; changed(false); });
        root.addView(limiter);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(button("Guardar predefinição", true, v -> saveCustom()), new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(0, dp(46), 1);
        gap.leftMargin = dp(8);
        actions.addView(button("Repor tudo", false, v -> reset()), gap);
        root.addView(actions);
        status = label("", 12, MUTED, false);
        status.setPadding(0, dp(14), 0, 0);
        root.addView(status);
        refreshStatus();
        setContentView(scroll);
    }

    void refreshStatus() {
        String text = AudioEffects.session == 0 ? AppLanguage.text(this, "Toque uma música no Seeky para ouvir as alterações em tempo real.")
                : AudioEffects.problem.isEmpty() ? AppLanguage.text(this, "Aplicado à música que está a tocar.")
                : AppLanguage.text(this, "Este telefone não suporta:") + " " + AudioEffects.problem + ". " + AppLanguage.text(this, "Os outros efeitos continuam activos.");
        status.setText(text);
    }

    TextView section(String name) {
        TextView t = label(name.toUpperCase(Locale.ROOT), 11, MUTED, true);
        t.setLetterSpacing(.1f);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    TextView button(String text, boolean primary, View.OnClickListener click) {
        TextView b = label(text, 13, primary ? (AppPalette.contrast(Color.WHITE, accent) >= 4.5 ? Color.WHITE : 0xff111111) : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(primary ? accent : PANEL, 14));
        b.setOnClickListener(click);
        return b;
    }

    interface IntSet { void set(int v); }
    interface Format { String text(int v); }

    View slider(String name, int min, int max, int value, IntSet set, Format format) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(12), dp(4), dp(12), dp(4));
        line.setBackground(round(PANEL, 14));
        TextView l = label(name, 13, TEXT, false);
        line.addView(l, new LinearLayout.LayoutParams(dp(78), -2));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        TextView v = label(format.text(value), 12, TEXT, true);
        v.setGravity(Gravity.END);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) { v.setText(format.text(p + min)); if (user) set.set(p + min); }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        line.addView(bar, new LinearLayout.LayoutParams(0, dp(44), 1));
        line.addView(v, new LinearLayout.LayoutParams(dp(64), -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        line.setLayoutParams(lp);
        return line;
    }

    void buildPresets() {
        presets.removeAllViews();
        Map<String, float[]> custom = AudioEffects.custom(this);
        if ("Personalizado".equals(s.preset)) presets.addView(chip("Personalizado", true, null));
        for (Map.Entry<String, float[]> e : AudioEffects.PRESETS.entrySet()) presets.addView(chip(e.getKey(), e.getKey().equals(s.preset), e.getValue()));
        for (Map.Entry<String, float[]> e : custom.entrySet()) presets.addView(chip(e.getKey(), e.getKey().equals(s.preset), e.getValue()));
    }

    TextView chip(String name, boolean on, float[] gains) {
        TextView t = label(name, 13, on ? (AppPalette.contrast(Color.WHITE, accent) >= 4.5 ? Color.WHITE : 0xff111111) : TEXT, true);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(round(on ? accent : PANEL, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        if (gains != null) t.setOnClickListener(v -> { s.gains = gains.clone(); s.preset = name; buildPresets(); changed(false); });
        return t;
    }

    void saveCustom() {
        EditText name = new EditText(this);
        name.setHint(AppLanguage.text(this, "Nome (ex.: Carro, Auscultadores)"));
        name.setSingleLine(true);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(name, new LinearLayout.LayoutParams(-1, -2));
        new UiDialog.Builder(this).setTitle("Guardar predefinição").setView(box).setPositiveButton("Guardar", (d, w) -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) return;
            AudioEffects.saveCustom(this, n, s.gains);
            s.preset = n;
            buildPresets();
            changed(false);
        }).setNegativeButton("Cancelar", null).show();
    }

    void reset() {
        s = new AudioEffects.Settings();
        AudioEffects.save(this, s);
        recreate();
    }

    @Override protected void onResume() { super.onResume(); if (status != null) refreshStatus(); }

    // ---- bandas ----

    /** Curva de resposta + pré-amplificação + 10 bandas, tudo num só painel com toque. */
    final class EqView extends View {
        final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), curve = new Paint(Paint.ANTI_ALIAS_FLAG),
                fill = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG), panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        int dragging = -1;
        final float d;

        EqView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            grid.setColor(LINE);
            grid.setStrokeWidth(d);
            text.setColor(MUTED);
            text.setTextSize(10 * c.getResources().getDisplayMetrics().scaledDensity);
            text.setTextAlign(Paint.Align.CENTER);
            curve.setColor(accent);
            curve.setStyle(Paint.Style.STROKE);
            curve.setStrokeWidth(3 * d);
            curve.setStrokeJoin(Paint.Join.ROUND);
            track.setColor(0xff30333d);
            track.setStrokeWidth(3 * d);
            track.setStrokeCap(Paint.Cap.ROUND);
            thumb.setColor(accent);
            core.setColor(Color.WHITE);
            panel.setColor(PANEL);
            setContentDescription(AppLanguage.text(c, "Equalizador de 10 bandas"));
        }

        float top() { return 26 * d; }
        float bottom() { return getHeight() - 26 * d; }
        float left() { return 40 * d; }
        float column(int i) { float w = getWidth() - left() - 8 * d; return i == 0 ? 20 * d : left() + w * (i - .5f) / 10f; }
        float y(float db, float range) { return top() + (bottom() - top()) * (range - db) / (2 * range); }
        float db(float y, float range) { float v = range - (y - top()) / (bottom() - top()) * 2 * range; return Math.max(-range, Math.min(range, Math.round(v * 2) / 2f)); }

        @Override protected void onDraw(Canvas c) {
            c.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), 18 * d, 18 * d, panel);
            float range = AudioEffects.MAX_DB;
            for (int g = -12; g <= 12; g += 6) {
                float yy = y(g, range);
                grid.setAlpha(g == 0 ? 255 : 130);
                c.drawLine(left() - 4 * d, yy, getWidth() - 8 * d, yy, grid);
            }
            c.drawLine(left() - 6 * d, top() - 10 * d, left() - 6 * d, bottom() + 4 * d, grid);
            float[] g = AudioEffects.effective(s);
            Path path = new Path(), area = new Path();
            float x0 = column(1), x1 = column(10);
            for (float x = x0; x <= x1; x += 2 * d) {
                double t = (x - x0) / (x1 - x0), f = AudioEffects.FREQ[0] * Math.pow(AudioEffects.FREQ[9] / (double) AudioEffects.FREQ[0], t);
                float yy = y(Math.max(-range, Math.min(range, AudioEffects.response(g, (float) f) + s.preamp)), range);
                if (x == x0) { path.moveTo(x, yy); area.moveTo(x, y(0, range)); }
                path.lineTo(x, yy);
                area.lineTo(x, yy);
            }
            area.lineTo(x1, y(0, range));
            area.close();
            fill.setShader(new LinearGradient(0, top(), 0, bottom(), (accent & 0xffffff) | 0x66000000, (accent & 0xffffff) | 0x08000000, Shader.TileMode.CLAMP));
            c.drawPath(area, fill);
            c.drawPath(path, curve);
            for (int i = 0; i <= 10; i++) {
                float x = column(i), v = i == 0 ? s.preamp : s.gains[i - 1], r = i == 0 ? 12 : range;
                c.drawLine(x, top(), x, bottom(), track);
                float yy = y(v, r);
                thumb.setColor(i == 0 ? 0xffffc857 : accent);
                c.drawCircle(x, yy, (dragging == i ? 11 : 9) * d, thumb);
                c.drawCircle(x, yy, 4 * d, core);
                text.setColor(dragging == i ? TEXT : MUTED);
                if (dragging == i || v != 0) c.drawText((v > 0 ? "+" : "") + (v == (int) v ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v)), x, top() - 9 * d, text);
                text.setColor(MUTED);
                c.drawText(i == 0 ? AppLanguage.text(getContext(), "Pré") : AudioEffects.LABELS[i - 1], x, getHeight() - 8 * d, text);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!s.enabled) return false;
            float x = e.getX();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float best = Float.MAX_VALUE;
                    for (int i = 0; i <= 10; i++) { float dd = Math.abs(column(i) - x); if (dd < best) { best = dd; dragging = i; } }
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging < 0) return false;
                    if (dragging == 0) s.preamp = db(e.getY(), 12); else s.gains[dragging - 1] = db(e.getY(), AudioEffects.MAX_DB);
                    changed(dragging != 0);
                    return true;
                default:
                    dragging = -1;
                    invalidate();
                    return true;
            }
        }
    }

    // ---- botões rotativos ----

    interface Label { String text(int v); }

    /** Botão rotativo: arrasta para cima/baixo; arco de 270°; toque duplo volta a zero. */
    final class Knob extends View {
        final String name;
        final int min, max;
        int value;
        final IntSet set;
        final Label format;
        final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG), on = new Paint(Paint.ANTI_ALIAS_FLAG), body = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
        float lastY;
        long lastTap;
        final float d;

        Knob(Context c, String name, int min, int max, int value, IntSet set, Label format) {
            super(c);
            this.name = AppLanguage.text(c, name);
            this.min = min; this.max = max; this.value = Math.max(min, Math.min(max, value)); this.set = set; this.format = format;
            d = c.getResources().getDisplayMetrics().density;
            arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(5 * d); arc.setStrokeCap(Paint.Cap.ROUND); arc.setColor(0xff2c2f38);
            on.setStyle(Paint.Style.STROKE); on.setStrokeWidth(5 * d); on.setStrokeCap(Paint.Cap.ROUND); on.setColor(accent);
            body.setShader(null); body.setColor(0xff22252d);
            mark.setColor(TEXT); mark.setStrokeWidth(3 * d); mark.setStrokeCap(Paint.Cap.ROUND);
            text.setTextAlign(Paint.Align.CENTER);
            setContentDescription(this.name);
        }

        @Override protected void onDraw(Canvas c) {
            float size = Math.min(getWidth(), getHeight() - 30 * d) - 10 * d, cx = getWidth() / 2f, cy = 5 * d + size / 2f, r = size / 2f;
            RectF box = new RectF(cx - r + 4 * d, cy - r + 4 * d, cx + r - 4 * d, cy + r - 4 * d);
            c.drawArc(box, 135, 270, false, arc);
            float zero = min < 0 ? .5f : 0, t = (value - min) / (float) (max - min);
            c.drawArc(box, 135 + 270 * Math.min(zero, t), 270 * Math.abs(t - zero), false, on);
            c.drawCircle(cx, cy, r - 13 * d, body);
            double ang = Math.toRadians(135 + 270 * t);
            c.drawLine(cx + (float) Math.cos(ang) * (r - 28 * d), cy + (float) Math.sin(ang) * (r - 28 * d), cx + (float) Math.cos(ang) * (r - 17 * d), cy + (float) Math.sin(ang) * (r - 17 * d), mark);
            text.setColor(TEXT);
            text.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(format.text(value), cx, cy + 4 * d, text);
            text.setColor(MUTED);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
            c.drawText(name, cx, getHeight() - 8 * d, text);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastY = e.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                if (System.currentTimeMillis() - lastTap < 300) { value = Math.max(min, Math.min(max, 0)); set.set(value); changed(false); invalidate(); }
                lastTap = System.currentTimeMillis();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dy = lastY - e.getY();
                int step = Math.round(dy / (1.6f * d) * (max - min) / 100f);
                if (step != 0) {
                    value = Math.max(min, Math.min(max, value + step));
                    lastY = e.getY();
                    set.set(value);
                    changed(false);
                    invalidate();
                }
                return true;
            }
            return true;
        }
    }
}
