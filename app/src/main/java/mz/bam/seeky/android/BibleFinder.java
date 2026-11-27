package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.*;

/**
 * 5.60 · Encontrar a passagem a partir de um trecho: "da sua plenitude nos tem dado graça sobre graça" → João 1:16.
 * An index of the words of every verse of a Bible (accents, capitals, plurals and small words do not matter), so a
 * remembered phrase finds its verse even when written in another translation or with words in another order.
 * Shared by Android and Windows; Seeky IA covers what the words alone cannot (see {@link #aiPrompt}).
 */
final class BibleFinder {
    interface Sink { void verse(String book, int chapter, int verse, String text); }
    interface Source { void each(Sink sink) throws Exception; }
    static final class Hit {
        final String book, text; final int chapter, verse; final double score;
        Hit(String book, int chapter, int verse, String text, double score) { this.book = book; this.chapter = chapter; this.verse = verse; this.text = text; this.score = score; }
        String title() { return book + " " + chapter + ":" + verse; }
        BibleReferences.Reference reference() { return BibleReferences.lookup(title()); }
    }

    static final Set<String> SMALL = new HashSet<>(Arrays.asList((
        "a o e as os de do da dos das em no na nos nas um uma uns umas que se por para com sem ao aos à às é era foi ser são sou "
        + "ele ela eles elas eu tu nós vós me te lhe lhes vos seu sua seus suas meu minha meus minhas teu tua teus tuas nosso nossa nossos nossas "
        + "dele dela deles delas este esta estes estas esse essa esses essas aquele aquela isto isso aquilo mas ou nem como quando onde "
        + "não sim já também só mais muito muita pelo pela pelos pelas num numa sobre até entre então porque pois quem qual quais tem têm "
        + "há havia todo toda todos todas the and of to in that he his him they them it is was for with not be are who which shall unto").split(" ")));

    final String[] books, texts; final int[] chapters, verses; final int[][] words; final Map<String, int[]> postings; final int count;

    /** Lower case, no accents, plurals and endings trimmed to a short stem; small words left out. */
    static List<String> stems(String text) {
        List<String> out = new ArrayList<>(); if (text == null) return out;
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        for (String w : plain.split("[^\\p{L}\\p{N}]+")) {
            if (w.length() < 2 || SMALL.contains(w)) continue;
            String s = w;
            if (s.length() > 4 && s.endsWith("s")) s = s.substring(0, s.length() - 1);
            if (s.length() > 5) s = s.substring(0, 5);
            if (!SMALL.contains(s)) out.add(s);
        }
        return out;
    }

