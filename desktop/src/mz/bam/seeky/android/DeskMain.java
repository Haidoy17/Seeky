package mz.bam.seeky.android;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Seeky Desktop for Windows: the same Seeky (Bible and study, notes, books, files and media) on
 * the computer. The logic is shared with the Android app; the screens are JavaFX.
 */
public final class DeskMain extends Application {
    static DeskMain instance;
    static final String VERSION = DeskVersion.NAME;
    final ExecutorService worker = Executors.newFixedThreadPool(3, r -> { Thread t = new Thread(r, "seeky-desk"); t.setDaemon(true); return t; });
    DeskStore store;
    Stage stage; Scene scene; BorderPane root;
    final StackPane content = new StackPane();
    final Label status = new Label();
    final StackPane stack = new StackPane();
    DeskWindow window;
    int busy;
    javafx.animation.PauseTransition hideStatus;
    final VBox nav = new VBox(4);
    final Map<String, Button> navButtons = new LinkedHashMap<>();
    String current = "";
    DeskBible bible; DeskStudy study; DeskNotes notes; DeskReader reader; DeskFiles files; DeskMedia media; DeskFocus focus; DeskLibrary music, videos;


    @Override public void start(Stage stage) throws Exception {
        instance = this; this.stage = stage;
        store = new DeskStore();
        bible = new DeskBible(this); study = new DeskStudy(this); notes = new DeskNotes(this); reader = new DeskReader(this); files = new DeskFiles(this); media = new DeskMedia(this); focus = new DeskFocus(this); music = new DeskLibrary(this, DeskLibrary.AUDIO); videos = new DeskLibrary(this, DeskLibrary.VIDEO);
        root = new BorderPane();
        root.getStyleClass().add("root-pane"); theme(store.get("theme", DeskThemes.DEFAULT));
        nav.getStyleClass().add("nav"); nav.setPadding(new Insets(18, 12, 18, 12)); nav.setPrefWidth(232);
        Label brand = new Label("Seeky"); brand.getStyleClass().add("brand");
        VBox head = new VBox(0, brand); head.setPadding(new Insets(0, 10, 16, 10));
        nav.getChildren().add(head);
        item("home", "Início", "home"); item("bible", "Bíblia", "book"); item("study", "Estudo bíblico", "study"); item("game", "Jogo da Bíblia", "game");
        item("notes", "Bloco de notas", "note"); item("reader", "Livros", "reader"); item("focus", "Foco", "timer"); item("files", "Ficheiros", "folder"); item("music", "Música", "music"); item("videos", "Vídeos", "video");
        Region gap = new Region(); VBox.setVgrow(gap, Priority.ALWAYS); nav.getChildren().add(gap);
        item("settings", "Definições", "settings");
        // Avisos discretos no canto (sem barra de estado): aparecem e desaparecem sozinhos.
        status.getStyleClass().add("toast"); status.setPadding(new Insets(9, 16, 9, 16)); status.setWrapText(true); status.setMaxWidth(420);
        status.setOpacity(0); status.setVisible(false); status.setMouseTransparent(true);
        StackPane.setAlignment(status, Pos.BOTTOM_RIGHT); StackPane.setMargin(status, new Insets(0, 22, 20, 0));
        stack.getChildren().addAll(content, status); VBox.setVgrow(stack, Priority.ALWAYS);
        window = new DeskWindow(stage);
        window.bar.getChildren().add(0, focus.chip); HBox.setMargin(focus.chip, new Insets(0, 0, 0, 16));
        VBox main = new VBox(window.bar(), stack);
        nav.setPadding(new Insets(14, 12, 18, 12));
        head.setOnMousePressed(window.bar.getOnMousePressed()); head.setOnMouseDragged(window.bar.getOnMouseDragged()); head.setOnMouseReleased(window.bar.getOnMouseReleased()); head.setOnMouseClicked(window.bar.getOnMouseClicked());
        root.setLeft(nav); root.setCenter(main); root.getStyleClass().add("window");
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        scene = new Scene(root, Math.min(1240, screen.getWidth()), Math.min(800, screen.getHeight()));
        window.attach(scene);
        scene.getStylesheets().add(DeskMain.class.getResource("/seeky.css").toExternalForm());
        stage.setTitle("Seeky " + VERSION);
        try { stage.getIcons().add(new Image(DeskMain.class.getResourceAsStream("/seeky.png"))); } catch (Exception ignored) {}
        stage.setScene(scene); stage.setMinWidth(760); stage.setMinHeight(520);
        if (DeskThemes.SCHEDULE.equals(store.get("theme", ""))) { javafx.animation.Timeline schedule = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(5), e -> { if (DeskThemes.SCHEDULE.equals(store.get("theme", ""))) theme(DeskThemes.SCHEDULE); })); schedule.setCycleCount(javafx.animation.Animation.INDEFINITE); schedule.play(); }
        stage.setOnCloseRequest(e -> { focus.stop(); store.put("maximized", window.maximized ? "1" : "0"); DeskSpeech.stop(); media.stop(); store.close(); worker.shutdownNow(); Platform.exit(); });
        open(store.get("last", "home"));
        stage.show();
        if ("1".equals(store.get("maximized", "0"))) window.maximize();
        stage.setOnHidden(e -> store.put("maximized", window.maximized ? "1" : "0"));
        String shots = getParameters().getNamed().get("screenshots");
        if (shots != null) { demo(); screenshots(new java.io.File(shots), new ArrayList<>(Arrays.asList("home", "bible", "study", "game", "notes", "reader", "reader-grid", "reader-list", "book", "marks", "focus", "files", "music", "music-albums", "videos", "settings"))); return; }
        worker.execute(() -> DeskUpdates.check(this, false));
    }
    /** Example content for the build's screenshots only (a few verses written for the test, one note). */
    java.io.File demoBook;
    void demo() {
        try {
            if (store.bible() == 0) {
                DeskBible.importFile(store, DeskSelfTest.myBible(DeskStore.folder("exemplo")), "Exemplo (teste)", () -> false);
                bible.book = "Génesis"; bible.chapter = 1;
            }
            demoBook = new java.io.File(DeskStore.home(), "O caminho da fé.txt");
            if (!demoBook.isFile()) {
                java.nio.file.Files.write(demoBook.toPath(), ("O caminho da fé\n\nA fé começa com um passo pequeno. Abraão saiu sem saber para onde ia, mas confiou na promessa.\n\nComo diz Hebreus 11:1, a fé é a certeza das coisas que se esperam e a convicção dos factos que se não vêem.\n\nQuem caminha pela fé aprende a esperar, a ouvir e a perseverar, mesmo quando o caminho parece longo.\n\nNo fim, a fé não é um sentimento, mas uma confiança que se escolhe todos os dias.").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                DeskMarks.add(store, demoBook.getPath(), "O caminho da fé", 0, "Parte 1", "Abraão saiu sem saber para onde ia", DeskMarks.COLORS[0], "Génesis 12");
                DeskMarks.add(store, demoBook.getPath(), "O caminho da fé", 0, "Parte 1", "uma confiança que se escolhe todos os dias", DeskMarks.COLORS[1], "");
                store.update("INSERT INTO focus(day,subject,minutes,ended) VALUES(?,?,?,?)", BibleStudyCore.day(System.currentTimeMillis()), "Estudo bíblico", 50, System.currentTimeMillis());
                store.update("INSERT INTO focus(day,subject,minutes,ended) VALUES(?,?,?,?)", BibleStudyCore.day(System.currentTimeMillis() - 86400000L), "Leitura", 25, System.currentTimeMillis() - 86400000L);
            }
            if (store.count("SELECT COUNT(*) FROM tracks") == 0) {
                java.io.File m = DeskStore.folder("exemplo/musica"); List<java.io.File> songs = new ArrayList<>();
                String[][] demoSongs = {{"Grande é o Senhor", "Coro Seeky", "Louvores", "2019", "Gospel"}, {"Rocha eterna", "Coro Seeky", "Louvores", "2019", "Gospel"}, {"Aleluia", "Banda da Fé", "Ao vivo em Maputo", "2021", "Gospel"}, {"Graça", "Ana Mabunda", "Caminho", "2023", "Pop"}, {"Luz do mundo", "Ana Mabunda", "Caminho", "2023", "Pop"}, {"Salmo 23", "Banda da Fé", "Ao vivo em Maputo", "2021", "Gospel"}};
                for (int i = 0; i < demoSongs.length; i++) songs.add(DeskSelfTest.mp3(m, "faixa" + i + ".mp3", demoSongs[i][0], demoSongs[i][1], demoSongs[i][2], demoSongs[i][3], demoSongs[i][4], (i % 2 + 1) + ""));
                DeskLibrary.addFiles(store, DeskLibrary.AUDIO, songs);
                for (int i = 0; i < 5; i++) DeskLibrary.played(store, songs.get(0).getAbsolutePath()); for (int i = 0; i < 3; i++) DeskLibrary.played(store, songs.get(3).getAbsolutePath()); DeskLibrary.played(store, songs.get(2).getAbsolutePath());
                store.update("UPDATE tracks SET favorite=1 WHERE path=?", songs.get(3).getAbsolutePath());
            }
            if (store.count("SELECT COUNT(*) FROM books") <= 1) {
                java.io.File shelf = DeskStore.folder("exemplo/livros"); List<java.io.File> books = new ArrayList<>();
                Object[][] demoBooks = {{"Confissões", 0x6b2f3f}, {"O peregrino", 0x2f4f6b}, {"Cartas de esperança", 0x7a5a2f}, {"Graça abundante", 0x2f5d50}, {"A cidade de Deus", 0x3f4b7a}, {"Mere christianity", 0x5b3a6b}, {"Salmos para cada dia", 0x4a6b3a}};
                for (int i = 0; i < demoBooks.length; i++) books.add(DeskSelfTest.epub(shelf, "livro" + i + ".epub", (String) demoBooks[i][0], (Integer) demoBooks[i][1]));
                java.io.File pdf = new java.io.File(shelf, "Estudo de Romanos.pdf"); try (java.io.OutputStream o = new java.io.FileOutputStream(pdf)) { DeskPdf.note("Estudo de Romanos", "# Estudo de Romanos\nCapítulo 1 a 8: a justiça de Deus.", shelf, o); } books.add(pdf);
                DeskReader.importBooks(store, books);
                store.update("UPDATE books SET position=3,total=10,opened=? WHERE path=?", System.currentTimeMillis() - 3600000, books.get(1).getPath());
                store.update("UPDATE books SET position=8,total=10,opened=? WHERE path=?", System.currentTimeMillis() - 7200000, books.get(3).getPath());
            }
            if (store.count("SELECT COUNT(*) FROM notes") == 0) store.update("INSERT INTO notes(title,body,folder,created,updated) VALUES(?,?,?,?,?)", "Sermão de domingo", "## Fé\nLer Hebreus 11:1 e João 3:16.\n[ ] Preparar slides\n[x] Escolher hinos", "Sermões", System.currentTimeMillis(), System.currentTimeMillis());
        } catch (Exception e) { System.out.println("demo: " + e); }
    }
    /** "--screenshots=<folder>": opens each area, saves a picture of the window and closes (build evidence). */
    void screenshots(java.io.File dir, List<String> left) {
        dir.mkdirs();
        if (left.isEmpty()) { Platform.exit(); System.exit(0); return; }
        String id = left.remove(0);
        if (id.equals("book")) reader.open(demoBook, 0, 0);
        else if (id.startsWith("reader-")) { store.put("books_mode", id.endsWith("grid") ? "1" : "2"); open("reader"); store.put("books_mode", "0"); }
        else if (id.equals("music-albums")) { music.tab = "Álbuns"; open("music"); music.tab = "Início"; }
        else if (id.equals("notes-edit")) notes.edit(0, "Estudo: o amor de Deus", "# O amor de Deus\nLer **João 3:16** e Romanos 5:8.\n\n- Deus amou o mundo\n- Deu o seu Filho\n\n[ ] Orar pela família\n[x] Ler 1 João 4\n\n> Porque Deus amou o mundo de tal maneira…");
        else open(id);
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1500));
        wait.setOnFinished(e -> {
            try { javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(scene.snapshot(null), null), "png", new java.io.File(dir, "desktop-" + id + ".png")); } catch (Exception x) { System.out.println("screenshot " + id + ": " + x); }
            if (id.equals("notes")) left.add(0, "notes-edit");
            screenshots(dir, left);
        });
        wait.play();
    }

    void item(String id, String label, String icon) {
        Button b = new Button(label, DeskUi.icon(icon, 20)); b.getStyleClass().add("nav-item"); b.setMaxWidth(Double.MAX_VALUE); b.setAlignment(Pos.CENTER_LEFT); b.setGraphicTextGap(12);
        b.setOnAction(e -> open(id)); navButtons.put(id, b); nav.getChildren().add(b);
    }
    /** Shows a module. */
    void open(String id) {
        Node view;
        switch (id) {
            case "bible": view = bible.view(); break;
            case "study": view = study.view(); break;
            case "game": view = study.game(); break;
            case "notes": view = notes.view(); break;
            case "reader": view = reader.view(); break;
            case "files": view = files.view(); break;
            case "music": case "media": view = music.view(); id = "music"; break;
            case "videos": view = videos.view(); break;
            case "focus": view = focus.view(); break;
            case "marks": view = DeskMarks.view(this); id = "reader"; break;
            case "settings": view = DeskSettings.view(this); break;
            default: id = "home"; view = home();
        }
        current = id; store.put("last", id);
        for (Map.Entry<String, Button> e : navButtons.entrySet()) e.getValue().getStyleClass().remove("selected");
        Button b = navButtons.get(id); if (b != null) b.getStyleClass().add("selected");
        show(view);
    }
    /** Shows something inside the current area (a book, a note…), with a short fade and rise. */
    void show(Node view) {
        content.getChildren().setAll(view);
        if (getParameters() != null && getParameters().getNamed().containsKey("screenshots")) return;
        view.setOpacity(0); view.setTranslateY(10);
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(170), view); fade.setToValue(1);
        javafx.animation.TranslateTransition rise = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(220), view); rise.setToY(0); rise.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        new javafx.animation.ParallelTransition(fade, rise).play();
    }

    Node home() {
        VBox page = DeskUi.page();
        page.getChildren().add(DeskUi.title(greeting()));
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(14);
        Object[][] tiles = {
            {"book", "Bíblia", "Ler, procurar passagens e importar Bíblias", "bible"},
            {"study", "Estudo bíblico", "Plano de leitura, sequência e versículos para decorar", "study"},
            {"game", "Jogo da Bíblia", "Quatro níveis, 60 s por pergunta, recordes", "game"},
            {"note", "Bloco de notas", "Texto rico, tarefas, passagens, Word, PDF e Seeky IA", "notes"},
            {"reader", "Livros", "PDF, EPUB, Word, TXT e outros formatos", "reader"},
            {"folder", "Ficheiros", "Explorar discos, procurar e organizar", "files"},
            {"music", "Música", "Faixas, álbuns, artistas, anos e mais ouvidas", "music"},
            {"video", "Vídeos", "Pastas, vistos recentemente e favoritos", "videos"},
            {"timer", "Foco", "Pomodoro por disciplina e tempo de estudo da semana", "focus"},
            {"marker", "Marcações", "Tudo o que realçou nos livros, com as suas notas", "marks"},
            {"settings", "Definições", "Tema, IA, voz e actualizações", "settings"}};
        List<Node> cells = new ArrayList<>();
        for (Object[] t : tiles) { Node tile = DeskUi.tile((String) t[0], (String) t[1], (String) t[2], () -> open((String) t[3])); GridPane.setHgrow(tile, Priority.ALWAYS); ((Region) tile).setMaxWidth(Double.MAX_VALUE); cells.add(tile); }
        DeskUi.responsive(grid, cells, 300);
        page.getChildren().add(grid);
        VBox today = study.todayCard(); if (today != null) page.getChildren().add(today);
        VBox recent = reader.recentCard(); if (recent != null) page.getChildren().add(recent);
        return DeskUi.scroll(page);
    }
    String greeting() { int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return h < 12 ? "Bom dia" : h < 19 ? "Boa tarde" : "Boa noite"; }

    // ---- threads and messages ----
    interface Job<T> { T call() throws Exception; }
    /** Runs work off the window and shows the result (or the error) back on it. */
    <T> void run(String what, Job<T> job, Consumer<T> done) {
        if (what != null) { busy++; status(what); }
        worker.execute(() -> {
            try { T v = job.call(); Platform.runLater(() -> { if (what != null) idle(); if (done != null) done.accept(v); }); }
            catch (Throwable e) { String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); Platform.runLater(() -> { if (what != null) idle(); DeskUi.info("Não foi possível concluir", m); }); }
        });
    }
    void idle() { busy = Math.max(0, busy - 1); if (busy == 0) hide(600); }
    /** A short notice in the corner; it fades away by itself (stays while work is running). */
    void status(String text) {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(() -> status(text)); return; }
        if (text == null || text.trim().isEmpty()) { hide(0); return; }
        status.setText(text); status.setVisible(true);
        javafx.animation.FadeTransition in = new javafx.animation.FadeTransition(javafx.util.Duration.millis(150), status); in.setToValue(1); in.play();
        if (busy == 0) hide(3200); else if (hideStatus != null) hideStatus.stop();
    }
    void hide(int after) {
        if (hideStatus != null) hideStatus.stop();
        hideStatus = new javafx.animation.PauseTransition(javafx.util.Duration.millis(after));
        hideStatus.setOnFinished(e -> { javafx.animation.FadeTransition out = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), status); out.setToValue(0); out.setOnFinished(x -> { if (status.getOpacity() == 0) status.setVisible(false); }); out.play(); });
        hideStatus.play();
    }
    /** Applies a theme by name (Seeky escuro/claro, the phone's themes or Horário) and remembers it. */
    void theme(String name) {
        DeskThemes.current = DeskThemes.find(name);
        root.getStyleClass().removeAll("dark", "light"); root.getStyleClass().add(DeskThemes.current.light ? "light" : "dark");
        root.setStyle(DeskThemes.current.css()); store.put("theme", name);
    }
}
