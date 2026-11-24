package mz.bam.seeky.android;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Bible: import (MyBible, USX, USFM, CSV/TSV, text, EPUB), read by chapter, passages, search, listen. */
final class DeskBible {
    final DeskMain d;
    String book = "Génesis"; int chapter = 1;
    DeskBible(DeskMain d) { this.d = d; book = d.store.get("bible_book", "Génesis"); try { chapter = Integer.parseInt(d.store.get("bible_chapter", "1")); } catch (Exception e) { chapter = 1; } }

    Node view() {
        long bible = d.store.bible();
        VBox page = DeskUi.page();
        HBox top = DeskUi.row(DeskUi.title("Bíblia"), DeskUi.grow());
        ComboBox<String> versions = new ComboBox<>();
        List<Object[]> list = new ArrayList<>();
        try { list = d.store.rows("SELECT id,title FROM bibles ORDER BY created"); } catch (SQLException ignored) {}
        for (Object[] r : list) versions.getItems().add(r[1] + "");
        for (int i = 0; i < list.size(); i++) if (((Number) list.get(i)[0]).longValue() == bible) versions.getSelectionModel().select(i);
        final List<Object[]> all = list;
        versions.setOnAction(e -> { int i = versions.getSelectionModel().getSelectedIndex(); if (i >= 0) { d.store.put("bible", all.get(i)[0] + ""); d.open("bible"); } });
        if (!list.isEmpty()) top.getChildren().add(versions);
        top.getChildren().add(DeskUi.button("Importar Bíblia", list.isEmpty(), this::importBible));
        if (!list.isEmpty()) top.getChildren().add(DeskUi.iconButton("trash", "Remover esta Bíblia", () -> remove(bible)));
        page.getChildren().add(top);
        if (bible == 0) {
            page.getChildren().add(DeskUi.card(DeskUi.heading("Ainda não há Bíblias"), DeskUi.text("Importe uma Bíblia que tenha no computador: módulos MyBible (.SQLite3), USX, USFM, CSV/TSV, texto (.txt) ou EPUB. O Seeky não distribui traduções; use ficheiros que pode usar."), DeskUi.button("Importar Bíblia", true, this::importBible)));
            return DeskUi.scroll(page);
        }
        // Passage and search
        TextField query = new TextField(); query.setPromptText("Passagem (João 3:16, Salmo 23) ou palavras a procurar"); HBox.setHgrow(query, Priority.ALWAYS);
        VBox results = new VBox(8);
        Runnable go = () -> find(bible, query.getText(), results);
        query.setOnAction(e -> go.run());
        page.getChildren().addAll(DeskUi.row(query, DeskUi.button("Procurar", true, go)), results);
        // Book and chapter
        ComboBox<String> books = new ComboBox<>(FXCollections.observableArrayList(booksOf(bible)));
        if (!books.getItems().contains(book) && !books.getItems().isEmpty()) book = books.getItems().get(0);
        books.getSelectionModel().select(book);
        Spinner<Integer> chap = new Spinner<>(1, Math.max(1, chaptersOf(bible, book)), Math.min(chapter, Math.max(1, chaptersOf(bible, book)))); chap.setEditable(true); chap.setPrefWidth(90);
        VBox text = new VBox(10);
        Runnable show = () -> { book = books.getValue(); chapter = chap.getValue(); d.store.put("bible_book", book); d.store.put("bible_chapter", chapter + ""); chapterView(bible, text); };
        books.setOnAction(e -> { int max = Math.max(1, chaptersOf(bible, books.getValue())); chap.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, 1)); show.run(); });
        chap.valueProperty().addListener((o, a, b) -> show.run());
        HBox nav = DeskUi.row(books, chap, DeskUi.iconButton("back", "Capítulo anterior", () -> { if (chap.getValue() > 1) chap.decrement(); }), DeskUi.button("Seguinte", false, chap::increment), DeskUi.grow(),
            DeskUi.iconButton("speaker", "Ouvir o capítulo", () -> DeskSpeech.toggle(d, plainChapter(bible))), DeskUi.button("Marcar como lido", false, () -> { d.study.markRead(book, chapter); d.status(book + " " + chapter + " marcado como lido hoje."); }));
        page.getChildren().addAll(nav, text);
        chapterView(bible, text);
        return DeskUi.scroll(page);
    }
    List<String> booksOf(long bible) { List<String> out = new ArrayList<>(); try { Set<String> have = new HashSet<>(); for (Object[] r : d.store.rows("SELECT DISTINCT book FROM verses WHERE bible=?", bible)) have.add(r[0] + ""); for (String b : BibleReferences.books(true)) if (have.remove(b)) out.add(b); out.addAll(new TreeSet<>(have)); } catch (SQLException ignored) {} return out; }
    int chaptersOf(long bible, String b) { try { return (int) d.store.count("SELECT MAX(chapter) FROM verses WHERE bible=? AND book=?", bible, b); } catch (SQLException e) { return 1; } }
    String plainChapter(long bible) { StringBuilder s = new StringBuilder(book + ", capítulo " + chapter + ". "); try { for (Object[] v : d.store.chapter(bible, book, chapter)) s.append(v[1]).append(' '); } catch (SQLException ignored) {} return s.toString(); }
    void chapterView(long bible, VBox box) {
        box.getChildren().clear();
        Label h = DeskUi.title(book + " " + chapter); box.getChildren().add(h);
        TextFlow flow = new TextFlow(); flow.getStyleClass().add("scripture"); flow.setLineSpacing(6);
        try {
            List<Object[]> verses = d.store.chapter(bible, book, chapter);
            if (verses.isEmpty()) { box.getChildren().add(DeskUi.muted("Este capítulo não está nesta Bíblia.")); return; }
            for (Object[] v : verses) {
                Text n = new Text(" " + v[0] + " "); n.getStyleClass().add("verse-number");
                Text t = new Text(v[1] + " "); t.getStyleClass().add("verse-text");
                final int num = ((Number) v[0]).intValue(); final String body = v[1] + "";
                t.setOnMouseClicked(e -> verseMenu(num, body).show(t, e.getScreenX(), e.getScreenY()));
                flow.getChildren().addAll(n, t);
            }
        } catch (SQLException e) { box.getChildren().add(DeskUi.muted(e.getMessage())); return; }
        box.getChildren().add(DeskUi.card(flow));
    }
    ContextMenu verseMenu(int verse, String text) {
        String ref = book + " " + chapter + ":" + verse;
        MenuItem copy = new MenuItem("Copiar " + ref); copy.setOnAction(e -> copy(ref + " — " + text));
        MenuItem card = new MenuItem("Guardar para decorar"); card.setOnAction(e -> { d.study.addCard(ref, text); d.status(ref + " guardado para decorar."); });
        MenuItem note = new MenuItem("Criar nota com este versículo"); note.setOnAction(e -> d.notes.newWith(ref, "> " + ref + "\n> " + text + "\n\n"));
        MenuItem listen = new MenuItem("Ouvir"); listen.setOnAction(e -> DeskSpeech.say(d, ref + ". " + text));
        return new ContextMenu(copy, card, note, listen);
    }
    static void copy(String s) { ClipboardContent c = new ClipboardContent(); c.putString(s); Clipboard.getSystemClipboard().setContent(c); }

    /** A passage ("João 3:16") or a word search in the chosen Bible. */
    void find(long bible, String q, VBox out) {
        out.getChildren().clear(); if (q == null || q.trim().isEmpty()) return;
        BibleReferences.Reference ref = BibleReferences.lookup(q.trim());
        if (ref != null) {
            try {
                String text = d.store.passage(bible, ref);
                Button open = DeskUi.button("Abrir o capítulo", false, () -> { book = ref.book; chapter = ref.chapter; d.store.put("bible_book", book); d.store.put("bible_chapter", chapter + ""); d.open("bible"); });
                out.getChildren().add(DeskUi.card(DeskUi.heading(ref.title), text.isEmpty() ? DeskUi.muted("Esta passagem não está nesta Bíblia.") : DeskUi.text(text),
                    DeskUi.row(open, DeskUi.button("Copiar", false, () -> copy(ref.title + " — " + text.replaceAll("\\[\\d+\\] ", ""))), DeskUi.button("Ouvir", false, () -> DeskSpeech.say(d, ref.title + ". " + text.replaceAll("\\[\\d+\\] ", ""))), DeskUi.button("Nota", false, () -> d.notes.newWith(ref.title, "> " + ref.title + "\n> " + text.replaceAll("\\[\\d+\\] ", "") + "\n\n")))));
            } catch (SQLException e) { out.getChildren().add(DeskUi.muted(e.getMessage())); }
            return;
        }
        String needle = q.trim();
        d.run("A procurar…", () -> d.store.rows("SELECT book,chapter,verse,text FROM verses WHERE bible=? AND text LIKE ? LIMIT 200", bible, "%" + needle.replace("%", "") + "%"), rows -> {
            out.getChildren().add(DeskUi.muted(rows.size() + (rows.size() == 200 ? "+ " : " ") + "versículos com \"" + needle + "\""));
            for (Object[] r : rows) { Label l = DeskUi.text(r[0] + " " + r[1] + ":" + r[2] + " — " + r[3]); l.getStyleClass().add("result"); l.setOnMouseClicked(e -> { book = r[0] + ""; chapter = ((Number) r[1]).intValue(); d.store.put("bible_book", book); d.store.put("bible_chapter", chapter + ""); d.open("bible"); }); out.getChildren().add(l); }
        });
    }

    void remove(long bible) {
        if (!DeskUi.confirm("Remover esta Bíblia?", "Os versículos desta tradução são apagados do Seeky (o ficheiro original fica onde estava).")) return;
        d.run("A remover…", () -> { d.store.update("DELETE FROM verses WHERE bible=?", bible); d.store.update("DELETE FROM bibles WHERE id=?", bible); d.store.put("bible", "0"); return null; }, v -> d.open("bible"));
    }

    void importBible() {
        FileChooser fc = new FileChooser(); fc.setTitle("Importar Bíblia");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Bíblias", "*.SQLite3", "*.sqlite3", "*.sqlite", "*.db", "*.usx", "*.xml", "*.usfm", "*.sfm", "*.txt", "*.csv", "*.tsv", "*.epub", "*.zip"), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        File f = fc.showOpenDialog(d.stage); if (f == null) return;
        String title = DeskUi.ask("Nome da tradução", "Como quer chamar esta Bíblia?", f.getName().replaceFirst("\\.[^.]+$", "")); if (title == null) return;
        d.run("A importar " + f.getName() + "…", () -> importFile(d.store, f, title, () -> false), n -> { d.status(n + " versículos importados."); d.open("bible"); DeskUi.info("Bíblia importada", title + ": " + n + " versículos."); });
    }
    /** Imports a Bible file into the store; returns the number of verses. Nothing is kept when it fails. */
    static int importFile(DeskStore store, File f, String title, java.util.function.BooleanSupplier stop) throws Exception {
        String name = f.getName().toLowerCase(Locale.ROOT);
        Connection db = store.db; db.setAutoCommit(false);
        try {
            long id = store.update("INSERT INTO bibles(title,source,created) VALUES(?,?,?)", title, f.getPath(), System.currentTimeMillis());
            PreparedStatement insert = db.prepareStatement("INSERT OR REPLACE INTO verses(bible,book,chapter,verse,text) VALUES(?,?,?,?,?)");
            int[] stored = {0};
            BibleText.Sink sink = (b, c, v, t) -> { insert.setLong(1, id); insert.setString(2, b); insert.setInt(3, c); insert.setInt(4, v); insert.setString(5, t); insert.addBatch(); if (++stored[0] % 2000 == 0) insert.executeBatch(); };
            if (head(f).startsWith("SQLite format 3")) DeskMyBible.read(f, sink, stop);
            else if (name.endsWith(".usx") || name.endsWith(".xml")) { try (InputStream in = new FileInputStream(f)) { BibleUsx.read(in, sink, stop, () -> {}); } }
            else if (name.endsWith(".epub")) { try (BookArchive book = new BookArchive(f, true)) { BiblePlainText parser = new BiblePlainText(sink); for (int part = 0; part < book.chapters.size(); part++) BibleEpubText.read(book, part, parser, stop, () -> {}); parser.flush(); } }
            else if (name.endsWith(".zip")) { try (ZipFile zip = new ZipFile(f)) { for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries(); e.hasMoreElements(); ) { java.util.zip.ZipEntry z = e.nextElement(); if (z.getName().toLowerCase(Locale.ROOT).endsWith(".usx")) try (InputStream in = zip.getInputStream(z)) { BibleUsx.read(in, sink, stop, () -> {}); } } } }
            else BibleEncoding.parse(() -> new FileInputStream(f), sink, () -> { insert.clearBatch(); stored[0] = 0; try (PreparedStatement del = db.prepareStatement("DELETE FROM verses WHERE bible=?")) { del.setLong(1, id); del.executeUpdate(); } }, stop);
            insert.executeBatch(); insert.close();
            long n = store.count("SELECT COUNT(*) FROM verses WHERE bible=?", id);
            if (n == 0) throw new IOException("Nenhum versículo reconhecido neste ficheiro.");
            db.commit(); store.put("bible", id + "");
            return (int) n;
        } catch (Exception e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    static String head(File f) { try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[16]; int n = in.read(b); return n <= 0 ? "" : new String(b, 0, n, StandardCharsets.ISO_8859_1); } catch (IOException e) { return ""; } }
}
