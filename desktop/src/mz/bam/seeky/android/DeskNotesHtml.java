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
    /** Bible references become links the window answers ("seeky:passage?…"). */
    static String linkPassages(String html) {
        List<BibleReferences.Reference> refs = BibleReferences.references(html);
        if (refs.isEmpty()) return html;
        StringBuilder out = new StringBuilder(); int at = 0;
        for (BibleReferences.Reference r : refs) {
            if (r.start < at || html.lastIndexOf('<', r.start) > html.lastIndexOf('>', r.start)) continue;
            out.append(html, at, r.start).append("<a class=\"passage\" href=\"seeky:").append(esc(r.title)).append("\">").append(html, r.start, r.end).append("</a>"); at = r.end;
        }
        return out.append(html.substring(at)).toString();
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
