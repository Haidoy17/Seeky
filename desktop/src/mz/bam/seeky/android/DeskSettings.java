package mz.bam.seeky.android;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Definições: theme, Seeky IA, Windows voice, updates and where the data lives. */
final class DeskSettings {
    private DeskSettings() {}
    /** Swatches of every theme; the chosen one has a ring in the accent colour. */
    static FlowPane themes(DeskMain d) {
        FlowPane grid = new FlowPane(10, 10); String chosen = d.store.get("theme", DeskThemes.DEFAULT);
        if (chosen.equals("dark")) chosen = "Seeky escuro"; else if (chosen.equals("light")) chosen = "Seeky claro";
        List<String> names = new ArrayList<>(); for (DeskThemes.Theme t : DeskThemes.ALL) names.add(t.name); names.add(2, DeskThemes.SCHEDULE);
        for (String name : names) {
            boolean schedule = name.equals(DeskThemes.SCHEDULE);
            DeskThemes.Theme t = DeskThemes.find(name), night = DeskThemes.find("Floresta"), day = DeskThemes.find("Areia");
            javafx.scene.shape.Rectangle back = new javafx.scene.shape.Rectangle(132, 54); back.setArcWidth(16); back.setArcHeight(16);
            back.setFill(schedule ? new javafx.scene.paint.LinearGradient(0, 0, 1, 0, true, javafx.scene.paint.CycleMethod.NO_CYCLE, new javafx.scene.paint.Stop(0.5, color(day.bg)), new javafx.scene.paint.Stop(0.5, color(night.bg))) : color(t.bg));
            back.setStroke(name.equals(chosen) ? color(DeskThemes.current.accent) : color(DeskThemes.current.line)); back.setStrokeWidth(name.equals(chosen) ? 3 : 1);
            javafx.scene.shape.Rectangle card = new javafx.scene.shape.Rectangle(70, 18); card.setArcWidth(8); card.setArcHeight(8); card.setFill(color(schedule ? day.surface : t.surface));
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(8, color(schedule ? night.accent : t.accent));
            HBox inner = new HBox(8, card, dot); inner.setAlignment(javafx.geometry.Pos.CENTER); inner.setMouseTransparent(true);
            StackPane swatch = new StackPane(back, inner);
            Label label = DeskUi.text(name); label.setStyle("-fx-font-size: 13px;");
            VBox box = new VBox(5, swatch, label); box.setCursor(javafx.scene.Cursor.HAND);
            Tooltip.install(box, new Tooltip(schedule ? "Areia das 7h às 19h, Floresta à noite" : t.about));
            box.setOnMouseClicked(e -> { d.theme(name); d.open("settings"); });
            grid.getChildren().add(box);
        }
        return grid;
    }
    /** Botão Flash: on/off, size, edge, keep running, start with Windows and the index. */
    static Node flashCard(DeskMain d) {
        CheckBox on = new CheckBox("Botão flutuante de pesquisa (Flash)"); on.setSelected(d.flash.enabled());
        on.setOnAction(e -> { d.store.put("flash_on", on.isSelected() ? "1" : "0"); if (on.isSelected()) d.flash.restart(); else d.flash.hide(); });
        CheckBox edge = new CheckBox("Encostar à margem do ecrã"); edge.setSelected(!"0".equals(d.store.get("flash_edge", "1"))); edge.setOnAction(e -> d.store.put("flash_edge", edge.isSelected() ? "1" : "0"));
        CheckBox keep = new CheckBox("Manter o botão quando fechar a janela do Seeky"); keep.setSelected(!"0".equals(d.store.get("flash_keep", "1"))); keep.setOnAction(e -> d.store.put("flash_keep", keep.isSelected() ? "1" : "0"));
        CheckBox boot = new CheckBox("Iniciar com o Windows (só o botão Flash)"); boot.setSelected(DeskFlash.startsWithWindows());
        boot.setOnAction(e -> { boolean want = boot.isSelected(); d.run(null, () -> { DeskFlash.startWithWindows(want); return want; }, v -> d.status(v ? "O botão Flash abre com o Windows." : "O Seeky já não abre com o Windows.")); });
        ToggleGroup g = new ToggleGroup(); HBox sizes = new HBox(6);
        String[] names = {"Pequeno", "Médio", "Grande"};
        for (int i = 0; i < 3; i++) { final String v = i + ""; ToggleButton b = new ToggleButton(names[i]); b.getStyleClass().add("chip"); b.setToggleGroup(g); b.setSelected(d.store.get("flash_size", "1").equals(v)); b.setOnAction(e -> { d.store.put("flash_size", v); d.flash.restart(); }); sizes.getChildren().add(b); }
        long n = d.flash.index.count(), built = d.flash.index.built();
        Label info = DeskUi.muted(built == 0 ? "O índice prepara-se na primeira pesquisa." : n + " ficheiros no índice · actualizado " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date(built)));
        VBox folders = new VBox(4);
        for (String f : d.store.get("flash_folders", "").split("\n")) if (!f.trim().isEmpty()) folders.getChildren().add(DeskUi.row(DeskUi.text(f), DeskUi.grow(), DeskUi.button("Remover", false, () -> { List<String> all = new ArrayList<>(Arrays.asList(d.store.get("flash_folders", "").split("\n"))); all.remove(f); d.store.put("flash_folders", String.join("\n", all)); d.open("settings"); })));
        Button add = DeskUi.button("Juntar pasta ao índice", false, () -> { javafx.stage.DirectoryChooser c = new javafx.stage.DirectoryChooser(); java.io.File f = c.showDialog(d.stage); if (f == null) return; String all = d.store.get("flash_folders", ""); d.store.put("flash_folders", (all.isEmpty() ? "" : all + "\n") + f.getAbsolutePath()); d.flash.reindex(true); d.open("settings"); });
        return DeskUi.card(DeskUi.heading("Botão Flash"), DeskUi.muted("Pesquisa rápida por cima das outras janelas, como no telemóvel. Também abre com Ctrl+K."),
            on, DeskUi.row(DeskUi.muted("Tamanho"), sizes), edge, keep, boot, DeskUi.heading("Índice"), info, folders,
            DeskUi.row(DeskUi.button("Actualizar o índice agora", false, () -> d.flash.reindex(true)), add, DeskUi.button("Abrir a pesquisa", true, () -> d.flash.open(""))));
    }
    static javafx.scene.paint.Color color(int c) { return javafx.scene.paint.Color.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255); }
    static Node view(DeskMain d) {
        VBox page = DeskUi.page(DeskUi.title("Definições"));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Tema"), DeskUi.muted("Os mesmos temas do Seeky no telemóvel."), themes(d)));
        page.getChildren().add(flashCard(d));
        DeskAi.load(d.store);
        page.getChildren().add(DeskUi.card(DeskUi.heading("Seeky IA (Google Gemini)"), DeskUi.muted(d.store.get("gemini_key", "").isEmpty() ? (SeekyAiService.available() ? "Sem chave própria: a usar o serviço Seeky IA (limite diário por computador)." : "Sem chave guardada.") : "Chave guardada e protegida pelo Windows · modelo " + (SeekyAiModels.chosen.isEmpty() ? "automático (" + SeekyAiModels.current() + ")" : SeekyAiModels.label(SeekyAiModels.chosen))), DeskUi.button("Ligação da IA", true, () -> { DeskAi.settings(d); d.open("settings"); })));
        ComboBox<String> skip = new ComboBox<>(); skip.getItems().addAll("10 segundos", "15 segundos", "30 segundos");
        String cur = d.store.get("media_skip", "15"); skip.getSelectionModel().select(cur.equals("10") ? 0 : cur.equals("30") ? 2 : 1);
        skip.setOnAction(e -> d.store.put("media_skip", new String[]{"10", "15", "30"}[Math.max(0, skip.getSelectionModel().getSelectedIndex())]));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Leitor de música e vídeo"), DeskUi.muted("Os botões de recuar e avançar saltam este tempo. Aleatório e Repetir (tudo ou esta faixa) estão na barra do leitor."), DeskUi.row(DeskUi.muted("Saltar"), skip)));
        page.getChildren().add(DeskAudio.card(d));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Actualizações"), DeskUi.muted("Versão " + DeskMain.VERSION), DeskUi.button("Procurar actualizações agora", true, () -> d.worker.execute(() -> DeskUpdates.check(d, true)))));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Os seus dados"), DeskUi.muted("Bíblias, notas, livros e estudo ficam guardados neste computador."), DeskUi.button("Abrir a pasta", false, () -> DeskFiles.system(DeskStore.home()))));
        return DeskUi.scroll(page);
    }
}
