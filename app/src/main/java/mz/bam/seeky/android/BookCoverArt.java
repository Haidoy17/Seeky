package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.Locale;

/**
 * Capa gerada para livros sem imagem, no estilo do tema activo:
 * editorial (Areia/Floresta/clássicos), rótulo empilhado em papel (Aurora/Ouro), moldura néon,
 * vidro com brilho violeta, minimalista preto e branco (Gelo), quadrados (Carvão) e degradê (Ciano).
 * A cor varia por título mas é sempre a mesma para o mesmo livro. Em tamanhos pequenos mostra a inicial.
 */
final class BookCoverArt extends View {
    static final int[] TONES = {0xff4a5a43, 0xffa0583f, 0xffb08d5e, 0xff3e4e5e, 0xff5e3f57, 0xff2f5d5a, 0xff3a3a3d, 0xff8c4a2f};
    final String title, author, style;
    final AppPalette palette;
    final int tone, index;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    BookCoverArt(Context c, String title, String author) {
        super(c);
        this.title = title == null || title.trim().isEmpty() ? "Seeky" : title.trim();
        this.author = author == null ? "" : author.trim();
        palette = AppPalette.of(c);
        style = palette.style;
        index = Math.floorMod(this.title.toLowerCase(Locale.ROOT).hashCode(), 64);
        tone = tones(style)[index % tones(style).length];
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static int[] tones(String style) {
        switch (style) {
            case "aurora": return new int[]{0xff752b40, 0xff5c2133, 0xff8b3a50, 0xff4f3a41, 0xff6b3a3a};
            case "ouro": return new int[]{0xff2c2c2c, 0xff3a3530, 0xff262626, 0xff33302a};
            case "neon": return new int[]{0xff151515, 0xff19181a};
            case "vidro": return new int[]{0xff17161d, 0xff1a1822, 0xff15151b};
            case "gelo": return new int[]{0xfff5f5f6, 0xffefeff1};
            case "carvao": return new int[]{0xff222223, 0xff2a2a2b};
            case "ciano": return new int[]{0xff1d97bd, 0xff2559c9, 0xff1f7fa6, 0xff3a4fd0};
            case "ardosia": return new int[]{0xff565c69, 0xff4a4f5a, 0xffc9735e, 0xff4e7d68};
            case "salvia": return new int[]{0xff5f7e7b, 0xff405a5c, 0xff7a7866, 0xff4f6a62};
            case "nevoa": return new int[]{0xff6e7f9a, 0xff5d7f92, 0xff8a6f7a, 0xff4f5f7b};
            case "hortela": return new int[]{0xff2c6975, 0xff3f8a7a, 0xff1f4f58, 0xff4d8f86};
            case "pinheiro": return new int[]{0xff00412e, 0xff2f5d3f, 0xff4f7a45, 0xff1d4a36};
            case "menta": return new int[]{0xff1b4332, 0xff2d6a4f, 0xff40916c, 0xff2f5f49};
            case "petroleo": return new int[]{0xff4f6966, 0xff3f5553, 0xffb57f5a, 0xff5c7774};
            case "quartzo": return new int[]{0xff4f7d75, 0xff3b6058, 0xff8a6f6b, 0xff5d7c76};
            case "folha": return new int[]{0xff52805f, 0xff416b4e, 0xff6a8a5a, 0xff4d7562};
            case "produtivo": return new int[]{0xff1f4e5f, 0xff163a47, 0xffc0741a, 0xff2d6375};
            default: return TONES;
        }
    }

    /** Cor de fundo da moldura enquanto a capa se desenha. */
    static int tone(Context c, String title) {
        int[] t = tones(AppPalette.of(c).style);
        return t[Math.floorMod((title == null ? "" : title.trim().toLowerCase(Locale.ROOT)).hashCode(), 64) % t.length];
    }

    static int tone(String title) { return TONES[Math.floorMod((title == null ? "" : title.trim().toLowerCase(Locale.ROOT)).hashCode(), TONES.length)]; }

    float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        RectF all = new RectF(0, 0, w, h);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone);
        c.drawRect(all, paint);
        boolean small = w < dp(96);
        int ink;
        switch (style) {
            case "aurora": case "ouro": drawPaperLabel(c, w, h, small); return;
            case "neon": ink = 0xfff4f4f4;
                ThemeStyle.grid(c, new RectF(0, h * .55f, w, h), getResources().getDisplayMetrics().density, 0x10ffffff);
                Path frame = new Path();
                float in = small ? dp(5) : dp(9);
                frame.addRoundRect(new RectF(in, in, w - in, h - in), dp(12), dp(12), Path.Direction.CW);
                ThemeStyle.glow(c, frame, palette.accent, getResources().getDisplayMetrics().density * (small ? .45f : .7f));
                text.setShadowLayer(dp(8), 0, 0, (palette.accent & 0xffffff) | 0xcc000000);
                break;
            case "vidro": ink = 0xfff1f0f5;
                ThemeStyle.bloom(c, w * .85f, h * .1f, Math.max(w, h) * .8f, palette.secondary);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0x26ffffff);
                c.drawRoundRect(dp(1), dp(1), w - dp(1), h - dp(1), dp(10), dp(10), paint); paint.setStyle(Paint.Style.FILL);
                break;
            case "gelo": ink = 0xff111113;
                paint.setColor(0xff111113);
                c.drawCircle(w * (.74f + (index % 3) * .05f), h * (.12f + (index % 4) * .02f), w * .30f, paint);
                paint.setColor(0xffd9d9de);
                c.drawRect(w * .1f, h * .40f, w * .9f, h * .40f + Math.max(1, dp(1)), paint);
                break;
            case "carvao": ink = 0xfff2f2f2;
                ThemeStyle.squares(c, all, getResources().getDisplayMetrics().density * .55f, 0x0fffffff);
                paint.setColor(palette.accent);
                c.drawRect(w * .1f, h * .40f, w * .1f + dp(26), h * .40f + dp(3), paint);
                break;
            case "ciano": ink = 0xffffffff;
                paint.setShader(new LinearGradient(0, 0, w, h, tone, ThemeColors.blend(tone, 0xff2559c9, .6f), Shader.TileMode.CLAMP));
                c.drawRect(all, paint); paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); paint.setColor(0x55ffffff);
                c.drawCircle(w * .78f, h * .22f, w * .26f, paint); c.drawCircle(w * .78f, h * .22f, w * .4f, paint); paint.setStyle(Paint.Style.FILL);
                break;
            default: drawEditorial(c, w, h, small); return;
        }
        if (small) { initial(c, w, h, ink, style.equals("gelo") ? h * .78f : h * .45f); return; }
        // título alinhado à esquerda, na metade inferior
        boolean gelo = style.equals("gelo");
        text.setColor(ink);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(gelo ? ThemeStyle.CONDENSED : ThemeStyle.BOLD);
        text.setLetterSpacing(gelo ? .02f : 0);
        text.setTextSize(Math.max(dp(12), Math.min(dp(22), w * .13f)));
        String shown = gelo ? title.toUpperCase(Locale.ROOT) : title;
        float pad = w * .1f;
        StaticLayout layout = fit(shown, (int) (w - pad * 2), Layout.Alignment.ALIGN_NORMAL, 4);
        // título e autor na zona central: o rodapé fica livre para o menu ⋮ e o selo de progresso
        float top = h * .44f;
        if (top + layout.getHeight() > h * .72f) top = Math.max(h * .2f, h * .72f - layout.getHeight());
        c.save(); c.translate(pad, top); layout.draw(c); c.restore();
        text.setShadowLayer(0, 0, 0, 0);
        caption(c, author, pad, Math.min(h * .78f, top + layout.getHeight() + dp(16)), w - pad * 2, gelo ? 0xff5f5f66 : (ink & 0xffffff) | 0xb3000000, Paint.Align.LEFT);
    }

    /** Aurora e Ouro: papel liso e um rótulo empilhado com o título em maiúsculas espaçadas. */
    void drawPaperLabel(Canvas c, float w, float h, boolean small) {
        float density = getResources().getDisplayMetrics().density;
        ThemeStyle.paper(c, new RectF(0, 0, w, h), index, density * .7f);
        boolean gold = style.equals("ouro");
        int label = gold ? 0xffc6a23c : 0xff8d8987;
        float top = h * (small ? .30f : .36f), bottom = h * (small ? .66f : .70f), r = Math.min(w, h) * .14f;
        paint.setColor(0x40000000);
        c.drawRoundRect(-r, top + dp(3), w * .94f, bottom + dp(3), r, r, paint);
        paint.setColor(label);
        c.drawRoundRect(-r, top, w * .94f, bottom, r, r, paint);
        c.save(); c.clipRect(0, top, w * .94f, bottom); ThemeStyle.paper(c, new RectF(0, top, w, bottom), index + 5, density * .7f); c.restore();
        int ink = 0xff26221f;
        if (small) { initial(c, w, h, ink, (top + bottom) / 2); return; }
        text.setColor(ink);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(ThemeStyle.BOLD);
        text.setLetterSpacing(.08f);
        text.setTextSize(Math.max(dp(10), Math.min(dp(17), w * .095f)));
        float pad = w * .1f;
        StaticLayout layout = fit(title.toUpperCase(Locale.ROOT), (int) (w * .84f - pad), Layout.Alignment.ALIGN_NORMAL, 3);
        float y = Math.max(top + dp(8), top + (bottom - top - layout.getHeight()) / 2 - dp(7));
        c.save(); c.translate(pad, y); layout.draw(c); c.restore();
        text.setLetterSpacing(.18f);
        caption(c, author.isEmpty() ? "SEEKY" : author.toUpperCase(Locale.ROOT), pad, Math.min(bottom - dp(9), y + layout.getHeight() + dp(14)), w * .84f - pad, 0x99261f1c, Paint.Align.LEFT);
    }

    /** Areia, Floresta e clássicos: cor sólida, moldura fina, título centrado em serifa. */
    void drawEditorial(Canvas c, float w, float h, boolean small) {
        paint.setShader(new LinearGradient(0, 0, w, h, 0x26ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, Math.min(w * .08f, dp(10)), 0, 0x33000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, Math.min(w * .08f, dp(10)), h, paint);
        paint.setShader(null);
        int cream = 0xfff4eee3;
        if (small) { initial(c, w, h, cream, h * .45f); return; }
        float inset = Math.max(dp(6), w * .06f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(.8f)));
        paint.setColor(0x66f4eee3);
        c.drawRect(inset, inset, w - inset, h - inset, paint);
        paint.setStyle(Paint.Style.FILL);
        float pad = inset + Math.max(dp(6), w * .06f);
        text.setColor(cream);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(ThemeStyle.SERIF_BOLD);
        text.setTextSize(Math.max(dp(12), Math.min(dp(22), w * .13f)));
        StaticLayout layout = fit(title, (int) Math.max(1, w - pad * 2), Layout.Alignment.ALIGN_CENTER, 4);
        float top = h * .2f;
        c.save(); c.translate(pad, top); layout.draw(c); c.restore();
        float lineY = top + layout.getHeight() + dp(10);
        paint.setColor(0x99f4eee3);
        c.drawRect(w / 2 - dp(14), lineY, w / 2 + dp(14), lineY + Math.max(1f, dp(1)), paint);
        if (h > dp(120)) {
            text.setLetterSpacing(.12f);
            caption(c, author.toUpperCase(Locale.ROOT), w / 2, Math.min(h - inset - dp(14), lineY + dp(30)), w - pad * 2, 0xccf4eee3, Paint.Align.CENTER);
        }
    }

    /** Ajusta o tamanho para nunca partir uma palavra a meio. */
    StaticLayout fit(String value, int width, Layout.Alignment align, int lines) {
        width = Math.max(1, width);
        for (String word : value.split("\\s+")) while (text.getTextSize() > dp(9) && text.measureText(word) > width) text.setTextSize(text.getTextSize() * .92f);
        return StaticLayout.Builder.obtain(value, 0, value.length(), text, width).setAlignment(align).setMaxLines(lines)
                .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0, 1.06f).build();
    }

    void caption(Canvas c, String value, float x, float y, float width, int color, Paint.Align align) {
        if (value == null || value.isEmpty()) return;
        TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        small.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        small.setTextSize(Math.max(dp(8), Math.min(dp(11), getWidth() * .065f)));
        small.setLetterSpacing(.1f);
        small.setColor(color);
        small.setTextAlign(align);
        CharSequence fit = TextUtils.ellipsize(value, small, Math.max(1, width), TextUtils.TruncateAt.END);
        c.drawText(fit, 0, fit.length(), x, y, small);
    }

    void initial(Canvas c, float w, float h, int color, float centerY) {
        text.setColor(color);
        text.setTypeface(style.equals("areia") || style.equals("floresta") || style.equals("classic") ? ThemeStyle.SERIF_BOLD : ThemeStyle.BOLD);
        text.setLetterSpacing(0);
        text.setTextSize(Math.min(w, h) * .42f);
        text.setTextAlign(Paint.Align.CENTER);
        String initial = title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(initial, w / 2, centerY - (fm.ascent + fm.descent) / 2, text);
        text.setShadowLayer(0, 0, 0, 0);
    }
}
