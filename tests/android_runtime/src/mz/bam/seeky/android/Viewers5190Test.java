package mz.bam.seeky.android;

import android.app.Application;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import java.io.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.19.0: leitor de música e visualizador de imagens com ícones, com capturas. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, qualifiers = "w360dp-h760dp-mdpi", application = Application.class, shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Viewers5190Test {
    static File fixture;

    public static class Files extends ContentProvider {
        public boolean onCreate() { return true; }
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY); }
        public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{u.getLastPathSegment(), fixture.length()});
            return c;
        }
        public String getType(Uri u) { return u.getLastPathSegment().endsWith(".png") ? "image/png" : "audio/wav"; }
        public Uri insert(Uri u, ContentValues v) { return null; }
        public int delete(Uri u, String s, String[] a) { return 0; }
        public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
    }

    Application app;

    @Before public void before() {
        app = RuntimeEnvironment.getApplication();
        Files provider = new Files();
        ProviderInfo info = new ProviderInfo();
        info.authority = "seeky.viewers.test";
        provider.attachInfo(app, info);
        ShadowContentResolver.registerProviderInternal(info.authority, provider);
    }

    void idle(long ms) { shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS); }

    void capture(View v, String name, int w, int h) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(System.getProperty("seeky.screenshot") + "-5190-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
        b.recycle();
    }

    @Test public void musicPlayerUsesIconControlsAndRemainingTime() throws Exception {
        fixture = new File(app.getCacheDir(), "song.wav");
        VoiceToolsTest.writeWav(fixture, new byte[48000]);
        MediaPlaybackService media = Robolectric.buildService(MediaPlaybackService.class).create().get();
        shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app, MediaPlaybackService.class), media.onBind(null));
        ActivityController<MediaActivity> c = Robolectric.buildActivity(MediaActivity.class,
                new Intent(app, MediaActivity.class).setDataAndType(Uri.parse("content://seeky.viewers.test/Minha_musica.wav"), "audio/wav").putExtra("name", "Minha_musica.wav"));
        MediaActivity a = c.setup().get();
        idle(600);
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        String labels = BooksToolsTest.labels(root);
        assertTrue(labels.contains("Minha musica"));
        assertFalse("no text-only transport buttons", labels.contains("Reproduzir") || labels.contains("−10 s"));
        assertEquals("Reproduzir", a.play.getContentDescription().toString());
        assertNotNull(VoiceToolsTest.findText(root, "1×"));
        assertNotNull("partilhar no cabeçalho", PageTurnTest.findDescription(root, "Partilhar"));
        capture(root, "musica", 360, 760);
        a.share();
        idle(400);
        UiPanel share = UiPanel.open.get(a).iterator().next();
        String options = BooksToolsTest.labels(share.root);
        assertTrue(options, options.contains("Partilhar música") && options.contains("Partilhar o que estou a ouvir") && options.contains("Abrir noutra aplicação"));
        capture(share.root, "musica-partilhar", 340, 420);
        UiPanel.dismissAll(a);
        c.pause().stop().destroy();
    }

    @Test public void imageViewerShowsDetailsAndIconControls() throws Exception {
        fixture = new File(app.getCacheDir(), "foto.png");
        Bitmap b = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(0xff2a6f97);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffffd34d);
        canvas.drawCircle(1150, 330, 170, p);
        p.setColor(0xff1b4332);
        Path hills = new Path();
        hills.moveTo(0, 1200); hills.lineTo(0, 800); hills.quadTo(500, 520, 900, 820); hills.quadTo(1250, 620, 1600, 760); hills.lineTo(1600, 1200); hills.close();
        canvas.drawPath(hills, p);
        try (FileOutputStream out = new FileOutputStream(fixture)) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
        b.recycle();
        ActivityController<ReaderActivity> c = Robolectric.buildActivity(ReaderActivity.class,
                new Intent(app, ReaderActivity.class).setDataAndType(Uri.parse("content://seeky.viewers.test/foto.png"), "image/png").putExtra("name", "foto.png"));
        ReaderActivity a = c.get();
        a.setTheme(android.R.style.Theme_Material_NoActionBar);
        c.setup();
        for (int i = 0; i < 40 && a.controls.getChildCount() < 6; i++) { a.worker.submit(() -> {}).get(5, TimeUnit.SECONDS); idle(100); }
        assertEquals(6, a.controls.getChildCount());
        String details = a.status.getText().toString();
        assertTrue(details, details.contains("1600 × 1200") && details.contains("1.9 MP"));
        assertNotNull(PageTurnTest.findDescription(a.controls, "Informação da imagem"));
        assertNotNull(PageTurnTest.findDescription(a.controls, "Rodar"));
        assertNotNull(PageTurnTest.findDescription(a.controls, "Editar foto"));
        assertNotNull(PageTurnTest.findDescription(a.controls, "Partilhar"));
        assertNotNull(PageTurnTest.findDescription(a.controls, "Abrir noutra aplicação"));
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        capture(root, "imagem", 360, 760);
        c.pause().stop().destroy();
    }

    @Test public void voiceLanguagesOfferSamplesAndDistinguishVoices() {
        assertTrue(VoiceLanguages.CODES.size() >= 15);
        assertEquals("pt", VoiceLanguages.CODES.get(0));
        assertTrue(VoiceLanguages.sample("sw").startsWith("Habari"));
        assertTrue(VoiceLanguages.sample("xx").startsWith("Olá"));
        java.util.List<String> tags = java.util.Arrays.asList("pt-PT", "pt-PT", "pt-BR");
        assertEquals("Voz 1 · ", VoiceLanguages.ordinal(tags, 0));
        assertEquals("Voz 2 · ", VoiceLanguages.ordinal(tags, 1));
        assertEquals("", VoiceLanguages.ordinal(tags, 2));
    }
}
