package mz.bam.seeky.android;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** 5.40: Seeky IA in notes (prompts, what is sent, cleaning and applying answers) and line spacing in Word. */
public final class NotepadAi540Test {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) throws Exception {
        check(NotepadAiText.ACTIONS.length == 8, "8 acções");
        for (NotepadAiText.Action a : NotepadAiText.ACTIONS) { String p = NotepadAiText.prompt(a.key, "faz um esboço", false); check(p.contains("'## '") && p.contains("[ ]") && !p.trim().isEmpty(), "formato em " + a.key); }
        check(NotepadAiText.prompt("pedir", "transforma em sermão", false).contains("transforma em sermão"), "pedido livre");
        check(NotepadAiText.prompt("resumir", "", true).startsWith("Summarise"), "inglês");
        check(NotepadAiText.action("corrigir").replace && NotepadAiText.action("topicos").replace && !NotepadAiText.action("resumir").replace, "substituir só em corrigir e tópicos");
        String body = "Fé e {cor:azul}esperança{/cor}\n![Foto](note-img:img_1.png)\n[c] Centro\n[🎙 Áudio 0:12](note-audio:rec_1.m4a)\n- ponto";
        String src = NotepadAiText.source("Estudo", body);
        check(src.equals("# Estudo\n\nFé e esperança\nCentro\n- ponto"), "enviado sem imagens, áudio e cores: " + src);
        String answer = "```markdown\nAqui:\n* um\n+ dois\n- [ ] fazer isto\n##### fundo\n\n\n\nfim\n```";
        String clean = NotepadAiText.clean(answer);
        check(clean.equals("Aqui:\n- um\n- dois\n[ ] fazer isto\n#### fundo\n\nfim"), "limpo: " + clean);
        String added = NotepadAiText.apply(NotepadAiText.action("resumir"), "Texto\n\n", "Ideia principal.", false);
        check(added.equals("Texto\n\n## Resumo\nIdeia principal.\n"), "resumo no fim: " + added);
        check(NotepadAiText.apply(NotepadAiText.action("resumir"), "Text", "Main.", true).contains("## Summary"), "título em inglês");
        String replaced = NotepadAiText.apply(NotepadAiText.action("corrigir"), body, "Fé e esperança corrigida\n- ponto", false);
        check(replaced.contains("note-img:img_1.png") && replaced.contains("note-audio:rec_1.m4a") && replaced.startsWith("Fé e esperança corrigida"), "imagens e áudio ficam: " + replaced);
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 1500; i++) big.append("linha número ").append(i).append('\n');
        check(NotepadAiText.tooLong(big.toString()) && NotepadAiText.cut(big.toString()).length() <= NotepadAiText.LIMIT && NotepadAiText.cut(big.toString()).endsWith(""), "nota longa cortada");
        // Word: line spacing.
        byte[] normal = NotepadExport.docx("T", "Texto", null), wide = NotepadExport.docx("T", "Texto", null, 200);
        check(styles(normal).contains("w:line=\"288\"") && styles(wide).contains("w:line=\"480\""), "espaçamento no Word");
        System.out.println("PASS notepad AI and spacing (" + checks + " checks)");
    }
    static String styles(byte[] zip) throws IOException { try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) { ZipEntry e; while ((e = in.getNextEntry()) != null) if (e.getName().equals("word/styles.xml")) { ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) o.write(b, 0, n); return o.toString("UTF-8"); } } return ""; }
}
