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
}
