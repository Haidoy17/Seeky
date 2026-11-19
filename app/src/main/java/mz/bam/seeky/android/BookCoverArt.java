package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/**
 * Capa gerada para livros sem imagem de capa: cor sólida em tons naturais (sempre a mesma para
 * o mesmo título), brilho suave, moldura fina, título em serifa e autor. Em tamanhos pequenos
 * mostra só a inicial, para não competir com o selo de progresso.
 */
final class BookCoverArt extends View {
    static final int[] TONES = {0xff4a5a43, 0xffa0583f, 0xffb08d5e, 0xff3e4e5e, 0xff5e3f57, 0xff2f5d5a, 0xff3a3a3d, 0xff8c4a2f};
    final String title, author;
    final int tone;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    BookCoverArt(Context c, String title, String author) {
        super(c);
        this.title = title == null || title.trim().isEmpty() ? "Seeky" : title.trim();
        this.author = author == null ? "" : author.trim();
        tone = TONES[Math.floorMod(this.title.toLowerCase(java.util.Locale.ROOT).hashCode(), TONES.length)];
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static int tone(String title) { return TONES[Math.floorMod((title == null ? "" : title.trim().toLowerCase(java.util.Locale.ROOT)).hashCode(), TONES.length)]; }

    float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tone);
        c.drawRect(0, 0, w, h, paint);
        // brilho diagonal e lombada
        paint.setShader(new LinearGradient(0, 0, w, h, 0x26ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(new LinearGradient(0, 0, Math.min(w * .08f, dp(10)), 0, 0x33000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, Math.min(w * .08f, dp(10)), h, paint);
        paint.setShader(null);
        int cream = 0xfff4eee3;
        boolean small = w < dp(96);
        if (small) {
            text.setColor(cream);
            text.setTypeface(Typeface.create("serif", Typeface.NORMAL));
            text.setTextSize(Math.min(w, h) * .46f);
            text.setTextAlign(Paint.Align.CENTER);
            String initial = title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
            Paint.FontMetrics fm = text.getFontMetrics();
            c.drawText(initial, w / 2, h * .42f - (fm.ascent + fm.descent) / 2, text);
            return;
        }
        // moldura fina
        float inset = Math.max(dp(6), w * .06f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(.8f)));
        paint.setColor(0x66f4eee3);
        c.drawRect(inset, inset, w - inset, h - inset, paint);
        paint.setStyle(Paint.Style.FILL);
        // título
        float pad = inset + Math.max(dp(6), w * .06f);
        text.setColor(cream);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        text.setTextSize(Math.max(dp(12), Math.min(dp(22), w * .13f)));
        int width = (int) Math.max(1, w - pad * 2);
        // Nunca partir palavras a meio: reduz a letra até a palavra mais longa caber.
        for (String word : title.split("\\s+")) while (text.getTextSize() > dp(9) && text.measureText(word) > width) text.setTextSize(text.getTextSize() * .92f);
        StaticLayout layout = StaticLayout.Builder.obtain(title, 0, title.length(), text, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4).setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setLineSpacing(0, 1.05f).build();
        float top = h * .2f;
        c.save();
        c.translate(pad, top);
        layout.draw(c);
        c.restore();
        // filete decorativo
        float lineY = top + layout.getHeight() + dp(10);
        paint.setColor(0x99f4eee3);
        c.drawRect(w / 2 - dp(14), lineY, w / 2 + dp(14), lineY + Math.max(1f, dp(1)), paint);
        // autor
        if (!author.isEmpty() && h > dp(120)) {
            TextPaint small2 = new TextPaint(text);
            small2.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            small2.setTextSize(Math.max(dp(9), Math.min(dp(12), w * .075f)));
            small2.setLetterSpacing(.12f);
            small2.setColor(0xccf4eee3);
            small2.setTextAlign(Paint.Align.CENTER);
            String a = author.toUpperCase(java.util.Locale.ROOT);
            CharSequence fit = android.text.TextUtils.ellipsize(a, small2, w - pad * 2, android.text.TextUtils.TruncateAt.END);
            c.drawText(fit, 0, fit.length(), w / 2, Math.min(h - inset - dp(14), lineY + dp(30)), small2);
        }
    }
}
