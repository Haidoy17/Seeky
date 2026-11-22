package mz.bam.seeky.android;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.*;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bloco de notas. List with Notes and Tasks, date sections, list/card/grid views, folders and a
 * floating search bar. Editor with live formatting (H1–H4, bold, italic, underline, strike,
 * highlight, colours, alignment, lists, tasks, indent, tables), pictures, drawings, voice
 * recordings, dictation, paper backgrounds, lock, reminder, home-screen shortcut and export to PDF,
 * Word, Markdown and text. Bible references are recognised and notes link to books and Estudos.
 */
public final class NotepadActivity extends Activity {
    static final int DICTATE = 51, EXPORT = 52, GALLERY = 54, CAMERA = 55;
    Screens s;
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    String screen = "list", tab = "notes", filter = "all", query = "", sort = "updated", view = "card";
    long bookMaterial; String bookUri = "", bookTitle = "";
    List<NotepadStore.Note> notes = new ArrayList<>();
    LinearLayout listBox;
    // Editor
    NotepadStore.Note note;
    EditText title, body;
    TextView reading, info, dateLine;
    LinearLayout passages, contextRow, formatSheet, editorRoot;
    ScrollView editorScroll;
    boolean dirty, readMode, applying;
    final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    String lastSnapshot = "", exportKind = "";
    File cameraShot;
    TextToSpeech voice;
    int ink, muted;
    final Runnable autosave = this::saveNow, refreshPassages = this::scanPassages, restyle = this::styleNow;

