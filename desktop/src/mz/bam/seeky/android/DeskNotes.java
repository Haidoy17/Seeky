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
        page.getChildren().add(DeskUi.row(DeskUi.title("Bloco de notas"), DeskUi.grow(), DeskUi.button("Modelos", false, this::templates), DeskUi.button("Nova nota", true, () -> edit(0, "", ""))));
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
    void templates() {
        ChoiceDialog<String> c = new ChoiceDialog<>(); for (String[] t : NotepadText.TEMPLATES) c.getItems().add(t[0]); c.setSelectedItem(c.getItems().get(0)); c.setHeaderText("Modelos"); c.setTitle("Seeky"); DeskUi.style(c.getDialogPane());
        c.showAndWait().ifPresent(name -> { for (String[] t : NotepadText.TEMPLATES) if (t[0].equals(name)) edit(0, "", t[1]); });
    }
    void newWith(String title, String body) { d.open("notes"); edit(0, title, body); }

    /** The editor: text with Seeky's marks on the left, the formatted note on the right. */
    void edit(long id, String title0, String body0) {
        long[] note = {id};
        TextField title = new TextField(title0); title.setPromptText("Título"); title.getStyleClass().add("note-title");
        TextArea body = new TextArea(body0); body.setWrapText(true); body.getStyleClass().add("note-body"); body.setPromptText("Escreva aqui… Passagens como João 3:16 são reconhecidas.");
        WebView preview = new WebView(); preview.setContextMenuEnabled(false);
        Label info = DeskUi.muted("");
        FlowPane passages = new FlowPane(6, 6);
        PauseTransition later = new PauseTransition(Duration.millis(500));
        Runnable refresh = () -> {
            boolean dark = d.root.getStyleClass().contains("dark");
            preview.getEngine().loadContent(DeskNotesHtml.html(title.getText(), body.getText(), media(), dark, 0));
            int[] t = NotepadText.tasks(body.getText());
            info.setText(NotepadText.words(body.getText()) + " palavras · ~" + NotepadText.minutes(body.getText()) + " min" + (t[1] > 0 ? " · ☑ " + t[0] + "/" + t[1] : ""));
            passages.getChildren().clear(); Set<String> seen = new LinkedHashSet<>();
            for (BibleReferences.Reference r : BibleReferences.references(title.getText() + "\n" + body.getText())) if (seen.add(r.title)) passages.getChildren().add(DeskUi.button(r.title, false, () -> passage(r)));
        };
        Runnable save = () -> {
            String t = title.getText(), b = body.getText(); if (note[0] == 0 && t.trim().isEmpty() && b.trim().isEmpty()) return;
            long now = System.currentTimeMillis();
            try {
                if (note[0] == 0) note[0] = d.store.update("INSERT INTO notes(title,body,created,updated) VALUES(?,?,?,?)", t, b, now, now);
                else d.store.update("UPDATE notes SET title=?,body=?,updated=? WHERE id=?", t, b, now, note[0]);
                d.status("Nota guardada · " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(now)));
            } catch (SQLException e) { d.status(e.getMessage()); }
        };
        later.setOnFinished(e -> { refresh.run(); save.run(); });
        body.textProperty().addListener((o, a, b) -> later.playFromStart()); title.textProperty().addListener((o, a, b) -> later.playFromStart());
        preview.getEngine().locationProperty().addListener((o, a, url) -> { if (url != null && url.startsWith("seeky:")) { BibleReferences.Reference r = BibleReferences.lookup(url.substring(6).replace("%20", " ")); if (r != null) passage(r); refresh.run(); } });
        // Formatting
        ToolBar tools = new ToolBar();
        Object[][] marks = {{"B", "**"}, {"I", "*"}, {"U", "__"}, {"S", "~~"}, {"Destacar", "=="}};
        for (Object[] m : marks) { Button b = new Button((String) m[0]); b.setOnAction(e -> apply(body, NotepadText.wrap(body.getText(), body.getSelection().getStart(), body.getSelection().getEnd(), (String) m[1]))); tools.getItems().add(b); }
        tools.getItems().add(new Separator(Orientation.VERTICAL));
        for (int h = 1; h <= 3; h++) { final int lv = h; Button b = new Button("H" + h); b.setOnAction(e -> apply(body, NotepadText.heading(body.getText(), body.getSelection().getStart(), body.getSelection().getEnd(), lv))); tools.getItems().add(b); }
        Object[][] prefixes = {{"• Lista", "- "}, {"1. Lista", "1. "}, {"☐ Tarefa", "[ ] "}, {"❝ Citação", "> "}};
        for (Object[] p : prefixes) { Button b = new Button((String) p[0]); b.setOnAction(e -> apply(body, NotepadText.prefix(body.getText(), body.getSelection().getStart(), body.getSelection().getEnd(), (String) p[1]))); tools.getItems().add(b); }
        Button table = new Button("Tabela"); table.setOnAction(e -> apply(body, NotepadText.insert(body.getText(), body.getCaretPosition(), NotepadText.table(3, 2), true)));
        Button picture = new Button("Imagem"); picture.setOnAction(e -> insertImage(body));
        Button verse = new Button("Passagem"); verse.setOnAction(e -> insertPassage(body));
        tools.getItems().addAll(new Separator(Orientation.VERTICAL), table, picture, verse);
        // Actions
        Button back = new Button(); back.setGraphic(DeskUi.icon("back", 20)); back.getStyleClass().add("round"); back.setOnAction(e -> { later.stop(); save.run(); d.open("notes"); });
        MenuButton more = new MenuButton("Mais");
        MenuItem word = new MenuItem("Exportar Word (.docx)"); word.setOnAction(e -> export(title.getText(), body.getText(), "docx"));
        MenuItem pdf = new MenuItem("Exportar PDF"); pdf.setOnAction(e -> export(title.getText(), body.getText(), "pdf"));
        MenuItem md = new MenuItem("Exportar Markdown (.md)"); md.setOnAction(e -> export(title.getText(), body.getText(), "md"));
        MenuItem listen = new MenuItem("Ouvir a nota"); listen.setOnAction(e -> DeskSpeech.toggle(d, NotepadText.plain(title.getText() + "\n" + body.getText())));
        MenuItem pin = new MenuItem("Fixar / desafixar"); pin.setOnAction(e -> { save.run(); try { d.store.update("UPDATE notes SET pinned=1-pinned WHERE id=?", note[0]); } catch (SQLException ignored) {} });
        MenuItem folder = new MenuItem("Mover para pasta…"); folder.setOnAction(e -> { save.run(); String f = DeskUi.ask("Pasta", "Nome da pasta (vazio para nenhuma)", ""); try { d.store.update("UPDATE notes SET folder=? WHERE id=?", f == null ? "" : f, note[0]); } catch (SQLException ignored) {} });
        MenuItem delete = new MenuItem("Apagar"); delete.setOnAction(e -> { if (note[0] != 0 && DeskUi.confirm("Apagar esta nota?", "Não pode ser desfeito.")) { try { d.store.update("DELETE FROM notes WHERE id=?", note[0]); } catch (SQLException ignored) {} later.stop(); d.open("notes"); } });
        more.getItems().addAll(word, pdf, md, new SeparatorMenuItem(), listen, pin, folder, new SeparatorMenuItem(), delete);
        Button ai = new Button("Seeky IA", DeskUi.icon("sparkle", 18)); ai.getStyleClass().add("primary"); ai.setOnAction(e -> DeskAi.menu(d, body, title));
        HBox top = DeskUi.row(back, title, ai, more); HBox.setHgrow(title, Priority.ALWAYS);
        SplitPane split = new SplitPane(body, preview); split.setDividerPositions(0.5); VBox.setVgrow(split, Priority.ALWAYS);
        VBox page = new VBox(10, top, tools, split, passages, info); page.setPadding(new javafx.geometry.Insets(18, 22, 14, 22)); page.getStyleClass().add("page");
        d.show(page); refresh.run(); body.requestFocus();
    }
    static void apply(TextArea body, NotepadText.Edit e) { if (e == null) return; body.setText(e.text); body.selectRange(Math.min(e.start, e.text.length()), Math.min(e.end, e.text.length())); body.requestFocus(); }
    void passage(BibleReferences.Reference r) {
        String text = ""; try { text = d.store.passage(d.store.bible(), r).replaceAll("\\[\\d+\\] ", ""); } catch (SQLException ignored) {}
        DeskUi.info(r.title, text.isEmpty() ? "Importe uma Bíblia (Bíblia → Importar) para ler o texto desta passagem." : text);
    }
    void insertPassage(TextArea body) {
        String q = DeskUi.ask("Inserir passagem", "Por exemplo: João 3:16-18 ou Salmo 23", ""); if (q == null) return;
        BibleReferences.Reference r = BibleReferences.lookup(q); if (r == null) { DeskUi.info("Passagem não reconhecida", "Escreva o livro, o capítulo e o versículo."); return; }
        String text = ""; try { text = d.store.passage(d.store.bible(), r).replaceAll("\\[\\d+\\] ", ""); } catch (SQLException ignored) {}
        apply(body, NotepadText.insert(body.getText(), body.getCaretPosition(), text.isEmpty() ? "> " + r.title : NotepadText.passage(r.title, text, ""), true));
    }
    void insertImage(TextArea body) {
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
        File f = fc.showOpenDialog(d.stage); if (f == null) return;
        try { String ext = f.getName().replaceFirst(".*\\.", "").toLowerCase(Locale.ROOT); String name = "img_" + System.currentTimeMillis() + "." + ext; Files.copy(f.toPath(), new File(media(), name).toPath()); apply(body, NotepadText.insert(body.getText(), body.getCaretPosition(), "![Imagem](note-img:" + name + ")", true)); }
        catch (IOException e) { DeskUi.info("Não foi possível usar a imagem", e.getMessage()); }
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
