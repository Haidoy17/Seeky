package mz.bam.seeky.android;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Biblioteca de música e vídeo: procura automática no telefone (MediaStore), pastas escolhidas
 * pelo utilizador (incluindo cartão SD e pastas que o Android não indexa) e ficheiros adicionados
 * à mão. Organiza por faixas, artistas, álbuns, géneros, anos, pastas, favoritas, recentes e
 * mais ouvidas.
 */
final class MediaLibrary extends SQLiteOpenHelper {
    static final String AUDIO = "audio", VIDEO = "video";
    static final int MAX_FOLDER_FILES = 5000;

    MediaLibrary(Context c) { super(c.getApplicationContext(), "media_library.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE tracks(id INTEGER PRIMARY KEY,uri TEXT UNIQUE NOT NULL,kind TEXT NOT NULL,title TEXT NOT NULL DEFAULT '',artist TEXT NOT NULL DEFAULT '',album TEXT NOT NULL DEFAULT '',"
                + "genre TEXT NOT NULL DEFAULT '',year INTEGER NOT NULL DEFAULT 0,track INTEGER NOT NULL DEFAULT 0,duration INTEGER NOT NULL DEFAULT 0,folder TEXT NOT NULL DEFAULT '',path TEXT NOT NULL DEFAULT '',"
                + "size INTEGER NOT NULL DEFAULT 0,added INTEGER NOT NULL DEFAULT 0,modified INTEGER NOT NULL DEFAULT 0,source TEXT NOT NULL DEFAULT 'store',origin TEXT NOT NULL DEFAULT '',plays INTEGER NOT NULL DEFAULT 0,"
                + "last_played INTEGER NOT NULL DEFAULT 0,favorite INTEGER NOT NULL DEFAULT 0,width INTEGER NOT NULL DEFAULT 0,height INTEGER NOT NULL DEFAULT 0,seen INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE INDEX tracks_kind ON tracks(kind,title)");
        d.execSQL("CREATE INDEX tracks_artist ON tracks(kind,artist)");
        d.execSQL("CREATE INDEX tracks_album ON tracks(kind,album)");
        d.execSQL("CREATE INDEX tracks_folder ON tracks(kind,folder)");
        d.execSQL("CREATE TABLE folders(uri TEXT PRIMARY KEY,kind TEXT NOT NULL,label TEXT NOT NULL,added INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase d, int a, int b) {}

    static final class Track {
        long id, duration, size, added, modified, lastPlayed;
        String uri = "", kind = AUDIO, title = "", artist = "", album = "", genre = "", folder = "", path = "", source = "store", origin = "";
        int year, track, plays, width, height;
        boolean favorite;

        String subtitle() {
            String a = artist.isEmpty() ? "" : artist, b = album.isEmpty() ? "" : album;
            return a.isEmpty() ? b : b.isEmpty() ? a : a + " · " + b;
        }

        MediaQueue.Item item() { return new MediaQueue.Item(uri, title, artist, album, duration); }
    }

    static final class Group {
        String name = "";
        int count, albums;
        long duration;
        String sample = "";
    }

    static Track track(Cursor c) {
        Track t = new Track();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.uri = c.getString(c.getColumnIndexOrThrow("uri"));
        t.kind = c.getString(c.getColumnIndexOrThrow("kind"));
        t.title = c.getString(c.getColumnIndexOrThrow("title"));
        t.artist = c.getString(c.getColumnIndexOrThrow("artist"));
        t.album = c.getString(c.getColumnIndexOrThrow("album"));
        t.genre = c.getString(c.getColumnIndexOrThrow("genre"));
        t.year = c.getInt(c.getColumnIndexOrThrow("year"));
        t.track = c.getInt(c.getColumnIndexOrThrow("track"));
        t.duration = c.getLong(c.getColumnIndexOrThrow("duration"));
        t.folder = c.getString(c.getColumnIndexOrThrow("folder"));
        t.path = c.getString(c.getColumnIndexOrThrow("path"));
        t.size = c.getLong(c.getColumnIndexOrThrow("size"));
        t.added = c.getLong(c.getColumnIndexOrThrow("added"));
        t.modified = c.getLong(c.getColumnIndexOrThrow("modified"));
        t.source = c.getString(c.getColumnIndexOrThrow("source"));
        t.origin = c.getString(c.getColumnIndexOrThrow("origin"));
        t.plays = c.getInt(c.getColumnIndexOrThrow("plays"));
        t.lastPlayed = c.getLong(c.getColumnIndexOrThrow("last_played"));
        t.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) != 0;
        t.width = c.getInt(c.getColumnIndexOrThrow("width"));
        t.height = c.getInt(c.getColumnIndexOrThrow("height"));
        return t;
    }

    static ContentValues values(Track t, int seen) {
        ContentValues v = new ContentValues();
        v.put("uri", t.uri); v.put("kind", t.kind); v.put("title", t.title); v.put("artist", t.artist); v.put("album", t.album); v.put("genre", t.genre);
        v.put("year", t.year); v.put("track", t.track); v.put("duration", t.duration); v.put("folder", t.folder); v.put("path", t.path); v.put("size", t.size);
        v.put("added", t.added); v.put("modified", t.modified); v.put("source", t.source); v.put("origin", t.origin); v.put("width", t.width); v.put("height", t.height); v.put("seen", seen);
        return v;
    }

    /** Insere ou actualiza mantendo contagens de reprodução e favoritas. */
    void upsert(SQLiteDatabase d, Track t, int seen) {
        ContentValues v = values(t, seen);
        if (d.update("tracks", v, "uri=?", new String[]{t.uri}) == 0) d.insert("tracks", null, v);
    }

    static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.equalsIgnoreCase("<unknown>") || t.equalsIgnoreCase("unknown") ? "" : t;
    }

