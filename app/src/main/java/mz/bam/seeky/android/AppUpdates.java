package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/**
 * Actualizações dentro da aplicação, a partir do repositório público do Seeky no GitHub.
 *
 * <p>O ficheiro {@code entrega/latest.json} indica a versão mais recente, o link do APK e o
 * SHA-256. Antes de abrir o instalador do Android, o Seeky confirma: (1) HTTPS e anfitriões do
 * GitHub apenas; (2) tamanho e SHA-256 iguais aos anunciados; (3) o APK é o mesmo pacote, de
 * versão superior, assinado com o MESMO certificado da aplicação instalada. Um ficheiro
 * adulterado ou assinado por outra chave nunca chega ao instalador.
 */
final class AppUpdates {
    static final String MANIFEST = "https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/latest.json";
    static final Set<String> HOSTS = new HashSet<>(Arrays.asList("raw.githubusercontent.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com"));
    static final long DAY = 24L * 60 * 60 * 1000, MAX_APK = 300L * 1024 * 1024;
    static final String PREFS = "updates";

    private AppUpdates() {}

    /** Descrição de uma versão publicada. */
    static final class Release {
        final long versionCode, bytes;
        final String versionName, apk, sha256, notes;
        Release(long code, String name, String apk, String sha256, long bytes, String notes) {
            versionCode = code; versionName = name; this.apk = apk; this.sha256 = sha256; this.bytes = bytes; this.notes = notes;
        }
    }

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean automatic(Context c) { return prefs(c).getBoolean("automatic", true); }

    static long installedVersion(Context c) {
        try {
            PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception e) { return 0; }
    }

