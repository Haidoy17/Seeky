package mz.bam.seeky.android;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.Settings;
import java.io.File;
import java.util.*;

/**
 * Limpeza: cache do Seeky, miniaturas da galeria, APKs duplicados ou já instalados, ficheiros
 * duplicados e atalhos do Android para libertar espaço.
 *
 * <p>Ficheiros do utilizador (APKs, duplicados) vão sempre para a lixeira do Seeky com
 * confirmação — nunca são apagados de imediato. Só a cache e as miniaturas (que o Android e o
 * Seeky voltam a criar) são removidas directamente. A cache de outras aplicações só pode ser
 * limpa pelo próprio Android, por isso o Seeky abre as definições certas.
 */
final class StorageCleaner {
    private StorageCleaner() {}

    /** Relatório dos APKs: duplicados (mesmo pacote e versão) e já instalados (versão igual ou anterior). */
    static final class ApkReport {
        final List<Catalog.Entry> removable = new ArrayList<>();
        int installed, duplicates;
        long bytes;
    }

    static void show(MainActivity a) {
        a.toast("A analisar o armazenamento…");
        a.finder.execute(() -> {
            long cache = size(a.getCacheDir()) + size(a.getExternalCacheDir());
            long thumbs = 0;
            if (StorageAccess.all(a)) for (File dir : thumbnailDirs()) thumbs += size(dir);
            ApkReport apks = scanApks(a);
            final long thumbBytes = thumbs;
            a.ui(() -> panel(a, cache, thumbBytes, apks));
        });
    }

    static void panel(MainActivity a, long cache, long thumbs, ApkReport apks) {
        UiPanel p = new UiPanel(a, "Limpeza", AppLanguage.text(a, "Liberte espaço com segurança"), "trash");
        p.section("Seeky");
        p.action("trash", "Limpar cache do Seeky", Rules.bytes(cache) + " · " + AppLanguage.text(a, "pré-visualizações e ficheiros temporários"), false, () -> clearCache(a));
        p.section("Ficheiros");
        p.action("android", "APKs duplicados ou já instalados",
                apks.removable.isEmpty() ? AppLanguage.text(a, "Nada a limpar") : apks.removable.size() + " · " + Rules.bytes(apks.bytes) + " · " + apks.installed + " " + AppLanguage.text(a, "já instalados") + ", " + apks.duplicates + " " + AppLanguage.text(a, "duplicados"),
                false, () -> {
                    if (apks.removable.isEmpty()) a.toast("Não há APKs duplicados nem já instalados.");
                    else a.confirmDelete(new ArrayList<>(apks.removable));
                });
        p.action("copy", "Ficheiros duplicados", "Encontrar cópias iguais e escolher qual conservar", false, a::duplicates);
        p.action("image", "Miniaturas da galeria", (StorageAccess.all(a) ? Rules.bytes(thumbs) : AppLanguage.text(a, "Requer acesso aos ficheiros")) + " · " + AppLanguage.text(a, "o Android volta a criá-las"), false, () -> clearThumbnails(a));
        p.section("Android");
        p.action("storage", "Libertar espaço no telefone", "Ferramenta de limpeza do Android", false, () -> openSystem(a, true));
        p.action("settings", "Cache de outras aplicações", "Só o Android pode limpá-la: abre as definições de armazenamento", false, () -> openSystem(a, false));
        p.note("APKs e duplicados vão para a lixeira do Seeky, de onde ainda podem ser recuperados.");
        p.show();
    }

    // ---- Cache do Seeky ----

    static void clearCache(MainActivity a) {
        a.finder.execute(() -> {
            long before = size(a.getCacheDir()) + size(a.getExternalCacheDir());
            long cutoff = System.currentTimeMillis() - 60_000; // não mexe no que está a ser usado agora
            clear(a.getCacheDir(), cutoff);
            clear(a.getExternalCacheDir(), cutoff);
            long freed = Math.max(0, before - size(a.getCacheDir()) - size(a.getExternalCacheDir()));
            a.ui(() -> a.toast(AppLanguage.text(a, "Cache limpa:") + " " + Rules.bytes(freed)));
        });
    }

