package mz.bam.seeky.android;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Looper;
import android.view.View;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.24.0: digitalizador com efeitos e páginas, mapa do armazenamento renovado e Bíblias do eBible.org. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Features5240Test {
    ReferenceDesign5150Test f;
    MainActivity a;

    @Before public void before() throws Exception { f = new ReferenceDesign5150Test(); f.before(); a = f.a; }
    @After public void after() throws Exception { UiPanel.dismissAll(a); f.after(); }

    /** Folha branca com texto escuro e uma sombra forte do lado esquerdo, sobre mesa escura. */
    static Bitmap shadowedPage(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xffeeeeee);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, 0, w, 0, 0xff707070, 0x00707070, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, shade);
        Paint ink = new Paint();
        ink.setColor(0xff202020);
        for (int y = 40; y < h - 40; y += 40) c.drawRect(30, y, w - 30, y + 8, ink);
        return b;
    }

    @Test public void documentEffectRemovesShadowsAndKeepsTextDark() {
        Bitmap page = shadowedPage(400, 300);
        ScanFilters.Settings s = new ScanFilters.Settings();
        s.sharpness = 0;
        Bitmap doc = ScanFilters.apply(page, s);
        int shadowPaper = Color.red(doc.getPixel(20, 20)), litPaper = Color.red(doc.getPixel(380, 20)), text = Color.red(doc.getPixel(200, 44));
        assertTrue("papel na sombra fica claro: " + shadowPaper, shadowPaper > 225);
        assertTrue("papel iluminado fica branco: " + litPaper, litPaper > 240);
        assertTrue("texto fica escuro: " + text, text < 80);
        s.filter = ScanFilters.BW;
        Bitmap bw = ScanFilters.apply(page, s);
        for (int x = 0; x < 400; x += 37) for (int y = 0; y < 300; y += 29) { int v = Color.red(bw.getPixel(x, y)); assertTrue(v == 0 || v == 255); }
        s.filter = ScanFilters.ORIGINAL;
        s.rotation = 90;
        Bitmap turned = ScanFilters.apply(page, s);
        assertEquals(300, turned.getWidth());
        assertEquals(400, turned.getHeight());
        ScanFilters.Settings back = ScanFilters.Settings.decode(s.encode());
        assertEquals(90, back.rotation);
        assertEquals(ScanFilters.ORIGINAL, back.filter);
    }

    @Test public void autoLevelsStretchAFlatPhoto() {
        Bitmap dull = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(dull);
        c.drawColor(0xff808080);
        Paint p = new Paint();
        p.setColor(0xffa0a0a0);
        c.drawRect(0, 0, 50, 100, p);
        ScanFilters.Settings s = new ScanFilters.Settings();
        s.filter = ScanFilters.AUTO;
        s.sharpness = 0;
        Bitmap out = ScanFilters.apply(dull, s);
        assertTrue(Color.red(out.getPixel(10, 50)) > 220);
        assertTrue(Color.red(out.getPixel(90, 50)) < 40);
    }

    @Test public void detectionFindsDarkAndColouredDocumentsToo() {
        Bitmap photo = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(photo);
        c.drawColor(0xffd9d4c8);
        Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
        green.setColor(0xff5f7a5c);
        Path book = new Path();
        book.moveTo(90, 120); book.lineTo(320, 130); book.lineTo(330, 500); book.lineTo(80, 490); book.close();
        c.drawPath(book, green);
        float[] q = ScanActivity.detect(photo);
        float[] expected = {90, 120, 320, 130, 330, 500, 80, 490};
        for (int i = 0; i < 8; i++) assertEquals("canto " + i, expected[i], q[i], 14);
    }

    static File jpeg(File dir, String name, Bitmap b) throws IOException {
        File f = new File(dir, name);
        try (OutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 92, out); }
        return f;
    }

    @Test public void scannerShowsCropEffectsPagesAndExportOptions() throws Exception {
        ActivityController<ScanActivity> c = Robolectric.buildActivity(ScanActivity.class);
        ScanActivity s = c.get();
        s.setTheme(android.R.style.Theme_Material_NoActionBar);
        c.create().start().resume().visible();
        shadowOf(Looper.getMainLooper()).idle();
        Intent camera = shadowOf(s).getNextStartedActivityForResult().intent;
        assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, camera.getAction());
        assertTrue(BooksToolsTest.labels(s.getWindow().getDecorView()).contains("Nenhuma página ainda"));
        Bitmap photo = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(photo);
        cv.drawColor(0xff3a3a3a);
        Paint white = new Paint();
        white.setColor(0xfff2f2f2);
        cv.drawRect(90, 80, 520, 720, white);
        Paint ink = new Paint();
        ink.setColor(0xff222222);
        for (int y = 140; y < 680; y += 36) cv.drawRect(130, y, 480, y + 9, ink);
        ScanProvider.dir(s).mkdirs();
        s.shot = jpeg(ScanProvider.dir(s), "shot_1.jpg", photo);
        s.onActivityResult(ScanActivity.CAMERA, Activity.RESULT_OK, new Intent());
        assertEquals(ScanActivity.CROP, s.state);
        assertEquals(90, s.current.corners[0], 12);
        View root = ((android.view.ViewGroup) s.getWindow().getDecorView().findViewById(android.R.id.content)).getChildAt(0);
        assertTrue(BooksToolsTest.labels(root).contains("Detectar"));
        f.capture(root, "digitalizar-recortar-5240", 360, 800);
        s.show(ScanActivity.EDIT);
        s.work.submit(() -> {}).get(20, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        String labels = BooksToolsTest.labels(root);
        for (String name : new String[]{"Documento", "Melhorar", "Sem sombras", "Preto e branco", "Ajustes"}) assertTrue(name, labels.contains(name));
        assertNotNull(s.preview);
        f.capture(root, "digitalizar-efeitos-5240", 360, 800);
        s.keepPage();
        assertEquals(1, s.pages.size());
        assertEquals(ScanActivity.PAGES, s.state);
        s.work.submit(() -> {}).get(20, TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(BooksToolsTest.labels(root).contains("Exportar"));
        f.capture(root, "digitalizar-paginas-5240", 360, 800);
        s.exportPanel();
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        UiPanel export = UiPanel.open.get(s).iterator().next();
        String options = BooksToolsTest.labels(export.root);
        for (String name : new String[]{"PDF", "Imagens JPG", "A4", "Carta", "Alta", "Compacta", "Extrair texto (OCR)", "Guardar e partilhar"}) assertTrue(name, options.contains(name));
        f.capture(export.root, "digitalizar-exportar-5240", 340, 760);
        UiPanel.dismissAll(s);
        c.pause().stop().destroy();
    }

    @Test public void scannedPagesBecomeAPdfInEveryPageSize() throws Exception {
        PdfTools.init(a);
        File dir = java.nio.file.Files.createTempDirectory("scan").toFile();
        File one = jpeg(dir, "1.jpg", shadowedPage(600, 800)), two = jpeg(dir, "2.jpg", shadowedPage(800, 500));
        for (int mode = 0; mode < 3; mode++) {
            File pdf = new File(dir, "p" + mode + ".pdf");
            PdfTools.images(Arrays.asList(one, two), pdf, mode, .8f, mode == 2 ? 0 : 12);
            try (com.tom_roush.pdfbox.pdmodel.PDDocument d = com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf)) {
                assertEquals(2, d.getNumberOfPages());
                float w = d.getPage(0).getMediaBox().getWidth(), h = d.getPage(0).getMediaBox().getHeight();
                if (mode == 0) assertEquals(841.9f, h, 1);
                if (mode == 1) assertEquals(792f, h, 1);
                if (mode == 2) { assertEquals(595f, w, 1); assertEquals(595f * 800 / 600, h, 2); }
                assertTrue("página 2 deitada", d.getPage(1).getMediaBox().getWidth() > d.getPage(1).getMediaBox().getHeight());
            }
        }
    }

    // ---- Bíblias ----

    @Test public void bibleCatalogCsvHandlesQuotesCommasAndLineBreaks() throws Exception {
        String csv = "﻿\"languageCode\",\"translationId\",\"languageName\",\"languageNameInEnglish\",\"title\",\"description\",\"Redistributable\",\"Copyright\",\"UpdateDate\",\"OTbooks\",\"NTbooks\",\"DCbooks\",\"downloadable\",\"shortTitle\"\r\n"
                + "\"por\",\"porbr2018\",\"Português\",\"Portuguese\",\"Bíblia Livre\",\"Tradução, livre\",\"True\",\"Copyright © 2018\",\"2026-01-02\",\"39\",\"27\",\"0\",\"True\",\"Bíblia Livre\"\r\n"
                + "\"por\",\"porbrbsl\",\"Português\",\"Portuguese\",\"Bíblia Portuguesa Mundial\",\"Linha 1\nLinha \"\"2\"\"\",\"True\",\"public domain\",\"2025-05-05\",\"39\",\"27\",\"0\",\"True\",\"BPM\"\r\n"
                + "\"eng\",\"engxx\",\"English\",\"English\",\"Private\",\"\",\"False\",\"©\",\"2020-01-01\",\"0\",\"27\",\"0\",\"False\",\"X\"\r\n"
                + "\"vmw\",\"vmw\",\"Emakhuwa\",\"Makhuwa\",\"Nlakano Nanaano\",\"\",\"True\",\"©\",\"2024-03-03\",\"0\",\"27\",\"0\",\"True\",\"Makhuwa NT\"\r\n";
        List<BibleCatalog.Version> list = BibleCatalog.parse(new StringReader(csv));
        assertEquals(3, list.size());
        assertEquals("Tradução, livre", list.get(0).description);
        assertEquals("Linha 1\nLinha \"2\"", list.get(1).description);
        assertEquals("Domínio público", list.get(1).license(a));
        assertEquals("Bíblia completa", list.get(0).scope(a));
        assertEquals("Novo Testamento", list.get(2).scope(a));
        assertEquals(2, BibleCatalog.language(list, "por").size());
        assertTrue(BibleCatalog.matches(list.get(2), "makhuwa"));
        assertFalse(BibleCatalog.matches(list.get(2), "português"));
    }

    static byte[] usfmZip() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("00-FRTpor.usfm"));
            z.write("\\id FRT Introdução\n\\p Prefácio sem versículos.\n".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("70-JHNpor.usfm"));
            z.write(("\\id JHN Teste\n\\c 3\n\\p \\v 16 Porque Deus amou o \\w mundo|strong=\"G2889\"\\w* de tal maneira\\f + \\fr 3.16 \\ft nota\\f* que deu o seu Filho.\\x - \\xo 3.16 \\xt Rm 5.8\\x*\n"
                    + "\\v 17 Porque Deus enviou o seu Filho. \\v 18 Quem crê nele não é condenado.\n").getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("02-GENpor.usfm"));
            z.write("﻿\\id GEN Teste\n\\c 1\n\\p\n\\v 1 No princípio criou Deus os céus e a terra.\n\\v 2 E a terra era sem forma.\n".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("copr.htm"));
            z.write("<html/>".getBytes(StandardCharsets.UTF_8));
        }
        return b.toByteArray();
    }

    @Test public void downloadedUsfmIsCombinedCleanedAndParsedByTheExistingImporter() throws Exception {
        StringWriter out = new StringWriter();
        assertEquals(2, BibleCatalog.combine(new ByteArrayInputStream(usfmZip()), out));
        String text = out.toString();
        assertTrue(text.indexOf("\\id GEN") < text.indexOf("\\id JHN"));
        assertFalse(text.contains("FRT"));
        assertFalse(text.contains("strong"));
        assertFalse(text.contains("Rm 5.8"));
        assertFalse(text.contains("nota"));
        Map<String, String> verses = new LinkedHashMap<>();
        int n = BibleText.parse(new StringReader(text), (book, chapter, verse, value) -> verses.put(book + " " + chapter + ":" + verse, value), () -> false);
        assertEquals(5, n);
        assertEquals("No princípio criou Deus os céus e a terra.", verses.get(BibleText.code("GEN") + " 1:1"));
        assertEquals("Porque Deus amou o mundo de tal maneira que deu o seu Filho.", verses.get(BibleText.code("JHN") + " 3:16").replaceAll("\\s+", " "));
        assertEquals("Quem crê nele não é condenado.", verses.get(BibleText.code("JHN") + " 3:18"));
    }

    /** Opcional: com SEEKY_BIBLE_ZIP a apontar para um ZIP USFM real do eBible.org. */
    @Test public void realEbibleZipWhenAvailable() throws Exception {
        String path = System.getenv("SEEKY_BIBLE_ZIP");
        Assume.assumeTrue(path != null && new File(path).isFile());
        StringWriter out = new StringWriter();
        int books;
        try (InputStream in = new FileInputStream(path)) { books = BibleCatalog.combine(in, out); }
        assertTrue(books >= 27);
        int[] count = {0};
        boolean[] dirty = {false};
        BibleText.parse(new StringReader(out.toString()), (book, chapter, verse, value) -> { count[0]++; if (value.contains("\\") || value.contains("|")) dirty[0] = true; }, () -> false);
        System.out.println("eBible: " + books + " livros, " + count[0] + " versículos");
        assertTrue(count[0] > 7900);
        assertFalse(dirty[0]);
    }

    @Test public void bibleProviderServesOnlyCleanDownloads() throws Exception {
        File dir = BibleCatalog.downloads(a);
        java.nio.file.Files.write(new File(dir, "porbr2018.usfm").toPath(), "\\id GEN\n".getBytes(StandardCharsets.UTF_8));
        ScanProvider provider = Robolectric.setupContentProvider(ScanProvider.class, a.getPackageName() + ".scan");
        assertNotNull(provider.openFile(BibleCatalog.uri(a, "porbr2018.usfm"), "r"));
        try { provider.openFile(BibleCatalog.uri(a, "porbr2018.usfm"), "w"); fail(); } catch (FileNotFoundException expected) {}
        try { provider.openFile(android.net.Uri.parse("content://" + a.getPackageName() + ".scan/bible/..%2Fx.usfm"), "r"); fail(); } catch (FileNotFoundException expected) {}
        android.database.Cursor c = provider.query(BibleCatalog.uri(a, "porbr2018.usfm"), null, null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals("porbr2018.usfm", c.getString(0));
    }

    @Test public void biblePanelShowsPortugueseVersionsAndLanguages() throws Exception {
        String csv = "\"languageCode\",\"translationId\",\"languageName\",\"languageNameInEnglish\",\"title\",\"description\",\"Redistributable\",\"Copyright\",\"UpdateDate\",\"OTbooks\",\"NTbooks\",\"DCbooks\",\"downloadable\",\"shortTitle\"\n"
                + "\"por\",\"porbr2018\",\"Português\",\"Portuguese\",\"Bíblia Livre\",\"\",\"True\",\"Copyright © 2018\",\"2026-01-02\",\"39\",\"27\",\"0\",\"True\",\"Bíblia Livre\"\n"
                + "\"por\",\"porbrbsl\",\"Português\",\"Portuguese\",\"Bíblia Portuguesa Mundial\",\"\",\"True\",\"public domain\",\"2025-05-05\",\"39\",\"27\",\"0\",\"True\",\"BPM\"\n"
                + "\"eng\",\"engwebp\",\"English\",\"English\",\"World English Bible\",\"\",\"True\",\"public domain\",\"2025-05-05\",\"39\",\"27\",\"0\",\"True\",\"WEB\"\n";
        List<BibleCatalog.Version> list = BibleCatalog.parse(new StringReader(csv));
        BibleCatalog.panel(a, list);
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        UiPanel p = UiPanel.open.get(a).iterator().next();
        String labels = BooksToolsTest.labels(p.root);
        for (String s : new String[]{"Bíblia Livre", "Bíblia Portuguesa Mundial", "Domínio público", "English", "Todas as línguas", "Bíblias importadas e ficheiros"}) assertTrue(s, labels.contains(s));
        f.capture(p.root, "biblias-5240", 340, 900);
    }

    @Test public void rowsShowFriendlyAges() {
        long now = System.currentTimeMillis();
        assertEquals("hoje", UiRows.ago(a, now - 1000, now));
        assertEquals("há 3 dias", UiRows.ago(a, now - 3 * 86400000L, now));
        assertEquals("há 8 meses", UiRows.ago(a, now - 245 * 86400000L, now));
        assertEquals("há 2 anos", UiRows.ago(a, now - 800 * 86400000L, now));
    }
}
