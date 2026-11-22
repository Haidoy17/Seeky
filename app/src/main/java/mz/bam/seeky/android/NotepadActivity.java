package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bloco de notas: notes with folders, colours, pins, tags, tasks, light formatting, dictation,
 * reading aloud, sharing and export, and a recycle bin. Bible references written in a note are
 * recognised: they become links that open the passage, can bring the verse text into the note and
 * can be saved to the Bible study. Notes live in Estudos too, linked to a study module and a book.
 */
public final class NotepadActivity extends Activity {
    static final int DICTATE = 51, EXPORT = 52;
    Screens s;
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    String screen = "list", filter = "all", query = "", sort = "updated";
    List<NotepadStore.Note> notes = new ArrayList<>();
    LinearLayout listBox;
    // Editor
    NotepadStore.Note note;
    EditText title, body;
    TextView reading, info;
    LinearLayout passages, readBox, editBox, readChip;
    boolean dirty, readMode, applying;
    final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    String lastSnapshot = "";
    TextToSpeech voice;
    final Runnable autosave = this::saveNow, refreshPassages = this::scanPassages;

    static void open(Context c) { Intent i = new Intent(c, NotepadActivity.class); if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
    /** A new note that starts with a passage (reference and text), e.g. from the passage viewer. */
    static void withPassage(Context c, String reference, String text, String version) { Intent i = new Intent(c, NotepadActivity.class).putExtra("passage_title", reference).putExtra("passage_text", text).putExtra("passage_version", version); if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
    /** A new note linked to a book (and part), e.g. from the reader or the bookcase. */
    static void forBook(Context c, long material, int part, String text) { Intent i = new Intent(c, NotepadActivity.class).putExtra("material", material).putExtra("segment", part).putExtra("text", text == null ? "" : text); if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        s = new Screens(this);
        sort = getSharedPreferences("notepad", 0).getString("sort", "updated");
        Intent i = getIntent();
        long id = i.getLongExtra("note", 0);
        if (id > 0) edit(id);
        else if (i.hasExtra("passage_title") || i.hasExtra("material") || i.hasExtra("text")) {
            NotepadStore.Note n = new NotepadStore.Note();
            if (i.hasExtra("passage_title")) { n.title = i.getStringExtra("passage_title"); n.body = NotepadText.passage(i.getStringExtra("passage_title"), i.getStringExtra("passage_text") == null ? "" : i.getStringExtra("passage_text"), i.getStringExtra("passage_version")) + "\n\n"; n.folder = AppLanguage.text(this, "Estudo bíblico"); }
            n.material = i.getLongExtra("material", 0); n.segment = i.getIntExtra("segment", 0);
            String text = i.getStringExtra("text"); if (text != null && !text.trim().isEmpty()) n.body = "> " + text.trim().replace("\n", "\n> ") + "\n\n";
            editor(n);
        } else list();
    }
    @Override protected void onPause() { super.onPause(); if (screen.equals("edit")) saveNow(); }
    @Override protected void onDestroy() { PlusUi.MAIN.removeCallbacks(autosave); if (voice != null) voice.shutdown(); worker.shutdown(); super.onDestroy(); }
    @Override public void onBackPressed() { if (screen.equals("edit") || screen.equals("trash")) { if (screen.equals("edit")) saveNow(); list(); } else super.onBackPressed(); }
    void toast(String t) { PlusUi.toast(this, t); }
    String tr(String t) { return AppLanguage.text(this, t); }

    // ---------------------------------------------------------------- list
    void list() {
        screen = "list";
        LinearLayout content = s.page();
        content.addView(s.header("Bloco de notas", "Notas, tarefas e estudos · passagens bíblicas reconhecidas", this::finish));
        LinearLayout searchRow = s.row();
        EditText search = new EditText(this); search.setHint(tr("Procurar nas notas")); search.setSingleLine(true); search.setText(query); search.setTextColor(s.p.ink); search.setHintTextColor(s.p.muted); search.setTextSize(15);
        search.setBackground(s.round(s.p.surface, 16)); search.setPadding(s.dp(14), s.dp(10), s.dp(14), s.dp(10));
        search.addTextChangedListener(new SimpleWatcher(t -> { query = t; drawList(); }));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, s.dp(50), 1));
        FrameLayout add = new FrameLayout(this); add.setBackground(s.round(s.p.accent, 16)); add.addView(new Icons(this, "add", s.p.onAccent), new FrameLayout.LayoutParams(s.dp(24), s.dp(24), Gravity.CENTER)); add.setContentDescription(tr("Nova nota")); add.setOnClickListener(v -> newNote()); Motion.touch(add);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(s.dp(50), s.dp(50)); ap.leftMargin = s.dp(8); searchRow.addView(add, ap);
        content.addView(searchRow);
        LinearLayout filters = s.row();
        content.addView(s.scroller(filters), s.gap(10));
        listBox = s.column();
        content.addView(listBox, s.gap(6));
        worker.execute(() -> {
            List<NotepadStore.Note> all = NotepadStore.all(this);
            List<String> folders; try (NotepadStore pad = new NotepadStore(this)) { folders = pad.folders(); }
            PlusUi.post(this, () -> {
                if (!screen.equals("list")) return;
                notes = all;
                String[][] base = {{"all", "Todas"}, {"pinned", "Fixadas"}, {"tasks", "Tarefas"}, {"bible", "Com passagens"}, {"books", "Ligadas a livros"}};
                for (String[] f : base) s.addChip(filters, s.chip(null, f[1], filter.equals(f[0]), () -> { filter = f[0]; list(); }));
                for (String f : folders) s.addChip(filters, s.chip("folder", f, filter.equals("folder:" + f), () -> { filter = "folder:" + f; list(); }));
                s.addChip(filters, s.chip("filters", sort.equals("title") ? "A–Z" : sort.equals("created") ? "Criadas" : "Recentes", false, this::chooseSort));
                s.addChip(filters, s.chip("trash", "Lixo", false, this::trash));
                drawList();
            });
        });
    }
    void chooseSort() {
        String[] keys = {"updated", "created", "title"}, names = {tr("Editadas recentemente"), tr("Criadas recentemente"), tr("Título (A–Z)")};
        new UiDialog.Builder(this).setTitle("Ordenar notas").setItems(names, (d, w) -> { sort = keys[w]; getSharedPreferences("notepad", 0).edit().putString("sort", sort).apply(); list(); }).show();
    }
    boolean matches(NotepadStore.Note n) {
        if (filter.equals("pinned") && !n.pinned) return false;
        if (filter.equals("tasks") && NotepadText.tasks(n.body)[1] == 0) return false;
        if (filter.equals("bible") && BibleReferences.references(n.title + "\n" + n.body).isEmpty()) return false;
        if (filter.equals("books") && n.material <= 0) return false;
        if (filter.startsWith("folder:") && !n.folder.equals(filter.substring(7))) return false;
        if (!query.trim().isEmpty()) { String q = BibleGame.plain(query); String all = BibleGame.plain(n.title + " " + n.body + " " + n.tags + " " + n.folder + " " + n.bookTitle); for (String w : q.split(" ")) if (!all.contains(w)) return false; }
        return true;
    }
    void drawList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        List<NotepadStore.Note> shown = new ArrayList<>();
        for (NotepadStore.Note n : notes) if (matches(n)) shown.add(n);
        Comparator<NotepadStore.Note> order = sort.equals("title") ? (x, y) -> x.shownTitle().compareToIgnoreCase(y.shownTitle()) : sort.equals("created") ? (x, y) -> Long.compare(y.created, x.created) : (x, y) -> Long.compare(y.updated, x.updated);
        shown.sort((x, y) -> x.pinned != y.pinned ? (x.pinned ? -1 : 1) : order.compare(x, y));
        TextView count = s.text(shown.size() + " " + tr(shown.size() == 1 ? "nota" : "notas"), 13, s.p.muted, true);
        count.setPadding(s.dp(4), s.dp(6), 0, s.dp(6)); listBox.addView(count);
        if (shown.isEmpty()) {
            LinearLayout empty = s.card();
            empty.addView(s.text(notes.isEmpty() ? "Comece o seu bloco de notas" : "Nenhuma nota encontrada", 18, s.p.ink, true));
            empty.addView(s.text(notes.isEmpty() ? "Escreva ideias, sermões, estudos e tarefas. Se escrever uma passagem como \"João 3:16\" ou \"Is 40.31\", o Seeky reconhece-a: toque para a ler, traga o texto para a nota ou guarde-a no estudo bíblico." : "Tente outra palavra ou outro filtro.", 14, s.p.muted, false));
            empty.addView(s.button("Nova nota", true, this::newNote), s.gap(12));
            listBox.addView(empty, s.gap(4));
            return;
        }
        for (NotepadStore.Note n : shown) listBox.addView(card(n), s.gap(10));
    }
    View card(NotepadStore.Note n) {
        LinearLayout c = s.card();
        GradientDrawable bg = s.round(n.color == 0 ? s.p.surface : blend(s.p.surface, n.color, s.p.light ? .55f : .25f), 20);
        c.setBackground(bg);
        LinearLayout top = s.row();
        TextView t = s.text(n.shownTitle(), 17, s.p.ink, true); t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (n.pinned) top.addView(new Icons(this, "pin", s.p.accent), new LinearLayout.LayoutParams(s.dp(18), s.dp(18)));
        c.addView(top);
        String preview = NotepadText.plain(n.body);
        if (n.title.trim().isEmpty() || preview.startsWith(n.shownTitle())) { int nl = preview.indexOf('\n'); preview = nl < 0 ? "" : preview.substring(nl + 1).trim(); }
        if (!preview.isEmpty()) { TextView p = s.text(preview, 14, s.p.muted, false); p.setMaxLines(3); p.setEllipsize(TextUtils.TruncateAt.END); p.setPadding(0, s.dp(4), 0, 0); c.addView(p); }
        List<String> bits = new ArrayList<>();
        bits.add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(n.updated)));
        int[] tasks = NotepadText.tasks(n.body); if (tasks[1] > 0) bits.add("☑ " + tasks[0] + "/" + tasks[1]);
        int refs = BibleReferences.references(n.body).size(); if (refs > 0) bits.add("📖 " + refs);
        if (!n.folder.isEmpty()) bits.add(n.folder);
        if (!n.bookTitle.isEmpty()) bits.add("📚 " + n.bookTitle);
        TextView meta = s.text(String.join(" · ", bits), 12, s.p.muted, false); meta.setPadding(0, s.dp(8), 0, 0); meta.setMaxLines(2); c.addView(meta);
        c.setContentDescription(n.shownTitle());
        c.setOnClickListener(v -> edit(n.id));
        c.setOnLongClickListener(v -> { noteMenu(n); return true; });
        Motion.touch(c);
        return c;
    }
    static int blend(int a, int b, float t) { return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t)); }
    void noteMenu(NotepadStore.Note n) {
        UiPanel p = new UiPanel(this, n.shownTitle(), null, "note");
        p.action("edit", "Abrir", null, false, () -> edit(n.id));
        p.action("pin", n.pinned ? "Desafixar" : "Fixar no topo", null, false, () -> update(n, () -> n.pinned = !n.pinned));
        p.action("palette", "Cor", NotepadStore.COLOR_NAMES[colorIndex(n.color)], false, () -> chooseColor(n.color, c -> update(n, () -> n.color = c)));
        p.action("folder", "Pasta", n.folder.isEmpty() ? tr("Sem pasta") : n.folder, false, () -> chooseFolder(n.folder, f -> update(n, () -> n.folder = f)));
        p.action("copy", "Duplicar", null, false, () -> worker.execute(() -> { NotepadStore.Note copy = NotepadStore.load(this, n.id); if (copy == null) return; copy.id = 0; copy.created = 0; copy.title = n.shownTitle() + " (" + tr("cópia") + ")"; NotepadStore.save(this, copy); PlusUi.post(this, this::list); }));
        p.action("share", "Partilhar", null, false, () -> share(n.shownTitle(), n.body));
        p.danger("Mover para o lixo", () -> worker.execute(() -> { try { NotepadStore.trash(this, n.id); } catch (Exception e) { AppDiagnostics.record(this, "notepad-trash", e); } PlusUi.post(this, () -> { toast("Nota movida para o lixo."); list(); }); }));
        p.show();
    }
    static int colorIndex(int color) { for (int i = 0; i < NotepadStore.COLORS.length; i++) if (NotepadStore.COLORS[i] == color) return i; return 0; }
    void update(NotepadStore.Note n, Runnable change) { change.run(); worker.execute(() -> { NotepadStore.Note fresh = NotepadStore.load(this, n.id); if (fresh == null) return; fresh.pinned = n.pinned; fresh.color = n.color; fresh.folder = n.folder; NotepadStore.save(this, fresh); PlusUi.post(this, this::list); }); }
    interface Chosen<T> { void take(T value); }
    void chooseColor(int current, Chosen<Integer> done) {
        UiPanel p = new UiPanel(this, "Cor da nota", null, "palette");
        for (int i = 0; i < NotepadStore.COLORS.length; i++) { final int c = NotepadStore.COLORS[i]; p.action(c == current ? "check" : "palette", NotepadStore.COLOR_NAMES[i], null, false, () -> done.take(c)); }
        p.show();
    }
    void chooseFolder(String current, Chosen<String> done) {
        worker.execute(() -> {
            List<String> folders; try (NotepadStore pad = new NotepadStore(this)) { folders = pad.folders(); }
            for (String d : new String[]{tr("Estudo bíblico"), tr("Sermões"), tr("Pessoal"), tr("Trabalho")}) if (!folders.contains(d)) folders.add(d);
            PlusUi.post(this, () -> {
                UiPanel p = new UiPanel(this, "Pasta", null, "folder");
                p.action("close", "Sem pasta", null, false, () -> done.take(""));
                for (String f : folders) p.action(f.equals(current) ? "check" : "folder", f, null, false, () -> done.take(f));
                p.action("add", "Nova pasta…", null, false, () -> {
                    EditText input = PlusUi.input(this, "Nome da pasta", "", 40);
                    new UiDialog.Builder(this).setTitle("Nova pasta").setView(input).setPositiveButton("Guardar", (d, w) -> { String name = input.getText().toString().trim(); if (!name.isEmpty()) done.take(name); }).setNegativeButton("Cancelar", null).show();
                });
                p.show();
            });
        });
    }
    void newNote() {
        UiPanel p = new UiPanel(this, "Nova nota", tr("Em branco ou a partir de um modelo"), "add");
        p.action("note", "Nota em branco", null, false, () -> editor(new NotepadStore.Note()));
        for (String[] t : NotepadText.TEMPLATES) p.action(t[0].startsWith("Lista") ? "check" : t[0].contains("bíblico") || t[0].contains("Devocional") ? "book" : "note", t[0], null, false, () -> { NotepadStore.Note n = new NotepadStore.Note(); n.body = t[1]; if (t[0].contains("bíblico") || t[0].contains("Devocional") || t[0].contains("Sermão")) n.folder = tr(t[0].contains("Sermão") ? "Sermões" : "Estudo bíblico"); editor(n); });
        p.show();
    }

    // ---------------------------------------------------------------- trash
    void trash() {
        screen = "trash";
        LinearLayout content = s.page();
        content.addView(s.header("Lixo", "As notas ficam aqui até as restaurar ou esvaziar o lixo", this::list));
        LinearLayout box = s.column(); content.addView(box);
        worker.execute(() -> {
            List<NotepadStore.Trashed> items = NotepadStore.trashed(this);
            PlusUi.post(this, () -> {
                if (!screen.equals("trash")) return;
                if (items.isEmpty()) { box.addView(s.text("O lixo está vazio.", 15, s.p.muted, false)); return; }
                for (NotepadStore.Trashed t : items) {
                    box.addView(s.entry("note", t.title, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(t.deleted)), () -> {
                        UiPanel p = new UiPanel(this, t.title, null, "trash");
                        p.action("restore", "Restaurar", null, false, () -> worker.execute(() -> { try { NotepadStore.restore(this, t.id); } catch (Exception e) { AppDiagnostics.record(this, "notepad-restore", e); } PlusUi.post(this, () -> { toast("Nota restaurada."); trash(); }); }));
                        p.danger("Apagar para sempre", () -> worker.execute(() -> { NotepadStore.forget(this, t.id); PlusUi.post(this, this::trash); }));
                        p.show();
                    }), s.gap(8));
                }
                box.addView(s.button("Esvaziar o lixo", false, () -> new UiDialog.Builder(this).setTitle("Esvaziar o lixo?").setMessage("As notas do lixo serão apagadas para sempre.").setPositiveButton("Esvaziar", (d, w) -> worker.execute(() -> { NotepadStore.forget(this, 0); PlusUi.post(this, this::trash); })).setNegativeButton("Cancelar", null).show()), s.gap(16));
            });
        });
    }

    // ---------------------------------------------------------------- editor
    void edit(long id) { worker.execute(() -> { NotepadStore.Note n = NotepadStore.load(this, id); PlusUi.post(this, () -> { if (n == null) { toast("Esta nota já não existe."); list(); } else editor(n); }); }); }
    void editor(NotepadStore.Note n) {
        screen = "edit"; note = n; dirty = false; readMode = n.id > 0 && !n.body.trim().isEmpty() && getSharedPreferences("notepad", 0).getBoolean("open_reading", false); undo.clear(); redo.clear(); lastSnapshot = n.body;
        LinearLayout content = s.page();
        content.addView(s.header(n.id > 0 ? "Nota" : "Nova nota", null, () -> { saveNow(); list(); }));
        title = new EditText(this); title.setHint(tr("Título")); title.setText(n.title); title.setTextSize(22); title.setTypeface(ThemeStyle.SERIF_BOLD); title.setTextColor(s.p.ink); title.setHintTextColor(s.p.muted); title.setBackground(null); title.setPadding(s.dp(4), 0, s.dp(4), s.dp(4));
        title.addTextChangedListener(new SimpleWatcher(t -> changed()));
        content.addView(title);
        info = s.text("", 12, s.p.muted, false); info.setPadding(s.dp(4), 0, 0, s.dp(8)); content.addView(info);
        LinearLayout tools = s.row(); content.addView(s.scroller(tools));
        String[][] buttons = {{"type", "Ver", "read"}, {"type", "Negrito", "bold"}, {"type", "Itálico", "italic"}, {"type", "Título", "heading"}, {"queue", "Lista", "list"}, {"check", "Tarefa", "task"}, {"edit", "Destacar", "mark"}, {"book", "Passagem", "passage"}, {"calendar", "Data", "date"}, {"mic", "Ditar", "dictate"}, {"speaker", "Ouvir", "listen"}, {"back", "Desfazer", "undo"}, {"refresh", "Refazer", "redo"}};
        for (String[] b : buttons) { LinearLayout chip = s.chip(b[0], b[1], false, () -> tool(b[2])); if (b[2].equals("read")) readChip = chip; s.addChip(tools, chip); }
        editBox = s.column();
        body = new EditText(this); body.setText(n.body); body.setHint(tr("Escreva aqui… Passagens como João 3:16 são reconhecidas.")); body.setGravity(Gravity.TOP); body.setMinLines(10); body.setTextSize(17); body.setLineSpacing(s.dp(3), 1.05f); body.setTextColor(s.p.ink); body.setHintTextColor(s.p.muted);
        body.setBackground(s.round(n.color == 0 ? s.p.surface : blend(s.p.surface, n.color, s.p.light ? .35f : .2f), 18)); body.setPadding(s.dp(16), s.dp(14), s.dp(16), s.dp(14));
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addTextChangedListener(new SimpleWatcher(t -> { if (!applying) { remember(); changed(); } }));
        editBox.addView(body, new LinearLayout.LayoutParams(-1, -2));
        content.addView(editBox, s.gap(10));
        readBox = s.column();
        reading = s.text("", 17, s.p.ink, false); reading.setLineSpacing(s.dp(4), 1.08f); reading.setTextIsSelectable(false); reading.setPadding(s.dp(16), s.dp(14), s.dp(16), s.dp(14)); reading.setBackground(s.round(s.p.surface, 18));
        readBox.addView(reading);
        TextView hint = s.text("Toque numa passagem para a ler, ou numa tarefa para a marcar. \"Editar\" volta a escrever.", 12, s.p.muted, false); hint.setPadding(s.dp(4), s.dp(6), 0, 0); readBox.addView(hint);
        content.addView(readBox, s.gap(10));
        content.addView(s.section("Passagens nesta nota"));
        passages = s.column(); content.addView(passages);
        content.addView(s.section("Ligações e organização"));
        LinearLayout links = s.column(); content.addView(links);
        links.addView(s.entry("book", n.bookTitle.isEmpty() ? tr("Ligar a um livro") : n.bookTitle, n.material > 0 ? tr("Toque para abrir o livro · toque longo para mudar") : tr("A nota aparece com o livro nos Estudos"), this::bookLink), s.gap(0));
        links.getChildAt(links.getChildCount() - 1).setOnLongClickListener(v -> { chooseBook(); return true; });
        links.addView(s.entry("course", n.subjectTitle.isEmpty() ? tr("Ligar a um módulo de estudo") : n.subjectTitle, tr("Também fica em Estudos → Anotações"), this::chooseSubject), s.gap(8));
        links.addView(s.entry("folder", n.folder.isEmpty() ? tr("Pasta") : n.folder, tr("Organize as notas por pastas"), () -> chooseFolder(note.folder, f -> { note.folder = f; changed(); editorRefresh(); })), s.gap(8));
        links.addView(s.entry("collection", n.tags.isEmpty() ? tr("Etiquetas") : n.tags, tr("Por exemplo: oração, família, fé"), this::editTags), s.gap(8));
        LinearLayout actions = s.row();
        s.addChip(actions, s.chip("pin", n.pinned ? "Fixada" : "Fixar", n.pinned, () -> { note.pinned = !note.pinned; changed(); editorRefresh(); }));
        s.addChip(actions, s.chip("palette", "Cor", false, () -> chooseColor(note.color, c -> { note.color = c; changed(); editorRefresh(); })));
        s.addChip(actions, s.chip("share", "Partilhar", false, () -> share(currentTitle(), body.getText().toString())));
        s.addChip(actions, s.chip("document", "Exportar", false, this::export));
        s.addChip(actions, s.chip("copy", "Copiar", false, () -> { ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(currentTitle(), NotepadText.plain(body.getText().toString()))); toast("Nota copiada."); }));
        s.addChip(actions, s.chip("trash", "Lixo", false, this::trashCurrent));
        content.addView(s.scroller(actions), s.gap(16));
        showMode();
        updateInfo();
        PlusUi.MAIN.post(refreshPassages);
        if (n.id == 0 && n.body.isEmpty()) body.requestFocus();
    }
    /** Re-opens the editor with the current state (after colour, pin, links). */
    void editorRefresh() { saveNow(); NotepadStore.Note n = note; PlusUi.MAIN.post(() -> editor(n)); }
    String currentTitle() { return NotepadText.title(title.getText().toString(), body.getText().toString()); }
    void changed() { dirty = true; PlusUi.MAIN.removeCallbacks(autosave); PlusUi.MAIN.postDelayed(autosave, 1500); PlusUi.MAIN.removeCallbacks(refreshPassages); PlusUi.MAIN.postDelayed(refreshPassages, 700); updateInfo(); }
    void updateInfo() {
        if (info == null || body == null) return;
        String text = body.getText().toString();
        int[] tasks = NotepadText.tasks(text);
        List<String> bits = new ArrayList<>();
        bits.add(NotepadText.words(text) + " " + tr("palavras")); bits.add("~" + NotepadText.minutes(text) + " min");
        if (tasks[1] > 0) bits.add("☑ " + tasks[0] + "/" + tasks[1]);
        if (!note.folder.isEmpty()) bits.add(note.folder);
        if (note.updated > 0) bits.add(tr(dirty ? "a guardar…" : "guardada") + " " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(note.updated)));
        info.setText(String.join(" · ", bits));
    }
    void saveNow() {
        PlusUi.MAIN.removeCallbacks(autosave);
        if (note == null || title == null || body == null) return;
        String t = title.getText().toString(), b = body.getText().toString();
        if (!dirty && note.id > 0) return;
        if (note.id == 0 && t.trim().isEmpty() && b.trim().isEmpty()) return;
        note.title = t; note.body = b;
        final NotepadStore.Note n = note;
        dirty = false;
        worker.execute(() -> { NotepadStore.save(this, n); PlusUi.post(this, this::updateInfo); });
    }
    void remember() {
        String now = body.getText().toString();
        if (now.equals(lastSnapshot)) return;
        if (Math.abs(now.length() - lastSnapshot.length()) >= 1) { undo.push(lastSnapshot); if (undo.size() > 100) undo.removeLast(); redo.clear(); }
        lastSnapshot = now;
    }
    void setBody(String text, int start, int end) {
        applying = true; body.setText(text); body.setSelection(Math.max(0, Math.min(start, text.length())), Math.max(0, Math.min(end, text.length()))); applying = false; lastSnapshot = text; changed(); if (readMode) render();
    }
    void tool(String what) {
        String text = body.getText().toString();
        int a = Math.max(0, body.getSelectionStart()), b = Math.max(0, body.getSelectionEnd());
        NotepadText.Edit e = null;
        switch (what) {
            case "read": readMode = !readMode; showMode(); return;
            case "bold": e = NotepadText.wrap(text, a, b, "**"); break;
            case "italic": e = NotepadText.wrap(text, a, b, "*"); break;
            case "mark": e = NotepadText.wrap(text, a, b, "=="); break;
            case "heading": e = NotepadText.prefix(text, a, b, "# "); break;
            case "list": e = NotepadText.prefix(text, a, b, "- "); break;
            case "task": e = NotepadText.prefix(text, a, b, "[ ] "); break;
            case "date": e = NotepadText.insert(text, b, DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(new Date()), false); break;
            case "passage": askPassage(); return;
            case "dictate": dictate(); return;
            case "listen": listen(); return;
            case "undo": if (undo.isEmpty()) { toast("Nada para desfazer."); return; } redo.push(text); String u = undo.pop(); setBody(u, u.length(), u.length()); return;
            case "redo": if (redo.isEmpty()) { toast("Nada para refazer."); return; } undo.push(text); String r = redo.pop(); setBody(r, r.length(), r.length()); return;
        }
        if (e != null) { undo.push(text); redo.clear(); if (readMode) { readMode = false; showMode(); } setBody(e.text, e.start, e.end); body.requestFocus(); }
    }
    void showMode() {
        if (readChip != null && readChip.getChildCount() > 1) { ((TextView) readChip.getChildAt(1)).setText(tr(readMode ? "Editar" : "Ver")); readChip.setContentDescription(tr(readMode ? "Editar" : "Ver")); }
        editBox.setVisibility(readMode ? View.GONE : View.VISIBLE);
        readBox.setVisibility(readMode ? View.VISIBLE : View.GONE);
        if (readMode) { InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (im != null) im.hideSoftInputFromWindow(body.getWindowToken(), 0); render(); }
    }
    /** Reading view: headings, bold, italic, highlights, tasks to tick and Bible passages to tap. */
    void render() {
        String text = body.getText().toString();
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final int lineNo = i;
            String line = lines[i];
            int start = out.length();
            if (line.matches("^#{1,3}\\s.*")) {
                int level = line.indexOf(' ');
                out.append(line.substring(level + 1));
                out.setSpan(new RelativeSizeSpan(level == 1 ? 1.35f : level == 2 ? 1.2f : 1.1f), start, out.length(), 0);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), 0);
            } else if (line.matches("^\\s*\\[[ xX]\\].*")) {
                boolean done = !line.matches("^\\s*\\[ \\].*");
                out.append(done ? "☑ " : "☐ ");
                out.setSpan(new ClickableSpan() { @Override public void onClick(View v) { String t = NotepadText.toggle(body.getText().toString(), lineNo); undo.push(body.getText().toString()); setBody(t, t.length(), t.length()); } @Override public void updateDrawState(TextPaint ds) { ds.setColor(s.p.accent); ds.setUnderlineText(false); } }, start, start + 2, 0);
                int textStart = out.length();
                out.append(line.replaceFirst("^\\s*\\[[ xX]\\]\\s?", ""));
                if (done) { out.setSpan(new StrikethroughSpan(), textStart, out.length(), 0); out.setSpan(new ForegroundColorSpan(s.p.muted), textStart, out.length(), 0); }
            } else if (line.matches("^\\s*[-•*]\\s.*")) {
                out.append("•  ").append(line.replaceFirst("^\\s*[-•*]\\s", ""));
            } else if (line.startsWith(">")) {
                out.append(line.replaceFirst("^>\\s?", ""));
                out.setSpan(new QuoteSpan(s.p.accent), start, out.length(), 0);
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), 0);
            } else out.append(line);
            inline(out, start);
            if (i < lines.length - 1) out.append('\n');
        }
        reading.setText(out, TextView.BufferType.SPANNABLE);
        reading.setMovementMethod(LinkMovementMethod.getInstance());
        BibleLinks.linkify(this, reading, s.p.accent, worker, true);
    }
    /** **negrito**, *itálico* and ==destaque== inside the line just added. */
    void inline(SpannableStringBuilder out, int from) {
        String[][] marks = {{"**", "b"}, {"==", "m"}, {"*", "i"}};
        for (String[] m : marks) {
            int at = from;
            while (true) {
                String all = out.toString();
                int open = all.indexOf(m[0], at); if (open < 0) break;
                int close = all.indexOf(m[0], open + m[0].length()); if (close < 0 || all.substring(open, close).contains("\n")) break;
                if (m[0].equals("*") && close == open + 1) { at = close + 1; continue; }
                out.delete(close, close + m[0].length()); out.delete(open, open + m[0].length());
                int end = close - m[0].length();
                Object span = m[1].equals("b") ? new StyleSpan(Typeface.BOLD) : m[1].equals("i") ? new StyleSpan(Typeface.ITALIC) : new BackgroundColorSpan((s.p.accent & 0xffffff) | 0x40000000);
                out.setSpan(span, open, end, 0);
                at = end;
            }
        }
    }
    /** Lists the passages written in the note, each one to read, bring into the note or keep in the Bible study. */
    void scanPassages() {
        if (passages == null || body == null) return;
        final String text = title.getText().toString() + "\n" + body.getText().toString();
        worker.execute(() -> {
            List<BibleReferences.Reference> refs = BibleReferences.references(text);
            PlusUi.post(this, () -> {
                if (!screen.equals("edit")) return;
                passages.removeAllViews();
                if (refs.isEmpty()) { TextView t = s.text("Escreva uma referência (por exemplo Romanos 8:28 ou Salmo 23) e ela aparece aqui.", 13, s.p.muted, false); t.setPadding(s.dp(4), 0, 0, 0); passages.addView(t); return; }
                LinkedHashMap<String, BibleReferences.Reference> unique = new LinkedHashMap<>();
                for (BibleReferences.Reference r : refs) unique.putIfAbsent(r.title, r);
                LinearLayout row = s.row();
                for (BibleReferences.Reference r : unique.values()) s.addChip(row, s.chip("book", r.title, false, () -> passageMenu(r)));
                passages.addView(s.scroller(row));
            });
        });
    }
    void passageMenu(BibleReferences.Reference r) {
        UiPanel p = new UiPanel(this, r.title, null, "book");
        p.action("book", "Ler a passagem", tr("Leitura rápida, capítulo, versões"), false, () -> BibleLinks.open(this, r));
        p.action("add", "Trazer o texto para a nota", tr("Da Bíblia importada"), false, () -> insertPassage(r));
        p.action("bookmark", "Guardar no estudo bíblico", tr("Passagens guardadas e decorar"), false, () -> worker.execute(() -> {
            BibleStudyStore.Verses v = new BibleStudyStore.Verses(this); String t = v.text(r);
            BibleLinks.save(this, r, v.version(), t);
            PlusUi.post(this, () -> toast(t.isEmpty() ? "Guardada (importe uma Bíblia para ver o texto)." : "Guardada no estudo bíblico."));
        }));
        p.action("calendar", "Abrir o estudo bíblico", null, false, () -> BibleStudyActivity.open(this));
        p.show();
    }
    void askPassage() {
        EditText input = PlusUi.input(this, "Por exemplo: João 3:16-18 ou Salmo 23", "", 80);
        new UiDialog.Builder(this).setTitle("Inserir passagem").setView(input).setPositiveButton("Inserir", (d, w) -> {
            BibleReferences.Reference r = BibleReferences.lookup(input.getText().toString());
            if (r == null) { toast("Não reconheci a passagem. Escreva o livro, o capítulo e o versículo."); return; }
            insertPassage(r);
        }).setNegativeButton("Cancelar", null).show();
    }
    void insertPassage(BibleReferences.Reference r) {
        worker.execute(() -> {
            BibleStudyStore.Verses v = new BibleStudyStore.Verses(this);
            String text = v.text(r), version = v.version();
            PlusUi.post(this, () -> {
                if (!screen.equals("edit")) return;
                if (readMode) { readMode = false; showMode(); }
                String block = text.isEmpty() ? "> " + r.title : NotepadText.passage(r.title, text, version);
                String current = body.getText().toString();
                int at = body.getSelectionEnd() < 0 ? current.length() : body.getSelectionEnd();
                undo.push(current);
                NotepadText.Edit e = NotepadText.insert(current, at, block, true);
                setBody(e.text, e.start, e.end);
                if (text.isEmpty()) toast("Importe uma Bíblia (Estudo bíblico) para trazer o texto dos versículos.");
            });
        });
    }
    void dictate() {
        try { startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.english(this) ? "en-US" : "pt-PT").putExtra(RecognizerIntent.EXTRA_PROMPT, tr("Fale; o texto entra na nota")), DICTATE); }
        catch (Exception e) { toast("Este telefone não tem reconhecimento de voz."); }
    }
    void listen() {
        String text = NotepadText.plain(currentTitle() + "\n" + body.getText().toString());
        if (voice != null && voice.isSpeaking()) { voice.stop(); return; }
        if (voice == null) voice = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) { voice.setLanguage(AppLanguage.english(this) ? Locale.US : new Locale("pt", "PT")); speak(text); } else toast("A voz do telefone não está disponível."); });
        else speak(text);
    }
    void speak(String text) { voice.speak(text.length() > 3900 ? text.substring(0, 3900) : text, TextToSpeech.QUEUE_FLUSH, null, "notepad"); toast("A ler a nota. Toque outra vez em Ouvir para parar."); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == DICTATE && result == RESULT_OK && data != null && body != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard == null || heard.isEmpty()) return;
            String current = body.getText().toString(); int at = Math.max(0, body.getSelectionEnd());
            String add = (at > 0 && !Character.isWhitespace(current.charAt(at - 1)) ? " " : "") + heard.get(0);
            undo.push(current); NotepadText.Edit e = NotepadText.insert(current, at, add, false); setBody(e.text, e.start, e.end);
        }
        if (request == EXPORT && result == RESULT_OK && data != null && data.getData() != null) {
            Uri target = data.getData(); final String md = NotepadText.markdown(title.getText().toString(), body.getText().toString());
            worker.execute(() -> { try (OutputStream out = getContentResolver().openOutputStream(target)) { if (out == null) throw new java.io.IOException(); out.write(md.getBytes(StandardCharsets.UTF_8)); PlusUi.post(this, () -> toast("Nota exportada.")); } catch (Exception e) { PlusUi.post(this, () -> toast("Não foi possível exportar a nota.")); } });
        }
    }
    void export() {
        UiPanel p = new UiPanel(this, "Exportar", null, "document");
        p.action("document", "Ficheiro Markdown (.md)", tr("Abre em qualquer editor"), false, () -> createFile("text/markdown", ".md"));
        p.action("type", "Ficheiro de texto (.txt)", null, false, () -> createFile("text/plain", ".txt"));
        p.show();
    }
    void createFile(String mime, String ext) {
        try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, currentTitle().replaceAll("[\\\\/:*?\"<>|]", " ").trim() + ext), EXPORT); }
        catch (Exception e) { toast("Não há onde guardar ficheiros neste telefone."); }
    }
    void share(String name, String text) {
        try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, name).putExtra(Intent.EXTRA_TEXT, name + "\n\n" + NotepadText.plain(text)), tr("Partilhar nota"))); }
        catch (Exception e) { toast("Não há aplicações para partilhar."); }
    }
    void trashCurrent() {
        new UiDialog.Builder(this).setTitle("Mover para o lixo?").setMessage("Pode restaurá-la depois em Lixo.").setPositiveButton("Mover", (d, w) -> {
            final long id = note.id; dirty = false; PlusUi.MAIN.removeCallbacks(autosave);
            worker.execute(() -> { try { if (id > 0) NotepadStore.trash(this, id); } catch (Exception e) { AppDiagnostics.record(this, "notepad-trash", e); } PlusUi.post(this, () -> { toast("Nota movida para o lixo."); note = null; list(); }); });
        }).setNegativeButton("Cancelar", null).show();
    }
    void editTags() {
        EditText input = PlusUi.input(this, "Etiquetas separadas por vírgulas", note.tags, 200);
        new UiDialog.Builder(this).setTitle("Etiquetas").setView(input).setPositiveButton("Guardar", (d, w) -> { note.tags = String.join(", ", NotepadText.tags(input.getText().toString())); changed(); editorRefresh(); }).setNegativeButton("Cancelar", null).show();
    }
    void bookLink() { if (note.material > 0) openBook(); else chooseBook(); }
    void chooseBook() {
        PagedPicker.show(this, "Ligar a um livro", (q, offset, limit) -> {
            List<PagedPicker.Entry> rows = new ArrayList<>(); long total;
            try (StudyStore db = new StudyStore(this)) {
                String where = "(title LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\')"; String like = StudySchedule.like(q);
                total = db.count("materials", where, new String[]{like, like});
                for (StudyStore.Row r : db.page("materials", where, new String[]{like, like}, "updated DESC", limit, offset)) rows.add(new PagedPicker.Entry("" + r.id(), r.s("title"), r.s("author")));
            }
            return new PagedPicker.Page(rows, total);
        }, e -> { note.material = Long.parseLong(e.id); note.bookTitle = e.label; changed(); editorRefresh(); });
    }
    void openBook() {
        final long id = note.material; final int part = note.segment;
        worker.execute(() -> {
            StudyStore.Row r; try (StudyStore db = new StudyStore(this)) { r = db.get("materials", id); }
            PlusUi.post(this, () -> {
                if (r == null) { toast("O livro já não está disponível."); return; }
                Intent i = new Intent(this, ReaderActivity.class).setDataAndType(StudyFileProvider.uri(this, id), r.s("mime")).putExtra("name", r.s("name")).putExtra("study_title", r.s("title")).putExtra("study_material", id).putExtra("full_screen", true);
                if (part > 0) i.putExtra("study_segment", part);
                startActivity(i);
            });
        });
    }
    void chooseSubject() {
        worker.execute(() -> {
            List<StudyStore.Row> subjects; try (StudyStore db = new StudyStore(this)) { subjects = db.rows("subjects", null, null, "title COLLATE NOCASE", 200); }
            PlusUi.post(this, () -> {
                UiPanel p = new UiPanel(this, "Módulo de estudo", tr("A nota aparece nas Anotações deste módulo"), "course");
                p.action("close", "Sem módulo", null, false, () -> { note.subject = 0; note.subjectTitle = ""; changed(); editorRefresh(); });
                for (StudyStore.Row r : subjects) p.action(r.id() == note.subject ? "check" : "course", r.s("title"), null, false, () -> { note.subject = r.id(); note.subjectTitle = r.s("title"); changed(); editorRefresh(); });
                if (subjects.isEmpty()) p.note(tr("Ainda não há módulos. Crie um em Estudos."));
                p.show();
            });
        });
    }

    static final class SimpleWatcher implements TextWatcher {
        interface Change { void on(String text); }
        final Change change;
        SimpleWatcher(Change change) { this.change = change; }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable e) { change.on(e.toString()); }
    }
}
