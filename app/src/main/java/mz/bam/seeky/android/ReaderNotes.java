package mz.bam.seeky.android;

import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

/**
 * Notes inside the reader: write a note about the book without leaving it (quick formatting, the
 * page or chapter, the selected words), see the book's notes and open them, and find them later in
 * the Bloco de notas under the book's name.
 */
final class ReaderNotes {
    private ReaderNotes() {}

    static long material(ReaderActivity a) { return a.getIntent().getLongExtra("study_material", 0); }
    static String uri(ReaderActivity a) { return a.uri == null ? "" : a.uri.toString(); }
    static String title(ReaderActivity a) { String t = a.getIntent().getStringExtra("study_title"); if (t == null || t.trim().isEmpty()) t = a.name; return t == null ? "" : t.replaceFirst("\\.[A-Za-z0-9]{2,5}$", ""); }
    static String where(ReaderActivity a, int at) { return (a.kind != null && a.kind.equals("pdf") ? AppLanguage.text(a, "Página") : AppLanguage.text(a, "Capítulo")) + " " + (at + 1); }

    /** The panel; quote is the selected text to start the note with (may be empty). */
    static void show(ReaderActivity a, String quote) {
        final int at = a.chrome == null ? 0 : a.chrome.current();
        UiPanel panel = new UiPanel(a, "Notas deste livro", title(a), "note");
        float d = a.getResources().getDisplayMetrics().density;
        // Composer
        EditText input = new EditText(a);
        input.setHint(AppLanguage.text(a, "Escreva a sua nota… (fica no Bloco de notas, ligada a este livro)"));
        input.setMinLines(4); input.setMaxLines(10); input.setGravity(Gravity.TOP); input.setTextSize(15); input.setTextColor(panel.ink); input.setHintTextColor(panel.muted);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(panel.shape(ThemeColors.blend(panel.surface, panel.ink, .05f), 14)); int pad = (int) (12 * d); input.setPadding(pad, pad, pad, pad);
        if (quote != null && !quote.trim().isEmpty()) { String q = "> " + quote.trim().replace("\n", "\n> ") + "\n> — " + where(a, at) + "\n\n"; input.setText(q); input.setSelection(q.length()); }
        panel.content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(a); row.setPadding(0, (int) (8 * d), 0, 0);
        String[][] buttons = {{"B", "bold"}, {"I", "italic"}, {"U", "under"}, {"H", "heading"}, {"•", "list"}, {"☐", "task"}, {"❝", "quote"}, {"📄", "page"}};
        for (String[] b : buttons) {
            TextView t = new TextView(a); t.setText(b[0]); t.setTextSize(16); t.setTextColor(panel.ink); t.setGravity(Gravity.CENTER); t.setTypeface(b[1].equals("italic") ? Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC) : Typeface.DEFAULT_BOLD);
            if (b[1].equals("under")) t.setPaintFlags(t.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            t.setBackground(panel.shape(ThemeColors.blend(panel.surface, panel.accent, .08f), 12)); t.setContentDescription(AppLanguage.text(a, NotepadActivity.label(b[1].equals("heading") ? "h2" : b[1].equals("page") ? "page" : b[1])));
            if (b[1].equals("page")) t.setContentDescription(AppLanguage.text(a, "Inserir a página ou o capítulo"));
            t.setOnClickListener(v -> {
                String text = input.getText().toString(); int s = Math.max(0, input.getSelectionStart()), e = Math.max(0, input.getSelectionEnd());
                NotepadText.Edit edit;
                switch (b[1]) {
                    case "bold": edit = NotepadText.wrap(text, s, e, "**"); break;
                    case "italic": edit = NotepadText.wrap(text, s, e, "*"); break;
                    case "under": edit = NotepadText.wrap(text, s, e, "__"); break;
                    case "heading": edit = NotepadText.heading(text, s, e, 2); break;
                    case "list": edit = NotepadText.prefix(text, s, e, "- "); break;
                    case "task": edit = NotepadText.prefix(text, s, e, "[ ] "); break;
                    case "quote": edit = NotepadText.prefix(text, s, e, "> "); break;
                    default: edit = NotepadText.insert(text, e, "📄 " + where(a, at), false);
                }
                if (edit == null) return;
                input.setText(edit.text); input.setSelection(Math.min(edit.start, edit.text.length()), Math.min(edit.end, edit.text.length())); input.requestFocus();
            });
            Motion.touch(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (42 * d), 1); lp.rightMargin = (int) (5 * d); row.addView(t, lp);
        }
        panel.content.addView(row);
        LinearLayout actions = new LinearLayout(a); actions.setPadding(0, (int) (10 * d), 0, (int) (6 * d));
        TextView save = new TextView(a); save.setText(AppLanguage.text(a, "Guardar nota")); save.setTextSize(15); save.setTypeface(Typeface.DEFAULT_BOLD); save.setGravity(Gravity.CENTER); save.setTextColor(AppPalette.of(a).onAccent); save.setBackground(panel.shape(panel.accent, 14));
        TextView full = new TextView(a); full.setText(AppLanguage.text(a, "Abrir no Bloco de notas")); full.setTextSize(14); full.setTypeface(Typeface.DEFAULT_BOLD); full.setGravity(Gravity.CENTER); full.setTextColor(panel.accent); full.setBackground(panel.shape(ThemeColors.blend(panel.surface, panel.accent, .10f), 14));
        save.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) { PlusUi.toast(a, "Escreva a nota primeiro."); return; }
            NotepadStore.Note n = newNote(a, at); n.body = text;
            panel.dialog.dismiss();
            a.worker.execute(() -> { NotepadStore.save(a, n); a.ui(() -> PlusUi.toast(a, "Nota guardada no Bloco de notas, ligada a este livro.")); });
        });
        full.setOnClickListener(v -> {
            String text = input.getText().toString().trim(); panel.dialog.dismiss();
            if (text.isEmpty()) NotepadActivity.bookNotes(a, material(a), uri(a), title(a));
            else NotepadActivity.start(a, new android.content.Intent(a, NotepadActivity.class).putExtra("material", material(a)).putExtra("book_uri", uri(a)).putExtra("book_title", title(a)).putExtra("segment", at).putExtra("body", text).putExtra("new", true));
        });
        Motion.touch(save); Motion.touch(full);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, (int) (48 * d), 1); left.rightMargin = (int) (10 * d);
        actions.addView(save, left); actions.addView(full, new LinearLayout.LayoutParams(0, (int) (48 * d), 1));
        panel.content.addView(actions);
        // This book's notes
        LinearLayout list = new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL);
        panel.content.addView(list);
        a.worker.execute(() -> {
            List<NotepadStore.Note> notes = NotepadStore.forBook(a, material(a), uri(a));
            notes.sort((x, y) -> Long.compare(y.updated, x.updated));
            a.ui(() -> {
                TextView h = new TextView(a); h.setText(AppLanguage.text(a, "Notas deste livro").toUpperCase(Locale.ROOT) + " · " + notes.size()); h.setTextSize(10); h.setTextColor(panel.muted); h.setLetterSpacing(.1f); h.setPadding((int) (3 * d), (int) (15 * d), 0, (int) (6 * d)); list.addView(h);
                if (notes.isEmpty()) { TextView e = new TextView(a); e.setText(AppLanguage.text(a, "Ainda não há notas deste livro. As que escrever aqui aparecem também no Bloco de notas.")); e.setTextSize(13); e.setTextColor(panel.muted); e.setPadding((int) (3 * d), 0, (int) (3 * d), (int) (8 * d)); list.addView(e); }
                for (int i = 0; i < Math.min(30, notes.size()); i++) {
                    NotepadStore.Note n = notes.get(i);
                    LinearLayout card = new LinearLayout(a); card.setOrientation(LinearLayout.VERTICAL); card.setPadding((int) (14 * d), (int) (11 * d), (int) (14 * d), (int) (11 * d)); card.setBackground(panel.shape(ThemeColors.blend(panel.surface, n.color == 0 ? panel.ink : n.color, n.color == 0 ? .04f : .18f), 14));
                    TextView t = new TextView(a); t.setText(n.shownTitle()); t.setTextSize(15); t.setTypeface(Typeface.DEFAULT_BOLD); t.setTextColor(panel.ink); t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); card.addView(t);
                    String preview = n.locked ? AppLanguage.text(a, "Nota bloqueada") : NotepadText.plain(n.body).replaceAll("\\s+", " ").trim();
                    if (!preview.isEmpty()) { TextView p = new TextView(a); p.setText(preview); p.setTextSize(13); p.setTextColor(panel.muted); p.setMaxLines(2); p.setEllipsize(TextUtils.TruncateAt.END); card.addView(p); }
                    TextView when = new TextView(a); when.setText((n.segment > 0 ? where(a, n.segment) + " · " : "") + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(n.updated))); when.setTextSize(11); when.setTextColor(panel.muted); when.setPadding(0, (int) (4 * d), 0, 0); card.addView(when);
                    card.setContentDescription(n.shownTitle());
                    card.setOnClickListener(v -> { panel.dialog.dismiss(); a.startActivity(new android.content.Intent(a, NotepadActivity.class).putExtra("note", n.id)); });
                    card.setOnLongClickListener(v -> {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty() || n.locked) return false;
                        panel.dialog.dismiss();
                        a.worker.execute(() -> { NotepadStore.Note fresh = NotepadStore.load(a, n.id); if (fresh == null) return; fresh.body = fresh.body + (fresh.body.endsWith("\n") || fresh.body.isEmpty() ? "" : "\n") + "\n" + text; NotepadStore.save(a, fresh); a.ui(() -> PlusUi.toast(a, AppLanguage.text(a, "Acrescentado a") + " " + n.shownTitle())); });
                        return true;
                    });
                    Motion.touch(card);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = (int) (7 * d); list.addView(card, lp);
                }
                if (!notes.isEmpty()) { TextView tip = new TextView(a); tip.setText(AppLanguage.text(a, "Toque para abrir. Toque longo para acrescentar o que escreveu acima a essa nota.")); tip.setTextSize(11.5f); tip.setTextColor(panel.muted); tip.setPadding((int) (3 * d), 0, 0, (int) (6 * d)); list.addView(tip); }
                // Earlier ways of taking notes
                panel.section("Mais");
                long id = material(a);
                if (id > 0) panel.action("course", "Nota de estudo desta página", AppLanguage.text(a, "Em Estudos → Anotações"), false, () -> StudyCapture.form(a, id, at, "", 0));
                panel.action("edit", "Anotar uma passagem do texto", null, false, () -> { if (a.kind.equals("pdf") && !a.tools.pdfText) a.tools.pageText(at); else if (a.tools.ready()) a.tools.note(); });
                panel.action("contents", "Anotações do documento", null, false, () -> a.tools.documentAnnotations());
            });
        });
        panel.show();
    }
    static NotepadStore.Note newNote(ReaderActivity a, int at) {
        NotepadStore.Note n = new NotepadStore.Note();
        n.material = material(a); n.segment = at;
        if (n.material > 0) n.bookTitle = title(a); else { n.source = uri(a); n.sourceTitle = title(a); }
        return n;
    }
}
