package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** 5.39: rich notes — blocks and styled runs, formatting commands and Word export. */
public final class NotepadDoc539Test {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        String body = "# Título\n## Secção\n#### Pequeno\nTexto **forte**, *leve*, __sublinhado__, ~~riscado~~, ==marcado== e {cor:azul}azul{/cor}.\n- um\n  - dois\n1. primeiro\n2. segundo\n[x] feito\n[ ] por fazer\n> citação\n[c] centrado\n---\n| A | B |\n|---|---|\n| 1 | **2** |\n![Foto](note-img:img_1.png)\n[🎙 Áudio 0:12](note-audio:rec_1.m4a)\n5 * 3 = 15";
        List<NotepadDoc.Block> b = NotepadDoc.parse(body);
        String kinds = ""; for (NotepadDoc.Block x : b) kinds += x.kind + ",";
        check(kinds.equals("h1,h2,h4,p,bullet,bullet,number,number,task,task,quote,p,rule,table,image,audio,p,"), "blocos: " + kinds);
        NotepadDoc.Block p = b.get(3);
        boolean bold = false, italic = false, under = false, strike = false, mark = false, blue = false;
        for (NotepadDoc.Run r : p.runs) { if (r.text.equals("forte")) bold = r.bold; if (r.text.equals("leve")) italic = r.italic; if (r.text.equals("sublinhado")) under = r.underline; if (r.text.equals("riscado")) strike = r.strike; if (r.text.equals("marcado")) mark = r.mark; if (r.text.equals("azul")) blue = r.color == NotepadDoc.color("azul"); }
        check(bold && italic && under && strike && mark && blue, "estilos dentro da linha");
        check(p.text().equals("Texto forte, leve, sublinhado, riscado, marcado e azul."), "texto sem marcas: " + p.text());
        check(b.get(5).level == 1 && b.get(7).number == 2 && b.get(8).done && !b.get(9).done && b.get(11).align == 'c', "recuo, numeração, tarefas e alinhamento");
        check(b.get(13).rows.size() == 2 && b.get(13).rows.get(1).get(1).equals("**2**"), "tabela sem a linha separadora");
        check(b.get(14).ref.equals("img_1.png") && b.get(15).ref.equals("rec_1.m4a") && b.get(15).label.startsWith("🎙"), "imagem e áudio");
        check(b.get(16).text().equals("5 * 3 = 15"), "asterisco solto fica como texto");
        check(NotepadDoc.media(body).equals(Arrays.asList("img_1.png", "rec_1.m4a")), "ficheiros da nota");
        // Formatting commands.
        check(NotepadText.heading("texto", 0, 0, 2).text.equals("## texto") && NotepadText.heading("## texto", 0, 0, 0).text.equals("texto"), "H2 e voltar a corpo");
        check(NotepadText.heading("- item", 0, 0, 1).text.equals("# item"), "título substitui a lista");
        check(NotepadText.indent("a\nb", 0, 3, 1).text.equals("  a\n  b") && NotepadText.indent("  a", 0, 0, -1).text.equals("a"), "recuo");
        check(NotepadText.align("  [c] x", 0, 0, 'r').text.equals("  [d] x") && NotepadText.align("[c] x", 0, 0, 'l').text.equals("x"), "alinhamento");
        check(NotepadText.prefix("a\nb\nc", 0, 5, "1. ").text.equals("1. a\n2. b\n3. c"), "lista numerada");
        check(NotepadText.prefix("1. a\n2. b", 0, 8, "1. ").text.equals("a\nb"), "tirar numeração");
        check(NotepadText.prefix("  [c] - x", 0, 0, "[ ] ").text.equals("  [c] [ ] x"), "tarefa mantém recuo e alinhamento");
        NotepadText.Edit c = NotepadText.color("graça e paz", 0, 5, "verde");
        check(c.text.equals("{cor:verde}graça{/cor} e paz"), "cor: " + c.text);
        check(NotepadText.color(c.text, c.start, c.end, "roxo").text.equals("{cor:roxo}graça{/cor} e paz"), "mudar a cor");
        check(NotepadText.color(c.text, c.start, c.end, null).text.equals("graça e paz"), "tirar a cor");
        check(NotepadText.table(2, 1).equals("| Coluna 1 | Coluna 2 |\n|---|---|\n|   |   |"), "tabela nova");
        check(NotepadDoc.parse(NotepadText.table(3, 2)).get(0).rows.size() == 3, "linhas vazias da tabela ficam");
        check(NotepadText.plain("[c] ## Olá {cor:azul}mundo{/cor} __x__ ![Foto](note-img:a.png)").equals("Olá mundo x Foto"), "texto simples sem marcas novas: " + NotepadText.plain("[c] ## Olá {cor:azul}mundo{/cor} __x__ ![Foto](note-img:a.png)"));
        check(NotepadText.markdown("", "[c] __u__ {cor:azul}a{/cor}").equals("<u>u</u> a\n"), "Markdown com sublinhado");
        // Word.
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        byte[] docx = NotepadExport.docx("Estudo & oração", body, ref -> ref.equals("img_1.png") ? new Object[]{png, 800, 400, "png"} : null);
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(docx))) { ZipEntry e; while ((e = z.getNextEntry()) != null) { ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n; while ((n = z.read(buf)) > 0) o.write(buf, 0, n); parts.put(e.getName(), e.getName().endsWith(".png") ? "png:" + o.size() : new String(o.toByteArray(), StandardCharsets.UTF_8)); } }
        for (String need : new String[]{"[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/numbering.xml", "word/_rels/document.xml.rels", "docProps/core.xml", "word/media/image1.png"}) check(parts.containsKey(need), "parte " + need);
        for (Map.Entry<String, String> e : parts.entrySet()) if (e.getKey().endsWith(".xml") || e.getKey().endsWith(".rels")) DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(e.getValue().getBytes(StandardCharsets.UTF_8)));
        checks++;
        String d = parts.get("word/document.xml");
        check(d.contains("Estudo &amp; oração") && d.contains("w:val=\"Heading1\"") && d.contains("w:val=\"Heading4\"") && d.contains("<w:numId w:val=\"2\"/>") && d.contains("<w:ilvl w:val=\"1\"/>"), "títulos e listas");
        check(d.contains("<w:u w:val=\"single\"/>") && d.contains("<w:strike/>") && d.contains("<w:highlight w:val=\"yellow\"/>") && d.contains("<w:color w:val=\"2F6FDE\"/>"), "estilos dos textos");
        check(d.contains("<w:tbl>") && d.contains("w:val=\"Quote\"") && d.contains("<w:jc w:val=\"center\"/>") && d.contains("☑ ") && d.contains("r:embed=\"rIdImg1\"") && d.contains("🎙"), "tabela, citação, centro, tarefas, imagem e áudio");
        check(d.contains("cx=\"5486400\" cy=\"2743200\""), "imagem com a proporção certa");
        System.out.println("NotepadDoc539Test OK · " + checks + " verificações");
    }
}
