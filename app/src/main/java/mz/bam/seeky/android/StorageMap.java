package mz.bam.seeky.android;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.*;

/**
 * Mapa do armazenamento: que pastas ocupam mais espaço (com barras e navegação para dentro) e
 * ficheiros grandes que não são alterados há mais de 6 meses. Usa o catálogo do Seeky, por isso
 * abre num instante mesmo com muitos ficheiros.
 */
final class StorageMap {
    static final long BIG = 50L * 1024 * 1024, OLD = 180L * 86400000L;

    private StorageMap() {}

    static final class Model {
        final Map<String, Long> total = new HashMap<>(), direct = new HashMap<>();
        final Map<String, Set<String>> children = new HashMap<>();
        final List<String> roots = new ArrayList<>();
        final List<File> oldBig = new ArrayList<>();
        final List<Long> oldBigSizes = new ArrayList<>();
        long oldBigBytes;
    }

    static Model model;

    /** Agrega os tamanhos do catálogo por pasta, até à raiz de cada volume. */
    static Model build(Catalog db, List<File> volumes, long now) {
        Model m = new Model();
        List<String> roots = new ArrayList<>();
        for (File v : volumes) roots.add(v.getPath());
        roots.sort((x, y) -> y.length() - x.length());
        List<Object[]> big = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT path,size,modified FROM files WHERE path IS NOT NULL AND path!=''", null)) {
            while (c.moveToNext()) {
                String path = c.getString(0);
                long size = Math.max(0, c.getLong(1)), modified = c.getLong(2);
                String root = null;
                for (String r : roots) if (path.startsWith(r + "/")) { root = r; break; }
                if (root == null) continue;
                if (!m.roots.contains(root)) m.roots.add(root);
                String folder = parent(path);
                m.direct.merge(folder, size, Long::sum);
                String child = null;
                for (String f = folder; f != null; f = f.equals(root) ? null : parent(f)) {
                    m.total.merge(f, size, Long::sum);
                    if (child != null) m.children.computeIfAbsent(f, k -> new HashSet<>()).add(child);
                    child = f;
                    if (f.length() <= root.length()) break;
                }
                if (size >= BIG && modified > 0 && now - modified > OLD) big.add(new Object[]{path, size});
            }
        }
        big.sort((x, y) -> Long.compare((Long) y[1], (Long) x[1]));
        for (int i = 0; i < Math.min(30, big.size()); i++) { m.oldBig.add(new File((String) big.get(i)[0])); m.oldBigSizes.add((Long) big.get(i)[1]); m.oldBigBytes += (Long) big.get(i)[1]; }
        m.roots.sort((x, y) -> Long.compare(m.total.getOrDefault(y, 0L), m.total.getOrDefault(x, 0L)));
        return m;
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
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
        if (folder == null && m.roots.size() == 1) folder = m.roots.get(0);
        String name = folder == null ? AppLanguage.text(a, "Armazenamento") : m.roots.contains(folder) ? AppLanguage.text(a, "Memória do telefone") : new File(folder).getName();
        long total = folder == null ? 0 : m.total.getOrDefault(folder, 0L);
        if (folder == null) for (String r : m.roots) total += m.total.getOrDefault(r, 0L);
        UiPanel p = new UiPanel(a, name, Rules.bytes(total) + " " + AppLanguage.text(a, "no catálogo"), "storage");
        final String current = folder;
        if (folder != null && !m.roots.contains(folder)) p.action("back", "Pasta anterior", parent(folder), false, () -> open(a, parent(current)));
        List<String> list = new ArrayList<>(folder == null ? m.roots : m.children.getOrDefault(folder, Collections.emptySet()));
        list.sort((x, y) -> Long.compare(m.total.getOrDefault(y, 0L), m.total.getOrDefault(x, 0L)));
        if (!list.isEmpty()) p.section(folder == null ? "Volumes" : "Pastas");
        long max = 1;
        for (String f : list) max = Math.max(max, m.total.getOrDefault(f, 0L));
        for (int i = 0; i < Math.min(40, list.size()); i++) {
            String f = list.get(i);
            p.content.addView(row(a, p, m.roots.contains(f) ? f : new File(f).getName(), m.total.getOrDefault(f, 0L), total == 0 ? 0 : m.total.getOrDefault(f, 0L) / (float) Math.max(1, total), () -> { p.dialog.dismiss(); open(a, f); }));
        }
        if (folder != null) {
            long here = m.direct.getOrDefault(folder, 0L);
            if (here > 0) p.note(AppLanguage.text(a, "Ficheiros directamente nesta pasta:") + " " + Rules.bytes(here));
            p.action("folder", "Abrir no Explorar", "Ver e gerir os ficheiros desta pasta", false, () -> explore(a, current));
        }
        if (folder == null || m.roots.contains(folder)) {
            p.section("Grandes e antigos");
            if (m.oldBig.isEmpty()) p.note("Nenhum ficheiro com mais de 50 MB sem alterações há mais de 6 meses.");
            else {
                for (int i = 0; i < Math.min(8, m.oldBig.size()); i++) {
                    File f = m.oldBig.get(i);
                    p.note(f.getName() + " · " + Rules.bytes(m.oldBigSizes.get(i)) + " · " + parent(f.getPath()));
                }
                p.action("trash", "Rever " + m.oldBig.size() + " ficheiros grandes e antigos", Rules.bytes(m.oldBigBytes) + " · " + AppLanguage.text(a, "vão para a lixeira com confirmação"), false, () -> {
                    List<Catalog.Entry> entries = new ArrayList<>();
                    for (File f : m.oldBig) {
                        File root = rootOf(a, f);
                        if (f.isFile() && root != null) entries.add(StorageAccess.entry(f, root));
                    }
                    if (entries.isEmpty()) a.toast("Os ficheiros já não existem.");
                    else a.confirmDelete(entries);
                });
            }
        }
        p.show();
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

    static View row(MainActivity a, UiPanel p, String name, long bytes, float fraction, Runnable click) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(p.dp(12), p.dp(10), p.dp(12), p.dp(10));
        GradientDrawable bg = p.shape(p.surface, 14);
        bg.setStroke(p.dp(1), AppPalette.of(a).line);
        box.setBackground(bg);
        LinearLayout top = new LinearLayout(a);
        TextView n = p.text("", 14, p.ink);
        n.setText(name);
        n.setTypeface(ThemeStyle.BOLD);
        n.setMaxLines(1);
        n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        top.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
        TextView size = p.text(Rules.bytes(bytes) + " · " + Math.round(fraction * 100) + "%", 12, p.muted);
        size.setGravity(Gravity.END);
        top.addView(size);
        box.addView(top);
        FrameLayout track = new FrameLayout(a);
        track.setBackground(p.shape(AppPalette.of(a).tint, 4));
        View fill = new View(a);
        fill.setBackground(p.shape(p.accent, 4));
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        track.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = Math.max(p.dp(4), Math.round((r - l) * Math.min(1f, fraction)));
            if (fill.getLayoutParams().width != w) { fill.getLayoutParams().width = w; fill.requestLayout(); }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, p.dp(6));
        tp.topMargin = p.dp(8);
        box.addView(track, tp);
        box.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = p.dp(8);
        box.setLayoutParams(lp);
        return box;
    }
}
