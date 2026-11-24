package mz.bam.seeky.android;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Música e vídeo: queue, controls, video in the window and synced lyrics (.lrc beside the file or LRCLIB). */
final class DeskMedia {
    final DeskMain d;
    final List<File> queue = new ArrayList<>(); int index = -1;
    MediaPlayer player; final MediaView video = new MediaView();
    final Label now = DeskUi.heading("Nada a tocar"), time = DeskUi.muted("0:00 / 0:00");
    final Slider seek = new Slider(0, 1, 0), volume = new Slider(0, 1, 0.8);
    final ListView<String> list = new ListView<>(); final ListView<String> lyricsView = new ListView<>();
    final List<long[]> lyricTimes = new ArrayList<>(); final Button play = DeskUi.iconButton("play", "Tocar / pausa", this::toggle);
    boolean seeking;
    DeskMedia(DeskMain d) {
        this.d = d; video.setPreserveRatio(true); video.setFitHeight(360);
        seek.valueChangingProperty().addListener((o, a, b) -> { seeking = b; if (!b && player != null) player.seek(player.getTotalDuration().multiply(seek.getValue())); });
        seek.setOnMouseReleased(e -> { if (player != null) player.seek(player.getTotalDuration().multiply(seek.getValue())); });
        volume.valueProperty().addListener((o, a, b) -> { if (player != null) player.setVolume(b.doubleValue()); });
        list.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedIndex() >= 0) start(list.getSelectionModel().getSelectedIndex()); });
        lyricsView.setPlaceholder(DeskUi.muted("Sem letra. Toque em Procurar letra."));
    }
    static final String[] TYPES = {"*.mp3", "*.m4a", "*.aac", "*.wav", "*.aif", "*.aiff", "*.mp4", "*.m4v", "*.flv"};
    Node view() {
        VBox page = DeskUi.page(DeskUi.row(DeskUi.title("Música e vídeo"), DeskUi.grow(), DeskUi.button("Abrir ficheiros", true, this::chooseFiles), DeskUi.button("Abrir pasta", false, this::chooseFolder)));
        HBox controls = DeskUi.row(DeskUi.iconButton("back", "Anterior", () -> start(index - 1)), play, DeskUi.button("›", false, () -> start(index + 1)), time, DeskUi.grow(), DeskUi.icon("speaker", 18), volume);
        seek.setMaxWidth(Double.MAX_VALUE);
        VBox player = DeskUi.card(now, video, seek, controls);
        list.setPrefHeight(260); lyricsView.setPrefHeight(260);
        VBox queueBox = new VBox(6, DeskUi.heading("Fila"), list), lyricsBox = new VBox(6, DeskUi.row(DeskUi.heading("Letra"), DeskUi.grow(), DeskUi.button("Procurar letra", false, this::findLyrics)), lyricsView);
        HBox.setHgrow(queueBox, Priority.ALWAYS); HBox.setHgrow(lyricsBox, Priority.ALWAYS);
        page.getChildren().addAll(player, new HBox(14, queueBox, lyricsBox), DeskUi.muted("Formatos: MP3, M4A/AAC, WAV, AIFF e vídeo MP4 (H.264). Para outros (FLAC, MKV…), use Ficheiros → Abrir com o Windows."));
        refreshList();
        return DeskUi.scroll(page);
    }
    void chooseFiles() { FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Música e vídeo", TYPES)); List<File> f = fc.showOpenMultipleDialog(d.stage); if (f != null && !f.isEmpty()) play(f, 0); }
    void chooseFolder() {
        DirectoryChooser c = new DirectoryChooser(); File dir = c.showDialog(d.stage); if (dir == null) return;
        List<File> found = new ArrayList<>(); File[] kids = dir.listFiles(); if (kids != null) for (File f : kids) for (String t : TYPES) if (f.getName().toLowerCase(Locale.ROOT).endsWith(t.substring(1))) found.add(f);
        found.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT))); if (found.isEmpty()) DeskUi.info("Sem música nesta pasta", dir.getPath()); else play(found, 0);
    }
    void play(List<File> files, int at) { queue.clear(); queue.addAll(files); refreshList(); start(at); }
    void refreshList() { List<String> names = new ArrayList<>(); for (File f : queue) names.add(f.getName()); list.setItems(FXCollections.observableArrayList(names)); if (index >= 0 && index < names.size()) list.getSelectionModel().select(index); }
    void start(int at) {
        if (queue.isEmpty() || at < 0 || at >= queue.size()) return;
        stop(); index = at; File f = queue.get(at);
        try {
            Media m = new Media(f.toURI().toString()); player = new MediaPlayer(m); player.setVolume(volume.getValue());
            video.setMediaPlayer(player); video.setVisible(MediaFormats.kind(f.getName(), null).equals("video")); video.setManaged(video.isVisible());
            player.currentTimeProperty().addListener((o, a, t) -> tick(t));
            player.setOnEndOfMedia(() -> start(index + 1));
            player.setOnError(() -> d.status("Não foi possível tocar " + f.getName() + ": " + player.getError().getMessage()));
            player.play(); play.setGraphic(DeskUi.icon("pause", 20));
            now.setText(f.getName().replaceFirst("\\.[^.]+$", "")); refreshList(); loadLyrics(f); d.status("A tocar " + f.getName());
        } catch (Exception e) { d.status("Formato não suportado: " + f.getName()); }
    }
    void toggle() { if (player == null) { if (!queue.isEmpty()) start(Math.max(0, index)); return; } if (player.getStatus() == MediaPlayer.Status.PLAYING) { player.pause(); play.setGraphic(DeskUi.icon("play", 20)); } else { player.play(); play.setGraphic(DeskUi.icon("pause", 20)); } }
    void stop() { if (player != null) { player.stop(); player.dispose(); player = null; } }
    static String clock(Duration t) { if (t == null || t.isUnknown()) return "0:00"; long s = (long) t.toSeconds(); return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60); }
    void tick(Duration t) {
        if (player == null) return; Duration total = player.getTotalDuration();
        time.setText(clock(t) + " / " + clock(total)); if (!seeking && total != null && total.toMillis() > 0) seek.setValue(t.toMillis() / total.toMillis());
        long ms = (long) t.toMillis(); int line = -1; for (int i = 0; i < lyricTimes.size(); i++) if (lyricTimes.get(i)[0] <= ms) line = i;
        if (line >= 0 && line != lyricsView.getSelectionModel().getSelectedIndex()) { lyricsView.getSelectionModel().select(line); lyricsView.scrollTo(Math.max(0, line - 3)); }
    }
    // ---- lyrics ----
    static File saved(File song) { return new File(DeskStore.folder("letras"), Integer.toHexString(song.getAbsolutePath().hashCode()) + ".lrc"); }
    void loadLyrics(File f) {
        lyricTimes.clear(); lyricsView.getItems().clear();
        File beside = new File(f.getParentFile(), f.getName().replaceFirst("\\.[^.]+$", "") + ".lrc"), mine = saved(f);
        File src = beside.isFile() ? beside : mine.isFile() ? mine : null; if (src == null) return;
        try { show(new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8)); } catch (IOException ignored) {}
    }
    /** "[01:02.30] text" lines (synced) or plain text. */
    void show(String lrc) {
        lyricTimes.clear(); List<String> lines = new ArrayList<>(); boolean synced = false;
        for (String l : lrc.split("\\r?\\n")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\](.*)$").matcher(l.trim());
            if (m.matches()) { long ms = Long.parseLong(m.group(1)) * 60000 + Long.parseLong(m.group(2)) * 1000 + (m.group(3) == null ? 0 : Long.parseLong((m.group(3) + "00").substring(0, 3))); lyricTimes.add(new long[]{ms}); lines.add(m.group(4).trim()); synced = true; }
            else if (!l.trim().isEmpty() && !l.trim().matches("^\\[[a-z]+:.*\\]$")) { if (!synced) lines.add(l.trim()); }
        }
        lyricsView.setItems(FXCollections.observableArrayList(lines));
    }
    void findLyrics() {
        if (index < 0) return; File f = queue.get(index); String title = LyricsText.clean(f.getName().replaceFirst("\\.[^.]+$", "")); long ms = player == null || player.getTotalDuration().isUnknown() ? 0 : (long) player.getTotalDuration().toMillis();
        d.run("A procurar a letra de " + title + "…", () -> {
            HttpURLConnection c = (HttpURLConnection) new URL("https://lrclib.net/api/search?q=" + URLEncoder.encode(title, "UTF-8")).openConnection();
            c.setRequestProperty("User-Agent", "Seeky Desktop (https://github.com/Haidoy17/Seeky)"); c.setConnectTimeout(15000); c.setReadTimeout(20000);
            if (c.getResponseCode() != 200) throw new IOException("O serviço de letras respondeu " + c.getResponseCode() + ".");
            String raw; try (InputStream in = c.getInputStream()) { raw = new String(ContentText.read(in, 4 << 20), StandardCharsets.UTF_8); }
            JSONArray arr = new JSONArray(raw); String best = null; double bestScore = -1;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i); String synced = o.optString("syncedLyrics", ""), plain = o.optString("plainLyrics", ""); if (synced.isEmpty() && plain.isEmpty()) continue;
                double s = LyricsText.similarity(title, o.optString("trackName", "") + " " + o.optString("artistName", "")) + (synced.isEmpty() ? 0 : 0.2) - (ms > 0 && o.optDouble("duration", 0) > 0 ? Math.min(0.5, Math.abs(o.optDouble("duration", 0) * 1000 - ms) / 60000.0) : 0);
                if (s > bestScore) { bestScore = s; best = synced.isEmpty() ? plain : synced; }
            }
            if (best == null) throw new IOException("Não encontrei a letra de " + title + ".");
            Files.write(saved(f).toPath(), best.getBytes(StandardCharsets.UTF_8)); return best;
        }, this::show);
    }
}
