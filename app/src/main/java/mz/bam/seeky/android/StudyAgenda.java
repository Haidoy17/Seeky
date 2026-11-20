package mz.bam.seeky.android;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Provas e trabalhos: calendário com contagem decrescente e lembretes (alguns dias antes, às 9h,
 * e no próprio dia, às 7h30). Guardado em {@code study_agenda}.
 */
final class StudyAgenda {
    static final String PREFS = "study_agenda";
    static final String[] KINDS = {"Exame", "Teste", "Trabalho", "Apresentação"};
    static final int[] REMIND = {0, 1, 3, 7};
    static final String[] REMIND_LABELS = {"Só no dia", "1 dia antes", "3 dias antes", "1 semana antes"};

    private StudyAgenda() {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static JSONArray items(Context c) { try { return new JSONArray(prefs(c).getString("items", "[]")); } catch (Exception e) { return new JSONArray(); } }

    static void store(Context c, JSONArray list) { prefs(c).edit().putString("items", list.toString()).apply(); }

    static long dayStart(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** Dias de calendário entre hoje e a data (negativo se já passou). */
    static int daysUntil(long date, long now) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(dayStart(now));
        b.setTimeInMillis(dayStart(date));
        int days = 0;
        if (b.before(a)) { while (b.before(a) && days > -4000) { b.add(Calendar.DAY_OF_YEAR, 1); days--; } }
        else while (a.before(b) && days < 4000) { a.add(Calendar.DAY_OF_YEAR, 1); days++; }
        return days;
    }

    static String countdown(Context c, int days) {
        if (days == 0) return AppLanguage.text(c, "Hoje");
        if (days == 1) return AppLanguage.text(c, "Amanhã");
        if (days < 0) return AppLanguage.text(c, "Já passou");
        return AppLanguage.text(c, "Faltam") + " " + days + " " + AppLanguage.text(c, "dias");
    }

    static long add(Context c, String title, String kind, String subject, long date, int remind) throws Exception {
        SharedPreferences p = prefs(c);
        long id = p.getLong("next_id", 1);
        p.edit().putLong("next_id", id + 1).apply();
        JSONArray list = items(c);
        list.put(new JSONObject().put("id", id).put("title", title).put("kind", kind).put("subject", subject).put("date", dayStart(date)).put("remind", remind).put("done", false));
        store(c, list);
        scheduleAll(c);
        return id;
    }

    static JSONObject find(Context c, long id) {
        JSONArray list = items(c);
        for (int i = 0; i < list.length(); i++) if (list.optJSONObject(i).optLong("id") == id) return list.optJSONObject(i);
        return null;
    }

    static void update(Context c, long id, String key, Object value) throws Exception {
        JSONArray list = items(c), out = new JSONArray();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o.optLong("id") == id) { if (value == null) { cancel(c, id); continue; } o.put(key, value); }
            out.put(o);
        }
        store(c, out);
        scheduleAll(c);
    }

    // ---- lembretes ----

    static int request(long id, int which) { return 6000 + (int) (id % 5000) * 2 + which; }

    static void cancel(Context c, long id) {
        for (int k = 0; k < 2; k++) Notify.cancel(c, Notify.alarm(c, SeekyAlarms.AGENDA, request(id, k), id * 2 + k));
    }

    static void scheduleAll(Context c) {
        JSONArray list = items(c);
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            long id = o.optLong("id"), date = o.optLong("date");
            cancel(c, id);
            if (o.optBoolean("done") || date + 86400000L < now) continue;
            int before = o.optInt("remind", 1);
            long early = date - before * 86400000L + 9 * 3600000L, day = date + 7 * 3600000L + 30 * 60000L;
            if (before > 0 && early > now) Notify.at(c, early, Notify.alarm(c, SeekyAlarms.AGENDA, request(id, 0), id * 2));
            if (day > now) Notify.at(c, day, Notify.alarm(c, SeekyAlarms.AGENDA, request(id, 1), id * 2 + 1));
        }
    }

    static void fire(Context c, long code) {
        JSONObject o = find(c, code / 2);
        if (o == null || o.optBoolean("done")) return;
        int days = daysUntil(o.optLong("date"), System.currentTimeMillis());
        String what = AppLanguage.text(c, o.optString("kind")) + ": " + o.optString("title") + (o.optString("subject").isEmpty() ? "" : " · " + o.optString("subject"));
        Notify.show(c, 7000 + (int) (code % 1000), countdown(c, days), what, new Intent(c, MainActivity.class).putExtra(SeekyWidget.EXTRA, "agenda"));
    }

    // ---- interface ----

    static void show(MainActivity a) {
        JSONArray list = items(a);
        long now = System.currentTimeMillis();
        List<JSONObject> next = new ArrayList<>(), past = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o.optBoolean("done") || daysUntil(o.optLong("date"), now) < 0) past.add(o); else next.add(o);
        }
        next.sort((x, y) -> Long.compare(x.optLong("date"), y.optLong("date")));
        past.sort((x, y) -> Long.compare(y.optLong("date"), x.optLong("date")));
        UiPanel p = new UiPanel(a, "Provas e trabalhos", next.isEmpty() ? AppLanguage.text(a, "Nada marcado") : next.size() + " " + AppLanguage.text(a, "por fazer"), "calendar");
        p.action("add", "Marcar prova ou trabalho", "Data, disciplina e lembrete", false, () -> form(a));
        if (!next.isEmpty()) p.section("Próximos");
        DateFormat date = DateFormat.getDateInstance(DateFormat.FULL);
        for (JSONObject o : next) p.content.addView(card(a, p, o, date, now));
        if (!past.isEmpty()) {
            p.section("Concluídos");
            for (int i = 0; i < Math.min(6, past.size()); i++) {
                JSONObject o = past.get(i);
                p.action("check", o.optString("title"), AppLanguage.text(a, o.optString("kind")) + " · " + date.format(new Date(o.optLong("date"))), false, () -> itemMenu(a, o));
            }
        }
        p.show();
    }

    static android.view.View card(MainActivity a, UiPanel p, JSONObject o, DateFormat date, long now) {
        int days = daysUntil(o.optLong("date"), now);
        LinearLayout box = new LinearLayout(a);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(p.dp(12), p.dp(10), p.dp(12), p.dp(10));
        GradientDrawable bg = p.shape(p.surface, 16);
        bg.setStroke(p.dp(1), AppPalette.of(a).line);
        box.setBackground(bg);
        TextView count = p.text("", days <= 1 ? 13 : 18, days <= 3 ? 0xffb93d56 : p.accent);
        count.setText(days <= 1 ? countdown(a, days) : days + "\n" + AppLanguage.text(a, "dias"));
        count.setTypeface(ThemeStyle.BOLD);
        count.setGravity(Gravity.CENTER);
        count.setMinWidth(p.dp(58));
        box.addView(count);
        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(p.dp(10), 0, 0, 0);
        TextView title = p.text("", 15, p.ink);
        title.setText(o.optString("title"));
        title.setTypeface(ThemeStyle.BOLD);
        text.addView(title);
        String sub = AppLanguage.text(a, o.optString("kind")) + (o.optString("subject").isEmpty() ? "" : " · " + o.optString("subject")) + "\n" + date.format(new Date(o.optLong("date")));
        TextView detail = p.text("", 12, p.muted);
        detail.setText(sub);
        text.addView(detail);
        box.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        box.setOnClickListener(v -> { p.dialog.dismiss(); itemMenu(a, o); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = p.dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    static void itemMenu(MainActivity a, JSONObject o) {
        long id = o.optLong("id");
        boolean done = o.optBoolean("done");
        a.dialog().setTitle(o.optString("title")).setItems(new String[]{done ? "Marcar como por fazer" : "Marcar como feito", "Apagar"}, (d, w) -> {
            try {
                if (w == 0) update(a, id, "done", !done);
                else update(a, id, "done", null);
                show(a);
            } catch (Exception e) { a.showError(e); }
        }).setNegativeButton("Fechar", null).show();
    }

    static void form(MainActivity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(20), a.dp(6), a.dp(20), 0);
        EditText title = new EditText(a), subject = new EditText(a);
        title.setHint(AppLanguage.text(a, "Ex.: Exame de Matemática"));
        subject.setHint(AppLanguage.text(a, "Disciplina (opcional)"));
        box.addView(title);
        box.addView(subject);
        final int[] kind = {0}, remind = {1};
        final long[] date = {dayStart(System.currentTimeMillis() + 7 * 86400000L)};
        TextView kindView = chip(a, ""), dateView = chip(a, ""), remindView = chip(a, "");
        Runnable refresh = () -> {
            kindView.setText(AppLanguage.text(a, "Tipo:") + " " + AppLanguage.text(a, KINDS[kind[0]]));
            dateView.setText(AppLanguage.text(a, "Data:") + " " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(date[0])));
            remindView.setText(AppLanguage.text(a, "Lembrete:") + " " + AppLanguage.text(a, REMIND_LABELS[remind[0]]));
        };
        refresh.run();
        kindView.setOnClickListener(v -> { kind[0] = (kind[0] + 1) % KINDS.length; refresh.run(); });
        remindView.setOnClickListener(v -> { remind[0] = (remind[0] + 1) % REMIND.length; refresh.run(); });
        dateView.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(date[0]);
            new DatePickerDialog(a, (picker, y, m, d) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(y, m, d, 0, 0, 0);
                date[0] = dayStart(chosen.getTimeInMillis());
                refresh.run();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        box.addView(kindView);
        box.addView(dateView);
        box.addView(remindView);
        a.dialog().setTitle("Marcar prova ou trabalho").setView(box).setPositiveButton("Guardar", (d, w) -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) { a.toast("Escreva o título."); return; }
            try {
                add(a, t, KINDS[kind[0]], subject.getText().toString().trim(), date[0], REMIND[remind[0]]);
                Notify.ask(a);
                show(a);
            } catch (Exception e) { a.showError(e); }
        }).setNegativeButton("Cancelar", null).show();
    }

    static TextView chip(MainActivity a, String text) {
        AppPalette p = AppPalette.of(a);
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(p.ink);
        t.setPadding(a.dp(14), a.dp(11), a.dp(14), a.dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(p.tint);
        bg.setCornerRadius(a.dp(12));
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = a.dp(8);
        t.setLayoutParams(lp);
        return t;
    }
}
