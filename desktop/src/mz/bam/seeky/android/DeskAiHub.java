package mz.bam.seeky.android;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 5.51 · Seeky IA no computador: perguntar a vários documentos de uma vez, resumir um livro inteiro
 * (por etapas, retoma onde parou) e criar um plano de estudo a partir de um tema (disciplina, sessões
 * com data na Agenda, cartões e uma nota). O mesmo núcleo do telemóvel (SeekyAiDeep).
 */
final class DeskAiHub {
    final DeskMain d;
    String tab = "Perguntar";
    static final String[] TABS = {"Perguntar", "Resumir livro", "Plano de estudo"};
    final List<File> docs = new ArrayList<>();
    final Map<String, String> texts = new HashMap<>();
    final List<SeekyAiCore.Message> history = new ArrayList<>();
    final List<String[]> chat = new ArrayList<>(); // {role, text, sources}
    volatile SeekyAiClient.Call call;
    String summaryTitle = "", summaryText = ""; File summaryBook;
    DeskAiHub(DeskMain d) { this.d = d; }

    static void schema(DeskStore s) {
        try (java.sql.Statement st = s.db.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ai_parts(book TEXT NOT NULL,part INTEGER NOT NULL,total INTEGER NOT NULL,summary TEXT NOT NULL,PRIMARY KEY(book,part))");
        } catch (SQLException ignored) {}
    }

    Node view() {
        schema(d.store); DeskStudies.schema(d.store);
        VBox page = DeskUi.page();
        HBox tabs = new HBox(6);
        for (String t : TABS) { ToggleButton b = new ToggleButton(t); b.getStyleClass().add("chip"); b.setSelected(t.equals(tab)); b.setOnAction(e -> { tab = t; d.open("ai"); }); tabs.getChildren().add(b); }
        page.getChildren().addAll(DeskUi.row(DeskUi.title("Seeky IA"), DeskUi.grow(), DeskUi.button("Ligação da IA", false, () -> DeskAi.settings(d))), tabs);
        if (!DeskAi.ready(d.store))
            page.getChildren().add(DeskUi.card(DeskUi.heading("Ligue primeiro a Seeky IA"), DeskUi.text("A Seeky IA usa a sua chave gratuita do Google Gemini. Só é enviado à Google o texto necessário para cada pedido."), DeskUi.button("Ligar a IA", true, () -> DeskAi.settings(d))));
        switch (tab) {
            case "Resumir livro": page.getChildren().add(summaryView()); break;
            case "Plano de estudo": page.getChildren().add(planView()); break;
            default: page.getChildren().add(askView());
        }
        return DeskUi.scroll(page);
    }
    boolean ready() {
        DeskAi.load(d.store);
        if (DeskAi.ready(d.store)) return true;
        if (DeskUi.confirm("Ligar a Seeky IA", "Guarde primeiro a sua chave do Google Gemini.")) DeskAi.settings(d);
        return false;
    }

    // ============================================================ texto dos documentos
    static final int MAX_TEXT = 3_000_000;
    /** All the words of a book or document (PDF: every page; EPUB/Word/others: every chapter). */
    static String fullText(File f) throws Exception {
        String n = f.getName().toLowerCase(Locale.ROOT); StringBuilder b = new StringBuilder();
        if (n.endsWith(".pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(f, org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) {
                org.apache.pdfbox.text.PDFTextStripper s = new org.apache.pdfbox.text.PDFTextStripper(); int pages = doc.getNumberOfPages();
                for (int from = 1; from <= pages && b.length() < MAX_TEXT; from += 50) { s.setStartPage(from); s.setEndPage(Math.min(pages, from + 49)); b.append(s.getText(doc)); }
            }
        } else { DeskReader.Book book = DeskReader.chapters(f); for (String t : book.texts) { b.append(t).append("\n\n"); if (b.length() > MAX_TEXT) break; } }
        String t = b.length() > MAX_TEXT ? b.substring(0, MAX_TEXT) : b.toString();
        if (t.trim().length() < 40) throw new Exception(f.getName() + ": não tem texto que o Seeky consiga ler (digitalizações precisam de OCR).");
        return t;
    }
    static String title(File f) { return f.getName().replaceFirst("\\.[^.]+$", ""); }
    static final String[] KINDS = {"*.pdf", "*.epub", "*.docx", "*.txt", "*.md", "*.fb2", "*.mobi", "*.azw3", "*.odt", "*.rtf", "*.mht", "*.mhtml"};

