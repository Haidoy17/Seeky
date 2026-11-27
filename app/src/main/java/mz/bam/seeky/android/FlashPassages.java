package mz.bam.seeky.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 5.60 · In the Flash: a phrase that is part of a verse ("da sua plenitude nos tem dado graça sobre graça") shows
 * the passage it comes from above the files; a touch opens it. "Perguntar à Seeky IA" finds it by meaning.
 */
final class FlashPassages {
    private FlashPassages() {}
    static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "seeky-flash-biblia"); t.setDaemon(true); return t; });
    static volatile int serial;

    static void update(Context c, FlashPanelView panel, String query, Consumer<Intent> launch) { if (panel != null) update(c, panel.passages, panel.palette, query, launch); }
    static void update(Context c, LinearLayout box, AppPalette palette, String query, Consumer<Intent> launch) {
        int id = ++serial; box.removeAllViews();
        String q = query == null ? "" : query.trim();
        BibleReferences.Reference typed = q.isEmpty() ? null : BibleReferences.lookup(q);
        if (typed == null && !BibleFinder.looksLikeWords(q)) return;
        Context app = c.getApplicationContext();
        WORK.execute(() -> {
            List<BibleFinder.Hit> hits = typed != null ? java.util.Collections.emptyList() : BibleFind.local(app, q, 2); boolean ai = typed == null && BibleFind.aiReady(app);
            box.post(() -> {
                if (id != serial) return; box.removeAllViews();
                if (typed != null) box.addView(row(c, palette, typed.title, "Abrir a passagem", () -> launch.accept(PassageActivity.intent(c, typed.title, null, false))));
                for (int i = 0; i < hits.size(); i++) { BibleFinder.Hit h = hits.get(i); String t = h.text.length() > 120 ? h.text.substring(0, 120) + "…" : h.text;
                    box.addView(row(c, palette, h.title() + (i == 0 && BibleFinder.confident(hits) ? " · " + AppLanguage.text(c, "passagem encontrada") : ""), "«" + t + "»", () -> launch.accept(PassageActivity.intent(c, h.title(), null, false)))); }
                if (ai) box.addView(row(c, palette, AppLanguage.text(c, "Perguntar à Seeky IA que passagem é"), AppLanguage.text(c, "Também por palavras suas"), () -> launch.accept(PassageActivity.intent(c, null, q, true))));
            });
        });
    }
    static TextView row(Context c, AppPalette p, String title, String sub, Runnable click) { TextView v = new TextView(c);
        SpannableStringBuilder s = new SpannableStringBuilder(title); s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0); s.setSpan(new ForegroundColorSpan(p.accent), 0, s.length(), 0);
        s.append('\n').append(sub); s.setSpan(new ForegroundColorSpan(p.muted), title.length() + 1, s.length(), 0);
        v.setText(s); v.setTextSize(14); v.setTextColor(p.ink); v.setMaxLines(4); v.setGravity(Gravity.CENTER_VERTICAL);
        int pad = PlusUi.dp(c, 12); v.setPadding(pad, PlusUi.dp(c, 9), pad, PlusUi.dp(c, 9));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(p.tint); bg.setCornerRadius(PlusUi.dp(c, 14)); v.setBackground(bg);
        v.setCompoundDrawablePadding(PlusUi.dp(c, 8)); v.setOnClickListener(x -> click.run()); v.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = PlusUi.dp(c, 8); v.setLayoutParams(lp);
        return v;
    }
}
