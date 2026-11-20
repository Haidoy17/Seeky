package mz.bam.seeky.android;

import android.graphics.*;
import android.text.TextPaint;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Motor do editor de fotografias (sem interface, testável): geometria (rodar 90°, espelhar,
 * endireitar com zoom automático, perspectiva vertical/horizontal e recorte), 12 filtros com
 * intensidade, 10 ajustes (brilho, contraste, saturação, calor, matiz, realces, sombras, nitidez,
 * vinheta, grão) e camadas de desenho e texto em coordenadas relativas ao recorte.
 */
final class PhotoEdit {
    static final String[] FILTERS = {"Original", "Auto", "Vívido", "Quente", "Frio", "Preto e branco", "Sépia", "Vintage", "Dramático", "Suave", "Noir", "Desbotado"};
    static final int BRIGHTNESS = 0, CONTRAST = 1, SATURATION = 2, WARMTH = 3, TINT = 4, HIGHLIGHTS = 5, SHADOWS = 6, SHARPNESS = 7, VIGNETTE = 8, GRAIN = 9;
    static final String[] ADJUST = {"Brilho", "Contraste", "Saturação", "Calor", "Matiz", "Realces", "Sombras", "Nitidez", "Vinheta", "Grão"};
    static final String[] ADJUST_ICONS = {"eye", "contents", "palette", "star", "palette", "eye", "eye", "type", "dot", "grid"};
    static final boolean[] ONLY_POSITIVE = {false, false, false, false, false, false, false, true, true, true};

    private PhotoEdit() {}

    static final class Stroke {
        int color;
        float width;
        boolean marker;
        float[] points = new float[0];
        Stroke copy() { Stroke s = new Stroke(); s.color = color; s.width = width; s.marker = marker; s.points = points.clone(); return s; }
        void add(float x, float y) { float[] p = java.util.Arrays.copyOf(points, points.length + 2); p[points.length] = x; p[points.length + 1] = y; points = p; }
    }

    static final class Label {
        String text = "";
        int color = Color.WHITE;
        float size = .07f, x = .5f, y = .5f;
        boolean box;
        Label copy() { Label l = new Label(); l.text = text; l.color = color; l.size = size; l.x = x; l.y = y; l.box = box; return l; }
    }

    static final class State {
        int rotate;
        boolean flipH, flipV;
        float straighten, keystoneV, keystoneH;
        float[] crop = {0, 0, 1, 1};
        int[] adjust = new int[ADJUST.length];
        int filter, intensity = 100;
        List<Stroke> strokes = new ArrayList<>();
        List<Label> labels = new ArrayList<>();

        State copy() {
            State s = new State();
            s.rotate = rotate; s.flipH = flipH; s.flipV = flipV;
            s.straighten = straighten; s.keystoneV = keystoneV; s.keystoneH = keystoneH;
            s.crop = crop.clone(); s.adjust = adjust.clone(); s.filter = filter; s.intensity = intensity;
            for (Stroke k : strokes) s.strokes.add(k.copy());
            for (Label l : labels) s.labels.add(l.copy());
            return s;
        }

        boolean changed() {
            if (rotate % 4 != 0 || flipH || flipV || straighten != 0 || keystoneV != 0 || keystoneH != 0 || filter != 0 || !strokes.isEmpty() || !labels.isEmpty()) return true;
            if (crop[0] != 0 || crop[1] != 0 || crop[2] != 1 || crop[3] != 1) return true;
            for (int a : adjust) if (a != 0) return true;
            return false;
        }

        boolean geometryOnly() {
            if (filter != 0 || !strokes.isEmpty() || !labels.isEmpty()) return false;
            for (int a : adjust) if (a != 0) return false;
            return true;
        }
    }

    // ---- geometria ----

    /** Tamanho da imagem depois de rodada (90°) — o enquadramento onde vivem o endireitar e o recorte. */
    static int[] frame(int w, int h, State s) { return s.rotate % 2 == 0 ? new int[]{w, h} : new int[]{h, w}; }

