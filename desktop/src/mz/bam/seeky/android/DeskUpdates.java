package mz.bam.seeky.android;

import javafx.application.Platform;
import javafx.scene.control.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.*;
import org.json.JSONObject;

/**
 * Updates of Seeky for Windows, published together with the Android ones. entrega/windows/latest.json
 * names the version, the application package (a few MB) and its SHA-256. The package goes to
 * %LOCALAPPDATA%\Seeky\app\<version>; Seeky.exe starts the newest valid one, so Java is not downloaded again.
 */
final class DeskUpdates {
    private DeskUpdates() {}
    static final String MANIFEST = "https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/windows/latest.json";
    static final String HOST = "raw.githubusercontent.com";
    static File apps() { String local = System.getenv("LOCALAPPDATA"); File base = local != null ? new File(local, "Seeky") : DeskStore.home(); File f = new File(base, "app"); f.mkdirs(); return f; }
    static void check(DeskMain d, boolean manual) {
        try {
            if (!manual) { long last = Long.parseLong(d.store.get("update_check", "0")); if (System.currentTimeMillis() - last < 6 * 3600_000L) return; }
            d.store.put("update_check", String.valueOf(System.currentTimeMillis()));
            JSONObject m = new JSONObject(new String(get(MANIFEST + "?t=" + System.currentTimeMillis() / 60000, 65536), StandardCharsets.UTF_8));
            long code = m.getLong("versionCode"); String name = m.getString("versionName"), url = m.getString("app"), sha = m.getString("sha256").toLowerCase();
            if (code <= DeskVersion.CODE) { if (manual) Platform.runLater(() -> DeskUi.info("O Seeky está actualizado", "Versão " + DeskMain.VERSION + ".")); return; }
            Platform.runLater(() -> { if (DeskUi.confirm("Nova versão: Seeky " + name, m.optString("notes", "") + "\n\nOs seus dados mantêm-se. Actualizar agora?")) d.run("A descarregar o Seeky " + name + "…", () -> install(name, url, sha), dir -> ready(d, name)); });
        } catch (Exception e) { if (manual) Platform.runLater(() -> DeskUi.info("Não foi possível procurar actualizações", "Verifique a Internet. (" + e.getMessage() + ")")); }
    }
    /** 5.55: "Actualização pronta" in the Seeky style, with Reiniciar agora (the new version opens by itself). */
    static void ready(DeskMain d, String name) {
        Alert a = new Alert(Alert.AlertType.NONE, "A versão " + name + " já está no computador. Os seus dados ficam como estão.", new ButtonType("Reiniciar agora", ButtonBar.ButtonData.OK_DONE), new ButtonType("Mais tarde", ButtonBar.ButtonData.CANCEL_CLOSE));
        a.setTitle("Seeky"); a.setHeaderText("Actualização pronta · Seeky " + name); DeskUi.style(a.getDialogPane());
        a.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).ifPresent(b -> restart(d));
    }
    /** Opens Seeky again a moment after this one closes (so the single-instance check lets it start). */
    static void restart(DeskMain d) {
        String local = System.getenv("LOCALAPPDATA"); File exe = local == null ? null : new File(local, "Programs\\Seeky\\Seeky.exe");
        if (exe == null || !exe.isFile()) { d.status("Feche e volte a abrir o Seeky para usar a nova versão."); return; }
        try { ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", "Start-Sleep -Seconds 2; Start-Process -FilePath $env:SEEKY_EXE"); pb.environment().put("SEEKY_EXE", exe.getAbsolutePath()); pb.start(); } catch (IOException e) { d.status("Feche e volte a abrir o Seeky para usar a nova versão."); return; }
        d.quit();
    }
    static byte[] get(String url, int max) throws IOException {
        URL u = new URL(url); if (!"https".equals(u.getProtocol()) || !HOST.equals(u.getHost())) throw new IOException("Ligação de actualização recusada.");
        HttpURLConnection c = (HttpURLConnection) u.openConnection(); c.setInstanceFollowRedirects(false); c.setUseCaches(false); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setRequestProperty("User-Agent", "Seeky-Desktop");
        if (c.getResponseCode() != 200) throw new IOException("O servidor respondeu " + c.getResponseCode() + ".");
        try (InputStream in = c.getInputStream()) { return ContentText.read(in, max); }
    }
    static File install(String name, String url, String sha) throws Exception {
        if (!name.matches("[0-9][0-9.]{0,20}")) throw new IOException("Versão inválida.");
        byte[] zip = get(url, 200 << 20);
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(zip)); if (!hash.equals(sha)) throw new IOException("O pacote descarregado não corresponde (SHA-256). Nada foi instalado.");
        File dir = new File(apps(), name), tmp = new File(apps(), name + ".partial"); deleteTree(tmp); tmp.mkdirs();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                Path out = tmp.toPath().resolve(e.getName()).normalize(); if (!out.startsWith(tmp.toPath())) throw new IOException("Pacote inválido.");
                if (e.isDirectory()) Files.createDirectories(out); else { Files.createDirectories(out.getParent()); Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING); }
            }
        }
        if (!new File(tmp, "seeky-desktop.jar").isFile()) throw new IOException("Pacote incompleto.");
        deleteTree(dir); if (!tmp.renameTo(dir)) throw new IOException("Não foi possível instalar a actualização.");
        Files.write(new File(apps(), "current.txt").toPath(), name.getBytes(StandardCharsets.UTF_8));
        return dir;
    }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    static void deleteTree(File f) { File[] k = f.listFiles(); if (k != null) for (File x : k) deleteTree(x); f.delete(); }
}
