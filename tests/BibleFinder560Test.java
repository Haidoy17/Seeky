package mz.bam.seeky.android;

import java.util.*;

/** 5.60 · Encontrar a passagem a partir de um trecho (outra tradução, outra ordem) e a resposta da Seeky IA. */
public final class BibleFinder560Test {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); System.out.println("PASS " + what); }
    public static void main(String[] args) throws Exception {
        final String[][] v = {
            {"João", "1", "14", "E o Verbo se fez carne, e habitou entre nós, e vimos a sua glória, como a glória do unigénito do Pai, cheio de graça e de verdade."},
            {"João", "1", "16", "E todos nós recebemos também da sua plenitude, e graça por graça."},
            {"João", "1", "17", "Porque a lei foi dada por Moisés; a graça e a verdade vieram por Jesus Cristo."},
            {"João", "3", "16", "Porque Deus amou o mundo de tal maneira que deu o seu Filho unigénito, para que todo aquele que nele crê não pereça, mas tenha a vida eterna."},
            {"Romanos", "8", "28", "E sabemos que todas as coisas contribuem juntamente para o bem daqueles que amam a Deus, daqueles que são chamados por seu decreto."},
            {"Efésios", "2", "8", "Porque pela graça sois salvos, por meio da fé; e isto não vem de vós; é dom de Deus."},
            {"Colossenses", "2", "9", "Porque nele habita corporalmente toda a plenitude da divindade."},
            {"Salmos", "23", "1", "O Senhor é o meu pastor; nada me faltará."},
            {"Filipenses", "4", "13", "Posso todas as coisas naquele que me fortalece."}};
        BibleFinder f = new BibleFinder(sink -> { for (String[] x : v) sink.verse(x[0], Integer.parseInt(x[1]), Integer.parseInt(x[2]), x[3]); });
        String[][] asked = {
            {"da sua plenitude nos tem dado graça sobre graça", "João 1:16"},
            {"Deus amou tanto o mundo que deu o seu único filho", "João 3:16"},
            {"TUDO POSSO naquele que me fortalece", "Filipenses 4:13"},
            {"todas as coisas cooperam para o bem dos que amam a Deus", "Romanos 8:28"},
            {"salvos pela graca mediante a fe", "Efésios 2:8"}};
        for (String[] q : asked) {
            List<BibleFinder.Hit> h = f.find(q[0], 3);
            check(!h.isEmpty() && h.get(0).title().equals(q[1]) && BibleFinder.confident(h) && h.get(0).reference() != null, "«" + q[0] + "» → " + q[1]);
        }
        check(f.find("receita de bolo de chocolate com morangos", 3).isEmpty(), "palavras sem passagem não inventam resultados");
        check(BibleFinder.looksLikeWords("da sua plenitude graça") && !BibleFinder.looksLikeWords("João 3:16") && !BibleFinder.looksLikeWords("relatório.pdf"), "o Flash só procura versículos em frases");
        List<BibleReferences.Reference> ai = BibleFinder.aiAnswer("1. João 1:16\n- 1 Samuel 1:12\n**Efésios 2:8**\nJoão 1:16");
        check(ai.size() == 3 && ai.get(0).title.equals("João 1:16") && ai.get(1).book.contains("Samuel") && ai.get(1).chapter == 1 && ai.get(2).title.equals("Efésios 2:8"), "a resposta da Seeky IA vira referências (sem repetir)");
        check(BibleFinder.aiPrompt("Jesus chorou", false).contains("Jesus chorou") && BibleFinder.aiPrompt("x", true).contains("John 1:16"), "o pedido à Seeky IA");
    }
}
