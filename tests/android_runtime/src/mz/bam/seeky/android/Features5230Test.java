package mz.bam.seeky.android;

import android.app.*;
import android.graphics.*;
import android.os.Looper;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.23.0: Wi-Fi, estatísticas, cartões, PDF, digitalização, OPDS, provas, Pomodoro, tema Horário e painéis. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Features5230Test {
    ReferenceDesign5150Test f;
    MainActivity a;

    @Before public void before() throws Exception { f = new ReferenceDesign5150Test(); f.before(); a = f.a; }
    @After public void after() throws Exception { UiPanel.dismissAll(a); UiDialog.dismissAll(a); WifiTransfer.stopRunning(); f.after(); }

    UiPanel latest() {
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS);
        List<UiPanel> list = new ArrayList<>(UiPanel.open.get(a));
        return list.get(list.size() - 1);
    }

    // ---- Wi-Fi ----

    static String[] http(int port, String method, String path, String cookie, Map<String, String> headers, byte[] body) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(10000);
            StringBuilder h = new StringBuilder(method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n");
            if (cookie != null) h.append("Cookie: ").append(cookie).append("\r\n");
            if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            if (body != null) h.append("Content-Length: ").append(body.length).append("\r\n");
            h.append("\r\n");
            OutputStream out = s.getOutputStream();
            out.write(h.toString().getBytes(StandardCharsets.UTF_8));
            if (body != null) out.write(body);
            out.flush();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = s.getInputStream().read(b)) != -1; ) all.write(b, 0, n);
            String text = all.toString("UTF-8");
            int split = text.indexOf("\r\n\r\n");
            return new String[]{text.substring(0, split), text.substring(split + 4)};
        }
    }

    @Test public void wifiTransferNeedsTheCodeAndKeepsUploadsInsideTheSharedFolder() throws Exception {
        File root = Files.createTempDirectory("wifi").toFile();
        File book = new File(root, "livro.txt");
        Files.write(book.toPath(), "Conteúdo".getBytes(StandardCharsets.UTF_8));
        File secret = new File(root.getParentFile(), "fora-" + UUID.randomUUID() + ".txt");
        Files.write(secret.toPath(), "segredo".getBytes(StandardCharsets.UTF_8));
        WifiTransfer.Server s = new WifiTransfer.Server(root, "123456", 0);
        s.start();
        try {
            int port = s.port();
            assertTrue(http(port, "GET", "/", null, null, null)[1].contains("código"));
            assertTrue(http(port, "GET", "/f?p=livro.txt", null, null, null)[0].startsWith("HTTP/1.1 403"));
            assertTrue(http(port, "POST", "/login", null, null, "pin=000000".getBytes())[0].startsWith("HTTP/1.1 403"));
            String[] ok = http(port, "POST", "/login", null, null, "pin=123+456".getBytes());
            assertTrue(ok[0], ok[0].startsWith("HTTP/1.1 303"));
            assertTrue(ok[0].contains("HttpOnly") && ok[0].contains("SameSite=Strict"));
            String cookie = "seeky=" + s.token;
            assertTrue(http(port, "GET", "/", cookie, null, null)[1].contains("livro.txt"));
            assertEquals("Conteúdo", http(port, "GET", "/f?p=livro.txt", cookie, null, null)[1]);
            assertTrue(http(port, "GET", "/f?p=../" + secret.getName(), cookie, null, null)[0].startsWith("HTTP/1.1 404"));
            byte[] data = "enviado do PC".getBytes(StandardCharsets.UTF_8);
            assertTrue("sem cabeçalho próprio", http(port, "PUT", "/up?d=&n=nota.txt", cookie, null, data)[0].startsWith("HTTP/1.1 403"));
            Map<String, String> header = Collections.singletonMap("X-Seeky", "1");
            assertTrue(http(port, "PUT", "/up?d=&n=..%2F..%2Fnota.txt", cookie, header, data)[0].startsWith("HTTP/1.1 200"));
            assertEquals("enviado do PC", new String(Files.readAllBytes(new File(root, "nota.txt").toPath()), StandardCharsets.UTF_8));
            assertTrue(http(port, "PUT", "/up?d=&n=nota.txt", cookie, header, data)[0].startsWith("HTTP/1.1 200"));
            assertTrue(new File(root, "nota (1).txt").isFile());
            assertEquals(2, s.received.get());
            for (int i = 0; i < 5; i++) http(port, "POST", "/login", null, null, "pin=111111".getBytes());
            assertTrue(http(port, "POST", "/login", null, null, "pin=123456".getBytes())[0].startsWith("HTTP/1.1 429"));
        } finally { s.stop(); secret.delete(); }
    }

    @Test public void wifiNamesAreCleaned() {
        assertEquals("nota.txt", WifiTransfer.cleanName("../../nota.txt"));
        assertEquals("ficheiro", WifiTransfer.cleanName("..."));
        assertEquals("a b.pdf", WifiTransfer.cleanName("C:\\x\\a b.pdf"));
        assertEquals("&lt;b&gt;", WifiTransfer.esc("<b>"));
    }

    // ---- leitura ----

    @Test public void readingStatsCountStreaksWeekAndBooks() {
        ReadingStats.prefs(a).edit().clear().putInt("goal", 10).apply();
        long now = System.currentTimeMillis();
        ReadingStats.add(a, ReadingStats.shift(now, -2), 700, "u1", "Livro A");
        ReadingStats.add(a, ReadingStats.shift(now, -1), 650, "u2", "Livro B");
        ReadingStats.add(a, now, 120, "u1", "Livro A");
        assertEquals(2, ReadingStats.streak(a, now));
        ReadingStats.add(a, now, 600, "u1", "Livro A");
        assertEquals(3, ReadingStats.streak(a, now));
        long[] week = ReadingStats.week(a, now);
        assertEquals(720, week[6]);
        assertEquals(650, week[5]);
        assertEquals("Livro A", ReadingStats.topBooks(a, 1).get(0)[0]);
        ReadingStats.add(a, now, 3, "u1", "Livro A");
        assertEquals(720, ReadingStats.seconds(a, ReadingStats.day(now)));
        ReadingStats.add(a, now, 10 * 3600, "u3", "Longo");
        assertEquals(720 + 4 * 3600, ReadingStats.seconds(a, ReadingStats.day(now)));
    }

    @Test public void readingReminderNotifiesOnlyWhenGoalIsMissing() {
        ReadingStats.prefs(a).edit().clear().putInt("goal", 20).putInt("reminder", 20).apply();
        ReadingStats.remind(a);
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        assertEquals(1, shadowOf(nm).getAllNotifications().size());
        assertNotNull(shadowOf(a.getSystemService(AlarmManager.class)).getNextScheduledAlarm());
        nm.cancelAll();
        ReadingStats.add(a, System.currentTimeMillis(), 30 * 60, "u", "x");
        ReadingStats.remind(a);
        assertEquals(0, shadowOf(nm).getAllNotifications().size());
    }

    @Test public void annotationCardsUseNotesOrHideTheKeyword() {
        String[] note = AnnotationTools.card(new AnnotationTools.Item(1, "note", "A fotossíntese ocorre nos cloroplastos.", "Onde ocorre?"));
        assertEquals("«A fotossíntese ocorre nos cloroplastos.»", note[0]);
        assertEquals("Onde ocorre?", note[1]);
        String[] cloze = AnnotationTools.card(new AnnotationTools.Item(2, "highlight", "A fotossíntese ocorre nos cloroplastos das plantas.", ""));
        assertTrue(cloze[0].contains("_____"));
        assertFalse(cloze[0].contains("fotossíntese"));
        assertEquals("A fotossíntese ocorre nos cloroplastos das plantas.", cloze[1]);
        assertNull(AnnotationTools.card(new AnnotationTools.Item(3, "highlight", "Sim.", "")));
    }

    @Test public void annotationCardsAreSavedOnceForReview() throws Exception {
        List<String[]> cards = Arrays.asList(new String[]{"Frente única 5230", "Verso"}, new String[]{"Outra frente 5230", "Outro verso"});
        assertEquals(2, AnnotationTools.insert(a, cards, "Livro"));
        assertEquals(0, AnnotationTools.insert(a, cards, "Livro"));
        try (StudyStore db = new StudyStore(a)) { assertEquals(1, db.count("cards", "front=? AND tags LIKE 'destaques%'", new String[]{"Frente única 5230"})); }
        assertTrue(TodayWidget.dueCards(a, System.currentTimeMillis() + 1000) >= 2);
    }

    @Test public void vocabularyWordsBecomeCards() throws Exception {
        assertEquals("cloroplasto", WordLookup.clean("  «cloroplasto», "));
        assertEquals(1, WordLookup.save(a, "cloroplasto", "organelo da fotossíntese", true));
        assertEquals(0, WordLookup.save(a, "cloroplasto", "", true));
        assertEquals(1, WordLookup.words(a).length());
        try (StudyStore db = new StudyStore(a)) { assertEquals(1, db.count("cards", "front=?", new String[]{"cloroplasto"})); }
    }

    @Test public void opdsFeedsGiveBooksSubcatalogsAndNextPage() throws Exception {
        String xml = "<?xml version='1.0'?><feed xmlns='http://www.w3.org/2005/Atom' xmlns:opds='http://opds-spec.org/2010/catalog'><title>Resultados</title>"
                + "<link rel='next' href='/ebooks/search.opds/?start_index=26' type='application/atom+xml;profile=opds-catalog'/>"
                + "<entry><title>Dom Casmurro</title><author><name>Machado de Assis</name></author><summary>Romance.</summary>"
                + "<link rel='http://opds-spec.org/acquisition' type='application/epub+zip' href='https://www.gutenberg.org/ebooks/55752.epub3.images'/></entry>"
                + "<entry><title>Mais livros do autor</title><link rel='subsection' type='application/atom+xml;profile=opds-catalog' href='/ebooks/author/1.opds'/></entry>"
                + "<entry><title>Sem ligações</title></entry></feed>";
        OpenLibraries.Feed feed = OpenLibraries.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "https://www.gutenberg.org/ebooks/search.opds/");
        assertEquals("Resultados", feed.title);
        assertEquals("https://www.gutenberg.org/ebooks/search.opds/?start_index=26", feed.next);
        assertEquals(3, feed.entries.size());
        assertEquals("Machado de Assis", feed.entries.get(0).author);
        assertEquals("https://www.gutenberg.org/ebooks/55752.epub3.images", feed.entries.get(0).book().href);
        assertNull(feed.entries.get(1).book());
        assertEquals("https://www.gutenberg.org/ebooks/author/1.opds", feed.entries.get(1).catalog().href);
        assertEquals("Machado de Assis - Dom Casmurro.epub", OpenLibraries.fileName(feed.entries.get(0), feed.entries.get(0).book()));
        assertTrue(OpenLibraries.allowed("https://www.gutenberg.org/x"));
        assertTrue(OpenLibraries.allowed("http://192.168.1.20:8080/opds"));
        assertFalse(OpenLibraries.allowed("http://example.com/opds"));
        assertFalse(OpenLibraries.allowed("file:///sdcard/x"));
    }

    // ---- estudos ----

    @Test public void agendaCountsDaysAndSchedulesReminders() throws Exception {
        long now = System.currentTimeMillis();
        assertEquals(0, StudyAgenda.daysUntil(now, now));
        assertEquals(3, StudyAgenda.daysUntil(ReadingStats.shift(now, 3), now));
        assertEquals(-2, StudyAgenda.daysUntil(ReadingStats.shift(now, -2), now));
        StudyAgenda.prefs(a).edit().clear().apply();
        long id = StudyAgenda.add(a, "Exame de Matemática", "Exame", "Matemática", ReadingStats.shift(now, 5), 3);
        assertTrue(shadowOf(a.getSystemService(AlarmManager.class)).getScheduledAlarms().size() >= 2);
        StudyAgenda.fire(a, id * 2);
        assertEquals(1, shadowOf(a.getSystemService(NotificationManager.class)).getAllNotifications().size());
        StudyAgenda.update(a, id, "done", true);
        assertTrue(StudyAgenda.find(a, id).optBoolean("done"));
        StudyAgenda.update(a, id, "done", null);
        assertNull(StudyAgenda.find(a, id));
    }

    @Test public void pomodoroAlternatesFocusAndBreaksWithALongFinalBreak() {
        assertArrayEquals(new Object[]{"Pausa", 5, 1}, Pomodoro.after("Foco", 1, 0));
        assertArrayEquals(new Object[]{"Foco", 25, 2}, Pomodoro.after("Pausa", 1, 0));
        assertArrayEquals(new Object[]{"Pausa", 15, 4}, Pomodoro.after("Foco", 4, 0));
        assertNull(Pomodoro.after("Pausa", 4, 0));
        assertArrayEquals(new Object[]{"Pausa", 10, 2}, Pomodoro.after("Foco", 2, 1));
        Pomodoro.prefs(a).edit().putBoolean("active", true).putString("phase", "Foco").putInt("round", 1).putInt("pattern", 0).apply();
        Pomodoro.completed(a);
        assertTrue(Pomodoro.prefs(a).getBoolean("waiting", false));
        assertEquals("Pausa", Pomodoro.prefs(a).getString("next_type", ""));
        Pomodoro.stop(a);
    }

    // ---- PDF e digitalização ----

    static File jpeg(File dir, String name, int w, int h, int color) throws IOException {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        File f = new File(dir, name);
        try (OutputStream out = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 90, out); }
        return f;
    }

    @Test public void pdfToolsCreateMergeExtractRotateAndCompress() throws Exception {
        PdfTools.init(a);
        File dir = Files.createTempDirectory("pdf").toFile();
        File photos = new File(dir, "fotos.pdf");
        PdfTools.images(Arrays.asList(jpeg(dir, "a.jpg", 900, 1200, Color.RED), jpeg(dir, "b.jpg", 1400, 800, Color.BLUE)), photos);
        assertEquals(2, PdfTools.pages(photos));
        File merged = new File(dir, "junto.pdf");
        PdfTools.merge(Arrays.asList(photos, photos), merged);
        assertEquals(4, PdfTools.pages(merged));
        File part = new File(dir, "parte.pdf");
        PdfTools.extract(merged, 2, 3, part);
        assertEquals(2, PdfTools.pages(part));
        try { PdfTools.extract(merged, 3, 9, new File(dir, "x.pdf")); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("4")); }
        File turned = new File(dir, "rodado.pdf");
        PdfTools.rotate(part, 90, turned);
        try (com.tom_roush.pdfbox.pdmodel.PDDocument d = com.tom_roush.pdfbox.pdmodel.PDDocument.load(turned)) { assertEquals(90, d.getPage(0).getRotation()); }
        File small = new File(dir, "comprimido.pdf");
        long[] r = PdfTools.compress(photos, 50, 400, small);
        assertEquals(2, PdfTools.pages(small));
        assertEquals(2, r[2]);
        assertTrue("menor: " + r[0] + " → " + r[1], r[1] < r[0]);
    }

    @Test public void scannerFindsThePageAndStraightensIt() {
        Bitmap photo = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(photo);
        c.drawColor(0xff303030);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setColor(Color.WHITE);
        Path page = new Path();
        page.moveTo(80, 60); page.lineTo(330, 90); page.lineTo(310, 540); page.lineTo(60, 510); page.close();
        c.drawPath(page, white);
        float[] q = ScanActivity.detect(photo);
        float[] expected = {80, 60, 330, 90, 310, 540, 60, 510};
        for (int i = 0; i < 8; i++) assertEquals("canto " + i, expected[i], q[i], 12);
        Bitmap flat = ScanActivity.warp(photo, q, 2000);
        assertTrue(flat.getHeight() > flat.getWidth());
        assertTrue(Color.red(flat.getPixel(flat.getWidth() / 2, flat.getHeight() / 2)) > 240);
        Bitmap empty = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        empty.eraseColor(Color.GRAY);
        assertEquals(4f, ScanActivity.detect(empty)[0], .5f);
    }

    // ---- pasta privada, tema, atalhos, painéis ----

    @Test public void privateFolderStoresAndRemovesFiles() throws Exception {
        PrivateFolder.delete(PrivateFolder.dir(a));
        org.json.JSONObject item = PrivateFolder.store(a, new ByteArrayInputStream("foto".getBytes()), "../foto.jpg", "/sdcard/DCIM/foto.jpg", 4);
        assertEquals("foto.jpg", item.getString("name"));
        assertTrue(PrivateFolder.file(a, item).isFile());
        assertTrue(DiskWalker.inside(a.getFilesDir(), PrivateFolder.file(a, item)));
        assertEquals(1, PrivateFolder.load(a).length());
        try { PrivateFolder.store(a, new ByteArrayInputStream("ab".getBytes()), "x.txt", "", 5); fail(); } catch (IOException expected) {}
        assertEquals(1, PrivateFolder.load(a).length());
        PrivateFolder.remove(a, item.getString("id"));
        assertEquals(0, PrivateFolder.load(a).length());
        assertFalse(PrivateFolder.file(a, item).exists());
    }


    @Test public void scheduleThemeFollowsTheClock() {
        assertTrue(Arrays.asList(AppPalette.NAMES).contains(AppPalette.SCHEDULE));
        Calendar day = Calendar.getInstance(), night = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 10);
        night.set(Calendar.HOUR_OF_DAY, 22);
        assertTrue(AppPalette.daytime(day.getTimeInMillis()));
        assertFalse(AppPalette.daytime(night.getTimeInMillis()));
        a.prefs.edit().putString("theme", AppPalette.SCHEDULE).apply();
        assertEquals(AppPalette.daytime(System.currentTimeMillis()) ? "areia" : "floresta", AppPalette.of(a).style);
        a.prefs.edit().putString("theme", AppPalette.DEFAULT).apply();
    }

    @Test public void launcherShortcutsAndTodayWidgetRouteThroughTheMainScreen() {
        AppShortcuts.update(a);
        List<android.content.pm.ShortcutInfo> list = a.getSystemService(android.content.pm.ShortcutManager.class).getDynamicShortcuts();
        assertTrue(list.size() >= 3);
        assertEquals("search", list.get(0).getIntent().getStringExtra(SeekyWidget.EXTRA));
        TodayWidget.Summary s = TodayWidget.summary(a);
        assertEquals(ReadingStats.goal(a) * 60L, s.goal);
        Bitmap bar = TodayWidget.bar(a, .5f, Color.RED, Color.GRAY);
        assertEquals(Color.RED, bar.getPixel(bar.getWidth() / 4, bar.getHeight() / 2));
        a.handleWidget(new android.content.Intent().putExtra(SeekyWidget.EXTRA, "stats"));
        UiPanel p = latest();
        assertTrue(BooksToolsTest.labels(p.root).contains("Meta diária"));
    }

    @Test public void morePageOffersTheNewTools() throws Exception {
        a.tab = "Ferramentas"; a.render(); f.drain();
        String labels = BooksToolsTest.labels(a.shell);
        for (String item : new String[]{"Transferir por Wi-Fi", "Ferramentas de PDF", "Digitalizar documento", "Mapa do armazenamento", "Pasta privada",
                "Estatísticas e metas de leitura", "Bibliotecas gratuitas", "Vocabulário", "Provas e trabalhos", "Pomodoro"}) assertTrue(item, labels.contains(item));
    }

    @Test public void panelsOpenWithTheirMainActions() throws Exception {
        ReadingStats.prefs(a).edit().clear().putInt("goal", 15).apply();
        long now = System.currentTimeMillis();
        for (int d = 0; d < 7; d++) ReadingStats.add(a, ReadingStats.shift(now, -d), 300 + d * 160, "u" + d, "Livro " + d);
        ReadingStats.show(a);
        UiPanel stats = latest();
        assertTrue(BooksToolsTest.labels(stats.root).contains("Lembrete diário"));
        f.capture(stats.root, "estatisticas-5230", 340, 760);
        UiPanel.dismissAll(a);
        PdfTools.show(a);
        assertTrue(BooksToolsTest.labels(latest().root).contains("Juntar PDFs"));
        f.capture(latest().root, "ferramentas-pdf-5230", 340, 620);
        UiPanel.dismissAll(a);
        StudyAgenda.prefs(a).edit().clear().apply();
        StudyAgenda.add(a, "Exame de Biologia", "Exame", "Biologia", ReadingStats.shift(now, 2), 1);
        StudyAgenda.add(a, "Trabalho de História", "Trabalho", "História", ReadingStats.shift(now, 12), 3);
        StudyAgenda.show(a);
        assertTrue(BooksToolsTest.labels(latest().root).contains("Exame de Biologia"));
        f.capture(latest().root, "provas-5230", 340, 560);
        UiPanel.dismissAll(a);
        Pomodoro.show(a);
        assertTrue(BooksToolsTest.labels(latest().root).contains("Clássico 25 + 5 min"));
        f.capture(latest().root, "pomodoro-5230", 340, 560);
        UiPanel.dismissAll(a);
        OpenLibraries.show(a);
        assertTrue(BooksToolsTest.labels(latest().root).contains("Livros em português"));
        f.capture(latest().root, "bibliotecas-5230", 340, 560);
        UiPanel.dismissAll(a);
        PrivateFolder.panel(a);
        assertTrue(BooksToolsTest.labels(latest().root).contains("Adicionar ficheiros"));
        UiPanel.dismissAll(a);
        WordLookup.show(a, "cloroplasto");
        assertTrue(BooksToolsTest.labels(latest().root).contains("Traduzir"));
        f.capture(latest().root, "dicionario-5230", 340, 480);
    }

    @Test public void storageMapAggregatesFoldersFromTheCatalog() throws Exception {
        File root = android.os.Environment.getExternalStorageDirectory();
        File big = new File(root, "Filmes/antigo.mkv"), photo = new File(root, "DCIM/Camera/a.jpg");
        big.getParentFile().mkdirs();
        photo.getParentFile().mkdirs();
        Files.write(big.toPath(), new byte[10]);
        Files.write(photo.toPath(), new byte[10]);
        android.database.sqlite.SQLiteDatabase db = a.db.getWritableDatabase();
        long old = System.currentTimeMillis() - 400L * 86400000L;
        db.execSQL("INSERT OR REPLACE INTO files(uri,root,name,path,mime,category,size,modified,norm,body,gen,parent) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{"file://" + big.getPath(), root.getPath(), big.getName(), big.getPath(), "video/x-matroska", "Vídeos", 80L * 1024 * 1024, old, "", "", 0, big.getParent()});
        db.execSQL("INSERT OR REPLACE INTO files(uri,root,name,path,mime,category,size,modified,norm,body,gen,parent) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{"file://" + photo.getPath(), root.getPath(), photo.getName(), photo.getPath(), "image/jpeg", "Imagens", 3L * 1024 * 1024, System.currentTimeMillis(), "", "", 0, photo.getParent()});
        StorageMap.Model m = StorageMap.build(a.db, Collections.singletonList(root), System.currentTimeMillis());
        String r = root.getPath();
        assertEquals(83L * 1024 * 1024, (long) m.total.get(r));
        assertEquals(3L * 1024 * 1024, (long) m.total.get(r + "/DCIM"));
        assertTrue(m.children.get(r).contains(r + "/Filmes"));
        assertTrue(m.children.get(r + "/DCIM").contains(r + "/DCIM/Camera"));
        assertEquals(1, m.old.size());
        assertEquals(80L * 1024 * 1024, m.types.get("Vídeos")[0]);
        assertEquals(1, m.types.get("Imagens")[1]);
        StorageMap.model = m;
        StorageMap.open(a, null);
        UiPanel p = latest();
        String labels = BooksToolsTest.labels(p.root);
        assertTrue(labels, labels.contains("Filmes"));
        assertTrue(labels.contains("antigo.mkv"));
        assertTrue(labels.contains("Memória interna"));
        assertTrue(labels.contains("Vídeos"));
        assertFalse("sem caminhos crus", labels.contains(r + "/Filmes"));
        f.capture(p.root, "mapa-armazenamento-5230", 340, 1500);
    }
}
