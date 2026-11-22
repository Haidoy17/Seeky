package mz.bam.seeky.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.*;
import org.json.JSONObject;

/**
 * The notebook keeps its text in the study notes (so every note also appears in Estudos, linked to
 * its module and book) and only its looks here: colour, pin, folder, last opened, and a recycle bin
 * that keeps deleted notes until they are restored or emptied.
 */
final class NotepadStore extends SQLiteOpenHelper {
    static final int[] COLORS = {0, 0xfff6d365, 0xff9be3b1, 0xff8fd3f4, 0xfff7a8b8, 0xffc7b3f5, 0xffffb38a};
    static final String[] COLOR_NAMES = {"Sem cor", "Amarelo", "Verde", "Azul", "Rosa", "Lilás", "Laranja"};
    NotepadStore(Context c) { super(c.getApplicationContext(), "seeky-notepad.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE meta(note INTEGER PRIMARY KEY,color INTEGER NOT NULL DEFAULT 0,pinned INTEGER NOT NULL DEFAULT 0,folder TEXT NOT NULL DEFAULT '',opened INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE TABLE trash(id INTEGER PRIMARY KEY,data TEXT NOT NULL,meta TEXT NOT NULL DEFAULT '{}',deleted INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {}

    static final class Note {
        long id, subject, material, created, updated, opened; int segment, color; boolean pinned;
        String title = "", body = "", tags = "", folder = "", bookTitle = "", subjectTitle = "";
        String shownTitle() { return NotepadText.title(title, body); }
    }
    static final class Meta { int color; boolean pinned; String folder = ""; long opened; }

    Meta meta(long note) {
        Meta m = new Meta();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT color,pinned,folder,opened FROM meta WHERE note=?", new String[]{"" + note})) { if (c.moveToFirst()) { m.color = c.getInt(0); m.pinned = c.getInt(1) != 0; m.folder = c.getString(2); m.opened = c.getLong(3); } }
        return m;
    }
    void meta(long note, Meta m) {
        ContentValues v = new ContentValues(); v.put("note", note); v.put("color", m.color); v.put("pinned", m.pinned ? 1 : 0); v.put("folder", m.folder == null ? "" : m.folder.trim()); v.put("opened", m.opened);
        getWritableDatabase().insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    Map<Long, Meta> metas() {
        Map<Long, Meta> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT note,color,pinned,folder,opened FROM meta", null)) { while (c.moveToNext()) { Meta m = new Meta(); m.color = c.getInt(1); m.pinned = c.getInt(2) != 0; m.folder = c.getString(3); m.opened = c.getLong(4); out.put(c.getLong(0), m); } }
        return out;
    }
    List<String> folders() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT folder FROM meta WHERE folder<>'' ORDER BY folder COLLATE NOCASE", null)) { while (c.moveToNext()) out.add(c.getString(0)); }
        return out;
    }

    static Note note(StudyStore.Row r, Meta m) {
        Note n = new Note(); n.id = r.id(); n.subject = r.n("subject"); n.material = r.n("material"); n.segment = (int) r.n("segment"); n.title = r.s("title"); n.body = r.s("body"); n.tags = r.s("tags"); n.created = r.n("created"); n.updated = r.n("updated");
        if (m != null) { n.color = m.color; n.pinned = m.pinned; n.folder = m.folder; n.opened = m.opened; }
        return n;
    }
    /** Every note (newest first), with its looks, linked book and study module names. */
    static List<Note> all(Context c) {
        List<Note> out = new ArrayList<>();
        Map<Long, Meta> metas;
        try (NotepadStore pad = new NotepadStore(c)) { metas = pad.metas(); }
        try (StudyStore db = new StudyStore(c)) {
            Map<Long, String> books = new HashMap<>(), subjects = new HashMap<>();
            for (StudyStore.Row r : db.rows("notes", null, null, "updated DESC", 5000)) {
                Note n = note(r, metas.get(r.id()));
                if (n.material > 0) { if (!books.containsKey(n.material)) { StudyStore.Row b = db.get("materials", n.material); books.put(n.material, b == null ? "" : b.s("title")); } n.bookTitle = books.get(n.material); }
                if (n.subject > 0) { if (!subjects.containsKey(n.subject)) { StudyStore.Row s = db.get("subjects", n.subject); subjects.put(n.subject, s == null ? "" : s.s("title")); } n.subjectTitle = subjects.get(n.subject); }
                out.add(n);
            }
        }
        return out;
    }
    static Note load(Context c, long id) {
        try (StudyStore db = new StudyStore(c); NotepadStore pad = new NotepadStore(c)) {
            StudyStore.Row r = db.get("notes", id);
            if (r == null) return null;
            Note n = note(r, pad.meta(id));
            if (n.material > 0) { StudyStore.Row b = db.get("materials", n.material); n.bookTitle = b == null ? "" : b.s("title"); }
            if (n.subject > 0) { StudyStore.Row s = db.get("subjects", n.subject); n.subjectTitle = s == null ? "" : s.s("title"); }
            return n;
        }
    }
    /** Saves text and links in the study notes and the looks here; returns the note id. */
    static long save(Context c, Note n) {
        long now = System.currentTimeMillis();
        if (n.created == 0) n.created = now;
        n.updated = now;
        String title = n.title == null ? "" : n.title.trim();
        try (StudyStore db = new StudyStore(c)) {
            if (n.subject > 0 && db.get("subjects", n.subject) == null) n.subject = 0;
            if (n.material > 0 && db.get("materials", n.material) == null) n.material = 0;
            n.id = db.save("notes", n.id, StudyStore.values("subject", n.subject, "material", n.material, "segment", n.segment, "title", title.isEmpty() ? NotepadText.title("", n.body) : title, "body", n.body == null ? "" : n.body, "tags", n.tags == null ? "" : n.tags, "created", n.created, "updated", n.updated));
        }
        try (NotepadStore pad = new NotepadStore(c)) { Meta m = new Meta(); m.color = n.color; m.pinned = n.pinned; m.folder = n.folder; m.opened = now; pad.meta(n.id, m); }
        StudyEvents.changed();
        return n.id;
    }
    /** Moves a note to the recycle bin (removed from Estudos too). */
    static void trash(Context c, long id) throws Exception {
        try (StudyStore db = new StudyStore(c); NotepadStore pad = new NotepadStore(c)) {
            StudyStore.Row r = db.get("notes", id);
            if (r == null) return;
            JSONObject data = new JSONObject();
            for (String k : new String[]{"subject", "material", "segment", "created", "updated"}) data.put(k, r.n(k));
            for (String k : new String[]{"title", "body", "cues", "summary", "tags"}) data.put(k, r.s(k));
            Meta m = pad.meta(id);
            JSONObject meta = new JSONObject().put("color", m.color).put("pinned", m.pinned).put("folder", m.folder);
            ContentValues v = new ContentValues(); v.put("data", data.toString()); v.put("meta", meta.toString()); v.put("deleted", System.currentTimeMillis());
            pad.getWritableDatabase().insert("trash", null, v);
            pad.getWritableDatabase().delete("meta", "note=?", new String[]{"" + id});
            db.getWritableDatabase().delete("notes", "id=?", new String[]{"" + id});
        }
        StudyEvents.changed();
    }
    static final class Trashed { long id, deleted; String title = "", body = ""; }
    static List<Trashed> trashed(Context c) {
        List<Trashed> out = new ArrayList<>();
        try (NotepadStore pad = new NotepadStore(c); Cursor cur = pad.getReadableDatabase().rawQuery("SELECT id,data,deleted FROM trash ORDER BY deleted DESC LIMIT 500", null)) {
            while (cur.moveToNext()) { Trashed t = new Trashed(); t.id = cur.getLong(0); t.deleted = cur.getLong(2); try { JSONObject d = new JSONObject(cur.getString(1)); t.title = NotepadText.title(d.optString("title"), d.optString("body")); t.body = d.optString("body"); } catch (Exception ignored) {} out.add(t); }
        }
        return out;
    }
    /** Back from the bin (a new id in Estudos); returns the note id. */
    static long restore(Context c, long trashId) throws Exception {
        long id;
        try (NotepadStore pad = new NotepadStore(c)) {
            String data, meta;
            try (Cursor cur = pad.getReadableDatabase().rawQuery("SELECT data,meta FROM trash WHERE id=?", new String[]{"" + trashId})) { if (!cur.moveToFirst()) return 0; data = cur.getString(0); meta = cur.getString(1); }
            JSONObject d = new JSONObject(data), m = new JSONObject(meta);
            try (StudyStore db = new StudyStore(c)) {
                long subject = d.optLong("subject"), material = d.optLong("material");
                if (subject > 0 && db.get("subjects", subject) == null) subject = 0;
                if (material > 0 && db.get("materials", material) == null) material = 0;
                id = db.save("notes", 0, StudyStore.values("subject", subject, "material", material, "segment", d.optLong("segment"), "title", d.optString("title"), "body", d.optString("body"), "cues", d.optString("cues"), "summary", d.optString("summary"), "tags", d.optString("tags"), "created", d.optLong("created"), "updated", System.currentTimeMillis()));
            }
            Meta back = new Meta(); back.color = m.optInt("color"); back.pinned = m.optBoolean("pinned"); back.folder = m.optString("folder"); pad.meta(id, back);
            pad.getWritableDatabase().delete("trash", "id=?", new String[]{"" + trashId});
        }
        StudyEvents.changed();
        return id;
    }
    static void forget(Context c, long trashId) { try (NotepadStore pad = new NotepadStore(c)) { pad.getWritableDatabase().delete("trash", trashId > 0 ? "id=?" : null, trashId > 0 ? new String[]{"" + trashId} : null); } }
}
