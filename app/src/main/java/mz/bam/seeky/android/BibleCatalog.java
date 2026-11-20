package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Bíblias para descarregar: o catálogo do eBible.org (mais de 1500 traduções em mais de 1200
 * línguas, todas com autorização para descarga gratuita). O ZIP USFM de cada versão é juntado
 * num só ficheiro limpo (sem notas, referências cruzadas nem atributos de Strong) e entra pela
 * importação de Bíblias já existente, que é atómica e testada.
 *
 * <p>Versões com direitos de autor que não estão no catálogo (ACF, ARA, NVI, NTLH…) não podem
 * ser distribuídas pelo Seeky; importam-se a partir do ficheiro MyBible, USX ou USFM do utilizador.
 */
final class BibleCatalog {
    static final String CATALOG = "https://ebible.org/Scriptures/translations.csv";
    static final String DOWNLOAD = "https://ebible.org/Scriptures/";
    static final long MAX_ZIP = 80L * 1024 * 1024, MAX_BOOK = 16L * 1024 * 1024, CACHE_AGE = 7L * 86400000L;
    static final ExecutorService net = Executors.newSingleThreadExecutor();
    static List<Version> cached;

    private BibleCatalog() {}

    static final class Version {
        String language, id, languageName, languageEnglish, title, shortTitle, description, copyright, updated;
        boolean redistributable;
        int ot, nt, dc;

        String scope(Context c) {
            if (ot >= 39 && nt >= 27) return AppLanguage.text(c, dc > 0 ? "Bíblia completa com deuterocanónicos" : "Bíblia completa");
            if (nt >= 27 && ot == 0) return AppLanguage.text(c, "Novo Testamento");
            if (ot > 0 && nt == 0) return AppLanguage.text(c, "Antigo Testamento (parcial)");
            return (ot + nt + dc) + " " + AppLanguage.text(c, "livros");
        }

        String license(Context c) {
            String cp = copyright == null ? "" : copyright.toLowerCase(Locale.ROOT);
            if (cp.contains("public domain") || cp.contains("domínio público")) return AppLanguage.text(c, "Domínio público");
            return AppLanguage.text(c, redistributable ? "Licença livre" : "Uso pessoal");
        }

        String name() { return title == null || title.isEmpty() ? (shortTitle == null ? id : shortTitle) : title; }
    }

    // ---- catálogo (CSV) ----

