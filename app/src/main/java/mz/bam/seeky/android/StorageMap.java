package mz.bam.seeky.android;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import java.io.File;
import java.util.*;

/**
 * Mapa do armazenamento: resumo do telefone, espaço por tipo de ficheiro, volumes com nomes
 * legíveis (Memória interna, Cartão SD), pastas maiores com navegação e ficheiros grandes que não
 * mudam há mais de 6 meses. Usa o catálogo do Seeky, por isso abre num instante.
 */
final class StorageMap {
    static final long BIG = 50L * 1024 * 1024, OLD = 180L * 86400000L;
    static final String[] TYPES = {"Vídeos", "Áudio", "Imagens", "Documentos", "PDF", "Aplicações", "Arquivos", "Outros"};

    private StorageMap() {}

    static final class Old { final File file; final long size, modified; final String category; Old(File f, long s, long m, String c) { file = f; size = s; modified = m; category = c; } }

    static final class Model {
        final Map<String, Long> total = new HashMap<>(), direct = new HashMap<>();
        final Map<String, Integer> count = new HashMap<>();
        final Map<String, Set<String>> children = new HashMap<>();
        final Map<String, long[]> types = new HashMap<>();
        final List<String> roots = new ArrayList<>();
        final List<Old> old = new ArrayList<>();
        long oldBytes, files;
    }

    static Model model;

    /** Agrega os tamanhos do catálogo por pasta (até à raiz de cada volume) e por tipo. */
    static Model build(Catalog db, List<File> volumes, long now) {
        Model m = new Model();
        List<String> roots = new ArrayList<>();
        for (File v : volumes) roots.add(v.getPath());
        roots.sort((x, y) -> y.length() - x.length());
        List<Old> old = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT path,size,modified,category FROM files WHERE path IS NOT NULL AND path!=''", null)) {
            while (c.moveToNext()) {
                String path = c.getString(0), category = c.isNull(3) ? "Outros" : c.getString(3);
                long size = Math.max(0, c.getLong(1)), modified = c.getLong(2);
                String root = null;
                for (String r : roots) if (path.startsWith(r + "/")) { root = r; break; }
                if (root == null) continue;
                if (!m.roots.contains(root)) m.roots.add(root);
                m.files++;
                long[] t = m.types.computeIfAbsent(Arrays.asList(TYPES).contains(category) ? category : "Outros", k -> new long[2]);
                t[0] += size;
                t[1]++;
                String folder = parent(path);
                m.direct.merge(folder, size, Long::sum);
                String child = null;
                for (String f = folder; f != null; f = f.equals(root) ? null : parent(f)) {
                    m.total.merge(f, size, Long::sum);
                    m.count.merge(f, 1, Integer::sum);
                    if (child != null) m.children.computeIfAbsent(f, k -> new HashSet<>()).add(child);
                    child = f;
                    if (f.length() <= root.length()) break;
                }
                if (size >= BIG && modified > 0 && now - modified > OLD) old.add(new Old(new File(path), size, modified, category));
            }
        }
        old.sort((x, y) -> Long.compare(y.size, x.size));
        for (int i = 0; i < Math.min(30, old.size()); i++) { m.old.add(old.get(i)); m.oldBytes += old.get(i).size; }
        m.roots.sort((x, y) -> Long.compare(m.total.getOrDefault(y, 0L), m.total.getOrDefault(x, 0L)));
        return m;
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    static boolean internal(String root) {
        try { return new File(root).getCanonicalPath().equals(Environment.getExternalStorageDirectory().getCanonicalPath()); } catch (Exception e) { return false; }
    }

    /** Nome do volume como o Android o mostra (Memória interna, Cartão SD, USB…). */
    static String volumeName(Context c, String root) {
        if (internal(root)) return AppLanguage.text(c, "Memória interna");
        try {
            StorageVolume v = c.getSystemService(StorageManager.class).getStorageVolume(new File(root));
            if (v != null && v.getDescription(c) != null) return v.getDescription(c);
        } catch (Exception ignored) {}
        return AppLanguage.text(c, "Cartão SD");
    }

