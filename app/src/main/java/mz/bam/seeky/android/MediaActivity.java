package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitor de música e de vídeo. O serviço ({@link MediaPlaybackService}) toca; este ecrã mostra e
 * controla, com uma organização fixa:
 *
 * <p><b>Música</b> — cabeçalho (voltar · "A reproduzir" e posição na fila · Mais); separadores
 * <i>Capa · Letra · Fila</i>; título, artista e favorita; barra de progresso; controlos na ordem
 * habitual (aleatório · anterior · reproduzir · seguinte · repetir); barra de acções com nome
 * (Equalizador · Velocidade · Temporizador · Partilhar). Tudo o resto fica no menu Mais,
 * arrumado por secções.
 *
 * <p><b>Vídeo</b> — imagem em ecrã inteiro; tocar mostra/esconde os controlos (escondem-se
 * sozinhos); rodar o ecrã (automático, horizontal, vertical), rodar a imagem 90°, ajuste
 * (ajustar, preencher, esticar), velocidade, fotograma e partilhar.
 */
public final class MediaActivity extends Activity implements TextureView.SurfaceTextureListener {
    static final int COVER = 0, LYRICS = 1, QUEUE = 2;
    static final String[] FITS = {"Ajustar", "Preencher", "Esticar"}, ORIENTATIONS = {"Automático", "Horizontal", "Vertical"};

    final long workspaceEpoch = WorkspaceEpoch.value;
    final Handler handler = new Handler(Looper.getMainLooper());
    MediaPlaybackService service;
    boolean bound, seeking, isVideo, shownPlaying, favorite, overlayShown = true, awaitingClipboard;
    Uri uri;
    String name, clipboardBefore = "";
    AppPalette palette;
    TextView time, remaining, status, speedChip, titleView, subtitleView, queueInfo, lyricSource, fitLabel, orientationLabel;
    FrameLayout play, stage;
    Icons playIcon, repeatIcon, shuffleIcon, heartIcon;
    TextView repeatBadge;
    SeekBar seek;
    TextureView video;
    Surface surface;
    ImageView art;
    View coverFrame, overlay;
    FrameLayout lyricsPanel;
    LinearLayout lyricLines, segments;
    ScrollView lyricScroll;
    ListView queueList;
    BaseAdapter queueAdapter;
    final List<MediaQueue.Item> queueRows = new ArrayList<>();
    Lyrics.Result lyrics;
    int page = COVER, shownSerial = -1, lyricIndex = -2, fit, rotation, orientation, lyricSize = 20;
    String lyricsFor = "";
    /** Transcription task of the shown track (id, state, message, progress, total) and whether it sounds like a talk. */
    String[] transcription;
    boolean speechTrack, refreshingLyrics;
    TextView transcribeStatus;
    java.util.concurrent.ExecutorService aiWorker;
    final Runnable tasksChanged = this::onTasksChanged;

