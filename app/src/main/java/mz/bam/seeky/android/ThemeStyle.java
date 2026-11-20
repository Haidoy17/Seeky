package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import java.util.Random;

/**
 * Identidade visual de cada tema moderno: tipografia dos títulos, forma das caixas dos ícones
 * e texturas desenhadas (papel, quadrados arredondados, grelha, brilho néon). Tudo nativo,
 * sem imagens nem bibliotecas extra.
 */
final class ThemeStyle {
    private ThemeStyle() {}

    static final Typeface SERIF = Typeface.create("serif", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    /** Títulos: sempre em negrito (pedido do autor, 5.22.1 — as versões finas liam-se mal). */
    static final Typeface SERIF_BOLD = Typeface.create("serif", Typeface.BOLD);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    /** Tipografia de título conforme o tema. */
    static void title(TextView t, float sizeSp) {
        AppPalette p = AppPalette.of(t.getContext());
        t.setShadowLayer(0, 0, 0, 0);
        t.setAllCaps(false);
        t.setLetterSpacing(0);
        switch (p.style) {
            case "areia": case "floresta": case "salvia": case "pinheiro": t.setTypeface(SERIF_BOLD); break;
            case "aurora": case "ouro": t.setTypeface(BOLD); t.setAllCaps(sizeSp >= 18); t.setLetterSpacing(sizeSp >= 18 ? .08f : .02f); break;
            case "neon": t.setTypeface(BOLD); t.setShadowLayer(sizeSp >= 18 ? 14 : 8, 0, 0, (p.accent & 0xffffff) | 0x99000000); break;
            case "gelo": t.setTypeface(CONDENSED); t.setAllCaps(sizeSp >= 20); t.setLetterSpacing(.02f); break;
            case "ciano": t.setTypeface(BOLD); t.setLetterSpacing(.01f); break;
            case "vidro": case "carvao": t.setTypeface(BOLD); t.setLetterSpacing(-.01f); break;
            default: t.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    /** Fundo da caixa de um ícone (menus, painéis, ferramentas, categorias). */
    static Drawable tile(Context c, AppPalette p, int sizeDp) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        switch (p.tile) {
            case AppPalette.TILE_OUTLINE:
                g.setColor(p.surface);
                g.setCornerRadius(sizeDp * .28f * d);
                g.setStroke(Math.max(1, Math.round(d)), p.style.equals("neon") ? ThemeColors.blend(p.line, p.accent, .35f) : p.line);
                return g;
            case AppPalette.TILE_NONE:
                g.setColor(Color.TRANSPARENT);
                return g;
            case AppPalette.TILE_CIRCLE:
                g.setShape(GradientDrawable.OVAL);
                g.setColor(p.tint);
                return g;
            default:
                g.setColor(p.tint);
                g.setCornerRadius(sizeDp * .3f * d);
                return g;
        }
    }

    /** Caixa grande das categorias do Início: cartão claro/escuro conforme o tema. */
    static Drawable card(Context c, AppPalette p, int sizeDp) {
        float d = c.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        if (p.tile == AppPalette.TILE_CIRCLE) { g.setShape(GradientDrawable.OVAL); g.setColor(p.surface); g.setStroke(Math.round(d), p.line); return g; }
        g.setColor(p.style.equals("aurora") || p.style.equals("ouro") ? ThemeColors.blend(p.surface, p.secondary, p.light ? .10f : .08f) : p.surface);
        g.setCornerRadius(sizeDp * (p.style.equals("gelo") ? .32f : .26f) * d);
        if (p.tile == AppPalette.TILE_OUTLINE || p.style.equals("areia") || p.style.equals("gelo") || p.style.equals("vidro"))
            g.setStroke(Math.max(1, Math.round(d)), p.style.equals("neon") ? ThemeColors.blend(p.line, p.accent, .35f) : p.line);
        return g;
    }

    private static Bitmap noise;

    /** Textura de papel: ruído fino gerado uma vez (96×96) e repetido — leve mesmo a deslizar. */
    static void paper(Canvas c, RectF b, long seed, float density) {
        Bitmap tile = noise();
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        BitmapShader shader = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix m = new Matrix();
        m.setScale(Math.max(1, density * .55f), Math.max(1, density * .55f));
        m.postTranslate((seed * 37) % 96, (seed * 53) % 96);
        shader.setLocalMatrix(m);
        p.setShader(shader);
        c.drawRect(b, p);
    }

    static synchronized Bitmap noise() {
        if (noise != null) return noise;
        int size = 96;
        int[] px = new int[size * size];
        Random r = new Random(1906);
        for (int i = 0; i < px.length; i++) {
            int v = r.nextInt(100);
            px[i] = v < 7 ? 0x14000000 : v < 14 ? 0x12ffffff : v < 18 ? 0x0a000000 : 0;
        }
        noise = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        return noise;
    }

    /** Padrão de quadrados arredondados inclinados (tema Carvão). */
    static void squares(Canvas c, RectF b, float density, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float size = 34 * density, gap = 9 * density, step = size + gap, r = 9 * density;
        c.save();
        c.clipRect(b);
        c.rotate(-14, b.centerX(), b.centerY());
        float span = Math.max(b.width(), b.height()) * 1.6f;
        for (float y = b.centerY() - span / 2; y < b.centerY() + span / 2; y += step)
            for (float x = b.centerX() - span / 2; x < b.centerX() + span / 2; x += step)
                c.drawRoundRect(x, y, x + size, y + size, r, r, p);
        c.restore();
    }

    /** Grelha em perspectiva (tema Néon). */
    static void grid(Canvas c, RectF b, float density, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(Math.max(1, density * .6f));
        c.save();
        c.clipRect(b);
        float step = 26 * density;
        for (float x = b.left - b.height(); x < b.right + b.height(); x += step)
            c.drawLine(x, b.bottom, x + b.height() * .45f, b.top, p);
        // linhas horizontais cada vez mais juntas (perspectiva); limite fixo para nunca prender o desenho
        float s = step;
        for (int i = 0; i < 40 && s > 1.5f; i++) { float y = b.bottom - (step - s) / (1 - .86f); if (y < b.top) break; c.drawLine(b.left, y, b.right, y, p); s *= .86f; }
        c.restore();
    }

    /** Traço com brilho (néon): várias passagens largas e transparentes por baixo de uma fina. */
    static void glow(Canvas c, Path path, int color, float density) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        float[] widths = {14, 9, 5, 2.2f};
        int[] alphas = {0x14, 0x26, 0x55, 0xff};
        for (int i = 0; i < widths.length; i++) {
            p.setStrokeWidth(widths[i] * density);
            p.setColor((color & 0xffffff) | (alphas[i] << 24));
            c.drawPath(path, p);
        }
        p.setStrokeWidth(.9f * density);
        p.setColor(0xccffffff);
        c.drawPath(path, p);
    }

    /** Brilho radial suave (tema Vidro). */
    static void bloom(Canvas c, float cx, float cy, float radius, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(cx, cy, Math.max(1, radius), new int[]{(color & 0xffffff) | 0x55000000, (color & 0xffffff) | 0x18000000, color & 0xffffff}, new float[]{0, .45f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, p);
    }
}
