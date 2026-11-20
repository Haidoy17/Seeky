package mz.bam.seeky.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

/**
 * Estatísticas e metas de leitura: tempo lido por dia (contado enquanto o leitor está visível),
 * dias seguidos a cumprir a meta, semana, mês e livros com mais tempo, com lembrete diário.
 * Tudo fica no aparelho, em {@code reading_stats}.
 */
final class ReadingStats {
    static final String PREFS = "reading_stats";
    static final int[] GOALS = {5, 10, 15, 20, 30, 45, 60, 90};
    static final int[] HOURS = {-1, 7, 12, 18, 20, 21, 22};
    static final long MAX_SESSION = 4 * 3600;
    private static long started;

    private ReadingStats() {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static int goal(Context c) { return prefs(c).getInt("goal", 15); }
    static int reminder(Context c) { return prefs(c).getInt("reminder", -1); }
    static String day(long time) { return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(time)); }

    // ---- registo ----

    static void begin() { started = SystemClock.elapsedRealtime(); }

    static void end(Context c, String uri, String title) {
        if (started == 0) return;
        long seconds = (SystemClock.elapsedRealtime() - started) / 1000;
        started = 0;
        add(c, System.currentTimeMillis(), seconds, uri, title);
    }

    static synchronized void add(Context c, long now, long seconds, String uri, String title) {
        if (seconds < 5) return;
        seconds = Math.min(seconds, MAX_SESSION);
        SharedPreferences p = prefs(c);
        try {
            JSONObject days = json(p.getString("days", "{}"));
            String key = day(now);
            days.put(key, days.optLong(key) + seconds);
            String oldest = day(now - 400L * 86400000L);
            for (Iterator<String> it = days.keys(); it.hasNext(); ) if (it.next().compareTo(oldest) < 0) it.remove();
            JSONObject books = json(p.getString("books", "{}"));
            if (uri != null && !uri.isEmpty()) {
                JSONObject b = books.optJSONObject(uri);
                if (b == null) b = new JSONObject();
                b.put("t", title == null ? "" : title);
                b.put("s", b.optLong("s") + seconds);
                b.put("last", now);
                books.put(uri, b);
                trim(books, 60);
            }
            p.edit().putString("days", days.toString()).putString("books", books.toString()).apply();
        } catch (Exception e) { AppDiagnostics.record(c, "reading-stats", e); }
        TodayWidget.update(c);
    }

