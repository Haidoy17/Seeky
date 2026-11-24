package mz.bam.seeky.android;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;

/**
 * O índice do Flash no computador: os nomes dos ficheiros das pastas do utilizador (Ambiente de
 * trabalho, Documentos, Transferências, Imagens, Música, Vídeos, OneDrive e as pastas escolhidas),
 * numa tabela de pesquisa por texto (FTS5) em %APPDATA%\Seeky\indice.db, para respostas imediatas.
 */
final class DeskFlashIndex {
    static final int MAX_FILES = 400000;
    static final Set<String> SKIP = new HashSet<>(Arrays.asList("appdata", "node_modules", ".git", "$recycle.bin", "system volume information", "windows", "program files", "program files (x86)", "programdata", "__pycache__", ".cache", ".gradle", ".m2"));
    static final String[] PDF = {"pdf"}, DOCS = {"doc", "docx", "odt", "rtf", "txt", "md", "epub", "mobi", "azw3", "fb2", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "cbz", "mht", "mhtml", "pages", "key", "numbers"},
        IMAGES = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tif", "tiff", "svg", "ico"}, VIDEOS = {"mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v", "flv", "3gp", "mpg", "mpeg", "ts"},
        AUDIO = {"mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "aif", "aiff", "amr"};
    static final String[] CATEGORIES = {"Tudo", "PDF", "Documentos", "Imagens", "Vídeos", "Áudio"};

    static String category(String name) {
        String e = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (Arrays.asList(PDF).contains(e)) return "PDF"; if (Arrays.asList(DOCS).contains(e)) return "Documentos";
        if (Arrays.asList(IMAGES).contains(e)) return "Imagens"; if (Arrays.asList(VIDEOS).contains(e)) return "Vídeos";
        if (Arrays.asList(AUDIO).contains(e)) return "Áudio"; return "Outros";
    }
    /** FTS5 prefix query: every word must start a word of the name ("rel fin" finds "Relatório_final.pdf"). */
    static String expression(String q) {
        StringBuilder b = new StringBuilder(); int n = 0;
        for (String t : Rules.normalize(q == null ? "" : q).split("[^\\p{L}\\p{N}]+")) { if (t.isEmpty()) continue; if (++n > 12) break; if (b.length() > 0) b.append(" AND "); b.append('"').append(t.replace("\"", "")).append("\" *"); }
        return b.toString();
    }
    static String words(String name) { return Rules.normalize(name.replaceFirst("\\.[^.]+$", "")).replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " " + Rules.normalize(name); }

    final File file;
    DeskFlashIndex(File file) { this.file = file; }
    static DeskFlashIndex standard() { return new DeskFlashIndex(new File(DeskStore.home(), "indice.db")); }
    Connection open() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE VIRTUAL TABLE IF NOT EXISTS files USING fts5(words, path UNINDEXED, name UNINDEXED, folder UNINDEXED, category UNINDEXED, size UNINDEXED, modified UNINDEXED, tokenize='unicode61 remove_diacritics 2')");
            s.execute("CREATE TABLE IF NOT EXISTS info(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
        return c;
    }

    static final class Hit { String path, name, folder, category; long size, modified; }
    static Hit hit(ResultSet r) throws SQLException { Hit h = new Hit(); h.path = r.getString(1); h.name = r.getString(2); h.folder = r.getString(3); h.category = r.getString(4); h.size = r.getLong(5); h.modified = r.getLong(6); return h; }

    /** Names that start with the typed words, best matches first; empty query: the files changed most recently. */
    List<Hit> search(String q, String category, int limit) throws SQLException {
        List<Hit> out = new ArrayList<>();
        String expr = expression(q); boolean all = category == null || category.equals("Tudo");
        try (Connection c = open()) {
            String sql = expr.isEmpty() ? "SELECT path,name,folder,category,size,modified FROM files" + (all ? "" : " WHERE category=?") + " ORDER BY CAST(modified AS INTEGER) DESC LIMIT ?"
                : "SELECT path,name,folder,category,size,modified FROM files WHERE files MATCH ?" + (all ? "" : " AND category=?") + " ORDER BY rank, CAST(modified AS INTEGER) DESC LIMIT ?";
            try (PreparedStatement p = c.prepareStatement(sql)) {
                int i = 1; if (!expr.isEmpty()) p.setString(i++, "words : (" + expr + ")"); if (!all) p.setString(i++, category); p.setInt(i, limit);
                try (ResultSet r = p.executeQuery()) { while (r.next()) out.add(hit(r)); }
            }
        }
        return out;
    }
    long count() { try (Connection c = open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM files")) { return r.next() ? r.getLong(1) : 0; } catch (SQLException e) { return 0; } }
    long built() { try (Connection c = open(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT value FROM info WHERE key='built'")) { return r.next() ? Long.parseLong(r.getString(1)) : 0; } catch (Exception e) { return 0; } }

    /** Reads every folder again and replaces the index in one transaction; returns how many files. */
    int rebuild(List<File> roots, java.util.function.BooleanSupplier stop) throws Exception {
        List<Object[]> rows = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            Path start = root.toPath();
            Files.walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), 32, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (stop.getAsBoolean() || rows.size() >= MAX_FILES) return FileVisitResult.TERMINATE;
                    String n = d.getFileName() == null ? "" : d.getFileName().toString();
                    if (!d.equals(start) && (n.startsWith(".") || n.startsWith("$") || SKIP.contains(n.toLowerCase(Locale.ROOT)))) return FileVisitResult.SKIP_SUBTREE;
                    try { if (Files.isHidden(d) && !d.equals(start)) return FileVisitResult.SKIP_SUBTREE; } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (!a.isRegularFile()) return FileVisitResult.CONTINUE;
                    String name = f.getFileName().toString(); if (name.startsWith("~$") || name.equalsIgnoreCase("desktop.ini") || name.equalsIgnoreCase("thumbs.db")) return FileVisitResult.CONTINUE;
                    String path = f.toAbsolutePath().toString(); if (!seen.add(path)) return FileVisitResult.CONTINUE;
                    rows.add(new Object[]{words(name), path, name, f.getParent() == null ? "" : f.getParent().toString(), category(name), a.size(), a.lastModifiedTime().toMillis()});
                    return rows.size() >= MAX_FILES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
            });
        }
        if (stop.getAsBoolean()) throw new java.io.InterruptedIOException("Índice cancelado.");
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement(); PreparedStatement p = c.prepareStatement("INSERT INTO files(words,path,name,folder,category,size,modified) VALUES(?,?,?,?,?,?,?)")) {
                s.execute("DELETE FROM files");
                int n = 0; for (Object[] r : rows) { for (int i = 0; i < r.length; i++) p.setObject(i + 1, r[i]); p.addBatch(); if (++n % 5000 == 0) p.executeBatch(); }
                p.executeBatch();
                try (PreparedStatement info = c.prepareStatement("INSERT OR REPLACE INTO info(key,value) VALUES('built',?)")) { info.setString(1, System.currentTimeMillis() + ""); info.executeUpdate(); }
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
        return rows.size();
    }
    /** The user's folders (the ones that exist) plus the music/video folders and the extra ones chosen. */
    static List<File> roots(DeskStore s) {
        LinkedHashSet<File> out = new LinkedHashSet<>(); File home = new File(System.getProperty("user.home"));
        for (String n : new String[]{"Desktop", "Documents", "Downloads", "Pictures", "Music", "Videos", "OneDrive", "Ambiente de trabalho", "Documentos", "Transferências", "Imagens", "Música", "Vídeos"}) { File f = new File(home, n); if (f.isDirectory()) out.add(f.getAbsoluteFile()); }
        for (String key : new String[]{"flash_folders", "folders_audio", "folders_video"}) for (String f : s.get(key, "").split("\n")) if (!f.trim().isEmpty() && new File(f.trim()).isDirectory()) out.add(new File(f.trim()).getAbsoluteFile());
        // A folder inside another one is read only once.
        List<File> list = new ArrayList<>(out); List<File> result = new ArrayList<>();
        for (File f : list) { boolean inside = false; for (File g : list) if (g != f && f.getPath().startsWith(g.getPath() + File.separator)) inside = true; if (!inside) result.add(f); }
        return result;
    }
}
