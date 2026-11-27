package mz.bam.seeky.android;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.*;

/**
 * Música e Vídeos, separados e organizados como no telemóvel: pastas adicionadas (procuradas outra vez
 * sozinhas), Início com adicionadas recentemente, mais ouvidas (hoje, esta semana, este mês, sempre),
 * ouvidas recentemente e favoritas; Faixas, Álbuns, Artistas, Pastas, Géneros e Anos.
 */
final class DeskLibrary {
    static final String AUDIO = "audio", VIDEO = "video";
    static final String[] AUDIO_TYPES = {"mp3", "m4a", "aac", "wav", "aif", "aiff"};
    static final String[] VIDEO_TYPES = {"mp4", "m4v", "flv", "mkv", "avi", "mov", "wmv", "webm", "mpg", "mpeg", "3gp", "ts"};
    static final Set<String> PLAYABLE_VIDEO = new HashSet<>(Arrays.asList("mp4", "m4v", "flv"));
    static final String[] AUDIO_TABS = {"Início", "Faixas", "Álbuns", "Artistas", "Pastas", "Géneros", "Anos", "Favoritas"};
    static final String[] VIDEO_TABS = {"Início", "Vídeos", "Pastas", "Anos", "Favoritos"};
    static final String[] PERIODS = {"Hoje", "Esta semana", "Este mês", "Sempre"};
    static final int TODAY = 0, WEEK = 1, MONTH = 2, EVER = 3;

    final DeskMain d; final String kind;
    String tab = "Início", groupColumn, groupValue, groupLabel, special, query = "";
    int period = WEEK;
    volatile boolean scanning;
    DeskLibrary(DeskMain d, String kind) { this.d = d; this.kind = kind; }
    boolean video() { return kind.equals(VIDEO); }

    // ================================================================ dados
    static final class Track { String path = "", title = "", artist = "", album = "", genre = "", folder = ""; int year, track, plays; boolean favorite; long added, last; }
    static final class Group { final String value, label; final int count; String detail = ""; Group(String v, String l, int c) { value = v; label = l; count = c; } }
    static final class Section { final String title, action; final Runnable run; Section(String t, String a, Runnable r) { title = t; action = a; run = r; } }
    static final Object PERIOD_ROW = new Object();

