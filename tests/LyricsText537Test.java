package mz.bam.seeky.android;

import java.util.*;

/** 5.37: cleaned searches, safe choice of online lyrics, talks vs songs, and AI transcription text. */
public final class LyricsText537Test {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }

    public static void main(String[] args) {
        clean(); queries(); similarity(); pick(); speech(); parse(); merge(); prompt(); audio();
        System.out.println("LyricsText537Test OK · " + checks + " verificações");
    }
    static void clean() {
        check(LyricsText.clean("01 - Hillsong United - Oceans (Where Feet May Fail) (Official Video).mp3").equals("Hillsong United - Oceans (Where Feet May Fail)"), "faixa, vídeo oficial e extensão: " + LyricsText.clean("01 - Hillsong United - Oceans (Where Feet May Fail) (Official Video).mp3"));
        check(LyricsText.clean("Aline_Barros_-_Ressuscita-me_[Lyric Video]_www.musicas.com.mp3").equals("Aline Barros - Ressuscita-me"), "sublinhados e site: " + LyricsText.clean("Aline_Barros_-_Ressuscita-me_[Lyric Video]_www.musicas.com.mp3"));
        check(LyricsText.clean("<unknown>").isEmpty() && LyricsText.clean("Artista desconhecido").isEmpty(), "artista desconhecido");
        check(LyricsText.core("Deus de Promessas (feat. Davi Sacer)").equals("Deus de Promessas") && LyricsText.core("Song ft. Someone").equals("Song"), "sem feat.");
        check(LyricsText.core("Deus com nós").equals("Deus com nós"), "'com' fora de parênteses fica");
    }
    static void queries() {
        List<LyricsText.Query> q = LyricsText.queries("Oceans (Where Feet May Fail)", "Hillsong UNITED", "01 Oceans.mp3");
        check(q.get(0).title.equals("Oceans (Where Feet May Fail)") && q.get(0).artist.equals("Hillsong UNITED"), "etiquetas primeiro: " + q);
        q = LyricsText.queries("", "", "Fernandinho - Uma Nova História (Ao Vivo) (Official Video).mp3");
        check(q.get(0).title.equals("Uma Nova História (Ao Vivo)") && q.get(0).artist.equals("Fernandinho"), "Artista - Título do nome do ficheiro: " + q);
        check(q.get(1).title.equals("Fernandinho") && q.get(1).artist.equals("Uma Nova História (Ao Vivo)"), "ordem inversa também: " + q);
        q = LyricsText.queries("AUD-20240101-WA0001", "<unknown>", "AUD-20240101-WA0001.mp3");
        check(q.size() == 1 && q.get(0).artist.isEmpty(), "sem artista: " + q);
        check(LyricsText.queries("", "", "").isEmpty(), "nada para procurar");
    }
    static void similarity() {
        check(LyricsText.similarity("Ressuscita-me", "Ressuscita Me") == 1, "pontuação ignorada");
        check(LyricsText.similarity("Coração", "Coracao") == 1, "acentos ignorados");
        check(LyricsText.similarity("Oceans", "Oceans (Where Feet May Fail)") > .6, "título contido");
        check(LyricsText.similarity("Oceans", "Ocean Eyes") < .8, "títulos diferentes");
        check(LyricsText.similarity("", "x") == 0, "vazio");
    }
    static LyricsText.Candidate result(String track, String artist, double duration, boolean synced) { return new LyricsText.Candidate(track, artist, duration, synced, true); }
    static void pick() {
        LyricsText.Query q = new LyricsText.Query("Ressuscita-me", "Aline Barros");
        List<LyricsText.Candidate> list = Arrays.asList(result("Ressuscita-me", "Outro Cantor", 240, true), result("Ressuscita-me", "Aline Barros", 318, false), result("Ressuscita-me", "Aline Barros", 301, true));
        check(LyricsText.pick(list, q, 300_000) == 2, "artista e duração certos, sincronizada: " + LyricsText.pick(list, q, 300_000));
        check(LyricsText.pick(Collections.singletonList(result("Ressuscita-me", "Outro Cantor", 240, true)), q, 300_000) < 0, "outro cantor e outra duração recusados");
        LyricsText.Query bare = new LyricsText.Query("Hino da Vitória", "");
        check(LyricsText.pick(Collections.singletonList(result("Hino da Vitória", "X", 180, true)), bare, 0) < 0, "sem artista nem duração: não arrisca");
        check(LyricsText.pick(Collections.singletonList(result("Hino da Vitória", "X", 181, true)), bare, 180_000) == 0, "sem artista mas com a mesma duração: aceita");
        check(LyricsText.pick(Collections.singletonList(result("Hino da Vitória", "Coral", 600, true)), new LyricsText.Query("Hino da Vitória", "Coral"), 180_000) < 0, "duração muito diferente recusada");
        check(LyricsText.pick(Collections.singletonList(new LyricsText.Candidate("X", "Y", 100, false, false)), new LyricsText.Query("X", "Y"), 100_000) < 0, "resultado sem letra ignorado");
        check(LyricsText.pick(Collections.singletonList(result("Oceans", "Hillsong UNITED", 0, true)), new LyricsText.Query("Oceans", "Hillsong United"), 0) == 0, "título e artista iguais, sem duração: aceita");
    }
    static void speech() {
        check(LyricsText.speech("Pregação - Pr. João - A fé", "", 30 * 60_000), "pregação");
        check(LyricsText.speech("Culto de domingo", "", 0) && LyricsText.speech("", "PTT-20240101-WA0003.opus", 40_000) && LyricsText.speech("Estudo Bíblico 3", "", 0), "culto, nota de voz, estudo bíblico");
        check(LyricsText.speech("", "gravação.mp3", 20 * 60_000), "áudio longo");
        check(!LyricsText.speech("Oceans", "Oceans.mp3", 4 * 60_000) && !LyricsText.speech("Mensageiro", "", 0), "música normal (palavra parecida não conta)");
    }
    static void parse() {
        List<LyricsText.Timed> lines = LyricsText.parse("```\n[00:01.50] Primeira frase\n[00:05] **Segunda** frase\n[1:02:03] fora do excerto\n- [00:04.00] \"Antes da anterior\"\n\n[00:09.7] Última\n```", 360_000);
        check(lines.size() == 5, "linhas: " + lines.size());
        check(lines.get(0).time == 1500 && lines.get(0).text.equals("Primeira frase"), "tempo com centésimos");
        check(lines.get(1).time == 5000 && lines.get(1).text.equals("Segunda frase"), "Markdown removido");
        check(lines.get(2).time == 361_500, "tempo acima do excerto limitado: " + lines.get(2).time);
        check(lines.get(3).time > lines.get(2).time && lines.get(3).text.equals("Antes da anterior"), "ordem mantida e aspas removidas");
        check(lines.get(4).text.equals("Última"), "décimas");
        List<LyricsText.Timed> loose = LyricsText.parse("Sem tempos aqui\nSegunda linha\nTerceira", 90_000);
        check(loose.size() == 3 && loose.get(0).time == 0 && loose.get(1).time == 30_000 && loose.get(2).time == 60_000, "texto sem tempos espalhado");
        check(LyricsText.parse("", 1000).isEmpty() && LyricsText.parse(null, 1000).isEmpty(), "vazio");
    }
    static void merge() {
        List<List<LyricsText.Timed>> chunks = new ArrayList<>();
        chunks.add(LyricsText.parse("[00:00.00] ♪\n[05:58.00] Fim da primeira parte", 360_000));
        chunks.add(LyricsText.parse("[00:00.00] ♪\n[00:01.00] ♪\n[00:03.00] Começo da segunda", 360_000));
        List<LyricsText.Timed> all = LyricsText.merge(chunks, Arrays.asList(0L, 360_000L));
        check(all.size() == 4, "♪ repetidos juntos: " + all.size());
        check(all.get(1).time == 358_000 && all.get(3).time == 363_000, "tempos na linha do áudio");
        String lrc = LyricsText.lrc(all);
        check(lrc.contains("[05:58.00]Fim da primeira parte") && lrc.contains("[06:03.00]Começo da segunda"), "LRC: " + lrc);
        List<LyricsText.Timed> long_ = LyricsText.merge(Collections.singletonList(LyricsText.parse("[00:10.00] Já passou uma hora", 360_000)), Collections.singletonList(3_600_000L));
        check(LyricsText.lrc(long_).startsWith("[60:10.00]"), "minutos acima de 59: " + LyricsText.lrc(long_));
    }
    static void prompt() {
        String prompt = LyricsText.prompt("Culto", 360_000, 720_000, false);
        check(prompt.contains("6:00 a 12:00") && prompt.contains("[mm:ss.cc]") && prompt.contains("não traduzas"), "instrução em português");
        check(LyricsText.prompt("", 0, 60_000, true).contains("0:00 to 1:00"), "instrução em inglês");
        check(LyricsText.clock(3_725_000).equals("1:02:05"), "relógio com horas");
    }
    static void audio() {
        byte[] h = LyricsText.adts(100, 16000, 1);
        check(h.length == 7 && (h[0] & 255) == 0xFF && (h[1] & 255) == 0xF1, "sincronismo ADTS");
        int profile = (h[2] & 255) >> 6, index = ((h[2] & 255) >> 2) & 15, channels = ((h[2] & 1) << 2) | ((h[3] & 255) >> 6), length = ((h[3] & 3) << 11) | ((h[4] & 255) << 3) | ((h[5] & 255) >> 5);
        check(profile == 1 && index == 8 && channels == 1 && length == 107, "ADTS: perfil LC, 16 kHz, mono, tamanho com cabeçalho");
        byte[] w = LyricsText.wav(16000, 1, 32000);
        check(w.length == 44 && new String(w, 0, 4).equals("RIFF") && new String(w, 8, 4).equals("WAVE") && (w[24] & 255 | (w[25] & 255) << 8) == 16000 && (w[40] & 255 | (w[41] & 255) << 8) == 32000, "cabeçalho WAV");
        // 44.1 kHz stereo, 1 second of a constant tone in left and silence in right → 16000 mono samples at half level.
        LyricsText.Resampler r = new LyricsText.Resampler(44100, 2);
        int total = 0; short[] out = new short[r.capacity(4410)]; boolean level = true;
        for (int block = 0; block < 10; block++) {
            short[] in = new short[4410 * 2];
            for (int f = 0; f < 4410; f++) { in[f * 2] = 10000; in[f * 2 + 1] = 0; }
            int n = r.process(in, 4410, out); total += n;
            for (int i = 0; i < n; i++) if (out[i] != 5000) level = false;
        }
        check(Math.abs(total - 16000) <= 2, "44,1 kHz para 16 kHz: " + total);
        check(level, "mono pela média dos canais");
        LyricsText.Resampler same = new LyricsText.Resampler(16000, 1);
        short[] ramp = new short[1000]; for (int i = 0; i < 1000; i++) ramp[i] = (short) i;
        short[] o = new short[same.capacity(1000)]; int n1 = same.process(ramp, 1000, o);
        check(n1 >= 998 && o[10] == 10 && o[500] == 500, "16 kHz fica igual");
        LyricsText.Resampler up = new LyricsText.Resampler(8000, 1);
        short[] o2 = new short[up.capacity(800)]; int n2 = up.process(new short[800], 800, o2);
        check(Math.abs(n2 - 1600) <= 2, "8 kHz para 16 kHz: " + n2);
    }
}
