package mz.bam.seeky.android;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;
import java.util.IdentityHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Leitor de música e de vídeo (Mais → Música / Vídeos): biblioteca com procura automática no
 * telefone, pastas e ficheiros adicionados, pesquisa e separadores (faixas, artistas, álbuns,
 * géneros, anos, pastas, favoritas, recentes, mais ouvidas), tocar tudo, aleatório, fila e
 * mini-leitor.
 */
public final class LibraryActivity extends Activity {
    static final int ADD_FILES = 1, ADD_FOLDER = 2, PERMISSION = 3;
    static final String[] AUDIO_TABS = {"Início", "Faixas", "Álbuns", "Artistas", "Pastas", "Géneros", "Anos", "Favoritas"};
    static final String[] VIDEO_TABS = {"Início", "Vídeos", "Pastas", "Anos", "Favoritos"};
    static final String[] SORTS = {"Título", "Artista", "Álbum", "Ano", "Data de adição", "Duração"};
    static final String[] SORT_SQL = {"title COLLATE NOCASE", "artist COLLATE NOCASE,album COLLATE NOCASE,track", "album COLLATE NOCASE,track,title COLLATE NOCASE", "year DESC,album COLLATE NOCASE,track", "added DESC", "duration DESC"};

    String kind = MediaLibrary.AUDIO, tab, query = "", groupColumn, groupValue, special;
    int period = MediaLibrary.WEEK;

    /** Cabeçalho de secção do Início (com "Ver todas") ou nota. */
    static final class Section {
        final String title, action;
        final Runnable run;
        final boolean note;
        Section(String title, String action, Runnable run, boolean note) { this.title = title; this.action = action; this.run = run; this.note = note; }
    }

