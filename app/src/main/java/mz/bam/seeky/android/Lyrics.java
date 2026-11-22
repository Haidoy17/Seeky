package mz.bam.seeky.android;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Letras das músicas, procuradas por esta ordem: letra guardada ou editada no Seeky, ficheiro
 * .lrc/.txt com o mesmo nome ao lado da música, letra incorporada no ficheiro (ID3 USLT, FLAC
 * LYRICS, MP4 ©lyr) e, se permitido, LRCLIB e Lyrics.ovh na internet (só envia título, artista,
 * álbum e duração). O que não for encontrado pode ser transcrito pela IA (TranscribeWork). O
 * resultado fica guardado no telefone.
 */
final class Lyrics {
    static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    static final class Line {
        final long time;
        final String text;
        Line(long time, String text) { this.time = time; this.text = text; }
    }

    static final class Result {
        String text = "", source = "";
        List<Line> synced = new ArrayList<>();
        boolean instrumental;
        boolean found() { return !text.trim().isEmpty() || instrumental; }
        boolean isSynced() { return !synced.isEmpty(); }
    }

    private Lyrics() {}

    static boolean online(Context c) { return c.getSharedPreferences("lyrics", 0).getBoolean("online", true); }
    static void setOnline(Context c, boolean on) { c.getSharedPreferences("lyrics", 0).edit().putBoolean("online", on).apply(); }

    // ---- LRC ----