    /** Só HTTPS para os anfitriões do GitHub, sem credenciais nem portas estranhas. */
    static boolean allowed(String url) {
        try {
            URL u = new URL(url);
            return "https".equals(u.getProtocol()) && HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT)) && u.getUserInfo() == null && (u.getPort() == -1 || u.getPort() == 443);
        } catch (Exception e) { return false; }
    }

    /** Valida o JSON publicado; recusa campos em falta, links fora do GitHub ou SHA-256 mal formado. */
    static Release parse(String json) throws Exception {
        if (json.length() > 65536) throw new IOException("Manifesto demasiado grande.");
        JsonBudget.check(json, 4, 16);
        JSONObject o = new JSONObject(json);
        long code = o.getLong("versionCode"), bytes = o.optLong("bytes", -1);
        String name = o.getString("versionName"), apk = o.getString("apk"), sha = o.getString("sha256").toLowerCase(Locale.ROOT);
        if (code <= 0 || name.length() > 40 || !allowed(apk) || !sha.matches("[0-9a-f]{64}") || bytes > MAX_APK) throw new IOException("Manifesto de actualização inválido.");
        String notes = o.optString("notes", "");
        if (notes.length() > 2000) notes = notes.substring(0, 2000);
        return new Release(code, name, apk, sha, bytes, notes);
    }

    static HttpURLConnection open(String url) throws IOException {
        String current = url;
        for (int hops = 0; hops < 4; hops++) {
            if (!allowed(current)) throw new IOException("Ligação de actualização recusada.");
            HttpsURLConnection c = (HttpsURLConnection) new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json, application/octet-stream");
            c.setRequestProperty("User-Agent", "Seeky-Android");
            int status = c.getResponseCode();
            if (status / 100 == 3) {
                String next = c.getHeaderField("Location");
                c.disconnect();
                if (next == null) throw new IOException("Redireccionamento inválido.");
                current = new URL(new URL(current), next).toString();
                continue;
            }
            if (status != 200) { c.disconnect(); throw new IOException("Servidor de actualizações respondeu " + status + "."); }
            return c;
        }
        throw new IOException("Demasiados redireccionamentos.");
    }

    static Release fetch() throws Exception {
        HttpURLConnection c = open(MANIFEST + "?t=" + System.currentTimeMillis() / 60000);
        try (InputStream in = c.getInputStream()) {
            return parse(new String(ContentText.read(in, 65536), "UTF-8"));
        } finally { c.disconnect(); }
    }

    // ---- Verificação automática e manual ----

    /** Ao abrir a aplicação: no máximo uma vez por dia, em silêncio se não houver nada. */
    static void maybeCheck(MainActivity a) {
        if (!automatic(a)) return;
        if (installedVersion(a) <= 0) return; // versão própria desconhecida: não há com que comparar
        long last = prefs(a).getLong("last_check", 0), now = System.currentTimeMillis();
        if (now - last < DAY && now >= last) return;
        prefs(a).edit().putLong("last_check", now).apply();
        check(a, false);
    }

    static void check(Activity a, boolean manual) {
        if (manual) Toast.makeText(a, AppLanguage.text(a, "A procurar actualizações…"), Toast.LENGTH_SHORT).show();
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                Release r = fetch();
                long installed = installedVersion(a);
                ui.post(() -> {
                    if (a.isFinishing() || a.isDestroyed()) return;
                    if (r.versionCode <= installed) {
                        if (manual) Toast.makeText(a, AppLanguage.text(a, "O Seeky está actualizado."), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!manual && prefs(a).getLong("snooze_" + r.versionCode, 0) > System.currentTimeMillis()) return;
                    offer(a, r);
                });
            } catch (Exception e) {
                if (manual) ui.post(() -> { if (!a.isFinishing()) Toast.makeText(a, AppLanguage.text(a, "Não foi possível procurar actualizações. Verifique a Internet."), Toast.LENGTH_LONG).show(); });
            }
        }, "seeky-update-check").start();
    }

    static void offer(Activity a, Release r) {
        String size = r.bytes > 0 ? " · " + android.text.format.Formatter.formatShortFileSize(a, r.bytes) : "";
        String message = AppLanguage.text(a, "Nova versão disponível:") + " Seeky " + r.versionName + size
                + (r.notes.isEmpty() ? "" : "\n\n" + r.notes) + "\n\n" + AppLanguage.text(a, "Os seus livros, estudos e definições mantêm-se.");
        new UiDialog.Builder(a).setTitle("Actualização do Seeky").setMessage(message)
                .setPositiveButton("Actualizar agora", (d, w) -> download(a, r))
                .setNegativeButton("Mais tarde", (d, w) -> prefs(a).edit().putLong("snooze_" + r.versionCode, System.currentTimeMillis() + 3 * DAY).apply())
                .show();
    }

    // ---- Descarga, verificação e instalação ----

    static void download(Activity a, Release r) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = PlusUi.dp(a, 20);
        box.setPadding(pad, PlusUi.dp(a, 8), pad, 0);
        TextView label = new TextView(a);
        label.setText(AppLanguage.text(a, "A descarregar…"));
        label.setTextColor(AppPalette.of(a).ink);
        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        box.addView(label);
        box.addView(bar);
        final boolean[] cancelled = {false};
        android.app.AlertDialog dialog = new UiDialog.Builder(a).setTitle("Actualização do Seeky").setView(box)
                .setNegativeButton("Cancelar", (d, w) -> cancelled[0] = true).setCancelable(false).show();
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            File target = new File(ApkInstallActivity.directory(a), UUID.randomUUID() + ".apk");
            try {
                verifyDownload(a, r, target, cancelled, (done, total) -> ui.post(() -> {
                    if (total > 0) bar.setProgress((int) (1000L * done / total));
                    label.setText(AppLanguage.text(a, "A descarregar…") + " " + android.text.format.Formatter.formatShortFileSize(a, done)
                            + (total > 0 ? " / " + android.text.format.Formatter.formatShortFileSize(a, total) : ""));
                }));
                ui.post(() -> {
                    try { dialog.dismiss(); } catch (Exception ignored) {}
                    install(a, target);
                });
            } catch (Exception e) {
                target.delete();
                ui.post(() -> {
                    try { dialog.dismiss(); } catch (Exception ignored) {}
                    if (!cancelled[0]) new UiDialog.Builder(a).setTitle("Actualização do Seeky").setMessage(e.getMessage() == null ? AppLanguage.text(a, "A descarga falhou.") : e.getMessage()).setPositiveButton("Fechar", null).show();
                });
            }
        }, "seeky-update-download").start();
    }

    interface Progress { void update(long done, long total); }

    static void verifyDownload(Context c, Release r, File target, boolean[] cancelled, Progress progress) throws Exception {
        HttpURLConnection conn = open(r.apk);
        long total = conn.getContentLengthLong();
        if (r.bytes > 0 && total > 0 && total != r.bytes) { conn.disconnect(); throw new IOException("Tamanho do ficheiro inesperado."); }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long done = 0;
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) != -1; ) {
                if (cancelled[0]) throw new InterruptedIOException("Cancelado.");
                done += n;
                if (done > MAX_APK || (r.bytes > 0 && done > r.bytes)) throw new IOException("Ficheiro maior do que o anunciado.");
                out.write(buffer, 0, n);
                digest.update(buffer, 0, n);
                progress.update(done, total > 0 ? total : r.bytes);
            }
        } finally { conn.disconnect(); }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        if (!hex.toString().equals(r.sha256)) throw new IOException(AppLanguage.text(c, "O ficheiro descarregado não corresponde à versão publicada. Tente novamente."));
        verifyPackage(c, target, r.versionCode);
    }

    /**
     * O APK tem de ser o Seeky, mais recente e assinado com o mesmo certificado instalado.
     *
     * <p>No Android 9–12, {@code getPackageArchiveInfo} só devolve o certificado de um ficheiro
     * quando também se pede {@code GET_SIGNATURES}; pedir apenas {@code GET_SIGNING_CERTIFICATES}
     * dava uma lista vazia e a actualização era recusada como "não assinada" (corrigido na 5.23).
     * Lê-se agora o certificado por três vias (SigningInfo, Signature[] e assinatura v1 do JAR).
     * Se nenhuma funcionar no aparelho, a decisão fica com o instalador do Android, que recusa
     * sempre uma actualização assinada com outra chave.
     */
    @SuppressWarnings("deprecation")
    static void verifyPackage(Context c, File apk, long minimumVersion) throws Exception {
        PackageManager pm = c.getPackageManager();
        int flags = PackageManager.GET_SIGNATURES | (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : 0);
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getPath(), flags);
        if (archive == null) archive = pm.getPackageArchiveInfo(apk.getPath(), 0);
        if (archive == null) throw new IOException(AppLanguage.text(c, "O ficheiro descarregado não é um APK válido."));
        long version = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        if (!c.getPackageName().equals(archive.packageName) || version < minimumVersion || version <= installedVersion(c))
            throw new IOException(AppLanguage.text(c, "O APK descarregado não é uma versão mais recente do Seeky."));
        Set<String> mine = installedSigners(c);
        Set<String> theirs = signers(archive);
        if (theirs.isEmpty()) theirs = jarSigners(apk);
        if (theirs.isEmpty() || mine.isEmpty()) return; // o instalador do Android verifica a chave
        if (!sameSigner(theirs, mine))
            throw new IOException(AppLanguage.text(c, "O APK descarregado está assinado com outra chave e não foi instalado.")
                    + "\n\n" + AppLanguage.text(c, "Instalado:") + " " + shortPrint(mine) + "\n" + AppLanguage.text(c, "Descarregado:") + " " + shortPrint(theirs));
    }

    @SuppressWarnings("deprecation")
    static Set<String> installedSigners(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            int flags = PackageManager.GET_SIGNATURES | (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : 0);
            return signers(pm.getPackageInfo(c.getPackageName(), flags));
        } catch (Exception e) { return new HashSet<>(); }
    }

    /** Certificados de todas as fontes que o aparelho preencher (histórico de rotação incluído). */
    @SuppressWarnings("deprecation")
    static Set<String> signers(PackageInfo info) {
        Set<String> out = new HashSet<>();
        if (info == null) return out;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            add(out, info.signingInfo.getApkContentsSigners());
            if (!info.signingInfo.hasMultipleSigners()) add(out, info.signingInfo.getSigningCertificateHistory());
        }
        add(out, info.signatures);
        return out;
    }

    static void add(Set<String> out, Signature[] list) {
        if (list != null) for (Signature s : list) {
            try { out.add(toHex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))); } catch (Exception ignored) {}
        }
    }

    /**
     * Assinatura v1 (JAR): o {@link java.util.jar.JarFile} verifica cada entrada ao lê-la até ao fim
     * e só então expõe os certificados. Conta apenas se todas as entradas estiverem assinadas pelo
     * mesmo conjunto de certificados.
     */
    static Set<String> jarSigners(File apk) {
        Set<String> common = null;
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(apk, true)) {
            byte[] buffer = new byte[65536];
            for (java.util.Enumeration<java.util.jar.JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                java.util.jar.JarEntry entry = e.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) continue;
                try (InputStream in = jar.getInputStream(entry)) { while (in.read(buffer) != -1) { /* verificação */ } }
                java.security.cert.Certificate[] certs = entry.getCertificates();
                if (certs == null || certs.length == 0) return new HashSet<>();
                Set<String> these = new HashSet<>();
                for (java.security.cert.Certificate cert : certs) these.add(toHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));
                if (common == null) common = these; else if (!common.equals(these)) return new HashSet<>();
            }
        } catch (Exception e) { return new HashSet<>(); }
        return common == null ? new HashSet<>() : common;
    }

    static String shortPrint(Set<String> prints) {
        StringBuilder b = new StringBuilder();
        for (String p : new TreeSet<>(prints)) { if (b.length() > 0) b.append(", "); b.append(p, 0, Math.min(16, p.length())); }
        return b.toString();
    }

    static boolean sameSigner(Set<String> archive, Set<String> installed) {
        if (archive.isEmpty() || installed.isEmpty()) return false;
        for (String s : archive) if (installed.contains(s)) return true;
        return false;
    }

    static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte x : bytes) b.append(String.format(Locale.ROOT, "%02x", x & 255));
        return b.toString();
    }

    static void install(Activity a, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new UiDialog.Builder(a).setTitle("Permitir actualizações")
                    .setMessage(AppLanguage.text(a, "Para instalar a actualização, autorize o Seeky a instalar aplicações. Depois volte e toque em Actualizar."))
                    .setPositiveButton("Abrir definições", (d, w) -> {
                        try { a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); }
                        catch (Exception e) { PlusUi.error(a, e); }
                        prefs(a).edit().putString("pending_apk", apk.getPath()).apply();
                    }).setNegativeButton("Fechar", null).show();
            return;
        }
        Uri uri = ApkInstallActivity.share(a, apk);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, ApkInstallActivity.MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setClipData(android.content.ClipData.newRawUri("Seeky", uri));
        try { a.startActivity(i); } catch (Exception e) { PlusUi.error(a, e); }
    }

    /** Depois de autorizar a instalação nas definições, retoma o APK já verificado. */
    static void resumePending(Activity a) {
        String path = prefs(a).getString("pending_apk", null);
        if (path == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) return;
        prefs(a).edit().remove("pending_apk").apply();
        File apk = new File(path);
        try {
            File root = ApkInstallActivity.directory(a).getCanonicalFile();
            if (!apk.getCanonicalFile().getParentFile().equals(root) || !apk.isFile()) return;
            verifyPackage(a, apk, 0);
            install(a, apk);
        } catch (Exception e) { apk.delete(); }
    }

    static void settings(Activity a) {
        boolean on = automatic(a);
        UiPanel panel = new UiPanel(a, "Actualizações", AppLanguage.text(a, "Versão instalada:") + " " + MainActivity.VERSION.replace("Seeky ", ""), "refresh");
        panel.note("O Seeky procura novas versões no GitHub e só instala ficheiros assinados com a mesma chave. Os seus dados mantêm-se.");
        panel.action("refresh", "Procurar actualizações agora", null, false, () -> check(a, true));
        panel.action("clock", on ? "Desligar verificação automática" : "Ligar verificação automática", on ? "Activa · uma vez por dia" : "Desligada", false, () -> {
            prefs(a).edit().putBoolean("automatic", !on).apply();
            settings(a);
        });
        panel.show();
    }
}
