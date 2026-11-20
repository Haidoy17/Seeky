package mz.bam.seeky.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.EditText;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dicionário e tradução para a palavra ou frase seleccionada no leitor.
 *
 * <p>Sem internet, usa os dicionários e tradutores instalados no telefone (por exemplo, o Google
 * Tradutor com o idioma descarregado funciona offline). Com internet, abre o Wikcionário. A
 * palavra pode ficar no Vocabulário do Seeky e virar um cartão de revisão.
 */
final class WordLookup {
    static final String PREFS = "vocabulary";

    private WordLookup() {}

    static String clean(String text) {
        String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("^[\\p{Punct}«»“”‘’…—–]+|[\\p{Punct}«»“”‘’…—–]+$", "");
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    static void show(Activity a, String selected) {
        String word = clean(selected);
        if (word.isEmpty()) { PlusUi.toast(a, "Seleccione uma palavra."); return; }
        UiPanel p = new UiPanel(a, word, AppLanguage.text(a, "Dicionário e tradução"), "translate");
        p.action("dictionary", "Definição", "Dicionários instalados no telefone (funcionam sem internet)", false, () -> process(a, word));
        p.action("translate", "Traduzir", "Google Tradutor ou outro tradutor instalado", false, () -> translate(a, word));
        p.action("globe", "Wikcionário", "Definição, origem e pronúncia (precisa de internet)", false, () -> wiktionary(a, word));
        p.action("bookmark", "Guardar no vocabulário", "Com o seu significado e um cartão de revisão", false, () -> askMeaning(a, word));
        p.note("Dica: no Google Tradutor, descarregue o idioma para traduzir sem internet.");
        p.show();
    }

    static void process(Activity a, String word) {
        Intent i = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").putExtra(Intent.EXTRA_PROCESS_TEXT, word).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        try { a.startActivity(Intent.createChooser(i, AppLanguage.text(a, "Procurar com"))); }
        catch (Exception e) { wiktionary(a, word); }
    }

    static void translate(Activity a, String word) {
        if (Build.VERSION.SDK_INT >= 29) {
            try { a.startActivity(new Intent(Intent.ACTION_TRANSLATE).putExtra(Intent.EXTRA_TEXT, word)); return; } catch (ActivityNotFoundException ignored) {} catch (Exception ignored) {}
        }
        Intent google = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage("com.google.android.apps.translate")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, word).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        try { a.startActivity(google); return; } catch (Exception ignored) {}
        String target = AppLanguage.english(a) ? "en" : "pt";
        browse(a, "https://translate.google.com/?sl=auto&tl=" + target + "&op=translate&text=" + Uri.encode(word));
    }

    static void wiktionary(Activity a, String word) {
        String host = AppLanguage.english(a) ? "en" : "pt";
        browse(a, "https://" + host + ".wiktionary.org/wiki/" + Uri.encode(word.toLowerCase(Locale.ROOT).replace(' ', '_')));
    }

    static void browse(Activity a, String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { PlusUi.toast(a, "Não há navegador disponível."); }
    }

    // ---- vocabulário ----

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static JSONArray words(Context c) {
        try { return new JSONArray(prefs(c).getString("words", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static void askMeaning(Activity a, String word) {
        EditText meaning = new EditText(a);
        meaning.setHint(AppLanguage.text(a, "Significado ou tradução"));
        meaning.setMinLines(2);
        android.widget.FrameLayout box = new android.widget.FrameLayout(a);
        int pad = PlusUi.dp(a, 20);
        box.setPadding(pad, PlusUi.dp(a, 6), pad, 0);
        box.addView(meaning);
        new UiDialog.Builder(a).setTitle(word).setView(box)
                .setPositiveButton("Guardar", (d, w) -> PlusUi.run(a, () -> {
                    int card = save(a, word, meaning.getText().toString().trim(), true);
                    PlusUi.post(a, () -> PlusUi.toast(a, card > 0 ? "Guardado no vocabulário e nos cartões de revisão." : "Guardado no vocabulário."));
                }))
                .setNegativeButton("Cancelar", null).show();
    }

    /** Guarda (ou actualiza) a palavra; com significado, cria também um cartão de revisão. Devolve 1 se criou o cartão. */
    static synchronized int save(Context c, String word, String meaning, boolean card) throws Exception {
        JSONArray list = words(c), out = new JSONArray();
        for (int i = 0; i < list.length(); i++) if (!word.equalsIgnoreCase(list.optJSONObject(i).optString("w"))) out.put(list.optJSONObject(i));
        out.put(new JSONObject().put("w", word).put("m", meaning).put("t", System.currentTimeMillis()));
        while (out.length() > 2000) out.remove(0);
        prefs(c).edit().putString("words", out.toString()).apply();
        if (!card || meaning.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        try (StudyStore db = new StudyStore(c)) {
            db.save("cards", 0, StudyStore.values("subject", 0L, "front", word, "back", meaning, "tags", "vocabulário", "due", now, "updated", now));
        }
        StudyEvents.changed();
        return 1;
    }

    static void vocabulary(MainActivity a) {
        JSONArray list = words(a);
        UiPanel p = new UiPanel(a, "Vocabulário", list.length() + " " + AppLanguage.text(a, "palavras guardadas"), "dictionary");
        if (list.length() == 0) p.note("Seleccione uma palavra no leitor e escolha \"Dicionário e tradução\" para começar o seu vocabulário.");
        for (int i = list.length() - 1; i >= 0 && i >= list.length() - 200; i--) {
            JSONObject item = list.optJSONObject(i);
            String w = item.optString("w");
            p.action("dictionary", w, item.optString("m").isEmpty() ? AppLanguage.text(a, "Sem significado") : item.optString("m"), false, () -> show(a, w));
        }
        p.show();
    }
}
