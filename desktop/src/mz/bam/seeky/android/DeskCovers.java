package mz.bam.seeky.android;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * Capas dos livros: a imagem de capa do EPUB (ou do EPUB convertido), a primeira imagem de um CBZ,
 * a primeira página de um PDF; os restantes recebem uma capa desenhada com o título, como no telemóvel.
 * As capas ficam guardadas em %APPDATA%\Seeky\capas.
 */
final class DeskCovers {
    private DeskCovers() {}
    static final ExecutorService WORK = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "seeky-capas"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t; });
    static final Map<String, Image> MEMORY = Collections.synchronizedMap(new LinkedHashMap<String, Image>(64, .75f, true) { protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 300; } });
    static final int W = 240, H = 360;
    static final int[] TONES = {0xff7b3f2f, 0xff2f5d50, 0xff3f4b7a, 0xff6b4f2a, 0xff5b3a6b, 0xff2f4f6b, 0xff7a5a2f, 0xff4a6b3a, 0xff6b2f3f, 0xff3a5a6b};

    static String key(File f) {
        try { MessageDigest md = MessageDigest.getInstance("SHA-1"); byte[] h = md.digest((f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length()).getBytes(StandardCharsets.UTF_8)); StringBuilder s = new StringBuilder(); for (int i = 0; i < 10; i++) s.append(String.format("%02x", h[i])); return s.toString(); }
        catch (Exception e) { return Integer.toHexString(f.getAbsolutePath().hashCode()); }
    }
    static File cached(File f) { return new File(DeskStore.folder("capas"), key(f) + ".png"); }
    static File none(File f) { return new File(DeskStore.folder("capas"), key(f) + ".none"); }

    /** A cover node of the given width: the drawn cover at once, the real one when it is ready. */
    static Node view(File f, String title, double width) {
        double height = Math.round(width * 1.5);
        StackPane box = new StackPane(); box.setMinSize(width, height); box.setPrefSize(width, height); box.setMaxSize(width, height); box.getStyleClass().add("cover");
        Rectangle clip = new Rectangle(width, height); clip.setArcWidth(10); clip.setArcHeight(10); box.setClip(clip);
        box.getChildren().add(drawn(title, f.getName(), width, height));
        Image known = MEMORY.get(f.getAbsolutePath());
        if (known != null) { box.getChildren().add(image(known, width, height)); return box; }
        WORK.execute(() -> {
            try {
                File png = cover(f); if (png == null) return;
                Image img = new Image(png.toURI().toString(), W, H, true, true);
                if (img.isError()) return; MEMORY.put(f.getAbsolutePath(), img);
                javafx.application.Platform.runLater(() -> box.getChildren().add(image(img, width, height)));
            } catch (Throwable ignored) {}
        });
        return box;
    }
    static ImageView image(Image img, double w, double h) { ImageView v = new ImageView(img); v.setFitWidth(w); v.setFitHeight(h); v.setPreserveRatio(false); v.setSmooth(true); return v; }
    /** The drawn cover: a colour from the title, a spine line, the title and the format. */
    static Node drawn(String title, String name, double w, double h) {
        int tone = TONES[Math.floorMod((title == null ? "" : title.toLowerCase(Locale.ROOT)).hashCode(), TONES.length)];
        StackPane p = new StackPane(); p.setStyle("-fx-background-color: linear-gradient(to bottom right, " + DeskThemes.hex(tone) + ", " + DeskThemes.hex(DeskThemes.blend(tone, 0x000000, .35f)) + ");");
        Region spine = new Region(); spine.setStyle("-fx-background-color: rgba(255,255,255,0.18);"); spine.setMaxWidth(3); StackPane.setAlignment(spine, Pos.CENTER_LEFT); StackPane.setMargin(spine, new Insets(0, 0, 0, w * 0.07));
        Label t = new Label(title); t.setWrapText(true); t.setStyle("-fx-text-fill: white; -fx-font-family: Georgia, 'Cambria', serif; -fx-font-weight: bold; -fx-font-size: " + Math.max(10, Math.round(w / 9.5)) + "px;"); t.setMaxWidth(w * 0.78); t.setAlignment(Pos.CENTER); t.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT) : "";
        Label k = new Label(ext); k.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: " + Math.max(8, Math.round(w / 16)) + "px; -fx-font-weight: bold;"); StackPane.setAlignment(k, Pos.BOTTOM_CENTER); StackPane.setMargin(k, new Insets(0, 0, h * 0.06, 0));
        p.getChildren().addAll(spine, t, k); p.setMinSize(w, h); p.setPrefSize(w, h);
        return p;
    }

    /** The cover file (cached), or null when the book has no picture of its own. */
    static File cover(File f) throws Exception {
        File png = cached(f), no = none(f);
        if (png.isFile()) return png; if (no.isFile()) return null;
        BufferedImage img = null; String n = f.getName().toLowerCase(Locale.ROOT);
        try {
            if (n.endsWith(".epub")) img = epub(f);
            else if (n.endsWith(".cbz")) img = cbz(f);
            else if (n.endsWith(".pdf")) img = pdf(f);
            else if (EbookConvert.supported(f.getName()) && !n.endsWith(".docx")) { File conv = new File(DeskStore.folder("convertidos"), EbookConvert.epubName(f.getName())); if (conv.isFile()) img = epub(conv); }
        } catch (Throwable ignored) {}
        if (img == null) { no.createNewFile(); return null; }
        ImageIO.write(scale(img), "png", png); return png;
    }
    static BufferedImage scale(BufferedImage src) {
        double r = Math.max((double) W / src.getWidth(), (double) H / src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * r)), h = Math.max(1, (int) Math.round(src.getHeight() * r));
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB); java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, (W - w) / 2, (H - h) / 2, w, h, null); g.dispose(); return out;
    }
    static BufferedImage read(InputStream in) throws IOException { byte[] b = ContentText.read(in, 20 << 20); return ImageIO.read(new ByteArrayInputStream(b)); }
    /** EPUB: the manifest's cover-image, the "cover" meta, or a picture called cover. */
    static BufferedImage epub(File f) throws Exception {
        try (ZipFile z = new ZipFile(f)) {
            ZipEntry container = z.getEntry("META-INF/container.xml"); if (container == null) return null;
            String c; try (InputStream in = z.getInputStream(container)) { c = new String(ContentText.read(in, 1 << 20), StandardCharsets.UTF_8); }
            Matcher m = Pattern.compile("full-path\\s*=\\s*\"([^\"]+)\"").matcher(c); if (!m.find()) return null;
            String opfPath = m.group(1); ZipEntry opfEntry = z.getEntry(opfPath); if (opfEntry == null) return null;
            String opf; try (InputStream in = z.getInputStream(opfEntry)) { opf = new String(ContentText.read(in, 4 << 20), StandardCharsets.UTF_8); }
            String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
            Map<String, String[]> items = new LinkedHashMap<>(); // id → {href, media-type, properties}
            Matcher it = Pattern.compile("<(?:\\w+:)?item\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE).matcher(opf);
            while (it.find()) { String a = it.group(1); items.put(attr(a, "id"), new String[]{attr(a, "href"), attr(a, "media-type"), attr(a, "properties")}); }
            String href = null;
            for (String[] v : items.values()) if (v[2].contains("cover-image")) { href = v[0]; break; }
            if (href == null) { Matcher meta = Pattern.compile("<(?:\\w+:)?meta\\b[^>]*name\\s*=\\s*\"cover\"[^>]*content\\s*=\\s*\"([^\"]+)\"|<(?:\\w+:)?meta\\b[^>]*content\\s*=\\s*\"([^\"]+)\"[^>]*name\\s*=\\s*\"cover\"", Pattern.CASE_INSENSITIVE).matcher(opf); if (meta.find()) { String id = meta.group(1) != null ? meta.group(1) : meta.group(2); String[] v = items.get(id); if (v != null) href = v[0]; } }
            if (href == null) for (String[] v : items.values()) if (v[1].startsWith("image/") && v[0].toLowerCase(Locale.ROOT).contains("cover")) { href = v[0]; break; }
            if (href == null) return null;
            String path = normalize(base + java.net.URLDecoder.decode(href, "UTF-8")); ZipEntry img = z.getEntry(path); if (img == null) return null;
            try (InputStream in = z.getInputStream(img)) { return read(in); }
        }
    }
    /** The title written in an EPUB (dc:title), or "" when there is none. */
    static String epubTitle(File f) {
        try (ZipFile z = new ZipFile(f)) {
            ZipEntry container = z.getEntry("META-INF/container.xml"); if (container == null) return "";
            String c; try (InputStream in = z.getInputStream(container)) { c = new String(ContentText.read(in, 1 << 20), StandardCharsets.UTF_8); }
            Matcher m = Pattern.compile("full-path\\s*=\\s*\"([^\"]+)\"").matcher(c); if (!m.find()) return "";
            ZipEntry opfEntry = z.getEntry(m.group(1)); if (opfEntry == null) return "";
            String opf; try (InputStream in = z.getInputStream(opfEntry)) { opf = new String(ContentText.read(in, 4 << 20), StandardCharsets.UTF_8); }
            Matcher t = Pattern.compile("<dc:title[^>]*>([^<]{1,300})</dc:title>", Pattern.CASE_INSENSITIVE).matcher(opf);
            return t.find() ? t.group(1).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").trim() : "";
        } catch (Exception e) { return ""; }
    }
    static String attr(String tag, String name) { Matcher m = Pattern.compile("(?:^|\\s)" + name + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(tag); return m.find() ? m.group(1) : ""; }
    static String normalize(String p) { Deque<String> out = new ArrayDeque<>(); for (String s : p.split("/")) { if (s.isEmpty() || s.equals(".")) continue; if (s.equals("..")) { if (!out.isEmpty()) out.removeLast(); } else out.addLast(s); } return String.join("/", out); }
    static BufferedImage cbz(File f) throws Exception {
        try (ZipFile z = new ZipFile(f)) {
            List<String> names = new ArrayList<>(); for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) { String n = e.nextElement().getName(); if (n.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png|gif|bmp)$") && !n.contains("__MACOSX")) names.add(n); }
            if (names.isEmpty()) return null; names.sort(String.CASE_INSENSITIVE_ORDER);
            try (InputStream in = z.getInputStream(z.getEntry(names.get(0)))) { return read(in); }
        }
    }
    static BufferedImage pdf(File f) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(f)) {
            if (doc.getNumberOfPages() == 0) return null;
            org.apache.pdfbox.pdmodel.common.PDRectangle box = doc.getPage(0).getCropBox();
            float dpi = (float) Math.max(20, Math.min(110, 72.0 * H / Math.max(1, box.getHeight())));
            return new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0, dpi);
        }
    }
}
