package mz.bam.seeky.android;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/** Seeky IA on the computer: the user's Gemini key (protected by Windows), model choice with fallback, actions on a note. */
final class DeskAi {
    private DeskAi() {}
    static boolean loaded;
    static void load(DeskStore s) {
        if (loaded) return; loaded = true; SeekyAiService.load(serviceStore(s));
        String known = s.get("gemini_models", "");
        SeekyAiModels.restore(s.get("gemini_model", ""), s.get("gemini_working", ""), known.isEmpty() ? Collections.emptyList() : Arrays.asList(known.split(",")));
        SeekyAiModels.store = (chosen, working, available) -> { s.put("gemini_model", chosen); s.put("gemini_working", working); s.put("gemini_models", String.join(",", available)); };
    }
    /** The person's own key, or (5.55) the Seeky IA service when there is none. */
    static String key(DeskStore s) throws Exception { String own = DeskSecret.reveal(s.get("gemini_key", "")); return own.isEmpty() && SeekyAiService.available() ? SeekyAiService.KEY : own; }
    /** Seeky IA can answer: own key saved, or the Seeky IA service is on. */
    static boolean ready(DeskStore s) { load(s); return !s.get("gemini_key", "").isEmpty() || SeekyAiService.available(); }
    static SeekyAiService.Store serviceStore(DeskStore s) { return new SeekyAiService.Store() { public String get(String k, String f) { return s.get(k, f); } public void put(String k, String v) { s.put(k, v); } }; }

    /** Ligação da IA: key, model and test. */
    static void settings(DeskMain d) {
        load(d.store);
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Seeky"); dialog.setHeaderText("Ligação da IA · Google Gemini"); DeskUi.style(dialog.getDialogPane());
        PasswordField key = new PasswordField(); key.setPromptText(d.store.get("gemini_key", "").isEmpty() ? "Chave do Google AI Studio" : "Chave guardada · deixe vazio para manter");
        ComboBox<String> model = new ComboBox<>(); model.getItems().add("Automático (recomendado)");
        List<String> names = new ArrayList<>(SeekyAiModels.available.isEmpty() ? Arrays.asList(SeekyAiModels.FALLBACKS) : SeekyAiModels.available);
        for (String m : names) model.getItems().add(SeekyAiModels.label(m));
        model.getSelectionModel().select(SeekyAiModels.chosen.isEmpty() ? 0 : Math.max(0, names.indexOf(SeekyAiModels.chosen) + 1));
        Label status = DeskUi.muted("Automático usa " + SeekyAiModels.current() + ". A chave fica protegida pelo Windows e só é enviada à Google." + (SeekyAiService.available() ? "\n\nSem chave, o Seeky IA já funciona pelo serviço do Seeky. " + SeekyAiService.about() : ""));
        VBox box = new VBox(10, DeskUi.text("Use uma chave do Google AI Studio (quota gratuita). O Seeky envia à Google só o texto que pedir."), key, DeskUi.muted("Modelo"), model, status);
        box.setPrefWidth(460); dialog.getDialogPane().setContent(box);
        ButtonType test = new ButtonType("Testar e guardar", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(test, ButtonType.CANCEL);
        Button ok = (Button) dialog.getDialogPane().lookupButton(test);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            e.consume(); ok.setDisable(true); status.setText("A testar uma resposta do Gemini…");
            String entered = key.getText().trim(); int pick = model.getSelectionModel().getSelectedIndex(); String wanted = pick <= 0 ? "" : names.get(pick - 1);
            d.run(null, () -> {
                String secret = entered.isEmpty() ? key(d.store) : entered; if (secret.isEmpty()) throw new Exception("Introduza uma chave do Google AI Studio.");
                if (!SeekyAiClient.validKey(secret)) throw new Exception("Introduza uma chave válida do Google AI Studio.");
                SeekyAiModels.choose(wanted); SeekyAiClient.testConnection(secret, new SeekyAiClient.Call());
                if (!entered.isEmpty()) d.store.put("gemini_key", DeskSecret.protect(secret));
                return SeekyAiModels.working.isEmpty() ? SeekyAiModels.current() : SeekyAiModels.working;
            }, used -> { String n = SeekyAiModels.takeNotice(); dialog.close(); DeskUi.info("Ligado ao Gemini", (n.isEmpty() ? "" : n + "\n") + "Modelo: " + used); });
            ok.setDisable(false);
        });
        dialog.showAndWait();
    }

    /** The ✨ menu of a note. */
    static void menu(DeskMain d, TextArea body, TextField title) { menu(d, body, title, body); }
    /** 5.57: the note editor keeps the marks in a TextArea that is not shown; the menu opens by the button. */
    static void menu(DeskMain d, TextArea body, TextField title, javafx.scene.Node anchor) {
        load(d.store);
        if (!ready(d.store)) { if (DeskUi.confirm("Ligar a Seeky IA", "Para usar a IA nas notas, guarde primeiro a sua chave do Google Gemini.")) settings(d); return; }
        ContextMenu m = new ContextMenu();
        for (NotepadAiText.Action a : NotepadAiText.ACTIONS) { MenuItem i = new MenuItem(a.label); i.setOnAction(e -> run(d, a, a.key.equals("pedir") ? DeskUi.ask("Pedir ao Seeky", "Por exemplo: transforma em esboço de sermão", "") : "", body, title)); m.getItems().add(i); }
        m.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }
    static void run(DeskMain d, NotepadAiText.Action a, String extra, TextArea body, TextField title) {
        if (a.key.equals("pedir") && extra == null) return;
        String text = body.getText(); int s = body.getSelection().getStart(), e = body.getSelection().getEnd(); boolean part = e > s;
        String source = part ? NotepadAiText.source("", text.substring(s, e)) : NotepadAiText.source(title.getText(), text);
        if (source.trim().isEmpty()) { DeskUi.info("Nota vazia", "Escreva alguma coisa na nota primeiro."); return; }
        if (NotepadAiText.tooLong(source)) { if (a.replace) { DeskUi.info("Nota longa", "Seleccione uma parte e tente outra vez."); return; } source = NotepadAiText.cut(source); }
        final String sent = source;
        d.run("O Seeky está a escrever…", () -> new SeekyAiClient().ask(SeekyAiCore.GEMINI, key(d.store), sent, NotepadText.title(title.getText(), text), Collections.emptyList(), NotepadAiText.prompt(a.key, extra, false), false, new SeekyAiClient.Call()).text, answer -> {
            String switched = SeekyAiModels.takeNotice(); if (!switched.isEmpty()) d.status(switched);
            String clean = DeskRich.tidy(answer); ButtonType ok = new ButtonType(a.replace ? "Substituir" : "Inserir na nota", ButtonBar.ButtonData.OK_DONE);
            DeskRich.show(d, a.label, clean, ok, new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE)).filter(b -> b == ok).ifPresent(b -> {
                if (!body.getText().equals(text)) { body.appendText("\n\n" + clean); return; }
                if (a.replace && part) body.setText(text.substring(0, s) + clean + text.substring(e)); else body.setText(NotepadAiText.apply(a, text, clean, false));
            });
        });
    }
}
