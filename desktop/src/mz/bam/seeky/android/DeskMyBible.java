package mz.bam.seeky.android;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;

/** MyBible modules (.SQLite3) on the computer: the same reading as on Android, through JDBC. No imported SQL is run. */
final class DeskMyBible {
    private DeskMyBible() {}
    static final String[] CODES = {"GEN","EXO","LEV","NUM","DEU","JOS","JDG","RUT","1SA","2SA","1KI","2KI","1CH","2CH","EZR","NEH","TOB","JDT","EST","1MA","2MA","JOB","PSA","PRO","ECC","SNG","WIS","SIR","ISA","JER","LAM","BAR","EZK","DAN","HOS","JOL","AMO","OBA","JON","MIC","NAM","HAB","ZEP","HAG","ZEC","MAL","MAT","MRK","LUK","JHN","ACT","ROM","1CO","2CO","GAL","EPH","PHP","COL","1TH","2TH","1TI","2TI","TIT","PHM","HEB","JAS","1PE","2PE","1JN","2JN","3JN","JUD","REV"};
    static String standard(int number) { return number % 10 == 0 && number >= 10 && number <= CODES.length * 10 ? BibleText.code(CODES[number / 10 - 1]) : ""; }
    static String clean(String raw) throws IOException {
        if (raw == null || raw.length() > 65536) throw new IOException("Versículo MyBible demasiado longo.");
        String value = raw.replaceAll("(?is)<(S|f|n|script|style)\\b[^>]*>.*?</\\1\\s*>", "").replaceAll("(?is)<(?:br|pb)\\s*/?>", " ").replaceAll("(?s)<[^>]*>", "");
        Matcher m = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z]+);").matcher(value); StringBuffer out = new StringBuffer();
        String[] keys = {"amp","lt","gt","quot","apos","nbsp","hellip","ndash","mdash","lsquo","rsquo","ldquo","rdquo","laquo","raquo"}, texts = {"&","<",">","\"","'"," ","…","–","—","‘","’","“","”","«","»"};
        while (m.find()) {
            String code = m.group(1), rep = null;
            try { if (code.startsWith("#")) { int n = Integer.parseInt(code.substring(code.startsWith("#x") ? 2 : 1), code.startsWith("#x") ? 16 : 10); if (Character.isValidCodePoint(n) && n > 0 && (n < 0xd800 || n > 0xdfff)) rep = new String(Character.toChars(n)); } } catch (Exception ignored) {}
            if (rep == null) for (int i = 0; i < keys.length; i++) if (keys[i].equals(code)) rep = texts[i];
            m.appendReplacement(out, Matcher.quoteReplacement(rep == null ? m.group() : rep));
        }
        m.appendTail(out);
        return out.toString().replaceAll("[\\s\\p{Zs}]+", " ").trim();
    }
    static boolean table(Connection db, String name) throws SQLException { try (PreparedStatement p = db.prepareStatement("SELECT sql FROM sqlite_master WHERE type='table' AND name=?")) { p.setString(1, name); try (ResultSet r = p.executeQuery()) { return r.next() && r.getString(1) != null && !r.getString(1).toUpperCase(Locale.ROOT).contains("VIRTUAL TABLE"); } } }
    static Set<String> columns(Connection db, String table) throws SQLException { Set<String> out = new HashSet<>(); try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(\"" + table + "\")")) { while (r.next()) out.add(r.getString("name").toLowerCase(Locale.ROOT)); } return out; }
    static int read(File file, BibleText.Sink sink, BooleanSupplier stop) throws Exception {
        org.sqlite.SQLiteConfig cfg = new org.sqlite.SQLiteConfig(); cfg.setReadOnly(true);
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + file.getPath(), cfg.toProperties())) {
            if (!table(db, "verses") || !columns(db, "verses").containsAll(Arrays.asList("book_number", "chapter", "verse", "text"))) throw new IOException("Este módulo não contém a tabela de versículos MyBible. Comentários, dicionários e planos não são Bíblias de texto.");
            Map<Integer, String> names = new HashMap<>();
            if (table(db, "books") && columns(db, "books").contains("book_number")) {
                Set<String> f = columns(db, "books"); String lng = f.contains("long_name") ? "long_name" : "''", sht = f.contains("short_name") ? "short_name" : "''";
                try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT book_number," + lng + "," + sht + " FROM books")) {
                    while (r.next()) { String n = BibleText.code(clean(r.getString(2) == null ? "" : r.getString(2))); if (n.isEmpty()) n = BibleText.code(clean(r.getString(3) == null ? "" : r.getString(3))); if (!n.isEmpty()) names.put(r.getInt(1), n); }
                }
            }
            int count = 0;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT book_number,chapter,verse,text FROM verses ORDER BY book_number,chapter,verse")) {
                while (r.next()) {
                    if (stop.getAsBoolean()) throw new InterruptedIOException("Importação cancelada.");
                    int number = r.getInt(1), chapter = r.getInt(2), verse = r.getInt(3); if (chapter == 0 || verse == 0) continue;
                    String book = names.getOrDefault(number, standard(number));
                    if (book.isEmpty()) throw new IOException("Livro MyBible desconhecido: " + number + ".");
                    Object text = r.getObject(4); if (text != null && !(text instanceof String)) throw new IOException("O módulo usa versículos comprimidos ou num formato não suportado.");
                    if (count >= 200000) throw new IOException("A Bíblia excede 200000 versículos.");
                    if (BibleText.emit(sink, book, chapter, verse, clean((String) text))) count++;
                }
            }
            if (count == 0) throw new IOException("O módulo MyBible não contém versículos de texto.");
            return count;
        }
    }

    // ---- Títulos (stories/subheadings) e comentários dos módulos MyBible ----
    interface Titles { void title(String book, int chapter, int verse, int order, String text) throws Exception; }
    interface Comments { void comment(String book, int chapter, int verse, int chapterTo, int verseTo, String text) throws Exception; }
    static Connection open(File file) throws SQLException { org.sqlite.SQLiteConfig cfg = new org.sqlite.SQLiteConfig(); cfg.setReadOnly(true); return DriverManager.getConnection("jdbc:sqlite:" + file.getPath(), cfg.toProperties()); }
    static Map<Integer, String> names(Connection db) throws Exception {
        Map<Integer, String> names = new HashMap<>();
        if (table(db, "books") && columns(db, "books").contains("book_number")) {
            Set<String> f = columns(db, "books"); String lng = f.contains("long_name") ? "long_name" : "''", sht = f.contains("short_name") ? "short_name" : "''";
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT book_number," + lng + "," + sht + " FROM books")) {
                while (r.next()) { String n = BibleText.code(clean(r.getString(2) == null ? "" : r.getString(2))); if (n.isEmpty()) n = BibleText.code(clean(r.getString(3) == null ? "" : r.getString(3))); if (!n.isEmpty()) names.put(r.getInt(1), n); }
            }
        }
        return names;
    }
    /** Section titles of a module: "stories" (in Bibles), "subheadings" (.subheadings.SQLite3) or "headings". Returns how many. */
    static int titles(File file, Titles sink, BooleanSupplier stop) throws Exception {
        try (Connection db = open(file)) {
            Map<Integer, String> names = names(db);
            String[][] kinds = {{"stories", "title"}, {"subheadings", "subheading"}, {"headings", "heading"}, {"subheadings", "title"}};
            for (String[] k : kinds) {
                if (!table(db, k[0])) continue; Set<String> cols = columns(db, k[0]);
                if (!cols.containsAll(Arrays.asList("book_number", "chapter", "verse", k[1]))) continue;
                String order = cols.contains("order_if_several") ? "order_if_several" : "0";
                int count = 0;
                try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT book_number,chapter,verse," + order + "," + k[1] + " FROM " + k[0] + " ORDER BY book_number,chapter,verse," + order)) {
                    while (r.next()) {
                        if (stop.getAsBoolean()) throw new InterruptedIOException("Importação cancelada.");
                        String book = names.getOrDefault(r.getInt(1), standard(r.getInt(1))); if (book.isEmpty()) continue;
                        Object raw = r.getObject(5); if (!(raw instanceof String)) continue;
                        String text = clean((String) raw); if (text.isEmpty() || text.length() > 400) continue;
                        if (++count > 100000) throw new IOException("Demasiados títulos no módulo.");
                        sink.title(book, Math.max(1, r.getInt(2)), Math.max(1, r.getInt(3)), r.getInt(4), text);
                    }
                }
                if (count > 0) return count;
            }
            return 0;
        }
    }
    /** A MyBible commentary module (.commentaries.SQLite3). Returns how many comments. */
    static int comments(File file, Comments sink, BooleanSupplier stop) throws Exception {
        try (Connection db = open(file)) {
            if (!table(db, "commentaries")) throw new IOException("Este ficheiro não é um módulo de comentários MyBible (.commentaries.SQLite3).");
            Set<String> cols = columns(db, "commentaries");
            if (!cols.containsAll(Arrays.asList("book_number", "chapter_number_from", "verse_number_from", "text"))) throw new IOException("O módulo de comentários não tem as colunas esperadas.");
            String ct = cols.contains("chapter_number_to") ? "chapter_number_to" : "chapter_number_from", vt = cols.contains("verse_number_to") ? "verse_number_to" : "verse_number_from";
            Map<Integer, String> names = names(db); int count = 0;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT book_number,chapter_number_from,verse_number_from," + ct + "," + vt + ",text FROM commentaries ORDER BY book_number,chapter_number_from,verse_number_from")) {
                while (r.next()) {
                    if (stop.getAsBoolean()) throw new InterruptedIOException("Importação cancelada.");
                    String book = names.getOrDefault(r.getInt(1), standard(r.getInt(1))); if (book.isEmpty()) continue;
                    Object raw = r.getObject(6); if (!(raw instanceof String)) continue;
                    String text = commentText((String) raw); if (text.isEmpty()) continue;
                    int c = Math.max(1, r.getInt(2)), v = Math.max(0, r.getInt(3)), c2 = Math.max(c, r.getInt(4)), v2 = r.getInt(5); if (c2 == c && v2 < v) v2 = v;
                    if (++count > 300000) throw new IOException("Demasiados comentários no módulo.");
                    sink.comment(book, c, v, c2, v2, text);
                }
            }
            if (count == 0) throw new IOException("O módulo não tem comentários de texto.");
            return count;
        }
    }
    /** The description of a module ("info" table), used as the commentary's name. */
    static String description(File file) {
        try (Connection db = open(file)) {
            if (table(db, "info")) try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM info WHERE name='description'")) { if (r.next() && r.getString(1) != null) return clean(r.getString(1)); }
        } catch (Exception ignored) {}
        return file.getName().replaceFirst("(?i)(\\.commentaries)?\\.sqlite3?$", "");
    }
    /** Commentary HTML as readable text: paragraphs and line breaks kept, tags removed. */
    static String commentText(String html) throws IOException {
        if (html.length() > 400000) throw new IOException("Comentário demasiado longo.");
        String t = html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", "").replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("(?i)</p\\s*>|<p\\b[^>]*>", "\n\n");
        StringBuilder out = new StringBuilder();
        for (String para : t.split("\n")) { String c = clean(para); if (!c.isEmpty()) { if (out.length() > 0) out.append('\n'); out.append(c); } }
        return out.toString();
    }
    /** Companion modules beside a MyBible Bible: "X.subheadings.SQLite3" and "X.commentaries.SQLite3". */
    static File companion(File bible, String kind) {
        String base = bible.getName().replaceFirst("(?i)\\.sqlite3?$", "");
        File dir = bible.getAbsoluteFile().getParentFile(); if (dir == null) return null;
        for (String ext : new String[]{".SQLite3", ".sqlite3", ".SQLite", ".sqlite"}) { File f = new File(dir, base + "." + kind + ext); if (f.isFile()) return f; }
        return null;
    }
}
