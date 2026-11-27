package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * "Seeky.exe --self-test": checks the shared logic and the desktop storage without a window, so the
 * Windows build can be verified on a computer (and in CI). Prints PASS lines; exit code 0 when all pass.
 */
final class DeskSelfTest {
    private DeskSelfTest() {}
    static int checks; static boolean ok = true;
    static void check(boolean v, String name) { checks++; System.out.println((v ? "PASS " : "FAIL ") + name); ok &= v; }
    /** A small MyBible Bible (with "stories" titles) and its ".commentaries" module, written for the tests and the screenshots. */
    static File myBible(File dir) throws Exception {
        File bible = new File(dir, "Exemplo.SQLite3"), notes = new File(dir, "Exemplo.commentaries.SQLite3");
        bible.delete(); notes.delete();
        try (java.sql.Connection db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + bible.getPath()); java.sql.Statement s = db.createStatement()) {
            s.execute("CREATE TABLE books(book_number NUMERIC,short_name TEXT,long_name TEXT)");
            s.execute("INSERT INTO books VALUES(10,'Gn','Génesis'),(230,'Sl','Salmos'),(500,'Jo','João')");
            s.execute("CREATE TABLE verses(book_number NUMERIC,chapter NUMERIC,verse NUMERIC,text TEXT)");
            s.execute("INSERT INTO verses VALUES(10,1,1,'No princípio, Deus criou os céus e a terra.'),(10,1,2,'A terra estava sem forma e vazia; havia trevas sobre a face do abismo.'),(10,1,3,'Deus disse: <i>Haja luz</i>. E houve luz.'),(10,1,4,'Deus viu que a luz era boa e separou a luz das trevas.'),(10,1,5,'Chamou à luz Dia e às trevas Noite.'),(10,1,26,'Deus disse: Façamos o homem à nossa imagem.'),(10,1,27,'Deus criou o homem à sua imagem.'),(230,23,1,'O Senhor é o meu pastor; nada me faltará.'),(230,23,2,'Faz-me descansar em verdes pastos.'),(500,3,16,'Porque Deus amou o mundo de tal maneira que deu o seu Filho unigénito.')");
            s.execute("CREATE TABLE stories(book_number NUMERIC,chapter NUMERIC,verse NUMERIC,order_if_several NUMERIC,title TEXT)");
            s.execute("INSERT INTO stories VALUES(10,1,1,0,'A criação'),(10,1,26,0,'A criação do ser humano'),(230,23,1,0,'O Senhor é o meu pastor'),(500,3,16,0,'O amor de Deus')");
        }
        try (java.sql.Connection db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + notes.getPath()); java.sql.Statement s = db.createStatement()) {
            s.execute("CREATE TABLE info(name TEXT,value TEXT)"); s.execute("INSERT INTO info VALUES('description','Comentário de exemplo')");
            s.execute("CREATE TABLE commentaries(book_number NUMERIC,chapter_number_from NUMERIC,verse_number_from NUMERIC,chapter_number_to NUMERIC,verse_number_to NUMERIC,marker TEXT,text TEXT)");
            s.execute("INSERT INTO commentaries VALUES(10,1,1,1,2,'','<p><b>No princípio</b>: o início de tudo o que existe.</p><p>Deus é o Criador.</p>'),(10,1,3,1,3,'','A primeira palavra de Deus traz luz.'),(500,3,16,3,16,'','O versículo mais conhecido da Bíblia.')");
        }
        return bible;
    }
    /** An MP3 with an ID3v2.3 tag (no sound: enough for the library and the tests). */
    static File mp3(File dir, String name, String title, String artist, String album, String year, String genre, String track) throws IOException {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        String[][] f = {{"TIT2", title}, {"TPE1", artist}, {"TALB", album}, {"TYER", year}, {"TCON", genre}, {"TRCK", track}};
        for (String[] x : f) {
            if (x[1] == null) continue; byte[] v = x[1].getBytes(StandardCharsets.UTF_16); int len = v.length + 1;
            frames.write(x[0].getBytes(StandardCharsets.ISO_8859_1)); frames.write(new byte[]{(byte) (len >> 24), (byte) (len >> 16), (byte) (len >> 8), (byte) len, 0, 0, 1}); frames.write(v);
        }
        byte[] body = frames.toByteArray(); int n = body.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'I', 'D', '3', 3, 0, 0, (byte) ((n >> 21) & 0x7f), (byte) ((n >> 14) & 0x7f), (byte) ((n >> 7) & 0x7f), (byte) (n & 0x7f)}); out.write(body); out.write(new byte[64]);
        File file = new File(dir, name); Files.write(file.toPath(), out.toByteArray()); return file;
    }
    /** An M4A with title, artist, album and year in moov/udta/meta/ilst. */
    static File m4a(File dir, String name, String title, String artist, String album, String year) throws IOException {
        ByteArrayOutputStream ilst = new ByteArrayOutputStream();
        String[][] items = {{"\u00a9nam", title}, {"\u00a9ART", artist}, {"\u00a9alb", album}, {"\u00a9day", year}};
        for (String[] it : items) { byte[] v = it[1].getBytes(StandardCharsets.UTF_8); byte[] data = box("data", concat(new byte[]{0, 0, 0, 1, 0, 0, 0, 0}, v)); ilst.write(box(it[0], data)); }
        byte[] meta = box("meta", concat(new byte[4], box("ilst", ilst.toByteArray())));
        byte[] file = concat(box("ftyp", "M4A isom".getBytes(StandardCharsets.ISO_8859_1)), box("moov", box("udta", meta)));
        File f = new File(dir, name); Files.write(f.toPath(), file); return f;
    }
    static byte[] box(String type, byte[] content) { int n = content.length + 8; byte[] t = type.getBytes(StandardCharsets.ISO_8859_1); return concat(new byte[]{(byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n, t[0], t[1], t[2], t[3]}, content); }
    static byte[] concat(byte[] a, byte[] b) { byte[] o = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, o, a.length, b.length); return o; }
    /** A small EPUB with a cover picture (cover-image) and one chapter. */
    static File epub(File dir, String name, String title, int color) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(300, 450, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics(); g.setColor(new java.awt.Color(color)); g.fillRect(0, 0, 300, 450); g.setColor(java.awt.Color.WHITE); g.fillRect(30, 60, 240, 6); g.fillRect(30, 380, 240, 6);
        g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.BOLD, 30)); int y = 170; for (String w : title.split(" ")) { g.drawString(w, 40, y); y += 40; } g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(img, "png", png);
        File f = new File(dir, name);
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new FileOutputStream(f))) {
            z.putNextEntry(new java.util.zip.ZipEntry("mimetype")); z.write("application/epub+zip".getBytes(StandardCharsets.US_ASCII));
            z.putNextEntry(new java.util.zip.ZipEntry("META-INF/container.xml")); z.write("<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new java.util.zip.ZipEntry("OEBPS/content.opf")); z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">seeky-" + name + "</dc:identifier><dc:title>" + title + "</dc:title><dc:language>pt</dc:language></metadata><manifest><item id=\"capa\" href=\"images/capa.png\" media-type=\"image/png\" properties=\"cover-image\"/><item id=\"c1\" href=\"c1.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"c1\"/></spine></package>").getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new java.util.zip.ZipEntry("OEBPS/c1.xhtml")); z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>" + title + "</title></head><body><h1>" + title + "</h1><p>Um livro de exemplo do Seeky.</p></body></html>").getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new java.util.zip.ZipEntry("OEBPS/images/capa.png")); z.write(png.toByteArray());
        }
        return f;
    }
    /** A phone kept in memory, merging like the real one. */
    static final class FakePhone implements SyncClient.Local {
        final SyncState st = new SyncState();
        public SyncState state(long since) { SyncState c = new SyncState(); c.notes.addAll(st.notes); c.deleted.putAll(st.deleted); c.progress.addAll(st.progress); c.readings.addAll(st.readings); c.cards.addAll(st.cards); return c; }
        public SyncState.Changes apply(SyncState pc) {
            SyncState.Changes ch = SyncState.merge(st, pc);
            for (Map.Entry<String, String> e : ch.relink.entrySet()) for (SyncState.Note n : st.notes) if (n.uuid.equals(e.getKey())) n.uuid = e.getValue();
            for (SyncState.Note n : ch.upsert) { st.notes.removeIf(x -> x.uuid.equals(n.uuid)); st.notes.add(n); }
            st.notes.removeIf(x -> ch.delete.contains(x.uuid)); st.deleted.putAll(ch.tombs);
            for (SyncState.Progress p : ch.progress) { st.progress.removeIf(x -> x.key().equals(p.key())); st.progress.add(p); }
            for (String[] r : ch.readings) st.readings.add(SyncState.reading(r[0], r[1], Integer.parseInt(r[2])));
            return ch;
        }
        SyncState.Note note(String uuid) { for (SyncState.Note n : st.notes) if (n.uuid.equals(uuid)) return n; return null; }
    }
    static boolean syncTest(DeskStore s, File home) throws Exception {
        String realHome = System.getProperty("user.home"); System.setProperty("user.home", home.getPath());
        DeskSync host = new DeskSync(null, s); DeskSync.schema(s);
        SyncServer server = new SyncServer(host, 0, 0); server.start();
        try {
            String phoneId = SyncState.uuid(); FakePhone phone = new FakePhone();
            host.newCode(); String code = host.code(), wrong = code.equals("000000") ? "111111" : "000000";
            boolean refused; try { SyncClient.pair("127.0.0.1", server.port(), phoneId, "Telemóvel de teste", wrong); refused = false; } catch (IOException e) { refused = e.getMessage().contains("Código errado"); }
            SyncClient.Computer pc = SyncClient.pair("127.0.0.1", server.port(), phoneId, "Telemóvel de teste", code);
            boolean codeGone = host.code() == null && pc.token.length == 32;
            // PC has a note and a book read to page 5; the phone has its own note, the same book at page 8 (later) and a chapter read.
            long pcNote = s.update("INSERT INTO notes(title,body,created,updated) VALUES(?,?,?,?)", "Do computador", "Texto PC", 1000, 2000);
            File book = new File(home, "Livro sincronizado.pdf"); Files.write(book.toPath(), new byte[3000]);
            s.update("INSERT OR REPLACE INTO books(path,title,kind,position,total,opened) VALUES(?,?,?,?,?,?)", book.getPath(), "Livro", "pdf", 5, 40, 5000L);
            SyncState.Note a = new SyncState.Note(); a.uuid = SyncState.uuid(); a.title = "Do telemóvel"; a.body = "Texto do telemóvel ![f](note-img:img_sync.png)"; a.created = 1500; a.updated = 3000; phone.st.notes.add(a);
            SyncState.Progress p = new SyncState.Progress(); p.name = "Livro sincronizado.pdf"; p.bytes = 3000; p.kind = "pdf"; p.position = 8; p.total = 40; p.at = 9000; phone.st.progress.add(p);
            phone.st.readings.add(SyncState.reading("2026-11-20", "João", 3));
            SyncClient.Local withMedia = new SyncClient.Local() {
                public SyncState state(long since) throws Exception { SyncState st = phone.state(since); st.media.put("img_sync.png", new byte[]{9, 8, 7}); return st; }
                public SyncState.Changes apply(SyncState c) throws Exception { return phone.apply(c); }
            };
            SyncClient.Result r1 = SyncClient.sync(pc, phoneId, "Telemóvel de teste", withMedia, 0);
            String pcUuid = DeskSync.uuidOf(s, pcNote);
            boolean first = r1.notesThere == 1 && phone.note(pcUuid) != null && s.count("SELECT COUNT(*) FROM notes WHERE title='Do telemóvel'") == 1
                && new File(DeskNotes.media(), "img_sync.png").isFile() && s.count("SELECT position FROM books WHERE path=?", book.getPath()) == 8
                && s.count("SELECT COUNT(*) FROM readings WHERE book='João' AND chapter=3") == 1;
            // Edited on the phone later; deleted on the computer.
            phone.note(pcUuid).body = "Editado no telemóvel"; phone.note(pcUuid).updated = 99999;
            long phoneNoteHere = DeskSync.localOf(s, a.uuid); s.update("DELETE FROM notes WHERE id=?", phoneNoteHere); DeskSync.deleted(s, phoneNoteHere);
            SyncClient.sync(pc, phoneId, "Telemóvel de teste", phone, 1);
            boolean second = s.rows("SELECT body FROM notes WHERE id=?", pcNote).get(0)[0].equals("Editado no telemóvel") && phone.note(a.uuid) == null;
            // A third sync changes nothing.
            SyncClient.Result r3 = SyncClient.sync(pc, phoneId, "Telemóvel de teste", phone, 2);
            boolean quiet = r3.notesThere == 0 && r3.notesHere == 0;
            // Files: phone → computer (Transferências\Seeky) and computer → phone (outbox).
            byte[] data = new byte[700000]; new Random(3).nextBytes(data);
            String saved = SyncClient.send(pc, phoneId, "Telemóvel de teste", "foto da reunião.jpg", data.length, new ByteArrayInputStream(data));
            boolean upload = Arrays.equals(Files.readAllBytes(new File(DeskSync.inbox(), saved).toPath()), data);
            File out = new File(home, "para o telemóvel.pdf"); Files.write(out.toPath(), data);
            s.update("INSERT INTO sync_outbox(id,path,name,size,added) VALUES(?,?,?,?,?)", SyncState.uuid(), out.getPath(), out.getName(), out.length(), 1L);
            ByteArrayOutputStream got = new ByteArrayOutputStream(); String[] gotName = {""};
            int received = SyncClient.receive(pc, phoneId, "Telemóvel de teste", new SyncClient.Sink() { public OutputStream open(String n, long size) { gotName[0] = n; return got; } public void done(String n, boolean ok) {} });
            boolean download = received == 1 && gotName[0].equals("para o telemóvel.pdf") && Arrays.equals(got.toByteArray(), data) && host.outbox().isEmpty();
            // A forgotten phone cannot sync any more.
            host.forget(phoneId); boolean locked; try { SyncClient.sync(pc, phoneId, "x", phone, 3); locked = false; } catch (IOException e) { locked = e.getMessage().contains("já não está ligado"); }
            System.out.println("  ligado: recusa=" + refused + " código=" + codeGone + " 1.ª=" + first + " 2.ª=" + second + " calma=" + quiet + " envio=" + upload + " receber=" + download + " esquecido=" + locked);
            return refused && codeGone && first && second && quiet && upload && download && locked;
        } finally { server.close(); System.setProperty("user.home", realHome); }
    }
    /** 5.57 · Notes written in the one-page editor come back as the same marks (the phone, Word and PDF read them). */
    static final String[] NOTES = {
        "# Estudo\nTexto com **negrito**, *itálico*, __sublinhado__, ~~riscado~~ e ==destaque==.\n{cor:azul}Frase azul{/cor} e normal.\n\n- um\n- dois\n  - dentro\n1. primeiro\n2. segundo\n[ ] fazer\n[x] feito\n> Citação\n[c] Centrado\n[d] À direita\n  Recuado\n---\n| A | B |\n|---|---|\n| 1 | 2 |\nLer João 3:16 e Romanos 5:8.",
        "## Sermão\n\n### Pontos\n1. **Fé**: confiar\n2. *Esperança*\n\n#### Fim\nSem marcas."
    };
    /** Runs the editor page in a hidden window and compares what it gives back. */
    static boolean notes() {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1); boolean[] all = {true};
        javafx.application.Platform.startup(() -> {
            javafx.scene.web.WebView web = new javafx.scene.web.WebView(); javafx.scene.web.WebEngine e = web.getEngine();
            String script = DeskNotes.editorScript(); int[] at = {0}; DeskNotesBridge bridge = new DeskNotesBridge(m -> {}); web.setUserData(bridge);
            e.getLoadWorker().stateProperty().addListener((o, a, st) -> {
                if (st != javafx.concurrent.Worker.State.SUCCEEDED) return;
                if (at[0] >= NOTES.length) { // 5.58: a book page lights up the paragraph being read aloud
                    Object lit = e.executeScript("seekyRead(1); var p=document.querySelectorAll('p.para'); p.length + ':' + (p[1].className.indexOf('reading')>=0) + ':' + (p[0].className.indexOf('reading')>=0)");
                    boolean ok7 = "3:true:false".equals(String.valueOf(lit)); all[0] &= ok7;
                    System.out.println((ok7 ? "PASS" : "FAIL") + " livro: parágrafo lido realçado (" + lit + ")");
                    done.countDown(); return;
                }
                ((netscape.javascript.JSObject) e.executeScript("window")).setMember("seeky", bridge); e.executeScript(script);
                String back = String.valueOf(e.executeScript("markup()")), want = NOTES[at[0]];
                boolean same = back.equals(want); all[0] &= same;
                System.out.println((same ? "PASS" : "FAIL") + " nota " + (at[0] + 1) + " volta igual do editor" + (same ? "" : "\n--- esperado\n" + want + "\n--- recebido\n" + back));
                Object passages = e.executeScript("decorate(); document.querySelectorAll('span.passage').length");
                if (at[0] == 0) { boolean ok2 = passages instanceof Number && ((Number) passages).intValue() == 2; all[0] &= ok2; System.out.println((ok2 ? "PASS" : "FAIL") + " passagens marcadas no editor: " + passages); }
                // Typing: a new bullet and a ticked task survive.
                if (at[0] == 1) {
                    e.executeScript("var p=document.createElement('p');p.textContent='Nova linha com Salmo 23:1';document.getElementById('doc').appendChild(p);decorate();");
                    String typed = String.valueOf(e.executeScript("markup()")); boolean ok3 = typed.endsWith("Sem marcas.\nNova linha com Salmo 23:1"); all[0] &= ok3;
                    System.out.println((ok3 ? "PASS" : "FAIL") + " texto escrito no editor guardado" + (ok3 ? "" : ": " + typed));
                    // The toolbar: bold, then a task list, on the selected line.
                    e.executeScript("var d=document.getElementById('doc'),p=d.lastChild,r=document.createRange();r.selectNodeContents(p);var s=window.getSelection();s.removeAllRanges();s.addRange(r);cmd('bold','');");
                    String bold = String.valueOf(e.executeScript("markup()")); boolean ok4 = bold.endsWith("\n**Nova linha com Salmo 23:1**"); all[0] &= ok4;
                    System.out.println((ok4 ? "PASS" : "FAIL") + " negrito pela barra" + (ok4 ? "" : ": " + bold));
                    e.executeScript("cmd('list','task');");
                    String task = String.valueOf(e.executeScript("markup()")); boolean ok5 = task.endsWith("\n[ ] **Nova linha com Salmo 23:1**"); all[0] &= ok5;
                    System.out.println((ok5 ? "PASS" : "FAIL") + " tarefa pela barra" + (ok5 ? "" : ": " + task));
                    e.executeScript("var d=document.getElementById('doc'),p=document.createElement('p');p.textContent='Título novo';d.appendChild(p);var r=document.createRange();r.selectNodeContents(p);r.collapse(true);var s=window.getSelection();s.removeAllRanges();s.addRange(r);cmd('block','h2');");
                    String head = String.valueOf(e.executeScript("markup()")); boolean ok6 = head.endsWith("\n## Título novo"); all[0] &= ok6;
                    System.out.println((ok6 ? "PASS" : "FAIL") + " título pela barra" + (ok6 ? "" : ": " + head));
                }
                if (++at[0] < NOTES.length) e.loadContent(DeskNotesHtml.editor(NOTES[at[0]], DeskNotes.media()));
                else { // 5.58: then a book page
                    DeskReader.Book b = new DeskReader.Book(); b.title = "Livro"; b.names.add("Capítulo 1"); b.texts.add("Primeiro parágrafo.\n\nSegundo parágrafo com Salmos 23:1.\nTerceiro.");
                    try { e.loadContent(DeskReader.chapterHtml(b, 0, Collections.emptyList())); } catch (RuntimeException ex) { System.out.println("FAIL livro: " + ex); all[0] = false; done.countDown(); }
                }
            });
            e.loadContent(DeskNotesHtml.editor(NOTES[0], DeskNotes.media()));
        });
        try { if (!done.await(60, java.util.concurrent.TimeUnit.SECONDS)) { System.out.println("FAIL editor de notas não respondeu"); all[0] = false; } } catch (InterruptedException ex) { all[0] = false; }
        System.out.println((all[0] ? "PASS" : "FAIL") + " editor de notas");
        return all[0];
    }
    static boolean run() {
        try {
            File home = Files.createTempDirectory("seeky-selftest").toFile(); System.setProperty("seeky.home", home.getPath());
            check(DeskVersion.NAME.matches("\\d+\\.\\d+\\.\\d+") && DeskVersion.CODE > 0, "versão " + DeskVersion.NAME);
            BibleReferences.Reference r = BibleReferences.lookup("João 3:16"); check(r != null && r.book.equals("João") && r.chapter == 3, "referências bíblicas");
            check(!NotepadDoc.parse("# Título\n**negrito** e [ ] tarefa").isEmpty(), "formato das notas");
            check(NotepadExport.docx("T", "# Olá\nTexto", null, 0).length > 1000, "exportar Word");
            ByteArrayOutputStream pdf = new ByteArrayOutputStream(); DeskPdf.note("T", "# Olá\nTexto com acentos: ção, é.", home, pdf); check(new String(pdf.toByteArray(), 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-"), "exportar PDF");
            try (DeskStore s = new DeskStore()) {
                File bible = new File(home, "biblia.csv"); Files.write(bible.toPath(), "GEN,1,1,No princípio criou Deus os céus e a terra.\nGEN,1,2,\nJHN,3,16,Porque Deus amou o mundo de tal maneira.\n".getBytes(StandardCharsets.UTF_8));
                int n = DeskBible.importFile(s, bible, "Teste", () -> false); check(n == 2, "importar Bíblia CSV (versículo vazio saltado)");
                check(s.passage(s.bible(), BibleReferences.lookup("João 3:16")).contains("amou o mundo"), "ler passagem");
                s.update("INSERT INTO notes(title,body,created,updated) VALUES(?,?,?,?)", "Nota", "Ver João 3:16", 1, 1); check(s.count("SELECT COUNT(*) FROM notes") == 1, "guardar nota");
                // MyBible: títulos de secção (stories) e o módulo de comentários ao lado.
                int verses = DeskBible.importFile(s, myBible(home), "Exemplo MyBible", () -> false); long mb = s.bible();
                check(verses == 10 && s.count("SELECT COUNT(*) FROM titles WHERE bible=?", mb) == 4 && s.count("SELECT COUNT(*) FROM comments") == 3
                    && s.rows("SELECT text FROM comments WHERE chapter=1 AND verse=1").get(0)[0].toString().equals("No princípio: o início de tudo o que existe.\nDeus é o Criador.")
                    && s.rows("SELECT text FROM titles WHERE book='Génesis' AND verse=26").get(0)[0].equals("A criação do ser humano"), "Bíblia MyBible com títulos e comentários");
                // Música: etiquetas ID3 e MP4, pastas, mais ouvidas.
                File songs = new File(home, "Musica"); songs.mkdirs(); new File(songs, "Album").mkdirs();
                mp3(new File(songs, "Album"), "01.mp3", "Grande é o Senhor", "Coro Seeky", "Louvores", "2019", "(83)", "1/10");
                m4a(songs, "b.m4a", "Aleluia", "Banda", "Ao vivo", "2021-05-02");
                Files.write(new File(songs, "Artista X - Canção sem etiquetas.mp3").toPath(), new byte[200]);
                DeskTags a = DeskTags.read(new File(songs, "Album/01.mp3")), b = DeskTags.read(new File(songs, "b.m4a")), c = DeskTags.read(new File(songs, "Artista X - Canção sem etiquetas.mp3"));
                check(a.title.equals("Grande é o Senhor") && a.artist.equals("Coro Seeky") && a.album.equals("Louvores") && a.year == 2019 && a.track == 1 && b.title.equals("Aleluia") && b.year == 2021 && b.album.equals("Ao vivo") && c.artist.equals("Artista X") && c.title.equals("Canção sem etiquetas"), "etiquetas das músicas (ID3 e M4A)");
                int[] scanned = DeskLibrary.scan(s, DeskLibrary.AUDIO, Collections.singletonList(songs.getPath()));
                String one = new File(songs, "Album/01.mp3").getAbsolutePath(); DeskLibrary.played(s, one); DeskLibrary.played(s, one);
                List<DeskLibrary.Track> top = DeskLibrary.top(s, DeskLibrary.AUDIO, DeskLibrary.WEEK, System.currentTimeMillis(), 5);
                check(scanned[0] == 3 && DeskLibrary.scan(s, DeskLibrary.AUDIO, Collections.singletonList(songs.getPath()))[0] == 0 && top.size() == 1 && top.get(0).plays == 2 && s.count("SELECT COUNT(DISTINCT album) FROM tracks WHERE kind='audio'") == 3, "biblioteca de música (pastas, álbuns, mais ouvidas)");
                // Livros: importar pasta e subpastas, capas do EPUB e do PDF.
                File shelf = new File(home, "Livros"); new File(shelf, "Sub").mkdirs();
                File ep = epub(new File(shelf, "Sub"), "fe.epub", "A fé que vence", 0x2f5d50);
                File bookPdf = new File(shelf, "notas.pdf"); try (OutputStream o = new FileOutputStream(bookPdf)) { DeskPdf.note("Notas", "# Notas\nTexto", home, o); }
                Files.write(new File(shelf, "texto.txt").toPath(), "Um texto.".getBytes(StandardCharsets.UTF_8)); Files.write(new File(shelf, "foto.jpg").toPath(), new byte[10]);
                int imported = DeskReader.importBooks(s, DeskReader.findBooks(shelf)), again2 = DeskReader.importBooks(s, DeskReader.findBooks(shelf));
                File epCover = DeskCovers.cover(ep), pdfCover = DeskCovers.cover(bookPdf), txtCover = DeskCovers.cover(new File(shelf, "texto.txt"));
                java.awt.image.BufferedImage ci = epCover == null ? null : javax.imageio.ImageIO.read(epCover);
                check(imported == 3 && again2 == 0 && ci != null && ci.getWidth() == DeskCovers.W && (ci.getRGB(120, 300) & 0xffffff) == 0x2f5d50 && pdfCover != null && txtCover == null && s.rows("SELECT title FROM books WHERE path=?", ep.getPath()).get(0)[0].equals("A fé que vence"), "livros: importar pasta e capas (EPUB, PDF)");
                // Flash: índice FTS5 dos nomes, categorias e procura por início de palavra.
                File docs = new File(home, "Docs"); new File(docs, "sub").mkdirs(); new File(docs, ".oculta").mkdirs();
                for (String fn : new String[]{"Relatório final 2025.pdf", "sub/relatorio_vendas.xlsx", "sub/foto do relatório.jpg", "Planeamento.docx", ".oculta/relatorio secreto.pdf", "musica/Relato.mp3"}) { File f = new File(docs, fn); f.getParentFile().mkdirs(); Files.write(f.toPath(), new byte[10]); }
                DeskFlashIndex idx = new DeskFlashIndex(new File(home, "indice-teste.db")); int indexed = idx.rebuild(Collections.singletonList(docs), () -> false);
                List<DeskFlashIndex.Hit> rel = idx.search("relat", "Tudo", 20), pdfHits = idx.search("relat", "PDF", 20), two = idx.search("relat fin", "Tudo", 20), recent = idx.search("", "Imagens", 5);
                check(indexed == 5 && rel.size() == 4 && pdfHits.size() == 1 && pdfHits.get(0).name.equals("Relatório final 2025.pdf") && two.size() == 1 && recent.size() == 1 && DeskFlashIndex.category("x.MKV").equals("Vídeos"), "Flash: índice e pesquisa (" + rel.size() + ")");
                // Notas entre o telemóvel e o computador (.seekynotes), com imagem e sem repetir.
                File img = new File(DeskNotes.media(), "img_teste.png"); Files.write(img.toPath(), new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
                s.update("INSERT INTO notes(title,body,folder,created,updated) VALUES(?,?,?,?,?)", "Com imagem", "Texto\n![Imagem](note-img:img_teste.png)", "Sermões", 2, 3);
                File pack = new File(home, "notas" + NotesTransfer.EXTENSION); int exported = DeskNotes.exportTo(s, pack);
                s.update("DELETE FROM notes WHERE title=?", "Com imagem"); img.delete();
                int[] first = DeskNotes.importFrom(s, pack), again = DeskNotes.importFrom(s, pack);
                check(exported == 2 && first[0] == 1 && first[1] == 1 && again[0] == 0 && again[1] == 2 && img.isFile() && s.count("SELECT COUNT(*) FROM notes WHERE folder='Sermões'") == 1, "notas do telemóvel (.seekynotes)");
                // Marcações nos livros.
                DeskMarks.add(s, "livro.txt", "Livro", 0, "Parte 1", "amou o mundo", DeskMarks.COLORS[1], "nota");
                String painted = DeskMarks.paint("Porque Deus amou o mundo de tal maneira.", DeskMarks.list(s, "livro.txt", 0));
                check(painted.contains("<mark id='m") && painted.contains(">amou o mundo</mark>") && DeskMarks.paint("<a href=\"x\">amou o mundo</a>", DeskMarks.list(s, "livro.txt", 0)).contains("<mark"), "marcações nos livros");
                // Estudos: o que a Seeky IA devolve passa a cartões e perguntas de escolha múltipla; revisão espaçada como no telemóvel.
                DeskStudies.schema(s); long subj = s.update("INSERT INTO st_subjects(title,color,created,updated) VALUES(?,?,?,?)", "Teologia", 0, 1L, 1L);
                int[] made = DeskStudies.save(s, subj, "Pergunta: O que é a graça?\nResposta: Favor imerecido.\n\nPergunta: Quem escreveu Romanos?\nResposta: Paulo.\n\nPERGUNTAS\nQ: Quantos evangelhos há?\nA) 2\nB) 3\nC) 4\nD) 5\nCerta: C\nPorquê: Mateus, Marcos, Lucas e João.\nQ: Sem opções\nCerta: A");
                StudySchedule.Next good = StudySchedule.next(0, 0, 0, 0, 2), retry = StudySchedule.next(0, 3, 2, 0, 0);
                check(made[0] == 2 && made[1] == 1 && s.rows("SELECT answer,correct FROM st_questions").get(0)[0].equals("4") && good.interval == 1 && retry.interval == 0, "estudos: cartões e perguntas da IA, revisão espaçada");
                // Seeky IA mais forte (5.51): vários documentos, plano de estudo guardado nos Estudos.
                String many = SeekyAiDeep.block(Arrays.asList(new SeekyAiDeep.Doc("Graça", "Introdução.\n\nA graça é favor imerecido de Deus."), new SeekyAiDeep.Doc("Receitas", "Bolo de farinha e ovos.")), "O que é a graça?", 4000);
                SeekyAiDeep.Plan plan = SeekyAiDeep.parse("TÍTULO: João\nOBJECTIVO: Ler o evangelho.\nDIA 1 | Introdução | Ler João 1\nDIA 2 | Sinais | Ler João 2\nCARTÕES\nPergunta: Quantos sinais?\nResposta: Sete.", 6);
                DeskAiHub.schema(s); long course = s.update("INSERT INTO st_courses(title,description,created,updated) VALUES(?,?,?,?)", "Bacharelato", "", 1L, 1L); long planned = DeskAiHub.save(s, plan, 1000L, 3, 30, course, "module");
                check(many.contains("[D1·T") && !many.contains("[D2·T") && s.count("SELECT COUNT(*) FROM st_tasks WHERE subject=? AND estimate=30", planned) == 2 && s.count("SELECT COUNT(*) FROM st_cards WHERE subject=?", planned) == 1 && s.count("SELECT COUNT(*) FROM notes WHERE subject=? AND title LIKE 'Plano de estudo%'", planned) == 1 && s.count("SELECT COUNT(*) FROM st_subjects WHERE id=? AND course_id=? AND kind='module'", planned, course) == 1, "seeky ia: vários documentos e plano de estudo num curso");
                SyncState pcSide = new SyncState(), phoneSide = new SyncState();
                SyncState.StudyCard sc = new SyncState.StudyCard(); sc.subject = "Teologia"; sc.front = "O que é a graça?"; sc.back = "Favor"; sc.updated = 10; pcSide.studyCards.add(sc);
                SyncState.StudyCard newer = new SyncState.StudyCard(); newer.subject = "teologia"; newer.front = "O que é a  graça?"; newer.back = "Favor imerecido"; newer.updated = 20; phoneSide.studyCards.add(newer);
                SyncState.Task gone = new SyncState.Task(); gone.subject = "Teologia"; gone.title = "Ler"; gone.due = 0; gone.updated = 5; pcSide.tasks.add(gone); phoneSide.gone.put(gone.key(), 50L);
                SyncState.Changes sch = SyncState.merge(pcSide, phoneSide);
                check(sch.studyCards.size() == 1 && sch.studyCards.get(0).back.equals("Favor imerecido") && sch.remove.contains(gone.key()) && SyncState.merge(phoneSide, pcSide).studyCards.isEmpty(), "estudos: juntar com o telemóvel (mais recente, apagadas)");
                // Áudio (5.53): MP3 com etiquetas; no Windows, a voz do Windows gravada e convertida em MP3.
                java.io.File wavIn = new java.io.File(home, "teste.wav"), mp3Out = new java.io.File(home, "01 - Capítulo.mp3");
                { int rate = 22050, frames = rate; java.nio.ByteBuffer head = java.nio.ByteBuffer.wrap(DeskVoices.wavHeader(rate, 1, 16, frames * 2L)); byte[] pcm = new byte[frames * 2]; for (int i = 0; i < frames; i++) { short v = (short) (Math.sin(2 * Math.PI * 440 * i / rate) * 8000); pcm[2 * i] = (byte) v; pcm[2 * i + 1] = (byte) (v >> 8); } try (java.io.FileOutputStream o = new java.io.FileOutputStream(wavIn)) { o.write(head.array()); o.write(pcm); } }
                double secs = Mp3Writer.fromWav(wavIn, mp3Out, new Mp3Writer.Tags("Capítulo 1", "Livro", "Seeky", 1, 3), () -> false, null);
                byte[] mp3b = java.nio.file.Files.readAllBytes(mp3Out.toPath()); boolean hashes = true; for (DeskVoices.Voice v : DeskVoices.PIPER) hashes &= v.sha.matches("[0-9a-f]{64}") && v.jsonSha.matches("[0-9a-f]{64}");
                check(mp3b[0] == 'I' && mp3b[1] == 'D' && mp3b[2] == '3' && Math.abs(secs - 1.0) < 0.01 && mp3b.length > 6000 && mp3b.length < 12000 && hashes && DeskVoices.ENGINE_SHA.matches("[0-9a-f]{64}") && DeskAudio.fileName(3, 12, "Cap: 3/a").equals("03 - Cap 3 a.mp3"), "áudio: MP3 com etiquetas e vozes Piper verificadas");
                // Vozes naturais do Edge (5.54): a ponte local com a página do Edge, simulada aqui; e o Azure para gravar.
                boolean bridge = false;
                { DeskEdge e = new DeskEdge(); try {
                    String page = http("GET", e.base(), null, "127.0.0.1:" + e.port()), wrongHost = http("GET", e.base(), null, "evil.example:" + e.port()), wrongToken = http("GET", "http://127.0.0.1:" + e.port() + "/s/x/", null, "127.0.0.1:" + e.port());
                    http("POST", e.base() + "voices", "[{\"name\":\"Microsoft Raquel Online (Natural) - Portuguese (Portugal)\",\"lang\":\"pt-PT\"},{\"name\":\"Microsoft David - English (United States)\",\"lang\":\"en-US\"},{\"name\":\"Microsoft Francisca Online (Natural) - Portuguese (Brazil)\",\"lang\":\"pt-BR\"}]", "127.0.0.1:" + e.port());
                    boolean[] ended = {false}; int id = e.queue("Olá mundo.", "Microsoft Raquel Online (Natural) - Portuguese (Portugal)", 1.0, () -> ended[0] = true);
                    String cmd = http("GET", e.base() + "next", null, "127.0.0.1:" + e.port());
                    http("POST", e.base() + "event", "{\"type\":\"end\",\"id\":" + id + "}", "127.0.0.1:" + e.port());
                    List<DeskEdge.Voice> nat = DeskEdge.sorted(e.voices, true);
                    System.out.println("  edge-ponte: página=" + page.contains("speechSynthesis") + " host=" + wrongHost + " código=" + wrongToken + " fala=" + cmd.contains("Olá mundo.") + " fim=" + ended[0] + " vozes=" + DeskEdge.sorted(e.voices, true).size() + (DeskEdge.sorted(e.voices, true).isEmpty() ? "" : " " + DeskEdge.sorted(e.voices, true).get(0).azure() + " / " + DeskEdge.sorted(e.voices, true).get(0).label()));
                    bridge = page.contains("speechSynthesis") && wrongHost.equals("404") && wrongToken.equals("404") && cmd.contains("\"op\":\"speak\"") && cmd.contains("Olá mundo.") && ended[0]
                        && nat.size() == 2 && nat.get(0).azure().equals("pt-PT-RaquelNeural") && nat.get(1).azure().equals("pt-BR-FranciscaNeural") && nat.get(0).label().startsWith("Raquel · Português");
                } finally { e.close(); } }
                String longText = String.join(" ", Collections.nCopies(800, "Frase curta.")); List<String> pieces = DeskAzure.pieces(longText);
                check(bridge && pieces.size() == 5 && String.join(" ", pieces).equals(longText) && pieces.stream().allMatch(x -> x.length() <= DeskAzure.PIECE) && DeskAzure.ssml("A & <b>", "pt-PT-RaquelNeural", "pt-PT", 2).contains("A &amp; &lt;b&gt;") && DeskAzure.ssml("x", "pt-PT-RaquelNeural", "pt-PT", 2).contains("rate='+16%'"), "áudio: vozes naturais do Edge (ponte local) e Azure para gravar");
                if (DeskSecret.windows() && DeskEdge.available()) { try { List<DeskEdge.Voice> real = DeskEdge.sorted(DeskEdge.get().voices(45000), true); System.out.println("  edge: " + real.size() + " vozes naturais" + (real.isEmpty() ? "" : " (por ex. " + real.get(0).name + ")")); } catch (Exception ex) { System.out.println("  edge: " + ex.getMessage()); } finally { if (DeskEdge.instance != null) { DeskEdge.instance.close(); DeskEdge.instance = null; } } }
                if (DeskSecret.windows()) {
                    java.io.File spoken = new java.io.File(home, "voz.wav"), spokenMp3 = new java.io.File(home, "voz.mp3");
                    boolean voiced = false; try { DeskVoices.windowsWav("", 0, "Olá. Este é o Seeky a gravar um capítulo em áudio.", spoken, () -> false); voiced = Mp3Writer.fromWav(spoken, spokenMp3, new Mp3Writer.Tags("Teste", "Seeky", "Seeky", 1, 1), () -> false, null) > 1.0 && spokenMp3.length() > 8000; } catch (Exception e) { System.out.println("voz do Windows: " + e.getMessage()); }
                    check(voiced, "áudio: voz do Windows gravada em MP3");
                } else check(DeskVoices.lines("Um\n\n  dois  \ntrês").equals("Um\ndois\ntrês\n"), "áudio: texto preparado para a voz");
                // Cursos › módulos ou disciplinas (5.52): vindos do telemóvel e o curso escrito à mão da 5.50.
                SyncState.Changes cc = new SyncState.Changes(); SyncState.Course co = new SyncState.Course(); co.title = "Curso do telemóvel"; co.description = "Três anos"; co.updated = 30; cc.courses.add(co);
                SyncState.Subject sj = new SyncState.Subject(); sj.title = "Grego"; sj.course = "Curso do telemóvel"; sj.kind = "module"; sj.updated = 30; cc.subjects.add(sj); DeskSync.applyStudy(s, cc);
                s.update("INSERT INTO st_subjects(title,color,created,updated,course) VALUES(?,?,?,?,?)", "Antiga", 0, 1L, 1L, "Curso antigo"); DeskStudies.schema(s);
                SyncState back = DeskSync.export(s, 0); boolean travels = false; for (SyncState.Subject x : back.subjects) if (x.title.equals("Grego") && x.course.equals("Curso do telemóvel") && x.kind.equals("module")) travels = true;
                check(s.count("SELECT COUNT(*) FROM st_subjects JOIN st_courses ON st_courses.id=st_subjects.course_id WHERE st_subjects.title='Grego' AND st_courses.title='Curso do telemóvel' AND kind='module'") == 1
                    && s.count("SELECT COUNT(*) FROM st_subjects JOIN st_courses ON st_courses.id=st_subjects.course_id WHERE st_subjects.title='Antiga' AND st_courses.title='Curso antigo'") == 1 && travels, "estudos: cursos com módulos e disciplinas");
                // Seeky ligado: emparelhar com o código, juntar notas/leitura/estudo, apagar, ficheiros nos dois sentidos.
                check(syncTest(s, home), "Seeky ligado (emparelhar, sincronizar, ficheiros)");
                List<BibleGame.Question> q = new BibleGame(BibleGame.EASY, new Random(1), DeskStudy.verses(s)).round(); check(q.size() == BibleGame.ROUND, "jogo da Bíblia (" + q.size() + " perguntas)");
            }
            check(DeskThemes.ALL.size() >= 21 && DeskThemes.find("Petróleo").css().contains("-seeky-accent: #f7c9a1") && DeskThemes.find(DeskThemes.SCHEDULE) != null, "temas do telemóvel");
            check(DeskFocus.hours(75).equals("1 h 15 min") && DeskFocus.mmss(1500).equals("25:00"), "foco (Pomodoro)");
            check(SeekyAiModels.current().startsWith("gemini-"), "modelo Gemini " + SeekyAiModels.current());
            File txt = new File(home, "livro.txt"); Files.write(txt.toPath(), "Capítulo um.\n\nTexto do livro.".getBytes(StandardCharsets.UTF_8)); check(DeskReader.chapters(txt).texts.size() == 1, "ler livro TXT");
            Class.forName("org.apache.pdfbox.pdmodel.PDDocument"); check(true, "PDFBox presente");
            Class.forName("javafx.scene.media.MediaPlayer"); check(true, "JavaFX Media presente");
            // 5.57: passagens num PDF reconhecidas com o sítio na página (como no telemóvel).
            { File pp = new File(home, "passagens.pdf"); try (OutputStream o = new FileOutputStream(pp)) { DeskPdf.note("Estudo", "# Estudo\nLeia João 3:16 com atenção e depois Romanos 8:28.", home, o); }
              try (org.apache.pdfbox.pdmodel.PDDocument pd = org.apache.pdfbox.pdmodel.PDDocument.load(pp)) {
                  DeskPdfPassages.Page pg = DeskPdfPassages.find(pd, 0); float w = pd.getPage(0).getMediaBox().getWidth(), h = pd.getPage(0).getMediaBox().getHeight();
                  boolean inside = !pg.hits.isEmpty(); for (DeskPdfPassages.Hit hit : pg.hits) for (double[] b : hit.boxes) inside &= b[0] >= 0 && b[2] <= w && b[1] >= 0 && b[3] <= h && b[2] > b[0] && b[3] > b[1];
                  check(pg.hits.size() == 2 && pg.hits.get(0).ref.title.equals("João 3:16") && pg.hits.get(1).ref.title.equals("Romanos 8:28") && inside && pg.text.contains("João 3:16"), "passagens no PDF com o sítio na página: " + pg.hits.size());
              } }
            // 5.58: passagens ditas como no telemóvel (não como horas) e um parágrafo por linha (para o realce do que se lê).
            { String said = DeskSpeech.prepare("Leia Salmos 13:40 hoje.\n\n  Depois João 3:16.", "pt-PT"); String[] lines = said.trim().split("\n");
              check(lines.length == 2 && said.contains("capítulo 13") && said.contains("versículo 40") && !said.contains("13:40") && said.contains("capítulo 3"), "passagens lidas como no telemóvel: " + lines[0]); }
            // 5.58: seleccionar e marcar texto num PDF (letra mais próxima, encontrar a marcação depois).
            { File pp = new File(home, "marcar.pdf"); try (OutputStream o = new FileOutputStream(pp)) { DeskPdf.note("Marcar", "# Marcar\nA graça de Deus é suficiente para todos os dias.", home, o); }
              try (org.apache.pdfbox.pdmodel.PDDocument pd = org.apache.pdfbox.pdmodel.PDDocument.load(pp)) {
                  DeskPdfPassages.Page pg = DeskPdfPassages.find(pd, 0); int[] at = DeskPdfPassages.locate(pg, "graça de   Deus"); boolean ok2 = at != null && pg.text.substring(at[0], at[1]).replaceAll("\\s+", " ").equals("graça de Deus");
                  double[] c = at == null ? null : pg.chars.get(at[0]); int near = c == null ? -1 : DeskPdfPassages.nearest(pg, (c[0] + c[2]) / 2, (c[1] + c[3]) / 2);
                  check(ok2 && near == at[0] && DeskPdfPassages.boxes(pg, at[0], at[1]).size() == 1, "marcar texto num PDF");
              } }
            // 5.58: a página seguinte vai para o Edge sem parar a actual, e o Edge diz o parágrafo que está a ler.
            { DeskEdge e = new DeskEdge(); try {
                  int[] para = {-1}; int id = e.queue("Página dois.", "x", 1.0, () -> {}, n -> para[0] = n, true);
                  String cmd = http("GET", e.base() + "next", null, "127.0.0.1:" + e.port());
                  http("POST", e.base() + "event", "{\"type\":\"para\",\"id\":" + id + ",\"para\":3}", "127.0.0.1:" + e.port());
                  check(cmd.contains("\"op\":\"add\"") && para[0] == 3 && e.page().contains("onboundary"), "Edge: página seguinte em fila e parágrafo lido");
              } finally { e.close(); } }
            check(DeskNotesBridge.refsOf("Ver Salmo 23:1 e Mt 5:3").startsWith("4\t15\tSalmos 23:1") || DeskNotesBridge.refsOf("Ver Salmo 23:1 e Mt 5:3").contains("23:1"), "passagens para o editor de notas");
            // 5.56: parar a leitura (ou começar outra) muda a geração, por isso a página seguinte só vem quando a leitura acaba sozinha.
            { int g = DeskSpeech.generation; DeskSpeech.stop(); int g2 = DeskSpeech.generation; DeskSpeech.stop(); check(g2 == g + 1 && DeskSpeech.generation == g + 2 && !DeskSpeech.busy(), "parar a leitura não passa à página seguinte"); }
        } catch (Throwable e) { e.printStackTrace(System.out); check(false, "erro: " + e); }
        System.out.println((ok ? "PASS " : "FAIL ") + checks + " verificações do Seeky Desktop");
        return ok;
    }
    /** A plain HTTP request with a chosen Host header (to test the Edge bridge); returns the body, or the status code when it is not 200. */
    static String http(String method, String url, String body, String host) throws Exception {
        java.net.URL u = new java.net.URL(url);
        try (java.net.Socket s = new java.net.Socket(u.getHost(), u.getPort())) {
            s.setSoTimeout(30000); byte[] b = body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.io.OutputStream o = s.getOutputStream(); o.write((method + " " + u.getPath() + " HTTP/1.1\r\nHost: " + host + "\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII)); o.write(b); o.flush();
            String all = new String(s.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String status = all.substring(9, 12); return status.equals("200") ? all.substring(all.indexOf("\r\n\r\n") + 4) : status;
        }
    }
}