    static void trim(JSONObject books, int keep) {
        if (books.length() <= keep) return;
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = books.keys(); it.hasNext(); ) keys.add(it.next());
        keys.sort((x, y) -> Long.compare(books.optJSONObject(x).optLong("last"), books.optJSONObject(y).optLong("last")));
        for (int i = 0; i < keys.size() - keep; i++) books.remove(keys.get(i));
    }

    static JSONObject json(String s) {
        try { return new JSONObject(s == null || s.isEmpty() ? "{}" : s); } catch (Exception e) { return new JSONObject(); }
    }

    // ---- leitura dos números ----

    static long seconds(Context c, String day) { return json(prefs(c).getString("days", "{}")).optLong(day); }

    static long shift(long time, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.add(Calendar.DAY_OF_YEAR, days);
        return cal.getTimeInMillis();
    }

    /** Dias seguidos a cumprir a meta; hoje só conta depois de cumprida, sem quebrar a sequência. */
    static int streak(Context c, long now) {
        JSONObject days = json(prefs(c).getString("days", "{}"));
        long need = goal(c) * 60L;
        long d = days.optLong(day(now)) >= need ? now : shift(now, -1);
        int n = 0;
        while (days.optLong(day(d)) >= need && n < 400) { n++; d = shift(d, -1); }
        return n;
    }

    /** Segundos dos últimos 7 dias, do mais antigo para hoje. */
    static long[] week(Context c, long now) {
        JSONObject days = json(prefs(c).getString("days", "{}"));
        long[] out = new long[7];
        for (int i = 0; i < 7; i++) out[i] = days.optLong(day(shift(now, i - 6)));
        return out;
    }

    static long month(Context c, long now) {
        JSONObject days = json(prefs(c).getString("days", "{}"));
        String prefix = day(now).substring(0, 8);
        long total = 0;
        for (Iterator<String> it = days.keys(); it.hasNext(); ) { String k = it.next(); if (k.startsWith(prefix)) total += days.optLong(k); }
        return total;
    }

    static List<String[]> topBooks(Context c, int n) {
        JSONObject books = json(prefs(c).getString("books", "{}"));
        List<String[]> out = new ArrayList<>();
        for (Iterator<String> it = books.keys(); it.hasNext(); ) {
            JSONObject b = books.optJSONObject(it.next());
            if (b != null) out.add(new String[]{b.optString("t"), String.valueOf(b.optLong("s"))});
        }
        out.sort((x, y) -> Long.compare(Long.parseLong(y[1]), Long.parseLong(x[1])));
        return out.subList(0, Math.min(n, out.size()));
    }

    static String minutes(Context c, long seconds) {
        long m = seconds / 60;
        if (m < 60) return m + " min";
        return (m / 60) + " h " + (m % 60) + " min";
    }

    // ---- lembrete diário ----

    static void schedule(Context c) {
        int hour = reminder(c);
        android.app.PendingIntent pi = Notify.alarm(c, SeekyAlarms.READING, 501, 0);
        if (hour < 0) { Notify.cancel(c, pi); return; }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 60000) cal.add(Calendar.DAY_OF_YEAR, 1);
        Notify.at(c, cal.getTimeInMillis(), pi);
    }

    static void remind(Context c) {
        long now = System.currentTimeMillis();
        long done = seconds(c, day(now)), need = goal(c) * 60L;
        if (done < need) {
            LastReading.Saved last = LastReading.get(c);
            String text = AppLanguage.text(c, "Faltam") + " " + minutes(c, need - done) + " " + AppLanguage.text(c, "para a meta de leitura de hoje.")
                    + (last.exists() ? " " + AppLanguage.text(c, "Continue:") + " " + last.title : "");
            Intent open = new Intent(c, MainActivity.class).putExtra(SeekyWidget.EXTRA, "continue");
            Notify.show(c, 501, "Meta de leitura", text, open);
        }
        schedule(c);
    }

    // ---- painel ----

    static void show(MainActivity a) {
        long now = System.currentTimeMillis();
        long today = seconds(a, day(now)), need = goal(a) * 60L;
        UiPanel p = new UiPanel(a, "Estatísticas de leitura", AppLanguage.text(a, "Tempo lido e meta diária"), "chart");
        p.section("Hoje");
        TextView big = p.text(minutes(a, today) + " / " + goal(a) + " min", 24, p.ink);
        big.setTypeface(ThemeStyle.BOLD);
        p.content.addView(big);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress((int) Math.min(1000, need == 0 ? 0 : today * 1000 / need));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(p.accent));
        p.content.addView(bar, new LinearLayout.LayoutParams(-1, p.dp(14)));
        int streak = streak(a, now);
        p.note(today >= need ? AppLanguage.text(a, "Meta de hoje cumprida.") + " " + streak + " " + AppLanguage.text(a, "dias seguidos.")
                : streak + " " + AppLanguage.text(a, "dias seguidos a cumprir a meta."));
        p.section("Últimos 7 dias");
        long[] week = week(a, now);
        p.content.addView(new WeekBars(a, week, need, p.accent, p.muted), new LinearLayout.LayoutParams(-1, p.dp(120)));
        long total = 0;
        for (long s : week) total += s;
        p.note(AppLanguage.text(a, "Esta semana:") + " " + minutes(a, total) + " · " + AppLanguage.text(a, "Este mês:") + " " + minutes(a, month(a, now)));
        List<String[]> top = topBooks(a, 5);
        if (!top.isEmpty()) {
            p.section("Livros com mais tempo");
            long most = Math.max(1, Long.parseLong(top.get(0)[1]));
            for (String[] b : top) p.content.addView(UiRows.meter(p, a, "book", b[0].isEmpty() ? AppLanguage.text(a, "Sem título") : b[0], minutes(a, Long.parseLong(b[1])), null, Long.parseLong(b[1]) / (float) most, p.accent, null));
        }
        p.section("Meta e lembrete");
        p.action("star", "Meta diária: " + goal(a) + " min", "Quantos minutos quer ler por dia", false, () -> chooseGoal(a));
        int hour = reminder(a);
        p.action("clock", hour < 0 ? "Lembrete diário: desligado" : "Lembrete diário: " + hour + ":00", "Avisa se a meta ainda não foi cumprida", false, () -> chooseReminder(a));
        p.action("widget", "Widget Hoje", "Meta, livro actual, Ouvir e cartões para rever", false, () -> TodayWidget.pin(a));
        p.show();
    }

    static void chooseGoal(MainActivity a) {
        String[] items = new String[GOALS.length];
        for (int i = 0; i < GOALS.length; i++) items[i] = GOALS[i] + " min";
        a.dialog().setTitle("Meta diária").setItems(items, (d, w) -> {
            prefs(a).edit().putInt("goal", GOALS[w]).apply();
            TodayWidget.update(a);
            show(a);
        }).setNegativeButton("Cancelar", null).show();
    }

    static void chooseReminder(MainActivity a) {
        String[] items = new String[HOURS.length];
        for (int i = 0; i < HOURS.length; i++) items[i] = HOURS[i] < 0 ? AppLanguage.text(a, "Desligado") : HOURS[i] + ":00";
        a.dialog().setTitle("Lembrete diário").setItems(items, (d, w) -> {
            prefs(a).edit().putInt("reminder", HOURS[w]).apply();
            if (HOURS[w] >= 0) Notify.ask(a);
            schedule(a);
            show(a);
        }).setNegativeButton("Cancelar", null).show();
    }

    /** Barras simples dos últimos 7 dias, com a linha da meta. */
    static final class WeekBars extends View {
        final long[] values;
        final long goal;
        final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String[] labels = new String[7];

        WeekBars(Context c, long[] values, long goal, int accent, int muted) {
            super(c);
            this.values = values;
            this.goal = goal;
            bar.setColor(accent);
            text.setColor(muted);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
            line.setColor((muted & 0xffffff) | 0x66000000);
            line.setStrokeWidth(c.getResources().getDisplayMetrics().density);
            long now = System.currentTimeMillis();
            SimpleDateFormat f = new SimpleDateFormat("EEE", AppLanguage.english(c) ? Locale.ENGLISH : new Locale("pt", "PT"));
            for (int i = 0; i < 7; i++) labels[i] = f.format(new Date(shift(now, i - 6))).substring(0, 1).toUpperCase(Locale.ROOT);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            float bottom = h - 18 * d, top = 6 * d;
            long max = Math.max(goal, 60);
            for (long v : values) max = Math.max(max, v);
            float slot = w / 7f, bw = Math.min(26 * d, slot * .55f);
            float goalY = bottom - (bottom - top) * goal / (float) max;
            if (goal > 0) canvas.drawLine(0, goalY, w, goalY, line);
            for (int i = 0; i < 7; i++) {
                float cx = slot * i + slot / 2, bh = (bottom - top) * values[i] / (float) max;
                bar.setAlpha(values[i] >= goal && goal > 0 ? 255 : 150);
                canvas.drawRoundRect(new RectF(cx - bw / 2, bottom - Math.max(bh, values[i] > 0 ? 3 * d : 0), cx + bw / 2, bottom), 5 * d, 5 * d, bar);
                canvas.drawText(labels[i], cx, h - 4 * d, text);
            }
        }
    }
}
