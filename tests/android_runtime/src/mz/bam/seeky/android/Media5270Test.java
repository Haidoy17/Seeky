package mz.bam.seeky.android;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.27.0: leitor reorganizado (áudio e vídeo), letras online/colar/copiar e mais ouvidas por período. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Media5270Test {
    final Application app = RuntimeEnvironment.getApplication();

    @Before public void reset() { MediaQueue.items.clear(); MediaQueue.order = new int[0]; MediaQueue.position = -1; MediaQueue.shuffle = false; MediaQueue.repeat = 0; MediaQueue.loaded = false; MediaQueue.file(app).delete(); }

    void capture(View v, String name) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 360, 800);
        Bitmap b = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(System.getProperty("seeky.screenshot") + "-5270-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }

    MediaActivity player(String name, String type) {
        ActivityController<MediaActivity> c = Robolectric.buildActivity(MediaActivity.class, new Intent(app, MediaActivity.class).setDataAndType(Uri.parse("content://seeky.none/" + name), type).putExtra("name", name));
        MediaActivity a = c.setup().get();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        return a;
    }

    @Test public void audioPlayerIsOrganisedInFixedPlaces() throws Exception {
        MediaActivity a = player("Graça.mp3", "audio/mpeg");
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        String labels = BooksToolsTest.labels(root);
        for (String s : new String[]{"A reproduzir", "Capa", "Letra", "Fila", "Equalizador", "Velocidade", "Temporizador", "Partilhar", "1×"}) assertTrue(s, labels.contains(s));
        for (String d : new String[]{"Aleatório", "Faixa anterior", "Reproduzir", "Faixa seguinte", "Repetir", "Favorita", "Mais opções"}) assertNotNull(d, PageTurnTest.findDescription(root, d));
        assertFalse("sem botões de ±10 s soltos no ecrã da música", labels.contains("−10 s"));
        capture(root, "musica-capa");
        a.menu();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        String menu = BooksToolsTest.labels(UiPanel.open.get(a).iterator().next().root);
        for (String s : new String[]{"REPRODUÇÃO", "ESTA FAIXA", "BIBLIOTECA", "−10 s", "+10 s", "Capítulos e títulos", "Detalhes", "Abrir a biblioteca de música", "Parar reprodução"}) assertTrue(s, menu.contains(s));
        capture(UiPanel.open.get(a).iterator().next().root, "musica-mais");
        UiPanel.dismissAll(a);
    }

    @Test public void lyricsCanBeSearchedPastedCopiedAndAppliedFromTheClipboard() throws Exception {
        MediaActivity a = player("Oceans.mp3", "audio/mpeg");
        a.showPage(MediaActivity.LYRICS);
        a.showLyricsResult(new Lyrics.Result());
        String labels = BooksToolsTest.labels(a.lyricsPanel);
        for (String s : new String[]{"Procurar online", "Google", "Colar", "Copiar", "Editar", "Procurar no Google e copiar", "Colar letra copiada"}) assertTrue(s, labels.contains(s));
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        capture(root, "letra-vazia");
        ClipboardManager cm = (ClipboardManager) app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        a.clipboardBefore = "antigo";
        a.awaitingClipboard = true;
        cm.setPrimaryClip(ClipData.newPlainText("x", "You call me out upon the waters\nThe great unknown\nWhere feet may fail\nAnd there I find You in the mystery"));
        a.checkClipboard();
        assertNotNull(a.lyrics);
        assertEquals("Copiada da internet", a.lyrics.source);
        assertTrue(a.lyrics.text.startsWith("You call me out"));
        assertFalse(a.awaitingClipboard);
        assertEquals("You call me out upon the waters\nThe great unknown\nWhere feet may fail\nAnd there I find You in the mystery", Lyrics.cached(app, a.uri.toString()).text);
        a.copyLyrics();
        assertTrue(cm.getPrimaryClip().getItemAt(0).getText().toString().contains("great unknown"));
        cm.setPrimaryClip(ClipData.newPlainText("x", "[00:01.00]Linha um\n[00:03.00]Linha dois"));
        a.pasteLyrics();
        assertTrue(a.lyrics.isSynced());
        a.resizeLyrics(4);
        assertEquals(24, a.lyricSize);
        capture(root, "letra-colada");
        a.awaitingClipboard = true;
        a.clipboardBefore = "";
        cm.setPrimaryClip(ClipData.newPlainText("x", "https://exemplo.com"));
        a.checkClipboard();
        assertTrue("um endereço não é aplicado como letra", a.awaitingClipboard);
        assertTrue(Lyrics.looksLikeLyrics("a\nb\nc\nlinha suficientemente longa para contar como letra"));
        assertEquals("a\n\nb", Lyrics.cleanPasted("a  \r\n\r\n\r\n\r\nb"));
        a.results("Oceans Hillsong", java.util.Collections.emptyList(), null);
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        assertTrue(BooksToolsTest.labels(UiPanel.open.get(a).iterator().next().root).contains("Procurar no Google"));
        UiPanel.dismissAll(a);
    }

    @Test public void videoPlayerRotatesScreenAndImageAndChangesFit() throws Exception {
        MediaActivity a = player("Culto.mp4", "video/mp4");
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        for (String d : new String[]{"Rodar ecrã", "Rodar imagem", "Ajustar", "Velocidade", "Partilhar", "Recuar 10 segundos", "Avançar 10 segundos", "Reproduzir"}) assertNotNull(d, PageTurnTest.findDescription(root, d));
        a.cycleOrientation();
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, a.getRequestedOrientation());
        a.cycleOrientation();
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, a.getRequestedOrientation());
        a.rotateImage();
        assertEquals(90, a.rotation);
        a.cycleFit();
        assertEquals("Preencher", a.fitLabel.getText().toString());
        capture(root, "video");
        Matrix fit = MediaActivity.videoMatrix(1080, 1920, 1920, 1080, 0, 0);
        RectF box = new RectF(0, 0, 1080, 1920);
        fit.mapRect(box);
        assertEquals("deitado num ecrã vertical: largura toda", 1080, box.width(), 1);
        assertEquals(1080 * 1080 / 1920f, box.height(), 1);
        Matrix turned = MediaActivity.videoMatrix(1080, 1920, 1920, 1080, 90, 0);
        box = new RectF(0, 0, 1080, 1920);
        turned.mapRect(box);
        assertEquals("rodado 90°: ocupa a altura", 1920, box.height(), 2);
        assertEquals(1080, box.width(), 2);
        Matrix fill = MediaActivity.videoMatrix(1080, 1920, 1920, 1080, 0, 1);
        box = new RectF(0, 0, 1080, 1920);
        fill.mapRect(box);
        assertTrue("preencher: sem barras", box.height() >= 1919 && box.width() > 1080);
    }

    @Test public void mostPlayedTodayWeekMonthAndRecentlyAdded() throws Exception {
        long now = System.currentTimeMillis(), day = 86400000L;
        try (MediaLibrary db = new MediaLibrary(app)) {
            android.database.sqlite.SQLiteDatabase d = db.getWritableDatabase();
            String[] names = {"Hoje", "Semana", "Mês", "Antiga"};
            for (int i = 0; i < names.length; i++) {
                MediaLibrary.Track t = new MediaLibrary.Track();
                t.uri = "content://p/" + i; t.title = names[i]; t.kind = "audio"; t.added = now - i * 10 * day;
                db.upsert(d, t, 0);
            }
        }
        for (int k = 0; k < 3; k++) MediaLibrary.played(app, "content://p/0", now - 1000 * k);
        for (int k = 0; k < 4; k++) MediaLibrary.played(app, "content://p/1", now - 3 * day - k);
        for (int k = 0; k < 5; k++) MediaLibrary.played(app, "content://p/2", now - 20 * day - k);
        MediaLibrary.played(app, "content://p/3", now - 90 * day);
        try (MediaLibrary db = new MediaLibrary(app)) {
            List<MediaLibrary.Track> today = db.top("audio", MediaLibrary.TODAY, now, 10);
            assertEquals(1, today.size());
            assertEquals("Hoje", today.get(0).title);
            assertEquals(3, today.get(0).plays);
            List<MediaLibrary.Track> week = db.top("audio", MediaLibrary.WEEK, now, 10);
            assertEquals("Semana", week.get(0).title);
            assertEquals(2, week.size());
            List<MediaLibrary.Track> month = db.top("audio", MediaLibrary.MONTH, now, 10);
            assertEquals("Mês", month.get(0).title);
            assertEquals(3, month.size());
            assertEquals(4, db.top("audio", MediaLibrary.EVER, now, 10).size());
            assertEquals("Hoje", db.tracks("audio", null, null, "added DESC", 1).get(0).title);
        }
        MediaLibrary.LibraryPrefs.set(app, "scan_audio", now);
        ActivityController<LibraryActivity> c = Robolectric.buildActivity(LibraryActivity.class, new Intent(app, LibraryActivity.class).putExtra("kind", "audio"));
        LibraryActivity a = c.get();
        a.setTheme(android.R.style.Theme_Material_NoActionBar);
        c.setup();
        for (int i = 0; i < 6; i++) { a.work.submit(() -> {}).get(20, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS); }
        a.list.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1400, View.MeasureSpec.EXACTLY));
        a.list.layout(0, 0, 360, 1400);
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        String labels = BooksToolsTest.labels(root);
        for (String s : new String[]{"Adicionadas recentemente", "Mais ouvidas", "Hoje", "Esta semana", "Este mês", "Sempre", "Ouvidas recentemente"}) assertTrue(s, labels.contains(s));
        capture(root, "inicio-musica");
        a.period = MediaLibrary.MONTH;
        a.special = "top";
        a.load();
        for (int i = 0; i < 4; i++) { a.work.submit(() -> {}).get(20, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS); }
        assertEquals(3, a.visible.size());
        assertTrue(a.title.getText().toString().contains("Este mês"));
        a.onBackPressed();
        assertNull(a.special);
        c.pause().stop().destroy();
    }
}
