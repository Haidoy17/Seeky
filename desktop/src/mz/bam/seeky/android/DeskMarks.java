package mz.bam.seeky.android;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import java.io.File;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;

/**
 * Marcações nos livros: texto realçado com cores (e uma nota opcional) nos livros de texto, páginas
 * marcadas nos PDF, e "As minhas marcações" com todas, por livro, para voltar a cada uma.
 */
final class DeskMarks {
    private DeskMarks() {}
    static final int[] COLORS = {0xf6d365, 0x9be3b1, 0x8fd3f4, 0xf7a8b8, 0xc7b3f5};
    static final String[] NAMES = {"Amarelo", "Verde", "Azul", "Rosa", "Lilás"};

    static final class Mark { long id; String path = "", title = "", place = "", text = "", note = ""; int chapter, color; long created; }

    static List<Mark> list(DeskStore s, String path, int chapter) {
        List<Mark> out = new ArrayList<>();
        try {
            String sql = "SELECT id,path,title,chapter,place,text,color,note,created FROM marks" + (path == null ? " ORDER BY created DESC" : chapter < 0 ? " WHERE path=? ORDER BY chapter,id" : " WHERE path=? AND chapter=? ORDER BY id");
            List<Object[]> rows = path == null ? s.rows(sql) : chapter < 0 ? s.rows(sql, path) : s.rows(sql, path, chapter);
            for (Object[] r : rows) { Mark m = new Mark(); m.id = ((Number) r[0]).longValue(); m.path = r[1] + ""; m.title = r[2] + ""; m.chapter = ((Number) r[3]).intValue(); m.place = r[4] + ""; m.text = r[5] + ""; m.color = ((Number) r[6]).intValue(); m.note = r[7] + ""; m.created = ((Number) r[8]).longValue(); out.add(m); }
        } catch (SQLException ignored) {}
        return out;
    }
    static long add(DeskStore s, String path, String title, int chapter, String place, String text, int color, String note) throws SQLException {
        String clean = text == null ? "" : text.replace("\r", "").trim(); if (clean.length() > 4000) clean = clean.substring(0, 4000);
        return s.update("INSERT INTO marks(path,title,chapter,place,text,color,note,created) VALUES(?,?,?,?,?,?,?,?)", path, title, chapter, place == null ? "" : place, clean, color, note == null ? "" : note, System.currentTimeMillis());
    }

    /** Wraps the marked words of one (already escaped) paragraph in coloured <mark>s that can be clicked. */
    static String paint(String escaped, List<Mark> marks) {
        String out = escaped;
        for (Mark m : marks) for (String piece : m.text.split("\n")) {
            String p = DeskNotesHtml.esc(piece.trim()); if (p.length() < 2) continue;
            int at = out.indexOf(p); if (at < 0 || inside(out, at)) continue;
            String title = m.note.isEmpty() ? "" : " title='" + DeskNotesHtml.esc(m.note).replace("'", "&#39;") + "'";
            out = out.substring(0, at) + "<mark id='m" + m.id + "'" + title + " onclick=\"alert('seekymark:" + m.id + "')\" style='background:" + DeskThemes.hex(m.color) + ";color:#111;border-radius:3px;padding:0 1px;cursor:pointer" + (m.note.isEmpty() ? "" : ";border-bottom:2px dotted #333") + "'>" + p + "</mark>" + out.substring(at + p.length());
        }
        return out;
    }
    /** True when the position falls inside an existing tag (so a mark never cuts one in half). */
    static boolean inside(String html, int at) { int open = html.lastIndexOf('<', at), close = html.lastIndexOf('>', at); return open > close; }

