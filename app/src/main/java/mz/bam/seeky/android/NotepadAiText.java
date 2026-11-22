package mz.bam.seeky.android;

import java.util.*;
import java.util.regex.*;

/**
 * What the Seeky IA does with a note: the instructions for each action, the note text it sends
 * (without pictures, recordings and colour marks), the cleaning of the answer into the notebook's
 * own marks, and how the answer goes back into the note (added at the end or replacing the text,
 * always keeping the note's pictures and recordings).
 */
final class NotepadAiText {
    private NotepadAiText() {}
    static final int LIMIT = 12000;

    static final class Action {
        final String key, label, detail, icon, heading; final boolean replace;
        Action(String key, String label, String detail, String icon, String heading, boolean replace) { this.key = key; this.label = label; this.detail = detail; this.icon = icon; this.heading = heading; this.replace = replace; }
    }
    static final Action[] ACTIONS = {
        new Action("resumir", "Resumir", "As ideias principais no fim da nota", "document", "Resumo", false),
        new Action("corrigir", "Corrigir o texto", "Ortografia, acentos e gramática, sem mudar o sentido", "check", "", true),
        new Action("topicos", "Organizar em tópicos", "Títulos e listas claras com o mesmo conteúdo", "queue", "", true),
        new Action("perguntas", "Perguntas de estudo", "Perguntas com respostas para rever a matéria", "quiz", "Perguntas de estudo", false),
        new Action("tarefas", "Extrair tarefas", "O que há para fazer, numa lista para marcar", "check", "Tarefas", false),
        new Action("passagens", "Passagens relacionadas", "Textos bíblicos ligados ao tema, para tocar e ler", "book", "Passagens relacionadas", false),
        new Action("continuar", "Continuar a escrever", "Mais um ou dois parágrafos no mesmo estilo", "edit", "", false),
        new Action("pedir", "Pedir outra coisa…", "Escreva o que quer que o Seeky faça com a nota", "quiz", "Seeky IA", false),
    };
    static Action action(String key) { for (Action a : ACTIONS) if (a.key.equals(key)) return a; return null; }

    static final String FORMAT_PT = " Escreve só o conteúdo pedido, sem introdução nem comentário final, em texto simples com estas marcas: '## ' para títulos, '- ' para listas, '1. ' para listas numeradas, '[ ] ' para tarefas, '**negrito**', '*itálico*' e '> ' para citações. Não uses tabelas nem blocos de código. Escreve as referências bíblicas por extenso (por exemplo João 3:16).";
    static final String FORMAT_EN = " Write only the requested content, with no introduction or closing remark, as plain text using these marks: '## ' for headings, '- ' for lists, '1. ' for numbered lists, '[ ] ' for tasks, '**bold**', '*italic*' and '> ' for quotes. Do not use tables or code blocks. Write Bible references in full (for example John 3:16).";

    /** The instruction sent with the note. */
    static String prompt(String key, String extra, boolean english) {
        String p;
        switch (key) {
            case "resumir": p = english ? "Summarise this note: the main ideas in a short paragraph followed by 3 to 7 key points." : "Resume esta nota: as ideias principais num parágrafo curto e depois 3 a 7 pontos-chave."; break;
            case "corrigir": p = english ? "Correct spelling, accents, punctuation and grammar in this note. Keep the meaning, the order, the language and the existing marks (headings, lists, tasks, quotes). Return the whole corrected note." : "Corrige a ortografia, os acentos, a pontuação e a gramática desta nota. Mantém o sentido, a ordem, a língua e as marcas que já existem (títulos, listas, tarefas, citações). Devolve a nota inteira corrigida."; break;
            case "topicos": p = english ? "Reorganise this note into clear headings and lists, keeping all the content and the language. Do not add new facts. Return the whole reorganised note." : "Reorganiza esta nota em títulos e listas claros, mantendo todo o conteúdo e a língua. Não acrescentes factos novos. Devolve a nota inteira reorganizada."; break;
            case "perguntas": p = english ? "Write 5 to 8 study questions about this note, each followed by a short answer on the next line starting with '> '. Number the questions." : "Escreve 5 a 8 perguntas de estudo sobre esta nota, cada uma seguida de uma resposta curta na linha seguinte começada por '> '. Numera as perguntas."; break;
            case "tarefas": p = english ? "List the actions and tasks that come out of this note, one per line, each starting with '[ ] '. If there are none, suggest up to 3 useful next steps." : "Lista as acções e tarefas que saem desta nota, uma por linha, cada uma começada por '[ ] '. Se não houver nenhuma, sugere até 3 próximos passos úteis."; break;
            case "passagens": p = english ? "Suggest 4 to 8 Bible passages related to the theme of this note. For each, a line '- Book chapter:verse — why it relates' in one sentence." : "Sugere 4 a 8 passagens bíblicas relacionadas com o tema desta nota. Para cada uma, uma linha '- Livro capítulo:versículo — porque se relaciona' numa frase."; break;
            case "continuar": p = english ? "Continue writing this note with one or two paragraphs in the same style, tone and language, without repeating what is already written." : "Continua a escrever esta nota com um ou dois parágrafos no mesmo estilo, tom e língua, sem repetir o que já está escrito."; break;
            default: p = (english ? "Request about this note: " : "Pedido sobre esta nota: ") + (extra == null ? "" : extra.trim());
        }
        return p + (english ? FORMAT_EN : FORMAT_PT);
    }

