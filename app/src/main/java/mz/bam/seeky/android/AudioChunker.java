package mz.bam.seeky.android;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts an audio (or video) file into short mono 16 kHz pieces for transcription: the system decoder
 * reads any format the phone plays, and each piece is written as AAC (ADTS, 32 kb/s, about 240 KB a
 * minute) or, if the phone has no AAC encoder, as WAV with shorter pieces.
 */
final class AudioChunker {
    interface Cancel { boolean cancelled(); }
    static final class Piece { final File file; final String mime; final long startMs, durationMs; Piece(File file, String mime, long startMs, long durationMs) { this.file = file; this.mime = mime; this.startMs = startMs; this.durationMs = durationMs; } }
    interface Splitter { List<Piece> split(Context c, Uri uri, File dir, long chunkMs, Cancel cancel) throws IOException; }
    static Splitter splitter = AudioChunker::split;

    static final int BITRATE = 32000;
    static final long WAV_CHUNK_MS = 3 * 60 * 1000L;

    static List<Piece> split(Context c, Uri uri, File dir, long chunkMs, Cancel cancel) throws IOException {
        try { return decode(c, uri, dir, chunkMs, cancel, true); }
        catch (InterruptedIOException e) { throw e; }
        catch (IOException | RuntimeException e) {
            AppDiagnostics.record(c, "transcribe-aac", e);
            clear(dir);
            return decode(c, uri, dir, Math.min(chunkMs, WAV_CHUNK_MS), cancel, false);
        }
    }
    static void clear(File dir) { File[] old = dir.listFiles((d, n) -> n.startsWith("chunk-")); if (old != null) for (File f : old) f.delete(); }

