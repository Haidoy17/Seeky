package mz.bam.seeky.android;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * 5.55 · Seeky IA sem chave: quando a pessoa não tem chave própria, o Seeky usa o serviço Seeky IA (um pequeno
 * servidor do autor do Seeky que guarda a chave do Gemini como segredo e aplica limites diários por instalação).
 * A chave nunca está dentro da aplicação. O endereço do serviço vem de entrega/seeky-ia.json no GitHub do Seeky e
 * só é aceite se for HTTPS num domínio *.workers.dev; sem esse ficheiro (ou desligado) tudo funciona como antes.
 */
final class SeekyAiService {
    private SeekyAiService() {}
    static final String CONFIG = "https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/seeky-ia.json";
    /** Placed where the personal key would go; the client then talks to the service instead of Google. */
    static final String KEY = "seeky-service";
    static final long REFRESH = 12 * 3600_000L;
    static volatile String url = "", install = "";

    interface Store { String get(String key, String fallback); void put(String key, String value); }

    static boolean allowed(String u) { return u != null && u.matches("https://[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*\\.workers\\.dev/?"); }
    static boolean available() { return !url.isEmpty() && !install.isEmpty(); }
    static String base() { String u = url; return u.endsWith("/") ? u.substring(0, u.length() - 1) : u; }

    /** Loads the saved address and this installation's random id; asks GitHub again at most every 12 hours. */
    static void load(Store s) {
        String id = s.get("ai_install", "");
        if (!id.matches("[a-f0-9]{32}")) { byte[] r = new byte[16]; new java.security.SecureRandom().nextBytes(r); StringBuilder b = new StringBuilder(); for (byte x : r) b.append(String.format("%02x", x)); id = b.toString(); s.put("ai_install", id); }
        install = id;
        String saved = s.get("ai_service", ""); url = allowed(saved) ? saved : "";
    }
    static void refresh(Store s) {
        load(s);
        long last; try { last = Long.parseLong(s.get("ai_service_checked", "0")); } catch (NumberFormatException e) { last = 0; }
        if (System.currentTimeMillis() - last < REFRESH) return;
        try { String found = fetch(); s.put("ai_service", found); s.put("ai_service_checked", "" + System.currentTimeMillis()); url = found; }
        catch (Exception ignored) {} // offline: keep what was saved
    }
    /** The address in entrega/seeky-ia.json when it is switched on and safe; otherwise "". */
    static String fetch() throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(CONFIG + "?t=" + System.currentTimeMillis() / 600000).openConnection();
        try {
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent", "Seeky");
            if (c.getResponseCode() == 404) return "";
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            byte[] raw; try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[4096]; for (int n; (n = in.read(b)) != -1; ) { out.write(b, 0, n); if (out.size() > 8192) throw new IOException("grande"); } raw = out.toByteArray(); }
            return parse(new String(raw, StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }
    static String parse(String json) { try { JSONObject o = new JSONObject(json); String u = o.optString("service", "").trim(); return o.optBoolean("enabled", false) && allowed(u) ? u : ""; } catch (Exception e) { return ""; } }
    /** What the user is told about the service. */
    static String about() { return "Sem chave própria, o Seeky usa o serviço Seeky IA: o texto que pedir passa pelo servidor do Seeky até ao Google Gemini, com um limite diário por aparelho. Com a sua chave do Google AI Studio tem mais pedidos e o texto vai directamente à Google."; }
}
