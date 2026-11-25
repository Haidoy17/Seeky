package mz.bam.seeky.android;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 5.53 · Vozes naturais Piper no computador (gratuitas, sem Internet depois de descarregadas) e as vozes do
 * Windows, para ler em voz alta e para gravar capítulos em áudio. O Piper e cada voz só são descarregados
 * quando o utilizador pede, de endereços fixos, e só são usados se o SHA-256 for o esperado.
 */
final class DeskVoices {
    private DeskVoices() {}

    static final String ENGINE_URL = "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip";
    static final String ENGINE_SHA = "f3c58906402b24f3a96d92145f58acba6d86c9b5db896d207f78dc80811efcea";
    static final long ENGINE_BYTES = 22477236L;
    static final String VOICES_URL = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/";

    static final class Voice {
        final String id, label, language, path, sha, jsonSha, licence;
        Voice(String id, String label, String language, String path, String sha, String jsonSha, String licence) { this.id = id; this.label = label; this.language = language; this.path = path; this.sha = sha; this.jsonSha = jsonSha; this.licence = licence; }
        File model() { return new File(root(), id + ".onnx"); }
        File config() { return new File(root(), id + ".onnx.json"); }
        boolean ready() { return model().isFile() && model().length() > 1_000_000 && config().isFile(); }
    }
    /** Voices with open licences (CC0 or CC BY 4.0), "medium" quality, about 63 MB each. */
    static final List<Voice> PIPER = Arrays.asList(
        new Voice("pt_PT-tugao-medium", "Português (Portugal) · Tugão", "pt-PT", "pt/pt_PT/tug%C3%A3o/medium/pt_PT-tug%C3%A3o-medium", "223a7aaca69a155c61897e8ada7c3b13bc306e16c72dbb9c2fed733e2b0927d4", "fe0918dfc0f1a89264a6eea4afe8e95d8e9fed3cc6c81b5c2f87fcb2b50c7320", "CC0"),
        new Voice("pt_BR-faber-medium", "Português (Brasil) · Faber", "pt-BR", "pt/pt_BR/faber/medium/pt_BR-faber-medium", "858555e3a064209c57088fe6bd70c4c3dc54d03eaa00c45d5ecaf43a33f95aa7", "7e694de195ae3fc36dd732c445eb04fb49b649854893cb5506b978f0d50a1d6f", "CC0"),
        new Voice("es_ES-davefx-medium", "Español · Davefx", "es-ES", "es/es_ES/davefx/medium/es_ES-davefx-medium", "6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917", "0e0dda87c732f6f38771ff274a6380d9252f327dca77aa2963d5fbdf9ec54842", "CC0"),
        new Voice("fr_FR-siwis-medium", "Français · Siwis", "fr-FR", "fr/fr_FR/siwis/medium/fr_FR-siwis-medium", "641d1ab097da2b81128c076810edb052b385decc8be3381814802a64a73baf99", "39479916c2db192b5ac9764daddd0c744d83e023ad890c6976c0633ae4df8959", "CC BY 4.0 (SIWIS, Universidade de Edimburgo)"),
        new Voice("en_US-joe-medium", "English (US) · Joe", "en-US", "en/en_US/joe/medium/en_US-joe-medium", "58afce0321b8d9c46d7cdf9c16500cc55a793b4220212dba6b70fb788b3baf06", "3d6d5410b3795cb1950595247ef8f06190719e6fdbfa3a2356d8ec368e1aad33", "CC0"),
        new Voice("en_GB-alba-medium", "English (UK) · Alba", "en-GB", "en/en_GB/alba/medium/en_GB-alba-medium", "401369c4a81d09fdd86c32c5c864440811dbdcc66466cde2d64f7133a66ad03b", "aa965a2f02ecced632c2694e1fc72bbff6d65f265fab567ca945918c73dd89f4", "CC BY 4.0"));