    /** Caminho legível: "Memória interna › Android › media". */
    static String pretty(Context c, Model m, String path) {
        for (String r : m.roots) if (path.equals(r) || path.startsWith(r + "/")) {
            String rest = path.substring(r.length()).replaceFirst("^/", "");
            return volumeName(c, r) + (rest.isEmpty() ? "" : " › " + rest.replace("/", " › "));
        }
        return path;
    }

    /** Pasta abreviada para listas: no máximo as duas últimas partes. */
    static String shortFolder(Context c, Model m, String folder) {
        String full = pretty(c, m, folder);
        String[] parts = full.split(" › ");
        if (parts.length <= 3) return full;
        return parts[0] + " › … › " + parts[parts.length - 2] + " › " + parts[parts.length - 1];
    }

    static String rootOfPath(Model m, String path) {
        for (String r : m.roots) if (path.equals(r) || path.startsWith(r + "/")) return r;
        return null;
    }

    static void show(MainActivity a) {
        a.toast("A calcular…");
        a.finder.execute(() -> {
            try {
                Model m = build(a.db, StorageAccess.volumes(a), System.currentTimeMillis());
                a.ui(() -> { model = m; open(a, null); });
            } catch (Exception e) { a.ui(() -> a.showError(e)); }
        });
    }

    static void open(MainActivity a, String folder) {
        Model m = model;
        if (m == null) return;
        if (m.roots.isEmpty()) {
            a.dialog().setTitle("Mapa do armazenamento").setMessage("O catálogo ainda está vazio. Active o acesso aos ficheiros e espere pela primeira análise.").setPositiveButton("Fechar", null).show();
            return;
        }
        if (folder == null) overview(a, m);
        else a.finder.execute(() -> {
            List<Catalog.Entry> files = largest(a, folder);
            a.ui(() -> folderPanel(a, m, folder, files));
        });
    }

    // ---- visão geral ----

