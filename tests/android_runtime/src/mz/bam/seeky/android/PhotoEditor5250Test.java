package mz.bam.seeky.android;

import android.app.Application;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.25.0: editor de fotografias (geometria, filtros, ajustes, desenho, texto) e partilha nos leitores. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhotoEditor5250Test {
    final Application app = RuntimeEnvironment.getApplication();

    static Bitmap photo() {
        Bitmap b = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xff808080);
        Paint p = new Paint();
        p.setColor(Color.RED);
        c.drawRect(0, 0, 100, 300, p);
        p.setColor(0xff2050c0);
        c.drawRect(300, 0, 400, 300, p);
        return b;
    }

    @Test public void geometryRotatesMirrorsCropsAndStraightensWithoutEmptyCorners() {
        Bitmap src = photo();
        PhotoEdit.State s = new PhotoEdit.State();
        s.rotate = 1;
        Bitmap r = PhotoEdit.geometry(src, s, true, 2000);
        assertEquals(300, r.getWidth());
        assertEquals(400, r.getHeight());
        assertEquals("rodado: a faixa vermelha da esquerda fica em cima", Color.RED, r.getPixel(150, 20));
        s = new PhotoEdit.State();
        s.flipH = true;
        Bitmap m = PhotoEdit.geometry(src, s, true, 2000);
        assertEquals(Color.RED, m.getPixel(380, 150));
        s = new PhotoEdit.State();
        s.crop = new float[]{0, 0, .5f, 1};
        Bitmap c = PhotoEdit.geometry(src, s, true, 2000);
        assertEquals(200, c.getWidth());
        assertEquals(Color.RED, c.getPixel(10, 150));
        Bitmap uniform = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        uniform.eraseColor(0xff33aa55);
        s = new PhotoEdit.State();
        s.straighten = 12;
        Bitmap st = PhotoEdit.geometry(uniform, s, true, 2000);
        for (int[] p : new int[][]{{1, 1}, {398, 1}, {1, 298}, {398, 298}}) assertTrue("canto sem preto", Color.green(st.getPixel(p[0], p[1])) > 120);
        s.keystoneV = .6f;
        s.keystoneH = -.4f;
        Bitmap k = PhotoEdit.geometry(uniform, s, true, 2000);
        assertEquals(400, k.getWidth());
        assertTrue(PhotoEdit.cover(400, 300, 0) == 1f);
    }

    @Test public void filtersAndIntensity() {
        int[] px = {Color.rgb(200, 100, 50)};
        PhotoEdit.filter(px, 5, 100);
        assertEquals(Color.red(px[0]), Color.green(px[0]));
        assertEquals(Color.green(px[0]), Color.blue(px[0]));
        int[] sepia = {Color.rgb(128, 128, 128)};
        PhotoEdit.filter(sepia, 6, 100);
        assertTrue(Color.red(sepia[0]) > Color.green(sepia[0]) && Color.green(sepia[0]) > Color.blue(sepia[0]));
        int[] none = {Color.rgb(200, 100, 50)};
        PhotoEdit.filter(none, 6, 0);
        assertEquals(Color.rgb(200, 100, 50), none[0]);
        int[] half = {Color.rgb(200, 100, 50)}, full = {Color.rgb(200, 100, 50)};
        PhotoEdit.filter(half, 10, 50);
        PhotoEdit.filter(full, 10, 100);
        assertTrue(Math.abs(Color.blue(half[0]) - 50) < Math.abs(Color.blue(full[0]) - 50));
        for (int f = 0; f < PhotoEdit.FILTERS.length; f++) { int[] any = {Color.rgb(10, 200, 90), Color.rgb(250, 250, 250)}; PhotoEdit.filter(any, f, 100); }
    }

    @Test public void adjustmentsChangeTheRightThings() {
        int w = 50, h = 50;
        int[] base = new int[w * h];
        java.util.Arrays.fill(base, Color.rgb(120, 110, 100));
        int[] a = base.clone();
        int[] adj = new int[PhotoEdit.ADJUST.length];
        adj[PhotoEdit.BRIGHTNESS] = 50;
        PhotoEdit.adjust(a, w, h, adj);
        assertTrue(Color.red(a[0]) > 160);
        a = base.clone();
        adj = new int[PhotoEdit.ADJUST.length];
        adj[PhotoEdit.SATURATION] = -100;
        PhotoEdit.adjust(a, w, h, adj);
        assertEquals(Color.red(a[0]), Color.blue(a[0]), 1);
        a = base.clone();
        adj = new int[PhotoEdit.ADJUST.length];
        adj[PhotoEdit.WARMTH] = 80;
        PhotoEdit.adjust(a, w, h, adj);
        assertTrue(Color.red(a[0]) > 120 && Color.blue(a[0]) < 100);
        a = base.clone();
        adj = new int[PhotoEdit.ADJUST.length];
        adj[PhotoEdit.VIGNETTE] = 100;
        PhotoEdit.adjust(a, w, h, adj);
        assertTrue("cantos mais escuros", Color.red(a[0]) < Color.red(a[25 * w + 25]) - 30);
        PhotoEdit.State s = new PhotoEdit.State();
        Bitmap dark = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        dark.eraseColor(Color.rgb(40, 40, 40));
        PhotoEdit.auto(dark, s);
        assertTrue(s.adjust[PhotoEdit.BRIGHTNESS] > 20);
        assertTrue(s.changed());
    }

    @Test public void drawingAndTextFollowTheCrop() {
        PhotoEdit.State s = new PhotoEdit.State();
        PhotoEdit.Stroke k = new PhotoEdit.Stroke();
        k.color = Color.YELLOW;
        k.width = .05f;
        k.add(.1f, .5f);
        k.add(.9f, .5f);
        s.strokes.add(k);
        PhotoEdit.Label l = new PhotoEdit.Label();
        l.text = "Olá";
        l.color = Color.GREEN;
        l.x = .5f;
        l.y = .2f;
        l.size = .12f;
        s.labels.add(l);
        Bitmap out = PhotoEdit.render(photo(), s, true, 2000, true);
        assertEquals(Color.YELLOW, out.getPixel(200, 150));
        boolean green = false;
        for (int x = 150; x < 250 && !green; x++) for (int y = 30; y < 70; y++) if (Color.green(out.getPixel(x, y)) > 200 && Color.red(out.getPixel(x, y)) < 80) { green = true; break; }
        assertTrue("texto desenhado", green);
        PhotoEdit.State copy = s.copy();
        copy.strokes.get(0).color = Color.BLUE;
        copy.labels.get(0).text = "x";
        assertEquals(Color.YELLOW, s.strokes.get(0).color);
        assertEquals("Olá", s.labels.get(0).text);
        assertFalse(new PhotoEdit.State().changed());
    }

    void idle(ActivityController<?> c, PhotoEditorActivity a) throws Exception {
        for (int i = 0; i < 6; i++) { a.work.submit(() -> {}).get(20, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS); }
    }

    void capture(View v, String name) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 360, 800);
        Bitmap b = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        try (FileOutputStream out = new FileOutputStream(System.getProperty("seeky.screenshot") + "-5250-" + name + ".png")) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }

    @Test public void editorOpensWithAllToolsUndoesAndShares() throws Exception {
        File f = new File(app.getCacheDir(), "paisagem.jpg");
        Bitmap b = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xff2a6f97);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xffffd34d);
        c.drawCircle(860, 250, 130, p);
        p.setColor(0xff1b4332);
        Path hills = new Path();
        hills.moveTo(0, 800); hills.lineTo(0, 540); hills.quadTo(380, 360, 680, 560); hills.quadTo(940, 420, 1200, 520); hills.lineTo(1200, 800); hills.close();
        c.drawPath(hills, p);
        try (FileOutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 92, out); }
        ActivityController<PhotoEditorActivity> ctl = Robolectric.buildActivity(PhotoEditorActivity.class,
                new Intent(app, PhotoEditorActivity.class).setDataAndType(Uri.fromFile(f), "image/jpeg").putExtra("name", "paisagem.jpg"));
        PhotoEditorActivity a = ctl.get();
        ctl.create().start().resume().visible();
        idle(ctl, a);
        View root = ((ViewGroup) a.findViewById(android.R.id.content)).getChildAt(0);
        String labels = BooksToolsTest.labels(root);
        for (String t : new String[]{"Recortar", "Ajustar", "Filtros", "Desenhar", "Texto", "Guardar", "Automático", "Brilho", "Vinheta"}) assertTrue(t, labels.contains(t));
        assertNotNull(a.rendered);
        a.before();
        a.state.adjust[PhotoEdit.CONTRAST] = 30;
        a.state.adjust[PhotoEdit.VIGNETTE] = 40;
        a.showTab(PhotoEditorActivity.ADJUST);
        idle(ctl, a);
        capture(root, "ajustar");
        a.showTab(PhotoEditorActivity.FILTERS);
        idle(ctl, a);
        labels = BooksToolsTest.labels(root);
        for (String t : new String[]{"Vívido", "Sépia", "Vintage", "Noir"}) assertTrue(t, labels.contains(t));
        a.before();
        a.state.filter = 7;
        a.showTab(PhotoEditorActivity.FILTERS);
        idle(ctl, a);
        assertTrue(BooksToolsTest.labels(root).contains("Intensidade"));
        capture(root, "filtros");
        a.showTab(PhotoEditorActivity.CROP);
        idle(ctl, a);
        labels = BooksToolsTest.labels(root);
        for (String t : new String[]{"Livre", "1:1", "16:9", "Endireitar °", "Persp. vertical", "Espelhar ↔", "Rodar"}) assertTrue(t, labels.contains(t));
        a.ratio = 1f;
        a.applyRatio();
        int[] fr = PhotoEdit.frame(a.preview.getWidth(), a.preview.getHeight(), a.state);
        assertEquals(1f, (a.state.crop[2] - a.state.crop[0]) * fr[0] / ((a.state.crop[3] - a.state.crop[1]) * fr[1]), .02f);
        a.state.straighten = 4;
        a.showTab(PhotoEditorActivity.CROP);
        idle(ctl, a);
        capture(root, "recortar");
        a.showTab(PhotoEditorActivity.TEXT);
        a.before();
        PhotoEdit.Label l = new PhotoEdit.Label();
        l.text = "Férias 2026";
        l.box = true;
        l.color = Color.WHITE;
        l.y = .85f;
        a.state.labels.add(l);
        a.selected = l;
        a.showTab(PhotoEditorActivity.TEXT);
        idle(ctl, a);
        assertTrue(BooksToolsTest.labels(root).contains("Com fundo") || BooksToolsTest.labels(root).contains("Sem fundo"));
        capture(root, "texto");
        int before = a.state.labels.size();
        a.undo();
        assertEquals(before - 1, a.state.labels.size());
        a.redo();
        assertEquals(before, a.state.labels.size());
        a.savePanel();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        UiPanel save = UiPanel.open.get(a).iterator().next();
        String options = BooksToolsTest.labels(save.root);
        assertTrue(options, options.contains("Guardar como cópia") && options.contains("Partilhar") && options.contains("paisagem (editada).jpg"));
        UiPanel.dismissAll(a);
        Bitmap full = a.full(a.state);
        assertTrue("recorte 1:1 em alta resolução", Math.abs(full.getWidth() - full.getHeight()) < 20 && full.getWidth() >= 700);
        a.save(false, true);
        idle(ctl, a);
        Intent chooser = shadowOf(a).getNextStartedActivity();
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, send.getAction());
        Uri shared = send.getParcelableExtra(Intent.EXTRA_STREAM);
        assertEquals(a.getPackageName() + ".scan", shared.getAuthority());
        assertTrue(shared.getPath().endsWith("paisagem (editada).jpg"));
        ctl.pause().stop().destroy();
    }

    @Test public void shareToolsSendFilesTextAndImages() {
        android.app.Activity act = Robolectric.buildActivity(android.app.Activity.class).setup().get();
        ShareTools.text(act, "  Porque Deus amou o mundo  ");
        Intent chooser = shadowOf(act).getNextStartedActivity();
        Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals("Porque Deus amou o mundo", send.getStringExtra(Intent.EXTRA_TEXT));
        ShareTools.file(act, Uri.parse("content://outra.app/musica.mp3"), "musica.mp3", "audio/mpeg");
        send = shadowOf(act).getNextStartedActivity().getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals("audio/mpeg", send.getType());
        assertEquals(Uri.parse("content://outra.app/musica.mp3"), send.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }
}
