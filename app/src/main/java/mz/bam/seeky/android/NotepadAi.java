package mz.bam.seeky.android;

import android.app.Dialog;
import android.content.ClipData;
import android.text.method.LinkMovementMethod;
import android.widget.*;
import java.util.Collections;

/**
 * Seeky IA inside a note: summarise, correct, organise, study questions, tasks, related Bible
 * passages, continue writing or any other request. Works on the selected text when there is one.
 * Uses the user's own Gemini key, kept encrypted in the phone (Estudar com Seeky).
 */
final class NotepadAi {
    private NotepadAi() {}
    interface Clients { SeekyAiClient create(); }
    interface Vaults { SeekyAiVault create(android.content.Context c); }
    static Clients clients = SeekyAiClient::new;
    static Vaults vaults = SeekyAiVault::new;

    static void menu(NotepadActivity a) {
        if (!SeekyAiSettings.allowed(a, SeekyAiCore.GEMINI)) {
            new UiDialog.Builder(a).setTitle("Seeky IA")
                .setMessage("Para usar a IA nas notas, ligue primeiro o Seeky ao Google Gemini em Estudar com Seeky. A sua chave fica guardada e cifrada neste telefone.")
                .setPositiveButton("Configurar", (d, w) -> SeekyAi.open(a, 0)).setNegativeButton("Cancelar", null).show();
            return;
        }
        boolean selection = a.body != null && a.body.getSelectionEnd() > a.body.getSelectionStart();
        UiPanel p = new UiPanel(a, "Seeky IA", AppLanguage.text(a, selection ? "Sobre o texto seleccionado · enviado ao Google Gemini" : "Sobre esta nota · o texto é enviado ao Google Gemini"), "sparkle");
        for (NotepadAiText.Action act : NotepadAiText.ACTIONS) p.action(act.icon, act.label, AppLanguage.text(a, act.detail), false, () -> {
            if (act.key.equals("pedir")) {
                EditText input = PlusUi.input(a, "Por exemplo: transforma em esboço de sermão", "", 600);
                new UiDialog.Builder(a).setTitle("Pedir ao Seeky").setView(input).setPositiveButton("Pedir", (d, w) -> { String t = input.getText().toString().trim(); if (!t.isEmpty()) run(a, act, t); }).setNegativeButton("Cancelar", null).show();
            } else run(a, act, "");
        });
        p.show();
    }

