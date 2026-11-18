package mz.bam.seeky.android;

import java.util.*;
import java.util.regex.*;

/**
 * Prepara o texto para soar natural na voz sintetizada, sem perder o mapa de
 * posições usado para destacar a leitura no ecrã.
 *
 * <p>Regras conservadoras e literais (nenhuma expressão vem do utilizador):
 * <ul>
 *   <li>abreviaturas comuns lidas por extenso ("Dr." → "Doutor", "pág." → "página");</li>
 *   <li>numerais romanos depois de "Capítulo", "Parte", "Século"… passam a árabes,
 *   que todos os motores lêem correctamente;</li>
 *   <li>endereços web lidos apenas pelo domínio, em vez de letra a letra;</li>
 *   <li>pontos-guia de índices ("Introdução ........ 5"), marcadores e pontuação
 *   repetida deixam de ser soletrados;</li>
 *   <li>palavras longas em MAIÚSCULAS (títulos) não são soletradas como siglas;</li>
 *   <li>"%", "§", "°C" e "&amp;" lidos por extenso.</li>
 * </ul>
 * As palavras do dicionário de pronúncia do utilizador são protegidas: a regra
 * pessoal vence sempre a regra automática.
 */
final class SpeechNormalizer {
    interface Replacement { String of(Matcher m, String language); }