    /** Books from the shelf (several at once, with a search box). */
    List<File> pickBooks(String header, boolean many) {
        List<Object[]> rows; try { rows = d.store.rows("SELECT path,title FROM books ORDER BY opened DESC, title COLLATE NOCASE"); } catch (SQLException e) { rows = new ArrayList<>(); }
        if (rows.isEmpty()) { DeskUi.info("A estante está vazia", "Importe livros em Livros, ou escolha ficheiros do computador."); return Collections.emptyList(); }
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText(header); DeskUi.style(dlg.getDialogPane());
        TextField search = new TextField(); search.setPromptText("Procurar na estante");
        ListView<Object[]> list = new ListView<>(); list.setPrefSize(520, 380);
        list.getSelectionModel().setSelectionMode(many ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        list.setCellFactory(v -> new ListCell<Object[]>() { protected void updateItem(Object[] r, boolean empty) { super.updateItem(r, empty); setText(empty || r == null ? null : r[1] + ""); } });
        List<Object[]> all = rows; list.getItems().setAll(all);
        search.textProperty().addListener((o, a, q) -> { String f = SeekyAiDeep.fold(q.trim()); List<Object[]> keep = new ArrayList<>(); for (Object[] r : all) if (f.isEmpty() || SeekyAiDeep.fold(r[1] + "").contains(f)) keep.add(r); list.getItems().setAll(keep); });
        dlg.getDialogPane().setContent(new VBox(8, search, many ? DeskUi.muted("Ctrl + clique para escolher vários.") : new Region(), list));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> r = dlg.showAndWait();
        if (!r.isPresent() || r.get() != ButtonType.OK) return Collections.emptyList();
        List<File> out = new ArrayList<>(); for (Object[] x : list.getSelectionModel().getSelectedItems()) out.add(new File(x[0] + ""));
        return out;
    }
    List<File> pickFiles(boolean many) {
        FileChooser fc = new FileChooser(); fc.setTitle("Escolher documentos"); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Livros e documentos", KINDS));
        if (many) { List<File> l = fc.showOpenMultipleDialog(d.stage); return l == null ? Collections.emptyList() : l; }
        File f = fc.showOpenDialog(d.stage); return f == null ? Collections.emptyList() : Collections.singletonList(f);
    }
    List<File> pickMaterials() {
        List<Object[]> subs; try { subs = d.store.rows("SELECT id,title FROM st_subjects ORDER BY title COLLATE NOCASE"); } catch (SQLException e) { subs = new ArrayList<>(); }
        if (subs.isEmpty()) { DeskUi.info("Sem disciplinas", "Crie uma disciplina nos Estudos e junte-lhe materiais."); return Collections.emptyList(); }
        ChoiceDialog<String> c = new ChoiceDialog<>(); for (Object[] s : subs) c.getItems().add(s[1] + ""); c.setSelectedItem(c.getItems().get(0)); c.setHeaderText("Materiais de que disciplina?"); c.setTitle("Seeky"); DeskUi.style(c.getDialogPane());
        Optional<String> pick = c.showAndWait(); if (!pick.isPresent()) return Collections.emptyList();
        List<File> out = new ArrayList<>();
        for (Object[] s : subs) if ((s[1] + "").equals(pick.get())) try { for (Object[] m : d.store.rows("SELECT path FROM st_materials WHERE subject=? ORDER BY added", s[0])) out.add(new File(m[0] + "")); } catch (SQLException ignored) {}
        if (out.isEmpty()) DeskUi.info("Sem materiais", "Esta disciplina ainda não tem materiais.");
        return out;
    }

    // ============================================================ perguntar a vários documentos
    Node askView() {
        VBox box = new VBox(12);
        FlowPane chips = new FlowPane(8, 8);
        for (File f : docs) {
            Button x = new Button("✕"); x.getStyleClass().add("flat"); x.setOnAction(e -> { docs.remove(f); d.open("ai"); });
            HBox chip = new HBox(6, DeskUi.icon(f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf") ? "reader" : "book", 15), new Label(title(f)), x); chip.setAlignment(Pos.CENTER_LEFT); chip.getStyleClass().add("doc-chip");
            chips.getChildren().add(chip);
        }
        MenuButton add = new MenuButton("Juntar documentos"); add.getStyleClass().add("primary");
        MenuItem shelf = new MenuItem("Livros da estante…"); shelf.setOnAction(e -> addDocs(pickBooks("Livros para a conversa", true)));
        MenuItem files = new MenuItem("Ficheiros do computador…"); files.setOnAction(e -> addDocs(pickFiles(true)));
        MenuItem mats = new MenuItem("Materiais de uma disciplina…"); mats.setOnAction(e -> addDocs(pickMaterials()));
        add.getItems().addAll(shelf, files, mats);
        Button fresh = DeskUi.button("Nova conversa", false, () -> { history.clear(); chat.clear(); d.open("ai"); });
        box.getChildren().add(DeskUi.card(DeskUi.heading("Perguntar a vários documentos"),
            DeskUi.muted("Junte até " + SeekyAiDeep.MAX_DOCS + " livros ou documentos e pergunte o que quiser. O Seeky escolhe no computador os trechos que respondem à pergunta e envia só esses; a resposta indica de que documento vem cada ideia [D1·T4]."),
            docs.isEmpty() ? DeskUi.text("Ainda não juntou documentos.") : chips, DeskUi.row(add, fresh)));
        VBox messages = new VBox(10);
        if (chat.isEmpty()) {
            VBox ideas = new VBox(6, DeskUi.muted("Por exemplo:"));
            for (String s : new String[]{"O que dizem estes livros sobre a graça?", "Compara a forma como os documentos explicam a justificação.", "Faz um resumo do que os documentos têm em comum."}) {
                Hyperlink h = new Hyperlink(s); h.setOnAction(e -> send(s)); ideas.getChildren().add(h);
            }
            messages.getChildren().add(ideas);
        }
        for (String[] m : chat) messages.getChildren().add(bubble(m));
        TextArea input = new TextArea(); input.setPromptText(docs.isEmpty() ? "Junte documentos e escreva a pergunta…" : (docs.size() == 1 ? "Pergunte ao documento…" : "Pergunte aos " + docs.size() + " documentos…")); input.setWrapText(true); input.setPrefRowCount(3);
        input.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) { e.consume(); String q = input.getText(); input.clear(); send(q); } });
        Button go = DeskUi.button(call != null ? "Parar" : "Enviar", true, () -> { if (call != null) { call.cancel(); return; } String q = input.getText(); input.clear(); send(q); });
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox bar = new HBox(10, input, go); bar.setAlignment(Pos.BOTTOM_RIGHT);
        box.getChildren().addAll(messages, bar);
        if (!docs.isEmpty()) Platform.runLater(input::requestFocus);
        return box;
    }
    Node bubble(String[] m) {
        boolean me = m[0].equals("user");
        Label who = new Label(me ? "Você" : "Seeky"); who.getStyleClass().add(me ? "muted" : "accent-label");
        String shown = me ? m[1] : DeskRich.tidy(m[1]); // 5.59: the answer formatted, without raw marks
        Node body = me ? DeskUi.text(m[1]) : DeskRich.node(d, shown);
        VBox b = new VBox(6, who, body); b.getStyleClass().addAll("card", me ? "bubble-me" : "bubble"); b.setPadding(new javafx.geometry.Insets(12, 14, 12, 14));
        if (!me) {
            if (m.length > 2 && !m[2].isEmpty()) b.getChildren().add(DeskUi.muted(m[2]));
            b.getChildren().add(DeskUi.row(DeskUi.button("Copiar", false, () -> { ClipboardContent c = new ClipboardContent(); c.putString(DeskRich.plain(shown)); Clipboard.getSystemClipboard().setContent(c); d.status("Resposta copiada."); }),
                DeskUi.button("Guardar como nota", false, () -> saveNote("Seeky IA · " + firstQuestion(), shown + (m.length > 2 && !m[2].isEmpty() ? "\n\n" + m[2] : ""), "Seeky IA"))));
        }
        return b;
    }
    String firstQuestion() { for (String[] m : chat) if (m[0].equals("user")) return m[1].length() > 60 ? m[1].substring(0, 60) + "…" : m[1]; return "conversa"; }
    void addDocs(List<File> more) {
        for (File f : more) if (!docs.contains(f) && docs.size() < SeekyAiDeep.MAX_DOCS) docs.add(f);
        if (more.size() > 0 && docs.size() >= SeekyAiDeep.MAX_DOCS) d.status("No máximo " + SeekyAiDeep.MAX_DOCS + " documentos por conversa.");
        d.open("ai");
    }
    void send(String raw) {
        String q = raw == null ? "" : raw.trim(); if (q.isEmpty() || call != null) return;
        if (docs.isEmpty()) { DeskUi.info("Junte documentos", "Escolha primeiro os livros ou documentos a que quer perguntar."); return; }
        if (q.length() > SeekyAiCore.QUESTION) { DeskUi.info("Pergunta longa", "Escreva uma pergunta até 4000 caracteres."); return; }
        if (!ready()) return;
        List<File> using = new ArrayList<>(docs); List<SeekyAiCore.Message> past = new ArrayList<>(history);
        chat.add(new String[]{"user", q}); SeekyAiClient.Call c = new SeekyAiClient.Call(); call = c; d.open("ai");
        d.run("A ler os documentos e a preparar a resposta…", () -> { try {
            List<SeekyAiDeep.Doc> list = new ArrayList<>();
            for (File f : using) { String t = texts.get(f.getPath()); if (t == null) { t = fullText(f); texts.put(f.getPath(), t); } list.add(new SeekyAiDeep.Doc(title(f), t)); }
            String block = SeekyAiDeep.block(list, SeekyAiDeep.query(q, past), SeekyAiDeep.BUDGET);
            String answer = new SeekyAiClient().askMaterials(SeekyAiCore.GEMINI, DeskAi.key(d.store), block, past, q, false, c).text;
            StringBuilder src = new StringBuilder();
            for (int i : SeekyAiDeep.cited(answer, list.size())) src.append(src.length() == 0 ? "Fontes: " : " · ").append("D").append(i).append(" ").append(list.get(i - 1).name);
            return new String[]{answer, src.toString()};
        } finally { Platform.runLater(() -> { if (call == c) { call = null; d.open("ai"); } }); } }, r -> {
            call = null; String n = SeekyAiModels.takeNotice(); if (!n.isEmpty()) d.status(n);
            history.add(new SeekyAiCore.Message("user", q)); history.add(new SeekyAiCore.Message("assistant", r[0]));
            while (history.size() > 12) { history.remove(0); history.remove(0); }
            chat.add(new String[]{"assistant", r[0], r[1]}); d.open("ai");
        });
    }