    static String stripExt(String name) { return name == null ? "" : name.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "").replace('_', ' ').trim(); }

    static String col(Cursor c, String name) { int i = c.getColumnIndex(name); return i < 0 || c.isNull(i) ? "" : c.getString(i); }
    static long num(Cursor c, String name) { int i = c.getColumnIndex(name); return i < 0 || c.isNull(i) ? 0 : c.getLong(i); }

    static String parentName(String path) {
        if (path == null || path.isEmpty()) return "";
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = p.lastIndexOf('/');
        String parent = slash > 0 ? p.substring(0, slash) : "";
        int s2 = parent.lastIndexOf('/');
        return s2 >= 0 ? parent.substring(s2 + 1) : parent;
    }

    // ---- procura automática (MediaStore) ----

    /** Lê a MediaStore do Android; remove o que deixou de existir. Devolve o número de ficheiros. */
    int scanStore(Context c, String kind) {
        Uri base = kind.equals(VIDEO) ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = kind.equals(VIDEO) ? null : MediaStore.Audio.Media.IS_MUSIC + "!=0";
        SQLiteDatabase d = getWritableDatabase();
        int found = 0, seen = (int) (System.currentTimeMillis() / 1000 % 1000000000);
        d.beginTransaction();
        try (Cursor cur = c.getContentResolver().query(base, null, selection, null, null)) {
            if (cur == null) { d.setTransactionSuccessful(); return 0; }
            while (cur.moveToNext()) {
                Track t = new Track();
                t.kind = kind;
                t.uri = ContentUris.withAppendedId(base, num(cur, MediaStore.MediaColumns._ID)).toString();
                t.path = col(cur, MediaStore.MediaColumns.DATA);
                String display = col(cur, MediaStore.MediaColumns.DISPLAY_NAME);
                t.title = clean(col(cur, MediaStore.MediaColumns.TITLE));
                if (t.title.isEmpty()) t.title = stripExt(display);
                t.artist = clean(col(cur, "artist"));
                t.album = clean(col(cur, "album"));
                t.genre = clean(col(cur, "genre"));
                t.year = (int) num(cur, "year");
                t.track = (int) (num(cur, "track") % 1000);
                t.duration = num(cur, "duration");
                t.size = num(cur, MediaStore.MediaColumns.SIZE);
                t.added = num(cur, MediaStore.MediaColumns.DATE_ADDED) * 1000;
                t.modified = num(cur, MediaStore.MediaColumns.DATE_MODIFIED) * 1000;
                t.width = (int) num(cur, "width");
                t.height = (int) num(cur, "height");
                String bucket = col(cur, "bucket_display_name");
                t.folder = !bucket.isEmpty() ? bucket : parentName(t.path);
                if (t.year == 0 && kind.equals(VIDEO)) {
                    long taken = num(cur, "datetaken");
                    long when = taken > 0 ? taken : t.added;
                    if (when > 0) { Calendar cal = Calendar.getInstance(); cal.setTimeInMillis(when); t.year = cal.get(Calendar.YEAR); }
                }
                t.source = "store";
                upsert(d, t, seen);
                found++;
            }
            d.delete("tracks", "kind=? AND source='store' AND seen!=?", new String[]{kind, String.valueOf(seen)});
            d.setTransactionSuccessful();
        } catch (SecurityException e) {
            throw e;
        } finally { d.endTransaction(); }
        LibraryPrefs.set(c, "scan_" + kind, System.currentTimeMillis());
        return found;
    }

    // ---- pastas e ficheiros escolhidos ----

    /** Lê título, artista, álbum, género, ano, faixa, duração e tamanho do vídeo. */
    static Track probe(Context c, Uri uri, String name, String kind) {
        Track t = new Track();
        t.uri = uri.toString();
        t.kind = kind;
        t.title = stripExt(name);
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            String title = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            if (!title.isEmpty()) t.title = title;
            t.artist = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            if (t.artist.isEmpty()) t.artist = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
            t.album = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            t.genre = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
            t.year = year(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR), r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
            t.track = leadingInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
            t.duration = leadingInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            t.width = leadingInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            t.height = leadingInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (Exception ignored) {
        } finally { try { r.release(); } catch (Exception ignored) {} }
        return t;
    }

    static int leadingInt(String s) {
        if (s == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        try { return m.find() ? Integer.parseInt(m.group()) : 0; } catch (NumberFormatException e) { return 0; }
    }

    static int year(String year, String date) {
        int y = leadingInt(year);
        if (y >= 1000 && y <= 9999) return y;
        y = date == null || date.length() < 4 ? 0 : leadingInt(date.substring(0, 4));
        return y >= 1000 && y <= 9999 ? y : 0;
    }

    /** Ficheiros escolhidos à mão (ACTION_OPEN_DOCUMENT). */
    int addFiles(Context c, List<Uri> uris, String kind) {
        SQLiteDatabase d = getWritableDatabase();
        int added = 0;
        for (Uri u : uris) {
            try { c.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String name = PdfTools.baseName(c, u) + PrivateFolder.extension(c, u);
            Track t = probe(c, u, name, kind);
            t.source = "manual";
            t.folder = AppLanguage.text(c, "Adicionados");
            t.added = System.currentTimeMillis();
            upsert(d, t, 0);
            added++;
        }
        return added;
    }

    static boolean matches(String name, String mime, String kind) {
        String k = MediaFormats.kind(name, mime == null ? "" : mime);
        return kind.equals(VIDEO) ? k.equals("video") : k.equals("audio");
    }

    /** Pasta escolhida (árvore SAF): percorre subpastas e junta os ficheiros do tipo pedido. */
    int addFolder(Context c, Uri tree, String kind, BooleanSupplier cancelled) {
        try { c.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        String label = rootId.contains(":") ? rootId.substring(rootId.indexOf(':') + 1) : rootId;
        if (label.isEmpty()) label = AppLanguage.text(c, "Pasta");
        ContentValues f = new ContentValues();
        f.put("uri", tree.toString()); f.put("kind", kind); f.put("label", label); f.put("added", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("folders", null, f, SQLiteDatabase.CONFLICT_REPLACE);
        Set<String> known = new HashSet<>();
        try (Cursor k = getReadableDatabase().rawQuery("SELECT uri,modified FROM tracks WHERE origin=?", new String[]{tree.toString()})) {
            while (k.moveToNext()) known.add(k.getString(0) + "|" + k.getLong(1));
        }
        int count = 0;
        Deque<String[]> stack = new ArrayDeque<>();
        stack.push(new String[]{rootId, label});
        int seen = (int) (System.currentTimeMillis() / 1000 % 1000000000);
        SQLiteDatabase d = getWritableDatabase();
        while (!stack.isEmpty() && count < MAX_FOLDER_FILES && !cancelled.getAsBoolean()) {
            String[] dir = stack.pop();
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir[0]);
            try (Cursor cur = c.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
                if (cur == null) continue;
                while (cur.moveToNext() && count < MAX_FOLDER_FILES) {
                    String id = cur.getString(0), name = cur.getString(1), mime = cur.getString(2);
                    if (name == null || name.startsWith(".")) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { stack.push(new String[]{id, name}); continue; }
                    if (!matches(name, mime, kind)) continue;
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    long modified = cur.isNull(4) ? 0 : cur.getLong(4);
                    count++;
                    if (known.contains(doc + "|" + modified)) { ContentValues v = new ContentValues(); v.put("seen", seen); d.update("tracks", v, "uri=?", new String[]{doc.toString()}); continue; }
                    Track t = probe(c, doc, name, kind);
                    t.source = "folder";
                    t.origin = tree.toString();
                    t.folder = dir[1];
                    t.size = cur.isNull(3) ? 0 : cur.getLong(3);
                    t.modified = modified;
                    t.added = System.currentTimeMillis();
                    upsert(d, t, seen);
                }
            } catch (Exception e) { AppDiagnostics.record(c, "media-folder", e); }
        }
        if (!cancelled.getAsBoolean()) d.delete("tracks", "origin=? AND seen!=?", new String[]{tree.toString(), String.valueOf(seen)});
        return count;
    }

    void removeFolder(String tree) {
        getWritableDatabase().delete("tracks", "origin=?", new String[]{tree});
        getWritableDatabase().delete("folders", "uri=?", new String[]{tree});
    }

    List<String[]> folders(String kind) {
        List<String[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT uri,label FROM folders WHERE kind=? ORDER BY label COLLATE NOCASE", new String[]{kind})) {
            while (c.moveToNext()) out.add(new String[]{c.getString(0), c.getString(1)});
        }
        return out;
    }

    // ---- consultas ----

    List<Track> tracks(String kind, String where, String[] args, String order, int limit) {
        List<Track> out = new ArrayList<>();
        String w = "kind=?" + (where == null || where.isEmpty() ? "" : " AND (" + where + ")");
        String[] a = new String[(args == null ? 0 : args.length) + 1];
        a[0] = kind;
        if (args != null) System.arraycopy(args, 0, a, 1, args.length);
        try (Cursor c = getReadableDatabase().query("tracks", null, w, a, null, null, order, limit > 0 ? String.valueOf(limit) : null)) {
            while (c.moveToNext()) out.add(track(c));
        }
        return out;
    }

    static String search(String q) { return "title LIKE ? OR artist LIKE ? OR album LIKE ? OR genre LIKE ? OR folder LIKE ?"; }
    static String[] searchArgs(String q) { String p = "%" + q.trim() + "%"; return new String[]{p, p, p, p, p}; }

    /** Agrupa por coluna (artist, album, genre, year, folder). */
    List<Group> groups(String kind, String column, String q) {
        List<Group> out = new ArrayList<>();
        if (!Arrays.asList("artist", "album", "genre", "year", "folder").contains(column)) return out;
        String w = "kind=?" + (q == null || q.trim().isEmpty() ? "" : " AND (" + column + " LIKE ?)");
        String[] a = q == null || q.trim().isEmpty() ? new String[]{kind} : new String[]{kind, "%" + q.trim() + "%"};
        String order = column.equals("year") ? column + " DESC" : column + " COLLATE NOCASE";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + column + ",COUNT(*),SUM(duration),MIN(uri),COUNT(DISTINCT album) FROM tracks WHERE " + w + " GROUP BY " + column + " ORDER BY " + order, a)) {
            while (c.moveToNext()) {
                Group g = new Group();
                g.name = c.isNull(0) ? "" : c.getString(0);
                g.count = c.getInt(1);
                g.duration = c.getLong(2);
                g.sample = c.getString(3);
                g.albums = c.getInt(4);
                out.add(g);
            }
        }
        return out;
    }

    long[] totals(String kind) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*),COALESCE(SUM(duration),0),COALESCE(SUM(size),0) FROM tracks WHERE kind=?", new String[]{kind})) {
            c.moveToFirst();
            return new long[]{c.getLong(0), c.getLong(1), c.getLong(2)};
        }
    }

    void favorite(String uri, boolean on) {
        ContentValues v = new ContentValues();
        v.put("favorite", on ? 1 : 0);
        getWritableDatabase().update("tracks", v, "uri=?", new String[]{uri});
    }

    void remove(String uri) { getWritableDatabase().delete("tracks", "uri=?", new String[]{uri}); }

    static void played(Context c, String uri) {
        try (MediaLibrary db = new MediaLibrary(c)) {
            db.getWritableDatabase().execSQL("UPDATE tracks SET plays=plays+1,last_played=? WHERE uri=?", new Object[]{System.currentTimeMillis(), uri});
        } catch (Exception ignored) {}
    }

    /** Preferências simples da biblioteca (data da última procura, ordenação). */
    static final class LibraryPrefs {
        static android.content.SharedPreferences p(Context c) { return c.getApplicationContext().getSharedPreferences("media_library", Context.MODE_PRIVATE); }
        static void set(Context c, String k, long v) { p(c).edit().putLong(k, v).apply(); }
        static long get(Context c, String k) { return p(c).getLong(k, 0); }
    }
}
