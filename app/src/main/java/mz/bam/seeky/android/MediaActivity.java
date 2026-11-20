package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.Locale;

/**
 * Leitor de música e vídeo. O serviço em primeiro plano ({@link MediaPlaybackService}) mantém o
 * descodificador e o foco de áudio; este ecrã só mostra e controla.
 *
 * <p>Música: capa do álbum (imagem incorporada no ficheiro, com fundo em degradê tirado das
 * suas cores), título, artista e álbum. Vídeo: palco preto com ajuste da proporção.
 * Controlos com ícones: recuar/avançar 10 s, reproduzir/pausar circular, velocidade,
 * repetir e temporizador de sono.
 */
public final class MediaActivity extends Activity implements TextureView.SurfaceTextureListener {
    final long workspaceEpoch = WorkspaceEpoch.value;
    final Handler handler = new Handler(Looper.getMainLooper());
    MediaPlaybackService service;
    boolean bound, seeking, isVideo;
    Uri uri;
    String name;
    AppPalette palette;
    TextView time, remaining, status, speedChip, titleView, subtitleView;
    FrameLayout play;
    Icons playIcon, repeatIcon;
    SeekBar seek;
    TextureView video;
    Surface surface;
    FrameLayout stage;
    ImageView art;
    boolean shownPlaying;
    int shownSerial = -1, lyricIndex = -2;
    Icons shuffleIcon;
    TextView repeatBadge, lyricSource;
    FrameLayout lyricsPanel;
    LinearLayout lyricLines;
    ScrollView lyricScroll;
    View coverFrame;
    Lyrics.Result lyrics;
    boolean showLyrics;
    String lyricsFor = "";

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

