package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helpers for lyrics: clean tags and file names for searching, choose the right online result
 * (title, artist and duration must agree), recognise talks and sermons, and read the timed text
 * returned by the AI transcription. Also the small audio pieces the transcription needs (mono 16 kHz
 * resampling, ADTS and WAV headers).
 */
final class LyricsText {
    static final class Query {
        final String title, artist;
        Query(String title, String artist) { this.title = title == null ? "" : title; this.artist = artist == null ? "" : artist; }
        @Override public boolean equals(Object o) { return o instanceof Query && norm(((Query) o).title).equals(norm(title)) && norm(((Query) o).artist).equals(norm(artist)); }
        @Override public int hashCode() { return (norm(title) + "|" + norm(artist)).hashCode(); }
        @Override public String toString() { return artist.isEmpty() ? title : artist + " — " + title; }
    }
    static final class Timed {
        final long time; final String text;
        Timed(long time, String text) { this.time = time; this.text = text; }
    }

    static final Pattern BRACKETED = Pattern.compile("(?i)[\\(\\[\\{][^\\)\\]\\}]*(official|oficial|lyric|letra|legendad|video|vídeo|clipe|clip\\b|audio|áudio|visuali[sz]er|\\bhd\\b|\\bhq\\b|4k|1080p|720p|remaster|mp3|download|baixar|www\\.|\\.com)[^\\)\\]\\}]*[\\)\\]\\}]");
    static final Pattern UNKNOWN = Pattern.compile("(?i)<?unknown>?|desconhecid[oa]|artista desconhecido|various artists|vários artistas|varios artistas|unknown artist");

