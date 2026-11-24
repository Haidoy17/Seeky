package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Song details for the music library: title, artist, album, album artist, year, genre and track
 * number, read from ID3v2 (2.2, 2.3, 2.4) and ID3v1 tags (MP3) and from MP4 atoms (M4A/AAC/MP4).
 * Files without tags use the file name ("Artista - Título") and the folder.
 */
final class DeskTags {
    String title = "", artist = "", album = "", albumArtist = "", genre = "";
    int year, track;
    static final String[] GENRES = {"Blues","Classic Rock","Country","Dance","Disco","Funk","Grunge","Hip-Hop","Jazz","Metal","New Age","Oldies","Other","Pop","R&B","Rap","Reggae","Rock","Techno","Industrial","Alternative","Ska","Death Metal","Pranks","Soundtrack","Euro-Techno","Ambient","Trip-Hop","Vocal","Jazz+Funk","Fusion","Trance","Classical","Instrumental","Acid","House","Game","Sound Clip","Gospel","Noise","AlternRock","Bass","Soul","Punk","Space","Meditative","Instrumental Pop","Instrumental Rock","Ethnic","Gothic","Darkwave","Techno-Industrial","Electronic","Pop-Folk","Eurodance","Dream","Southern Rock","Comedy","Cult","Gangsta","Top 40","Christian Rap","Pop/Funk","Jungle","Native American","Cabaret","New Wave","Psychadelic","Rave","Showtunes","Trailer","Lo-Fi","Tribal","Acid Punk","Acid Jazz","Polka","Retro","Musical","Rock & Roll","Hard Rock"};

    static DeskTags read(File f) {
        DeskTags t = new DeskTags();
        String name = f.getName().toLowerCase(Locale.ROOT);
        try (RandomAccessFile in = new RandomAccessFile(f, "r")) {
            if (name.endsWith(".mp3")) { t.id3v2(in); if (t.title.isEmpty() || t.artist.isEmpty()) t.id3v1(in); }
            else if (name.endsWith(".m4a") || name.endsWith(".mp4") || name.endsWith(".aac") || name.endsWith(".m4b") || name.endsWith(".m4v")) t.mp4(in);
            else if (name.endsWith(".wav") || name.endsWith(".aif") || name.endsWith(".aiff")) t.id3v2(in);
        } catch (Exception ignored) {}
        t.fallback(f);
        return t;
    }
    /** Without tags: "Artista - Título.mp3"; the album is the folder's name. */
    void fallback(File f) {
        title = title.trim(); artist = artist.trim(); album = album.trim(); albumArtist = albumArtist.trim(); genre = genre.trim();
        String base = f.getName().replaceFirst("\\.[^.]+$", "").replace('_', ' ').trim();
        if (title.isEmpty()) {
            String clean = base.replaceFirst("^\\d{1,3}[ .\\-_]+", "");
            int dash = clean.indexOf(" - ");
            if (dash > 0 && artist.isEmpty()) { artist = clean.substring(0, dash).trim(); title = clean.substring(dash + 3).trim(); } else title = clean.isEmpty() ? base : clean;
        }
        if (artist.isEmpty() && !albumArtist.isEmpty()) artist = albumArtist;
        if (year < 0 || year > 3000) year = 0;
    }

