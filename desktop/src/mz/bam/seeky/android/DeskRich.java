package mz.bam.seeky.android;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 5.59 · Seeky IA's answers (and any text with note marks) shown formatted, never as raw "##", "-" or "*":
 * headings, paragraphs, bullet and numbered lists, tasks, quotes and tables, with bold, italic, colours and
 * Bible passages that open with a click. The excerpt tags the model adds ("[T1]", "[D2·T4]") are removed.
 */
final class DeskRich {
    private DeskRich() {}
    static final Pattern TAGS = Pattern.compile("\\s*\\[(?:D\\d+\\s*[·.:-]?\\s*)?T\\d+(?:\\s*[,;]\\s*(?:D\\d+\\s*[·.:-]?\\s*)?T\\d+)*\\]");
    static final Pattern GENERAL = Pattern.compile("\\(\\s*(?:conhecimento geral|general knowledge)\\s*\\)", Pattern.CASE_INSENSITIVE);

    /** The answer as clean note marks: lists and headings tidied, excerpt tags gone, spacing kept even. */
    static String tidy(String answer) {
        String t = NotepadAiText.clean(answer == null ? "" : answer);
        t = TAGS.matcher(t).replaceAll("");
        t = GENERAL.matcher(t).replaceAll("(conhecimento geral)");
        t = t.replaceAll("(?m)[ \\t]+$", "").replaceAll("\\*\\*\\s*\\*\\*", "").replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }
    /** The answer as plain text, for Copiar (bullets as •, no marks). */
    static String plain(String marks) { return NotepadText.plain(marks).replaceAll("\n{3,}", "\n\n").trim(); }

    /** The marks as JavaFX nodes (they grow with the text; put them in a ScrollPane when long). */
    static VBox node(DeskMain d, String marks) {
        VBox box = new VBox(6); box.getStyleClass().add("rich");
        for (NotepadDoc.Block k : NotepadDoc.parse(marks)) {
            Node n;
            switch (k.kind) {
                case "h1": case "h2": case "h3": case "h4": { TextFlow f = flow(d, k.runs, "rich-" + k.kind); f.setPadding(new Insets(box.getChildren().isEmpty() ? 0 : 8, 0, 0, 0)); n = f; break; }
                case "bullet": case "number": case "task": {
                    Label mark = new Label(k.kind.equals("bullet") ? (k.level % 2 == 0 ? "•" : "◦") : k.kind.equals("number") ? k.number + "." : k.done ? "☑" : "☐");
                    mark.getStyleClass().add("rich-mark"); mark.setMinWidth(Region.USE_PREF_SIZE);
                    TextFlow f = flow(d, k.runs, k.done ? "rich-done" : null); HBox.setHgrow(f, Priority.ALWAYS);
                    HBox row = new HBox(8, mark, f); row.setAlignment(Pos.TOP_LEFT); row.setPadding(new Insets(0, 0, 0, 6 + k.level * 20)); n = row; break;
                }
                case "quote": { TextFlow f = flow(d, k.runs, "rich-quote-text"); VBox q = new VBox(f); q.getStyleClass().add("rich-quote"); n = q; break; }
                case "rule": n = new Separator(); break;
                case "table": {
                    GridPane g = new GridPane(); g.getStyleClass().add("rich-table");
                    for (int r = 0; r < k.rows.size(); r++) for (int c = 0; c < k.rows.get(r).size(); c++) {
                        TextFlow cell = flow(d, NotepadDoc.runs(k.rows.get(r).get(c)), r == 0 ? "rich-th" : null); StackPane p = new StackPane(cell); p.getStyleClass().add(r == 0 ? "rich-head" : "rich-cell"); StackPane.setAlignment(cell, Pos.CENTER_LEFT); g.add(p, c, r);
                    }
                    n = g; break;
                }
                case "image": case "audio": continue;
                default: {
                    if (k.text().trim().isEmpty()) { Region gap = new Region(); gap.setMinHeight(4); n = gap; break; }
                    TextFlow f = flow(d, k.runs, null); if (k.align == 'c') f.setTextAlignment(javafx.scene.text.TextAlignment.CENTER); else if (k.align == 'r') f.setTextAlignment(javafx.scene.text.TextAlignment.RIGHT);
                    f.setPadding(new Insets(0, 0, 0, k.level * 20)); n = f;
                }
            }
            box.getChildren().add(n);
        }
        return box;
    }
    /** One paragraph's runs; passages become links, "(conhecimento geral)" a quiet note. */
    static TextFlow flow(DeskMain d, List<NotepadDoc.Run> runs, String cls) {
        TextFlow f = new TextFlow(); f.getStyleClass().add("rich-flow"); if (cls != null) f.getStyleClass().add(cls); f.setLineSpacing(2);
        StringBuilder all = new StringBuilder(); for (NotepadDoc.Run r : runs) all.append(r.text);
        List<BibleReferences.Reference> refs = BibleReferences.references(all.toString());
        int at = 0;
        for (NotepadDoc.Run r : runs) {
            int start = at, end = at + r.text.length(); at = end;
            int i = start;
            while (i < end) {
                BibleReferences.Reference ref = null; for (BibleReferences.Reference x : refs) if (x.start < end && x.end > i && x.start >= i) { if (ref == null || x.start < ref.start) ref = x; }
                int cut = ref == null ? end : Math.max(i, ref.start);
                if (cut > i) addText(f, all.substring(i, cut), r);
                if (ref == null) break;
                int stop = Math.min(end, ref.end); Hyperlink link = new Hyperlink(all.substring(cut, stop)); link.getStyleClass().add("rich-passage"); BibleReferences.Reference go = ref;
                link.setOnAction(e -> DeskPassage.show(d, go)); link.setTooltip(new Tooltip(ref.title + " — clique para ler")); link.setPadding(Insets.EMPTY); f.getChildren().add(link);
                i = stop;
            }
        }
        return f;
    }
    static void addText(TextFlow f, String s, NotepadDoc.Run r) {
        Matcher m = Pattern.compile("\\(conhecimento geral\\)").matcher(s); int at = 0;
        while (m.find()) { add(f, s.substring(at, m.start()), r); Text g = new Text("conhecimento geral"); g.getStyleClass().addAll("rich-text", "rich-general"); f.getChildren().add(g); at = m.end(); }
        add(f, s.substring(at), r);
    }
    static void add(TextFlow f, String s, NotepadDoc.Run r) {
        if (s.isEmpty()) return;
        Text t = new Text(s); t.getStyleClass().add("rich-text");
        String style = (r.bold ? "-fx-font-weight: bold;" : "") + (r.italic ? "-fx-font-style: italic;" : "");
        if (r.color != 0) style += "-fx-fill: " + String.format(Locale.ROOT, "#%06x", r.color & 0xffffff) + ";";
        if (!style.isEmpty()) t.setStyle(style);
        t.setUnderline(r.underline); t.setStrikethrough(r.strike);
        if (r.mark) t.getStyleClass().add("rich-highlight");
        f.getChildren().add(t);
    }