    static String ext(String name) { int i = name.lastIndexOf('.'); return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT); }
    static boolean accepts(String kind, String name) { String e = ext(name); return Arrays.asList(kind.equals(AUDIO) ? AUDIO_TYPES : VIDEO_TYPES).contains(e); }
    static boolean playable(String name) { String e = ext(name); return Arrays.asList(AUDIO_TYPES).contains(e) || PLAYABLE_VIDEO.contains(e); }

    List<String> folders() { List<String> out = new ArrayList<>(); for (String f : d.store.get("folders_" + kind, "").split("\n")) if (!f.trim().isEmpty()) out.add(f.trim()); return out; }
    void folders(List<String> list) { d.store.put("folders_" + kind, String.join("\n", list)); }

    static Track track(Object[] r) {
        Track t = new Track(); t.path = r[0] + ""; t.title = r[1] + ""; t.artist = r[2] + ""; t.album = r[3] + ""; t.genre = r[4] + ""; t.year = ((Number) r[5]).intValue(); t.track = ((Number) r[6]).intValue();
        t.folder = r[7] + ""; t.added = ((Number) r[8]).longValue(); t.favorite = ((Number) r[9]).intValue() == 1; t.plays = ((Number) r[10]).intValue(); t.last = ((Number) r[11]).longValue(); return t;
    }
    static final String COLUMNS = "path,title,artist,album,genre,year,track,folder,added,favorite,plays,last_played";
    static List<Track> tracks(DeskStore s, String kind, String where, List<Object> args, String order, int limit) {
        List<Track> out = new ArrayList<>();
        List<Object> all = new ArrayList<>(); all.add(kind); if (args != null) all.addAll(args);
        try { for (Object[] r : s.rows("SELECT " + COLUMNS + " FROM tracks WHERE kind=?" + (where == null || where.isEmpty() ? "" : " AND (" + where + ")") + " ORDER BY " + order + (limit > 0 ? " LIMIT " + limit : ""), all.toArray())) out.add(track(r)); } catch (SQLException ignored) {}
        return out;
    }
    /** Start of a period: today at midnight, the last 7 or 30 days, or always. */
    static long since(int period, long now) {
        if (period == TODAY) { Calendar c = Calendar.getInstance(); c.setTimeInMillis(now); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.getTimeInMillis(); }
        return period == WEEK ? now - 7 * 86400000L : period == MONTH ? now - 30 * 86400000L : 0;
    }
    /** Most played in a period; plays becomes the number of plays in the period. */
    static List<Track> top(DeskStore s, String kind, int period, long now, int limit) {
        if (period == EVER) return tracks(s, kind, "plays>0", null, "plays DESC,last_played DESC", limit);
        List<Track> out = new ArrayList<>();
        try { for (Object[] r : s.rows("SELECT t.path,t.title,t.artist,t.album,t.genre,t.year,t.track,t.folder,t.added,t.favorite,COUNT(*) AS n,MAX(p.at) FROM plays p JOIN tracks t ON t.path=p.path WHERE t.kind=? AND p.at>=? GROUP BY p.path ORDER BY n DESC,MAX(p.at) DESC LIMIT " + Math.max(1, limit), kind, since(period, now))) out.add(track(r)); } catch (SQLException ignored) {}
        return out;
    }
    static void played(DeskStore s, String path) {
        long now = System.currentTimeMillis();
        try { s.update("INSERT INTO plays(path,at) VALUES(?,?)", path, now); s.update("UPDATE tracks SET plays=plays+1,last_played=? WHERE path=?", now, path); } catch (SQLException ignored) {}
    }
    List<Group> groups(String column) {
        List<Group> out = new ArrayList<>();
        String q = query.trim().isEmpty() ? "" : " AND " + column + " LIKE ?";
        List<Object> args = new ArrayList<>(); args.add(kind); if (!q.isEmpty()) args.add("%" + query.trim() + "%");
        String order = column.equals("year") ? column + " DESC" : column + " COLLATE NOCASE";
        try {
            String extra = column.equals("album") ? "MAX(artist)" : column.equals("artist") ? "COUNT(DISTINCT album)" : "''";
            for (Object[] r : d.store.rows("SELECT " + column + ",COUNT(*)," + extra + " FROM tracks WHERE kind=?" + q + " GROUP BY " + column + " ORDER BY " + order, args.toArray())) {
                String v = r[0] + ""; String label = v;
                if (column.equals("year")) label = v.equals("0") ? "Sem ano" : v;
                else if (column.equals("folder")) label = v.isEmpty() ? "Sem pasta" : new File(v).getName().isEmpty() ? v : new File(v).getName();
                else if (v.isEmpty()) label = column.equals("artist") ? "Artista desconhecido" : column.equals("album") ? "Álbum desconhecido" : "Sem género";
                Group g = new Group(v, label, ((Number) r[1]).intValue());
                if (column.equals("album") && r[2] != null && !(r[2] + "").isEmpty()) g.detail = r[2] + "";
                if (column.equals("artist") && r[2] instanceof Number && ((Number) r[2]).intValue() > 0) { int n = ((Number) r[2]).intValue(); g.detail = n + (n == 1 ? " álbum" : " álbuns"); }
                out.add(g);
            }
        } catch (SQLException ignored) {}
        return out;
    }

    /** Looks for music (or videos) in the added folders: new files come in, changed ones are re-read, missing ones leave. */
    static int[] scan(DeskStore s, String kind, List<String> folders) throws Exception {
        Set<String> seen = new HashSet<>(); int added = 0, total = 0;
        Map<String, Long> known = new HashMap<>();
        for (Object[] r : s.rows("SELECT path,modified FROM tracks WHERE kind=?", kind)) known.put(r[0] + "", ((Number) r[1]).longValue());
        boolean auto = s.db.getAutoCommit(); s.db.setAutoCommit(false);
        try {
            for (String folder : folders) {
                Path root = Paths.get(folder); if (!Files.isDirectory(root)) continue;
                List<Path> files = new ArrayList<>();
                Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 24, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) { String n = dir.getFileName() == null ? "" : dir.getFileName().toString(); return !dir.equals(root) && (n.startsWith(".") || n.startsWith("$") || n.equalsIgnoreCase("System Volume Information")) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE; }
                    @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) { if (a.isRegularFile() && accepts(kind, f.getFileName().toString()) && files.size() < 100000) files.add(f); return FileVisitResult.CONTINUE; }
                    @Override public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
                });
                for (Path p : files) {
                    File f = p.toFile(); String path = f.getAbsolutePath(); if (!seen.add(path)) continue; total++;
                    long modified = f.lastModified(); Long had = known.get(path);
                    if (had != null && had == modified) continue;
                    DeskTags t = kind.equals(AUDIO) ? DeskTags.read(f) : video(f);
                    String dir = f.getParent() == null ? "" : f.getParent();
                    if (had == null) { s.update("INSERT OR REPLACE INTO tracks(path,kind,title,artist,album,album_artist,genre,year,track,folder,size,modified,added) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", path, kind, t.title, t.artist, t.album, t.albumArtist, t.genre, t.year, t.track, dir, f.length(), modified, System.currentTimeMillis()); added++; }
                    else s.update("UPDATE tracks SET title=?,artist=?,album=?,album_artist=?,genre=?,year=?,track=?,folder=?,size=?,modified=? WHERE path=?", t.title, t.artist, t.album, t.albumArtist, t.genre, t.year, t.track, dir, f.length(), modified, path);
                }
            }
            // Files that disappeared from the added folders leave the library (the others, added one by one, stay while they exist).
            for (String path : known.keySet()) if (!seen.contains(path) && !new File(path).isFile()) s.update("DELETE FROM tracks WHERE path=?", path);
            s.db.commit();
        } catch (Exception e) { s.db.rollback(); throw e; }
        finally { s.db.setAutoCommit(auto); }
        return new int[]{added, total};
    }
    /** Videos: the title is the file name; the year is the file's date. */
    static DeskTags video(File f) {
        DeskTags t = new DeskTags(); t.title = f.getName().replaceFirst("\\.[^.]+$", "").replace('_', ' ').trim();
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(f.lastModified()); t.year = c.get(Calendar.YEAR); t.album = f.getParentFile() == null ? "" : f.getParentFile().getName();
        return t;
    }
    static void addFiles(DeskStore s, String kind, List<File> files) throws SQLException {
        for (File f : files) {
            if (!accepts(kind, f.getName())) continue;
            DeskTags t = kind.equals(AUDIO) ? DeskTags.read(f) : video(f);
            s.update("INSERT OR REPLACE INTO tracks(path,kind,title,artist,album,album_artist,genre,year,track,folder,size,modified,added) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", f.getAbsolutePath(), kind, t.title, t.artist, t.album, t.albumArtist, t.genre, t.year, t.track, f.getParent() == null ? "" : f.getParent(), f.length(), f.lastModified(), System.currentTimeMillis());
        }
    }

    // ================================================================ ecrã
    Node view() {
        VBox page = new VBox(12); page.setPadding(new Insets(20, 28, 14, 28)); page.getStyleClass().add("page");
        TextField search = new TextField(query); search.setPromptText(video() ? "Pesquisar vídeos ou pastas" : "Pesquisar músicas, artistas ou álbuns"); search.setPrefWidth(300);
        MenuButton more = new MenuButton("Mais");
        MenuItem files = new MenuItem(video() ? "Adicionar vídeos…" : "Adicionar músicas…"); files.setOnAction(e -> addFiles());
        MenuItem managed = new MenuItem("Pastas adicionadas…"); managed.setOnAction(e -> manageFolders());
        MenuItem rescan = new MenuItem("Procurar de novo nas pastas"); rescan.setOnAction(e -> rescan(true));
        more.getItems().addAll(files, managed, rescan);
        HBox head = DeskUi.row(DeskUi.title(video() ? "Vídeos" : "Música"), DeskUi.grow(), search, DeskUi.button("Adicionar pasta", true, this::addFolder), more);
        // Tabs like on the phone
        HBox tabs = new HBox(6); tabs.setAlignment(Pos.CENTER_LEFT);
        for (String t : video() ? VIDEO_TABS : AUDIO_TABS) {
            ToggleButton b = new ToggleButton(t); b.getStyleClass().add("chip"); b.setSelected(t.equals(tab) && groupValue == null && special == null);
            b.setOnAction(e -> { tab = t; groupColumn = null; groupValue = null; special = null; d.open(kind.equals(AUDIO) ? "music" : "videos"); });
            tabs.getChildren().add(b);
        }
        ScrollPane tabScroll = new ScrollPane(tabs); tabScroll.setFitToHeight(true); tabScroll.getStyleClass().add("page-scroll"); tabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); tabScroll.setMinHeight(44);
        ListView<Object> list = new ListView<>(); list.getStyleClass().add("library-list"); VBox.setVgrow(list, Priority.ALWAYS);
        Label info = DeskUi.muted("");
        Map<Object, List<Track>> owners = new IdentityHashMap<>(); Set<Object> showPlays = Collections.newSetFromMap(new IdentityHashMap<>());
        list.setCellFactory(v -> new Cell(owners, showPlays));
        Runnable fill = () -> fill(list, owners, showPlays, info);
        // 5.56: one click selects (Ctrl/Shift for several), a double click or Enter plays; search waits for a pause in typing.
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ENTER) { Object o = list.getSelectionModel().getSelectedItem(); if (o instanceof Track) playFrom(list, owners, (Track) o); } });
        javafx.animation.PauseTransition typing = new javafx.animation.PauseTransition(javafx.util.Duration.millis(220)); typing.setOnFinished(e -> fill.run());
        search.textProperty().addListener((o, a, b) -> { query = b; typing.playFromStart(); });
        Label chosen = DeskUi.muted(""); HBox selection = DeskUi.row(chosen, DeskUi.grow(),
            DeskUi.button("Tocar", true, () -> { List<Track> sel = selected(list, null); if (!sel.isEmpty()) d.media.play(sel, 0); }),
            DeskUi.button("Tocar a seguir", false, () -> d.media.playNext(selected(list, null))),
            DeskUi.button("Adicionar à fila", false, () -> d.media.enqueue(selected(list, null))),
            DeskUi.button("Limpar selecção", false, () -> list.getSelectionModel().clearSelection()));
        selection.getStyleClass().add("selection-row"); selection.setVisible(false); selection.setManaged(false);
        list.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Object>) c -> {
            int n = selected(list, null).size(); selection.setVisible(n > 0); selection.setManaged(n > 0);
            chosen.setText(n == 0 ? "" : (n == 1 ? (video() ? "1 vídeo seleccionado" : "1 música seleccionada") : n + (video() ? " vídeos seleccionados" : " músicas seleccionadas")) + " · duplo clique toca");
        });
        d.media.changed = list::refresh;
        page.getChildren().addAll(head, tabScroll);
        if (groupValue != null || special != null) {
            String title = groupValue != null ? groupLabel : special.equals("recent") ? (video() ? "Adicionados recentemente" : "Adicionadas recentemente") : special.equals("history") ? (video() ? "Vistos recentemente" : "Ouvidas recentemente") : (video() ? "Mais vistos" : "Mais ouvidas");
            page.getChildren().add(DeskUi.row(DeskUi.iconButton("back", "Voltar", () -> { groupValue = null; special = null; d.open(kind.equals(AUDIO) ? "music" : "videos"); }), DeskUi.heading(title), DeskUi.grow(), DeskUi.button(video() ? "Ver tudo" : "Tocar tudo", false, () -> playAll(list))));
        }
        if (video() && d.media.isVideo()) page.getChildren().add(d.media.videoCard());
        page.getChildren().addAll(info, selection, list);
        page.getChildren().add(d.media.bar());
        fill.run();
        if (folders().isEmpty() && !scanning) countEmpty(list);
        else rescan(false);
        return page;
    }
    void countEmpty(ListView<Object> list) { try { if (d.store.count("SELECT COUNT(*) FROM tracks WHERE kind=?", kind) == 0) list.setPlaceholder(empty()); } catch (SQLException ignored) {} }
    Node empty() {
        VBox box = DeskUi.card(DeskUi.heading(video() ? "Junte os seus vídeos" : "Junte a sua música"),
            DeskUi.text(video() ? "Adicione as pastas onde guarda vídeos (por exemplo Vídeos ou Transferências). O Seeky volta a procurar nelas sempre que abrir este ecrã." : "Adicione as pastas onde guarda música (por exemplo Música ou Transferências). O Seeky organiza por faixas, álbuns, artistas, géneros e anos, e volta a procurar nelas sempre que abrir este ecrã."),
            DeskUi.row(DeskUi.button("Adicionar pasta", true, this::addFolder), DeskUi.button(video() ? "Adicionar vídeos" : "Adicionar músicas", false, this::addFiles)));
        box.setMaxWidth(560); box.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane wrap = new StackPane(box); StackPane.setAlignment(box, Pos.TOP_CENTER); wrap.setPadding(new Insets(20, 0, 0, 0)); return wrap;
    }
    void playAll(ListView<Object> list) { List<Track> all = new ArrayList<>(); for (Object o : list.getItems()) if (o instanceof Track) all.add((Track) o); if (!all.isEmpty()) d.media.play(all, 0); }

    String groupColumnOf(String t) {
        switch (t) { case "Artistas": return "artist"; case "Álbuns": return "album"; case "Géneros": return "genre"; case "Anos": return "year"; case "Pastas": return "folder"; default: return null; }
    }
    String search() { return "title LIKE ? OR artist LIKE ? OR album LIKE ? OR folder LIKE ?"; }
    List<Object> searchArgs() { String q = "%" + query.trim() + "%"; return Arrays.asList(q, q, q, q); }
    void fill(ListView<Object> list, Map<Object, List<Track>> owners, Set<Object> showPlays, Label info) {
        owners.clear(); showPlays.clear();
        List<Object> out = new ArrayList<>(); String q = query.trim(); long now = System.currentTimeMillis();
        String order = video() ? "added DESC" : "title COLLATE NOCASE";
        List<Track> tracks;
        if (special != null) {
            if (special.equals("recent")) tracks = tracks(d.store, kind, q.isEmpty() ? null : search(), q.isEmpty() ? null : searchArgs(), "added DESC", 300);
            else if (special.equals("history")) tracks = tracks(d.store, kind, "last_played>0", null, "last_played DESC", 200);
            else { out.add(PERIOD_ROW); tracks = top(d.store, kind, period, now, 200); showPlays.addAll(tracks); }
            for (Track t : tracks) owners.put(t, tracks);
            out.addAll(tracks); if (tracks.isEmpty()) out.add(new Section(period == TODAY ? (video() ? "Ainda não viu nada hoje." : "Ainda não ouviu nada hoje.") : "Nada neste período.", null, null));
            info.setText(count(tracks.size()));
        } else if (groupValue != null) {
            String ord = groupColumn.equals("album") ? "track,title COLLATE NOCASE" : groupColumn.equals("artist") ? "album COLLATE NOCASE,track,title COLLATE NOCASE" : order;
            List<Object> args = new ArrayList<>(); args.add(groupColumn.equals("year") ? (Object) Integer.parseInt(groupValue) : groupValue); if (!q.isEmpty()) args.addAll(searchArgs());
            tracks = tracks(d.store, kind, groupColumn + "=?" + (q.isEmpty() ? "" : " AND (" + search() + ")"), args, ord, 0);
            for (Track t : tracks) owners.put(t, tracks); out.addAll(tracks); info.setText(count(tracks.size()));
        } else if (tab.equals("Início") && q.isEmpty()) {
            List<Track> recent = tracks(d.store, kind, null, null, "added DESC", 6), top = top(d.store, kind, period, now, 8), history = tracks(d.store, kind, "last_played>0", null, "last_played DESC", 5), favs = tracks(d.store, kind, "favorite=1", null, "title COLLATE NOCASE", 5);
            section(out, owners, video() ? "Adicionados recentemente" : "Adicionadas recentemente", recent, () -> { special = "recent"; reopen(); });
            out.add(new Section(video() ? "Mais vistos" : "Mais ouvidas", "Ver todas", () -> { special = "top"; reopen(); })); out.add(PERIOD_ROW);
            showPlays.addAll(top); for (Track t : top) { out.add(t); owners.put(t, top); }
            if (top.isEmpty()) out.add(new Section(period == TODAY ? (video() ? "Ainda não viu nada hoje." : "Ainda não ouviu nada hoje.") : "Nada neste período.", null, null));
            if (!history.isEmpty()) section(out, owners, video() ? "Vistos recentemente" : "Ouvidas recentemente", history, () -> { special = "history"; reopen(); });
            if (!favs.isEmpty()) section(out, owners, video() ? "Favoritos" : "Favoritas", favs, () -> { tab = video() ? "Favoritos" : "Favoritas"; reopen(); });
            long all = 0; try { all = d.store.count("SELECT COUNT(*) FROM tracks WHERE kind=?", kind); } catch (SQLException ignored) {}
            info.setText(all == 0 ? "" : count((int) all) + " na biblioteca");
            if (all == 0) out.clear();
        } else if (groupColumnOf(tab) != null) {
            String column = groupColumnOf(tab); List<Group> groups = groups(column); out.addAll(groups);
            list.setUserData(column); info.setText(groups.size() + " " + tab.toLowerCase(Locale.ROOT));
        } else {
            String where = q.isEmpty() ? "" : search(); List<Object> args = q.isEmpty() ? null : new ArrayList<>(searchArgs());
            if (tab.startsWith("Favorit")) where = where.isEmpty() ? "favorite=1" : "favorite=1 AND (" + where + ")";
            tracks = tracks(d.store, kind, where, args, order, 0);
            for (Track t : tracks) owners.put(t, tracks); out.addAll(tracks); info.setText(count(tracks.size()));
        }
        list.setItems(FXCollections.observableArrayList(out));
        if (out.isEmpty()) { list.setPlaceholder(folders().isEmpty() && info.getText().isEmpty() ? empty() : DeskUi.muted(q.isEmpty() ? "Nada aqui ainda." : "Nada encontrado com \"" + q + "\".")); }
    }
    void section(List<Object> out, Map<Object, List<Track>> owners, String title, List<Track> tracks, Runnable all) {
        if (tracks.isEmpty()) return; out.add(new Section(title, "Ver todas", all)); for (Track t : tracks) { out.add(t); owners.put(t, tracks); }
    }
    String count(int n) { return video() ? n + (n == 1 ? " vídeo" : " vídeos") : n + (n == 1 ? " música" : " músicas"); }
    void reopen() { d.open(kind.equals(AUDIO) ? "music" : "videos"); }

    /** The selected tracks in list order; with {@code t} not among them, just {@code t}. */
    static List<Track> selected(ListView<Object> list, Track t) {
        List<Track> out = new ArrayList<>(); for (Object o : list.getItems()) if (o instanceof Track && list.getSelectionModel().getSelectedItems().contains(o)) out.add((Track) o);
        if (t != null && !out.contains(t)) return Collections.singletonList(t);
        return out;
    }
    /** Plays {@code t}: with several selected, those; otherwise from it in the list it came from (its section). */
    void playFrom(ListView<Object> list, Map<Object, List<Track>> owners, Track t) {
        List<Track> sel = selected(list, t);
        if (sel.size() > 1) { d.media.play(sel, sel.indexOf(t)); return; }
        List<Track> owner = owners.getOrDefault(t, Collections.singletonList(t)); d.media.play(owner, Math.max(0, owner.indexOf(t)));
    }
    /** One row: a track, a group (album, artist…), a section heading or the periods. */
    final class Cell extends ListCell<Object> {
        final Map<Object, List<Track>> owners; final Set<Object> showPlays;
        Cell(Map<Object, List<Track>> owners, Set<Object> showPlays) { this.owners = owners; this.showPlays = showPlays; }
        // 5.56: a track row is built once per cell and only its words change when scrolling (much lighter in long lists).
        HBox trackRow; StackPane trackBadge; Label trackTitle, trackSub, trackPlays; Button trackFav; ContextMenu trackMenu; String badgeIcon = "";
        Track track() { Object o = getItem(); return o instanceof Track ? (Track) o : null; }
        void buildTrack() {
            trackBadge = new StackPane(); trackBadge.getStyleClass().add("badge"); trackBadge.setMinSize(40, 40); trackBadge.setMaxSize(40, 40);
            trackTitle = DeskUi.heading(""); trackTitle.setWrapText(false); trackSub = DeskUi.muted(""); trackPlays = DeskUi.muted("");
            VBox words = new VBox(2, trackTitle, trackSub); HBox.setHgrow(words, Priority.ALWAYS); words.setMinWidth(0);
            trackFav = new Button("♡"); trackFav.getStyleClass().add("heart"); trackFav.setTooltip(new Tooltip(video() ? "Favorito" : "Favorita"));
            trackFav.setOnAction(e -> { Track t = track(); if (t == null) return; t.favorite = !t.favorite; try { d.store.update("UPDATE tracks SET favorite=? WHERE path=?", t.favorite ? 1 : 0, t.path); } catch (SQLException ignored) {} trackFav.setText(t.favorite ? "♥" : "♡"); });
            trackRow = new HBox(14, trackBadge, words, trackPlays, trackFav); trackRow.setAlignment(Pos.CENTER_LEFT);
            MenuItem play = new MenuItem("Tocar"); play.setOnAction(e -> { Track t = track(); if (t != null) playFrom(getListView(), owners, t); });
            MenuItem next = new MenuItem("Tocar a seguir"); next.setOnAction(e -> { Track t = track(); if (t != null) d.media.playNext(selected(getListView(), t)); });
            MenuItem queue = new MenuItem("Adicionar à fila"); queue.setOnAction(e -> { Track t = track(); if (t != null) d.media.enqueue(selected(getListView(), t)); });
            MenuItem folder = new MenuItem("Abrir a pasta"); folder.setOnAction(e -> { Track t = track(); if (t != null) DeskFiles.system(new File(t.path).getParentFile()); });
            MenuItem external = new MenuItem("Abrir no Windows"); external.setOnAction(e -> { Track t = track(); if (t != null) DeskFiles.system(new File(t.path)); });
            MenuItem remove = new MenuItem("Remover da biblioteca"); remove.setOnAction(e -> { List<Track> gone = new ArrayList<>(selected(getListView(), track())); ListView<Object> lv = getListView(); for (Track t : gone) { try { d.store.update("DELETE FROM tracks WHERE path=?", t.path); } catch (SQLException ignored) {} lv.getItems().remove(t); } });
            trackMenu = new ContextMenu(play, next, queue, new SeparatorMenuItem(), folder, external, remove);
        }
        @Override protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty); setText(null); getStyleClass().removeAll("section-cell", "track-cell", "playing");
            if (empty || item == null) { setGraphic(null); setOnMouseClicked(null); setContextMenu(null); return; }
            if (item == PERIOD_ROW) {
                HBox row = new HBox(6); row.setPadding(new Insets(2, 0, 6, 0));
                for (int i = 0; i < PERIODS.length; i++) { final int p = i; ToggleButton b = new ToggleButton(PERIODS[i]); b.getStyleClass().add("chip"); b.setSelected(period == i); b.setOnAction(e -> { period = p; reopen(); }); row.getChildren().add(b); }
                setGraphic(row); setOnMouseClicked(null); setContextMenu(null); getStyleClass().add("section-cell"); return;
            }
            if (item instanceof Section) {
                Section s = (Section) item; HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(12, 0, 4, 0));
                Label t = s.run == null ? DeskUi.muted(s.title) : DeskUi.heading(s.title); row.getChildren().addAll(t, DeskUi.grow());
                if (s.action != null) { Hyperlink l = new Hyperlink(s.action); l.getStyleClass().add("link"); l.setOnAction(e -> s.run.run()); row.getChildren().add(l); }
                setGraphic(row); setOnMouseClicked(null); setContextMenu(null); getStyleClass().add("section-cell"); return;
            }
            if (item instanceof Group) {
                Group g = (Group) item; String column = (String) getListView().getUserData();
                String icon = "folder".equals(column) ? "folder" : "year".equals(column) ? "study" : "artist".equals(column) ? "person" : "genre".equals(column) ? "game" : "disc";
                HBox row = new HBox(14, badge(icon), new VBox(2, DeskUi.heading(g.label), DeskUi.muted((g.detail.isEmpty() ? "" : g.detail + " · ") + count(g.count) + ("folder".equals(column) ? " · " + g.value : "")))); row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row); getStyleClass().add("track-cell"); setContextMenu(null);
                setOnMouseClicked(e -> { groupColumn = column; groupValue = g.value; groupLabel = g.label; reopen(); });
                return;
            }
            Track t = (Track) item; boolean current = t.path.equals(d.media.currentPath());
            String sub = video() ? (t.album.isEmpty() ? "" : t.album) + (t.year > 0 ? " · " + t.year : "") : (t.artist.isEmpty() ? "Artista desconhecido" : t.artist) + (t.album.isEmpty() ? "" : " · " + t.album) + (t.year > 0 ? " · " + t.year : "");
            if (!playable(t.path)) sub += " · abre no leitor do Windows";
            if (trackRow == null) buildTrack();
            String icon = current ? "play" : video() ? "video" : "music"; if (!icon.equals(badgeIcon)) { trackBadge.getChildren().setAll(DeskUi.icon(icon, 20)); badgeIcon = icon; }
            trackTitle.setText(t.title); trackSub.setText(sub);
            boolean plays = showPlays.contains(t) && t.plays > 0; trackPlays.setText(plays ? t.plays + (t.plays == 1 ? " vez" : " vezes") : ""); trackPlays.setVisible(plays); trackPlays.setManaged(plays);
            trackFav.setText(t.favorite ? "♥" : "♡");
            setGraphic(trackRow); getStyleClass().add("track-cell"); if (current) getStyleClass().add("playing");
            setOnMouseClicked(e -> { if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 2) { Track now = track(); if (now != null) playFrom(getListView(), owners, now); } });
            setContextMenu(trackMenu);
        }
    }
    static Node badge(String icon) { StackPane b = new StackPane(DeskUi.icon(icon, 20)); b.getStyleClass().add("badge"); b.setMinSize(40, 40); b.setMaxSize(40, 40); return b; }

    // ================================================================ pastas
    void addFolder() {
        DirectoryChooser c = new DirectoryChooser(); c.setTitle(video() ? "Pasta com vídeos" : "Pasta com música");
        String home = System.getProperty("user.home"); File guess = new File(home, video() ? "Videos" : "Music"); if (guess.isDirectory()) c.setInitialDirectory(guess);
        File dir = c.showDialog(d.stage); if (dir == null) return;
        List<String> all = folders(); if (!all.contains(dir.getAbsolutePath())) all.add(dir.getAbsolutePath()); folders(all);
        rescan(true);
    }
    void addFiles() {
        FileChooser fc = new FileChooser(); List<String> pats = new ArrayList<>(); for (String e : video() ? VIDEO_TYPES : AUDIO_TYPES) pats.add("*." + e);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(video() ? "Vídeos" : "Música", pats));
        List<File> f = fc.showOpenMultipleDialog(d.stage); if (f == null || f.isEmpty()) return;
        d.run("A juntar à biblioteca…", () -> { addFiles(d.store, kind, f); return f.size(); }, n -> { tab = video() ? "Vídeos" : "Faixas"; reopen(); });
    }
    void manageFolders() {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Seeky"); dialog.setHeaderText("Pastas adicionadas"); DeskUi.style(dialog.getDialogPane());
        VBox box = new VBox(8); box.setPrefWidth(520);
        Runnable[] draw = {null};
        draw[0] = () -> {
            box.getChildren().clear(); List<String> all = folders();
            if (all.isEmpty()) box.getChildren().add(DeskUi.muted("Ainda não adicionou pastas."));
            for (String f : all) box.getChildren().add(DeskUi.row(DeskUi.icon("folder", 18), DeskUi.text(f), DeskUi.grow(), DeskUi.button("Remover", false, () -> {
                List<String> left = folders(); left.remove(f); folders(left);
                try { d.store.update("DELETE FROM tracks WHERE kind=? AND (folder=? OR folder LIKE ?)", kind, f, f + File.separator + "%"); } catch (SQLException ignored) {}
                draw[0].run();
            })));
            box.getChildren().add(DeskUi.button("Adicionar pasta", true, () -> { dialog.close(); addFolder(); }));
        };
        draw[0].run(); dialog.getDialogPane().setContent(box); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait(); reopen();
    }
    /** Searches the folders again in the background (at most every 2 minutes unless asked). */
    void rescan(boolean asked) {
        if (scanning || folders().isEmpty()) return;
        long last = Long.parseLong(d.store.get("scan_" + kind, "0"));
        if (!asked && System.currentTimeMillis() - last < 120000) return;
        scanning = true; d.store.put("scan_" + kind, System.currentTimeMillis() + "");
        d.run(asked ? (video() ? "A procurar vídeos…" : "A procurar música…") : null, () -> { try { return scan(d.store, kind, folders()); } finally { scanning = false; } }, r -> {
            if (asked) d.status(r[1] + (video() ? " vídeos" : " músicas") + " nas pastas" + (r[0] > 0 ? " · " + r[0] + " novas" : ""));
            if (asked || r[0] > 0) { String current = d.current; if (current.equals(kind.equals(AUDIO) ? "music" : "videos")) reopen(); }
        });
    }
}
