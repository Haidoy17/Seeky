package mz.bam.seeky.android;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import java.io.File;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modo Flash no computador, como no telemóvel: um botão redondo que fica por cima das outras janelas
 * (arrasta-se e encosta à margem) e abre um painel de pesquisa: Flash (índice, instantâneo), Completa
 * (também notas, livros, Bíblia, músicas e marcações) e Pastas (procura agora no disco), com
 * Tudo, PDF, Documentos, Imagens, Vídeos e Áudio. Pode continuar ligado com o Seeky fechado.
 */
final class DeskFlash {
    final DeskMain d;
    final DeskFlashIndex index = DeskFlashIndex.standard();
    Stage owner, bubble, panel; Scene panelScene;
    TextField input; Label status; ListView<Result> results; String category = "Tudo", mode = "flash";
    final Map<String, ToggleButton> chips = new LinkedHashMap<>(); ToggleButton flashB, fullB, foldersB;
    final AtomicInteger generation = new AtomicInteger();
    volatile boolean indexing;
    double dragX, dragY, pressX, pressY;

    DeskFlash(DeskMain d) { this.d = d; }
    boolean enabled() { return "1".equals(d.store.get("flash_on", "1")); }
    int size() { switch (d.store.get("flash_size", "1")) { case "0": return 46; case "2": return 70; default: return 58; } }

    static final class Result {
        final String icon, title, sub; final Runnable open; final File file;
        Result(String icon, String title, String sub, File file, Runnable open) { this.icon = icon; this.title = title; this.sub = sub; this.file = file; this.open = open; }
    }

