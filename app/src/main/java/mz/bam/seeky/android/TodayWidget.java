package mz.bam.seeky.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;

/**
 * Widget "Hoje": livro actual, progresso da meta de leitura, dias seguidos e cartões de estudo
 * para rever, com os botões Continuar, Ouvir e Rever. Actualiza-se ao ler, ao mudar de tema e a
 * cada 30 minutos.
 */
public final class TodayWidget extends AppWidgetProvider {

    static void pin(android.app.Activity a) {
        AppWidgetManager manager = AppWidgetManager.getInstance(a);
        if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(a, TodayWidget.class), null, null);
        else PlusUi.toast(a, "Mantenha premido o ecrã inicial, toque em Widgets e escolha Seeky Hoje.");
    }

    static void update(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
            if (ids.length == 0) return;
            Summary s = summary(c);
            for (int id : ids) render(c, m, id, s);
        } catch (RuntimeException ignored) {}
    }

    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        Summary s = summary(c);
        for (int id : ids) render(c, manager, id, s);
    }

    static final class Summary { String book; long today, goal; int streak, cards; }

    static Summary summary(Context c) {
        Summary s = new Summary();
        long now = System.currentTimeMillis();
        LastReading.Saved last = LastReading.get(c);
        s.book = last.exists() ? last.title : AppLanguage.text(c, "Abra um livro para começar");
        s.today = ReadingStats.seconds(c, ReadingStats.day(now));
        s.goal = ReadingStats.goal(c) * 60L;
        s.streak = ReadingStats.streak(c, now);
        s.cards = dueCards(c, now);
        return s;
    }

    static int dueCards(Context c, long now) {
        try (StudyStore db = new StudyStore(c)) {
            return (int) db.count("cards", "suspended=0 AND due<=?", new String[]{String.valueOf(now)});
        } catch (Exception e) { return 0; }
    }

    static void render(Context c, AppWidgetManager m, int id, Summary s) {
        AppPalette p = AppPalette.of(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.today_widget);
        v.setInt(R.id.today_bg, "setColorFilter", p.surface);
        v.setTextColor(R.id.today_label, p.accent);
        v.setTextViewText(R.id.today_label, AppLanguage.text(c, "HOJE"));
        v.setTextColor(R.id.today_book, p.ink);
        v.setTextViewText(R.id.today_book, s.book);
        v.setTextColor(R.id.today_goal, p.muted);
        String goal = ReadingStats.minutes(c, s.today) + " / " + (s.goal / 60) + " min";
        if (s.streak > 0) goal += " · " + s.streak + " " + AppLanguage.text(c, "dias seguidos");
        if (s.cards > 0) goal += " · " + s.cards + " " + AppLanguage.text(c, "cartões");
        v.setTextViewText(R.id.today_goal, goal);
        v.setImageViewBitmap(R.id.today_progress, bar(c, s.goal == 0 ? 0 : Math.min(1f, s.today / (float) s.goal), p.accent, p.tint));
        int[][] buttons = {{R.id.today_continue, R.id.today_continue_bg, R.id.today_continue_text},
                {R.id.today_listen, R.id.today_listen_bg, R.id.today_listen_text},
                {R.id.today_review, R.id.today_review_bg, R.id.today_review_text}};
        String[] actions = {"continue", "listen", "review"}, labels = {"Continuar", "Ouvir", "Rever"};
        for (int i = 0; i < 3; i++) {
            boolean main = i == 0;
            v.setInt(buttons[i][1], "setColorFilter", main ? p.accent : p.tint);
            v.setTextColor(buttons[i][2], main ? p.onAccent : p.ink);
            v.setTextViewText(buttons[i][2], AppLanguage.text(c, labels[i]));
            v.setOnClickPendingIntent(buttons[i][0], SeekyWidget.open(c, actions[i], 90 + i));
        }
        v.setOnClickPendingIntent(R.id.today_root, SeekyWidget.open(c, "stats", 89));
        m.updateAppWidget(id, v);
    }

    /** Barra de progresso desenhada como imagem: funciona com as cores do tema em qualquer Android. */
    static Bitmap bar(Context c, float fraction, int fill, int track) {
        float d = c.getResources().getDisplayMetrics().density;
        int w = Math.max(1, (int) (280 * d)), h = Math.max(1, (int) (8 * d));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(track);
        canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, paint);
        if (fraction > 0) {
            paint.setColor(fill);
            canvas.drawRoundRect(new RectF(0, 0, Math.max(h, w * fraction), h), h / 2f, h / 2f, paint);
        }
        return b;
    }
}
