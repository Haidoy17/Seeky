package mz.bam.seeky.android;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.DateFormat;
import java.util.*;

/** Ficheiros: discs and the user's folders, browse, search by name, open in Seeky or Windows, copy, move, rename, Recycle Bin. */
final class DeskFiles {
    final DeskMain d;
    File folder;
    DeskFiles(DeskMain d) { this.d = d; }
    static File user(String... names) { File home = new File(System.getProperty("user.home")); for (String n : names) { File f = new File(home, n); if (f.isDirectory()) return f; } return null; }

    Node view() {
        VBox page = DeskUi.page();
        page.getChildren().add(DeskUi.row(DeskUi.title("Ficheiros"), DeskUi.grow(), DeskUi.button("Escolher pasta", false, () -> { DirectoryChooser c = new DirectoryChooser(); File f = c.showDialog(d.stage); if (f != null) { folder = f; d.open("files"); } })));
        // Places
        FlowPane places = new FlowPane(8, 8);
        Object[][] spots = {{"Ambiente de trabalho", user("Desktop", "Ambiente de trabalho", "OneDrive/Desktop")}, {"Documentos", user("Documents", "Documentos", "OneDrive/Documents")}, {"Transferências", user("Downloads", "Transferências")}, {"Imagens", user("Pictures", "Imagens")}, {"Música", user("Music", "Música")}, {"Vídeos", user("Videos", "Vídeos")}};
        for (Object[] s : spots) if (s[1] != null) places.getChildren().add(DeskUi.button((String) s[0], false, () -> { folder = (File) s[1]; d.open("files"); }));
        for (File root : File.listRoots()) { long free = root.getUsableSpace(), total = root.getTotalSpace(); places.getChildren().add(DeskUi.button(root.getPath() + (total > 0 ? "  (" + Rules.bytes(free) + " livres)" : ""), false, () -> { folder = root; d.open("files"); })); }
        page.getChildren().add(places);
        if (folder == null) folder = user("Documents", "Documentos"); if (folder == null) folder = new File(System.getProperty("user.home"));
        // Path and search
        TextField search = new TextField(); search.setPromptText("Procurar nomes nesta pasta e subpastas"); HBox.setHgrow(search, Priority.ALWAYS);
        Button up = DeskUi.button("Subir", false, () -> { if (folder.getParentFile() != null) { folder = folder.getParentFile(); d.open("files"); } });
        page.getChildren().add(DeskUi.row(up, DeskUi.heading(folder.getPath())));
        TableView<File> table = table();
        table.setItems(FXCollections.observableArrayList(list(folder)));
        Runnable find = () -> { String q = search.getText().trim(); if (q.isEmpty()) { table.setItems(FXCollections.observableArrayList(list(folder))); return; } search(folder, q, table); };
        search.setOnAction(e -> find.run());
        page.getChildren().addAll(DeskUi.row(search, DeskUi.button("Procurar", true, find), DeskUi.button("Nova pasta", false, this::mkdir)), table);
        VBox.setVgrow(table, Priority.ALWAYS); table.setPrefHeight(520);
        return page;
    }
    static List<File> list(File dir) {
        File[] kids = dir.listFiles(); List<File> out = new ArrayList<>(); if (kids == null) return out;
        for (File f : kids) if (!f.isHidden()) out.add(f);
        out.sort((a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }
    TableView<File> table() {
        TableView<File> t = new TableView<>(); t.setPlaceholder(DeskUi.muted("Pasta vazia ou sem acesso."));
        TableColumn<File, String> name = new TableColumn<>("Nome"); name.setCellValueFactory(c -> new SimpleStringProperty((c.getValue().isDirectory() ? "📁 " : "") + c.getValue().getName())); name.setPrefWidth(420);
        TableColumn<File, String> size = new TableColumn<>("Tamanho"); size.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isDirectory() ? "" : Rules.bytes(c.getValue().length()))); size.setPrefWidth(110);
        TableColumn<File, String> date = new TableColumn<>("Modificado"); date.setCellValueFactory(c -> new SimpleStringProperty(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(c.getValue().lastModified())))); date.setPrefWidth(160);
        TableColumn<File, String> where = new TableColumn<>("Pasta"); where.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getParent() == null ? "" : c.getValue().getParent())); where.setPrefWidth(300);
        t.getColumns().addAll(Arrays.asList(name, size, date, where));
        t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        t.setRowFactory(tv -> { TableRow<File> row = new TableRow<>(); row.setOnMouseClicked(e -> { if (row.isEmpty()) return; File f = row.getItem(); if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) open(f); else if (e.getButton() == MouseButton.SECONDARY) menu(t).show(row, e.getScreenX(), e.getScreenY()); }); return row; });
        return t;
    }
    void open(File f) {
        if (f.isDirectory()) { folder = f; d.open("files"); return; }
        String kind = MediaFormats.kind(f.getName(), null);
        if (DeskReader.accepts(f.getName())) { d.open("reader"); d.reader.open(f); }
        else if (kind.equals("audio") || kind.equals("video")) { d.open("media"); d.media.play(Collections.singletonList(f), 0); }
        else system(f);
    }
    static void system(File f) { try { Desktop.getDesktop().open(f); } catch (Exception e) { DeskUi.info("Não foi possível abrir", e.getMessage()); } }
    ContextMenu menu(TableView<File> t) {
        List<File> sel = new ArrayList<>(t.getSelectionModel().getSelectedItems());
        MenuItem open = new MenuItem("Abrir"); open.setOnAction(e -> { if (!sel.isEmpty()) open(sel.get(0)); });
        MenuItem windows = new MenuItem("Abrir com o Windows"); windows.setOnAction(e -> { if (!sel.isEmpty()) system(sel.get(0)); });
        MenuItem show = new MenuItem("Mostrar no Explorador do Windows"); show.setOnAction(e -> { if (!sel.isEmpty()) try { new ProcessBuilder("explorer.exe", "/select,", sel.get(0).getPath()).start(); } catch (IOException x) { system(sel.get(0).getParentFile()); } });
        MenuItem copy = new MenuItem("Copiar para…"); copy.setOnAction(e -> transfer(sel, false));
        MenuItem move = new MenuItem("Mover para…"); move.setOnAction(e -> transfer(sel, true));
        MenuItem rename = new MenuItem("Mudar o nome"); rename.setOnAction(e -> { if (sel.size() == 1) rename(sel.get(0)); });
        MenuItem trash = new MenuItem("Enviar para a Reciclagem"); trash.setOnAction(e -> trash(sel));
        return new ContextMenu(open, windows, show, new SeparatorMenuItem(), copy, move, rename, new SeparatorMenuItem(), trash);
    }
    void transfer(List<File> files, boolean move) {
        if (files.isEmpty()) return; DirectoryChooser c = new DirectoryChooser(); c.setTitle(move ? "Mover para" : "Copiar para"); File to = c.showDialog(d.stage); if (to == null) return;
        d.run(move ? "A mover…" : "A copiar…", () -> {
            int n = 0;
            for (File f : files) {
                Path target = to.toPath().resolve(f.getName()); if (Files.exists(target)) throw new IOException("Já existe " + target.getFileName() + " no destino.");
                if (move) Files.move(f.toPath(), target); else if (f.isDirectory()) copyTree(f.toPath(), target); else Files.copy(f.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES);
                n++;
            }
            return n;
        }, n -> { d.status(n + (move ? " movidos." : " copiados.")); d.open("files"); });
    }
    static void copyTree(Path from, Path to) throws IOException {
        try (java.util.stream.Stream<Path> all = Files.walk(from)) {
            for (Path p : (Iterable<Path>) all::iterator) { Path t = to.resolve(from.relativize(p).toString()); if (Files.isDirectory(p)) Files.createDirectories(t); else Files.copy(p, t, StandardCopyOption.COPY_ATTRIBUTES); }
        }
    }
    void rename(File f) { String n = DeskUi.ask("Mudar o nome", "Novo nome", f.getName()); if (n == null || n.equals(f.getName()) || n.contains("/") || n.contains("\\")) return; File to = new File(f.getParentFile(), n); if (to.exists() || !f.renameTo(to)) DeskUi.info("Não foi possível mudar o nome", to.getPath()); d.open("files"); }
    void trash(List<File> files) {
        if (files.isEmpty() || !DeskUi.confirm("Enviar para a Reciclagem?", files.size() + " itens. Pode recuperá-los na Reciclagem do Windows.")) return;
        int n = 0; for (File f : files) { try { if (Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) && Desktop.getDesktop().moveToTrash(f)) n++; } catch (Exception ignored) {} }
        d.status(n + " enviados para a Reciclagem." + (n < files.size() ? " Alguns não foram enviados." : "")); d.open("files");
    }
    void mkdir() { String n = DeskUi.ask("Nova pasta", "Nome", ""); if (n == null) return; if (!new File(folder, n).mkdirs()) DeskUi.info("Não foi possível criar a pasta", n); d.open("files"); }
    void search(File root, String q, TableView<File> table) {
        table.setItems(FXCollections.observableArrayList()); table.setPlaceholder(DeskUi.muted("A procurar…"));
        d.run("A procurar \"" + q + "\" em " + root.getPath() + "…", () -> {
            List<File> found = new ArrayList<>(); long deadline = System.nanoTime() + 20_000_000_000L;
            try {
                DiskWalker.walk(root, false, 2_000_000, deadline, new DiskWalker.Visitor() {
                    public void file(File f) { if (found.size() < 500 && Rules.nameMatches(f.getName(), q)) found.add(f); }
                    public void unreadable(File dir) {}
                    public boolean cancelled() { return found.size() >= 500; }
                });
            } catch (java.io.InterruptedIOException enough) { /* 500 results: stop looking */ }
            return found;
        }, found -> { table.setItems(FXCollections.observableArrayList(found)); table.setPlaceholder(DeskUi.muted("Nada encontrado com \"" + q + "\".")); d.status(found.size() + (found.size() >= 500 ? "+ " : " ") + "resultados"); });
    }
}
