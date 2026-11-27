package mz.bam.seeky.android;

import java.io.File;
import java.util.*;

/** A note (Seeky's marks) as HTML for the preview: headings, styles, colours, lists, tasks, quotes, tables, pictures, passages. */
final class DeskNotesHtml {
    private DeskNotesHtml() {}
    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    static String runs(List<NotepadDoc.Run> runs) {
        StringBuilder b = new StringBuilder();
        for (NotepadDoc.Run r : runs) {
            String t = esc(r.text);
            if (r.bold) t = "<b>" + t + "</b>"; if (r.italic) t = "<i>" + t + "</i>"; if (r.underline) t = "<u>" + t + "</u>"; if (r.strike) t = "<s>" + t + "</s>";
            if (r.mark) t = "<mark>" + t + "</mark>";
            if (r.color != 0) t = String.format(Locale.ROOT, "<span style=\"color:#%06x\">", r.color & 0xffffff) + t + "</span>";
            b.append(t);
        }
        return linkPassages(b.toString());
    }
    /** Bible references become links; a click opens the passage over the page (DeskPassage.SCRIPT). */
    static String linkPassages(String html) {
        List<BibleReferences.Reference> refs = BibleReferences.references(html);
        if (refs.isEmpty()) return html;
        StringBuilder out = new StringBuilder(); int at = 0;
        for (BibleReferences.Reference r : refs) {
            if (r.start < at || html.lastIndexOf('<', r.start) > html.lastIndexOf('>', r.start)) continue;
            out.append(html, at, r.start).append("<a class=\"passage\" href=\"#\" data-ref=\"").append(esc(r.title)).append("\">").append(html, r.start, r.end).append("</a>"); at = r.end;
        }
        return out.append(html.substring(at)).toString();
    }
    /** Styled runs without passage links (the editor marks passages itself as you write). */
    static String plainRuns(List<NotepadDoc.Run> runs) {
        StringBuilder b = new StringBuilder();
        for (NotepadDoc.Run r : runs) {
            String t = esc(r.text);
            if (r.bold) t = "<b>" + t + "</b>"; if (r.italic) t = "<i>" + t + "</i>"; if (r.underline) t = "<u>" + t + "</u>"; if (r.strike) t = "<s>" + t + "</s>";
            if (r.mark) t = "<mark>" + t + "</mark>";
            if (r.color != 0) t = String.format(Locale.ROOT, "<span style=\"color:#%06x\">", r.color & 0xffffff) + t + "</span>";
            b.append(t);
        }
        return b.toString();
    }
    static String levelAttr(int level, char align) {
        String style = (level > 0 ? "margin-left:" + (level * 24) + "px;" : "") + (align == 'c' ? "text-align:center;" : align == 'r' ? "text-align:right;" : "");
        return (level > 0 ? " data-level=\"" + level + "\"" : "") + (style.isEmpty() ? "" : " style=\"" + style + "\"");
    }
    /**
     * 5.57 · The note as one page you write in (no separate preview): the same look as the final note, editable.
     * notes-editor.js turns it back into the note marks as you type.
     */
    static String editor(String body, File media) {
        DeskThemes.Theme t = DeskThemes.current;
        String ink = DeskThemes.hex(t.ink), muted = DeskThemes.hex(t.muted), accent = DeskThemes.hex(t.accent), bg = DeskThemes.hex(t.surface), line = DeskThemes.hex(t.line);
        StringBuilder b = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><style>");
        b.append("html,body{margin:0;background:").append(bg).append("}body{font-family:'Segoe UI',Roboto,Arial,sans-serif;font-size:16px;color:").append(ink).append(";line-height:1.55}")
         .append("#doc{outline:none;min-height:calc(100vh - 60px);padding:18px 34px 40px;caret-color:").append(accent).append("}#doc:empty:before{content:'Escreva aqui… Passagens como João 3:16 são reconhecidas.';color:").append(muted).append("}")
         .append("p{margin:0 0 6px}h1{font-size:28px;margin:14px 0 6px}h2{font-size:23px;margin:12px 0 6px}h3{font-size:19px;margin:10px 0 4px}h4{font-size:17px;margin:8px 0 4px}")
         .append("blockquote{border-left:4px solid ").append(accent).append(";margin:8px 0;padding:4px 14px;color:").append(muted).append("}")
         .append("mark{background:#f6d36566;color:inherit}span.passage{color:").append(accent).append(";border-bottom:1px dotted ").append(accent).append(";cursor:pointer}")
         .append("table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid ").append(line).append(";padding:6px 10px;min-width:60px}th{background:").append(DeskThemes.hex(t.raised())).append("}")
         .append("hr{border:none;border-top:1px solid ").append(line).append(";margin:14px 0}img{max-width:100%;border-radius:8px}a.audio{color:").append(accent).append("}")
         .append("ul.tasks{list-style:none;padding-left:4px}li.task,ul.tasks>li{position:relative;padding-left:28px}li.task:before,ul.tasks>li:before{content:'\\2610';position:absolute;left:0;top:-1px;font-size:19px;cursor:pointer;color:").append(accent).append("}")
         .append("li.task[data-done='1']:before,ul.tasks>li[data-done='1']:before{content:'\\2611'}li.task[data-done='1'],ul.tasks>li[data-done='1']{color:").append(muted).append(";text-decoration:line-through}")
         .append(DeskPassage.scrollbarCss()).append("</style></head><body><div id=\"doc\" contenteditable=\"true\" spellcheck=\"true\">");
        String list = "";
        for (NotepadDoc.Block k : NotepadDoc.parse(body)) {
            String want = k.kind.equals("bullet") ? "ul" : k.kind.equals("task") ? "ul class=\"tasks\"" : k.kind.equals("number") ? "ol" : "";
            if (!want.equals(list)) { if (!list.isEmpty()) b.append("</").append(list.startsWith("ul") ? "ul" : "ol").append(">"); if (!want.isEmpty()) b.append("<").append(want).append(">"); list = want; }
            String attrs = levelAttr(k.level, k.align), inner = plainRuns(k.runs);
            switch (k.kind) {
                case "h1": case "h2": case "h3": case "h4": b.append("<").append(k.kind).append(attrs).append(">").append(inner.isEmpty() ? "<br>" : inner).append("</").append(k.kind).append(">"); break;
                case "bullet": case "number": b.append("<li").append(attrs).append(">").append(inner.isEmpty() ? "<br>" : inner).append("</li>"); break;
                case "task": b.append("<li class=\"task\" data-done=\"").append(k.done ? "1" : "0").append("\"").append(attrs).append(">").append(inner.isEmpty() ? "<br>" : inner).append("</li>"); break;
                case "quote": b.append("<blockquote>").append(inner.isEmpty() ? "<br>" : inner).append("</blockquote>"); break;
                case "rule": b.append("<hr>"); break;
                case "image": { File f = new File(media, k.ref); b.append("<p").append(levelAttr(0, k.align)).append("><img data-ref=\"").append(esc(k.ref)).append("\" data-label=\"").append(esc(k.label)).append("\" src=\"").append(f.toURI()).append("\" alt=\"").append(esc(k.label)).append("\"></p>"); break; }
                case "audio": b.append("<p><a class=\"audio\" href=\"#\" contenteditable=\"false\" data-ref=\"").append(esc(k.ref)).append("\" data-label=\"").append(esc(k.label)).append("\">").append(esc(k.label)).append("</a></p>"); break;
                case "table": {
                    b.append("<table>");
                    for (int r = 0; r < k.rows.size(); r++) { b.append("<tr>"); for (String cell : k.rows.get(r)) { String c = plainRuns(NotepadDoc.runs(cell)); b.append(r == 0 ? "<th>" : "<td>").append(c.isEmpty() ? "<br>" : c).append(r == 0 ? "</th>" : "</td>"); } b.append("</tr>"); }
                    b.append("</table>"); break;
                }
                default: b.append("<p").append(attrs).append(">").append(inner.isEmpty() ? "<br>" : inner).append("</p>");
            }
        }
        if (!list.isEmpty()) b.append("</").append(list.startsWith("ul") ? "ul" : "ol").append(">");
        if (NotepadDoc.parse(body).isEmpty()) b.append("<p><br></p>");
        b.append("</div><script>window.SEEKY_COLORS=[");
        for (int i = 0; i < NotepadDoc.COLOR_KEYS.length; i++) { int c = NotepadDoc.COLOR_VALUES[i]; b.append(i == 0 ? "" : ",").append("['").append(NotepadDoc.COLOR_KEYS[i]).append("',").append((c >> 16) & 255).append(",").append((c >> 8) & 255).append(",").append(c & 255).append("]"); }
        b.append("];</script>").append(DeskPassage.SCRIPT);
        return b.append("</body></html>").toString();
    }
    static String html(String title, String body, File media, boolean dark, int spacing) {
        StringBuilder b = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><style>");
        DeskThemes.Theme t = DeskThemes.current; dark = !t.light;
        String ink = DeskThemes.hex(t.ink), muted = DeskThemes.hex(t.muted), accent = DeskThemes.hex(t.accent), bg = DeskThemes.hex(t.surface), line = DeskThemes.hex(t.line);
        b.append("body{font-family:'Segoe UI',Roboto,Arial,sans-serif;font-size:16px;color:").append(ink).append(";background:").append(bg).append(";padding:22px 30px;line-height:").append(spacing > 0 ? spacing / 100.0 * 1.25 : 1.5).append("}")
         .append("h1{font-size:28px;margin:14px 0 6px}h2{font-size:23px;margin:12px 0 6px}h3{font-size:19px}h4{font-size:17px}")
         .append("blockquote{border-left:4px solid ").append(accent).append(";margin:8px 0;padding:4px 14px;color:").append(muted).append("}")
         .append("mark{background:#f6d36555;color:inherit}a.passage{color:").append(accent).append(";text-decoration:none;border-bottom:1px dotted}")
         .append("table{border-collapse:collapse;margin:8px 0}td,th{border:1px solid ").append(line).append(";padding:6px 10px}th{background:").append(DeskThemes.hex(t.raised())).append("}")
         .append("hr{border:none;border-top:1px solid ").append(line).append(";margin:14px 0}img{max-width:100%;border-radius:8px}.task{list-style:none;margin-left:-22px}.done{color:").append(muted).append(";text-decoration:line-through}")
         .append(".title{font-size:30px;font-weight:700;margin:0 0 12px}</style></head><body>");
        if (title != null && !title.trim().isEmpty()) b.append("<div class=\"title\">").append(esc(title)).append("</div>");
        String list = "";
        for (NotepadDoc.Block k : NotepadDoc.parse(body)) {
            String want = k.kind.equals("bullet") || k.kind.equals("task") ? "ul" : k.kind.equals("number") ? "ol" : "";
            if (!want.equals(list)) { if (!list.isEmpty()) b.append("</").append(list).append(">"); if (!want.isEmpty()) b.append("<").append(want).append(">"); list = want; }
            String align = k.align == 'c' ? " style=\"text-align:center\"" : k.align == 'r' ? " style=\"text-align:right\"" : "";
            String indent = k.level > 0 ? " style=\"margin-left:" + (k.level * 24) + "px\"" : "";
            switch (k.kind) {
                case "h1": case "h2": case "h3": case "h4": b.append("<").append(k.kind).append(align).append(">").append(runs(k.runs)).append("</").append(k.kind).append(">"); break;
                case "bullet": case "number": b.append("<li").append(indent).append(">").append(runs(k.runs)).append("</li>"); break;
                case "task": b.append("<li class=\"task").append(k.done ? " done" : "").append("\"").append(indent).append(">").append(k.done ? "☑ " : "☐ ").append(runs(k.runs)).append("</li>"); break;
                case "quote": b.append("<blockquote>").append(runs(k.runs)).append("</blockquote>"); break;
                case "rule": b.append("<hr>"); break;
                case "image": { File f = new File(media, k.ref); b.append("<p").append(align).append("><img src=\"").append(f.toURI()).append("\" alt=\"").append(esc(k.label)).append("\"></p>"); break; }
                case "audio": { File f = new File(media, k.ref); b.append("<p><a href=\"").append(f.toURI()).append("\">🎙 ").append(esc(k.label)).append("</a></p>"); break; }
                case "table": {
                    b.append("<table>");
                    for (int r = 0; r < k.rows.size(); r++) { b.append("<tr>"); for (String cell : k.rows.get(r)) b.append(r == 0 ? "<th>" : "<td>").append(runs(NotepadDoc.runs(cell))).append(r == 0 ? "</th>" : "</td>"); b.append("</tr>"); }
                    b.append("</table>"); break;
                }
                default: String inner = runs(k.runs); b.append("<p").append(align.isEmpty() ? indent : align).append(">").append(inner.isEmpty() ? "&nbsp;" : inner).append("</p>");
            }
        }
        if (!list.isEmpty()) b.append("</").append(list).append(">");
        return b.append("</body></html>").toString();
    }
}
