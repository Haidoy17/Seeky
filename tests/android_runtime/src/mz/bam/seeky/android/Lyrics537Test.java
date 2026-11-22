package mz.bam.seeky.android;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.37: every audio gets lyrics — online search first, then AI transcription saved piece by piece. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Lyrics537Test {
    final Application app = RuntimeEnvironment.getApplication();
    final List<JSONObject> sent = new ArrayList<>();
    AudioChunker.Splitter oldSplitter;SeekyAiClient.Transport oldTransport;TranscribeWork.Vaults oldVaults;

    @Before public void setup() {
        oldSplitter = AudioChunker.splitter; oldTransport = TranscribeWork.transport; oldVaults = TranscribeWork.vaults;
        Lyrics.setOnline(app, false);
        app.getSharedPreferences("seeky_ai", 0).edit().putBoolean("configured_gemini", true).putBoolean("terms_gemini", true).commit();
        TranscribeWork.vaults = c -> new SeekyAiVault(c) { @Override synchronized String get(String provider) { return "AIzaTesteTesteTesteTeste123"; } };
        // Two pieces: 6 minutes and 2 minutes.
        AudioChunker.splitter = (c, uri, dir, chunkMs, cancel) -> {
            List<AudioChunker.Piece> out = new ArrayList<>();
            long[][] parts = {{0, 360_000}, {360_000, 120_000}};
            for (int i = 0; i < parts.length; i++) { File f = new File(dir, "chunk-00" + i + ".aac"); Files.write(f.toPath(), new byte[]{(byte) 0xFF, (byte) 0xF1, 1, 2, 3, 4, 5, 6}); out.add(new AudioChunker.Piece(f, "audio/aac", parts[i][0], parts[i][1])); }
            return out;
        };
    }
    @After public void restore() { AudioChunker.splitter = oldSplitter; TranscribeWork.transport = oldTransport; TranscribeWork.vaults = oldVaults; }

    static String answer(String text) throws JSONException { return new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "STOP").put("content", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", text)))))).toString(); }
    String job(String uri) throws Exception { try (TaskStore db = new TaskStore(app)) { return db.create("transcribe", "Letra · Culto", new JSONObject().put("uri", uri).put("key", Lyrics.key(uri)).put("name", "Pregação de domingo.mp3").put("title", "Pregação de domingo").put("duration", 480_000).put("english", false)); } }

    @Test public void transcriptionSendsCompactAudioAndSavesSynchronisedLyrics() throws Exception {
        String uri = "content://seeky.none/pregacao.mp3";
        TranscribeWork.transport = (provider, key, payload, call) -> {
            sent.add(payload);
            JSONArray parts = payload.getJSONArray("contents").getJSONObject(0).getJSONArray("parts");
            assertEquals("audio/aac", parts.getJSONObject(0).getJSONObject("inline_data").getString("mime_type"));
            assertTrue(parts.getJSONObject(1).getString("text").contains("[mm:ss.cc]"));
            return sent.size() == 1 ? answer("[00:00.50] Irmãos, abram as Bíblias em João 3.\n[05:59.00] Porque Deus amou o mundo") : answer("```\n[00:02.00] Que deu o seu Filho unigénito.\n```");
        };
        try (WorkTestSupport w = new WorkTestSupport()) {
            String id = job(uri);
            w.run(id);
            assertEquals(2, sent.size());
            assertTrue(sent.get(1).toString().contains("6:00 a 8:00"));
            Lyrics.Result r = Lyrics.cached(app, uri);
            assertNotNull(r);
            assertEquals(TranscribeWork.SOURCE, r.source);
            assertEquals(3, r.synced.size());
            assertEquals(500, r.synced.get(0).time);
            assertEquals(359_000, r.synced.get(1).time);
            assertEquals(362_000, r.synced.get(2).time);
            assertEquals("Que deu o seu Filho unigénito.", r.synced.get(2).text);
            String[] s = TranscribeWork.status(app, uri);
            assertEquals(id, s[0]);
            assertTrue(s[2], s[2].contains("3 linhas"));
            assertFalse("partes de áudio apagadas", TranscribeWork.root(app, id).exists() && TranscribeWork.root(app, id).list().length > 0);
        }
    }
    @Test public void quotaLimitPausesKeepingThePartAlreadyWritten() throws Exception {
        String uri = "content://seeky.none/aula.mp3";
        TranscribeWork.transport = (provider, key, payload, call) -> { sent.add(payload); if (sent.size() == 1) return answer("[00:01.00] Primeira parte da aula"); throw new SeekyAiClient.HttpFailure(429, "quota"); };
        try (WorkTestSupport w = new WorkTestSupport()) {
            String id = job(uri);
            try { w.run(id); } catch (InterruptedIOException expected) {}
            try (TaskStore db = new TaskStore(app)) { assertEquals("paused", db.job(id).s("state")); assertTrue(db.job(id).s("message").contains("Quota")); assertEquals(1, db.count(id, "done")); assertEquals(1, db.count(id, "pending")); }
            Lyrics.Result r = Lyrics.cached(app, uri);
            assertEquals(TranscribeWork.PARTIAL, r.source);
            assertEquals("Primeira parte da aula", r.synced.get(0).text);
            TranscribeWork.transport = (provider, key, payload, call) -> { sent.add(payload); return answer("[00:03.00] Conclusão"); };
            w.run(id);
            assertEquals(3, sent.size());
            r = Lyrics.cached(app, uri);
            assertEquals(TranscribeWork.SOURCE, r.source);
            assertEquals(2, r.synced.size());
            assertEquals(363_000, r.synced.get(1).time);
        }
    }
    @Test public void protectedSongsAndBlockedAnswersAreExplained() throws Exception {
        assertEquals("RECITATION", TranscribeWork.reply(new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "RECITATION"))).toString()).reason);
        try { TranscribeWork.reply(new JSONObject().put("promptFeedback", new JSONObject().put("blockReason", "OTHER")).toString()); fail(); } catch (JSONException expected) {}
        String uri = "content://seeky.none/hit.mp3";
        TranscribeWork.transport = (provider, key, payload, call) -> new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "RECITATION"))).toString();
        try (WorkTestSupport w = new WorkTestSupport()) {
            String id = job(uri);
            w.run(id);
            assertNull(Lyrics.cached(app, uri));
            assertTrue(TranscribeWork.status(app, uri)[2].contains("direitos de autor"));
        }
    }
    @Test public void automaticModeNeedsConsentConnectionAndWaitsADayBeforeRetrying() throws Exception {
        String uri = "content://seeky.none/x.mp3";
        TranscribeWork.setMode(app, "ask");
        assertFalse(TranscribeWork.automatic(app, uri));
        TranscribeWork.setMode(app, "auto");
        assertTrue(TranscribeWork.automatic(app, uri));
        TranscribeWork.prefs(app).edit().putLong("tried_" + Lyrics.key(uri), System.currentTimeMillis()).commit();
        assertFalse("tentado hoje", TranscribeWork.automatic(app, uri));
        TranscribeWork.prefs(app).edit().remove("tried_" + Lyrics.key(uri)).commit();
        app.getSharedPreferences("seeky_ai", 0).edit().clear().commit();
        assertFalse("sem ligação à IA", TranscribeWork.automatic(app, uri));
        TranscribeWork.setMode(app, "ask");
    }

    MediaActivity player(String name) {
        ActivityController<MediaActivity> c = Robolectric.buildActivity(MediaActivity.class, new Intent(app, MediaActivity.class).setDataAndType(Uri.parse("content://seeky.none/" + name), "audio/mpeg").putExtra("name", name));
        MediaActivity a = c.setup().get();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        return a;
    }
    void capture(View v, String name) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 360, 800);
        Bitmap b = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888); v.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(System.getProperty("seeky.screenshot") + "-5370-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
    @Test public void playerOffersTranscriptionAndShowsItsProgress() throws Exception {
        MediaActivity a = player("Pregação - Pr. Mateus - A fé.mp3");
        a.showPage(MediaActivity.LYRICS);
        a.speechTrack = true; a.transcription = null;
        a.showLyricsResult(new Lyrics.Result());
        String labels = BooksToolsTest.labels(a.lyricsPanel);
        for (String s : new String[]{"Este áudio parece uma pregação, aula ou conversa.", "Transcrever com IA", "Procurar no Google e copiar", "Colar letra copiada"}) assertTrue(s + " em " + labels, labels.contains(s));
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        capture(root, "letra-transcrever");
        a.transcription = new String[]{"id", "running", "A transcrever 0:00–6:00 · parte 1 de 3", "0", "3"};
        a.showLyricsResult(new Lyrics.Result());
        labels = BooksToolsTest.labels(a.lyricsPanel);
        assertTrue(labels, labels.contains("A escrever a letra com IA…") && labels.contains("parte 1 de 3") && labels.contains("Ver tarefas em segundo plano"));
        a.transcription = new String[]{"id", "running", "A transcrever 6:00–12:00 · parte 2 de 3", "1", "3"};
        a.showLyricsResult(Lyrics.result("[00:00.50]Irmãos, abram as Bíblias em João 3.\n[00:04.00]Vamos ler juntos.\n[05:59.00]Porque Deus amou o mundo", TranscribeWork.PARTIAL));
        labels = BooksToolsTest.labels(a.lyricsPanel);
        assertTrue(labels, labels.contains("Irmãos, abram as Bíblias em João 3.") && labels.contains("A letra continua a chegar"));
        capture(root, "letra-transcrita");
        // Without the Gemini connection, the button explains how to connect.
        app.getSharedPreferences("seeky_ai", 0).edit().clear().commit();
        a.transcribe();
        shadowOf(Looper.getMainLooper()).idle();
        UiDialog d = (UiDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(BooksToolsTest.labels(d.root).contains("Ligar a IA"));
        d.dismiss();
    }
}
