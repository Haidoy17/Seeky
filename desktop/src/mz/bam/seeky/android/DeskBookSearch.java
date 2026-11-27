package mz.bam.seeky.android;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 5.59 · Procurar no livro aberto (PDF, EPUB, Word, TXT…): every page or chapter is searched in the background
 * (accents and capitals do not matter), the results come with the page and a few words around, and a click goes
 * there with the words lit up. Ctrl+F opens it; Enter and Shift+Enter go to the next and previous result.
 */
final class DeskBookSearch {
    interface Source { int count(); String text(int part); String place(int part); }
    interface Go { void to(int part, String query, int occurrence); }
    static final class Result { final int part, occurrence; final String before, match, after; Result(int part, int occurrence, String before, String match, String after) { this.part = part; this.occurrence = occurrence; this.before = before; this.match = match; this.after = after; } }

    final DeskMain d; final Source source; final Go go; final Runnable closed;
    final VBox panel = new VBox(10); final TextField field = new TextField(); final Label info = DeskUi.muted("");
    final ListView<Result> list = new ListView<>(); final AtomicInteger generation = new AtomicInteger();
    final Map<Integer, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
    String query = ""; int current = -1;

    DeskBookSearch(DeskMain d, Source source, Go go, Runnable closed) {
        this.d = d; this.source = source; this.go = go; this.closed = closed;
        field.setPromptText("Procurar neste livro"); HBox.setHgrow(field, Priority.ALWAYS);
        Button close = DeskUi.iconButton("back", "Fechar a pesquisa", this::close); close.getGraphic().setRotate(180);
        Button prev = new Button("↑"), next = new Button("↓"); prev.setTooltip(new Tooltip("Resultado anterior (Shift+Enter)")); next.setTooltip(new Tooltip("Resultado seguinte (Enter)"));
        prev.setOnAction(e -> step(-1)); next.setOnAction(e -> step(1));
        HBox top = new HBox(6, field, prev, next, close); top.setAlignment(Pos.CENTER_LEFT);
        list.getStyleClass().add("search-results"); list.setFixedCellSize(-1); VBox.setVgrow(list, Priority.ALWAYS); list.setPlaceholder(DeskUi.muted("Escreva uma palavra ou frase."));
        list.setCellFactory(v -> new ListCell<Result>() {
            @Override protected void updateItem(Result r, boolean empty) {
                super.updateItem(r, empty); setText(null);
                if (empty || r == null) { setGraphic(null); return; }
                Text b = new Text(r.before), m = new Text(r.match), a = new Text(r.after); b.getStyleClass().add("rich-text"); a.getStyleClass().add("rich-text"); m.getStyleClass().addAll("rich-text", "search-match");
                TextFlow f = new TextFlow(b, m, a); f.prefWidthProperty().bind(list.widthProperty().subtract(34)); f.maxWidthProperty().bind(list.widthProperty().subtract(34));
                Label where = DeskUi.muted(source.place(r.part)); where.getStyleClass().add("caps");
                VBox cell = new VBox(3, where, f); cell.setPadding(new Insets(4, 2, 4, 2)); setGraphic(cell);
            }
        });
        list.setOnMouseClicked(e -> { int i = list.getSelectionModel().getSelectedIndex(); if (i >= 0) open(i); });
        list.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ENTER) { int i = list.getSelectionModel().getSelectedIndex(); if (i >= 0) open(i); } });
        javafx.animation.PauseTransition typing = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300)); typing.setOnFinished(e -> search(field.getText()));
        field.textProperty().addListener((o, a, b) -> typing.playFromStart());
        field.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) { typing.stop(); if (!field.getText().trim().equals(query)) search(field.getText()); else step(e.isShiftDown() ? -1 : 1); e.consume(); }
            else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) { close(); e.consume(); }
        });
        panel.getChildren().addAll(DeskUi.heading("Procurar no livro"), top, info, list); panel.getStyleClass().addAll("card", "book-search"); panel.setPadding(new Insets(12));
        panel.setPrefWidth(350); panel.setMinWidth(310); panel.setMaxWidth(370); field.setMinWidth(90); field.setPrefWidth(150); panel.setVisible(false); panel.setManaged(false);
    }
    boolean open() { return panel.isVisible(); }
    void show() { panel.setVisible(true); panel.setManaged(true); Platform.runLater(() -> { field.requestFocus(); field.selectAll(); }); }
    void close() { generation.incrementAndGet(); panel.setVisible(false); panel.setManaged(false); query = ""; current = -1; closed.run(); }

    /** Lower case without accents, letter for letter (the positions stay the same). */
    static String fold(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); String n = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD); b.append(Character.toLowerCase(n.isEmpty() ? c : n.charAt(0))); }
        return b.toString();
    }
    /** Every place of {@code query} in {@code text}: {start, end}. */
    static List<int[]> find(String text, String query) {
        List<int[]> out = new ArrayList<>(); String q = fold(query.trim().replaceAll("\\s+", " ")); if (q.isEmpty() || text == null) return out;
        String t = fold(text.replaceAll("\\s", " ")); // same length: every white space becomes one space
        // spaces in the text may be several: let one space in the query match any run of them
        String[] words = q.split(" "); StringBuilder rx = new StringBuilder();
        for (int i = 0; i < words.length; i++) { if (i > 0) rx.append(" +"); rx.append(java.util.regex.Pattern.quote(words[i])); }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(rx.toString()).matcher(t);
        while (m.find() && out.size() < 2000) out.add(new int[]{m.start(), m.end()});
        return out;
    }
    void search(String raw) {
        String q = raw == null ? "" : raw.trim(); query = q; current = -1;
        int gen = generation.incrementAndGet(); list.getItems().clear();
        if (q.length() < 2) { info.setText(q.isEmpty() ? "" : "Escreva pelo menos 2 letras."); closed.run(); return; }
        info.setText("A procurar…");
        int total = source.count();
        d.worker.execute(() -> {
            List<Result> batch = new ArrayList<>(); int found = 0;
            for (int p = 0; p < total && generation.get() == gen; p++) {
                String text = cache.computeIfAbsent(p, i -> { String t = source.text(i); return t == null ? "" : t; });
                List<int[]> hits = find(text, q);
                for (int k = 0; k < hits.size() && found < 500; k++, found++) {
                    int[] h = hits.get(k); String flat = text.replaceAll("\\s", " ");
                    int a = Math.max(0, h[0] - 45), z = Math.min(flat.length(), h[1] + 60);
                    batch.add(new Result(p, k, (a > 0 ? "…" : "") + flat.substring(a, h[0]).replaceAll(" +", " ").replaceFirst("^\\S*\\s", a > 0 ? "" : "$0"), flat.substring(h[0], h[1]), flat.substring(h[1], z).replaceAll(" +", " ") + (z < flat.length() ? "…" : "")));
                }
                if (p % 8 == 7 || p == total - 1 || found >= 500) {
                    List<Result> send = new ArrayList<>(batch); batch.clear(); int pp = p, ff = found;
                    Platform.runLater(() -> { if (generation.get() != gen) return; list.getItems().addAll(send); info.setText(ff == 0 ? "A procurar… (" + (pp + 1) + " de " + total + ")" : ff + (ff == 1 ? " resultado" : " resultados") + (pp + 1 < total && ff < 500 ? " · a procurar (" + (pp + 1) + " de " + total + ")" : "")); });
                }
                if (found >= 500) break;
            }
            int ff = found;
            Platform.runLater(() -> {
                if (generation.get() != gen) return;
                info.setText(ff == 0 ? "Nada encontrado com «" + q + "»." : ff + (ff == 1 ? " resultado" : " resultados") + (ff >= 500 ? " (os primeiros 500)" : "") + " · Enter: seguinte");
                if (ff > 0 && current < 0) open(0);
            });
        });
    }
    void open(int i) { if (i < 0 || i >= list.getItems().size()) return; current = i; list.getSelectionModel().select(i); list.scrollTo(Math.max(0, i - 2)); Result r = list.getItems().get(i); go.to(r.part, query, r.occurrence); }
    void step(int dir) { int n = list.getItems().size(); if (n == 0) return; open(((current < 0 ? (dir > 0 ? -1 : 0) : current) + dir + n) % n); }
}