    static final Pattern MEDIA = Pattern.compile("^\\s*(!\\[[^\\]]*\\]\\(note-img:[A-Za-z0-9._-]+\\)|\\[[^\\]]*\\]\\(note-audio:[A-Za-z0-9._-]+\\))\\s*$");
    /** The note as sent: title first, without pictures, recordings, colour and alignment marks. */
    static String source(String title, String body) {
        StringBuilder b = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) b.append("# ").append(title.trim()).append("\n\n");
        for (String line : (body == null ? "" : body).replace("\r", "").split("\n", -1)) {
            if (MEDIA.matcher(line).matches()) continue;
            b.append(line.replaceAll("\\{cor:[a-z]+\\}|\\{/cor\\}", "").replaceFirst("^(\\s*)\\[[cd]\\] ", "$1")).append('\n');
        }
        return b.toString().trim();
    }
    /** True when the note is longer than one request can take. */
    static boolean tooLong(String source) { return source.length() > LIMIT; }
    static String cut(String source) { if (!tooLong(source)) return source; int at = source.lastIndexOf('\n', LIMIT); return source.substring(0, at > LIMIT / 2 ? at : LIMIT); }

    /** The answer in the notebook's own marks. */
    static String clean(String answer) {
        if (answer == null) return "";
        String t = answer.replace("\r", "").trim();
        t = t.replaceAll("(?m)^```[a-zA-Z]*\\s*$", "");
        StringBuilder out = new StringBuilder();
        for (String line : t.split("\n", -1)) {
            String l = line;
            Matcher m = Pattern.compile("^(\\s*)[*+•]\\s+(.*)$").matcher(l);
            if (m.matches()) l = m.group(1) + "- " + m.group(2);
            m = Pattern.compile("^(\\s*)-\\s+\\[([ xX])\\]\\s+(.*)$").matcher(l);
            if (m.matches()) l = m.group(1) + "[" + m.group(2) + "] " + m.group(3);
            if (l.matches("^#{5,}\\s.*")) l = "####" + l.replaceFirst("^#+", "");
            out.append(l).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
    /** A note whose text was replaced keeps its pictures and recordings at the end. */
    static String keepMedia(String original, String replacement) {
        StringBuilder missing = new StringBuilder();
        for (String line : (original == null ? "" : original).split("\n")) if (MEDIA.matcher(line).matches() && !replacement.contains(line.trim())) missing.append('\n').append(line.trim());
        return missing.length() == 0 ? replacement : replacement + "\n" + missing;
    }
    /** The note after the answer: added under a heading at the end, or replacing the text. */
    static String apply(Action a, String body, String answer, boolean english) {
        String clean = clean(answer);
        if (a.replace) return keepMedia(body, clean) + "\n";
        String base = body == null ? "" : body.replaceAll("\\s+$", "");
        String head = a.heading.isEmpty() ? "" : "## " + (english ? english(a.heading) : a.heading) + "\n";
        return (base.isEmpty() ? "" : base + "\n\n") + head + clean + "\n";
    }
    static String english(String heading) {
        switch (heading) { case "Resumo": return "Summary"; case "Perguntas de estudo": return "Study questions"; case "Tarefas": return "Tasks"; case "Passagens relacionadas": return "Related passages"; default: return heading.equals("Seeky IA") ? "Seeky AI" : heading; }
    }
}
