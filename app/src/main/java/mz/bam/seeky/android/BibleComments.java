package mz.bam.seeky.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Comentários bíblicos MyBible (.commentaries.SQLite3) no telemóvel: carregados para uma base própria,
 * escolhe-se qual mostrar e, na leitura, os versículos comentados levam ✎ e o comentário aparece por
 * baixo do capítulo.
 */
final class BibleComments extends SQLiteOpenHelper {
    static final String PREFS = "bible_comments";
    BibleComments(Context c) { super(c.getApplicationContext(), "seeky-bible-comments.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE commentaries(id INTEGER PRIMARY KEY,title TEXT NOT NULL,created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE comments(commentary INTEGER NOT NULL,book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,chapter_to INTEGER NOT NULL,verse_to INTEGER NOT NULL,value TEXT NOT NULL)");
        d.execSQL("CREATE INDEX comments_at ON comments(commentary,book,chapter)");
    }
    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {}

    static final class Comment { String book = "", text = ""; int chapter, verse, chapterTo, verseTo;
        String where() { return verse == 0 ? book + " " + chapter + (chapterTo > chapter ? "–" + chapterTo : "") : book + " " + chapter + ":" + verse + (chapterTo > chapter ? "–" + chapterTo + ":" + verseTo : verseTo > verse ? "–" + verseTo : ""); }
        /** Shown at its first verse (verse 1 when it starts in an earlier chapter or covers the whole chapter). */
        int anchor(int shownChapter) { return shownChapter == chapter ? Math.max(1, verse) : 1; }
    }
    /** Loads a module; returns {id, comments}. Nothing is kept when it fails. */
    static long[] load(Context c, File module, String title, BooleanSupplier stop) throws Exception {
        try (BibleComments db = new BibleComments(c)) {
            SQLiteDatabase w = db.getWritableDatabase(); w.beginTransaction();
            try {
                ContentValues head = new ContentValues(); head.put("title", title); head.put("created", System.currentTimeMillis()); long id = w.insertOrThrow("commentaries", null, head);
                int n = BibleMyBible.comments(module, (b, ch, v, ch2, v2, text) -> { ContentValues r = new ContentValues(); r.put("commentary", id); r.put("book", b); r.put("chapter", ch); r.put("verse", v); r.put("chapter_to", ch2); r.put("verse_to", v2); r.put("value", text); w.insertOrThrow("comments", null, r); }, stop);
                w.setTransactionSuccessful();
                c.getSharedPreferences(PREFS, 0).edit().putLong("chosen", id).apply();
                return new long[]{id, n};
            } finally { w.endTransaction(); }
        }
    }
    /** {id, title} of every commentary, oldest first. */
    static List<String[]> all(Context c) {
        List<String[]> out = new ArrayList<>();
        try (BibleComments db = new BibleComments(c); Cursor r = db.getReadableDatabase().rawQuery("SELECT id,title FROM commentaries ORDER BY created", null)) { while (r.moveToNext()) out.add(new String[]{r.getString(0), r.getString(1)}); } catch (Exception ignored) {}
        return out;
    }
    static long chosen(Context c) {
        long id = c.getSharedPreferences(PREFS, 0).getLong("chosen", 0); List<String[]> all = all(c);
        for (String[] x : all) if (Long.parseLong(x[0]) == id) return id;
        return all.isEmpty() ? 0 : Long.parseLong(all.get(all.size() - 1)[0]);
    }
    static String title(Context c, long id) { for (String[] x : all(c)) if (Long.parseLong(x[0]) == id) return x[1]; return ""; }
    static void choose(Context c, long id) { c.getSharedPreferences(PREFS, 0).edit().putLong("chosen", id).apply(); }
    static void delete(Context c, long id) { try (BibleComments db = new BibleComments(c)) { SQLiteDatabase w = db.getWritableDatabase(); w.delete("comments", "commentary=?", new String[]{"" + id}); w.delete("commentaries", "id=?", new String[]{"" + id}); } catch (Exception ignored) {} }
    /** Comments of the chosen commentary that touch these chapters of a book. */
    static List<Comment> chapters(Context c, String book, int from, int to) {
        List<Comment> out = new ArrayList<>(); long id = chosen(c); if (id == 0 || !c.getSharedPreferences(PREFS, 0).getBoolean("show", true)) return out;
        try (BibleComments db = new BibleComments(c); Cursor r = db.getReadableDatabase().rawQuery("SELECT book,chapter,verse,chapter_to,verse_to,value FROM comments WHERE commentary=? AND book=? AND chapter<=? AND chapter_to>=? ORDER BY chapter,verse LIMIT 400", new String[]{"" + id, book, "" + to, "" + from})) {
            while (r.moveToNext()) { Comment m = new Comment(); m.book = r.getString(0); m.chapter = r.getInt(1); m.verse = r.getInt(2); m.chapterTo = r.getInt(3); m.verseTo = r.getInt(4); m.text = r.getString(5); out.add(m); }
        } catch (Exception ignored) {}
        return out;
    }
}
