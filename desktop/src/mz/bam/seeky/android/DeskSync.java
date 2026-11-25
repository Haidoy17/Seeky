package mz.bam.seeky.android;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.net.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Seeky ligado no computador: emparelha telemóveis com um código de 6 dígitos, junta notas, leitura,
 * estudo bíblico e música com o telemóvel, recebe ficheiros (Transferências\Seeky) e guarda os que vão
 * para o telemóvel até à próxima sincronização.
 */
final class DeskSync implements SyncServer.Host {
    final DeskMain d; final DeskStore s;
    SyncServer server; String code; long codeUntil; String lastError = "";
    final Map<String, byte[]> tokens = new HashMap<>();
    DeskSync(DeskMain d, DeskStore s) { this.d = d; this.s = s; }
    void ui(Runnable r) { if (d != null) Platform.runLater(r); }

    // ================================================================ servidor
    boolean enabled() { return "1".equals(s.get("sync_on", "0")); }
    void start() {
        if (server != null) return;
        try { SyncServer sv = new SyncServer(this, SyncWire.TCP_PORT, SyncWire.UDP_PORT); sv.start(); server = sv; s.put("sync_on", "1"); lastError = ""; }
        catch (Exception e) { lastError = "A porta " + SyncWire.TCP_PORT + " está ocupada: feche outro Seeky aberto e tente outra vez."; }
    }
    void stop() { if (server != null) server.close(); server = null; }
    @Override public String id() { String id = s.get("sync_id", ""); if (id.isEmpty()) { id = SyncState.uuid(); s.put("sync_id", id); } return id; }
    @Override public String name() { String n = System.getenv("COMPUTERNAME"); if (n == null || n.isEmpty()) try { n = InetAddress.getLocalHost().getHostName(); } catch (Exception e) { n = "Computador"; } return n; }
    /** A new 6-digit code, valid for 10 minutes (shown big on the "Telemóvel" page). */
    synchronized String newCode() { code = String.format(Locale.ROOT, "%06d", SyncWire.RANDOM.nextInt(1000000)); codeUntil = System.currentTimeMillis() + 600000; if (server != null) synchronized (server) { server.failures = 0; } return code; }
    @Override public synchronized String code() { return code != null && System.currentTimeMillis() < codeUntil ? code : null; }
    @Override public synchronized void codeUsed() { code = null; ui(() -> { if (d.current.equals("phone")) d.open("phone"); }); }
    @Override public void codeFailed() { ui(() -> d.status("Um telemóvel tentou um código errado.")); }