    BibleFinder(Source source) throws Exception {
        List<String> b = new ArrayList<>(), t = new ArrayList<>(); List<Integer> c = new ArrayList<>(), v = new ArrayList<>();
        source.each((book, chapter, verse, text) -> { b.add(book); c.add(chapter); v.add(verse); t.add(text == null ? "" : text.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim()); });
        count = b.size(); books = b.toArray(new String[0]); texts = t.toArray(new String[0]); chapters = new int[count]; verses = new int[count]; words = new int[count][];
        Map<String, Integer> ids = new HashMap<>(); List<int[]> lists = new ArrayList<>(); List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            chapters[i] = c.get(i); verses[i] = v.get(i);
            LinkedHashSet<String> seen = new LinkedHashSet<>(stems(texts[i]));
            int[] mine = new int[seen.size()]; int k = 0;
            for (String s : seen) {
                Integer id = ids.get(s); if (id == null) { id = lists.size(); ids.put(s, id); lists.add(new int[8]); sizes.add(0); }
                int[] l = lists.get(id); int n = sizes.get(id); if (n == l.length) { l = Arrays.copyOf(l, n * 2); lists.set(id, l); } l[n] = i; sizes.set(id, n + 1);
                mine[k++] = id;
            }
            words[i] = mine;
        }
        postings = new HashMap<>(ids.size() * 2);
        for (Map.Entry<String, Integer> e : ids.entrySet()) postings.put(e.getKey(), Arrays.copyOf(lists.get(e.getValue()), sizes.get(e.getValue())));
    }
    double weight(String stem) { int[] p = postings.get(stem); return p == null ? 0 : Math.log(1 + (double) count / p.length); }

    /**
     * The verses that best hold the words of {@code query}, best first: how much of the query's weight (rare words
     * count more) a verse contains, with a small preference for verses that are not much longer than the query.
     */
    List<Hit> find(String query, int limit) {
        List<String> q = new ArrayList<>(new LinkedHashSet<>(stems(query)));
        List<Hit> out = new ArrayList<>(); if (q.isEmpty() || count == 0) return out;
        double total = 0; for (String s : q) total += Math.max(weight(s), q.size() == 1 ? 0 : 0.7); // unknown words still count, so they lower the score
        if (total <= 0) return out;
        float[] score = new float[count]; int[] matched = new int[count]; List<Integer> touched = new ArrayList<>();
        for (String s : q) { int[] p = postings.get(s); if (p == null) continue; float w = (float) weight(s); for (int i : p) { if (matched[i] == 0) touched.add(i); score[i] += w; matched[i]++; } }
        int need = q.size() <= 2 ? q.size() : Math.max(2, (int) Math.ceil(q.size() * 0.4));
        PriorityQueue<Hit> best = new PriorityQueue<>(Comparator.comparingDouble(h -> h.score));
        for (int i : touched) {
            if (matched[i] < need) continue;
            double s = score[i] / total, extra = Math.max(0, words[i].length - q.size() * 2.5);
            s -= Math.min(0.08, extra * 0.004);
            if (s < 0.42) continue;
            best.add(new Hit(books[i], chapters[i], verses[i], texts[i], s)); if (best.size() > limit) best.poll();
        }
        out.addAll(best); out.sort((a, b) -> Double.compare(b.score, a.score));
        return out;
    }
    /** A hit good enough to show as "this is the passage". */
    static boolean confident(List<Hit> hits) { return !hits.isEmpty() && hits.get(0).score >= 0.6 && (hits.size() == 1 || hits.get(0).score - hits.get(1).score >= 0.08 || hits.get(0).score >= 0.85); }

    // ---- one index per Bible, kept while the app runs
    static final Map<String, BibleFinder> CACHE = new HashMap<>();
    static synchronized BibleFinder of(String key, Source source) throws Exception {
        BibleFinder f = CACHE.get(key); if (f != null) return f;
        if (CACHE.size() > 2) CACHE.clear();
        f = new BibleFinder(source); CACHE.put(key, f); return f;
    }
    static synchronized void forget() { CACHE.clear(); }

    // ---- Seeky IA: plain words → the passage
    /** Asks the model which passages the person means; the text itself then comes from the Bible on the device. */
    static String aiPrompt(String words, boolean english) {
        return english
            ? "Someone is looking for a Bible passage. They remember these words (maybe incomplete, from another translation or in their own simple words): «" + words.trim() + "». Give the most likely passages, at most 3, most likely first, one per line, only the full reference in English (for example John 1:16). Write nothing else."
            : "Uma pessoa procura uma passagem da Bíblia. Lembra-se destas palavras (podem estar incompletas, ser de outra tradução ou ditas por palavras suas, simples): «" + words.trim() + "». Indica as passagens mais prováveis, no máximo 3, da mais provável para a menos provável, uma por linha, só a referência completa em português (por exemplo João 1:16). Não escrevas mais nada.";
    }
    /** The references in the model's answer, in its order, without repeats. */
    static List<BibleReferences.Reference> aiAnswer(String answer) {
        List<BibleReferences.Reference> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        if (answer == null) return out;
        for (String line : answer.split("\n")) {
            String l = line.replaceAll("^\\s*(?:[-*•]|\\d+[.)])\\s+", "").replaceAll("[*_]", "").trim(); if (l.isEmpty()) continue;
            BibleReferences.Reference r = BibleReferences.lookup(l);
            if (r == null) { List<BibleReferences.Reference> found = BibleReferences.references(l); if (!found.isEmpty()) r = found.get(0); }
            if (r != null && seen.add(r.title) && out.size() < 3) out.add(r);
        }
        return out;
    }
    /** Whether a search looks like remembered words of a passage (not a file name or a reference). */
    static boolean looksLikeWords(String q) { return q != null && BibleReferences.lookup(q) == null && stems(q).size() >= 2 && q.trim().split("\\s+").length >= 3; }
}
