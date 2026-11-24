package mz.bam.seeky.android;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * O que o telemóvel e o computador trocam no Seeky ligado e como se junta: notas (a versão mais recente
 * de cada uma; as apagadas ficam apagadas nos dois), onde ficou a leitura de cada livro (o mesmo
 * ficheiro: nome e tamanho), capítulos lidos, versículos para decorar, recordes do jogo, músicas
 * favoritas e audições. Cada lado lê o seu estado, junta o do outro e aplica só as diferenças.
 */
final class SyncState {
    static final int VERSION = 1;
    static final class Note { String uuid = "", title = "", body = "", folder = "", source = ""; boolean pinned; int color, spacing; long created, updated; }
    static final class Progress { String name = "", kind = ""; long bytes, at; int position, total;
        String key() { return key(name, bytes); }
        static String key(String name, long bytes) { return Rules.normalize(name == null ? "" : name).trim() + "|" + bytes; } }
    static final class Card { String reference = "", text = ""; int interval, ease = 250; long due; }
    static final class Record { String name = ""; int level, points, correct; long at; String key() { return name + "|" + level + "|" + points + "|" + at; } }
    static final class Play { String song = ""; long at; }

    String device = "", name = ""; long since;
    final List<Note> notes = new ArrayList<>();
    final Map<String, Long> deleted = new LinkedHashMap<>();
    final Map<String, byte[]> media = new LinkedHashMap<>();
    final List<Progress> progress = new ArrayList<>();
    final Set<String> readings = new LinkedHashSet<>(); // "day|book|chapter"
    final List<Card> cards = new ArrayList<>();
    final List<Record> records = new ArrayList<>();
    final List<Play> plays = new ArrayList<>();
    final Set<String> favorites = new LinkedHashSet<>();

    /** The same song on both sides: artist and title without accents or case. */
    static String song(String artist, String title) { return Rules.normalize((artist == null ? "" : artist) + "|" + (title == null ? "" : title)).replaceAll("\\s+", " ").trim(); }
    static String reading(String day, String book, int chapter) { return day + "|" + book + "|" + chapter; }

