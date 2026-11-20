package mz.bam.seeky.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Efeitos da digitalização, feitos píxel a píxel (iguais em todos os telefones):
 * Original, Melhorar (níveis automáticos por canal), Documento (sombras removidas, texto nítido
 * sobre branco), Sem sombras (mantém as cores), Cinzento, Preto e branco (limiar de Otsu) e
 * Vívido; depois brilho, contraste, nitidez e rotação.
 */
final class ScanFilters {
    static final int ORIGINAL = 0, AUTO = 1, DOCUMENT = 2, NO_SHADOW = 3, GRAY = 4, BW = 5, VIVID = 6;
    static final String[] NAMES = {"Original", "Melhorar", "Documento", "Sem sombras", "Cinzento", "Preto e branco", "Vívido"};

    private ScanFilters() {}

    /** Definições de uma página. */
    static final class Settings {
        int filter = DOCUMENT, brightness, contrast, sharpness = 30, rotation;
        Settings copy() { Settings s = new Settings(); s.filter = filter; s.brightness = brightness; s.contrast = contrast; s.sharpness = sharpness; s.rotation = rotation; return s; }
        String encode() { return filter + "," + brightness + "," + contrast + "," + sharpness + "," + rotation; }
        static Settings decode(String s) {
            Settings out = new Settings();
            try { String[] p = s.split(","); out.filter = Integer.parseInt(p[0]); out.brightness = Integer.parseInt(p[1]); out.contrast = Integer.parseInt(p[2]); out.sharpness = Integer.parseInt(p[3]); out.rotation = Integer.parseInt(p[4]); } catch (Exception ignored) {}
            return out;
        }
    }

    /** Recorte com perspectiva + efeito + ajustes + rotação, com o lado maior limitado a {@code maxSide}. */
    static Bitmap render(Bitmap photo, float[] corners, Settings s, int maxSide) {
        Bitmap flat = ScanActivity.warp(photo, corners, maxSide);
        Bitmap out = apply(flat, s);
        if (out != flat) flat.recycle();
        return out;
    }