    // ---- ID3 ----
    void id3v2(RandomAccessFile in) throws IOException {
        in.seek(0); byte[] head = new byte[10]; if (in.read(head) != 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') return;
        int major = head[3], flags = head[5] & 255, size = syncsafe(head, 6);
        if (size <= 0 || size > 64 << 20 || major < 2 || major > 4) return;
        byte[] tag = new byte[size]; in.readFully(tag);
        if ((flags & 0x80) != 0 && major < 4) tag = unsync(tag);
        int at = 0;
        if ((flags & 0x40) != 0 && major >= 3) { int ext = major == 4 ? syncsafe(tag, 0) : int32(tag, 0) + 4; at = Math.max(0, Math.min(tag.length, ext)); }
        int headLen = major == 2 ? 6 : 10;
        while (at + headLen <= tag.length) {
            String id = new String(tag, at, major == 2 ? 3 : 4, StandardCharsets.ISO_8859_1);
            if (id.charAt(0) == 0) break;
            int len = major == 2 ? ((tag[at + 3] & 255) << 16) | ((tag[at + 4] & 255) << 8) | (tag[at + 5] & 255) : major == 4 ? syncsafe(tag, at + 4) : int32(tag, at + 4);
            int body = at + headLen; if (len <= 0 || body + len > tag.length) break;
            if (id.startsWith("T") && !id.equals("TXXX") && !id.equals("TXX")) {
                String value = text(tag, body, len);
                switch (id) {
                    case "TIT2": case "TT2": title = value; break;
                    case "TPE1": case "TP1": artist = value; break;
                    case "TPE2": case "TP2": albumArtist = value; break;
                    case "TALB": case "TAL": album = value; break;
                    case "TYER": case "TYE": case "TDRC": case "TDOR": if (year == 0) year = year(value); break;
                    case "TCON": case "TCO": genre = genre(value); break;
                    case "TRCK": case "TRK": track = number(value); break;
                    default:
                }
            }
            at = body + len;
        }
    }
    void id3v1(RandomAccessFile in) throws IOException {
        if (in.length() < 128) return;
        in.seek(in.length() - 128); byte[] b = new byte[128]; in.readFully(b);
        if (b[0] != 'T' || b[1] != 'A' || b[2] != 'G') return;
        if (title.isEmpty()) title = latin(b, 3, 30); if (artist.isEmpty()) artist = latin(b, 33, 30); if (album.isEmpty()) album = latin(b, 63, 30);
        if (year == 0) year = year(latin(b, 93, 4));
        if (b[125] == 0 && b[126] != 0 && track == 0) track = b[126] & 255;
        if (genre.isEmpty() && (b[127] & 255) < GENRES.length) genre = GENRES[b[127] & 255];
    }
    static String text(byte[] b, int at, int len) {
        int enc = b[at]; int from = at + 1, to = at + len;
        String s;
        switch (enc) {
            case 1: s = new String(b, from, to - from, StandardCharsets.UTF_16); break;
            case 2: s = new String(b, from, to - from, StandardCharsets.UTF_16BE); break;
            case 3: s = new String(b, from, to - from, StandardCharsets.UTF_8); break;
            default: s = new String(b, from, to - from, StandardCharsets.ISO_8859_1);
        }
        int zero = s.indexOf('\0'); if (zero >= 0) s = s.substring(0, zero); // the first value of a list
        return s.replace("﻿", "").trim();
    }
    static String latin(byte[] b, int at, int len) { String s = new String(b, at, len, StandardCharsets.ISO_8859_1); int z = s.indexOf('\0'); return (z >= 0 ? s.substring(0, z) : s).trim(); }
    static int syncsafe(byte[] b, int at) { return ((b[at] & 0x7f) << 21) | ((b[at + 1] & 0x7f) << 14) | ((b[at + 2] & 0x7f) << 7) | (b[at + 3] & 0x7f); }
    static int int32(byte[] b, int at) { return ((b[at] & 255) << 24) | ((b[at + 1] & 255) << 16) | ((b[at + 2] & 255) << 8) | (b[at + 3] & 255); }
    static byte[] unsync(byte[] b) { ByteArrayOutputStream o = new ByteArrayOutputStream(b.length); for (int i = 0; i < b.length; i++) { o.write(b[i]); if ((b[i] & 255) == 0xff && i + 1 < b.length && b[i + 1] == 0) i++; } return o.toByteArray(); }
    static int year(String v) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("(1[5-9]\\d\\d|20\\d\\d|21\\d\\d)").matcher(v == null ? "" : v); return m.find() ? Integer.parseInt(m.group(1)) : 0; }
    static int number(String v) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{1,4})").matcher(v == null ? "" : v); return m.find() ? Integer.parseInt(m.group(1)) : 0; }
    /** "(17)", "17" or "(17)Rock" become the genre's name. */
    static String genre(String v) {
        if (v == null) return ""; java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\(?(\\d{1,3})\\)?(.*)$").matcher(v.trim());
        if (m.matches()) { String rest = m.group(2).trim(); if (!rest.isEmpty()) return rest; int n = Integer.parseInt(m.group(1)); return n < GENRES.length ? GENRES[n] : ""; }
        return v.trim();
    }

    // ---- MP4 / M4A ----
    void mp4(RandomAccessFile in) throws IOException {
        long[] moov = find(in, 0, in.length(), "moov"); if (moov == null) return;
        long[] udta = find(in, moov[0], moov[1], "udta"); if (udta == null) return;
        long[] meta = find(in, udta[0], udta[1], "meta"); if (meta == null) return;
        long[] ilst = find(in, meta[0] + 4, meta[1], "ilst"); if (ilst == null) return;
        long at = ilst[0];
        while (at + 8 <= ilst[1]) {
            in.seek(at); long size = in.readInt() & 0xffffffffL; byte[] type = new byte[4]; in.readFully(type);
            if (size < 8 || at + size > ilst[1]) break;
            long[] data = find(in, at + 8, at + size, "data");
            if (data != null && data[1] - data[0] > 8 && data[1] - data[0] < 1 << 20) {
                in.seek(data[0]); int kind = in.readInt() & 0xffffff; in.readInt();
                byte[] value = new byte[(int) (data[1] - data[0] - 8)]; in.readFully(value);
                String key = new String(type, StandardCharsets.ISO_8859_1), s = new String(value, StandardCharsets.UTF_8).trim();
                switch (key) {
                    case "©nam": title = s; break;
                    case "©ART": artist = s; break;
                    case "aART": albumArtist = s; break;
                    case "©alb": album = s; break;
                    case "©day": year = year(s); break;
                    case "©gen": genre = s; break;
                    case "gnre": if (value.length >= 2) { int g = ((value[0] & 255) << 8 | (value[1] & 255)) - 1; if (g >= 0 && g < GENRES.length) genre = GENRES[g]; } break;
                    case "trkn": if (kind == 0 && value.length >= 4) track = ((value[2] & 255) << 8) | (value[3] & 255); break;
                    default:
                }
            }
            at += size;
        }
    }
    /** {start of content, end} of the first box of this type between from and to. */
    static long[] find(RandomAccessFile in, long from, long to, String want) throws IOException {
        long at = from;
        while (at + 8 <= to) {
            in.seek(at); long size = in.readInt() & 0xffffffffL; byte[] type = new byte[4]; in.readFully(type); long header = 8;
            if (size == 1) { size = in.readLong(); header = 16; } else if (size == 0) size = to - at;
            if (size < header || at + size > to) return null;
            if (new String(type, StandardCharsets.ISO_8859_1).equals(want)) return new long[]{at + header, at + size};
            at += size;
        }
        return null;
    }
}
