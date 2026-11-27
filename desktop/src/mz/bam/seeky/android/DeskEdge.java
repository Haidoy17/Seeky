package mz.bam.seeky.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 5.54 · Vozes naturais do Microsoft Edge para ler em voz alta, sem chave e sem custo: o Seeky abre o próprio
 * Edge escondido numa página local que usa a Web Speech API do navegador (as vozes "Online (Natural)" que o
 * Edge dá às páginas web) e envia-lhe o texto. Comunicação só em 127.0.0.1, com um código aleatório no endereço.
 * O Edge toca o som; não há forma oficial de o gravar — para MP3 com estas vozes usa-se o Azure Speech.
 */
final class DeskEdge {
    static final class Voice {
        final String name, lang; final boolean natural;
        Voice(String name, String lang) { this.name = name; this.lang = lang; natural = name.contains("(Natural)"); }
        /** "Microsoft Francisca Online (Natural) - Portuguese (Brazil)" → "Francisca". */
        String person() { java.util.regex.Matcher m = java.util.regex.Pattern.compile("^Microsoft\\s+(.+?)\\s+Online").matcher(name); return m.find() ? m.group(1) : name; }
        /** The same voice in Azure Speech: "pt-BR-FranciscaNeural". */
        String azure() { return lang + "-" + person().replaceAll("\\s+", "") + "Neural"; }
        String label() { return person() + " · " + language(lang); }
    }
    static String language(String tag) {
        Locale l = Locale.forLanguageTag(tag); String lang = l.getDisplayLanguage(new Locale("pt", "PT")), country = l.getDisplayCountry(new Locale("pt", "PT"));
        if (lang.isEmpty()) return tag; lang = lang.substring(0, 1).toUpperCase(new Locale("pt", "PT")) + lang.substring(1);
        return country.isEmpty() ? lang : lang + " (" + country + ")";
    }
    /** Natural voices first, Portuguese first, then by language and name. */
    static List<Voice> sorted(List<Voice> all, boolean naturalOnly) {
        List<Voice> out = new ArrayList<>(); for (Voice v : all) if (!naturalOnly || v.natural) out.add(v);
        out.sort(Comparator.comparing((Voice v) -> !v.lang.startsWith("pt")).thenComparing(v -> !v.lang.equals("pt-PT")).thenComparing(v -> v.lang).thenComparing(Voice::person));
        return out;
    }

    // ---------------------------------------------------------------- the local bridge
    final String token; final ServerSocket server; final BlockingQueue<String> commands = new LinkedBlockingQueue<>();
    final Map<Integer, Runnable> finished = new ConcurrentHashMap<>();
    /** 5.58: told which paragraph (from 0) of the text is being read. */
    final Map<Integer, java.util.function.IntConsumer> paragraphs = new ConcurrentHashMap<>(); final CountDownLatch listed = new CountDownLatch(1);
    volatile List<Voice> voices = new ArrayList<>(); volatile Process edge; volatile long lastPoll; volatile String lastError = "";
    int ids;
    DeskEdge() throws IOException {
        byte[] r = new byte[16]; new SecureRandom().nextBytes(r); StringBuilder t = new StringBuilder(); for (byte b : r) t.append(String.format("%02x", b)); token = t.toString();
        server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        Thread accept = new Thread(this::serve, "seeky-edge"); accept.setDaemon(true); accept.start();
    }
    int port() { return server.getLocalPort(); }
    String base() { return "http://127.0.0.1:" + port() + "/s/" + token + "/"; }

    static volatile DeskEdge instance;
    static synchronized DeskEdge get() throws IOException { if (instance == null) instance = new DeskEdge(); return instance; }

    static File exe() {
        String[] roots = {System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA")};
        for (String r : roots) if (r != null) { File f = new File(r, "Microsoft\\Edge\\Application\\msedge.exe"); if (f.isFile()) return f; }
        return null;
    }
    static boolean available() { return DeskSecret.windows() && exe() != null; }
    /** Opens Edge hidden (its own profile in the Seeky folder) on the local page, once. */
    synchronized void open() throws IOException {
        if (edge != null && edge.isAlive() && System.currentTimeMillis() - lastPoll < 60000) return;
        File exe = exe(); if (exe == null) throw new IOException("O Microsoft Edge não está instalado neste computador.");
        if (edge != null) kill(edge);
        ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath(), "--app=" + base(), "--user-data-dir=" + DeskStore.folder("edge-voz").getAbsolutePath(),
            "--no-first-run", "--no-default-browser-check", "--disable-sync", "--autoplay-policy=no-user-gesture-required", "--window-position=-32000,-32000", "--window-size=320,200", "--disable-features=Translate,msEdgeTranslate");
        pb.redirectErrorStream(true); pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        edge = pb.start(); lastPoll = System.currentTimeMillis();
        if (!hooked) { hooked = true; Runtime.getRuntime().addShutdownHook(new Thread(() -> { Process e = edge; if (e != null) kill(e); })); }
    }
    boolean hooked;
    /** Edge starts helper processes (renderer, GPU, audio): close them all, not only the first one. */
    static void kill(Process p) { try { p.descendants().forEach(ProcessHandle::destroyForcibly); } catch (Exception ignored) {} p.destroyForcibly(); }
    void close() { Process p = edge; if (p != null) kill(p); edge = null; try { server.close(); } catch (IOException ignored) {} }

