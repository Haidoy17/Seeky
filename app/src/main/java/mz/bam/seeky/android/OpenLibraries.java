package mz.bam.seeky.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Xml;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/**
 * Bibliotecas gratuitas: catálogos OPDS (Project Gutenberg incluído e outros que o utilizador
 * acrescente, como um servidor Calibre em casa). Pesquisa, navegação e descarga de EPUB/PDF, que
 * entram directamente na biblioteca do Seeky.
 *
 * <p>Só HTTPS, excepto endereços da rede local; feeds até 5 MB e livros até 200 MB; o XML é lido
 * sem entidades externas.
 */
final class OpenLibraries {
    static final String GUTENBERG = "https://www.gutenberg.org/ebooks/search.opds/";
    static final long MAX_FEED = 5L * 1024 * 1024, MAX_BOOK = 200L * 1024 * 1024;
    static final ExecutorService net = Executors.newSingleThreadExecutor();

    private OpenLibraries() {}

    static final class Link {
        final String rel, type, href;
        Link(String rel, String type, String href) { this.rel = rel; this.type = type; this.href = href; }
    }

    static final class Entry {
        String title = "", author = "", summary = "";
        final List<Link> links = new ArrayList<>();

        Link book() {
            Link pdf = null;
            for (Link l : links) {
                boolean acquisition = l.rel.startsWith("http://opds-spec.org/acquisition");
                if (acquisition && l.type.startsWith("application/epub+zip")) return l;
                if (acquisition && l.type.startsWith("application/pdf") && pdf == null) pdf = l;
            }
            return pdf;
        }

        Link catalog() {
            for (Link l : links) if (l.type.contains("opds-catalog") || (l.type.contains("atom+xml") && !l.rel.contains("acquisition") && !l.rel.equals("alternate")))
                if (!l.rel.equals("self") && !l.rel.equals("related")) return l;
            return null;
        }
    }

    static final class Feed {
        String title = "";
        String next;
        final List<Entry> entries = new ArrayList<>();
    }

    // ---- leitura do feed ----

    static Feed parse(InputStream in, String base) throws Exception {
        XmlPullParser x = Xml.newPullParser();
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        x.setInput(in, null);
        Feed feed = new Feed();
        Entry entry = null;
        boolean inAuthor = false;
        String text = null;
        for (int ev = x.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = x.next()) {
            String name = ev == XmlPullParser.START_TAG || ev == XmlPullParser.END_TAG ? local(x.getName()) : "";
            if (ev == XmlPullParser.START_TAG) {
                text = null;
                if (name.equals("entry")) entry = new Entry();
                else if (name.equals("author")) inAuthor = true;
                else if (name.equals("link")) {
                    String rel = attr(x, "rel"), type = attr(x, "type"), href = attr(x, "href");
                    if (href.isEmpty()) continue;
                    String abs = new URL(new URL(base), href).toString();
                    if (entry != null) entry.links.add(new Link(rel, type.toLowerCase(Locale.ROOT), abs));
                    else if (rel.equals("next")) feed.next = abs;
                }
            } else if (ev == XmlPullParser.TEXT) {
                text = (text == null ? "" : text) + x.getText();
            } else if (ev == XmlPullParser.END_TAG) {
                String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
                if (name.equals("entry") && entry != null) { if (!entry.title.isEmpty()) feed.entries.add(entry); entry = null; }
                else if (name.equals("author")) inAuthor = false;
                else if (name.equals("title")) { if (entry != null) entry.title = value; else if (feed.title.isEmpty()) feed.title = value; }
                else if (name.equals("name") && inAuthor && entry != null && entry.author.isEmpty()) entry.author = value;
                else if ((name.equals("summary") || name.equals("content")) && entry != null && entry.summary.isEmpty()) entry.summary = value.length() > 600 ? value.substring(0, 600) + "…" : value;
                text = null;
            }
        }
        return feed;
    }

    static String local(String name) { int colon = name.indexOf(':'); return colon < 0 ? name : name.substring(colon + 1); }

    static String attr(XmlPullParser x, String name) {
        for (int i = 0; i < x.getAttributeCount(); i++) if (local(x.getAttributeName(i)).equals(name)) return x.getAttributeValue(i) == null ? "" : x.getAttributeValue(i);
        return "";
    }

    /** HTTPS sempre; HTTP só para servidores na rede local (por exemplo, Calibre em casa). */
    static boolean allowed(String url) {
        try {
            URL u = new URL(url);
            if (u.getProtocol().equals("https")) return true;
            if (!u.getProtocol().equals("http")) return false;
            String host = u.getHost();
            if (!host.matches("[0-9.]+")) return host.endsWith(".local") || host.equals("localhost");
            InetAddress a = InetAddress.getByName(host);
            return a.isSiteLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress();
        } catch (Exception e) { return false; }
    }

