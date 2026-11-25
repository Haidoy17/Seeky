package mz.bam.seeky.android;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * 5.54 · Gravar em MP3 com as vozes naturais do Edge pela via oficial: o Azure Speech da Microsoft (as mesmas vozes
 * neurais, p. ex. pt-PT-RaquelNeural). Usa a chave do utilizador (o plano gratuito F0 dá 500 000 caracteres por
 * mês), guardada protegida pelo Windows e enviada só à Microsoft. O texto vai em partes e o MP3 é juntado.
 */
final class DeskAzure {
    private DeskAzure() {}
    static final String[] REGIONS = {"westeurope", "northeurope", "francecentral", "uksouth", "swedencentral", "eastus", "eastus2", "westus2", "brazilsouth", "southafricanorth", "centralindia", "southeastasia", "japaneast"};
    static final String FORMAT = "audio-24khz-48kbitrate-mono-mp3";
    static final int PIECE = 2500;

    static boolean configured(DeskStore s) { return !s.get("azure_key", "").isEmpty() && region(s.get("azure_region", "")) != null; }
    static String region(String r) { return r != null && r.matches("[a-z0-9]{3,30}") ? r : null; }
    static String key(DeskStore s) throws Exception { return DeskSecret.reveal(s.get("azure_key", "")); }

    static String xml(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replaceAll("[\\p{Cc}&&[^\\n\\t]]", " "); }
    /** SSML for one piece; rate −5…5 becomes −40 %…+40 %. */
    static String ssml(String text, String voice, String lang, int rate) {
        if (!voice.matches("[A-Za-z]{2,3}-[A-Za-z0-9]{2,8}(-[A-Za-z0-9]+)?-[A-Za-z0-9]+Neural")) throw new IllegalArgumentException("Voz inválida: " + voice);
        int pct = Math.max(-5, Math.min(5, rate)) * 8;
        return "<speak version='1.0' xml:lang='" + xml(lang) + "' xmlns='http://www.w3.org/2001/10/synthesis'><voice name='" + voice + "'><prosody rate='" + (pct >= 0 ? "+" : "") + pct + "%'>" + xml(text) + "</prosody></voice></speak>";
    }
    /** Pieces of at most 2500 characters, cut at a paragraph, a sentence or a space. */
    static List<String> pieces(String text) {
        List<String> out = new ArrayList<>(); String t = text.replace("\r", "").trim();
        while (t.length() > PIECE) {
            int cut = t.lastIndexOf("\n", PIECE); if (cut < PIECE / 3) cut = Math.max(t.lastIndexOf(". ", PIECE), Math.max(t.lastIndexOf("! ", PIECE), t.lastIndexOf("? ", PIECE))) + 1;
            if (cut < PIECE / 3) cut = t.lastIndexOf(' ', PIECE); if (cut < PIECE / 3) cut = PIECE;
            out.add(t.substring(0, cut).trim()); t = t.substring(cut).trim();
        }
        if (!t.isEmpty()) out.add(t);
        return out;
    }
    static byte[] synth(String region, String key, String ssml, BooleanSupplier cancel) throws Exception {
        if (region(region) == null) throw new IOException("Região do Azure inválida.");
        for (int attempt = 0; ; attempt++) {
            HttpURLConnection c = (HttpURLConnection) new URL("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1").openConnection();
            try {
                c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(90000); c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Ocp-Apim-Subscription-Key", key); c.setRequestProperty("Content-Type", "application/ssml+xml"); c.setRequestProperty("X-Microsoft-OutputFormat", FORMAT); c.setRequestProperty("User-Agent", "Seeky");
                byte[] body = ssml.getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(body.length);
                try (OutputStream o = c.getOutputStream()) { o.write(body); }
                int code = c.getResponseCode();
                if (code == 429 && attempt < 3) { for (int s = 0; s < 20; s++) { if (cancel.getAsBoolean()) throw new InterruptedIOException(); Thread.sleep(1000); } continue; }
                if (code == 401 || code == 403) throw new IOException("Azure Speech: chave recusada (HTTP " + code + "). Confirme a chave e a região do recurso.");
                if (code == 400) throw new IOException("Azure Speech: pedido recusado (HTTP 400). A voz escolhida pode não existir nesta região.");
                if (code == 429) throw new IOException("Azure Speech: limite de pedidos atingido. Tente mais tarde ou verifique a quota do plano gratuito.");
                if (code != 200) throw new IOException("Azure Speech respondeu HTTP " + code + ".");
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] b = new byte[1 << 16]; for (int n; (n = in.read(b)) != -1; ) { if (cancel.getAsBoolean()) throw new InterruptedIOException(); out.write(b, 0, n); if (out.size() > 60_000_000) throw new IOException("Resposta demasiado grande."); }
                    return out.toByteArray();
                }
            } finally { c.disconnect(); }
        }
    }
    /** MP3 bytes without a leading ID3 tag (each piece is joined after Seeky's own tag). */
    static byte[] frames(byte[] mp3) {
        if (mp3.length > 10 && mp3[0] == 'I' && mp3[1] == 'D' && mp3[2] == '3') { int size = 10 + ((mp3[6] & 0x7f) << 21 | (mp3[7] & 0x7f) << 14 | (mp3[8] & 0x7f) << 7 | (mp3[9] & 0x7f)); if (size < mp3.length) return Arrays.copyOfRange(mp3, size, mp3.length); }
        return mp3;
    }
    interface Progress { void at(int done, int total); }
    /** A chapter read by an Azure neural voice into one tagged MP3. Returns the seconds of audio (48 kbit/s). */
    static double mp3(String region, String key, String voice, String lang, int rate, String text, File out, Mp3Writer.Tags tags, BooleanSupplier cancel, Progress p) throws Exception {
        List<String> parts = pieces(text); long bytes = 0; File part = new File(out.getPath() + ".part");
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(part))) {
            o.write(Mp3Writer.id3(tags));
            for (int i = 0; i < parts.size(); i++) {
                if (cancel.getAsBoolean()) throw new InterruptedIOException();
                byte[] f = frames(synth(region, key, ssml(parts.get(i), voice, lang, rate), cancel)); o.write(f); bytes += f.length;
                if (p != null) p.at(i + 1, parts.size());
            }
        } catch (Exception e) { part.delete(); throw e; }
        if (out.exists()) out.delete();
        if (!part.renameTo(out)) throw new IOException("Não foi possível guardar o MP3.");
        return bytes * 8.0 / 48000;
    }
}
