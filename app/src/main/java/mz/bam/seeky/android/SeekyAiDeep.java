package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/**
 * 5.51 · Seeky IA mais forte, igual no telemóvel e no computador:
 * perguntas sobre vários documentos (os trechos mais relevantes são escolhidos no aparelho),
 * resumo de um livro inteiro por etapas (resumos parciais juntos num resumo geral)
 * e plano de estudo a partir de um tema (sessões com data e cartões de revisão).
 */
final class SeekyAiDeep {
    private SeekyAiDeep() {}

    // ================= vários documentos =================
    static final int PASSAGE = 1400, BUDGET = 24000, MAX_DOCS = 12, MAX_PASSAGES = 6000;

    static final class Doc {
        final String name, text;
        Doc(String name, String text) { this.name = name == null || name.trim().isEmpty() ? "Documento" : name.trim(); this.text = text == null ? "" : text; }
    }
    static final class Passage {
        final int doc, number, start, end; final String text; double score;
        Passage(int doc, int number, int start, int end, String source) { this.doc = doc; this.number = number; this.start = start; this.end = end; text = source.substring(start, end).trim(); }
        String id() { return "[D" + (doc + 1) + "·T" + number + "]"; }
    }

    /** Paragraph-sized passages (about 1400 characters), cut at a paragraph, a sentence or a space. */
    static List<Passage> passages(int doc, String text) {
        List<Passage> out = new ArrayList<>(); int at = 0, n = text.length();
        while (at < n && out.size() < MAX_PASSAGES) {
            while (at < n && Character.isWhitespace(text.charAt(at))) at++;
            if (at >= n) break;
            int end = Math.min(n, at + PASSAGE);
            if (end < n) {
                int cut = text.lastIndexOf("\n\n", end); if (cut <= at + PASSAGE / 3) cut = Math.max(text.lastIndexOf(". ", end), text.lastIndexOf(".\n", end)) + 1;
                if (cut <= at + PASSAGE / 3) cut = text.lastIndexOf(' ', end);
                if (cut > at + PASSAGE / 3) end = cut;
                if (end < n && Character.isLowSurrogate(text.charAt(end))) end--;
            }
            Passage p = new Passage(doc, out.size() + 1, at, end, text);
            if (!p.text.isEmpty()) out.add(p);
            at = Math.max(at + 1, end);
        }
        return out;
    }

    static final Set<String> STOP = new HashSet<>(Arrays.asList(
        "que", "dos", "das", "uma", "umas", "uns", "para", "por", "com", "sem", "nao", "sim", "mais", "menos", "como", "qual", "quais", "quem", "onde", "quando",
        "porque", "porq", "isso", "isto", "esse", "essa", "este", "esta", "estes", "estas", "aquele", "aquela", "sao", "ser", "foi", "era", "tem", "ter", "seu", "sua",
        "seus", "suas", "nos", "nas", "aos", "pelo", "pela", "pelos", "pelas", "entre", "sobre", "ate", "tambem", "muito", "cada", "todo", "toda", "todos", "todas",
        "dizem", "diz", "dize", "documento", "documentos", "texto", "textos", "livro", "livros", "explica", "explique", "resume", "resuma", "resumo", "fala", "falam",
        "segundo", "algum", "alguma", "ele", "ela", "eles", "elas", "lhe", "meu", "minha", "the", "and", "for", "are", "was", "with", "what", "which", "who", "how",
        "why", "does", "this", "that", "from", "into", "about", "say", "says", "book", "books", "document", "documents", "explain", "summary", "summarise"));