    static void overview(MainActivity a, Model m) {
        UiPanel p = new UiPanel(a, "Mapa do armazenamento", m.files + " " + AppLanguage.text(a, "ficheiros no catálogo"), "storage");
        String main = m.roots.get(0);
        for (String r : m.roots) if (internal(r)) main = r;
        long used = 0, size = 0;
        try { StatFs fs = new StatFs(main); size = fs.getTotalBytes(); used = size - fs.getAvailableBytes(); } catch (Exception ignored) {}
        long catalog = 0;
        for (String r : m.roots) catalog += m.total.getOrDefault(r, 0L);
        p.content.addView(UiRows.hero(p, a, volumeName(a, main),
                size > 0 ? Rules.bytes(used) + " " + AppLanguage.text(a, "de") + " " + Rules.bytes(size) : Rules.bytes(catalog),
                size > 0 ? Rules.bytes(size - used) + " " + AppLanguage.text(a, "livres") + " · " + Math.round(used * 100.0 / size) + "% " + AppLanguage.text(a, "ocupado") : "",
                size > 0 ? used / (float) size : -1, p.accent,
                new String[][]{{Rules.bytes(catalog), AppLanguage.text(a, "nos seus ficheiros")}, {String.valueOf(m.files), AppLanguage.text(a, "ficheiros")},
                        {Rules.bytes(m.oldBytes), AppLanguage.text(a, "grandes e antigos")}}));

        p.section("Por tipo");
        List<String> types = new ArrayList<>();
        for (String t : TYPES) if (m.types.containsKey(t) && m.types.get(t)[0] > 0) types.add(t);
        types.sort((x, y) -> Long.compare(m.types.get(y)[0], m.types.get(x)[0]));
        String[] labels = new String[types.size()];
        long[] values = new long[types.size()];
        int[] colors = new int[types.size()];
        for (int i = 0; i < types.size(); i++) { labels[i] = AppLanguage.text(a, types.get(i)); values[i] = m.types.get(types.get(i))[0]; colors[i] = UiRows.color(types.get(i)); }
        if (!types.isEmpty()) p.content.addView(UiRows.stacked(p, a, labels, values, colors));
        for (String t : types) {
            long[] v = m.types.get(t);
            p.content.addView(UiRows.meter(p, a, Icons.category(t), AppLanguage.text(a, t), Rules.bytes(v[0]), UiRows.count(a, v[1], "ficheiro", "ficheiros"),
                    catalog == 0 ? 0 : v[0] / (float) catalog, UiRows.color(t), () -> { p.dialog.dismiss(); byType(a, t); }));
        }

        if (m.roots.size() > 1) {
            p.section("Volumes");
            for (String r : m.roots) {
                long t = m.total.getOrDefault(r, 0L);
                String free = "";
                try { StatFs fs = new StatFs(r); free = " · " + Rules.bytes(fs.getAvailableBytes()) + " " + AppLanguage.text(a, "livres"); } catch (Exception ignored) {}
                p.content.addView(UiRows.meter(p, a, internal(r) ? "phone" : "storage", volumeName(a, r), Rules.bytes(t),
                        UiRows.count(a, m.count.getOrDefault(r, 0), "ficheiro", "ficheiros") + free, catalog == 0 ? 0 : t / (float) catalog, p.accent, () -> { p.dialog.dismiss(); open(a, r); }));
            }
        }

        p.section("Pastas maiores");
        List<String> top = new ArrayList<>(m.children.getOrDefault(main, Collections.emptySet()));
        top.sort((x, y) -> Long.compare(m.total.getOrDefault(y, 0L), m.total.getOrDefault(x, 0L)));
        long mainTotal = Math.max(1, m.total.getOrDefault(main, 0L));
        for (int i = 0; i < Math.min(8, top.size()); i++) {
            String f = top.get(i);
            p.content.addView(folderRow(a, p, m, f, mainTotal));
        }
        final String mainRoot = main;
        p.action("folder", "Ver todas as pastas", volumeName(a, main), false, () -> open(a, mainRoot));

        if (!m.old.isEmpty()) {
            p.section("Grandes e antigos");
            p.note(AppLanguage.text(a, "Ficheiros com mais de 50 MB sem alterações há mais de 6 meses. Toque para abrir, partilhar ou apagar."));
            for (int i = 0; i < Math.min(6, m.old.size()); i++) {
                Old o = m.old.get(i);
                p.content.addView(UiRows.file(p, a, o.file.getName(), o.category, o.size, o.modified, shortFolder(a, m, parent(o.file.getPath())), () -> { p.dialog.dismiss(); fileMenu(a, m, o.file); }));
            }
            p.action("trash", "Rever todos (" + m.old.size() + ")", Rules.bytes(m.oldBytes) + " · " + AppLanguage.text(a, "vão para a lixeira com confirmação"), false, () -> reviewOld(a, m));
        }
        p.show();
    }

    static android.view.View folderRow(MainActivity a, UiPanel p, Model m, String f, long parentTotal) {
        long t = m.total.getOrDefault(f, 0L);
        int files = m.count.getOrDefault(f, 0), sub = m.children.getOrDefault(f, Collections.emptySet()).size();
        String detail = UiRows.count(a, files, "ficheiro", "ficheiros") + (sub > 0 ? " · " + UiRows.count(a, sub, "pasta", "pastas") : "");
        return UiRows.meter(p, a, "folder", new File(f).getName(), Rules.bytes(t), detail + " · " + Math.round(t * 100.0 / Math.max(1, parentTotal)) + "%",
                t / (float) Math.max(1, parentTotal), p.accent, () -> { p.dialog.dismiss(); open(a, f); });
    }

    // ---- pasta ----

