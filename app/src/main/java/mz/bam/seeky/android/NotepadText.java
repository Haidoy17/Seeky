package mz.bam.seeky.android;

import java.util.*;

/**
 * Plain-text rules of the notebook: light marks (# título, **negrito**, *itálico*, ==destaque==,
 * "- " listas, "[ ] " tarefas), counts, toggling tasks, inserting marks at the cursor, templates and
 * export. Notes stay readable as plain text anywhere they are copied.
 */
final class NotepadText {
    /** Title shown in lists: the title field, or the first non-empty line. */
    static String title(String title, String body) {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        if (body != null) for (String line : body.split("\n")) { String t = strip(line).trim(); if (!t.isEmpty()) return t.length() > 80 ? t.substring(0, 80).trim() + "…" : t; }
        return "Nota sem título";
    }
    /** A line without its marks ("# ", "- ", "[x] ", "**"…). */
    static String strip(String line) {
        return line.replaceFirst("^\\s*(\\[[cd]\\]\\s+)?(#{1,4}\\s+|[-•*]\\s+|\\[[ xX]\\]\\s+|\\d+[.)]\\s+|>\\s+)?", "").replace("**", "").replace("==", "").replace("~~", "").replace("__", "").replaceAll("\\{cor:\\w+\\}|\\{/cor\\}", "").replaceAll("!?\\[([^\\]]*)\\]\\(note-(img|audio):[^)]*\\)", "$1").replaceAll("(?<![\\w*])\\*(?!\\s)([^*\\n]+?)\\*(?![\\w*])", "$1");
    }
    static String plain(String body) {
        if (body == null) return "";
        StringBuilder b = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            String t = line.trim(), prefix = t.matches("^\\[[xX]\\]\\s.*") ? "☑ " : t.matches("^\\[ \\]\\s.*") ? "☐ " : t.matches("^[-•*]\\s.*") ? "• " : "";
            b.append(prefix).append(strip(line).trim()).append('\n');
        }
        return b.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
    static int words(String body) { int n = 0; for (String w : plain(body).split("\\s+")) if (w.matches(".*[\\p{L}\\p{N}].*")) n++; return n; }
    /** Minutes to read at about 200 words a minute (at least one). */
    static int minutes(String body) { return Math.max(1, (int) Math.ceil(words(body) / 200.0)); }
    /** {done, total} tasks. */
    static int[] tasks(String body) {
        int done = 0, total = 0;
        if (body != null) for (String line : body.split("\n")) { String t = line.trim(); if (t.matches("^\\[[xX]\\]\\s.*") || t.equals("[x]") || t.equals("[X]")) { done++; total++; } else if (t.matches("^\\[ \\]\\s.*") || t.equals("[ ]")) total++; }
        return new int[]{done, total};
    }
    /** Ticks or unticks the task on the given line (0-based). */
    static String toggle(String body, int line) {
        String[] lines = body.split("\n", -1);
        if (line < 0 || line >= lines.length) return body;
        String l = lines[line];
        if (l.matches("^\\s*\\[ \\].*")) lines[line] = l.replaceFirst("\\[ \\]", "[x]");
        else if (l.matches("^\\s*\\[[xX]\\].*")) lines[line] = l.replaceFirst("\\[[xX]\\]", "[ ]");
        return String.join("\n", lines);
    }
    /** Line number that contains a character offset. */
    static int lineAt(String body, int offset) { int n = 0; for (int i = 0; i < Math.min(offset, body.length()); i++) if (body.charAt(i) == '\n') n++; return n; }
    static int lineStart(String body, int offset) { int i = Math.max(0, Math.min(offset, body.length())); while (i > 0 && body.charAt(i - 1) != '\n') i--; return i; }

