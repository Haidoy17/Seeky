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
    /** 5.58: also the box of every letter (null for the spaces and line ends added between words), to select and mark. */
    static final class Page { String text = ""; final List<Hit> hits = new ArrayList<>(); final List<double[]> chars = new ArrayList<>(); }

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
        for (TextPosition tp : where) {
            if (tp == null) { out.chars.add(null); continue; }
            double h = Math.max(tp.getHeightDir(), 4), x0 = tp.getXDirAdj(), bottom = tp.getYDirAdj();
            out.chars.add(new double[]{x0, bottom - h, x0 + Math.max(tp.getWidthDirAdj(), 1), bottom});
        }
        for (BibleReferences.Reference r : BibleReferences.references(out.text)) {
            Hit hit = new Hit(r); hit.boxes.addAll(boxes(out, r.start, r.end));
            if (!hit.boxes.isEmpty()) out.hits.add(hit);
        }
        return out;
    }
    /** The boxes (one per line) covering letters start…end. */
    static List<double[]> boxes(Page page, int start, int end) {
        List<double[]> out = new ArrayList<>(); double[] box = null; double lineY = Double.NaN;
        for (int i = Math.max(0, start); i < Math.min(end, page.chars.size()); i++) {
            double[] c = page.chars.get(i); if (c == null) continue; double h = c[3] - c[1];
            if (box == null || Math.abs(c[3] - lineY) > h * 0.6) { if (box != null) out.add(box); box = c.clone(); lineY = c[3]; }
            else { box[0] = Math.min(box[0], c[0]); box[1] = Math.min(box[1], c[1]); box[2] = Math.max(box[2], c[2]); box[3] = Math.max(box[3], c[3]); }
        }
        if (box != null) out.add(box);
        return out;
    }
    /** The letter nearest to a point (PDF points from the top-left), or -1 on a page without text. */
    static int nearest(Page page, double x, double y) {
        int best = -1; double dist = Double.MAX_VALUE;
        for (int i = 0; i < page.chars.size(); i++) {
            double[] c = page.chars.get(i); if (c == null) continue;
            double dx = x < c[0] ? c[0] - x : x > c[2] ? x - c[2] : 0, dy = y < c[1] ? c[1] - y : y > c[3] ? y - c[3] : 0, d = dx + dy * 4;
            if (d < dist) { dist = d; best = i; if (d == 0) break; }
        }
        return best;
    }
    /** Where a marked text is on the page {start, end} (spaces and line ends do not matter), or null. */
    static int[] locate(Page page, String needle) {
        if (needle == null || needle.trim().length() < 2) return null;
        StringBuilder flat = new StringBuilder(); List<Integer> map = new ArrayList<>(); boolean space = false;
        for (int i = 0; i < page.text.length(); i++) { char c = page.text.charAt(i); if (Character.isWhitespace(c)) { if (!space && flat.length() > 0) { flat.append(' '); map.add(i); } space = true; } else { flat.append(c); map.add(i); space = false; } }
        String want = needle.trim().replaceAll("\\s+", " "); int at = flat.indexOf(want); if (at < 0) return null;
        return new int[]{map.get(at), map.get(at + want.length() - 1) + 1};
    }
}
