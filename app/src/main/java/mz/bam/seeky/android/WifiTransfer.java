package mz.bam.seeky.android;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transferência por Wi-Fi: o Seeky abre um pequeno servidor na rede local (porta 8080) que o
 * computador visita no navegador para ver pastas, descarregar e enviar ficheiros, sem cabo nem
 * internet.
 *
 * <p>Segurança: só funciona enquanto o painel está aberto (pára sozinho após 15 min sem uso);
 * pede o código de 6 dígitos mostrado no telefone; 5 códigos errados bloqueiam 1 minuto e 20
 * desligam o servidor; a sessão é um cookie aleatório HttpOnly/SameSite=Strict; os envios exigem
 * um cabeçalho próprio (protege contra pedidos de outros sites); caminhos fora da pasta
 * partilhada são recusados.
 */
final class WifiTransfer {
    static final long IDLE = 15 * 60 * 1000L;
    static Server running;

    private WifiTransfer() {}

    // ---- servidor ----

    static final class Server implements Runnable {
        final File root;
        final String pin, token;
        final ServerSocket socket;
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        final AtomicInteger received = new AtomicInteger(), sent = new AtomicInteger();
        final List<String> receivedPaths = Collections.synchronizedList(new ArrayList<>());
        volatile boolean stopped;
        volatile long lastUse = System.currentTimeMillis();
        volatile Runnable changed;
        int failures, total;
        long lockedUntil;

