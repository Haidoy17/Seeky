package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The Gemini key kept with Windows' own protection (DPAPI, only this Windows user can read it),
 * through PowerShell. The key goes in and out through the process's standard input/output, never
 * in a command line. Elsewhere (tests on Linux) it is only encoded.
 */
final class DeskSecret {
    private DeskSecret() {}
    static boolean windows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
    static String ps(String script, String input) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(false).start();
        try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(input); }
        String out; try (InputStream in = p.getInputStream()) { out = new String(ContentText.read(in, 1 << 20), StandardCharsets.UTF_8).trim(); }
        if (p.waitFor() != 0) throw new IOException("A protecção do Windows não respondeu.");
        return out;
    }
    static String protect(String secret) throws Exception {
        if (!windows()) return "plain:" + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8));
        return "dpapi:" + ps("Add-Type -AssemblyName System.Security;$s=[Console]::In.ReadToEnd();[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($s),$null,'CurrentUser'))", secret);
    }
    static String reveal(String stored) throws Exception {
        if (stored == null || stored.isEmpty()) return "";
        if (stored.startsWith("plain:")) return new String(Base64.getDecoder().decode(stored.substring(6)), StandardCharsets.UTF_8);
        if (stored.startsWith("dpapi:")) return ps("Add-Type -AssemblyName System.Security;$s=[Console]::In.ReadToEnd().Trim();[Console]::Out.Write([Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String($s),$null,'CurrentUser')))", stored.substring(6));
        return "";
    }
}