    JSONArray devices() { try { return new JSONArray(s.get("sync_devices", "[]")); } catch (Exception e) { return new JSONArray(); } }
    @Override public synchronized byte[] token(String device) {
        byte[] t = tokens.get(device); if (t != null) return t;
        JSONArray all = devices();
        for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null && device.equals(o.optString("id"))) try { t = SyncWire.unb64(DeskSecret.reveal(o.optString("token"))); tokens.put(device, t); return t; } catch (Exception e) { return null; } }
        return null;
    }
    @Override public synchronized void paired(String device, String deviceName, byte[] token) {
        JSONArray all = devices(), out = new JSONArray();
        for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null && !device.equals(o.optString("id"))) out.put(o); }
        try { out.put(new JSONObject().put("id", device).put("name", deviceName).put("token", DeskSecret.protect(SyncWire.b64(token))).put("paired", System.currentTimeMillis()).put("last", 0)); } catch (Exception e) { throw new RuntimeException(e); }
        s.put("sync_devices", out.toString()); tokens.put(device, token);
        ui(() -> d.status(deviceName + " ficou ligado a este computador."));
    }
    void forget(String device) { JSONArray all = devices(), out = new JSONArray(); for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null && !device.equals(o.optString("id"))) out.put(o); } s.put("sync_devices", out.toString()); synchronized (this) { tokens.remove(device); } }
    long lastSync(String device) { JSONArray all = devices(); for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null && device.equals(o.optString("id"))) return o.optLong("last"); } return 0; }
    @Override public void synced(String device, String deviceName, SyncState.Changes ch) {
        JSONArray all = devices();
        for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null && device.equals(o.optString("id"))) try { o.put("last", System.currentTimeMillis()).put("name", deviceName); } catch (Exception ignored) {} }
        s.put("sync_devices", all.toString());
        ui(() -> { d.status(deviceName + " sincronizado" + (ch.empty() ? "." : ": " + ch.notes() + (ch.notes() == 1 ? " nota" : " notas") + " e " + (ch.progress.size() + ch.readings.size() + ch.cards.size() + ch.records.size() + ch.plays.size() + ch.favorites.size()) + " outras mudanças.")); if (!ch.empty() && !d.current.equals("notes")) d.open(d.current); });
    }

    // ================================================================ estado deste computador
    static void schema(DeskStore s) {
        try (java.sql.Statement st = s.db.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS sync_ids(kind TEXT NOT NULL,local INTEGER NOT NULL,uuid TEXT NOT NULL,PRIMARY KEY(kind,local),UNIQUE(kind,uuid))");
            st.execute("CREATE TABLE IF NOT EXISTS sync_deleted(kind TEXT NOT NULL,uuid TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(kind,uuid))");
            st.execute("CREATE TABLE IF NOT EXISTS sync_outbox(id TEXT PRIMARY KEY,path TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,added INTEGER NOT NULL)");
            for (String c : new String[]{"color INTEGER NOT NULL DEFAULT 0", "spacing INTEGER NOT NULL DEFAULT 0"}) try { st.execute("ALTER TABLE notes ADD COLUMN " + c); } catch (SQLException exists) {}
        } catch (SQLException ignored) {}
        DeskStudies.schema(s);
    }
    /** Remembers a deleted note so the phone deletes it too. */
    static void deleted(DeskStore s, long note) {
        try { List<Object[]> r = s.rows("SELECT uuid FROM sync_ids WHERE kind='note' AND local=?", note); if (!r.isEmpty()) { s.update("INSERT OR REPLACE INTO sync_deleted(kind,uuid,at) VALUES('note',?,?)", r.get(0)[0], System.currentTimeMillis()); s.update("DELETE FROM sync_ids WHERE kind='note' AND local=?", note); } } catch (SQLException ignored) {}
    }
    static String uuidOf(DeskStore s, long note) throws SQLException {
        List<Object[]> r = s.rows("SELECT uuid FROM sync_ids WHERE kind='note' AND local=?", note); if (!r.isEmpty()) return r.get(0)[0] + "";
        String u = SyncState.uuid(); s.update("INSERT INTO sync_ids(kind,local,uuid) VALUES('note',?,?)", note, u); return u;
    }
    static long localOf(DeskStore s, String uuid) throws SQLException { List<Object[]> r = s.rows("SELECT local FROM sync_ids WHERE kind='note' AND uuid=?", uuid); return r.isEmpty() ? 0 : ((Number) r.get(0)[0]).longValue(); }

    static SyncState export(DeskStore s, long since) throws SQLException {
        schema(s); SyncState st = new SyncState();
        for (Object[] r : s.rows("SELECT id,title,body,folder,pinned,book,created,updated,color,spacing FROM notes")) {
            SyncState.Note n = new SyncState.Note(); n.uuid = uuidOf(s, ((Number) r[0]).longValue()); n.title = r[1] + ""; n.body = r[2] + ""; n.folder = r[3] + ""; n.pinned = ((Number) r[4]).intValue() == 1; n.source = r[5] + "";
            n.created = ((Number) r[6]).longValue(); n.updated = ((Number) r[7]).longValue(); n.color = ((Number) r[8]).intValue(); n.spacing = ((Number) r[9]).intValue(); st.notes.add(n);
        }
        for (Object[] r : s.rows("SELECT uuid,at FROM sync_deleted WHERE kind='note'")) st.deleted.put(r[0] + "", ((Number) r[1]).longValue());
        SyncState.attach(st, since, name -> { File f = new File(DeskNotes.media(), name); try { return f.isFile() && f.length() < (16 << 20) ? Files.readAllBytes(f.toPath()) : null; } catch (Exception e) { return null; } });
        for (Object[] r : s.rows("SELECT path,kind,position,total,opened FROM books WHERE opened>0")) {
            File f = new File(r[0] + ""); if (!f.isFile()) continue;
            SyncState.Progress p = new SyncState.Progress(); p.name = f.getName(); p.bytes = f.length(); p.kind = r[1] + ""; p.position = ((Number) r[2]).intValue(); p.total = ((Number) r[3]).intValue(); p.at = ((Number) r[4]).longValue(); st.progress.add(p);
        }
        for (Object[] r : s.rows("SELECT day,book,chapter FROM readings")) st.readings.add(SyncState.reading(r[0] + "", r[1] + "", ((Number) r[2]).intValue()));
        for (Object[] r : s.rows("SELECT reference,text,interval,ease,due FROM cards")) { SyncState.Card c = new SyncState.Card(); c.reference = r[0] + ""; c.text = r[1] + ""; c.interval = ((Number) r[2]).intValue(); c.ease = ((Number) r[3]).intValue(); c.due = ((Number) r[4]).longValue(); st.cards.add(c); }
        for (Object[] r : s.rows("SELECT name,level,points,correct,played FROM records")) { SyncState.Record x = new SyncState.Record(); x.name = r[0] + ""; x.level = ((Number) r[1]).intValue(); x.points = ((Number) r[2]).intValue(); x.correct = ((Number) r[3]).intValue(); x.at = ((Number) r[4]).longValue(); st.records.add(x); }
        for (Object[] r : s.rows("SELECT t.artist,t.title,p.at FROM plays p JOIN tracks t ON t.path=p.path WHERE t.kind='audio' AND p.at>?", Math.max(0, since - 86400000L))) { SyncState.Play p = new SyncState.Play(); p.song = SyncState.song(r[0] + "", r[1] + ""); p.at = ((Number) r[2]).longValue(); st.plays.add(p); }
        for (Object[] r : s.rows("SELECT artist,title FROM tracks WHERE kind='audio' AND favorite=1")) st.favorites.add(SyncState.song(r[0] + "", r[1] + ""));
        Map<Long, String> names = new HashMap<>();
        for (Object[] r : s.rows("SELECT id,title,color,created,updated FROM st_subjects")) { SyncState.Subject x = new SyncState.Subject(); x.title = r[1] + ""; x.color = ((Number) r[2]).intValue(); x.created = ((Number) r[3]).longValue(); x.updated = ((Number) r[4]).longValue(); st.subjects.add(x); names.put(((Number) r[0]).longValue(), x.title); }
        for (Object[] r : s.rows("SELECT subject,front,back,due,interval,reps,lapses,updated FROM st_cards")) { SyncState.StudyCard x = new SyncState.StudyCard(); x.subject = names.getOrDefault(((Number) r[0]).longValue(), ""); x.front = r[1] + ""; x.back = r[2] + ""; x.due = ((Number) r[3]).longValue(); x.interval = ((Number) r[4]).intValue(); x.reps = ((Number) r[5]).intValue(); x.lapses = ((Number) r[6]).intValue(); x.updated = ((Number) r[7]).longValue(); st.studyCards.add(x); }
        for (Object[] r : s.rows("SELECT subject,prompt,answer,choices,correct,explanation,updated FROM st_questions")) { SyncState.Question x = new SyncState.Question(); x.subject = names.getOrDefault(((Number) r[0]).longValue(), ""); x.prompt = r[1] + ""; x.answer = r[2] + ""; x.choices = r[3] + ""; x.correct = ((Number) r[4]).intValue(); x.explanation = r[5] + ""; x.updated = ((Number) r[6]).longValue(); st.questions.add(x); }
        for (Object[] r : s.rows("SELECT subject,title,due,estimate,priority,done,updated FROM st_tasks")) { SyncState.Task x = new SyncState.Task(); x.subject = names.getOrDefault(((Number) r[0]).longValue(), ""); x.title = r[1] + ""; x.due = ((Number) r[2]).longValue(); x.estimate = ((Number) r[3]).intValue(); x.priority = ((Number) r[4]).intValue(); x.done = ((Number) r[5]).intValue() == 1; x.updated = ((Number) r[6]).longValue(); st.tasks.add(x); }
        for (Object[] r : s.rows("SELECT key,at FROM sync_gone")) st.gone.put(r[0] + "", ((Number) r[1]).longValue());
        return st;
    }
    /** Brings the phone's changes into this computer, in one transaction. */
    static SyncState.Changes merge(DeskStore s, SyncState phone) throws Exception {
        synchronized (s) {
            SyncState mine = export(s, Long.MAX_VALUE); SyncState.Changes ch = SyncState.merge(mine, phone);
            boolean auto = s.db.getAutoCommit(); s.db.setAutoCommit(false);
            try {
                for (Map.Entry<String, String> e : ch.relink.entrySet()) s.update("UPDATE sync_ids SET uuid=? WHERE kind='note' AND uuid=?", e.getValue(), e.getKey());
                for (Map.Entry<String, byte[]> m : phone.media.entrySet()) { File f = new File(DeskNotes.media(), m.getKey()); if (!f.isFile()) Files.write(f.toPath(), m.getValue()); }
                for (SyncState.Note n : ch.upsert) {
                    long local = localOf(s, n.uuid);
                    if (local > 0 && s.count("SELECT COUNT(*) FROM notes WHERE id=?", local) > 0) s.update("UPDATE notes SET title=?,body=?,folder=?,pinned=?,book=?,created=?,updated=?,color=?,spacing=? WHERE id=?", n.title, n.body, n.folder, n.pinned ? 1 : 0, n.source, n.created, n.updated, n.color, n.spacing, local);
                    else { long id = s.update("INSERT INTO notes(title,body,folder,pinned,book,created,updated,color,spacing) VALUES(?,?,?,?,?,?,?,?,?)", n.title, n.body, n.folder, n.pinned ? 1 : 0, n.source, n.created, n.updated, n.color, n.spacing); s.update("INSERT OR REPLACE INTO sync_ids(kind,local,uuid) VALUES('note',?,?)", id, n.uuid); }
                }
                for (String u : ch.delete) { long local = localOf(s, u); if (local > 0) s.update("DELETE FROM notes WHERE id=?", local); s.update("DELETE FROM sync_ids WHERE kind='note' AND uuid=?", u); }
                for (Map.Entry<String, Long> t : ch.tombs.entrySet()) s.update("INSERT OR REPLACE INTO sync_deleted(kind,uuid,at) VALUES('note',?,?)", t.getKey(), t.getValue());
                if (!ch.progress.isEmpty()) for (Object[] r : s.rows("SELECT path FROM books")) {
                    File f = new File(r[0] + ""); if (!f.isFile()) continue; String key = SyncState.Progress.key(f.getName(), f.length());
                    for (SyncState.Progress p : ch.progress) if (p.key().equals(key)) s.update("UPDATE books SET position=?,total=?,opened=? WHERE path=?", p.position, p.total, p.at, r[0]);
                }
                for (String[] r : ch.readings) s.update("INSERT OR IGNORE INTO readings(day,book,chapter) VALUES(?,?,?)", r[0], r[1], Integer.parseInt(r[2]));
                for (SyncState.Card c : ch.cards) { if (s.count("SELECT COUNT(*) FROM cards WHERE reference=?", c.reference) > 0) s.update("UPDATE cards SET text=?,interval=?,ease=?,due=? WHERE reference=?", c.text, c.interval, c.ease, c.due, c.reference); else s.update("INSERT INTO cards(reference,text,interval,ease,due) VALUES(?,?,?,?,?)", c.reference, c.text, c.interval, c.ease, c.due); }
                for (SyncState.Record r : ch.records) s.update("INSERT INTO records(level,name,points,correct,played) VALUES(?,?,?,?,?)", r.level, r.name, r.points, r.correct, r.at);
                if (!ch.plays.isEmpty() || !ch.favorites.isEmpty()) {
                    Map<String, String> paths = new HashMap<>(); for (Object[] r : s.rows("SELECT path,artist,title FROM tracks WHERE kind='audio'")) paths.putIfAbsent(SyncState.song(r[1] + "", r[2] + ""), r[0] + "");
                    for (SyncState.Play p : ch.plays) { String path = paths.get(p.song); if (path == null) continue; s.update("INSERT INTO plays(path,at) VALUES(?,?)", path, p.at); s.update("UPDATE tracks SET plays=plays+1,last_played=MAX(last_played,?) WHERE path=?", p.at, path); }
                    for (String f : ch.favorites) { String path = paths.get(f); if (path != null) s.update("UPDATE tracks SET favorite=1 WHERE path=?", path); }
                }
                applyStudy(s, ch);
                s.db.commit(); return ch;
            } catch (Exception e) { s.db.rollback(); throw e; }
            finally { s.db.setAutoCommit(auto); }
        }
    }
    /** Estudos from the phone: subjects by name, then cards, questions and tasks (newest wins), then deletions. */
    static void applyStudy(DeskStore s, SyncState.Changes ch) throws SQLException {
        if (ch.study() == 0 && ch.goneTombs.isEmpty()) return;
        Map<String, Long> subjects = new HashMap<>(); for (Object[] r : s.rows("SELECT id,title FROM st_subjects")) subjects.put(SyncState.norm(r[1] + ""), ((Number) r[0]).longValue());
        for (SyncState.Subject x : ch.subjects) { Long id = subjects.get(SyncState.norm(x.title)); if (id != null) s.update("UPDATE st_subjects SET title=?,color=?,updated=? WHERE id=?", x.title, x.color, x.updated, id); else subjects.put(SyncState.norm(x.title), s.update("INSERT INTO st_subjects(title,color,created,updated) VALUES(?,?,?,?)", x.title, x.color, x.created, x.updated)); }
        java.util.function.Function<String, Long> subjectId = t -> { if (t == null || t.trim().isEmpty()) return 0L; Long id = subjects.get(SyncState.norm(t)); if (id != null) return id; try { long n = s.update("INSERT INTO st_subjects(title,color,created,updated) VALUES(?,?,?,?)", t, DeskStudies.COLORS[subjects.size() % DeskStudies.COLORS.length], System.currentTimeMillis(), 0L); subjects.put(SyncState.norm(t), n); return n; } catch (SQLException e) { return 0L; } };
        Map<Long, String> names = new HashMap<>(); for (Map.Entry<String, Long> e : subjects.entrySet()) names.put(e.getValue(), e.getKey());
        Map<String, Long> cards = new HashMap<>(), questions = new HashMap<>(), tasks = new HashMap<>();
        for (Object[] r : s.rows("SELECT st_cards.id,COALESCE(st_subjects.title,''),front FROM st_cards LEFT JOIN st_subjects ON st_subjects.id=st_cards.subject")) cards.put("card:" + SyncState.studyKey(r[1] + "", r[2] + ""), ((Number) r[0]).longValue());
        for (Object[] r : s.rows("SELECT st_questions.id,COALESCE(st_subjects.title,''),prompt FROM st_questions LEFT JOIN st_subjects ON st_subjects.id=st_questions.subject")) questions.put("question:" + SyncState.studyKey(r[1] + "", r[2] + ""), ((Number) r[0]).longValue());
        for (Object[] r : s.rows("SELECT st_tasks.id,COALESCE(st_subjects.title,''),st_tasks.title,due FROM st_tasks LEFT JOIN st_subjects ON st_subjects.id=st_tasks.subject")) tasks.put("task:" + SyncState.taskKey(r[1] + "", r[2] + "", ((Number) r[3]).longValue()), ((Number) r[0]).longValue());
        for (SyncState.StudyCard x : ch.studyCards) { Long id = cards.get(x.key()); if (id != null) s.update("UPDATE st_cards SET back=?,due=?,interval=?,reps=?,lapses=?,updated=? WHERE id=?", x.back, x.due, x.interval, x.reps, x.lapses, x.updated, id); else s.update("INSERT INTO st_cards(subject,front,back,due,interval,reps,lapses,updated) VALUES(?,?,?,?,?,?,?,?)", subjectId.apply(x.subject), x.front, x.back, x.due, x.interval, x.reps, x.lapses, x.updated); }
        for (SyncState.Question x : ch.questions) { Long id = questions.get(x.key()); if (id != null) s.update("UPDATE st_questions SET answer=?,choices=?,correct=?,explanation=?,updated=? WHERE id=?", x.answer, x.choices, x.correct, x.explanation, x.updated, id); else s.update("INSERT INTO st_questions(subject,prompt,answer,choices,correct,explanation,updated) VALUES(?,?,?,?,?,?,?)", subjectId.apply(x.subject), x.prompt, x.answer, x.choices, x.correct, x.explanation, x.updated); }
        for (SyncState.Task x : ch.tasks) { Long id = tasks.get(x.key()); if (id != null) s.update("UPDATE st_tasks SET estimate=?,priority=?,done=?,updated=? WHERE id=?", x.estimate, x.priority, x.done ? 1 : 0, x.updated, id); else s.update("INSERT INTO st_tasks(subject,title,due,estimate,priority,done,updated) VALUES(?,?,?,?,?,?,?)", subjectId.apply(x.subject), x.title, x.due, x.estimate, x.priority, x.done ? 1 : 0, x.updated); }
        for (String k : ch.remove) {
            Long id = k.startsWith("card:") ? cards.get(k) : k.startsWith("question:") ? questions.get(k) : k.startsWith("task:") ? tasks.get(k) : null;
            if (id != null) s.update("DELETE FROM " + (k.startsWith("card:") ? "st_cards" : k.startsWith("question:") ? "st_questions" : "st_tasks") + " WHERE id=?", id);
            if (k.startsWith("subject:")) { Long sid = subjects.get(k.substring(8)); if (sid != null) { for (String t : new String[]{"st_cards", "st_questions", "st_tasks", "st_quiz"}) s.update("UPDATE " + t + " SET subject=0 WHERE subject=?", sid); s.update("UPDATE notes SET subject=0 WHERE subject=?", sid); s.update("DELETE FROM st_materials WHERE subject=?", sid); s.update("DELETE FROM st_subjects WHERE id=?", sid); } }
        }
        for (Map.Entry<String, Long> t : ch.goneTombs.entrySet()) s.update("INSERT OR REPLACE INTO sync_gone(key,at) VALUES(?,?)", t.getKey(), t.getValue());
    }
    @Override public SyncState.Changes apply(String device, SyncState phone) throws Exception { return merge(s, phone); }
    @Override public SyncState state(String device, long since) throws Exception { synchronized (s) { SyncState st = export(s, since); st.device = id(); st.name = name(); return st; } }

    // ================================================================ ficheiros
    static File inbox() { File home = new File(System.getProperty("user.home")); File dl = new File(home, "Downloads"); if (!dl.isDirectory()) dl = new File(home, "Transferências"); if (!dl.isDirectory()) dl = home; File f = new File(dl, "Seeky"); f.mkdirs(); return f; }
    @Override public File incoming(String name) { return SyncWire.unique(inbox(), name); }
    @Override public void received(File f, String device) { ui(() -> d.status("Recebido de " + device + ": " + f.getName() + " (Transferências\\Seeky)")); }
    @Override public List<String[]> outbox() { List<String[]> out = new ArrayList<>(); try { for (Object[] r : s.rows("SELECT id,name,size,path FROM sync_outbox ORDER BY added")) if (new File(r[3] + "").isFile()) out.add(new String[]{r[0] + "", r[1] + "", r[2] + ""}); } catch (SQLException ignored) {} return out; }
    @Override public File outboxFile(String id) { try { List<Object[]> r = s.rows("SELECT path FROM sync_outbox WHERE id=?", id); return r.isEmpty() ? null : new File(r.get(0)[0] + ""); } catch (SQLException e) { return null; } }
    @Override public void taken(String id) { try { s.update("DELETE FROM sync_outbox WHERE id=?", id); } catch (SQLException ignored) {} ui(() -> { if (d.current.equals("phone")) d.open("phone"); }); }
    void send(List<File> files) {
        int n = 0; for (File f : files) if (f.isFile()) try { s.update("INSERT INTO sync_outbox(id,path,name,size,added) VALUES(?,?,?,?,?)", SyncState.uuid(), f.getAbsolutePath(), f.getName(), f.length(), System.currentTimeMillis()); n++; } catch (SQLException ignored) {}
        start(); d.status(n + (n == 1 ? " ficheiro vai" : " ficheiros vão") + " para o telemóvel na próxima sincronização.");
    }
    static List<String> addresses() {
        List<String> out = new ArrayList<>();
        try { for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) if (n.isUp() && !n.isLoopback() && !n.isVirtual()) for (InetAddress a : Collections.list(n.getInetAddresses())) if (a instanceof Inet4Address && !a.isLinkLocalAddress()) out.add(a.getHostAddress()); } catch (Exception ignored) {}
        return out;
    }

    // ================================================================ ecrã
    Node view() {
        start(); if (code() == null) newCode();
        VBox page = DeskUi.page(DeskUi.title("Telemóvel"));
        if (!lastError.isEmpty()) page.getChildren().add(DeskUi.card(DeskUi.heading("Não foi possível ligar"), DeskUi.text(lastError), DeskUi.button("Tentar outra vez", true, () -> { stop(); d.open("phone"); })));
        Label big = new Label(code().substring(0, 3) + " " + code().substring(3)); big.getStyleClass().add("pair-code");
        page.getChildren().add(DeskUi.card(DeskUi.heading("Ligar um telemóvel"),
            DeskUi.text("1. No telemóvel, abra o Seeky → Ferramentas → Seeky no computador → Ligar ao computador.\n2. Escolha \"" + name() + "\" e escreva este código:"), big,
            DeskUi.muted("O código vale 10 minutos. O telemóvel e o computador têm de estar na mesma rede Wi-Fi. Se o Windows perguntar, permita o Seeky em redes privadas."),
            DeskUi.row(DeskUi.button("Código novo", false, () -> { newCode(); d.open("phone"); }), DeskUi.muted("Endereço deste computador: " + String.join(", ", addresses())))));
        VBox phones = new VBox(8); JSONArray all = devices();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i); if (o == null) continue; String id = o.optString("id"), nm = o.optString("name"); long last = o.optLong("last");
            phones.getChildren().add(DeskUi.row(DeskUi.icon("phone", 22), new VBox(2, DeskUi.heading(nm), DeskUi.muted(last == 0 ? "Ligado, ainda sem sincronizar" : "Sincronizado " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last)))), DeskUi.grow(),
                DeskUi.button("Desligar", false, () -> { if (DeskUi.confirm("Desligar " + nm + "?", "O telemóvel deixa de sincronizar com este computador. Os dados ficam nos dois.")) { forget(id); d.open("phone"); } })));
        }
        if (all.length() == 0) phones.getChildren().add(DeskUi.muted("Ainda nenhum telemóvel ligado."));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Telemóveis ligados"), DeskUi.muted("Notas, onde ficou a leitura dos livros, capítulos lidos, versículos para decorar, recordes do jogo e músicas favoritas e mais ouvidas ficam iguais nos dois. O telemóvel sincroniza ao abrir o Seeky e quando toca em Sincronizar agora."), phones));
        VBox queue = new VBox(6);
        for (String[] f : outbox()) queue.getChildren().add(DeskUi.row(DeskUi.icon("export", 18), DeskUi.text(f[1]), DeskUi.muted(Rules.bytes(Long.parseLong(f[2]))), DeskUi.grow(), DeskUi.button("Tirar", false, () -> { taken(f[0]); d.open("phone"); })));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Enviar para o telemóvel"), DeskUi.muted("Os ficheiros chegam ao telemóvel na próxima sincronização, em Transferências/Seeky."), queue,
            DeskUi.row(DeskUi.button("Escolher ficheiros…", true, () -> { FileChooser fc = new FileChooser(); List<File> f = fc.showOpenMultipleDialog(d.stage); if (f != null) { send(f); d.open("phone"); } }))));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Recebidos do telemóvel"), DeskUi.muted(inbox().getPath()), DeskUi.button("Abrir a pasta", false, () -> DeskFiles.system(inbox()))));
        return DeskUi.scroll(page);
    }
}