    /** Botão redondo só com ícone, área de toque de 48 dp e descrição para leitores de ecrã. */
    FrameLayout iconButton(String symbol, String description, int size, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        Icons icon = new Icons(this, symbol, palette.ink);
        b.addView(icon, new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));
        b.setContentDescription(AppLanguage.text(this, description));
        b.setBackground(ripple(Color.TRANSPARENT, 999));
        b.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); action.run(); });
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

    protected void attachBaseContext(Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        palette = AppPalette.of(this);
        uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        name = getIntent().getStringExtra("name");
        if (name == null) name = "Seeky";
        isVideo = MediaFormats.kind(name, getIntent().getType() == null ? "" : getIntent().getType()).equals("video");
        getWindow().setStatusBarColor(isVideo ? Color.BLACK : palette.bg);
        getWindow().setNavigationBarColor(palette.bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(palette.bg);
        if (Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((v, i) -> {
            android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return i;
        });

        // Cabeçalho: voltar · "A reproduzir" · mais opções
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), dp(4), dp(4), dp(4));
        head.addView(iconButton("back", "Voltar", 22, this::finish), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = text(isVideo ? "Vídeo" : "A reproduzir", 13, palette.muted);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        if (!isVideo) head.addView(iconButton("tune", "Equalizador", 22, () -> EqualizerActivity.open(this)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        head.addView(iconButton("share", "Partilhar", 22, this::share), new LinearLayout.LayoutParams(dp(48), dp(48)));
        head.addView(iconButton("more", "Mais opções", 22, this::menu), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(head);

        stage = new FrameLayout(this);
        if (isVideo) {
            stage.setBackgroundColor(Color.BLACK);
            video = new TextureView(this);
            video.setSurfaceTextureListener(this);
            stage.addView(video, new FrameLayout.LayoutParams(-1, -1));
            root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            // Capa: quadrada, cantos arredondados, sombra; fundo em degradê com as cores da capa.
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
            placeholder.setElevation(dp(11)); // acima da sombra da capa
            FrameLayout cover = new FrameLayout(this);
            cover.addView(art, new FrameLayout.LayoutParams(-1, -1));
            cover.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
            cover.setTag(placeholder);
            stage.setPadding(dp(32), dp(12), dp(32), dp(12));
            coverFrame = new SquareFrame(this, cover);
            stage.addView(coverFrame, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            buildLyricsPanel();
            stage.addView(lyricsPanel, new FrameLayout.LayoutParams(-1, -1));
            root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        // Título e subtítulo (artista · álbum, ou formato)
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(24), dp(16), dp(24), dp(4));
        titleView = text("", 20, palette.ink);
        titleView.setText(clean(name));
        ThemeStyle.title(titleView,20);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setSelected(true);
        info.addView(titleView);
        subtitleView = text("", 14, palette.muted);
        subtitleView.setText(MediaFormats.ext(name).toUpperCase(Locale.ROOT));
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(subtitleView);
        root.addView(info);

        // Barra de progresso com tempo decorrido e restante
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.line));
        seek.setContentDescription(AppLanguage.text(this, "Posição da reprodução"));
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(-1, dp(36));
        seekParams.setMargins(dp(8), dp(8), dp(8), 0);
        root.addView(seek, seekParams);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) {
                if (user && service != null) time.setText(ReaderActivity.clock((int) ((long) service.duration() * value / 1000)));
            }
            public void onStartTrackingTouch(SeekBar b) { seeking = true; }
            public void onStopTrackingTouch(SeekBar b) {
                if (service != null) service.seek((long) service.duration() * b.getProgress() / 1000);
                seeking = false;
            }
        });
        LinearLayout times = new LinearLayout(this);
        times.setPadding(dp(24), 0, dp(24), 0);
        time = text("0:00", 12, palette.muted);
        remaining = text("-0:00", 12, palette.muted);
        remaining.setGravity(Gravity.END);
        times.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(remaining, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(times);

        // Controlos principais
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(12), 0, dp(4));
        boolean tracks = !isVideo || MediaQueue.size() > 1;
        if (tracks) controls.addView(iconButton("skipprev", "Faixa anterior", 26, () -> command(MediaPlaybackService.TRACK_PREV)), new LinearLayout.LayoutParams(dp(52), dp(52)));
        controls.addView(iconButton("replay10", "Recuar 10 segundos", 28, () -> { if (service != null) service.skip(-10000); }), new LinearLayout.LayoutParams(dp(56), dp(56)));
        play = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ThemeColors.blend(palette.accent, Color.WHITE, .12f), palette.accent});
        circle.setShape(GradientDrawable.OVAL);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        play.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40ffffff), circle, mask));
        play.setElevation(dp(6));
        playIcon = new Icons(this, "playfill", palette.onAccent);
        play.addView(playIcon, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        play.setContentDescription(AppLanguage.text(this, "Reproduzir"));
        play.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); command(service != null && service.playing ? MediaPlaybackService.PAUSE : MediaPlaybackService.PLAY); });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        playParams.setMargins(dp(tracks ? 12 : 28), 0, dp(tracks ? 12 : 28), 0);
        controls.addView(play, playParams);
        controls.addView(iconButton("forward10", "Avançar 10 segundos", 28, () -> { if (service != null) service.skip(10000); }), new LinearLayout.LayoutParams(dp(56), dp(56)));
        if (tracks) controls.addView(iconButton("skipnext", "Faixa seguinte", 26, () -> command(MediaPlaybackService.TRACK_NEXT)), new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(controls);

        // Controlos secundários: velocidade · repetir · temporizador · capítulos
        LinearLayout extras = new LinearLayout(this);
        extras.setGravity(Gravity.CENTER);
        extras.setPadding(dp(16), dp(4), dp(16), dp(8));
        speedChip = text("1×", 14, palette.ink);
        speedChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        speedChip.setGravity(Gravity.CENTER);
        speedChip.setBackground(ripple(palette.tint, 18));
        speedChip.setContentDescription(AppLanguage.text(this, "Velocidade"));
        speedChip.setOnClickListener(v -> chooseSpeed());
        extras.addView(speedChip, new LinearLayout.LayoutParams(dp(64), dp(40)));
        MediaQueue.load(this);
        FrameLayout shuffle = iconButton("shuffle", "Aleatório", 22, () -> {
            MediaQueue.setShuffle(this, !MediaQueue.shuffle);
            refreshRepeat();
            toast(MediaQueue.shuffle ? "Aleatório activado" : "Aleatório desligado");
        });
        shuffleIcon = (Icons) shuffle.getChildAt(0);
        extras.addView(shuffle, spaced());
        FrameLayout repeat = iconButton("repeat", "Repetir", 22, () -> {
            MediaQueue.cycleRepeat(this);
            if (service != null) service.repeat = MediaQueue.repeat == MediaQueue.REPEAT_ONE;
            refreshRepeat();
            toast(MediaQueue.repeat == MediaQueue.REPEAT_ONE ? "Repetir esta faixa" : MediaQueue.repeat == MediaQueue.REPEAT_ALL ? "Repetir tudo" : "Repetir desligado");
        });
        repeatIcon = (Icons) repeat.getChildAt(0);
        repeatBadge = text("1", 9, palette.onAccent);
        repeatBadge.setGravity(Gravity.CENTER);
        repeatBadge.setTypeface(ThemeStyle.BOLD);
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(palette.accent);
        repeatBadge.setBackground(badge);
        repeatBadge.setVisibility(View.GONE);
        repeat.addView(repeatBadge, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP | Gravity.END));
        extras.addView(repeat, spaced());
        if (!isVideo) extras.addView(iconButton("type", "Letra", 22, this::toggleLyrics), spaced());
        extras.addView(iconButton("tasks", "Fila de reprodução", 22, this::queuePanel), spaced());
        extras.addView(iconButton("clock", "Temporizador de sono", 22, () -> NarrationIndex.sleep(this)), spaced());
        extras.addView(iconButton("contents", "Capítulos e títulos", 22, () -> NarrationIndex.show(this)), spaced());
        HorizontalScrollView extraScroll = new HorizontalScrollView(this);
        extraScroll.setHorizontalScrollBarEnabled(false);
        extraScroll.addView(extras);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, -2);
        ep.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(extraScroll, ep);

        status = text("A preparar…", 12, palette.muted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, dp(10));
        root.addView(status);
        setContentView(root);

        if (!isVideo) loadArtwork();
        try {
            startForegroundService(new Intent(this, MediaPlaybackService.class).setAction(MediaPlaybackService.OPEN).setData(uri).putExtra("name", name));
        } catch (Exception e) {
            status.setText(AppLanguage.text(this, "Não foi possível reproduzir."));
            AppDiagnostics.record(this, "media-start", e);
        }
    }

    LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
        p.setMargins(dp(14), 0, 0, 0);
        return p;
    }

    static String clean(String file) { return file.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "").replace('_', ' '); }

    void toast(String message) { Toast.makeText(this, AppLanguage.text(this, message), Toast.LENGTH_SHORT).show(); }

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
                if (isFinishing() || isDestroyed()) return;
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

    void chooseSpeed() {
        final float[] values = {.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = new String[values.length];
        int current = 1;
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

    void refreshSpeed() { if (service != null) speedChip.setText(speedLabel(service.speed)); }

    void refreshRepeat() {
        if (repeatIcon == null) return;
        boolean on = MediaQueue.repeat != MediaQueue.REPEAT_OFF || (service != null && service.repeat);
        repeatIcon.setSymbol("repeat", on ? palette.accent : palette.ink);
        ((View) repeatIcon.getParent()).setBackground(ripple(on ? palette.tint : Color.TRANSPARENT, 999));
        if (repeatBadge != null) repeatBadge.setVisibility(MediaQueue.repeat == MediaQueue.REPEAT_ONE || (service != null && service.repeat) ? View.VISIBLE : View.GONE);
        if (shuffleIcon != null) {
            shuffleIcon.setSymbol("shuffle", MediaQueue.shuffle ? palette.accent : palette.ink);
            ((View) shuffleIcon.getParent()).setBackground(ripple(MediaQueue.shuffle ? palette.tint : Color.TRANSPARENT, 999));
        }
    }

    // ---- faixa mudou (fila) ----

    void trackChanged() {
        if (service == null || service.uri == null || service.uri.equals(uri)) return;
        uri = service.uri;
        name = service.title;
        titleView.setText(clean(name));
        subtitleView.setText(service.artist.isEmpty() ? MediaFormats.ext(name).toUpperCase(Locale.ROOT) : service.artist);
        if (!isVideo) { art.setImageDrawable(null); View cover = (View) art.getParent(); Object ph = cover.getTag(); if (ph instanceof View) ((View) ph).setVisibility(View.VISIBLE); loadArtwork(); }
        lyrics = null;
        lyricsFor = "";
        if (showLyrics) loadLyrics(false);
    }

    void queuePanel() {
        MediaQueue.load(this);
        List<MediaQueue.Item> next = MediaQueue.upcoming();
        UiPanel p = new UiPanel(this, "Fila de reprodução", UiRows.count(this, MediaQueue.size(), "faixa", "faixas") + (MediaQueue.shuffle ? " · " + AppLanguage.text(this, "aleatório") : ""), "tasks");
        if (next.size() <= 1) p.note("Só há esta faixa. Abra Mais → Música para tocar álbuns, artistas ou listas inteiras.");
        for (int i = 0; i < Math.min(100, next.size()); i++) {
            MediaQueue.Item it = next.get(i);
            final int original = MediaQueue.items.indexOf(it);
            p.action(i == 0 ? "playfill" : "audio", (i == 0 ? "▶ " : (i + ". ")) + clean(it.title), it.artist, false, () -> {
                if (MediaQueue.jump(this, original) != null) command(MediaPlaybackService.QUEUE);
            });
        }
        p.show();
    }

    // ---- letras ----

    void buildLyricsPanel() {
        lyricsPanel = new FrameLayout(this);
        lyricsPanel.setVisibility(View.GONE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor((palette.surface & 0xffffff) | 0xee000000);
        bg.setCornerRadius(dp(24));
        lyricsPanel.setBackground(bg);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(8), dp(4), 0);
        lyricSource = text("", 11, palette.muted);
        head.addView(lyricSource, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(iconButton("edit", "Editar letra", 18, this::editLyrics), new LinearLayout.LayoutParams(dp(40), dp(40)));
        head.addView(iconButton("refresh", "Procurar de novo", 18, () -> { Lyrics.forget(this, uri.toString()); loadLyrics(true); }), new LinearLayout.LayoutParams(dp(40), dp(40)));
        box.addView(head);
        lyricScroll = new ScrollView(this);
        lyricScroll.setVerticalScrollBarEnabled(false);
        lyricLines = new LinearLayout(this);
        lyricLines.setOrientation(LinearLayout.VERTICAL);
        lyricLines.setPadding(dp(20), dp(8), dp(20), dp(160));
        lyricScroll.addView(lyricLines);
        box.addView(lyricScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        lyricsPanel.addView(box, new FrameLayout.LayoutParams(-1, -1));
    }

    void toggleLyrics() {
        showLyrics = !showLyrics;
        lyricsPanel.setVisibility(showLyrics ? View.VISIBLE : View.GONE);
        coverFrame.setVisibility(showLyrics ? View.INVISIBLE : View.VISIBLE);
        stage.setPadding(dp(showLyrics ? 14 : 32), dp(12), dp(showLyrics ? 14 : 32), dp(12));
        if (showLyrics && !uri.toString().equals(lyricsFor)) loadLyrics(false);
    }

    void loadLyrics(boolean force) {
        final Uri target = uri;
        lyricsFor = target.toString();
        lyricIndex = -2;
        lyricLines.removeAllViews();
        lyricSource.setText(AppLanguage.text(this, "A procurar a letra…"));
        final String title = titleView.getText().toString(), artist = service != null ? service.artist : "", album = "";
        final long duration = service != null ? service.duration() : 0;
        final String[] meta = {title, artist};
        new Thread(() -> {
            String t = meta[0], a = meta[1], al = album;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, target);
                String mt = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), ma = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), mb = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                if (mt != null && !mt.trim().isEmpty()) t = mt.trim();
                if (ma != null && !ma.trim().isEmpty()) a = ma.trim();
                if (mb != null) al = mb.trim();
            } catch (Exception ignored) {} finally { try { r.release(); } catch (Exception ignored) {} }
            Lyrics.Result result = Lyrics.find(this, target, t, a, al, duration, Lyrics.online(this));
            handler.post(() -> { if (!isFinishing() && target.toString().equals(lyricsFor)) showLyricsResult(result); });
        }, "seeky-lyrics").start();
    }

    void showLyricsResult(Lyrics.Result r) {
        lyrics = r;
        lyricLines.removeAllViews();
        lyricIndex = -2;
        if (!r.found()) {
            lyricSource.setText(AppLanguage.text(this, "Letra não encontrada"));
            TextView t = text(Lyrics.online(this) ? "Não encontrámos a letra desta música. Toque no lápis para a colar ou escrever." : "Active as letras da internet em Música → Mais opções, ou toque no lápis para colar a letra.", 15, palette.muted);
            t.setPadding(0, dp(24), 0, 0);
            lyricLines.addView(t);
            return;
        }
        lyricSource.setText(AppLanguage.text(this, "Letra:") + " " + AppLanguage.text(this, r.source) + (r.isSynced() ? " · " + AppLanguage.text(this, "sincronizada") : ""));
        if (r.instrumental && r.text.isEmpty()) { lyricLines.addView(text("♪ Instrumental ♪", 20, palette.muted)); return; }
        if (r.isSynced()) {
            for (Lyrics.Line line : r.synced) {
                TextView t = text(line.text.isEmpty() ? "♪" : line.text, 20, palette.muted);
                t.setTypeface(ThemeStyle.BOLD);
                t.setPadding(0, dp(7), 0, dp(7));
                final long at = line.time;
                t.setOnClickListener(v -> { if (service != null) service.seek(at); });
                lyricLines.addView(t);
            }
        } else {
            TextView t = text(r.text, 17, palette.ink);
            t.setLineSpacing(dp(4), 1f);
            t.setTextIsSelectable(true);
            lyricLines.addView(t);
        }
    }

    void syncLyrics(long position) {
        if (!showLyrics || lyrics == null || !lyrics.isSynced()) return;
        int i = Lyrics.current(lyrics.synced, position + 250);
        if (i == lyricIndex || i >= lyricLines.getChildCount()) return;
        if (lyricIndex >= 0 && lyricIndex < lyricLines.getChildCount()) { TextView old = (TextView) lyricLines.getChildAt(lyricIndex); old.setTextColor(palette.muted); old.setTextSize(20); }
        lyricIndex = i;
        if (i < 0) return;
        TextView now = (TextView) lyricLines.getChildAt(i);
        now.setTextColor(palette.accent);
        now.setTextSize(23);
        lyricScroll.post(() -> lyricScroll.smoothScrollTo(0, Math.max(0, now.getTop() - lyricScroll.getHeight() / 3)));
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
            if (text.isEmpty()) { Lyrics.forget(this, uri.toString()); loadLyrics(false); return; }
            Lyrics.store(this, uri.toString(), text, "Seeky");
            showLyricsResult(Lyrics.result(text, "Seeky"));
        }).setNegativeButton("Cancelar", null).show();
    }

    void command(String action) {
        try { startForegroundService(new Intent(this, MediaPlaybackService.class).setAction(action)); }
        catch (Exception e) { AppDiagnostics.record(this, "media-command", e); }
    }

    String mime() { return MediaFormats.mime(name, getIntent().getType()); }

    /** Partilhar enquanto toca: o ficheiro, o que está a ouvir (texto) ou, no vídeo, o fotograma actual. */
    void share() {
        UiPanel p = new UiPanel(this, "Partilhar", name, "share");
        p.action(isVideo ? "video" : "audio", isVideo ? "Partilhar vídeo" : "Partilhar música", AppLanguage.text(this, "Envia o ficheiro para WhatsApp, e-mail, Bluetooth…"), false, () -> ShareTools.file(this, uri, name, mime()));
        if (!isVideo) p.action("note", "Partilhar o que estou a ouvir", AppLanguage.text(this, "Título e artista em texto"), false, () -> {
            String title = titleView == null ? name : titleView.getText().toString(), artist = subtitleView == null ? "" : subtitleView.getText().toString();
            ShareTools.text(this, AppLanguage.text(this, "A ouvir:") + " " + title + (artist.isEmpty() ? "" : " — " + artist));
        });
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

    void menu() {
        new UiDialog.Builder(this).setTitle("Reprodução").setItems(new String[]{"Velocidade", "Repetir", "Abrir fora", "Parar reprodução", "Capítulos e títulos", "Temporizador de sono", "Partilhar"}, (d, n) -> {
            if (n == 6) { share(); return; }
            if (n == 0) chooseSpeed();
            else if (n == 1) { if (service != null) { service.repeat = !service.repeat; refreshRepeat(); } }
            else if (n == 2) {
                try {
                    Catalog.Entry e = new Catalog.Entry();
                    e.uri = uri.toString();
                    Uri shared = StorageAccess.shareUri(this, e);
                    Intent outside = new Intent(Intent.ACTION_VIEW).setDataAndType(shared, MediaFormats.mime(name, getIntent().getType())).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    outside.setClipData(ClipData.newRawUri(name, shared));
                    startActivity(Intent.createChooser(outside, AppLanguage.text(this, "Abrir fora")));
                } catch (Exception e) { toast("Formato indisponível."); }
            } else if (n == 4) NarrationIndex.show(this);
            else if (n == 5) NarrationIndex.sleep(this);
            else { command(MediaPlaybackService.STOP); finish(); }
        }).show();
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
                    shownPlaying = service.playing;
                    playIcon.setTag(Boolean.TRUE);
                    playIcon.setSymbol(service.playing ? "pausefill" : "playfill", palette.onAccent);
                    play.setContentDescription(AppLanguage.text(MediaActivity.this, service.playing ? "Pausar" : "Reproduzir"));
                }
                status.setText(AppLanguage.text(MediaActivity.this, !service.error.isEmpty() ? service.error : !service.ready ? "A preparar…" : service.repeat ? "Repetir activado" : "Reprodução em segundo plano"));
                refreshSpeed();
                refreshRepeat();
                fitVideo();
            }
            handler.postDelayed(this, showLyrics ? 250 : 500);
        }
    };

    protected void onStart() {
        super.onStart();
        if (workspaceEpoch != WorkspaceEpoch.value) { finish(); return; }
        bound = bindService(new Intent(this, MediaPlaybackService.class), connection, BIND_AUTO_CREATE);
    }

    protected void onStop() {
        handler.removeCallbacks(tick);
        if (service != null) service.attach(null);
        if (bound) { unbindService(connection); bound = false; }
        service = null;
        super.onStop();
    }

    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (surface != null) { surface.release(); surface = null; }
        super.onDestroy();
    }

    void fitVideo() {
        if (video == null || service == null || service.player == null || !service.ready) return;
        int w = service.player.getVideoWidth(), h = service.player.getVideoHeight();
        if (w < 1 || h < 1 || video.getWidth() < 1 || video.getHeight() < 1) return;
        float vw = video.getWidth(), vh = video.getHeight(), scale = Math.min(vw / w, vh / h);
        Matrix m = new Matrix();
        m.setScale(w * scale / vw, h * scale / vh, vw / 2, vh / 2);
        video.setTransform(m);
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