    // ================================================================ botão
    /** A hidden window owns the button and the panel, so they never get a taskbar entry of their own. */
    void ensureOwner() {
        if (owner != null) return;
        owner = new Stage(StageStyle.UTILITY); owner.setOpacity(0); owner.setWidth(1); owner.setHeight(1); owner.setX(-10000); owner.setY(-10000);
        owner.setScene(new Scene(new Pane(), 1, 1)); owner.show();
    }
    void show() {
        if (!enabled()) return;
        ensureOwner();
        if (bubble == null) build();
        bubble.show(); bubble.toFront();
    }
    void hide() { if (bubble != null) bubble.hide(); if (panel != null) panel.hide(); }
    void restart() { if (bubble != null) { bubble.hide(); bubble = null; } if (enabled()) show(); }
    void build() {
        int s = size();
        StackPane circle = new StackPane(DeskUi.icon("search", s * 0.42)); circle.getStyleClass().add("flash-bubble"); circle.setMinSize(s, s); circle.setMaxSize(s, s);
        StackPane root = new StackPane(circle); root.setPadding(new Insets(10)); root.setStyle("-fx-background-color: transparent;"); root.getStyleClass().add("root-pane");
        root.setStyle(DeskThemes.current.css() + "-fx-background-color: transparent;");
        Scene scene = new Scene(root, s + 20, s + 20); scene.setFill(Color.TRANSPARENT); scene.getStylesheets().add(DeskMain.class.getResource("/seeky.css").toExternalForm());
        bubble = new Stage(StageStyle.TRANSPARENT); bubble.initOwner(owner); bubble.setAlwaysOnTop(true); bubble.setScene(scene); bubble.setTitle("Seeky Flash");
        Rectangle2D v = Screen.getPrimary().getVisualBounds();
        bubble.setX(clamp(Double.parseDouble(d.store.get("flash_x", String.valueOf(v.getMaxX() - s - 28))), v.getMinX(), v.getMaxX() - s - 20));
        bubble.setY(clamp(Double.parseDouble(d.store.get("flash_y", String.valueOf(v.getMinY() + v.getHeight() * 0.55))), v.getMinY(), v.getMaxY() - s - 20));
        Tooltip.install(circle, new Tooltip("Pesquisar (Flash)"));
        ScaleTransition press = new ScaleTransition(Duration.millis(110), circle);
        circle.setOnMousePressed(e -> { dragX = e.getScreenX() - bubble.getX(); dragY = e.getScreenY() - bubble.getY(); pressX = e.getScreenX(); pressY = e.getScreenY(); press.stop(); press.setToX(0.9); press.setToY(0.9); press.playFromStart(); });
        circle.setOnMouseDragged(e -> { bubble.setX(e.getScreenX() - dragX); bubble.setY(e.getScreenY() - dragY); circle.getStyleClass().add("lifted"); if (panel != null && panel.isShowing()) panel.hide(); });
        circle.setOnMouseReleased(e -> {
            press.stop(); press.setToX(1); press.setToY(1); press.playFromStart(); circle.getStyleClass().remove("lifted");
            boolean moved = Math.hypot(e.getScreenX() - pressX, e.getScreenY() - pressY) > 6;
            if (moved) { snap(); return; }
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) { menu().show(circle, e.getScreenX(), e.getScreenY()); return; }
            toggle();
        });
    }
    ContextMenu menu() {
        MenuItem open = new MenuItem("Abrir Seeky"); open.setOnAction(e -> d.bringToFront());
        MenuItem search = new MenuItem("Pesquisar"); search.setOnAction(e -> toggle());
        MenuItem off = new MenuItem("Desligar flutuante"); off.setOnAction(e -> turnOff());
        return new ContextMenu(open, search, new SeparatorMenuItem(), off);
    }
    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    Rectangle2D screen(double x, double y) { List<Screen> s = Screen.getScreensForRectangle(x, y, 1, 1); return (s.isEmpty() ? Screen.getPrimary() : s.get(0)).getVisualBounds(); }
    /** Released: inside the screen and, if chosen, against the nearest side edge. */
    void snap() {
        Rectangle2D v = screen(bubble.getX() + bubble.getWidth() / 2, bubble.getY() + bubble.getHeight() / 2);
        double x = clamp(bubble.getX(), v.getMinX() - 6, v.getMaxX() - bubble.getWidth() + 6), y = clamp(bubble.getY(), v.getMinY(), v.getMaxY() - bubble.getHeight());
        if (!"0".equals(d.store.get("flash_edge", "1"))) x = (x + bubble.getWidth() / 2 < v.getMinX() + v.getWidth() / 2) ? v.getMinX() - 4 : v.getMaxX() - bubble.getWidth() + 4;
        bubble.setX(x); bubble.setY(y);
        d.store.put("flash_x", String.valueOf((int) x)); d.store.put("flash_y", String.valueOf((int) y));
    }
    void turnOff() { d.store.put("flash_on", "0"); hide(); d.status("Botão Flash desligado. Pode ligá-lo em Definições."); if (!d.stage.isShowing()) d.bringToFront(); }

    // ================================================================ painel
    void toggle() { if (panel != null && panel.isShowing()) panel.hide(); else open(""); }
    /** Opens the search panel beside the button (or in the middle of the screen when the button is off). */
    void open(String text) {
        ensureOwner();
        if (panel == null) buildPanel();
        panelScene.getRoot().setStyle(DeskThemes.current.css());
        panelScene.getRoot().getStyleClass().removeAll("dark", "light"); panelScene.getRoot().getStyleClass().add(DeskThemes.current.light ? "light" : "dark");
        double w = 460, h = 600;
        Rectangle2D v; double x, y;
        if (bubble != null && bubble.isShowing()) {
            v = screen(bubble.getX(), bubble.getY());
            boolean right = bubble.getX() + bubble.getWidth() / 2 > v.getMinX() + v.getWidth() / 2;
            x = right ? bubble.getX() - w - 4 : bubble.getX() + bubble.getWidth() + 4; y = bubble.getY() + bubble.getHeight() / 2 - h / 2;
        } else { v = Screen.getPrimary().getVisualBounds(); x = v.getMinX() + (v.getWidth() - w) / 2; y = v.getMinY() + v.getHeight() * 0.15; }
        panel.setX(clamp(x, v.getMinX() + 6, v.getMaxX() - w - 6)); panel.setY(clamp(y, v.getMinY() + 6, v.getMaxY() - h - 6));
        panel.show(); panel.toFront(); panel.requestFocus();
        if (text != null && !text.isEmpty()) input.setText(text);
        input.requestFocus(); input.selectAll(); search();
        if (index.built() == 0 || System.currentTimeMillis() - index.built() > 6 * 3600_000L) reindex(false);
    }
    void buildPanel() {
        VBox root = new VBox(8); root.getStyleClass().addAll("root-pane", "flash-panel"); root.setPadding(new Insets(14, 16, 12, 16));
        Label brand = new Label("Seeky"); brand.getStyleClass().add("brand"); brand.setStyle("-fx-font-size: 22px;");
        Label tag = DeskUi.muted("Flash");
        Button close = DeskUi.iconButton("back", "Recolher", () -> panel.hide()); close.getGraphic().setRotate(-90);
        HBox head = DeskUi.row(brand, tag, DeskUi.grow(), close);
        input = new TextField(); input.setPromptText("Pesquisar ficheiros"); HBox.setHgrow(input, Priority.ALWAYS); input.getStyleClass().add("flash-input");
        Button clear = DeskUi.iconButton("add", "Limpar pesquisa", () -> input.setText("")); clear.getGraphic().setRotate(45);
        HBox field = new HBox(8, DeskUi.icon("search", 20), input, clear); field.setAlignment(Pos.CENTER_LEFT); field.getStyleClass().add("flash-field"); field.setPadding(new Insets(2, 6, 2, 12));
        HBox cats = new HBox(6);
        for (String c : DeskFlashIndex.CATEGORIES) { ToggleButton b = new ToggleButton(c); b.getStyleClass().addAll("chip", "small"); b.setMinWidth(Region.USE_PREF_SIZE); b.setOnAction(e -> { category = c; refreshChips(); search(); }); chips.put(c, b); cats.getChildren().add(b); }
        refreshChips();
        status = DeskUi.muted("Recentes");
        results = new ListView<>(); results.getStyleClass().add("library-list"); VBox.setVgrow(results, Priority.ALWAYS);
        results.setCellFactory(v -> new ListCell<Result>() {
            @Override protected void updateItem(Result r, boolean empty) {
                super.updateItem(r, empty); setText(null); getStyleClass().remove("track-cell");
                if (empty || r == null) { setGraphic(null); setOnMouseClicked(null); setContextMenu(null); return; }
                Label t = DeskUi.heading(r.title); t.setStyle("-fx-font-size: 14px;"); t.setWrapText(false);
                VBox words = new VBox(1, t, DeskUi.muted(r.sub)); words.setMinWidth(0); HBox.setHgrow(words, Priority.ALWAYS);
                HBox row = new HBox(12, DeskLibrary.badge(r.icon), words); row.setAlignment(Pos.CENTER_LEFT); setGraphic(row); getStyleClass().add("track-cell");
                setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) launch(r); });
                if (r.file != null) {
                    MenuItem folder = new MenuItem("Abrir a pasta"); folder.setOnAction(e -> DeskFiles.system(r.file.getParentFile()));
                    MenuItem windows = new MenuItem("Abrir no Windows"); windows.setOnAction(e -> DeskFiles.system(r.file));
                    MenuItem copy = new MenuItem("Copiar o caminho"); copy.setOnAction(e -> DeskBible.copy(r.file.getAbsolutePath()));
                    setContextMenu(new ContextMenu(folder, windows, copy));
                } else setContextMenu(null);
            }
        });
        results.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && results.getSelectionModel().getSelectedItem() != null) launch(results.getSelectionModel().getSelectedItem()); });
        ToggleGroup g = new ToggleGroup();
        flashB = new ToggleButton("Flash"); fullB = new ToggleButton("Completa"); foldersB = new ToggleButton("Pastas");
        for (ToggleButton b : new ToggleButton[]{flashB, fullB, foldersB}) { b.setToggleGroup(g); b.getStyleClass().add("chip"); b.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(b, Priority.ALWAYS); }
        Tooltip.install(flashB, new Tooltip("Instantâneo: os nomes dos ficheiros já indexados")); Tooltip.install(fullB, new Tooltip("Também notas, livros, Bíblia, músicas e marcações")); Tooltip.install(foldersB, new Tooltip("Procura agora nas pastas (encontra ficheiros novos)"));
        flashB.setOnAction(e -> { mode = "flash"; refreshModes(); search(); }); fullB.setOnAction(e -> { mode = "full"; refreshModes(); search(); }); foldersB.setOnAction(e -> { mode = "folders"; refreshModes(); search(); });
        mode = d.store.get("flash_mode", "flash"); refreshModes();
        HBox modes = new HBox(6, flashB, fullB, foldersB);
        Hyperlink open = new Hyperlink("Abrir Seeky"); open.getStyleClass().add("link"); open.setOnAction(e -> { panel.hide(); d.bringToFront(); });
        Button power = DeskUi.iconButton("power", "Desligar flutuante", this::turnOff);
        HBox foot = DeskUi.row(open, DeskUi.grow(), power);
        root.getChildren().addAll(head, field, cats, status, results, modes, foot);
        panelScene = new Scene(root, 460, 600); panelScene.setFill(Color.TRANSPARENT); panelScene.getStylesheets().add(DeskMain.class.getResource("/seeky.css").toExternalForm());
        panel = new Stage(StageStyle.TRANSPARENT); panel.initOwner(owner); panel.setAlwaysOnTop(true); panel.setScene(panelScene); panel.setTitle("Seeky Flash");
        panelScene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) panel.hide();
            else if (e.getCode() == KeyCode.DOWN && input.isFocused()) { results.requestFocus(); results.getSelectionModel().selectFirst(); }
        });
        input.setOnAction(e -> { if (!results.getItems().isEmpty()) launch(results.getItems().get(0)); });
        javafx.animation.PauseTransition later = new javafx.animation.PauseTransition(Duration.millis(140));
        later.setOnFinished(e -> search());
        input.textProperty().addListener((o, a, b) -> later.playFromStart());
        // Click elsewhere: the panel folds away, like on the phone.
        panel.focusedProperty().addListener((o, a, focused) -> { if (!focused && !d.getParameters().getNamed().containsKey("screenshots")) Platform.runLater(() -> { if (!panel.isFocused() && !anyPopup()) panel.hide(); }); });
    }
    boolean anyPopup() { for (javafx.stage.Window w : javafx.stage.Window.getWindows()) if (w instanceof ContextMenu && w.isShowing()) return true; return false; }
    void refreshChips() { for (Map.Entry<String, ToggleButton> c : chips.entrySet()) c.getValue().setSelected(c.getKey().equals(category)); }
    void refreshModes() { flashB.setSelected(mode.equals("flash")); fullB.setSelected(mode.equals("full")); foldersB.setSelected(mode.equals("folders")); d.store.put("flash_mode", mode); input.setPromptText(mode.equals("full") ? "Pesquisar ficheiros, notas, livros e a Bíblia" : "Pesquisar ficheiros"); }

    void launch(Result r) { if (panel != null) panel.hide(); r.open.run(); }
    /** A file found: books open in the reader, music and videos in the player, the rest in Windows. */
    void openFile(File f) {
        if (!f.exists()) { d.status("O ficheiro já não existe: " + f.getName()); return; }
        if (DeskReader.accepts(f.getName())) { d.bringToFront(); d.open("reader"); d.reader.open(f); }
        else if (DeskLibrary.playable(f.getName())) { d.bringToFront(); d.media.playFiles(Collections.singletonList(f), 0); }
        else DeskFiles.system(f);
    }
    static String iconOf(String category) { switch (category) { case "PDF": return "reader"; case "Documentos": return "note"; case "Imagens": return "image"; case "Vídeos": return "video"; case "Áudio": return "music"; default: return "folder"; } }
    Result file(File f, String category, long size, long modified) {
        String sub = (f.getParent() == null ? "" : f.getParentFile().getName()) + " · " + Rules.bytes(size) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(modified));
        return new Result(iconOf(category), f.getName(), sub, f, () -> openFile(f));
    }

    void search() {
        String q = input.getText().trim(), cat = category, m = mode; int token = generation.incrementAndGet();
        if (m.equals("folders") && q.isEmpty()) { results.setItems(FXCollections.observableArrayList()); status.setText("Escreva o nome a procurar nas pastas"); return; }
        status.setText(q.isEmpty() ? "Recentes" : m.equals("folders") ? "A procurar nas pastas…" : "A procurar…");
        d.worker.execute(() -> {
            List<Result> out = new ArrayList<>(); String note = "";
            try {
                if (m.equals("folders")) out.addAll(walk(q, cat, token));
                else {
                    if (m.equals("full") && !q.isEmpty()) out.addAll(seeky(q, cat));
                    for (DeskFlashIndex.Hit h : index.search(q, cat, q.isEmpty() ? 25 : 60)) out.add(file(new File(h.path), h.category, h.size, h.modified));
                    if (index.built() == 0) note = indexing ? "A preparar o índice…" : "Índice vazio";
                }
            } catch (Exception e) { note = e.getMessage() == null ? "" : e.getMessage(); }
            final String extra = note;
            Platform.runLater(() -> {
                if (token != generation.get()) return;
                results.setItems(FXCollections.observableArrayList(out));
                status.setText(!extra.isEmpty() && out.isEmpty() ? extra : q.isEmpty() ? "Recentes" : out.size() + (out.size() == 1 ? " resultado" : " resultados") + (out.size() >= 60 ? "+" : ""));
                results.setPlaceholder(DeskUi.muted(q.isEmpty() ? "" : "Nada encontrado com \"" + q + "\"."));
            });
        });
    }
    /** "Completa": the Seeky's own things too. */
    List<Result> seeky(String q, String cat) throws SQLException {
        List<Result> out = new ArrayList<>(); String like = "%" + q + "%";
        if (cat.equals("Tudo") || cat.equals("Documentos")) {
            for (Object[] r : d.store.rows("SELECT id,title,body FROM notes WHERE title LIKE ? OR body LIKE ? ORDER BY updated DESC LIMIT 8", like, like)) {
                long id = ((Number) r[0]).longValue(); String title = NotepadText.title(r[1] + "", r[2] + ""), body = r[2] + "";
                out.add(new Result("note", title, "Nota do Seeky", null, () -> { d.bringToFront(); d.open("notes"); d.notes.edit(id, r[1] + "", body); }));
            }
            for (Object[] r : d.store.rows("SELECT path,title FROM marks WHERE text LIKE ? OR note LIKE ? ORDER BY created DESC LIMIT 5", like, like)) {
                out.add(new Result("marker", r[1] + "", "Marcação", null, () -> { d.bringToFront(); d.show(DeskMarks.view(d)); }));
            }
        }
        if (cat.equals("Tudo") || cat.equals("Documentos") || cat.equals("PDF"))
            for (Object[] r : d.store.rows("SELECT path,title,kind FROM books WHERE title LIKE ? ORDER BY opened DESC LIMIT 6", like)) {
                File f = new File(r[0] + ""); if (cat.equals("PDF") && !"pdf".equals(r[2])) continue;
                out.add(new Result("reader", r[1] + "", "Livro na estante", f, () -> { d.bringToFront(); d.open("reader"); d.reader.open(f); }));
            }
        if (cat.equals("Tudo") || cat.equals("Áudio") || cat.equals("Vídeos"))
            for (DeskLibrary.Track t : DeskLibrary.tracks(d.store, cat.equals("Vídeos") ? DeskLibrary.VIDEO : DeskLibrary.AUDIO, "title LIKE ? OR artist LIKE ? OR album LIKE ?", Arrays.asList(like, like, like), "plays DESC,title", 6))
                out.add(new Result(cat.equals("Vídeos") ? "video" : "music", t.title, (t.artist.isEmpty() ? "" : t.artist + " · ") + (cat.equals("Vídeos") ? "Vídeo" : "Música"), new File(t.path), () -> { d.bringToFront(); d.media.play(Collections.singletonList(t), 0); }));
        if (cat.equals("Tudo")) {
            long bible = d.store.bible();
            BibleReferences.Reference ref = BibleReferences.lookup(q);
            if (bible > 0 && ref != null) { String text = d.store.passage(bible, ref).replaceAll("\\[\\d+\\] ", ""); if (!text.isEmpty()) out.add(0, new Result("book", ref.title, text.length() > 90 ? text.substring(0, 90) + "…" : text, null, () -> { d.bringToFront(); d.bible.book = ref.book; d.bible.chapter = ref.chapter; d.store.put("bible_book", ref.book); d.store.put("bible_chapter", ref.chapter + ""); d.open("bible"); })); }
            else if (bible > 0 && q.length() >= 3)
                for (Object[] r : d.store.rows("SELECT book,chapter,verse,text FROM verses WHERE bible=? AND text LIKE ? LIMIT 5", bible, like)) {
                    String b = r[0] + ""; int c = ((Number) r[1]).intValue(); String t = r[3] + "";
                    out.add(new Result("book", b + " " + c + ":" + r[2], t.length() > 90 ? t.substring(0, 90) + "…" : t, null, () -> { d.bringToFront(); d.bible.book = b; d.bible.chapter = c; d.store.put("bible_book", b); d.store.put("bible_chapter", c + ""); d.open("bible"); }));
                }
        }
        return out;
    }
    /** "Pastas": walks the folders now (finds what the index does not know yet). */
    List<Result> walk(String q, String cat, int token) {
        List<Result> out = new ArrayList<>(); String[] words = Rules.normalize(q).split("\\s+"); long deadline = System.currentTimeMillis() + 15000;
        for (File root : DeskFlashIndex.roots(d.store)) {
            try {
                DiskWalker.walk(root, false, 500000, deadline, new DiskWalker.Visitor() {
                    public void file(File f) {
                        if (out.size() >= 200) return; String n = Rules.normalize(f.getName()); for (String w : words) if (!n.contains(w)) return;
                        String c = DeskFlashIndex.category(f.getName()); if (!cat.equals("Tudo") && !cat.equals(c)) return;
                        out.add(DeskFlash.this.file(f, c, f.length(), f.lastModified()));
                    }
                    public void unreadable(File dir) {}
                    public boolean cancelled() { return token != generation.get() || out.size() >= 200; }
                });
            } catch (Exception ignored) {}
            if (token != generation.get() || out.size() >= 200 || System.currentTimeMillis() > deadline) break;
        }
        out.sort(Comparator.comparingLong((Result r) -> r.file.lastModified()).reversed());
        return out;
    }
    /** Builds the index in the background (at start when older than 6 hours, or when asked). */
    void reindex(boolean asked) {
        if (indexing) return; indexing = true;
        d.worker.execute(() -> {
            try { int n = index.rebuild(DeskFlashIndex.roots(d.store), () -> false); if (asked) d.status(n + " ficheiros no índice do Flash."); Platform.runLater(() -> { if (panel != null && panel.isShowing()) search(); }); }
            catch (Exception e) { if (asked) d.status("Não foi possível actualizar o índice: " + e.getMessage()); }
            finally { indexing = false; }
        });
    }

    // ================================================================ arranque com o Windows
    static File startup() { String appData = System.getenv("APPDATA"); return appData == null ? null : new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup\\Seeky Flash.lnk"); }
    static boolean startsWithWindows() { File f = startup(); return f != null && f.isFile(); }
    /** A shortcut in the Startup folder that opens only the Flash button when Windows starts. */
    static void startWithWindows(boolean on) throws Exception {
        File link = startup(); if (link == null) throw new Exception("Disponível só no Windows.");
        if (!on) { link.delete(); return; }
        String exe = System.getProperty("seeky.launcher", ""); if (exe.isEmpty() || !new File(exe).isFile()) throw new Exception("Abra o Seeky pelo atalho do menu Iniciar para activar esta opção.");
        String script = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut($env:SEEKY_LINK);$s.TargetPath=$env:SEEKY_EXE;$s.Arguments='--flash';$s.WorkingDirectory=[IO.Path]::GetDirectoryName($env:SEEKY_EXE);$s.Description='Seeky Flash';$s.Save()";
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
        pb.environment().put("SEEKY_LINK", link.getAbsolutePath()); pb.environment().put("SEEKY_EXE", exe);
        Process p = pb.start(); if (p.waitFor() != 0 || !link.isFile()) throw new Exception("O Windows não deixou criar o atalho de arranque.");
    }
}
