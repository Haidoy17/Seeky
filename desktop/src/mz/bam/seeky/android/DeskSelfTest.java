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
