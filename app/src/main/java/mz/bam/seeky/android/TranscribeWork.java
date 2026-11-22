package mz.bam.seeky.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/**
 * Lyrics for everything the audio player plays: when no lyrics exist (on the phone or online), the
 * track is transcribed by the Gemini connection the owner set up. The audio is cut into short mono
 * pieces, each piece is sent once with an instruction to write what is said or sung with times, and
 * the timed lines are saved as synchronised lyrics as soon as each piece arrives. Runs as a resumable
 * background task; quota limits pause it.
 */
final class TranscribeWork {
    static final String SOURCE = "Transcrição automática (Gemini)", PARTIAL = SOURCE + " · em curso";
    static final long CHUNK_MS = 6 * 60 * 1000L, RETRY_MS = 24 * 3600 * 1000L;
    static final int MAX_PIECE = 12 * 1024 * 1024;
    interface Vaults { SeekyAiVault create(Context c); }
    static Vaults vaults = SeekyAiVault::new;
    static SeekyAiClient.Transport transport = new SeekyAiClient.Network();

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("lyrics", 0); }
    /** "auto", "ask" (default) or "off". */
    static String mode(Context c) { String m = prefs(c).getString("transcribe", "ask"); return m.equals("auto") || m.equals("off") ? m : "ask"; }
    static void setMode(Context c, String mode) { prefs(c).edit().putString("transcribe", mode).apply(); }
    static boolean wifiOnly(Context c) { return prefs(c).getBoolean("transcribe_wifi", false); }
    static void setWifiOnly(Context c, boolean on) { prefs(c).edit().putBoolean("transcribe_wifi", on).apply(); }
    static boolean ready(Context c) { return SeekyAiSettings.allowed(c, SeekyAiCore.GEMINI); }
    static boolean network(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) return false;
            return !wifiOnly(c) || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } catch (RuntimeException e) { return true; }
    }

    /** The newest transcription task for this track: {id, state, message, progress, total}, or null. */
    static String[] status(Context c, String uri) {
        try (TaskStore db = new TaskStore(c); Cursor cur = db.getReadableDatabase().rawQuery("SELECT id,state,message,progress,total FROM jobs WHERE type='transcribe' AND instr(payload,?)>0 ORDER BY created DESC LIMIT 1", new String[]{"\"key\":\"" + Lyrics.key(uri) + "\""})) {
            if (!cur.moveToFirst()) return null;
            return new String[]{cur.getString(0), cur.getString(1), cur.getString(2), "" + cur.getLong(3), "" + cur.getLong(4)};
        } catch (RuntimeException e) { return null; }
    }
    static boolean busy(String[] s) { return s != null && Arrays.asList("queued", "running", "pausing").contains(s[1]); }
    /** Automatic start: chosen "automatic", connection ready, right network, nothing running and not tried in the last day. */
    static boolean automatic(Context c, String uri) {
        if (!mode(c).equals("auto") || !ready(c) || !network(c)) return false;
        String[] s = status(c, uri);
        if (busy(s) || s != null && s[1].equals("paused")) return false;
        return System.currentTimeMillis() - prefs(c).getLong("tried_" + Lyrics.key(uri), 0) > RETRY_MS;
    }
    /** Queues (or resumes) the transcription of one track; returns the task id. */
    static String enqueue(Context c, Uri uri, String name, String title, long durationMs) throws Exception {
        String[] s = status(c, uri.toString());
        String id;
        prefs(c).edit().putLong("tried_" + Lyrics.key(uri.toString()), System.currentTimeMillis()).apply();
        if (s != null && !s[1].equals("complete") && !s[1].equals("cancelled")) id = s[0];
        else try (TaskStore db = new TaskStore(c)) {
            String label = title == null || title.trim().isEmpty() ? name : title;
            id = db.create("transcribe", "Letra · " + AudioTitles.limit(label == null ? "Áudio" : label, 120, 360), new JSONObject().put("uri", uri.toString()).put("key", Lyrics.key(uri.toString())).put("name", name == null ? "" : name).put("title", title == null ? "" : title).put("duration", durationMs).put("english", AppLanguage.english(c)));
        }
        TaskService.start(c.getApplicationContext(), id);
        return id;
    }

    static File root(Context c, String id) { if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException(); File dir = new File(new File(c.getFilesDir(), "work-content"), id); dir.mkdirs(); return dir; }

    static void run(Context c, StudyStore.Row job, TaskStore work, TaskService.Control control) throws Exception {
        if (!ready(c)) throw new IOException("Ligue primeiro a IA (Gemini) em Estudar com Seeky → Ligação da IA.");
        String id = job.s("id");
        JSONObject data = new JSONObject(job.s("payload"));
        Uri uri = Uri.parse(data.getString("uri"));
        String key = vaults.create(c).get(SeekyAiCore.GEMINI);
        if (key.isEmpty()) throw new IOException("Configure a chave pessoal do Gemini.");
        File dir = root(c, id);
        if (!data.optBoolean("prepared")) {
            control.progress("A preparar o áudio para transcrever…", 0, 0);
            AudioChunker.clear(dir);
            work.getWritableDatabase().delete("items", "job=?", new String[]{id});
            List<AudioChunker.Piece> pieces = AudioChunker.splitter.split(c, uri, dir, CHUNK_MS, control::cancelled);
            android.database.sqlite.SQLiteDatabase sql = work.getWritableDatabase();
            sql.beginTransaction();
            try {
                for (int i = 0; i < pieces.size(); i++) { AudioChunker.Piece p = pieces.get(i); work.add(id, i, uri.toString(), p.file.getName(), p.durationMs, p.startMs); work.item(id, i, StudyStore.values("target", p.file.getPath(), "mime", p.mime)); }
                data.put("prepared", true);
                work.payload(id, data);
                work.totals(id);
                sql.setTransactionSuccessful();
            } finally { sql.endTransaction(); }
        }
        long total = work.count(id, null);
        boolean english = data.optBoolean("english");
        String title = data.optString("title").isEmpty() ? LyricsText.clean(data.optString("name")) : data.optString("title");
        for (;;) {
            control.check();
            List<StudyStore.Row> pending = work.items(id, "pending", 0, 1);
            if (pending.isEmpty()) break;
            StudyStore.Row item = pending.get(0);
            int seq = (int) item.n("seq");
            long start = item.n("modified"), length = item.n("bytes");
            File piece = new File(item.s("target"));
            if (!piece.isFile()) { data.put("prepared", false); work.payload(id, data); throw new IOException("O áudio preparado desapareceu. Retome para preparar de novo."); }
            control.progress("A transcrever " + LyricsText.clock(start) + "–" + LyricsText.clock(start + length) + " · parte " + (seq + 1) + " de " + total, work.count(id, "done"), total);
            SeekyAiClient.Call call = new SeekyAiClient.Call();
            control.interrupt = call::cancel;
            try {
                LyricsText.Reply reply = ask(key, piece, item.s("mime"), LyricsText.prompt(title, start, start + length, english), call);
                List<LyricsText.Timed> lines = LyricsText.parse(reply.text, length);
                String note = reply.reason.equals("RECITATION") ? "recitation" : reply.reason.equals("MAX_TOKENS") ? "partial" : "";
                work.item(id, seq, StudyStore.values("state", "done", "message", LyricsText.lrc(lines), "name", item.s("name") + (note.isEmpty() ? "" : " · " + note)));
                piece.delete();
            } catch (InterruptedIOException e) { throw e; }
            catch (SeekyAiClient.HttpFailure e) {
                if (e.status == 429) { publish(c, id, work, data, false); work.state(id, "paused", "Quota do Gemini atingida. A parte já transcrita ficou guardada; retome mais tarde."); control.stopped = true; throw new InterruptedIOException(); }
                throw e;
            } finally { control.interrupt = null; }
            publish(c, id, work, data, false);
        }
        int lines = publish(c, id, work, data, true);
        AudioChunker.clear(dir);
        dir.delete();
        long blocked = 0;
        for (StudyStore.Row r : work.items(id, "done", 0, 1000)) if (r.s("name").endsWith("· recitation")) blocked++;
        String message = lines == 0 ? (blocked > 0 ? "O Gemini não transcreve esta música (direitos de autor). Procure a letra online ou cole-a." : "Não foi encontrada voz para transcrever.") : lines + " linhas de letra guardadas" + (blocked > 0 ? " · algumas partes de música protegida ficaram sem texto" : "") + ".";
        work.update(id, StudyStore.values("message", message));
        control.progress(message, total, total);
    }

    /** Joins the transcribed pieces and saves them as the track's synchronised lyrics. Returns the number of lines. */
    static int publish(Context c, String id, TaskStore work, JSONObject data, boolean finished) throws Exception {
        List<StudyStore.Row> rows = new ArrayList<>();
        for (int page = 0; ; page += 200) { List<StudyStore.Row> part = work.items(id, null, page, 200); rows.addAll(part); if (part.size() < 200) break; }
        rows.sort((x, y) -> Long.compare(x.n("seq"), y.n("seq")));
        List<List<LyricsText.Timed>> chunks = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        for (StudyStore.Row r : rows) { if (!r.s("state").equals("done")) { if (!finished) break; continue; } chunks.add(LyricsText.parse(r.s("message"), 0)); starts.add(r.n("modified")); }
        List<LyricsText.Timed> all = LyricsText.merge(chunks, starts);
        boolean voice = false;
        for (LyricsText.Timed t : all) if (!t.text.equals("♪")) { voice = true; break; }
        String uri = data.getString("uri");
        if (voice) { Lyrics.store(c, uri, LyricsText.lrc(all), finished ? SOURCE : PARTIAL); TaskEvents.changed(); }
        return voice ? all.size() : 0;
    }

    static LyricsText.Reply ask(String key, File piece, String mime, String prompt, SeekyAiClient.Call call) throws Exception {
        if (piece.length() > MAX_PIECE) throw new IOException("Parte de áudio demasiado grande.");
        byte[] audio = Files.readAllBytes(piece.toPath());
        JSONObject payload = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray()
                        .put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", mime.isEmpty() ? "audio/aac" : mime).put("data", android.util.Base64.encodeToString(audio, android.util.Base64.NO_WRAP))))
                        .put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", new JSONObject().put("temperature", 0).put("maxOutputTokens", 8192));
        String raw = transport.post(SeekyAiCore.GEMINI, key, payload, call);
        call.check();
        try { return reply(raw); }
        catch (JSONException e) { throw new IOException(e.getMessage() != null && e.getMessage().startsWith("blocked") ? "O Gemini recusou este áudio." : "Resposta do Gemini inválida."); }
    }
    /** Text and finish reason of a Gemini answer; a blocked prompt is an error. */
    static LyricsText.Reply reply(String raw) throws JSONException {
        JSONObject data = new JSONObject(raw);
        JSONObject feedback = data.optJSONObject("promptFeedback");
        if (feedback != null && !feedback.optString("blockReason", "").isEmpty()) throw new JSONException("blocked:" + feedback.optString("blockReason"));
        JSONArray candidates = data.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return new LyricsText.Reply("", "EMPTY");
        JSONObject first = candidates.getJSONObject(0);
        StringBuilder text = new StringBuilder();
        JSONObject content = first.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts != null) for (int i = 0; i < parts.length(); i++) { JSONObject p = parts.optJSONObject(i); if (p != null && !p.optBoolean("thought")) text.append(p.optString("text", "")); }
        return new LyricsText.Reply(text.toString(), first.optString("finishReason", ""));
    }
    private TranscribeWork() {}
}