    static List<Piece> decode(Context c, Uri uri, File dir, long chunkMs, Cancel cancel, boolean aac) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        Sink sink = null;
        try {
            extractor.setDataSource(c, uri, null);
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) { String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME); if (mime != null && mime.startsWith("audio/")) { track = i; break; } }
            if (track < 0) throw new IOException("Este ficheiro não tem som para transcrever.");
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
            decoder.start();
            int rate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100, channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            boolean floats = false;
            LyricsText.Resampler resampler = new LyricsText.Resampler(rate, channels);
            sink = aac ? new AacSink(dir, chunkMs) : new WavSink(dir, chunkMs);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            short[] pcm = new short[0], out = new short[0];
            for (int idle = 0; idle < 2000; ) {
                if (cancel.cancelled()) throw new InterruptedIOException();
                if (!inputDone) {
                    int in = decoder.dequeueInputBuffer(10000);
                    if (in >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(in);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) { decoder.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else { decoder.queueInputBuffer(in, 0, size, Math.max(0, extractor.getSampleTime()), 0); extractor.advance(); }
                    }
                }
                int index = decoder.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = decoder.getOutputFormat();
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    floats = f.containsKey("pcm-encoding") && f.getInteger("pcm-encoding") == 4;
                    resampler = new LyricsText.Resampler(rate, channels);
                    continue;
                }
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) { if (inputDone) idle++; continue; }
                if (index < 0) continue;
                idle = 0;
                ByteBuffer buffer = decoder.getOutputBuffer(index);
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset); buffer.limit(info.offset + info.size);
                    ByteBuffer data = buffer.slice().order(ByteOrder.nativeOrder());
                    int samples = floats ? info.size / 4 : info.size / 2;
                    if (pcm.length < samples) pcm = new short[samples];
                    if (floats) { for (int i = 0; i < samples; i++) pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(data.getFloat(i * 4) * 32767f))); }
                    else data.asShortBuffer().get(pcm, 0, samples);
                    int frames = samples / Math.max(1, channels);
                    int need = resampler.capacity(frames);
                    if (out.length < need) out = new short[need];
                    int produced = resampler.process(pcm, frames, out);
                    sink.write(out, produced, cancel);
                }
                decoder.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
            return sink.finish(cancel);
        } finally {
            if (decoder != null) try { decoder.stop(); } catch (RuntimeException ignored) {} finally { try { decoder.release(); } catch (RuntimeException ignored) {} }
            if (sink != null) sink.abort();
            extractor.release();
        }
    }

    abstract static class Sink {
        final File dir; final long chunkSamples; final List<Piece> pieces = new ArrayList<>(); long written, inChunk; int index;
        Sink(File dir, long chunkMs) { this.dir = dir; chunkSamples = Math.max(LyricsText.RATE, chunkMs * LyricsText.RATE / 1000); }
        void write(short[] s, int count, Cancel cancel) throws IOException {
            int off = 0;
            while (count > 0) {
                if (inChunk == 0) open();
                int take = (int) Math.min(count, chunkSamples - inChunk);
                feed(s, off, take, cancel);
                off += take; count -= take; inChunk += take; written += take;
                if (inChunk >= chunkSamples) close(cancel);
            }
        }
        List<Piece> finish(Cancel cancel) throws IOException { if (inChunk > 0) close(cancel); if (pieces.isEmpty()) throw new IOException("Não foi possível ler o som deste ficheiro."); return pieces; }
        void add(File f, String mime) { long start = (written - inChunk) * 1000 / LyricsText.RATE; pieces.add(new Piece(f, mime, start, inChunk * 1000 / LyricsText.RATE)); inChunk = 0; index++; }
        abstract void open() throws IOException;
        abstract void feed(short[] s, int off, int count, Cancel cancel) throws IOException;
        abstract void close(Cancel cancel) throws IOException;
        abstract void abort();
    }

    static final class WavSink extends Sink {
        RandomAccessFile file; File current;
        WavSink(File dir, long chunkMs) { super(dir, chunkMs); }
        void open() throws IOException { current = new File(dir, String.format(java.util.Locale.ROOT, "chunk-%03d.wav", index)); file = new RandomAccessFile(current, "rw"); file.setLength(0); file.write(LyricsText.wav(LyricsText.RATE, 1, 0)); }
        void feed(short[] s, int off, int count, Cancel cancel) throws IOException { byte[] b = new byte[count * 2]; for (int i = 0; i < count; i++) { short v = s[off + i]; b[i * 2] = (byte) v; b[i * 2 + 1] = (byte) (v >> 8); } file.write(b); }
        void close(Cancel cancel) throws IOException { long data = file.length() - 44; file.seek(0); file.write(LyricsText.wav(LyricsText.RATE, 1, data)); file.close(); file = null; add(current, "audio/wav"); }
        void abort() { if (file != null) try { file.close(); } catch (IOException ignored) {} }
    }

    /** AAC-LC through the system encoder; each frame gets an ADTS header so the piece is a plain .aac stream. */
    static final class AacSink extends Sink {
        MediaCodec encoder; OutputStream out; File current; long pts; final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        AacSink(File dir, long chunkMs) throws IOException { super(dir, chunkMs); MediaCodec probe = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC); probe.release(); }
        void open() throws IOException {
            MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, LyricsText.RATE, 1);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            f.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            current = new File(dir, String.format(java.util.Locale.ROOT, "chunk-%03d.aac", index));
            out = new BufferedOutputStream(new FileOutputStream(current));
            pts = 0;
        }
        void feed(short[] s, int off, int count, Cancel cancel) throws IOException {
            int guard = 0;
            while (count > 0) {
                if (cancel.cancelled()) throw new InterruptedIOException();
                int in = encoder.dequeueInputBuffer(10000);
                if (in >= 0) {
                    ByteBuffer b = encoder.getInputBuffer(in);
                    b.clear();
                    int take = Math.min(count, b.capacity() / 2);
                    b.order(ByteOrder.nativeOrder()).asShortBuffer().put(s, off, take);
                    encoder.queueInputBuffer(in, 0, take * 2, pts, 0);
                    pts += take * 1000000L / LyricsText.RATE; off += take; count -= take; guard = 0;
                } else if (++guard > 500) throw new IOException("O codificador de áudio não respondeu.");
                drain(false);
            }
        }
        void drain(boolean end) throws IOException {
            for (int waits = 0; ; ) {
                int index = encoder.dequeueOutputBuffer(info, end ? 10000 : 0);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end || ++waits > 300) return; continue; }
                if (index < 0) continue;
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                    ByteBuffer b = encoder.getOutputBuffer(index);
                    byte[] frame = new byte[info.size];
                    b.position(info.offset); b.get(frame);
                    out.write(LyricsText.adts(frame.length, LyricsText.RATE, 1)); out.write(frame);
                }
                encoder.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }
        void close(Cancel cancel) throws IOException {
            for (int guard = 0; ; guard++) { int in = encoder.dequeueInputBuffer(10000); if (in >= 0) { encoder.queueInputBuffer(in, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); break; } drain(false); if (guard > 500) throw new IOException("O codificador de áudio não respondeu."); }
            drain(true);
            release();
            out.close(); out = null;
            if (current.length() < 64) throw new IOException("O codificador de áudio não produziu som.");
            add(current, "audio/aac");
        }
        void release() { if (encoder != null) { try { encoder.stop(); } catch (RuntimeException ignored) {} try { encoder.release(); } catch (RuntimeException ignored) {} encoder = null; } }
        void abort() { release(); if (out != null) try { out.close(); } catch (IOException ignored) {} }
    }
    private AudioChunker() {}
}
