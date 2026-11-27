package mz.bam.seeky.android;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import java.sql.SQLException;
import java.util.*;

/**
 * 5.57 · Bible passages anywhere (notes, books, PDF pages): a click shows the text in a window over the page,
 * without leaving for the Bíblia. Also the pieces shared by the web pages: their scroll bar and the click
 * script that turns {@code <a class="passage" data-ref="…">} into a call back to the window.
 */
final class DeskPassage {
    private DeskPassage() {}
    /** Clicks on a passage reach Java as an alert "seekyref:<title>" (works for any language, unlike a link). */
    static final String SCRIPT = "<script>document.addEventListener('click',function(e){var a=e.target;while(a&&a.nodeType===1&&!(a.classList&&a.classList.contains('passage')))a=a.parentNode;"
        + "if(a&&a.nodeType===1&&a.getAttribute&&a.getAttribute('data-ref')){var s=window.getSelection&&window.getSelection();if(s&&!s.isCollapsed&&s.toString().length>0)return;e.preventDefault();alert('seekyref:'+a.getAttribute('data-ref'));}},true);</script>";
    /** A clear scroll bar at the right edge of the web pages, in the theme's colours. */
    static String scrollbarCss() {
        DeskThemes.Theme t = DeskThemes.current;
        return "::-webkit-scrollbar{width:14px;height:14px}::-webkit-scrollbar-track{background:" + DeskThemes.hex(t.raised()) + ";border-radius:7px}"
            + "::-webkit-scrollbar-thumb{background:" + DeskThemes.hex(t.muted) + ";border-radius:7px;border:3px solid " + DeskThemes.hex(t.raised()) + "}"
            + "::-webkit-scrollbar-thumb:hover{background:" + DeskThemes.hex(t.accent) + "}";
    }
    /** Handles an alert from a page; true when it was a passage. */
    static boolean alert(DeskMain d, String data) {
        if (data == null || !data.startsWith("seekyref:")) return false;
        BibleReferences.Reference r = BibleReferences.lookup(data.substring(9));
        if (r != null) show(d, r);
        return true;
    }

    /** The passage's verses {number, text}, from the chosen Bible. */
    static List<Object[]> verses(DeskStore store, long bible, BibleReferences.Reference r) throws SQLException {
        List<Object[]> out = new ArrayList<>();
        for (BibleReferences.Part part : r.parts) {
            for (Object[] v : store.chapter(bible, r.book, part.chapter)) {
                int n = ((Number) v[0]).intValue(); boolean in = part.whole || part.verses.isEmpty();
                for (int[] range : part.verses) if (n >= range[0] && (n <= range[1] || range.length > 2 && range[2] == 1)) in = true;
                if (in) out.add(new Object[]{r.parts.size() > 1 ? part.chapter + ":" + n : "" + n, v[1]});
            }
        }
        return out;
    }
    /** The window with the passage: verses, version, Copiar, Abrir na Bíblia. */
    static void show(DeskMain d, BibleReferences.Reference r) { dialog(d, r).showAndWait(); }
    /** The window (built, not shown): its buttons copy the passage or open it in the Bíblia. */
    static Alert dialog(DeskMain d, BibleReferences.Reference r) {
        long bible = d.store.bible(); List<Object[]> verses = new ArrayList<>(); String version = "";
        try { if (bible > 0) { verses = verses(d.store, bible, r); List<Object[]> t = d.store.rows("SELECT title FROM bibles WHERE id=?", bible); if (!t.isEmpty()) version = t.get(0)[0] + ""; } } catch (SQLException ignored) {}
        Alert dlg = new Alert(Alert.AlertType.NONE); dlg.setTitle("Seeky"); dlg.setHeaderText(r.title + (version.isEmpty() ? "" : " · " + version)); DeskUi.style(dlg.getDialogPane());
        TextFlow flow = new TextFlow(); flow.setLineSpacing(4); flow.getStyleClass().add("passage-text");
        StringBuilder plain = new StringBuilder();
        if (verses.isEmpty()) flow.getChildren().add(new Text(bible == 0 ? "Ainda não há uma Bíblia no computador. Importe uma em Bíblia › Importar para ler as passagens aqui." : "Esta Bíblia não tem " + r.title + "."));
        for (Object[] v : verses) {
            Text n = new Text(v[0] + " "); n.getStyleClass().add("verse-number"); n.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            Text t = new Text(v[1] + " "); t.setStyle("-fx-font-size: 16px;");
            flow.getChildren().addAll(n, t); plain.append(plain.length() == 0 ? "" : " ").append(v[1]);
        }
        for (javafx.scene.Node n : flow.getChildren()) ((Text) n).setFill(DeskSettings.color(n.getStyleClass().contains("verse-number") ? DeskThemes.current.accent : DeskThemes.current.ink));
        ScrollPane scroll = new ScrollPane(flow); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(Math.min(380, 60 + verses.size() * 46)); scroll.setPrefWidth(560); scroll.getStyleClass().add("page-scroll");
        flow.setPrefWidth(530);
        dlg.getDialogPane().setContent(scroll);
        ButtonType copy = new ButtonType("Copiar", ButtonBar.ButtonData.LEFT), open = new ButtonType("Abrir na Bíblia", ButtonBar.ButtonData.OTHER), close = new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (!verses.isEmpty()) dlg.getDialogPane().getButtonTypes().addAll(copy, open);
        dlg.getDialogPane().getButtonTypes().add(close);
        String text = r.title + (version.isEmpty() ? "" : " (" + version + ")") + "\n" + plain;
        dlg.setResultConverter(b -> {
            if (b == copy) { javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent(); c.putString(text); javafx.scene.input.Clipboard.getSystemClipboard().setContent(c); d.status("Passagem copiada."); }
            else if (b == open) { d.bible.book = r.book; d.bible.chapter = r.parts.isEmpty() ? r.chapter : r.parts.get(0).chapter; d.open("bible"); }
            return b;
        });
        return dlg;
    }
}