    static final class Rule {
        final Pattern pattern; final int group; final Replacement replacement; final boolean portuguese, english;
        Rule(String regex, int flags, int group, boolean portuguese, boolean english, Replacement replacement) {
            this.pattern = Pattern.compile(regex, flags); this.group = group; this.portuguese = portuguese; this.english = english; this.replacement = replacement;
        }
        boolean applies(String language) {
            boolean pt = language.startsWith("pt"), en = language.startsWith("en");
            return portuguese && english || portuguese && pt || english && en;
        }
    }

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String END = "(?=[\\s,;:)\\]\\d]|$)";
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // 1. Endereços web: só o domínio.
        RULES.add(new Rule("(?:https?://|www\\.)[^\\s<>\"]*[^\\s<>\".,;:!?)\\]]", CI, 0, true, true, (m, l) -> host(m.group())));
        // 2. Pontos-guia e separadores decorativos.
        RULES.add(new Rule("[.·…_=~\\-–—]{4,}|(?:\\. ){3,}\\.?", 0, 0, true, true, (m, l) -> " "));
        // 3. Pontuação repetida.
        RULES.add(new Rule("([!?])[!?]+", 0, 0, true, true, (m, l) -> m.group(1)));
        // 4. Marcadores de lista.
        RULES.add(new Rule("(?:^|(?<=\\n))[ \\t]*[•◦▪▫■□►▶●○∙‣⁃][ \\t]*", 0, 0, true, true, (m, l) -> ""));
        RULES.add(new Rule("[ \\t]*[•◦▪▫■□►▶●○∙‣⁃](?=\\s)", 0, 0, true, true, (m, l) -> ", "));
        // 5. Ênfase com asteriscos (não entre números).
        RULES.add(new Rule("(?<![\\d*])\\*{1,3}(?![\\d*])", 0, 0, true, true, (m, l) -> ""));
        // 6. Numerais romanos depois de palavras de secção.
        RULES.add(new Rule("(?iu:\\b(?:cap[ií]tulo|caps?\\.|parte|livro|s[ée]culo|s[ée]c\\.|tomo|volume|vol\\.|sec[cç][ãa]o|se[cç][ãa]o|ato|acto|cena|salmo|lição|unidade|chapter|ch\\.|part|book|century|act|scene|psalm|lesson|unit))\\s+([IVXLCDM]{1,8})(?![\\p{L}\\p{N}])",
                0, 1, true, true, (m, l) -> { int n = roman(m.group(1)); return n > 0 ? Integer.toString(n) : null; }));
        // 7. Símbolos.
        RULES.add(new Rule("(?<=\\d)\\s?%", 0, 0, true, true, (m, l) -> l.startsWith("pt") ? " por cento" : " percent"));
        RULES.add(new Rule("(?<=\\d)\\s?[°º]\\s?C(?![\\p{L}])", 0, 0, true, true, (m, l) -> l.startsWith("pt") ? " graus Celsius" : " degrees Celsius"));
        RULES.add(new Rule("§§?\\s?", 0, 0, true, true, (m, l) -> l.startsWith("pt") ? (m.group().startsWith("§§") ? "parágrafos " : "parágrafo ") : "section "));
        RULES.add(new Rule("(?<=\\s)&(?=\\s)", 0, 0, true, true, (m, l) -> l.startsWith("pt") ? "e" : "and"));
        // 8. Abreviaturas portuguesas (o ponto final da abreviatura também é substituído).
        abbreviations(true, false, new String[][]{
                {"p. ex.", "por exemplo"}, {"V. Ex.ª", "Vossa Excelência"}, {"Sr.", "Senhor"}, {"Srs.", "Senhores"}, {"Sra.", "Senhora"}, {"Sras.", "Senhoras"}, {"Srta.", "Senhorita"},
                {"Dr.", "Doutor"}, {"Drs.", "Doutores"}, {"Dra.", "Doutora"}, {"Prof.", "Professor"}, {"Profa.", "Professora"}, {"Prof.ª", "Professora"},
                {"Eng.", "Engenheiro"}, {"Engª.", "Engenheira"}, {"Av.", "Avenida"}, {"pág.", "página"}, {"págs.", "páginas"},
                {"pp.", "páginas"}, {"cap.", "capítulo"}, {"caps.", "capítulos"}, {"vol.", "volume"}, {"vols.", "volumes"},
                {"séc.", "século"}, {"obs.", "observação"}, {"tel.", "telefone"}, {"n.º", "número"}, {"nº", "número"},
                {"n.os", "números"}, {"ex.", "exemplo"}, {"aprox.", "aproximadamente"},
                {"máx.", "máximo"}, {"mín.", "mínimo"}, {"e/ou", "e ou"}
        });
        RULES.add(new Rule("\\betc(?=\\.)", CI, 0, true, false, (m, l) -> "etcétera"));
        // 9. Abreviaturas inglesas.
        abbreviations(false, true, new String[][]{
                {"Mr.", "Mister"}, {"Mrs.", "Missus"}, {"Dr.", "Doctor"}, {"Prof.", "Professor"}, {"vs.", "versus"},
                {"e.g.", "for example"}, {"i.e.", "that is"}, {"pp.", "pages"}, {"vol.", "volume"}, {"ch.", "chapter"},
                {"approx.", "approximately"}, {"and/or", "and or"}
        });
        RULES.add(new Rule("\\betc(?=\\.)", CI, 0, false, true, (m, l) -> "et cetera"));
        RULES.add(new Rule("\\bNo\\.(?=\\s?\\d)", 0, 0, false, true, (m, l) -> "number"));
        // 10. Títulos em MAIÚSCULAS: evita que o motor soletre "INTRODUÇÃO" como sigla.
        RULES.add(new Rule("(?<![\\p{L}\\p{N}])\\p{Lu}{6,}(?![\\p{L}\\p{N}])", 0, 0, true, true, (m, l) -> {
            String word = m.group();
            if (roman(word) > 0 || !word.matches(".*[AEIOUÁÉÍÓÚÂÊÔÃÕÀYaeiou].*")) return null;
            Locale locale = l.startsWith("pt") ? Locale.forLanguageTag("pt") : Locale.ENGLISH;
            return word.charAt(0) + word.substring(1).toLowerCase(locale);
        }));
    }

    private static void abbreviations(boolean pt, boolean en, String[][] pairs) {
        for (String[] pair : pairs) {
            final String say = pair[1];
            RULES.add(new Rule("(?<![\\p{L}\\p{N}])" + Pattern.quote(pair[0]) + END, CI, 0, pt, en,
                    (m, l) -> Character.isUpperCase(m.group().charAt(0)) && !Character.isUpperCase(say.charAt(0))
                            ? Character.toUpperCase(say.charAt(0)) + say.substring(1) : say));
        }
    }

    static SpeechText.Prepared apply(SpeechText.Prepared in, String language) {
        return apply(in, language, Collections.<PronunciationRules.Rule>emptyList());
    }

    /** Aplica as regras automáticas, sem tocar nas palavras do dicionário pessoal. */
    static SpeechText.Prepared apply(SpeechText.Prepared in, String language, List<PronunciationRules.Rule> personal) {
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String text = in.text;
        if (text.isEmpty()) return in;
        List<int[]> taken = new ArrayList<>();
        for (PronunciationRules.Rule rule : personal) {
            Matcher m = rule.match.matcher(text);
            while (m.find()) taken.add(new int[]{m.start(), m.end(), -1});
        }
        int reserved = taken.size();
        List<String> values = new ArrayList<>();
        for (Rule rule : RULES) {
            if (!rule.applies(lang)) continue;
            Matcher m = rule.pattern.matcher(text);
            while (m.find()) {
                int start = m.start(rule.group), end = m.end(rule.group);
                if (start < 0 || end <= start) continue;
                if (overlaps(taken, start, end)) continue;
                String value = rule.replacement.of(m, lang);
                if (value == null || value.equals(text.substring(start, end))) continue;
                taken.add(new int[]{start, end, values.size()});
                values.add(value);
            }
        }
        if (taken.size() == reserved) return in;
        List<int[]> spans = new ArrayList<>(taken.subList(reserved, taken.size()));
        spans.sort((a, b) -> Integer.compare(a[0], b[0]));
        StringBuilder out = new StringBuilder(text.length() + 64);
        int[] map = new int[text.length() + 64];
        int at = 0, n = 0;
        for (int[] span : spans) {
            while (at < span[0]) { map = ensure(map, n); out.append(text.charAt(at)); map[n++] = in.sourceOffset(at++); }
            String value = values.get(span[2]);
            int anchor = in.sourceOffset(span[0]);
            for (int k = 0; k < value.length(); k++) { map = ensure(map, n); out.append(value.charAt(k)); map[n++] = anchor; }
            at = span[1];
        }
        while (at < text.length()) { map = ensure(map, n); out.append(text.charAt(at)); map[n++] = in.sourceOffset(at++); }
        return new SpeechText.Prepared(out.toString(), Arrays.copyOf(map, n), in.sourceLength);
    }

    private static int[] ensure(int[] map, int n) { return n < map.length ? map : Arrays.copyOf(map, map.length * 2 + 16); }

    private static boolean overlaps(List<int[]> spans, int start, int end) {
        for (int[] s : spans) if (start < s[1] && end > s[0]) return true;
        return false;
    }

    /** Domínio legível de um URL, sem esquema, "www." nem caminho. */
    static String host(String url) {
        String h = url.replaceFirst("(?i)^https?://", "");
        int cut = h.length();
        for (char c : new char[]{'/', '?', '#'}) { int i = h.indexOf(c); if (i >= 0 && i < cut) cut = i; }
        h = h.substring(0, cut);
        int at = h.lastIndexOf('@');
        if (at >= 0) h = h.substring(at + 1);
        int port = h.indexOf(':');
        if (port >= 0) h = h.substring(0, port);
        h = h.replaceFirst("(?i)^www\\.", "");
        return h.isEmpty() ? url : h;
    }

    /** Valor de um numeral romano canónico (I a MMMCMXCIX); 0 quando não é válido. */
    static int roman(String s) {
        if (s == null || s.isEmpty() || s.length() > 15) return 0;
        int total = 0, prior = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = value(s.charAt(i));
            if (v == 0) return 0;
            if (v < prior) total -= v; else { total += v; prior = v; }
        }
        return total > 0 && total < 4000 && toRoman(total).equals(s) ? total : 0;
    }

    private static int value(char c) {
        switch (c) { case 'I': return 1; case 'V': return 5; case 'X': return 10; case 'L': return 50; case 'C': return 100; case 'D': return 500; case 'M': return 1000; default: return 0; }
    }

    static String toRoman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) { out.append(r[i]); n -= v[i]; }
        return out.toString();
    }

    private SpeechNormalizer() {}
}
