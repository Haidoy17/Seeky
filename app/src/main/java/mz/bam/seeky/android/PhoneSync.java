package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.EditText;
import java.io.*;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.*;

/**
 * Seeky no computador (telemóvel): liga-se ao Seeky para Windows na mesma rede Wi-Fi, sincroniza notas,
 * onde ficou a leitura dos livros da biblioteca, capítulos lidos, versículos para decorar, recordes do
 * jogo, músicas favoritas e audições, e troca ficheiros. Sincroniza também sozinho ao abrir o Seeky.
 * Notas bloqueadas nunca saem do telemóvel.
 */
final class PhoneSync implements SyncClient.Local {
    static final String PREFS = "seeky_sync";
    final Context c;
    PhoneSync(Context c) { this.c = c.getApplicationContext(); }

    // ================================================================ identidade e computador
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }
    static String device(Context c) { String id = prefs(c).getString("device", ""); if (id.isEmpty()) { id = SyncState.uuid(); prefs(c).edit().putString("device", id).apply(); } return id; }
    static String deviceName() { String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER) + " " + (Build.MODEL == null ? "" : Build.MODEL); m = m.trim(); return m.isEmpty() ? "Telemóvel" : Character.toUpperCase(m.charAt(0)) + m.substring(1); }
    static SyncClient.Computer computer(Context c) {
        SharedPreferences p = prefs(c); String token = p.getString("token", ""); if (token.isEmpty()) return null;
        SyncClient.Computer pc = new SyncClient.Computer(); pc.id = p.getString("pc_id", ""); pc.name = p.getString("pc_name", "Computador"); pc.host = p.getString("pc_host", ""); pc.port = p.getInt("pc_port", SyncWire.TCP_PORT); pc.token = SyncWire.unb64(token); return pc;
    }
    static void remember(Context c, SyncClient.Computer pc) { prefs(c).edit().putString("pc_id", pc.id).putString("pc_name", pc.name).putString("pc_host", pc.host).putInt("pc_port", pc.port).putString("token", SyncWire.b64(pc.token)).apply(); }
    static void forget(Context c) { prefs(c).edit().remove("pc_id").remove("pc_name").remove("pc_host").remove("pc_port").remove("token").remove("last").apply(); }

    // ================================================================ notas: identidades e apagadas
    static final class Ids extends SQLiteOpenHelper {
        Ids(Context c) { super(c.getApplicationContext(), "seeky-sync.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE ids(kind TEXT NOT NULL,local INTEGER NOT NULL,uuid TEXT NOT NULL,PRIMARY KEY(kind,local),UNIQUE(kind,uuid))");
            d.execSQL("CREATE TABLE deleted(kind TEXT NOT NULL,uuid TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(kind,uuid))");
        }
        @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {}
        String uuid(long local) {
            try (Cursor r = getReadableDatabase().rawQuery("SELECT uuid FROM ids WHERE kind='note' AND local=?", new String[]{"" + local})) { if (r.moveToFirst()) return r.getString(0); }
            String u = SyncState.uuid(); ContentValues v = new ContentValues(); v.put("kind", "note"); v.put("local", local); v.put("uuid", u); getWritableDatabase().insertWithOnConflict("ids", null, v, SQLiteDatabase.CONFLICT_REPLACE); return u;
        }
        long local(String uuid) { try (Cursor r = getReadableDatabase().rawQuery("SELECT local FROM ids WHERE kind='note' AND uuid=?", new String[]{uuid})) { return r.moveToFirst() ? r.getLong(0) : 0; } }
        void link(long local, String uuid) { getWritableDatabase().delete("ids", "kind='note' AND uuid=?", new String[]{uuid}); ContentValues v = new ContentValues(); v.put("kind", "note"); v.put("local", local); v.put("uuid", uuid); getWritableDatabase().insertWithOnConflict("ids", null, v, SQLiteDatabase.CONFLICT_REPLACE); }
        void tomb(String uuid, long at) { ContentValues v = new ContentValues(); v.put("kind", "note"); v.put("uuid", uuid); v.put("at", at); getWritableDatabase().insertWithOnConflict("deleted", null, v, SQLiteDatabase.CONFLICT_REPLACE); }
        Map<String, Long> tombs() { Map<String, Long> out = new LinkedHashMap<>(); try (Cursor r = getReadableDatabase().rawQuery("SELECT uuid,at FROM deleted WHERE kind='note'", null)) { while (r.moveToNext()) out.put(r.getString(0), r.getLong(1)); } return out; }
    }
    /** A note went to the bin: the computer removes it too at the next sync. */
    static void noteDeleted(Context c, long id) {
        try (Ids ids = new Ids(c); Cursor r = ids.getReadableDatabase().rawQuery("SELECT uuid FROM ids WHERE kind='note' AND local=?", new String[]{"" + id})) {
            if (r.moveToFirst()) { ids.tomb(r.getString(0), System.currentTimeMillis()); ids.getWritableDatabase().delete("ids", "kind='note' AND local=?", new String[]{"" + id}); }
        } catch (Exception ignored) {}
    }

    // ================================================================ estado do telemóvel
    @Override public SyncState state(long since) {
        SyncState st = new SyncState();
        try (Ids ids = new Ids(c)) {
            for (NotepadStore.Note n : NotepadStore.all(c)) {
                if (n.locked) continue; // fica só no telemóvel
                SyncState.Note x = new SyncState.Note(); x.uuid = ids.uuid(n.id); x.title = n.title; x.body = n.body; x.folder = n.folder; x.source = n.book(); x.pinned = n.pinned; x.color = n.color; x.spacing = n.spacing; x.created = n.created; x.updated = n.updated; st.notes.add(x);
            }
            st.deleted.putAll(ids.tombs());
        }
        SyncState.attach(st, since, name -> { File f = NotepadStore.media(c, name); try { return f != null && f.isFile() && f.length() < (16 << 20) ? Files.readAllBytes(f.toPath()) : null; } catch (IOException e) { return null; } });
        SharedPreferences seeky = c.getSharedPreferences("seeky", 0);
        try (StudyStore db = new StudyStore(c); Cursor r = db.getReadableDatabase().rawQuery("SELECT id,name,bytes,mime FROM materials", null)) {
            while (r.moveToNext()) {
                String uri = StudyFileProvider.uri(c, r.getLong(0)).toString(); long at = seeky.getLong("reader_at_" + uri, 0); if (at == 0) continue;
                boolean pdf = "application/pdf".equals(r.getString(3)) || r.getString(1).toLowerCase(Locale.ROOT).endsWith(".pdf");
                SyncState.Progress p = new SyncState.Progress(); p.name = r.getString(1); p.bytes = r.getLong(2); p.kind = pdf ? "pdf" : "epub"; p.position = seeky.getInt((pdf ? "reader_page_" : "reader_chapter_") + uri, 0); p.at = at; st.progress.add(p);
            }
        } catch (Exception ignored) {}
        try (BibleStudyStore b = new BibleStudyStore(c)) {
            SQLiteDatabase d = b.getReadableDatabase();
            try (Cursor r = d.rawQuery("SELECT day,book,chapter FROM readings", null)) { while (r.moveToNext()) st.readings.add(SyncState.reading(r.getString(0), r.getString(1), r.getInt(2))); }
            try (Cursor r = d.rawQuery("SELECT title,text,interval,ease,due FROM cards", null)) { while (r.moveToNext()) { SyncState.Card x = new SyncState.Card(); x.reference = r.getString(0); x.text = r.getString(1); x.interval = r.getInt(2); x.ease = r.getInt(3); x.due = r.getLong(4); st.cards.add(x); } }
            try (Cursor r = d.rawQuery("SELECT name,level,score,correct,at FROM records", null)) { while (r.moveToNext()) { SyncState.Record x = new SyncState.Record(); x.name = r.getString(0); x.level = r.getInt(1); x.points = r.getInt(2); x.correct = r.getInt(3); x.at = r.getLong(4); st.records.add(x); } }
        } catch (Exception ignored) {}
        try (MediaLibrary m = new MediaLibrary(c)) {
            SQLiteDatabase d = m.getReadableDatabase();
            try (Cursor r = d.rawQuery("SELECT t.artist,t.title,p.at FROM plays p JOIN tracks t ON t.uri=p.uri WHERE t.kind='audio' AND p.at>?", new String[]{"" + Math.max(0, since - 86400000L)})) { while (r.moveToNext()) { SyncState.Play x = new SyncState.Play(); x.song = SyncState.song(r.getString(0), r.getString(1)); x.at = r.getLong(2); st.plays.add(x); } }
            try (Cursor r = d.rawQuery("SELECT artist,title FROM tracks WHERE kind='audio' AND favorite=1", null)) { while (r.moveToNext()) st.favorites.add(SyncState.song(r.getString(0), r.getString(1))); }
        } catch (Exception ignored) {}
        return st;
    }

    @Override public SyncState.Changes apply(SyncState pc) throws Exception {
        SyncState mine = state(Long.MAX_VALUE); SyncState.Changes ch = SyncState.merge(mine, pc);
        try (Ids ids = new Ids(c)) {
            for (Map.Entry<String, String> e : ch.relink.entrySet()) { long local = ids.local(e.getKey()); if (local > 0) ids.link(local, e.getValue()); }
            for (Map.Entry<String, byte[]> m : pc.media.entrySet()) { File f = NotepadStore.media(c, m.getKey()); if (f != null && !f.isFile()) Files.write(f.toPath(), m.getValue()); }
            for (SyncState.Note n : ch.upsert) {
                long local = ids.local(n.uuid); NotepadStore.Note note = local > 0 ? NotepadStore.load(c, local) : null;
                if (note != null && note.locked) continue;
                if (note == null) note = new NotepadStore.Note();
                note.title = n.title; note.body = n.body; note.folder = n.folder; note.pinned = n.pinned; note.color = n.color; note.spacing = n.spacing; note.created = n.created; if (note.sourceTitle.isEmpty() && note.book().isEmpty()) note.sourceTitle = n.source;
                long id = NotepadStore.save(c, note);
                try (StudyStore db = new StudyStore(c)) { ContentValues v = new ContentValues(); v.put("updated", n.updated); v.put("created", n.created); db.getWritableDatabase().update("notes", v, "id=?", new String[]{"" + id}); }
                ids.link(id, n.uuid);
            }
            for (String u : ch.delete) { long local = ids.local(u); if (local > 0) { try { NotepadStore.trash(c, local); } catch (Exception ignored) {} } ids.getWritableDatabase().delete("ids", "kind='note' AND uuid=?", new String[]{u}); }
            for (Map.Entry<String, Long> t : ch.tombs.entrySet()) ids.tomb(t.getKey(), t.getValue());
        }
        if (!ch.progress.isEmpty()) {
            SharedPreferences.Editor e = c.getSharedPreferences("seeky", 0).edit();
            try (StudyStore db = new StudyStore(c); Cursor r = db.getReadableDatabase().rawQuery("SELECT id,name,bytes FROM materials", null)) {
                while (r.moveToNext()) { String key = SyncState.Progress.key(r.getString(1), r.getLong(2)); for (SyncState.Progress p : ch.progress) if (p.key().equals(key)) { String uri = StudyFileProvider.uri(c, r.getLong(0)).toString(); e.putInt((p.kind.equals("pdf") ? "reader_page_" : "reader_chapter_") + uri, p.position).putLong("reader_at_" + uri, p.at); } }
            }
            e.apply();
        }
        if (!ch.readings.isEmpty() || !ch.cards.isEmpty() || !ch.records.isEmpty()) try (BibleStudyStore b = new BibleStudyStore(c)) {
            SQLiteDatabase d = b.getWritableDatabase(); d.beginTransaction();
            try {
                for (String[] r : ch.readings) { ContentValues v = new ContentValues(); v.put("day", r[0]); v.put("book", r[1]); v.put("chapter", Integer.parseInt(r[2])); v.put("at", System.currentTimeMillis()); d.insertWithOnConflict("readings", null, v, SQLiteDatabase.CONFLICT_IGNORE); }
                for (SyncState.Card x : ch.cards) {
                    ContentValues v = new ContentValues(); v.put("text", x.text); v.put("interval", x.interval); v.put("ease", x.ease); v.put("due", x.due);
                    if (d.update("cards", v, "title=?", new String[]{x.reference}) == 0) {
                        BibleReferences.Reference ref = BibleReferences.lookup(x.reference);
                        v.put("title", x.reference); v.put("book", ref == null ? "" : ref.book); v.put("chapter", ref == null ? 0 : ref.chapter);
                        int verse = 0, last = 0; if (ref != null && !ref.ranges.isEmpty()) { verse = ref.ranges.get(0)[1]; last = Math.min(ref.ranges.get(0)[3], 999); }
                        v.put("verse", verse); v.put("last_verse", Math.max(verse, last)); v.put("created", System.currentTimeMillis()); d.insert("cards", null, v);
                    }
                }
                for (SyncState.Record x : ch.records) { ContentValues v = new ContentValues(); v.put("name", x.name); v.put("level", x.level); v.put("score", x.points); v.put("correct", x.correct); v.put("seconds", 0); v.put("at", x.at); d.insert("records", null, v); }
                d.setTransactionSuccessful();
            } finally { d.endTransaction(); }
        }
        if (!ch.plays.isEmpty() || !ch.favorites.isEmpty()) try (MediaLibrary m = new MediaLibrary(c)) {
            SQLiteDatabase d = m.getWritableDatabase(); Map<String, String> uris = new HashMap<>();
            try (Cursor r = d.rawQuery("SELECT uri,artist,title FROM tracks WHERE kind='audio'", null)) { while (r.moveToNext()) uris.putIfAbsent(SyncState.song(r.getString(1), r.getString(2)), r.getString(0)); }
            d.beginTransaction();
            try {
                for (SyncState.Play p : ch.plays) { String uri = uris.get(p.song); if (uri == null) continue; ContentValues v = new ContentValues(); v.put("uri", uri); v.put("at", p.at); d.insert("plays", null, v); d.execSQL("UPDATE tracks SET plays=plays+1,last_played=MAX(last_played,?) WHERE uri=?", new Object[]{p.at, uri}); }
                for (String f : ch.favorites) { String uri = uris.get(f); if (uri != null) d.execSQL("UPDATE tracks SET favorite=1 WHERE uri=?", new Object[]{uri}); }
                d.setTransactionSuccessful();
            } finally { d.endTransaction(); }
        }
        if (ch.notes() > 0 || !ch.cards.isEmpty() || !ch.readings.isEmpty()) StudyEvents.changed();
        return ch;
    }

    // ================================================================ rede
    /** The saved computer; if its address changed (new Wi-Fi), finds it again by its identity. */
    static SyncClient.Computer reach(Context c) throws IOException {
        SyncClient.Computer pc = computer(c); if (pc == null) throw new IOException("Ligue primeiro o telemóvel ao computador.");
        try (java.net.Socket s = new java.net.Socket()) { s.connect(new java.net.InetSocketAddress(pc.host, pc.port), 2500); return pc; }
        catch (IOException e) {
            for (SyncClient.Computer found : SyncClient.discover(2500)) if (found.id.equals(pc.id)) { pc.host = found.host; pc.port = found.port; remember(c, pc); return pc; }
            throw new IOException("O computador \"" + pc.name + "\" não respondeu. Abra o Seeky no computador e use a mesma rede Wi-Fi.");
        }
    }
    static final class Summary { int notesHere, otherHere, notesThere, otherThere, files; }
    /** Sync + files waiting on the computer. */
    static Summary now(Context c) throws Exception {
        SyncClient.Computer pc = reach(c); long since = prefs(c).getLong("last", 0);
        SyncClient.Result r = SyncClient.sync(pc, device(c), deviceName(), new PhoneSync(c), since);
        Summary s = new Summary(); s.notesHere = r.notesHere; s.otherHere = r.otherHere; s.notesThere = r.notesThere; s.otherThere = r.otherThere;
        s.files = receive(c, pc);
        prefs(c).edit().putLong("last", System.currentTimeMillis()).apply();
        return s;
    }
    static File downloads() { File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky"); d.mkdirs(); return d; }
    static int receive(Context c, SyncClient.Computer pc) throws Exception {
        File dir = downloads(); List<String> saved = new ArrayList<>(); File[] current = {null};
        int n = SyncClient.receive(pc, device(c), deviceName(), new SyncClient.Sink() {
            public OutputStream open(String name, long size) throws IOException { if (dir.getUsableSpace() < size + (64 << 20)) throw new IOException("Sem espaço no telemóvel para " + name + "."); current[0] = SyncWire.unique(dir, name); return new BufferedOutputStream(new FileOutputStream(current[0])); }
            public void done(String name, boolean ok) { if (!ok && current[0] != null) current[0].delete(); else if (current[0] != null) saved.add(current[0].getPath()); }
        });
        if (!saved.isEmpty()) android.media.MediaScannerConnection.scanFile(c, saved.toArray(new String[0]), null, null);
        return n;
    }
    /** At start: if this phone has a computer and the last sync is older than 10 minutes, sync quietly. */
    static void auto(Context c) {
        if (computer(c) == null || System.currentTimeMillis() - prefs(c).getLong("last", 0) < 600000L || running) return;
        running = true;
        new Thread(() -> { try { now(c); } catch (Exception ignored) {} finally { running = false; } }, "seeky-ligado").start();
    }
    static volatile boolean running;

    // ================================================================ ecrã
    static final int FILES = 98;
    static String tr(Context c, String s) { return AppLanguage.text(c, s); }
    static void show(Activity a) {
        SyncClient.Computer pc = computer(a); long last = prefs(a).getLong("last", 0);
        UiPanel p = new UiPanel(a, "Seeky no computador", pc == null ? tr(a, "Notas, leitura, estudo e música iguais no telemóvel e no Seeky para Windows") : tr(a, "Ligado a") + " " + pc.name + (last == 0 ? "" : " · " + tr(a, "sincronizado") + " " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(last))), "phone");
        if (pc == null) p.action("phone", "Ligar ao computador", tr(a, "No computador: Seeky → Telemóvel mostra o código"), false, () -> connect(a));
        else {
            p.action("refresh", "Sincronizar agora", tr(a, "Notas, leitura, estudo, música e ficheiros à espera"), false, () -> syncNow(a));
            p.action("share", "Enviar ficheiros para o computador", tr(a, "Chegam a Transferências\\Seeky no computador"), false, () -> { try { a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), FILES); } catch (Exception e) { PlusUi.error(a, e); } });
            p.action("phone", "Ligar a outro computador", null, false, () -> connect(a));
            p.danger("Desligar deste computador", () -> new UiDialog.Builder(a).setTitle("Desligar do computador?").setMessage("Os dados ficam nos dois; deixam só de sincronizar.").setPositiveButton("Desligar", (d, w) -> { forget(a); PlusUi.toast(a, "Desligado."); }).setNegativeButton("Cancelar", null).show());
        }
        p.show();
    }
    static void syncNow(Activity a) {
        PlusUi.toast(a, "A sincronizar com o computador…");
        PlusUi.run(a, () -> {
            try { Summary s = now(a); PlusUi.post(a, () -> PlusUi.toast(a, tr(a, "Sincronizado") + ": " + s.notesHere + " " + tr(a, s.notesHere == 1 ? "nota recebida" : "notas recebidas") + ", " + s.notesThere + " " + tr(a, s.notesThere == 1 ? "enviada" : "enviadas") + (s.files > 0 ? ", " + s.files + " " + tr(a, s.files == 1 ? "ficheiro em Transferências/Seeky" : "ficheiros em Transferências/Seeky") : "") + ".")); }
            catch (Exception e) { String m = e.getMessage(); PlusUi.post(a, () -> PlusUi.toast(a, m == null ? tr(a, "Não foi possível sincronizar.") : m)); }
        });
    }
    /** Finds the computers with Seeky open, then asks for the code shown there. */
    static void connect(Activity a) {
        PlusUi.toast(a, "A procurar o computador na rede Wi-Fi…");
        PlusUi.run(a, () -> {
            List<SyncClient.Computer> found = SyncClient.discover(2500);
            PlusUi.post(a, () -> {
                UiPanel p = new UiPanel(a, "Ligar ao computador", found.isEmpty() ? tr(a, "Nenhum encontrado. No computador abra Seeky → Telemóvel; use a mesma rede Wi-Fi.") : tr(a, "Escolha o computador"), "phone");
                for (SyncClient.Computer pc : found) p.action("phone", pc.name, pc.host, false, () -> askCode(a, pc.host, pc.port, pc.name));
                p.action("edit", "Escrever o endereço do computador", tr(a, "Aparece no computador, em Seeky → Telemóvel"), false, () -> {
                    EditText host = PlusUi.input(a, "Ex.: 192.168.1.20", "", 60);
                    new UiDialog.Builder(a).setTitle("Endereço do computador").setView(host).setPositiveButton("Seguinte", (d, w) -> { String h = host.getText().toString().trim(); if (!h.isEmpty()) askCode(a, h, SyncWire.TCP_PORT, h); }).setNegativeButton("Cancelar", null).show();
                });
                p.action("refresh", "Procurar outra vez", null, false, () -> connect(a));
                p.show();
            });
        });
    }
    static void askCode(Activity a, String host, int port, String name) {
        EditText code = PlusUi.input(a, "123 456", "", 7); code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new UiDialog.Builder(a).setTitle(tr(a, "Código de") + " " + name).setMessage("Escreva os 6 números que aparecem no computador, em Seeky → Telemóvel.").setView(code).setPositiveButton("Ligar", (d, w) -> {
            String value = code.getText().toString();
            PlusUi.run(a, () -> {
                try { SyncClient.Computer pc = SyncClient.pair(host, port, device(a), deviceName(), value); remember(a, pc); prefs(a).edit().putLong("last", 0).apply(); PlusUi.post(a, () -> { PlusUi.toast(a, tr(a, "Ligado a") + " " + pc.name + "."); syncNow(a); }); }
                catch (Exception e) { String m = e.getMessage(); PlusUi.post(a, () -> PlusUi.toast(a, m == null ? tr(a, "Não foi possível ligar.") : m)); }
            });
        }).setNegativeButton("Cancelar", null).show();
    }
    /** Files chosen in "Enviar ficheiros para o computador". */
    static void sendFiles(Activity a, Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return;
        PlusUi.toast(a, "A enviar para o computador…");
        PlusUi.run(a, () -> {
            int ok = 0; String error = null;
            try {
                SyncClient.Computer pc = reach(a);
                for (Uri u : uris) {
                    String name = "ficheiro"; long size = -1;
                    try (Cursor r = a.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE}, null, null, null)) { if (r != null && r.moveToFirst()) { if (!r.isNull(0)) name = r.getString(0); if (!r.isNull(1)) size = r.getLong(1); } }
                    if (size < 0) { error = name + ": " + tr(a, "tamanho desconhecido."); continue; }
                    try (InputStream in = a.getContentResolver().openInputStream(u)) { if (in == null) throw new IOException(name); SyncClient.send(pc, device(a), deviceName(), name, size, in); ok++; }
                }
            } catch (Exception e) { error = e.getMessage(); }
            final int sent = ok; final String problem = error;
            PlusUi.post(a, () -> PlusUi.toast(a, sent + " " + tr(a, sent == 1 ? "ficheiro enviado para o computador." : "ficheiros enviados para o computador.") + (problem == null ? "" : " " + problem)));
        });
    }
}
