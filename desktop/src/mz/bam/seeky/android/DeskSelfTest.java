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
                List<BibleGame.Question> q = new BibleGame(BibleGame.EASY, new Random(1), DeskStudy.verses(s)).round(); check(q.size() == BibleGame.ROUND, "jogo da Bíblia (" + q.size() + " perguntas)");
            }
            check(DeskThemes.ALL.size() >= 21 && DeskThemes.find("Petróleo").css().contains("-seeky-accent: #f7c9a1") && DeskThemes.find(DeskThemes.SCHEDULE) != null, "temas do telemóvel");
            check(DeskFocus.hours(75).equals("1 h 15 min") && DeskFocus.mmss(1500).equals("25:00"), "foco (Pomodoro)");
            check(SeekyAiModels.current().startsWith("gemini-"), "modelo Gemini " + SeekyAiModels.current());
            File txt = new File(home, "livro.txt"); Files.write(txt.toPath(), "Capítulo um.\n\nTexto do livro.".getBytes(StandardCharsets.UTF_8)); check(DeskReader.chapters(txt).texts.size() == 1, "ler livro TXT");
            Class.forName("org.apache.pdfbox.pdmodel.PDDocument"); check(true, "PDFBox presente");
            Class.forName("javafx.scene.media.MediaPlayer"); check(true, "JavaFX Media presente");
        } catch (Throwable e) { e.printStackTrace(System.out); check(false, "erro: " + e); }
        System.out.println((ok ? "PASS " : "FAIL ") + checks + " verificações do Seeky Desktop");
        return ok;
    }
}