    // ---- JSON ----
    JSONObject json() throws Exception {
        JSONObject o = new JSONObject().put("v", VERSION).put("device", device).put("name", name).put("since", since);
        JSONArray n = new JSONArray(); for (Note x : notes) n.put(new JSONObject().put("uuid", x.uuid).put("title", x.title).put("body", x.body).put("folder", x.folder).put("source", x.source).put("pinned", x.pinned).put("color", x.color).put("spacing", x.spacing).put("created", x.created).put("updated", x.updated)); o.put("notes", n);
        JSONObject d = new JSONObject(); for (Map.Entry<String, Long> e : deleted.entrySet()) d.put(e.getKey(), e.getValue()); o.put("deleted", d);
        JSONArray p = new JSONArray(); for (Progress x : progress) p.put(new JSONObject().put("name", x.name).put("bytes", x.bytes).put("kind", x.kind).put("position", x.position).put("total", x.total).put("at", x.at)); o.put("progress", p);
        o.put("readings", new JSONArray(readings));
        JSONArray c = new JSONArray(); for (Card x : cards) c.put(new JSONObject().put("reference", x.reference).put("text", x.text).put("interval", x.interval).put("ease", x.ease).put("due", x.due)); o.put("cards", c);
        JSONArray r = new JSONArray(); for (Record x : records) r.put(new JSONObject().put("name", x.name).put("level", x.level).put("points", x.points).put("correct", x.correct).put("at", x.at)); o.put("records", r);
        JSONArray pl = new JSONArray(); for (Play x : plays) pl.put(new JSONObject().put("song", x.song).put("at", x.at)); o.put("plays", pl);
        o.put("favorites", new JSONArray(favorites));
        return o;
    }
    static SyncState parse(String text) throws Exception {
        JsonBudget.check(text, 8, 400000);
        JSONObject o = new JSONObject(text); if (o.optInt("v", 0) != VERSION) throw new Exception("Versões diferentes do Seeky: actualize o telemóvel e o computador.");
        SyncState s = new SyncState(); s.device = o.optString("device", ""); s.name = o.optString("name", ""); s.since = o.optLong("since", 0);
        long now = System.currentTimeMillis() + 86400000L;
        JSONArray n = o.optJSONArray("notes"); if (n != null) for (int i = 0; i < n.length(); i++) { JSONObject x = n.getJSONObject(i); Note t = new Note(); t.uuid = x.optString("uuid", ""); if (!t.uuid.matches("[a-f0-9-]{8,64}")) continue; t.title = x.optString("title", ""); t.body = x.optString("body", ""); t.folder = x.optString("folder", ""); t.source = x.optString("source", ""); t.pinned = x.optBoolean("pinned"); t.color = x.optInt("color"); t.spacing = Math.max(0, Math.min(300, x.optInt("spacing"))); t.created = Math.min(now, x.optLong("created")); t.updated = Math.min(now, x.optLong("updated")); s.notes.add(t); }
        JSONObject d = o.optJSONObject("deleted"); if (d != null) for (Iterator<String> it = d.keys(); it.hasNext(); ) { String k = it.next(); if (k.matches("[a-f0-9-]{8,64}")) s.deleted.put(k, Math.min(now, d.optLong(k))); }
        JSONArray p = o.optJSONArray("progress"); if (p != null) for (int i = 0; i < p.length(); i++) { JSONObject x = p.getJSONObject(i); Progress t = new Progress(); t.name = x.optString("name"); t.bytes = x.optLong("bytes"); t.kind = x.optString("kind"); t.position = Math.max(0, x.optInt("position")); t.total = Math.max(0, x.optInt("total")); t.at = Math.min(now, x.optLong("at")); if (!t.name.isEmpty() && t.bytes > 0) s.progress.add(t); }
        JSONArray r = o.optJSONArray("readings"); if (r != null) for (int i = 0; i < r.length(); i++) { String v = r.optString(i, ""); if (v.matches("\\d{4}-\\d{2}-\\d{2}\\|[^|]{1,60}\\|\\d{1,3}")) s.readings.add(v); }
        JSONArray c = o.optJSONArray("cards"); if (c != null) for (int i = 0; i < c.length(); i++) { JSONObject x = c.getJSONObject(i); Card t = new Card(); t.reference = x.optString("reference"); t.text = x.optString("text"); t.interval = Math.max(0, x.optInt("interval")); t.ease = Math.max(130, Math.min(400, x.optInt("ease", 250))); t.due = x.optLong("due"); if (!t.reference.isEmpty() && !t.text.isEmpty()) s.cards.add(t); }
        JSONArray rc = o.optJSONArray("records"); if (rc != null) for (int i = 0; i < rc.length(); i++) { JSONObject x = rc.getJSONObject(i); Record t = new Record(); t.name = x.optString("name"); t.level = x.optInt("level"); t.points = Math.max(0, x.optInt("points")); t.correct = Math.max(0, x.optInt("correct")); t.at = x.optLong("at"); if (!t.name.isEmpty()) s.records.add(t); }
        JSONArray pl = o.optJSONArray("plays"); if (pl != null) for (int i = 0; i < pl.length(); i++) { JSONObject x = pl.getJSONObject(i); Play t = new Play(); t.song = x.optString("song"); t.at = Math.min(now, x.optLong("at")); if (!t.song.isEmpty() && t.at > 0) s.plays.add(t); }
        JSONArray f = o.optJSONArray("favorites"); if (f != null) for (int i = 0; i < f.length(); i++) if (!f.optString(i, "").isEmpty()) s.favorites.add(f.optString(i));
        return s;
    }

