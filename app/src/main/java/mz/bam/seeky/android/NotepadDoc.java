package mz.bam.seeky.android;

import java.util.*;

/**
 * A note as blocks (headings H1–H4, paragraphs, bullet, numbered and task lists, quotes, tables,
 * dividers, images and voice recordings) with styled runs (bold, italic, underline, strike,
 * highlight, colour). One reading of the marks serves the reading view, Word and PDF export.
 *
 * Marks: "# … ####", "**negrito**", "*itálico*", "__sublinhado__", "~~riscado~~", "==destaque==",
 * "{cor:azul}…{/cor}", "- ", "1. ", "[ ] "/"[x] ", "> ", "[c] " (centro), "[d] " (direita), two spaces
 * per indent level, "| a | b |" tables, "---", "![…](note-img:file)" and "[🎙 …](note-audio:file)".
 */
final class NotepadDoc {
    static final String[] COLOR_KEYS = {"azul", "vermelho", "laranja", "verde", "roxo", "cinza"};
    static final String[] COLOR_NAMES = {"Azul", "Vermelho", "Laranja", "Verde", "Roxo", "Cinzento"};
    static final int[] COLOR_VALUES = {0xff2f6fde, 0xffd64545, 0xffe08a1a, 0xff2e9d57, 0xff8a4fd1, 0xff7a7a7a};
    static int color(String key) { for (int i = 0; i < COLOR_KEYS.length; i++) if (COLOR_KEYS[i].equals(key)) return COLOR_VALUES[i]; return 0; }

    static final class Run {
        final String text; final boolean bold, italic, underline, strike, mark; final int color;
        Run(String text, boolean bold, boolean italic, boolean underline, boolean strike, boolean mark, int color) { this.text = text; this.bold = bold; this.italic = italic; this.underline = underline; this.strike = strike; this.mark = mark; this.color = color; }
    }
    static final class Block {
        String kind = "p";   // p h1 h2 h3 h4 bullet number task quote image audio table rule
        int level, number, line; char align = 'l'; boolean done; String ref = "", label = "";
        List<Run> runs = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        String text() { StringBuilder b = new StringBuilder(); for (Run r : runs) b.append(r.text); return b.toString(); }
    }

