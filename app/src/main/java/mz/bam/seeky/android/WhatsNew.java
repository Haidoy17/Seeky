package mz.bam.seeky.android;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

/** Painel "Novidades": aparece uma vez depois de cada actualização e fica em Mais. */
final class WhatsNew {
    static final String KEY = "whats_new_seen";

    private WhatsNew() {}

    /** Mostra as novidades na primeira abertura após uma actualização (nunca numa instalação nova). */
    static void maybeShow(MainActivity a) {
        SharedPreferences p = a.prefs;
        if (MainActivity.VERSION.equals(p.getString(KEY, ""))) return;
        boolean updated = true;
        try {
            PackageInfo info = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            updated = info.lastUpdateTime > info.firstInstallTime;
        } catch (Exception ignored) {}
        p.edit().putString(KEY, MainActivity.VERSION).apply();
        if (updated) a.handler.postDelayed(() -> { if (!a.isFinishing() && !a.dead) show(a); }, 700);
    }

    static void show(MainActivity a) {
        UiPanel panel = new UiPanel(a, "Novidades da 5.19.0", "Toque numa novidade para a experimentar", "star");
        panel.action("lock", "Bloqueio da aplicação", "Impressão digital, rosto ou PIN do telefone", false, () -> AppLock.show(a));
        panel.action("restore", "Cópia automática", "Estudos e notas guardados todos os dias ou semanas", false, () -> AutoBackup.show(a));
        panel.action("palette", "Tema Sistema", "Acompanha o modo claro/escuro do Android", false, () -> ThemeSettings.show(a));
        panel.action("flash", "Botão Flash renovado", "Toque longo para ajustes, lançar para a margem, atalho nas Definições Rápidas", false, a::iconSettings);
        panel.section("Voz");
        panel.note("Leitura mais natural: \"Dr.\" lê-se \"Doutor\", \"Capítulo IV\" lê-se \"Capítulo 4\" e os endereços web pelo nome do site. A voz escolhe a variante do seu país (pt-PT em Moçambique). Novo controlo de Tom em Ler em voz alta.");
        panel.section("Segurança");
        panel.note("Ficheiros partilhados por outras aplicações já não podem pedir ao Seeky que leia a sua área privada.");
        panel.show();
    }
}
