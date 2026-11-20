package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Linhas de painel para números e ficheiros: cartão de resumo, barras proporcionais com ícone,
 * barra empilhada com legenda e linhas de ficheiro (ícone do tipo, nome, tamanho, idade e pasta).
 * Usadas no mapa do armazenamento, nas estatísticas de leitura e no Pomodoro.
 */
final class UiRows {
    private UiRows() {}

    /** Cor fixa por categoria (os gráficos precisam de cores distintas em todos os temas). */
    static int color(String category) {
        switch (category == null ? "" : category) {
            case "Vídeos": return 0xffe4572e;
            case "Áudio": return 0xfff3a712;
            case "Imagens": return 0xff2a9d8f;
            case "PDF": return 0xffc0392b;
            case "Documentos": return 0xff4f6dd6;
            case "Aplicações": return 0xff8e44ad;
            case "Arquivos": return 0xff6d7b8d;
            default: return 0xff9aa5b1;
        }
    }

    static LinearLayout card(UiPanel p, Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(p.dp(14), p.dp(12), p.dp(14), p.dp(12));
        GradientDrawable bg = p.shape(p.surface, 16);
        bg.setStroke(p.dp(1), AppPalette.of(c).line);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = p.dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    static TextView text(UiPanel p, String s, int size, int color, boolean bold) {
        TextView t = p.text("", size, color);
        t.setText(s);
        if (bold) t.setTypeface(ThemeStyle.BOLD);
        return t;
    }

    static FrameLayout tile(UiPanel p, Context c, String symbol, int color) {
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(p.shape((color & 0xffffff) | 0x22000000, 12));
        tile.addView(new Icons(c, symbol, color), new FrameLayout.LayoutParams(p.dp(22), p.dp(22), Gravity.CENTER));
        return tile;
    }

    static View bar(UiPanel p, Context c, float fraction, int color) {
        Bar b = new Bar(c, Math.max(0f, Math.min(1f, fraction)), color, AppPalette.of(c).tint);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, p.dp(6)));
        return b;
    }

    /** Resumo: título pequeno, valor grande, barra e uma grelha de 2–3 números. */
    static View hero(UiPanel p, Context c, String caption, String big, String detail, float fraction, int color, String[][] stats) {
        LinearLayout box = card(p, c);
        box.addView(text(p, caption.toUpperCase(java.util.Locale.ROOT), 10, p.muted, false));
        TextView value = text(p, big, 24, p.ink, true);
        value.setPadding(0, p.dp(4), 0, p.dp(2));
        box.addView(value);
        if (fraction >= 0) {
            View bar = bar(p, c, fraction, color);
            ((LinearLayout.LayoutParams) bar.getLayoutParams()).height = p.dp(10);
            ((LinearLayout.LayoutParams) bar.getLayoutParams()).topMargin = p.dp(6);
            box.addView(bar);
        }
        if (detail != null && !detail.isEmpty()) {
            TextView d = text(p, detail, 12, p.muted, false);
            d.setPadding(0, p.dp(6), 0, 0);
            box.addView(d);
        }
        if (stats != null && stats.length > 0) {
            LinearLayout grid = new LinearLayout(c);
            grid.setPadding(0, p.dp(12), 0, 0);
            for (String[] s : stats) {
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.addView(text(p, s[0], 16, p.ink, true));
                TextView l = text(p, s[1], 11, p.muted, false);
                l.setMaxLines(2);
                cell.addView(l);
                grid.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
            }
            box.addView(grid);
        }
        return box;
    }

    /** Linha com ícone, título, valor à direita, barra proporcional e subtítulo. */
    static View meter(UiPanel p, Context c, String symbol, String title, String value, String subtitle, float fraction, int color, Runnable click) {
        LinearLayout box = card(p, c);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (symbol != null) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(p.dp(38), p.dp(38));
            tp.rightMargin = p.dp(12);
            top.addView(tile(p, c, symbol, color), tp);
        }
        LinearLayout middle = new LinearLayout(c);
        middle.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = new LinearLayout(c);
        TextView t = text(p, title, 14, p.ink, true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        line.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(p, value, 13, p.ink, true);
        v.setPadding(p.dp(8), 0, 0, 0);
        line.addView(v);
        middle.addView(line);
        View bar = bar(p, c, fraction, color);
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).topMargin = p.dp(7);
        middle.addView(bar);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(p, subtitle, 11, p.muted, false);
            s.setPadding(0, p.dp(5), 0, 0);
            s.setMaxLines(2);
            s.setEllipsize(TextUtils.TruncateAt.END);
            middle.addView(s);
        }
        top.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));
        if (click != null) {
            Icons arrow = new Icons(c, "chevron", p.muted);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(p.dp(18), p.dp(18));
            ap.leftMargin = p.dp(6);
            top.addView(arrow, ap);
            box.setOnClickListener(x -> click.run());
        }
        box.addView(top);
        return box;
    }

    /** Ficheiro: ícone do tipo, nome numa linha, "tamanho · há N meses" e a pasta abreviada. */
    static View file(UiPanel p, Context c, String name, String category, long size, long modified, String folder, Runnable click) {
        LinearLayout box = card(p, c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int color = color(category);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(p.dp(40), p.dp(40));
        tp.rightMargin = p.dp(12);
        box.addView(tile(p, c, Icons.category(category == null ? "" : category), color), tp);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView n = text(p, name, 14, p.ink, true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        text.addView(n);
        LinearLayout chips = new LinearLayout(c);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(0, p.dp(4), 0, 0);
        TextView sz = text(p, Rules.bytes(size), 11, color, true);
        sz.setPadding(p.dp(7), p.dp(2), p.dp(7), p.dp(2));
        sz.setBackground(p.shape((color & 0xffffff) | 0x1f000000, 8));
        chips.addView(sz);
        if (modified > 0) {
            TextView age = text(p, ago(c, modified, System.currentTimeMillis()), 11, p.muted, false);
            age.setPadding(p.dp(8), 0, 0, 0);
            chips.addView(age);
        }
        text.addView(chips);
        if (folder != null && !folder.isEmpty()) {
            TextView f = text(p, folder, 11, p.muted, false);
            f.setPadding(0, p.dp(4), 0, 0);
            f.setMaxLines(1);
            f.setEllipsize(TextUtils.TruncateAt.START);
            text.addView(f);
        }
        box.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (click != null) box.setOnClickListener(x -> click.run());
        return box;
    }

    /** Barra empilhada por categoria e legenda em duas colunas. */
    static View stacked(UiPanel p, Context c, String[] labels, long[] values, int[] colors) {
        LinearLayout box = card(p, c);
        long total = 0;
        for (long v : values) total += Math.max(0, v);
        Stack bar = new Stack(c, values, colors, AppPalette.of(c).tint);
        box.addView(bar, new LinearLayout.LayoutParams(-1, p.dp(14)));
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(c);
                row.setPadding(0, p.dp(i == 0 ? 12 : 6), 0, 0);
                box.addView(row);
            }
            LinearLayout item = new LinearLayout(c);
            item.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(c);
            dot.setBackground(p.shape(colors[i], 5));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(p.dp(10), p.dp(10));
            dp.rightMargin = p.dp(7);
            item.addView(dot, dp);
            TextView l = text(p, labels[i] + " · " + (total == 0 ? 0 : Math.round(values[i] * 100.0 / total)) + "%", 12, p.ink, false);
            l.setMaxLines(1);
            item.addView(l);
            row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return box;
    }

    /** "1 ficheiro" / "3 ficheiros". */
    static String count(Context c, long n, String one, String many) { return n + " " + AppLanguage.text(c, n == 1 ? one : many); }

    static String ago(Context c, long time, long now) {
        long days = Math.max(0, (now - time) / 86400000L);
        if (days < 1) return AppLanguage.text(c, "hoje");
        if (days < 31) return AppLanguage.text(c, "há") + " " + days + " " + AppLanguage.text(c, days == 1 ? "dia" : "dias");
        if (days < 365) { long m = days / 30; return AppLanguage.text(c, "há") + " " + m + " " + AppLanguage.text(c, m == 1 ? "mês" : "meses"); }
        long y = days / 365;
        return AppLanguage.text(c, "há") + " " + y + " " + AppLanguage.text(c, y == 1 ? "ano" : "anos");
    }

    static final class Bar extends View {
        final float fraction;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG);
        Bar(Context c, float fraction, int color, int trackColor) { super(c); this.fraction = fraction; fill.setColor(color); track.setColor(trackColor); }
        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth(), r = h / 2;
            canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, track);
            if (fraction > 0) canvas.drawRoundRect(new RectF(0, 0, Math.max(h, w * fraction), h), r, r, fill);
        }
    }

    static final class Stack extends View {
        final long[] values;
        final int[] colors;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG);
        Stack(Context c, long[] values, int[] colors, int trackColor) { super(c); this.values = values; this.colors = colors; track.setColor(trackColor); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2;
            long total = 0;
            for (long v : values) total += Math.max(0, v);
            canvas.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(new RectF(0, 0, w, h), r, r, android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawRect(0, 0, w, h, track);
            float x = 0, gap = getResources().getDisplayMetrics().density;
            for (int i = 0; i < values.length && total > 0; i++) {
                float seg = w * Math.max(0, values[i]) / total;
                if (seg <= 0) continue;
                paint.setColor(colors[i]);
                canvas.drawRect(x, 0, Math.min(w, x + seg), h, paint);
                x += seg;
                if (x < w) { paint.setColor(Color.TRANSPARENT); canvas.drawRect(x - gap / 2, 0, x + gap / 2, h, track); }
            }
            canvas.restore();
        }
    }
}