    /** All the voices Edge offers (it opens Edge the first time). */
    List<Voice> voices(long waitMillis) throws Exception {
        if (voices.isEmpty()) { open(); commands.offer(new JSONObject().put("op", "voices").toString()); listed.await(waitMillis, TimeUnit.MILLISECONDS); }
        return voices;
    }
    synchronized int speak(String text, String voice, double rate, Runnable done) throws IOException { open(); return queue(text, voice, rate, done); }
    /** Hands the text to the page (which reads it in short pieces) and remembers who to tell when it ends. */
    synchronized int queue(String text, String voice, double rate, Runnable done) { return queue(text, voice, rate, done, null, false); }
    /**
     * Hands text to the page. {@code add}: after what is already being read, without stopping it (5.58: the next
     * page is given to Edge before this one ends, so turning the page makes no pause).
     */
    synchronized int queue(String text, String voice, double rate, Runnable done, java.util.function.IntConsumer paragraph, boolean add) {
        int id = ++ids; if (done != null) finished.put(id, done); if (paragraph != null) paragraphs.put(id, paragraph);
        if (!add) commands.clear();
        commands.offer(new JSONObject().put("op", add ? "add" : "speak").put("id", id).put("text", text).put("voice", voice).put("rate", rate).toString());
        return id;
    }
    synchronized int append(String text, String voice, double rate, Runnable done, java.util.function.IntConsumer paragraph) throws IOException { open(); return queue(text, voice, rate, done, paragraph, true); }
    void stop() { commands.clear(); commands.offer(new JSONObject().put("op", "stop").toString()); paragraphs.clear(); for (Runnable r : finished.values()) r.run(); finished.clear(); }

