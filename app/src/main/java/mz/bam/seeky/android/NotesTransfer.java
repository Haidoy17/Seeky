package mz.bam.seeky.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Notas entre o telemóvel e o computador: um ficheiro ".seekynotes" (JSON) com as notas, o seu
 * aspecto (pasta, cor, fixada, espaçamento, livro) e as imagens e gravações que usam. O Seeky para
 * Android e o Seeky para Windows escrevem e lêem o mesmo formato; ao importar, as notas que já
 * existem (mesmo título e texto) não são repetidas.
 */
final class NotesTransfer {
    static final String FORMAT = "seeky-notes-1", EXTENSION = ".seekynotes", MIME = "application/json";
    static final int MAX_FILE = 64 * 1024 * 1024, MAX_NOTES = 20000, MAX_MEDIA = 40 * 1024 * 1024, MAX_TEXT = 2 * 1024 * 1024;
    static final Pattern MEDIA = Pattern.compile("\\((note-(?:img|audio)):([A-Za-z0-9._-]{1,80})\\)");
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private NotesTransfer() {}

    static final class Item {
        String title = "", body = "", folder = "", source = "", tags = "";
        long created, updated; int color, spacing; boolean pinned;
    }
    /** Reads one picture or recording of the notes by name (null when it is missing). */
    interface Media { byte[] read(String name); }
    static final class Parsed { final List<Item> notes = new ArrayList<>(); final Map<String, byte[]> media = new LinkedHashMap<>(); String from = ""; long exported; }

    /** Names of the pictures and recordings a note uses. */
    static Set<String> media(String body) {
        Set<String> out = new LinkedHashSet<>(); if (body == null) return out;
        Matcher m = MEDIA.matcher(body); while (m.find()) out.add(m.group(2));
        return out;
    }

    static byte[] write(List<Item> notes, Media media, String from) throws Exception {
        if (notes.size() > MAX_NOTES) throw new Exception("Mais de " + MAX_NOTES + " notas.");
        JSONArray list = new JSONArray(); Map<String, String> files = new LinkedHashMap<>(); long mediaBytes = 0;
        for (Item n : notes) {
            list.put(new JSONObject().put("title", n.title).put("body", n.body).put("folder", n.folder).put("source", n.source).put("tags", n.tags)
                .put("created", n.created).put("updated", n.updated).put("color", n.color).put("spacing", n.spacing).put("pinned", n.pinned));
            if (media == null) continue;
            for (String name : media(n.body)) {
                if (files.containsKey(name)) continue;
                byte[] data = media.read(name); if (data == null) continue;
                if (mediaBytes + data.length > MAX_MEDIA) continue; // too much: the note keeps its text
                mediaBytes += data.length; files.put(name, Base64.getEncoder().encodeToString(data));
            }
        }
        JSONObject root = new JSONObject().put("format", FORMAT).put("schema", 1).put("from", from == null ? "" : from).put("exported", System.currentTimeMillis()).put("notes", list).put("media", new JSONObject(files));
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Parsed read(byte[] data) throws Exception {
        if (data == null || data.length == 0) throw new Exception("Ficheiro vazio.");
        if (data.length > MAX_FILE) throw new Exception("Ficheiro de notas acima de 64 MB.");
        JSONObject root;
        try { root = new JSONObject(new String(data, StandardCharsets.UTF_8)); } catch (Exception e) { throw new Exception("Este ficheiro não é uma cópia de notas do Seeky."); }
        if (!FORMAT.equals(root.optString("format")) || root.optInt("schema", 0) != 1) throw new Exception("Este ficheiro não é uma cópia de notas do Seeky.");
        Parsed out = new Parsed(); out.from = root.optString("from", ""); out.exported = root.optLong("exported", 0);
        JSONArray list = root.optJSONArray("notes"); if (list == null) throw new Exception("O ficheiro não tem notas.");
        if (list.length() > MAX_NOTES) throw new Exception("Demasiadas notas no ficheiro.");
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i); if (o == null) continue;
            Item n = new Item();
            n.title = text(o, "title", 2000); n.body = text(o, "body", MAX_TEXT); n.folder = text(o, "folder", 200).trim(); n.source = text(o, "source", 2000); n.tags = text(o, "tags", 2000);
            n.created = clampTime(o.optLong("created", now), now); n.updated = Math.max(n.created, clampTime(o.optLong("updated", n.created), now));
            n.color = o.optInt("color", 0); n.spacing = Math.max(0, Math.min(300, o.optInt("spacing", 0))); n.pinned = o.optBoolean("pinned", false);
            if (n.title.trim().isEmpty() && n.body.trim().isEmpty()) continue;
            out.notes.add(n);
        }
        JSONObject media = root.optJSONObject("media"); long total = 0;
        if (media != null) for (Iterator<String> it = media.keys(); it.hasNext(); ) {
            String name = it.next(); if (!NAME.matcher(name).matches() || name.startsWith(".")) continue;
            byte[] bytes; try { bytes = Base64.getDecoder().decode(media.optString(name, "")); } catch (IllegalArgumentException e) { continue; }
            if (bytes.length == 0 || (total += bytes.length) > MAX_MEDIA) continue;
            out.media.put(name, bytes);
        }
        return out;
    }
    static String text(JSONObject o, String key, int max) throws Exception { String s = o.optString(key, ""); if (s.length() > max) throw new Exception("Nota demasiado longa no ficheiro."); return s; }
    static long clampTime(long t, long now) { return t <= 0 || t > now + 86400000L ? now : t; }

    /** Identity used to skip notes that already exist (same title and text). */
    static String key(String title, String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(((title == null ? "" : title.trim()) + "\n" + (body == null ? "" : body.trim())).getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder(); for (int i = 0; i < 12; i++) s.append(String.format("%02x", h[i] & 255)); return s.toString();
        } catch (Exception e) { return (title + "\n" + body).hashCode() + ""; }
    }
    /** When a picture with the same name but other content already exists, the note uses a new name. */
    static String rename(String body, String from, String to) { return body.replace(":" + from + ")", ":" + to + ")"); }
    static String fresh(String name, Set<String> taken) {
        String base = name.replaceFirst("\\.[^.]*$", ""), ext = name.length() > base.length() ? name.substring(base.length()) : "";
        for (int i = 2; ; i++) { String n = base + "_" + i + ext; if (n.length() <= 80 && !taken.contains(n)) return n; if (n.length() > 80) { base = base.substring(0, Math.max(1, base.length() - 4)); } }
    }
    static String fileName() { return "Notas do Seeky " + new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + EXTENSION; }
}
