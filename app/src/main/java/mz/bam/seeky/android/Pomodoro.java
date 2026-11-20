package mz.bam.seeky.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

/**
 * Pomodoro ligado às disciplinas: ciclos de foco e pausa (25/5, 50/10 ou 15/3, pausa longa no
 * fim) sobre o temporizador de estudo do Seeky, que regista o tempo de cada disciplina. No fim de
 * cada fase, uma notificação abre o Seeky e começa a fase seguinte.
 */
final class Pomodoro {
    static final int[][] PATTERNS = {{25, 5, 15}, {50, 10, 20}, {15, 3, 10}};
    static final String[] LABELS = {"Clássico 25 + 5 min", "Longo 50 + 10 min", "Curto 15 + 3 min"};
    static final int ROUNDS = 4, NOTIFICATION = 520;

    private Pomodoro() {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("pomodoro", Context.MODE_PRIVATE); }

    static boolean active(Context c) { return prefs(c).getBoolean("active", false); }

    /** Próxima fase depois da que terminou: {tipo, minutos, ronda} ou null quando o ciclo acaba. */
    static Object[] after(String phase, int round, int pattern) {
        int[] p = PATTERNS[Math.max(0, Math.min(PATTERNS.length - 1, pattern))];
        if (phase.equals("Foco")) return round >= ROUNDS ? new Object[]{"Pausa", p[2], round} : new Object[]{"Pausa", p[1], round};
        if (round >= ROUNDS) return null;
        return new Object[]{"Foco", p[0], round + 1};
    }

    static void start(MainActivity a, long subject, String subjectName, int pattern) {
        if (StudyTimerService.running || FocusSessions.prefs(a).getBoolean("timer_active", false)) { a.toast("Já existe uma sessão em curso. Termine-a primeiro."); return; }
        prefs(a).edit().putBoolean("active", true).putLong("subject", subject).putString("name", subjectName).putInt("pattern", pattern)
                .putInt("round", 1).putString("phase", "Foco").putBoolean("waiting", false).apply();
        Notify.ask(a);
        begin(a, "Foco", PATTERNS[pattern][0], 1);
    }

    static void begin(Context c, String type, int minutes, int round) {
        SharedPreferences p = prefs(c);
        String goal = "Pomodoro " + round + "/" + ROUNDS + (type.equals("Pausa") ? " · " + AppLanguage.text(c, "pausa") : "") + (p.getString("name", "").isEmpty() ? "" : " · " + p.getString("name", ""));
        p.edit().putString("phase", type).putInt("round", round).putBoolean("waiting", false).apply();
        StudyTimerService.begin(c, goal.length() > 200 ? goal.substring(0, 200) : goal, type.equals("Foco") ? p.getLong("subject", 0) : 0, minutes, type);
    }