    /** Title or file name without track numbers, "(Official Video)", site tags, extensions or underscores. */
    static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('_', ' ').replaceAll("(?i)\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav|wma|amr|3gp|mp4|m4b|mka)$", "");
        t = BRACKETED.matcher(t).replaceAll(" ");
        t = t.replaceAll("(?i)(www\\.\\S+|\\S+\\.(com|net|org|info|co\\.mz)\\b)", " ").replaceAll("@\\w+", " ");
        t = t.replaceAll("(?i)\\b(official (music )?video|official audio|video oficial|vídeo oficial|clipe oficial|audio oficial|áudio oficial|lyric video|lyrics|com letra|letra oficial|320 ?kbps|128 ?kbps)\\b", " ");
        t = t.replaceAll("^\\s*(?:(?:track|faixa)\\s*)?\\d{1,3}\\s*[-._)]\\s*", "");
        t = t.replaceAll("\\s+", " ").trim().replaceAll("^[-–—.|\\s]+|[-–—.|\\s]+$", "");
        return UNKNOWN.matcher(t).matches() ? "" : t;
    }
    /** Without "feat. X": catalogues file songs under the main artist and plain title. */
    static String core(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring|part\\.?|com|with)\\s+[^\\)\\]]*[\\)\\]]", "")
                .replaceAll("(?i)\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", "").replaceAll("\\s+", " ").trim();
    }
    /** Search attempts, most likely first: tags; "Artist - Title" (and the reverse) from the title or file name; title alone. */
    static List<Query> queries(String title, String artist, String file) {
        LinkedHashSet<Query> out = new LinkedHashSet<>();
        String t = clean(title), a = clean(artist), f = clean(file);
        if (!t.isEmpty() && !a.isEmpty() && !t.matches(".*\\s[-–—|]\\s.*")) out.add(new Query(core(t), core(a)));
        for (String s : new String[]{t, f}) {
            String[] parts = s.split("\\s+[-–—|]\\s+");
            if (parts.length < 2) continue;
            String first = parts[0].trim(), rest = String.join(" - ", Arrays.copyOfRange(parts, 1, parts.length)).trim();
            out.add(new Query(core(rest), core(first)));
            out.add(new Query(core(first), core(rest)));
        }
        if (!t.isEmpty()) out.add(new Query(core(t), core(a)));
        if (!f.isEmpty()) out.add(new Query(core(f), ""));
        List<Query> list = new ArrayList<>();
        for (Query q : out) if (norm(q.title).length() >= 2) list.add(q);
        return list.size() > 5 ? new ArrayList<>(list.subList(0, 5)) : list;
    }

    static String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    /** 0…1: equal names, one inside the other, or shared letter pairs (typos, punctuation). */
    static double similarity(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        if (x.equals(y)) return 1;
        if ((" " + x + " ").contains(" " + y + " ") || (" " + y + " ").contains(" " + x + " ")) return .55 + .45 * Math.min(x.length(), y.length()) / (double) Math.max(x.length(), y.length());
        String p = x.replace(" ", ""), q = y.replace(" ", "");
        if (p.length() < 2 || q.length() < 2) return 0;
        Map<String, Integer> pairs = new HashMap<>();
        for (int i = 0; i + 1 < p.length(); i++) pairs.merge(p.substring(i, i + 2), 1, Integer::sum);
        int common = 0;
        for (int i = 0; i + 1 < q.length(); i++) { String k = q.substring(i, i + 2); Integer n = pairs.get(k); if (n != null && n > 0) { common++; pairs.put(k, n - 1); } }
        return 2.0 * common / (p.length() - 1 + q.length() - 1);
    }
    /** How well an online result fits; below 0 means "do not use". */
    static double score(Query q, String track, String artist, double durationSec, boolean synced, long durationMs) {
        double title = similarity(q.title, track);
        if (title < .6) return -1;
        double who = q.artist.isEmpty() ? -1 : similarity(q.artist, artist);
        double diff = durationMs > 0 && durationSec > 0 ? Math.abs(durationSec * 1000 - durationMs) / 1000.0 : -1;
        if (who >= 0 && who < .45 && (diff < 0 || diff > 3)) return -1;
        if (who < 0 && (diff < 0 || diff > 8)) return -1;
        if (diff > 25) return -1;
        double s = .6 * title + (who < 0 ? .1 : .25 * who) + (diff < 0 ? .05 : diff <= 3 ? .2 : diff <= 8 ? .1 : 0) + (synced ? .05 : 0);
        return s >= .72 ? s : -1;
    }
    /** One online result: name, artist, duration in seconds, timed lines, any lyrics at all. */
    static final class Candidate {
        final String track, artist; final double duration; final boolean synced, usable;
        Candidate(String track, String artist, double duration, boolean synced, boolean usable) { this.track = track == null ? "" : track; this.artist = artist == null ? "" : artist; this.duration = duration; this.synced = synced; this.usable = usable; }
    }
    /** Index of the best search result for this attempt, or −1. */
    static int pick(List<Candidate> results, Query q, long durationMs) {
        int best = -1; double top = -1;
        for (int i = 0; i < results.size(); i++) {
            Candidate c = results.get(i);
            if (!c.usable) continue;
            double s = score(q, c.track, c.artist, c.duration, c.synced, durationMs);
            if (s > top) { top = s; best = i; }
        }
        return best;
    }

    static final Pattern SPEECH = Pattern.compile("(?i)(^|[^\\p{L}])(prega[cç][aã]o|prega[cç][oõ]es|serm[aã]o|serm[oõ]es|sermon|culto|estudo b[ií]blico|palestra|mensagem|podcast|aula|ministra[cç][aã]o|ensino|devocional|testemunho|confer[eê]ncia|semin[aá]rio|entrevista|audiobook|audiolivro|[aá]udio ?livro|ptt|nota de voz|voice note|reuni[aã]o)([^\\p{L}]|$)");
    /** Talks, sermons, classes and voice notes: transcribe instead of looking for song lyrics. */
    static boolean speech(String title, String file, long durationMs) {
        return durationMs > 15 * 60 * 1000L || SPEECH.matcher((title == null ? "" : title) + " " + (file == null ? "" : file)).find();
    }

    // ---- AI transcription ----

    static String clock(long ms) { long s = Math.max(0, ms / 1000); return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60); }
    static String prompt(String title, long startMs, long endMs, boolean english) {
        String name = title == null ? "" : title.trim();
        if (english) return "Transcribe this audio" + (name.isEmpty() ? "" : " (\"" + name + "\"") + ", excerpt from " + clock(startMs) + " to " + clock(endMs) + (name.isEmpty() ? "" : ")") + ". "
                + "Write exactly what is said or sung, in the original language (do not translate, summarise or comment). "
                + "Required format: one line per short sentence or verse (about 12 words at most), starting with the time that sentence begins, counted from the start of THIS excerpt (00:00): [mm:ss.cc] text. "
                + "For music, write the sung lyrics; instrumental or silent parts get no lines. If there is no voice at all, reply only: [00:00.00] ♪. No Markdown, no quotes.";
        return "Transcreve este áudio" + (name.isEmpty() ? "" : " («" + name + "»") + ", excerto de " + clock(startMs) + " a " + clock(endMs) + (name.isEmpty() ? "" : ")") + ". "
                + "Escreve exactamente o que é dito ou cantado, na língua original (não traduzas, não resumas, não acrescentes comentários). "
                + "Formato obrigatório: uma linha por frase curta ou verso (no máximo cerca de 12 palavras), a começar pelo tempo em que essa frase começa, contado desde o início DESTE excerto (00:00): [mm:ss.cc] texto. "
                + "Para música, escreve a letra cantada; partes só instrumentais ou em silêncio ficam sem linhas. Se não houver voz nenhuma, responde só: [00:00.00] ♪. Não uses Markdown nem aspas.";
    }
    static final class Reply { final String text, reason; Reply(String text, String reason) { this.text = text; this.reason = reason; } }
    static final Pattern STAMP = Pattern.compile("^\\s*[\\(\\[]?\\s*(\\d{1,3})(?::(\\d{1,2}))?:(\\d{2})(?:[.,](\\d{1,3}))?\\s*[\\)\\]]?\\s*[-–—:|]?\\s*(.*)$");
    /** Timed lines of one excerpt, kept in order and inside the excerpt; untimed text is spread evenly. */
    static List<Timed> parse(String reply, long chunkMs) {
        List<Timed> out = new ArrayList<>();
        List<String> loose = new ArrayList<>();
        if (reply == null) return out;
        for (String raw : reply.replace("\r", "").split("\n")) {
            String line = raw.trim().replaceAll("^[-*•>]+\\s*(?=[\\[(]?\\s*\\d)", "");
            if (line.isEmpty() || line.startsWith("```")) continue;
            Matcher m = STAMP.matcher(line);
            if (m.matches()) {
                long a = Long.parseLong(m.group(1)), mid = m.group(2) == null ? -1 : Long.parseLong(m.group(2)), sec = Long.parseLong(m.group(3));
                long frac = 0;
                if (m.group(4) != null) { String f = m.group(4); frac = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1); }
                long time = mid >= 0 ? a * 3600000 + mid * 60000 + sec * 1000 + frac : a * 60000 + sec * 1000 + frac;
                String text = tidy(m.group(5));
                if (!text.isEmpty()) out.add(new Timed(time, text));
            } else { String text = tidy(line); if (!text.isEmpty()) loose.add(text); }
        }
        if (out.isEmpty() && !loose.isEmpty()) for (int i = 0; i < loose.size(); i++) out.add(new Timed(chunkMs > 0 ? chunkMs * i / loose.size() : i * 4000L, loose.get(i)));
        List<Timed> ordered = new ArrayList<>();
        long last = -1, limit = chunkMs > 0 ? chunkMs + 1500 : Long.MAX_VALUE;
        for (Timed t : out) {
            long time = Math.max(0, Math.min(t.time, limit));
            if (time <= last) time = last + 1;
            ordered.add(new Timed(time, t.text));
            last = time;
        }
        return ordered;
    }
    static String tidy(String s) {
        String t = s == null ? "" : s.trim();
        t = t.replaceAll("^[-*•>]+\\s*", "").replaceAll("\\*\\*|__", "").trim();
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("«") && t.endsWith("»") || t.startsWith("“") && t.endsWith("”"))) t = t.substring(1, t.length() - 1).trim();
        return t.replaceAll("\\s+", " ");
    }
    /** Lines of every excerpt on the track's timeline. */
    static List<Timed> merge(List<List<Timed>> chunks, List<Long> starts) {
        List<Timed> all = new ArrayList<>();
        long last = -1;
        for (int i = 0; i < chunks.size(); i++) for (Timed t : chunks.get(i)) {
            long time = Math.max(starts.get(i) + t.time, last + 1);
            if (t.text.equals("♪") && !all.isEmpty() && all.get(all.size() - 1).text.equals("♪")) continue;
            all.add(new Timed(time, t.text)); last = time;
        }
        return all;
    }
    static String lrc(List<Timed> lines) {
        StringBuilder b = new StringBuilder();
        for (Timed l : lines) b.append(String.format(Locale.ROOT, "[%02d:%02d.%02d]", l.time / 60000, (l.time / 1000) % 60, (l.time % 1000) / 10)).append(l.text).append('\n');
        return b.toString();
    }

    // ---- audio pieces ----

    static final int RATE = 16000;
    static int frequencyIndex(int rate) { int[] r = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350}; for (int i = 0; i < r.length; i++) if (r[i] == rate) return i; return 8; }
    /** 7-byte ADTS header (AAC-LC, no CRC) for a frame of {@code payload} bytes. */
    static byte[] adts(int payload, int sampleRate, int channels) {
        int length = payload + 7, profile = 1, index = frequencyIndex(sampleRate);
        return new byte[]{(byte) 0xFF, (byte) 0xF1, (byte) ((profile << 6) | (index << 2) | (channels >> 2)), (byte) (((channels & 3) << 6) | (length >> 11)), (byte) ((length >> 3) & 0xFF), (byte) (((length & 7) << 5) | 0x1F), (byte) 0xFC};
    }
    /** 44-byte PCM WAV header. */
    static byte[] wav(int sampleRate, int channels, long dataBytes) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt((int) (36 + dataBytes)).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt((int) dataBytes);
        return b.array();
    }
    /** Interleaved PCM of any rate and channel count to mono 16 kHz, by averaging and linear interpolation; keeps state between blocks. */
    static final class Resampler {
        final int inRate, channels; final double step; double t; double prev; boolean started;
        Resampler(int inRate, int channels) { this.inRate = Math.max(1, inRate); this.channels = Math.max(1, channels); step = this.inRate / (double) RATE; }
        int capacity(int frames) { return (int) Math.ceil(frames / step) + 2; }
        int process(short[] in, int frames, short[] out) {
            if (frames <= 0) return 0;
            double[] mono = new double[frames];
            for (int f = 0; f < frames; f++) { int sum = 0; for (int c = 0; c < channels; c++) sum += in[f * channels + c]; mono[f] = sum / (double) channels; }
            if (!started) { started = true; prev = mono[0]; t = 0; }
            int n = 0;
            while (true) {
                int i = (int) Math.floor(t);
                if (i + 1 >= frames) break;
                double frac = t - i, a = i < 0 ? prev : mono[i], b = mono[i + 1];
                double v = a + (b - a) * frac;
                out[n++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v)));
                t += step;
            }
            prev = mono[frames - 1];
            t -= frames;
            return n;
        }
    }
    private LyricsText() {}
}
