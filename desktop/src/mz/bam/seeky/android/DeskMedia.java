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

/**
 * The player shared by Música and Vídeos: queue, a "now playing" bar at the bottom of both screens,
 * the video in the window, plays counted for "mais ouvidas" (after 30 seconds or at the end) and
 * synced lyrics (.lrc beside the file, or LRCLIB).
 */
final class DeskMedia {
    final DeskMain d;
    final List<DeskLibrary.Track> queue = new ArrayList<>(); int index = -1;
    MediaPlayer player; final MediaView video = new MediaView();
    final Label now = DeskUi.heading("Nada a tocar"), who = DeskUi.muted(""), time = DeskUi.muted("0:00 / 0:00");
    final Slider seek = new Slider(0, 1, 0), volume = new Slider(0, 1, 0.8);
    final ListView<String> list = new ListView<>(); final ListView<String> lyricsView = new ListView<>();
    final List<long[]> lyricTimes = new ArrayList<>(); final Button play = DeskUi.iconButton("play", "Tocar / pausa", this::toggle);
    boolean seeking, counted, lyricsOpen;
    // 5.55: aleatório, repetir (desligado / tudo / esta faixa) e saltos de 10, 15 ou 30 s escolhidos nas Definições.
    boolean shuffle; int repeat; final List<Integer> history = new ArrayList<>(); final Random random = new Random();
    int skipSeconds() { try { int s = Integer.parseInt(d.store.get("media_skip", "15")); return s == 10 || s == 30 ? s : 15; } catch (NumberFormatException e) { return 15; } }
    void jump(int sign) { if (player == null) return; Duration t = player.getCurrentTime().add(Duration.seconds(sign * skipSeconds())); Duration total = player.getTotalDuration(); if (t.lessThan(Duration.ZERO)) t = Duration.ZERO; if (total != null && !total.isUnknown() && t.greaterThan(total)) t = total.subtract(Duration.millis(200)); player.seek(t); }
    /** The next track: in order, or at random (not the same one), wrapping round when "repetir tudo" is on. */
    int nextIndex() {
        if (queue.isEmpty()) return -1;
        if (shuffle && queue.size() > 1) { int n; do n = random.nextInt(queue.size()); while (n == index); return n; }
        if (index + 1 < queue.size()) return index + 1;
        return repeat == 1 ? 0 : -1;
    }
    void next() { int n = nextIndex(); if (n >= 0) { if (index >= 0) history.add(index); start(n); } }
    /** Previous: back to the start of this track after 3 s; otherwise the track played before (or the one above). */
    void previous() {
        if (player != null && player.getCurrentTime().toSeconds() > 3) { player.seek(Duration.ZERO); return; }
        if (!history.isEmpty()) { start(history.remove(history.size() - 1)); return; }
        if (index > 0) start(index - 1); else if (repeat == 1 && !queue.isEmpty()) start(queue.size() - 1); else if (player != null) player.seek(Duration.ZERO);
    }
    void ended() { count(); if (repeat == 2 && player != null) { player.seek(Duration.ZERO); player.play(); return; } int n = nextIndex(); if (n >= 0) { if (index >= 0) history.add(index); start(n); } else { play.setGraphic(DeskUi.icon("play", 20)); } }
    DeskMedia(DeskMain d) {
        this.d = d; video.setPreserveRatio(true); video.setFitHeight(340);
        seek.valueChangingProperty().addListener((o, a, b) -> { seeking = b; if (!b && player != null) player.seek(player.getTotalDuration().multiply(seek.getValue())); });
        seek.setOnMouseReleased(e -> { if (player != null) player.seek(player.getTotalDuration().multiply(seek.getValue())); });
        volume.valueProperty().addListener((o, a, b) -> { if (player != null) player.setVolume(b.doubleValue()); d.store.put("volume", String.format(Locale.ROOT, "%.2f", b.doubleValue())); });
        try { volume.setValue(Double.parseDouble(d.store.get("volume", "0.8"))); } catch (Exception ignored) {}
        list.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedIndex() >= 0) start(list.getSelectionModel().getSelectedIndex()); });
        shuffle = "1".equals(d.store.get("media_shuffle", "0")); try { repeat = Math.max(0, Math.min(2, Integer.parseInt(d.store.get("media_repeat", "0")))); } catch (NumberFormatException e) { repeat = 0; }
        lyricsView.setPlaceholder(DeskUi.muted("Sem letra. Toque em Procurar letra."));
        now.setWrapText(false); now.setMinWidth(60);
    }
    String currentPath() { return index >= 0 && index < queue.size() ? queue.get(index).path : ""; }
    boolean isVideo() { return player != null && index >= 0 && index < queue.size() && DeskLibrary.accepts(DeskLibrary.VIDEO, queue.get(index).path); }

    /** The bar at the bottom of Música and Vídeos, hidden while nothing was played. 5.56: one bar that is redrawn
     *  in place (shuffle, repeat, lyrics), instead of building the whole screen again. */
    final VBox barBox = new VBox(10);
    /** Told when the track changes, so the list shows which one is playing (set by the screen shown). */
    Runnable changed;
    Node bar() { fillBar(); return barBox; }
    void fillBar() {
        boolean shown = !queue.isEmpty(); barBox.setVisible(shown); barBox.setManaged(shown);
        if (!barBox.getStyleClass().contains("now-playing")) { barBox.getStyleClass().add("now-playing"); barBox.setPadding(new javafx.geometry.Insets(10, 16, 10, 16)); }
        if (!shown) { barBox.getChildren().clear(); return; }
        seek.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(seek, Priority.ALWAYS); volume.setPrefWidth(110);
        VBox words = new VBox(1, now, who); words.setMinWidth(120); words.setPrefWidth(260); words.setMaxWidth(320);
        Button lyrics = DeskUi.iconButton("lyrics", "Letra", () -> { lyricsOpen = !lyricsOpen; fillBar(); });
        Button queueB = DeskUi.iconButton("queue", "Fila", this::showQueue);
        int s = skipSeconds();
        Button mix = DeskUi.iconButton("shuffle", shuffle ? "Aleatório: ligado" : "Aleatório: desligado", () -> { shuffle = !shuffle; d.store.put("media_shuffle", shuffle ? "1" : "0"); d.status(shuffle ? "Aleatório ligado" : "Aleatório desligado"); fillBar(); });
        Button again = DeskUi.iconButton(repeat == 2 ? "repeat1" : "repeat", repeat == 0 ? "Repetir: desligado" : repeat == 1 ? "Repetir: tudo" : "Repetir: esta faixa", () -> { repeat = (repeat + 1) % 3; d.store.put("media_repeat", "" + repeat); d.status(repeat == 0 ? "Repetir desligado" : repeat == 1 ? "Repetir tudo" : "Repetir esta faixa"); fillBar(); });
        if (shuffle) mix.getStyleClass().add("on"); if (repeat > 0) again.getStyleClass().add("on");
        Button back = DeskUi.iconButton("rewind", "Recuar " + s + " s", () -> jump(-1)), fwd = DeskUi.iconButton("forward", "Avançar " + s + " s", () -> jump(1));
        HBox controls = DeskUi.row(mix, DeskUi.iconButton("prev", "Anterior", this::previous), back, play, fwd, DeskUi.iconButton("next", "Seguinte", this::next), again);
        HBox row = new HBox(14, words, controls, new VBox(2, seek, time), DeskUi.icon("speaker", 18), volume, queueB, lyrics); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(row.getChildren().get(2), Priority.ALWAYS);
        barBox.getChildren().setAll(row);
        if (lyricsOpen) { lyricsView.setPrefHeight(220); barBox.getChildren().add(0, new VBox(6, DeskUi.row(DeskUi.heading("Letra"), DeskUi.grow(), DeskUi.button("Procurar letra", false, this::findLyrics), DeskUi.button("Editar letra", false, this::editLyrics)), lyricsView)); }
    }
    /** The video, above the list in Vídeos. */
    Node videoCard() { video.fitWidthProperty().unbind(); VBox box = DeskUi.card(video); box.setAlignment(javafx.geometry.Pos.CENTER); return box; }
    void showQueue() {
        refreshList(); Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("Fila · " + queue.size()); DeskUi.style(dlg.getDialogPane());
        list.setPrefSize(460, 380); dlg.getDialogPane().setContent(list); dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dlg.show();
    }

    void play(List<DeskLibrary.Track> tracks, int at) { queue.clear(); queue.addAll(tracks); history.clear(); refreshList(); start(at); }
    /** Several chosen tracks, in their order, right after the one playing. */
    void playNext(List<DeskLibrary.Track> ts) { if (ts.isEmpty()) return; if (queue.isEmpty()) { play(ts, 0); return; } queue.addAll(Math.min(queue.size(), index + 1), ts); refreshList(); d.status(ts.size() == 1 ? "A seguir: " + ts.get(0).title : ts.size() + " músicas a seguir"); }
    void enqueue(List<DeskLibrary.Track> ts) { if (ts.isEmpty()) return; if (queue.isEmpty()) { play(ts, 0); return; } queue.addAll(ts); refreshList(); d.status(ts.size() == 1 ? "Na fila: " + ts.get(0).title : ts.size() + " músicas na fila"); }
    void playNext(DeskLibrary.Track t) { if (queue.isEmpty()) { play(Collections.singletonList(t), 0); return; } queue.add(Math.min(queue.size(), index + 1), t); refreshList(); d.status("A seguir: " + t.title); }
    void enqueue(DeskLibrary.Track t) { if (queue.isEmpty()) { play(Collections.singletonList(t), 0); return; } queue.add(t); refreshList(); d.status("Na fila: " + t.title); }
    /** Files opened from Ficheiros. */
    void playFiles(List<File> files, int at) {
        List<DeskLibrary.Track> list = new ArrayList<>();
        for (File f : files) { DeskLibrary.Track t = new DeskLibrary.Track(); t.path = f.getAbsolutePath(); DeskTags tags = DeskLibrary.accepts(DeskLibrary.AUDIO, f.getName()) ? DeskTags.read(f) : DeskLibrary.video(f); t.title = tags.title; t.artist = tags.artist; t.album = tags.album; list.add(t); }
        play(list, at); d.open(DeskLibrary.accepts(DeskLibrary.VIDEO, files.get(at).getName()) ? "videos" : "music");
    }
    void refreshList() { List<String> names = new ArrayList<>(); for (DeskLibrary.Track t : queue) names.add(t.title + (t.artist.isEmpty() ? "" : " — " + t.artist)); list.setItems(FXCollections.observableArrayList(names)); if (index >= 0 && index < names.size()) list.getSelectionModel().select(index); }
    void start(int at) {
        if (queue.isEmpty() || at < 0 || at >= queue.size()) return;
        stop(); index = at; DeskLibrary.Track t = queue.get(at); File f = new File(t.path);
        now.setText(t.title); who.setText(t.artist.isEmpty() ? t.album : t.artist + (t.album.isEmpty() ? "" : " · " + t.album));
        if (!DeskLibrary.playable(f.getName())) { DeskFiles.system(f); DeskLibrary.played(d.store, t.path); d.status(f.getName() + " abriu no leitor do Windows."); return; }
        try {
            Media m = new Media(f.toURI().toString()); player = new MediaPlayer(m); player.setVolume(volume.getValue()); counted = false;
            video.setMediaPlayer(player);
            player.currentTimeProperty().addListener((o, a, tm) -> tick(tm));
            player.setOnEndOfMedia(this::ended);
            player.setOnError(() -> d.status("Não foi possível tocar " + f.getName() + ": " + player.getError().getMessage()));
            player.play(); play.setGraphic(DeskUi.icon("pause", 20));
            refreshList(); loadLyrics(f);
            if (isVideo() && (d.current.equals("music") || d.current.equals("videos"))) { if (!d.current.equals("videos") || !video.isVisible() || video.getScene() == null) d.open("videos"); }
            fillBar(); if (changed != null) changed.run();
        } catch (Exception e) { d.status("Formato não suportado: " + f.getName() + ". Use MP3, M4A, WAV ou MP4, ou abra-o com o Windows."); }
    }
    void count() { if (!counted && index >= 0 && index < queue.size()) { counted = true; DeskLibrary.played(d.store, queue.get(index).path); } }
    void toggle() { if (player == null) { if (!queue.isEmpty()) start(Math.max(0, index)); return; } if (player.getStatus() == MediaPlayer.Status.PLAYING) { player.pause(); play.setGraphic(DeskUi.icon("play", 20)); } else { player.play(); play.setGraphic(DeskUi.icon("pause", 20)); } }
    void stop() { if (player != null) { player.stop(); player.dispose(); player = null; } }
    static String clock(Duration t) { if (t == null || t.isUnknown()) return "0:00"; long s = (long) t.toSeconds(); return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60); }
    long lastTick = -1;
    void tick(Duration t) {
        if (player == null) return; Duration total = player.getTotalDuration();
        // 5.56: the clock moves many times a second; redraw a few times a second only (lighter while music plays).
        long step = (long) (t.toMillis() / 250); if (step == lastTick) return; lastTick = step;
        if (!counted && t.toSeconds() >= 30) count();
        time.setText(clock(t) + " / " + clock(total)); if (!seeking && total != null && total.toMillis() > 0) seek.setValue(t.toMillis() / total.toMillis());
        long ms = (long) t.toMillis(); int line = -1; for (int i = 0; i < lyricTimes.size(); i++) if (lyricTimes.get(i)[0] <= ms) line = i;
        if (line >= 0 && line != lyricsView.getSelectionModel().getSelectedIndex()) { lyricsView.getSelectionModel().select(line); lyricsView.scrollTo(Math.max(0, line - 3)); }
    }
    // ---- lyrics ----
    static File saved(File song) { return new File(DeskStore.folder("letras"), Integer.toHexString(song.getAbsolutePath().hashCode()) + ".lrc"); }
    void loadLyrics(File f) {
        lyricTimes.clear(); lyricsView.getItems().clear();
        File beside = new File(f.getParentFile(), f.getName().replaceFirst("\\.[^.]+$", "") + ".lrc"), mine = saved(f);
        File src = mine.isFile() ? mine : beside.isFile() ? beside : null; if (src == null) return; // the user's own correction wins
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
    /** 5.55: correct a wrong lyric by hand (plain text, or "[mm:ss.cc] verso" lines to keep it in time). */
    void editLyrics() {
        if (index < 0 || index >= queue.size()) return; File f = new File(queue.get(index).path), mine = saved(f), beside = new File(f.getParentFile(), f.getName().replaceFirst("\\.[^.]+$", "") + ".lrc");
        String current = ""; try { File src = mine.isFile() ? mine : beside.isFile() ? beside : null; if (src != null) current = new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8); } catch (IOException ignored) {}
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("Editar letra · " + queue.get(index).title); DeskUi.style(dlg.getDialogPane());
        TextArea text = new TextArea(current); text.setWrapText(true); text.setPrefSize(560, 420); text.setPromptText("Escreva ou cole a letra. Para acompanhar a música use linhas como [01:02.30] verso.");
        Button stamp = DeskUi.button("Marcar o tempo actual na linha", false, () -> { if (player == null) return; Duration t = player.getCurrentTime(); long ms = (long) t.toMillis(); String tag = String.format(Locale.ROOT, "[%02d:%02d.%02d] ", ms / 60000, (ms / 1000) % 60, (ms / 10) % 100); int caret = text.getCaretPosition(); int start = text.getText().lastIndexOf('\n', Math.max(0, caret - 1)) + 1; String line = text.getText().substring(start).replaceFirst("(?s)\n.*", ""); if (line.matches("^\\[\\d+:\\d+(?:[.:]\\d+)?\\].*")) { text.replaceText(start, start + line.indexOf(']') + 1 + (line.length() > line.indexOf(']') + 1 && line.charAt(line.indexOf(']') + 1) == ' ' ? 1 : 0), tag); } else text.insertText(start, tag); int nl = text.getText().indexOf('\n', start); text.positionCaret(nl < 0 ? text.getLength() : nl + 1); });
        dlg.getDialogPane().setContent(new VBox(8, text, DeskUi.row(stamp, DeskUi.muted("Toque enquanto a música toca para pôr o tempo no início da linha.")))); 
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE), remove = new ButtonType("Apagar letra", ButtonBar.ButtonData.LEFT);
        dlg.getDialogPane().getButtonTypes().addAll(save, remove, ButtonType.CANCEL);
        dlg.showAndWait().ifPresent(b -> {
            try {
                if (b == save) { Files.write(mine.toPath(), text.getText().getBytes(StandardCharsets.UTF_8)); show(text.getText()); d.status("Letra guardada."); }
                else if (b == remove) { Files.write(mine.toPath(), new byte[0]); show(""); d.status("Letra apagada desta música."); }
            } catch (IOException e) { DeskUi.info("Não foi possível guardar a letra", e.getMessage()); }
        });
    }
    void findLyrics() {
        if (index < 0 || index >= queue.size()) return; DeskLibrary.Track tr = queue.get(index); File f = new File(tr.path); String title = LyricsText.clean((tr.artist.isEmpty() ? "" : tr.artist + " ") + tr.title); long ms = player == null || player.getTotalDuration().isUnknown() ? 0 : (long) player.getTotalDuration().toMillis();
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
