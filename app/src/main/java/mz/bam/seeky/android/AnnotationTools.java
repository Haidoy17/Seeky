package mz.bam.seeky.android;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Destaques e notas do leitor: exportação para PDF e criação de cartões de revisão.
 * Com nota, o cartão mostra o trecho e pede a nota; sem nota, esconde a palavra-chave do trecho
 * (preencher o espaço) e o verso mostra o trecho completo.
 */
final class AnnotationTools {
    static final int LIMIT = 20000;

    private AnnotationTools() {}

    static final class Item {
        final int part;
        final String kind, text, note;
        Item(int part, String kind, String text, String note) { this.part = part; this.kind = kind; this.text = text; this.note = note; }
        String label() { return Highlights.label(kind); }
    }

    static List<Item> collect(ReaderStore store, String uri) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = store.getReadableDatabase().rawQuery("SELECT segment,value,marks FROM draft WHERE uri=? AND marks!='[]' ORDER BY segment,digest", new String[]{uri})) {
            while (c.moveToNext() && out.size() < LIMIT) {
                StudyText text = ReaderStore.decode(c.getString(1), c.getString(2));
                for (StudyText.Mark m : text.marks) {
                    if (m.start < 0 || m.end > text.text.length() || m.end <= m.start) continue;
                    out.add(new Item(c.getInt(0) + 1, m.kind, text.text.substring(m.start, m.end).trim(), m.note.trim()));
                    if (out.size() >= LIMIT) break;
                }
            }
        }
        return out;
    }

    // ---- cartões ----

    static String excerpt(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /** Frente e verso do cartão, ou null se o trecho não der um bom cartão. */
    static String[] card(Item it) {
        String text = excerpt(it.text, 400);
        if (text.length() < 3) return null;
        if (!it.note.isEmpty()) return new String[]{"«" + text + "»", excerpt(it.note, 600)};
        String key = null;
        for (String w : text.split("[^\\p{L}\\p{N}-]+")) if (w.length() >= 5 && (key == null || w.length() > key.length())) key = w;
        if (key == null || text.split("\\s+").length < 4) return null;
        int at = text.indexOf(key);
        String blank = text.substring(0, at) + "_____" + text.substring(at + key.length());
        return new String[]{blank, text};
    }

    static List<String[]> cards(List<Item> items) {
        List<String[]> out = new ArrayList<>();
        for (Item it : items) { String[] c = card(it); if (c != null) out.add(c); }
        return out;
    }

    static int insert(Context c, List<String[]> cards, String title) throws Exception {
        long now = System.currentTimeMillis();
        String tags = excerpt("destaques, " + (title == null ? "" : title), 80);
        int added = 0;
        try (StudyStore db = new StudyStore(c)) {
            for (String[] card : cards) {
                if (db.count("cards", "front=? AND back=?", new String[]{card[0], card[1]}) > 0) continue;
                db.save("cards", 0, StudyStore.values("subject", 0L, "front", card[0], "back", card[1], "tags", tags, "due", now, "updated", now));
                added++;
            }
        }
        if (added > 0) StudyEvents.changed();
        return added;
    }

    static void cards(ReaderActivity a, ReaderStore store) {
        String uri = a.uri.toString(), title = a.name;
        PlusUi.run(a, () -> {
            List<String[]> list = cards(collect(store, uri));
            PlusUi.post(a, () -> {
                if (list.isEmpty()) {
                    new UiDialog.Builder(a).setTitle("Cartões de revisão").setMessage("Ainda não há destaques nem notas que dêem cartões. Destaque frases importantes ou acrescente notas e tente novamente.")
                            .setPositiveButton("Fechar", null).show();
                    return;
                }
                new UiDialog.Builder(a).setTitle("Criar " + list.size() + " cartões?")
                        .setMessage("Notas: o cartão mostra o trecho e pede a sua nota.\nDestaques: a palavra-chave fica escondida para completar.\n\nOs cartões aparecem em Estudos → Rever, com repetição espaçada.")
                        .setPositiveButton("Criar cartões", (d, w) -> PlusUi.run(a, () -> {
                            int added = insert(a, list, title);
                            PlusUi.post(a, () -> PlusUi.toast(a, added + " " + AppLanguage.text(a, "cartões criados.")));
                        })).setNegativeButton("Cancelar", null).show();
            });
        });
    }

    // ---- PDF ----

    /** PDF A4 com título, data e cada anotação (parte, tipo, trecho e nota). Devolve o número de anotações. */
    static int pdf(ReaderStore store, String uri, String title, File out) throws IOException {
        List<Item> items = collect(store, uri);
        PdfDocument doc = new PdfDocument();
        try {
            Writer w = new Writer(doc);
            w.text(title, 20, true, Color.BLACK, 6);
            w.text(items.size() + " anotações · " + java.text.DateFormat.getDateInstance().format(new java.util.Date()), 10, false, 0xff666666, 18);
            for (Item it : items) {
                w.text(("Parte " + it.part + " · " + it.label()).toUpperCase(Locale.ROOT), 9, true, 0xff3f4b3a, 4);
                w.text(excerpt(it.text, 3000), 12, false, Color.BLACK, it.note.isEmpty() ? 14 : 4);
                if (!it.note.isEmpty()) w.text("Nota: " + excerpt(it.note, 3000), 11, false, 0xff444444, 14);
            }
            w.finish();
            try (OutputStream o = new FileOutputStream(out)) { doc.writeTo(o); }
        } finally { doc.close(); }
        return items.size();
    }

    /** Paginação simples por blocos de texto. */
    static final class Writer {
        static final int W = 595, H = 842, M = 48;
        final PdfDocument doc;
        PdfDocument.Page page;
        Canvas canvas;
        float y;
        int number;

        Writer(PdfDocument doc) { this.doc = doc; newPage(); }

        void newPage() {
            if (page != null) doc.finishPage(page);
            page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++number).create());
            canvas = page.getCanvas();
            y = M;
        }

        void text(String s, float size, boolean bold, int color, float after) {
            TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            p.setTextSize(size);
            p.setColor(color);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            StaticLayout layout = StaticLayout.Builder.obtain(s, 0, s.length(), p, W - 2 * M).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0, 1.15f).build();
            int line = 0;
            while (line < layout.getLineCount()) {
                if (y + layout.getLineBottom(line) - layout.getLineTop(line) > H - M) newPage();
                int last = line;
                while (last + 1 < layout.getLineCount() && y + layout.getLineBottom(last + 1) - layout.getLineTop(line) <= H - M) last++;
                canvas.save();
                canvas.translate(M, y - layout.getLineTop(line));
                canvas.clipRect(0, layout.getLineTop(line), W - 2 * M, layout.getLineBottom(last));
                layout.draw(canvas);
                canvas.restore();
                y += layout.getLineBottom(last) - layout.getLineTop(line);
                line = last + 1;
                if (line < layout.getLineCount()) newPage();
            }
            y += after;
        }

        void finish() { if (page != null) { doc.finishPage(page); page = null; } }
    }
}