    final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName c, IBinder binder) {
            if (!(binder instanceof MediaPlaybackService.LocalBinder)) return;
            service = ((MediaPlaybackService.LocalBinder) binder).service();
            if (surface != null) service.attach(surface);
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
        public void onServiceDisconnected(ComponentName c) { service = null; }
    };

    int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(AppLanguage.text(this, value));
        t.setTextColor(color);
        t.setTextSize(size);
        return t;
    }

    int ink() { return isVideo ? Color.WHITE : palette.ink; }
    int muted() { return isVideo ? 0xccffffff : palette.muted; }

    /** Botão redondo só com ícone, área de toque de 48 dp e descrição para leitores de ecrã. */
    FrameLayout iconButton(String symbol, String description, int size, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        Icons icon = new Icons(this, symbol, ink());
        b.addView(icon, new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));
        b.setContentDescription(AppLanguage.text(this, description));
        b.setBackground(ripple(Color.TRANSPARENT, 999));
        b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); action.run(); poke(); });
        return b;
    }

    /** Botão com ícone e nome por baixo (barra de acções). */
    LinearLayout action(String symbol, String label, Runnable run) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(2), dp(6), dp(2), dp(6));
        b.setBackground(ripple(Color.TRANSPARENT, 14));
        b.addView(new Icons(this, symbol, ink()), new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView t = text(label, 11, muted());
        t.setPadding(0, dp(4), 0, 0);
        t.setMaxLines(1);
        t.setGravity(Gravity.CENTER);
        b.addView(t, new LinearLayout.LayoutParams(-2, -2));
        b.setContentDescription(AppLanguage.text(this, label));
        b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); run.run(); poke(); });
        return b;
    }

    android.graphics.drawable.Drawable ripple(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radius));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((palette.accent & 0xffffff) | 0x33000000), shape, mask);
    }

    GradientDrawable round(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }

    protected void attachBaseContext(Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        palette = AppPalette.of(this);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        name = getIntent().getStringExtra("name");
        if (name == null) name = "Seeky";
        isVideo = MediaFormats.kind(name, getIntent().getType() == null ? "" : getIntent().getType()).equals("video");
        MediaQueue.load(this);
        if (isVideo) buildVideo(); else buildAudio();
        if (!isVideo) loadArtwork();
        loadFavorite();
        try {
            startForegroundService(new Intent(this, MediaPlaybackService.class).setAction(MediaPlaybackService.OPEN).setData(uri).putExtra("name", name));
        } catch (Exception e) {
            status.setText(AppLanguage.text(this, "Não foi possível reproduzir."));
            AppDiagnostics.record(this, "media-start", e);
        }
    }

    // ---- música ----

    void buildAudio() {
        getWindow().setStatusBarColor(palette.bg);
        getWindow().setNavigationBarColor(palette.bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(palette.bg);
        insets(root);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), dp(4), dp(4), 0);
        head.addView(iconButton("back", "Voltar", 22, this::finish), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        TextView heading = text("A reproduzir", 13, palette.ink);
        heading.setTypeface(ThemeStyle.BOLD);
        heading.setGravity(Gravity.CENTER);
        middle.addView(heading, new LinearLayout.LayoutParams(-2, -2));
        queueInfo = text("", 11, palette.muted);
        queueInfo.setGravity(Gravity.CENTER);
        middle.addView(queueInfo, new LinearLayout.LayoutParams(-2, -2));
        head.addView(middle, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(iconButton("more", "Mais opções", 22, this::menu), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(head);

        segments = new LinearLayout(this);
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        segments.setBackground(round(palette.tint, 18));
        String[][] tabs = {{"Capa", "Capa do álbum"}, {"Letra", "Letra"}, {"Fila", "Fila de reprodução"}};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            TextView t = text(tabs[i][0], 13, palette.ink);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(ThemeStyle.BOLD);
            t.setPadding(0, dp(7), 0, dp(7));
            t.setContentDescription(AppLanguage.text(this, tabs[i][1]));
            t.setOnClickListener(v -> showPage(target));
            segments.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(dp(24), dp(6), dp(24), dp(4));
        root.addView(segments, sp);

        stage = new FrameLayout(this);
        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        art.setElevation(dp(10));
        GradientDrawable artShape = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{palette.heroStart, palette.heroEnd});
        artShape.setCornerRadius(dp(24));
        art.setBackground(artShape);
        art.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        FrameLayout placeholder = new FrameLayout(this);
        placeholder.addView(new Icons(this, "audio", 0xffffffff), new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER));
        placeholder.setElevation(dp(11));
        FrameLayout cover = new FrameLayout(this);
        cover.addView(art, new FrameLayout.LayoutParams(-1, -1));
        cover.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        cover.setTag(placeholder);
        coverFrame = new SquareFrame(this, cover);
        stage.setPadding(dp(28), dp(10), dp(28), dp(6));
        stage.addView(coverFrame, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        buildLyricsPanel();
        stage.addView(lyricsPanel, new FrameLayout.LayoutParams(-1, -1));
        buildQueue();
        stage.addView(queueList, new FrameLayout.LayoutParams(-1, -1));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(24), dp(10), dp(12), 0);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = text("", 20, palette.ink);
        titleView.setText(clean(name));
        ThemeStyle.title(titleView, 20);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setSelected(true);
        texts.addView(titleView);
        subtitleView = text("", 14, palette.muted);
        subtitleView.setText(MediaFormats.ext(name).toUpperCase(Locale.ROOT));
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(subtitleView);
        info.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout heart = iconButton("heart", "Favorita", 24, this::toggleFavorite);
        heartIcon = (Icons) heart.getChildAt(0);
        info.addView(heart, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(info);

        buildSeek(root, palette.line);

        LinearLayout transport = new LinearLayout(this);
        transport.setGravity(Gravity.CENTER);
        transport.setPadding(dp(12), dp(6), dp(12), dp(2));
        FrameLayout shuffle = iconButton("shuffle", "Aleatório", 22, this::toggleShuffle);
        shuffleIcon = (Icons) shuffle.getChildAt(0);
        transport.addView(shuffle, new LinearLayout.LayoutParams(0, dp(52), 1));
        transport.addView(iconButton("skipprev", "Faixa anterior", 26, () -> command(MediaPlaybackService.TRACK_PREV)), new LinearLayout.LayoutParams(0, dp(56), 1));
        buildPlay(74);
        transport.addView(play, new LinearLayout.LayoutParams(dp(74), dp(74)));
        transport.addView(iconButton("skipnext", "Faixa seguinte", 26, () -> command(MediaPlaybackService.TRACK_NEXT)), new LinearLayout.LayoutParams(0, dp(56), 1));
        FrameLayout repeat = iconButton("repeat", "Repetir", 22, this::cycleRepeat);
        repeatIcon = (Icons) repeat.getChildAt(0);
        repeatBadge = text("1", 9, palette.onAccent);
        repeatBadge.setGravity(Gravity.CENTER);
        repeatBadge.setTypeface(ThemeStyle.BOLD);
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(palette.accent);
        repeatBadge.setBackground(badge);
        repeatBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER);
        bp.leftMargin = dp(12);
        bp.topMargin = -dp(10);
        repeat.addView(repeatBadge, bp);
        transport.addView(repeat, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(transport);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(12), dp(8), dp(12), dp(2));
        actions.addView(action("tune", "Equalizador", () -> EqualizerActivity.open(this)), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout speed = new LinearLayout(this);
        speed.setOrientation(LinearLayout.VERTICAL);
        speed.setGravity(Gravity.CENTER);
        speed.setPadding(dp(2), dp(6), dp(2), dp(6));
        speed.setBackground(ripple(Color.TRANSPARENT, 14));
        speedChip = text("1×", 15, palette.ink);
        speedChip.setTypeface(ThemeStyle.BOLD);
        speedChip.setGravity(Gravity.CENTER);
        speed.addView(speedChip, new LinearLayout.LayoutParams(-2, dp(22)));
        TextView sl = text("Velocidade", 11, palette.muted);
        sl.setPadding(0, dp(4), 0, 0);
        speed.addView(sl);
        speed.setContentDescription(AppLanguage.text(this, "Velocidade"));
        speed.setOnClickListener(v -> chooseSpeed());
        actions.addView(speed, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action("timer", "Temporizador", () -> NarrationIndex.sleep(this)), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action("share", "Partilhar", this::share), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        status = text("A preparar…", 11, palette.muted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(8));
        root.addView(status);
        setContentView(root);
        showPage(COVER);
        // A letra é procurada logo (e, se escolhido, transcrita), mesmo antes de abrir o separador Letra.
        handler.post(() -> { if (!isFinishing() && uri != null && lyricLines != null && lyricsFor.isEmpty()) loadLyrics(); });
    }

    void insets(View root) {
        if (Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((v, i) -> {
            android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return i;
        });
    }

    void buildPlay(int size) {
        play = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ThemeColors.blend(palette.accent, Color.WHITE, .12f), palette.accent});
        circle.setShape(GradientDrawable.OVAL);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        play.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40ffffff), circle, mask));
        play.setElevation(dp(6));
        playIcon = new Icons(this, "playfill", palette.onAccent);
        play.addView(playIcon, new FrameLayout.LayoutParams(dp(size * 34 / 76), dp(size * 34 / 76), Gravity.CENTER));
        play.setContentDescription(AppLanguage.text(this, "Reproduzir"));
        play.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); command(service != null && service.playing ? MediaPlaybackService.PAUSE : MediaPlaybackService.PLAY); poke(); });
    }

    void buildSeek(LinearLayout root, int track) {
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(track));
        seek.setContentDescription(AppLanguage.text(this, "Posição da reprodução"));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, dp(34));
        seekParams.setMargins(dp(8), dp(6), dp(8), 0);
        root.addView(seek, seekParams);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) {
                if (user && service != null) time.setText(ReaderActivity.clock((int) ((long) service.duration() * value / 1000)));
            }
            public void onStartTrackingTouch(SeekBar b) { seeking = true; handler.removeCallbacks(hide); }
            public void onStopTrackingTouch(SeekBar b) {
                if (service != null) service.seek((long) service.duration() * b.getProgress() / 1000);
                seeking = false;
                poke();
            }
        });
        LinearLayout times = new LinearLayout(this);
        times.setPadding(dp(24), 0, dp(24), 0);
        time = text("0:00", 12, muted());
        remaining = text("-0:00", 12, muted());
        remaining.setGravity(Gravity.END);
        times.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(remaining, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(times);
    }

    void showPage(int target) {
        page = target;
        coverFrame.setVisibility(target == COVER ? View.VISIBLE : View.INVISIBLE);
        lyricsPanel.setVisibility(target == LYRICS ? View.VISIBLE : View.GONE);
        queueList.setVisibility(target == QUEUE ? View.VISIBLE : View.GONE);
        stage.setPadding(dp(target == COVER ? 28 : 12), dp(10), dp(target == COVER ? 28 : 12), dp(6));
        for (int i = 0; i < segments.getChildCount(); i++) {
            TextView t = (TextView) segments.getChildAt(i);
            boolean on = i == target;
            t.setBackground(on ? round(palette.surface, 15) : null);
            t.setElevation(on ? dp(2) : 0);
            t.setTextColor(on ? palette.ink : palette.muted);
        }
        if (target == LYRICS && !uri.toString().equals(lyricsFor)) loadLyrics();
        if (target == QUEUE) refreshQueue();
    }

    /** Compatibilidade: alterna entre capa e letra. */
    void toggleLyrics() { showPage(page == LYRICS ? COVER : LYRICS); }

    // ---- vídeo ----

    void buildVideo() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        stage = new FrameLayout(this);
        video = new TextureView(this);
        video.setSurfaceTextureListener(this);
        stage.addView(video, new FrameLayout.LayoutParams(-1, -1));
        stage.setOnClickListener(v -> setOverlay(!overlayShown));
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout ui = new LinearLayout(this);
        ui.setOrientation(LinearLayout.VERTICAL);
        overlay = ui;
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(8), dp(4), dp(18));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xcc000000, 0x00000000}));
        top.addView(iconButton("back", "Voltar", 22, this::finish), new LinearLayout.LayoutParams(dp(48), dp(48)));
        titleView = text("", 16, Color.WHITE);
        titleView.setText(clean(name));
        titleView.setTypeface(ThemeStyle.BOLD);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.addView(titleView);
        subtitleView = text("", 11, 0xccffffff);
        subtitleView.setText(MediaFormats.ext(name).toUpperCase(Locale.ROOT));
        tt.addView(subtitleView);
        queueInfo = subtitleView;
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(iconButton("screenrotate", "Rodar ecrã", 22, this::cycleOrientation), new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(iconButton("more", "Mais opções", 22, this::menu), new LinearLayout.LayoutParams(dp(48), dp(48)));
        ui.addView(top);

        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        boolean tracks = MediaQueue.size() > 1;
        if (tracks) center.addView(iconButton("skipprev", "Faixa anterior", 26, () -> command(MediaPlaybackService.TRACK_PREV)), new LinearLayout.LayoutParams(dp(56), dp(56)));
        center.addView(iconButton(MediaSkip.backIcon(this), AppLanguage.text(this, "Recuar") + " " + MediaSkip.seconds(this) + " s", 30, () -> { if (service != null) service.skip(-MediaSkip.ms(this)); }), new LinearLayout.LayoutParams(dp(64), dp(64)));
        buildPlay(72);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(72), dp(72));
        pp.setMargins(dp(18), 0, dp(18), 0);
        center.addView(play, pp);
        center.addView(iconButton(MediaSkip.forwardIcon(this), AppLanguage.text(this, "Avançar") + " " + MediaSkip.seconds(this) + " s", 30, () -> { if (service != null) service.skip(MediaSkip.ms(this)); }), new LinearLayout.LayoutParams(dp(64), dp(64)));
        if (tracks) center.addView(iconButton("skipnext", "Faixa seguinte", 26, () -> command(MediaPlaybackService.TRACK_NEXT)), new LinearLayout.LayoutParams(dp(56), dp(56)));
        ui.addView(center, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(0, dp(18), 0, dp(10));
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xdd000000, 0x00000000}));
        buildSeek(bottom, 0x55ffffff);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(8), dp(4), dp(8), 0);
        LinearLayout rotate = action("screenrotate", "Rodar ecrã", this::cycleOrientation);
        orientationLabel = (TextView) rotate.getChildAt(1);
        actions.addView(rotate, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout turn = action("rotate", "Rodar 90°", this::rotateImage);
        turn.setContentDescription(AppLanguage.text(this, "Rodar imagem"));
        actions.addView(turn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout fitting = action("aspect", "Ajustar", this::cycleFit);
        fitLabel = (TextView) fitting.getChildAt(1);
        actions.addView(fitting, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout speed = new LinearLayout(this);
        speed.setOrientation(LinearLayout.VERTICAL);
        speed.setGravity(Gravity.CENTER);
        speed.setPadding(dp(2), dp(6), dp(2), dp(6));
        speedChip = text("1×", 15, Color.WHITE);
        speedChip.setTypeface(ThemeStyle.BOLD);
        speedChip.setGravity(Gravity.CENTER);
        speed.addView(speedChip, new LinearLayout.LayoutParams(-2, dp(22)));
        TextView sl = text("Velocidade", 11, 0xccffffff);
        sl.setPadding(0, dp(4), 0, 0);
        speed.addView(sl);
        speed.setContentDescription(AppLanguage.text(this, "Velocidade"));
        speed.setOnClickListener(v -> { chooseSpeed(); poke(); });
        actions.addView(speed, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(action("share", "Partilhar", this::share), new LinearLayout.LayoutParams(0, -2, 1));
        bottom.addView(actions);
        status = text("A preparar…", 11, 0xaaffffff);
        status.setGravity(Gravity.CENTER);
        bottom.addView(status);
        ui.addView(bottom);
        root.addView(ui, new FrameLayout.LayoutParams(-1, -1));
        insets(ui);
        setContentView(root);
        poke();
    }

    final Runnable hide = () -> { if (isVideo && service != null && service.playing && !seeking) setOverlay(false); };

    /** Volta a contar os 4 s para esconder os controlos do vídeo. */
    void poke() {
        if (!isVideo) return;
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, 4000);
    }

    void setOverlay(boolean show) {
        overlayShown = show;
        if (overlay != null) overlay.animate().alpha(show ? 1f : 0f).setDuration(180).withEndAction(() -> { if (!overlayShown) overlay.setVisibility(View.GONE); }).start();
        if (show && overlay != null) overlay.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (show) c.show(WindowInsets.Type.systemBars());
                else { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
            }
        }
        if (show) poke();
    }

    void cycleOrientation() {
        orientation = (orientation + 1) % ORIENTATIONS.length;
        setRequestedOrientation(orientation == 1 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : orientation == 2 ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        if (orientationLabel != null) orientationLabel.setText(AppLanguage.text(this, ORIENTATIONS[orientation]));
        toast(AppLanguage.text(this, "Ecrã:") + " " + AppLanguage.text(this, ORIENTATIONS[orientation]));
    }

    void rotateImage() {
        rotation = (rotation + 90) % 360;
        fitVideo();
        toast(AppLanguage.text(this, "Imagem rodada") + " " + rotation + "°");
    }

    void cycleFit() {
        fit = (fit + 1) % FITS.length;
        if (fitLabel != null) fitLabel.setText(AppLanguage.text(this, FITS[fit]));
        fitVideo();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (video != null) video.post(this::fitVideo);
    }

    /** Ajustar (cabe todo), preencher (sem barras pretas) ou esticar, com a rotação escolhida. */
    void fitVideo() {
        if (video == null || service == null || service.player == null || !service.ready) return;
        int w, h;
        try { w = service.player.getVideoWidth(); h = service.player.getVideoHeight(); } catch (Exception e) { return; }
        if (w < 1 || h < 1 || video.getWidth() < 1 || video.getHeight() < 1) return;
        video.setTransform(videoMatrix(video.getWidth(), video.getHeight(), w, h, rotation, fit));
    }

    static Matrix videoMatrix(float vw, float vh, int w, int h, int rotation, int fit) {
        boolean side = rotation % 180 != 0;
        float rw = side ? h : w, rh = side ? w : h;
        float boxW, boxH;
        if (fit == 2) { boxW = side ? vh : vw; boxH = side ? vw : vh; }
        else {
            float s = fit == 1 ? Math.max(vw / rw, vh / rh) : Math.min(vw / rw, vh / rh);
            boxW = w * s;
            boxH = h * s;
        }
        Matrix m = new Matrix();
        m.setScale(boxW / vw, boxH / vh, vw / 2, vh / 2);
        if (rotation != 0) m.postRotate(rotation, vw / 2, vh / 2);
        return m;
    }

    // ---- favorita, aleatório, repetir ----

    void loadFavorite() {
        final String u = uri.toString();
        new Thread(() -> {
            boolean f = false;
            try (MediaLibrary db = new MediaLibrary(this)) { List<MediaLibrary.Track> t = db.tracks(isVideo ? MediaLibrary.VIDEO : MediaLibrary.AUDIO, "uri=?", new String[]{u}, null, 1); f = !t.isEmpty() && t.get(0).favorite; } catch (Exception ignored) {}
            final boolean fav = f;
            handler.post(() -> { if (u.equals(uri.toString())) { favorite = fav; refreshHeart(); } });
        }, "seeky-favorite").start();
    }

    void refreshHeart() { if (heartIcon != null) heartIcon.setSymbol(favorite ? "heartfill" : "heart", favorite ? palette.accent : palette.ink); }

    void toggleFavorite() {
        favorite = !favorite;
        refreshHeart();
        final String u = uri.toString(), t = titleView.getText().toString(), a = service != null ? service.artist : "";
        final boolean on = favorite;
        new Thread(() -> MediaLibrary.setFavorite(this, u, t, a, isVideo ? MediaLibrary.VIDEO : MediaLibrary.AUDIO, on), "seeky-favorite").start();
        toast(on ? "Adicionada às favoritas" : "Retirada das favoritas");
    }

    void toggleShuffle() {
        MediaQueue.setShuffle(this, !MediaQueue.shuffle);
        refreshRepeat();
        if (page == QUEUE) refreshQueue();
        toast(MediaQueue.shuffle ? "Aleatório activado" : "Aleatório desligado");
    }

    void cycleRepeat() {
        MediaQueue.cycleRepeat(this);
        if (service != null) service.repeat = MediaQueue.repeat == MediaQueue.REPEAT_ONE;
        refreshRepeat();
        toast(MediaQueue.repeat == MediaQueue.REPEAT_ONE ? "Repetir esta faixa" : MediaQueue.repeat == MediaQueue.REPEAT_ALL ? "Repetir tudo" : "Repetir desligado");
    }

    void refreshRepeat() {
        if (repeatIcon != null) {
            boolean on = MediaQueue.repeat != MediaQueue.REPEAT_OFF || (service != null && service.repeat);
            repeatIcon.setSymbol("repeat", on ? palette.accent : palette.ink);
            if (repeatBadge != null) repeatBadge.setVisibility(MediaQueue.repeat == MediaQueue.REPEAT_ONE || (service != null && service.repeat) ? View.VISIBLE : View.GONE);
        }
        if (shuffleIcon != null) shuffleIcon.setSymbol("shuffle", MediaQueue.shuffle ? palette.accent : palette.ink);
        if (queueInfo != null && !isVideo) {
            int size = MediaQueue.size(), pos = MediaQueue.position + 1;
            queueInfo.setText(size > 1 ? AppLanguage.text(this, "Faixa") + " " + pos + " " + AppLanguage.text(this, "de") + " " + size + (MediaQueue.shuffle ? " · " + AppLanguage.text(this, "aleatório") : "") : "");
        }
    }

    void toast(String message) { Toast.makeText(this, AppLanguage.text(this, message), Toast.LENGTH_SHORT).show(); }

    // ---- menus ----

    /** Menu Mais, arrumado por secções. */
    void menu() {
        UiPanel p = new UiPanel(this, "Mais opções", clean(titleView.getText().toString()), isVideo ? "video" : "audio");
        p.section("Reprodução");
        if (!isVideo) p.quick(new String[]{MediaSkip.backIcon(this), MediaSkip.forwardIcon(this), "timer"}, new String[]{MediaSkip.back(this), MediaSkip.forward(this), "Temporizador"}, new Runnable[]{
                () -> { if (service != null) service.skip(-MediaSkip.ms(this)); }, () -> { if (service != null) service.skip(MediaSkip.ms(this)); }, () -> NarrationIndex.sleep(this)});
        else {
            p.action("screenrotate", AppLanguage.text(this, "Rodar ecrã:") + " " + AppLanguage.text(this, ORIENTATIONS[orientation]), "Automático, horizontal ou vertical", false, this::cycleOrientation);
            p.action("rotate", "Rodar imagem 90°", "Para vídeos gravados de lado", false, this::rotateImage);
            p.action("aspect", AppLanguage.text(this, "Ajuste:") + " " + AppLanguage.text(this, FITS[fit]), "Ajustar, preencher ou esticar", false, this::cycleFit);
            p.action("timer", "Temporizador de sono", null, false, () -> NarrationIndex.sleep(this));
        }
        p.action("speed", AppLanguage.text(this, "Velocidade:") + " " + speedLabel(service == null ? 1 : service.speed), null, false, this::chooseSpeed);
        p.action("contents", "Capítulos e títulos", "Saltar para uma marca do ficheiro", false, () -> NarrationIndex.show(this));
        p.section(isVideo ? "Este vídeo" : "Esta faixa");
        if (!isVideo) {
            p.action(favorite ? "heartfill" : "heart", favorite ? "Retirar das favoritas" : "Adicionar às favoritas", null, false, this::toggleFavorite);
            p.action("lyrics", "Letra", "Ver, procurar online, colar ou copiar", false, () -> showPage(LYRICS));
            p.action("queue", "Fila de reprodução", null, false, () -> showPage(QUEUE));
        }
        p.action("share", "Partilhar", null, false, this::share);
        p.action("info", "Detalhes", null, false, this::details);
        p.action("more", "Abrir noutra aplicação", null, false, this::outside);
        p.section("Biblioteca");
        p.action(isVideo ? "video" : "audio", isVideo ? "Abrir a biblioteca de vídeos" : "Abrir a biblioteca de música", null, false, () -> LibraryActivity.open(this, isVideo ? MediaLibrary.VIDEO : MediaLibrary.AUDIO));
        if (!isVideo) p.action("tune", "Equalizador e efeitos", null, false, () -> EqualizerActivity.open(this));
        p.danger("Parar reprodução", () -> { command(MediaPlaybackService.STOP); finish(); });
        p.show();
    }

    void details() {
        final Uri target = uri;
        new Thread(() -> {
            StringBuilder b = new StringBuilder();
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, target);
                String[][] keys = {{"Título:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)}, {"Artista:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)},
                        {"Álbum:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)}, {"Género:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)},
                        {"Ano:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)}, {"Taxa de bits:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)},
                        {"Resolução:", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) == null ? null : r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) + " × " + r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)}};
                for (String[] k : keys) if (k[1] != null && !k[1].trim().isEmpty()) b.append(AppLanguage.text(this, k[0])).append(' ').append(k[0].equals("Taxa de bits:") ? Math.round(Long.parseLong(k[1].trim()) / 1000.0) + " kbps" : k[1].trim()).append('\n');
            } catch (Exception ignored) {} finally { try { r.release(); } catch (Exception ignored) {} }
            b.append(AppLanguage.text(this, "Ficheiro:")).append(' ').append(name).append('\n').append(AppLanguage.text(this, "Formato:")).append(' ').append(MediaFormats.ext(name).toUpperCase(Locale.ROOT));
            if (service != null) b.append('\n').append(AppLanguage.text(this, "Duração:")).append(' ').append(ReaderActivity.clock(service.duration()));
            final String text = b.toString();
            handler.post(() -> new UiDialog.Builder(this).setTitle("Detalhes").setMessage(text).setPositiveButton("Fechar", null).show());
        }, "seeky-details").start();
    }

    String mime() { return MediaFormats.mime(name, getIntent().getType()); }

    /** Partilhar enquanto toca: o ficheiro, o que está a ouvir (texto) ou, no vídeo, o fotograma actual. */
    void share() {
        UiPanel p = new UiPanel(this, "Partilhar", name, "share");
        p.action(isVideo ? "video" : "audio", isVideo ? "Partilhar vídeo" : "Partilhar música", AppLanguage.text(this, "Envia o ficheiro para WhatsApp, e-mail, Bluetooth…"), false, () -> ShareTools.file(this, uri, name, mime()));
        if (!isVideo) p.action("note", "Partilhar o que estou a ouvir", AppLanguage.text(this, "Título e artista em texto"), false, () -> {
            String title = titleView == null ? name : titleView.getText().toString(), artist = service == null ? "" : service.artist;
            ShareTools.text(this, AppLanguage.text(this, "A ouvir:") + " " + title + (artist.isEmpty() ? "" : " — " + artist));
        });
        if (!isVideo && lyrics != null && lyrics.found()) p.action("lyrics", "Partilhar a letra", null, false, () -> ShareTools.text(this, clean(titleView.getText().toString()) + "\n\n" + lyrics.text));
        if (isVideo && video != null) p.action("image", "Partilhar este fotograma", AppLanguage.text(this, "Imagem do momento actual do vídeo"), false, () -> {
            Bitmap frame = video.getBitmap();
            if (frame == null) toast("O vídeo ainda não mostrou nenhuma imagem.");
            else ShareTools.bitmap(this, frame, name.replaceAll("\\.[^.]+$", "") + " - fotograma.jpg", false);
        });
        p.action("more", "Abrir noutra aplicação", null, false, this::outside);
        p.show();
    }

    void outside() {
        try {
            Catalog.Entry e = new Catalog.Entry();
            e.uri = uri.toString();
            Uri shared = StorageAccess.shareUri(this, e);
            Intent outside = new Intent(Intent.ACTION_VIEW).setDataAndType(shared, mime()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            outside.setClipData(ClipData.newRawUri(name, shared));
            startActivity(Intent.createChooser(outside, AppLanguage.text(this, "Abrir fora")));
        } catch (Exception e) { toast("Formato indisponível."); }
    }

    // ---- fila ----

    void buildQueue() {
        queueList = new ListView(this);
        queueList.setDivider(null);
        queueList.setVisibility(View.GONE);
        queueList.setBackground(round(palette.surface, 20));
        queueList.setPadding(0, dp(6), 0, dp(6));
        queueList.setClipToPadding(false);
        queueList.setContentDescription(AppLanguage.text(this, "Fila de reprodução"));
        queueAdapter = new BaseAdapter() {
            public int getCount() { return queueRows.size(); }
            public Object getItem(int i) { return queueRows.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View convert, ViewGroup parent) {
                LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : queueRow();
                MediaQueue.Item it = queueRows.get(i);
                boolean now = i == 0;
                TextView n = (TextView) row.getChildAt(0), t = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(0), a = (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1);
                n.setText(now ? "▶" : String.valueOf(i));
                n.setTextColor(now ? palette.accent : palette.muted);
                t.setText(clean(it.title));
                t.setTextColor(now ? palette.accent : palette.ink);
                a.setText(it.artist.isEmpty() ? AppLanguage.text(MediaActivity.this, "Artista desconhecido") : it.artist);
                return row;
            }
        };
        queueList.setAdapter(queueAdapter);
        queueList.setOnItemClickListener((parent, view, i, id) -> {
            int original = MediaQueue.items.indexOf(queueRows.get(i));
            if (i > 0 && MediaQueue.jump(this, original) != null) command(MediaPlaybackService.QUEUE);
        });
    }

    LinearLayout queueRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(9), dp(14), dp(9));
        TextView n = text("", 13, palette.muted);
        n.setGravity(Gravity.CENTER);
        row.addView(n, new LinearLayout.LayoutParams(dp(30), -2));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(8), 0, 0, 0);
        TextView t = text("", 15, palette.ink);
        t.setTypeface(ThemeStyle.BOLD);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        TextView a = text("", 12, palette.muted);
        a.setSingleLine();
        a.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        texts.addView(a);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    void refreshQueue() {
        if (queueList == null) return;
        MediaQueue.load(this);
        queueRows.clear();
        queueRows.addAll(MediaQueue.upcoming());
        queueAdapter.notifyDataSetChanged();
    }

    /** Compatibilidade: abre o separador da fila. */
    void queuePanel() { showPage(QUEUE); }

    // ---- faixa mudou (fila) ----

    void trackChanged() {
        refreshRepeat();
        if (page == QUEUE) refreshQueue();
        if (service == null || service.uri == null || service.uri.equals(uri)) return;
        uri = service.uri;
        name = service.title;
        titleView.setText(clean(name));
        subtitleView.setText(service.artist.isEmpty() ? MediaFormats.ext(name).toUpperCase(Locale.ROOT) : service.artist);
        if (!isVideo) { art.setImageDrawable(null); View cover = (View) art.getParent(); Object ph = cover.getTag(); if (ph instanceof View) ((View) ph).setVisibility(View.VISIBLE); loadArtwork(); }
        lyrics = null;
        lyricsFor = "";
        loadFavorite();
        if (!isVideo) loadLyrics();
    }

    // ---- letras ----

    LinearLayout chip(String symbol, String label, Runnable run) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), dp(6), dp(12), dp(6));
        c.setBackground(round(palette.tint, 14));
        c.addView(new Icons(this, symbol, palette.ink), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView t = text(label, 12, palette.ink);
        t.setTypeface(ThemeStyle.BOLD);
        t.setPadding(dp(6), 0, 0, 0);
        c.addView(t);
        c.setContentDescription(AppLanguage.text(this, label));
        c.setOnClickListener(v -> run.run());
        return c;
    }

    void buildLyricsPanel() {
        lyricsPanel = new FrameLayout(this);
        lyricsPanel.setVisibility(View.GONE);
        lyricsPanel.setBackground(round(palette.surface, 20));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        lyricSource = text("", 11, palette.muted);
        lyricSource.setPadding(dp(16), dp(10), dp(16), dp(4));
        box.addView(lyricSource);
        HorizontalScrollView bar = new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(dp(12), dp(2), dp(12), dp(6));
        View[] items = {chip("mic", "Transcrever com IA", this::transcribe), chip("globe", "Procurar online", this::searchOnline), chip("search", "Google", this::searchWeb), chip("paste", "Colar", this::pasteLyrics),
                chip("copy", "Copiar", this::copyLyrics), chip("edit", "Editar", this::editLyrics), chip("type", "A−", () -> resizeLyrics(-2)), chip("type", "A+", () -> resizeLyrics(2))};
        for (View v : items) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(6); tools.addView(v, lp); }
        bar.addView(tools);
        box.addView(bar);
        lyricScroll = new ScrollView(this);
        lyricScroll.setVerticalScrollBarEnabled(false);
        lyricLines = new LinearLayout(this);
        lyricLines.setOrientation(LinearLayout.VERTICAL);
        lyricLines.setPadding(dp(20), dp(8), dp(20), dp(140));
        lyricScroll.addView(lyricLines);
        box.addView(lyricScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        lyricsPanel.addView(box, new FrameLayout.LayoutParams(-1, -1));
        lyricSize = getSharedPreferences("lyrics", 0).getInt("size", 20);
    }

    String[] trackMeta() {
        String t = clean(titleView.getText().toString()), a = service != null ? service.artist : "";
        return new String[]{t, a};
    }

    void loadLyrics() {
        final Uri target = uri;
        lyricsFor = target.toString();
        lyricIndex = -2;
        lyricLines.removeAllViews();
        lyricSource.setText(AppLanguage.text(this, "A procurar a letra…"));
        final String[] meta = trackMeta();
        final long duration = service != null ? service.duration() : 0;
        final String file = name == null ? "" : name;
        new Thread(() -> {
            String t = meta[0], a = meta[1], al = "";
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            long length = duration;
            try {
                r.setDataSource(this, target);
                String mt = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), ma = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), mb = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), md = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (mt != null && !mt.trim().isEmpty()) t = mt.trim();
                if (ma != null && !ma.trim().isEmpty()) a = ma.trim();
                if (mb != null) al = mb.trim();
                if (length <= 0 && md != null) try { length = Long.parseLong(md.trim()); } catch (NumberFormatException ignored) {}
            } catch (Exception ignored) {} finally { try { r.release(); } catch (Exception ignored) {} }
            Lyrics.Result result = Lyrics.find(this, target, t, a, al, length, Lyrics.online(this), file);
            boolean speech = LyricsText.speech(t, file, length);
            String[] state = null;
            if (!result.found() || result.source.equals(TranscribeWork.PARTIAL)) {
                state = TranscribeWork.status(this, target.toString());
                if (!result.found() && TranscribeWork.prefs(this).getBoolean("consent", false) && TranscribeWork.automatic(this, target.toString())) {
                    try { TranscribeWork.enqueue(this, target, file, t, length); state = TranscribeWork.status(this, target.toString()); }
                    catch (Exception e) { AppDiagnostics.record(this, "transcribe-auto", e); }
                }
            }
            final String[] task = state;
            handler.post(() -> { if (!isFinishing() && target.toString().equals(lyricsFor)) { transcription = task; speechTrack = speech; showLyricsResult(result); } });
        }, "seeky-lyrics").start();
    }

    /** While a transcription runs: show each new piece of text and the progress, without blocking the screen. */
    void onTasksChanged() {
        if (isFinishing() || uri == null || refreshingLyrics || !uri.toString().equals(lyricsFor)) return;
        if (transcription == null && (lyrics == null || lyrics.found())) return;
        refreshingLyrics = true;
        final Uri target = uri;
        new Thread(() -> {
            String[] state = TranscribeWork.status(this, target.toString());
            Lyrics.Result saved = Lyrics.cached(this, target.toString());
            handler.post(() -> {
                refreshingLyrics = false;
                if (isFinishing() || !target.toString().equals(lyricsFor)) return;
                transcription = state;
                boolean changed = saved != null && (lyrics == null || !lyrics.found() || !saved.source.equals(lyrics.source) || saved.synced.size() != lyrics.synced.size());
                if (changed || saved == null && lyrics != null && !lyrics.found()) showLyricsResult(saved == null ? new Lyrics.Result() : saved);
                else if (transcribeStatus != null) transcribeStatus.setText(statusText(state));
            });
        }, "seeky-lyrics-task").start();
    }
    String statusText(String[] s) {
        if (s == null) return "";
        if (TranscribeWork.busy(s)) return s[2].startsWith("A transcrever") ? AppLanguage.text(this, s[2]) : AppLanguage.text(this, "A transcrever com IA") + (s[2].isEmpty() || s[2].equals("Em espera") ? "…" : " · " + AppLanguage.text(this, s[2]));
        if (s[1].equals("paused")) return AppLanguage.text(this, "Transcrição em pausa") + (s[2].isEmpty() ? "" : " · " + AppLanguage.text(this, s[2]));
        if (s[1].equals("failed")) return AppLanguage.text(this, "A transcrição falhou") + (s[2].isEmpty() ? "" : ": " + AppLanguage.text(this, s[2]));
        if (s[1].equals("complete")) return AppLanguage.text(this, s[2]);
        return "";
    }

    /** "Transcrever com IA": needs the Gemini connection and, once, permission to send the audio. */
    void transcribe() {
        if (uri == null || isVideo) return;
        if (!TranscribeWork.ready(this)) {
            new UiDialog.Builder(this).setTitle("Transcrever com IA").setMessage(AppLanguage.text(this, "Para escrever a letra de qualquer áudio (pregações, aulas, músicas sem letra na internet), ligue o Seeky ao Gemini com uma chave gratuita do Google AI Studio. Depois toque outra vez em Transcrever com IA."))
                    .setPositiveButton("Ligar a IA", (d, w) -> { if (aiWorker == null) aiWorker = java.util.concurrent.Executors.newSingleThreadExecutor(); SeekyAiSettings.show(this, new SeekyAiVault(this), aiWorker, () -> { if (TranscribeWork.ready(this)) transcribe(); }); })
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        if (!TranscribeWork.prefs(this).getBoolean("consent", false)) {
            new UiDialog.Builder(this).setTitle("Transcrever com IA").setMessage(AppLanguage.text(this, "O som desta faixa é enviado ao Google Gemini, comprimido (cerca de 0,25 MB por minuto), com a sua chave, para escrever o que é dito ou cantado com os tempos. Usa a quota gratuita da sua conta. A letra fica guardada no telefone e acompanha a música. Em músicas protegidas por direitos de autor o Gemini pode recusar.\n\nQuer que o Seeky faça isto sozinho sempre que não encontrar a letra?"))
                    .setPositiveButton("Sempre automático", (d, w) -> { TranscribeWork.prefs(this).edit().putBoolean("consent", true).apply(); TranscribeWork.setMode(this, "auto"); startTranscription(); })
                    .setNeutralButton("Só esta vez", (d, w) -> { TranscribeWork.prefs(this).edit().putBoolean("consent", true).apply(); startTranscription(); })
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        startTranscription();
    }
    void startTranscription() {
        final Uri target = uri;
        final String file = name == null ? "" : name, title = trackMeta()[0];
        final long duration = service != null ? service.duration() : 0;
        if (TranscribeWork.wifiOnly(this) && !TranscribeWork.network(this)) toast("A transcrição está definida para Wi-Fi; começa mesmo assim porque pediu.");
        new Thread(() -> {
            String[] state = null;
            try { TranscribeWork.enqueue(this, target, file, title, duration); state = TranscribeWork.status(this, target.toString()); }
            catch (Exception e) { AppDiagnostics.record(this, "transcribe-start", e); }
            final String[] task = state;
            handler.post(() -> {
                if (isFinishing() || !target.toString().equals(lyricsFor)) return;
                if (task == null) { toast("Não foi possível iniciar a transcrição."); return; }
                transcription = task;
                if (page != LYRICS) showPage(LYRICS);
                if (lyrics == null || !lyrics.found()) showLyricsResult(new Lyrics.Result());
                toast("A transcrever em segundo plano: a letra aparece aos poucos.");
            });
        }, "seeky-transcribe").start();
    }

    void showLyricsResult(Lyrics.Result r) {
        lyrics = r;
        lyricLines.removeAllViews();
        lyricIndex = -2;
        transcribeStatus = null;
        if (!r.found()) {
            boolean running = TranscribeWork.busy(transcription);
            lyricSource.setText(AppLanguage.text(this, running ? "A transcrever a letra com IA" : "Letra não encontrada"));
            TextView t = text(running ? "A escrever a letra com IA…" : speechTrack ? "Este áudio parece uma pregação, aula ou conversa." : "Não encontrámos a letra desta música.", 16, palette.ink);
            t.setTypeface(ThemeStyle.BOLD);
            t.setPadding(0, dp(16), 0, dp(6));
            lyricLines.addView(t);
            String hint = running ? "Pode continuar a ouvir ou sair: a letra aparece aqui aos poucos, sincronizada com o áudio."
                    : speechTrack ? "Estes áudios não têm letra na internet. Transcreva com IA: o Seeky escreve tudo o que é dito, com os tempos, e guarda."
                    : "Transcreva com IA, procure online e escolha a versão certa, ou abra o Google, copie a letra e volte: o Seeky aplica-a sozinho.";
            TextView how = text(hint, 14, palette.muted);
            how.setPadding(0, 0, 0, dp(10));
            lyricLines.addView(how);
            String state = statusText(transcription);
            if (!state.isEmpty()) { transcribeStatus = text(state, 13, running ? palette.accent : palette.muted); transcribeStatus.setPadding(0, 0, 0, dp(12)); lyricLines.addView(transcribeStatus); }
            if (running) { lyricLines.addView(bigButton("tasks", "Ver tarefas em segundo plano", () -> TaskService.open(this))); return; }
            lyricLines.addView(bigButton("mic", transcription != null && (transcription[1].equals("paused") || transcription[1].equals("failed")) ? "Retomar a transcrição com IA" : "Transcrever com IA", this::transcribe));
            if (!speechTrack || transcription != null) lyricLines.addView(bigButton("globe", "Procurar online", this::searchOnline));
            lyricLines.addView(bigButton("search", "Procurar no Google e copiar", this::searchWeb));
            lyricLines.addView(bigButton("paste", "Colar letra copiada", this::pasteLyrics));
            if (TranscribeWork.mode(this).equals("ask") && TranscribeWork.ready(this) && !TranscribeWork.prefs(this).getBoolean("consent", false)) { TextView auto = text("Dica: escolha \"Sempre automático\" e o Seeky transcreve sozinho tudo o que não tiver letra.", 12, palette.muted); auto.setPadding(0, dp(6), 0, 0); lyricLines.addView(auto); }
            return;
        }
        lyricSource.setText(AppLanguage.text(this, "Letra:") + " " + AppLanguage.text(this, r.source) + (r.isSynced() ? " · " + AppLanguage.text(this, "sincronizada") : ""));
        if (r.instrumental && r.text.isEmpty()) { lyricLines.addView(text("♪ Instrumental ♪", 20, palette.muted)); return; }
        if (r.isSynced()) {
            for (Lyrics.Line line : r.synced) {
                TextView t = text(line.text.isEmpty() ? "♪" : line.text, lyricSize, palette.muted);
                t.setTypeface(ThemeStyle.BOLD);
                t.setPadding(0, dp(7), 0, dp(7));
                final long at = line.time;
                t.setOnClickListener(v -> { if (service != null) service.seek(at); });
                t.setOnLongClickListener(v -> { copy(((TextView) v).getText().toString()); return true; });
                lyricLines.addView(t);
            }
        } else {
            TextView t = text(r.text, lyricSize - 3, palette.ink);
            t.setLineSpacing(dp(4), 1f);
            t.setTextIsSelectable(true);
            lyricLines.addView(t);
        }
        if (r.source.startsWith("Transcrição")) {
            boolean partial = r.source.equals(TranscribeWork.PARTIAL);
            String note = partial ? statusText(transcription) : "";
            TextView foot = text((note.isEmpty() ? "" : note + "\n") + AppLanguage.text(this, partial ? "A letra continua a chegar enquanto a IA transcreve o resto do áudio." : "Transcrição automática: pode ter pequenos erros. Corrija em Editar."), 12, palette.muted);
            foot.setPadding(0, dp(14), 0, dp(4));
            if (partial) transcribeStatus = foot;
            lyricLines.addView(foot);
        }
    }

    LinearLayout bigButton(String symbol, String label, Runnable run) {
        LinearLayout b = chip(symbol, label, run);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    void syncLyrics(long position) {
        if (page != LYRICS || lyrics == null || !lyrics.isSynced()) return;
        int i = Lyrics.current(lyrics.synced, position + 250);
        if (i == lyricIndex || i >= lyricLines.getChildCount()) return;
        if (lyricIndex >= 0 && lyricIndex < lyricLines.getChildCount()) { TextView old = (TextView) lyricLines.getChildAt(lyricIndex); old.setTextColor(palette.muted); old.setTextSize(lyricSize); }
        lyricIndex = i;
        if (i < 0) return;
        TextView now = (TextView) lyricLines.getChildAt(i);
        now.setTextColor(palette.accent);
        now.setTextSize(lyricSize + 3);
        lyricScroll.post(() -> lyricScroll.smoothScrollTo(0, Math.max(0, now.getTop() - lyricScroll.getHeight() / 3)));
    }

    void resizeLyrics(int delta) {
        lyricSize = Math.max(14, Math.min(32, lyricSize + delta));
        getSharedPreferences("lyrics", 0).edit().putInt("size", lyricSize).apply();
        if (lyrics != null) showLyricsResult(lyrics);
    }

    void apply(String text, String source) {
        String clean = Lyrics.cleanPasted(text);
        if (clean.isEmpty()) return;
        Lyrics.store(this, uri.toString(), clean, source);
        showLyricsResult(Lyrics.result(clean, source));
    }

    void copy(String text) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Letra", text));
        toast("Copiado.");
    }

    void copyLyrics() {
        if (lyrics == null || !lyrics.found() || lyrics.text.isEmpty()) { toast("Ainda não há letra para copiar."); return; }
        copy(lyrics.text);
    }

    String clipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence t = clip.getItemAt(0).coerceToText(this);
            return t == null ? "" : t.toString();
        } catch (Exception e) { return ""; }
    }

    void pasteLyrics() {
        String text = clipboard();
        if (text.trim().isEmpty()) { toast("A área de transferência está vazia. Copie a letra primeiro."); return; }
        apply(text, "Colada");
        toast("Letra aplicada.");
    }

    /** Abre a pesquisa no navegador; ao voltar, se tiver copiado uma letra, é aplicada sozinha. */
    void searchWeb() {
        String[] m = trackMeta();
        clipboardBefore = clipboard();
        awaitingClipboard = true;
        String q = (m[0] + " " + m[1] + " " + AppLanguage.text(this, "letra")).trim();
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))); }
        catch (Exception e) { awaitingClipboard = false; toast("Não há navegador disponível."); }
        toast("Copie a letra e volte ao Seeky: é aplicada automaticamente.");
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus && awaitingClipboard) checkClipboard();
    }

    void checkClipboard() {
        String text = clipboard();
        if (text.equals(clipboardBefore) || !Lyrics.looksLikeLyrics(text)) return;
        awaitingClipboard = false;
        if (page != LYRICS) showPage(LYRICS);
        apply(text, "Copiada da internet");
        toast("Letra copiada aplicada automaticamente.");
    }

    /** Pesquisa na LRCLIB com o texto editável e lista de resultados; o escolhido é aplicado logo. */
    void searchOnline() {
        String[] m = trackMeta();
        EditText q = new EditText(this);
        q.setSingleLine(true);
        q.setText((m[0] + " " + m[1]).trim());
        q.setSelection(q.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(q);
        new UiDialog.Builder(this).setTitle("Procurar letra online").setMessage(AppLanguage.text(this, "Título e artista (pode alterar)")).setView(box)
                .setPositiveButton("Procurar", (d, w) -> runSearch(q.getText().toString())).setNegativeButton("Cancelar", null).show();
    }

    void runSearch(String query) {
        toast("A procurar…");
        new Thread(() -> {
            List<Lyrics.Candidate> found;
            String error = null;
            try { found = Lyrics.search(query); } catch (Exception e) { found = new ArrayList<>(); error = e.getMessage(); }
            final List<Lyrics.Candidate> list = found;
            final String fail = error;
            handler.post(() -> { if (!isFinishing()) results(query, list, fail); });
        }, "seeky-lyrics-search").start();
    }

    void results(String query, List<Lyrics.Candidate> list, String error) {
        UiPanel p = new UiPanel(this, "Resultados", query, "globe");
        if (list.isEmpty()) p.note(error != null ? AppLanguage.text(this, "Sem ligação à internet ou serviço indisponível.") : AppLanguage.text(this, "Nenhuma letra encontrada. Tente só o título, ou procure no Google e copie."));
        for (Lyrics.Candidate c : list) p.action(c.synced ? "lyrics" : "type", c.track, c.detail() + " · " + AppLanguage.text(this, c.synced ? "sincronizada" : "texto"), false, () -> {
            Lyrics.Result r = Lyrics.fromJson(c.json);
            Lyrics.store(this, uri.toString(), r.isSynced() ? Lyrics.lrcText(r) : r.text, "LRCLIB");
            if (page != LYRICS) showPage(LYRICS);
            showLyricsResult(r);
            toast("Letra aplicada.");
        });
        p.action("search", "Procurar no Google", "Copie a letra e volte: aplica-se sozinha", false, this::searchWeb);
        p.show();
    }

    void editLyrics() {
        EditText input = new EditText(this);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP);
        input.setHint(AppLanguage.text(this, "Cole aqui a letra (texto ou LRC com [mm:ss])"));
        if (lyrics != null && lyrics.found()) input.setText(lyrics.isSynced() ? Lyrics.lrcText(lyrics) : lyrics.text);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input);
        new UiDialog.Builder(this).setTitle("Editar letra").setView(box).setPositiveButton("Guardar", (d, w) -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) { Lyrics.forget(this, uri.toString()); loadLyrics(); return; }
            apply(text, "Seeky");
        }).setNeutralButton("Remover", (d, w) -> { Lyrics.forget(this, uri.toString()); showLyricsResult(new Lyrics.Result()); })
                .setNegativeButton("Cancelar", null).show();
    }

    // ---- capa ----

    /** Lê título, artista, álbum e capa incorporados (ID3/MP4) fora da thread principal. */
    void loadArtwork() {
        final Uri target = uri;
        new Thread(() -> {
            String title = null, artist = null, album = null;
            Bitmap picture = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, target);
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                byte[] bytes = r.getEmbeddedPicture();
                if (bytes != null && bytes.length < 8 * 1024 * 1024) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                    o.inSampleSize = 1;
                    while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > 1024) o.inSampleSize *= 2;
                    o.inJustDecodeBounds = false;
                    picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
                }
            } catch (Exception | OutOfMemoryError ignored) {
            } finally {
                try { r.release(); } catch (Exception ignored) {}
            }
            final String t = title, sub = join(artist, album);
            final Bitmap b = picture;
            handler.post(() -> {
                if (isFinishing() || isDestroyed() || !target.equals(uri)) return;
                if (t != null && !t.trim().isEmpty()) titleView.setText(t.trim());
                if (!sub.isEmpty()) subtitleView.setText(sub);
                if (b != null) {
                    art.setImageBitmap(b);
                    View cover = (View) art.getParent();
                    Object placeholder = cover.getTag();
                    if (placeholder instanceof View) ((View) placeholder).setVisibility(View.GONE);
                    int tone = averageColor(b);
                    View root = (View) stage.getParent();
                    GradientDrawable wash = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                            new int[]{ThemeColors.blend(palette.bg, tone, palette.light ? .22f : .35f), palette.bg});
                    root.setBackground(wash);
                    getWindow().setStatusBarColor(ThemeColors.blend(palette.bg, tone, palette.light ? .22f : .35f));
                }
            });
        }, "seeky-media-art").start();
    }

    static String clean(String file) { return file.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "").replace('_', ' '); }

    static String join(String a, String b) {
        String x = a == null ? "" : a.trim(), y = b == null ? "" : b.trim();
        return x.isEmpty() ? y : y.isEmpty() ? x : x + " · " + y;
    }

    /** Cor média de uma versão reduzida da capa; suficiente para um fundo discreto. */
    static int averageColor(Bitmap b) {
        Bitmap small = Bitmap.createScaledBitmap(b, 16, 16, true);
        long r = 0, g = 0, bl = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int c = small.getPixel(x, y);
            r += (c >> 16) & 255; g += (c >> 8) & 255; bl += c & 255;
        }
        if (small != b) small.recycle();
        return 0xff000000 | (int) (r / 256) << 16 | (int) (g / 256) << 8 | (int) (bl / 256);
    }

    // ---- velocidade e comandos ----

    void chooseSpeed() {
        final float[] values = {.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = new String[values.length];
        int current = 2;
        for (int i = 0; i < values.length; i++) {
            labels[i] = speedLabel(values[i]);
            if (service != null && Math.abs(service.speed - values[i]) < .01f) current = i;
        }
        new UiDialog.Builder(this).setTitle("Velocidade").setSingleChoiceItems(labels, current, (d, n) -> {
            if (service != null) service.speed(values[n]);
            d.dismiss();
            refreshSpeed();
        }).show();
    }

    static String speedLabel(float v) {
        return (v == (int) v ? String.valueOf((int) v) : String.valueOf(v).replace(".0", "")) + "×";
    }

    void refreshSpeed() { if (service != null && speedChip != null) speedChip.setText(speedLabel(service.speed)); }

    void command(String action) {
        try { startForegroundService(new Intent(this, MediaPlaybackService.class).setAction(action)); }
        catch (Exception e) { AppDiagnostics.record(this, "media-command", e); }
    }

    final Runnable tick = new Runnable() {
        public void run() {
            if (service != null) {
                int duration = service.duration(), position = service.position();
                if (!seeking) {
                    seek.setProgress(duration == 0 ? 0 : (int) (1000L * position / duration));
                    time.setText(ReaderActivity.clock(position));
                }
                remaining.setText("-" + ReaderActivity.clock(Math.max(0, duration - position)));
                if (service.serial != shownSerial) { shownSerial = service.serial; trackChanged(); }
                syncLyrics(position);
                if (shownPlaying != service.playing || playIcon.getTag() == null) {
                    boolean started = !shownPlaying && service.playing;
                    shownPlaying = service.playing;
                    playIcon.setTag(Boolean.TRUE);
                    playIcon.setSymbol(service.playing ? "pausefill" : "playfill", palette.onAccent);
                    play.setContentDescription(AppLanguage.text(MediaActivity.this, service.playing ? "Pausar" : "Reproduzir"));
                    if (started) poke();
                }
                status.setText(AppLanguage.text(MediaActivity.this, !service.error.isEmpty() ? service.error : !service.ready ? "A preparar…" : isVideo ? "" : "Reprodução em segundo plano"));
                refreshSpeed();
                refreshRepeat();
                fitVideo();
            }
            handler.postDelayed(this, page == LYRICS ? 250 : 500);
        }
    };

    protected void onStart() {
        super.onStart();
        if (workspaceEpoch != WorkspaceEpoch.value) { finish(); return; }
        bound = bindService(new Intent(this, MediaPlaybackService.class), connection, BIND_AUTO_CREATE);
        UiPanel.visibility(this, true);
        TaskEvents.add(tasksChanged);
    }

    protected void onStop() {
        TaskEvents.remove(tasksChanged);
        handler.removeCallbacks(tick);
        if (service != null) service.attach(null);
        if (bound) { unbindService(connection); bound = false; }
        service = null;
        UiPanel.visibility(this, false);
        super.onStop();
    }

    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        UiPanel.dismissAll(this);
        if (surface != null) { surface.release(); surface = null; }
        if (aiWorker != null) aiWorker.shutdown();
        super.onDestroy();
    }

    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        surface = new Surface(texture);
        if (service != null) service.attach(surface);
    }
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) { fitVideo(); }
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        if (service != null) service.attach(null);
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

    /** Mantém a capa quadrada e centrada em qualquer orientação. */
    static final class SquareFrame extends FrameLayout {
        SquareFrame(Context c, View child) { super(c); addView(child, new FrameLayout.LayoutParams(-1, -1)); }
        @Override protected void onMeasure(int w, int h) {
            int side = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
            int spec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
            super.onMeasure(spec, spec);
        }
    }
}