    /** CSV RFC 4180: campos entre aspas, "" dentro das aspas e quebras de linha no campo. */
    static List<List<String>> csv(Reader in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, any = false;
        int c;
        while ((c = in.read()) != -1) {
            char ch = (char) c;
            any = true;
            if (quoted) {
                if (ch == '"') {
                    in.mark(1);
                    int next = in.read();
                    if (next == '"') field.append('"');
                    else { quoted = false; if (next != -1) in.reset(); }
                } else field.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == ',') { row.add(field.toString()); field.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') { in.mark(1); if (in.read() != '\n') in.reset(); }
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
                any = false;
            } else field.append(ch);
        }
        if (any || field.length() > 0) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    static List<Version> parse(Reader reader) throws IOException {
        List<List<String>> rows = csv(new BufferedReader(reader));
        List<Version> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) col.put(rows.get(0).get(i).replace("﻿", "").trim(), i);
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (!"True".equalsIgnoreCase(get(row, col, "downloadable"))) continue;
            Version v = new Version();
            v.language = get(row, col, "languageCode");
            v.id = get(row, col, "translationId");
            if (!v.id.matches("[A-Za-z0-9_-]{2,40}")) continue;
            v.languageName = get(row, col, "languageName");
            v.languageEnglish = get(row, col, "languageNameInEnglish");
            v.title = get(row, col, "title");
            v.shortTitle = get(row, col, "shortTitle");
            v.description = get(row, col, "description");
            v.copyright = get(row, col, "Copyright");
            v.updated = get(row, col, "UpdateDate");
            v.redistributable = "True".equalsIgnoreCase(get(row, col, "Redistributable"));
            v.ot = number(get(row, col, "OTbooks"));
            v.nt = number(get(row, col, "NTbooks"));
            v.dc = number(get(row, col, "DCbooks"));
            out.add(v);
        }
        return out;
    }

    static String get(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return i == null || i >= row.size() ? "" : row.get(i).trim();
    }

    static int number(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }

    static File cacheFile(Context c) { return new File(c.getFilesDir(), "bible-catalog.csv"); }

    static List<Version> load(Context c) throws Exception {
        if (cached != null) return cached;
        File f = cacheFile(c);
        if (!f.isFile() || System.currentTimeMillis() - f.lastModified() > CACHE_AGE) {
            try {
                HttpURLConnection conn = OpenLibraries.open(CATALOG);
                File part = new File(c.getFilesDir(), "bible-catalog.part");
                try (InputStream in = new OpenLibraries.Limited(conn.getInputStream(), 8L * 1024 * 1024); OutputStream out = new FileOutputStream(part)) { PdfTools.copy(in, out); }
                finally { conn.disconnect(); }
                if (part.length() < 1000 || !part.renameTo(f)) part.delete();
            } catch (Exception e) { if (!f.isFile()) throw e; }
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) { cached = parse(r); }
        return cached;
    }

    static List<Version> language(List<Version> all, String code) {
        List<Version> out = new ArrayList<>();
        for (Version v : all) if (v.language.equals(code)) out.add(v);
        out.sort((x, y) -> Integer.compare(y.ot + y.nt + y.dc, x.ot + x.nt + x.dc) != 0 ? Integer.compare(y.ot + y.nt + y.dc, x.ot + x.nt + x.dc) : x.name().compareToIgnoreCase(y.name()));
        return out;
    }

    static boolean matches(Version v, String q) {
        if (q.isEmpty()) return true;
        String all = (v.title + " " + v.shortTitle + " " + v.languageName + " " + v.languageEnglish + " " + v.id + " " + v.language).toLowerCase(Locale.ROOT);
        for (String word : q.toLowerCase(Locale.ROOT).trim().split("\\s+")) if (!all.contains(word)) return false;
        return true;
    }

    // ---- USFM ----

    /** Limpa uma linha USFM para a importação: tira notas, referências cruzadas e atributos de palavras. */
    static String clean(String usfm) {
        String t = usfm.replaceAll("(?s)\\\\f[e]? .*?\\\\f[e]?\\*", "")
                .replaceAll("(?s)\\\\x .*?\\\\x\\*", "")
                .replaceAll("(?s)\\\\rq .*?\\\\rq\\*", "")
                .replaceAll("\\|[^\\\\|]*?(\\\\\\+?w\\*)", "$1");
        return t.replaceAll("(?m)(?<!^)[ \\t]*(\\\\[cv] [0-9])", "\n$1");
    }

    /** Junta os livros USFM do ZIP (ordem do ficheiro, só livros reconhecidos); devolve o número de livros. */
    static int combine(InputStream zip, Writer out) throws IOException {
        TreeMap<String, String> books = new TreeMap<>();
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(zip)) {
            ZipEntry e;
            int entries = 0;
            while ((e = z.getNextEntry()) != null) {
                if (++entries > 5000) throw new IOException("ZIP com demasiadas entradas.");
                String name = e.getName();
                if (e.isDirectory() || !name.toLowerCase(Locale.ROOT).endsWith(".usfm") || name.contains("..")) continue;
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buffer = new byte[65536];
                for (int n; (n = z.read(buffer)) != -1; ) {
                    b.write(buffer, 0, n);
                    if (b.size() > MAX_BOOK) throw new IOException("Livro acima de 16 MB.");
                }
                total += b.size();
                if (total > 4 * MAX_ZIP) throw new IOException("Bíblia demasiado grande.");
                String text = b.toString("UTF-8");
                if (text.startsWith("﻿")) text = text.substring(1);
                String id = "";
                for (String line : text.split("\n", 30)) if (line.startsWith("\\id ")) { id = line.substring(4).trim().split("\\s+")[0]; break; }
                if (id.isEmpty() || BibleText.code(id).isEmpty()) continue;
                books.put(name.substring(name.lastIndexOf('/') + 1), clean(text));
            }
        }
        for (String text : books.values()) { out.write(text); if (!text.endsWith("\n")) out.write('\n'); }
        return books.size();
    }

    static File downloads(Context c) {
        File dir = new File(c.getFilesDir(), "bible-downloads");
        dir.mkdirs();
        return dir;
    }

    static Uri uri(Context c, String file) { return Uri.parse("content://" + c.getPackageName() + ".scan/bible/" + file); }

    // ---- interface ----

    static void show(Activity a) {
        PlusUi.toast(a, "A carregar o catálogo de Bíblias…");
        net.execute(() -> {
            try {
                List<Version> all = load(a);
                a.runOnUiThread(() -> panel(a, all));
            } catch (Exception e) {
                a.runOnUiThread(() -> new UiDialog.Builder(a).setTitle("Catálogo indisponível")
                        .setMessage(AppLanguage.text(a, "Ligue-se à internet para ver as Bíblias disponíveis. As Bíblias já importadas continuam disponíveis sem internet.") + "\n\n" + e.getMessage())
                        .setPositiveButton("Fechar", null).setNeutralButton("Importar ficheiro", (d, w) -> imported(a)).show());
            }
        });
    }

    static void panel(Activity a, List<Version> all) {
        Set<String> languages = new HashSet<>();
        for (Version v : all) languages.add(v.language);
        UiPanel p = new UiPanel(a, "Bíblias", all.size() + " " + AppLanguage.text(a, "versões em") + " " + languages.size() + " " + AppLanguage.text(a, "línguas · eBible.org"), "book");
        p.quick(new String[]{"search", "globe", "import"}, new String[]{"Pesquisar", "Línguas", "Importar"},
                new Runnable[]{() -> search(a, all, ""), () -> languagePicker(a, all), () -> imported(a)});
        p.section("Português");
        for (Version v : language(all, "por")) p.content.addView(card(a, p, v));
        p.section("Mais procuradas");
        String[][] quick = {{"eng", "English"}, {"spa", "Español"}, {"fra", "Français"}, {"swh", "Kiswahili"}, {"ita", "Italiano"}, {"deu", "Deutsch"}};
        for (String[] q : quick) {
            int n = language(all, q[0]).size();
            if (n > 0) p.action("globe", q[1], UiRows.count(a, n, "versão", "versões"), false, () -> languagePanel(a, all, q[0], q[1]));
        }
        p.action("globe", "Todas as línguas", languages.size() + " " + AppLanguage.text(a, "línguas, incluindo línguas de Moçambique"), false, () -> languagePicker(a, all));
        p.section("As suas Bíblias");
        p.action("book", "Bíblias importadas e ficheiros", "MyBible (.SQLite3), USX, USFM, ZIP ou TXT", false, () -> imported(a));
        p.note("Versões com direitos de autor que não estão no catálogo (ACF, ARA, NVI, NTLH, NAA…) não podem ser distribuídas pelo Seeky: importe o ficheiro MyBible ou USFM que tiver.");
        p.show();
    }

    static android.view.View card(Activity a, UiPanel p, Version v) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(p.dp(14), p.dp(12), p.dp(14), p.dp(12));
        GradientDrawable bg = p.shape(p.surface, 16);
        bg.setStroke(p.dp(1), AppPalette.of(a).line);
        box.setBackground(bg);
        TextView title = p.text("", 15, p.ink);
        title.setText(v.name());
        title.setTypeface(ThemeStyle.BOLD);
        box.addView(title);
        TextView detail = p.text("", 12, p.muted);
        detail.setText(v.scope(a) + " · " + v.license(a) + (v.updated.length() >= 4 ? " · " + v.updated.substring(0, 4) : ""));
        detail.setPadding(0, p.dp(3), 0, 0);
        box.addView(detail);
        LinearLayout tags = new LinearLayout(a);
        tags.setPadding(0, p.dp(8), 0, 0);
        tags.addView(tag(a, p, v.languageName.isEmpty() ? v.language : v.languageName));
        if (!v.shortTitle.isEmpty() && !v.shortTitle.equals(v.title)) tags.addView(tag(a, p, v.shortTitle.length() > 28 ? v.shortTitle.substring(0, 27) + "…" : v.shortTitle));
        box.addView(tags);
        box.setOnClickListener(x -> { p.dialog.dismiss(); confirm(a, v); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = p.dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    static TextView tag(Activity a, UiPanel p, String text) {
        TextView t = p.text("", 11, p.accent);
        t.setText(text);
        t.setPadding(p.dp(8), p.dp(3), p.dp(8), p.dp(3));
        t.setBackground(p.shape(AppPalette.of(a).tint, 10));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = p.dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    static void languagePanel(Activity a, List<Version> all, String code, String name) {
        UiPanel p = new UiPanel(a, name, AppLanguage.text(a, "Escolha uma versão"), "globe");
        List<Version> list = language(all, code);
        for (int i = 0; i < Math.min(80, list.size()); i++) p.content.addView(card(a, p, list.get(i)));
        p.show();
    }

    static void languagePicker(Activity a, List<Version> all) {
        Map<String, String> names = new TreeMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Version v : all) {
            names.put(v.language, v.languageName.isEmpty() ? v.languageEnglish : v.languageName + (v.languageEnglish.isEmpty() || v.languageEnglish.equals(v.languageName) ? "" : " · " + v.languageEnglish));
            counts.merge(v.language, 1, Integer::sum);
        }
        List<String> codes = new ArrayList<>(names.keySet());
        codes.sort((x, y) -> names.get(x).compareToIgnoreCase(names.get(y)));
        PagedPicker.show(a, AppLanguage.text(a, "Língua"), (q, offset, limit) -> {
            List<PagedPicker.Entry> rows = new ArrayList<>();
            int total = 0;
            for (String code : codes) {
                String label = names.get(code);
                if (!q.isEmpty() && !(label + " " + code).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) continue;
                if (total >= offset && rows.size() < limit) rows.add(new PagedPicker.Entry(code, label, UiRows.count(a, counts.get(code), "versão", "versões")));
                total++;
            }
            return new PagedPicker.Page(rows, total);
        }, e -> languagePanel(a, all, e.id, e.label));
    }

    static void search(Activity a, List<Version> all, String initial) {
        PagedPicker.show(a, AppLanguage.text(a, "Pesquisar Bíblias"), (q, offset, limit) -> {
            List<PagedPicker.Entry> rows = new ArrayList<>();
            int total = 0;
            for (Version v : all) {
                if (!matches(v, q)) continue;
                if (total >= offset && rows.size() < limit) rows.add(new PagedPicker.Entry(v.id, v.name(), v.languageName + " · " + v.scope(a) + " · " + v.license(a)));
                total++;
            }
            return new PagedPicker.Page(rows, total);
        }, e -> { for (Version v : all) if (v.id.equals(e.id)) { confirm(a, v); break; } });
    }

    static void confirm(Activity a, Version v) {
        String message = (v.description.isEmpty() ? "" : v.description + "\n\n") + v.languageName + " · " + v.scope(a) + "\n" + v.license(a) + (v.copyright.isEmpty() ? "" : " · " + v.copyright)
                + (v.updated.isEmpty() ? "" : "\n" + AppLanguage.text(a, "Actualizada em") + " " + v.updated)
                + "\n\n" + AppLanguage.text(a, "Fica disponível sem internet depois de importada.");
        new UiDialog.Builder(a).setTitle(v.name()).setMessage(message)
                .setPositiveButton("Descarregar e importar", (d, w) -> download(a, v))
                .setNegativeButton("Cancelar", null).show();
    }

    static void download(Activity a, Version v) {
        PlusUi.toast(a, AppLanguage.text(a, "A descarregar") + " " + v.name() + "…");
        net.execute(() -> {
            File dir = downloads(a);
            String name = v.id + ".usfm";
            File target = new File(dir, name), part = new File(dir, name + ".parte");
            try {
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) if (System.currentTimeMillis() - f.lastModified() > 86400000L) f.delete();
                HttpURLConnection conn = OpenLibraries.open(DOWNLOAD + v.id + "_usfm.zip");
                int books;
                try (InputStream in = new OpenLibraries.Limited(new BufferedInputStream(conn.getInputStream()), MAX_ZIP);
                     Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(part), StandardCharsets.UTF_8))) {
                    books = combine(in, out);
                } finally { conn.disconnect(); }
                if (books == 0) throw new IOException(AppLanguage.text(a, "Esta versão não tem livros bíblicos reconhecidos."));
                if (target.exists()) target.delete();
                if (!part.renameTo(target)) throw new IOException(AppLanguage.text(a, "Não foi possível guardar a Bíblia."));
                final int count = books;
                a.runOnUiThread(() -> {
                    BibleWork.enqueue(a, uri(a, name), v.name(), 0);
                    PlusUi.toast(a, count + " " + AppLanguage.text(a, "livros descarregados. A importar…"));
                });
            } catch (Exception e) {
                part.delete();
                a.runOnUiThread(() -> new UiDialog.Builder(a).setTitle("A descarga falhou")
                        .setMessage(e.getMessage() == null ? AppLanguage.text(a, "Tente novamente.") : e.getMessage()).setPositiveButton("Fechar", null).show());
            }
        });
    }

    /** Bíblias já importadas e importação de ficheiros (ecrã existente das Ferramentas). */
    static void imported(Activity a) {
        if (a instanceof ToolsActivity) { ((ToolsActivity) a).bibles(); return; }
        try { a.startActivity(new Intent(a, ToolsActivity.class).putExtra("open_bibles", true)); } catch (Exception e) { PlusUi.toast(a, "Não foi possível abrir as Bíblias importadas."); }
    }
}
