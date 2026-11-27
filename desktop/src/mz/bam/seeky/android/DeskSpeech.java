package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Reading aloud with a Piper natural voice (5.53) or with the voices installed in Windows (System.Speech, as Narrator). */
final class DeskSpeech {
    private DeskSpeech() {}
    static volatile Process speaking;
    static final Process[] piper = new Process[1];
    static volatile boolean piperBusy, edgeBusy;
    /** 5.56: changes at every stop or new reading, so only a reading that ends by itself goes on to the next page. */
    static volatile int generation;
    static boolean busy() { Process p = speaking; return p != null && p.isAlive() || piperBusy || edgeBusy; }
    static void stop() { generation++; if (edgeBusy && DeskEdge.instance != null) DeskEdge.instance.stop(); edgeBusy = false; Process p = speaking; if (p != null) p.destroyForcibly(); speaking = null; Process q = piper[0]; if (q != null) q.destroyForcibly(); piper[0] = null; piperBusy = false; }
    static void toggle(DeskMain d, String text) { if (busy()) { stop(); d.status("Leitura parada."); } else say(d, text); }
    static void say(DeskMain d, String text) { say(d, text, null); }
    /** Reads the text; {@code then} runs on the window's thread when it ends by itself (not when stopped). */
    static void say(DeskMain d, String text, Runnable then) {
        stop(); final int mine = generation;
        Runnable ended = () -> { if (then != null && generation == mine) javafx.application.Platform.runLater(() -> { if (generation == mine) then.run(); }); };
        if (!DeskSecret.windows()) { d.status("A leitura em voz alta usa as vozes do Windows."); return; }
        String voice = d.store.get("voice", ""); int rate = Integer.parseInt(d.store.get("voice_rate", "0"));
        if ("edge".equals(d.store.get("voice_engine", "")) && DeskEdge.available()) {
            String name = d.store.get("edge_voice", "");
            try { DeskEdge edge = DeskEdge.get(); edge.lastError = ""; edgeBusy = true; edge.speak(text.length() > 200000 ? text.substring(0, 200000) : text, name, DeskEdge.rate(rate), () -> { edgeBusy = false; if (edge.lastError.isEmpty()) ended.run(); }); d.status("A ler com a voz natural do Edge… (toque outra vez para parar)"); return; }
            catch (Exception e) { edgeBusy = false; d.status("O Edge não abriu; a usar a voz do Windows."); }
        }
        DeskVoices.Voice natural = DeskVoices.chosen(d.store);
        if (natural != null) {
            try { piperBusy = true; DeskVoices.speak(natural, rate, text.length() > 200000 ? text.substring(0, 200000) : text, piper, () -> { piperBusy = false; ended.run(); }); d.status("A ler com " + natural.label + "… (toque outra vez para parar)"); return; }
            catch (Exception e) { piperBusy = false; d.status("A voz Piper não arrancou; a usar a voz do Windows."); }
        }
        String script = "[Console]::InputEncoding=[Text.Encoding]::UTF8;Add-Type -AssemblyName System.Speech;$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;" + (voice.isEmpty() ? "" : "try{$s.SelectVoice('" + voice.replace("'", "''") + "')}catch{};") + "$s.Rate=" + Math.max(-10, Math.min(10, rate)) + ";$t=[Console]::In.ReadToEnd();$s.Speak($t)";
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).start();
            try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(text.length() > 200000 ? text.substring(0, 200000) : text); }
            speaking = p; p.onExit().thenRun(() -> { if (p.exitValue() == 0) ended.run(); }); d.status("A ler em voz alta… (toque outra vez para parar)");
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