    // ---------------------------------------------------------------- tiny HTTP server (127.0.0.1 only)
    void serve() {
        while (!server.isClosed()) {
            try { Socket s = server.accept(); Thread h = new Thread(() -> handle(s), "seeky-edge-req"); h.setDaemon(true); h.start(); }
            catch (IOException e) { if (server.isClosed()) return; }
        }
    }
    void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(40000);
            InputStream in = new BufferedInputStream(sock.getInputStream()); OutputStream out = sock.getOutputStream();
            String request = line(in); if (request == null) return;
            Map<String, String> headers = new HashMap<>(); for (String h; (h = line(in)) != null && !h.isEmpty(); ) { int c = h.indexOf(':'); if (c > 0) headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim()); }
            String[] parts = request.split(" "); if (parts.length < 2) return;
            String method = parts[0], path = parts[1];
            if (!("127.0.0.1:" + port()).equals(headers.get("host")) || !path.startsWith("/s/" + token + "/")) { reply(out, 404, "text/plain", "Não encontrado"); return; }
            String route = path.substring(("/s/" + token + "/").length());
            int length = Math.min(4_000_000, parse(headers.get("content-length")));
            byte[] body = new byte[Math.max(0, length)]; int read = 0; while (read < body.length) { int n = in.read(body, read, body.length - read); if (n < 0) break; read += n; }
            String text = new String(body, 0, read, StandardCharsets.UTF_8);
            lastPoll = System.currentTimeMillis();
            switch (method + " " + route) {
                case "GET ": reply(out, 200, "text/html; charset=utf-8", page()); break;
                case "GET next": { String c = commands.poll(25, TimeUnit.SECONDS); reply(out, 200, "application/json", c == null ? "{\"op\":\"wait\"}" : c); break; }
                case "POST voices": {
                    JSONArray a = new JSONArray(text); List<Voice> list = new ArrayList<>();
                    for (int i = 0; i < a.length() && i < 2000; i++) { JSONObject v = a.getJSONObject(i); String n = v.optString("name"), l = v.optString("lang"); if (!n.isEmpty() && n.length() < 200 && l.matches("[A-Za-z0-9-]{2,20}")) list.add(new Voice(n, l)); }
                    if (!list.isEmpty()) { voices = list; listed.countDown(); }
                    reply(out, 200, "application/json", "{}"); break;
                }
                case "POST event": {
                    JSONObject e = new JSONObject(text); String type = e.optString("type");
                    if (type.equals("error")) lastError = e.optString("detail");
                    if (type.equals("para")) { java.util.function.IntConsumer c = paragraphs.get(e.optInt("id")); if (c != null) c.accept(e.optInt("para")); }
                    if (type.equals("end") || type.equals("error")) { paragraphs.remove(e.optInt("id")); Runnable r = finished.remove(e.optInt("id")); if (r != null) r.run(); }
                    reply(out, 200, "application/json", "{}"); break;
                }
                default: reply(out, 404, "text/plain", "Não encontrado");
            }
        } catch (Exception ignored) {}
    }
    static int parse(String n) { try { return n == null ? 0 : Integer.parseInt(n.trim()); } catch (NumberFormatException e) { return 0; } }
    static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); int c;
        while ((c = in.read()) != -1) { if (c == '\n') break; if (c != '\r') b.write(c); if (b.size() > 8192) throw new IOException("linha longa"); }
        return c == -1 && b.size() == 0 ? null : new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
    static void reply(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(b); out.flush();
    }

    /** The local page: lists Edge's voices and speaks what Seeky sends, in short pieces so long chapters do not stall. */
    static String page() {
        return "<!doctype html><html><head><meta charset='utf-8'><title>Seeky · voz</title></head><body style='font-family:sans-serif'>Seeky · voz do Edge<script>\n"
            + "const T=location.pathname;let active=new Set(),voice=null,rate=1;\n"
            + "function post(p,o){return fetch(T+p,{method:'POST',body:JSON.stringify(o)}).catch(()=>{});}\n"
            + "function list(){const v=speechSynthesis.getVoices().map(x=>({name:x.name,lang:x.lang}));if(v.length)post('voices',v);}\n"
            + "speechSynthesis.onvoiceschanged=list;setTimeout(list,400);setTimeout(list,2500);\n"
            // 5.56: big pieces (whole paragraphs, cut at the end of a sentence), all queued at once, so Edge already has
            // the next one while it reads. 5.58: up to ~2400 letters; each piece remembers where its paragraphs start, so
            // the page can tell Seeky which paragraph is being read (to light it up and follow it, as on the phone).
            + "function pieces(t){const out=[];let cur='',marks=[],para=-1;const flush=()=>{if(cur.trim())out.push({text:cur,marks:marks});cur='';marks=[];};"
            + "for(const raw of t.split(/\\n+/)){let p=raw.trim();if(!p)continue;para++;if(cur&&cur.length+p.length+1>2400)flush();"
            + "while(p.length>2400){let cut=-1;for(const m of ['. ','! ','? ','; ',': ',', ',' ']){const i=p.lastIndexOf(m,2400);if(i>600){cut=i+1;break;}}if(cut<0)cut=2400;if(cur)flush();out.push({text:p.slice(0,cut).trim(),marks:[[0,para]]});p=p.slice(cut).trim();}"
            + "if(cur)cur+='\\n';marks.push([cur.length,para]);cur+=p;}flush();return out;}\n"
            + "function paraAt(marks,ci){let p=marks.length?marks[0][1]:0;for(const m of marks){if(m[0]<=ci)p=m[1];else break;}return p;}\n"
            + "function speakAll(id,parts){let last=-1;const tell=n=>{if(n!==last&&active.has(id)){last=n;post('event',{type:'para',id,para:n});}};"
            + "parts.forEach((part,i)=>{const u=new SpeechSynthesisUtterance(part.text);if(voice){u.voice=voice;u.lang=voice.lang;}u.rate=rate;"
            + "u.onstart=()=>tell(paraAt(part.marks,0));u.onboundary=e=>tell(paraAt(part.marks,e.charIndex||0));"
            + "if(i===parts.length-1)u.onend=()=>{if(active.has(id)){active.delete(id);post('event',{type:'end',id});}};"
            + "u.onerror=e=>{if(e.error==='interrupted'||e.error==='canceled'||!active.has(id))return;active.delete(id);post('event',{type:'error',id,detail:e.error});};speechSynthesis.speak(u);});"
            + "if(!parts.length){active.delete(id);post('event',{type:'end',id});}}\n"
            + "function handle(c){if(c.op==='voices')list();else if(c.op==='stop'){active.clear();speechSynthesis.cancel();}else if(c.op==='speak'||c.op==='add'){if(c.op==='speak'){speechSynthesis.cancel();active.clear();}active.add(c.id);voice=speechSynthesis.getVoices().find(v=>v.name===c.voice)||null;rate=Math.max(.5,Math.min(2,c.rate||1));speakAll(c.id,pieces(c.text||''));}}\n"
            + "async function loop(){for(;;){try{const r=await fetch(T+'next',{cache:'no-store'});handle(await r.json());}catch(e){await new Promise(z=>setTimeout(z,1500));}}}loop();\n"
            + "</script></body></html>";
    }
    /** Edge's rate: the Windows-style −5…5 setting becomes 0.6…1.6. */
    static double rate(int setting) { return Math.max(0.5, Math.min(2.0, 1.0 + setting * 0.1)); }
}
