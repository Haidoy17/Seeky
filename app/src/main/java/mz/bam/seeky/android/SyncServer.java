package mz.bam.seeky.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Seeky ligado, do lado do computador: responde a "há algum Seeky?" na rede local (UDP), emparelha com
 * o código de 6 dígitos que está no ecrã e, depois, junta o estado do telemóvel, devolve o seu e troca
 * ficheiros. Só fala com telemóveis emparelhados; tudo vai cifrado.
 */
final class SyncServer implements Closeable {
    interface Host {
        String id(); String name();
        /** The code on the screen now, or null when "Ligar telemóvel" is not open. */
        String code(); void codeUsed(); void codeFailed();
        byte[] token(String device); void paired(String device, String deviceName, byte[] token);
        SyncState.Changes apply(String device, SyncState phone) throws Exception;
        SyncState state(String device, long since) throws Exception;
        void synced(String device, String deviceName, SyncState.Changes changes);
        List<String[]> outbox(); File outboxFile(String id); void taken(String id);
        File incoming(String name); void received(File f, String device);
    }
    final Host host; final int tcpPort, udpPort;
    ServerSocket tcp; DatagramSocket udp; volatile boolean closed;
    final ExecutorService pool = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "seeky-ligado"); t.setDaemon(true); return t; });
    int failures;

    SyncServer(Host host, int tcpPort, int udpPort) { this.host = host; this.tcpPort = tcpPort; this.udpPort = udpPort; }
    int port() { return tcp == null ? tcpPort : tcp.getLocalPort(); }

    void start() throws IOException {
        tcp = new ServerSocket(); tcp.setReuseAddress(true); tcp.bind(new InetSocketAddress(tcpPort), 8);
        Thread accept = new Thread(() -> { while (!closed) { try { Socket s = tcp.accept(); s.setSoTimeout(120000); pool.execute(() -> handle(s)); } catch (IOException e) { if (closed) return; } } }, "seeky-ligado-tcp");
        accept.setDaemon(true); accept.start();
        try {
            udp = new DatagramSocket(null); udp.setReuseAddress(true); udp.bind(new InetSocketAddress(udpPort));
            Thread answer = new Thread(this::answer, "seeky-ligado-udp"); answer.setDaemon(true); answer.start();
        } catch (IOException e) { udp = null; } // without discovery the phone can still use the address
    }
    /** "SEEKY?" from a phone → "SEEKY!{id, name, port}" back to it. */
    void answer() {
        byte[] buf = new byte[512];
        while (!closed) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length); udp.receive(p);
                if (!new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).startsWith(SyncWire.HELLO)) continue;
                byte[] reply = (SyncWire.ANSWER + new JSONObject().put("id", host.id()).put("name", host.name()).put("port", port())).getBytes(StandardCharsets.UTF_8);
                udp.send(new DatagramPacket(reply, reply.length, p.getAddress(), p.getPort()));
            } catch (Exception e) { if (closed) return; }
        }
    }
    void handle(Socket s) {
        try (SyncWire.Channel ch = new SyncWire.Channel(s.getInputStream(), s.getOutputStream(), s)) {
            JSONObject open = new JSONObject(ch.plain()); String op = open.optString("op"), device = open.optString("device", "");
            if (!device.matches("[a-f0-9-]{8,64}")) { ch.plain(error("Telemóvel desconhecido.")); return; }
            if (op.equals("pair")) pair(ch, open, device);
            else if (op.equals("session")) session(ch, device, open.optString("name", "Telemóvel"));
        } catch (Exception ignored) {}
    }
    static String error(String m) { return "{\"error\":" + JSONObject.quote(m) + "}"; }
    synchronized boolean tooMany() { return failures >= 5; }
    void pair(SyncWire.Channel ch, JSONObject open, String device) throws Exception {
        String code = host.code();
        if (code == null) { ch.plain(error("No computador, abra Ligar telemóvel para ver o código.")); return; }
        if (tooMany()) { ch.plain(error("Demasiadas tentativas. No computador, mostre um código novo.")); return; }
        ch.key = SyncWire.codeKey(code, SyncWire.unb64(open.getString("salt")));
        try { if (!"seeky".equals(new JSONObject(ch.text()).optString("hello"))) throw new SecurityException(); }
        catch (SecurityException | org.json.JSONException e) { synchronized (this) { failures++; } host.codeFailed(); ch.plain(error("Código errado.")); return; }
        synchronized (this) { failures = 0; }
        byte[] token = SyncWire.random(32);
        host.paired(device, open.optString("name", "Telemóvel"), token); host.codeUsed();
        ch.send(new JSONObject().put("token", SyncWire.b64(token)).put("id", host.id()).put("name", host.name()).toString());
    }
    void session(SyncWire.Channel ch, String device, String deviceName) throws Exception {
        byte[] token = host.token(device);
        if (token == null) { ch.plain(error("Este telemóvel já não está ligado a este computador. Ligue-o outra vez.")); return; }
        ch.plain("{\"ok\":true}"); ch.key = SyncWire.tokenKey(token);
        for (;;) {
            JSONObject r = new JSONObject(ch.text()); String req = r.optString("req");
            switch (req) {
                case "sync": {
                    SyncState phone = SyncState.parse(ch.text()); phone.receiveMedia(ch);
                    SyncState.Changes changes = host.apply(device, phone);
                    SyncState mine = host.state(device, phone.since);
                    ch.send(new JSONObject().put("notes", changes.notes()).put("other", changes.progress.size() + changes.readings.size() + changes.cards.size() + changes.records.size() + changes.plays.size() + changes.favorites.size() + changes.study()).toString());
                    ch.send(mine.json().toString()); mine.sendMedia(ch);
                    host.synced(device, deviceName, changes);
                    break;
                }
                case "outbox": {
                    JSONArray files = new JSONArray(); for (String[] f : host.outbox()) files.put(new JSONObject().put("id", f[0]).put("name", f[1]).put("size", Long.parseLong(f[2])));
                    ch.send(new JSONObject().put("files", files).toString()); break;
                }
                case "get": {
                    File f = host.outboxFile(r.optString("id")); if (f == null || !f.isFile()) { ch.send(error("O ficheiro já não existe no computador.")); break; }
                    ch.send(new JSONObject().put("name", f.getName()).put("size", f.length()).toString());
                    try (InputStream in = new FileInputStream(f)) { SyncWire.sendFile(ch, in, f.length()); }
                    break;
                }
                case "taken": host.taken(r.optString("id")); ch.send("{\"ok\":true}"); break;
                case "put": {
                    long size = r.optLong("size", -1); String name = SyncWire.cleanName(r.optString("name"));
                    if (size < 0 || size > (8L << 30)) { ch.send(error("Tamanho inválido.")); return; }
                    File target = host.incoming(name), part = new File(target.getPath() + ".parte");
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(part))) { SyncWire.receiveFile(ch, out, size); }
                    catch (Exception e) { part.delete(); throw e; }
                    if (!part.renameTo(target)) { part.delete(); ch.send(error("Não foi possível guardar.")); break; }
                    host.received(target, deviceName);
                    ch.send(new JSONObject().put("ok", true).put("saved", target.getName()).toString()); break;
                }
                default: return; // "bye"
            }
        }
    }
    @Override public void close() { closed = true; try { if (tcp != null) tcp.close(); } catch (IOException ignored) {} if (udp != null) udp.close(); pool.shutdownNow(); }
}