    /** Linhas com tempo ([mm:ss.xx]), várias marcas por linha e [offset:±ms]. */
    static List<Line> parseLrc(String text) {
        List<Line> out = new ArrayList<>();
        if (text == null) return out;
        long offset = 0;
        Matcher om = OFFSET.matcher(text);
        if (om.find()) try { offset = Long.parseLong(om.group(1)); } catch (NumberFormatException ignored) {}
        for (String raw : text.split("\\r?\\n")) {
            Matcher m = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2)), frac = 0;
                if (m.group(3) != null) { String f = m.group(3); frac = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1); }
                times.add(Math.max(0, min * 60000 + sec * 1000 + frac - offset));
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String lyric = raw.substring(end).replaceAll("<\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?>", "").trim();
            for (long t : times) out.add(new Line(t, lyric));
        }
        out.sort((a, b) -> Long.compare(a.time, b.time));
        return out;
    }

    /** Texto sem marcas de tempo (para mostrar letras sincronizadas como texto). */
    static String plain(String text) {
        StringBuilder b = new StringBuilder();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.replaceAll("\\[[^\\]]*\\]", "").replaceAll("<\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?>", "").trim();
            if (raw.trim().matches("\\[(ar|ti|al|by|offset|length|re|ve|au):.*\\]")) continue;
            b.append(line).append('\n');
        }
        return b.toString().trim();
    }

    /** Linha actual para a posição dada (−1 antes da primeira). */
    static int current(List<Line> lines, long position) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) { int mid = (lo + hi) >>> 1; if (lines.get(mid).time <= position) { ans = mid; lo = mid + 1; } else hi = mid - 1; }
        return ans;
    }

    static Result result(String text, String source) {
        Result r = new Result();
        r.source = source;
        r.synced = parseLrc(text);
        r.text = r.isSynced() ? plain(text) : text.trim();
        return r;
    }

    // ---- cache ----

    static File dir(Context c) { File d = new File(c.getFilesDir(), "lyrics"); d.mkdirs(); return d; }

    static String key(String uri) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format(Locale.ROOT, "%02x", h[i] & 255));
            return b.toString();
        } catch (Exception e) { return Integer.toHexString(uri.hashCode()); }
    }

    static void store(Context c, String uri, String text, String source) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir(c), key(uri) + ".lrc")), StandardCharsets.UTF_8)) { w.write("[re:" + source + "]\n" + text); }
        catch (IOException ignored) {}
        new File(dir(c), key(uri) + ".none").delete();
    }

    static Result cached(Context c, String uri) {
        File f = new File(dir(c), key(uri) + ".lrc");
        if (!f.isFile()) return null;
        try {
            String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String source = "Seeky";
            Matcher m = Pattern.compile("^\\[re:([^\\]]*)\\]\\n").matcher(text);
            if (m.find()) { source = m.group(1); text = text.substring(m.end()); }
            return result(text, source);
        } catch (IOException e) { return null; }
    }

    static void forget(Context c, String uri) {
        new File(dir(c), key(uri) + ".lrc").delete();
        new File(dir(c), key(uri) + ".none").delete();
    }

    // ---- procura ----

    static Result find(Context c, Uri uri, String title, String artist, String album, long durationMs, boolean allowOnline) { return find(c, uri, title, artist, album, durationMs, allowOnline, null); }
    /**
     * Guardada → .lrc ao lado → incorporada → internet (várias tentativas com o nome limpo; só aceita
     * resultados com título, artista e duração coerentes). Pregações, aulas e notas de voz não são
     * procuradas como músicas: ficam para a transcrição automática.
     */
    static Result find(Context c, Uri uri, String title, String artist, String album, long durationMs, boolean allowOnline, String file) {
        Result r = cached(c, uri.toString());
        if (r != null) return r;
        String side = sidecar(uri);
        if (side != null) return result(side, "Ficheiro LRC");
        String embedded = embedded(c, uri);
        if (embedded != null && !embedded.trim().isEmpty()) return result(embedded, "Incorporada no ficheiro");
        File none = new File(dir(c), key(uri.toString()) + ".none");
        boolean nameless = (title == null || title.trim().isEmpty()) && (file == null || file.trim().isEmpty());
        if (!allowOnline || nameless || LyricsText.speech(title, file, durationMs) || (none.isFile() && System.currentTimeMillis() - none.lastModified() < 7 * 86400000L)) return new Result();
        try {
            Result net = online(title, artist, album, durationMs, file);
            if (net.found()) { store(c, uri.toString(), net.isSynced() ? lrcText(net) : net.text, net.source); return net; }
            try { none.createNewFile(); none.setLastModified(System.currentTimeMillis()); } catch (IOException ignored) {}
        } catch (Exception ignored) {}
        return new Result();
    }
    /** Procurado já e não encontrado na internet (nos últimos 7 dias). */
    static boolean missing(Context c, String uri) { File none = new File(dir(c), key(uri) + ".none"); return none.isFile() && System.currentTimeMillis() - none.lastModified() < 7 * 86400000L; }

    /** LRCLIB com até 3 tentativas (exacta e pesquisa), pesquisa livre e, por fim, Lyrics.ovh (texto simples). */
    static Result online(String title, String artist, String album, long durationMs, String file) throws Exception {
        List<LyricsText.Query> queries = LyricsText.queries(title, artist, file);
        for (int i = 0; i < Math.min(3, queries.size()); i++) {
            LyricsText.Query q = queries.get(i);
            if (!q.artist.isEmpty()) {
                String exact = get("https://lrclib.net/api/get?track_name=" + enc(q.title) + "&artist_name=" + enc(q.artist) + (i == 0 && album != null && !album.trim().isEmpty() ? "&album_name=" + enc(album) : "") + (durationMs > 0 ? "&duration=" + Math.round(durationMs / 1000.0) : ""));
                if (exact != null) { JSONObject o = new JSONObject(exact); if (durationMs <= 0 || o.optDouble("duration", 0) <= 0 || Math.abs(o.optDouble("duration") * 1000 - durationMs) < 8000) { Result r = fromJson(o); if (r.found()) return r; } }
            }
            String search = get("https://lrclib.net/api/search?track_name=" + enc(q.title) + (q.artist.isEmpty() ? "" : "&artist_name=" + enc(q.artist)));
            if (search != null) { JSONArray list = new JSONArray(search); int k = LyricsText.pick(candidates(list), q, durationMs); if (k >= 0) return fromJson(list.getJSONObject(k)); }
        }
        if (!queries.isEmpty()) {
            LyricsText.Query q = queries.get(0);
            String free = get("https://lrclib.net/api/search?q=" + enc((q.artist + " " + q.title).trim()));
            if (free != null) { JSONArray list = new JSONArray(free); int k = LyricsText.pick(candidates(list), q, durationMs); if (k >= 0) return fromJson(list.getJSONObject(k)); }
        }
        for (LyricsText.Query q : queries) {
            if (q.artist.isEmpty()) continue;
            String body = get("https://api.lyrics.ovh/v1/" + enc(q.artist).replace("+", "%20") + "/" + enc(q.title).replace("+", "%20"));
            if (body != null) { String text = new JSONObject(body).optString("lyrics", "").replace("\r\n", "\n").replaceFirst("^(?i)paroles de la chanson[^\n]*\n", "").trim(); if (text.length() >= 40) return result(text, "Lyrics.ovh"); }
            break;
        }
        return new Result();
    }

    static String lrcText(Result r) {
        StringBuilder b = new StringBuilder();
        for (Line l : r.synced) b.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]", l.time / 60000, (l.time / 1000) % 60, (l.time % 1000) / 10)).append(l.text).append('\n');
        return b.toString();
    }

    /** Ficheiro .lrc (ou .txt) com o mesmo nome ao lado da música. */
    static String sidecar(Uri uri) {
        if (!"file".equals(uri.getScheme()) || uri.getPath() == null) return null;
        String path = uri.getPath();
        int dot = path.lastIndexOf('.');
        String base = dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
        for (String ext : new String[]{".lrc", ".LRC", ".txt"}) {
            File f = new File(base + ext);
            if (f.isFile() && f.length() < 512 * 1024) try { return decodeText(java.nio.file.Files.readAllBytes(f.toPath())); } catch (IOException ignored) {}
        }
        return null;
    }

    static String decodeText(byte[] b) {
        if (b.length >= 2 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0xFE) return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        if (b.length >= 2 && (b[0] & 255) == 0xFE && (b[1] & 255) == 0xFF) return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        int start = b.length >= 3 && (b[0] & 255) == 0xEF && (b[1] & 255) == 0xBB && (b[2] & 255) == 0xBF ? 3 : 0;
        String utf = new String(b, start, b.length - start, StandardCharsets.UTF_8);
        return utf.contains("�") ? new String(b, Charset.forName("windows-1252")) : utf;
    }

    // ---- letra incorporada ----

    static String embedded(Context c, Uri uri) {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            byte[] head = new byte[10];
            if (readFully(in, head) < 10) return null;
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
                int size = syncsafe(head, 6);
                if (size <= 0 || size > 16 * 1024 * 1024) return null;
                byte[] tag = new byte[size];
                int got = readFully(in, tag);
                return id3(Arrays.copyOf(tag, got), head[3], (head[5] & 0x40) != 0);
            }
            if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return flac(in, Arrays.copyOfRange(head, 4, 8), Arrays.copyOfRange(head, 8, 10));
            byte[] rest = new byte[6 * 1024 * 1024];
            int got = readFully(in, rest);
            byte[] all = new byte[10 + got];
            System.arraycopy(head, 0, all, 0, 10);
            System.arraycopy(rest, 0, all, 10, got);
            return mp4(all);
        } catch (Exception e) { return null; }
    }

    static int readFully(InputStream in, byte[] b) throws IOException {
        int n = 0;
        while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
        return n;
    }

    static int syncsafe(byte[] b, int o) { return ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f); }
    static int be(byte[] b, int o, int n) { int v = 0; for (int i = 0; i < n; i++) v = (v << 8) | (b[o + i] & 255); return v; }

    /** Molduras ID3v2.2/2.3/2.4: USLT (ou ULT) com a letra. */
    static String id3(byte[] tag, int version, boolean extended) {
        int p = 0;
        if (extended && tag.length >= 4) p = version >= 4 ? syncsafe(tag, 0) : be(tag, 0, 4) + 4;
        boolean v22 = version == 2;
        int header = v22 ? 6 : 10;
        while (p + header <= tag.length) {
            String id = new String(tag, p, v22 ? 3 : 4, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) break;
            int size = v22 ? be(tag, p + 3, 3) : version >= 4 ? syncsafe(tag, p + 4) : be(tag, p + 4, 4);
            int data = p + header;
            if (size <= 0 || data + size > tag.length) break;
            if (id.equals("USLT") || id.equals("ULT")) {
                int enc = tag[data] & 255, q = data + 4, end = data + size;
                q = skipString(tag, q, end, enc);
                return decode(tag, q, end, enc).trim();
            }
            p = data + size;
        }
        return null;
    }

    static int skipString(byte[] b, int p, int end, int enc) {
        if (enc == 1 || enc == 2) { while (p + 1 < end && !(b[p] == 0 && b[p + 1] == 0)) p += 2; return Math.min(end, p + 2); }
        while (p < end && b[p] != 0) p++;
        return Math.min(end, p + 1);
    }

    static String decode(byte[] b, int from, int to, int enc) {
        if (to <= from) return "";
        switch (enc) {
            case 1: return new String(b, from, to - from, StandardCharsets.UTF_16).replace("\u0000", "");
            case 2: return new String(b, from, to - from, StandardCharsets.UTF_16BE).replace("\u0000", "");
            case 3: return new String(b, from, to - from, StandardCharsets.UTF_8).replace("\u0000", "");
            default: return new String(b, from, to - from, StandardCharsets.ISO_8859_1).replace("\u0000", "");
        }
    }

    /** FLAC: bloco VORBIS_COMMENT com LYRICS= ou UNSYNCEDLYRICS=. */
    /** FLAC: bloco VORBIS_COMMENT com LYRICS= ou UNSYNCEDLYRICS=. {@code already}: bytes do 1.º bloco já lidos. */
    static String flac(InputStream in, byte[] first, byte[] already) throws IOException {
        byte[] header = first, pre = already;
        for (int blocks = 0; blocks < 64; blocks++) {
            boolean last = (header[0] & 0x80) != 0;
            int type = header[0] & 0x7f, len = be(header, 1, 3), have = Math.min(pre.length, len);
            if (type == 4) {
                if (len > 16 * 1024 * 1024) return null;
                byte[] body = new byte[len];
                System.arraycopy(pre, 0, body, 0, have);
                byte[] rest = new byte[len - have];
                readFully(in, rest);
                System.arraycopy(rest, 0, body, have, rest.length);
                return vorbis(body);
            }
            skipFully(in, len - have);
            if (last) return null;
            header = new byte[4];
            pre = new byte[0];
            if (readFully(in, header) < 4) return null;
        }
        return null;
    }

    static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) { long k = in.skip(n); if (k <= 0) { if (in.read() < 0) return; k = 1; } n -= k; }
    }

    static String vorbis(byte[] body) {
        if (body.length < 8) return null;
        int p = 0, vendor = le(body, p);
        p += 4 + vendor;
        if (p + 4 > body.length) return null;
        int count = le(body, p);
        p += 4;
        for (int i = 0; i < count && p + 4 <= body.length; i++) {
            int l = le(body, p);
            p += 4;
            if (l < 0 || p + l > body.length) break;
            String field = new String(body, p, l, StandardCharsets.UTF_8);
            p += l;
            int eq = field.indexOf('=');
            if (eq > 0 && (field.substring(0, eq).equalsIgnoreCase("LYRICS") || field.substring(0, eq).equalsIgnoreCase("UNSYNCEDLYRICS"))) return field.substring(eq + 1);
        }
        return null;
    }

    static int le(byte[] b, int o) { return (b[o] & 255) | (b[o + 1] & 255) << 8 | (b[o + 2] & 255) << 16 | (b[o + 3] & 255) << 24; }

    /** MP4/M4A: caixa ©lyr → data. */
    static String mp4(byte[] b) {
        byte[] key = {(byte) 0xA9, 'l', 'y', 'r'};
        for (int i = 4; i + 16 < b.length; i++) {
            if (b[i] == key[0] && b[i + 1] == key[1] && b[i + 2] == key[2] && b[i + 3] == key[3]) {
                int d = i + 4;
                if (d + 16 <= b.length && b[d + 4] == 'd' && b[d + 5] == 'a' && b[d + 6] == 't' && b[d + 7] == 'a') {
                    int len = be(b, d, 4);
                    int start = d + 16, end = Math.min(b.length, d + len);
                    if (end > start) return new String(b, start, end - start, StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    // ---- LRCLIB ----

    static List<LyricsText.Candidate> candidates(JSONArray list) {
        List<LyricsText.Candidate> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) { out.add(new LyricsText.Candidate("", "", 0, false, false)); continue; }
            boolean synced = !o.isNull("syncedLyrics") && !o.optString("syncedLyrics").isEmpty(), plain = !o.isNull("plainLyrics") && !o.optString("plainLyrics").isEmpty();
            out.add(new LyricsText.Candidate(o.optString("trackName", o.optString("name")), o.optString("artistName"), o.optDouble("duration", 0), synced, synced || plain || o.optBoolean("instrumental")));
        }
        return out;
    }
    static Result lrclib(String title, String artist, String album, long durationMs) throws Exception { return online(title, artist, album, durationMs, null); }

    /** Um resultado da pesquisa online, para o utilizador escolher. */
    static final class Candidate {
        String track = "", artist = "", album = "";
        long duration;
        boolean synced, instrumental;
        JSONObject json;
        String detail() {
            StringBuilder b = new StringBuilder(artist);
            if (!album.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(album);
            if (duration > 0) b.append(b.length() > 0 ? " · " : "").append(duration / 60).append(':').append(String.format(Locale.ROOT, "%02d", duration % 60));
            return b.toString();
        }
    }

    /** Pesquisa livre na LRCLIB (título, artista ou parte da letra); até 20 resultados. */
    static List<Candidate> search(String query) throws Exception {
        List<Candidate> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return out;
        String body = get("https://lrclib.net/api/search?q=" + enc(query));
        if (body == null) return out;
        JSONArray list = new JSONArray(body);
        for (int i = 0; i < Math.min(20, list.length()); i++) {
            JSONObject o = list.getJSONObject(i);
            Candidate c = new Candidate();
            c.track = o.optString("trackName", o.optString("name"));
            c.artist = o.optString("artistName");
            c.album = o.optString("albumName");
            c.duration = Math.round(o.optDouble("duration", 0));
            c.synced = !o.isNull("syncedLyrics") && !o.optString("syncedLyrics").isEmpty();
            c.instrumental = o.optBoolean("instrumental");
            c.json = o;
            if (c.synced || c.instrumental || (!o.isNull("plainLyrics") && !o.optString("plainLyrics").isEmpty())) out.add(c);
        }
        out.sort((a, b) -> Boolean.compare(b.synced, a.synced));
        return out;
    }

    /** Texto copiado de uma página: limpa espaços e linhas a mais. */
    static String cleanPasted(String text) {
        if (text == null) return "";
        String t = text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }

    /** Parece uma letra? (várias linhas e algum texto) — usado ao aplicar automaticamente o que foi copiado. */
    static boolean looksLikeLyrics(String text) {
        String t = cleanPasted(text);
        return t.length() >= 40 && t.split("\n").length >= 3 && !t.startsWith("http");
    }

    static Result fromJson(JSONObject o) {
        String synced = o.isNull("syncedLyrics") ? "" : o.optString("syncedLyrics"), plainText = o.isNull("plainLyrics") ? "" : o.optString("plainLyrics");
        Result r = !synced.isEmpty() ? result(synced, "LRCLIB") : result(plainText, "LRCLIB");
        if (r.text.isEmpty() && !plainText.isEmpty()) r.text = plainText;
        r.instrumental = o.optBoolean("instrumental");
        return r;
    }

    static String enc(String s) throws UnsupportedEncodingException { return URLEncoder.encode(s.trim(), "UTF-8"); }

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Seeky/" + MainActivity.VERSION.replace("Seeky ", "") + " (Android music player)");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("Serviço de letras: HTTP " + code);
            try (InputStream in = new OpenLibraries.Limited(c.getInputStream(), 2 * 1024 * 1024)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                PdfTools.copy(in, b);
                return b.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }
}
