package mz.bam.seeky.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Seeky ligado, do lado do telemóvel: encontra o computador, emparelha, sincroniza e troca ficheiros. */
final class SyncClient {
    private SyncClient() {}
    interface Local { SyncState state(long since) throws Exception; SyncState.Changes apply(SyncState computer) throws Exception; }
    static final class Computer { String id = "", name = "", host = ""; int port = SyncWire.TCP_PORT; byte[] token, key; }
    static final class Result { int notesHere, otherHere, notesThere, otherThere; }

    /** Asks the local network who has Seeky open on a computer (a second or two). */
    static List<Computer> discover(int millis) {
        List<Computer> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true); s.setSoTimeout(300); byte[] hello = SyncWire.HELLO.getBytes(StandardCharsets.UTF_8);
            Set<InetAddress> targets = new LinkedHashSet<>(); targets.add(InetAddress.getByName("255.255.255.255"));
            try { for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) if (n.isUp() && !n.isLoopback()) for (InterfaceAddress a : n.getInterfaceAddresses()) if (a.getBroadcast() != null) targets.add(a.getBroadcast()); } catch (Exception ignored) {}
            long end = System.currentTimeMillis() + millis, nextSend = 0;
            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() >= nextSend) { for (InetAddress t : targets) try { s.send(new DatagramPacket(hello, hello.length, t, SyncWire.UDP_PORT)); } catch (IOException ignored) {} nextSend = System.currentTimeMillis() + 700; }
                try {
                    byte[] buf = new byte[512]; DatagramPacket p = new DatagramPacket(buf, buf.length); s.receive(p);
                    String text = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8); if (!text.startsWith(SyncWire.ANSWER)) continue;
                    JSONObject o = new JSONObject(text.substring(SyncWire.ANSWER.length())); Computer c = new Computer();
                    c.id = o.optString("id"); c.name = o.optString("name", "Computador"); c.port = o.optInt("port", SyncWire.TCP_PORT); c.host = p.getAddress().getHostAddress();
                    if (seen.add(c.id)) out.add(c);
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }
    static SyncWire.Channel connect(String host, int port) throws IOException {
        Socket s = new Socket(); s.connect(new InetSocketAddress(host, port), 6000); s.setSoTimeout(120000);
        return new SyncWire.Channel(s.getInputStream(), s.getOutputStream(), s);
    }
    static void failIfError(String plain) throws IOException { JSONObject o; try { o = new JSONObject(plain); } catch (Exception e) { return; } if (o.has("error")) throw new IOException(o.optString("error")); }

    /**
     * Pairs with the 6-digit code shown on the computer (5.55). An ephemeral ECDH exchange gives the key; the code is
     * checked bit by bit with commitments, so nobody can test codes away from the connection. The phone keeps the
     * computer's own public key and refuses any other computer afterwards.
     */
    static Computer pair(String host, int port, String device, String deviceName, String code) throws Exception {
        int value; try { value = SyncWire.codeValue(code); } catch (IllegalArgumentException e) { throw new IOException("Escreva os 6 números que aparecem no computador."); }
        try (SyncWire.Channel ch = connect(host, port)) {
            java.security.KeyPair mine = SyncWire.ephemeral(); byte[] myPub = mine.getPublic().getEncoded();
            ch.plain(new JSONObject().put("op", "pair2").put("device", device).put("name", deviceName).put("epk", SyncWire.b64(myPub)).toString());
            JSONObject hi = new JSONObject(ch.plain()); if (hi.has("error")) throw new IOException(hi.optString("error"));
            byte[] theirEph = SyncWire.unb64(hi.getString("epk")), theirStatic = SyncWire.unb64(hi.getString("spk"));
            byte[] transcript = SyncWire.sha(SyncWire.ascii("seeky-pair-2"), SyncWire.ascii(device), myPub, theirEph, theirStatic);
            for (int i = 0; i < SyncWire.CODE_BITS; i++) {
                byte[] nc = SyncWire.random(16); int b = SyncWire.bit(value, i);
                ch.plain(new JSONObject().put("c", SyncWire.b64(SyncWire.commit(nc, "phone", i, b, transcript))).toString());
                JSONObject cb = new JSONObject(ch.plain()); if (cb.has("error")) throw new IOException(cb.optString("error"));
                ch.plain(new JSONObject().put("n", SyncWire.b64(nc)).toString());
                JSONObject nb = new JSONObject(ch.plain()); if (nb.has("error")) throw new IOException(nb.optString("error"));
                if (!SyncWire.same(SyncWire.commit(SyncWire.unb64(nb.getString("n")), "pc", i, b, transcript), SyncWire.unb64(cb.getString("c")))) throw new IOException("Código errado.");
            }
            java.security.PublicKey eph = SyncWire.publicKey(theirEph), stat = SyncWire.publicKey(theirStatic);
            ch.key = SyncWire.hkdf(transcript, SyncWire.concat(SyncWire.agree(mine.getPrivate(), eph), SyncWire.agree(mine.getPrivate(), stat)), "seeky-pair-key-2");
            JSONObject o = new JSONObject(ch.text());
            Computer c = new Computer(); c.host = host; c.port = port; c.id = o.optString("id"); c.name = o.optString("name", "Computador"); c.token = SyncWire.unb64(o.getString("token")); c.key = theirStatic;
            if (c.token.length < 32) throw new IOException("Resposta inválida do computador.");
            return c;
        } catch (IOException e) { throw e; } catch (SecurityException e) { throw new IOException("Código errado ou computador desconhecido."); } catch (Exception e) { String m = e.getMessage(); throw new IOException(m == null ? "Não foi possível ligar ao computador." : m); }
    }
    /** A session (5.55): fresh ECDH keys each time, bound to the computer's pinned key and to the pairing secret. */
    static SyncWire.Channel session(Computer c, String device, String deviceName) throws Exception {
        if (c.key == null || c.key.length == 0) throw new IOException("Por segurança, volte a ligar o telemóvel ao computador (código novo no computador).");
        SyncWire.Channel ch = connect(c.host, c.port);
        try {
            java.security.KeyPair mine = SyncWire.ephemeral(); byte[] myPub = mine.getPublic().getEncoded(), nc = SyncWire.random(16);
            ch.plain(new JSONObject().put("op", "session2").put("device", device).put("name", deviceName).put("epk", SyncWire.b64(myPub)).put("n", SyncWire.b64(nc)).toString());
            JSONObject hi = new JSONObject(ch.plain()); if (hi.has("error")) throw new IOException(hi.optString("error"));
            byte[] theirEph = SyncWire.unb64(hi.getString("epk")), ns = SyncWire.unb64(hi.getString("n"));
            byte[] transcript = SyncWire.sha(SyncWire.ascii("seeky-session-2"), SyncWire.ascii(device), myPub, theirEph, c.key, nc, ns);
            byte[] secret = SyncWire.concat(SyncWire.concat(SyncWire.agree(mine.getPrivate(), SyncWire.publicKey(theirEph)), SyncWire.agree(mine.getPrivate(), SyncWire.publicKey(c.key))), c.token);
            ch.key = SyncWire.hkdf(transcript, secret, "seeky-session-key-2");
            // Only the computer that was paired (its private key and the pairing secret) can answer this.
            JSONObject ok; try { ch.send("{\"hello\":\"seeky\"}"); ok = new JSONObject(ch.text()); } catch (Exception e) { throw new SecurityException("other computer"); }
            if (!ok.optBoolean("ok")) throw new SecurityException("other computer");
            return ch;
        }
        catch (SecurityException e) { ch.close(); throw new IOException("O computador não é o mesmo que foi ligado. Volte a ligar o telemóvel."); }
        catch (Exception e) { ch.close(); throw e; }
    }
    /** Both sides end up with the same notes, study and music; returns what changed on each side. */
    static Result sync(Computer c, String device, String deviceName, Local local, long since) throws Exception {
        try (SyncWire.Channel ch = session(c, device, deviceName)) {
            SyncState mine = local.state(since); mine.device = device; mine.name = deviceName; mine.since = since;
            ch.send("{\"req\":\"sync\"}"); ch.send(mine.json().toString()); mine.sendMedia(ch);
            JSONObject there = new JSONObject(ch.text()); failIfError(there.toString());
            SyncState computer = SyncState.parse(ch.text()); computer.receiveMedia(ch);
            SyncState.Changes here = local.apply(computer);
            ch.send("{\"req\":\"bye\"}");
            Result r = new Result(); r.notesThere = there.optInt("notes"); r.otherThere = there.optInt("other"); r.notesHere = here.notes(); r.otherHere = here.progress.size() + here.readings.size() + here.cards.size() + here.records.size() + here.plays.size() + here.favorites.size() + here.study();
            return r;
        }
    }
    /** Sends one file to the computer (it lands in Transferências\Seeky there). */
    static String send(Computer c, String device, String deviceName, String name, long size, InputStream data) throws Exception {
        try (SyncWire.Channel ch = session(c, device, deviceName)) {
            ch.send(new JSONObject().put("req", "put").put("name", name).put("size", size).toString());
            SyncWire.sendFile(ch, data, size);
            JSONObject o = new JSONObject(ch.text()); failIfError(o.toString()); ch.send("{\"req\":\"bye\"}");
            return o.optString("saved", name);
        }
    }
    interface Sink { OutputStream open(String name, long size) throws IOException; void done(String name, boolean ok) throws IOException; }
    /** Receives the files the computer put in "Enviar para o telemóvel"; returns how many arrived. */
    static int receive(Computer c, String device, String deviceName, Sink sink) throws Exception {
        int n = 0;
        try (SyncWire.Channel ch = session(c, device, deviceName)) {
            ch.send("{\"req\":\"outbox\"}"); JSONArray files = new JSONObject(ch.text()).optJSONArray("files");
            for (int i = 0; files != null && i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i); ch.send(new JSONObject().put("req", "get").put("id", f.getString("id")).toString());
                JSONObject head = new JSONObject(ch.text()); if (head.has("error")) { ch.send(new JSONObject().put("req", "taken").put("id", f.getString("id")).toString()); ch.text(); continue; }
                String name = SyncWire.cleanName(head.optString("name")); long size = head.optLong("size");
                boolean ok = false;
                try (OutputStream out = sink.open(name, size)) { SyncWire.receiveFile(ch, out, size); ok = true; }
                finally { sink.done(name, ok); }
                ch.send(new JSONObject().put("req", "taken").put("id", f.getString("id")).toString()); ch.text(); n++;
            }
            ch.send("{\"req\":\"bye\"}");
        }
        return n;
    }
}
