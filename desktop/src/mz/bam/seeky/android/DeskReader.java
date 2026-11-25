package mz.bam.seeky.android;

import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Livros: a library with progress; PDF pages, EPUB/Word/FB2/MOBI/ODT/RTF chapters and text files;
 * coloured marks with notes, marked PDF pages and Seeky IA (summary, explanation, questions).
 */
final class DeskReader {
    final DeskMain d;
    DeskReader(DeskMain d) { this.d = d; }
    static final String[] TYPES = {"*.pdf", "*.epub", "*.docx", "*.txt", "*.md", "*.fb2", "*.mobi", "*.azw3", "*.prc", "*.odt", "*.rtf", "*.mht", "*.mhtml", "*.cbz"};

    VBox recentCard() {
        try {
            List<Object[]> r = d.store.rows("SELECT path,title,position,total FROM books WHERE opened>0 ORDER BY opened DESC LIMIT 1"); if (r.isEmpty()) return null;
            Object[] b = r.get(0); int total = ((Number) b[3]).intValue(), pos = ((Number) b[2]).intValue();
            return DeskUi.card(DeskUi.heading("Continuar a ler"), DeskUi.text(b[1] + ""), DeskUi.muted(total > 0 ? (pos + 1) + " de " + total : ""), DeskUi.button("Abrir", true, () -> open(new File(b[0] + ""))));
        } catch (SQLException e) { return null; }
    }
    static final String[] MODES = {"Estante", "Grelha", "Lista"};
    String filter = ""; int page;
    static final int SHELF_PAGE = 40, LIST_PAGE = 80;
    /** Livros as on the phone: wooden bookcase, grid of covers or list, a page at a time; import files or a folder with sub-folders. */
    Node view() {
        long marks = 0; try { marks = d.store.count("SELECT COUNT(*) FROM marks"); } catch (SQLException ignored) {}
        int mode = Math.max(0, Math.min(2, Integer.parseInt(d.store.get("books_mode", "0"))));
        MenuButton add = new MenuButton("Importar"); add.getStyleClass().add("primary");
        MenuItem files = new MenuItem("Importar livros…"); files.setOnAction(e -> chooseMany());
        MenuItem folder = new MenuItem("Importar pasta e subpastas…"); folder.setOnAction(e -> importFolder());
        MenuItem one = new MenuItem("Abrir um livro…"); one.setOnAction(e -> choose());
        add.getItems().addAll(files, folder, one);
        HBox modes = new HBox(4); ToggleGroup group = new ToggleGroup();
        for (int m = 0; m < MODES.length; m++) { final int mm = m; ToggleButton b = new ToggleButton(MODES[m]); b.setToggleGroup(group); b.getStyleClass().add("chip"); b.setSelected(m == mode); b.setOnAction(e -> { d.store.put("books_mode", mm + ""); page = 0; d.open("reader"); }); modes.getChildren().add(b); }
        TextField search = new TextField(filter); search.setPromptText("Procurar livros"); search.setPrefWidth(220);
        VBox page0 = DeskUi.page(DeskUi.row(DeskUi.title("Livros"), DeskUi.grow(), search, DeskUi.button("Marcações" + (marks > 0 ? " · " + marks : ""), false, () -> d.show(DeskMarks.view(d))), add));
        long all = 0; try { all = d.store.count("SELECT COUNT(*) FROM books"); } catch (SQLException ignored) {}
        Label count = DeskUi.muted(""); count.getStyleClass().add("caps");
        page0.getChildren().add(DeskUi.row(count, DeskUi.grow(), modes));
        if (all == 0) {
            page0.getChildren().add(DeskUi.card(DeskUi.heading("A sua estante está vazia"), DeskUi.text("Importe os livros do computador (PDF, EPUB, Word, TXT, FB2, MOBI, AZW3, ODT, RTF ou CBZ), um a um ou uma pasta inteira com as subpastas. Ficam aqui com a capa e o ponto onde parou."),
                DeskUi.row(DeskUi.button("Importar pasta", true, this::importFolder), DeskUi.button("Importar livros", false, this::chooseMany))));
            return DeskUi.scroll(page0);
        }
        VBox shelf = new VBox(); HBox pager = new HBox(10); pager.setAlignment(Pos.CENTER);
        page0.getChildren().addAll(shelf, pager);
        ScrollPane scroll = DeskUi.scroll(page0);
        int per = mode == 2 ? LIST_PAGE : SHELF_PAGE;
        Runnable[] draw = {null};
        draw[0] = () -> {
            String q = search.getText().trim(); filter = q;
            String where = q.isEmpty() ? "" : " WHERE title LIKE ? OR path LIKE ?"; Object[] args = q.isEmpty() ? new Object[0] : new Object[]{"%" + q + "%", "%" + q + "%"};
            long total = 0; List<Object[]> shown = new ArrayList<>();
            try {
                total = d.store.count("SELECT COUNT(*) FROM books" + where, args);
                int pages = (int) Math.max(1, (total + per - 1) / per); page = Math.max(0, Math.min(page, pages - 1));
                Object[] pa = Arrays.copyOf(args, args.length + 2); pa[args.length] = per; pa[args.length + 1] = page * per;
                shown = d.store.rows("SELECT path,title,kind,position,total,opened FROM books" + where + " ORDER BY opened DESC, title COLLATE NOCASE LIMIT ? OFFSET ?", pa);
            } catch (SQLException ignored) {}
            count.setText((total + (total == 1 ? " livro" : " livros")).toUpperCase(Locale.ROOT));
            DeskCovers.newPage();
            shelf.getChildren().setAll(mode == 0 ? bookcase(shown, page0) : mode == 1 ? grid(shown) : list(shown));
            int pages = (int) Math.max(1, (total + per - 1) / per);
            pager.getChildren().clear();
            if (pages > 1) {
                Button prev = DeskUi.button("Anterior", false, () -> { page--; draw[0].run(); scroll.setVvalue(0); }), next = DeskUi.button("Seguinte", false, () -> { page++; draw[0].run(); scroll.setVvalue(0); });
                prev.setDisable(page == 0); next.setDisable(page >= pages - 1);
                pager.getChildren().addAll(prev, DeskUi.muted("Página " + (page + 1) + " de " + pages), next);
            }
        };
        javafx.animation.PauseTransition later = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
        later.setOnFinished(e -> { page = 0; draw[0].run(); });
        search.textProperty().addListener((o, a, b) -> later.playFromStart());
        draw[0].run();
        return scroll;
    }
    double progress(Object[] b) { int total = ((Number) b[4]).intValue(), pos = ((Number) b[3]).intValue(); long opened = ((Number) b[5]).longValue(); return opened == 0 || total <= 0 ? 0 : Math.min(1, (pos + 1.0) / total); }
    void bind(Node n, File f) {
        n.setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) open(f); });
        MenuItem o = new MenuItem("Abrir"); o.setOnAction(e -> open(f));
        MenuItem dir = new MenuItem("Abrir a pasta"); dir.setOnAction(e -> DeskFiles.system(f.getParentFile()));
        MenuItem out = new MenuItem("Tirar da estante"); out.setOnAction(e -> forget(f));
        MenuItem sum = new MenuItem("Resumir o livro inteiro (Seeky IA)"); sum.setOnAction(e -> { d.ai.tab = "Resumir livro"; d.open("ai"); d.ai.summarise(f); });
        MenuItem ask = new MenuItem("Perguntar a este livro (Seeky IA)"); ask.setOnAction(e -> { d.ai.tab = "Perguntar"; d.ai.addDocs(Collections.singletonList(f)); });
        ContextMenu menu = new ContextMenu(o, dir, sum, ask, out); n.setOnContextMenuRequested(e -> menu.show(n, e.getScreenX(), e.getScreenY()));
        n.setCursor(javafx.scene.Cursor.HAND); DeskUi.lift(n);
    }
    /** Estante: covers standing on wooden planks, as many per shelf as fit. */
    Node bookcase(List<Object[]> books, Region page) {
        VBox caseBox = new VBox(); caseBox.getStyleClass().add("bookcase");
        double coverW = 104;
        int perRow = Math.max(3, (int) ((Math.max(700, d.content.getWidth() - 90)) / (coverW + 22)));
        for (int start = 0; start < books.size(); start += perRow) {
            HBox row = new HBox(22); row.setAlignment(Pos.BOTTOM_LEFT); row.setPadding(new Insets(18, 22, 0, 22));
            for (int i = start; i < Math.min(books.size(), start + perRow); i++) {
                Object[] b = books.get(i); File f = new File(b[0] + "");
                StackPane book = new StackPane(DeskCovers.view(f, b[1] + "", coverW)); book.getStyleClass().add("shelf-book");
                double p = progress(b);
                if (p > 0) { Label pct = new Label(Math.round(p * 100) + "%"); pct.getStyleClass().add("cover-badge"); StackPane.setAlignment(pct, Pos.TOP_RIGHT); StackPane.setMargin(pct, new Insets(5)); book.getChildren().add(pct); }
                Tooltip.install(book, new Tooltip(b[1] + (f.exists() ? "" : "\nFicheiro em falta")));
                bind(book, f); row.getChildren().add(book);
            }
            Region plank = new Region(); plank.getStyleClass().add("plank"); plank.setMinHeight(16); plank.setMaxWidth(Double.MAX_VALUE);
            caseBox.getChildren().addAll(row, plank);
        }
        return caseBox;
    }
    /** Grelha: covers with the title and progress under each. */
    Node grid(List<Object[]> books) {
        FlowPane grid = new FlowPane(18, 22);
        for (Object[] b : books) {
            File f = new File(b[0] + ""); double p = progress(b);
            Label t = DeskUi.text(b[1] + ""); t.setMaxWidth(140); t.setWrapText(true); t.setMaxHeight(42); t.setStyle("-fx-font-size: 13px;");
            ProgressBar bar = new ProgressBar(p); bar.setPrefWidth(140); bar.setMaxHeight(6); bar.getStyleClass().add("thin");
            VBox cell = new VBox(6, DeskCovers.view(f, b[1] + "", 140), t, bar); cell.setPrefWidth(140);
            if (!f.exists()) cell.getChildren().add(DeskUi.muted("Ficheiro em falta"));
            bind(cell, f); grid.getChildren().add(cell);
        }
        return grid;
    }
    /** Lista: small cover, title, format, progress and when it was last read. */
    Node list(List<Object[]> books) {
        VBox list = new VBox(8);
        for (Object[] b : books) {
            File f = new File(b[0] + ""); double p = progress(b); long opened = ((Number) b[5]).longValue();
            String when = opened == 0 ? "Por ler" : "Lido " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new Date(opened));
            VBox words = new VBox(3, DeskUi.heading(b[1] + ""), DeskUi.muted((b[2] + "").toUpperCase(Locale.ROOT) + " · " + when + (f.exists() ? "" : " · ficheiro em falta")));
            ProgressBar bar = new ProgressBar(p); bar.setPrefWidth(220); bar.getStyleClass().add("thin"); words.getChildren().add(DeskUi.row(bar, DeskUi.muted(Math.round(p * 100) + "%")));
            HBox.setHgrow(words, Priority.ALWAYS);
            HBox row = new HBox(14, DeskCovers.view(f, b[1] + "", 48), words); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().addAll("card", "tile"); row.setPadding(new Insets(10, 14, 10, 10));
            bind(row, f); list.getChildren().add(row);
        }
        return list;
    }
    void chooseMany() {
        FileChooser fc = new FileChooser(); fc.setTitle("Importar livros"); fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Livros", TYPES), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        List<File> files = fc.showOpenMultipleDialog(d.stage); if (files == null || files.isEmpty()) return;
        d.run("A importar livros…", () -> importBooks(d.store, files), n -> { d.open("reader"); d.status(n + (n == 1 ? " livro na estante." : " livros na estante.")); });
    }
    void importFolder() {
        javafx.stage.DirectoryChooser c = new javafx.stage.DirectoryChooser(); c.setTitle("Importar pasta e subpastas");
        File docs = new File(System.getProperty("user.home"), "Documents"); if (docs.isDirectory()) c.setInitialDirectory(docs);
        File dir = c.showDialog(d.stage); if (dir == null) return;
        d.run("A procurar livros em " + dir.getName() + "…", () -> { List<File> found = findBooks(dir); d.status("A juntar " + found.size() + " livros à estante…"); return importBooks(d.store, found, seen -> d.status("A juntar à estante… " + seen + " de " + found.size())); }, n -> { page = 0; d.open("reader"); DeskUi.info(n + (n == 1 ? " livro importado" : " livros importados"), n == 0 ? "Não encontrei livros nesta pasta nem nas subpastas." : "Estão na estante, com as capas."); });
    }
    /** Every book file in a folder and its sub-folders (hidden folders skipped, at most 20000). */
    static List<File> findBooks(File dir) throws IOException {
        List<File> out = new ArrayList<>();
        java.nio.file.Files.walkFileTree(dir.toPath(), EnumSet.noneOf(java.nio.file.FileVisitOption.class), 24, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            @Override public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path p, java.nio.file.attribute.BasicFileAttributes a) { String n = p.getFileName() == null ? "" : p.getFileName().toString(); return !p.equals(dir.toPath()) && (n.startsWith(".") || n.startsWith("$")) ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE; }
            @Override public java.nio.file.FileVisitResult visitFile(java.nio.file.Path p, java.nio.file.attribute.BasicFileAttributes a) { if (a.isRegularFile() && accepts(p.getFileName().toString()) && a.size() > 0 && out.size() < 20000) out.add(p.toFile()); return java.nio.file.FileVisitResult.CONTINUE; }
            @Override public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path p, IOException e) { return java.nio.file.FileVisitResult.CONTINUE; }
        });
        return out;
    }
    /** Puts books on the shelf (already there: kept with their progress). Returns how many are new. */
    static int importBooks(DeskStore store, List<File> files) throws SQLException { return importBooks(store, files, null); }
    /** Puts books on the shelf in one transaction (already there: kept with their progress). Returns how many are new. */
    static int importBooks(DeskStore store, List<File> files, java.util.function.IntConsumer progress) throws SQLException {
        Set<String> have = new HashSet<>(); for (Object[] r : store.rows("SELECT path FROM books")) have.add(r[0] + "");
        int n = 0, seen = 0; boolean auto = store.db.getAutoCommit(); store.db.setAutoCommit(false);
        try (java.sql.PreparedStatement p = store.db.prepareStatement("INSERT OR IGNORE INTO books(path,title,kind,position,total,opened) VALUES(?,?,?,0,0,0)")) {
            for (File f : files) {
                seen++; if (progress != null && seen % 100 == 0) progress.accept(seen);
                if (!accepts(f.getName()) || !have.add(f.getPath())) continue;
                String kind = f.getName().replaceFirst(".*\\.", "").toLowerCase(Locale.ROOT);
                String title = kind.equals("epub") ? DeskCovers.epubTitle(f) : "";
                if (title.isEmpty()) title = f.getName().replaceFirst("\\.[^.]+$", "").replace('_', ' ');
                p.setString(1, f.getPath()); p.setString(2, title); p.setString(3, kind); p.addBatch(); n++;
                if (n % 500 == 0) p.executeBatch();
            }
            p.executeBatch(); store.db.commit();
        } catch (SQLException e) { store.db.rollback(); throw e; }
        finally { store.db.setAutoCommit(auto); }
        return n;
    }
    void forget(File f) { if (DeskUi.confirm("Tirar da estante?", f.getName() + "\nO ficheiro não é apagado.")) { try { d.store.update("DELETE FROM books WHERE path=?", f.getPath()); } catch (SQLException ignored) {} d.open("reader"); } }
    void choose() {
        FileChooser fc = new FileChooser(); fc.setTitle("Abrir livro"); fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Livros", TYPES), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        File f = fc.showOpenDialog(d.stage); if (f != null) open(f);
    }
    static boolean accepts(String name) { String n = name.toLowerCase(Locale.ROOT); for (String t : TYPES) if (n.endsWith(t.substring(1))) return true; return false; }

    void open(File f) { open(f, -1, 0); }
    /** Opens a book at a chapter or page (-1: where the reader stopped), showing a mark when given. */
    void open(File f, int at, long mark) {
        if (!f.isFile()) { DeskUi.info("Livro indisponível", f.getPath()); return; }
        String name = f.getName().toLowerCase(Locale.ROOT);
        int pos = at; if (pos < 0) { pos = 0; try { pos = (int) d.store.count("SELECT position FROM books WHERE path=?", f.getPath()); } catch (SQLException ignored) {} }
        final int start = pos;
        if (name.endsWith(".pdf")) d.run("A abrir " + f.getName() + "…", () -> PDDocument.load(f), doc -> pdf(f, doc, start));
        else d.run("A abrir " + f.getName() + "…", () -> chapters(f), book -> text(f, book, start, mark));
    }
    void remember(File f, String title, String kind, int position, int total) {
        try { d.store.update("INSERT OR REPLACE INTO books(path,title,kind,position,total,opened) VALUES(?,?,?,?,?,?)", f.getPath(), title, kind, position, total, System.currentTimeMillis()); } catch (SQLException ignored) {}
    }
    /** The reading bar: back, title, extra controls, previous/next, listen, mark, Seeky IA, note. */
    HBox bar(String title, Label where, Runnable prev, Runnable next, Runnable listen, Node... extra) {
        Button back = DeskUi.iconButton("back", "Voltar à estante", () -> { DeskSpeech.stop(); d.open("reader"); });
        Label t = DeskUi.heading(title); HBox.setHgrow(t, Priority.ALWAYS); t.setMaxWidth(Double.MAX_VALUE); t.setWrapText(false); t.setMinWidth(60); t.setTooltip(new Tooltip(title));
        where.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = DeskUi.row(back, t); row.getChildren().addAll(extra);
        row.getChildren().addAll(DeskUi.iconButton("back", "Anterior", prev), where, nextButton(next), DeskUi.iconButton("speaker", "Ouvir", listen));
        return row;
    }
    static Button nextButton(Runnable next) { Button b = DeskUi.iconButton("back", "Seguinte", next); b.getGraphic().setRotate(180); return b; }
    Button noteButton(String title, java.util.function.Supplier<String> where) { return DeskUi.iconButton("note", "Nota deste livro", () -> d.notes.newWith(title + " — " + where.get(), "> " + title + " · " + where.get() + "\n\n")); }

    // ---- Seeky IA no leitor ----
    static final String[][] AI = {
        {"resumo", "Resumir"}, {"explicar", "Explicar a selecção"}, {"perguntas", "Perguntas de estudo"}, {"pedir", "Perguntar sobre o texto…"}};
    static String prompt(String key, String extra) {
        String p;
        switch (key) {
            case "resumo": p = "Resume este texto de um livro: as ideias principais num parágrafo curto e depois 3 a 7 pontos-chave."; break;
            case "explicar": p = "Explica este excerto de um livro de forma simples e clara: o que quer dizer, o contexto necessário e, se for um texto bíblico ou cristão, passagens bíblicas relacionadas."; break;
            case "perguntas": p = "Escreve 5 a 8 perguntas de estudo sobre este texto, cada uma seguida de uma resposta curta na linha seguinte começada por '> '. Numera as perguntas."; break;
            default: p = "Pergunta do leitor sobre este texto: " + (extra == null ? "" : extra.trim());
        }
        return p + NotepadAiText.FORMAT_PT;
    }
    MenuButton aiMenu(String title, java.util.function.Supplier<String> place, java.util.function.Supplier<String> whole, java.util.function.Supplier<String> selection) {
        MenuButton m = new MenuButton("Seeky IA", DeskUi.icon("sparkle", 18)); m.getStyleClass().add("primary"); m.setMinWidth(Region.USE_PREF_SIZE);
        for (String[] a : AI) {
            MenuItem i = new MenuItem(a[1]);
            i.setOnAction(e -> {
                String source = a[0].equals("explicar") ? selection.get() : whole.get();
                if (a[0].equals("explicar") && (source == null || source.trim().isEmpty())) { DeskUi.info("Seleccione um excerto", "Seleccione com o rato as palavras que quer explicar e escolha outra vez Explicar a selecção."); return; }
                String extra = a[0].equals("pedir") ? DeskUi.ask("Perguntar ao Seeky", "Por exemplo: quem são as personagens deste capítulo?", "") : "";
                if (extra == null) return;
                ask(a[1], prompt(a[0], extra), source, title + " · " + place.get());
            });
            m.getItems().add(i);
        }
        return m;
    }
    void ask(String label, String prompt, String source, String title) {
        DeskAi.load(d.store);
        if (d.store.get("gemini_key", "").isEmpty()) { if (DeskUi.confirm("Ligar a Seeky IA", "Para usar a IA nos livros, guarde primeiro a sua chave do Google Gemini.")) DeskAi.settings(d); return; }
        String text = source == null ? "" : source.trim(); if (text.isEmpty()) { DeskUi.info("Sem texto", "Esta página não tem texto que o Seeky consiga ler."); return; }
        final String sent = NotepadAiText.cut(text);
        d.run("O Seeky está a ler…", () -> new SeekyAiClient().ask(SeekyAiCore.GEMINI, DeskAi.key(d.store), sent, title, Collections.emptyList(), prompt, false, new SeekyAiClient.Call()).text, answer -> {
            String switched = SeekyAiModels.takeNotice(); if (!switched.isEmpty()) d.status(switched);
            String clean = NotepadAiText.clean(answer);
            Alert show = new Alert(Alert.AlertType.NONE, clean, new ButtonType("Guardar como nota", ButtonBar.ButtonData.OK_DONE), new ButtonType("Copiar", ButtonBar.ButtonData.OTHER), ButtonType.CLOSE);
            show.setHeaderText(label + " · " + title); show.setTitle("Seeky IA"); show.getDialogPane().setPrefWidth(660); DeskUi.style(show.getDialogPane());
            show.showAndWait().ifPresent(b -> {
                if (b.getButtonData() == ButtonBar.ButtonData.OK_DONE) d.notes.newWith(label + " — " + title, "> " + title + "\n\n" + clean);
                else if (b.getButtonData() == ButtonBar.ButtonData.OTHER) { javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent(); c.putString(clean); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c); d.status("Copiado."); }
            });
        });
    }

    // ---- PDF ----
    void pdf(File f, PDDocument doc, int start) {
        PDFRenderer render = new PDFRenderer(doc); int pages = doc.getNumberOfPages();
        String title = Optional.ofNullable(doc.getDocumentInformation().getTitle()).filter(s -> !s.trim().isEmpty()).orElse(f.getName().replaceFirst("(?i)\\.pdf$", ""));
        int[] at = {Math.min(Math.max(0, start), Math.max(0, pages - 1))}; double[] zoom = {1.4};
        ImageView view = new ImageView(); view.setPreserveRatio(true);
        Label where = DeskUi.muted("");
        StackPane holder = new StackPane(view); ScrollPane scroll = new ScrollPane(holder); scroll.setFitToWidth(true); scroll.setPannable(true); VBox.setVgrow(scroll, Priority.ALWAYS); DeskUi.smooth(scroll);
        String[] pageText = {""};
        Button markPage = DeskUi.button("Marcar página", false, () -> {});
        Runnable draw = () -> {
            where.setText((at[0] + 1) + " / " + pages); remember(f, title, "pdf", at[0], pages);
            boolean marked = !DeskMarks.list(d.store, f.getPath(), at[0]).isEmpty(); markPage.setText(marked ? "Página marcada" : "Marcar página");
            int p = at[0];
            d.run(null, () -> { synchronized (doc) { return render.renderImageWithDPI(p, (float) (96 * zoom[0])); } }, img -> { if (p == at[0]) { view.setImage(SwingFXUtils.toFXImage(img, null)); scroll.setVvalue(0); } });
            d.worker.execute(() -> { try { org.apache.pdfbox.text.PDFTextStripper s = new org.apache.pdfbox.text.PDFTextStripper(); s.setStartPage(p + 1); s.setEndPage(p + 1); String t; synchronized (doc) { t = s.getText(doc); } if (p == at[0]) pageText[0] = t; } catch (Exception ignored) {} });
        };
        markPage.setOnAction(e -> {
            List<DeskMarks.Mark> existing = DeskMarks.list(d.store, f.getPath(), at[0]);
            if (!existing.isEmpty()) { DeskMarks.menu(d, existing.get(0).id, draw); return; }
            String snippet = pageText[0].replaceAll("\\s+", " ").trim(); if (snippet.length() > 220) snippet = snippet.substring(0, 220) + "…";
            pageMark(f, title, at[0], snippet, draw);
        });
        Runnable prev = () -> { if (at[0] > 0) { at[0]--; draw.run(); } }, next = () -> { if (at[0] < pages - 1) { at[0]++; draw.run(); } };
        Slider zoomBar = new Slider(0.6, 3, zoom[0]); zoomBar.setPrefWidth(120); zoomBar.setTooltip(new Tooltip("Zoom")); zoomBar.valueChangingProperty().addListener((o, a, b) -> { if (!b) { zoom[0] = zoomBar.getValue(); draw.run(); } });
        java.util.function.Supplier<String> place = () -> "página " + (at[0] + 1);
        HBox top = bar(title, where, prev, next, () -> DeskSpeech.toggle(d, pageText[0]), zoomBar, markPage);
        top.getChildren().addAll(aiMenu(title, place, () -> pageText[0], () -> ""), noteButton(title, place));
        VBox page = new VBox(10, top, scroll); page.setPadding(new javafx.geometry.Insets(14, 18, 10, 18)); page.getStyleClass().add("page");
        page.setOnKeyPressed(e -> { switch (e.getCode()) { case RIGHT: case PAGE_DOWN: next.run(); break; case LEFT: case PAGE_UP: prev.run(); break; default: } });
        d.show(page); page.requestFocus(); draw.run();
    }
    void pageMark(File f, String title, int page, String snippet, Runnable done) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Seeky"); dialog.setHeaderText("Marcar a página " + (page + 1)); DeskUi.style(dialog.getDialogPane());
        int[] color = {DeskMarks.COLORS[0]};
        TextArea note = new TextArea(); note.setPromptText("Nota sobre esta página (opcional)"); note.setWrapText(true); note.setPrefRowCount(3);
        VBox box = new VBox(10, DeskMarks.dots(c -> color[0] = c), note); box.setPrefWidth(440); dialog.getDialogPane().setContent(box);
        ButtonType save = new ButtonType("Marcar", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.showAndWait().filter(b -> b == save).ifPresent(b -> {
            try { DeskMarks.add(d.store, f.getPath(), title, page, "Página " + (page + 1), snippet.isEmpty() ? "Página " + (page + 1) : snippet, color[0], note.getText().trim()); } catch (SQLException ignored) {}
            d.status("Página " + (page + 1) + " marcada."); done.run();
        });
    }

    // ---- EPUB, Word, converted formats and text ----
    static final class Book { String title = ""; final List<String> names = new ArrayList<>(), texts = new ArrayList<>(); String kind = ""; }
    static Book chapters(File f) throws Exception {
        String name = f.getName(), lower = name.toLowerCase(Locale.ROOT); Book b = new Book(); b.title = name.replaceFirst("\\.[^.]+$", "");
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            String all = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); b.kind = "txt";
            int size = 40000; for (int i = 0; i < all.length(); i += size) { int end = Math.min(all.length(), i + size); int cut = all.lastIndexOf('\n', end); if (end < all.length() && cut > i) end = cut; b.texts.add(all.substring(i, end)); b.names.add("Parte " + (b.texts.size())); i = end - size; }
            if (b.texts.isEmpty()) { b.texts.add(""); b.names.add("Texto"); }
            return b;
        }
        File source = f; boolean epub = lower.endsWith(".epub");
        if (EbookConvert.supported(name) && !epub && !lower.endsWith(".docx")) { source = new File(DeskStore.folder("convertidos"), EbookConvert.epubName(name)); EbookConvert.convert(f, name, source); epub = true; }
        BookArchive a = new BookArchive(source, epub || source.getName().toLowerCase(Locale.ROOT).endsWith(".epub"));
        try {
            b.kind = epub ? "epub" : "docx"; if (a.title != null && !a.title.trim().isEmpty()) b.title = a.title.trim();
            for (int i = 0; i < a.chapters.size(); i++) { b.texts.add(a.chapter(i)); b.names.add(i < a.names.size() && !a.names.get(i).trim().isEmpty() ? a.names.get(i) : "Capítulo " + (i + 1)); }
        } finally { a.close(); }
        if (b.texts.isEmpty()) throw new IOException("Este livro não tem texto legível.");
        return b;
    }
    /** The chapter as HTML in the theme's colours, with its marks painted in. */
    static String chapterHtml(Book book, int at, List<DeskMarks.Mark> marks) {
        DeskThemes.Theme th = DeskThemes.current;
        StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>body{font-family:Georgia,'Segoe UI',serif;font-size:19px;line-height:1.65;max-width:760px;margin:24px auto;padding:0 24px;")
            .append("background:").append(DeskThemes.hex(th.bg)).append(";color:").append(DeskThemes.hex(th.ink)).append("}h2{font-family:'Segoe UI',sans-serif}a.passage{color:").append(DeskThemes.hex(th.accent))
            .append(";text-decoration:none;border-bottom:1px dotted}::selection{background:").append(DeskThemes.hex(th.accent)).append(";color:").append(DeskThemes.hex(th.onAccent)).append("}</style></head><body><h2>").append(DeskNotesHtml.esc(book.names.get(at))).append("</h2>");
        for (String para : book.texts.get(at).split("\\n\\s*\\n|\\n")) if (!para.trim().isEmpty()) h.append("<p>").append(DeskMarks.paint(DeskNotesHtml.linkPassages(DeskNotesHtml.esc(para.trim())), marks)).append("</p>");
        return h.append("</body></html>").toString();
    }
    void text(File f, Book book, int start, long showMark) {
        int[] at = {Math.min(Math.max(0, start), book.texts.size() - 1)};
        WebView web = new WebView();
        Label where = DeskUi.muted("");
        ComboBox<String> toc = new ComboBox<>(); toc.getItems().addAll(book.names); toc.setPrefWidth(200); toc.setMinWidth(120);
        String[] selected = {""};
        List<Runnable> afterLoad = new ArrayList<>();
        web.getEngine().getLoadWorker().stateProperty().addListener((o, a, s) -> { if (s == Worker.State.SUCCEEDED) { List<Runnable> run = new ArrayList<>(afterLoad); afterLoad.clear(); for (Runnable r : run) r.run(); } });
        // Selection bar: colours, note, copy, listen, explain.
        HBox selBar = new HBox(6); selBar.getStyleClass().add("selection-bar"); selBar.setPadding(new javafx.geometry.Insets(6, 10, 6, 10)); selBar.setVisible(false); selBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Pane layer = new Pane(selBar); layer.setPickOnBounds(false);
        StackPane holder = new StackPane(web, layer); VBox.setVgrow(holder, Priority.ALWAYS);
        Runnable[] draw = {null};
        java.util.function.Consumer<Boolean> render = keep -> {
            Object y = null; if (keep) try { y = web.getEngine().executeScript("window.scrollY"); } catch (Exception ignored) {}
            final Object scrollY = y;
            where.setText((at[0] + 1) + " / " + book.texts.size()); remember(f, book.title, book.kind, at[0], book.texts.size()); toc.getSelectionModel().select(at[0]);
            if (scrollY != null) afterLoad.add(() -> web.getEngine().executeScript("window.scrollTo(0," + scrollY + ")"));
            selBar.setVisible(false);
            web.getEngine().loadContent(chapterHtml(book, at[0], DeskMarks.list(d.store, f.getPath(), at[0])));
        };
        draw[0] = () -> render.accept(false);
        Runnable redraw = () -> render.accept(true);
        java.util.function.IntConsumer mark = c -> {
            if (selected[0].isEmpty()) return;
            try { DeskMarks.add(d.store, f.getPath(), book.title, at[0], book.names.get(at[0]), selected[0], c, ""); } catch (SQLException ignored) {}
            redraw.run();
        };
        Button note = DeskUi.button("Nota", false, () -> {
            String s = selected[0]; if (s.isEmpty()) return;
            String n = DeskUi.ask("Nota sobre a marcação", "“" + (s.length() > 120 ? s.substring(0, 120) + "…" : s) + "”", "");
            if (n == null) return;
            try { DeskMarks.add(d.store, f.getPath(), book.title, at[0], book.names.get(at[0]), s, DeskMarks.COLORS[0], n); } catch (SQLException ignored) {}
            redraw.run();
        });
        Button copy = DeskUi.button("Copiar", false, () -> { javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent(); c.putString(selected[0]); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c); selBar.setVisible(false); d.status("Copiado."); });
        Button listen = DeskUi.iconButton("speaker", "Ouvir a selecção", () -> DeskSpeech.say(d, selected[0]));
        Button explain = DeskUi.button("Explicar", true, () -> { selBar.setVisible(false); ask("Explicar a selecção", prompt("explicar", ""), selected[0], book.title + " · " + book.names.get(at[0])); });
        explain.setGraphic(DeskUi.icon("sparkle", 16));
        selBar.getChildren().addAll(DeskMarks.dots(mark), new Separator(javafx.geometry.Orientation.VERTICAL), note, copy, listen, explain);
        web.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            Object sel; try { sel = web.getEngine().executeScript("window.getSelection().toString()"); } catch (Exception x) { sel = null; }
            String s = sel == null ? "" : sel.toString().trim();
            if (s.length() < 2) { selBar.setVisible(false); selected[0] = ""; return; }
            selected[0] = s.length() > 4000 ? s.substring(0, 4000) : s;
            Point2D p = holder.sceneToLocal(e.getSceneX(), e.getSceneY());
            selBar.setVisible(true); selBar.applyCss(); selBar.autosize();
            double w = selBar.getWidth(), h = selBar.getHeight();
            double x = Math.max(8, Math.min(p.getX() - w / 2, holder.getWidth() - w - 8)), y = p.getY() + 18;
            if (y + h > holder.getHeight() - 8) y = p.getY() - h - 18;
            selBar.relocate(x, Math.max(8, y));
        });
        web.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> selBar.setVisible(false));
        web.getEngine().setOnAlert(e -> { String m = e.getData(); if (m != null && m.startsWith("seekymark:")) { try { DeskMarks.menu(d, Long.parseLong(m.substring(10)), redraw); } catch (NumberFormatException ignored) {} } });
        web.getEngine().locationProperty().addListener((o, a, url) -> { if (url != null && url.startsWith("seeky:")) { BibleReferences.Reference r = BibleReferences.lookup(url.substring(6).replace("%20", " ")); if (r != null) d.notes.passage(r); redraw.run(); } });
        toc.setOnAction(e -> { int i = toc.getSelectionModel().getSelectedIndex(); if (i >= 0 && i != at[0]) { at[0] = i; draw[0].run(); } });
        Runnable prev = () -> { if (at[0] > 0) { at[0]--; draw[0].run(); } }, next = () -> { if (at[0] < book.texts.size() - 1) { at[0]++; draw[0].run(); } };
        java.util.function.Supplier<String> place = () -> book.names.get(at[0]);
        HBox top = bar(book.title, where, prev, next, () -> DeskSpeech.toggle(d, book.texts.get(at[0])), toc);
        top.getChildren().addAll(aiMenu(book.title, place, () -> book.texts.get(at[0]), () -> selected[0]), noteButton(book.title, place));
        VBox page = new VBox(10, top, holder); page.setPadding(new javafx.geometry.Insets(14, 18, 10, 18)); page.getStyleClass().add("page");
        d.show(page);
        if (showMark > 0) afterLoad.add(() -> web.getEngine().executeScript("var e=document.getElementById('m" + showMark + "'); if(e){e.scrollIntoView({block:'center'}); e.style.outline='3px solid " + DeskThemes.hex(DeskThemes.current.accent) + "';}"));
        draw[0].run();
    }
}
