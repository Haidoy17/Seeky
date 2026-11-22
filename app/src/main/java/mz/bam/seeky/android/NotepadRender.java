package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;
import android.text.*;
import android.text.style.*;
import android.util.LruCache;
import android.view.View;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How notes look: live styling of the marks while typing (the marks fade, the text takes the style),
 * the clean reading view (tasks to tick, voice recordings to play, pictures, tables) and the PDF.
 */
final class NotepadRender {
    interface Actions { void toggle(int line); void play(String ref); }
    static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) { @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); } };
    static Bitmap bitmap(Context c, String ref, int maxWidth) {
        if (ref == null || maxWidth <= 0) return null;
        String key = ref + "@" + maxWidth;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        File f = NotepadStore.media(c, ref);
        if (f == null || !f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1; while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options(); d.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
        if (b == null) return null;
        if (b.getWidth() > maxWidth) b = Bitmap.createScaledBitmap(b, maxWidth, Math.max(1, b.getHeight() * maxWidth / b.getWidth()), true);
        CACHE.put(key, b);
        return b;
    }

    // ---------------------------------------------------------------- live styling
    interface Mine {}
    static final class Bold extends StyleSpan implements Mine { Bold() { super(Typeface.BOLD); } }
    static final class Italic extends StyleSpan implements Mine { Italic() { super(Typeface.ITALIC); } }
    static final class Under extends UnderlineSpan implements Mine {}
    static final class Strike extends StrikethroughSpan implements Mine {}
    static final class Mark extends BackgroundColorSpan implements Mine { Mark(int c) { super(c); } }
    static final class Ink extends ForegroundColorSpan implements Mine { Ink(int c) { super(c); } }
    static final class Size extends RelativeSizeSpan implements Mine { Size(float s) { super(s); } }
    static final class Quote extends QuoteSpan implements Mine { Quote(int c) { super(c); } }
    static final class Align extends AlignmentSpan.Standard implements Mine { Align(Layout.Alignment a) { super(a); } }
    static final class Margin extends LeadingMarginSpan.Standard implements Mine { Margin(int px) { super(px); } }
    static final class Picture extends ImageSpan implements Mine { Picture(Bitmap b) { super(b, ImageSpan.ALIGN_BOTTOM); } }
    static final class Mono extends TypefaceSpan implements Mine { Mono() { super("monospace"); } }

    static final Pattern[] INLINE = {Pattern.compile("\\*\\*(.+?)\\*\\*"), Pattern.compile("__(.+?)__"), Pattern.compile("~~(.+?)~~"), Pattern.compile("==(.+?)=="), Pattern.compile("(?<![*\\w])\\*(?![\\s*])([^*\\n]+?)(?<!\\s)\\*(?![*\\w])"), Pattern.compile("\\{cor:(\\w+)\\}(.+?)\\{/cor\\}")};
    static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(note-img:([A-Za-z0-9._-]+)\\)"), AUDIO = Pattern.compile("\\[[^\\]]*\\]\\(note-audio:[A-Za-z0-9._-]+\\)");
    /** Styles an editable note in place; only this class's spans are touched, so typing and the keyboard are not disturbed. */
    static void style(Context c, Editable e, AppPalette p, int ink, int muted, int imageWidth) {
        for (Object o : e.getSpans(0, e.length(), Mine.class)) e.removeSpan(o);
        String text = e.toString();
        float d = c.getResources().getDisplayMetrics().density;
        int dim = (muted & 0x00ffffff) | 0x99000000, start = 0;
        for (String line : text.split("\n", -1)) {
            int end = start + line.length();
            Matcher m = NotepadText.LINE.matcher(line);
            if (m.matches()) {
                int indent = m.group(1).length(), at = start + indent;
                if (indent > 0) e.setSpan(new Margin((int) (indent * 10 * d)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (m.group(2) != null) { int a = at, z = a + m.group(2).length(); e.setSpan(new Ink(dim), a, z, 33); e.setSpan(new Size(.8f), a, z, 33); e.setSpan(new Align(m.group(2).startsWith("[c") ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_OPPOSITE), start, end, 33); at = z; }
                String prefix = m.group(3) == null ? "" : m.group(3);
                if (!prefix.isEmpty()) {
                    e.setSpan(new Ink(prefix.startsWith("[") || prefix.matches("[-•] |\\d+[.)] ") ? p.accent : dim), at, at + prefix.length(), 33);
                    if (prefix.startsWith("#")) { int level = prefix.trim().length(); e.setSpan(new Size(.75f), at, at + prefix.length(), 33); e.setSpan(new Size(level == 1 ? 1.5f : level == 2 ? 1.32f : level == 3 ? 1.18f : 1.08f), at + prefix.length(), end, 33); e.setSpan(new Bold(), at + prefix.length(), end, 33); }
                    else if (prefix.startsWith(">")) { e.setSpan(new Quote(p.accent), at, end, 33); e.setSpan(new Italic(), at + prefix.length(), end, 33); }
                    else if (prefix.matches("\\[[xX]\\] ")) { e.setSpan(new Strike(), at + prefix.length(), end, 33); e.setSpan(new Ink(muted), at + prefix.length(), end, 33); }
                }
            }
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) e.setSpan(new Mono(), start, end, 33);
            Matcher img = IMAGE.matcher(line);
            if (img.find()) { Bitmap b = bitmap(c, img.group(1), imageWidth); if (b != null) e.setSpan(new Picture(b), start + img.start(), start + img.end(), 33); }
            Matcher au = AUDIO.matcher(line);
            if (au.find()) { e.setSpan(new Ink(p.accent), start + au.start(), start + au.end(), 33); e.setSpan(new Bold(), start + au.start(), start + au.end(), 33); }
            for (int k = 0; k < INLINE.length; k++) {
                Matcher x = INLINE[k].matcher(line);
                while (x.find()) {
                    int a = start + x.start(), z = start + x.end(), g = k == 5 ? 2 : 1, ia = start + x.start(g), iz = start + x.end(g);
                    e.setSpan(new Ink(dim), a, ia, 33); e.setSpan(new Ink(dim), iz, z, 33); e.setSpan(new Size(.8f), a, ia, 33); e.setSpan(new Size(.8f), iz, z, 33);
                    Object span = k == 0 ? new Bold() : k == 1 ? new Under() : k == 2 ? new Strike() : k == 3 ? new Mark((p.accent & 0xffffff) | 0x44000000) : k == 4 ? new Italic() : new Ink(NotepadDoc.color(x.group(1)) == 0 ? ink : NotepadDoc.color(x.group(1)));
                    e.setSpan(span, ia, iz, 33);
                }
            }
            start = end + 1;
        }
    }

    // ---------------------------------------------------------------- reading view and PDF
    /** Clean text of a note: marks become styles. {@code actions} null for the PDF (no taps). */
    static SpannableStringBuilder render(Context c, String body, int ink, int muted, int accent, float unit, int imageWidth, Actions actions) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        List<NotepadDoc.Block> blocks = NotepadDoc.parse(body);
        for (int i = 0; i < blocks.size(); i++) {
            NotepadDoc.Block b = blocks.get(i);
            int start = out.length();
            switch (b.kind) {
                case "image": {
                    Bitmap bmp = bitmap(c, b.ref, imageWidth);
                    if (bmp != null) { out.append("￼"); out.setSpan(new ImageSpan(c, bmp, ImageSpan.ALIGN_BOTTOM), start, start + 1, 33); }
                    else { out.append("[").append(b.label.isEmpty() ? "imagem" : b.label).append("]"); out.setSpan(new ForegroundColorSpan(muted), start, out.length(), 33); }
                    break;
                }
                case "audio": {
                    String label = b.label.replace("🎙", "").trim();
                    out.append("▶  🎙 ").append(label.isEmpty() ? "Gravação de voz" : label);
                    out.setSpan(new ForegroundColorSpan(accent), start, out.length(), 33); out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), 33);
                    if (actions != null) { final String ref = b.ref; out.setSpan(new ClickableSpan() { @Override public void onClick(View v) { actions.play(ref); } @Override public void updateDrawState(TextPaint ds) { ds.setColor(accent); ds.setUnderlineText(false); } }, start, out.length(), 33); }
                    break;
                }
                case "rule": out.append("────────────────────"); out.setSpan(new ForegroundColorSpan(muted), start, out.length(), 33); out.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), start, out.length(), 33); break;
                case "table": {
                    int cols = 0; for (List<String> r : b.rows) cols = Math.max(cols, r.size());
                    int[] width = new int[cols];
                    List<List<String>> plain = new ArrayList<>();
                    for (List<String> r : b.rows) { List<String> pr = new ArrayList<>(); for (int k = 0; k < cols; k++) { String t = k < r.size() ? NotepadText.strip(r.get(k)) : ""; pr.add(t); width[k] = Math.max(width[k], t.length()); } plain.add(pr); }
                    for (int r = 0; r < plain.size(); r++) {
                        if (r > 0) out.append('\n');
                        int rs = out.length();
                        StringBuilder row = new StringBuilder();
                        for (int k = 0; k < cols; k++) { String t = plain.get(r).get(k); row.append(k == 0 ? "│ " : " │ ").append(t); for (int pad = t.length(); pad < width[k]; pad++) row.append(' '); }
                        out.append(row).append(" │");
                        if (r == 0) out.setSpan(new StyleSpan(Typeface.BOLD), rs, out.length(), 33);
                    }
                    out.setSpan(new TypefaceSpan("monospace"), start, out.length(), 33); out.setSpan(new RelativeSizeSpan(.88f), start, out.length(), 33);
                    break;
                }
                default: {
                    String lead = "";
                    if (b.kind.equals("bullet")) lead = (b.level % 2 == 0 ? "•" : "◦") + "  ";
                    else if (b.kind.equals("number")) lead = b.number + ".  ";
                    else if (b.kind.equals("task")) lead = b.done ? "☑  " : "☐  ";
                    out.append(lead);
                    if (b.kind.equals("task")) {
                        out.setSpan(new ForegroundColorSpan(accent), start, start + 1, 33); out.setSpan(new RelativeSizeSpan(1.15f), start, start + 1, 33);
                        if (actions != null) { final int line = b.line; out.setSpan(new ClickableSpan() { @Override public void onClick(View v) { actions.toggle(line); } @Override public void updateDrawState(TextPaint ds) { ds.setColor(accent); ds.setUnderlineText(false); } }, start, start + 1, 33); }
                    } else if (!lead.isEmpty()) out.setSpan(new ForegroundColorSpan(accent), start, start + lead.length(), 33);
                    int textStart = out.length();
                    for (NotepadDoc.Run r : b.runs) {
                        int a = out.length(); out.append(r.text); int z = out.length();
                        if (r.bold) out.setSpan(new StyleSpan(Typeface.BOLD), a, z, 33);
                        if (r.italic) out.setSpan(new StyleSpan(Typeface.ITALIC), a, z, 33);
                        if (r.underline) out.setSpan(new UnderlineSpan(), a, z, 33);
                        if (r.strike) out.setSpan(new StrikethroughSpan(), a, z, 33);
                        if (r.mark) out.setSpan(new BackgroundColorSpan((accent & 0xffffff) | 0x44000000), a, z, 33);
                        if (r.color != 0) out.setSpan(new ForegroundColorSpan(r.color), a, z, 33);
                    }
                    if (b.kind.matches("h[1-4]")) { int level = b.kind.charAt(1) - '0'; out.setSpan(new RelativeSizeSpan(level == 1 ? 1.55f : level == 2 ? 1.35f : level == 3 ? 1.18f : 1.08f), start, out.length(), 33); out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), 33); }
                    if (b.kind.equals("quote")) { out.setSpan(new QuoteSpan(accent), start, out.length(), 33); out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), 33); }
                    if (b.kind.equals("task") && b.done) { out.setSpan(new StrikethroughSpan(), textStart, out.length(), 33); out.setSpan(new ForegroundColorSpan(muted), textStart, out.length(), 33); }
                }
            }
            if (b.align != 'l') out.setSpan(new AlignmentSpan.Standard(b.align == 'c' ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_OPPOSITE), start, out.length(), 33);
            int margin = (int) (unit * (b.level * 18 + (b.kind.equals("bullet") || b.kind.equals("number") || b.kind.equals("task") ? 4 : 0)));
            if (margin > 0 && out.length() > start) out.setSpan(new LeadingMarginSpan.Standard(margin, margin + (int) (unit * (b.kind.equals("number") ? 18 : b.kind.equals("p") ? 0 : 16))), start, out.length(), 33);
            if (i < blocks.size() - 1) out.append('\n');
        }
        return out;
    }

    /** A4 PDF of a note: title, styled text, lists, tables and pictures, page numbers at the foot. */
    static void pdf(Context c, String title, String body, OutputStream target) throws IOException {
        int pageW = 595, pageH = 842, margin = 54, width = pageW - margin * 2;
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(11.5f); paint.setColor(0xff1f1f1f);
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (title != null && !title.trim().isEmpty()) { text.append(title.trim()).append("\n\n"); text.setSpan(new RelativeSizeSpan(1.9f), 0, title.trim().length(), 33); text.setSpan(new StyleSpan(Typeface.BOLD), 0, title.trim().length(), 33); }
        text.append(render(c, body, 0xff1f1f1f, 0xff777777, 0xff8a6a2a, 1f, width, null));
        // Pictures no taller than a page.
        for (ImageSpan span : text.getSpans(0, text.length(), ImageSpan.class)) {
            Drawable dr = span.getDrawable(); Rect r = dr.getBounds();
            if (r.height() > pageH - margin * 2 - 40) { int h = pageH - margin * 2 - 40, w = Math.max(1, r.width() * h / Math.max(1, r.height())); dr.setBounds(0, 0, w, h); }
        }
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setLineSpacing(0, 1.18f).setIncludePad(true).build();
        PdfDocument doc = new PdfDocument();
        try {
            int line = 0, number = 0, lines = layout.getLineCount();
            TextPaint foot = new TextPaint(Paint.ANTI_ALIAS_FLAG); foot.setTextSize(8.5f); foot.setColor(0xff8a8a8a); foot.setTextAlign(Paint.Align.CENTER);
            do {
                number++;
                PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, number).create());
                Canvas canvas = page.getCanvas();
                int top = layout.getLineTop(line), end = line, limit = pageH - margin * 2;
                while (end < lines && layout.getLineBottom(end) - top <= limit) end++;
                if (end == line) end = line + 1;
                int bottom = layout.getLineBottom(end - 1);
                canvas.save(); canvas.translate(margin, margin - top); canvas.clipRect(0, top, width, bottom); layout.draw(canvas); canvas.restore();
                canvas.drawText(number + "", pageW / 2f, pageH - 26, foot);
                doc.finishPage(page);
                line = end;
            } while (line < lines);
            doc.writeTo(target);
        } finally { doc.close(); }
    }
    private NotepadRender() {}
}
