package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Reading aloud with the natural voices of Edge (5.54), a Piper voice (5.53) or the voices installed in Windows.
 * 5.58: the text is prepared as on the phone (passages are said as "Salmos capítulo 13, versículo 40", not as a time
 * of day); Edge and the Windows voices take the next page while this one is being read (no pause between pages);
 * and they tell which paragraph is being read, so the book can light it up and follow it.
 */
final class DeskSpeech {
    private DeskSpeech() {}
    static final int NONE = 0, EDGE = 1, PIPER = 2, WINDOWS = 3;
    static volatile int engine = NONE;
    /** Changes at every stop or new reading, so only a reading that ends by itself goes on to the next page. */
    static volatile int generation;
    /** Texts handed to Edge or to the Windows voice and not yet read to the end. */
    static final AtomicInteger pending = new AtomicInteger();
    static final Process[] piper = new Process[1];
    static volatile boolean piperBusy;
    // Edge
    static volatile String edgeVoice = ""; static volatile double edgeRate = 1;
    // Windows voices: one PowerShell for the whole reading, fed paragraph by paragraph.
    static volatile Process speaking; static volatile Writer windowsIn;
    static final Map<Integer, Runnable> windowsEnds = new ConcurrentHashMap<>();
    static final Map<Integer, IntConsumer> windowsParas = new ConcurrentHashMap<>();
    static final AtomicInteger windowsIds = new AtomicInteger();
    static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "seeky-voz-windows"); t.setDaemon(true); return t; });

    static boolean busy() { return pending.get() > 0 || piperBusy; }
    static void stop() {
        generation++;
        int was = engine; engine = NONE;
        if (was == EDGE && DeskEdge.instance != null) DeskEdge.instance.stop();
        pending.set(0);
        Process p = speaking; speaking = null; windowsIn = null; windowsEnds.clear(); windowsParas.clear(); if (p != null) p.destroyForcibly();
        Process q = piper[0]; if (q != null) q.destroyForcibly(); piper[0] = null; piperBusy = false;
    }
    static void toggle(DeskMain d, String text) { if (busy()) { stop(); d.status("Leitura parada."); } else say(d, text); }
    static void say(DeskMain d, String text) { say(d, text, null, null); }
    static void say(DeskMain d, String text, Runnable then) { say(d, text, then, null); }

    /** The text as the voice should say it: one paragraph per line, passages and numbers spelled out (as on the phone). */
    static String prepare(String text, String language) {
        if (text == null) return "";
        if (text.length() > 200000) text = text.substring(0, 200000);
        StringBuilder b = new StringBuilder();
        for (String line : text.replace("\r", "").split("\n")) {
            String t = line.trim(); if (t.isEmpty()) continue;
            String said; try { said = SpeechText.forVoice(t, language).text; } catch (RuntimeException e) { said = t; }
            b.append(said.replace('\n', ' ').trim()).append('\n');
        }
        return b.toString();
    }
    static String language(DeskMain d) {
        if (engine == EDGE || "edge".equals(d.store.get("voice_engine", ""))) { String l = d.store.get("edge_lang", "pt-PT"); return l.isEmpty() ? "pt-PT" : l; }
        DeskVoices.Voice v = DeskVoices.chosen(d.store); return v != null && v.id.length() >= 5 ? v.id.substring(0, 5).replace('_', '-') : "pt-PT";
    }
    /** Runs {@code then} on the window's thread when this text ends by itself (not when stopped). */
    static Runnable ending(int mine, Runnable then, boolean[] failed) {
        return () -> {
            if (generation != mine) return;
            if (pending.decrementAndGet() <= 0) { pending.set(0); closeWindowsWhenIdle(); }
            if (then != null && (failed == null || !failed[0])) javafx.application.Platform.runLater(() -> { if (generation == mine) then.run(); });
        };
    }
    static IntConsumer paragraphs(int mine, IntConsumer para) {
        return para == null ? null : n -> { if (generation == mine) javafx.application.Platform.runLater(() -> { if (generation == mine) para.accept(n); }); };
    }

    /** Reads the text; {@code then} runs when it ends by itself; {@code para} is told the paragraph being read (Edge, Windows). */
    static void say(DeskMain d, String text, Runnable then, IntConsumer para) {
        stop(); final int mine = generation;
        if (!DeskSecret.windows()) { d.status("A leitura em voz alta usa as vozes do Windows."); return; }
        String voice = d.store.get("voice", ""); int rate = Integer.parseInt(d.store.get("voice_rate", "0"));
        if ("edge".equals(d.store.get("voice_engine", "")) && DeskEdge.available()) {
            edgeVoice = d.store.get("edge_voice", ""); edgeRate = DeskEdge.rate(rate);
            try {
                DeskEdge edge = DeskEdge.get(); edge.open(); edge.lastError = ""; engine = EDGE; pending.set(1);
                boolean[] failed = {false}; Runnable end = ending(mine, then, failed);
                edge.queue(prepare(text, language(d)), edgeVoice, edgeRate, () -> { failed[0] = !edge.lastError.isEmpty(); end.run(); }, paragraphs(mine, para), false);
                d.status("A ler com a voz natural do Edge… (toque outra vez para parar)"); return;
            } catch (Exception e) { engine = NONE; pending.set(0); d.status("O Edge não abriu; a usar a voz do Windows."); }
        }
        DeskVoices.Voice natural = DeskVoices.chosen(d.store);
        if (natural != null) {
            try { engine = PIPER; piperBusy = true; DeskVoices.speak(natural, rate, prepare(text, language(d)), piper, () -> { piperBusy = false; if (generation == mine && then != null) javafx.application.Platform.runLater(() -> { if (generation == mine) then.run(); }); }); d.status("A ler com " + natural.label + "… (toque outra vez para parar)"); return; }
            catch (Exception e) { engine = NONE; piperBusy = false; d.status("A voz Piper não arrancou; a usar a voz do Windows."); }
        }
        try { startWindows(voice, rate); engine = WINDOWS; pending.set(1); sendWindows(prepare(text, "pt-PT"), ending(mine, then, null), paragraphs(mine, para)); d.status("A ler em voz alta… (toque outra vez para parar)"); }
        catch (IOException e) { engine = NONE; pending.set(0); d.status("A voz do Windows não está disponível."); }
    }
    /**
     * More text after what is being read (the next page), without a pause; false when the voice cannot take it
     * (Piper, or nothing is being read): then the caller starts it with {@link #say} when this one ends.
     */
    static boolean queue(DeskMain d, String text, Runnable then, IntConsumer para) {
        final int mine = generation;
        if (engine == EDGE && pending.get() > 0 && DeskEdge.instance != null) {
            try {
                DeskEdge edge = DeskEdge.instance; pending.incrementAndGet(); boolean[] failed = {false}; Runnable end = ending(mine, then, failed);
                edge.append(prepare(text, language(d)), edgeVoice, edgeRate, () -> { failed[0] = !edge.lastError.isEmpty(); end.run(); }, paragraphs(mine, para));
                return true;
            } catch (IOException e) { pending.decrementAndGet(); return false; }
        }
        if (engine == WINDOWS && pending.get() > 0 && windowsIn != null && speaking != null && speaking.isAlive()) {
            pending.incrementAndGet(); sendWindows(prepare(text, "pt-PT"), ending(mine, then, null), paragraphs(mine, para)); return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Windows voices (System.Speech)
    static void startWindows(String voice, int rate) throws IOException {
        String script = "[Console]::InputEncoding=[Text.Encoding]::UTF8;[Console]::OutputEncoding=[Text.Encoding]::UTF8;Add-Type -AssemblyName System.Speech;$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
            + (voice.isEmpty() ? "" : "try{$s.SelectVoice('" + voice.replace("'", "''") + "')}catch{};") + "$s.Rate=" + Math.max(-10, Math.min(10, rate)) + ";"
            + "while(($l=[Console]::In.ReadLine()) -ne $null){if($l.Length -lt 2){continue};if($l.StartsWith('S ')){$s.Speak($l.Substring(2))}else{[Console]::Out.WriteLine($l);[Console]::Out.Flush()}}";
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script); pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start(); speaking = p; windowsIn = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
        Thread read = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String l; (l = in.readLine()) != null; ) {
                    String[] w = l.trim().split(" ");
                    try {
                        if (w[0].equals("E") && w.length >= 2) { Runnable r = windowsEnds.remove(Integer.parseInt(w[1])); windowsParas.remove(Integer.parseInt(w[1])); if (r != null) r.run(); }
                        else if (w[0].equals("P") && w.length >= 3) { IntConsumer c = windowsParas.get(Integer.parseInt(w[1])); if (c != null) c.accept(Integer.parseInt(w[2])); }
                    } catch (NumberFormatException ignored) {}
                }
            } catch (IOException ignored) {}
        }, "seeky-voz-windows-fim"); read.setDaemon(true); read.start();
    }
    /** Paragraph by paragraph: "P id n" is echoed back as it starts, "E id" when the text ends. */
    static void sendWindows(String prepared, Runnable end, IntConsumer para) {
        int id = windowsIds.incrementAndGet(); windowsEnds.put(id, end); if (para != null) windowsParas.put(id, para);
        Writer w = windowsIn; if (w == null) return;
        WRITER.execute(() -> {
            try {
                int n = 0;
                for (String line : prepared.split("\n")) { if (line.trim().isEmpty()) continue; w.write("P " + id + " " + n++ + "\nS " + line + "\n"); w.flush(); }
                w.write("E " + id + "\n"); w.flush();
            } catch (IOException ignored) {}
        });
    }
    /** Nothing more to read: let the PowerShell end. */
    static void closeWindowsWhenIdle() {
        Writer w = windowsIn; if (engine != WINDOWS || w == null) return;
        windowsIn = null; WRITER.execute(() -> { try { w.close(); } catch (IOException ignored) {} });
    }

    /** Names of the installed Windows voices. */
    static java.util.List<String> voices() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!DeskSecret.windows()) return out;
        try { String list = DeskSecret.ps("Add-Type -AssemblyName System.Speech;(New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices()|%{$_.VoiceInfo.Name}", ""); for (String l : list.split("\\r?\\n")) if (!l.trim().isEmpty()) out.add(l.trim()); } catch (Exception ignored) {}
        return out;
    }
}
