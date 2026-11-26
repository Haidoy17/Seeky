package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seeky ligado — a conversa entre o telemóvel e o computador na rede local: mensagens com tamanho à
 * frente, cifradas com AES-GCM (chave de 256 bits), sem HTTP e sem passar pela internet.
 * Ao emparelhar, a chave vem do código de 6 dígitos mostrado no computador (PBKDF2); depois usa-se a
 * chave de sessão combinada nesse momento.
 */
final class SyncWire {
    private SyncWire() {}
    static final int UDP_PORT = 47901, TCP_PORT = 47902, MAX_FRAME = 8 << 20, CHUNK = 256 << 10;
    static final String HELLO = "SEEKY?", ANSWER = "SEEKY!";
    static final SecureRandom RANDOM = new SecureRandom();

    static byte[] random(int n) { byte[] b = new byte[n]; RANDOM.nextBytes(b); return b; }
    static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    static byte[] unb64(String s) { return Base64.getDecoder().decode(s); }
    /** Key from the pairing code (PBKDF2-HMAC-SHA256, 20000 rounds). */
    static byte[] codeKey(String code, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return f.generateSecret(new PBEKeySpec(code.toCharArray(), salt, 20000, 256)).getEncoded();
    }
    /** Session key from the token agreed when pairing. */
    static byte[] tokenKey(byte[] token) throws Exception { return MessageDigest.getInstance("SHA-256").digest(concat("seeky-sync-1".getBytes(StandardCharsets.US_ASCII), token)); }
    static byte[] concat(byte[] a, byte[] b) { byte[] o = new byte[a.length + b.length]; System.arraycopy(a, 0, o, 0, a.length); System.arraycopy(b, 0, o, a.length, b.length); return o; }
    static boolean same(byte[] a, byte[] b) { return MessageDigest.isEqual(a, b); }