        Server(File root, String pin, int port) throws IOException {
            this.root = root.getCanonicalFile();
            this.pin = pin;
            this.token = random(16);
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port));
            socket = s;
        }

        int port() { return socket.getLocalPort(); }

        void start() { Thread t = new Thread(this, "seeky-wifi"); t.setDaemon(true); t.start(); }

        @Override public void run() {
            while (!stopped) {
                try {
                    Socket s = socket.accept();
                    s.setSoTimeout(30000);
                    pool.execute(() -> handle(s));
                } catch (Exception e) { if (stopped) break; }
            }
        }

        void stop() {
            stopped = true;
            try { socket.close(); } catch (IOException ignored) {}
            pool.shutdownNow();
        }

        void handle(Socket s) {
            try (Socket sock = s; InputStream in = new BufferedInputStream(sock.getInputStream(), 65536); OutputStream out = new BufferedOutputStream(sock.getOutputStream(), 65536)) {
                Request r = Request.read(in);
                if (r == null) return;
                lastUse = System.currentTimeMillis();
                respond(r, in, out);
                out.flush();
            } catch (Exception ignored) {}
        }

        boolean authed(Request r) {
            String cookie = r.headers.get("cookie");
            if (cookie == null) return false;
            for (String part : cookie.split(";")) {
                String p = part.trim();
                if (p.startsWith("seeky=") && same(p.substring(6), token)) return true;
            }
            return false;
        }

        void respond(Request r, InputStream in, OutputStream out) throws IOException {
            String path = r.path;
            if (path.equals("/login") && r.method.equals("POST")) { login(r, in, out); return; }
            if (!authed(r)) {
                if (r.method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) html(out, 200, loginPage(""), null);
                else text(out, 403, "Sessão necessária.");
                return;
            }
            if (path.equals("/") && r.method.equals("GET")) {
                File dir = resolve(r.query.getOrDefault("d", ""));
                if (dir == null || !dir.isDirectory()) { text(out, 404, "Pasta não encontrada."); return; }
                html(out, 200, listing(dir), null);
            } else if (path.equals("/f") && r.method.equals("GET")) {
                File f = resolve(r.query.getOrDefault("p", ""));
                if (f == null || !f.isFile() || !f.canRead()) { text(out, 404, "Ficheiro não encontrado."); return; }
                download(f, out);
                sent.incrementAndGet();
                notifyChanged();
            } else if (path.equals("/up") && r.method.equals("PUT")) {
                upload(r, in, out);
            } else if (path.equals("/logout")) {
                header(out, 303, "text/plain", 0, "Location: /\r\nSet-Cookie: seeky=; Max-Age=0; Path=/; HttpOnly; SameSite=Strict\r\n");
            } else text(out, 404, "Não encontrado.");
        }

        void login(Request r, InputStream in, OutputStream out) throws IOException {
            long length = r.length();
            if (length < 0 || length > 1024) { text(out, 413, "Pedido inválido."); return; }
            byte[] body = new byte[(int) length];
            int read = 0;
            while (read < body.length) { int n = in.read(body, read, body.length - read); if (n < 0) break; read += n; }
            String typed = form(new String(body, 0, read, StandardCharsets.UTF_8)).getOrDefault("pin", "").replaceAll("\\s", "");
            synchronized (this) {
                long now = System.currentTimeMillis();
                if (now < lockedUntil) { html(out, 429, loginPage("Demasiadas tentativas. Aguarde um minuto."), null); return; }
                if (same(typed, pin)) {
                    failures = 0;
                    header(out, 303, "text/plain", 0, "Location: /\r\nSet-Cookie: seeky=" + token + "; Path=/; HttpOnly; SameSite=Strict\r\n");
                    return;
                }
                failures++;
                total++;
                if (failures >= 5) { lockedUntil = now + 60000; failures = 0; }
                if (total >= 20) { html(out, 403, loginPage("O Seeky desligou a transferência por segurança."), null); out.flush(); stop(); notifyChanged(); return; }
            }
            html(out, 403, loginPage("Código errado. Confirme o código mostrado no telefone."), null);
        }

        void upload(Request r, InputStream in, OutputStream out) throws IOException {
            if (!"1".equals(r.headers.get("x-seeky"))) { text(out, 403, "Pedido recusado."); return; }
            File dir = resolve(r.query.getOrDefault("d", ""));
            long length = r.length();
            if (dir == null || !dir.isDirectory() || !dir.canWrite()) { text(out, 403, "Não é possível gravar nesta pasta."); return; }
            if (length < 0 || length > 8L * 1024 * 1024 * 1024) { text(out, 411, "Tamanho do ficheiro em falta."); return; }
            File target = unique(dir, cleanName(r.query.getOrDefault("n", "")));
            File part = new File(dir, "." + target.getName() + ".parte");
            try (OutputStream file = new FileOutputStream(part)) {
                byte[] buffer = new byte[65536];
                long left = length;
                while (left > 0) {
                    int n = in.read(buffer, 0, (int) Math.min(buffer.length, left));
                    if (n < 0) throw new EOFException();
                    file.write(buffer, 0, n);
                    left -= n;
                    lastUse = System.currentTimeMillis();
                }
            } catch (IOException e) { part.delete(); throw e; }
            if (!part.renameTo(target)) { part.delete(); text(out, 500, "Não foi possível guardar."); return; }
            received.incrementAndGet();
            receivedPaths.add(target.getPath());
            notifyChanged();
            byte[] json = ("{\"ok\":true,\"name\":\"" + jsonEscape(target.getName()) + "\"}").getBytes(StandardCharsets.UTF_8);
            header(out, 200, "application/json; charset=utf-8", json.length, "");
            out.write(json);
        }

        void download(File f, OutputStream out) throws IOException {
            String type = URLConnection.guessContentTypeFromName(f.getName());
            header(out, 200, type == null ? "application/octet-stream" : type, f.length(),
                    "Content-Disposition: attachment; filename*=UTF-8''" + encode(f.getName()) + "\r\n");
            try (InputStream file = new FileInputStream(f)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = file.read(buffer)) != -1; ) { out.write(buffer, 0, n); lastUse = System.currentTimeMillis(); }
            }
        }

        /** Caminho relativo à pasta partilhada; null se sair dela. */
        File resolve(String relative) {
            try {
                File f = new File(root, relative == null ? "" : relative).getCanonicalFile();
                String base = root.getPath();
                if (f.getPath().equals(base) || f.getPath().startsWith(base.endsWith(File.separator) ? base : base + File.separator)) return f;
            } catch (IOException ignored) {}
            return null;
        }

        String relative(File f) {
            String base = root.getPath(), p = f.getPath();
            if (p.equals(base)) return "";
            return p.substring(base.length() + (base.endsWith(File.separator) ? 0 : 1));
        }

        void notifyChanged() { Runnable r = changed; if (r != null) r.run(); }

        String listing(File dir) {
            StringBuilder b = new StringBuilder(page("Seeky"));
            String rel = relative(dir);
            b.append("<header><b>Seeky</b> · ").append(esc(rel.isEmpty() ? "Armazenamento" : rel)).append("<a class=out href='/logout'>Sair</a></header>");
            if (dir.canWrite()) b.append("<section class=up><label>Enviar ficheiros para esta pasta<input id=files type=file multiple></label><div id=state></div></section>");
            b.append("<ul>");
            if (!rel.isEmpty()) {
                File parent = dir.getParentFile();
                b.append("<li class=dir><a href='/?d=").append(encode(parent == null ? "" : relative(parent))).append("'>⬑ Pasta anterior</a></li>");
            }
            File[] children = dir.listFiles();
            List<File> list = children == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(children));
            list.removeIf(f -> f.getName().startsWith("."));
            list.sort((x, y) -> x.isDirectory() != y.isDirectory() ? (x.isDirectory() ? -1 : 1) : x.getName().compareToIgnoreCase(y.getName()));
            DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM);
            for (File f : list) {
                if (f.isDirectory()) b.append("<li class=dir><a href='/?d=").append(encode(relative(f))).append("'>📁 ").append(esc(f.getName())).append("</a></li>");
                else b.append("<li><a href='/f?p=").append(encode(relative(f))).append("'>").append(esc(f.getName())).append("</a><small>")
                        .append(size(f.length())).append(" · ").append(esc(date.format(new Date(f.lastModified())))).append("</small></li>");
            }
            if (list.isEmpty()) b.append("<li><small>Pasta vazia</small></li>");
            b.append("</ul>");
            if (dir.canWrite()) b.append("<script>var d=").append(jsString(rel)).append(";").append(UPLOAD_JS).append("</script>");
            return b.append("</body></html>").toString();
        }

        String loginPage(String error) {
            return page("Seeky") + "<form class=login method=post action='/login'><h1>Seeky</h1><p>Escreva o código mostrado no telefone.</p>"
                    + (error.isEmpty() ? "" : "<p class=err>" + esc(error) + "</p>")
                    + "<input name=pin inputmode=numeric autocomplete=off maxlength=7 autofocus><button>Entrar</button></form></body></html>";
        }
    }

    static final String UPLOAD_JS = "var f=document.getElementById('files'),s=document.getElementById('state');"
            + "f.onchange=function(){var l=Array.prototype.slice.call(f.files),i=0;function next(){if(i>=l.length){s.textContent='Concluído. A actualizar…';setTimeout(function(){location.reload()},600);return;}"
            + "var file=l[i++],x=new XMLHttpRequest();x.open('PUT','/up?d='+encodeURIComponent(d)+'&n='+encodeURIComponent(file.name));x.setRequestHeader('X-Seeky','1');"
            + "x.upload.onprogress=function(e){if(e.lengthComputable)s.textContent=file.name+' · '+Math.round(e.loaded*100/e.total)+'%';};"
            + "x.onload=function(){if(x.status!=200)s.textContent='Falhou: '+file.name;next();};x.onerror=function(){s.textContent='Ligação perdida';};x.send(file);}next();};";

    static String page(String title) {
        return "<!doctype html><html lang=pt><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>" + esc(title) + "</title><style>"
                + "body{font:15px system-ui,sans-serif;margin:0;background:#f3eee6;color:#23251f}header{padding:16px 20px;background:#3f4b3a;color:#fff;display:flex;gap:8px;align-items:center}"
                + "header .out{margin-left:auto;color:#fff}ul{list-style:none;margin:0;padding:8px 12px}li{background:#fbf8f2;margin:6px 0;padding:12px 14px;border-radius:12px;display:flex;justify-content:space-between;gap:12px}"
                + "a{color:#23251f;text-decoration:none;word-break:break-word}li.dir a{font-weight:600}small{color:#686358;white-space:nowrap}"
                + ".up{margin:12px;padding:14px;border:2px dashed #b8ae9c;border-radius:14px}.up input{display:block;margin-top:8px}#state{margin-top:8px;color:#3f4b3a}"
                + ".login{max-width:320px;margin:12vh auto;padding:24px;background:#fbf8f2;border-radius:18px;text-align:center}.login input{font-size:28px;letter-spacing:6px;width:100%;text-align:center;padding:8px;box-sizing:border-box}"
                + ".login button{margin-top:12px;width:100%;padding:12px;border:0;border-radius:12px;background:#3f4b3a;color:#fff;font-size:16px}.err{color:#b93d56}"
                + "</style></head><body>";
    }

    // ---- pedidos HTTP ----

    static final class Request {
        String method, path;
        final Map<String, String> query = new HashMap<>(), headers = new HashMap<>();

        long length() {
            try { return Long.parseLong(headers.getOrDefault("content-length", "-1").trim()); } catch (NumberFormatException e) { return -1; }
        }

        static Request read(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int state = 0;
            while (state < 4) {
                int c = in.read();
                if (c < 0) return null;
                head.write(c);
                if (head.size() > 16384) return null;
                state = (c == '\r' && (state == 0 || state == 2)) ? state + 1 : (c == '\n' && (state == 1 || state == 3)) ? state + 1 : 0;
            }
            String[] lines = head.toString("UTF-8").split("\r\n");
            String[] first = lines[0].split(" ");
            if (first.length < 2) return null;
            Request r = new Request();
            r.method = first[0].toUpperCase(Locale.ROOT);
            String target = first[1];
            int q = target.indexOf('?');
            r.path = q < 0 ? target : target.substring(0, q);
            if (q >= 0) r.query.putAll(form(target.substring(q + 1)));
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) r.headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
            }
            return r;
        }
    }

    static Map<String, String> form(String s) {
        Map<String, String> out = new HashMap<>();
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            try {
                out.put(URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), "UTF-8"), eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {}
        }
        return out;
    }

    static void header(OutputStream out, int code, String type, long length, String extra) throws IOException {
        String reason = code == 200 ? "OK" : code == 303 ? "See Other" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : code == 429 ? "Too Many Requests" : "Error";
        String h = "HTTP/1.1 " + code + " " + reason + "\r\nContent-Type: " + type + "\r\nContent-Length: " + length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer"
                + "\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'"
                + "\r\nConnection: close\r\n" + extra + "\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
    }

    static void html(OutputStream out, int code, String body, String extra) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        header(out, code, "text/html; charset=utf-8", b.length, extra == null ? "" : extra);
        out.write(b);
    }

    static void text(OutputStream out, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        header(out, code, "text/plain; charset=utf-8", b.length, "");
        out.write(b);
    }

    // ---- utilitários ----

    static String random(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 255));
        return s.toString();
    }

    static String pin() { return String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000)); }

    static boolean same(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String cleanName(String raw) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}:*?\"<>|]", "").trim();
        while (name.startsWith(".")) name = name.substring(1);
        if (name.length() > 180) name = name.substring(name.length() - 180);
        return name.isEmpty() ? "ficheiro" : name;
    }

    static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) { f = new File(dir, base + " (" + i + ")" + ext); if (!f.exists()) return f; }
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String encode(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (UnsupportedEncodingException e) { return ""; }
    }

    static String jsonEscape(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20 || ch == '<' || ch == '>' || ch == '&' || ch == 0x2028 || ch == 0x2029) b.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.toString();
    }

    static String jsString(String s) { return "\"" + jsonEscape(s) + "\""; }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return String.format(Locale.ROOT, "%.1f %s", v, units[i]);
    }

    /** Endereço IPv4 da rede local (Wi-Fi ou ponto de acesso do telefone). */
    static String address() {
        String fallback = null;
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                String name = n.getName().toLowerCase(Locale.ROOT);
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || !a.isSiteLocalAddress()) continue;
                    if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("eth")) return a.getHostAddress();
                    if (fallback == null && !name.startsWith("rmnet") && !name.startsWith("ccmni")) fallback = a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    static File sharedRoot(Context c) {
        if (StorageAccess.all(c)) return Environment.getExternalStorageDirectory();
        File seeky = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky");
        seeky.mkdirs();
        return seeky;
    }

    // ---- painel ----

    static void show(MainActivity a) {
        String ip = address();
        if (ip == null) {
            a.dialog().setTitle("Transferir por Wi-Fi").setMessage("Ligue o Wi-Fi do telefone (ou o ponto de acesso) e ponha o computador na mesma rede. Depois tente novamente.")
                    .setPositiveButton("Fechar", null).show();
            return;
        }
        stopRunning();
        Server server = null;
        for (int port = 8080; port <= 8090 && server == null; port++) {
            try { server = new Server(sharedRoot(a), pin(), port); } catch (IOException ignored) {}
        }
        if (server == null) { a.toast("Não foi possível abrir a transferência. Tente novamente."); return; }
        final Server s = server;
        running = s;
        s.start();
        WifiManager wifi = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.WifiLock lock = null;
        try { if (wifi != null) { lock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "seeky:transfer"); lock.acquire(); } } catch (Exception ignored) { lock = null; }
        final WifiManager.WifiLock held = lock;
        String url = "http://" + ip + (s.port() == 80 ? "" : ":" + s.port());
        UiPanel p = new UiPanel(a, "Transferir por Wi-Fi", AppLanguage.text(a, "Computador e telefone na mesma rede"), "wifi");
        p.section("No computador, abra");
        TextView address = p.text(url, 22, p.accent);
        address.setTypeface(ThemeStyle.BOLD);
        address.setTextIsSelectable(true);
        p.content.addView(address);
        p.section("Código de acesso");
        TextView code = p.text(s.pin.substring(0, 3) + " " + s.pin.substring(3), 30, p.ink);
        code.setTypeface(ThemeStyle.BOLD);
        code.setLetterSpacing(.12f);
        p.content.addView(code);
        TextView status = p.text("À espera do computador…", 13, p.muted);
        status.setPadding(p.dp(3), p.dp(10), p.dp(3), p.dp(4));
        p.content.addView(status);
        p.note(StorageAccess.all(a) ? "Pode ver todas as pastas do telefone e enviar ficheiros para qualquer uma delas."
                : "Sem acesso geral aos ficheiros, a transferência usa a pasta Download/Seeky. Active o acesso geral em Ferramentas para ver todas as pastas.");
        p.note("Mantenha este painel aberto durante a transferência. Desliga-se ao fechar ou após 15 minutos sem uso.");
        p.action("close", "Parar transferência", "Fecha o acesso do computador", false, () -> {});
        Handler ui = new Handler(Looper.getMainLooper());
        s.changed = () -> ui.post(() -> {
            if (s.stopped) { status.setText(AppLanguage.text(a, "Transferência desligada por segurança.")); return; }
            status.setText(AppLanguage.text(a, "Recebidos:") + " " + s.received.get() + " · " + AppLanguage.text(a, "Enviados:") + " " + s.sent.get());
        });
        Runnable[] idle = new Runnable[1];
        idle[0] = () -> {
            if (s.stopped || running != s) return;
            if (System.currentTimeMillis() - s.lastUse > IDLE) { try { p.dialog.dismiss(); } catch (Exception ignored) {} return; }
            ui.postDelayed(idle[0], 30000);
        };
        ui.postDelayed(idle[0], 30000);
        p.dismissed = () -> {
            ui.removeCallbacks(idle[0]);
            int got = s.received.get();
            s.stop();
            if (running == s) running = null;
            try { if (held != null && held.isHeld()) held.release(); } catch (Exception ignored) {}
            if (got > 0) {
                MediaScannerConnection.scanFile(a.getApplicationContext(), s.receivedPaths.toArray(new String[0]), null, null);
                a.toast(got + " " + AppLanguage.text(a, "ficheiros recebidos. O catálogo vai actualizar-se."));
            }
        };
        p.show();
        try { p.dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } catch (Exception ignored) {}
    }

    static void stopRunning() {
        Server s = running;
        running = null;
        if (s != null) s.stop();
    }
}