    static List<Block> parse(String body) {
        List<Block> out = new ArrayList<>();
        String[] lines = (body == null ? "" : body).replace("\r", "").split("\n", -1);
        int[] counters = new int[8];
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.trim().startsWith("|") && raw.trim().endsWith("|") && raw.trim().length() > 1) {
                Block t = new Block(); t.kind = "table"; t.line = i;
                while (i < lines.length && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|") && lines[i].trim().length() > 1) {
                    String row = lines[i].trim();
                    if (!(row.replaceAll("[|\\-:\\s]", "").isEmpty() && row.contains("-"))) {
                        List<String> cells = new ArrayList<>();
                        for (String c : row.substring(1, row.length() - 1).split("\\|", -1)) cells.add(c.trim());
                        t.rows.add(cells);
                    }
                    i++;
                }
                i--;
                if (!t.rows.isEmpty()) out.add(t);
                Arrays.fill(counters, 0);
                continue;
            }
            Block b = new Block(); b.line = i;
            int spaces = 0; while (spaces < raw.length() && raw.charAt(spaces) == ' ') spaces++;
            b.level = Math.min(6, spaces / 2);
            String line = raw.substring(spaces);
            if (line.startsWith("[c] ")) { b.align = 'c'; line = line.substring(4); } else if (line.startsWith("[d] ")) { b.align = 'r'; line = line.substring(4); }
            java.util.regex.Matcher m;
            if (line.trim().equals("---")) b.kind = "rule";
            else if ((m = java.util.regex.Pattern.compile("^!\\[([^\\]]*)\\]\\(note-img:([A-Za-z0-9._-]+)\\)\\s*$").matcher(line)).matches()) { b.kind = "image"; b.label = m.group(1); b.ref = m.group(2); }
            else if ((m = java.util.regex.Pattern.compile("^\\[([^\\]]*)\\]\\(note-audio:([A-Za-z0-9._-]+)\\)\\s*$").matcher(line)).matches()) { b.kind = "audio"; b.label = m.group(1); b.ref = m.group(2); }
            else if (line.matches("^#{1,4}\\s.*")) { int h = line.indexOf(' '); b.kind = "h" + h; b.runs = runs(line.substring(h + 1)); }
            else if (line.matches("^\\[[ xX]\\](\\s.*)?$")) { b.kind = "task"; b.done = line.charAt(1) != ' '; b.runs = runs(line.length() > 3 ? line.substring(4) : ""); }
            else if (line.matches("^[-•*]\\s.*")) { b.kind = "bullet"; b.runs = runs(line.substring(2)); }
            else if ((m = java.util.regex.Pattern.compile("^(\\d{1,3})[.)]\\s(.*)$").matcher(line)).matches()) { b.kind = "number"; b.runs = runs(m.group(2)); }
            else if (line.startsWith(">")) { b.kind = "quote"; b.runs = runs(line.replaceFirst("^>\\s?", "")); }
            else b.runs = runs(line);
            if (b.kind.equals("number")) { counters[b.level]++; for (int k = b.level + 1; k < counters.length; k++) counters[k] = 0; b.number = counters[b.level]; }
            else if (!b.kind.equals("bullet") && !b.kind.equals("task")) Arrays.fill(counters, 0);
            out.add(b);
        }
        while (!out.isEmpty() && out.get(out.size() - 1).kind.equals("p") && out.get(out.size() - 1).text().isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    static final String[] MARKS = {"**", "__", "~~", "==", "*"};
    /** Inline styles; a mark only counts when it is opened and closed on the same line. */
    static List<Run> runs(String text) {
        List<Run> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        Set<String> paired = new HashSet<>();
        for (String mk : MARKS) { int count = 0, at = 0; String rest = text; if (mk.equals("*")) rest = text.replace("**", "\u0000\u0000"); while ((at = rest.indexOf(mk, at)) >= 0) { count++; at += mk.length(); } if (count >= 2) paired.add(mk); }
        boolean bold = false, italic = false, underline = false, strike = false, mark = false; int color = 0;
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            String hit = null;
            if (text.startsWith("{cor:", i)) { int close = text.indexOf('}', i); if (close > i && text.indexOf("{/cor}", close) > 0) { String key = text.substring(i + 5, close); int c = color(key); if (c != 0) { flush(out, cur, bold, italic, underline, strike, mark, color); color = c; i = close + 1; continue; } } }
            if (text.startsWith("{/cor}", i) && color != 0) { flush(out, cur, bold, italic, underline, strike, mark, color); color = 0; i += 6; continue; }
            for (String mk : MARKS) if (text.startsWith(mk, i) && paired.contains(mk)) { hit = mk; break; }
            if (hit != null) {
                flush(out, cur, bold, italic, underline, strike, mark, color);
                switch (hit) { case "**": bold = !bold; break; case "__": underline = !underline; break; case "~~": strike = !strike; break; case "==": mark = !mark; break; default: italic = !italic; }
                i += hit.length();
                continue;
            }
            cur.append(text.charAt(i)); i++;
        }
        flush(out, cur, bold, italic, underline, strike, mark, color);
        return out;
    }
    static void flush(List<Run> out, StringBuilder cur, boolean b, boolean i, boolean u, boolean s, boolean m, int c) { if (cur.length() > 0) { out.add(new Run(cur.toString(), b, i, u, s, m, c)); cur.setLength(0); } }
    /** Every image and recording file a note refers to. */
    static List<String> media(String body) { List<String> out = new ArrayList<>(); for (Block b : parse(body)) if (b.kind.equals("image") || b.kind.equals("audio")) out.add(b.ref); return out; }
    private NotepadDoc() {}
}