    static File root() { return DeskStore.folder("vozes"); }
    static File piperExe() { return new File(new File(root(), "piper"), "piper.exe"); }
    static boolean engineReady() { return piperExe().isFile(); }
    static Voice find(String id) { for (Voice v : PIPER) if (v.id.equals(id)) return v; return null; }
    /** The voice chosen in Definições when it is a Piper voice that is installed, else null (Windows voices). */
    static Voice chosen(DeskStore s) { if (!"piper".equals(s.get("voice_engine", "windows"))) return null; Voice v = find(s.get("piper_voice", "")); return v != null && v.ready() && engineReady() ? v : null; }
    /** Piper speed: a smaller length scale is faster (rate -5..5 as for the Windows voices). */
    static double lengthScale(int rate) { return Math.max(0.6, Math.min(1.6, 1.0 - rate * 0.08)); }
    static int sampleRate(Voice v) { try { String j = new String(Files.readAllBytes(v.config().toPath()), StandardCharsets.UTF_8); java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"sample_rate\"\\s*:\\s*(\\d+)").matcher(j); return m.find() ? Integer.parseInt(m.group(1)) : 22050; } catch (IOException e) { return 22050; } }

    // ---------------------------------------------------------------- descarregar
    interface Progress { void at(long done, long total, String what); }
    static void installEngine(BooleanSupplier cancel, Progress p) throws Exception {
        File zip = new File(root(), "piper.zip.part");
        try {
            download(ENGINE_URL, zip, ENGINE_SHA, cancel, p, "Motor Piper");
            File into = new File(root(), "piper.new"); deleteTree(into); into.mkdirs();
            try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
                for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                    if (cancel.getAsBoolean()) throw new InterruptedIOException();
                    String name = e.getName().replace('\\', '/'); if (name.startsWith("piper/")) name = name.substring(6); if (name.isEmpty()) continue;
                    File out = new File(into, name).getCanonicalFile();
                    if (!out.toPath().startsWith(into.getCanonicalFile().toPath())) throw new IOException("Pacote do Piper inválido.");
                    if (e.isDirectory()) { out.mkdirs(); continue; }
                    out.getParentFile().mkdirs(); Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (!new File(into, "piper.exe").isFile()) throw new IOException("O pacote do Piper não tem piper.exe.");
            File target = new File(root(), "piper"); deleteTree(target);
            if (!into.renameTo(target)) throw new IOException("Não foi possível instalar o Piper.");
        } finally { zip.delete(); }
    }
    static void installVoice(Voice v, BooleanSupplier cancel, Progress p) throws Exception {
        File model = new File(root(), v.id + ".onnx.part"), config = new File(root(), v.id + ".onnx.json.part");
        try {
            download(VOICES_URL + v.path + ".onnx.json", config, v.jsonSha, cancel, p, v.label + " (configuração)");
            download(VOICES_URL + v.path + ".onnx", model, v.sha, cancel, p, v.label);
            Files.move(config.toPath(), v.config().toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(model.toPath(), v.model().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally { model.delete(); config.delete(); }
    }
    static void removeVoice(Voice v) { v.model().delete(); v.config().delete(); }
    /** HTTPS download to a file, refused unless the SHA-256 is the expected one. */
    static void download(String url, File to, String sha, BooleanSupplier cancel, Progress p, String what) throws Exception {
        URL u = new URL(url); HttpURLConnection c = null; int hops = 0;
        while (true) {
            if (!"https".equals(u.getProtocol())) throw new IOException("Endereço inseguro.");
            c = (HttpURLConnection) u.openConnection(); c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(60000); c.setRequestProperty("User-Agent", "Seeky");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && hops++ < 6) { String next = c.getHeaderField("Location"); c.disconnect(); u = new URL(u, next); continue; }
            if (code != 200) throw new IOException(what + ": o servidor respondeu " + code + ".");
            break;
        }
        long total = c.getContentLengthLong(), done = 0; MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = c.getInputStream(); OutputStream out = new BufferedOutputStream(new FileOutputStream(to), 1 << 16)) {
            byte[] b = new byte[1 << 16]; long shown = 0;
            for (int n; (n = in.read(b)) != -1; ) {
                if (cancel.getAsBoolean()) throw new InterruptedIOException();
                out.write(b, 0, n); md.update(b, 0, n); done += n;
                if (done > 200_000_000L) throw new IOException(what + ": ficheiro demasiado grande.");
                long now = System.currentTimeMillis(); if (now - shown > 300) { shown = now; p.at(done, total, what); }
            }
        } finally { c.disconnect(); }
        StringBuilder h = new StringBuilder(); for (byte x : md.digest()) h.append(String.format("%02x", x));
        if (!h.toString().equals(sha)) { to.delete(); throw new IOException(what + ": o ficheiro descarregado não corresponde ao esperado (SHA-256). Nada foi instalado."); }
    }
    static void deleteTree(File f) { if (f == null || !f.exists()) return; File[] k = f.listFiles(); if (k != null) for (File x : k) deleteTree(x); f.delete(); }

    // ---------------------------------------------------------------- falar
    /** Piper writes raw 16-bit mono PCM for all the text (one paragraph per line). */
    static Process piper(Voice v, int rate) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(piperExe().getAbsolutePath(), "--model", v.model().getAbsolutePath(), "--config", v.config().getAbsolutePath(), "--output_raw", "--length_scale", String.format(Locale.ROOT, "%.2f", lengthScale(rate)), "--sentence_silence", "0.35");
        pb.directory(piperExe().getParentFile()); pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }
    /** The text as Piper wants it: paragraphs on separate lines, no control characters. */
    static String lines(String text) {
        StringBuilder b = new StringBuilder();
        for (String para : text.replace("\r", "").split("\\n\\s*\\n|\\n")) { String t = para.replaceAll("[\\p{Cc}\\s]+", " ").trim(); if (!t.isEmpty()) b.append(t).append('\n'); }
        return b.toString();
    }
    /** Reading aloud with Piper: the sound comes out while it is being made. */
    static Thread speak(Voice v, int rate, String text, Process[] holder, Runnable done) throws Exception {
        Process p = piper(v, rate); holder[0] = p; int sr = sampleRate(v);
        Thread feed = new Thread(() -> { try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(lines(text)); } catch (IOException ignored) {} }, "seeky-piper-in");
        Thread play = new Thread(() -> {
            javax.sound.sampled.AudioFormat f = new javax.sound.sampled.AudioFormat(sr, 16, 1, true, false);
            try (InputStream in = p.getInputStream(); javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(f)) {
                line.open(f, sr); line.start(); byte[] b = new byte[4096];
                for (int n; (n = in.read(b)) > 0; ) { if (!p.isAlive() && n <= 0) break; line.write(b, 0, n - (n & 1)); }
                if (p.isAlive() || p.exitValue() == 0) line.drain();
            } catch (Exception ignored) {} finally { done.run(); }
        }, "seeky-piper-out");
        feed.setDaemon(true); play.setDaemon(true); feed.start(); play.start(); return play;
    }

    // ---------------------------------------------------------------- gravar
    /** The text spoken by a Piper voice into a 16-bit mono WAV file. */
    static void piperWav(Voice v, int rate, String text, File wav, BooleanSupplier cancel) throws Exception {
        Process p = piper(v, rate); int sr = sampleRate(v);
        Thread feed = new Thread(() -> { try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(lines(text)); } catch (IOException ignored) {} }, "seeky-piper-in"); feed.setDaemon(true); feed.start();
        try (RandomAccessFile out = new RandomAccessFile(wav, "rw"); InputStream in = new BufferedInputStream(p.getInputStream())) {
            out.setLength(0); out.write(new byte[44]); long bytes = 0; byte[] b = new byte[1 << 16];
            for (int n; (n = in.read(b)) > 0; ) { if (cancel.getAsBoolean()) { p.destroyForcibly(); throw new InterruptedIOException(); } out.write(b, 0, n); bytes += n; }
            if (p.waitFor() != 0 || bytes < 2) throw new IOException("O Piper não conseguiu ler este texto.");
            bytes -= bytes & 1; out.setLength(44 + bytes); out.seek(0); out.write(wavHeader(sr, 1, 16, bytes));
        } finally { if (p.isAlive()) p.destroyForcibly(); }
    }
    static byte[] wavHeader(int rate, int channels, int bits, long data) {
        java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt((int) (36 + data)).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) channels)
            .putInt(rate).putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits).put("data".getBytes(StandardCharsets.US_ASCII)).putInt((int) data);
        return h.array();
    }
    /** The text spoken by a Windows voice (System.Speech) into a WAV file. */
    static void windowsWav(String voice, int rate, String text, File wav, BooleanSupplier cancel) throws Exception {
        if (!DeskSecret.windows()) throw new IOException("As vozes do Windows só existem no Windows.");
        String script = "[Console]::InputEncoding=[Text.Encoding]::UTF8;Add-Type -AssemblyName System.Speech;$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;" + (voice == null || voice.isEmpty() ? "" : "try{$s.SelectVoice('" + voice.replace("'", "''") + "')}catch{};")
            + "$s.Rate=" + Math.max(-10, Math.min(10, rate)) + ";$s.SetOutputToWaveFile($env:SEEKY_WAV);$t=[Console]::In.ReadToEnd();$s.Speak($t);$s.Dispose()";
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script); pb.environment().put("SEEKY_WAV", wav.getAbsolutePath()); pb.redirectErrorStream(true);
        Process p = pb.start();
        try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) { w.write(text); }
        Thread drain = new Thread(() -> { try (InputStream in = p.getInputStream()) { while (in.read() != -1) {} } catch (IOException ignored) {} }); drain.setDaemon(true); drain.start();
        while (p.isAlive()) { if (cancel.getAsBoolean()) { p.destroyForcibly(); throw new InterruptedIOException(); } Thread.sleep(200); }
        if (p.exitValue() != 0 || !wav.isFile() || wav.length() <= 44) throw new IOException("A voz do Windows não conseguiu gravar este texto.");
    }
}