    static void clearThumbnails(MainActivity a) {
        if (!StorageAccess.all(a)) { a.requestGlobal(); return; }
        a.dialog().setTitle("Miniaturas da galeria").setMessage("Apagar as miniaturas guardadas pela galeria? As fotos não são afectadas e o Android volta a criar as miniaturas quando forem precisas.")
                .setPositiveButton("Apagar miniaturas", (d, w) -> a.finder.execute(() -> {
                    long freed = 0;
                    for (File dir : thumbnailDirs()) { long s = size(dir); clear(dir, Long.MAX_VALUE); freed += s - size(dir); }
                    final long total = Math.max(0, freed);
                    a.ui(() -> a.toast(AppLanguage.text(a, "Miniaturas apagadas:") + " " + Rules.bytes(total)));
                })).setNegativeButton("Cancelar", null).show();
    }

    static List<File> thumbnailDirs() {
        File root = Environment.getExternalStorageDirectory();
        List<File> out = new ArrayList<>();
        for (String path : new String[]{"DCIM/.thumbnails", "Pictures/.thumbnails", "Movies/.thumbnails"}) {
            File f = new File(root, path);
            if (f.isDirectory()) out.add(f);
        }
        return out;
    }

    // ---- APKs ----

    @SuppressWarnings("deprecation")
    static ApkReport scanApks(MainActivity a) {
        ApkReport report = new ApkReport();
        List<Catalog.Entry> entries;
        try { entries = a.db.query("", "Aplicações", false, false, 0, 3000); } catch (Exception e) { return report; }
        PackageManager pm = a.getPackageManager();
        Map<String, List<Catalog.Entry>> groups = new HashMap<>();
        Map<String, Long> versions = new HashMap<>();
        for (Catalog.Entry e : entries) {
            if (e.path == null || !e.name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
            File f = new File(e.path);
            if (!f.isFile()) continue;
            PackageInfo info;
            try { info = pm.getPackageArchiveInfo(f.getPath(), 0); } catch (Exception ex) { info = null; }
            if (info == null || info.packageName == null) continue;
            long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            String key = info.packageName + ":" + version;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            versions.put(key, version);
        }
        for (Map.Entry<String, List<Catalog.Entry>> g : groups.entrySet()) {
            String pkg = g.getKey().substring(0, g.getKey().lastIndexOf(':'));
            long installed = -1;
            try {
                PackageInfo i = pm.getPackageInfo(pkg, 0);
                installed = Build.VERSION.SDK_INT >= 28 ? i.getLongVersionCode() : i.versionCode;
            } catch (Exception ignored) {}
            List<Catalog.Entry> copies = g.getValue();
            copies.sort((x, y) -> Long.compare(y.modified, x.modified)); // mais recente primeiro
            if (installed >= versions.get(g.getKey())) {
                report.installed += copies.size();
                for (Catalog.Entry e : copies) add(report, e);
            } else if (copies.size() > 1) {
                report.duplicates += copies.size() - 1;
                for (int i = 1; i < copies.size(); i++) add(report, copies.get(i));
            }
        }
        return report;
    }

    static void add(ApkReport r, Catalog.Entry e) { r.removable.add(e); r.bytes += Math.max(0, e.size); }

    // ---- Android ----

    static void openSystem(MainActivity a, boolean free) {
        try {
            a.startActivity(new Intent(free ? StorageManager.ACTION_MANAGE_STORAGE : Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        } catch (Exception e) {
            try { a.startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); } catch (Exception again) { a.showError(again); }
        }
    }

    // ---- ficheiros ----

    static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] list = f.listFiles();
        if (list != null) for (File c : list) total += size(c);
        return total;
    }

    /** Apaga o conteúdo (não a pasta), ignorando o que foi alterado depois de {@code cutoff}. */
    static void clear(File dir, long cutoff) {
        if (dir == null || !dir.isDirectory()) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) { clear(f, cutoff); f.delete(); }
            else if (f.lastModified() < cutoff) f.delete();
        }
    }
}
