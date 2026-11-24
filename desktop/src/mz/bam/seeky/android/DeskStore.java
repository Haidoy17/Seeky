package mz.bam.seeky.android;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Seeky Desktop's own data (Windows: %APPDATA%\Seeky\seeky.db): Bibles and verses, notes, books
 * and reading progress, Bible study (chapters read, memory cards, game records) and settings.
 * One SQLite file, the same kind of storage as on Android.
 */
final class DeskStore implements AutoCloseable {
    static File home() {
        String appData = System.getenv("APPDATA");
        File dir = appData != null && !appData.isEmpty() ? new File(appData, "Seeky") : new File(System.getProperty("user.home"), ".seeky-desktop");
        String override = System.getProperty("seeky.home");
        if (override != null && !override.isEmpty()) dir = new File(override);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }
    static File folder(String name) { File f = new File(home(), name); if (!f.isDirectory()) f.mkdirs(); return f; }

    final Connection db;
    DeskStore() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) { throw new SQLException("SQLite em falta.", e); }
        db = DriverManager.getConnection("jdbc:sqlite:" + new File(home(), "seeky.db").getPath());
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS bibles(id INTEGER PRIMARY KEY,title TEXT NOT NULL,source TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS verses(bible INTEGER NOT NULL,book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,text TEXT NOT NULL,PRIMARY KEY(bible,book,chapter,verse))");
            s.execute("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY,title TEXT NOT NULL DEFAULT '',body TEXT NOT NULL DEFAULT '',folder TEXT NOT NULL DEFAULT '',pinned INTEGER NOT NULL DEFAULT 0,book TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL,updated INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS books(path TEXT PRIMARY KEY,title TEXT NOT NULL,kind TEXT NOT NULL,position INTEGER NOT NULL DEFAULT 0,total INTEGER NOT NULL DEFAULT 0,opened INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS readings(day TEXT NOT NULL,book TEXT NOT NULL,chapter INTEGER NOT NULL,PRIMARY KEY(book,chapter))");
            s.execute("CREATE TABLE IF NOT EXISTS cards(id INTEGER PRIMARY KEY,reference TEXT NOT NULL,text TEXT NOT NULL,interval INTEGER NOT NULL DEFAULT 0,ease INTEGER NOT NULL DEFAULT 250,due INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS records(level INTEGER NOT NULL,name TEXT NOT NULL,points INTEGER NOT NULL,correct INTEGER NOT NULL,played INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        }
    }
    @Override public void close() { try { db.close(); } catch (SQLException ignored) {} }

    // ---- settings ----
    String get(String key, String fallback) { try (PreparedStatement p = db.prepareStatement("SELECT value FROM settings WHERE key=?")) { p.setString(1, key); try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : fallback; } } catch (SQLException e) { return fallback; } }
    void put(String key, String value) { try (PreparedStatement p = db.prepareStatement("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)")) { p.setString(1, key); p.setString(2, value == null ? "" : value); p.executeUpdate(); } catch (SQLException ignored) {} }

    // ---- small query helpers ----
    List<Object[]> rows(String sql, Object... args) throws SQLException {
        try (PreparedStatement p = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            try (ResultSet r = p.executeQuery()) {
                int n = r.getMetaData().getColumnCount(); List<Object[]> out = new ArrayList<>();
                while (r.next()) { Object[] row = new Object[n]; for (int i = 0; i < n; i++) row[i] = r.getObject(i + 1); out.add(row); }
                return out;
            }
        }
    }
    long update(String sql, Object... args) throws SQLException {
        try (PreparedStatement p = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            p.executeUpdate();
            try (ResultSet k = p.getGeneratedKeys()) { return k.next() ? k.getLong(1) : 0; }
        }
    }
    long count(String sql, Object... args) throws SQLException { List<Object[]> r = rows(sql, args); return r.isEmpty() || r.get(0)[0] == null ? 0 : ((Number) r.get(0)[0]).longValue(); }

    // ---- Bibles ----
    long bible() { try { long chosen = Long.parseLong(get("bible", "0")); if (chosen > 0 && count("SELECT COUNT(*) FROM bibles WHERE id=?", chosen) > 0) return chosen; List<Object[]> r = rows("SELECT id FROM bibles ORDER BY created LIMIT 1"); return r.isEmpty() ? 0 : ((Number) r.get(0)[0]).longValue(); } catch (Exception e) { return 0; } }
    String verse(long bible, String book, int chapter, int verse) {
        try { List<Object[]> r = rows("SELECT text FROM verses WHERE bible=? AND book=? AND chapter=? AND verse=?", bible, book, chapter, verse); return r.isEmpty() ? "" : (String) r.get(0)[0]; } catch (SQLException e) { return ""; }
    }
    /** {verse, text} of a chapter. */
    List<Object[]> chapter(long bible, String book, int chapter) throws SQLException { return rows("SELECT verse,text FROM verses WHERE bible=? AND book=? AND chapter=? ORDER BY verse", bible, book, chapter); }
    /** The text of a reference ("João 3:16-18"), verse numbers in brackets when there are several. */
    String passage(long bible, BibleReferences.Reference ref) throws SQLException {
        StringBuilder out = new StringBuilder();
        for (BibleReferences.Part part : ref.parts) {
            List<Object[]> verses = chapter(bible, ref.book, part.chapter);
            for (Object[] v : verses) {
                int n = ((Number) v[0]).intValue(); boolean in = part.whole || part.verses.isEmpty();
                for (int[] range : part.verses) if (n >= range[0] && (n <= range[1] || range.length > 2 && range[2] == 1)) in = true;
                if (in) { if (out.length() > 0) out.append(' '); out.append('[').append(n).append("] ").append(v[1]); }
            }
        }
        return out.toString();
    }
}