    /** Escala que faz a imagem rodada de {@code degrees} cobrir o enquadramento sem cantos vazios. */
    static float cover(float w, float h, float degrees) {
        double a = Math.toRadians(Math.abs(degrees)), c = Math.cos(a), s = Math.sin(a);
        return (float) Math.max((w * c + h * s) / w, (w * s + h * c) / h);
    }

    /** Matriz da fotografia original para o enquadramento W×H (sem recorte). */
    static Matrix matrix(int w, int h, State s) {
        int[] f = frame(w, h, s);
        float fw = f[0], fh = f[1];
        Matrix m = new Matrix();
        m.postTranslate(-w / 2f, -h / 2f);
        m.postRotate(90 * (s.rotate % 4));
        m.postScale(s.flipH ? -1 : 1, s.flipV ? -1 : 1);
        m.postTranslate(fw / 2f, fh / 2f);
        if (s.keystoneV != 0 || s.keystoneH != 0) {
            float kv = s.keystoneV * .25f, kh = s.keystoneH * .25f;
            float[] src = {
                    fw * Math.max(0, kv) + 0, fh * Math.max(0, kh),
                    fw - fw * Math.max(0, kv), fh * Math.max(0, -kh),
                    fw - fw * Math.max(0, -kv), fh - fh * Math.max(0, -kh),
                    fw * Math.max(0, -kv), fh - fh * Math.max(0, kh)};
            Matrix p = new Matrix();
            p.setPolyToPoly(src, 0, new float[]{0, 0, fw, 0, fw, fh, 0, fh}, 0, 4);
            m.postConcat(p);
        }
        if (s.straighten != 0) {
            float k = cover(fw, fh, s.straighten);
            m.postRotate(s.straighten, fw / 2f, fh / 2f);
            m.postScale(k, k, fw / 2f, fh / 2f);
        }
        return m;
    }

    /** Aplica a geometria; com {@code crop} devolve só o recorte. Lado maior até {@code maxSide}. */
    static Bitmap geometry(Bitmap src, State s, boolean crop, int maxSide) {
        int[] f = frame(src.getWidth(), src.getHeight(), s);
        float cl = crop ? s.crop[0] : 0, ct = crop ? s.crop[1] : 0, cr = crop ? s.crop[2] : 1, cb = crop ? s.crop[3] : 1;
        float ow = f[0] * (cr - cl), oh = f[1] * (cb - ct);
        float scale = Math.min(1f, maxSide / Math.max(1f, Math.max(ow, oh)));
        int bw = Math.max(1, Math.round(ow * scale)), bh = Math.max(1, Math.round(oh * scale));
        Matrix m = matrix(src.getWidth(), src.getHeight(), s);
        m.postTranslate(-cl * f[0], -ct * f[1]);
        m.postScale(scale, scale);
        Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.BLACK);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    /** Geometria + filtro + ajustes (+ desenho e texto se {@code overlays}). */
    static Bitmap render(Bitmap src, State s, boolean crop, int maxSide, boolean overlays) {
        Bitmap g = geometry(src, s, crop, maxSide);
        int w = g.getWidth(), h = g.getHeight();
        int[] px = new int[w * h];
        g.getPixels(px, 0, w, 0, 0, w, h);
        filter(px, s.filter, s.intensity);
        adjust(px, w, h, s.adjust);
        g.setPixels(px, 0, w, 0, 0, w, h);
        if (overlays && crop) draw(new Canvas(g), s, new RectF(0, 0, w, h));
        return g;
    }

    // ---- filtros ----

