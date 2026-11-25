package mz.bam.seeky.android;

import android.app.Activity;import android.text.InputType;import android.widget.*;import java.util.*;

/** 5.51 · Plano de estudo a partir de um tema (Seeky IA): uma disciplina nova, sessões com data nas tarefas, cartões e uma nota com o plano. */
final class StudyPlanAi {
    private StudyPlanAi() {}
    interface Saved { void open(long subject); }

    static void ask(Activity a, Saved saved) { ask(a, 0, saved); }
    static void ask(Activity a, long presetCourse, Saved saved) {
        if (!SeekyAiSettings.allowed(a, SeekyAiCore.GEMINI)) { PlusUi.toast(a, "Configure primeiro a ligação em Estudar com Seeky."); SeekyAi.open(a, 0); return; }
        PlusUi.run(a, () -> { List<StudyStore.Row> courses; try (StudyStore db = new StudyStore(a)) { courses = db.rows("courses", null, null, "title COLLATE NOCASE", 500); } PlusUi.post(a, () -> form(a, courses, presetCourse, saved)); });
    }
    static void form(Activity a, List<StudyStore.Row> courses, long presetCourse, Saved saved) {
        LinearLayout form = PlusUi.column(a);
        EditText theme = PlusUi.input(a, "Tema (ex.: Evangelho de João)", "", 300);
        EditText weeks = PlusUi.input(a, "Semanas (1 a 12)", "4", 2), per = PlusUi.input(a, "Sessões por semana (1 a 7)", "3", 1), minutes = PlusUi.input(a, "Minutos por sessão", "30", 3);
        for (EditText e : new EditText[]{weeks, per, minutes}) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        Spinner level = new Spinner(a); String[] levels = new String[SeekyAiDeep.LEVELS.length]; for (int n = 0; n < levels.length; n++) levels[n] = AppLanguage.text(a, SeekyAiDeep.LEVELS[n]);
        level.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, levels));
        Spinner course = new Spinner(a); String[] names = new String[courses.size() + 1]; names[0] = AppLanguage.text(a, "Sem curso"); int chosen = 0;
        for (int n = 0; n < courses.size(); n++) { names[n + 1] = courses.get(n).s("title"); if (courses.get(n).id() == presetCourse) chosen = n + 1; }
        course.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, names)); course.setSelection(chosen);
        Spinner kind = new Spinner(a); kind.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, new String[]{AppLanguage.text(a, "Disciplina"), AppLanguage.text(a, "Módulo")}));
        form.addView(theme); form.addView(PlusUi.text(a, "Nível", 13)); form.addView(level); form.addView(weeks); form.addView(per); form.addView(minutes);
        form.addView(PlusUi.text(a, "Curso", 13)); form.addView(course); form.addView(PlusUi.text(a, "Criar como", 13)); form.addView(kind);
        form.addView(PlusUi.text(a, "A Seeky IA prepara as sessões, do essencial à revisão. Começa hoje. Revê o plano antes de o guardar.", 13));
        new UiDialog.Builder(a).setTitle("Plano de estudo com IA").setView(form).setPositiveButton("Criar plano", (d, w) -> {
            try {
                int wk = Integer.parseInt(weeks.getText().toString().trim()), pw = Integer.parseInt(per.getText().toString().trim()), min = Math.max(10, Math.min(240, Integer.parseInt(minutes.getText().toString().trim())));
                String q = SeekyAiDeep.planQuestion(theme.getText().toString(), wk, pw, min, level.getSelectedItemPosition(), AppLanguage.english(a));
                long cid = course.getSelectedItemPosition() == 0 ? 0 : courses.get(course.getSelectedItemPosition() - 1).id();
                create(a, theme.getText().toString(), q, SeekyAiDeep.sessions(wk, pw), pw, min, cid, kind.getSelectedItemPosition() == 1 ? "module" : "subject", saved);
            } catch (NumberFormatException e) { PlusUi.toast(a, "Escreva números nas semanas, sessões e minutos."); }
            catch (Exception e) { PlusUi.error(a, e); }
        }).setNegativeButton("Cancelar", null).show();
    }

    static void create(Activity a, String theme, String question, int max, int perWeek, int minutes, long courseId, String kind, Saved saved) {
        PlusUi.toast(a, "A Seeky IA está a preparar o plano…");
        PlusUi.run(a, () -> {
            String key = new SeekyAiVault(a).get(SeekyAiCore.GEMINI); if (key.isEmpty()) throw new java.io.IOException("Configure a chave da IA em Estudar com Seeky.");
            String answer = new SeekyAiClient().ask(SeekyAiCore.GEMINI, key, "", "Plano de estudo", Collections.emptyList(), question, AppLanguage.english(a), new SeekyAiClient.Call()).text;
            SeekyAiDeep.Plan plan = SeekyAiDeep.parse(answer, max);
            if (plan.sessions.isEmpty()) throw new java.io.IOException("A IA não devolveu um plano. Tente outra vez com um tema mais concreto.");
            if (plan.title.isEmpty()) plan.title = SeekyAiDeep.cut(theme, 80);
            long start = System.currentTimeMillis();
            PlusUi.post(a, () -> {
                String switched = SeekyAiModels.takeNotice(); if (!switched.isEmpty()) PlusUi.toast(a, switched);
                TextView body = PlusUi.text(a, plan.note(start, perWeek), 14); body.setTextIsSelectable(true);
                ScrollView scroll = new ScrollView(a); scroll.addView(body);
                new UiDialog.Builder(a).setTitle(plan.title + " · " + plan.sessions.size() + " " + AppLanguage.text(a, "sessões") + " · " + plan.cards.size() + " " + AppLanguage.text(a, "cartões")).setView(scroll)
                    .setPositiveButton("Guardar nos Estudos", (d, w) -> PlusUi.run(a, () -> { long sid; try (StudyStore db = new StudyStore(a)) { sid = save(db, plan, start, perWeek, minutes, courseId, kind); } StudyEvents.changed(); PlusUi.post(a, () -> { PlusUi.toast(a, "Plano guardado nos Estudos."); saved.open(sid); }); }))
                    .setNegativeButton("Cancelar", null).show();
            });
        });
    }

    /** A new subject with the sessions as dated tasks, the cards and the plan as a note. Returns the subject id. */
    static long save(StudyStore db, SeekyAiDeep.Plan plan, long start, int perWeek, int minutes, long courseId, String kind) {
        long now = System.currentTimeMillis();
        String title = plan.title; Set<String> taken = new HashSet<>();
        for (StudyStore.Row r : db.rows("subjects", null, null, null, 2000)) taken.add(r.s("title").toLowerCase(Locale.ROOT));
        for (int n = 2; taken.contains(title.toLowerCase(Locale.ROOT)); n++) title = plan.title + " (" + n + ")";
        plan.title = title;
        if (courseId > 0 && db.get("courses", courseId) == null) courseId = 0;
        long sid = db.save("subjects", 0, StudyStore.values("title", title, "color", 0xff2f5d7a, "created", now, "course", courseId, "kind", SyncState.kind(kind)));
        for (SeekyAiDeep.Session s : plan.sessions)
            db.save("tasks", 0, StudyStore.values("subject", sid, "title", SeekyAiDeep.cut("Sessão " + s.day + " · " + s.title + (s.detail.isEmpty() ? "" : " — " + s.detail), 200), "due", SeekyAiDeep.due(start, s.day, perWeek), "estimate", minutes, "priority", 1, "updated", now));
        for (StudyCards.Card c : plan.cards) db.save("cards", 0, StudyStore.values("subject", sid, "front", c.front, "back", c.back, "tags", "Plano da Seeky IA", "due", now, "updated", now));
        db.save("notes", 0, StudyStore.values("subject", sid, "title", "Plano de estudo · " + title, "body", plan.note(start, perWeek), "tags", "Seeky IA", "created", now, "updated", now));
        return sid;
    }
}