    // ============================================================ resumir livro inteiro
    Node summaryView() {
        VBox box = new VBox(12);
        box.getChildren().add(DeskUi.card(DeskUi.heading("Resumir um livro inteiro"),
            DeskUi.muted("O Seeky lê o livro todo em partes, resume cada parte e junta tudo num resumo geral com visão geral, ideias principais, conceitos a reter e perguntas de revisão. Se a quota da IA acabar a meio, volte mais tarde: continua onde parou."),
            DeskUi.row(DeskUi.button("Livro da estante…", true, () -> { List<File> f = pickBooks("Que livro resumir?", false); if (!f.isEmpty()) summarise(f.get(0)); }),
                DeskUi.button("Ficheiro do computador…", false, () -> { List<File> f = pickFiles(false); if (!f.isEmpty()) summarise(f.get(0)); }))));
        try {
            List<Object[]> open = d.store.rows("SELECT book,MAX(total),COUNT(*) FROM ai_parts GROUP BY book");
            for (Object[] o : open) {
                String key = o[0] + ""; File f = new File(key.replaceFirst("#[0-9]+$", ""));
                box.getChildren().add(DeskUi.card(DeskUi.heading("Por acabar: " + title(f)), DeskUi.muted(o[2] + " de " + o[1] + " partes resumidas."),
                    DeskUi.row(DeskUi.button("Continuar", true, () -> summarise(f)), DeskUi.button("Esquecer", false, () -> { try { d.store.update("DELETE FROM ai_parts WHERE book=?", key); } catch (SQLException ignored) {} d.open("ai"); }))));
            }
        } catch (SQLException ignored) {}
        if (!summaryText.isEmpty()) {
            ScrollPane t = new ScrollPane(DeskRich.node(d, DeskRich.tidy(summaryText))); t.setFitToWidth(true); t.setPrefViewportHeight(440); t.getStyleClass().add("page-scroll");
            box.getChildren().add(DeskUi.card(DeskUi.heading("Resumo geral · " + summaryTitle), DeskUi.muted("Guardado no Bloco de notas, na pasta Resumos."), t,
                DeskUi.row(DeskUi.button("Copiar", false, () -> { ClipboardContent c = new ClipboardContent(); c.putString(DeskRich.plain(DeskRich.tidy(summaryText))); Clipboard.getSystemClipboard().setContent(c); d.status("Resumo copiado."); }),
                    summaryBook == null ? new Region() : DeskUi.button("Abrir o livro", false, () -> d.reader.open(summaryBook)))));
        }
        return box;
    }
    void summarise(File f) {
        if (!ready()) return;
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("A resumir · " + title(f)); DeskUi.style(dlg.getDialogPane());
        ProgressBar bar = new ProgressBar(-1); bar.setPrefWidth(460); Label what = DeskUi.muted("A ler o livro…");
        dlg.getDialogPane().setContent(new VBox(10, what, bar)); dlg.getDialogPane().getButtonTypes().add(new ButtonType("Parar", ButtonBar.ButtonData.CANCEL_CLOSE));
        SeekyAiClient.Call c = new SeekyAiClient.Call(); dlg.setOnHidden(e -> c.cancel());
        d.run(null, () -> { try {
            String text = fullText(f), key = f.getPath() + "#" + text.length(), secret = DeskAi.key(d.store);
            List<String> parts = SeekyAiDeep.parts(text);
            Map<Integer, String> have = new HashMap<>();
            for (Object[] r : d.store.rows("SELECT part,summary FROM ai_parts WHERE book=?", key)) have.put(((Number) r[0]).intValue(), r[1] + "");
            int[] index = {0};
            SeekyAiDeep.Asker part = (block, q) -> {
                int i = index[0]++; String done = have.get(i); if (done != null) return done;
                String s = patient(() -> new SeekyAiClient().ask(SeekyAiCore.GEMINI, secret, block, title(f) + " · parte " + (i + 1), Collections.emptyList(), q, false, c).text, c, what);
                d.store.update("INSERT OR REPLACE INTO ai_parts(book,part,total,summary) VALUES(?,?,?,?)", key, i, parts.size(), s);
                return s;
            };
            SeekyAiDeep.Asker join = (block, q) -> patient(() -> new SeekyAiClient().askMaterials(SeekyAiCore.GEMINI, secret, block, Collections.emptyList(), q, false, c).text, c, what);
            String title = title(f);
            String summary = SeekyAiDeep.book(title, text, false, part, join, (done, total, step) -> Platform.runLater(() -> { what.setText(step + " · " + Math.min(done, total) + " de " + total + " pedidos"); bar.setProgress(total == 0 ? -1 : (double) done / total); }));
            d.store.update("DELETE FROM ai_parts WHERE book=?", key);
            return new String[]{title, summary};
        } finally { Platform.runLater(dlg::close); } }, r -> {
            dlg.close(); summaryTitle = r[0]; summaryText = r[1]; summaryBook = f; tab = "Resumir livro";
            saveNote("Resumo geral · " + r[0], DeskRich.tidy(r[1]) + "\n\nFonte: " + f.getName() + " (livro inteiro, resumido pela Seeky IA)", "Resumos");
            d.open("ai");
        });
        dlg.show();
    }
    interface Ask { String call() throws Exception; }
    /** The free quota allows few requests per minute: on "too many requests" wait and try again (three times). */
    static String patient(Ask a, SeekyAiClient.Call c, Label what) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try { return a.call(); }
            catch (SeekyAiClient.HttpFailure e) {
                if (e.status != 429 || e instanceof SeekyAiClient.ModelMissing || attempt >= 3) throw e;
                for (int s = 30; s > 0; s--) { c.check(); final int left = s; Platform.runLater(() -> what.setText("Limite de pedidos por minuto · a continuar dentro de " + left + " s")); Thread.sleep(1000); }
            }
        }
    }

    // ============================================================ plano de estudo
    Node planView() {
        TextField theme = new TextField(); theme.setPromptText("Por exemplo: Evangelho de João, Introdução à Economia, Verbos em inglês");
        ComboBox<String> level = new ComboBox<>(); level.getItems().addAll(SeekyAiDeep.LEVELS); level.getSelectionModel().select(0);
        Spinner<Integer> weeks = new Spinner<>(1, 12, 4), per = new Spinner<>(1, 7, 3);
        ComboBox<Integer> minutes = new ComboBox<>(); minutes.getItems().addAll(15, 25, 30, 45, 60, 90); minutes.getSelectionModel().select(Integer.valueOf(30));
        DatePicker start = new DatePicker(LocalDate.now());
        List<Object[]> courses; try { courses = d.store.rows("SELECT id,title FROM st_courses ORDER BY title COLLATE NOCASE"); } catch (SQLException e) { courses = new ArrayList<>(); }
        ComboBox<String> course = new ComboBox<>(); course.getItems().add("Sem curso"); for (Object[] c : courses) course.getItems().add(c[1] + ""); course.getItems().add("Novo curso…");
        long preset = d.studies.course; course.getSelectionModel().select(0); for (int i = 0; i < courses.size(); i++) if (((Number) courses.get(i)[0]).longValue() == preset) course.getSelectionModel().select(i + 1);
        ComboBox<String> type = new ComboBox<>(); type.getItems().addAll("Disciplina", "Módulo"); type.getSelectionModel().select(0);
        List<Object[]> known = courses;
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10);
        g.addRow(0, DeskUi.muted("Tema"), theme); g.addRow(1, DeskUi.muted("Nível"), level); g.addRow(2, DeskUi.muted("Semanas"), weeks);
        g.addRow(3, DeskUi.muted("Sessões por semana"), per); g.addRow(4, DeskUi.muted("Minutos por sessão"), minutes); g.addRow(5, DeskUi.muted("Começa em"), start);
        g.addRow(6, DeskUi.muted("Curso"), course); g.addRow(7, DeskUi.muted("Criar como"), type);
        GridPane.setHgrow(theme, Priority.ALWAYS);
        Label total = DeskUi.muted("");
        Runnable count = () -> total.setText(Math.min(60, weeks.getValue() * per.getValue()) + " sessões na Agenda, " + (type.getSelectionModel().getSelectedIndex() == 1 ? "um módulo novo" : "uma disciplina nova") + (course.getSelectionModel().getSelectedIndex() > 0 ? " no curso escolhido" : "") + ", cartões de revisão e uma nota com o plano.");
        weeks.valueProperty().addListener((o, a, b) -> count.run()); per.valueProperty().addListener((o, a, b) -> count.run()); type.valueProperty().addListener((o, a, b) -> count.run()); course.valueProperty().addListener((o, a, b) -> count.run()); count.run();
        Button make = DeskUi.button("Criar plano com a IA", true, () -> {
            int at = course.getSelectionModel().getSelectedIndex(); long cid = 0;
            if (at == course.getItems().size() - 1) { String c = DeskUi.ask("Novo curso", "Nome do curso", ""); if (c == null || c.trim().isEmpty()) return; try { long now = System.currentTimeMillis(); cid = d.store.update("INSERT INTO st_courses(title,description,created,updated) VALUES(?,?,?,?)", c.trim(), "", now, now); } catch (SQLException e) { return; } }
            else if (at > 0) cid = ((Number) known.get(at - 1)[0]).longValue();
            plan(theme.getText(), level.getSelectionModel().getSelectedIndex(), weeks.getValue(), per.getValue(), minutes.getValue(), start.getValue() == null ? LocalDate.now() : start.getValue(), cid, type.getSelectionModel().getSelectedIndex() == 1 ? "module" : "subject");
        });
        return new VBox(12, DeskUi.card(DeskUi.heading("Plano de estudo a partir de um tema"),
            DeskUi.muted("A Seeky IA prepara as sessões, do essencial à revisão. Revê o plano antes de o guardar nos Estudos."), g, total, make));
    }
    void plan(String theme, int level, int weeks, int perWeek, int minutes, LocalDate start, long courseId, String kind) {
        String q;
        try { q = SeekyAiDeep.planQuestion(theme, weeks, perWeek, minutes, level, false); } catch (IllegalArgumentException e) { DeskUi.info("Plano de estudo", e.getMessage()); return; }
        if (!ready()) return;
        int max = SeekyAiDeep.sessions(weeks, perWeek); long from = start.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        d.run("A Seeky IA está a preparar o plano…", () -> new SeekyAiClient().ask(SeekyAiCore.GEMINI, DeskAi.key(d.store), "", "Plano de estudo", Collections.emptyList(), q, false, new SeekyAiClient.Call()).text, answer -> {
            SeekyAiDeep.Plan p = SeekyAiDeep.parse(answer, max);
            if (p.sessions.isEmpty()) { DeskUi.info("A IA não devolveu um plano", "Tente outra vez, talvez com um tema mais concreto."); return; }
            if (p.title.isEmpty()) p.title = SeekyAiDeep.cut(theme, 80);
            ButtonType keep = new ButtonType("Guardar nos Estudos", ButtonBar.ButtonData.OK_DONE);
            DeskRich.show(d, p.title + " · " + p.sessions.size() + " sessões · " + p.cards.size() + " cartões", DeskRich.tidy(p.note(from, perWeek)), keep, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE)).filter(b -> b == keep).ifPresent(b -> {
                long sid = save(d.store, p, from, perWeek, minutes, courseId, kind);
                d.status("Plano guardado: " + p.title); d.studies.subject = sid; d.studies.course = courseId > 0 ? courseId : DeskStudies.NO_COURSE; d.studies.subjectTab = "Tarefas"; d.open("studies");
            });
        });
    }
    /** A new subject with the sessions as dated tasks, the cards and the plan as a note. Returns the subject id. */
    static long save(DeskStore s, SeekyAiDeep.Plan p, long start, int perWeek, int minutes, long courseId, String kind) {
        DeskStudies.schema(s);
        long now = System.currentTimeMillis(), sid;
        try {
            String title = p.title; int n = 2;
            while (s.count("SELECT COUNT(*) FROM st_subjects WHERE title=? COLLATE NOCASE", title) > 0) title = p.title + " (" + n++ + ")";
            p.title = title;
            sid = s.update("INSERT INTO st_subjects(title,color,created,updated,course_id,kind) VALUES(?,?,?,?,?,?)", title, DeskStudies.COLORS[(int) (s.count("SELECT COUNT(*) FROM st_subjects") % DeskStudies.COLORS.length)], now, now, Math.max(0, courseId), SyncState.kind(kind));
            for (SeekyAiDeep.Session x : p.sessions) {
                String t = SeekyAiDeep.cut("Sessão " + x.day + " · " + x.title + (x.detail.isEmpty() ? "" : " — " + x.detail), 200);
                s.update("INSERT INTO st_tasks(subject,title,due,estimate,priority,updated) VALUES(?,?,?,?,?,?)", sid, t, SeekyAiDeep.due(start, x.day, perWeek), minutes, 1, now);
            }
            for (StudyCards.Card c : p.cards) s.update("INSERT INTO st_cards(subject,front,back,refs,due,updated) VALUES(?,?,?,?,?,?)", sid, c.front, c.back, "Plano da Seeky IA", now, now);
            s.update("INSERT INTO notes(title,body,folder,subject,created,updated) VALUES(?,?,?,?,?,?)", "Plano de estudo · " + title, p.note(start, perWeek), title, sid, now, now);
            return sid;
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    void saveNote(String title, String body, String folder) {
        try { long now = System.currentTimeMillis(); d.store.update("INSERT INTO notes(title,body,folder,created,updated) VALUES(?,?,?,?,?)", SeekyAiDeep.cut(title, 200), body, folder, now, now); d.status("Guardado no Bloco de notas · " + folder); }
        catch (SQLException e) { DeskUi.info("Não foi possível guardar", e.getMessage()); }
    }
}
