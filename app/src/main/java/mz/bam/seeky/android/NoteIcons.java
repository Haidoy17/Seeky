package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * The notebook's own icons: one family drawn on a 24-unit grid with rounded strokes and a soft
 * tinted fill (duotone), so the list, the editor, the floating toolbar and the format panel look
 * consistent. Names the family does not draw fall back to the app's general icons.
 */
final class NoteIcons extends View {
    static final String[] NAMES = {"back", "undo", "redo", "eye", "more", "folder", "list", "cards", "grid", "search", "templates", "compose", "sparkle", "format", "image", "camera", "draw", "checklist", "passage", "table", "wave", "mic", "calendar", "pin", "book", "lock", "clock", "align-l", "align-c", "align-r", "indent", "outdent", "quote", "marker", "rule", "bullets", "numbers", "nocolor", "tag"};
    static boolean has(String name) { for (String n : NAMES) if (n.equals(name)) return true; return false; }
    /** A note icon, or the general icon of that name. */
    static View of(Context c, String name, int color) { return has(name) ? new NoteIcons(c, name, color) : new Icons(c, name, color); }

    final String name; final int color;
    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path(); final RectF r = new RectF();
    NoteIcons(Context c, String name, int color) {
        super(c); this.name = name; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND); line.setStrokeWidth(1.7f); line.setColor(color);
        fill.setStyle(Paint.Style.FILL); fill.setColor((color & 0x00ffffff) | 0x2e000000);
        solid.setStyle(Paint.Style.FILL); solid.setColor(color);
    }
    void rr(Canvas c, float l, float t, float rt, float b, float rad, boolean tint) { r.set(l, t, rt, b); if (tint) c.drawRoundRect(r, rad, rad, fill); c.drawRoundRect(r, rad, rad, line); }
    /** Separate strokes: every four numbers are one line (x1, y1, x2, y2). */
    void ln(Canvas c, float... p) { for (int i = 0; i + 3 < p.length; i += 4) c.drawLine(p[i], p[i + 1], p[i + 2], p[i + 3], line); }
    void dot(Canvas c, float x, float y, float rad) { c.drawCircle(x, y, rad, solid); }
    void star(Path p, float cx, float cy, float rad, float waist) {
        p.moveTo(cx, cy - rad); p.quadTo(cx + waist, cy - waist, cx + rad, cy); p.quadTo(cx + waist, cy + waist, cx, cy + rad); p.quadTo(cx - waist, cy + waist, cx - rad, cy); p.quadTo(cx - waist, cy - waist, cx, cy - rad); p.close();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float s = Math.min(getWidth(), getHeight()) / 24f; if (s <= 0) return;
        c.save(); c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2); c.scale(s, s);
        path.reset();
        switch (name) {
            case "back": line.setStrokeWidth(2f); path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19); c.drawPath(path, line); break;
            case "undo": case "redo": {
                if (name.equals("redo")) { c.scale(-1, 1, 12, 12); }
                path.moveTo(5, 10); path.lineTo(14, 10); r.set(9, 10, 19, 20); path.arcTo(r, -90, 180); path.lineTo(9, 20); c.drawPath(path, line);
                path.reset(); path.moveTo(9, 5.5f); path.lineTo(4.5f, 10); path.lineTo(9, 14.5f); c.drawPath(path, line); break;
            }
            case "eye": path.moveTo(2.5f, 12); path.quadTo(12, 3, 21.5f, 12); path.quadTo(12, 21, 2.5f, 12); path.close(); c.drawPath(path, fill); c.drawPath(path, line); c.drawCircle(12, 12, 3.2f, line); dot(c, 12, 12, 1.5f); break;
            case "more": dot(c, 5.5f, 12, 1.8f); dot(c, 12, 12, 1.8f); dot(c, 18.5f, 12, 1.8f); break;
            case "folder": rr(c, 3, 8, 21, 19.5f, 2.5f, true); path.moveTo(3, 9.5f); path.lineTo(3, 6.5f); path.quadTo(3, 4.5f, 5, 4.5f); path.lineTo(9, 4.5f); path.lineTo(11, 7); path.lineTo(19, 7); path.quadTo(21, 7, 21, 9); c.drawPath(path, line); break;
            case "list": for (int i = 0; i < 3; i++) { float y = 6 + i * 6; rr(c, 3.5f, y - 1.8f, 7.1f, y + 1.8f, 1, true); ln(c, 10.5f, y, 20.5f, y); } break;
            case "cards": rr(c, 3.5f, 4, 20.5f, 11, 2.5f, true); rr(c, 3.5f, 13.5f, 20.5f, 20.5f, 2.5f, true); break;
            case "grid": rr(c, 3.5f, 3.5f, 10.8f, 10.8f, 2.2f, true); rr(c, 13.2f, 3.5f, 20.5f, 10.8f, 2.2f, true); rr(c, 3.5f, 13.2f, 10.8f, 20.5f, 2.2f, true); rr(c, 13.2f, 13.2f, 20.5f, 20.5f, 2.2f, true); break;
            case "search": line.setStrokeWidth(1.9f); c.drawCircle(10.5f, 10.5f, 6.3f, fill); c.drawCircle(10.5f, 10.5f, 6.3f, line); ln(c, 15.3f, 15.3f, 20, 20); break;
            case "templates": rr(c, 8.5f, 3, 20, 16, 2, false); rr(c, 4, 7.5f, 15.5f, 21, 2, true); ln(c, 7, 12, 12.5f, 12, 7, 15, 12.5f, 15, 7, 18, 10.5f, 18); break;
            case "compose": path.moveTo(12, 4.5f); path.lineTo(6.5f, 4.5f); path.quadTo(4, 4.5f, 4, 7); path.lineTo(4, 17.5f); path.quadTo(4, 20, 6.5f, 20); path.lineTo(17, 20); path.quadTo(19.5f, 20, 19.5f, 17.5f); path.lineTo(19.5f, 12); c.drawPath(path, line);
                path.reset(); path.moveTo(18.3f, 3.2f); path.lineTo(20.8f, 5.7f); path.lineTo(12.5f, 14); path.lineTo(9.3f, 14.7f); path.lineTo(10, 11.5f); path.close(); c.drawPath(path, fill); c.drawPath(path, line); break;
            case "sparkle": star(path, 10, 13, 8, 1.6f); c.drawPath(path, fill); c.drawPath(path, line); path.reset(); star(path, 18.5f, 5, 3.2f, .7f); c.drawPath(path, solid); dot(c, 19.5f, 17.5f, 1.2f); break;
            case "format": {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setColor(color); t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
                t.setTextSize(17); c.drawText("A", 1.5f, 18.5f, t); t.setTextSize(13); c.drawText("a", 13.2f, 18.5f, t);
                ln(c, 3, 21.5f, 21, 21.5f); break;
            }
            case "image": rr(c, 3, 4.5f, 21, 19.5f, 3, false); path.moveTo(3.4f, 17); path.lineTo(8.5f, 11.5f); path.lineTo(12.5f, 15.5f); path.lineTo(15, 13); path.lineTo(20.6f, 18); path.lineTo(20.6f, 19.1f); path.lineTo(3.4f, 19.1f); path.close(); c.drawPath(path, fill);
                path.reset(); path.moveTo(3.4f, 17); path.lineTo(8.5f, 11.5f); path.lineTo(12.5f, 15.5f); path.lineTo(15, 13); path.lineTo(20.6f, 18); c.drawPath(path, line); dot(c, 15.8f, 8.8f, 1.7f); break;
            case "camera": path.moveTo(8, 7); path.lineTo(9.4f, 4.8f); path.lineTo(14.6f, 4.8f); path.lineTo(16, 7); c.drawPath(path, line); rr(c, 2.8f, 7, 21.2f, 19.5f, 3, false); c.drawCircle(12, 13, 3.8f, fill); c.drawCircle(12, 13, 3.8f, line); dot(c, 17.8f, 10, 1); break;
            case "draw": path.moveTo(17.2f, 3.3f); path.lineTo(20.2f, 6.3f); path.lineTo(11.5f, 15); path.lineTo(8, 15.9f); path.lineTo(8.9f, 12.4f); path.close(); c.drawPath(path, fill); c.drawPath(path, line);
                path.reset(); path.moveTo(3, 20.5f); path.cubicTo(5, 17.5f, 7, 22.5f, 9.5f, 19.8f); path.cubicTo(11.5f, 17.8f, 13.5f, 21.5f, 16, 19.8f); c.drawPath(path, line); break;
            case "checklist": rr(c, 3.2f, 3.8f, 9.6f, 10.2f, 1.8f, true); path.moveTo(4.7f, 7.1f); path.lineTo(6, 8.4f); path.lineTo(8.3f, 5.6f); c.drawPath(path, line); ln(c, 12.5f, 7, 20.8f, 7);
                rr(c, 3.2f, 13.8f, 9.6f, 20.2f, 1.8f, false); ln(c, 12.5f, 17, 20.8f, 17); break;
            case "passage": path.moveTo(12, 6.5f); path.quadTo(8, 4.3f, 3, 5.2f); path.lineTo(3, 18.6f); path.quadTo(8, 17.7f, 12, 19.8f); path.quadTo(16, 17.7f, 21, 18.6f); path.lineTo(21, 5.2f); path.quadTo(16, 4.3f, 12, 6.5f); path.close(); c.drawPath(path, fill); c.drawPath(path, line);
                ln(c, 12, 6.5f, 12, 19.6f); path.reset(); path.moveTo(15, 5.6f); path.lineTo(15, 11); path.lineTo(16.6f, 9.6f); path.lineTo(18.2f, 11); path.lineTo(18.2f, 5.2f); c.drawPath(path, solid); break;
            case "table": r.set(3.2f, 4.2f, 20.8f, 9.2f); c.save(); c.clipRect(r); r.set(3.2f, 4.2f, 20.8f, 19.8f); c.drawRoundRect(r, 2.5f, 2.5f, fill); c.restore(); rr(c, 3.2f, 4.2f, 20.8f, 19.8f, 2.5f, false); ln(c, 3.2f, 9.2f, 20.8f, 9.2f, 3.2f, 14.5f, 20.8f, 14.5f, 9.1f, 4.2f, 9.1f, 19.8f, 14.9f, 4.2f, 14.9f, 19.8f); break;
            case "wave": line.setStrokeWidth(2.1f); float[] h = {3, 7, 12, 8, 14, 6, 10, 4}; for (int i = 0; i < h.length; i++) { float x = 3.5f + i * 2.4f; ln(c, x, 12 - h[i] / 2, x, 12 + h[i] / 2); } break;
            case "mic": rr(c, 8.8f, 3, 15.2f, 14, 3.2f, true); path.moveTo(5.8f, 11); path.quadTo(5.8f, 17.3f, 12, 17.3f); path.quadTo(18.2f, 17.3f, 18.2f, 11); c.drawPath(path, line); ln(c, 12, 17.3f, 12, 21, 8.8f, 21, 15.2f, 21); break;
            case "calendar": r.set(3.5f, 5.5f, 20.5f, 10); c.save(); c.clipRect(r); r.set(3.5f, 5.5f, 20.5f, 20.5f); c.drawRoundRect(r, 2.5f, 2.5f, fill); c.restore(); rr(c, 3.5f, 5.5f, 20.5f, 20.5f, 2.5f, false); ln(c, 3.5f, 10, 20.5f, 10, 8, 3.5f, 8, 7, 16, 3.5f, 16, 7); dot(c, 8.2f, 14.2f, 1); dot(c, 12, 14.2f, 1); dot(c, 15.8f, 14.2f, 1.7f); dot(c, 8.2f, 17.4f, 1); dot(c, 12, 17.4f, 1); break;
            case "pin": path.moveTo(9, 3.5f); path.lineTo(15, 3.5f); path.lineTo(14, 9.5f); path.lineTo(17.5f, 13.5f); path.lineTo(6.5f, 13.5f); path.lineTo(10, 9.5f); path.close(); c.drawPath(path, fill); c.drawPath(path, line); ln(c, 12, 13.5f, 12, 21); break;
            case "book": rr(c, 5, 3, 19.5f, 21, 2.2f, true); ln(c, 8.5f, 3, 8.5f, 21, 11.5f, 8, 16.5f, 8); break;
            case "lock": rr(c, 5, 10.5f, 19, 20.5f, 2.6f, true); path.moveTo(8, 10.5f); path.lineTo(8, 8); path.quadTo(8, 3.8f, 12, 3.8f); path.quadTo(16, 3.8f, 16, 8); path.lineTo(16, 10.5f); c.drawPath(path, line); dot(c, 12, 15.5f, 1.5f); break;
            case "clock": c.drawCircle(12, 12, 8.5f, fill); c.drawCircle(12, 12, 8.5f, line); ln(c, 12, 7, 12, 12, 12, 12, 15.5f, 14); break;
            case "tag": path.moveTo(3.5f, 4.5f); path.lineTo(11.5f, 4.5f); path.lineTo(20, 13); path.lineTo(13, 20); path.lineTo(3.5f, 12.5f); path.close(); c.drawPath(path, fill); c.drawPath(path, line); dot(c, 8, 9, 1.5f); break;
            case "align-l": ln(c, 4, 6, 20, 6, 4, 10, 14, 10, 4, 14, 20, 14, 4, 18, 12, 18); break;
            case "align-c": ln(c, 4, 6, 20, 6, 7, 10, 17, 10, 4, 14, 20, 14, 8, 18, 16, 18); break;
            case "align-r": ln(c, 4, 6, 20, 6, 10, 10, 20, 10, 4, 14, 20, 14, 12, 18, 20, 18); break;
            case "indent": ln(c, 4, 5, 20, 5, 11, 10, 20, 10, 11, 14, 20, 14, 4, 19, 20, 19); path.moveTo(4, 9); path.lineTo(7.5f, 12); path.lineTo(4, 15); path.close(); c.drawPath(path, solid); break;
            case "outdent": ln(c, 4, 5, 20, 5, 11, 10, 20, 10, 11, 14, 20, 14, 4, 19, 20, 19); path.moveTo(7.5f, 9); path.lineTo(4, 12); path.lineTo(7.5f, 15); path.close(); c.drawPath(path, solid); break;
            case "quote": for (int k = 0; k < 2; k++) { float x = 4 + k * 9; r.set(x, 7, x + 6, 13); c.drawOval(r, solid); path.reset(); path.moveTo(x + 6, 10); path.quadTo(x + 6, 16.5f, x + 1, 18); c.drawPath(path, line); } break;
            case "marker": path.moveTo(14.5f, 3.5f); path.lineTo(20.5f, 9.5f); path.lineTo(12, 18); path.lineTo(6, 12); path.close(); c.drawPath(path, fill); c.drawPath(path, line); path.reset(); path.moveTo(6, 12); path.lineTo(3.5f, 18); path.lineTo(7, 18.5f); path.lineTo(12, 18); c.drawPath(path, line); r.set(3, 20.2f, 21, 21.8f); c.drawRoundRect(r, .8f, .8f, solid); break;
            case "rule": dot(c, 4, 12, 1.4f); ln(c, 7.5f, 12, 16.5f, 12); dot(c, 20, 12, 1.4f); line.setAlpha(110); ln(c, 6, 6.5f, 18, 6.5f, 6, 17.5f, 18, 17.5f); line.setAlpha(255); break;
            case "bullets": for (int i = 0; i < 3; i++) { float y = 6 + i * 6; dot(c, 5, y, 1.7f); ln(c, 9.5f, y, 20.5f, y); } break;
            case "numbers": {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setColor(color); t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); t.setTextSize(6.2f); t.setTextAlign(Paint.Align.CENTER);
                for (int i = 0; i < 3; i++) { float y = 6 + i * 6; c.drawText(String.valueOf(i + 1), 5, y + 2.2f, t); ln(c, 9.5f, y, 20.5f, y); } break;
            }
            case "nocolor": {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setColor(color); t.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); t.setTextSize(15); t.setTextAlign(Paint.Align.CENTER);
                c.drawText("A", 12, 16.5f, t); r.set(4, 19, 20, 21.6f); c.drawRoundRect(r, 1, 1, fill); line.setStrokeWidth(2f); ln(c, 4.5f, 20.5f, 19.5f, 3.5f); break;
            }
            default: break;
        }
        line.setStrokeWidth(1.7f);
        c.restore();
    }
}
