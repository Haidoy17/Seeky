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
    final VBox nav = new VBox(4);
    final Map<String, Button> navButtons = new LinkedHashMap<>();
    String current = "";
    DeskBible bible; DeskStudy study; DeskNotes notes; DeskReader reader; DeskFiles files; DeskMedia media;


    @Override public void start(Stage stage) throws Exception {
        instance = this; this.stage = stage;
        store = new DeskStore();
        bible = new DeskBible(this); study = new DeskStudy(this); notes = new DeskNotes(this); reader = new DeskReader(this); files = new DeskFiles(this); media = new DeskMedia(this);
        root = new BorderPane();
        root.getStyleClass().addAll("root-pane", store.get("theme", "dark"));
        nav.getStyleClass().add("nav"); nav.setPadding(new Insets(18, 12, 18, 12)); nav.setPrefWidth(232);
        Label brand = new Label("Seeky"); brand.getStyleClass().add("brand");
        Label sub = new Label("para Windows"); sub.getStyleClass().add("muted");
        VBox head = new VBox(0, brand, sub); head.setPadding(new Insets(4, 10, 16, 10));
        nav.getChildren().add(head);
        item("home", "Início", "home"); item("bible", "Bíblia", "book"); item("study", "Estudo bíblico", "study"); item("game", "Jogo da Bíblia", "game");
        item("notes", "Bloco de notas", "note"); item("reader", "Livros", "reader"); item("files", "Ficheiros", "folder"); item("media", "Música e vídeo", "music");
        Region gap = new Region(); VBox.setVgrow(gap, Priority.ALWAYS); nav.getChildren().add(gap);
        item("settings", "Definições", "settings");
        status.getStyleClass().add("status"); status.setPadding(new Insets(6, 16, 6, 16)); status.setMaxWidth(Double.MAX_VALUE);
        root.setLeft(nav); root.setCenter(content); root.setBottom(status);
        scene = new Scene(root, 1240, 800);
        scene.getStylesheets().add(DeskMain.class.getResource("/seeky.css").toExternalForm());
        stage.setTitle("Seeky " + VERSION);
        try { stage.getIcons().add(new Image(DeskMain.class.getResourceAsStream("/seeky.png"))); } catch (Exception ignored) {}
        stage.setScene(scene); stage.setMinWidth(900); stage.setMinHeight(600);
        stage.setOnCloseRequest(e -> { DeskSpeech.stop(); media.stop(); store.close(); worker.shutdownNow(); Platform.exit(); });
        open(store.get("last", "home"));
        stage.show();
        status("Pronto · " + DeskStore.home().getPath());
        String shots = getParameters().getNamed().get("screenshots");
        if (shots != null) { demo(); screenshots(new java.io.File(shots), new ArrayList<>(Arrays.asList("home", "bible", "study", "game", "notes", "reader", "files", "media", "settings"))); return; }
        worker.execute(() -> DeskUpdates.check(this, false));
    }
    /** Example content for the build's screenshots only (a few verses written for the test, one note). */
    void demo() {
        try {
            if (store.bible() == 0) {
                java.io.File f = java.io.File.createTempFile("seeky-demo", ".csv");
                java.nio.file.Files.write(f.toPath(), ("GEN,1,1,\"No princípio, Deus criou os céus e a terra.\"\nGEN,1,2,A terra estava sem forma e vazia.\nGEN,1,3,Deus disse: Haja luz. E houve luz.\nJHN,3,16,Porque Deus amou o mundo de tal maneira que deu o seu Filho unigénito.\nPSA,23,1,\"O Senhor é o meu pastor; nada me faltará.\"\nPSA,23,2,Faz-me descansar em verdes pastos.\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                DeskBible.importFile(store, f, "Exemplo (teste)", () -> false); f.delete();
                bible.book = "Génesis"; bible.chapter = 1;
            }
            if (store.count("SELECT COUNT(*) FROM notes") == 0) store.update("INSERT INTO notes(title,body,folder,created,updated) VALUES(?,?,?,?,?)", "Sermão de domingo", "## Fé\nLer Hebreus 11:1 e João 3:16.\n[ ] Preparar slides\n[x] Escolher hinos", "Sermões", System.currentTimeMillis(), System.currentTimeMillis());
        } catch (Exception e) { System.out.println("demo: " + e); }
    }
    /** "--screenshots=<folder>": opens each area, saves a picture of the window and closes (build evidence). */
    void screenshots(java.io.File dir, List<String> left) {
        dir.mkdirs();
        if (left.isEmpty()) { Platform.exit(); System.exit(0); return; }
        String id = left.remove(0);
        if (id.equals("notes-edit")) notes.edit(0, "Estudo: o amor de Deus", "# O amor de Deus\nLer **João 3:16** e Romanos 5:8.\n\n- Deus amou o mundo\n- Deu o seu Filho\n\n[ ] Orar pela família\n[x] Ler 1 João 4\n\n> Porque Deus amou o mundo de tal maneira…");
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
            case "media": view = media.view(); break;
            case "settings": view = DeskSettings.view(this); break;
            default: id = "home"; view = home();
        }
        current = id; store.put("last", id);
        for (Map.Entry<String, Button> e : navButtons.entrySet()) e.getValue().getStyleClass().remove("selected");
        Button b = navButtons.get(id); if (b != null) b.getStyleClass().add("selected");
        content.getChildren().setAll(view);
    }
    /** Shows something inside the current area (a book, a note…). */
    void show(Node view) { content.getChildren().setAll(view); }

    Node home() {
        VBox page = DeskUi.page();
        page.getChildren().add(DeskUi.title(greeting()));
        page.getChildren().add(DeskUi.muted("O mesmo Seeky do telemóvel, no computador: Bíblia e estudo, notas com IA, livros, ficheiros e multimédia."));
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(14);
        Object[][] tiles = {
            {"book", "Bíblia", "Ler, procurar passagens e importar Bíblias", "bible"},
            {"study", "Estudo bíblico", "Plano de leitura, sequência e versículos para decorar", "study"},
            {"game", "Jogo da Bíblia", "Quatro níveis, 60 s por pergunta, recordes", "game"},
            {"note", "Bloco de notas", "Texto rico, tarefas, passagens, Word, PDF e Seeky IA", "notes"},
            {"reader", "Livros", "PDF, EPUB, Word, TXT e outros formatos", "reader"},
            {"folder", "Ficheiros", "Explorar discos, procurar e organizar", "files"},
            {"music", "Música e vídeo", "Leitor com fila e letras sincronizadas", "media"},
            {"settings", "Definições", "Tema, IA, voz e actualizações", "settings"}};
        for (int i = 0; i < tiles.length; i++) { Object[] t = tiles[i]; Node tile = DeskUi.tile((String) t[0], (String) t[1], (String) t[2], () -> open((String) t[3])); GridPane.setHgrow(tile, Priority.ALWAYS); grid.add(tile, i % 2, i / 2); }
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(50); ColumnConstraints c2 = new ColumnConstraints(); c2.setPercentWidth(50); grid.getColumnConstraints().addAll(c1, c2);
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
        if (what != null) status(what);
        worker.execute(() -> {
            try { T v = job.call(); Platform.runLater(() -> { if (what != null) status("Pronto"); if (done != null) done.accept(v); }); }
            catch (Throwable e) { String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); Platform.runLater(() -> { status(m); DeskUi.info("Não foi possível concluir", m); }); }
        });
    }
    void status(String text) { if (Platform.isFxApplicationThread()) status.setText(text); else Platform.runLater(() -> status.setText(text)); }
    void theme(String name) { root.getStyleClass().removeAll("dark", "light"); root.getStyleClass().add(name); store.put("theme", name); }
}
