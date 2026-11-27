package mz.bam.seeky.android;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * 5.57 · Bible passages on a PDF page, as on the phone: the page's text with the place of every letter, the
 * passages found in it (BibleReferences) and the boxes they cover, in PDF points from the top-left corner.
 */
final class DeskPdfPassages {
    private DeskPdfPassages() {}
    static final class Hit { final BibleReferences.Reference ref; final List<double[]> boxes = new ArrayList<>(); Hit(BibleReferences.Reference ref) { this.ref = ref; } }
    static final class Page { String text = ""; final List<Hit> hits = new ArrayList<>(); }

    /** Reads page {@code page} (from 0). The caller keeps other threads away from the document. */
    static Page find(PDDocument doc, int page) throws IOException {
        StringBuilder text = new StringBuilder(); List<TextPosition> where = new ArrayList<>();
        PDFTextStripper s = new PDFTextStripper() {
            @Override protected void writeString(String str, List<TextPosition> positions) {
                for (TextPosition tp : positions) { String u = tp.getUnicode(); if (u == null) continue; for (int i = 0; i < u.length(); i++) { text.append(u.charAt(i)); where.add(tp); } }
            }
            @Override protected void writeWordSeparator() { text.append(' '); where.add(null); }
            @Override protected void writeLineSeparator() { text.append('\n'); where.add(null); }
        };
        s.setSortByPosition(true); s.setStartPage(page + 1); s.setEndPage(page + 1);
        s.writeText(doc, new StringWriter());
        Page out = new Page(); out.text = text.toString();
        for (BibleReferences.Reference r : BibleReferences.references(out.text)) {
            Hit hit = new Hit(r); double[] box = null; double lineY = Double.NaN;
            for (int i = Math.max(0, r.start); i < Math.min(r.end, where.size()); i++) {
                TextPosition tp = where.get(i); if (tp == null) continue;
                double h = Math.max(tp.getHeightDir(), 4), x0 = tp.getXDirAdj(), x1 = x0 + tp.getWidthDirAdj(), bottom = tp.getYDirAdj(), top = bottom - h;
                if (box == null || Math.abs(bottom - lineY) > h * 0.6) { if (box != null) hit.boxes.add(box); box = new double[]{x0, top, x1, bottom}; lineY = bottom; }
                else { box[0] = Math.min(box[0], x0); box[1] = Math.min(box[1], top); box[2] = Math.max(box[2], x1); box[3] = Math.max(box[3], bottom); }
            }
            if (box != null) hit.boxes.add(box);
            if (!hit.boxes.isEmpty()) out.hits.add(hit);
        }
        return out;
    }
}
