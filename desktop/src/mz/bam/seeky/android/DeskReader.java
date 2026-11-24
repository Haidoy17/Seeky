package mz.bam.seeky.android;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
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

/** Livros: a library with progress; PDF pages, EPUB/Word/FB2/MOBI/ODT/RTF chapters and text files. */
final class DeskReader {
    final DeskMain d;
    DeskReader(DeskMain d) { this.d = d; }
    static final String[] TYPES = {"*.pdf", "*.epub", "*.docx", "*.txt", "*.md", "*.fb2", "*.mobi", "*.azw3", "*.prc", "*.odt", "*.rtf", "*.mht", "*.mhtml", "*.cbz"};

    VBox recentCard() {
        try {
            List<Object[]> r = d.store.rows("SELECT path,title,position,total FROM books ORDER BY opened DESC LIMIT 1"); if (r.isEmpty()) return null;
            Object[] b = r.get(0); int total = ((Number) b[3]).intValue(), pos = ((Number) b[2]).intValue();
            return DeskUi.card(DeskUi.heading("Continuar a ler"), DeskUi.text(b[1] + ""), DeskUi.muted(total > 0 ? (pos + 1) + " de " + total : ""), DeskUi.button("Abrir", true, () -> open(new File(b[0] + ""))));
        } catch (SQLException e) { return null; }
    }
    Node view() {
        VBox page = DeskUi.page(DeskUi.row(DeskUi.title("Livros"), DeskUi.grow(), DeskUi.button("Abrir livro", true, this::choose)));
        List<Object[]> books; try { books = d.store.rows("SELECT path,title,kind,position,total FROM books ORDER BY opened DESC LIMIT 200"); } catch (SQLException e) { books = new ArrayList<>(); }
        if (books.isEmpty()) page.getChildren().add(DeskUi.card(DeskUi.heading("A sua estante está vazia"), DeskUi.text("Abra um livro do computador: PDF, EPUB, Word, TXT, FB2, MOBI, AZW3, ODT, RTF ou CBZ. Fica aqui com o ponto onde parou."), DeskUi.button("Abrir livro", true, this::choose)));
        FlowPane shelf = new FlowPane(14, 14);
        for (Object[] b : books) {
            File f = new File(b[0] + ""); int total = ((Number) b[4]).intValue(), pos = ((Number) b[3]).intValue();
            ProgressBar p = new ProgressBar(total > 0 ? (pos + 1.0) / total : 0); p.setMaxWidth(Double.MAX_VALUE);
            VBox card = DeskUi.card(DeskUi.icon("reader", 30), DeskUi.heading(b[1] + ""), DeskUi.muted((b[2] + "").toUpperCase(Locale.ROOT) + (f.exists() ? "" : " · ficheiro em falta")), p);
            card.setPrefWidth(230); card.getStyleClass().add("tile"); card.setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) forget(f); else open(f); });
            shelf.getChildren().add(card);
        }
        page.getChildren().add(shelf);
        if (!books.isEmpty()) page.getChildren().add(DeskUi.muted("Clique para continuar. Botão direito para tirar da estante."));
        return DeskUi.scroll(page);
    }
    void forget(File f) { if (DeskUi.confirm("Tirar da estante?", f.getName() + "\nO ficheiro não é apagado.")) { try { d.store.update("DELETE FROM books WHERE path=?", f.getPath()); } catch (SQLException ignored) {} d.open("reader"); } }
    void choose() {
        FileChooser fc = new FileChooser(); fc.setTitle("Abrir livro"); fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Livros", TYPES), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        File f = fc.showOpenDialog(d.stage); if (f != null) open(f);
    }
    static boolean accepts(String name) { String n = name.toLowerCase(Locale.ROOT); for (String t : TYPES) if (n.endsWith(t.substring(1))) return true; return false; }

    void open(File f) {
        if (!f.isFile()) { DeskUi.info("Livro indisponível", f.getPath()); return; }
        String name = f.getName().toLowerCase(Locale.ROOT);
        int pos = 0; try { pos = (int) d.store.count("SELECT position FROM books WHERE path=?", f.getPath()); } catch (SQLException ignored) {}
        final int start = pos;
        if (name.endsWith(".pdf")) d.run("A abrir " + f.getName() + "…", () -> PDDocument.load(f), doc -> pdf(f, doc, start));
        else d.run("A abrir " + f.getName() + "…", () -> chapters(f), book -> text(f, book, start));
    }
    void remember(File f, String title, String kind, int position, int total) {
        try { d.store.update("INSERT OR REPLACE INTO books(path,title,kind,position,total,opened) VALUES(?,?,?,?,?,?)", f.getPath(), title, kind, position, total, System.currentTimeMillis()); } catch (SQLException ignored) {}
    }
    Node bar(File f, String title, Label where, Runnable prev, Runnable next, String text) {
        Button back = new Button(); back.setGraphic(DeskUi.icon("back", 20)); back.getStyleClass().add("round"); back.setOnAction(e -> d.open("reader"));
        Label t = DeskUi.heading(title); HBox.setHgrow(t, Priority.ALWAYS); t.setMaxWidth(Double.MAX_VALUE);
        return DeskUi.row(back, t, DeskUi.button("‹", false, prev), where, DeskUi.button("›", false, next),
            DeskUi.iconButton("speaker", "Ouvir", () -> DeskSpeech.toggle(d, text == null ? "" : text)),
            DeskUi.iconButton("note", "Nota deste livro", () -> d.notes.newWith(title + " — " + where.getText(), "> " + title + " · " + where.getText() + "\n\n")));
    }

    // ---- PDF ----
    void pdf(File f, PDDocument doc, int start) {
        PDFRenderer render = new PDFRenderer(doc); int pages = doc.getNumberOfPages();
        String title = Optional.ofNullable(doc.getDocumentInformation().getTitle()).filter(s -> !s.trim().isEmpty()).orElse(f.getName().replaceFirst("\\.pdf$", ""));
        int[] at = {Math.min(Math.max(0, start), Math.max(0, pages - 1))}; double[] zoom = {1.4};
        ImageView view = new ImageView(); view.setPreserveRatio(true);
        Label where = DeskUi.muted("");
        StackPane holder = new StackPane(view); ScrollPane scroll = new ScrollPane(holder); scroll.setFitToWidth(true); scroll.setPannable(true); VBox.setVgrow(scroll, Priority.ALWAYS);
        String[] pageText = {""};
        Runnable draw = () -> {
            where.setText((at[0] + 1) + " / " + pages); remember(f, title, "pdf", at[0], pages);
            int p = at[0];
            d.run(null, () -> { synchronized (doc) { return render.renderImageWithDPI(p, (float) (96 * zoom[0])); } }, img -> { if (p == at[0]) { view.setImage(SwingFXUtils.toFXImage(img, null)); scroll.setVvalue(0); } });
            d.worker.execute(() -> { try { org.apache.pdfbox.text.PDFTextStripper s = new org.apache.pdfbox.text.PDFTextStripper(); s.setStartPage(p + 1); s.setEndPage(p + 1); synchronized (doc) { pageText[0] = s.getText(doc); } } catch (Exception ignored) {} });
        };
        Runnable prev = () -> { if (at[0] > 0) { at[0]--; draw.run(); } }, next = () -> { if (at[0] < pages - 1) { at[0]++; draw.run(); } };
        Slider zoomBar = new Slider(0.6, 3, zoom[0]); zoomBar.setPrefWidth(140); zoomBar.valueChangingProperty().addListener((o, a, b) -> { if (!b) { zoom[0] = zoomBar.getValue(); draw.run(); } });
        HBox top = (HBox) bar(f, title, where, prev, next, null);
        ((Button) top.getChildren().get(5)).setOnAction(e -> DeskSpeech.toggle(d, pageText[0]));
        top.getChildren().add(2, DeskUi.muted("Zoom")); top.getChildren().add(3, zoomBar);
        VBox page = new VBox(10, top, scroll); page.setPadding(new javafx.geometry.Insets(14, 18, 10, 18)); page.getStyleClass().add("page");
        page.setOnKeyPressed(e -> { switch (e.getCode()) { case RIGHT: case PAGE_DOWN: next.run(); break; case LEFT: case PAGE_UP: prev.run(); break; default: } });
        d.show(page); page.requestFocus(); draw.run();
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
    void text(File f, Book book, int start) {
        int[] at = {Math.min(Math.max(0, start), book.texts.size() - 1)};
        WebView web = new WebView(); VBox.setVgrow(web, Priority.ALWAYS);
        Label where = DeskUi.muted("");
        ComboBox<String> toc = new ComboBox<>(); toc.getItems().addAll(book.names); toc.setPrefWidth(220);
        Runnable draw = () -> {
            where.setText((at[0] + 1) + " / " + book.texts.size()); remember(f, book.title, book.kind, at[0], book.texts.size()); toc.getSelectionModel().select(at[0]);
            boolean dark = d.root.getStyleClass().contains("dark");
            StringBuilder h = new StringBuilder("<html><head><meta charset='utf-8'><style>body{font-family:Georgia,'Segoe UI',serif;font-size:19px;line-height:1.65;max-width:760px;margin:24px auto;padding:0 24px;")
                .append(dark ? "background:#1f1f1f;color:#e9e5dc" : "background:#fbf8f2;color:#26241f").append("}h2{font-family:'Segoe UI',sans-serif}</style></head><body><h2>").append(DeskNotesHtml.esc(book.names.get(at[0]))).append("</h2>");
            for (String para : book.texts.get(at[0]).split("\\n\\s*\\n|\\n")) if (!para.trim().isEmpty()) h.append("<p>").append(DeskNotesHtml.linkPassages(DeskNotesHtml.esc(para.trim()))).append("</p>");
            web.getEngine().loadContent(h.append("</body></html>").toString());
        };
        web.getEngine().locationProperty().addListener((o, a, url) -> { if (url != null && url.startsWith("seeky:")) { BibleReferences.Reference r = BibleReferences.lookup(url.substring(6).replace("%20", " ")); if (r != null) d.notes.passage(r); draw.run(); } });
        toc.setOnAction(e -> { int i = toc.getSelectionModel().getSelectedIndex(); if (i >= 0 && i != at[0]) { at[0] = i; draw.run(); } });
        Runnable prev = () -> { if (at[0] > 0) { at[0]--; draw.run(); } }, next = () -> { if (at[0] < book.texts.size() - 1) { at[0]++; draw.run(); } };
        HBox top = (HBox) bar(f, book.title, where, prev, next, null);
        ((Button) top.getChildren().get(5)).setOnAction(e -> DeskSpeech.toggle(d, book.texts.get(at[0])));
        top.getChildren().add(2, toc);
        VBox page = new VBox(10, top, web); page.setPadding(new javafx.geometry.Insets(14, 18, 10, 18)); page.getStyleClass().add("page");
        d.show(page); draw.run();
    }
}