    // ---- juntar ----
    /** What this side must change to have both sides together. */
    static final class Changes {
        final List<Note> upsert = new ArrayList<>(); final List<String> delete = new ArrayList<>();
        final Map<String, String> relink = new LinkedHashMap<>(); // local uuid → remote uuid (the same note sent before as a file)
        final Map<String, Long> tombs = new LinkedHashMap<>();
        final List<Progress> progress = new ArrayList<>(); final List<String[]> readings = new ArrayList<>();
        final List<Card> cards = new ArrayList<>(); final List<Record> records = new ArrayList<>();
        final List<Play> plays = new ArrayList<>(); final Set<String> favorites = new LinkedHashSet<>();
        boolean empty() { return upsert.isEmpty() && delete.isEmpty() && relink.isEmpty() && progress.isEmpty() && readings.isEmpty() && cards.isEmpty() && records.isEmpty() && plays.isEmpty() && favorites.isEmpty(); }
        int notes() { return upsert.size() + delete.size(); }
    }
    static Changes merge(SyncState local, SyncState remote) {
        Changes ch = new Changes();
        Map<String, Note> mine = new HashMap<>(); for (Note n : local.notes) mine.put(n.uuid, n);
        Set<String> theirIds = new HashSet<>(); for (Note n : remote.notes) theirIds.add(n.uuid);
        Map<String, Note> byContent = new HashMap<>(); for (Note n : local.notes) if (!theirIds.contains(n.uuid)) byContent.putIfAbsent(NotesTransfer.key(n.title, n.body), n);
        for (Note r : remote.notes) {
            Note l = mine.get(r.uuid);
            if (l == null) {
                Long gone = local.deleted.get(r.uuid); if (gone != null && gone >= r.updated) continue;
                Note twin = byContent.remove(NotesTransfer.key(r.title, r.body));
                if (twin != null) { ch.relink.put(twin.uuid, r.uuid); if (r.updated > twin.updated) ch.upsert.add(r); continue; }
                ch.upsert.add(r);
            } else if (r.updated > l.updated) ch.upsert.add(r);
        }
        for (Map.Entry<String, Long> t : remote.deleted.entrySet()) {
            Note l = mine.get(t.getKey()); if (l != null && l.updated <= t.getValue()) ch.delete.add(t.getKey());
            Long had = local.deleted.get(t.getKey()); if (had == null || had < t.getValue()) ch.tombs.put(t.getKey(), t.getValue());
        }
        Map<String, Progress> books = new HashMap<>(); for (Progress p : local.progress) books.put(p.key(), p);
        for (Progress r : remote.progress) { Progress l = books.get(r.key()); if (l != null && r.at > l.at && (r.position != l.position || r.total != l.total)) ch.progress.add(r); }
        for (String r : remote.readings) if (!local.readings.contains(r)) { String[] p = r.split("\\|"); ch.readings.add(new String[]{p[0], p[1], p[2]}); }
        Map<String, Card> cards = new HashMap<>(); for (Card c : local.cards) cards.put(Rules.normalize(c.reference), c);
        for (Card r : remote.cards) { Card l = cards.get(Rules.normalize(r.reference)); if (l == null || r.interval > l.interval || (r.interval == l.interval && r.due > l.due)) ch.cards.add(r); }
        Set<String> recs = new HashSet<>(); for (Record x : local.records) recs.add(x.key()); for (Record r : remote.records) if (recs.add(r.key())) ch.records.add(r);
        Set<String> heard = new HashSet<>(); for (Play x : local.plays) heard.add(x.song + "|" + x.at); for (Play r : remote.plays) if (heard.add(r.song + "|" + r.at)) ch.plays.add(r);
        for (String f : remote.favorites) if (!local.favorites.contains(f)) ch.favorites.add(f);
        return ch;
    }
    /** Pictures and recordings of the notes changed after "since" (so each one travels once). */
    static void attach(SyncState s, long since, NotesTransfer.Media read) {
        long total = 0;
        for (Note n : s.notes) if (n.updated > since) for (String name : NotesTransfer.media(n.body)) {
            if (s.media.containsKey(name)) continue; byte[] b = read.read(name); if (b == null) continue;
            if (total + b.length > NotesTransfer.MAX_MEDIA) return; total += b.length; s.media.put(name, b);
        }
    }
    /** The pictures go after the state, one sealed message each (a note's picture is never cut). */
    void sendMedia(SyncWire.Channel ch) throws Exception {
        ch.send(new JSONObject().put("media", media.size()).toString());
        for (Map.Entry<String, byte[]> e : media.entrySet()) { ch.send(new JSONObject().put("name", e.getKey()).toString()); ch.send(e.getValue()); }
    }
    void receiveMedia(SyncWire.Channel ch) throws Exception {
        int n = new JSONObject(ch.text()).optInt("media", 0); long total = 0;
        for (int i = 0; i < Math.min(n, 5000); i++) {
            String name = new JSONObject(ch.text()).optString("name", ""); byte[] b = ch.receive();
            if (NotesTransfer.NAME.matcher(name).matches() && !name.startsWith(".") && (total += b.length) <= NotesTransfer.MAX_MEDIA) media.put(name, b);
        }
    }
    static String uuid() { return UUID.randomUUID().toString(); }
}