    static Bitmap apply(Bitmap src, Settings s) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        switch (s.filter) {
            case AUTO: levels(px, true); saturate(px, 1.15f); break;
            case DOCUMENT: document(px, w, h, src, false); break;
            case NO_SHADOW: shadows(px, w, h, src); break;
            case GRAY: gray(px); levels(px, false); break;
            case BW: document(px, w, h, src, true); break;
            case VIVID: levels(px, true); saturate(px, 1.4f); contrast(px, 12); break;
            default: break;
        }
        if (s.contrast != 0) contrast(px, s.contrast);
        if (s.brightness != 0) brightness(px, s.brightness);
        if (s.sharpness > 0 && s.filter != BW) sharpen(px, w, h, s.sharpness / 100f);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (s.rotation % 360 == 0) return out;
        Matrix m = new Matrix();
        m.postRotate(s.rotation);
        Bitmap turned = Bitmap.createBitmap(out, 0, 0, w, h, m, true);
        if (turned != out) out.recycle();
        return turned;
    }

    static int clamp(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
    static int luma(int c) { return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000; }

    /** Níveis automáticos: estica 1 %–99 % de cada canal (ou da luminância) para 0–255. */
    static void levels(int[] px, boolean perChannel) {
        int[][] hist = new int[3][256];
        int step = Math.max(1, px.length / 200000);
        int n = 0;
        for (int i = 0; i < px.length; i += step) { int c = px[i]; hist[0][Color.red(c)]++; hist[1][Color.green(c)]++; hist[2][Color.blue(c)]++; n++; }
        int[] lo = new int[3], hi = new int[3];
        for (int ch = 0; ch < 3; ch++) {
            int acc = 0; lo[ch] = 0;
            for (int v = 0; v < 256; v++) { acc += hist[ch][v]; if (acc > n * .01) { lo[ch] = v; break; } }
            acc = 0; hi[ch] = 255;
            for (int v = 255; v >= 0; v--) { acc += hist[ch][v]; if (acc > n * .01) { hi[ch] = v; break; } }
        }
        if (!perChannel) { int l = Math.min(lo[0], Math.min(lo[1], lo[2])), h = Math.max(hi[0], Math.max(hi[1], hi[2])); lo = new int[]{l, l, l}; hi = new int[]{h, h, h}; }
        int[][] map = new int[3][256];
        for (int ch = 0; ch < 3; ch++) {
            int range = Math.max(24, hi[ch] - lo[ch]);
            for (int v = 0; v < 256; v++) map[ch][v] = clamp((v - lo[ch]) * 255 / range);
        }
        for (int i = 0; i < px.length; i++) { int c = px[i]; px[i] = Color.argb(255, map[0][Color.red(c)], map[1][Color.green(c)], map[2][Color.blue(c)]); }
    }

    static void gray(int[] px) { for (int i = 0; i < px.length; i++) { int l = luma(px[i]); px[i] = Color.argb(255, l, l, l); } }

    static void saturate(int[] px, float amount) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i], l = luma(c);
            px[i] = Color.argb(255, clamp(Math.round(l + (Color.red(c) - l) * amount)), clamp(Math.round(l + (Color.green(c) - l) * amount)), clamp(Math.round(l + (Color.blue(c) - l) * amount)));
        }
    }

    /** -100..100 */
    static void contrast(int[] px, int amount) {
        float f = (100f + amount) / 100f;
        f *= f;
        int[] map = new int[256];
        for (int v = 0; v < 256; v++) map[v] = clamp(Math.round((v - 128) * f + 128));
        for (int i = 0; i < px.length; i++) { int c = px[i]; px[i] = Color.argb(255, map[Color.red(c)], map[Color.green(c)], map[Color.blue(c)]); }
    }

    /** -100..100 */
    static void brightness(int[] px, int amount) {
        int d = Math.round(amount * 1.4f);
        for (int i = 0; i < px.length; i++) { int c = px[i]; px[i] = Color.argb(255, clamp(Color.red(c) + d), clamp(Color.green(c) + d), clamp(Color.blue(c) + d)); }
    }

    /** Fundo desfocado (iluminação da folha): reduzir muito e voltar a ampliar com filtro bilinear. */
    static int[] background(Bitmap src, int w, int h) {
        int sw = Math.max(2, w / 28), sh = Math.max(2, h / 28);
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        Bitmap tiny = Bitmap.createScaledBitmap(small, Math.max(2, sw / 2), Math.max(2, sh / 2), true);
        Bitmap big = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(big).drawBitmap(tiny, null, new android.graphics.Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        int[] bg = new int[w * h];
        big.getPixels(bg, 0, w, 0, 0, w, h);
        small.recycle(); tiny.recycle(); big.recycle();
        return bg;
    }

    /** Divide pela iluminação: o papel fica branco e o texto escuro, sem sombras da mão ou do telefone. */
    static void document(int[] px, int w, int h, Bitmap src, boolean hard) {
        int[] bg = background(src, w, h);
        int[] hist = new int[256];
        for (int i = 0; i < px.length; i++) {
            int l = luma(px[i]), b = Math.max(40, luma(bg[i]) + 6);
            int v = clamp(l * 255 / b);
            px[i] = v;
            hist[v]++;
        }
        int threshold = ScanActivity.otsu(hist, px.length);
        for (int i = 0; i < px.length; i++) {
            int v = px[i], out;
            if (hard) out = v > Math.min(threshold + 10, 230) ? 255 : 0;
            else out = clamp((v - 120) * 255 / 110);
            px[i] = Color.argb(255, out, out, out);
        }
    }

    static void shadows(int[] px, int w, int h, Bitmap src) {
        int[] bg = background(src, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i], b = bg[i];
            px[i] = Color.argb(255, clamp(Color.red(c) * 245 / Math.max(30, Color.red(b))), clamp(Color.green(c) * 245 / Math.max(30, Color.green(b))), clamp(Color.blue(c) * 245 / Math.max(30, Color.blue(b))));
        }
        levels(px, false);
    }

    /** Máscara de nitidez com vizinhança em cruz. */
    static void sharpen(int[] px, int w, int h, float amount) {
        if (w < 3 || h < 3) return;
        int[] src = px.clone();
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x, c = src[i], n = src[i - w], s = src[i + w], e = src[i + 1], o = src[i - 1];
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int br = (Color.red(n) + Color.red(s) + Color.red(e) + Color.red(o)) >> 2;
                int bgc = (Color.green(n) + Color.green(s) + Color.green(e) + Color.green(o)) >> 2;
                int bb = (Color.blue(n) + Color.blue(s) + Color.blue(e) + Color.blue(o)) >> 2;
                px[i] = Color.argb(255, clamp(Math.round(r + (r - br) * amount * 1.6f)), clamp(Math.round(g + (g - bgc) * amount * 1.6f)), clamp(Math.round(b + (b - bb) * amount * 1.6f)));
            }
        }
    }
}
