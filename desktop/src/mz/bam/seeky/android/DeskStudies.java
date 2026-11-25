package mz.bam.seeky.android;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.sql.SQLException;
import java.text.DateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.json.JSONArray;

/**
 * Estudos no computador, como no telemóvel: disciplinas com cor, materiais (livros e ficheiros),
 * cartões com a mesma revisão espaçada, perguntas para treinar, tarefas com prazo, notas da disciplina,
 * Seeky IA que cria cartões e perguntas a partir de um material ou de um texto, Hoje e Progresso.
 */
final class DeskStudies {
    final DeskMain d;
    String tab = "Hoje"; long subject; String subjectTab = "Cartões";
    static final String[] TABS = {"Hoje", "Disciplinas", "Cartões", "Perguntas", "Agenda", "Progresso"};
    static final int[] COLORS = {0x3f6b4f, 0x2f5d7a, 0x7a2e43, 0xb07a1f, 0x5b3a8b, 0x2c6975, 0x8a4b2a, 0x4a5a6b};
    static final long DAY = 86400000L;
    DeskStudies(DeskMain d) { this.d = d; }

    static void schema(DeskStore s) {
        try (java.sql.Statement st = s.db.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS st_subjects(id INTEGER PRIMARY KEY,title TEXT NOT NULL,color INTEGER NOT NULL DEFAULT 0,course TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL,updated INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS st_materials(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL,path TEXT NOT NULL,title TEXT NOT NULL,added INTEGER NOT NULL,UNIQUE(subject,path))");
            st.execute("CREATE TABLE IF NOT EXISTS st_cards(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL DEFAULT 0,front TEXT NOT NULL,back TEXT NOT NULL,refs TEXT NOT NULL DEFAULT '',due INTEGER NOT NULL,interval INTEGER NOT NULL DEFAULT 0,reps INTEGER NOT NULL DEFAULT 0,lapses INTEGER NOT NULL DEFAULT 0,updated INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS st_questions(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL DEFAULT 0,prompt TEXT NOT NULL,answer TEXT NOT NULL,choices TEXT NOT NULL DEFAULT '[]',correct INTEGER NOT NULL DEFAULT -1,explanation TEXT NOT NULL DEFAULT '',updated INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS st_tasks(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL DEFAULT 0,title TEXT NOT NULL,due INTEGER NOT NULL DEFAULT 0,estimate INTEGER NOT NULL DEFAULT 25,priority INTEGER NOT NULL DEFAULT 1,done INTEGER NOT NULL DEFAULT 0,updated INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS st_quiz(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL DEFAULT 0,total INTEGER NOT NULL,correct INTEGER NOT NULL,created INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS sync_gone(key TEXT PRIMARY KEY,at INTEGER NOT NULL)");
            try { st.execute("ALTER TABLE notes ADD COLUMN subject INTEGER NOT NULL DEFAULT 0"); } catch (SQLException exists) {}
        } catch (SQLException ignored) {}
    }
    /** Something deleted here is deleted on the phone too at the next sync. */
    static void gone(DeskStore s, String key) { try { s.update("INSERT OR REPLACE INTO sync_gone(key,at) VALUES(?,?)", key, System.currentTimeMillis()); } catch (SQLException ignored) {} }
    static String subjectTitle(DeskStore s, long id) { try { List<Object[]> r = s.rows("SELECT title FROM st_subjects WHERE id=?", id); return r.isEmpty() ? "" : r.get(0)[0] + ""; } catch (SQLException e) { return ""; } }
    void deleteRow(String table, long id) {
        try {
            String key = null;
            if (table.equals("st_cards")) { Object[] r = d.store.rows("SELECT subject,front FROM st_cards WHERE id=?", id).get(0); key = "card:" + SyncState.studyKey(subjectTitle(d.store, ((Number) r[0]).longValue()), r[1] + ""); }
            else if (table.equals("st_questions")) { Object[] r = d.store.rows("SELECT subject,prompt FROM st_questions WHERE id=?", id).get(0); key = "question:" + SyncState.studyKey(subjectTitle(d.store, ((Number) r[0]).longValue()), r[1] + ""); }
            else if (table.equals("st_tasks")) { Object[] r = d.store.rows("SELECT subject,title,due FROM st_tasks WHERE id=?", id).get(0); key = "task:" + SyncState.taskKey(subjectTitle(d.store, ((Number) r[0]).longValue()), r[1] + "", ((Number) r[2]).longValue()); }
            d.store.update("DELETE FROM " + table + " WHERE id=?", id);
            if (key != null) gone(d.store, key);
        } catch (Exception ignored) {}
    }
    void deleteSubject(long id) {
        String title = subjectTitle(d.store, id);
        try {
            for (String t : new String[]{"st_cards", "st_questions", "st_tasks", "st_quiz"}) d.store.update("UPDATE " + t + " SET subject=0 WHERE subject=?", id);
            d.store.update("UPDATE notes SET subject=0 WHERE subject=?", id); d.store.update("DELETE FROM st_materials WHERE subject=?", id); d.store.update("DELETE FROM st_subjects WHERE id=?", id);
            gone(d.store, "subject:" + SyncState.norm(title));
        } catch (SQLException ignored) {}
    }

    // ================================================================ ecrã
    Node view() {
        schema(d.store);
        VBox page = DeskUi.page();
        HBox tabs = new HBox(6);
        for (String t : TABS) { ToggleButton b = new ToggleButton(t); b.getStyleClass().add("chip"); b.setSelected(t.equals(tab) && subject == 0); b.setOnAction(e -> { tab = t; subject = 0; d.open("studies"); }); tabs.getChildren().add(b); }
        page.getChildren().addAll(DeskUi.row(DeskUi.title("Estudos"), DeskUi.grow(), DeskUi.button("Nova disciplina", false, this::newSubject), DeskUi.button("Plano com IA", false, () -> { d.ai.tab = "Plano de estudo"; d.open("ai"); }), DeskUi.button("Seeky IA", true, () -> generate(subject))), tabs);
        if (subject > 0) page.getChildren().add(subjectPage(subject));
        else switch (tab) {
            case "Disciplinas": page.getChildren().add(subjects()); break;
            case "Cartões": page.getChildren().add(cardsList(0)); break;
            case "Perguntas": page.getChildren().add(questionsList(0)); break;
            case "Agenda": page.getChildren().add(agenda(0)); break;
            case "Progresso": page.getChildren().add(progress()); break;
            default: page.getChildren().add(today());
        }
        return DeskUi.scroll(page);
    }
    long count(String sql, Object... a) { try { return d.store.count(sql, a); } catch (SQLException e) { return 0; } }
    List<Object[]> rows(String sql, Object... a) { try { return d.store.rows(sql, a); } catch (SQLException e) { return new ArrayList<>(); } }
    static long startOfDay(long t) { return LocalDate.ofInstant(java.time.Instant.ofEpochMilli(t), ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
    static String date(long t) { return t <= 0 ? "Sem prazo" : DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(t)); }

    /** Hoje: cards due, tasks for today and late, study time. */
    Node today() {
        long now = System.currentTimeMillis(), end = startOfDay(now) + DAY;
        long due = count("SELECT COUNT(*) FROM st_cards WHERE due<=?", now), cards = count("SELECT COUNT(*) FROM st_cards");
        VBox box = new VBox(14);
        box.getChildren().add(DeskUi.card(DeskUi.heading("Cartões para rever"), DeskUi.text(cards == 0 ? "Ainda não há cartões. Crie-os numa disciplina ou peça à Seeky IA." : due + (due == 1 ? " cartão para hoje" : " cartões para hoje") + " · " + cards + " no total"),
            DeskUi.row(DeskUi.button(due > 0 ? "Rever agora" : "Treinar mesmo assim", true, () -> review(0)), DeskUi.button("Perguntas", false, () -> quiz(0)))));
        VBox tasks = new VBox(6);
        for (Object[] t : rows("SELECT id,title,due,subject,priority FROM st_tasks WHERE done=0 AND due>0 AND due<? ORDER BY due,priority DESC LIMIT 20", end)) tasks.getChildren().add(taskRow(t, true));
        if (tasks.getChildren().isEmpty()) tasks.getChildren().add(DeskUi.muted("Nada para hoje. Acrescente tarefas na Agenda ou numa disciplina."));
        box.getChildren().add(DeskUi.card(DeskUi.heading("Tarefas de hoje e atrasadas"), tasks, DeskUi.button("Nova tarefa", false, () -> newTask(0))));
        long today = count("SELECT SUM(minutes) FROM focus WHERE day=?", BibleStudyCore.day(now)), week = count("SELECT SUM(minutes) FROM focus WHERE day>=?", BibleStudyCore.week(now).get(0));
        box.getChildren().add(DeskUi.card(DeskUi.heading("Tempo de estudo"), DeskUi.text("Hoje " + DeskFocus.hours((int) today) + " · esta semana " + DeskFocus.hours((int) week)), DeskUi.button("Foco", false, () -> d.open("focus"))));
        List<Object[]> soon = rows("SELECT id,title,due,subject,priority FROM st_tasks WHERE done=0 AND due>=? ORDER BY due LIMIT 5", end);
        if (!soon.isEmpty()) { VBox next = new VBox(6); for (Object[] t : soon) next.getChildren().add(taskRow(t, true)); box.getChildren().add(DeskUi.card(DeskUi.heading("Próximos prazos"), next)); }
        return box;
    }
    Node subjects() {
        List<Object[]> all = rows("SELECT id,title,color,course FROM st_subjects ORDER BY course COLLATE NOCASE,title COLLATE NOCASE");
        if (all.isEmpty()) return DeskUi.card(DeskUi.heading("Ainda não há disciplinas"), DeskUi.text("Crie uma disciplina para cada matéria (por exemplo Teologia, Matemática, Inglês) e junte-lhe materiais, cartões, perguntas e tarefas."), DeskUi.button("Nova disciplina", true, this::newSubject));
        FlowPane grid = new FlowPane(14, 14); long now = System.currentTimeMillis();
        for (Object[] s : all) {
            long id = ((Number) s[0]).longValue(); int color = ((Number) s[2]).intValue();
            Region stripe = new Region(); stripe.setMinHeight(6); stripe.setStyle("-fx-background-color: " + DeskThemes.hex(color) + "; -fx-background-radius: 6;");
            long due = count("SELECT COUNT(*) FROM st_cards WHERE subject=? AND due<=?", id, now), cards = count("SELECT COUNT(*) FROM st_cards WHERE subject=?", id), tasks = count("SELECT COUNT(*) FROM st_tasks WHERE subject=? AND done=0", id), mats = count("SELECT COUNT(*) FROM st_materials WHERE subject=?", id);
            VBox card = DeskUi.card(stripe, DeskUi.heading(s[1] + ""), DeskUi.muted((s[3] + "").isEmpty() ? "Disciplina" : s[3] + ""), DeskUi.text(mats + " materiais · " + cards + " cartões" + (due > 0 ? " (" + due + " para rever)" : "") + " · " + tasks + " tarefas"));
            card.setPrefWidth(300); card.getStyleClass().add("tile"); card.setOnMouseClicked(e -> { subject = id; subjectTab = "Cartões"; d.open("studies"); }); DeskUi.lift(card);
            grid.getChildren().add(card);
        }
        return grid;
    }
    Node subjectPage(long id) {
        List<Object[]> r = rows("SELECT title,color,course FROM st_subjects WHERE id=?", id); if (r.isEmpty()) { subject = 0; return subjects(); }
        String title = r.get(0)[0] + "";
        VBox box = new VBox(12);
        HBox tabs = new HBox(6);
        for (String t : new String[]{"Cartões", "Perguntas", "Materiais", "Tarefas", "Notas"}) { ToggleButton b = new ToggleButton(t); b.getStyleClass().add("chip"); b.setSelected(t.equals(subjectTab)); b.setOnAction(e -> { subjectTab = t; d.open("studies"); }); tabs.getChildren().add(b); }
        MenuButton more = new MenuButton("Mais");
        MenuItem rename = new MenuItem("Mudar o nome…"); rename.setOnAction(e -> { String n = DeskUi.ask("Nome da disciplina", "Nome", title); if (n != null) { try { d.store.update("UPDATE st_subjects SET title=?,updated=? WHERE id=?", n, System.currentTimeMillis(), id); } catch (SQLException ignored) {} d.open("studies"); } });
        MenuItem focus = new MenuItem("Estudar com o Foco"); focus.setOnAction(e -> { d.focus.subject = title; d.open("focus"); });
        MenuItem del = new MenuItem("Eliminar a disciplina"); del.setOnAction(e -> { if (DeskUi.confirm("Eliminar " + title + "?", "Os cartões, perguntas, tarefas e notas ficam, sem disciplina.")) { deleteSubject(id); subject = 0; d.open("studies"); } });
        more.getItems().addAll(rename, focus, new SeparatorMenuItem(), del);
        Region dot = new Region(); dot.setMinSize(14, 14); dot.setMaxSize(14, 14); dot.setStyle("-fx-background-color: " + DeskThemes.hex(((Number) r.get(0)[1]).intValue()) + "; -fx-background-radius: 7;");
        box.getChildren().addAll(DeskUi.row(DeskUi.iconButton("back", "Disciplinas", () -> { subject = 0; tab = "Disciplinas"; d.open("studies"); }), dot, DeskUi.heading(title), DeskUi.grow(), more), tabs);
        switch (subjectTab) {
            case "Perguntas": box.getChildren().add(questionsList(id)); break;
            case "Materiais": box.getChildren().add(materials(id)); break;
            case "Tarefas": box.getChildren().add(agenda(id)); break;
            case "Notas": box.getChildren().add(notes(id, title)); break;
            default: box.getChildren().add(cardsList(id));
        }
        return box;
    }

    // ---- disciplinas ----
    void newSubject() {
        String t = DeskUi.ask("Nova disciplina", "Nome (por exemplo Teologia Sistemática)", ""); if (t == null) return;
        try { long n = count("SELECT COUNT(*) FROM st_subjects"); long now = System.currentTimeMillis(); subject = d.store.update("INSERT INTO st_subjects(title,color,created,updated) VALUES(?,?,?,?)", t, COLORS[(int) (n % COLORS.length)], now, now); subjectTab = "Materiais"; } catch (SQLException ignored) {}
        d.open("studies");
    }
    long chooseSubject(String why) {
        List<Object[]> all = rows("SELECT id,title FROM st_subjects ORDER BY title COLLATE NOCASE"); if (all.isEmpty()) return 0;
        ChoiceDialog<String> c = new ChoiceDialog<>(); c.getItems().add("Sem disciplina"); for (Object[] s : all) c.getItems().add(s[1] + ""); c.setSelectedItem(c.getItems().get(0)); c.setHeaderText(why); c.setTitle("Seeky"); DeskUi.style(c.getDialogPane());
        Optional<String> pick = c.showAndWait(); if (!pick.isPresent()) return -1;
        for (Object[] s : all) if ((s[1] + "").equals(pick.get())) return ((Number) s[0]).longValue();
        return 0;
    }

    // ---- materiais ----
    Node materials(long id) {
        VBox list = new VBox(8);
        for (Object[] m : rows("SELECT id,path,title FROM st_materials WHERE subject=? ORDER BY added DESC", id)) {
            File f = new File(m[1] + ""); long mid = ((Number) m[0]).longValue();
            HBox row = DeskUi.row(DeskCovers.view(f, m[2] + "", 40), new VBox(2, DeskUi.heading(m[2] + ""), DeskUi.muted(f.exists() ? f.getParent() : "Ficheiro em falta")), DeskUi.grow(),
                DeskUi.button("Abrir", true, () -> openMaterial(f)), DeskUi.button("Criar cartões", false, () -> generateFrom(id, f)), DeskUi.iconButton("trash", "Tirar da disciplina", () -> { try { d.store.update("DELETE FROM st_materials WHERE id=?", mid); } catch (SQLException ignored) {} d.open("studies"); }));
            row.getStyleClass().add("card"); row.setPadding(new Insets(10)); list.getChildren().add(row);
        }
        if (list.getChildren().isEmpty()) list.getChildren().add(DeskUi.muted("Junte livros, apontamentos em PDF ou Word e outros ficheiros desta disciplina."));
        return new VBox(10, DeskUi.row(DeskUi.button("Juntar ficheiros…", true, () -> addMaterials(id)), DeskUi.button("Da estante…", false, () -> fromShelf(id))), list);
    }
    void addMaterials(long id) {
        FileChooser fc = new FileChooser(); fc.setTitle("Materiais da disciplina"); List<File> files = fc.showOpenMultipleDialog(d.stage); if (files == null) return;
        try { DeskReader.importBooks(d.store, files); for (File f : files) d.store.update("INSERT OR IGNORE INTO st_materials(subject,path,title,added) VALUES(?,?,?,?)", id, f.getPath(), f.getName().replaceFirst("\\.[^.]+$", ""), System.currentTimeMillis()); } catch (SQLException ignored) {}
        d.open("studies");
    }
    void fromShelf(long id) {
        List<Object[]> books = rows("SELECT path,title FROM books ORDER BY opened DESC, title COLLATE NOCASE LIMIT 300"); if (books.isEmpty()) { DeskUi.info("A estante está vazia", "Importe livros em Livros primeiro."); return; }
        ChoiceDialog<String> c = new ChoiceDialog<>(); for (Object[] b : books) c.getItems().add(b[1] + ""); c.setSelectedItem(c.getItems().get(0)); c.setHeaderText("Livro da estante"); c.setTitle("Seeky"); DeskUi.style(c.getDialogPane());
        c.showAndWait().ifPresent(t -> { for (Object[] b : books) if ((b[1] + "").equals(t)) try { d.store.update("INSERT OR IGNORE INTO st_materials(subject,path,title,added) VALUES(?,?,?,?)", id, b[0], b[1], System.currentTimeMillis()); } catch (SQLException ignored) {} d.open("studies"); });
    }
    void openMaterial(File f) { if (DeskReader.accepts(f.getName())) { d.open("reader"); d.reader.open(f); } else DeskFiles.system(f); }

    // ---- cartões ----
    Node cardsList(long id) {
        long now = System.currentTimeMillis();
        String where = id > 0 ? " WHERE subject=" + id : "";
        long due = count("SELECT COUNT(*) FROM st_cards" + (id > 0 ? " WHERE subject=? AND due<=?" : " WHERE due<=?"), id > 0 ? new Object[]{id, now} : new Object[]{now});
        VBox list = new VBox(8);
        for (Object[] c : rows("SELECT id,front,back,due,interval,subject FROM st_cards" + where + " ORDER BY due LIMIT 300")) {
            long cid = ((Number) c[0]).longValue(); long when = ((Number) c[3]).longValue();
            VBox words = new VBox(2, DeskUi.heading(c[1] + ""), DeskUi.muted(c[2] + ""), DeskUi.muted((when <= now ? "Para rever hoje" : "Próxima revisão " + date(when)) + (id == 0 ? subjectLabel(((Number) c[5]).longValue()) : ""))); HBox.setHgrow(words, Priority.ALWAYS);
            HBox row = new HBox(10, words, DeskUi.iconButton("note", "Editar", () -> editCard(cid, id)), DeskUi.iconButton("trash", "Eliminar", () -> { if (DeskUi.confirm("Eliminar este cartão?", c[1] + "")) { deleteRow("st_cards", cid); d.open("studies"); } }));
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("card"); row.setPadding(new Insets(12)); list.getChildren().add(row);
        }
        if (list.getChildren().isEmpty()) list.getChildren().add(DeskUi.muted("Sem cartões. Escreva-os ou peça à Seeky IA que os crie a partir de um material."));
        return new VBox(10, DeskUi.row(DeskUi.button(due > 0 ? "Rever " + due : "Treinar", true, () -> review(id)), DeskUi.button("Novo cartão", false, () -> editCard(0, id)), DeskUi.button("Criar com a IA", false, () -> generate(id))), list);
    }
    String subjectLabel(long sid) { String t = sid > 0 ? subjectTitle(d.store, sid) : ""; return t.isEmpty() ? "" : " · " + t; }
    void editCard(long cid, long sid) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText(cid == 0 ? "Novo cartão" : "Editar cartão"); DeskUi.style(dlg.getDialogPane());
        TextArea front = new TextArea(), back = new TextArea(); front.setPromptText("Pergunta (frente)"); back.setPromptText("Resposta (verso)"); front.setPrefRowCount(3); back.setPrefRowCount(4); front.setWrapText(true); back.setWrapText(true);
        if (cid > 0) { List<Object[]> r = rows("SELECT front,back FROM st_cards WHERE id=?", cid); if (!r.isEmpty()) { front.setText(r.get(0)[0] + ""); back.setText(r.get(0)[1] + ""); } }
        VBox box = new VBox(8, front, back); box.setPrefWidth(520); dlg.getDialogPane().setContent(box); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            String f = front.getText().trim(), k = back.getText().trim(); if (f.isEmpty() || k.isEmpty()) return; long now = System.currentTimeMillis();
            try { if (cid == 0) d.store.update("INSERT INTO st_cards(subject,front,back,due,updated) VALUES(?,?,?,?,?)", sid, f, k, now, now); else d.store.update("UPDATE st_cards SET front=?,back=?,updated=? WHERE id=?", f, k, now, cid); } catch (SQLException ignored) {}
            d.open("studies");
        });
    }
    /** Revisão: front, then the answer, then Outra vez / Difícil / Recordei / Fácil (StudySchedule, as on the phone). */
    void review(long sid) {
        long now = System.currentTimeMillis();
        List<Object[]> queue = rows("SELECT id,front,back,interval,reps,lapses FROM st_cards WHERE " + (sid > 0 ? "subject=" + sid + " AND " : "") + "due<=? ORDER BY due LIMIT 50", now);
        if (queue.isEmpty()) queue = rows("SELECT id,front,back,interval,reps,lapses FROM st_cards" + (sid > 0 ? " WHERE subject=" + sid : "") + " ORDER BY due LIMIT 20");
        if (queue.isEmpty()) { DeskUi.info("Sem cartões", "Crie cartões numa disciplina primeiro."); return; }
        showCard(queue, 0, new int[4]);
    }
    void showCard(List<Object[]> q, int at, int[] grades) {
        if (at >= q.size()) { d.show(DeskUi.scroll(DeskUi.page(DeskUi.title("Revisão concluída"), DeskUi.card(DeskUi.text("Recordei: " + grades[2] + " · Fácil: " + grades[3] + " · Difícil: " + grades[1] + " · Outra vez: " + grades[0])), DeskUi.button("Voltar aos Estudos", true, () -> d.open("studies"))))); return; }
        Object[] c = q.get(at); long id = ((Number) c[0]).longValue();
        Label answer = DeskUi.text(c[2] + ""); answer.setVisible(false); answer.getStyleClass().add("study-answer");
        HBox grade = new HBox(8); grade.setVisible(false);
        String[] names = {"Outra vez", "Difícil", "Recordei", "Fácil"};
        for (int g = 0; g < 4; g++) {
            final int gg = g; StudySchedule.Next n = StudySchedule.next(System.currentTimeMillis(), ((Number) c[3]).intValue(), ((Number) c[4]).intValue(), ((Number) c[5]).intValue(), g);
            Button b = DeskUi.button(names[g] + "\n" + (g == 0 ? "1 min" : n.interval + (n.interval == 1 ? " dia" : " dias")), g == 2, () -> {
                StudySchedule.Next next = StudySchedule.next(System.currentTimeMillis(), ((Number) c[3]).intValue(), ((Number) c[4]).intValue(), ((Number) c[5]).intValue(), gg);
                try { d.store.update("UPDATE st_cards SET due=?,interval=?,reps=?,lapses=?,updated=? WHERE id=?", next.due, next.interval, next.reps, next.lapses, System.currentTimeMillis(), id); } catch (SQLException ignored) {}
                grades[gg]++; showCard(q, at + 1, grades);
            });
            b.setTextAlignment(javafx.scene.text.TextAlignment.CENTER); b.setMinWidth(110); grade.getChildren().add(b);
        }
        Button show = DeskUi.button("Mostrar resposta", true, () -> {}); show.setOnAction(e -> { answer.setVisible(true); grade.setVisible(true); show.setVisible(false); });
        Label front = DeskUi.title(c[1] + ""); front.setStyle("-fx-font-size: 24px;");
        d.show(DeskUi.scroll(DeskUi.page(DeskUi.row(DeskUi.iconButton("back", "Sair", () -> d.open("studies")), DeskUi.heading("Revisão · " + (at + 1) + "/" + q.size())), DeskUi.card(front, new Separator(), answer), show, grade)));
    }

    // ---- perguntas ----
    Node questionsList(long id) {
        VBox list = new VBox(8);
        for (Object[] qn : rows("SELECT id,prompt,answer,subject FROM st_questions" + (id > 0 ? " WHERE subject=" + id : "") + " ORDER BY updated DESC LIMIT 300")) {
            long qid = ((Number) qn[0]).longValue();
            VBox words = new VBox(2, DeskUi.heading(qn[1] + ""), DeskUi.muted(qn[2] + "" + (id == 0 ? subjectLabel(((Number) qn[3]).longValue()) : ""))); HBox.setHgrow(words, Priority.ALWAYS);
            HBox row = new HBox(10, words, DeskUi.iconButton("trash", "Eliminar", () -> { deleteRow("st_questions", qid); d.open("studies"); })); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("card"); row.setPadding(new Insets(12)); list.getChildren().add(row);
        }
        if (list.getChildren().isEmpty()) list.getChildren().add(DeskUi.muted("Sem perguntas. Escreva-as ou peça à Seeky IA."));
        List<Object[]> runs = rows("SELECT total,correct,created FROM st_quiz" + (id > 0 ? " WHERE subject=" + id : "") + " ORDER BY created DESC LIMIT 3");
        VBox recent = new VBox(2); for (Object[] r : runs) recent.getChildren().add(DeskUi.muted(date(((Number) r[2]).longValue()) + ": " + r[1] + " de " + r[0] + " certas"));
        return new VBox(10, DeskUi.row(DeskUi.button("Treinar", true, () -> quiz(id)), DeskUi.button("Nova pergunta", false, () -> newQuestion(id)), DeskUi.button("Criar com a IA", false, () -> generate(id))), recent, list);
    }
    void newQuestion(long sid) {
        String p = DeskUi.ask("Nova pergunta", "Pergunta", ""); if (p == null) return; String a = DeskUi.ask("Resposta", p, ""); if (a == null) return;
        try { d.store.update("INSERT INTO st_questions(subject,prompt,answer,updated) VALUES(?,?,?,?)", sid, p, a, System.currentTimeMillis()); } catch (SQLException ignored) {}
        d.open("studies");
    }
    /** Treinar: up to 10 questions; choose an option or show the answer and say if it was right. */
    void quiz(long sid) {
        List<Object[]> q = rows("SELECT id,prompt,answer,choices,correct,explanation FROM st_questions" + (sid > 0 ? " WHERE subject=" + sid : "") + " ORDER BY RANDOM() LIMIT 10");
        if (q.isEmpty()) { DeskUi.info("Sem perguntas", "Crie perguntas numa disciplina primeiro."); return; }
        askQuestion(sid, q, 0, new int[1]);
    }
    void askQuestion(long sid, List<Object[]> q, int at, int[] right) {
        if (at >= q.size()) {
            try { d.store.update("INSERT INTO st_quiz(subject,total,correct,created) VALUES(?,?,?,?)", sid, q.size(), right[0], System.currentTimeMillis()); } catch (SQLException ignored) {}
            d.show(DeskUi.scroll(DeskUi.page(DeskUi.title("Resultado: " + right[0] + " de " + q.size()), DeskUi.button("Voltar aos Estudos", true, () -> d.open("studies"))))); return;
        }
        Object[] x = q.get(at); VBox after = new VBox(8), options = new VBox(8);
        JSONArray choices; try { choices = new JSONArray(x[3] + ""); } catch (Exception e) { choices = new JSONArray(); }
        int correct = ((Number) x[4]).intValue();
        Runnable next = () -> askQuestion(sid, q, at + 1, right);
        if (choices.length() >= 2 && correct >= 0 && correct < choices.length()) {
            for (int i = 0; i < choices.length(); i++) { final int ii = i; Button b = DeskUi.button(choices.optString(i), false, () -> {}); b.setMaxWidth(Double.MAX_VALUE);
                b.setOnAction(e -> { options.setDisable(true); boolean ok = ii == correct; if (ok) right[0]++; after.getChildren().setAll(DeskUi.heading(ok ? "Certo!" : "Não é"), DeskUi.text("Resposta: " + x[2]), (x[5] + "").isEmpty() ? new Region() : DeskUi.muted(x[5] + ""), DeskUi.button("Seguinte", true, next)); });
                options.getChildren().add(b); }
        } else {
            Button show = DeskUi.button("Mostrar resposta", true, () -> {});
            show.setOnAction(e -> { show.setDisable(true); after.getChildren().setAll(DeskUi.text(x[2] + ""), DeskUi.row(DeskUi.button("Acertei", true, () -> { right[0]++; next.run(); }), DeskUi.button("Errei", false, next))); });
            options.getChildren().add(show);
        }
        d.show(DeskUi.scroll(DeskUi.page(DeskUi.row(DeskUi.iconButton("back", "Sair", () -> d.open("studies")), DeskUi.heading("Perguntas · " + (at + 1) + "/" + q.size())), DeskUi.card(DeskUi.title(x[1] + "")), options, after)));
    }

    // ---- tarefas (agenda) ----
    Node taskRow(Object[] t, boolean showSubject) {
        long id = ((Number) t[0]).longValue(), due = ((Number) t[2]).longValue(); boolean late = due > 0 && due < startOfDay(System.currentTimeMillis());
        CheckBox done = new CheckBox(); done.setOnAction(e -> { try { d.store.update("UPDATE st_tasks SET done=1,updated=? WHERE id=?", System.currentTimeMillis(), id); } catch (SQLException ignored) {} d.open("studies"); });
        Label when = DeskUi.muted((late ? "Atrasada · " : "") + date(due) + (showSubject ? subjectLabel(((Number) t[3]).longValue()) : "") + (((Number) t[4]).intValue() >= 2 ? " · prioridade alta" : ""));
        if (late) when.setStyle("-fx-text-fill: #d9534f;");
        VBox words = new VBox(2, DeskUi.text(t[1] + ""), when); HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = new HBox(10, done, words, DeskUi.iconButton("trash", "Eliminar", () -> { deleteRow("st_tasks", id); d.open("studies"); })); row.setAlignment(Pos.CENTER_LEFT); return row;
    }
    Node agenda(long sid) {
        VBox pending = new VBox(8), done = new VBox(6);
        for (Object[] t : rows("SELECT id,title,due,subject,priority FROM st_tasks WHERE done=0" + (sid > 0 ? " AND subject=" + sid : "") + " ORDER BY CASE WHEN due=0 THEN 1 ELSE 0 END,due,priority DESC")) pending.getChildren().add(taskRow(t, sid == 0));
        for (Object[] t : rows("SELECT id,title,updated FROM st_tasks WHERE done=1" + (sid > 0 ? " AND subject=" + sid : "") + " ORDER BY updated DESC LIMIT 15")) done.getChildren().add(DeskUi.muted("✓ " + t[1]));
        if (pending.getChildren().isEmpty()) pending.getChildren().add(DeskUi.muted("Sem tarefas por fazer."));
        return new VBox(12, DeskUi.button("Nova tarefa", true, () -> newTask(sid)), DeskUi.card(DeskUi.heading("Por fazer"), pending), done.getChildren().isEmpty() ? new Region() : DeskUi.card(DeskUi.heading("Feitas"), done));
    }
    void newTask(long sid) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("Nova tarefa"); DeskUi.style(dlg.getDialogPane());
        TextField title = new TextField(); title.setPromptText("Por exemplo: ler o capítulo 3, entregar trabalho");
        DatePicker due = new DatePicker(LocalDate.now()); CheckBox none = new CheckBox("Sem prazo");
        ComboBox<String> prio = new ComboBox<>(); prio.getItems().addAll("Prioridade normal", "Prioridade alta"); prio.getSelectionModel().select(0);
        long chosen = sid;
        ComboBox<String> subj = new ComboBox<>(); List<Object[]> all = rows("SELECT id,title FROM st_subjects ORDER BY title COLLATE NOCASE"); subj.getItems().add("Sem disciplina"); for (Object[] s : all) subj.getItems().add(s[1] + "");
        subj.getSelectionModel().select(0); for (int i = 0; i < all.size(); i++) if (((Number) all.get(i)[0]).longValue() == sid) subj.getSelectionModel().select(i + 1);
        VBox box = new VBox(8, title, DeskUi.row(due, none), prio, subj); box.setPrefWidth(420); dlg.getDialogPane().setContent(box); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            if (title.getText().trim().isEmpty()) return;
            long when = none.isSelected() || due.getValue() == null ? 0 : due.getValue().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            int pick = subj.getSelectionModel().getSelectedIndex(); long s = pick <= 0 ? 0 : ((Number) all.get(pick - 1)[0]).longValue();
            try { d.store.update("INSERT INTO st_tasks(subject,title,due,priority,updated) VALUES(?,?,?,?,?)", s, title.getText().trim(), when, prio.getSelectionModel().getSelectedIndex() == 1 ? 2 : 1, System.currentTimeMillis()); } catch (SQLException ignored) {}
            d.open("studies");
        });
    }

    // ---- notas da disciplina ----
    Node notes(long sid, String title) {
        VBox list = new VBox(8);
        for (Object[] n : rows("SELECT id,title,body FROM notes WHERE subject=? ORDER BY updated DESC", sid)) {
            long nid = ((Number) n[0]).longValue(); String t = NotepadText.title(n[1] + "", n[2] + "");
            VBox card = DeskUi.card(DeskUi.heading(t), DeskUi.muted(NotepadText.plain(n[2] + "").replaceAll("\\s+", " ").trim().replaceFirst("^(.{0,160}).*$", "$1"))); card.getStyleClass().add("tile");
            card.setOnMouseClicked(e -> { d.open("notes"); d.notes.edit(nid, n[1] + "", n[2] + ""); }); list.getChildren().add(card);
        }
        if (list.getChildren().isEmpty()) list.getChildren().add(DeskUi.muted("As notas criadas aqui ficam no Bloco de notas, na pasta " + title + "."));
        return new VBox(10, DeskUi.button("Nova nota", true, () -> {
            try { long now = System.currentTimeMillis(); long id = d.store.update("INSERT INTO notes(title,body,folder,subject,created,updated) VALUES(?,?,?,?,?,?)", "", "", title, sid, now, now); d.open("notes"); d.notes.edit(id, "", ""); } catch (SQLException ignored) {}
        }), list);
    }

    // ---- progresso ----
    Node progress() {
        VBox box = new VBox(12); long now = System.currentTimeMillis(), weekStart = startOfDay(now) - 6 * DAY;
        List<Object[]> all = rows("SELECT id,title,color FROM st_subjects ORDER BY title COLLATE NOCASE");
        if (all.isEmpty()) return DeskUi.muted("Crie disciplinas para ver o progresso.");
        for (Object[] s : all) {
            long id = ((Number) s[0]).longValue(); String title = s[1] + "";
            long cards = count("SELECT COUNT(*) FROM st_cards WHERE subject=?", id), known = count("SELECT COUNT(*) FROM st_cards WHERE subject=? AND interval>=21", id), due = count("SELECT COUNT(*) FROM st_cards WHERE subject=? AND due<=?", id, now);
            long tasksDone = count("SELECT COUNT(*) FROM st_tasks WHERE subject=? AND done=1", id), tasks = count("SELECT COUNT(*) FROM st_tasks WHERE subject=?", id);
            long minutes = count("SELECT SUM(minutes) FROM focus WHERE subject=? AND ended>=?", title, weekStart);
            List<Object[]> q = rows("SELECT SUM(correct),SUM(total) FROM st_quiz WHERE subject=?", id); long ok = q.isEmpty() || q.get(0)[0] == null ? 0 : ((Number) q.get(0)[0]).longValue(), tot = q.isEmpty() || q.get(0)[1] == null ? 0 : ((Number) q.get(0)[1]).longValue();
            ProgressBar bar = new ProgressBar(cards == 0 ? 0 : (double) known / cards); bar.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(DeskUi.card(DeskUi.heading(title), bar, DeskUi.text(known + " de " + cards + " cartões bem sabidos · " + due + " para rever"), DeskUi.muted("Tarefas " + tasksDone + "/" + tasks + " · perguntas " + (tot == 0 ? "ainda sem treino" : Math.round(100.0 * ok / tot) + "% certas") + " · foco esta semana " + DeskFocus.hours((int) minutes))));
        }
        return box;
    }

    // ---- Seeky IA ----
    void generate(long sid) {
        long s = sid > 0 ? sid : chooseSubject("Para que disciplina?"); if (s < 0) return;
        ChoiceDialog<String> c = new ChoiceDialog<>("Escrever ou colar um texto", "Escrever ou colar um texto", "A partir de um material da disciplina"); c.setHeaderText("Seeky IA: criar cartões e perguntas"); c.setTitle("Seeky"); DeskUi.style(c.getDialogPane());
        c.showAndWait().ifPresent(way -> {
            if (way.startsWith("A partir")) { List<Object[]> m = rows("SELECT path,title FROM st_materials WHERE subject=?", s); if (m.isEmpty()) { DeskUi.info("Sem materiais", "Junte primeiro um material à disciplina."); return; }
                ChoiceDialog<String> pick = new ChoiceDialog<>(); for (Object[] x : m) pick.getItems().add(x[1] + ""); pick.setSelectedItem(pick.getItems().get(0)); pick.setHeaderText("Material"); DeskUi.style(pick.getDialogPane());
                pick.showAndWait().ifPresent(t -> { for (Object[] x : m) if ((x[1] + "").equals(t)) generateFrom(s, new File(x[0] + "")); }); }
            else {
                Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("Texto para estudar"); DeskUi.style(dlg.getDialogPane());
                TextArea text = new TextArea(); text.setWrapText(true); text.setPrefRowCount(12); text.setPrefWidth(560); dlg.getDialogPane().setContent(text); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                dlg.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> ask(s, "texto colado", text.getText()));
            }
        });
    }
    void generateFrom(long sid, File f) { d.run("A ler " + f.getName() + "…", () -> text(f), t -> ask(sid, f.getName(), t)); }
    /** The words of a material (first part of a book or PDF), for the IA. */
    static String text(File f) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT); StringBuilder b = new StringBuilder();
        if (n.endsWith(".pdf")) { try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(f, org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) { org.apache.pdfbox.text.PDFTextStripper s = new org.apache.pdfbox.text.PDFTextStripper(); s.setStartPage(1); s.setEndPage(Math.min(30, doc.getNumberOfPages())); b.append(s.getText(doc)); } }
        else { DeskReader.Book book = DeskReader.chapters(f); for (String t : book.texts) { b.append(t).append("\n\n"); if (b.length() > 60000) break; } }
        String t = b.toString().trim(); if (t.isEmpty()) throw new Exception("Este material não tem texto que o Seeky consiga ler.");
        return NotepadAiText.cut(t);
    }
    void ask(long sid, String source, String text) {
        if (text == null || text.trim().length() < 40) { DeskUi.info("Texto curto", "Escreva ou cole um texto com pelo menos algumas frases."); return; }
        DeskAi.load(d.store);
        if (d.store.get("gemini_key", "").isEmpty()) { if (DeskUi.confirm("Ligar a Seeky IA", "Para criar cartões com a IA, guarde primeiro a sua chave do Google Gemini.")) DeskAi.settings(d); return; }
        String prompt = "Cria oito cartões de revisão e cinco perguntas de escolha múltipla sobre este texto, em português. Para cada cartão usa exactamente este formato:\nPergunta: ...\nResposta: ...\n"
            + "Depois escreve a linha PERGUNTAS e, para cada pergunta, este formato:\nQ: ...\nA) ...\nB) ...\nC) ...\nD) ...\nCerta: (letra)\nPorquê: (uma frase)\nNão escrevas mais nada.";
        String sent = NotepadAiText.cut(text.trim());
        d.run("A Seeky IA está a criar cartões e perguntas…", () -> new SeekyAiClient().ask(SeekyAiCore.GEMINI, DeskAi.key(d.store), sent, source, Collections.emptyList(), prompt, false, new SeekyAiClient.Call()).text, answer -> {
            int[] n = save(d.store, sid, answer);
            if (n[0] + n[1] == 0) DeskUi.info("A IA não devolveu cartões", "Tente outra vez com outro excerto.");
            else { DeskUi.info("Criados pela Seeky IA", n[0] + " cartões e " + n[1] + " perguntas em " + (sid > 0 ? subjectTitle(d.store, sid) : "Estudos") + "."); subject = sid; subjectTab = "Cartões"; d.open("studies"); }
        });
    }
    /** Cards ("Pergunta:/Resposta:", the phone's parser) and multiple-choice questions ("Q:/A)…/Certa:"). Returns {cards, questions}. */
    static int[] save(DeskStore s, long sid, String answer) {
        String[] parts = answer.split("(?im)^\\s*PERGUNTAS\\s*$", 2);
        List<StudyCards.Card> cards = StudyCards.parse(parts[0]); long now = System.currentTimeMillis(); int nc = 0, nq = 0;
        for (StudyCards.Card c : cards) try { s.update("INSERT INTO st_cards(subject,front,back,refs,due,updated) VALUES(?,?,?,?,?,?)", sid, c.front, c.back, c.refs, now, now); nc++; } catch (SQLException ignored) {}
        if (parts.length > 1) {
            String q = null, why = ""; List<String> opts = new ArrayList<>(); int right = -1;
            for (String raw : (parts[1] + "\nQ: ").split("\\r?\\n")) {
                String line = raw.trim().replace("**", "");
                if (line.matches("(?i)^Q\\s*:.*")) {
                    if (q != null && opts.size() >= 2 && right >= 0 && right < opts.size()) try { s.update("INSERT INTO st_questions(subject,prompt,answer,choices,correct,explanation,updated) VALUES(?,?,?,?,?,?,?)", sid, q, opts.get(right), new JSONArray(opts).toString(), right, why, now); nq++; } catch (SQLException ignored) {}
                    q = line.substring(line.indexOf(':') + 1).trim(); opts = new ArrayList<>(); right = -1; why = ""; if (q.isEmpty()) q = null;
                } else if (line.matches("(?i)^[A-F]\\s*[)\\.].*")) opts.add(line.substring(2).trim());
                else if (line.matches("(?i)^Certa\\s*:.*")) { String l = line.substring(line.indexOf(':') + 1).trim().toUpperCase(Locale.ROOT); if (!l.isEmpty()) right = l.charAt(0) - 'A'; }
                else if (line.matches("(?i)^Porqu[eê]\\s*:.*")) why = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return new int[]{nc, nq};
    }
}