    /** Result of an edit: new text and the cursor (or selection) after it. */
    static final class Edit { final String text; final int start, end; Edit(String text, int start, int end) { this.text = text; this.start = start; this.end = end; } }
    /** Puts marks around the selection, or removes them if already there; with no selection, places the cursor between them. */
    static Edit wrap(String body, int start, int end, String mark) {
        int s = Math.max(0, Math.min(start, end)), e = Math.min(body.length(), Math.max(start, end));
        if (s >= mark.length() && e + mark.length() <= body.length() && body.startsWith(mark, s - mark.length()) && body.startsWith(mark, e))
            return new Edit(body.substring(0, s - mark.length()) + body.substring(s, e) + body.substring(e + mark.length()), s - mark.length(), e - mark.length());
        String inner = body.substring(s, e);
        return new Edit(body.substring(0, s) + mark + inner + mark + body.substring(e), s + mark.length(), e + mark.length());
    }
    static final java.util.regex.Pattern LINE = java.util.regex.Pattern.compile("^( *)(\\[[cd]\\] )?(#{1,4} |[-•] |\\d{1,3}[.)] |\\[[ xX]\\] |> )?(.*)$");
    interface Change { String[] apply(String[] parts, int index); }
    /** Rewrites every line touched by the selection as {indent, alignment, prefix, text}. */
    static Edit lines(String body, int start, int end, Change change) {
        int s = lineStart(body, Math.min(start, end)), e = Math.max(start, end);
        int stop = body.indexOf('\n', e); if (stop < 0) stop = body.length();
        String[] lines = body.substring(s, stop).split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            java.util.regex.Matcher m = LINE.matcher(lines[i]); m.matches();
            String[] parts = {m.group(1), m.group(2) == null ? "" : m.group(2), m.group(3) == null ? "" : m.group(3), m.group(4)};
            parts = change.apply(parts, i);
            if (i > 0) b.append('\n');
            b.append(parts[0]).append(parts[1]).append(parts[2]).append(parts[3]);
        }
        String text = body.substring(0, s) + b + body.substring(stop);
        int cursor = s + b.length();
        return new Edit(text, cursor, cursor);
    }
    /** Adds (or removes, when every line has it) a list, task, quote or heading prefix; numbered lists count up. */
    static Edit prefix(String body, int start, int end, String prefix) {
        int s = lineStart(body, Math.min(start, end)), e = Math.max(start, end);
        int stop = body.indexOf('\n', e); if (stop < 0) stop = body.length();
        boolean numbered = prefix.matches("\\d+[.)] "), all = true;
        for (String l : body.substring(s, stop).split("\n", -1)) { java.util.regex.Matcher m = LINE.matcher(l); m.matches(); String p = m.group(3) == null ? "" : m.group(3); if (numbered ? !p.matches("\\d+[.)] ") : !p.equals(prefix)) all = false; }
        final boolean remove = all;
        return lines(body, start, end, (parts, i) -> { parts[2] = remove ? "" : numbered ? (i + 1) + ". " : prefix; return parts; });
    }
    /** H1–H4, or 0 for body text. */
    static Edit heading(String body, int start, int end, int level) { return lines(body, start, end, (p, i) -> { p[2] = level <= 0 ? (p[2].startsWith("#") ? "" : p[2]) : "####".substring(0, Math.min(4, level)) + " "; return p; }); }
    static Edit indent(String body, int start, int end, int delta) { return lines(body, start, end, (p, i) -> { int n = Math.max(0, Math.min(12, p[0].length() + 2 * delta)); p[0] = "            ".substring(0, n); return p; }); }
    /** 'l', 'c' or 'r'. */
    static Edit align(String body, int start, int end, char a) { return lines(body, start, end, (p, i) -> { p[1] = a == 'c' ? "[c] " : a == 'r' ? "[d] " : ""; return p; }); }
    /** Colours the selection ({cor:azul}…{/cor}); a null key removes the colour around it. */
    static Edit color(String body, int start, int end, String key) {
        int s = Math.max(0, Math.min(start, end)), e = Math.min(body.length(), Math.max(start, end));
        int open = body.lastIndexOf("{cor:", s), close = open < 0 ? -1 : body.indexOf("{/cor}", open);
        if (open >= 0 && close >= e - 6 && body.indexOf('}', open) < s + 1 && close >= s) {
            int openEnd = body.indexOf('}', open) + 1;
            String inner = body.substring(openEnd, close);
            String rebuilt = key == null ? inner : "{cor:" + key + "}" + inner + "{/cor}";
            String text = body.substring(0, open) + rebuilt + body.substring(close + 6);
            int shift = key == null ? 0 : key.length() + 6;
            return new Edit(text, open + shift, open + shift + inner.length());
        }
        if (key == null) return new Edit(body, s, e);
        String tag = "{cor:" + key + "}";
        return new Edit(body.substring(0, s) + tag + body.substring(s, e) + "{/cor}" + body.substring(e), s + tag.length(), e + tag.length());
    }
    static String table(int columns, int rows) {
        StringBuilder b = new StringBuilder("|");
        for (int c = 0; c < columns; c++) b.append(" Coluna ").append(c + 1).append(" |");
        b.append("\n|"); for (int c = 0; c < columns; c++) b.append("---|");
        for (int r = 0; r < rows; r++) { b.append("\n|"); for (int c = 0; c < columns; c++) b.append("   |"); }
        return b.toString();
    }
    /** Text inserted at the cursor, on its own line when the cursor is in the middle of one. */
    static Edit insert(String body, int at, String text, boolean ownLine) {
        int p = Math.max(0, Math.min(at, body.length()));
        String before = body.substring(0, p), after = body.substring(p);
        String add = text;
        if (ownLine) { if (!before.isEmpty() && !before.endsWith("\n")) add = "\n" + add; if (!after.isEmpty() && !after.startsWith("\n")) add = add + "\n"; }
        return new Edit(before + add + after, p + add.length(), p + add.length());
    }
    /** A passage written into a note: the reference, the text and the version. */
    static String passage(String title, String text, String version) {
        return "> " + title + (version == null || version.isEmpty() ? "" : " (" + version + ")") + "\n> «" + text.trim().replaceAll("\\s+", " ") + "»";
    }
    /** Ready-made notes. */
    static final String[][] TEMPLATES = {
        {"Estudo bíblico (OIA)", "# Passagem\n\n# Observação — o que diz o texto?\n- \n\n# Interpretação — o que significa?\n- \n\n# Aplicação — o que muda na minha vida?\n[ ] \n\n# Oração\n"},
        {"Sermão / pregação", "# Tema\n\n# Texto base\n\n# Pontos principais\n1. \n2. \n3. \n\n# Passagens citadas\n- \n\n# Para pôr em prática\n[ ] \n"},
        {"Lista de tarefas", "[ ] \n[ ] \n[ ] \n"},
        {"Devocional do dia", "# Versículo do dia\n\n# O que Deus me ensinou\n\n# Gratidão\n- \n\n# Pedidos de oração\n[ ] \n"},
        {"Resumo de livro", "# Livro e autor\n\n# Ideias principais\n- \n\n# Citações\n> \n\n# O que vou aplicar\n[ ] \n"}};
    /** Markdown export (the marks are already Markdown-like). */
    static String markdown(String title, String body) {
        StringBuilder b = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) b.append("# ").append(title.trim()).append("\n\n");
        for (String line : (body == null ? "" : body).split("\n", -1)) b.append(line.replaceFirst("^(\\s*)\\[[cd]\\] ", "$1").replaceFirst("^(\\s*)\\[([ xX])\\]", "$1- [$2]").replace("==", "**").replaceAll("__(.+?)__", "<u>$1</u>").replaceAll("\\{cor:\\w+\\}|\\{/cor\\}", "")).append('\n');
        return b.toString().trim() + "\n";
    }
    /** Tags typed as "estudo, oração" or "#estudo #oração", without duplicates. */
    static List<String> tags(String value) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (value != null) for (String t : value.split("[,;#\\n]")) { String x = t.trim().replaceAll("\\s+", " "); if (!x.isEmpty() && x.length() <= 40) out.add(x); }
        return new ArrayList<>(out);
    }
    private NotepadText() {}
}
