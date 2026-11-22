package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Word (.docx) export of a note: headings, lists, tasks, quotes, tables, colours and pictures, without any library. */
final class NotepadExport {
    interface Images { /** Bytes, width and height in pixels, and "png" or "jpeg"; null when missing. */ Object[] image(String ref); }

    static byte[] docx(String title, String body, Images images) throws IOException { return docx(title, body, images, 0); }
    /** spacing: line spacing in percent (100 = single, 150, 200); 0 keeps the default (1,2). */
    static byte[] docx(String title, String body, Images images, int spacing) throws IOException {
        List<NotepadDoc.Block> blocks = NotepadDoc.parse(body);
        StringBuilder doc = new StringBuilder();
        List<String[]> media = new ArrayList<>(); // {rId, target, type}
        Map<String, byte[]> files = new LinkedHashMap<>();
        boolean numbers = false, bullets = false;
        if (title != null && !title.trim().isEmpty()) doc.append("<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>").append(run(new NotepadDoc.Run(title.trim(), false, false, false, false, false, 0))).append("</w:p>");
        int pic = 0;
        for (NotepadDoc.Block b : blocks) {
            String jc = b.align == 'c' ? "<w:jc w:val=\"center\"/>" : b.align == 'r' ? "<w:jc w:val=\"right\"/>" : "";
            String ind = b.level > 0 && !b.kind.equals("bullet") && !b.kind.equals("number") ? "<w:ind w:left=\"" + 720 * b.level + "\"/>" : "";
            switch (b.kind) {
                case "table": {
                    doc.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>");
                    for (String side : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) doc.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>");
                    doc.append("</w:tblBorders></w:tblPr>");
                    boolean head = true;
                    for (List<String> row : b.rows) {
                        doc.append("<w:tr>");
                        for (String cell : row) { doc.append("<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/>").append(head ? "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EDEDED\"/>" : "").append("</w:tcPr><w:p>"); for (NotepadDoc.Run r : NotepadDoc.runs(cell)) doc.append(run(head ? new NotepadDoc.Run(r.text, true, r.italic, r.underline, r.strike, r.mark, r.color) : r)); doc.append("</w:p></w:tc>"); }
                        doc.append("</w:tr>");
                        head = false;
                    }
                    doc.append("</w:tbl><w:p/>");
                    continue;
                }
                case "rule": doc.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\" w:color=\"999999\"/></w:pBdr></w:pPr></w:p>"); continue;
                case "image": {
                    Object[] img = images == null ? null : images.image(b.ref);
                    if (img == null) { doc.append("<w:p>").append(run(new NotepadDoc.Run("[" + (b.label.isEmpty() ? "imagem" : b.label) + "]", false, true, false, false, false, 0))).append("</w:p>"); continue; }
                    pic++;
                    String ext = "png".equals(img[3]) ? "png" : "jpeg", target = "media/image" + pic + "." + ext, rid = "rIdImg" + pic;
                    files.put("word/" + target, (byte[]) img[0]); media.add(new String[]{rid, target});
                    long w = Math.max(1, ((Number) img[1]).longValue()), h = Math.max(1, ((Number) img[2]).longValue());
                    long cx = Math.min(5486400L, w * 9525L), cy = cx * h / w;
                    doc.append("<w:p><w:pPr>").append(jc.isEmpty() ? "<w:jc w:val=\"center\"/>" : jc).append("</w:pPr><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/><wp:docPr id=\"").append(pic).append("\" name=\"Imagem ").append(pic).append("\"/><a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:nvPicPr><pic:cNvPr id=\"").append(pic).append("\" name=\"image").append(pic).append("\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"").append(rid).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
                    continue;
                }
                case "audio": doc.append("<w:p>").append(run(new NotepadDoc.Run("🎙 " + (b.label.isEmpty() ? "Gravação de voz" : b.label.replace("🎙", "").trim()), false, true, false, false, false, 0xff7a7a7a))).append("</w:p>"); continue;
                default: break;
            }
            doc.append("<w:p><w:pPr>");
            if (b.kind.matches("h[1-4]")) doc.append("<w:pStyle w:val=\"Heading").append(b.kind.charAt(1)).append("\"/>");
            else if (b.kind.equals("quote")) doc.append("<w:pStyle w:val=\"Quote\"/>");
            else if (b.kind.equals("bullet") || b.kind.equals("number")) { boolean num = b.kind.equals("number"); if (num) numbers = true; else bullets = true; doc.append("<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"").append(b.level).append("\"/><w:numId w:val=\"").append(num ? 2 : 1).append("\"/></w:numPr>"); }
            doc.append(jc).append(ind).append("</w:pPr>");
            if (b.kind.equals("task")) doc.append(run(new NotepadDoc.Run(b.done ? "☑ " : "☐ ", false, false, false, false, false, 0)));
            for (NotepadDoc.Run r : b.runs) doc.append(run(b.kind.equals("task") && b.done ? new NotepadDoc.Run(r.text, r.bold, r.italic, r.underline, true, r.mark, r.color == 0 ? 0xff7a7a7a : r.color) : r));
            doc.append("</w:p>");
        }
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"><w:body>"
                + doc + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>";
        StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/><Relationship Id=\"rIdNumbering\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>");
        for (String[] m : media) rels.append("<Relationship Id=\"").append(m[0]).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"").append(m[1]).append("\"/>");
        rels.append("</Relationships>");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/><Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/><Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>");
            put(zip, "docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" + esc(title == null ? "" : title.trim()) + "</dc:title><dc:creator>Seeky</dc:creator></cp:coreProperties>");
            put(zip, "word/document.xml", document);
            put(zip, "word/_rels/document.xml.rels", rels.toString());
            put(zip, "word/styles.xml", spacing > 0 ? STYLES.replace("w:line=\"288\"", "w:line=\"" + Math.max(240, Math.min(720, spacing * 240 / 100)) + "\"") : STYLES);
            put(zip, "word/numbering.xml", numbering());
            for (Map.Entry<String, byte[]> f : files.entrySet()) { zip.putNextEntry(new ZipEntry(f.getKey())); zip.write(f.getValue()); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    static String run(NotepadDoc.Run r) {
        StringBuilder b = new StringBuilder("<w:r>");
        StringBuilder pr = new StringBuilder();
        if (r.bold) pr.append("<w:b/>"); if (r.italic) pr.append("<w:i/>"); if (r.strike) pr.append("<w:strike/>");
        if (r.color != 0) pr.append("<w:color w:val=\"").append(String.format(Locale.ROOT, "%06X", r.color & 0xffffff)).append("\"/>");
        if (r.underline) pr.append("<w:u w:val=\"single\"/>"); if (r.mark) pr.append("<w:highlight w:val=\"yellow\"/>");
        if (pr.length() > 0) b.append("<w:rPr>").append(pr).append("</w:rPr>");
        b.append("<w:t xml:space=\"preserve\">").append(esc(r.text)).append("</w:t></w:r>");
        return b.toString();
    }
    static String esc(String s) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '&') o.append("&amp;"); else if (c == '<') o.append("&lt;"); else if (c == '>') o.append("&gt;"); else if (c == '"') o.append("&quot;"); else if (c >= 0x20 || c == '\t' || Character.isSurrogate(c)) o.append(c); }
        return o.toString();
    }
    static void put(ZipOutputStream zip, String name, String text) throws IOException { zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    static String numbering() {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:numbering xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">");
        String[] bullets = {"•", "◦", "▪"};
        for (int a = 0; a < 2; a++) {
            b.append("<w:abstractNum w:abstractNumId=\"").append(a).append("\">");
            for (int l = 0; l < 7; l++) b.append("<w:lvl w:ilvl=\"").append(l).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"").append(a == 0 ? "bullet" : "decimal").append("\"/><w:lvlText w:val=\"").append(a == 0 ? bullets[l % 3] : "%" + (l + 1) + ".").append("\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"").append(720 * (l + 1)).append("\" w:hanging=\"360\"/></w:pPr></w:lvl>");
            b.append("</w:abstractNum>");
        }
        b.append("<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num><w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>");
        return b.toString();
    }
    static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Calibri\"/><w:sz w:val=\"23\"/><w:lang w:val=\"pt-PT\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"288\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:after=\"240\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"44\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"36\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"200\" w:after=\"100\"/><w:outlineLvl w:val=\"1\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading3\"><w:name w:val=\"heading 3\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:keepNext/><w:spacing w:before=\"160\" w:after=\"80\"/><w:outlineLvl w:val=\"2\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"26\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading4\"><w:name w:val=\"heading 4\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:keepNext/><w:outlineLvl w:val=\"3\"/></w:pPr><w:rPr><w:b/><w:i/><w:sz w:val=\"24\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Quote\"><w:name w:val=\"Quote\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"8\" w:color=\"B08A3C\"/></w:pBdr><w:ind w:left=\"567\"/></w:pPr><w:rPr><w:i/><w:color w:val=\"555555\"/></w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:after=\"60\"/></w:pPr></w:style>"
            + "<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:name w:val=\"Table Grid\"/><w:tblPr><w:tblCellMar><w:left w:w=\"108\" w:type=\"dxa\"/><w:right w:w=\"108\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr></w:style></w:styles>";
    private NotepadExport() {}
}
