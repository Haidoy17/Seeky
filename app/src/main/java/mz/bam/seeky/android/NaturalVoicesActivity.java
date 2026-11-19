package mz.bam.seeky.android;

import android.content.*;
import android.net.Uri;
import android.speech.tts.TextToSpeech;

/**
 * Vozes naturais do Google (motor "Serviços de voz do Google"), sem chave de API nem módulo
 * extra. A versão online é a mais natural; a offline funciona sem Internet depois de baixada.
 * Substitui o antigo módulo Seeky Vozes Naturais, retirado na 5.19.0.
 */
public final class NaturalVoicesActivity extends WorkspaceActivity {
    static final String GOOGLE = GoogleVoiceSetup.GOOGLE;

    static void open(Context context) { context.startActivity(new Intent(context, NaturalVoicesActivity.class)); }

    String title() { return "Vozes naturais do Google"; }

    void load() {
        content.removeAllViews();
        SharedPreferences prefs = getSharedPreferences("seeky", 0);
        boolean google = installed(GOOGLE);
        boolean selected = GOOGLE.equals(prefs.getString("speech_engine", GOOGLE));
        boolean online = prefs.getBoolean("speech_online", false);

        if (!google) {
            note("Os Serviços de voz do Google não estão instalados ou estão desactivados neste telefone.");
            action("Instalar vozes do Google (Play Store)", this::playStore);
            action("Definições de voz do Android", () -> VoiceEngines.settings(this));
            return;
        }
        note(selected ? (online ? "Em uso: vozes do Google · online (mais naturais)" : "Em uso: vozes do Google · offline")
                : "As vozes do Google ainda não estão seleccionadas no Seeky.");
        action("Usar vozes online · mais naturais", () -> choose(true));
        action("Usar vozes offline · sem Internet", () -> choose(false));
        action("Baixar vozes offline (Português, English…)", this::download);
        action("Definições de voz do Android", () -> VoiceEngines.settings(this));
        note("Online: a voz mais natural do Google, precisa de Internet durante a leitura. Offline: baixe uma vez a voz Português (Portugal) ou Português (Brasil) e leia sem dados móveis.");
        note("Depois de escolher, abra um livro → Ler em voz alta e ajuste a voz, a velocidade e o tom.");
        if (installed(GoogleVoiceSetup.LEGACY_NATURAL)) {
            note("O antigo módulo Seeky Vozes Naturais já não é usado e pode ser removido.");
            action("Remover o módulo antigo", () -> {
                try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + GoogleVoiceSetup.LEGACY_NATURAL))); }
                catch (Exception e) { PlusUi.error(this, e); }
            });
        }
    }

    void choose(boolean online) {
        getSharedPreferences("seeky", 0).edit().putString("speech_engine", GOOGLE).putString("speech_voice", "").putBoolean("speech_online", online).apply();
        PlusUi.toast(this, online ? "Vozes online do Google seleccionadas." : "Vozes offline do Google seleccionadas.");
        load();
    }

    void download() {
        try { startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GOOGLE)); }
        catch (Exception e) { VoiceEngines.settings(this); }
    }

    void playStore() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GOOGLE))); }
        catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE))); }
            catch (Exception again) { PlusUi.error(this, again); }
        }
    }

    boolean installed(String pkg) {
        try { return getPackageManager().getApplicationInfo(pkg, 0).enabled; }
        catch (Exception e) { return false; }
    }

    protected void onResume() { super.onResume(); load(); }
}
