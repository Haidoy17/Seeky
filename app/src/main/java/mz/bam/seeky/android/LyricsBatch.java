package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.util.List;
import org.json.*;

/**
 * Lyrics for the whole audio library in the background: saved, .lrc beside the file, embedded and
 * online lyrics first (a short pause between lookups); then, if chosen, the tracks still without
 * lyrics are queued for transcription by the AI, at most {@link #LIMIT} per run to protect the quota.
 */
final class LyricsBatch {
    static final int LIMIT = 25;
    static void enqueue(Activity a, boolean transcribe) {
        PlusUi.run(a, () -> {
            String id;
            try (TaskStore db = new TaskStore(a)) { id = db.create("lyrics", transcribe ? "Letras para todas as músicas (com transcrição)" : "Letras para todas as músicas", new JSONObject().put("transcribe", transcribe).put("next", 0).put("found", 0).put("had", 0).put("missing", new JSONArray())); }
            final String job = id;
            TaskService.launch(a, job);
        });
    }
    static long pause = 300;

    static void run(Context c, StudyStore.Row job, TaskStore work, TaskService.Control control) throws Exception {
        String id = job.s("id");
        JSONObject data = new JSONObject(job.s("payload"));
        List<MediaLibrary.Track> tracks;
        try (MediaLibrary db = new MediaLibrary(c)) { tracks = db.tracks(MediaLibrary.AUDIO, null, null, "title COLLATE NOCASE", 100000); }
        int next = data.optInt("next"), found = data.optInt("found"), had = data.optInt("had");
        JSONArray missing = data.optJSONArray("missing");
        if (missing == null) missing = new JSONArray();
        boolean online = Lyrics.online(c);
        for (int i = next; i < tracks.size(); i++) {
            control.check();
            MediaLibrary.Track t = tracks.get(i);
            Uri uri = Uri.parse(t.uri);
            String file = t.path.isEmpty() ? t.title : new File(t.path).getName();
            boolean before = Lyrics.cached(c, t.uri) != null;
            boolean searched = !before && online && !Lyrics.missing(c, t.uri);
            Lyrics.Result r = Lyrics.find(c, uri, t.title, t.artist, t.album, t.duration, online, file);
            if (before) had++; else if (r.found()) found++; else if (missing.length() < 5000) missing.put(new JSONArray().put(t.uri).put(file).put(t.title).put(t.duration));
            if (i % 10 == 9 || i == tracks.size() - 1) { data.put("next", i + 1).put("found", found).put("had", had).put("missing", missing); work.payload(id, data); }
            control.progress("Letras · " + (i + 1) + " / " + tracks.size() + " · " + found + " encontradas · " + missing.length() + " sem letra", i + 1, tracks.size());
            if (searched && pause > 0) Thread.sleep(pause);
        }
        int queued = 0;
        if (data.optBoolean("transcribe") && TranscribeWork.ready(c)) {
            for (int i = 0; i < missing.length() && queued < LIMIT; i++) {
                control.check();
                JSONArray m = missing.getJSONArray(i);
                String[] s = TranscribeWork.status(c, m.getString(0));
                if (s != null && !s[1].equals("cancelled") && !s[1].equals("failed")) continue;
                TranscribeWork.enqueue(c, Uri.parse(m.getString(0)), m.getString(1), m.getString(2), m.getLong(3));
                queued++;
            }
        }
        String message = tracks.size() + " músicas · " + (had + found) + " com letra (" + found + " encontradas agora) · " + missing.length() + " sem letra"
                + (queued > 0 ? " · " + queued + " enviadas para transcrição" : data.optBoolean("transcribe") && !TranscribeWork.ready(c) ? " · ligue a IA (Gemini) para as transcrever" : "") + ".";
        work.update(id, StudyStore.values("message", message));
        control.progress(message, tracks.size(), tracks.size());
    }
    private LyricsBatch() {}
}