    static HttpURLConnection open(String url) throws IOException {
        if (!allowed(url)) throw new IOException("Endereço não permitido: use https://");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Seeky/" + MainActivity.VERSION.replace("Seeky ", "") + " (OPDS)");
        c.setRequestProperty("Accept", "application/atom+xml, application/xml;q=0.9, */*;q=0.5");
        int code = c.getResponseCode();
        if (code >= 300 && code < 400) {
            String location = c.getHeaderField("Location");
            c.disconnect();
            if (location == null) throw new IOException("Redireccionamento inválido.");
            return open(new URL(new URL(url), location).toString());
        }
        if (code != 200) { c.disconnect(); throw new IOException("O catálogo respondeu " + code + "."); }
        return c;
    }

    static Feed fetch(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = new Limited(c.getInputStream(), MAX_FEED)) { return parse(in, c.getURL().toString()); }
        finally { c.disconnect(); }
    }

    static final class Limited extends FilterInputStream {
        long left;
        Limited(InputStream in, long max) { super(in); left = max; }
        @Override public int read() throws IOException { if (left <= 0) throw new IOException("Resposta demasiado grande."); int r = super.read(); if (r >= 0) left--; return r; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) throw new IOException("Resposta demasiado grande.");
            int r = super.read(b, off, (int) Math.min(len, left));
            if (r > 0) left -= r;
            return r;
        }
    }

    // ---- catálogos guardados ----

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("opds", Context.MODE_PRIVATE); }

    static JSONArray saved(Context c) { try { return new JSONArray(prefs(c).getString("catalogs", "[]")); } catch (Exception e) { return new JSONArray(); } }

    // ---- interface ----

    static void show(MainActivity a) {
        UiPanel p = new UiPanel(a, "Bibliotecas gratuitas", AppLanguage.text(a, "Livros de domínio público e catálogos OPDS"), "globe");
        p.section("Project Gutenberg");
        p.action("search", "Pesquisar livros", "Título, autor ou assunto · mais de 70 000 livros", false, () -> search(a));
        p.action("book", "Livros em português", "Os mais descarregados em português", false, () -> feed(a, GUTENBERG + "?query=l.pt&sort_order=downloads", false));
        p.action("star", "Mais populares", "Os livros mais descarregados", false, () -> feed(a, GUTENBERG + "?sort_order=downloads", false));
        JSONArray list = saved(a);
        p.section("Os seus catálogos");
        for (int i = 0; i < list.length(); i++) {
            JSONObject c = list.optJSONObject(i);
            p.action("collection", c.optString("name"), c.optString("url"), false, () -> feed(a, c.optString("url"), true));
        }
        p.action("add", "Adicionar catálogo OPDS", "Endereço de uma biblioteca ou do servidor Calibre", false, () -> addCatalog(a));
        p.note("Os livros descarregados entram na sua biblioteca. Precisa de internet só para pesquisar e descarregar.");
        p.show();
    }

    static void search(MainActivity a) {
        EditText q = new EditText(a);
        q.setHint(AppLanguage.text(a, "Ex.: Machado de Assis, Eça de Queirós, Dom Casmurro"));
        q.setSingleLine(true);
        LinearLayout box = new LinearLayout(a);
        box.setPadding(a.dp(20), a.dp(6), a.dp(20), 0);
        box.addView(q, new LinearLayout.LayoutParams(-1, -2));
        a.dialog().setTitle("Pesquisar no Project Gutenberg").setView(box).setPositiveButton("Pesquisar", (d, w) -> {
            String text = q.getText().toString().trim();
            if (!text.isEmpty()) feed(a, GUTENBERG + "?query=" + Uri.encode(text), false);
        }).setNegativeButton("Cancelar", null).show();
    }

    static void addCatalog(MainActivity a) {
        EditText name = new EditText(a), url = new EditText(a);
        name.setHint(AppLanguage.text(a, "Nome"));
        url.setHint("https://…/opds");
        url.setSingleLine(true);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(20), a.dp(6), a.dp(20), 0);
        box.addView(name);
        box.addView(url);
        a.dialog().setTitle("Adicionar catálogo OPDS").setView(box).setPositiveButton("Adicionar", (d, w) -> {
            String u = url.getText().toString().trim(), n = name.getText().toString().trim();
            if (!allowed(u)) { a.toast("Use um endereço https:// (http:// só na rede local)."); return; }
            try {
                JSONArray list = saved(a);
                list.put(new JSONObject().put("name", n.isEmpty() ? new URL(u).getHost() : n).put("url", u));
                prefs(a).edit().putString("catalogs", list.toString()).apply();
                feed(a, u, true);
            } catch (Exception e) { a.showError(e); }
        }).setNegativeButton("Cancelar", null).show();
    }

    static void removeCatalog(MainActivity a, String url) {
        JSONArray list = saved(a), out = new JSONArray();
        for (int i = 0; i < list.length(); i++) if (!url.equals(list.optJSONObject(i).optString("url"))) out.put(list.optJSONObject(i));
        prefs(a).edit().putString("catalogs", out.toString()).apply();
        a.toast("Catálogo removido.");
    }

    static void feed(MainActivity a, String url, boolean custom) {
        a.toast("A abrir o catálogo…");
        net.execute(() -> {
            try {
                Feed f = fetch(url);
                a.ui(() -> showFeed(a, url, f, custom));
            } catch (Exception e) {
                a.ui(() -> a.dialog().setTitle("Catálogo indisponível").setMessage(AppLanguage.text(a, "Verifique a ligação à internet e tente novamente.") + "\n\n" + e.getMessage())
                        .setPositiveButton("Fechar", null).show());
            }
        });
    }

    static void showFeed(MainActivity a, String url, Feed f, boolean custom) {
        UiPanel p = new UiPanel(a, f.title.isEmpty() ? AppLanguage.text(a, "Catálogo") : f.title, f.entries.size() + " " + AppLanguage.text(a, "resultados"), "globe");
        if (f.entries.isEmpty()) p.note("Nada encontrado. Tente outras palavras.");
        for (int i = 0; i < Math.min(60, f.entries.size()); i++) {
            Entry e = f.entries.get(i);
            Link book = e.book();
            Link catalog = book == null ? e.catalog() : null;
            if (book == null && catalog == null) continue;
            p.action(book != null ? "book" : "folder", e.title, e.author.isEmpty() ? (book != null ? "EPUB" : AppLanguage.text(a, "Abrir")) : e.author, false, () -> {
                if (book != null) confirm(a, e, book);
                else feed(a, catalog.href, false);
            });
        }
        if (f.next != null) p.action("arrow", "Mais resultados", "Página seguinte", false, () -> feed(a, f.next, custom));
        if (custom) p.action("trash", "Remover este catálogo", url, false, () -> removeCatalog(a, url));
        p.show();
    }

    static void confirm(MainActivity a, Entry e, Link book) {
        String kind = book.type.startsWith("application/pdf") ? "PDF" : "EPUB";
        a.dialog().setTitle(e.title).setMessage((e.author.isEmpty() ? "" : e.author + "\n\n") + (e.summary.isEmpty() ? "" : e.summary + "\n\n") + AppLanguage.text(a, "Formato:") + " " + kind)
                .setPositiveButton("Descarregar para a biblioteca", (d, w) -> download(a, e, book))
                .setNegativeButton("Cancelar", null).show();
    }

    static File folder(Context c) {
        File dir = StorageAccess.all(c) ? new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky/Livros") : c.getExternalFilesDir("Livros");
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    static String fileName(Entry e, Link book) {
        String base = (e.author.isEmpty() ? "" : e.author + " - ") + e.title;
        base = WifiTransfer.cleanName(base);
        if (base.length() > 120) base = base.substring(0, 120);
        return base + (book.type.startsWith("application/pdf") ? ".pdf" : ".epub");
    }

    static void download(MainActivity a, Entry e, Link book) {
        a.toast("A descarregar…");
        net.execute(() -> {
            File dir = folder(a);
            File part = null;
            try {
                if (dir == null) throw new IOException("Sem pasta para guardar o livro.");
                File target = WifiTransfer.unique(dir, fileName(e, book));
                part = new File(dir, "." + target.getName() + ".parte");
                HttpURLConnection c = open(book.href);
                try (InputStream in = new Limited(c.getInputStream(), MAX_BOOK); OutputStream out = new FileOutputStream(part)) { PdfTools.copy(in, out); }
                finally { c.disconnect(); }
                if (part.length() < 64 || !part.renameTo(target)) throw new IOException("A descarga ficou incompleta.");
                android.media.MediaScannerConnection.scanFile(a.getApplicationContext(), new String[]{target.getPath()}, null, null);
                a.ui(() -> {
                    ImportWork.enqueue(a, Collections.singletonList(Uri.fromFile(target)), 0, true);
                    a.toast(AppLanguage.text(a, "A adicionar à biblioteca:") + " " + e.title);
                });
            } catch (Exception ex) {
                if (part != null) part.delete();
                a.ui(() -> a.dialog().setTitle("A descarga falhou").setMessage(ex.getMessage() == null ? AppLanguage.text(a, "Tente novamente.") : ex.getMessage()).setPositiveButton("Fechar", null).show());
            }
        });
    }
}
