package mz.bam.seeky.android;

import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/**
 * 5.53 · MP3 for narrations, the same on the phone and on the computer: the LAME encoder in pure Java
 * (jump3r, LGPL), constant bit rate for speech, ID3v2.3 title/book/author/track so players list the
 * chapters in order. Reads the PCM WAV files Seeky already writes (8/16/24/32-bit or float).
 */
final class Mp3Writer implements AutoCloseable {
    static final int[] RATES = {8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};
    final OutputStream out; final int channels; final Lame lame; final LameGlobalFlags gfp;
    final byte[] mp3 = new byte[1 << 17];
    long frames;

    static final class Tags {
        final String title, album, artist; final int track, total;
        Tags(String title, String album, String artist, int track, int total) { this.title = clean(title); this.album = clean(album); this.artist = clean(artist); this.track = track; this.total = total; }
        static String clean(String s) { return s == null ? "" : s.replaceAll("[\\p{Cc}]", " ").trim(); }
    }
    interface Progress { void at(long done, long total); }

    /** Speech: 64 kbit/s mono or 96 kbit/s stereo is clear and about a tenth of the WAV. */
    static int bitrate(int channels) { return channels == 1 ? 64 : 96; }

    Mp3Writer(OutputStream out, int sampleRate, int channels, int kbps) {
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("O MP3 aceita áudio mono ou estéreo.");
        if (sampleRate < 8000 || sampleRate > 48000) throw new IllegalArgumentException("Frequência de amostragem não suportada: " + sampleRate);
        this.out = out; this.channels = channels;
        lame = new Lame(); GainAnalysis ga = new GainAnalysis(); BitStream bs = new BitStream(); Presets p = new Presets(); QuantizePVT qupvt = new QuantizePVT();
        Quantize qu = new Quantize(); VBRTag vbr = new VBRTag(); Version ver = new Version(); ID3Tag id3 = new ID3Tag(); Reservoir rv = new Reservoir(); Takehiro tak = new Takehiro();
        Parse parse = new Parse(); MPGLib mpg = new MPGLib(); Interface intf = new Interface(); Common common = new Common();
        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg); bs.setModules(ga, mpg, ver, vbr); id3.setModules(bs, ver); p.setModules(lame);
        qu.setModules(bs, rv, qupvt, tak); qupvt.setModules(tak, rv, lame.enc.psy); rv.setModules(bs); tak.setModules(qupvt); vbr.setModules(lame, bs, ver);
        parse.setModules(ver, id3, p); mpg.setModules(intf, common); intf.setModules(vbr, common);
        gfp = lame.lame_init();
        gfp.num_channels = channels; gfp.in_samplerate = sampleRate; gfp.out_samplerate = valid(sampleRate) ? sampleRate : 0;
        gfp.mode = channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO; gfp.VBR = VbrMode.vbr_off; gfp.brate = kbps; gfp.quality = 5;
        id3.id3tag_init(gfp); gfp.write_id3tag_automatic = false; gfp.findReplayGain = false; gfp.bWriteVbrTag = false;
        if (lame.lame_init_params(gfp) < 0) throw new IllegalArgumentException("O codificador MP3 não aceitou este áudio.");
    }
    static boolean valid(int rate) { for (int r : RATES) if (r == rate) return true; return false; }

    /** Interleaved samples scaled to the full int range (16-bit sample << 16). */
    void write(int[] interleaved, int count) throws IOException {
        int n = count / channels; int[] l = new int[n], r = new int[n];
        for (int i = 0; i < n; i++) { l[i] = interleaved[i * channels]; r[i] = channels == 2 ? interleaved[i * channels + 1] : l[i]; }
        int done = lame.lame_encode_buffer_int(gfp, l, r, n, mp3, 0, mp3.length);
        if (done < 0) throw new IOException("Erro do codificador MP3 (" + done + ").");
        out.write(mp3, 0, done); frames += n;
    }
    void finish() throws IOException { int done = lame.lame_encode_flush(gfp, mp3, 0, mp3.length); if (done > 0) out.write(mp3, 0, done); out.flush(); }
    @Override public void close() { try { lame.lame_close(gfp); } catch (Exception ignored) {} }

    // ---------------------------------------------------------------- ID3v2.3
    /** An ID3v2.3 tag (UTF-16 text) with title, album, artist and track "n/total". */
    static byte[] id3(Tags t) throws IOException {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        frame(frames, "TIT2", t.title); frame(frames, "TALB", t.album); frame(frames, "TPE1", t.artist);
        if (t.track > 0) frame(frames, "TRCK", t.total > 0 ? t.track + "/" + t.total : "" + t.track);
        frame(frames, "TSSE", "Seeky");
        byte[] body = frames.toByteArray(); int size = body.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'I', 'D', '3', 3, 0, 0}); out.write(new byte[]{(byte) ((size >> 21) & 0x7f), (byte) ((size >> 14) & 0x7f), (byte) ((size >> 7) & 0x7f), (byte) (size & 0x7f)});
        out.write(body); return out.toByteArray();
    }
    static void frame(ByteArrayOutputStream out, String id, String text) throws IOException {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 1000) text = text.substring(0, 1000);
        byte[] value = text.getBytes(StandardCharsets.UTF_16); // with BOM
        int size = value.length + 1 + 2;
        out.write(id.getBytes(StandardCharsets.US_ASCII)); out.write(new byte[]{(byte) (size >>> 24), (byte) (size >>> 16), (byte) (size >>> 8), (byte) size, 0, 0});
        out.write(1); out.write(value); out.write(0); out.write(0);
    }

    // ---------------------------------------------------------------- WAV → MP3
    static final class Wav { int format, channels, rate, bits; long dataAt, dataBytes; }
    /** The format and the PCM data of a RIFF/WAVE file (PCM or IEEE float; WAVE_FORMAT_EXTENSIBLE too). */
    static Wav wav(RandomAccessFile f) throws IOException {
        byte[] h = new byte[12]; f.seek(0); f.readFully(h);
        if (!new String(h, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") || !new String(h, 8, 4, StandardCharsets.US_ASCII).equals("WAVE")) throw new IOException("Não é um ficheiro WAV.");
        Wav w = new Wav(); long at = 12, end = f.length();
        while (at + 8 <= end) {
            f.seek(at); byte[] c = new byte[8]; f.readFully(c); String id = new String(c, 0, 4, StandardCharsets.US_ASCII); long size = le32(c, 4);
            if (id.equals("fmt ")) { byte[] b = new byte[(int) Math.min(size, 64)]; f.readFully(b); w.format = le16(b, 0); w.channels = le16(b, 2); w.rate = (int) le32(b, 4); w.bits = le16(b, 14); if (w.format == 0xfffe && b.length >= 26) w.format = le16(b, 24); }
            else if (id.equals("data")) { w.dataAt = at + 8; w.dataBytes = Math.min(size, end - w.dataAt); }
            at += 8 + size + (size & 1);
        }
        if (w.dataAt == 0 || w.channels < 1 || w.rate <= 0) throw new IOException("WAV sem áudio.");
        if (!(w.format == 1 && (w.bits == 8 || w.bits == 16 || w.bits == 24 || w.bits == 32)) && !(w.format == 3 && w.bits == 32)) throw new IOException("Formato WAV não suportado para MP3.");
        return w;
    }
    static int le16(byte[] b, int i) { return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8; }
    static long le32(byte[] b, int i) { return (b[i] & 0xffL) | (b[i + 1] & 0xffL) << 8 | (b[i + 2] & 0xffL) << 16 | (b[i + 3] & 0xffL) << 24; }

    /** Converts a WAV narration into an MP3 with tags; cancellable, with progress. Returns the seconds of audio. */
    static double fromWav(File wavFile, File mp3File, Tags tags, BooleanSupplier cancelled, Progress progress) throws IOException {
        File part = new File(mp3File.getPath() + ".part");
        try (RandomAccessFile f = new RandomAccessFile(wavFile, "r"); OutputStream out = new BufferedOutputStream(new FileOutputStream(part), 1 << 16)) {
            Wav w = wav(f); int ch = Math.min(2, w.channels), size = w.bits / 8, frame = size * w.channels;
            out.write(id3(tags));
            try (Mp3Writer enc = new Mp3Writer(out, w.rate, ch, bitrate(ch))) {
                byte[] buf = new byte[frame * 4096]; int[] pcm = new int[ch * 4096]; long left = w.dataBytes - w.dataBytes % frame, done = 0; f.seek(w.dataAt);
                while (left > 0) {
                    if (cancelled != null && cancelled.getAsBoolean()) throw new InterruptedIOException();
                    int want = (int) Math.min(buf.length, left); f.readFully(buf, 0, want); int frames = want / frame, k = 0;
                    for (int i = 0; i < frames; i++) for (int c = 0; c < ch; c++) pcm[k++] = sample(buf, i * frame + c * size, w);
                    enc.write(pcm, k); left -= want; done += want;
                    if (progress != null) progress.at(done, w.dataBytes);
                }
                enc.finish();
                double seconds = enc.frames / (double) w.rate;
                out.close();
                if (mp3File.exists() && !mp3File.delete()) throw new IOException("Não foi possível substituir " + mp3File.getName());
                if (!part.renameTo(mp3File)) throw new IOException("Não foi possível guardar o MP3.");
                return seconds;
            }
        } catch (IOException | RuntimeException e) { part.delete(); throw e; }
    }
    static int sample(byte[] b, int i, Wav w) {
        if (w.format == 3) { int bits = (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] & 0xff) << 24; float v = Float.intBitsToFloat(bits); v = Math.max(-1f, Math.min(1f, v)); return (int) (v * 2147483647.0); }
        switch (w.bits) {
            case 8: return ((b[i] & 0xff) - 128) << 24;
            case 16: return (b[i] & 0xff) << 16 | b[i + 1] << 24;
            case 24: return (b[i] & 0xff) << 8 | (b[i + 1] & 0xff) << 16 | b[i + 2] << 24;
            default: return (b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | b[i + 3] << 24;
        }
    }
    /** The narration's file name with ".mp3" instead of ".wav". */
    static String name(String wavName) { return wavName.matches("(?i).*\\.wav$") ? wavName.substring(0, wavName.length() - 4) + ".mp3" : wavName + ".mp3"; }
}
