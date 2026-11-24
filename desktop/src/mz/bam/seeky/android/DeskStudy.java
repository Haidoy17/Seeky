package mz.bam.seeky.android;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.sql.SQLException;
import java.util.*;

/** Bible study (reading plan, streak, memory cards) and the Bible game, with Android's own rules. */
final class DeskStudy {
    final DeskMain d;
    DeskStudy(DeskMain d) { this.d = d; }

    String plan() { return d.store.get("plan", "year"); }
    Set<String> read() { Set<String> out = new HashSet<>(); try { for (Object[] r : d.store.rows("SELECT book,chapter FROM readings")) out.add(r[0] + " " + r[1]); } catch (SQLException ignored) {} return out; }
    Set<String> days() { Set<String> out = new HashSet<>(); try { for (Object[] r : d.store.rows("SELECT DISTINCT day FROM readings")) out.add(r[0] + ""); } catch (SQLException ignored) {} return out; }
    void markRead(String book, int chapter) { try { d.store.update("INSERT OR REPLACE INTO readings(day,book,chapter) VALUES(?,?,?)", BibleStudyCore.day(System.currentTimeMillis()), book, chapter); } catch (SQLException ignored) {} }

    /** "Leitura de hoje" on the home screen. */
    VBox todayCard() {
        List<String[]> next = BibleStudyCore.next(plan(), read(), Math.max(1, BibleStudyCore.daily(plan())));
        if (next.isEmpty()) return null;
        StringBuilder s = new StringBuilder(); for (String[] c : next) { if (s.length() > 0) s.append(" · "); s.append(c[0]).append(' ').append(c[1]); }
        int streak = BibleStudyCore.streak(days(), System.currentTimeMillis());
        return DeskUi.card(DeskUi.heading("Leitura de hoje"), DeskUi.text(s.toString()), DeskUi.muted(streak > 0 ? streak + (streak == 1 ? " dia seguido" : " dias seguidos") : "Comece hoje a sua sequência"),
            DeskUi.row(DeskUi.button("Ler agora", true, () -> { d.bible.book = next.get(0)[0]; d.bible.chapter = Integer.parseInt(next.get(0)[1]); d.store.put("bible_book", d.bible.book); d.store.put("bible_chapter", next.get(0)[1]); d.open("bible"); })));
    }

