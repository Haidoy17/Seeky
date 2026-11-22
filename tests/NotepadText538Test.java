package mz.bam.seeky.android;

import java.util.*;

/** 5.38: notebook text rules — titles, marks, tasks, editing at the cursor, passages and export. */
public final class NotepadText538Test {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        check(NotepadText.title("", "\n# Estudo de Romanos 8\ntexto").equals("Estudo de Romanos 8"), "título da primeira linha sem marcas");
        check(NotepadText.title("  Sermão  ", "x").equals("Sermão") && NotepadText.title("", "").equals("Nota sem título"), "título escrito ou vazio");
        String body = "# Plano\n[ ] Ler João 3\n[x] Orar\n- **forte** e *suave* e ==marcado==";
        check(Arrays.equals(NotepadText.tasks(body), new int[]{1, 2}), "tarefas feitas/total");
        check(NotepadText.plain(body).equals("Plano\n☐ Ler João 3\n☑ Orar\n• forte e suave e marcado"), "texto simples: " + NotepadText.plain(body));
        check(NotepadText.words(body) == 10, "palavras: " + NotepadText.words(body));
        check(NotepadText.minutes(body) == 1, "minutos de leitura");
        String toggled = NotepadText.toggle(body, 1);
        check(toggled.contains("[x] Ler João 3") && Arrays.equals(NotepadText.tasks(toggled), new int[]{2, 2}), "marcar tarefa");
        check(NotepadText.toggle(toggled, 2).contains("[ ] Orar") && NotepadText.toggle(body, 0).equals(body), "desmarcar; linha sem tarefa fica igual");
        NotepadText.Edit e = NotepadText.wrap("graça e paz", 0, 5, "**");
        check(e.text.equals("**graça** e paz") && e.start == 2 && e.end == 7, "negrito à volta da selecção");
        NotepadText.Edit back = NotepadText.wrap(e.text, e.start, e.end, "**");
        check(back.text.equals("graça e paz"), "segundo toque tira o negrito");
        NotepadText.Edit empty = NotepadText.wrap("abc", 3, 3, "==");
        check(empty.text.equals("abc====") && empty.start == 5, "sem selecção: cursor entre as marcas");
        NotepadText.Edit list = NotepadText.prefix("um\ndois\ntrês", 0, 6, "- ");
        check(list.text.equals("- um\n- dois\ntrês"), "lista nas linhas seleccionadas: " + list.text);
        check(NotepadText.prefix(list.text, 0, 8, "- ").text.equals("um\ndois\ntrês"), "tirar a lista");
        check(NotepadText.prefix("# Velho", 0, 0, "[ ] ").text.equals("[ ] Velho"), "trocar título por tarefa");
        NotepadText.Edit ins = NotepadText.insert("antesdepois", 5, "> João 3:16", true);
        check(ins.text.equals("antes\n> João 3:16\ndepois") && ins.start == 18, "passagem numa linha própria: " + ins.text);
        check(NotepadText.passage("João 3:16", "Porque Deus amou  o mundo", "Almeida").equals("> João 3:16 (Almeida)\n> «Porque Deus amou o mundo»"), "bloco da passagem");
        check(NotepadText.markdown("T", "[ ] a\n[x] b\n==c==").equals("# T\n\n- [ ] a\n- [x] b\n**c**\n"), "exportar Markdown: " + NotepadText.markdown("T", "[ ] a\n[x] b\n==c=="));
        check(NotepadText.tags("#fé, oração; fé\n família").equals(Arrays.asList("fé", "oração", "família")), "etiquetas");
        check(NotepadText.TEMPLATES.length == 5 && NotepadText.TEMPLATES[0][1].contains("Observação"), "modelos (estudo OIA, sermão…)");
        List<BibleReferences.Reference> refs = BibleReferences.references("Ler Rm 8.28 e Salmo 23 hoje; depois Isaías 40:31.");
        check(refs.size() == 3 && refs.get(0).title.startsWith("Romanos 8"), "passagens reconhecidas numa nota: " + refs.size());
        check(NotepadText.lineAt("a\nb\nc", 3) == 1 && NotepadText.lineStart("a\nbc", 3) == 2, "linhas e cursor");
        System.out.println("NotepadText538Test OK · " + checks + " verificações");
    }
}