    static List<Catalog.Entry> largest(MainActivity a, String folder) {
        List<Catalog.Entry> out = new ArrayList<>();
        File root = null;
        for (File v : StorageAccess.volumes(a)) if (folder.equals(v.getPath()) || folder.startsWith(v.getPath() + "/")) root = v;
        if (root == null) return out;
        try (Cursor c = a.db.getReadableDatabase().rawQuery("SELECT path FROM files WHERE parent=? ORDER BY size DESC LIMIT 12", new String[]{folder})) {
            while (c.moveToNext()) {
                File f = new File(c.getString(0));
                if (f.isFile()) out.add(StorageAccess.entry(f, root));
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void folderPanel(MainActivity a, Model m, String folder, List<Catalog.Entry> files) {
        String root = rootOfPath(m, folder);
        boolean isRoot = folder.equals(root);
        UiPanel p = new UiPanel(a, isRoot ? volumeName(a, folder) : new File(folder).getName(), pretty(a, m, folder), isRoot ? (internal(folder) ? "phone" : "storage") : "folder");
        long t = m.total.getOrDefault(folder, 0L), rootTotal = root == null ? t : Math.max(1, m.total.getOrDefault(root, 0L));
        p.content.addView(UiRows.hero(p, a, isRoot ? AppLanguage.text(a, "Volume") : AppLanguage.text(a, "Pasta"), Rules.bytes(t),
                isRoot ? "" : Math.round(t * 100.0 / rootTotal) + "% " + AppLanguage.text(a, "de") + " " + volumeName(a, root),
                isRoot ? -1 : t / (float) rootTotal, p.accent,
                new String[][]{{String.valueOf(m.count.getOrDefault(folder, 0)), AppLanguage.text(a, "ficheiros")},
                        {String.valueOf(m.children.getOrDefault(folder, Collections.emptySet()).size()), AppLanguage.text(a, "pastas")},
                        {Rules.bytes(m.direct.getOrDefault(folder, 0L)), AppLanguage.text(a, "nesta pasta")}}));
        final String up = parent(folder);
        p.quick(new String[]{"back", "folder", "storage"}, new String[]{isRoot ? "Resumo" : "Voltar", "Explorar", "Resumo"},
                new Runnable[]{() -> open(a, isRoot ? null : up), () -> explore(a, folder), () -> open(a, null)});
        List<String> list = new ArrayList<>(m.children.getOrDefault(folder, Collections.emptySet()));
        list.sort((x, y) -> Long.compare(m.total.getOrDefault(y, 0L), m.total.getOrDefault(x, 0L)));
        if (!list.isEmpty()) p.section("Pastas");
        for (int i = 0; i < Math.min(30, list.size()); i++) p.content.addView(folderRow(a, p, m, list.get(i), Math.max(1, t)));
        if (list.size() > 30) p.note((list.size() - 30) + " " + AppLanguage.text(a, "pastas mais pequenas não mostradas."));
        if (!files.isEmpty()) {
            p.section("Maiores ficheiros desta pasta");
            for (Catalog.Entry e : files) p.content.addView(UiRows.file(p, a, e.name, e.category, e.size, e.modified, null, () -> { p.dialog.dismiss(); a.fileMenu(e); }));
        }
        p.show();
    }

    static void fileMenu(MainActivity a, Model m, File f) {
        String root = rootOfPath(m, f.getPath());
        if (root == null || !f.isFile()) { a.toast("O ficheiro já não existe."); return; }
        a.fileMenu(StorageAccess.entry(f, new File(root)));
    }

    static void reviewOld(MainActivity a, Model m) {
        List<Catalog.Entry> entries = new ArrayList<>();
        for (Old o : m.old) {
            String root = rootOfPath(m, o.file.getPath());
            if (o.file.isFile() && root != null) entries.add(StorageAccess.entry(o.file, new File(root)));
        }
        if (entries.isEmpty()) a.toast("Os ficheiros já não existem.");
        else a.confirmDelete(entries);
    }

    static void byType(MainActivity a, String type) {
        a.query = "";
        a.category = type.equals("Outros") ? "Tudo" : type;
        a.minSize = 0;
        a.days = 0;
        a.extension = "";
        a.sort = 2;
        a.tab = "Pesquisa";
        a.render();
    }

    static File rootOf(Context c, File f) {
        for (File v : StorageAccess.volumes(c)) if (f.getPath().startsWith(v.getPath() + "/")) return v;
        return null;
    }

    static void explore(MainActivity a, String folder) {
        File f = new File(folder);
        File root = rootOf(a, new File(f, "x"));
        if (root == null || !f.isDirectory()) { a.toast("Pasta indisponível."); return; }
        a.browseRoot = root;
        a.browseFolder = f;
        a.tab = "Explorar";
        a.render();
    }
}