    /** Marcador da linha de períodos (Hoje · Esta semana · Este mês · Sempre). */
    static final Object PERIODS = new Object();
    final Map<Object, List<MediaLibrary.Track>> context = new IdentityHashMap<>();
    final Set<Object> counted = Collections.newSetFromMap(new IdentityHashMap<>());
    MediaLibrary db;
    AppPalette p;
    final ExecutorService work = Executors.newSingleThreadExecutor(), artWork = Executors.newFixedThreadPool(2);
    final Handler main = new Handler(Looper.getMainLooper());
    final List<Object> rows = new ArrayList<>();
    List<MediaLibrary.Track> visible = new ArrayList<>();
    LinearLayout tabs, mini;
    TextView title, summary, miniTitle, miniSub;
    Icons miniPlay;
    EditText search;
    ListView list;
    BaseAdapter adapter;
    ProgressBar busy;
    int generation;
    MediaPlaybackService service;
    final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(8 * 1024 * 1024) { @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); } };
    final Set<String> noArt = Collections.synchronizedSet(new HashSet<>());

    final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder b) { if (b instanceof MediaPlaybackService.LocalBinder) service = ((MediaPlaybackService.LocalBinder) b).service(); refreshMini(); }
        public void onServiceDisconnected(ComponentName n) { service = null; }
    };
    final Runnable tick = new Runnable() { public void run() { refreshMini(); main.postDelayed(this, 1000); } };

    static void open(Context c, String kind) {
        try { c.startActivity(new Intent(c, LibraryActivity.class).putExtra("kind", kind)); } catch (Exception e) { AppDiagnostics.record(c, "library-open", e); }
    }

    boolean video() { return kind.equals(MediaLibrary.VIDEO); }
    String[] tabNames() { return video() ? VIDEO_TABS : AUDIO_TABS; }
    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    TextView label(String text, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(AppLanguage.text(this, text));
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(ThemeStyle.BOLD);
        return t;
    }

    GradientDrawable round(int color, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); return g; }

    @Override protected void attachBaseContext(Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        kind = MediaLibrary.VIDEO.equals(getIntent().getStringExtra("kind")) ? MediaLibrary.VIDEO : MediaLibrary.AUDIO;
        tab = tabNames()[0];
        db = new MediaLibrary(this);
        p = AppPalette.of(this);
        getWindow().setStatusBarColor(p.bg);
        getWindow().setNavigationBarColor(p.surface);
        build();
        load();
        ensureAccessAndScan(false);
    }

    @Override protected void onStart() {
        super.onStart();
        try { bindService(new Intent(this, MediaPlaybackService.class), connection, 0); } catch (Exception ignored) {}
        main.post(tick);
        UiPanel.visibility(this, true);
    }

    @Override protected void onStop() {
        main.removeCallbacks(tick);
        try { unbindService(connection); } catch (Exception ignored) {}
        service = null;
        UiPanel.visibility(this, false);
        super.onStop();
    }

    @Override protected void onDestroy() {
        UiPanel.dismissAll(this);
        work.shutdownNow();
        artWork.shutdownNow();
        try { work.awaitTermination(800, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}
        db.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (groupValue != null) { groupValue = null; groupColumn = null; load(); return; }
        if (special != null) { special = null; load(); return; }
        if (search.getVisibility() == View.VISIBLE && !query.isEmpty()) { search.setText(""); search.setVisibility(View.GONE); return; }
        super.onBackPressed();
    }

    // ---- interface ----

    FrameLayout iconButton(String symbol, String description, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(this);
        f.addView(new Icons(this, symbol, p.ink), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        f.setBackground(round(p.tint, 22));
        f.setContentDescription(AppLanguage.text(this, description));
        f.setOnClickListener(click);
        return f;
    }

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(p.bg);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(4));
        top.addView(iconButton("back", "Voltar", v -> onBackPressed()), new LinearLayout.LayoutParams(dp(42), dp(42)));
        title = label(video() ? "Vídeos" : "Música", 22, p.ink, false);
        ThemeStyle.title(title, 22);
        title.setPadding(dp(12), 0, 0, 0);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(dp(42), dp(42));
        gap.leftMargin = dp(6);
        top.addView(iconButton("search", "Pesquisar", v -> toggleSearch()), gap);
        if (!video()) top.addView(iconButton("tune", "Equalizador", v -> EqualizerActivity.open(this)), new LinearLayout.LayoutParams(gap));
        top.addView(iconButton("more", "Mais opções", v -> menu()), new LinearLayout.LayoutParams(gap));
        root.addView(top);

        search = new EditText(this);
        search.setHint(AppLanguage.text(this, video() ? "Pesquisar vídeos ou pastas" : "Pesquisar músicas, artistas ou álbuns"));
        search.setSingleLine(true);
        search.setTextColor(p.ink);
        search.setHintTextColor(p.muted);
        search.setBackground(round(p.surface, 16));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setVisibility(View.GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { query = s.toString(); main.removeCallbacks(reload); main.postDelayed(reload, 250); }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(14), dp(6), dp(14), dp(2));
        root.addView(search, sp);

        HorizontalScrollView ts = new HorizontalScrollView(this);
        ts.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this);
        tabs.setPadding(dp(12), dp(8), dp(12), dp(6));
        ts.addView(tabs);
        root.addView(ts);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(2), dp(12), dp(6));
        summary = label("", 12, p.muted, false);
        bar.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(pill("playfill", video() ? "Ver tudo" : "Tocar tudo", true, v -> playAll(false)));
        LinearLayout.LayoutParams pg = new LinearLayout.LayoutParams(-2, -2);
        pg.leftMargin = dp(6);
        bar.addView(pill("shuffle", "Aleatório", false, v -> playAll(true)), pg);
        root.addView(bar);

        FrameLayout stage = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setFastScrollEnabled(true);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(8));
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> clicked(rows.get(position)));
        stage.addView(list, new FrameLayout.LayoutParams(-1, -1));
        busy = new ProgressBar(this);
        busy.setVisibility(View.GONE);
        stage.addView(busy, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(14), dp(8), dp(8), dp(8));
        mini.setBackground(round(p.surface, 0));
        mini.setElevation(dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        miniTitle = label("", 14, p.ink, true);
        miniTitle.setSingleLine();
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniSub = label("", 12, p.muted, false);
        miniSub.setSingleLine();
        miniSub.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(miniTitle);
        texts.addView(miniSub);
        mini.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout prev = new FrameLayout(this), play = new FrameLayout(this), next = new FrameLayout(this);
        prev.addView(new Icons(this, "skipprev", p.ink), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        miniPlay = new Icons(this, "playfill", p.onAccent);
        play.addView(miniPlay, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(p.accent);
        play.setBackground(circle);
        next.addView(new Icons(this, "skipnext", p.ink), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        prev.setOnClickListener(v -> command(MediaPlaybackService.TRACK_PREV));
        play.setOnClickListener(v -> command(service != null && service.playing ? MediaPlaybackService.PAUSE : MediaPlaybackService.PLAY));
        next.setOnClickListener(v -> command(MediaPlaybackService.TRACK_NEXT));
        prev.setContentDescription(AppLanguage.text(this, "Anterior"));
        play.setContentDescription(AppLanguage.text(this, "Reproduzir"));
        next.setContentDescription(AppLanguage.text(this, "Seguinte"));
        mini.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(44), dp(44));
        pl.setMargins(dp(4), 0, dp(4), 0);
        mini.addView(play, pl);
        mini.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        mini.setOnClickListener(v -> openPlayer());
        mini.setVisibility(View.GONE);
        root.addView(mini, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        buildTabs();
    }

    LinearLayout pill(String symbol, String text, boolean primary, View.OnClickListener click) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), dp(7), dp(14), dp(7));
        b.setBackground(round(primary ? p.accent : p.tint, 18));
        b.addView(new Icons(this, symbol, primary ? p.onAccent : p.ink), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView t = label(text, 13, primary ? p.onAccent : p.ink, true);
        t.setPadding(dp(6), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(click);
        return b;
    }

    void buildTabs() {
        tabs.removeAllViews();
        for (String name : tabNames()) {
            boolean on = name.equals(tab) && groupValue == null && special == null;
            TextView t = label(name, 13, on ? p.onAccent : p.ink, true);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            t.setBackground(round(on ? p.accent : p.tint, 16));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            t.setOnClickListener(v -> { tab = name; groupValue = null; groupColumn = null; special = null; load(); });
            tabs.addView(t, lp);
        }
    }

    void toggleSearch() {
        boolean show = search.getVisibility() != View.VISIBLE;
        search.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) search.requestFocus(); else search.setText("");
    }

    // ---- dados ----

    final Runnable reload = this::load;

    String groupColumn(String tab) {
        switch (tab) {
            case "Artistas": return "artist";
            case "Álbuns": return "album";
            case "Géneros": return "genre";
            case "Anos": return "year";
            case "Pastas": return "folder";
            default: return null;
        }
    }

    int sort() { return getSharedPreferences("media_library", 0).getInt("sort_" + kind, video() ? 4 : 0); }

    void load() {
        buildTabs();
        final int gen = ++generation;
        final String t = tab, q = query, gc = groupColumn, gv = groupValue, sp = special;
        final int per = period;
        busy.setVisibility(View.VISIBLE);
        work.execute(() -> {
            List<Object> out = new ArrayList<>();
            Map<Object, List<MediaLibrary.Track>> owners = new IdentityHashMap<>();
            Set<Object> plays = Collections.newSetFromMap(new IdentityHashMap<>());
            List<MediaLibrary.Track> tracks = new ArrayList<>();
            String info, heading = null;
            long now = System.currentTimeMillis();
            try {
                String column = groupColumn(t);
                if (sp != null) {
                    if (sp.equals("recent")) { tracks = db.tracks(kind, null, null, "added DESC", 300); heading = video() ? "Adicionados recentemente" : "Adicionadas recentemente"; }
                    else if (sp.equals("history")) { tracks = db.tracks(kind, "last_played>0", null, "last_played DESC", 200); heading = video() ? "Vistos recentemente" : "Ouvidas recentemente"; }
                    else { out.add(PERIODS); tracks = db.top(kind, per, now, 200); plays.addAll(tracks); heading = (video() ? "Mais vistos" : "Mais ouvidas") + " · " + AppLanguage.text(this, MediaLibrary.PERIODS[per]); }
                    if (!q.isEmpty()) tracks = filter(tracks, q);
                    out.addAll(tracks);
                    if (tracks.isEmpty()) out.add(new Section(per == MediaLibrary.TODAY ? "Ainda não ouviu nada hoje." : "Nada neste período.", null, null, true));
                    info = count(tracks);
                } else if (gv != null) {
                    String order = gc.equals("album") ? "track,title COLLATE NOCASE" : gc.equals("artist") ? "album COLLATE NOCASE,track,title COLLATE NOCASE" : SORT_SQL[sort()];
                    tracks = db.tracks(kind, gc + "=?" + (q.isEmpty() ? "" : " AND (" + MediaLibrary.search(q) + ")"), concat(new String[]{gv}, q.isEmpty() ? new String[0] : MediaLibrary.searchArgs(q)), order, 0);
                    out.addAll(tracks);
                    info = count(tracks);
                } else if (t.equals("Início") && q.isEmpty()) {
                    tracks = db.tracks(kind, null, null, SORT_SQL[sort()], 0);
                    List<MediaLibrary.Track> recent = db.tracks(kind, null, null, "added DESC", 6), top = db.top(kind, per, now, 8),
                            history = db.tracks(kind, "last_played>0", null, "last_played DESC", 5), favs = db.tracks(kind, "favorite=1", null, "title COLLATE NOCASE", 5);
                    section(out, owners, video() ? "Adicionados recentemente" : "Adicionadas recentemente", recent, () -> { special = "recent"; load(); }, "Ainda não há ficheiros na biblioteca.");
                    out.add(new Section(video() ? "Mais vistos" : "Mais ouvidas", "Ver todas", () -> { special = "top"; load(); }, false));
                    out.add(PERIODS);
                    plays.addAll(top);
                    for (MediaLibrary.Track x : top) { out.add(x); owners.put(x, top); }
                    if (top.isEmpty()) out.add(new Section(per == MediaLibrary.TODAY ? (video() ? "Ainda não viu nada hoje." : "Ainda não ouviu nada hoje.") : "Nada neste período.", null, null, true));
                    if (!history.isEmpty()) section(out, owners, video() ? "Vistos recentemente" : "Ouvidas recentemente", history, () -> { special = "history"; load(); }, null);
                    if (!favs.isEmpty()) section(out, owners, video() ? "Favoritos" : "Favoritas", favs, () -> { tab = tabNames()[tabNames().length - 1]; load(); }, null);
                    info = count(tracks);
                } else if (column != null) {
                    List<MediaLibrary.Group> groups = db.groups(kind, column, q);
                    out.addAll(groups);
                    info = groups.size() + " " + AppLanguage.text(this, t.toLowerCase(Locale.ROOT));
                } else {
                    String where = q.isEmpty() ? "" : MediaLibrary.search(q);
                    String[] args = q.isEmpty() ? null : MediaLibrary.searchArgs(q);
                    if (t.startsWith("Favorit")) where = and(where, "favorite=1");
                    tracks = db.tracks(kind, where, args, SORT_SQL[sort()], 0);
                    out.addAll(tracks);
                    info = count(tracks);
                }
            } catch (Exception e) { info = e.getMessage() == null ? "" : e.getMessage(); }
            final List<MediaLibrary.Track> shown = tracks;
            final String text = info, title2 = heading;
            main.post(() -> {
                if (gen != generation || isFinishing()) return;
                busy.setVisibility(View.GONE);
                rows.clear();
                rows.addAll(out);
                context.clear();
                context.putAll(owners);
                counted.clear();
                counted.addAll(plays);
                visible = shown;
                summary.setText(text);
                title.setText(title2 != null ? AppLanguage.text(this, title2) : groupValue != null ? groupName(groupColumn, groupValue) : AppLanguage.text(this, video() ? "Vídeos" : "Música"));
                adapter.notifyDataSetChanged();
                if (shown.isEmpty() && sp == null && gv == null && (t.equals("Início") || t.equals(tabNames()[1]))) showEmpty();
            });
        });
    }

    void section(List<Object> out, Map<Object, List<MediaLibrary.Track>> owners, String title, List<MediaLibrary.Track> list, Runnable all, String empty) {
        out.add(new Section(title, list.size() >= 5 ? "Ver todas" : null, all, false));
        for (MediaLibrary.Track x : list) { out.add(x); owners.put(x, list); }
        if (list.isEmpty() && empty != null) out.add(new Section(empty, null, null, true));
    }

    static List<MediaLibrary.Track> filter(List<MediaLibrary.Track> list, String q) {
        String needle = q.toLowerCase(Locale.ROOT).trim();
        List<MediaLibrary.Track> out = new ArrayList<>();
        for (MediaLibrary.Track t : list) if ((t.title + " " + t.artist + " " + t.album + " " + t.folder).toLowerCase(Locale.ROOT).contains(needle)) out.add(t);
        return out;
    }

    static String and(String a, String b) { return a == null || a.isEmpty() ? b : "(" + a + ") AND " + b; }

    static String[] concat(String[] a, String[] b) { String[] o = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, o, a.length, b.length); return o; }

    String count(List<MediaLibrary.Track> tracks) {
        long total = 0;
        for (MediaLibrary.Track t : tracks) total += t.duration;
        return UiRows.count(this, tracks.size(), video() ? "vídeo" : "faixa", video() ? "vídeos" : "faixas") + (total > 0 ? " · " + hours(total) : "");
    }

    String hours(long ms) {
        long min = ms / 60000;
        return min < 60 ? min + " min" : (min / 60) + " h " + (min % 60) + " min";
    }

    String groupName(String column, String value) {
        if (value != null && !value.isEmpty() && !value.equals("0")) return value;
        switch (column == null ? "" : column) {
            case "artist": return AppLanguage.text(this, "Artista desconhecido");
            case "album": return AppLanguage.text(this, "Álbum desconhecido");
            case "genre": return AppLanguage.text(this, "Sem género");
            case "year": return AppLanguage.text(this, "Sem ano");
            default: return AppLanguage.text(this, "Sem pasta");
        }
    }

    void showEmpty() {
        long total = 0;
        try { total = db.totals(kind)[0]; } catch (Exception ignored) {}
        if (total > 0 && (!query.isEmpty() || !tab.equals(tabNames()[0]))) { summary.setText(AppLanguage.text(this, "Nada aqui ainda.")); return; }
        UiPanel panel = new UiPanel(this, video() ? "Ainda não há vídeos" : "Ainda não há músicas", AppLanguage.text(this, "Escolha como juntar a sua biblioteca"), video() ? "video" : "audio");
        panel.action("search", video() ? "Procurar vídeos no telefone" : "Procurar músicas no telefone", "Memória interna e cartão SD, automaticamente", false, () -> ensureAccessAndScan(true));
        panel.action("folder", "Adicionar pasta", "Escolha uma pasta (inclui subpastas)", false, this::pickFolder);
        panel.action("add", video() ? "Adicionar vídeos" : "Adicionar músicas", "Escolha ficheiros soltos", false, this::pickFiles);
        panel.show();
    }

    // ---- acesso e procura ----

    String permission() {
        if (Build.VERSION.SDK_INT >= 33) return video() ? "android.permission.READ_MEDIA_VIDEO" : "android.permission.READ_MEDIA_AUDIO";
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    boolean hasAccess() { return StorageAccess.all(this) || checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED; }

    /** Com {@code force}, procura já; sem, só se nunca procurou ou há mais de 12 horas. */
    void ensureAccessAndScan(boolean force) {
        long last = MediaLibrary.LibraryPrefs.get(this, "scan_" + kind);
        boolean auto = getSharedPreferences("media_library", 0).getBoolean("auto_" + kind, true);
        if (!force && (!auto || System.currentTimeMillis() - last < 12 * 3600000L)) return;
        if (!hasAccess()) {
            if (!force && last > 0) return;
            try { requestPermissions(new String[]{permission()}, PERMISSION); } catch (Exception e) { PlusUi.toast(this, "Sem autorização para ler a biblioteca."); }
            return;
        }
        scan();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) scan();
        else PlusUi.toast(this, video() ? "Sem autorização: adicione pastas ou vídeos à mão." : "Sem autorização: adicione pastas ou músicas à mão.");
    }

    void scan() {
        busy.setVisibility(View.VISIBLE);
        PlusUi.toast(this, video() ? "A procurar vídeos no telefone…" : "A procurar músicas no telefone…");
        work.execute(() -> {
            String message;
            try {
                int n = db.scanStore(this, kind);
                for (String[] f : db.folders(kind)) db.addFolder(this, Uri.parse(f[0]), kind, this::isFinishing);
                message = UiRows.count(this, n, video() ? "vídeo encontrado" : "música encontrada", video() ? "vídeos encontrados" : "músicas encontradas");
            } catch (SecurityException e) { message = AppLanguage.text(this, "Sem autorização para ler a biblioteca do telefone."); }
            catch (Exception e) { message = e.getMessage() == null ? AppLanguage.text(this, "A procura falhou.") : e.getMessage(); }
            final String m = message;
            main.post(() -> { if (!isFinishing()) { PlusUi.toast(this, m); load(); } });
        });
    }

    void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(video() ? "video/*" : "audio/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, ADD_FILES); } catch (Exception e) { PlusUi.toast(this, "Não há um selector de ficheiros disponível."); }
    }

    void pickFolder() {
        try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), ADD_FOLDER); } catch (Exception e) { PlusUi.toast(this, "Não há um selector de pastas disponível."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == ADD_FILES) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            busy.setVisibility(View.VISIBLE);
            work.execute(() -> { int n = db.addFiles(this, uris, kind); main.post(() -> { PlusUi.toast(this, UiRows.count(this, n, "ficheiro adicionado", "ficheiros adicionados")); load(); }); });
        } else if (request == ADD_FOLDER && data.getData() != null) {
            Uri tree = data.getData();
            busy.setVisibility(View.VISIBLE);
            PlusUi.toast(this, "A ler a pasta…");
            work.execute(() -> { int n = db.addFolder(this, tree, kind, this::isFinishing); main.post(() -> { PlusUi.toast(this, UiRows.count(this, n, "ficheiro na pasta", "ficheiros na pasta")); load(); }); });
        }
    }

    // ---- menus ----

    void menu() {
        UiPanel panel = new UiPanel(this, video() ? "Vídeos" : "Música", AppLanguage.text(this, "Biblioteca e opções"), video() ? "video" : "audio");
        panel.action("search", "Procurar automaticamente", "Memória interna e cartão SD; actualiza a biblioteca", false, () -> ensureAccessAndScan(true));
        panel.action("folder", "Adicionar pasta", "Inclui subpastas e locais que o Android não indexa", false, this::pickFolder);
        panel.action("add", video() ? "Adicionar vídeos" : "Adicionar músicas", "Ficheiros soltos, do telefone ou da nuvem", false, this::pickFiles);
        List<String[]> folders = db.folders(kind);
        if (!folders.isEmpty()) panel.action("collection", "Pastas adicionadas (" + folders.size() + ")", "Ver ou remover", false, this::folders);
        panel.action("filters", "Ordenar: " + AppLanguage.text(this, SORTS[sort()]), "Título, artista, álbum, ano, data ou duração", false, this::chooseSort);
        boolean auto = getSharedPreferences("media_library", 0).getBoolean("auto_" + kind, true);
        panel.action("refresh", auto ? "Procurar novidades ao abrir: activo" : "Procurar novidades ao abrir: desligado", "No máximo de 12 em 12 horas", false, () -> {
            getSharedPreferences("media_library", 0).edit().putBoolean("auto_" + kind, !auto).apply();
            menu();
        });
        if (!video()) {
            panel.action("tune", "Equalizador e efeitos", "10 bandas, graves, agudos, virtualização, volume", false, () -> EqualizerActivity.open(this));
            panel.action("type", Lyrics.online(this) ? "Letras da internet: activo" : "Letras da internet: desligado", "LRCLIB e Lyrics.ovh · envia só título, artista, álbum e duração", false, () -> { Lyrics.setOnline(this, !Lyrics.online(this)); menu(); });
            String mode = TranscribeWork.mode(this);
            panel.action("mic", "Transcrever com IA quando não há letra: " + AppLanguage.text(this, mode.equals("auto") ? "automático" : mode.equals("off") ? "desligado" : "perguntar"), TranscribeWork.ready(this) ? "Pregações, aulas e músicas sem letra · envia o som ao Gemini com a sua chave" : "Ligue primeiro a IA (Gemini) em Estudar com Seeky", false, () -> {
                String next = mode.equals("ask") ? "auto" : mode.equals("auto") ? "off" : "ask";
                TranscribeWork.setMode(this, next);
                if (next.equals("auto")) TranscribeWork.prefs(this).edit().putBoolean("consent", true).apply();
                menu();
            });
            boolean wifi = TranscribeWork.wifiOnly(this);
            panel.action("wifi", wifi ? "Transcrição automática só em Wi-Fi: sim" : "Transcrição automática só em Wi-Fi: não", "Cerca de 0,25 MB enviados por minuto de áudio", false, () -> { TranscribeWork.setWifiOnly(this, !wifi); menu(); });
            panel.action("lyrics", "Letras para todas as músicas", "Procura no telefone e na internet; transcreve as que faltarem, se quiser", false, this::allLyrics);
        }
        long[] totals = db.totals(kind);
        panel.note(UiRows.count(this, totals[0], video() ? "vídeo" : "faixa", video() ? "vídeos" : "faixas") + " · " + hours(totals[1]) + " · " + Rules.bytes(totals[2]));
        panel.show();
    }

    /** Lyrics for the whole library, in the background (Tarefas). */
    void allLyrics() {
        long count = db.totals(MediaLibrary.AUDIO)[0];
        new UiDialog.Builder(this).setTitle("Letras para todas as músicas")
                .setMessage(AppLanguage.text(this, "O Seeky procura a letra de cada uma das") + " " + count + " " + AppLanguage.text(this, "músicas: guardadas, ficheiros .lrc, letras incorporadas e internet. As que continuarem sem letra podem ser transcritas pela IA (até 25 de cada vez, para poupar a quota); as restantes são transcritas quando as ouvir, se a transcrição automática estiver ligada."))
                .setPositiveButton("Procurar e transcrever", (d, w) -> { if (!TranscribeWork.ready(this)) PlusUi.toast(this, "Ligue a IA (Gemini) para transcrever; por agora só se procura."); else TranscribeWork.prefs(this).edit().putBoolean("consent", true).apply(); LyricsBatch.enqueue(this, TranscribeWork.ready(this)); })
                .setNeutralButton("Só procurar", (d, w) -> LyricsBatch.enqueue(this, false))
                .setNegativeButton("Cancelar", null).show();
    }

    void folders() {
        UiPanel panel = new UiPanel(this, "Pastas adicionadas", null, "folder");
        for (String[] f : db.folders(kind)) panel.action("folder", f[1], AppLanguage.text(this, "Toque para remover da biblioteca"), false, () ->
                new UiDialog.Builder(this).setTitle(f[1]).setMessage(AppLanguage.text(this, "Remover esta pasta da biblioteca? Os ficheiros não são apagados."))
                        .setPositiveButton("Remover", (d, w) -> { db.removeFolder(f[0]); load(); }).setNegativeButton("Cancelar", null).show());
        panel.show();
    }

    void chooseSort() {
        String[] names = new String[SORTS.length];
        for (int i = 0; i < SORTS.length; i++) names[i] = AppLanguage.text(this, SORTS[i]);
        new UiDialog.Builder(this).setTitle("Ordenar").setSingleChoiceItems(names, sort(), (d, w) -> {
            getSharedPreferences("media_library", 0).edit().putInt("sort_" + kind, w).apply();
            d.dismiss();
            load();
        }).show();
    }

    void trackMenu(MediaLibrary.Track t) {
        UiPanel panel = new UiPanel(this, t.title, t.subtitle().isEmpty() ? t.folder : t.subtitle(), video() ? "video" : "audio");
        panel.quick(new String[]{"playfill", "skipnext", "queue"}, new String[]{video() ? "Ver" : "Tocar", "A seguir", "Na fila"}, new Runnable[]{
                () -> { List<MediaLibrary.Track> owner = context.containsKey(t) ? context.get(t) : visible; play(owner.indexOf(t) >= 0 ? owner : Collections.singletonList(t), Math.max(0, owner.indexOf(t)), false); },
                () -> { MediaQueue.insertNext(this, t.item()); PlusUi.toast(this, "Toca a seguir."); },
                () -> { MediaQueue.append(this, t.item()); PlusUi.toast(this, "Adicionado à fila."); }});
        panel.action("star", t.favorite ? "Tirar das favoritas" : "Adicionar às favoritas", null, false, () -> { db.favorite(t.uri, !t.favorite); load(); });
        panel.action("share", "Partilhar", null, false, () -> ShareTools.file(this, Uri.parse(t.uri), t.title, video() ? "video/*" : "audio/*"));
        if (!video() && !t.artist.isEmpty()) panel.action("account", AppLanguage.text(this, "Artista:") + " " + t.artist, null, false, () -> drill("artist", t.artist));
        if (!video() && !t.album.isEmpty()) panel.action("collection", AppLanguage.text(this, "Álbum:") + " " + t.album, null, false, () -> drill("album", t.album));
        panel.action("info", "Detalhes", null, false, () -> details(t));
        panel.danger("Remover da biblioteca", () -> { db.remove(t.uri); load(); });
        panel.show();
    }

    void details(MediaLibrary.Track t) {
        StringBuilder b = new StringBuilder();
        if (!t.artist.isEmpty()) b.append(AppLanguage.text(this, "Artista:")).append(' ').append(t.artist).append('\n');
        if (!t.album.isEmpty()) b.append(AppLanguage.text(this, "Álbum:")).append(' ').append(t.album).append('\n');
        if (!t.genre.isEmpty()) b.append(AppLanguage.text(this, "Género:")).append(' ').append(t.genre).append('\n');
        if (t.year > 0) b.append(AppLanguage.text(this, "Ano:")).append(' ').append(t.year).append('\n');
        if (t.track > 0) b.append(AppLanguage.text(this, "Faixa:")).append(' ').append(t.track).append('\n');
        if (t.width > 0) b.append(AppLanguage.text(this, "Resolução:")).append(' ').append(t.width).append(" × ").append(t.height).append('\n');
        b.append(AppLanguage.text(this, "Duração:")).append(' ').append(ReaderActivity.clock((int) t.duration)).append('\n');
        if (t.size > 0) b.append(AppLanguage.text(this, "Tamanho:")).append(' ').append(Rules.bytes(t.size)).append('\n');
        b.append(AppLanguage.text(this, "Pasta:")).append(' ').append(t.folder).append('\n');
        b.append(AppLanguage.text(this, "Reproduções:")).append(' ').append(t.plays);
        new UiDialog.Builder(this).setTitle(t.title).setMessage(b.toString()).setPositiveButton("Fechar", null).show();
    }

    void drill(String column, String value) { groupColumn = column; groupValue = value; load(); }

    void clicked(Object row) {
        if (row instanceof MediaLibrary.Group) { MediaLibrary.Group g = (MediaLibrary.Group) row; drill(groupColumn(tab), g.name); }
        else if (row instanceof MediaLibrary.Track) { List<MediaLibrary.Track> owner = context.containsKey(row) ? context.get(row) : visible; play(owner, owner.indexOf(row), false); }
        else if (row instanceof Section && ((Section) row).run != null) ((Section) row).run.run();
    }

    // ---- reproduzir ----

    void playAll(boolean shuffle) {
        if (visible.isEmpty()) { PlusUi.toast(this, "Nada para tocar aqui."); return; }
        play(visible, shuffle ? new Random().nextInt(visible.size()) : 0, shuffle);
    }

    void play(List<MediaLibrary.Track> tracks, int index, boolean shuffle) {
        if (tracks.isEmpty() || index < 0) return;
        List<MediaQueue.Item> items = new ArrayList<>();
        for (MediaLibrary.Track t : tracks) items.add(t.item());
        MediaQueue.shuffle = shuffle;
        MediaQueue.set(this, items, index);
        try { startForegroundService(new Intent(this, MediaPlaybackService.class).setAction(MediaPlaybackService.QUEUE)); } catch (Exception e) { AppDiagnostics.record(this, "library-play", e); }
        openPlayer();
    }

    void openPlayer() {
        MediaQueue.load(this);
        MediaQueue.Item it = MediaQueue.current();
        if (it == null) return;
        try {
            startActivity(new Intent(this, MediaActivity.class).setDataAndType(Uri.parse(it.uri), video() ? "video/*" : "audio/*").putExtra("name", it.title));
        } catch (Exception e) { AppDiagnostics.record(this, "library-player", e); }
    }

    void command(String action) {
        try { startForegroundService(new Intent(this, MediaPlaybackService.class).setAction(action)); } catch (Exception e) { AppDiagnostics.record(this, "library-command", e); }
    }

    void refreshMini() {
        MediaPlaybackService s = service;
        if (s == null || s.uri == null) { mini.setVisibility(View.GONE); return; }
        mini.setVisibility(View.VISIBLE);
        miniTitle.setText(MediaActivity.clean(s.title));
        miniSub.setText(s.artist.isEmpty() ? AppLanguage.text(this, s.playing ? "A reproduzir" : "Pausado") : s.artist);
        miniPlay.setSymbol(s.playing ? "pausefill" : "playfill", p.onAccent);
        adapter.notifyDataSetChanged();
    }

    // ---- linhas ----

    final class RowAdapter extends BaseAdapter {
        public int getCount() { return rows.size(); }
        public Object getItem(int i) { return rows.get(i); }
        public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int i) { Object r = rows.get(i); return r == PERIODS ? 2 : r instanceof Section ? 1 : 0; }
        @Override public boolean isEnabled(int i) { Object r = rows.get(i); return r != PERIODS && !(r instanceof Section && ((Section) r).run == null); }

        public View getView(int i, View convert, ViewGroup parent) {
            Object row = rows.get(i);
            if (row == PERIODS) return periods();
            if (row instanceof Section) return sectionView((Section) row, convert);
            Holder h = convert == null ? new Holder() : (Holder) convert.getTag();
            String current = service != null && service.uri != null ? service.uri.toString() : "";
            if (row instanceof MediaLibrary.Track) {
                MediaLibrary.Track t = (MediaLibrary.Track) row;
                boolean playing = t.uri.equals(current);
                h.title.setText(t.title);
                h.title.setTextColor(playing ? p.accent : p.ink);
                h.subtitle.setText(video() ? join(t.folder, t.width > 0 ? t.height + "p" : "", t.size > 0 ? Rules.bytes(t.size) : "") : (t.subtitle().isEmpty() ? AppLanguage.text(h.root.getContext(), "Artista desconhecido") : t.subtitle()));
                h.right.setText(counted.contains(t) ? t.plays + "×" : t.duration > 0 ? ReaderActivity.clock((int) t.duration) : "");
                h.right.setTextColor(counted.contains(t) ? p.accent : p.muted);
                h.more.setVisibility(View.VISIBLE);
                h.more.setOnClickListener(v -> trackMenu(t));
                h.star.setVisibility(t.favorite ? View.VISIBLE : View.GONE);
                loadArt(h.art, h.placeholder, t.uri, video() ? "video" : "audio");
            } else {
                MediaLibrary.Group g = (MediaLibrary.Group) row;
                String column = groupColumn(tab);
                h.title.setText(groupName(column, g.name));
                h.title.setTextColor(p.ink);
                String detail = UiRows.count(h.root.getContext(), g.count, video() ? "vídeo" : "faixa", video() ? "vídeos" : "faixas");
                if ("artist".equals(column) && g.albums > 0) detail += " · " + UiRows.count(h.root.getContext(), g.albums, "álbum", "álbuns");
                h.subtitle.setText(detail);
                h.right.setText(g.duration > 0 ? hours(g.duration) : "");
                h.more.setVisibility(View.GONE);
                h.star.setVisibility(View.GONE);
                String symbol = "folder".equals(column) ? "folder" : "year".equals(column) ? "calendar" : "genre".equals(column) ? "palette" : "artist".equals(column) ? "account" : "collection";
                if ("album".equals(column) || "artist".equals(column)) loadArt(h.art, h.placeholder, g.sample, "audio");
                else { h.art.setImageDrawable(null); h.art.setTag(null); h.placeholder.setSymbol(symbol, p.accent); h.placeholder.setVisibility(View.VISIBLE); }
                if (!"album".equals(column) && !"artist".equals(column)) h.placeholder.setSymbol(symbol, p.accent);
            }
            return h.root;
        }
    }

    View sectionView(Section s, View convert) {
        LinearLayout row = convert instanceof LinearLayout && "section".equals(convert.getTag()) ? (LinearLayout) convert : null;
        if (row == null) {
            row = new LinearLayout(this);
            row.setTag("section");
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label("", 16, p.ink, true), new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(label("", 13, p.accent, true));
        }
        TextView title = (TextView) row.getChildAt(0), more = (TextView) row.getChildAt(1);
        title.setText(AppLanguage.text(this, s.title));
        title.setTextSize(s.note ? 13 : 17);
        title.setTypeface(s.note ? null : ThemeStyle.BOLD);
        title.setTextColor(s.note ? p.muted : p.ink);
        row.setPadding(dp(16), dp(s.note ? 4 : 18), dp(16), dp(s.note ? 10 : 6));
        more.setText(s.action == null ? "" : AppLanguage.text(this, s.action) + " ›");
        return row;
    }

    View periods() {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(14), dp(2), dp(14), dp(6));
        for (int i = 0; i < MediaLibrary.PERIODS.length; i++) {
            final int k = i;
            boolean on = i == period;
            TextView t = label(MediaLibrary.PERIODS[i], 12, on ? p.onAccent : p.ink, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(4), dp(7), dp(4), dp(7));
            t.setMaxLines(1);
            t.setBackground(round(on ? p.accent : p.tint, 14));
            t.setOnClickListener(v -> { period = k; load(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = i < MediaLibrary.PERIODS.length - 1 ? dp(6) : 0;
            row.addView(t, lp);
        }
        return row;
    }

    static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String s : parts) if (s != null && !s.isEmpty()) { if (b.length() > 0) b.append(" · "); b.append(s); }
        return b.toString();
    }

    final class Holder {
        final LinearLayout root = new LinearLayout(LibraryActivity.this);
        final ImageView art = new ImageView(LibraryActivity.this);
        final Icons placeholder = new Icons(LibraryActivity.this, video() ? "video" : "audio", p.accent);
        final TextView title = label("", 15, p.ink, true), subtitle = label("", 12, p.muted, false), right = label("", 12, p.muted, false);
        final Icons star = new Icons(LibraryActivity.this, "star", p.accent);
        final FrameLayout more = new FrameLayout(LibraryActivity.this);

        Holder() {
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(14), dp(7), dp(4), dp(7));
            root.setTag(this);
            FrameLayout frame = new FrameLayout(LibraryActivity.this);
            frame.setBackground(round(p.tint, 10));
            frame.setClipToOutline(true);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(placeholder, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
            frame.addView(art, new FrameLayout.LayoutParams(-1, -1));
            root.addView(frame, new LinearLayout.LayoutParams(dp(video() ? 92 : 50), dp(video() ? 54 : 50)));
            LinearLayout texts = new LinearLayout(LibraryActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, dp(6), 0);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setSingleLine();
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title);
            texts.addView(subtitle);
            root.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            root.addView(star, new LinearLayout.LayoutParams(dp(14), dp(14)));
            right.setPadding(dp(6), 0, 0, 0);
            root.addView(right);
            more.addView(new Icons(LibraryActivity.this, "more", p.muted), new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            more.setContentDescription(AppLanguage.text(LibraryActivity.this, "Opções"));
            root.addView(more, new LinearLayout.LayoutParams(dp(40), dp(44)));
        }
    }

    /** Capa do álbum ou miniatura do vídeo, em segundo plano e com cache. */
    void loadArt(ImageView view, Icons placeholder, String uri, String type) {
        view.setTag(uri);
        Bitmap cached = cache.get(uri);
        if (cached != null) { view.setImageBitmap(cached); placeholder.setVisibility(View.INVISIBLE); return; }
        view.setImageDrawable(null);
        placeholder.setSymbol(type.equals("video") ? "video" : "audio", p.accent);
        placeholder.setVisibility(View.VISIBLE);
        if (uri == null || noArt.contains(uri)) return;
        int size = dp(type.equals("video") ? 160 : 96);
        artWork.execute(() -> {
            Bitmap b = thumbnail(this, Uri.parse(uri), type, size);
            if (b == null) { noArt.add(uri); return; }
            cache.put(uri, b);
            main.post(() -> { if (uri.equals(view.getTag())) { view.setImageBitmap(b); placeholder.setVisibility(View.INVISIBLE); } });
        });
    }

    static Bitmap thumbnail(Context c, Uri uri, String type, int size) {
        if (Build.VERSION.SDK_INT >= 29 && "media".equals(uri.getAuthority())) {
            try { return c.getContentResolver().loadThumbnail(uri, new Size(size, size), null); } catch (Exception ignored) {}
        }
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            if (type.equals("video")) {
                Bitmap frame = r.getFrameAtTime(1000000);
                return frame == null ? null : PdfTools.fit(frame, size);
            }
            byte[] bytes = r.getEmbeddedPicture();
            if (bytes == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            o.inSampleSize = 1;
            while (Math.max(o.outWidth, o.outHeight) / (o.inSampleSize * 2) >= size) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
        } catch (Exception | OutOfMemoryError e) { return null; }
        finally { try { r.release(); } catch (Exception ignored) {} }
    }
}
