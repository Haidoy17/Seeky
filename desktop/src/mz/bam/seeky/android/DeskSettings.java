package mz.bam.seeky.android;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Definições: theme, Seeky IA, Windows voice, updates and where the data lives. */
final class DeskSettings {
    private DeskSettings() {}
    static Node view(DeskMain d) {
        VBox page = DeskUi.page(DeskUi.title("Definições"));
        ToggleGroup g = new ToggleGroup(); RadioButton dark = new RadioButton("Escuro"), light = new RadioButton("Claro"); dark.setToggleGroup(g); light.setToggleGroup(g);
        (d.root.getStyleClass().contains("light") ? light : dark).setSelected(true);
        dark.setOnAction(e -> d.theme("dark")); light.setOnAction(e -> d.theme("light"));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Tema"), DeskUi.row(dark, light)));
        DeskAi.load(d.store);
        page.getChildren().add(DeskUi.card(DeskUi.heading("Seeky IA (Google Gemini)"), DeskUi.muted(d.store.get("gemini_key", "").isEmpty() ? "Sem chave guardada." : "Chave guardada e protegida pelo Windows · modelo " + (SeekyAiModels.chosen.isEmpty() ? "automático (" + SeekyAiModels.current() + ")" : SeekyAiModels.label(SeekyAiModels.chosen))), DeskUi.button("Ligação da IA", true, () -> { DeskAi.settings(d); d.open("settings"); })));
        ComboBox<String> voices = new ComboBox<>(); voices.getItems().add("Voz predefinida do Windows");
        Slider rate = new Slider(-5, 5, Integer.parseInt(d.store.get("voice_rate", "0"))); rate.setMajorTickUnit(1); rate.setSnapToTicks(true); rate.setPrefWidth(220);
        rate.valueProperty().addListener((o, a, b) -> d.store.put("voice_rate", String.valueOf(b.intValue())));
        d.run(null, DeskSpeech::voices, list -> { voices.getItems().addAll(list); String v = d.store.get("voice", ""); voices.getSelectionModel().select(v.isEmpty() ? 0 : Math.max(0, voices.getItems().indexOf(v))); });
        voices.setOnAction(e -> { int i = voices.getSelectionModel().getSelectedIndex(); d.store.put("voice", i <= 0 ? "" : voices.getValue()); });
        page.getChildren().add(DeskUi.card(DeskUi.heading("Leitura em voz alta"), DeskUi.muted("Usa as vozes instaladas no Windows (Definições do Windows → Hora e idioma → Voz para adicionar mais, incluindo Português)."), DeskUi.row(voices, DeskUi.muted("Velocidade"), rate, DeskUi.button("Experimentar", false, () -> DeskSpeech.say(d, "Olá! Esta é a voz do Seeky no Windows.")))));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Actualizações"), DeskUi.muted("Versão instalada: " + DeskMain.VERSION + ". O Seeky para Windows recebe as mesmas novidades do Seeky para Android."), DeskUi.button("Procurar actualizações agora", true, () -> d.worker.execute(() -> DeskUpdates.check(d, true)))));
        page.getChildren().add(DeskUi.card(DeskUi.heading("Os seus dados"), DeskUi.muted(DeskStore.home().getPath()), DeskUi.button("Abrir a pasta", false, () -> DeskFiles.system(DeskStore.home()))));
        return DeskUi.scroll(page);
    }
}
