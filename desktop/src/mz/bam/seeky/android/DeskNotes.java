package mz.bam.seeky.android;

import javafx.animation.PauseTransition;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;

/** Bloco de notas: the same notes format as Android (headings, styles, colours, lists, tasks, tables, pictures, passages). */
final class DeskNotes {
    final DeskMain d;
    String filter = "";
    DeskNotes(DeskMain d) { this.d = d; }
    static File media() { return DeskStore.folder("notepad-media"); }

    Node view() {
        VBox page = DeskUi.page();
        TextField search = new TextField(filter); search.setPromptText("Pesquisar nas notas"); HBox.setHgrow(search, Priority.ALWAYS);
        page.getChildren().add(DeskUi.row(DeskUi.title("Bloco de notas"), DeskUi.grow(), transferMenu(), DeskUi.button("Modelos", false, this::templates), DeskUi.button("Nova nota", true, () -> edit(0, "", ""))));
        page.getChildren().add(search);
        VBox list = new VBox(10); page.getChildren().add(list);
        Runnable draw = () -> { filter = search.getText(); fill(list); };
        search.textProperty().addListener((o, a, b) -> draw.run());
        draw.run();
        return DeskUi.scroll(page);
    }
    void fill(VBox list) {
        list.getChildren().clear();
        List<Object[]> rows; try { rows = d.store.rows("SELECT id,title,body,folder,pinned,updated FROM notes ORDER BY pinned DESC,updated DESC"); } catch (SQLException e) { list.getChildren().add(DeskUi.muted(e.getMessage())); return; }
        String q = BibleGame.plain(filter.trim()); int shown = 0;
        for (Object[] r : rows) {
            String title = NotepadText.title(r[1] + "", r[2] + ""), body = r[2] + "";
            if (!q.isEmpty() && !BibleGame.plain(title + " " + body + " " + r[3]).contains(q)) continue;
            shown++;
            int[] tasks = NotepadText.tasks(body);
            String sub = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(((Number) r[5]).longValue())) + (r[3].toString().isEmpty() ? "" : " · " + r[3]) + (tasks[1] > 0 ? " · ☑ " + tasks[0] + "/" + tasks[1] : "") + (((Number) r[4]).intValue() == 1 ? " · fixada" : "");
            String preview = NotepadText.plain(body).replaceAll("\\s+", " ").trim(); if (preview.length() > 160) preview = preview.substring(0, 160) + "…";
            long id = ((Number) r[0]).longValue();
            VBox card = DeskUi.card(DeskUi.heading(title), DeskUi.muted(preview.isEmpty() ? "Nota vazia" : preview), DeskUi.muted(sub)); card.getStyleClass().add("tile");
            card.setOnMouseClicked(e -> edit(id, r[1] + "", body));
            list.getChildren().add(card);
        }
        if (shown == 0) list.getChildren().add(DeskUi.card(DeskUi.heading(rows.isEmpty() ? "Comece o seu bloco de notas" : "Nenhuma nota encontrada"), DeskUi.text("Escreva ideias, sermões, estudos e tarefas, com títulos, cores, listas e tabelas. Passagens como João 3:16 são reconhecidas.")));
    }
    /** Notas entre o telemóvel e o computador (.seekynotes). */
    MenuButton transferMenu() {
        MenuButton m = new MenuButton("Telemóvel");
        MenuItem in = new MenuItem("Importar notas do telemóvel…"); in.setOnAction(e -> importNotes());
        MenuItem out = new MenuItem("Enviar notas para o telemóvel…"); out.setOnAction(e -> exportNotes());
        m.getItems().addAll(in, out); return m;
    }
    void exportNotes() {
        FileChooser fc = new FileChooser(); fc.setTitle("Enviar notas para o telemóvel"); fc.setInitialFileName(NotesTransfer.fileName());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Notas do Seeky", "*" + NotesTransfer.EXTENSION)); File f = fc.showSaveDialog(d.stage); if (f == null) return;
        d.run("A preparar as notas…", () -> { int n = exportTo(d.store, f); return n; }, n -> DeskUi.info(n + (n == 1 ? " nota pronta" : " notas prontas"), "Passe o ficheiro para o telemóvel (cabo, email, Drive, WhatsApp…) e abra-o no Seeky: Bloco de notas → Pastas → Importar notas do computador."));
    }
    static int exportTo(DeskStore store, File f) throws Exception {
        List<NotesTransfer.Item> items = new ArrayList<>();
        for (Object[] r : store.rows("SELECT title,body,folder,pinned,book,created,updated FROM notes ORDER BY updated DESC")) {
            NotesTransfer.Item i = new NotesTransfer.Item(); i.title = r[0] + ""; i.body = r[1] + ""; i.folder = r[2] + ""; i.pinned = ((Number) r[3]).intValue() == 1; i.source = r[4] + ""; i.created = ((Number) r[5]).longValue(); i.updated = ((Number) r[6]).longValue();
            items.add(i);
        }
        byte[] data = NotesTransfer.write(items, name -> { File m = new File(media(), name); try { return m.isFile() && m.length() <= NotesTransfer.MAX_MEDIA ? Files.readAllBytes(m.toPath()) : null; } catch (IOException e) { return null; } }, "windows");
        Files.write(f.toPath(), data); return items.size();
    }
    void importNotes() {
        FileChooser fc = new FileChooser(); fc.setTitle("Importar notas do telemóvel");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Notas do Seeky", "*" + NotesTransfer.EXTENSION, "*.json"), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        File f = fc.showOpenDialog(d.stage); if (f == null) return;
        d.run("A importar as notas…", () -> importFrom(d.store, f), r -> { DeskUi.info(r[0] + (r[0] == 1 ? " nota importada" : " notas importadas"), r[1] > 0 ? r[1] + (r[1] == 1 ? " já existia e não foi repetida." : " já existiam e não foram repetidas.") : "Estão no Bloco de notas."); d.open("notes"); });
    }
    /** {added, skipped} */
    static int[] importFrom(DeskStore store, File f) throws Exception {
        if (f.length() > NotesTransfer.MAX_FILE) throw new IOException("Ficheiro de notas acima de 64 MB.");
        NotesTransfer.Parsed parsed = NotesTransfer.read(Files.readAllBytes(f.toPath()));
        Set<String> have = new HashSet<>(); for (Object[] r : store.rows("SELECT title,body FROM notes")) have.add(NotesTransfer.key(r[0] + "", r[1] + ""));
        Set<String> taken = new HashSet<>(); String[] names = media().list(); if (names != null) taken.addAll(Arrays.asList(names));
        Map<String, String> renamed = new HashMap<>(); int added = 0, skipped = 0;
        for (NotesTransfer.Item i : parsed.notes) {
            if (!have.add(NotesTransfer.key(i.title, i.body))) { skipped++; continue; }
            String body = i.body;
            for (String name : NotesTransfer.media(body)) {
                byte[] bytes = parsed.media.get(name); if (bytes == null) continue;
                String target = renamed.get(name);
                if (target == null) {
                    File m = new File(media(), name);
                    if (m.isFile() && !Arrays.equals(Files.readAllBytes(m.toPath()), bytes)) { target = NotesTransfer.fresh(name, taken); m = new File(media(), target); } else target = name;
                    if (!m.isFile()) Files.write(m.toPath(), bytes);
                    taken.add(target); renamed.put(name, target);
                }
                if (!target.equals(name)) body = NotesTransfer.rename(body, name, target);
            }
            store.update("INSERT INTO notes(title,body,folder,pinned,book,created,updated) VALUES(?,?,?,?,?,?,?)", i.title, body, i.folder, i.pinned ? 1 : 0, i.source, i.created, i.updated);
            added++;
        }
        return new int[]{added, skipped};
    }
    void templates() {
        ChoiceDialog<String> c = new ChoiceDialog<>(); for (String[] t : NotepadText.TEMPLATES) c.getItems().add(t[0]); c.setSelectedItem(c.getItems().get(0)); c.setHeaderText("Modelos"); c.setTitle("Seeky"); DeskUi.style(c.getDialogPane());
        c.showAndWait().ifPresent(name -> { for (String[] t : NotepadText.TEMPLATES) if (t[0].equals(name)) edit(0, "", t[1]); });
    }
    void newWith(String title, String body) { d.open("notes"); edit(0, title, body); }

    /** The page script of the editor (resources/notes-editor.js). */
    static String editorScript() {
        try (InputStream in = DeskNotes.class.getResourceAsStream("/notes-editor.js")) { return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { return ""; }
    }
    /**
     * 5.57 · The editor is one page: what you write is formatted as you write it and is the final note (no
     * separate text with marks, no preview). The note is still kept as Seeky's marks, so it is the same on the
     * phone, in Word and in PDF. Passages (João 3:16…) are marked as you write; a click shows them over the page.
     */
    void edit(long id, String title0, String body0) {
        long[] note = {id};
        TextField title = new TextField(title0); title.setPromptText("Título"); title.getStyleClass().add("note-title");
        TextArea source = new TextArea(body0); // the note's marks: saved, exported and given to Seeky IA (not shown)
        boolean[] fromPage = {false};
        WebView page = new WebView(); page.setContextMenuEnabled(false); VBox.setVgrow(page, Priority.ALWAYS);
        javafx.scene.web.WebEngine engine = page.getEngine();
        Label info = DeskUi.muted(""); String[] saved = {""};
        FlowPane passages = new FlowPane(6, 6);
        Runnable save = () -> {
            String t = title.getText(), b = source.getText(); if (note[0] == 0 && t.trim().isEmpty() && b.trim().isEmpty()) return;
            long now = System.currentTimeMillis();
            try {
                if (note[0] == 0) note[0] = d.store.update("INSERT INTO notes(title,body,created,updated) VALUES(?,?,?,?)", t, b, now, now);
                else d.store.update("UPDATE notes SET title=?,body=?,updated=? WHERE id=?", t, b, now, note[0]);
                saved[0] = "Guardada às " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(now));
            } catch (SQLException e) { d.status(e.getMessage()); }
        };
        String[] lastRefs = {null};
        Runnable refresh = () -> {
            String text = source.getText(); int[] t = NotepadText.tasks(text);
            info.setText(NotepadText.words(text) + " palavras · ~" + NotepadText.minutes(text) + " min" + (t[1] > 0 ? " · ☑ " + t[0] + "/" + t[1] : "") + (saved[0].isEmpty() ? "" : " · " + saved[0]));
            Set<String> seen = new LinkedHashSet<>(); List<BibleReferences.Reference> found = new ArrayList<>();
            for (BibleReferences.Reference r : BibleReferences.references(title.getText() + "\n" + NotepadText.plain(text))) if (seen.add(r.title)) found.add(r);
            String key = String.join("|", seen); if (key.equals(lastRefs[0])) return; lastRefs[0] = key;
            passages.getChildren().clear();
            if (!found.isEmpty()) passages.getChildren().add(DeskUi.muted("Passagens nesta nota:"));
            for (BibleReferences.Reference r : found) { Button b = DeskUi.button(r.title, false, () -> DeskPassage.show(d, r)); b.setGraphic(DeskUi.icon("book", 14)); passages.getChildren().add(b); }
        };
        DeskNotesBridge bridge = new DeskNotesBridge(markup -> { fromPage[0] = true; source.setText(markup); fromPage[0] = false; save.run(); refresh.run(); });
        page.setUserData(bridge); // the page only holds a weak reference
        String script = editorScript();
        engine.getLoadWorker().stateProperty().addListener((o, a, st) -> {
            if (st != javafx.concurrent.Worker.State.SUCCEEDED) return;
            ((netscape.javascript.JSObject) engine.executeScript("window")).setMember("seeky", bridge);
            engine.executeScript(script);
        });
        engine.setOnAlert(e -> DeskPassage.alert(d, e.getData()));
        Runnable load = () -> engine.loadContent(DeskNotesHtml.editor(source.getText(), media()));
        // Seeky IA (or a passage) changed the marks: show the note again.
        source.textProperty().addListener((o, a, b) -> { if (!fromPage[0]) { load.run(); save.run(); refresh.run(); } });
        PauseTransition later = new PauseTransition(Duration.millis(500));
        later.setOnFinished(e -> { save.run(); refresh.run(); });
        title.textProperty().addListener((o, a, b) -> later.playFromStart());
        /** The latest marks straight from the page (typing saves half a second later). */
        Runnable sync = () -> { try { Object m = engine.executeScript("window.markup ? window.markup() : null"); if (m instanceof String && !m.equals(source.getText())) { fromPage[0] = true; source.setText((String) m); fromPage[0] = false; save.run(); } } catch (Exception ignored) {} };
        java.util.function.BiConsumer<String, String> cmd = (name, value) -> { try { ((netscape.javascript.JSObject) engine.executeScript("window")).call("cmd", name, value == null ? "" : value); } catch (Exception ignored) {} page.requestFocus(); };
        // The toolbar: applies to what is selected, as in a word processor.
        FlowPane tools = new FlowPane(6, 6); tools.getStyleClass().add("note-tools"); // wraps instead of hiding buttons
        java.util.function.Function<String[], Button> tool = spec -> { Button b = new Button(spec[0]); b.setTooltip(new Tooltip(spec[1])); b.setFocusTraversable(false); b.setOnAction(e -> cmd.accept(spec[2], spec.length > 3 ? spec[3] : "")); tools.getChildren().add(b); return b; };
        tool.apply(new String[]{"B", "Negrito (Ctrl+B)", "bold"}).setStyle("-fx-font-weight: bold;");
        tool.apply(new String[]{"I", "Itálico (Ctrl+I)", "italic"}).setStyle("-fx-font-style: italic;");
        tool.apply(new String[]{"U", "Sublinhado (Ctrl+U)", "underline"}).setStyle("-fx-underline: true;");
        tool.apply(new String[]{"S", "Riscado", "strikeThrough"}).setStyle("-fx-strikethrough: true;");
        tool.apply(new String[]{"Destacar", "Destacar a amarelo", "mark"});
        MenuButton color = new MenuButton("Cor"); color.setFocusTraversable(false);
        for (int i = 0; i < NotepadDoc.COLOR_KEYS.length; i++) { int c = NotepadDoc.COLOR_VALUES[i]; String hex = String.format(Locale.ROOT, "#%06x", c & 0xffffff); javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(7, javafx.scene.paint.Color.web(hex)); MenuItem m = new MenuItem(NotepadDoc.COLOR_NAMES[i], dot); m.setOnAction(e -> cmd.accept("color", hex)); color.getItems().add(m); }
        MenuItem noColor = new MenuItem("Sem cor"); noColor.setOnAction(e -> cmd.accept("color", DeskThemes.hex(DeskThemes.current.ink))); color.getItems().add(noColor);
        tools.getChildren().addAll(color, gap());
        tool.apply(new String[]{"Texto", "Texto normal", "block", "p"});
        for (int h = 1; h <= 3; h++) tool.apply(new String[]{"H" + h, "Título " + h, "block", "h" + h});
        tools.getChildren().add(gap());
        tool.apply(new String[]{"• Lista", "Lista com pontos", "list", "ul"});
        tool.apply(new String[]{"1. Lista", "Lista numerada", "list", "ol"});
        tool.apply(new String[]{"☐ Tarefa", "Lista de tarefas (clique na caixa para marcar)", "list", "task"});
        tool.apply(new String[]{"❝ Citação", "Citação", "block", "blockquote"});
        tools.getChildren().add(gap());
        tool.apply(new String[]{"⇤", "Alinhar à esquerda", "justifyLeft"});
        tool.apply(new String[]{"≡", "Centrar", "justifyCenter"});
        tool.apply(new String[]{"⇥", "Alinhar à direita", "justifyRight"});
        tool.apply(new String[]{"→", "Aumentar o recuo", "indent"});
        tool.apply(new String[]{"←", "Diminuir o recuo", "outdent"});
        tools.getChildren().add(gap());
        Button table = new Button("Tabela"); table.setFocusTraversable(false); table.setOnAction(e -> cmd.accept("html", "<table><tr><th>Coluna 1</th><th>Coluna 2</th><th>Coluna 3</th></tr><tr><td><br></td><td><br></td><td><br></td></tr><tr><td><br></td><td><br></td><td><br></td></tr></table><p><br></p>"));
        Button line = new Button("Linha"); line.setFocusTraversable(false); line.setOnAction(e -> cmd.accept("insertHorizontalRule", ""));
        Button picture = new Button("Imagem"); picture.setFocusTraversable(false); picture.setOnAction(e -> insertImage(cmd));
        Button verse = new Button("Passagem"); verse.setFocusTraversable(false); verse.setOnAction(e -> insertPassage(cmd));
        tools.getChildren().addAll(table, line, picture, verse);
        // Actions
        Button back = new Button(); back.setGraphic(DeskUi.icon("back", 20)); back.getStyleClass().add("round"); back.setOnAction(e -> { later.stop(); sync.run(); save.run(); d.open("notes"); });
        MenuButton more = new MenuButton("Mais");
        MenuItem word = new MenuItem("Exportar Word (.docx)"); word.setOnAction(e -> { sync.run(); export(title.getText(), source.getText(), "docx"); });
        MenuItem pdf = new MenuItem("Exportar PDF"); pdf.setOnAction(e -> { sync.run(); export(title.getText(), source.getText(), "pdf"); });
        MenuItem md = new MenuItem("Exportar Markdown (.md)"); md.setOnAction(e -> { sync.run(); export(title.getText(), source.getText(), "md"); });
        MenuItem listen = new MenuItem("Ouvir a nota"); listen.setOnAction(e -> { sync.run(); DeskSpeech.toggle(d, NotepadText.plain(title.getText() + "\n" + source.getText())); });
        MenuItem pin = new MenuItem("Fixar / desafixar"); pin.setOnAction(e -> { sync.run(); save.run(); try { d.store.update("UPDATE notes SET pinned=1-pinned WHERE id=?", note[0]); } catch (SQLException ignored) {} });
        MenuItem folder = new MenuItem("Mover para pasta…"); folder.setOnAction(e -> { sync.run(); save.run(); String f = DeskUi.ask("Pasta", "Nome da pasta (vazio para nenhuma)", ""); try { d.store.update("UPDATE notes SET folder=? WHERE id=?", f == null ? "" : f, note[0]); } catch (SQLException ignored) {} });
        MenuItem delete = new MenuItem("Apagar"); delete.setOnAction(e -> { if (note[0] != 0 && DeskUi.confirm("Apagar esta nota?", "Não pode ser desfeito.")) { try { d.store.update("DELETE FROM notes WHERE id=?", note[0]); DeskSync.deleted(d.store, note[0]); } catch (SQLException ignored) {} later.stop(); d.open("notes"); } });
        more.getItems().addAll(word, pdf, md, new SeparatorMenuItem(), listen, pin, folder, new SeparatorMenuItem(), delete);
        Button ai = new Button("Seeky IA", DeskUi.icon("sparkle", 18)); ai.getStyleClass().add("primary"); ai.setOnAction(e -> { sync.run(); DeskAi.menu(d, source, title, ai); });
        HBox top = DeskUi.row(back, title, ai, more); HBox.setHgrow(title, Priority.ALWAYS);
        VBox card = new VBox(page); card.getStyleClass().add("note-page"); VBox.setVgrow(card, Priority.ALWAYS);
        VBox view = new VBox(10, top, tools, card, passages, info); view.setPadding(new javafx.geometry.Insets(18, 22, 14, 22)); view.getStyleClass().add("page");
        d.show(view); load.run(); refresh.run();
        javafx.application.Platform.runLater(() -> { if (title0.isEmpty() && body0.isEmpty()) title.requestFocus(); else page.requestFocus(); });
    }
    void passage(BibleReferences.Reference r) { DeskPassage.show(d, r); }
    static Region gap() { Region r = new Region(); r.setPrefWidth(8); return r; }
    void insertPassage(java.util.function.BiConsumer<String, String> cmd) {
        String q = DeskUi.ask("Inserir passagem", "Por exemplo: João 3:16-18 ou Salmo 23", ""); if (q == null) return;
        BibleReferences.Reference r = BibleReferences.lookup(q); if (r == null) { DeskUi.info("Passagem não reconhecida", "Escreva o livro, o capítulo e o versículo."); return; }
        String text = ""; try { text = d.store.passage(d.store.bible(), r).replaceAll("\\[\\d+\\] ", ""); } catch (SQLException ignored) {}
        String html = "<blockquote>" + DeskNotesHtml.esc(r.title) + "</blockquote>" + (text.isEmpty() ? "" : "<blockquote>«" + DeskNotesHtml.esc(text.trim().replaceAll("\\s+", " ")) + "»</blockquote>") + "<p><br></p>";
        cmd.accept("html", html);
    }
    void insertImage(java.util.function.BiConsumer<String, String> cmd) {
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
        File f = fc.showOpenDialog(d.stage); if (f == null) return;
        try {
            String ext = f.getName().replaceFirst(".*\\.", "").toLowerCase(Locale.ROOT); String name = "img_" + System.currentTimeMillis() + "." + ext; File copy = new File(media(), name); Files.copy(f.toPath(), copy.toPath());
            cmd.accept("html", "<p><img data-ref=\"" + name + "\" data-label=\"Imagem\" src=\"" + copy.toURI() + "\" alt=\"Imagem\"></p><p><br></p>");
        } catch (IOException e) { DeskUi.info("Não foi possível usar a imagem", e.getMessage()); }
    }
    void export(String title, String body, String kind) {
        String name = NotepadText.title(title, body).replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        FileChooser fc = new FileChooser(); fc.setInitialFileName(name + "." + kind); File f = fc.showSaveDialog(d.stage); if (f == null) return;
        d.run("A exportar…", () -> {
            try (OutputStream out = new FileOutputStream(f)) {
                if (kind.equals("docx")) out.write(NotepadExport.docx(NotepadText.title(title, body), body, DeskNotes::image, 0));
                else if (kind.equals("pdf")) DeskPdf.note(NotepadText.title(title, body), body, media(), out);
                else out.write(NotepadText.markdown(title, body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return f;
        }, file -> d.status("Nota exportada: " + file.getPath()));
    }
    /** Pictures for the Word file: {bytes, width, height, "png"/"jpeg"}. */
    static Object[] image(String ref) {
        try {
            File f = new File(media(), ref); if (!f.isFile()) return null;
            byte[] data = Files.readAllBytes(f.toPath());
            javafx.scene.image.Image img = new javafx.scene.image.Image(new ByteArrayInputStream(data));
            String type = ref.toLowerCase(Locale.ROOT).endsWith(".png") ? "png" : "jpeg";
            if (!type.equals("png") && !ref.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g")) return null;
            return new Object[]{data, (int) img.getWidth(), (int) img.getHeight(), type};
        } catch (Exception e) { return null; }
    }
}