    Node view() {
        VBox page = DeskUi.page(DeskUi.title("Estudo bíblico"));
        String plan = plan(); Set<String> read = read();
        ComboBox<String> plans = new ComboBox<>(); for (String[] p : BibleStudyCore.PLANS) plans.getItems().add(p[1]);
        for (int i = 0; i < BibleStudyCore.PLANS.length; i++) if (BibleStudyCore.PLANS[i][0].equals(plan)) plans.getSelectionModel().select(i);
        plans.setOnAction(e -> { d.store.put("plan", BibleStudyCore.PLANS[plans.getSelectionModel().getSelectedIndex()][0]); d.open("study"); });
        int progress = BibleStudyCore.progress(plan, read), streak = BibleStudyCore.streak(days(), System.currentTimeMillis());
        ProgressBar bar = new ProgressBar(progress / 100.0); bar.setMaxWidth(Double.MAX_VALUE);
        page.getChildren().add(DeskUi.card(DeskUi.row(DeskUi.heading("Plano de leitura"), DeskUi.grow(), plans), DeskUi.muted(BibleStudyCore.plan(plan)[3]), bar, DeskUi.text(progress + "% lido · " + (streak > 0 ? streak + " dias seguidos" : "sem sequência ainda"))));
        VBox today = todayCard(); if (today != null) page.getChildren().add(today);
        // This week
        HBox week = new HBox(8); Set<String> active = days();
        for (String day : BibleStudyCore.week(System.currentTimeMillis())) { Label l = new Label(day.substring(8)); l.getStyleClass().add(active.contains(day) ? "day-done" : "day"); l.setMinSize(40, 40); l.setAlignment(Pos.CENTER); week.getChildren().add(l); }
        page.getChildren().add(DeskUi.card(DeskUi.heading("Esta semana"), week));
        // Memory cards
        long due = 0, total = 0; try { due = d.store.count("SELECT COUNT(*) FROM cards WHERE due<=?", System.currentTimeMillis()); total = d.store.count("SELECT COUNT(*) FROM cards"); } catch (SQLException ignored) {}
        page.getChildren().add(DeskUi.card(DeskUi.heading("Decorar versículos"), DeskUi.text(total == 0 ? "Clique num versículo na Bíblia e escolha \"Guardar para decorar\"." : due + " para rever hoje · " + total + " cartões"),
            DeskUi.row(DeskUi.button(due > 0 ? "Rever agora" : "Treinar mesmo assim", true, this::review), DeskUi.button("Adicionar versículo", false, this::addByReference))));
        page.getChildren().add(DeskUi.tile("game", "Jogo da Bíblia", "Quatro níveis, 60 s por pergunta, recordes", () -> d.open("game")));
        return DeskUi.scroll(page);
    }
    void addCard(String ref, String text) { try { d.store.update("INSERT INTO cards(reference,text,due) VALUES(?,?,?)", ref, text, System.currentTimeMillis()); } catch (SQLException ignored) {} }
    void addByReference() {
        String q = DeskUi.ask("Adicionar versículo para decorar", "Livro, capítulo e versículo (ex.: João 3:16)", ""); if (q == null) return;
        BibleReferences.Reference r = BibleReferences.lookup(q); if (r == null) { DeskUi.info("Passagem não reconhecida", "Indique o livro, o capítulo e o versículo."); return; }
        String text = ""; try { text = d.store.passage(d.store.bible(), r).replaceAll("\\[\\d+\\] ", ""); } catch (SQLException ignored) {}
        if (text.isEmpty()) text = DeskUi.ask("Texto do versículo", "Esta passagem não está na Bíblia importada. Escreva o texto.", ""); if (text == null || text.isEmpty()) return;
        addCard(r.title, text); d.open("study");
    }
    /** Write the verse from memory; the schedule follows the same spaced repetition as Android. */
    void review() {
        List<Object[]> cards; try { cards = d.store.rows("SELECT id,reference,text,interval,ease FROM cards ORDER BY due LIMIT 20"); } catch (SQLException e) { return; }
        if (cards.isEmpty()) { DeskUi.info("Não há cartões", "Guarde versículos para decorar a partir da Bíblia."); return; }
        reviewCard(cards, 0);
    }
    void reviewCard(List<Object[]> cards, int at) {
        if (at >= cards.size()) { DeskUi.info("Sessão concluída", "Os cartões voltam quando for altura de os rever."); d.open("study"); return; }
        Object[] c = cards.get(at); String ref = c[1] + "", text = c[2] + "";
        TextArea answer = new TextArea(); answer.setPromptText("Escreva o versículo de memória"); answer.setWrapText(true); answer.setPrefRowCount(4);
        Label hint = DeskUi.muted("Pista: " + BibleStudyCore.initials(text)); hint.setVisible(false);
        VBox result = new VBox(6);
        Button check = DeskUi.button("Verificar", true, () -> {
            int sim = BibleStudyCore.similarity(text, answer.getText()), grade = BibleStudyCore.grade(sim);
            long[] next = BibleStudyCore.schedule(((Number) c[3]).intValue(), ((Number) c[4]).intValue(), grade, System.currentTimeMillis());
            try { d.store.update("UPDATE cards SET interval=?,ease=?,due=? WHERE id=?", next[0], next[1], next[2], c[0]); } catch (SQLException ignored) {}
            result.getChildren().setAll(DeskUi.heading("Lembrou " + sim + "% das palavras"), DeskUi.text(text), DeskUi.muted("Próxima revisão em " + BibleStudyCore.wait((int) next[0])), DeskUi.button(at + 1 < cards.size() ? "Seguinte" : "Ver resultado", true, () -> reviewCard(cards, at + 1)));
        });
        d.show(DeskUi.scroll(DeskUi.page(DeskUi.row(DeskUi.iconButton("back", "Voltar", () -> d.open("study")), DeskUi.title("Decorar · " + (at + 1) + "/" + cards.size())), DeskUi.card(DeskUi.heading(ref), answer, hint, DeskUi.row(check, DeskUi.button("Pista", false, () -> hint.setVisible(true)), DeskUi.button("Mostrar resposta", false, () -> answer.setText(text)))), result)));
    }

