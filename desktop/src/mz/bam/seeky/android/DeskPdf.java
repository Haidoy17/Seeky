package mz.bam.seeky.android;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** A note as a PDF (A4), with headings, bold/italic, lists, tasks, quotes, tables as text, pictures and page numbers. */
final class DeskPdf {
    private DeskPdf() {}
    static final float W = PDRectangle.A4.getWidth(), H = PDRectangle.A4.getHeight(), M = 54;
    /** A font that has Portuguese accents and symbols: Segoe UI / Arial on Windows, DejaVu on Linux. */
    static PDFont font(PDDocument doc, boolean bold) throws IOException {
        String windir = System.getenv("WINDIR"); if (windir == null) windir = "C:\\Windows";
        String[] names = bold ? new String[]{windir + "\\Fonts\\segoeuib.ttf", windir + "\\Fonts\\arialbd.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"} : new String[]{windir + "\\Fonts\\segoeui.ttf", windir + "\\Fonts\\arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"};
        for (String n : names) { File f = new File(n); if (f.isFile()) return PDType0Font.load(doc, f); }
        return bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
    }
    static final class Writer implements Closeable {
        final PDDocument doc; final PDFont regular, bold; PDPageContentStream cs; float y; int page;
        Writer(PDDocument doc) throws IOException { this.doc = doc; regular = font(doc, false); bold = font(doc, true); newPage(); }
        void newPage() throws IOException {
            if (cs != null) { footer(); cs.close(); }
            PDPage p = new PDPage(PDRectangle.A4); doc.addPage(p); cs = new PDPageContentStream(doc, p); y = H - M; page++;
        }
        void footer() throws IOException { cs.beginText(); cs.setFont(regular, 9); cs.newLineAtOffset(W / 2 - 4, 28); cs.showText(String.valueOf(page)); cs.endText(); }
        String safe(PDFont f, String s) { StringBuilder b = new StringBuilder(); for (int i = 0; i < s.length(); ) { int cp = s.codePointAt(i); String ch = new String(Character.toChars(cp)); try { f.encode(ch); b.append(ch); } catch (Exception e) { b.append(cp == '☐' ? "[ ]" : cp == '☑' ? "[x]" : "?"); } i += Character.charCount(cp); } return b.toString(); }
        /** Wrapped paragraph; returns nothing, moves down. */
        void text(String s, PDFont f, float size, float indent, float gapAfter) throws IOException {
            s = safe(f, s); float width = W - 2 * M - indent, lead = size * 1.4f;
            List<String> lines = new ArrayList<>(); StringBuilder line = new StringBuilder();
            for (String word : s.split(" ")) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (f.getStringWidth(tryLine) / 1000 * size > width && line.length() > 0) { lines.add(line.toString()); line = new StringBuilder(word); }
                else line = new StringBuilder(tryLine);
            }
            lines.add(line.toString());
            for (String l : lines) { if (y - lead < M) newPage(); y -= lead; cs.beginText(); cs.setFont(f, size); cs.newLineAtOffset(M + indent, y); cs.showText(l); cs.endText(); }
            y -= gapAfter;
        }
        void image(File f) throws IOException {
            if (!f.isFile()) return; PDImageXObject img = PDImageXObject.createFromFile(f.getPath(), doc);
            float w = Math.min(W - 2 * M, img.getWidth()), h = img.getHeight() * w / img.getWidth(); if (h > H - 2 * M) { h = H - 2 * M; w = img.getWidth() * h / img.getHeight(); }
            if (y - h < M) newPage(); y -= h; cs.drawImage(img, M, y, w, h); y -= 8;
        }
        void rule() throws IOException { if (y - 14 < M) newPage(); y -= 8; cs.moveTo(M, y); cs.lineTo(W - M, y); cs.stroke(); y -= 8; }
        @Override public void close() throws IOException { footer(); cs.close(); }
    }
    static void note(String title, String body, File media, OutputStream out) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            try (Writer w = new Writer(doc)) {
                if (title != null && !title.trim().isEmpty()) w.text(title.trim(), w.bold, 22, 0, 10);
                int number = 0;
                for (NotepadDoc.Block k : NotepadDoc.parse(body)) {
                    String t = k.text(); float indent = k.level * 18;
                    boolean strong = false; for (NotepadDoc.Run r : k.runs) strong |= r.bold;
                    switch (k.kind) {
                        case "h1": w.text(t, w.bold, 19, 0, 6); break;
                        case "h2": w.text(t, w.bold, 16, 0, 5); break;
                        case "h3": case "h4": w.text(t, w.bold, 13.5f, 0, 4); break;
                        case "bullet": w.text("•  " + t, w.regular, 11.5f, indent + 10, 2); break;
                        case "number": w.text(k.number + ".  " + t, w.regular, 11.5f, indent + 10, 2); break;
                        case "task": w.text((k.done ? "☑ " : "☐ ") + t, w.regular, 11.5f, indent + 10, 2); break;
                        case "quote": w.text("“" + t + "”", w.regular, 11.5f, 18, 4); break;
                        case "rule": w.rule(); break;
                        case "image": w.image(new File(media, k.ref)); break;
                        case "audio": w.text("🎙 " + k.label, w.regular, 11, 0, 3); break;
                        case "table": for (List<String> row : k.rows) w.text(String.join("   |   ", row), w.regular, 10.5f, 0, 1); w.y -= 6; break;
                        default: w.text(t.isEmpty() ? " " : t, strong && k.runs.size() == 1 ? w.bold : w.regular, 11.5f, indent, 4);
                    }
                }
            }
            doc.save(out);
        }
    }
}
