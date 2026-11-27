package mz.bam.seeky.android;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 5.53 · Áudio no computador: escolher a voz (natural Piper ou do Windows), descarregar vozes Piper e gravar
 * capítulos de um livro em MP3 (um ficheiro por capítulo, com título, livro e número da faixa).
 */
final class DeskAudio {
    private DeskAudio() {}

    // ================================================================ Definições
    static Node card(DeskMain d) {
        VBox box = new VBox(10);
        ComboBox<String> voice = new ComboBox<>(); voice.setPrefWidth(360);
        List<String[]> choices = new ArrayList<>(); // {engine, id}
        Runnable fill = () -> {
            voice.getItems().clear(); choices.clear();
            for (DeskVoices.Voice v : DeskVoices.PIPER) if (v.ready() && DeskVoices.engineReady()) { voice.getItems().add("Natural · " + v.label); choices.add(new String[]{"piper", v.id}); }
            voice.getItems().add("Windows · voz predefinida"); choices.add(new String[]{"windows", ""});
        };
        fill.run();
        Runnable select = () -> {
            String engine = d.store.get("voice_engine", "windows"), id = engine.equals("piper") ? d.store.get("piper_voice", "") : engine.equals("edge") ? d.store.get("edge_voice", "") : d.store.get("voice", "");
            int at = -1; for (int i = 0; i < choices.size(); i++) if (choices.get(i)[0].equals(engine) && choices.get(i)[1].equals(id)) at = i;
            if (at < 0) for (int i = 0; i < choices.size(); i++) if (choices.get(i)[0].equals("windows") && choices.get(i)[1].isEmpty()) at = i;
            voice.getSelectionModel().select(Math.max(0, at));
        };
        select.run();
        d.run(null, DeskSpeech::voices, list -> { for (String n : list) { voice.getItems().add("Windows · " + n); choices.add(new String[]{"windows", n}); } select.run(); });
        Label edgeState = DeskUi.muted(DeskEdge.available() ? "A procurar as vozes naturais do Edge…" : "O Microsoft Edge não foi encontrado neste computador.");
        if (DeskEdge.available()) d.run(null, () -> DeskEdge.sorted(DeskEdge.get().voices(20000), true), list -> {
            int at = 0; for (DeskEdge.Voice v : list) { voice.getItems().add(at, "Edge · " + v.label()); choices.add(at, new String[]{"edge", v.name, v.lang}); at++; }
            edgeState.setText(list.isEmpty() ? "O Edge não devolveu vozes naturais (precisam de Internet). Tente de novo mais tarde." : list.size() + " vozes naturais do Edge disponíveis (precisam de Internet para ler).");
            select.run();
        });
        voice.setOnAction(e -> { int i = voice.getSelectionModel().getSelectedIndex(); if (i < 0 || i >= choices.size()) return; String[] c = choices.get(i); d.store.put("voice_engine", c[0]); if (c[0].equals("piper")) d.store.put("piper_voice", c[1]); else if (c[0].equals("edge")) { d.store.put("edge_voice", c[1]); d.store.put("edge_lang", c[2]); } else d.store.put("voice", c[1]); });
        Slider rate = new Slider(-5, 5, Integer.parseInt(d.store.get("voice_rate", "0"))); rate.setMajorTickUnit(1); rate.setSnapToTicks(true); rate.setPrefWidth(200);
        rate.valueProperty().addListener((o, a, b) -> d.store.put("voice_rate", String.valueOf(b.intValue())));
        box.getChildren().addAll(DeskUi.heading("Leitura em voz alta e áudio"),
            DeskUi.muted("A voz escolhida lê os livros, a Bíblia e as notas, e grava capítulos em MP3 (menu de cada livro › Gravar em MP3). As vozes naturais do Edge são as mesmas da leitura em voz alta do Microsoft Edge."),
            DeskUi.row(voice, DeskUi.muted("Velocidade"), rate, DeskUi.button("Experimentar", false, () -> DeskSpeech.say(d, sample(d)))), edgeState, onward(d));
        box.getChildren().addAll(new Separator(), azure(d));
        // Vozes naturais Piper
        VBox natural = new VBox(8);
        natural.getChildren().add(DeskUi.heading("Vozes naturais (Piper)"));
        natural.getChildren().add(DeskUi.muted("Vozes gratuitas, mais naturais do que as do Windows, que funcionam sem Internet depois de descarregadas. O motor tem 22 MB e cada voz cerca de 63 MB; o Seeky confirma cada ficheiro (SHA-256) antes de o usar."));
        if (!DeskVoices.engineReady()) natural.getChildren().add(DeskUi.button("Descarregar o motor Piper (22 MB)", true, () -> fetch(d, "Motor Piper", (c, p) -> DeskVoices.installEngine(c, p))));
        for (DeskVoices.Voice v : DeskVoices.PIPER) {
            Label state = DeskUi.muted(v.ready() ? "Instalada" : "63 MB");
            Node action = v.ready()
                ? DeskUi.button("Remover", false, () -> { if (DeskUi.confirm("Remover " + v.label + "?", "Pode voltar a descarregá-la depois.")) { DeskVoices.removeVoice(v); if (v.id.equals(d.store.get("piper_voice", ""))) d.store.put("voice_engine", "windows"); d.open("settings"); } })
                : DeskUi.button("Descarregar", false, () -> {
                    if (!DeskVoices.engineReady()) { DeskUi.info("Falta o motor", "Descarregue primeiro o motor Piper."); return; }
                    fetch(d, v.label, (c, p) -> DeskVoices.installVoice(v, c, p), () -> { d.store.put("voice_engine", "piper"); d.store.put("piper_voice", v.id); });
                });
            VBox words = new VBox(1, DeskUi.text(v.label), DeskUi.muted("Licença " + v.licence)); HBox.setHgrow(words, Priority.ALWAYS);
            HBox row = new HBox(10, words, state, action); row.setAlignment(Pos.CENTER_LEFT);
            natural.getChildren().add(row);
        }
        box.getChildren().addAll(new Separator(), natural);
        box.getStyleClass().add("card"); box.setPadding(new javafx.geometry.Insets(16));
        return box;
    }
    /** Gravar com as vozes do Edge: o Azure Speech (chave gratuita do utilizador). */
    static Node azure(DeskMain d) {
        ComboBox<String> region = new ComboBox<>(); region.getItems().addAll(DeskAzure.REGIONS); region.getSelectionModel().select(d.store.get("azure_region", "westeurope"));
        PasswordField key = new PasswordField(); key.setPromptText(d.store.get("azure_key", "").isEmpty() ? "Chave do recurso Speech (Azure)" : "Chave guardada · deixe vazio para manter"); key.setPrefWidth(320);
        Label state = DeskUi.muted(DeskAzure.configured(d.store) ? "Ligado ao Azure Speech (" + d.store.get("azure_region", "") + "). As vozes do Edge também gravam MP3." : "Sem chave: as vozes do Edge só lêem em voz alta; para gravar MP3 use o Piper ou o Windows, ou ligue o Azure Speech.");
        Button save = DeskUi.button("Testar e guardar", true, () -> {
            String entered = key.getText().trim(), reg = region.getValue(); state.setText("A testar…");
            d.run(null, () -> { String k = entered.isEmpty() ? DeskAzure.key(d.store) : entered; if (k.length() < 16 || !k.matches("[A-Za-z0-9]+")) throw new Exception("Introduza a chave do recurso Speech do Azure."); byte[] mp3 = DeskAzure.synth(reg, k, DeskAzure.ssml("Olá.", "pt-PT-RaquelNeural", "pt-PT", 0), () -> false); if (mp3.length < 500) throw new Exception("O Azure não devolveu áudio."); if (!entered.isEmpty()) d.store.put("azure_key", DeskSecret.protect(k)); d.store.put("azure_region", reg); return true; },
                ok -> { state.setText("Ligado ao Azure Speech (" + reg + "). As vozes do Edge também gravam MP3."); key.clear(); });
        });
        Button forget = DeskUi.button("Esquecer a chave", false, () -> { d.store.put("azure_key", ""); d.open("settings"); });
        VBox box = new VBox(8, DeskUi.heading("Gravar com as vozes do Edge (Azure Speech)"),
            DeskUi.muted("O Edge só toca as vozes; para as gravar em MP3 a Microsoft oferece as mesmas vozes no Azure Speech. Crie um recurso Speech gratuito (500 000 caracteres por mês) em portal.azure.com e cole aqui a chave. A chave fica protegida pelo Windows e só é enviada à Microsoft com o texto a gravar."),
            DeskUi.row(region, key, save, forget), state);
        return box;
    }
    /** 5.56: Ouvir reads a page and turns to the next one by itself, to the end of the book. */
    static CheckBox onward(DeskMain d) {
        CheckBox c = new CheckBox("Ao ouvir um livro, passar sozinho à página seguinte quando acabar a leitura da página");
        c.setSelected(DeskReader.continuous(d)); c.setWrapText(true); c.setOnAction(e -> d.store.put("read_continue", c.isSelected() ? "1" : "0")); return c;
    }
    static String sample(DeskMain d) {
        if ("edge".equals(d.store.get("voice_engine", ""))) { String l = d.store.get("edge_lang", "pt"); if (l.startsWith("en")) return "Hello! This is Seeky reading aloud with a natural Edge voice."; if (l.startsWith("es")) return "¡Hola! Este es Seeky leyendo con una voz natural."; if (l.startsWith("fr")) return "Bonjour ! C'est Seeky qui lit avec une voix naturelle."; }
        DeskVoices.Voice v = DeskVoices.chosen(d.store);
        if (v != null && v.language.startsWith("en")) return "Hello! This is Seeky reading aloud with a natural voice.";
        if (v != null && v.language.startsWith("es")) return "¡Hola! Este es Seeky leyendo en voz alta.";
        if (v != null && v.language.startsWith("fr")) return "Bonjour ! C'est Seeky qui lit à voix haute.";
        return "Olá! Esta é a voz do Seeky a ler em voz alta.";
    }
    interface Fetch { void run(java.util.function.BooleanSupplier cancel, DeskVoices.Progress p) throws Exception; }
    static void fetch(DeskMain d, String what, Fetch job) { fetch(d, what, job, null); }
    /** A download with a progress bar and a Cancel button; the settings page is redrawn at the end. */
    static void fetch(DeskMain d, String what, Fetch job, Runnable after) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("A descarregar · " + what); DeskUi.style(dlg.getDialogPane());
        ProgressBar bar = new ProgressBar(0); bar.setPrefWidth(420); Label line = DeskUi.muted("A ligar…");
        dlg.getDialogPane().setContent(new VBox(10, line, bar)); dlg.getDialogPane().getButtonTypes().add(new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
        AtomicBoolean cancel = new AtomicBoolean(); dlg.setOnHidden(e -> cancel.set(true));
        d.run(null, () -> { try { job.run(cancel::get, (done, total, name) -> Platform.runLater(() -> { bar.setProgress(total > 0 ? (double) done / total : -1); line.setText(name + " · " + (done >> 20) + (total > 0 ? " de " + (total >> 20) : "") + " MB"); })); return true; } finally { Platform.runLater(dlg::close); } },
            ok -> { if (after != null) after.run(); d.status(what + " pronto."); d.open("settings"); });
        dlg.show();
    }