    /** Chamado pelo temporizador quando uma fase termina normalmente. */
    static void completed(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("active", false)) return;
        Object[] next = after(p.getString("phase", "Foco"), p.getInt("round", 1), p.getInt("pattern", 0));
        if (next == null) {
            p.edit().putBoolean("active", false).apply();
            Notify.show(c, NOTIFICATION, "Pomodoro concluído", AppLanguage.text(c, "Completou") + " " + ROUNDS + " " + AppLanguage.text(c, "rondas de foco. Bom trabalho!"), new Intent(c, MainActivity.class).putExtra(SeekyWidget.EXTRA, "pomodoro"));
            return;
        }
        p.edit().putBoolean("waiting", true).putString("next_type", (String) next[0]).putInt("next_minutes", (Integer) next[1]).putInt("next_round", (Integer) next[2]).apply();
        String text = next[0].equals("Pausa") ? AppLanguage.text(c, "Toque para começar a pausa de") + " " + next[1] + " min."
                : AppLanguage.text(c, "Toque para começar a ronda") + " " + next[2] + " " + AppLanguage.text(c, "de foco") + " (" + next[1] + " min).";
        Notify.show(c, NOTIFICATION, next[0].equals("Pausa") ? "Hora da pausa" : "Voltar ao foco", text, new Intent(c, MainActivity.class).putExtra(SeekyWidget.EXTRA, "pomodoro_next"));
    }

    /** O utilizador tocou na notificação: começa a fase seguinte (a actividade está visível). */
    static void continueNext(MainActivity a) {
        SharedPreferences p = prefs(a);
        if (!p.getBoolean("active", false) || !p.getBoolean("waiting", false)) { show(a); return; }
        if (StudyTimerService.running || FocusSessions.prefs(a).getBoolean("timer_active", false)) { a.toast("Já existe uma sessão em curso."); return; }
        begin(a, p.getString("next_type", "Foco"), p.getInt("next_minutes", 25), p.getInt("next_round", 1));
        a.toast(p.getString("next_type", "Foco").equals("Pausa") ? "Pausa iniciada." : "Foco iniciado.");
    }

    static void stop(Context c) { prefs(c).edit().putBoolean("active", false).putBoolean("waiting", false).apply(); }

    // ---- interface ----

    static List<Object[]> subjects(Context c) {
        List<Object[]> out = new ArrayList<>();
        try (StudyStore db = new StudyStore(c); Cursor cur = db.getReadableDatabase().rawQuery("SELECT id,title FROM subjects ORDER BY title COLLATE NOCASE LIMIT 200", null)) {
            while (cur.moveToNext()) out.add(new Object[]{cur.getLong(0), cur.getString(1)});
        } catch (Exception ignored) {}
        return out;
    }

    /** Minutos de foco por disciplina nos últimos 7 dias. */
    static List<String[]> week(Context c) {
        List<String[]> out = new ArrayList<>();
        long since = System.currentTimeMillis() - 7 * 86400000L;
        try (StudyStore db = new StudyStore(c); Cursor cur = db.getReadableDatabase().rawQuery(
                "SELECT COALESCE(s.title,''),SUM(x.seconds) FROM sessions x LEFT JOIN subjects s ON s.id=x.subject WHERE x.started>=? AND x.type='Foco' GROUP BY x.subject ORDER BY 2 DESC LIMIT 12",
                new String[]{String.valueOf(since)})) {
            while (cur.moveToNext()) out.add(new String[]{cur.getString(0), String.valueOf(cur.getLong(1))});
        } catch (Exception ignored) {}
        return out;
    }

    static void show(MainActivity a) {
        SharedPreferences p = prefs(a);
        UiPanel panel = new UiPanel(a, "Pomodoro", AppLanguage.text(a, "Foco e pausas, com o tempo de cada disciplina"), "clock");
        if (p.getBoolean("active", false)) {
            panel.section("Em curso");
            panel.note(AppLanguage.text(a, "Ronda") + " " + p.getInt("round", 1) + "/" + ROUNDS + " · " + AppLanguage.text(a, p.getString("phase", "Foco")) + (p.getString("name", "").isEmpty() ? "" : " · " + p.getString("name", "")));
            if (p.getBoolean("waiting", false)) panel.action("play", "Começar a fase seguinte", p.getString("next_type", "") + " · " + p.getInt("next_minutes", 0) + " min", false, () -> continueNext(a));
            panel.action("stop", "Terminar o ciclo Pomodoro", "O tempo já estudado fica registado", false, () -> { stop(a); a.toast("Ciclo terminado."); });
        } else {
            panel.section("Começar");
            for (int i = 0; i < PATTERNS.length; i++) {
                final int pattern = i;
                panel.action("play", LABELS[i], ROUNDS + " " + AppLanguage.text(a, "rondas · pausa longa de") + " " + PATTERNS[i][2] + " min", false, () -> chooseSubject(a, pattern));
            }
        }
        List<String[]> week = week(a);
        panel.section("Últimos 7 dias");
        if (week.isEmpty()) panel.note("Ainda não há sessões de foco esta semana.");
        for (String[] w : week) panel.note((w[0].isEmpty() ? AppLanguage.text(a, "Sem disciplina") : w[0]) + " · " + ReadingStats.minutes(a, Long.parseLong(w[1])));
        panel.show();
    }

    static void chooseSubject(MainActivity a, int pattern) {
        List<Object[]> list = subjects(a);
        String[] names = new String[list.size() + 1];
        names[0] = AppLanguage.text(a, "Sem disciplina");
        for (int i = 0; i < list.size(); i++) names[i + 1] = (String) list.get(i)[1];
        a.dialog().setTitle("Disciplina").setItems(names, (d, w) -> {
            long id = w == 0 ? 0 : (Long) list.get(w - 1)[0];
            start(a, id, w == 0 ? "" : names[w], pattern);
        }).setNegativeButton("Cancelar", null).show();
    }
}