    // ---- Bible game ----
    Node game() {
        VBox page = DeskUi.page(DeskUi.title("Jogo da Bíblia"), DeskUi.muted("Descubra o livro de cada passagem, completa versículos, histórias e mais. 60 segundos por pergunta: o tempo vale pontos."));
        if (d.store.bible() == 0) page.getChildren().add(DeskUi.muted("Sem Bíblia importada, o jogo usa histórias, livros e factos. Importe uma Bíblia para jogar também com versículos."));
        for (int level = 0; level < BibleGame.LEVELS.length; level++) {
            final int lv = level; long best = 0; try { best = d.store.count("SELECT MAX(points) FROM records WHERE level=?", level); } catch (SQLException ignored) {}
            page.getChildren().add(DeskUi.tile("game", BibleGame.LEVELS[level], BibleGame.SECONDS[level] + " s por pergunta · " + BibleGame.POINTS[level] + " pontos" + (best > 0 ? " · recorde " + best : ""), () -> play(lv)));
        }
        return DeskUi.scroll(page);
    }
    BibleGame.Verses verses() { return verses(d.store); }
    /** Random short verses of the chosen Bible for the game (also used by the self-test). */
    static BibleGame.Verses verses(DeskStore store) {
        long bible = store.bible();
        return new BibleGame.Verses() {
            public boolean available() { return bible > 0; }
            public List<BibleGame.Verse> random(List<String> books, int count, Random random) {
                List<BibleGame.Verse> out = new ArrayList<>();
                try {
                    String in = books == null || books.isEmpty() ? "" : " AND book IN (" + String.join(",", Collections.nCopies(books.size(), "?")) + ")";
                    List<Object> args = new ArrayList<>(); args.add(bible); if (books != null) args.addAll(books); args.add(count);
                    for (Object[] r : store.rows("SELECT book,chapter,verse,text FROM verses WHERE bible=? AND length(text) BETWEEN 30 AND 220" + in + " ORDER BY RANDOM() LIMIT ?", args.toArray())) out.add(new BibleGame.Verse(r[0] + "", ((Number) r[1]).intValue(), ((Number) r[2]).intValue(), r[3] + ""));
                } catch (SQLException ignored) {}
                return out;
            }
            public BibleGame.Verse get(String book, int chapter, int verse) {
                String text = store.verse(bible, book, chapter, verse);
                return text.isEmpty() ? null : new BibleGame.Verse(book, chapter, verse, text);
            }
        };
    }
    void play(int level) {
        List<BibleGame.Question> questions = new BibleGame(level, new Random(), verses()).round();
        ask(level, questions, 0, new int[3]);
    }
    /** score = {points, correct, streak} */
    void ask(int level, List<BibleGame.Question> qs, int at, int[] score) {
        if (at >= qs.size()) { finish(level, score); return; }
        BibleGame.Question q = qs.get(at);
        int[] left = {BibleGame.SECONDS[level]}; boolean[] done = {false};
        Label clock = DeskUi.heading(left[0] + " s"); clock.getStyleClass().add("clock");
        VBox answers = new VBox(8), after = new VBox(8);
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> { left[0]--; clock.setText(left[0] + " s"); }));
        timer.setCycleCount(BibleGame.SECONDS[level]);
        java.util.function.Consumer<String> answer = given -> {
            if (done[0]) return; done[0] = true; timer.stop();
            boolean ok = given != null && q.correct(given);
            if (ok) { score[1]++; score[2]++; } else score[2] = 0;
            score[0] += BibleGame.points(level, ok, Math.max(0, left[0]), score[2], false);
            answers.setDisable(true);
            after.getChildren().setAll(DeskUi.heading(ok ? "Certo!" : given == null ? "O tempo acabou" : "Não é"), DeskUi.text("Resposta certa: " + q.answer + (q.reference.isEmpty() ? "" : " · " + q.reference)), q.explain.isEmpty() ? new Region() : DeskUi.muted(q.explain), DeskUi.button(at + 1 < qs.size() ? "Seguinte" : "Ver resultado", true, () -> ask(level, qs, at + 1, score)));
        };
        timer.setOnFinished(e -> answer.accept(null));
        if (q.free()) { TextField f = new TextField(); f.setPromptText(q.word() ? "A palavra que falta" : "Nome do livro"); f.setOnAction(e -> answer.accept(f.getText())); answers.getChildren().addAll(f, DeskUi.button("Responder", true, () -> answer.accept(f.getText()))); }
        else for (String o : q.options) { Button b = DeskUi.button(o, false, () -> answer.accept(o)); b.setMaxWidth(Double.MAX_VALUE); answers.getChildren().add(b); }
        d.show(DeskUi.scroll(DeskUi.page(DeskUi.row(DeskUi.iconButton("back", "Sair do jogo", () -> { timer.stop(); d.open("game"); }), DeskUi.title(BibleGame.LEVELS[level] + " · " + (at + 1) + "/" + qs.size()), DeskUi.grow(), clock),
            DeskUi.muted(score[0] + " pontos"), DeskUi.card(DeskUi.heading(q.prompt), q.body.isEmpty() ? new Region() : DeskUi.text(q.body)), answers, after)));
        timer.play();
    }
    void finish(int level, int[] score) {
        String name = DeskUi.ask("Resultado: " + score[0] + " pontos · " + score[1] + "/10 certas", "O seu nome para o Recorde", d.store.get("player", ""));
        if (name != null) { d.store.put("player", name); try { d.store.update("INSERT INTO records(level,name,points,correct,played) VALUES(?,?,?,?,?)", level, name, score[0], score[1], System.currentTimeMillis()); } catch (SQLException ignored) {} }
        VBox page = DeskUi.page(DeskUi.title("Recordes · " + BibleGame.LEVELS[level]));
        try { int i = 1; for (Object[] r : d.store.rows("SELECT name,points,correct FROM records WHERE level=? ORDER BY points DESC LIMIT 10", level)) page.getChildren().add(DeskUi.text((i++) + ". " + r[0] + " — " + r[1] + " pontos (" + r[2] + "/10)")); } catch (SQLException ignored) {}
        page.getChildren().add(DeskUi.row(DeskUi.button("Jogar de novo", true, () -> play(level)), DeskUi.button("Outro nível", false, () -> d.open("game"))));
        d.show(DeskUi.scroll(page));
    }
}