    // ---------------------------------------------------------------- 5.55: emparelhar sem dar o código a adivinhar
    // Troca de chaves efémeras ECDH (P-256): a chave da ligação nunca depende do código. O código só confirma que
    // os dois lados são quem dizem, bit a bit (como o Bluetooth "passkey entry"): em cada volta cada lado compromete-se
    // antes de revelar, por isso um impostor tem uma só hipótese em cerca de um milhão e nada para testar fora da ligação.
    static final int CODE_BITS = 20;
    static java.security.KeyPair ephemeral() throws Exception { java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC"); g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), RANDOM); return g.generateKeyPair(); }
    static java.security.PublicKey publicKey(byte[] x509) throws Exception { if (x509 == null || x509.length < 60 || x509.length > 200) throw new SecurityException("Chave inválida."); return java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(x509)); }
    static java.security.PrivateKey privateKey(byte[] pkcs8) throws Exception { return java.security.KeyFactory.getInstance("EC").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs8)); }
    static byte[] agree(java.security.PrivateKey mine, java.security.PublicKey theirs) throws Exception { javax.crypto.KeyAgreement k = javax.crypto.KeyAgreement.getInstance("ECDH"); k.init(mine); k.doPhase(theirs, true); return k.generateSecret(); }
    static byte[] hmac(byte[] key, byte[]... parts) throws Exception { javax.crypto.Mac m = javax.crypto.Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(key, "HmacSHA256")); for (byte[] p : parts) m.update(p); return m.doFinal(); }
    static byte[] sha(byte[]... parts) throws Exception { MessageDigest d = MessageDigest.getInstance("SHA-256"); for (byte[] p : parts) { d.update(intBytes(p.length)); d.update(p); } return d.digest(); }
    static byte[] intBytes(int n) { return new byte[]{(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n}; }
    static byte[] ascii(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    /** HKDF-SHA256 (extract and one expand block): a 256-bit key. */
    static byte[] hkdf(byte[] salt, byte[] secret, String info) throws Exception { byte[] prk = hmac(salt, secret); return hmac(prk, ascii(info), new byte[]{1}); }
    /** The 6-digit code as 20 bits. */
    static int codeValue(String code) { String d = code == null ? "" : code.replaceAll("\\D", ""); if (d.length() != 6) throw new IllegalArgumentException("Código de 6 números."); return Integer.parseInt(d); }
    static int bit(int code, int i) { return (code >>> i) & 1; }
    /** Commitment to one bit of the code, bound to the whole conversation (both keys, the device, the side). */
    static byte[] commit(byte[] nonce, String side, int round, int bit, byte[] transcript) throws Exception { return hmac(nonce, ascii("seeky-bit-2|" + side + "|" + round + "|" + bit), transcript); }

    static byte[] seal(byte[] key, byte[] plain) throws Exception {
        byte[] iv = random(12); Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return concat(iv, c.doFinal(plain));
    }
    static byte[] open(byte[] key, byte[] sealed) throws Exception {
        if (sealed.length < 12 + 16) throw new IOException("Mensagem incompleta.");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, sealed, 0, 12));
        try { return c.doFinal(sealed, 12, sealed.length - 12); } catch (javax.crypto.AEADBadTagException e) { throw new SecurityException("Código ou chave errados."); }
    }

    static void frame(DataOutputStream out, byte[] b) throws IOException { if (b.length > MAX_FRAME) throw new IOException("Mensagem demasiado grande."); out.writeInt(b.length); out.write(b); out.flush(); }
    static byte[] frame(DataInputStream in) throws IOException { int n = in.readInt(); if (n < 0 || n > MAX_FRAME) throw new IOException("Mensagem inválida."); byte[] b = new byte[n]; in.readFully(b); return b; }

    /** One side of a conversation: plain frames for the opening, sealed frames afterwards. */
    static final class Channel implements Closeable {
        final DataInputStream in; final DataOutputStream out; final Closeable socket; byte[] key;
        Channel(InputStream in, OutputStream out, Closeable socket) { this.in = new DataInputStream(new BufferedInputStream(in, 65536)); this.out = new DataOutputStream(new BufferedOutputStream(out, 65536)); this.socket = socket; }
        void plain(String json) throws IOException { frame(out, json.getBytes(StandardCharsets.UTF_8)); }
        String plain() throws IOException { return new String(frame(in), StandardCharsets.UTF_8); }
        void send(byte[] b) throws Exception { frame(out, seal(key, b)); }
        byte[] receive() throws Exception { return SyncWire.open(key, frame(in)); }
        void send(String json) throws Exception { send(json.getBytes(StandardCharsets.UTF_8)); }
        String text() throws Exception { return new String(receive(), StandardCharsets.UTF_8); }
        @Override public void close() throws IOException { try { out.flush(); } catch (IOException ignored) {} socket.close(); }
    }
    /** Sends a file as sealed chunks after a header; the receiver checks the size and SHA-256. */
    static void sendFile(Channel ch, InputStream data, long size) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256"); byte[] buf = new byte[CHUNK]; long left = size;
        while (left > 0) { int n = data.read(buf, 0, (int) Math.min(buf.length, left)); if (n < 0) throw new EOFException("O ficheiro terminou antes do tempo."); sha.update(buf, 0, n); ch.send(java.util.Arrays.copyOf(buf, n)); left -= n; }
        ch.send(sha.digest());
    }
    static void receiveFile(Channel ch, OutputStream out, long size) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256"); long left = size;
        while (left > 0) { byte[] b = ch.receive(); if (b.length > left) throw new IOException("Ficheiro maior do que o anunciado."); sha.update(b); out.write(b); left -= b.length; }
        if (!same(sha.digest(), ch.receive())) throw new IOException("O ficheiro chegou danificado.");
    }
    /** A file name that is safe in any folder. */
    static String cleanName(String raw) {
        String n = raw == null ? "" : raw.replace('\\', '/'); n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\x00-\\x1f<>:\"|?*]", "_").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) n = "ficheiro"; if (n.startsWith(".")) n = "_" + n;
        return n.length() > 150 ? n.substring(0, 150) : n;
    }
    static File unique(File dir, String name) {
        File f = new File(dir, name); if (!f.exists()) return f;
        String base = name.replaceFirst("\\.[^.]*$", ""), ext = name.length() > base.length() ? name.substring(base.length()) : "";
        for (int i = 2; ; i++) { f = new File(dir, base + " (" + i + ")" + ext); if (!f.exists()) return f; }
    }
}
