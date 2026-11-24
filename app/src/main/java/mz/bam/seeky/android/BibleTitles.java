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
 * Títulos de secção das Bíblias ("A criação", "O Sermão do Monte"…), lidos dos módulos MyBible
 * (tabelas stories ou subheadings) ao importar a Bíblia ou carregados depois de um ficheiro à parte.
 * Ficam numa base própria, por Bíblia importada, e aparecem antes do versículo na leitura.
 */
final class BibleTitles extends SQLiteOpenHelper {
    BibleTitles(Context c) { super(c.getApplicationContext(), "seeky-bible-titles.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase d) { d.execSQL("CREATE TABLE titles(source INTEGER NOT NULL,book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,ord INTEGER NOT NULL,value TEXT NOT NULL,PRIMARY KEY(source,book,chapter,verse,ord))"); }
    @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {}

    static ContentValues row(long source, String book, int chapter, int verse, int ord, String value) {
        ContentValues v = new ContentValues(); v.put("source", source); v.put("book", book); v.put("chapter", chapter); v.put("verse", verse); v.put("ord", ord); v.put("value", value); return v;
    }
    /** Copies the titles staged during an import (stage table "titles") to the published Bible. */
    static int publish(Context c, long source, SQLiteDatabase stage) {
        int n = 0;
        try (BibleTitles t = new BibleTitles(c); Cursor r = stage.rawQuery("SELECT book,chapter,verse,ord,value FROM titles ORDER BY book,chapter,verse,ord", null)) {
            SQLiteDatabase db = t.getWritableDatabase(); db.beginTransaction();
            try {
                db.delete("titles", "source=?", new String[]{"" + source});
                while (r.moveToNext()) { db.insertWithOnConflict("titles", null, row(source, r.getString(0), r.getInt(1), r.getInt(2), r.getInt(3), r.getString(4)), SQLiteDatabase.CONFLICT_REPLACE); n++; }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception ignored) {}
        return n;
    }
    /** "Carregar títulos": a MyBible module (the Bible with titles or its .subheadings file) for a Bible already imported. */
    static int load(Context c, long source, File module, BooleanSupplier stop) throws Exception {
        try (BibleTitles t = new BibleTitles(c)) {
            SQLiteDatabase db = t.getWritableDatabase(); db.beginTransaction();
            try {
                db.delete("titles", "source=?", new String[]{"" + source});
                int n = BibleMyBible.titles(module, (b, ch, v, o, text) -> db.insertWithOnConflict("titles", null, row(source, b, ch, v, o, text), SQLiteDatabase.CONFLICT_REPLACE), stop);
                if (n == 0) throw new java.io.IOException("Este módulo não tem títulos de secção (tabelas stories ou subheadings).");
                db.setTransactionSuccessful(); return n;
            } finally { db.endTransaction(); }
        }
    }
    /** Titles of some chapters of a book: "chapter:verse" → titles in order. */
    static Map<String, List<String>> chapters(Context c, long source, String book, int from, int to) {
        Map<String, List<String>> out = new HashMap<>();
        try (BibleTitles t = new BibleTitles(c); Cursor r = t.getReadableDatabase().rawQuery("SELECT chapter,verse,value FROM titles WHERE source=? AND book=? AND chapter BETWEEN ? AND ? ORDER BY chapter,verse,ord", new String[]{"" + source, book, "" + from, "" + to})) {
            while (r.moveToNext()) out.computeIfAbsent(r.getInt(0) + ":" + r.getInt(1), k -> new ArrayList<>()).add(r.getString(2));
        } catch (Exception ignored) {}
        return out;
    }
    static long count(Context c, long source) { try (BibleTitles t = new BibleTitles(c)) { return android.database.DatabaseUtils.longForQuery(t.getReadableDatabase(), "SELECT COUNT(*) FROM titles WHERE source=?", new String[]{"" + source}); } catch (Exception e) { return 0; } }
    static void delete(Context c, long source) { try (BibleTitles t = new BibleTitles(c)) { t.getWritableDatabase().delete("titles", "source=?", new String[]{"" + source}); } catch (Exception ignored) {} }
}