    static String fold(String s) { return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT); }
    /** Words that carry meaning, folded (no accents) and cut to a stem so "graça" and "graças" meet. */
    static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        for (String w : fold(text).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() < 3 || STOP.contains(w)) continue;
            out.add(stem(w));
        }
        return out;
    }
    static String stem(String w) {
        if (w.length() > 4 && w.endsWith("s")) w = w.substring(0, w.length() - 1);
        return w.length() > 6 ? w.substring(0, 6) : w;
    }

    /** The chosen excerpts of several documents, labelled [D1·T4], under a character budget, with an honest header. */
    static String block(List<Doc> docs, String query, int budget) {
        if (docs.isEmpty()) throw new IllegalArgumentException("Escolha pelo menos um documento.");
        if (docs.size() > MAX_DOCS) throw new IllegalArgumentException("Escolha no máximo " + MAX_DOCS + " documentos.");
        List<List<Passage>> all = new ArrayList<>(); List<Passage> flat = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) { List<Passage> ps = passages(i, docs.get(i).text); all.add(ps); flat.addAll(ps); }
        Set<String> q = new LinkedHashSet<>(terms(query));
        Map<String, Integer> df = new HashMap<>(); List<Map<String, Integer>> tfs = new ArrayList<>();
        for (Passage p : flat) {
            Map<String, Integer> tf = new HashMap<>();
            for (String t : terms(p.text)) if (q.contains(t)) tf.merge(t, 1, Integer::sum);
            for (String t : tf.keySet()) df.merge(t, 1, Integer::sum);
            tfs.add(tf);
        }
        int total = Math.max(1, flat.size());
        for (int i = 0; i < flat.size(); i++) {
            double s = 0; Map<String, Integer> tf = tfs.get(i);
            for (Map.Entry<String, Integer> e : tf.entrySet()) s += (1 + Math.log(e.getValue())) * Math.log(1 + (double) total / df.get(e.getKey()));
            s += tf.size() > 1 ? tf.size() * 0.5 : 0; // more different words of the question in one passage
            flat.get(i).score = s;
        }
        Set<Passage> chosen = new LinkedHashSet<>(); int used = 0; boolean any = false;
        for (Passage p : flat) if (p.score > 0) { any = true; break; }
        if (any) {
            for (List<Passage> ps : all) { // the best passage of each document that answers at all
                Passage best = null; for (Passage p : ps) if (p.score > 0 && (best == null || p.score > best.score)) best = p;
                if (best != null && used + best.text.length() + 40 <= budget) { chosen.add(best); used += best.text.length() + 40; }
            }
            List<Passage> ranked = new ArrayList<>(); for (Passage p : flat) if (p.score > 0) ranked.add(p);
            ranked.sort((a, b) -> Double.compare(b.score, a.score));
            for (Passage p : ranked) { if (chosen.contains(p)) continue; if (used + p.text.length() + 40 > budget) continue; chosen.add(p); used += p.text.length() + 40; }
        } else { // a general request ("o que dizem estes documentos?"): an even sample of each document
            int share = budget / docs.size();
            for (List<Passage> ps : all) {
                if (ps.isEmpty()) continue; int fit = Math.max(1, share / (PASSAGE + 40)), step = Math.max(1, ps.size() / fit);
                for (int k = 0; k < ps.size() && fit > 0; k += step, fit--) { Passage p = ps.get(k); if (used + p.text.length() + 40 > budget) break; chosen.add(p); used += p.text.length() + 40; }
            }
        }
        List<Passage> order = new ArrayList<>(chosen); order.sort((a, b) -> a.doc != b.doc ? a.doc - b.doc : a.number - b.number);
        int[] per = new int[docs.size()]; for (Passage p : order) per[p.doc]++;
        StringBuilder b = new StringBuilder("DOCUMENTOS ESCOLHIDOS / SELECTED DOCUMENTS:\n");
        for (int i = 0; i < docs.size(); i++) {
            b.append("[D").append(i + 1).append("] ").append(label(docs.get(i).name)).append(" — ");
            b.append(all.get(i).isEmpty() ? "sem texto legível" : per[i] == 0 ? "nenhum trecho relevante para esta pergunta" : per[i] + (per[i] == 1 ? " trecho" : " trechos") + " de " + all.get(i).size()).append('\n');
        }
        b.append("<material>\n");
        for (Passage p : order) b.append(p.id()).append('\n').append(p.text).append("\n\n");
        b.append("</material>\n");
        b.append(any ? "The excerpts were chosen on the device by the words of the question; they are not the whole documents."
                     : "The question named no specific words, so an even sample of each document was sent; it is not the whole documents.");
        b.append(" After claims taken from the documents cite the supplied IDs such as [D1·T4]. Say which document each point comes from. If documents disagree, say so and name them. If the excerpts do not contain the answer, say it was not found in the chosen documents and suggest a more specific question. Never invent an ID.");
        return b.toString();
    }
    static String label(String name) { String n = name.replaceAll("[\\r\\n\\[\\]<>]+", " ").trim(); return n.length() > 120 ? n.substring(0, 120) + "…" : n; }
    /** The words used to choose excerpts: this question plus the previous one, so "e no segundo?" still finds the subject. */
    static String query(String question, List<SeekyAiCore.Message> history) {
        String q = question;
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).role.equals("user")) { q += "\n" + history.get(i).text; break; }
        return q;
    }
    static final Pattern CITE = Pattern.compile("\\[D([0-9]{1,2})·T([0-9]{1,4})\\]");
    /** The document numbers an answer cites (only ones that exist). */
    static Set<Integer> cited(String answer, int docs) { Set<Integer> out = new TreeSet<>(); Matcher m = CITE.matcher(answer); while (m.find()) { int d = Integer.parseInt(m.group(1)); if (d >= 1 && d <= docs) out.add(d); } return out; }

    // ================= livro inteiro =================
    static final int GROUP = 28000, MAX_PARTS = 400;
    interface Asker { String ask(String block, String question) throws Exception; }
    interface Progress { void step(int done, int total, String what); }

    /** The book cut into excerpts of the size one request takes. */
    static List<String> parts(String text) {
        List<String> out = new ArrayList<>();
        for (SeekyAiCore.Slice s : SeekyAiCore.slices(text)) { String p = text.substring(s.start, s.end); if (!p.trim().isEmpty()) out.add(p); }
        if (out.size() > MAX_PARTS) throw new IllegalArgumentException("Livro demasiado longo: escolha um intervalo de páginas ou capítulos.");
        return out;
    }
    static String partQuestion(boolean english) {
        return english ? "Summarise this excerpt as short bullet points: main ideas, key concepts, names and arguments. Do not mention other excerpts."
                       : "Resume este trecho em tópicos curtos: ideias principais, conceitos, nomes e argumentos importantes. Não fales de outros trechos.";
    }
    static String mergeQuestion(boolean english) {
        return english ? "Join these consecutive partial summaries into one summary, in order, keeping main ideas, concepts and names. Invent nothing."
                       : "Junta estes resumos parciais consecutivos num só resumo, por ordem, mantendo as ideias principais, os conceitos e os nomes. Não inventes nada.";
    }
    static String finalQuestion(boolean english, String title) {
        return english ? "Join these partial summaries, in order, into a general summary of «" + label(title) + "»: first an overview in 3 to 5 sentences, then the main ideas part by part, then concepts and names to remember, and finally 5 review questions. Invent nothing that is not in the summaries."
                       : "Junta estes resumos parciais, por ordem, num resumo geral de «" + label(title) + "»: primeiro uma visão geral em 3 a 5 frases, depois as ideias principais parte a parte, depois os conceitos e nomes a reter e, no fim, 5 perguntas de revisão. Não inventes nada que não esteja nos resumos.";
    }
    static String clean(String summary) { return summary.replaceAll("\\s*\\[(?:T|D[0-9]{1,2}·T)[0-9]{1,4}\\]", "").trim(); }
    /** Consecutive summaries grouped so that each group fits one request. */
    static List<List<Integer>> groups(List<String> items, int limit) {
        List<List<Integer>> out = new ArrayList<>(); List<Integer> cur = new ArrayList<>(); int size = 0;
        for (int i = 0; i < items.size(); i++) {
            int len = Math.min(limit, clean(items.get(i)).length()) + 16;
            if (!cur.isEmpty() && size + len > limit) { out.add(cur); cur = new ArrayList<>(); size = 0; }
            cur.add(i); size += len;
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
    static String summaries(String title, List<String> items, List<Integer> group, int limit) {
        StringBuilder b = new StringBuilder("RESUMOS PARCIAIS / PARTIAL SUMMARIES OF: ").append(label(title)).append("\n<material>\n");
        int share = Math.max(400, limit / Math.max(1, group.size()) - 16);
        for (int i : group) { String s = clean(items.get(i)); if (s.length() > share) s = s.substring(0, share) + "…"; b.append("[R").append(i + 1).append("]\n").append(s).append("\n\n"); }
        return b.append("</material>\nThese are summaries of consecutive parts of the same book, in reading order.").toString();
    }
    /** One step of joining: the next, shorter list. When the input fits one request the result is the general summary. */
    static List<String> step(List<String> items, String title, boolean english, Asker asker) throws Exception {
        List<List<Integer>> gs = groups(items, GROUP); boolean last = gs.size() == 1; List<String> out = new ArrayList<>();
        for (List<Integer> g : gs) out.add(asker.ask(summaries(title, items, g, GROUP), last ? finalQuestion(english, title) : mergeQuestion(english)));
        return out;
    }
    /** Requests still needed to join these summaries into the general one (an estimate for merged sizes). */
    static int cost(List<String> items) {
        if (items.size() <= 1) return items.size();
        List<String> cur = items; int total = 0;
        while (true) { int g = groups(cur, GROUP).size(); total += g; if (g == 1) return total; List<String> next = new ArrayList<>(); for (int i = 0; i < g; i++) next.add(repeat(5000)); cur = next; }
    }
    static String repeat(int n) { char[] c = new char[n]; Arrays.fill(c, 'x'); return new String(c); }
    /** The whole book: each excerpt summarised, then joined (in as many steps as needed) into a general summary. */
    static String book(String title, String text, boolean english, Asker part, Asker join, Progress progress) throws Exception {
        List<String> ps = parts(text); if (ps.isEmpty()) throw new IllegalArgumentException("Este livro não tem texto legível.");
        List<String> sums = new ArrayList<>(); int total = ps.size() + (ps.size() > 1 ? cost(Collections.nCopies(ps.size(), repeat(3000))) : 0), done = 0;
        for (String p : ps) { progress.step(done, total, "A resumir a parte " + (sums.size() + 1) + " de " + ps.size()); sums.add(part.ask(p, partQuestion(english))); done++; }
        if (sums.size() == 1) return clean(sums.get(0));
        List<String> cur = sums;
        while (true) {
            boolean last = groups(cur, GROUP).size() == 1;
            progress.step(done, total, last ? "A escrever o resumo geral" : "A juntar os resumos");
            cur = step(cur, title, english, join); done += 1;
            if (last) return clean(cur.get(0));
        }
    }

    // ================= plano de estudo =================
    static final class Session { final int day; final String title, detail; Session(int day, String title, String detail) { this.day = day; this.title = title; this.detail = detail; } }
    static final class Plan {
        String title = "", goal = ""; final List<Session> sessions = new ArrayList<>(); final List<StudyCards.Card> cards = new ArrayList<>();
        /** The plan as a note (title line, goal, sessions with dates). */
        String note(long start, int perWeek) {
            StringBuilder b = new StringBuilder();
            if (!goal.isEmpty()) b.append("Objectivo: ").append(goal).append("\n\n");
            b.append("Sessões\n");
            java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("EEE dd/MM", new Locale("pt", "PT"));
            for (Session s : sessions) b.append("• ").append(date.format(new Date(due(start, s.day, perWeek)))).append(" — ").append(s.title).append(s.detail.isEmpty() ? "" : ": " + s.detail).append('\n');
            if (!cards.isEmpty()) b.append("\n").append(cards.size()).append(" cartões de revisão nos Estudos.");
            return b.toString().trim();
        }
    }
    static final String[] LEVELS = {"Iniciante", "Intermédio", "Avançado"};
    static String planQuestion(String theme, int weeks, int perWeek, int minutes, int level, boolean english) {
        String t = theme == null ? "" : theme.replaceAll("[\\r\\n]+", " ").trim();
        if (t.length() < 3) throw new IllegalArgumentException("Escreva o tema do plano.");
        if (t.length() > 300) throw new IllegalArgumentException("Tema demasiado longo (até 300 caracteres).");
        int n = sessions(weeks, perWeek);
        String lv = LEVELS[Math.max(0, Math.min(2, level))];
        if (english) return "Create a study plan about: " + t + ". Level: " + lv + ". " + n + " study sessions of about " + minutes + " minutes (" + perWeek + " per week for " + weeks + " weeks), from basics to review, ending with a revision session.\n"
            + "Answer exactly in this format and nothing else:\nTITLE: (short subject name)\nGOAL: (one sentence)\nthen one line per session:\nDAY n | session title | what to do (one sentence)\nthen the line CARDS and eight flashcards:\nQuestion: ...\nAnswer: ...";
        return "Cria um plano de estudo sobre: " + t + ". Nível: " + lv + ". " + n + " sessões de cerca de " + minutes + " minutos (" + perWeek + " por semana durante " + weeks + (weeks == 1 ? " semana" : " semanas") + "), do essencial à revisão, terminando com uma sessão de revisão.\n"
            + "Responde exactamente neste formato e mais nada:\nTÍTULO: (nome curto da disciplina)\nOBJECTIVO: (uma frase)\ndepois uma linha por sessão:\nDIA n | título da sessão | o que fazer (uma frase)\ndepois a linha CARTÕES e oito cartões:\nPergunta: ...\nResposta: ...";
    }
    static int sessions(int weeks, int perWeek) {
        if (weeks < 1 || weeks > 12) throw new IllegalArgumentException("Escolha de 1 a 12 semanas.");
        if (perWeek < 1 || perWeek > 7) throw new IllegalArgumentException("Escolha de 1 a 7 sessões por semana.");
        return Math.min(60, weeks * perWeek);
    }
    static final Pattern TITLE = Pattern.compile("(?iu)^(?:t[ií]tulo|title)\\s*:\\s*(.+)$"), GOAL = Pattern.compile("(?iu)^(?:objectivo|objetivo|goal)\\s*:\\s*(.+)$"),
        DAY = Pattern.compile("(?iu)^(?:[-•*]\\s*)?(?:dia|day|sess[ãa]o|session)\\s*([0-9]{1,3})\\s*[|:–—-]\\s*(.+)$"), CARDS = Pattern.compile("(?iu)^(?:cart[õo]es|cards|flashcards)\\s*:?$");
    static Plan parse(String answer, int max) {
        Plan p = new Plan(); String[] lines = answer.split("\\r?\\n"); int cardsAt = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim().replace("**", "").replace("__", "");
            Matcher m;
            if (CARDS.matcher(line).matches()) { cardsAt = i + 1; break; }
            if (p.title.isEmpty() && (m = TITLE.matcher(line)).matches()) p.title = cut(m.group(1), 80);
            else if (p.goal.isEmpty() && (m = GOAL.matcher(line)).matches()) p.goal = cut(m.group(1), 400);
            else if ((m = DAY.matcher(line)).matches() && p.sessions.size() < max) {
                int day = Integer.parseInt(m.group(1)); String[] f = m.group(2).split("\\s*\\|\\s*", 2);
                String title = cut(f[0], 200), detail = f.length > 1 ? cut(f[1], 400) : "";
                if (day >= 1 && day <= max && !title.isEmpty()) p.sessions.add(new Session(day, title, detail));
            }
        }
        p.sessions.sort((a, b) -> a.day - b.day);
        if (cardsAt >= 0) { StringBuilder rest = new StringBuilder(); for (int i = cardsAt; i < lines.length; i++) rest.append(lines[i]).append('\n'); p.cards.addAll(StudyCards.parse(rest.toString())); }
        else p.cards.addAll(StudyCards.parse(answer.replaceAll("(?ims)^.*?(?=^\\s*(?:pergunta|question)\\s*:)", "")));
        return p;
    }
    static String cut(String s, int max) { s = s.trim(); return s.length() > max ? s.substring(0, max).trim() : s; }
    /** The date of session n: spread over the week (n per week), starting on the start day, at noon. */
    static long due(long start, int session, int perWeek) {
        int i = session - 1, week = i / perWeek, inWeek = i % perWeek, offset = week * 7 + (int) Math.round(inWeek * 7.0 / perWeek);
        java.util.Calendar c = java.util.Calendar.getInstance(); c.setTimeInMillis(start);
        c.set(java.util.Calendar.HOUR_OF_DAY, 12); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
        c.add(java.util.Calendar.DAY_OF_YEAR, offset); return c.getTimeInMillis();
    }
}
