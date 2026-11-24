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
        if (!list.isEmpty()) top.getChildren().add(extrasMenu(bible));
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
    /** The chapter with its section titles and, beside it, the comments of the chosen commentary. */
    void chapterView(long bible, VBox box) {
        box.getChildren().clear();
        Label h = DeskUi.title(book + " " + chapter); box.getChildren().add(h);
        TextFlow flow = new TextFlow(); flow.getStyleClass().add("scripture"); flow.setLineSpacing(6);
        boolean showTitles = !"0".equals(d.store.get("show_titles", "1")), showComments = !"0".equals(d.store.get("show_comments", "1"));
        long commentary = showComments ? commentary() : 0;
        List<Object[]> comments = commentary > 0 ? commentsOf(commentary, book, chapter) : new ArrayList<>();
        VBox side = new VBox(10); side.getStyleClass().add("comment-panel"); side.setPadding(new Insets(14)); side.setPrefWidth(330); side.setMinWidth(260);
        try {
            List<Object[]> verses = d.store.chapter(bible, book, chapter);
            if (verses.isEmpty()) { box.getChildren().add(DeskUi.muted("Este capítulo não está nesta Bíblia.")); return; }
            Map<Integer, List<String>> heads = new HashMap<>();
            if (showTitles) for (Object[] t : d.store.rows("SELECT verse,text FROM titles WHERE bible=? AND book=? AND chapter=? ORDER BY verse,ord", bible, book, chapter)) heads.computeIfAbsent(((Number) t[0]).intValue(), k -> new ArrayList<>()).add(t[1] + "");
            boolean first = true;
            for (Object[] v : verses) {
                final int num = ((Number) v[0]).intValue(); final String body = v[1] + "";
                for (String title : heads.getOrDefault(num, Collections.emptyList())) {
                    Text t = new Text((first ? "" : "\n\n") + title + "\n"); t.getStyleClass().add("verse-heading"); flow.getChildren().add(t); first = true;
                }
                Text n = new Text((first ? "" : " ") + num + " "); n.getStyleClass().add("verse-number");
                Text t = new Text(body); t.getStyleClass().add("verse-text");
                t.setOnMouseClicked(e -> verseMenu(num, body).show(t, e.getScreenX(), e.getScreenY()));
                flow.getChildren().addAll(n, t);
                List<Object[]> mine = new ArrayList<>(); for (Object[] cm : comments) if (covers(cm, chapter, num)) mine.add(cm);
                if (!mine.isEmpty()) {
                    Text mark = new Text(" ✎"); mark.getStyleClass().add("verse-comment"); Tooltip.install(mark, new Tooltip("Comentário"));
                    mark.setOnMouseClicked(e -> showComments(side, num, mine));
                    flow.getChildren().add(mark);
                }
                first = false;
            }
        } catch (SQLException e) { box.getChildren().add(DeskUi.muted(e.getMessage())); return; }
        VBox card = DeskUi.card(flow); HBox.setHgrow(card, Priority.ALWAYS);
        if (comments.isEmpty()) { box.getChildren().add(card); return; }
        String name = ""; try { List<Object[]> r = d.store.rows("SELECT title FROM commentaries WHERE id=?", commentary); if (!r.isEmpty()) name = r.get(0)[0] + ""; } catch (SQLException ignored) {}
        side.getChildren().setAll(DeskUi.heading("Comentário"), DeskUi.muted(name), DeskUi.text(comments.size() + (comments.size() == 1 ? " nota neste capítulo. Toque em ✎ junto a um versículo." : " notas neste capítulo. Toque em ✎ junto a um versículo.")));
        Object[] firstComment = comments.get(0); showComments(side, ((Number) firstComment[0]).intValue(), Collections.singletonList(firstComment));
        HBox both = new HBox(14, card, side); box.getChildren().add(both);
    }
    void showComments(VBox side, int verse, List<Object[]> list) {
        String name = side.getChildren().size() > 1 && side.getChildren().get(1) instanceof Label ? ((Label) side.getChildren().get(1)).getText() : "";
        side.getChildren().setAll(DeskUi.heading("Comentário"), DeskUi.muted(name));
        for (Object[] cm : list) {
            int c1 = ((Number) cm[3]).intValue(), c2 = ((Number) cm[4]).intValue(), v1 = ((Number) cm[0]).intValue(), v2 = ((Number) cm[1]).intValue();
            String where = v1 == 0 ? book + " " + c1 + (c2 > c1 ? "–" + c2 : "") : book + " " + c1 + ":" + v1 + (c2 > c1 ? "–" + c2 + ":" + v2 : v2 > v1 ? "–" + v2 : "");
            Label text = DeskUi.text(cm[2] + ""); text.setWrapText(true);
            side.getChildren().addAll(DeskUi.heading(where), text, DeskUi.row(DeskUi.button("Copiar", false, () -> copy(where + "\n" + cm[2])), DeskUi.button("Nota", false, () -> d.notes.newWith("Comentário · " + where, "> " + where + "\n\n" + cm[2] + "\n\n"))));
        }
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
        d.run("A remover…", () -> { d.store.update("DELETE FROM verses WHERE bible=?", bible); d.store.update("DELETE FROM titles WHERE bible=?", bible); d.store.update("DELETE FROM bibles WHERE id=?", bible); d.store.put("bible", "0"); return null; }, v -> d.open("bible"));
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
            boolean mybible = head(f).startsWith("SQLite format 3");
            if (mybible) DeskMyBible.read(f, sink, stop);
            else if (name.endsWith(".usx") || name.endsWith(".xml")) { try (InputStream in = new FileInputStream(f)) { BibleUsx.read(in, sink, stop, () -> {}); } }
            else if (name.endsWith(".epub")) { try (BookArchive book = new BookArchive(f, true)) { BiblePlainText parser = new BiblePlainText(sink); for (int part = 0; part < book.chapters.size(); part++) BibleEpubText.read(book, part, parser, stop, () -> {}); parser.flush(); } }
            else if (name.endsWith(".zip")) { try (ZipFile zip = new ZipFile(f)) { for (java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries(); e.hasMoreElements(); ) { java.util.zip.ZipEntry z = e.nextElement(); if (z.getName().toLowerCase(Locale.ROOT).endsWith(".usx")) try (InputStream in = zip.getInputStream(z)) { BibleUsx.read(in, sink, stop, () -> {}); } } } }
            else BibleEncoding.parse(() -> new FileInputStream(f), sink, () -> { insert.clearBatch(); stored[0] = 0; try (PreparedStatement del = db.prepareStatement("DELETE FROM verses WHERE bible=?")) { del.setLong(1, id); del.executeUpdate(); } }, stop);
            insert.executeBatch(); insert.close();
            long n = store.count("SELECT COUNT(*) FROM verses WHERE bible=?", id);
            if (n == 0) throw new IOException("Nenhum versículo reconhecido neste ficheiro.");
            if (mybible) {
                titlesInto(store, id, f, stop); // os títulos que vêm dentro do módulo
                File sub = DeskMyBible.companion(f, "subheadings"); if (sub != null) titlesInto(store, id, sub, stop);
            }
            db.commit(); store.put("bible", id + "");
            if (mybible) { File com = DeskMyBible.companion(f, "commentaries"); if (com != null) try { commentsInto(store, com, stop); } catch (Exception ignored) {} }
            return (int) n;
        } catch (Exception e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    /** Section titles of a MyBible module (or a .subheadings module) into a Bible; returns how many. */
    static int titlesInto(DeskStore store, long bible, File f, java.util.function.BooleanSupplier stop) throws Exception {
        try (PreparedStatement p = store.db.prepareStatement("INSERT OR REPLACE INTO titles(bible,book,chapter,verse,ord,text) VALUES(?,?,?,?,?,?)")) {
            int[] n = {0};
            DeskMyBible.titles(f, (b, c, v, o, t) -> { p.setLong(1, bible); p.setString(2, b); p.setInt(3, c); p.setInt(4, v); p.setInt(5, o); p.setString(6, t); p.addBatch(); if (++n[0] % 2000 == 0) p.executeBatch(); }, stop);
            p.executeBatch(); return n[0];
        }
    }
    /** A MyBible commentary module; returns {commentary id, comments}. */
    static long[] commentsInto(DeskStore store, File f, java.util.function.BooleanSupplier stop) throws Exception {
        boolean auto = store.db.getAutoCommit(); store.db.setAutoCommit(false);
        try {
            long id = store.update("INSERT INTO commentaries(title,source,created) VALUES(?,?,?)", DeskMyBible.description(f), f.getPath(), System.currentTimeMillis());
            int[] n = {0};
            try (PreparedStatement p = store.db.prepareStatement("INSERT INTO comments(commentary,book,chapter,verse,chapter_to,verse_to,text) VALUES(?,?,?,?,?,?,?)")) {
                DeskMyBible.comments(f, (b, c, v, c2, v2, t) -> { p.setLong(1, id); p.setString(2, b); p.setInt(3, c); p.setInt(4, v); p.setInt(5, c2); p.setInt(6, v2); p.setString(7, t); p.addBatch(); if (++n[0] % 2000 == 0) p.executeBatch(); }, stop);
                p.executeBatch();
            }
            store.db.commit(); store.put("commentary", id + "");
            return new long[]{id, n[0]};
        } catch (Exception e) { store.db.rollback(); throw e; }
        finally { store.db.setAutoCommit(auto); }
    }
    void loadTitles(long bible) {
        FileChooser fc = new FileChooser(); fc.setTitle("Carregar títulos (MyBible)");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Módulos MyBible", "*.SQLite3", "*.sqlite3", "*.sqlite"), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        File f = fc.showOpenDialog(d.stage); if (f == null) return;
        d.run("A carregar títulos…", () -> {
            if (!head(f).startsWith("SQLite format 3")) throw new IOException("Escolha um módulo MyBible (.SQLite3): a Bíblia com títulos ou o ficheiro .subheadings.");
            int n; Connection db = d.store.db; db.setAutoCommit(false);
            try { d.store.update("DELETE FROM titles WHERE bible=?", bible); n = titlesInto(d.store, bible, f, () -> false); if (n == 0) throw new IOException("Este módulo não tem títulos (tabelas stories ou subheadings)."); db.commit(); }
            catch (Exception e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
            return n;
        }, n -> { d.store.put("show_titles", "1"); d.open("bible"); DeskUi.info("Títulos carregados", n + " títulos de secção nesta Bíblia."); });
    }
    void loadComments() {
        FileChooser fc = new FileChooser(); fc.setTitle("Carregar comentários (MyBible)");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Comentários MyBible", "*.commentaries.SQLite3", "*.SQLite3", "*.sqlite3"), new FileChooser.ExtensionFilter("Todos os ficheiros", "*.*"));
        File f = fc.showOpenDialog(d.stage); if (f == null) return;
        d.run("A carregar comentários…", () -> commentsInto(d.store, f, () -> false), r -> { d.store.put("show_comments", "1"); d.open("bible"); DeskUi.info("Comentários carregados", r[1] + " comentários. Toque no sinal ✎ junto a um versículo para os ler."); });
    }
    long commentary() { try { long c = Long.parseLong(d.store.get("commentary", "0")); if (c > 0 && d.store.count("SELECT COUNT(*) FROM commentaries WHERE id=?", c) > 0) return c; List<Object[]> r = d.store.rows("SELECT id FROM commentaries ORDER BY created DESC LIMIT 1"); return r.isEmpty() ? 0 : ((Number) r.get(0)[0]).longValue(); } catch (Exception e) { return 0; } }
    /** Comments of the chosen commentary that touch this chapter: {verse, verse_to (or 999), text, chapter, chapter_to}. */
    List<Object[]> commentsOf(long commentary, String b, int c) {
        try { return d.store.rows("SELECT verse,verse_to,text,chapter,chapter_to FROM comments WHERE commentary=? AND book=? AND chapter<=? AND chapter_to>=? ORDER BY chapter,verse", commentary, b, c, c); } catch (SQLException e) { return new ArrayList<>(); }
    }
    /** A comment is shown at its first verse (verse 1 when it starts in an earlier chapter or covers the whole chapter). */
    static boolean covers(Object[] cm, int chapter, int verse) {
        int c1 = ((Number) cm[3]).intValue(), v1 = ((Number) cm[0]).intValue();
        return chapter == c1 ? verse == Math.max(1, v1) : verse == 1;
    }
    MenuButton extrasMenu(long bible) {
        MenuButton m = new MenuButton("Títulos e comentários");
        CheckMenuItem titles = new CheckMenuItem("Mostrar títulos de secção"); titles.setSelected(!"0".equals(d.store.get("show_titles", "1")));
        titles.setOnAction(e -> { d.store.put("show_titles", titles.isSelected() ? "1" : "0"); d.open("bible"); });
        CheckMenuItem comments = new CheckMenuItem("Mostrar comentários"); comments.setSelected(!"0".equals(d.store.get("show_comments", "1")));
        comments.setOnAction(e -> { d.store.put("show_comments", comments.isSelected() ? "1" : "0"); d.open("bible"); });
        MenuItem loadT = new MenuItem("Carregar títulos (MyBible)…"); loadT.setOnAction(e -> loadTitles(bible));
        MenuItem loadC = new MenuItem("Carregar comentários (MyBible)…"); loadC.setOnAction(e -> loadComments());
        m.getItems().addAll(titles, comments, new SeparatorMenuItem(), loadT, loadC);
        try {
            List<Object[]> all = d.store.rows("SELECT id,title FROM commentaries ORDER BY created"); long chosen = commentary();
            if (all.size() > 1) { m.getItems().add(new SeparatorMenuItem()); ToggleGroup g = new ToggleGroup(); for (Object[] r : all) { RadioMenuItem i = new RadioMenuItem("Comentário: " + r[1]); i.setToggleGroup(g); i.setSelected(((Number) r[0]).longValue() == chosen); i.setOnAction(e -> { d.store.put("commentary", r[0] + ""); d.open("bible"); }); m.getItems().add(i); } }
            if (!all.isEmpty()) { MenuItem del = new MenuItem("Remover o comentário escolhido"); del.setOnAction(e -> { long c = commentary(); if (c > 0 && DeskUi.confirm("Remover este comentário?", "O ficheiro original fica onde estava.")) { try { d.store.update("DELETE FROM comments WHERE commentary=?", c); d.store.update("DELETE FROM commentaries WHERE id=?", c); d.store.put("commentary", "0"); } catch (SQLException ignored) {} d.open("bible"); } }); m.getItems().add(del); }
        } catch (SQLException ignored) {}
        return m;
    }
    static String head(File f) { try (InputStream in = new FileInputStream(f)) { byte[] b = new byte[16]; int n = in.read(b); return n <= 0 ? "" : new String(b, 0, n, StandardCharsets.ISO_8859_1); } catch (IOException e) { return ""; } }
}