    // ================================================================ Gravar em MP3
    static final class Part { final String name, text; Part(String name, String text) { this.name = name; this.text = text; } }
    /** The chapters of a book (EPUB, Word, TXT and converted formats) or groups of 10 pages of a PDF. */
    static List<Part> parts(File f) throws Exception {
        List<Part> out = new ArrayList<>();
        if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.pdmodel.PDDocument.load(f, org.apache.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())) {
                org.apache.pdfbox.text.PDFTextStripper s = new org.apache.pdfbox.text.PDFTextStripper(); int pages = doc.getNumberOfPages();
                for (int from = 1; from <= pages; from += 10) { int to = Math.min(pages, from + 9); s.setStartPage(from); s.setEndPage(to); out.add(new Part(from == to ? "Página " + from : "Páginas " + from + "–" + to, s.getText(doc))); }
            }
        } else { DeskReader.Book b = DeskReader.chapters(f); for (int i = 0; i < b.texts.size(); i++) out.add(new Part(b.names.get(i), b.texts.get(i))); }
        return out;
    }
    static String safe(String name) { String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cc}]+", " ").replaceAll("\\s+", " ").trim(); if (s.length() > 80) s = s.substring(0, 80).trim(); return s.isEmpty() ? "Parte" : s; }
    static String fileName(int number, int total, String chapter) { int width = Math.max(2, String.valueOf(total).length()); return String.format(Locale.ROOT, "%0" + width + "d", number) + " - " + safe(chapter) + ".mp3"; }

    static String titleOf(DeskMain d, File book) { try { List<Object[]> r = d.store.rows("SELECT title FROM books WHERE path=?", book.getPath()); if (!r.isEmpty() && !(r.get(0)[0] + "").trim().isEmpty()) return r.get(0)[0] + ""; } catch (java.sql.SQLException ignored) {} return book.getName().replaceFirst("\\.[^.]+$", ""); }
    static void exportBook(DeskMain d, File book) {
        d.run("A abrir o livro…", () -> parts(book), list -> choose(d, book, list));
    }
    static void choose(DeskMain d, File book, List<Part> list) {
        if (list.isEmpty()) { DeskUi.info("Sem texto", "Este livro não tem texto que o Seeky consiga ler."); return; }
        String title = titleOf(d, book);
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("Gravar em MP3 · " + title); DeskUi.style(dlg.getDialogPane());
        Spinner<Integer> from = new Spinner<>(1, list.size(), 1), to = new Spinner<>(1, list.size(), list.size()); from.setEditable(true); to.setEditable(true);
        ComboBox<String> first = new ComboBox<>(), last = new ComboBox<>();
        for (int i = 0; i < list.size(); i++) { String n = (i + 1) + ". " + list.get(i).name; first.getItems().add(n); last.getItems().add(n); }
        first.getSelectionModel().select(0); last.getSelectionModel().select(list.size() - 1); first.setPrefWidth(320); last.setPrefWidth(320);
        DeskVoices.Voice natural = DeskVoices.chosen(d.store);
        boolean edge = "edge".equals(d.store.get("voice_engine", "")) && !d.store.get("edge_voice", "").isEmpty();
        if (edge && !DeskAzure.configured(d.store)) {
            Alert need = new Alert(Alert.AlertType.CONFIRMATION, "As vozes naturais do Edge só lêem em voz alta. Para as gravar em MP3, ligue o Azure Speech em Definições (chave gratuita), ou grave agora com uma voz Piper ou do Windows.", new ButtonType("Abrir Definições"), new ButtonType("Gravar com outra voz"), ButtonType.CANCEL);
            need.setHeaderText("Gravar com a voz do Edge"); need.setTitle("Seeky"); DeskUi.style(need.getDialogPane());
            Optional<ButtonType> pick = need.showAndWait(); if (!pick.isPresent() || pick.get() == ButtonType.CANCEL) return;
            if (pick.get().getText().equals("Abrir Definições")) { d.open("settings"); return; }
            edge = false;
        }
        boolean useEdge = edge;
        String voiceName = useEdge ? new DeskEdge.Voice(d.store.get("edge_voice", ""), d.store.get("edge_lang", "pt-PT")).label() + " (Edge, via Azure)" : natural != null ? natural.label + " (natural)" : d.store.get("voice", "").isEmpty() ? "voz predefinida do Windows" : d.store.get("voice", "") + " (Windows)";
        File music = new File(System.getProperty("user.home"), "Music");
        File[] folder = {new File(new File(music.isDirectory() ? music : new File(System.getProperty("user.home")), "Seeky"), safe(title))};
        Label where = DeskUi.text(folder[0].getPath());
        Button pick = DeskUi.button("Escolher pasta…", false, () -> { DirectoryChooser c = new DirectoryChooser(); c.setTitle("Pasta para os MP3"); File base = folder[0].getParentFile(); if (base != null && base.isDirectory()) c.setInitialDirectory(base); File f = c.showDialog(d.stage); if (f != null) { folder[0] = new File(f, safe(title)); where.setText(folder[0].getPath()); } });
        CheckBox addToMusic = new CheckBox("Juntar à Música do Seeky no fim"); addToMusic.setSelected(true);
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10);
        g.addRow(0, DeskUi.muted("Do capítulo"), first); g.addRow(1, DeskUi.muted("Até ao capítulo"), last); g.addRow(2, DeskUi.muted("Voz"), DeskUi.text(voiceName)); g.addRow(3, DeskUi.muted("Pasta"), new VBox(4, where, pick));
        VBox box = new VBox(10, g, addToMusic, DeskUi.muted("Um MP3 por capítulo (64 kbit/s, cerca de 30 MB por hora de leitura), com o título do capítulo, o livro e o número da faixa. A voz e a velocidade mudam-se em Definições."));
        box.setPrefWidth(560); dlg.getDialogPane().setContent(box);
        ButtonType go = new ButtonType("Gravar", ButtonBar.ButtonData.OK_DONE); dlg.getDialogPane().getButtonTypes().addAll(go, ButtonType.CANCEL);
        dlg.showAndWait().filter(b -> b == go).ifPresent(b -> {
            int a = first.getSelectionModel().getSelectedIndex(), z = last.getSelectionModel().getSelectedIndex();
            if (z < a) { int t = a; a = z; z = t; }
            record(d, book, title, list, a, z, folder[0], addToMusic.isSelected(), useEdge);
        });
    }
    static void record(DeskMain d, File book, String title, List<Part> list, int a, int z, File folder, boolean addToMusic, boolean useEdge) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Seeky"); dlg.setHeaderText("A gravar · " + title); DeskUi.style(dlg.getDialogPane());
        ProgressBar bar = new ProgressBar(0); bar.setPrefWidth(460); Label line = DeskUi.muted("A preparar…");
        dlg.getDialogPane().setContent(new VBox(10, line, bar)); dlg.getDialogPane().getButtonTypes().add(new ButtonType("Parar", ButtonBar.ButtonData.CANCEL_CLOSE));
        AtomicBoolean cancel = new AtomicBoolean(); dlg.setOnHidden(e -> cancel.set(true));
        DeskVoices.Voice natural = useEdge ? null : DeskVoices.chosen(d.store); String winVoice = d.store.get("voice", ""); int rate = Integer.parseInt(d.store.get("voice_rate", "0"));
        DeskEdge.Voice edgeVoice = useEdge ? new DeskEdge.Voice(d.store.get("edge_voice", ""), d.store.get("edge_lang", "pt-PT")) : null; String region = d.store.get("azure_region", "");
        d.run(null, () -> {
            try {
                if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Não foi possível criar a pasta " + folder);
                File tmp = DeskStore.folder("tmp-audio"); int count = z - a + 1, made = 0; double seconds = 0;
                for (int i = a; i <= z; i++) {
                    if (cancel.get()) break;
                    Part p = list.get(i); int n = i - a;
                    // 5.58: passages and numbers said as on the phone ("Salmos capítulo 13, versículo 40").
                    String text = DeskSpeech.prepare(p.text == null ? "" : p.text, edgeVoice != null ? edgeVoice.lang : natural != null && natural.id.length() >= 5 ? natural.id.substring(0, 5).replace('_', '-') : "pt-PT").trim();
                    if (text.length() < 2) continue;
                    final int step = n; Platform.runLater(() -> { line.setText("Capítulo " + (step + 1) + " de " + count + " · " + p.name + " · a gerar a voz"); bar.setProgress((double) step / count); });
                    if (edgeVoice != null) {
                        File out = new File(folder, fileName(i + 1, list.size(), p.name)); String key = DeskAzure.key(d.store);
                        seconds += DeskAzure.mp3(region, key, edgeVoice.azure(), edgeVoice.lang, rate, text, out, new Mp3Writer.Tags(p.name, title, "Seeky", i + 1, list.size()), cancel::get,
                            (done, total) -> Platform.runLater(() -> { line.setText("Capítulo " + (step + 1) + " de " + count + " · " + p.name + " · parte " + done + " de " + total); bar.setProgress((step + (double) done / total) / count); }));
                        made++; continue;
                    }
                    File wav = new File(tmp, "cap-" + UUID.randomUUID() + ".wav");
                    try {
                        if (natural != null) DeskVoices.piperWav(natural, rate, text, wav, cancel::get); else DeskVoices.windowsWav(winVoice, rate, text, wav, cancel::get);
                        Platform.runLater(() -> line.setText("Capítulo " + (step + 1) + " de " + count + " · " + p.name + " · a converter para MP3"));
                        File out = new File(folder, fileName(i + 1, list.size(), p.name));
                        seconds += Mp3Writer.fromWav(wav, out, new Mp3Writer.Tags(p.name, title, "Seeky", i + 1, list.size()), cancel::get,
                            (done, total) -> Platform.runLater(() -> bar.setProgress((step + 0.5 + 0.5 * done / Math.max(1, total)) / count)));
                        made++;
                    } finally { wav.delete(); }
                }
                return new double[]{made, seconds};
            } finally { Platform.runLater(dlg::close); }
        }, r -> {
            int made = (int) r[0]; long minutes = Math.round(r[1] / 60);
            if (made > 0 && addToMusic) { List<String> folders = d.music.folders(); if (!folders.contains(folder.getPath())) { folders.add(folder.getPath()); d.music.folders(folders); } d.music.rescan(false); }
            Alert done = new Alert(Alert.AlertType.INFORMATION, made + (made == 1 ? " capítulo gravado" : " capítulos gravados") + " em MP3 (" + minutes + " min) em\n" + folder.getPath() + (cancel.get() ? "\n\nA gravação foi parada; os capítulos já gravados ficam." : ""), new ButtonType("Abrir a pasta"), ButtonType.CLOSE);
            done.setHeaderText(made > 0 ? "Áudio gravado" : "Nada gravado"); done.setTitle("Seeky"); DeskUi.style(done.getDialogPane());
            done.showAndWait().filter(b -> b.getText().equals("Abrir a pasta")).ifPresent(b -> DeskFiles.system(folder));
        });
        dlg.show();
    }
}
