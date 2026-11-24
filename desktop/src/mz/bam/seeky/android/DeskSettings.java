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
    static javafx.scene.paint.Color color(int c) { return javafx.scene.paint.Color.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255); }
    static Node view(DeskMain d) {
        VBox page = DeskUi.page(DeskUi.title("Definições"));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Tema"), DeskUi.muted("Os mesmos temas do Seeky no telemóvel."), themes(d)));
        DeskAi.load(d.store);
        page.getChildren().add(DeskUi.card(DeskUi.heading("Seeky IA (Google Gemini)"), DeskUi.muted(d.store.get("gemini_key", "").isEmpty() ? "Sem chave guardada." : "Chave guardada e protegida pelo Windows · modelo " + (SeekyAiModels.chosen.isEmpty() ? "automático (" + SeekyAiModels.current() + ")" : SeekyAiModels.label(SeekyAiModels.chosen))), DeskUi.button("Ligação da IA", true, () -> { DeskAi.settings(d); d.open("settings"); })));
        ComboBox<String> voices = new ComboBox<>(); voices.getItems().add("Voz predefinida do Windows");
        Slider rate = new Slider(-5, 5, Integer.parseInt(d.store.get("voice_rate", "0"))); rate.setMajorTickUnit(1); rate.setSnapToTicks(true); rate.setPrefWidth(220);
        rate.valueProperty().addListener((o, a, b) -> d.store.put("voice_rate", String.valueOf(b.intValue())));
        d.run(null, DeskSpeech::voices, list -> { voices.getItems().addAll(list); String v = d.store.get("voice", ""); voices.getSelectionModel().select(v.isEmpty() ? 0 : Math.max(0, voices.getItems().indexOf(v))); });
        voices.setOnAction(e -> { int i = voices.getSelectionModel().getSelectedIndex(); d.store.put("voice", i <= 0 ? "" : voices.getValue()); });
        page.getChildren().add(DeskUi.card(DeskUi.heading("Leitura em voz alta"), DeskUi.muted("Usa as vozes instaladas no Windows (Definições do Windows → Hora e idioma → Voz para adicionar mais, incluindo Português)."), DeskUi.row(voices, DeskUi.muted("Velocidade"), rate, DeskUi.button("Experimentar", false, () -> DeskSpeech.say(d, "Olá! Esta é a voz do Seeky no Windows.")))));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Actualizações"), DeskUi.muted("Versão " + DeskMain.VERSION), DeskUi.button("Procurar actualizações agora", true, () -> d.worker.execute(() -> DeskUpdates.check(d, true)))));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Os seus dados"), DeskUi.muted("Bíblias, notas, livros e estudo ficam guardados neste computador."), DeskUi.button("Abrir a pasta", false, () -> DeskFiles.system(DeskStore.home()))));
        return DeskUi.scroll(page);
    }
}
