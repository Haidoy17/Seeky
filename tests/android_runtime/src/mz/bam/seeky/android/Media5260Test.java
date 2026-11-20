package mz.bam.seeky.android;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.26.0: fila, efeitos de áudio, letras, biblioteca de música/vídeo, equalizador e notificação de actualizações. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Media5260Test {
    final Application app = RuntimeEnvironment.getApplication();

    @Before public void reset() { MediaQueue.items.clear(); MediaQueue.order = new int[0]; MediaQueue.position = -1; MediaQueue.shuffle = false; MediaQueue.repeat = 0; MediaQueue.loaded = false; MediaQueue.file(app).delete(); }

    static List<MediaQueue.Item> items(int n) {
        List<MediaQueue.Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new MediaQueue.Item("content://t/" + i, "Faixa " + i, "Artista", "Álbum", 1000));
        return out;
    }

    @Test public void queueAdvancesRepeatsShufflesAndPersists() {
        MediaQueue.set(app, items(4), 1);
        assertEquals("Faixa 1", MediaQueue.current().title);
        assertEquals("Faixa 2", MediaQueue.next(app, false).title);
        assertEquals("Faixa 3", MediaQueue.next(app, false).title);
        assertNull("fim sem repetir", MediaQueue.next(app, false));
        MediaQueue.cycleRepeat(app);
        assertEquals(MediaQueue.REPEAT_ALL, MediaQueue.repeat);
        assertEquals("Faixa 0", MediaQueue.next(app, false).title);
        assertEquals("Faixa 3", MediaQueue.previous(app).title);
        MediaQueue.insertNext(app, new MediaQueue.Item("content://t/x", "Extra", "", "", 0));
        assertEquals("Extra", MediaQueue.next(app, false).title);
        MediaQueue.append(app, new MediaQueue.Item("content://t/y", "Última", "", "", 0));
        assertEquals("Última", MediaQueue.upcoming().get(MediaQueue.upcoming().size() - 1).title);
        MediaQueue.setShuffle(app, true);
        assertEquals("a faixa actual continua a primeira", "Extra", MediaQueue.current().title);
        Set<String> all = new HashSet<>();
        for (MediaQueue.Item it : MediaQueue.upcoming()) all.add(it.title);
        assertEquals(6, all.size());
        MediaQueue.loaded = false;
        MediaQueue.items.clear();
        MediaQueue.load(app);
        assertEquals(6, MediaQueue.size());
        assertEquals("Extra", MediaQueue.current().title);
        MediaQueue.opened(app, "content://t/2", "Faixa 2");
        assertEquals("Faixa 2", MediaQueue.current().title);
        MediaQueue.opened(app, "file:///sdcard/solta.mp3", "solta.mp3");
        assertEquals(1, MediaQueue.size());
    }

    @Test public void effectsMathPresetsAndSettings() {
        AudioEffects.Settings s = new AudioEffects.Settings();
        s.bass = 50;
        s.treble = 6;
        float[] g = AudioEffects.effective(s);
        assertEquals(6f, g[0], .01f);
        assertEquals(0f, g[5], .01f);
        assertEquals(6f, g[9], .01f);
        s.gains[0] = 15;
        assertEquals(15f, AudioEffects.effective(s)[0], .01f);
        float[] curve = {0, 0, 0, 0, 0, 6, 0, 0, 0, 0};
        assertEquals(6f, AudioEffects.response(curve, 1000), .01f);
        assertEquals(0f, AudioEffects.response(curve, 20), .01f);
        float mid = AudioEffects.response(curve, 700);
        assertTrue(mid > 0 && mid < 6);
        for (int i = 0; i < 9; i++) assertTrue(AudioEffects.cutoff(i) > AudioEffects.FREQ[i] && AudioEffects.cutoff(i) < AudioEffects.FREQ[i + 1]);
        assertArrayEquals(new float[]{0, -24}, AudioEffects.balance(-100), .01f);
        assertArrayEquals(new float[]{-12, 0}, AudioEffects.balance(50), .01f);
        for (float[] p : AudioEffects.PRESETS.values()) assertEquals(10, p.length);
        assertTrue(AudioEffects.PRESETS.containsKey("Gospel") && AudioEffects.PRESETS.containsKey("Voz falada"));
        s.preset = "Rock";
        s.gains = AudioEffects.PRESETS.get("Rock").clone();
        s.virtualizer = 40;
        AudioEffects.save(app, s);
        AudioEffects.Settings back = AudioEffects.load(app);
        assertEquals("Rock", back.preset);
        assertEquals(40, back.virtualizer);
        assertEquals(5f, back.gains[0], .01f);
        AudioEffects.saveCustom(app, "Carro", new float[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        assertEquals(10f, AudioEffects.custom(app).get("Carro")[9], .01f);
    }

    // ---- letras ----

    @Test public void lrcParsingAndCurrentLine() {
        String lrc = "[ar:Artista]\n[offset:+500]\n[00:10.00]Primeira\n[00:12.50][01:00.00]Refrão\n[00:20.5]Terceira <00:21.00>palavra\n";
        List<Lyrics.Line> lines = Lyrics.parseLrc(lrc);
        assertEquals(4, lines.size());
        assertEquals(9500, lines.get(0).time);
        assertEquals("Refrão", lines.get(1).text);
        assertEquals(20000, lines.get(2).time);
        assertEquals("Terceira palavra", lines.get(2).text);
        assertEquals(59500, lines.get(3).time);
        assertEquals(-1, Lyrics.current(lines, 5000));
        assertEquals(1, Lyrics.current(lines, 15000));
        assertEquals(3, Lyrics.current(lines, 90000));
        assertEquals("Primeira\nRefrão\nTerceira palavra", Lyrics.plain(lrc));
        Lyrics.Result r = Lyrics.result(lrc, "Seeky");
        assertTrue(r.isSynced());
        assertTrue(Lyrics.lrcText(r).startsWith("[00:09.50]Primeira"));
    }

    static byte[] syncsafe(int n) { return new byte[]{(byte) ((n >> 21) & 0x7f), (byte) ((n >> 14) & 0x7f), (byte) ((n >> 7) & 0x7f), (byte) (n & 0x7f)}; }

    static byte[] id3(int version, byte[] frameBody) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write("TIT2".getBytes(StandardCharsets.ISO_8859_1));
        byte[] tit = {3, 'O', 'l', 'a'};
        frame.write(version >= 4 ? syncsafe(tit.length) : new byte[]{0, 0, 0, (byte) tit.length});
        frame.write(new byte[]{0, 0});
        frame.write(tit);
        frame.write("USLT".getBytes(StandardCharsets.ISO_8859_1));
        int n = frameBody.length;
        frame.write(version >= 4 ? syncsafe(n) : new byte[]{(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n});
        frame.write(new byte[]{0, 0});
        frame.write(frameBody);
        byte[] frames = frame.toByteArray();
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(new byte[]{'I', 'D', '3', (byte) version, 0, 0});
        all.write(syncsafe(frames.length));
        all.write(frames);
        all.write(new byte[64]);
        return all.toByteArray();
    }

    File temp(String name, byte[] data) throws IOException {
        File f = new File(app.getCacheDir(), name);
        try (OutputStream o = new FileOutputStream(f)) { o.write(data); }
        return f;
    }

    @Test public void embeddedLyricsFromId3FlacAndMp4() throws Exception {
        ByteArrayOutputStream utf8 = new ByteArrayOutputStream();
        utf8.write(new byte[]{3, 'p', 'o', 'r', 'd', 'e', 's', 'c', 0});
        utf8.write("Graça infinita\nque me salvou".getBytes(StandardCharsets.UTF_8));
        assertEquals("Graça infinita\nque me salvou", Lyrics.embedded(app, Uri.fromFile(temp("a.mp3", id3(4, utf8.toByteArray())))));
        ByteArrayOutputStream utf16 = new ByteArrayOutputStream();
        utf16.write(new byte[]{1, 'e', 'n', 'g', (byte) 0xFF, (byte) 0xFE, 0, 0});
        utf16.write(new byte[]{(byte) 0xFF, (byte) 0xFE});
        utf16.write("Amazing grace".getBytes(StandardCharsets.UTF_16LE));
        assertEquals("Amazing grace", Lyrics.embedded(app, Uri.fromFile(temp("b.mp3", id3(3, utf16.toByteArray())))));

        ByteArrayOutputStream flac = new ByteArrayOutputStream();
        flac.write("fLaC".getBytes(StandardCharsets.ISO_8859_1));
        flac.write(new byte[]{0, 0, 0, 34});
        flac.write(new byte[34]);
        ByteArrayOutputStream vc = new ByteArrayOutputStream();
        byte[] vendor = "seeky".getBytes(StandardCharsets.UTF_8);
        vc.write(le(vendor.length)); vc.write(vendor); vc.write(le(2));
        byte[] f1 = "TITLE=Hino".getBytes(StandardCharsets.UTF_8), f2 = "LYRICS=Santo, santo, santo".getBytes(StandardCharsets.UTF_8);
        vc.write(le(f1.length)); vc.write(f1); vc.write(le(f2.length)); vc.write(f2);
        byte[] body = vc.toByteArray();
        flac.write(new byte[]{(byte) 0x84, (byte) (body.length >> 16), (byte) (body.length >> 8), (byte) body.length});
        flac.write(body);
        assertEquals("Santo, santo, santo", Lyrics.embedded(app, Uri.fromFile(temp("c.flac", flac.toByteArray()))));

        ByteArrayOutputStream mp4 = new ByteArrayOutputStream();
        mp4.write(new byte[]{0, 0, 0, 20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ', 0, 0, 0, 0, 'M', '4', 'A', ' '});
        byte[] text = "Aleluia".getBytes(StandardCharsets.UTF_8);
        int dataLen = 16 + text.length;
        mp4.write(new byte[]{0, 0, 0, (byte) (dataLen + 8), (byte) 0xA9, 'l', 'y', 'r', 0, 0, 0, (byte) dataLen, 'd', 'a', 't', 'a', 0, 0, 0, 1, 0, 0, 0, 0});
        mp4.write(text);
        mp4.write(new byte[32]);
        assertEquals("Aleluia", Lyrics.embedded(app, Uri.fromFile(temp("d.m4a", mp4.toByteArray()))));
    }

    static byte[] le(int v) { return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}; }

    @Test public void sidecarCacheAndLrclibJson() throws Exception {
        File song = temp("canção.mp3", new byte[100]);
        temp("canção.lrc", "[00:01.00]Linha um\n[00:03.00]Linha dois".getBytes(StandardCharsets.UTF_8));
        Lyrics.Result side = Lyrics.find(app, Uri.fromFile(song), "Canção", "", "", 0, false);
        assertEquals("Ficheiro LRC", side.source);
        assertEquals(2, side.synced.size());
        Uri other = Uri.parse("content://x/sem-letra");
        assertFalse(Lyrics.find(app, other, "Sem letra", "", "", 0, false).found());
        Lyrics.store(app, other.toString(), "Texto colado", "Seeky");
        Lyrics.Result stored = Lyrics.find(app, other, "Sem letra", "", "", 0, false);
        assertEquals("Texto colado", stored.text);
        assertEquals("Seeky", stored.source);
        Lyrics.forget(app, other.toString());
        assertFalse(Lyrics.find(app, other, "Sem letra", "", "", 0, false).found());
        Lyrics.Result net = Lyrics.fromJson(new org.json.JSONObject("{\"plainLyrics\":\"You call me out\",\"syncedLyrics\":\"[00:05.10] You call me out\",\"instrumental\":false}"));
        assertTrue(net.isSynced());
        assertEquals(5100, net.synced.get(0).time);
        assertEquals("LRCLIB", net.source);
    }

    // ---- biblioteca ----

    static MediaLibrary.Track t(String uri, String title, String artist, String album, int year, String genre, String folder, String kind) {
        MediaLibrary.Track t = new MediaLibrary.Track();
        t.uri = uri; t.title = title; t.artist = artist; t.album = album; t.year = year; t.genre = genre; t.folder = folder; t.kind = kind; t.duration = 200000; t.added = System.currentTimeMillis();
        return t;
    }

    void fill(MediaLibrary db) {
        android.database.sqlite.SQLiteDatabase d = db.getWritableDatabase();
        db.upsert(d, t("content://m/1", "Oceans", "Hillsong UNITED", "Zion", 2013, "Gospel", "Música", "audio"), 0);
        db.upsert(d, t("content://m/2", "Touch the Sky", "Hillsong UNITED", "Empires", 2015, "Gospel", "Música", "audio"), 0);
        db.upsert(d, t("content://m/3", "Nzambi", "Artista MZ", "Raízes", 2020, "Marrabenta", "Download", "audio"), 0);
        db.upsert(d, t("content://m/4", "Sem dados", "", "", 0, "", "WhatsApp Audio", "audio"), 0);
        db.upsert(d, t("content://v/1", "Culto de domingo", "", "", 2024, "", "Camera", "video"), 0);
    }

    @Test public void libraryGroupsSearchesAndCountsPlays() {
        try (MediaLibrary db = new MediaLibrary(app)) {
            fill(db);
            assertEquals(4, db.tracks("audio", null, null, "title", 0).size());
            assertEquals(1, db.tracks("video", null, null, "title", 0).size());
            List<MediaLibrary.Group> artists = db.groups("audio", "artist", "");
            assertEquals(3, artists.size());
            MediaLibrary.Group hill = null;
            for (MediaLibrary.Group g : artists) if (g.name.equals("Hillsong UNITED")) hill = g;
            assertEquals(2, hill.count);
            assertEquals(2, hill.albums);
            assertEquals(2020, Integer.parseInt(db.groups("audio", "year", "").get(0).name));
            assertEquals(1, db.groups("audio", "genre", "marra").size());
            assertTrue(db.groups("audio", "title; DROP TABLE tracks", "").isEmpty());
            assertEquals(1, db.tracks("audio", MediaLibrary.search("zion"), MediaLibrary.searchArgs("zion"), null, 0).size());
            db.favorite("content://m/3", true);
            assertEquals(1, db.tracks("audio", "favorite=1", null, null, 0).size());
            MediaLibrary.played(app, "content://m/1");
            MediaLibrary.played(app, "content://m/1");
            assertEquals(2, db.tracks("audio", "plays>0", null, null, 0).get(0).plays);
            assertEquals(4, db.totals("audio")[0]);
            assertEquals(0, db.scanStore(app, "audio"));
        }
        assertEquals(2015, MediaLibrary.year("2015", null));
        assertEquals(1998, MediaLibrary.year(null, "19980101T000000"));
        assertEquals(3, MediaLibrary.leadingInt("3/12"));
        assertEquals("Minha musica", MediaLibrary.stripExt("Minha_musica.mp3"));
    }

    void idle(ActivityController<LibraryActivity> c, LibraryActivity a) throws Exception {
        for (int i = 0; i < 6; i++) { a.work.submit(() -> {}).get(20, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS); }
    }

    void capture(View v, String name) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 360, 800);
        Bitmap b = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(System.getProperty("seeky.screenshot") + "-5260-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }

    @Test public void musicLibraryScreenTabsDrillAndPlay() throws Exception {
        try (MediaLibrary db = new MediaLibrary(app)) { fill(db); }
        MediaLibrary.LibraryPrefs.set(app, "scan_audio", System.currentTimeMillis());
        ActivityController<LibraryActivity> c = Robolectric.buildActivity(LibraryActivity.class, new Intent(app, LibraryActivity.class).putExtra("kind", "audio"));
        LibraryActivity a = c.get();
        a.setTheme(android.R.style.Theme_Material_NoActionBar);
        c.setup();
        idle(c, a);
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        String labels = BooksToolsTest.labels(root);
        for (String s : new String[]{"Música", "Faixas", "Artistas", "Álbuns", "Géneros", "Anos", "Pastas", "Favoritas", "Mais ouvidas", "Tocar tudo", "Aleatório"}) assertTrue(s, labels.contains(s));
        assertEquals(4, a.rows.size());
        a.list.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        a.list.layout(0, 0, 360, 600);
        capture(root, "musica-faixas");
        a.tab = "Artistas";
        a.load();
        idle(c, a);
        assertEquals(3, a.rows.size());
        capture(root, "musica-artistas");
        a.drill("artist", "Hillsong UNITED");
        idle(c, a);
        assertEquals(2, a.rows.size());
        assertEquals("Hillsong UNITED", a.title.getText().toString());
        assertEquals("álbuns por ordem: Empires antes de Zion", "Touch the Sky", a.visible.get(0).title);
        a.play(a.visible, 1, false);
        assertEquals(2, MediaQueue.size());
        assertEquals("Oceans", MediaQueue.current().title);
        boolean queued = false;
        for (Intent started; (started = shadowOf(a).getNextStartedService()) != null; ) queued |= MediaPlaybackService.QUEUE.equals(started.getAction());
        assertTrue("serviço recebe a fila", queued);
        assertEquals(MediaActivity.class.getName(), shadowOf(a).getNextStartedActivity().getComponent().getClassName());
        a.trackMenu(a.visible.get(0));
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        UiPanel menu = new ArrayList<>(UiPanel.open.get(a)).get(UiPanel.open.get(a).size() - 1);
        String options = BooksToolsTest.labels(menu.root);
        for (String s : new String[]{"A seguir", "Na fila", "Adicionar às favoritas", "Partilhar", "Detalhes", "Remover da biblioteca"}) assertTrue(s, options.contains(s));
        UiPanel.dismissAll(a);
        a.menu();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        options = BooksToolsTest.labels(UiPanel.open.get(a).iterator().next().root);
        for (String s : new String[]{"Procurar automaticamente", "Adicionar pasta", "Adicionar músicas", "Equalizador e efeitos", "Letras da internet"}) assertTrue(s, options.contains(s));
        UiPanel.dismissAll(a);
        c.pause().stop().destroy();
    }

    @Test public void videoLibraryShowsVideos() throws Exception {
        try (MediaLibrary db = new MediaLibrary(app)) { fill(db); }
        MediaLibrary.LibraryPrefs.set(app, "scan_video", System.currentTimeMillis());
        ActivityController<LibraryActivity> c = Robolectric.buildActivity(LibraryActivity.class, new Intent(app, LibraryActivity.class).putExtra("kind", "video"));
        LibraryActivity a = c.get();
        a.setTheme(android.R.style.Theme_Material_NoActionBar);
        c.setup();
        idle(c, a);
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        String labels = BooksToolsTest.labels(root);
        assertTrue(labels.contains("Vídeos") && labels.contains("Pastas") && labels.contains("Ver tudo"));
        assertEquals(1, a.rows.size());
        c.pause().stop().destroy();
    }

    @Test public void equalizerScreenAppliesPresetsAndKnobs() throws Exception {
        AudioEffects.save(app, new AudioEffects.Settings());
        ActivityController<EqualizerActivity> c = Robolectric.buildActivity(EqualizerActivity.class);
        EqualizerActivity a = c.setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        View root = a.getWindow().getDecorView();
        String labels = BooksToolsTest.labels(root);
        for (String s : new String[]{"Equalizador", "Plano", "Rock", "Gospel", "Voz falada", "Volume", "Balanço", "Limitador", "Guardar predefinição"}) assertTrue(s, labels.contains(s));
        for (String k : new String[]{"Graves", "Agudos", "Virtualização", "Volume extra", "Equalizador de 10 bandas"}) assertNotNull(k, PageTurnTest.findDescription(root, k));
        TextView rock = (TextView) VoiceToolsTest.findText(root, "Rock");
        rock.performClick();
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS);
        assertEquals("Rock", AudioEffects.load(app).preset);
        assertEquals(5f, AudioEffects.load(app).gains[0], .01f);
        View content = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        capture(content, "equalizador");
        c.pause().stop().destroy();
    }

    @Test public void playerHasTrackControlsQueueAndSyncedLyrics() throws Exception {
        File song = new File(app.getCacheDir(), "hino.wav");
        VoiceToolsTest.writeWav(song, new byte[48000]);
        MediaQueue.set(app, items(3), 0);
        ActivityController<MediaActivity> c = Robolectric.buildActivity(MediaActivity.class, new Intent(app, MediaActivity.class).setDataAndType(Uri.parse("content://seeky.none/hino.wav"), "audio/wav").putExtra("name", "Hino.wav"));
        MediaActivity a = c.setup().get();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        for (String d : new String[]{"Faixa anterior", "Faixa seguinte", "Aleatório", "Repetir", "Letra", "Fila de reprodução", "Equalizador"}) assertNotNull(d, PageTurnTest.findDescription(root, d));
        a.toggleLyrics();
        a.showLyricsResult(Lyrics.result("[00:01.00]Graça infinita\n[00:04.00]Que me salvou\n[00:08.00]Perdido estava", "LRCLIB"));
        a.syncLyrics(5000);
        TextView current = (TextView) a.lyricLines.getChildAt(1);
        assertEquals(a.palette.accent, current.getCurrentTextColor());
        assertTrue(BooksToolsTest.labels(root).contains("sincronizada"));
        capture(root, "letra");
        a.queuePanel();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        assertTrue(BooksToolsTest.labels(UiPanel.open.get(a).iterator().next().root).contains("Faixa 2"));
        UiPanel.dismissAll(a);
        c.pause().stop().destroy();
    }

    @Test public void updateNotificationsAreOnByDefaultAndConfigurable() throws Exception {
        assertTrue(AppUpdates.notifications(app));
        assertFalse("sem versão instalada conhecida, não avisa", UpdateJob.checkAndNotify(app));
        android.app.Activity act = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        AppUpdates.settings(act);
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        assertTrue(BooksToolsTest.labels(UiPanel.open.get(act).iterator().next().root).contains("Notificar novas versões: activo"));
        UpdateJob.schedule(app);
        assertNotNull(app.getSystemService(android.app.job.JobScheduler.class).getPendingJob(UpdateJob.ID));
        AppUpdates.prefs(app).edit().putBoolean("notify", false).apply();
        UpdateJob.schedule(app);
        assertNull(app.getSystemService(android.app.job.JobScheduler.class).getPendingJob(UpdateJob.ID));
    }
}