    /**
     * The answer window: the title, the formatted answer (it scrolls when long) and the buttons given; returns the
     * one pressed. Copiar is always there and copies the text without marks.
     */
    static Optional<ButtonType> show(DeskMain d, String header, String marks, ButtonType... buttons) { return dialog(d, header, marks, buttons).showAndWait(); }
    static Alert dialog(DeskMain d, String header, String marks, ButtonType... buttons) {
        Alert a = new Alert(Alert.AlertType.NONE); a.setTitle("Seeky IA"); a.setHeaderText(header); DeskUi.style(a.getDialogPane());
        VBox rich = node(d, marks); rich.setPadding(new Insets(4, 14, 10, 4));
        ScrollPane scroll = new ScrollPane(rich); scroll.setFitToWidth(true); scroll.getStyleClass().add("page-scroll"); scroll.setPrefViewportWidth(660); scroll.setPrefViewportHeight(Math.max(240, Math.min(500, 90 + marks.length() / 2.2)));
        a.getDialogPane().setContent(scroll); a.getDialogPane().setPrefWidth(720);
        ButtonType copy = new ButtonType("Copiar", ButtonBar.ButtonData.LEFT);
        a.getDialogPane().getButtonTypes().add(copy); a.getDialogPane().getButtonTypes().addAll(buttons);
        if (buttons.length == 0) a.getDialogPane().getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        // Copiar keeps the window open.
        Button c = (Button) a.getDialogPane().lookupButton(copy);
        c.addEventFilter(javafx.event.ActionEvent.ACTION, e -> { e.consume(); javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent(); cc.putString(plain(marks)); javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc); c.setText("Copiado ✓"); d.status("Copiado."); });
        return a;
    }
}
