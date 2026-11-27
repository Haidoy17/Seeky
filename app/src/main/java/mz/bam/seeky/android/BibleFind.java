package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import java.util.*;

/**
 * 5.60 · Encontrar a passagem a partir de um trecho ou de palavras simples (Flash, Consultar passagem, Estudar com
 * Seeky): the words are looked up in the chosen Bible (BibleFinder); when they are not clear enough Seeky IA says
 * which passage it is. The passage opens in the quick passage window, whose "Capítulo inteiro" shows the whole
 * chapter with the verse marked and brought into view.
 */
final class BibleFind {
    private BibleFind() {}
    /** The Bible used by the passage window (the last one chosen, or the first). */
    static long source(Context c) {
        long chosen = c.getSharedPreferences("seeky", 0).getLong("bible_quick_source", -1);
        try (PlusStore db = new PlusStore(c); Cursor q = db.getReadableDatabase().rawQuery("SELECT id FROM bible_sources ORDER BY CASE WHEN id=? THEN 0 ELSE 1 END, title COLLATE NOCASE LIMIT 1", new String[]{"" + chosen})) {
            return q.moveToFirst() ? q.getLong(0) : -1;
        }
    }
    static BibleFinder finder(Context c) throws Exception {
        long s = source(c); if (s <= 0) return null;
        Context app = c.getApplicationContext();
        long verses; try (PlusStore db = new PlusStore(app); Cursor q = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM bible_verses WHERE source=?", new String[]{"" + s})) { verses = q.moveToFirst() ? q.getLong(0) : 0; }
        return BibleFinder.of("android:" + s + ":" + verses, sink -> {
            try (PlusStore db = new PlusStore(app); Cursor q = db.getReadableDatabase().rawQuery("SELECT book,chapter,verse,value FROM bible_verses WHERE source=?", new String[]{"" + s})) {
                while (q.moveToNext()) sink.verse(q.getString(0), q.getInt(1), q.getInt(2), q.getString(3));
            }
        });
    }
    static List<BibleFinder.Hit> local(Context c, String words, int limit) { try { BibleFinder f = finder(c); return f == null ? Collections.emptyList() : f.find(words, limit); } catch (Exception e) { return Collections.emptyList(); } }
    static boolean aiReady(Context c) { try { return !new SeekyAiVault(c).get(SeekyAiCore.GEMINI).isEmpty(); } catch (Exception e) { return false; } }
    /** Seeky IA: which passages the words are (off the main thread). */
    static List<BibleReferences.Reference> ai(Context c, String words) throws Exception {
        String key = new SeekyAiVault(c).get(SeekyAiCore.GEMINI); if (key.isEmpty()) throw new java.io.IOException("Configure a chave da IA em Estudar com Seeky.");
        boolean english = AppLanguage.english(c);
        String answer = new SeekyAiClient().ask(SeekyAiCore.GEMINI, key, "", "Encontrar passagem", Collections.emptyList(), BibleFinder.aiPrompt(words, english), english, new SeekyAiClient.Call()).text;
        return BibleFinder.aiAnswer(answer);
    }

    /** Finds the passage for these words and shows it; {@code closed} runs when nothing is left on screen. */
    static void find(Activity a, String raw, boolean askAi, Runnable closed) {
        String words = raw == null ? "" : raw.trim();
        BibleReferences.Reference typed = BibleReferences.lookup(words);
        if (typed != null) { open(a, typed, closed); return; }
        if (words.length() < 3) { if (closed != null) closed.run(); return; }
        PlusUi.toast(a, askAi ? "A Seeky IA está a procurar a passagem…" : "A procurar a passagem…");
        PlusUi.run(a, () -> {
            if (source(a) <= 0) { PlusUi.post(a, () -> { PlusUi.toast(a, "Descarregue ou importe primeiro uma Bíblia (Ferramentas › Bíblias)."); if (closed != null) closed.run(); }); return; }
            List<BibleFinder.Hit> hits = local(a, words, 3); List<BibleReferences.Reference> refs = new ArrayList<>(); Set<String> seen = new HashSet<>();
            boolean sure = BibleFinder.confident(hits) && hits.get(0).score >= 0.8;
            if (sure && !askAi) { BibleReferences.Reference r = hits.get(0).reference(); PlusUi.post(a, () -> open(a, r, closed)); return; }
            if (aiReady(a) && (askAi || !sure)) { try { for (BibleReferences.Reference r : ai(a, words)) if (seen.add(r.title)) refs.add(r); } catch (Exception e) { PlusUi.post(a, () -> PlusUi.toast(a, "A Seeky IA não respondeu: " + e.getMessage())); } }
            for (BibleFinder.Hit h : hits) { BibleReferences.Reference r = h.reference(); if (r != null && seen.add(r.title)) refs.add(r); }
            PlusUi.post(a, () -> {
                String n = SeekyAiModels.takeNotice(); if (!n.isEmpty()) PlusUi.toast(a, n);
                if (refs.isEmpty()) { PlusUi.toast(a, "Não encontrei esta passagem. Escreva mais palavras do versículo" + (aiReady(a) ? "." : " ou ligue a Seeky IA.")); if (closed != null) closed.run(); return; }
                if (refs.size() == 1) { open(a, refs.get(0), closed); return; }
                List<String> names = new ArrayList<>(); for (BibleReferences.Reference r : refs) names.add(r.title);
                boolean[] picked = {false};
                new UiDialog.Builder(a).setTitle("Que passagem é?").setItems(names.toArray(new String[0]), (d, i) -> { picked[0] = true; open(a, refs.get(i), closed); })
                    .setNegativeButton("Fechar", null).setOnDismissListener(d -> { if (!picked[0] && closed != null) closed.run(); }).show();
            });
        });
    }
    static void open(Activity a, BibleReferences.Reference r, Runnable closed) {
        BibleLinks.Passage p = new BibleLinks.Passage(a, r, -1); p.onClosed = closed; p.load();
    }
    /** Asks for the words (Estudar com Seeky › Encontrar passagem). */
    static void ask(Activity a) {
        android.widget.EditText input = PlusUi.input(a, "Ex.: da sua plenitude nos tem dado graça sobre graça", "", 300);
        new UiDialog.Builder(a).setTitle("Encontrar passagem").setMessage("Escreva o que se lembra do versículo, mesmo por palavras suas. O texto mostrado é o da sua Bíblia.").setView(input)
            .setPositiveButton("Encontrar", (d, w) -> find(a, input.getText().toString(), false, null))
            .setNeutralButton("Perguntar à Seeky IA", (d, w) -> find(a, input.getText().toString(), true, null))
            .setNegativeButton("Cancelar", null).show();
    }
}
