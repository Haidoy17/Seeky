package mz.bam.seeky.android;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fila de reprodução partilhada pelo leitor e pelo serviço: faixa actual, seguinte e anterior,
 * ordem aleatória e repetição (desligada, tudo, uma). Guardada em ficheiro para sobreviver ao
 * fecho da aplicação.
 */
final class MediaQueue {
    static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2, MAX = 5000;

    static final class Item {
        final String uri, title, artist, album;
        final long duration;
        Item(String uri, String title, String artist, String album, long duration) {
            this.uri = uri; this.title = title == null ? "" : title; this.artist = artist == null ? "" : artist; this.album = album == null ? "" : album; this.duration = duration;
        }
        JSONObject json() throws Exception { return new JSONObject().put("u", uri).put("t", title).put("a", artist).put("b", album).put("d", duration); }
        static Item from(JSONObject o) { return new Item(o.optString("u"), o.optString("t"), o.optString("a"), o.optString("b"), o.optLong("d")); }
    }

    static final List<Item> items = new ArrayList<>();
    static int[] order = new int[0];
    static int position = -1, repeat = REPEAT_OFF;
    static boolean shuffle, loaded;

    private MediaQueue() {}

    static File file(Context c) { return new File(c.getFilesDir(), "media-queue.json"); }

    static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8));
            JSONArray list = o.getJSONArray("items");
            items.clear();
            for (int i = 0; i < list.length(); i++) items.add(Item.from(list.getJSONObject(i)));
            shuffle = o.optBoolean("shuffle");
            repeat = o.optInt("repeat");
            rebuild(o.optInt("index", 0));
            position = Math.max(0, Math.min(order.length - 1, o.optInt("position", 0)));
        } catch (Exception ignored) {}
    }

    static synchronized void save(Context c) {
        try {
            JSONArray list = new JSONArray();
            for (Item i : items) list.put(i.json());
            JSONObject o = new JSONObject().put("items", list).put("shuffle", shuffle).put("repeat", repeat).put("index", index()).put("position", position);
            File f = file(c), tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(o.toString().getBytes(StandardCharsets.UTF_8)); }
            tmp.renameTo(f);
        } catch (Exception ignored) {}
    }

    /** Índice (na lista original) da faixa actual. */
    static synchronized int index() { return position < 0 || position >= order.length ? -1 : order[position]; }

    static synchronized Item current() { int i = index(); return i < 0 || i >= items.size() ? null : items.get(i); }

    static synchronized int size() { return items.size(); }

    /** Nova fila a começar em {@code start}. */
    static synchronized void set(Context c, List<Item> list, int start) {
        items.clear();
        items.addAll(list.size() > MAX ? list.subList(0, MAX) : list);
        loaded = true;
        rebuild(Math.max(0, Math.min(items.size() - 1, start)));
        save(c);
    }

    /** Abriu-se um ficheiro solto: se não estiver na fila, a fila passa a ser só ele. */
    static synchronized void opened(Context c, String uri, String title) {
        load(c);
        for (int p = 0; p < order.length; p++) if (items.get(order[p]).uri.equals(uri)) { position = p; save(c); return; }
        set(c, Collections.singletonList(new Item(uri, title, "", "", 0)), 0);
    }

    /** Ordem de reprodução: natural, ou aleatória com a faixa {@code start} primeiro. */
    static void rebuild(int start) {
        int n = items.size();
        order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        if (shuffle && n > 1) {
            Random r = new Random();
            for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1), t = order[i]; order[i] = order[j]; order[j] = t; }
            for (int i = 0; i < n; i++) if (order[i] == start) { order[i] = order[0]; order[0] = start; break; }
            position = 0;
        } else position = n == 0 ? -1 : start;
    }

    static synchronized void setShuffle(Context c, boolean on) {
        int current = Math.max(0, index());
        shuffle = on;
        rebuild(current);
        save(c);
    }

    static synchronized void cycleRepeat(Context c) { repeat = (repeat + 1) % 3; save(c); }

    /** Seguinte: no fim volta ao início só com "repetir tudo" (ou se o utilizador pediu). */
    static synchronized Item next(Context c, boolean user) {
        if (order.length == 0) return null;
        if (position + 1 < order.length) position++;
        else if (repeat == REPEAT_ALL || user) position = 0;
        else return null;
        save(c);
        return current();
    }

    static synchronized Item previous(Context c) {
        if (order.length == 0) return null;
        position = position > 0 ? position - 1 : repeat == REPEAT_ALL ? order.length - 1 : 0;
        save(c);
        return current();
    }

    static synchronized Item jump(Context c, int originalIndex) {
        for (int p = 0; p < order.length; p++) if (order[p] == originalIndex) { position = p; save(c); return current(); }
        return null;
    }

    /** Lista na ordem em que vai tocar, a partir da actual. */
    static synchronized List<Item> upcoming() {
        List<Item> out = new ArrayList<>();
        for (int p = Math.max(0, position); p < order.length; p++) out.add(items.get(order[p]));
        return out;
    }

    /** Toca a seguir à faixa actual. */
    static synchronized void insertNext(Context c, Item item) {
        load(c);
        if (items.isEmpty()) { set(c, Collections.singletonList(item), 0); return; }
        items.add(item);
        int added = items.size() - 1;
        int[] o = new int[order.length + 1];
        int p = Math.max(0, position);
        System.arraycopy(order, 0, o, 0, p + 1);
        o[p + 1] = added;
        System.arraycopy(order, p + 1, o, p + 2, order.length - p - 1);
        order = o;
        save(c);
    }

    /** No fim da fila. */
    static synchronized void append(Context c, Item item) {
        load(c);
        if (items.isEmpty()) { set(c, Collections.singletonList(item), 0); return; }
        items.add(item);
        order = Arrays.copyOf(order, order.length + 1);
        order[order.length - 1] = items.size() - 1;
        save(c);
    }
}