    /** Colour dots, used in the selection bar and in the mark menu. */
    static HBox dots(java.util.function.IntConsumer pick) {
        HBox row = new HBox(6); row.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < COLORS.length; i++) {
            int c = COLORS[i]; Circle dot = new Circle(10, DeskSettings.color(c)); dot.setStroke(DeskSettings.color(0x000000)); dot.setStrokeWidth(0.4);
            Button b = new Button(); b.setGraphic(dot); b.getStyleClass().add("dot"); b.setTooltip(new Tooltip(NAMES[i])); b.setAccessibleText(NAMES[i]); b.setOnAction(e -> pick.accept(c));
            row.getChildren().add(b);
        }
        return row;
    }

    /** Menu of an existing mark: colour, note, copy, delete. */
    static void menu(DeskMain d, long id, Runnable changed) {
        Mark m = null; try { List<Object[]> r = d.store.rows("SELECT path FROM marks WHERE id=?", id); if (!r.isEmpty()) for (Mark x : list(d.store, r.get(0)[0] + "", -1)) if (x.id == id) m = x; } catch (SQLException ignored) {}
        if (m == null) return; final Mark mark = m;
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Seeky"); dialog.setHeaderText("Marcação"); DeskUi.style(dialog.getDialogPane());
        TextArea note = new TextArea(mark.note); note.setPromptText("Nota sobre esta marcação (opcional)"); note.setWrapText(true); note.setPrefRowCount(3);
        int[] color = {mark.color};
        Label text = DeskUi.text("“" + (mark.text.length() > 400 ? mark.text.substring(0, 400) + "…" : mark.text) + "”");
        VBox box = new VBox(10, text, dots(c -> color[0] = c), note); box.setPrefWidth(480); dialog.getDialogPane().setContent(box);
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE), delete = new ButtonType("Apagar", ButtonBar.ButtonData.LEFT), copy = new ButtonType("Copiar", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, copy, delete, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(b -> {
            try {
                if (b == save) d.store.update("UPDATE marks SET color=?,note=? WHERE id=?", color[0], note.getText().trim(), id);
                else if (b == delete) d.store.update("DELETE FROM marks WHERE id=?", id);
                else if (b == copy) { javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent(); c.putString(mark.text); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c); d.status("Copiado."); }
            } catch (SQLException ignored) {}
            if (changed != null) changed.run();
        });
    }

    /** As minhas marcações: every mark, newest first, grouped by book, with search and colour filter. */
    static Node view(DeskMain d) {
        VBox page = DeskUi.page();
        page.getChildren().add(DeskUi.row(DeskUi.iconButton("back", "Voltar aos livros", () -> d.open("reader")), DeskUi.title("As minhas marcações")));
        TextField search = new TextField(); search.setPromptText("Procurar nas marcações e notas"); HBox.setHgrow(search, Priority.ALWAYS);
        int[] filter = {-1};
        VBox list = new VBox(10);
        Runnable[] draw = {null};
        HBox colors = dots(c -> { filter[0] = filter[0] == c ? -1 : c; draw[0].run(); });
        page.getChildren().addAll(DeskUi.row(search, colors), list);
        draw[0] = () -> {
            list.getChildren().clear();
            String q = BibleGame.plain(search.getText().trim());
            Map<String, VBox> books = new LinkedHashMap<>();
            for (Mark m : list(d.store, null, 0)) {
                if (filter[0] >= 0 && m.color != filter[0]) continue;
                if (!q.isEmpty() && !BibleGame.plain(m.text + " " + m.note + " " + m.title).contains(q)) continue;
                VBox book = books.computeIfAbsent(m.path, p -> { VBox b = new VBox(8); b.getChildren().add(DeskUi.heading(m.title.isEmpty() ? new File(p).getName() : m.title)); return b; });
                Region bar = new Region(); bar.setMinWidth(5); bar.setMaxWidth(5); bar.setStyle("-fx-background-color: " + DeskThemes.hex(m.color) + "; -fx-background-radius: 3;");
                VBox words = new VBox(3, DeskUi.text(m.text.length() > 300 ? m.text.substring(0, 300) + "…" : m.text));
                if (!m.note.isEmpty()) words.getChildren().add(DeskUi.muted("Nota: " + m.note));
                words.getChildren().add(DeskUi.muted((m.place.isEmpty() ? "" : m.place + " · ") + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(m.created))));
                HBox.setHgrow(words, Priority.ALWAYS);
                HBox row = new HBox(12, bar, words); row.getStyleClass().addAll("card", "tile"); row.setPadding(new javafx.geometry.Insets(12));
                row.setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) menu(d, m.id, draw[0]); else d.reader.open(new File(m.path), m.chapter, m.id); });
                Tooltip.install(row, new Tooltip("Clique para abrir · botão direito para mudar a cor, a nota ou apagar"));
                book.getChildren().add(row);
            }
            if (books.isEmpty()) list.getChildren().add(DeskUi.card(DeskUi.heading(search.getText().isEmpty() && filter[0] < 0 ? "Ainda não há marcações" : "Nada encontrado"), DeskUi.text("Num livro, seleccione texto e escolha uma cor. Nos PDF, use Marcar página.")));
            list.getChildren().addAll(books.values());
        };
        search.textProperty().addListener((o, a, b) -> draw[0].run());
        draw[0].run();
        return DeskUi.scroll(page);
    }
}