    static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : Math.round(v); }

    static void filter(int[] px, int filter, int intensity) {
        if (filter <= 0 || filter >= FILTERS.length || intensity <= 0) return;
        int[] orig = intensity < 100 ? px.clone() : null;
        if (filter == 1) { ScanFilters.levels(px, true); ScanFilters.saturate(px, 1.1f); }
        else for (int i = 0; i < px.length; i++) {
            int c = px[i];
            float r = Color.red(c), g = Color.green(c), b = Color.blue(c), l = .299f * r + .587f * g + .114f * b;
            switch (filter) {
                case 2: r = l + (r - l) * 1.4f; g = l + (g - l) * 1.4f; b = l + (b - l) * 1.4f; r = (r - 128) * 1.15f + 128; g = (g - 128) * 1.15f + 128; b = (b - 128) * 1.15f + 128; break;
                case 3: r = r * 1.08f + 12; g = g * 1.02f + 4; b = b * .88f; break;
                case 4: r = r * .9f; g = g * 1.0f + 2; b = b * 1.1f + 12; break;
                case 5: r = g = b = (l - 128) * 1.1f + 128; break;
                case 6: { float sr = .393f * r + .769f * g + .189f * b, sg = .349f * r + .686f * g + .168f * b, sb = .272f * r + .534f * g + .131f * b; r = sr; g = sg; b = sb; break; }
                case 7: { float sr = .393f * r + .769f * g + .189f * b, sg = .349f * r + .686f * g + .168f * b, sb = .272f * r + .534f * g + .131f * b;
                    r = (r * .6f + sr * .4f) * .85f + 28; g = (g * .6f + sg * .4f) * .85f + 22; b = (b * .6f + sb * .4f) * .8f + 18; break; }
                case 8: r = l + (r - l) * .75f; g = l + (g - l) * .75f; b = l + (b - l) * .75f; r = (r - 128) * 1.35f + 118; g = (g - 128) * 1.35f + 118; b = (b - 128) * 1.35f + 118; break;
                case 9: r = (r - 128) * .82f + 136; g = (g - 128) * .82f + 136; b = (b - 128) * .82f + 136; r = l + (r - l) * .9f; g = l + (g - l) * .9f; b = l + (b - l) * .9f; break;
                case 10: r = g = b = (l - 128) * 1.45f + 128; break;
                case 11: r = (l + (r - l) * .7f) * .8f + 40; g = (l + (g - l) * .7f) * .8f + 40; b = (l + (b - l) * .7f) * .8f + 44; break;
                default: break;
            }
            px[i] = Color.rgb(clamp(r), clamp(g), clamp(b));
        }
        if (orig != null) {
            float k = intensity / 100f;
            for (int i = 0; i < px.length; i++) {
                int a = orig[i], b = px[i];
                px[i] = Color.rgb(clamp(Color.red(a) + (Color.red(b) - Color.red(a)) * k), clamp(Color.green(a) + (Color.green(b) - Color.green(a)) * k), clamp(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k));
            }
        }
    }

    // ---- ajustes ----

    static float smooth(float e0, float e1, float x) { float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0))); return t * t * (3 - 2 * t); }

    static void adjust(int[] px, int w, int h, int[] a) {
        boolean any = false;
        for (int v : a) any |= v != 0;
        if (!any) return;
        int[] lut = new int[256];
        float bright = a[BRIGHTNESS] * 1.1f, contrast = (100f + a[CONTRAST]) / 100f;
        contrast *= contrast;
        for (int v = 0; v < 256; v++) {
            float x = v + bright;
            x = (x - 128) * contrast + 128;
            x += a[HIGHLIGHTS] * .55f * smooth(110, 255, x);
            x += a[SHADOWS] * .55f * (1 - smooth(0, 140, x));
            lut[v] = clamp(x);
        }
        float sat = 1 + a[SATURATION] / 100f, warm = a[WARMTH] * .28f, tint = a[TINT] * .25f;
        float vignette = a[VIGNETTE] / 100f, grain = a[GRAIN] * .35f;
        Random random = new Random(1234);
        float cx = w / 2f, cy = h / 2f, maxD = (float) Math.hypot(cx, cy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x, c = px[i];
                float r = lut[Color.red(c)], g = lut[Color.green(c)], b = lut[Color.blue(c)];
                if (sat != 1) { float l = .299f * r + .587f * g + .114f * b; r = l + (r - l) * sat; g = l + (g - l) * sat; b = l + (b - l) * sat; }
                if (warm != 0) { r += warm; b -= warm; }
                if (tint != 0) { g -= tint; r += tint * .3f; b += tint * .3f; }
                if (vignette > 0) { float d = (float) Math.hypot(x - cx, y - cy) / maxD, k = 1 - vignette * .75f * smooth(.4f, 1.05f, d); r *= k; g *= k; b *= k; }
                if (grain > 0) { float n = (random.nextFloat() - .5f) * 2 * grain; r += n; g += n; b += n; }
                px[i] = Color.rgb(clamp(r), clamp(g), clamp(b));
            }
        }
        if (a[SHARPNESS] > 0) ScanFilters.sharpen(px, w, h, a[SHARPNESS] / 100f);
    }

    /** Ajuste automático a partir da luminância: brilho para o meio, mais contraste se a foto estiver baça. */
    static void auto(Bitmap preview, State s) {
        int w = preview.getWidth(), h = preview.getHeight(), step = Math.max(1, (w * h) / 40000);
        int[] px = new int[w * h];
        preview.getPixels(px, 0, w, 0, 0, w, h);
        double sum = 0, sq = 0;
        int n = 0;
        for (int i = 0; i < px.length; i += step) { int l = ScanFilters.luma(px[i]); sum += l; sq += l * l; n++; }
        double mean = sum / n, std = Math.sqrt(Math.max(0, sq / n - mean * mean));
        s.adjust[BRIGHTNESS] = (int) Math.max(-40, Math.min(40, (128 - mean) / 1.6));
        s.adjust[CONTRAST] = std < 45 ? 22 : std < 60 ? 10 : 0;
        s.adjust[SATURATION] = 12;
        s.adjust[SHADOWS] = mean < 100 ? 20 : 0;
        s.adjust[HIGHLIGHTS] = mean > 160 ? -20 : 0;
        s.adjust[SHARPNESS] = Math.max(s.adjust[SHARPNESS], 20);
    }

    // ---- desenho e texto ----

    static void draw(Canvas c, State s, RectF frame) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (Stroke k : s.strokes) stroke(c, k, frame, p);
        for (Label l : s.labels) label(c, l, frame);
    }

    static void stroke(Canvas c, Stroke k, RectF frame, Paint p) {
        if (k.points.length < 2) return;
        p.setColor(k.marker ? (k.color & 0xffffff) | 0x66000000 : k.color);
        p.setStrokeWidth(Math.max(1, k.width * frame.width() * (k.marker ? 2.5f : 1)));
        Path path = new Path();
        path.moveTo(frame.left + k.points[0] * frame.width(), frame.top + k.points[1] * frame.height());
        if (k.points.length == 2) path.lineTo(frame.left + k.points[0] * frame.width() + .1f, frame.top + k.points[1] * frame.height());
        for (int i = 2; i + 1 < k.points.length; i += 2) path.lineTo(frame.left + k.points[i] * frame.width(), frame.top + k.points[i + 1] * frame.height());
        c.drawPath(path, p);
    }

    static TextPaint labelPaint(Label l, RectF frame) {
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(l.color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(Math.max(6, l.size * frame.width()));
        t.setTextAlign(Paint.Align.CENTER);
        if (!l.box) t.setShadowLayer(t.getTextSize() * .08f, 0, t.getTextSize() * .04f, 0x99000000);
        return t;
    }

    /** Caixa do texto no enquadramento (para desenhar e para tocar). */
    static RectF labelBounds(Label l, RectF frame) {
        TextPaint t = labelPaint(l, frame);
        float w = t.measureText(l.text.isEmpty() ? " " : l.text), size = t.getTextSize();
        float cx = frame.left + l.x * frame.width(), cy = frame.top + l.y * frame.height();
        return new RectF(cx - w / 2 - size * .3f, cy - size * .85f, cx + w / 2 + size * .3f, cy + size * .35f);
    }

    static void label(Canvas c, Label l, RectF frame) {
        if (l.text.isEmpty()) return;
        TextPaint t = labelPaint(l, frame);
        if (l.box) {
            RectF b = labelBounds(l, frame);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            int lum = ScanFilters.luma(l.color);
            bg.setColor(lum > 140 ? 0xcc000000 : 0xe6ffffff);
            c.drawRoundRect(b, t.getTextSize() * .3f, t.getTextSize() * .3f, bg);
        }
        c.drawText(l.text, frame.left + l.x * frame.width(), frame.top + l.y * frame.height(), t);
    }
}
