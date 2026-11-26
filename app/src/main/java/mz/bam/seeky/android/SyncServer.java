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
        /** The computer's own EC key pair (P-256), made once and kept; the phone remembers its public half. */
        java.security.KeyPair identity() throws Exception;
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
            if (op.equals("pair2")) pair(ch, open, device);
            else if (op.equals("session2")) session(ch, open, device, open.optString("name", "Telemóvel"));
            else if (op.equals("pair") || op.equals("session")) ch.plain(error("Actualize o Seeky no telemóvel para a versão 5.55 ou mais recente e volte a ligá-lo."));
        } catch (Exception ignored) {}
    }
    static String error(String m) { return "{\"error\":" + JSONObject.quote(m) + "}"; }
    synchronized boolean tooMany() { return failures >= 5; }
    /** 5.55: ECDH for the key, the code confirmed bit by bit (the phone commits first in each round). */
    void pair(SyncWire.Channel ch, JSONObject open, String device) throws Exception {
        String code = host.code();
        if (code == null) { ch.plain(error("No computador, abra Ligar telemóvel para ver o código.")); return; }
        if (tooMany()) { ch.plain(error("Demasiadas tentativas. No computador, mostre um código novo.")); return; }
        int value = SyncWire.codeValue(code);
        byte[] theirPub = SyncWire.unb64(open.getString("epk")); java.security.PublicKey theirs = SyncWire.publicKey(theirPub);
        java.security.KeyPair eph = SyncWire.ephemeral(), id = host.identity(); byte[] ephPub = eph.getPublic().getEncoded(), idPub = id.getPublic().getEncoded();
        ch.plain(new JSONObject().put("epk", SyncWire.b64(ephPub)).put("spk", SyncWire.b64(idPub)).toString());
        byte[] transcript = SyncWire.sha(SyncWire.ascii("seeky-pair-2"), SyncWire.ascii(device), theirPub, ephPub, idPub);
        for (int i = 0; i < SyncWire.CODE_BITS; i++) {
            byte[] commitment = SyncWire.unb64(new JSONObject(ch.plain()).getString("c"));
            byte[] ns = SyncWire.random(16); int b = SyncWire.bit(value, i);
            ch.plain(new JSONObject().put("c", SyncWire.b64(SyncWire.commit(ns, "pc", i, b, transcript))).toString());
            byte[] nc = SyncWire.unb64(new JSONObject(ch.plain()).getString("n"));
            if (!SyncWire.same(SyncWire.commit(nc, "phone", i, b, transcript), commitment)) { synchronized (this) { failures++; } host.codeFailed(); ch.plain(error("Código errado.")); return; }
            ch.plain(new JSONObject().put("n", SyncWire.b64(ns)).toString());
        }
        synchronized (this) { failures = 0; }
        ch.key = SyncWire.hkdf(transcript, SyncWire.concat(SyncWire.agree(eph.getPrivate(), theirs), SyncWire.agree(id.getPrivate(), theirs)), "seeky-pair-key-2");
        byte[] token = SyncWire.random(32);
        host.paired(device, open.optString("name", "Telemóvel"), token); host.codeUsed();
        ch.send(new JSONObject().put("token", SyncWire.b64(token)).put("id", host.id()).put("name", host.name()).toString());
    }
    /** 5.55: fresh ECDH keys every session, bound to this computer's key, the pairing secret and both nonces. */
    void session(SyncWire.Channel ch, JSONObject open, String device, String deviceName) throws Exception {
        byte[] token = host.token(device);
        if (token == null) { ch.plain(error("Este telemóvel já não está ligado a este computador. Ligue-o outra vez.")); return; }
        byte[] theirPub = SyncWire.unb64(open.getString("epk")), nc = SyncWire.unb64(open.getString("n")); if (nc.length != 16) return;
        java.security.PublicKey theirs = SyncWire.publicKey(theirPub); java.security.KeyPair eph = SyncWire.ephemeral(), id = host.identity();
        byte[] ephPub = eph.getPublic().getEncoded(), ns = SyncWire.random(16);
        ch.plain(new JSONObject().put("epk", SyncWire.b64(ephPub)).put("n", SyncWire.b64(ns)).toString());
        byte[] transcript = SyncWire.sha(SyncWire.ascii("seeky-session-2"), SyncWire.ascii(device), theirPub, ephPub, id.getPublic().getEncoded(), nc, ns);
        ch.key = SyncWire.hkdf(transcript, SyncWire.concat(SyncWire.concat(SyncWire.agree(eph.getPrivate(), theirs), SyncWire.agree(id.getPrivate(), theirs)), token), "seeky-session-key-2");
        if (!"seeky".equals(new JSONObject(ch.text()).optString("hello"))) return;
        ch.send("{\"ok\":true}");
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