    static void open(Context c) { start(c, new Intent(c, NotepadActivity.class)); }
    static void start(Context c, Intent i) { if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); }
    static void withPassage(Context c, String reference, String text, String version) { start(c, new Intent(c, NotepadActivity.class).putExtra("passage_title", reference).putExtra("passage_text", text).putExtra("passage_version", version)); }
    /** A new note linked to a book (library id, or the file's address and title) with the selected words. */
    static void forBook(Context c, long material, int part, String text) { forBook(c, material, "", "", part, text); }
    static void forBook(Context c, long material, String uri, String bookTitle, int part, String text) { start(c, new Intent(c, NotepadActivity.class).putExtra("material", material).putExtra("book_uri", uri == null ? "" : uri).putExtra("book_title", bookTitle == null ? "" : bookTitle).putExtra("segment", part).putExtra("text", text == null ? "" : text).putExtra("new", true)); }
    /** The notes of one book. */
    static void bookNotes(Context c, long material, String uri, String bookTitle) { start(c, new Intent(c, NotepadActivity.class).putExtra("material", material).putExtra("book_uri", uri == null ? "" : uri).putExtra("book_title", bookTitle == null ? "" : bookTitle)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        s = new Screens(this);
        android.content.SharedPreferences p = getSharedPreferences("notepad", 0);
        sort = p.getString("sort", "updated"); view = p.getString("view", "card");
        Intent i = getIntent();
        bookMaterial = i.getLongExtra("material", 0); bookUri = i.getStringExtra("book_uri") == null ? "" : i.getStringExtra("book_uri"); bookTitle = i.getStringExtra("book_title") == null ? "" : i.getStringExtra("book_title");
        if (bookMaterial > 0 || !bookUri.isEmpty()) filter = "book";
        long id = i.getLongExtra("note", 0);
        if (id > 0) edit(id);
        else if (i.hasExtra("passage_title") || i.getBooleanExtra("new", false)) {
            NotepadStore.Note n = blank();
            if (i.hasExtra("passage_title")) { n.title = i.getStringExtra("passage_title"); n.body = NotepadText.passage(i.getStringExtra("passage_title"), i.getStringExtra("passage_text") == null ? "" : i.getStringExtra("passage_text"), i.getStringExtra("passage_version")) + "\n\n"; n.folder = tr("Estudo bíblico"); }
            n.segment = i.getIntExtra("segment", 0);
            String text = i.getStringExtra("text"); if (text != null && !text.trim().isEmpty()) n.body = "> " + text.trim().replace("\n", "\n> ") + "\n\n";
            if (i.getStringExtra("body") != null) n.body = i.getStringExtra("body") + "\n";
            editor(n);
        } else list();
    }
    NotepadStore.Note blank() { NotepadStore.Note n = new NotepadStore.Note(); n.material = bookMaterial; if (bookMaterial <= 0) { n.source = bookUri; n.sourceTitle = bookTitle; } else n.bookTitle = bookTitle; return n; }
    @Override protected void onPause() { super.onPause(); if (screen.equals("edit")) saveNow(); NotepadMedia.stop(); }
    @Override protected void onDestroy() { PlusUi.MAIN.removeCallbacks(autosave); PlusUi.MAIN.removeCallbacks(restyle); if (voice != null) voice.shutdown(); worker.shutdown(); super.onDestroy(); }
    @Override public void onBackPressed() { if (screen.equals("edit") && formatSheet != null && formatSheet.getVisibility() == View.VISIBLE) { formatSheet.setVisibility(View.GONE); return; } if (screen.equals("edit") || screen.equals("trash")) { if (screen.equals("edit")) saveNow(); list(); } else super.onBackPressed(); }
    void toast(String t) { PlusUi.toast(this, t); }
    String tr(String t) { return AppLanguage.text(this, t); }
    int dp(float n) { return s.dp(n); }
    FrameLayout circle(String icon, String label, Runnable action) {
        FrameLayout f = new FrameLayout(this); f.setBackground(s.round(s.p.surface, 26)); f.setElevation(dp(2));
        f.addView(new Icons(this, icon, s.p.ink), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setContentDescription(tr(label)); f.setOnClickListener(v -> action.run()); Motion.touch(f); return f;
    }

    // ================================================================ list
    void list() {
        screen = "list";
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(s.p.bg);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = s.column(); content.setPadding(dp(16), dp(10), dp(16), dp(110));
        scroll.addView(content); root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root); getWindow().setStatusBarColor(s.p.bg); getWindow().setNavigationBarColor(s.p.bg);
        // Top: folders · Notas | Tarefas · view
        LinearLayout top = s.row();
        top.addView(circle(filter.equals("book") ? "back" : "folder", filter.equals("book") ? "Voltar" : "Pastas", () -> { if (filter.equals("book") && !isTaskRoot()) finish(); else if (filter.equals("book")) { filter = "all"; bookMaterial = 0; bookUri = ""; list(); } else folders(); }), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout seg = s.row(); seg.setBackground(s.round(s.p.surface, 24)); seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (String[] t : new String[][]{{"notes", "Notas"}, {"tasks", "Tarefas"}}) { TextView b = s.text(t[1], 15, tab.equals(t[0]) ? s.p.onAccent : s.p.ink, true); b.setGravity(Gravity.CENTER); b.setPadding(dp(18), 0, dp(18), 0); if (tab.equals(t[0])) b.setBackground(s.round(s.p.accent, 20)); b.setContentDescription(tr(t[1])); b.setOnClickListener(v -> { tab = t[0]; list(); }); Motion.touch(b); seg.addView(b, new LinearLayout.LayoutParams(-2, dp(40))); }
        FrameLayout segWrap = new FrameLayout(this); segWrap.addView(seg, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        top.addView(segWrap, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(circle(view.equals("grid") ? "grid" : view.equals("list") ? "queue" : "collection", "Visualização", this::viewMenu), new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(top);
        TextView big = s.text(heading(), 32, s.p.ink, true); big.setTypeface(Screens.serif()); big.setPadding(dp(4), dp(14), 0, dp(2)); big.setMaxLines(2); big.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(big);
        listBox = s.column(); content.addView(listBox, s.gap(6));
        // Floating bar: search · templates · new
        LinearLayout bar = s.row(); bar.setPadding(dp(16), 0, dp(16), dp(16));
        LinearLayout pill = s.row(); pill.setBackground(s.round(s.p.surface, 28)); pill.setElevation(dp(6)); pill.setPadding(dp(16), 0, dp(10), 0);
        pill.addView(new Icons(this, "search", s.p.muted), new LinearLayout.LayoutParams(dp(20), dp(20)));
        EditText search = new EditText(this); search.setHint(tr(tab.equals("tasks") ? "Procurar tarefas" : "Pesquisar")); search.setText(query); search.setSingleLine(true); search.setBackground(null); search.setTextColor(s.p.ink); search.setHintTextColor(s.p.muted); search.setTextSize(16); search.setPadding(dp(10), 0, 0, 0);
        search.addTextChangedListener(new SimpleWatcher(t -> { query = t; drawList(); }));
        pill.addView(search, new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(pill, new LinearLayout.LayoutParams(0, dp(56), 1));
        FrameLayout templates = circle("cards", "Modelos", this::newNote); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(56), dp(56)); tp.leftMargin = dp(10); bar.addView(templates, tp);
        FrameLayout add = new FrameLayout(this); add.setBackground(s.round(s.p.accent, 28)); add.setElevation(dp(6)); add.addView(new Icons(this, "edit", s.p.onAccent), new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)); add.setContentDescription(tr(tab.equals("tasks") ? "Nova tarefa" : "Nova nota")); add.setOnClickListener(v -> { if (tab.equals("tasks")) newTask(); else editor(blank()); }); Motion.touch(add);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(56), dp(56)); ap.leftMargin = dp(10); bar.addView(add, ap);
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        worker.execute(() -> { List<NotepadStore.Note> all = NotepadStore.all(this); PlusUi.post(this, () -> { if (!screen.equals("list")) return; notes = all; drawList(); }); });
    }
    String heading() {
        if (tab.equals("tasks")) return tr("Tarefas");
        switch (filter) {
            case "book": return bookTitle.isEmpty() ? tr("Notas do livro") : bookTitle;
            case "pinned": return tr("Fixadas"); case "tasks": return tr("Com tarefas"); case "bible": return tr("Com passagens"); case "books": return tr("Notas de livros");
            default: return filter.startsWith("folder:") ? filter.substring(7) : tr("Todas as notas");
        }
    }
    void viewMenu() {
        UiPanel p = new UiPanel(this, "Visualização", null, "grid");
        String[][] views = {{"list", "Visualização em lista", "queue"}, {"card", "Visualização em cartão", "collection"}, {"grid", "Visualização em grelha", "grid"}};
        for (String[] v : views) p.action(view.equals(v[0]) ? "check" : v[2], v[1], null, false, () -> { view = v[0]; getSharedPreferences("notepad", 0).edit().putString("view", view).apply(); list(); });
        String[][] sorts = {{"updated", "Ordenar por edição"}, {"created", "Ordenar por criação"}, {"title", "Ordenar por título (A–Z)"}};
        for (String[] v : sorts) p.action(sort.equals(v[0]) ? "check" : "filters", v[1], null, false, () -> { sort = v[0]; getSharedPreferences("notepad", 0).edit().putString("sort", sort).apply(); list(); });
        p.show();
    }
    void folders() {
        worker.execute(() -> {
            List<String> folders; try (NotepadStore pad = new NotepadStore(this)) { folders = pad.folders(); }
            int bin = NotepadStore.trashed(this).size();
            PlusUi.post(this, () -> {
                UiPanel p = new UiPanel(this, "Pastas", notes.size() + " " + tr(notes.size() == 1 ? "nota" : "notas"), "folder");
                String[][] base = {{"all", "Todas as notas", "note"}, {"pinned", "Fixadas", "pin"}, {"tasks", "Com tarefas", "check"}, {"bible", "Com passagens", "book"}, {"books", "Notas de livros", "pdfbook"}};
                for (String[] f : base) p.action(filter.equals(f[0]) ? "check" : f[2], f[1], null, false, () -> { filter = f[0]; tab = "notes"; list(); });
                for (String f : folders) p.action(filter.equals("folder:" + f) ? "check" : "folder", f, count(f) + "", false, () -> { filter = "folder:" + f; tab = "notes"; list(); });
                p.action("add", "Nova pasta…", null, false, () -> ask("Nova pasta", "Nome da pasta", "", name -> { filter = "folder:" + name; toast("Pasta criada: guarde uma nota nela."); NotepadStore.Note n = blank(); n.folder = name; editor(n); }));
                p.action("trash", "Lixo", bin + "", false, this::trash);
                p.show();
            });
        });
    }
    int count(String folder) { int n = 0; for (NotepadStore.Note x : notes) if (x.folder.equals(folder)) n++; return n; }
    boolean matches(NotepadStore.Note n) {
        if (filter.equals("pinned") && !n.pinned) return false;
        if (filter.equals("tasks") && NotepadText.tasks(n.body)[1] == 0) return false;
        if (filter.equals("bible") && BibleReferences.references(n.title + "\n" + n.body).isEmpty()) return false;
        if (filter.equals("books") && n.material <= 0 && n.source.isEmpty()) return false;
        if (filter.equals("book") && !(bookMaterial > 0 && n.material == bookMaterial || !bookUri.isEmpty() && bookUri.equals(n.source))) return false;
        if (filter.startsWith("folder:") && !n.folder.equals(filter.substring(7))) return false;
        if (!query.trim().isEmpty()) { String q = BibleGame.plain(query); String all = BibleGame.plain(n.title + " " + (n.locked ? "" : n.body) + " " + n.tags + " " + n.folder + " " + n.book()); for (String w : q.split(" ")) if (!all.contains(w)) return false; }
        return true;
    }
    void drawList() {
        if (listBox == null) return;
        listBox.removeAllViews();
        if (tab.equals("tasks")) { drawTasks(); return; }
        List<NotepadStore.Note> shown = new ArrayList<>();
        for (NotepadStore.Note n : notes) if (matches(n)) shown.add(n);
        Comparator<NotepadStore.Note> order = sort.equals("title") ? (x, y) -> x.shownTitle().compareToIgnoreCase(y.shownTitle()) : sort.equals("created") ? (x, y) -> Long.compare(y.created, x.created) : (x, y) -> Long.compare(y.updated, x.updated);
        shown.sort(order);
        if (shown.isEmpty()) {
            LinearLayout empty = s.card(); empty.setPadding(dp(18), dp(18), dp(18), dp(18));
            empty.addView(s.text(notes.isEmpty() ? "Comece o seu bloco de notas" : filter.equals("book") ? "Ainda não há notas deste livro" : "Nenhuma nota encontrada", 18, s.p.ink, true));
            empty.addView(s.text(notes.isEmpty() ? "Escreva ideias, sermões, estudos e tarefas, com títulos, cores, listas, imagens, desenhos e gravações. Passagens como \"João 3:16\" são reconhecidas: toque para as ler." : filter.equals("book") ? "Escreva aqui as notas da leitura; também pode criá-las no leitor (ferramenta Notas) sem sair do livro." : "Tente outra palavra ou outra pasta.", 14, s.p.muted, false));
            empty.addView(s.button("Nova nota", true, () -> editor(blank())), s.gap(12));
            listBox.addView(empty, s.gap(8));
            return;
        }
        LinkedHashMap<String, List<NotepadStore.Note>> sections = new LinkedHashMap<>();
        List<NotepadStore.Note> pinned = new ArrayList<>();
        for (NotepadStore.Note n : shown) if (n.pinned && !filter.equals("pinned")) pinned.add(n);
        if (!pinned.isEmpty()) sections.put(tr("Fixadas"), pinned);
        long now = System.currentTimeMillis();
        for (NotepadStore.Note n : shown) { if (pinned.contains(n)) continue; String key = sort.equals("title") ? tr("Notas") : section(sort.equals("created") ? n.created : n.updated, now); sections.computeIfAbsent(key, k -> new ArrayList<>()).add(n); }
        for (Map.Entry<String, List<NotepadStore.Note>> e : sections.entrySet()) {
            TextView h = s.text(e.getKey(), 19, s.p.ink, true); h.setPadding(dp(4), dp(18), 0, dp(8)); listBox.addView(h);
            if (view.equals("list")) {
                LinearLayout group = s.column(); group.setBackground(s.round(s.p.surface, 22)); group.setPadding(dp(16), dp(4), dp(16), dp(4));
                for (int i = 0; i < e.getValue().size(); i++) { if (i > 0) { View line = new View(this); line.setBackgroundColor(s.p.line); group.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.7f)))); } group.addView(row(e.getValue().get(i))); }
                listBox.addView(group);
            } else if (view.equals("grid")) {
                List<NotepadStore.Note> items = e.getValue();
                for (int i = 0; i < items.size(); i += 2) {
                    LinearLayout r = s.row(); r.setGravity(Gravity.TOP);
                    for (int k = 0; k < 2; k++) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); if (k == 0) lp.rightMargin = dp(10); if (i + k < items.size()) r.addView(tile(items.get(i + k)), lp); else { lp.height = 0; r.addView(new View(this), lp); } }
                    listBox.addView(r, s.gap(i == 0 ? 0 : 10));
                }
            } else for (int i = 0; i < e.getValue().size(); i++) listBox.addView(card(e.getValue().get(i)), s.gap(i == 0 ? 0 : 10));
        }
    }
    static String section(long t, long now) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(now); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis(), day = 86400000L;
        if (t >= today) return "Hoje"; if (t >= today - day) return "Ontem"; if (t >= today - 7 * day) return "7 dias anteriores"; if (t >= today - 30 * day) return "30 dias anteriores";
        String m = new SimpleDateFormat("LLLL 'de' yyyy", new Locale("pt", "PT")).format(new Date(t));
        return Character.toUpperCase(m.charAt(0)) + m.substring(1);
    }
    String when(long t) { return android.text.format.DateUtils.isToday(t) ? DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(t)) : new SimpleDateFormat("dd/MM", Locale.ROOT).format(new Date(t)); }
    String preview(NotepadStore.Note n) {
        if (n.locked) return "🔒 " + tr("Nota bloqueada");
        String p = NotepadText.plain(n.body).replaceAll("\\s+", " ").trim();
        String t = n.shownTitle(); if (n.title.trim().isEmpty() && p.startsWith(t)) p = p.substring(t.length()).trim();
        return p;
    }
    String marks(NotepadStore.Note n) {
        List<String> bits = new ArrayList<>();
        int[] tasks = NotepadText.tasks(n.body); if (tasks[1] > 0) bits.add("☑ " + tasks[0] + "/" + tasks[1]);
        if (n.body.contains("note-img:")) bits.add("🖼"); if (n.body.contains("note-audio:")) bits.add("🎙");
        if (!BibleReferences.references(n.body).isEmpty()) bits.add("📖"); if (n.reminder > System.currentTimeMillis()) bits.add("⏰"); if (n.locked) bits.add("🔒");
        return String.join("  ", bits);
    }
    TextView chip(String text) { TextView t = s.text(text, 12.5f, s.p.muted, false); t.setPadding(dp(7), dp(2), dp(7), dp(2)); t.setBackground(s.round(s.p.tint, 6)); return t; }
    View folderLine(NotepadStore.Note n) {
        LinearLayout r = s.row(); r.setPadding(0, dp(6), 0, 0);
        r.addView(new Icons(this, !n.book().isEmpty() ? "pdfbook" : "folder", s.p.muted), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView f = s.text(!n.book().isEmpty() ? n.book() : n.folder.isEmpty() ? tr("Sem pasta") : n.folder, 13.5f, s.p.muted, false); f.setSingleLine(true); f.setEllipsize(TextUtils.TruncateAt.END); f.setPadding(dp(6), 0, dp(6), 0);
        r.addView(f, new LinearLayout.LayoutParams(0, -2, 1));
        String m = marks(n); if (!m.isEmpty()) r.addView(s.text(m, 12, s.p.muted, false));
        return r;
    }
    void bind(View v, NotepadStore.Note n) { v.setContentDescription(n.shownTitle()); v.setOnClickListener(x -> edit(n.id)); v.setOnLongClickListener(x -> { noteMenu(n); return true; }); Motion.touch(v); }
    View row(NotepadStore.Note n) {
        LinearLayout r = s.column(); r.setPadding(0, dp(12), 0, dp(12));
        TextView t = s.text(n.shownTitle(), 17, s.p.ink, true); t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); r.addView(t);
        LinearLayout second = s.row(); second.setPadding(0, dp(5), 0, 0);
        second.addView(chip(when(n.updated)));
        TextView p = s.text(preview(n), 14, s.p.muted, false); p.setSingleLine(true); p.setEllipsize(TextUtils.TruncateAt.END); p.setPadding(dp(8), 0, 0, 0);
        second.addView(p, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(second); r.addView(folderLine(n));
        bind(r, n); return r;
    }
    int tint(NotepadStore.Note n) { return n.color == 0 ? s.p.surface : blend(s.p.surface, n.color, s.p.light ? .5f : .22f); }
    View card(NotepadStore.Note n) {
        LinearLayout c = s.column(); c.setBackground(s.round(tint(n), 22)); c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout top = s.row();
        TextView t = s.text(n.shownTitle(), 18, s.p.ink, true); t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END); top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        if (n.pinned) top.addView(new Icons(this, "pin", s.p.accent), new LinearLayout.LayoutParams(dp(18), dp(18)));
        c.addView(top);
        String pv = preview(n); if (!pv.isEmpty()) { TextView p = s.text(pv, 14, s.p.muted, false); p.setMaxLines(2); p.setEllipsize(TextUtils.TruncateAt.END); p.setPadding(0, dp(4), 0, 0); c.addView(p); }
        c.addView(folderLine(n));
        LinearLayout foot = s.row(); foot.setPadding(0, dp(10), 0, 0); foot.addView(chip(when(n.updated))); c.addView(foot);
        bind(c, n); return c;
    }
    View tile(NotepadStore.Note n) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout c = s.column(); c.setBackground(s.round(tint(n), 20)); c.setPadding(dp(14), dp(14), dp(14), dp(12)); c.setMinimumHeight(dp(130));
        TextView t = s.text(n.shownTitle(), 17, s.p.ink, true); t.setMaxLines(3); t.setEllipsize(TextUtils.TruncateAt.END); t.setPadding(0, 0, n.pinned ? dp(18) : 0, 0); c.addView(t);
        String pv = preview(n); if (!pv.isEmpty()) { TextView p = s.text(pv, 13.5f, s.p.muted, false); p.setMaxLines(3); p.setEllipsize(TextUtils.TruncateAt.END); p.setPadding(0, dp(4), 0, 0); c.addView(p); }
        Bitmap thumb = firstImage(n); if (thumb != null) { ImageView iv = new ImageView(this); iv.setImageBitmap(thumb); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setClipToOutline(true); iv.setBackground(s.round(s.p.tint, 10)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(80)); lp.topMargin = dp(8); c.addView(iv, lp); }
        LinearLayout foot = s.row(); foot.setPadding(0, dp(10), 0, 0); foot.addView(chip(when(n.updated))); String m = marks(n); if (!m.isEmpty()) { TextView mk = s.text(m, 12, s.p.muted, false); mk.setPadding(dp(6), 0, 0, 0); foot.addView(mk); } c.addView(foot);
        f.addView(c, new FrameLayout.LayoutParams(-1, -2));
        if (n.pinned) { View ribbon = new Ribbon(this, s.p.accent); f.addView(ribbon, new FrameLayout.LayoutParams(dp(18), dp(24), Gravity.TOP | Gravity.END)); ((FrameLayout.LayoutParams) ribbon.getLayoutParams()).rightMargin = dp(14); }
        bind(f, n); return f;
    }
    Bitmap firstImage(NotepadStore.Note n) { if (n.locked) return null; for (NotepadDoc.Block b : NotepadDoc.parse(n.body)) if (b.kind.equals("image")) return NotepadRender.bitmap(this, b.ref, dp(160)); return null; }
    static final class Ribbon extends View { final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); Ribbon(Context c, int color) { super(c); p.setColor(color); } @Override protected void onDraw(Canvas c) { float w = getWidth(), h = getHeight(); Path path = new Path(); path.moveTo(0, 0); path.lineTo(w, 0); path.lineTo(w, h); path.lineTo(w / 2, h * .75f); path.lineTo(0, h); path.close(); c.drawPath(path, p); } }
    static int blend(int a, int b, float t) { return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t)); }

    // ---- tasks tab: every task of every note ----
    void drawTasks() {
        List<View> open = new ArrayList<>(), done = new ArrayList<>();
        for (NotepadStore.Note n : notes) {
            if (n.locked || !matches(n)) continue;
            String[] lines = n.body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i].trim().replaceFirst("^\\[[cd]\\] ", "");
                if (!l.matches("^\\[[ xX]\\].*")) continue;
                boolean ok = l.charAt(1) != ' '; String text = NotepadText.strip(l).trim(); if (text.isEmpty()) continue;
                if (!query.trim().isEmpty() && !BibleGame.plain(text).contains(BibleGame.plain(query))) continue;
                final int line = i; final NotepadStore.Note owner = n;
                LinearLayout r = s.row(); r.setPadding(dp(4), dp(10), dp(4), dp(10));
                TextView box = s.text(ok ? "☑" : "☐", 22, s.p.accent, false); box.setContentDescription(tr(ok ? "Desmarcar" : "Marcar como feita")); box.setOnClickListener(v -> toggleTask(owner, line)); r.addView(box, new LinearLayout.LayoutParams(dp(36), -2));
                LinearLayout col = s.column(); TextView t = s.text(text, 16, ok ? s.p.muted : s.p.ink, false); if (ok) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); col.addView(t); TextView from = s.text(owner.shownTitle(), 12.5f, s.p.muted, false); from.setSingleLine(true); from.setEllipsize(TextUtils.TruncateAt.END); col.addView(from);
                r.addView(col, new LinearLayout.LayoutParams(0, -2, 1)); r.setOnClickListener(v -> edit(owner.id)); Motion.touch(r);
                (ok ? done : open).add(r);
            }
        }
        if (open.isEmpty() && done.isEmpty()) { LinearLayout e = s.card(); e.addView(s.text("Sem tarefas", 18, s.p.ink, true)); e.addView(s.text("Toque no lápis para criar uma tarefa, ou escreva \"[ ] \" numa nota. Todas as tarefas das notas aparecem aqui.", 14, s.p.muted, false)); listBox.addView(e, s.gap(8)); return; }
        for (int part = 0; part < 2; part++) {
            List<View> rows = part == 0 ? open : done; if (rows.isEmpty()) continue;
            TextView h = s.text(part == 0 ? tr("Por fazer") + " · " + rows.size() : tr("Concluídas") + " · " + rows.size(), 19, s.p.ink, true); h.setPadding(dp(4), dp(18), 0, dp(8)); listBox.addView(h);
            LinearLayout group = s.column(); group.setBackground(s.round(s.p.surface, 22)); group.setPadding(dp(12), dp(2), dp(12), dp(2));
            for (int i = 0; i < Math.min(rows.size(), part == 0 ? 500 : 50); i++) { if (i > 0) { View line = new View(this); line.setBackgroundColor(s.p.line); group.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(.7f)))); } group.addView(rows.get(i)); }
            listBox.addView(group);
        }
    }
    void toggleTask(NotepadStore.Note n, int line) { worker.execute(() -> { NotepadStore.Note fresh = NotepadStore.load(this, n.id); if (fresh == null) return; fresh.body = NotepadText.toggle(fresh.body, line); NotepadStore.save(this, fresh); List<NotepadStore.Note> all = NotepadStore.all(this); PlusUi.post(this, () -> { notes = all; drawList(); }); }); }
    void newTask() {
        ask("Nova tarefa", "O que precisa de fazer?", "", text -> worker.execute(() -> {
            NotepadStore.Note target = null;
            for (NotepadStore.Note n : NotepadStore.all(this)) if (n.title.equals(tr("Tarefas")) && !n.locked) { target = n; break; }
            if (target == null) { target = new NotepadStore.Note(); target.title = tr("Tarefas"); target.body = ""; }
            target.body = (target.body.isEmpty() || target.body.endsWith("\n") ? target.body : target.body + "\n") + "[ ] " + text;
            NotepadStore.save(this, target);
            List<NotepadStore.Note> all = NotepadStore.all(this);
            PlusUi.post(this, () -> { notes = all; drawList(); });
        }));
    }
    interface Chosen<T> { void take(T value); }
    void ask(String title, String hint, String value, Chosen<String> done) {
        EditText input = PlusUi.input(this, hint, value, 200);
        new UiDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Guardar", (d, w) -> { String t = input.getText().toString().trim(); if (!t.isEmpty()) done.take(t); }).setNegativeButton("Cancelar", null).show();
    }

    void noteMenu(NotepadStore.Note n) {
        UiPanel p = new UiPanel(this, n.shownTitle(), null, "note");
        p.action("edit", "Abrir", null, false, () -> edit(n.id));
        p.action("pin", n.pinned ? "Desafixar" : "Fixar no topo", null, false, () -> update(n, () -> n.pinned = !n.pinned));
        p.action("palette", "Cor do cartão", NotepadStore.COLOR_NAMES[colorIndex(n.color)], false, () -> chooseColor(n.color, c -> update(n, () -> n.color = c)));
        p.action("folder", "Mover para pasta", n.folder.isEmpty() ? tr("Sem pasta") : n.folder, false, () -> chooseFolder(n.folder, f -> update(n, () -> n.folder = f)));
        p.action("copy", "Duplicar", null, false, () -> worker.execute(() -> { NotepadStore.Note copy = NotepadStore.load(this, n.id); if (copy == null) return; copy.id = 0; copy.created = 0; copy.reminder = 0; copy.title = n.shownTitle() + " (" + tr("cópia") + ")"; NotepadStore.save(this, copy); PlusUi.post(this, this::list); }));
        if (!n.locked) p.action("share", "Partilhar", null, false, () -> share(n.shownTitle(), n.body));
        p.danger("Mover para o lixo", () -> worker.execute(() -> { try { NotepadStore.trash(this, n.id); } catch (Exception e) { AppDiagnostics.record(this, "notepad-trash", e); } PlusUi.post(this, () -> { toast("Nota movida para o lixo."); list(); }); }));
        p.show();
    }
    static int colorIndex(int color) { for (int i = 0; i < NotepadStore.COLORS.length; i++) if (NotepadStore.COLORS[i] == color) return i; return 0; }
    void update(NotepadStore.Note n, Runnable change) { change.run(); worker.execute(() -> { NotepadStore.Note fresh = NotepadStore.load(this, n.id); if (fresh == null) return; fresh.pinned = n.pinned; fresh.color = n.color; fresh.folder = n.folder; NotepadStore.save(this, fresh); PlusUi.post(this, this::list); }); }
    void chooseColor(int current, Chosen<Integer> done) {
        UiPanel p = new UiPanel(this, "Cor do cartão", null, "palette");
        for (int i = 0; i < NotepadStore.COLORS.length; i++) { final int c = NotepadStore.COLORS[i]; p.action(c == current ? "check" : "palette", NotepadStore.COLOR_NAMES[i], null, false, () -> done.take(c)); }
        p.show();
    }
    void chooseFolder(String current, Chosen<String> done) {
        worker.execute(() -> {
            List<String> folders; try (NotepadStore pad = new NotepadStore(this)) { folders = pad.folders(); }
            for (String d : new String[]{tr("Estudo bíblico"), tr("Sermões"), tr("Pessoal"), tr("Trabalho")}) if (!folders.contains(d)) folders.add(d);
            PlusUi.post(this, () -> {
                UiPanel p = new UiPanel(this, "Mover para pasta", null, "folder");
                p.action("close", "Sem pasta", null, false, () -> done.take(""));
                for (String f : folders) p.action(f.equals(current) ? "check" : "folder", f, null, false, () -> done.take(f));
                p.action("add", "Nova pasta…", null, false, () -> ask("Nova pasta", "Nome da pasta", "", done::take));
                p.show();
            });
        });
    }
    void newNote() {
        UiPanel p = new UiPanel(this, "Modelos", tr("Comece mais depressa"), "cards");
        p.action("note", "Nota em branco", null, false, () -> editor(blank()));
        p.action("check", "Lista de tarefas", null, false, () -> { NotepadStore.Note n = blank(); n.body = "[ ] \n"; editor(n); });
        for (String[] t : NotepadText.TEMPLATES) if (!t[0].startsWith("Lista")) p.action(t[0].contains("bíblico") || t[0].contains("Devocional") ? "book" : t[0].contains("Sermão") ? "speaker" : "note", t[0], null, false, () -> { NotepadStore.Note n = blank(); n.body = t[1]; if (t[0].contains("bíblico") || t[0].contains("Devocional") || t[0].contains("Sermão")) n.folder = tr(t[0].contains("Sermão") ? "Sermões" : "Estudo bíblico"); editor(n); });
        p.action("speaker", "Nota de voz", tr("Grave e guarde a gravação na nota"), false, () -> { NotepadStore.Note n = blank(); n.title = tr("Nota de voz"); editor(n); PlusUi.MAIN.postDelayed(() -> tool("record"), 300); });
        p.action("edit", "Desenho", null, false, () -> { NotepadStore.Note n = blank(); n.title = tr("Desenho"); editor(n); PlusUi.MAIN.postDelayed(() -> tool("draw"), 300); });
        p.show();
    }

    // ================================================================ trash
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
                for (NotepadStore.Trashed t : items) box.addView(s.entry("note", t.title, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(t.deleted)), () -> {
                    UiPanel p = new UiPanel(this, t.title, null, "trash");
                    p.action("restore", "Restaurar", null, false, () -> worker.execute(() -> { try { NotepadStore.restore(this, t.id); } catch (Exception e) { AppDiagnostics.record(this, "notepad-restore", e); } PlusUi.post(this, () -> { toast("Nota restaurada."); trash(); }); }));
                    p.danger("Apagar para sempre", () -> worker.execute(() -> { NotepadStore.forget(this, t.id); PlusUi.post(this, this::trash); }));
                    p.show();
                }), s.gap(8));
                box.addView(s.button("Esvaziar o lixo", false, () -> new UiDialog.Builder(this).setTitle("Esvaziar o lixo?").setMessage("As notas do lixo serão apagadas para sempre.").setPositiveButton("Esvaziar", (d, w) -> worker.execute(() -> { NotepadStore.forget(this, 0); PlusUi.post(this, this::trash); })).setNegativeButton("Cancelar", null).show()), s.gap(16));
            });
        });
    }

    // ================================================================ editor
    void edit(long id) {
        worker.execute(() -> { NotepadStore.Note n = NotepadStore.load(this, id); PlusUi.post(this, () -> {
            if (n == null) { toast("Esta nota já não existe."); list(); return; }
            if (n.locked) Auth.confirm(this, "Abrir nota bloqueada", () -> editor(n)); else editor(n);
        }); });
    }
    static final String[][] PAPERS = {{"", "Sem fundo"}, {"papel", "Papel"}, {"linhas", "Linhas"}, {"grelha", "Quadriculado"}, {"pontos", "Pontos"}, {"amarelo", "Amarelo"}, {"lilas", "Lilás"}, {"ceu", "Céu"}, {"menta", "Menta"}, {"noite", "Noite"}};
    /** {top colour, bottom colour, ink, muted, pattern colour} of a paper. */
    int[] paper(String kind) {
        switch (kind) {
            case "papel": return new int[]{0xfff2eadb, 0xffece1cc, 0xff2a2520, 0xff7a6f60, 0};
            case "linhas": return new int[]{0xfffdfdfb, 0xfffdfdfb, 0xff1f2328, 0xff6b7078, 0xffcfd8e6};
            case "grelha": return new int[]{0xffffffff, 0xffffffff, 0xff1f2328, 0xff6b7078, 0xffe0e4ea};
            case "pontos": return new int[]{0xfffafafa, 0xfffafafa, 0xff1f2328, 0xff6b7078, 0xffb9bec6};
            case "amarelo": return new int[]{0xfffff6cf, 0xfffff1b8, 0xff2b2610, 0xff7a6d2e, 0};
            case "lilas": return new int[]{0xffe7dcf7, 0xfffbe3ef, 0xff2a2340, 0xff6d6280, 0};
            case "ceu": return new int[]{0xffd6e8f6, 0xfff6fbff, 0xff1d2a36, 0xff5d6c78, 0};
            case "menta": return new int[]{0xffd5f3e3, 0xfff3fbf6, 0xff1d3328, 0xff5c7466, 0};
            case "noite": return new int[]{0xff232526, 0xff3b3e41, 0xfff2f2f2, 0xffb8b8b8, 0};
            default: return new int[]{s.p.bg, s.p.bg, s.p.ink, s.p.muted, 0};
        }
    }
    /** Lines, squares or dots that follow the text lines of the note. */
    static final class Pattern extends Drawable {
        final String kind; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final TextView owner;
        Pattern(String kind, int color, TextView owner) { this.kind = kind; this.owner = owner; p.setColor(color); p.setStrokeWidth(Math.max(1, owner.getResources().getDisplayMetrics().density)); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); int step = Math.max(8, owner.getLineHeight()), top = owner.getPaddingTop() + (int) (step * .88f);
            if (kind.equals("linhas")) for (int y = top; y < b.bottom + step * 20; y += step) c.drawLine(b.left, y, b.right, y, p);
            else if (kind.equals("grelha")) { for (int y = top % step; y < b.bottom + step * 20; y += step) c.drawLine(b.left, y, b.right, y, p); for (int x = b.left; x < b.right; x += step) c.drawLine(x, b.top, x, b.bottom + step * 20, p); }
            else if (kind.equals("pontos")) { float r = p.getStrokeWidth() * 1.2f; for (int y = top % step; y < b.bottom + step * 20; y += step) for (int x = b.left + step / 2; x < b.right; x += step) c.drawCircle(x, y, r, p); }
        }
        @Override public void setAlpha(int a) {} @Override public void setColorFilter(ColorFilter f) {} @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
    void editor(NotepadStore.Note n) {
        screen = "edit"; note = n; dirty = false; readMode = false; undo.clear(); redo.clear(); lastSnapshot = n.body;
        int[] pp = paper(n.paper); ink = pp[2]; muted = pp[3];
        editorRoot = s.column();
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{pp[0], pp[1]}); editorRoot.setBackground(bg);
        setContentView(editorRoot); getWindow().setStatusBarColor(pp[0]); getWindow().setNavigationBarColor(pp[1]);
        if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) getWindow().getInsetsController().setSystemBarsAppearance(AppPalette.luminance(pp[0]) > .5 ? android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        // Top bar: back · undo/redo · read/edit · more
        LinearLayout top = s.row(); top.setPadding(dp(14), dp(10), dp(14), dp(6));
        top.addView(circle("back", "Voltar", () -> { saveNow(); if (getIntent().getLongExtra("note", 0) > 0 && isTaskRoot()) list(); else if (getIntent().getLongExtra("note", 0) > 0 || getIntent().getBooleanExtra("new", false) || getIntent().hasExtra("passage_title")) finish(); else list(); }), new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout history = s.row(); history.setBackground(s.round(s.p.surface, 24)); history.setElevation(dp(2));
        for (String[] h : new String[][]{{"undo", "Desfazer", "undo"}, {"redo", "Refazer", "redo"}}) { FrameLayout b = new FrameLayout(this); b.addView(new Icons(this, h[0], s.p.ink), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)); b.setContentDescription(tr(h[1])); b.setOnClickListener(v -> tool(h[2])); Motion.touch(b); history.addView(b, new LinearLayout.LayoutParams(dp(48), dp(48))); }
        top.addView(history);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(48), dp(48)); gp.leftMargin = dp(8);
        top.addView(circle("eye", "Ver / editar", () -> tool("read")), gp);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(48), dp(48)); mp.leftMargin = dp(8);
        top.addView(circle("more", "Mais opções", this::editorMenu), mp);
        editorRoot.addView(top);
        editorScroll = new ScrollView(this); editorScroll.setFillViewport(true);
        LinearLayout page = s.column(); page.setPadding(dp(20), dp(6), dp(20), dp(24)); editorScroll.addView(page);
        editorRoot.addView(editorScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dateLine = s.text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(n.updated > 0 ? n.updated : System.currentTimeMillis())), 13, muted, false); page.addView(dateLine);
        contextRow = s.row(); page.addView(s.scroller(contextRow));
        title = new EditText(this); title.setHint(tr("Título")); title.setText(n.title); title.setTextSize(28); title.setTypeface(ThemeStyle.BOLD); title.setTextColor(ink); title.setHintTextColor((muted & 0xffffff) | 0xaa000000); title.setBackground(null); title.setPadding(0, dp(8), 0, dp(4));
        title.addTextChangedListener(new SimpleWatcher(t -> changed()));
        page.addView(title);
        body = new EditText(this); body.setText(n.body); body.setHint(tr("Escreva aqui… Passagens como João 3:16 são reconhecidas.")); body.setGravity(Gravity.TOP); body.setMinLines(12); body.setTextSize(17); body.setTextColor(ink); body.setHintTextColor((muted & 0xffffff) | 0xaa000000);
        body.setPadding(0, dp(6), 0, dp(40)); body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setBackground(pp[4] != 0 ? new Pattern(n.paper, pp[4], body) : null);
        body.addTextChangedListener(new SimpleWatcher(t -> { if (!applying) { remember(); changed(); PlusUi.MAIN.removeCallbacks(restyle); PlusUi.MAIN.postDelayed(restyle, 160); } }));
        page.addView(body, new LinearLayout.LayoutParams(-1, -2));
        reading = s.text("", 17, ink, false); applySpacing(); reading.setPadding(0, dp(6), 0, dp(40)); reading.setVisibility(View.GONE);
        page.addView(reading);
        passages = s.column(); page.addView(passages);
        info = s.text("", 12, muted, false); info.setPadding(0, dp(10), 0, 0); page.addView(info);
        // Format sheet and floating toolbar
        formatSheet = formatPanel(); formatSheet.setVisibility(View.GONE);
        editorRoot.addView(formatSheet);
        LinearLayout tools = s.row(); tools.setPadding(dp(6), 0, dp(6), 0);
        String[][] buttons = {{"sparkle", "Seeky IA", "ai"}, {"type", "Formatar", "format"}, {"image", "Imagem", "image"}, {"camera", "Câmara", "camera"}, {"edit", "Desenho", "draw"}, {"check", "Tarefa", "task"}, {"book", "Passagem", "passage"}, {"grid", "Tabela", "table"}, {"speaker", "Gravar voz", "record"}, {"mic", "Ditar", "dictate"}, {"calendar", "Data", "date"}};
        for (String[] b : buttons) { FrameLayout f = new FrameLayout(this); f.addView(new Icons(this, b[0], s.p.ink), new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER)); f.setContentDescription(tr(b[1])); f.setOnClickListener(v -> tool(b[2])); Motion.touch(f); tools.addView(f, new LinearLayout.LayoutParams(dp(52), dp(52))); }
        HorizontalScrollView hs = s.scroller(tools); hs.setBackground(s.round(s.p.surface, 28)); hs.setElevation(dp(6));
        FrameLayout bar = new FrameLayout(this); bar.setPadding(dp(14), dp(6), dp(14), dp(12)); bar.addView(hs, new FrameLayout.LayoutParams(-2, dp(56), Gravity.CENTER));
        editorRoot.addView(bar);
        styleNow(); updateContext(); updateInfo();
        PlusUi.MAIN.post(refreshPassages);
        if (n.id == 0 && n.body.isEmpty()) { body.requestFocus(); } else if (n.id > 0 && getSharedPreferences("notepad", 0).getBoolean("open_reading", true) && !n.body.trim().isEmpty()) { readMode = true; showMode(); }
    }
    LinearLayout formatPanel() {
        LinearLayout sheet = s.column(); sheet.setBackground(s.round(s.p.surface, 26)); sheet.setPadding(dp(14), dp(14), dp(14), dp(10)); sheet.setElevation(dp(8));
        String[][][] rows = {
            {{"H1", "h1"}, {"H2", "h2"}, {"H3", "h3"}, {"H4", "h4"}, {"Corpo", "h0"}},
            {{"B", "bold"}, {"I", "italic"}, {"U", "under"}, {"S", "strike"}, {"→|", "indent"}, {"|←", "outdent"}},
            {{"1.", "number"}, {"•", "list"}, {"☐", "task"}, {"⫷", "left"}, {"≡", "center"}, {"⫸", "right"}},
            {{"❝", "quote"}, {"▬", "mark"}, {"—", "rule"}, {"⌫ cor", "nocolor"}}};
        for (String[][] row : rows) {
            LinearLayout r = s.row(); r.setBackground(s.round(s.p.tint, 14)); r.setClipToOutline(true);
            for (String[] b : row) {
                TextView t = s.text(b[0], b[0].length() > 2 ? 14 : 18, s.p.ink, true); t.setGravity(Gravity.CENTER);
                if (b[1].equals("italic")) t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)); if (b[1].equals("under")) t.setPaintFlags(t.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG); if (b[1].equals("strike")) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                t.setContentDescription(tr(label(b[1]))); t.setOnClickListener(v -> tool(b[1])); Motion.touch(t);
                r.addView(t, new LinearLayout.LayoutParams(0, dp(46), 1));
            }
            sheet.addView(r, s.gap(sheet.getChildCount() == 0 ? 0 : 8));
        }
        LinearLayout colors = s.row(); colors.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < NotepadDoc.COLOR_KEYS.length; i++) {
            final String key = NotepadDoc.COLOR_KEYS[i]; TextView a = s.text("A", 18, NotepadDoc.COLOR_VALUES[i], true); a.setGravity(Gravity.CENTER); a.setBackground(s.round((NotepadDoc.COLOR_VALUES[i] & 0xffffff) | 0x26000000, 12)); a.setContentDescription(tr("Cor") + " " + tr(NotepadDoc.COLOR_NAMES[i]));
            a.setOnClickListener(v -> applyEdit(NotepadText.color(body.getText().toString(), body.getSelectionStart(), body.getSelectionEnd(), key))); Motion.touch(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1); lp.rightMargin = dp(6); colors.addView(a, lp);
        }
        sheet.addView(colors);
        LinearLayout spacing = s.column(); spacing.setPadding(0, dp(10), 0, 0);
        TextView sl = s.text("Espaçamento entre linhas", 12.5f, s.p.muted, true); sl.setPadding(dp(4), 0, 0, dp(6)); spacing.addView(sl);
        LinearLayout opts = s.row(); opts.setBackground(s.round(s.p.tint, 14)); opts.setClipToOutline(true);
        for (int[] o : SPACINGS) {
            TextView t = s.text(o[0] == 0 ? tr("Normal") : String.format(Locale.ROOT, o[0] % 10 == 0 ? "%.1f" : "%.2f", o[0] / 100f).replace('.', ','), 14, note.spacing == o[0] ? s.p.onAccent : s.p.ink, true);
            t.setGravity(Gravity.CENTER); if (note.spacing == o[0]) t.setBackground(s.round(s.p.accent, 12));
            t.setContentDescription(tr("Espaçamento entre linhas") + " " + t.getText());
            t.setOnClickListener(v -> { note.spacing = o[0]; dirty = true; saveNow(); applySpacing(); for (int k = 0; k < opts.getChildCount(); k++) { TextView x = (TextView) opts.getChildAt(k); boolean on = SPACINGS[k][0] == note.spacing; x.setTextColor(on ? s.p.onAccent : s.p.ink); x.setBackground(on ? s.round(s.p.accent, 12) : null); } });
            Motion.touch(t); opts.addView(t, new LinearLayout.LayoutParams(0, dp(40), 1));
        }
        spacing.addView(opts, new LinearLayout.LayoutParams(-1, -2));
        sheet.addView(spacing);
        FrameLayout wrap = new FrameLayout(this); wrap.setPadding(dp(10), 0, dp(10), dp(4)); wrap.addView(sheet, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout holder = s.column(); holder.addView(wrap); return holder;
    }
    static String label(String what) {
        switch (what) {
            case "h1": return "Título 1"; case "h2": return "Título 2"; case "h3": return "Título 3"; case "h4": return "Título 4"; case "h0": return "Texto normal";
            case "bold": return "Negrito"; case "italic": return "Itálico"; case "under": return "Sublinhado"; case "strike": return "Riscado"; case "indent": return "Aumentar recuo"; case "outdent": return "Diminuir recuo";
            case "number": return "Lista numerada"; case "list": return "Lista com marcas"; case "task": return "Lista de tarefas"; case "left": return "Alinhar à esquerda"; case "center": return "Centrar"; case "right": return "Alinhar à direita";
            case "quote": return "Citação"; case "mark": return "Destacar"; case "rule": return "Linha divisória"; case "nocolor": return "Tirar a cor"; default: return what;
        }
    }
    static final int[][] SPACINGS = {{0}, {100}, {115}, {150}, {200}};
    /** Line spacing of the note in the editor and the reading view. */
    void applySpacing() {
        int sp = note == null ? 0 : note.spacing;
        float mult = sp == 0 ? 1.06f : sp / 100f; int extra = sp == 0 ? dp(4) : dp(1);
        if (body != null) { body.setLineSpacing(extra, mult); body.invalidate(); }
        if (reading != null) reading.setLineSpacing(extra, sp == 0 ? 1.08f : mult);
    }
    void styleNow() { if (body == null) return; NotepadRender.style(this, body.getText(), s.p, ink, muted, Math.max(dp(120), getResources().getDisplayMetrics().widthPixels - dp(48))); }
    void updateContext() {
        if (contextRow == null) return;
        contextRow.removeAllViews();
        List<Object[]> chips = new ArrayList<>();
        chips.add(new Object[]{"folder", note.folder.isEmpty() ? tr("Sem pasta") : note.folder, (Runnable) () -> chooseFolder(note.folder, f -> { note.folder = f; changed(); updateContext(); })});
        if (!note.book().isEmpty()) chips.add(new Object[]{"pdfbook", note.book(), (Runnable) this::openBook});
        if (!note.subjectTitle.isEmpty()) chips.add(new Object[]{"course", note.subjectTitle, (Runnable) this::chooseSubject});
        if (note.reminder > System.currentTimeMillis()) chips.add(new Object[]{"clock", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(note.reminder)), (Runnable) this::reminder});
        if (note.locked) chips.add(new Object[]{"lock", tr("Bloqueada"), (Runnable) this::toggleLock});
        if (note.pinned) chips.add(new Object[]{"pin", tr("Fixada"), (Runnable) () -> { note.pinned = false; changed(); updateContext(); }});
        for (Object[] c : chips) {
            LinearLayout chip = s.row(); chip.setPadding(dp(8), dp(4), dp(10), dp(4)); chip.setBackground(s.round((ink & 0xffffff) | 0x14000000, 14));
            chip.addView(new Icons(this, (String) c[0], muted), new LinearLayout.LayoutParams(dp(15), dp(15)));
            TextView t = s.text((String) c[1], 12.5f, muted, false); t.setSingleLine(true); t.setPadding(dp(5), 0, 0, 0); chip.addView(t);
            final Runnable r = (Runnable) c[2]; chip.setOnClickListener(v -> r.run()); Motion.touch(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(6); lp.topMargin = dp(6); contextRow.addView(chip, lp);
        }
    }
    String currentTitle() { return NotepadText.title(title.getText().toString(), body.getText().toString()); }
    void changed() { dirty = true; PlusUi.MAIN.removeCallbacks(autosave); PlusUi.MAIN.postDelayed(autosave, 1500); PlusUi.MAIN.removeCallbacks(refreshPassages); PlusUi.MAIN.postDelayed(refreshPassages, 700); updateInfo(); }
    void updateInfo() {
        if (info == null || body == null) return;
        String text = body.getText().toString(); int[] tasks = NotepadText.tasks(text);
        List<String> bits = new ArrayList<>();
        bits.add(NotepadText.words(text) + " " + tr("palavras")); bits.add("~" + NotepadText.minutes(text) + " min");
        if (tasks[1] > 0) bits.add("☑ " + tasks[0] + "/" + tasks[1]);
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
        final NotepadStore.Note n = note; dirty = false;
        worker.execute(() -> { NotepadStore.save(this, n); PlusUi.post(this, this::updateInfo); });
    }
    void remember() { String now = body.getText().toString(); if (now.equals(lastSnapshot)) return; undo.push(lastSnapshot); if (undo.size() > 150) undo.removeLast(); redo.clear(); lastSnapshot = now; }
    void setBody(String text, int start, int end) {
        applying = true; body.setText(text); body.setSelection(Math.max(0, Math.min(start, text.length())), Math.max(0, Math.min(end, text.length()))); applying = false; lastSnapshot = text; styleNow(); changed(); if (readMode) render();
    }
    void applyEdit(NotepadText.Edit e) { if (e == null) return; undo.push(body.getText().toString()); redo.clear(); if (readMode) { readMode = false; showMode(); } setBody(e.text, e.start, e.end); body.requestFocus(); }
    void tool(String what) {
        if (body == null) return;
        String text = body.getText().toString();
        int a = Math.max(0, body.getSelectionStart()), b = Math.max(0, body.getSelectionEnd());
        switch (what) {
            case "read": readMode = !readMode; showMode(); return;
            case "format": formatSheet.setVisibility(formatSheet.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); if (readMode) { readMode = false; showMode(); } return;
            case "bold": applyEdit(NotepadText.wrap(text, a, b, "**")); return;
            case "italic": applyEdit(NotepadText.wrap(text, a, b, "*")); return;
            case "under": applyEdit(NotepadText.wrap(text, a, b, "__")); return;
            case "strike": applyEdit(NotepadText.wrap(text, a, b, "~~")); return;
            case "mark": applyEdit(NotepadText.wrap(text, a, b, "==")); return;
            case "h1": case "h2": case "h3": case "h4": case "h0": applyEdit(NotepadText.heading(text, a, b, what.charAt(1) - '0')); return;
            case "list": applyEdit(NotepadText.prefix(text, a, b, "- ")); return;
            case "number": applyEdit(NotepadText.prefix(text, a, b, "1. ")); return;
            case "task": applyEdit(NotepadText.prefix(text, a, b, "[ ] ")); return;
            case "quote": applyEdit(NotepadText.prefix(text, a, b, "> ")); return;
            case "indent": applyEdit(NotepadText.indent(text, a, b, 1)); return;
            case "outdent": applyEdit(NotepadText.indent(text, a, b, -1)); return;
            case "left": applyEdit(NotepadText.align(text, a, b, 'l')); return;
            case "center": applyEdit(NotepadText.align(text, a, b, 'c')); return;
            case "right": applyEdit(NotepadText.align(text, a, b, 'r')); return;
            case "nocolor": applyEdit(NotepadText.color(text, a, b, null)); return;
            case "rule": applyEdit(NotepadText.insert(text, b, "---", true)); return;
            case "table": applyEdit(NotepadText.insert(text, b, NotepadText.table(3, 2), true)); return;
            case "date": applyEdit(NotepadText.insert(text, b, DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(new Date()), false)); return;
            case "passage": askPassage(); return;
            case "ai": saveNow(); NotepadAi.menu(this); return;
            case "dictate": dictate(); return;
            case "listen": listen(); return;
            case "image": try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), GALLERY); } catch (Exception e) { toast("Não há galeria neste telefone."); } return;
            case "camera": camera(); return;
            case "draw": NotepadMedia.draw(this, name -> insertMedia("![" + tr("Desenho") + "](note-img:" + name + ")")); return;
            case "record": NotepadMedia.record(this, (name, ms) -> insertMedia("[🎙 " + tr("Gravação") + " " + ms / 60000 + ":" + String.format(Locale.ROOT, "%02d", (ms / 1000) % 60) + "](note-audio:" + name + ")")); return;
            case "undo": if (undo.isEmpty()) { toast("Nada para desfazer."); return; } redo.push(text); String u = undo.pop(); setBody(u, u.length(), u.length()); return;
            case "redo": if (redo.isEmpty()) { toast("Nada para refazer."); return; } undo.push(text); String r = redo.pop(); setBody(r, r.length(), r.length()); return;
            default: break;
        }
    }
    void insertMedia(String token) { String current = body.getText().toString(); int at = Math.max(0, body.getSelectionEnd()); applyEdit(NotepadText.insert(current, at, token, true)); }
    void camera() {
        try {
            java.io.File dir = ScanProvider.dir(this); dir.mkdirs();
            cameraShot = new java.io.File(dir, "shot_" + (900000 + new Random().nextInt(99999)) + ".jpg");
            Uri out = ScanProvider.uri(this, cameraShot.getName());
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, out).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("", out));
            startActivityForResult(i, CAMERA);
        } catch (Exception e) { toast("Não há aplicação de câmara. Escolha da galeria."); tool("image"); }
    }
    void showMode() {
        if (body == null) return;
        body.setVisibility(readMode ? View.GONE : View.VISIBLE);
        reading.setVisibility(readMode ? View.VISIBLE : View.GONE);
        title.setEnabled(!readMode);
        if (readMode) { formatSheet.setVisibility(View.GONE); InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (im != null) im.hideSoftInputFromWindow(body.getWindowToken(), 0); render(); }
    }
    /** Reading view: styles applied, tasks to tick, recordings to play, pictures and Bible passages to tap. */
    void render() {
        SpannableStringBuilder out = NotepadRender.render(this, body.getText().toString(), ink, muted, s.p.accent, getResources().getDisplayMetrics().density, Math.max(dp(120), getResources().getDisplayMetrics().widthPixels - dp(48)), new NotepadRender.Actions() {
            public void toggle(int line) { String t = NotepadText.toggle(body.getText().toString(), line); undo.push(body.getText().toString()); setBody(t, t.length(), t.length()); }
            public void play(String ref) { NotepadMedia.play(NotepadActivity.this, ref); }
        });
        if (out.length() == 0) { out.append(tr("Nota vazia. Toque no olho para escrever.")); }
        reading.setText(out, TextView.BufferType.SPANNABLE);
        reading.setMovementMethod(LinkMovementMethod.getInstance());
        reading.setOnClickListener(null);
        BibleLinks.linkify(this, reading, s.p.accent, worker, true);
    }
    /** Passages written in the note: read, bring the text in, keep in the Bible study. */
    void scanPassages() {
        if (passages == null || body == null) return;
        final String text = title.getText().toString() + "\n" + body.getText().toString();
        worker.execute(() -> {
            List<BibleReferences.Reference> refs = BibleReferences.references(text);
            PlusUi.post(this, () -> {
                if (!screen.equals("edit")) return;
                passages.removeAllViews();
                if (refs.isEmpty()) return;
                TextView h = s.text("Passagens nesta nota", 13, muted, true); h.setAllCaps(true); h.setLetterSpacing(.05f); h.setPadding(0, dp(14), 0, dp(6)); passages.addView(h);
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
        p.action("bookmark", "Guardar no estudo bíblico", tr("Passagens guardadas e decorar"), false, () -> worker.execute(() -> { BibleStudyStore.Verses v = new BibleStudyStore.Verses(this); String t = v.text(r); BibleLinks.save(this, r, v.version(), t); PlusUi.post(this, () -> toast(t.isEmpty() ? "Guardada (importe uma Bíblia para ver o texto)." : "Guardada no estudo bíblico.")); }));
        p.action("calendar", "Abrir o estudo bíblico", null, false, () -> BibleStudyActivity.open(this));
        p.show();
    }
    void askPassage() {
        EditText input = PlusUi.input(this, "Por exemplo: João 3:16-18 ou Salmo 23", "", 80);
        new UiDialog.Builder(this).setTitle("Inserir passagem").setView(input).setPositiveButton("Inserir", (d, w) -> { BibleReferences.Reference r = BibleReferences.lookup(input.getText().toString()); if (r == null) { toast("Não reconheci a passagem. Escreva o livro, o capítulo e o versículo."); return; } insertPassage(r); }).setNegativeButton("Cancelar", null).show();
    }
    void insertPassage(BibleReferences.Reference r) {
        worker.execute(() -> {
            BibleStudyStore.Verses v = new BibleStudyStore.Verses(this); String text = v.text(r), version = v.version();
            PlusUi.post(this, () -> {
                if (!screen.equals("edit")) return;
                String block = text.isEmpty() ? "> " + r.title : NotepadText.passage(r.title, text, version);
                String current = body.getText().toString(); int at = body.getSelectionEnd() < 0 ? current.length() : body.getSelectionEnd();
                applyEdit(NotepadText.insert(current, at, block, true));
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
    void speak(String text) { voice.speak(text.length() > 3900 ? text.substring(0, 3900) : text, TextToSpeech.QUEUE_FLUSH, null, "notepad"); toast("A ler a nota. Escolha Ouvir outra vez para parar."); }

    // ---- ⋮ menu ----
    void editorMenu() {
        saveNow();
        UiPanel p = new UiPanel(this, currentTitle(), tr("Criada") + " " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(note.created > 0 ? note.created : System.currentTimeMillis())) + (note.updated > 0 ? " · " + tr("editada") + " " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(note.updated)) : ""), "note");
        p.action("sparkle", "Seeky IA", tr("Resumir, corrigir, organizar, perguntas, passagens"), false, () -> NotepadAi.menu(this));
        p.action("pin", note.pinned ? "Desafixar" : "Fixar no topo", null, false, () -> { note.pinned = !note.pinned; changed(); updateContext(); });
        p.action("lock", note.locked ? "Desbloquear" : "Bloquear", tr("Abre com impressão digital, rosto ou PIN do telefone"), false, this::toggleLock);
        p.action("palette", "Fundo da nota", tr("Papel, linhas, quadriculado, pontos, cores"), false, this::chooseBackground);
        p.action("clock", note.reminder > System.currentTimeMillis() ? "Lembrete: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(note.reminder)) : "Lembrete", null, false, this::reminder);
        p.action("widget", "Adicionar ao ecrã inicial", null, false, this::homeScreen);
        p.action("folder", "Mover para pasta", note.folder.isEmpty() ? tr("Sem pasta") : note.folder, false, () -> chooseFolder(note.folder, f -> { note.folder = f; changed(); updateContext(); }));
        p.action("pdfbook", note.book().isEmpty() ? "Ligar a um livro" : "Livro: " + note.book(), tr("Toque no livro da nota para o abrir"), false, this::chooseBook);
        p.action("course", note.subjectTitle.isEmpty() ? "Ligar a um módulo de estudo" : "Módulo: " + note.subjectTitle, tr("Também fica em Estudos → Anotações"), false, this::chooseSubject);
        p.action("collection", note.tags.isEmpty() ? "Etiquetas" : "Etiquetas: " + note.tags, null, false, this::editTags);
        p.action("pdf", "Exportar PDF", null, false, () -> export("pdf"));
        p.action("word", "Exportar Word (.docx)", null, false, () -> export("docx"));
        p.action("document", "Exportar Markdown ou texto", null, false, () -> { UiPanel q = new UiPanel(this, "Exportar", null, "document"); q.action("document", "Markdown (.md)", null, false, () -> export("md")); q.action("type", "Texto (.txt)", null, false, () -> export("txt")); q.show(); });
        p.action("share", "Partilhar", tr("Texto, PDF ou Word"), false, this::shareMenu);
        p.action("speaker", "Ouvir a nota", null, false, this::listen);
        p.action("copy", "Copiar texto", null, false, () -> { ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(currentTitle(), NotepadText.plain(body.getText().toString()))); toast("Nota copiada."); });
        p.danger("Apagar", this::trashCurrent);
        p.show();
    }
    void toggleLock() {
        if (!note.locked && !AppLock.deviceSecure(this)) { toast("Defina primeiro um bloqueio de ecrã no telefone (PIN, padrão ou impressão digital)."); return; }
        Auth.confirm(this, note.locked ? "Desbloquear a nota" : "Bloquear a nota", () -> { note.locked = !note.locked; dirty = true; saveNow(); updateContext(); toast(note.locked ? "Nota bloqueada: abre só com a sua confirmação." : "Nota desbloqueada."); });
    }
    void chooseBackground() {
        UiPanel p = new UiPanel(this, "Fundo da nota", null, "palette");
        for (String[] k : PAPERS) p.action(k[0].equals(note.paper) ? "check" : "palette", k[1], null, false, () -> { note.paper = k[0]; dirty = true; saveNow(); NotepadStore.Note n = note; boolean read = readMode; String t = title.getText().toString(), b = body.getText().toString(); n.title = t; n.body = b; editor(n); if (read) { readMode = true; showMode(); } });
        p.show();
    }
    void reminder() {
        Calendar c = Calendar.getInstance(); if (note.reminder > 0) c.setTimeInMillis(note.reminder); else c.add(Calendar.HOUR_OF_DAY, 1);
        UiPanel p = new UiPanel(this, "Lembrete", note.reminder > System.currentTimeMillis() ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(note.reminder)) : tr("Receba uma notificação com esta nota"), "clock");
        p.action("clock", "Daqui a 1 hora", null, false, () -> setReminder(System.currentTimeMillis() + 3600000L));
        p.action("calendar", "Amanhã às 8:00", null, false, () -> { Calendar t = Calendar.getInstance(); t.add(Calendar.DAY_OF_YEAR, 1); t.set(Calendar.HOUR_OF_DAY, 8); t.set(Calendar.MINUTE, 0); setReminder(t.getTimeInMillis()); });
        p.action("edit", "Escolher data e hora…", null, false, () -> new DatePickerDialog(this, (v, y, m, d) -> new TimePickerDialog(this, (tv, h, min) -> { Calendar t = Calendar.getInstance(); t.set(y, m, d, h, min, 0); setReminder(t.getTimeInMillis()); }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show(), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show());
        if (note.reminder > 0) p.danger("Tirar o lembrete", () -> setReminder(0));
        p.show();
    }
    void setReminder(long when) {
        if (when > 0 && when <= System.currentTimeMillis()) { toast("Escolha uma hora no futuro."); return; }
        note.reminder = when; dirty = true; Notify.ask(this); saveNow(); updateContext();
        if (when > 0) toast(tr("Lembrete para") + " " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(when)));
    }
    void homeScreen() {
        saveNow();
        PlusUi.MAIN.postDelayed(() -> {
            if (note.id <= 0) { toast("Escreva alguma coisa antes de a pôr no ecrã inicial."); return; }
            try {
                ShortcutManager sm = getSystemService(ShortcutManager.class);
                if (sm == null || !sm.isRequestPinShortcutSupported()) { toast("O ecrã inicial deste telefone não aceita atalhos."); return; }
                String name = currentTitle();
                ShortcutInfo info = new ShortcutInfo.Builder(this, "note-" + note.id).setShortLabel(name.length() > 12 ? name.substring(0, 11) + "…" : name).setLongLabel(name.length() > 25 ? name.substring(0, 24) + "…" : name).setIcon(Icon.createWithAdaptiveBitmap(AppShortcuts.icon(this, "note"))).setIntent(new Intent(Intent.ACTION_VIEW).setClass(this, NotepadActivity.class).putExtra("note", note.id)).build();
                sm.requestPinShortcut(info, null);
            } catch (Exception e) { toast("Não foi possível criar o atalho."); }
        }, 400);
    }
    void editTags() { EditText input = PlusUi.input(this, "Etiquetas separadas por vírgulas", note.tags, 200); new UiDialog.Builder(this).setTitle("Etiquetas").setView(input).setPositiveButton("Guardar", (d, w) -> { note.tags = String.join(", ", NotepadText.tags(input.getText().toString())); changed(); }).setNegativeButton("Cancelar", null).show(); }
    void chooseBook() {
        PagedPicker.show(this, "Ligar a um livro", (q, offset, limit) -> {
            List<PagedPicker.Entry> rows = new ArrayList<>(); long total;
            try (StudyStore db = new StudyStore(this)) { String where = "(title LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\')"; String like = StudySchedule.like(q); total = db.count("materials", where, new String[]{like, like}); for (StudyStore.Row r : db.page("materials", where, new String[]{like, like}, "updated DESC", limit, offset)) rows.add(new PagedPicker.Entry("" + r.id(), r.s("title"), r.s("author"))); }
            return new PagedPicker.Page(rows, total);
        }, e -> { note.material = Long.parseLong(e.id); note.bookTitle = e.label; note.source = ""; note.sourceTitle = ""; changed(); updateContext(); });
    }
    void openBook() {
        final long id = note.material; final int part = note.segment; final String uri = note.source;
        if (id <= 0 && !uri.isEmpty()) { try { startActivity(new Intent(this, ReaderActivity.class).setData(Uri.parse(uri)).putExtra("name", note.sourceTitle)); } catch (Exception e) { toast("O livro já não está disponível."); } return; }
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
                p.action("close", "Sem módulo", null, false, () -> { note.subject = 0; note.subjectTitle = ""; changed(); updateContext(); });
                for (StudyStore.Row r : subjects) p.action(r.id() == note.subject ? "check" : "course", r.s("title"), null, false, () -> { note.subject = r.id(); note.subjectTitle = r.s("title"); changed(); updateContext(); });
                if (subjects.isEmpty()) p.note(tr("Ainda não há módulos. Crie um em Estudos."));
                p.show();
            });
        });
    }
    // ---- export and share ----
    String fileName(String ext) { return currentTitle().replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim() + "." + ext; }
    static String mime(String kind) { switch (kind) { case "pdf": return "application/pdf"; case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; case "md": return "text/markdown"; default: return "text/plain"; } }
    void export(String kind) { saveNow(); exportKind = kind; try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime(kind)).putExtra(Intent.EXTRA_TITLE, fileName(kind)), EXPORT); } catch (Exception e) { toast("Não há onde guardar ficheiros neste telefone."); } }
    void write(String kind, OutputStream out) throws IOException {
        String t = title.getText().toString(), b = body.getText().toString();
        switch (kind) {
            case "pdf": NotepadRender.pdf(this, NotepadText.title(t, b), b, out, note == null ? 0 : note.spacing); break;
            case "docx": out.write(NotepadExport.docx(NotepadText.title(t, b), b, ref -> NotepadMedia.forExport(this, ref), note == null ? 0 : note.spacing)); break;
            case "md": out.write(NotepadText.markdown(t, b).getBytes(StandardCharsets.UTF_8)); break;
            default: out.write((NotepadText.title(t, b) + "\n\n" + NotepadText.plain(b)).getBytes(StandardCharsets.UTF_8));
        }
    }
    void shareMenu() {
        UiPanel p = new UiPanel(this, "Partilhar", null, "share");
        p.action("type", "Como texto", null, false, () -> share(currentTitle(), body.getText().toString()));
        p.action("pdf", "Como PDF", null, false, () -> shareFile("pdf"));
        p.action("word", "Como Word (.docx)", null, false, () -> shareFile("docx"));
        p.show();
    }
    void shareFile(String kind) {
        worker.execute(() -> {
            try {
                File f = ShareTools.temp(this, fileName(kind));
                try (OutputStream out = new FileOutputStream(f)) { PlusUi.post(this, () -> {}); write(kind, out); }
                PlusUi.post(this, () -> ShareTools.send(this, ShareTools.tempUri(this, f), f.getName(), mime(kind)));
            } catch (Exception e) { PlusUi.post(this, () -> toast("Não foi possível preparar o ficheiro.")); }
        });
    }
    void share(String name, String text) { try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, name).putExtra(Intent.EXTRA_TEXT, name + "\n\n" + NotepadText.plain(text)), tr("Partilhar nota"))); } catch (Exception e) { toast("Não há aplicações para partilhar."); } }
    void trashCurrent() {
        new UiDialog.Builder(this).setTitle("Mover para o lixo?").setMessage("Pode restaurá-la depois em Pastas → Lixo.").setPositiveButton("Mover", (d, w) -> {
            final long id = note.id; dirty = false; PlusUi.MAIN.removeCallbacks(autosave);
            worker.execute(() -> { try { if (id > 0) NotepadStore.trash(this, id); } catch (Exception e) { AppDiagnostics.record(this, "notepad-trash", e); } PlusUi.post(this, () -> { toast("Nota movida para o lixo."); note = null; list(); }); });
        }).setNegativeButton("Cancelar", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (Auth.result(request, result)) return;
        if (request == DICTATE && result == RESULT_OK && data != null && body != null) {
            ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (heard == null || heard.isEmpty()) return;
            String current = body.getText().toString(); int at = Math.max(0, body.getSelectionEnd());
            applyEdit(NotepadText.insert(current, at, (at > 0 && !Character.isWhitespace(current.charAt(at - 1)) ? " " : "") + heard.get(0), false));
        }
        if (request == EXPORT && result == RESULT_OK && data != null && data.getData() != null) {
            Uri target = data.getData(); final String kind = exportKind;
            worker.execute(() -> { try (OutputStream out = getContentResolver().openOutputStream(target)) { if (out == null) throw new IOException(); write(kind, out); PlusUi.post(this, () -> toast("Nota exportada.")); } catch (Exception e) { AppDiagnostics.record(this, "notepad-export", e); PlusUi.post(this, () -> toast("Não foi possível exportar a nota.")); } });
        }
        if ((request == GALLERY && result == RESULT_OK && data != null && data.getData() != null) || (request == CAMERA && result == RESULT_OK && cameraShot != null)) {
            final Uri source = request == GALLERY ? data.getData() : Uri.fromFile(cameraShot); final File shot = request == CAMERA ? cameraShot : null;
            worker.execute(() -> { try { String name = NotepadMedia.image(this, source); PlusUi.post(this, () -> insertMedia("![" + tr("Imagem") + "](note-img:" + name + ")")); } catch (Exception e) { PlusUi.post(this, () -> toast("Não foi possível usar esta imagem.")); } finally { if (shot != null) shot.delete(); } });
        }
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