    static void run(NotepadActivity a, NotepadAiText.Action act, String extra) {
        if (a.body == null) return;
        final String body = a.body.getText().toString();
        final int s = Math.max(0, Math.min(a.body.getSelectionStart(), a.body.getSelectionEnd())), e = Math.max(0, Math.max(a.body.getSelectionStart(), a.body.getSelectionEnd()));
        final boolean part = e > s;
        String source = part ? NotepadAiText.source("", body.substring(s, e)) : NotepadAiText.source(a.title.getText().toString(), body);
        if (source.trim().isEmpty()) { PlusUi.toast(a, "Escreva alguma coisa na nota primeiro."); return; }
        if (NotepadAiText.tooLong(source)) {
            if (act.replace) { PlusUi.toast(a, "A nota é longa demais para fazer isto de uma vez. Seleccione uma parte e tente outra vez."); return; }
            PlusUi.toast(a, "A nota é longa: o Seeky usa o início (12000 caracteres).");
            source = NotepadAiText.cut(source);
        }
        final String sent = source; final boolean english = AppLanguage.english(a);
        final SeekyAiClient.Call call = new SeekyAiClient.Call();
        ProgressBar spin = new ProgressBar(a); spin.setIndeterminate(true);
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(a.dp(8), a.dp(10), a.dp(8), a.dp(4));
        box.addView(spin); TextView label = new TextView(a); label.setText(AppLanguage.text(a, act.label) + " · " + AppLanguage.text(a, "O Seeky está a escrever…")); label.setTextColor(a.s.p.muted); label.setPadding(0, a.dp(10), 0, 0); label.setGravity(android.view.Gravity.CENTER); box.addView(label);
        Dialog waiting = new UiDialog.Builder(a).setTitle("Seeky IA").setView(box).setNegativeButton("Cancelar", (d, w) -> call.cancel()).setOnCancelListener(d -> call.cancel()).show();
        a.worker.execute(() -> {
            String key = "";
            try {
                key = vaults.create(a).get(SeekyAiCore.GEMINI);
                if (key == null || key.isEmpty()) throw new IllegalStateException(AppLanguage.text(a, "Configure a chave em Estudar com Seeky."));
                SeekyAiCore.Result r = clients.create().ask(SeekyAiCore.GEMINI, key, sent, a.currentTitle(), Collections.emptyList(), NotepadAiText.prompt(act.key, extra, english), english, call);
                final String answer = r.text;
                PlusUi.post(a, () -> { try { waiting.dismiss(); } catch (Exception ignored) {} if (!call.cancelled.get() && a.screen.equals("edit")) show(a, act, answer, body, part ? s : -1, part ? e : -1, english); });
            } catch (Exception ex) {
                final String why = SeekyAiClient.redact(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), key);
                PlusUi.post(a, () -> { try { waiting.dismiss(); } catch (Exception ignored) {} if (!call.cancelled.get()) PlusUi.toast(a, AppLanguage.text(a, "O Seeky IA não conseguiu responder") + ": " + why); });
            }
        });
    }

    /** The answer, formatted, with Insert/Replace, Copy and Discard. */
    static void show(NotepadActivity a, NotepadAiText.Action act, String answer, String body, int s, int e, boolean english) {
        String clean = NotepadAiText.clean(answer);
        if (clean.isEmpty()) { PlusUi.toast(a, "O Seeky IA não devolveu texto. Tente outra vez."); return; }
        TextView view = new TextView(a); view.setTextColor(a.s.p.ink); view.setTextSize(15); view.setLineSpacing(a.dp(2), 1.08f); view.setTextIsSelectable(false);
        view.setText(NotepadRender.render(a, clean, a.s.p.ink, a.s.p.muted, a.s.p.accent, a.getResources().getDisplayMetrics().density, a.dp(260), null));
        view.setMovementMethod(LinkMovementMethod.getInstance());
        ScrollView scroll = new ScrollView(a); scroll.addView(view); scroll.setPadding(a.dp(4), a.dp(6), a.dp(4), a.dp(6));
        FrameLayout frame = new FrameLayout(a); frame.addView(scroll, new FrameLayout.LayoutParams(-1, Math.min(a.dp(420), (int) (a.getResources().getDisplayMetrics().heightPixels * .55f))));
        boolean part = s >= 0 && e > s;
        String primary = act.replace ? (part ? "Substituir a selecção" : "Substituir o texto") : "Inserir na nota";
        new UiDialog.Builder(a).setTitle(AppLanguage.text(a, act.label)).setView(frame)
            .setPositiveButton(primary, (d, w) -> {
                String current = a.body.getText().toString();
                if (!current.equals(body)) { PlusUi.toast(a, "A nota mudou entretanto; o texto foi inserido no fim."); a.applyEdit(NotepadText.insert(current, current.length(), clean, true)); return; }
                String next; int caret;
                if (act.replace && part) { next = body.substring(0, s) + clean + body.substring(e); caret = s + clean.length(); }
                else { next = NotepadAiText.apply(act, body, clean, english); caret = next.length(); }
                a.applyEdit(new NotepadText.Edit(next, caret, caret));
                PlusUi.toast(a, act.replace ? "Texto substituído. Pode desfazer." : "Inserido na nota. Pode desfazer.");
            })
            .setNeutralButton("Copiar", (d, w) -> { try { ((android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Seeky IA", NotepadText.plain(clean))); PlusUi.toast(a, "Copiado."); } catch (Exception ignored) {} })
            .setNegativeButton("Descartar", null).show();
    }
}
