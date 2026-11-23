package mz.bam.seeky.android;

import java.util.*;

/**
 * The only places the Shizuku file service may touch: the apps' folders Android/data and
 * Android/obb of the phone's storage. Every path is normalised and checked on both sides (the
 * Seeky and the service), so a mistake or a crafted name can never reach other system files.
 */
final class ShellPaths {
    private ShellPaths() {}
    /** The storage roots ("/storage/emulated/0"); tests point this at a temporary folder. */
    static volatile String[] roots = {"/storage/emulated/0"};
    static final String[] AREAS = {"Android/data", "Android/obb"};

    /** "/storage/emulated/0/Android/data/x/./y//z" → "/storage/emulated/0/Android/data/x/y/z"; anything outside the two areas → IllegalArgumentException. */
    static String check(String path) {
        if (path == null || path.isEmpty() || path.length() > 4096 || path.indexOf('\0') >= 0) throw new IllegalArgumentException("Caminho inválido.");
        String p = path.startsWith("/sdcard/") || path.equals("/sdcard") ? roots[0] + path.substring(7) : path;
        if (!p.startsWith("/")) throw new IllegalArgumentException("Caminho inválido.");
        Deque<String> parts = new ArrayDeque<>();
        for (String s : p.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) throw new IllegalArgumentException("Caminho inválido.");
            parts.addLast(s);
        }
        String normal = "/" + String.join("/", parts);
        for (String root : roots) for (String area : AREAS) {
            String base = root + "/" + area;
            if (normal.equals(base) || normal.startsWith(base + "/")) return normal;
        }
        throw new IllegalArgumentException("Só as pastas Android/data e Android/obb são permitidas.");
    }
    static boolean allowed(String path) { try { check(path); return true; } catch (IllegalArgumentException e) { return false; } }
    /** The area a path is in ("/storage/emulated/0/Android/data"), or "". */
    static String area(String path) {
        String p = check(path);
        for (String root : roots) for (String a : AREAS) { String base = root + "/" + a; if (p.equals(base) || p.startsWith(base + "/")) return base; }
        return "";
    }
    static boolean isArea(String path) { return check(path).equals(area(path)); }
    /** A folder's child by a safe name. */
    static String child(String folder, String name) {
        if (name == null || name.isEmpty() || name.length() > 255 || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Nome inválido.");
        return check(check(folder) + "/" + name);
    }
    /** The parent while still inside the area; null at the area itself. */
    static String parent(String path) {
        String p = check(path);
        if (p.equals(area(p))) return null;
        return p.substring(0, p.lastIndexOf('/'));
    }
    /** "Android/data/com.app/files" for titles and messages. */
    static String display(String path) {
        String p = check(path);
        for (String root : roots) if (p.startsWith(root + "/")) return p.substring(root.length() + 1);
        return p;
    }
    /** The app's package of a path ("com.app"), or "" at the area level. */
    static String packageOf(String path) {
        String p = check(path), a = area(p);
        if (p.equals(a)) return "";
        String rest = p.substring(a.length() + 1); int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /** One file or folder as listed by the service. */
    static final class Entry {
        final String name; final boolean folder; final long size, modified;
        Entry(String name, boolean folder, long size, long modified) { this.name = name; this.folder = folder; this.size = size; this.modified = modified; }
    }
    /** Folders first, then by name (case and accents ignored). */
    static void sort(List<Entry> list) {
        java.text.Collator c = java.text.Collator.getInstance(new Locale("pt", "PT")); c.setStrength(java.text.Collator.PRIMARY);
        list.sort((a, b) -> a.folder != b.folder ? (a.folder ? -1 : 1) : c.compare(a.name, b.name));
    }
    /** A name that does not exist yet in the folder: "save.dat" → "save (2).dat". */
    static String free(String name, Set<String> taken) {
        if (!taken.contains(name)) return name;
        int dot = name.lastIndexOf('.'); String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 10000; i++) { String n = base + " (" + i + ")" + ext; if (!taken.contains(n)) return n; }
        return base + " (" + System.currentTimeMillis() + ")" + ext;
    }
}
