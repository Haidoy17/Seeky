package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Reading aloud with the voices installed in Windows (System.Speech, the same voices as Narrator). */
final class DeskSpeech {
    private DeskSpeech() {}
    static volatile Process speaking;
    static boolean busy() { Process p = speaking; return p != null && p.isAlive(); }
    static void stop() { Process p = speaking; if (p != null) p.destroyForcibly(); speaking = null; }
    static void toggle(DeskMain d, String text) { if (busy()) { stop(); d.status("Leitura parada."); } else say(d, text); }
    static void say(DeskMain d, String text) {
        stop();
        if (!DeskSecret.windows()) { d.status("A leitura em voz alta usa as vozes do Windows."); return; }
        String voice = d.store.get("voice", ""); int rate = Integer.parseInt(d.store.get("voice_rate", "0"));
        String script = "Add-Type -AssemblyName System.Speech;$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;" + (voice.isEmpty() ? "" : "try{$s.SelectVoice('" + voice.replace("'", "''") + "')}catch{};") + "$s.Rate=" + Math.max(-10, Math.min(10, rate)) + ";$t=[Console]::In.ReadToEnd();$s.Speak($t)";
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).start();
            try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(text.length() > 200000 ? text.substring(0, 200000) : text); }
            speaking = p; d.status("A ler em voz alta… (toque outra vez para parar)");
        } catch (IOException e) { d.status("A voz do Windows não está disponível."); }
    }
    /** Names of the installed Windows voices. */
    static java.util.List<String> voices() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!DeskSecret.windows()) return out;
        try { String list = DeskSecret.ps("Add-Type -AssemblyName System.Speech;(New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices()|%{$_.VoiceInfo.Name}", ""); for (String l : list.split("\\r?\\n")) if (!l.trim().isEmpty()) out.add(l.trim()); } catch (Exception ignored) {}
        return out;
    }
}
