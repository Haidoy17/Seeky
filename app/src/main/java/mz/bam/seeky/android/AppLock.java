package mz.bam.seeky.android;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Bloqueio da aplicação com a autenticação do próprio Android (impressão digital, rosto,
 * PIN, padrão ou palavra-passe). O Seeky não guarda nenhum código: confia no ecrã de
 * bloqueio do sistema.
 *
 * <p>Regras: ao abrir a aplicação (processo novo) ou ao voltar depois de mais tempo do que o
 * limite escolhido em segundo plano, qualquer ecrã do Seeky é coberto por {@link LockActivity}
 * até o utilizador se autenticar. O painel do botão Flash segue a mesma regra. A pré-visualização
 * na lista de aplicações recentes é ocultada (Android 13+).
 */
final class AppLock implements Application.ActivityLifecycleCallbacks {
    static final String PREFS = "app_lock";
    /** Tempos possíveis (segundos) até voltar a pedir autenticação. */
    static final int[] TIMEOUTS = {30, 60, 300, 900};

    private static final Object LOCK = new Object();
    private static long unlockedAt;      // elapsedRealtime do último desbloqueio; 0 = bloqueado
    private static long lastActive;      // última vez que o Seeky esteve visível
    private static int started;          // actividades do Seeky visíveis

    static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new AppLock());
    }

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
    static int timeout(Context c) {
        int value = prefs(c).getInt("timeout", 60);
        for (int t : TIMEOUTS) if (t == value) return t;
        return 60;
    }

    static boolean deviceSecure(Context c) {
        KeyguardManager keyguard = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    /** Verdadeiro quando o conteúdo do Seeky deve ficar escondido até nova autenticação. */
    static boolean required(Context c) {
        if (!enabled(c)) return false;
        if (!deviceSecure(c)) return false; // sem ecrã de bloqueio no Android não há como autenticar
        synchronized (LOCK) {
            if (unlockedAt == 0) return true;
            return started == 0 && SystemClock.elapsedRealtime() - lastActive > timeout(c) * 1000L;
        }
    }

    static void unlocked() {
        synchronized (LOCK) { unlockedAt = SystemClock.elapsedRealtime(); lastActive = unlockedAt; }
    }

    /** O painel Flash conta como uso activo (evita pedir autenticação a cada pesquisa). */
    static void touch() {
        synchronized (LOCK) { if (unlockedAt != 0) lastActive = SystemClock.elapsedRealtime(); }
    }

    /** Estado inicial de um processo novo (usado também pelos testes). */
    static void reset() { synchronized (LOCK) { unlockedAt = 0; lastActive = 0; started = 0; } }

    static void lockNow() { synchronized (LOCK) { unlockedAt = 0; } }

    static String timeoutLabel(int seconds) {
        return seconds < 60 ? seconds + " s" : seconds / 60 + " min";
    }

    // ---- Ciclo de vida das actividades ----

    @Override public void onActivityStarted(Activity a) {
        synchronized (LOCK) {
            if (started == 0 && unlockedAt != 0 && enabled(a)
                    && SystemClock.elapsedRealtime() - lastActive > timeout(a) * 1000L) unlockedAt = 0;
            started++;
        }
    }

    @Override public void onActivityResumed(Activity a) {
        boolean on = enabled(a);
        if (Build.VERSION.SDK_INT >= 33) a.setRecentsScreenshotEnabled(!on);
        if (a instanceof LockActivity || a instanceof FlashActivity || !on) return;
        if (required(a)) {
            a.startActivity(new Intent(a, LockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            a.overridePendingTransition(0, 0);
        }
    }

    @Override public void onActivityStopped(Activity a) {
        synchronized (LOCK) {
            started = Math.max(0, started - 1);
            if (started == 0 && unlockedAt != 0) lastActive = SystemClock.elapsedRealtime();
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}

    // ---- Definições ----

    static void show(Activity a) {
        boolean on = enabled(a);
        UiPanel panel = new UiPanel(a, "Bloqueio da aplicação",
                on ? "Activo · pede autenticação após " + timeoutLabel(timeout(a)) : "Desligado", "lock");
        panel.note("Usa a impressão digital, o rosto, o PIN ou o padrão do Android. O Seeky não guarda nenhum código.");
        if (!deviceSecure(a)) panel.note("Este telefone não tem bloqueio de ecrã. Defina primeiro um PIN, padrão ou impressão digital nas definições do Android.");
        if (on) {
            panel.action("lock", "Desligar bloqueio", null, false, () -> {
                prefs(a).edit().putBoolean("enabled", false).apply();
                if (Build.VERSION.SDK_INT >= 33) a.setRecentsScreenshotEnabled(true);
                Toast.makeText(a, AppLanguage.text(a, "Bloqueio desligado."), Toast.LENGTH_SHORT).show();
                show(a);
            });
            panel.action("clock", "Pedir autenticação após", timeoutLabel(timeout(a)) + " " + AppLanguage.text(a, "em segundo plano"), false, () -> chooseTimeout(a));
            panel.action("power", "Bloquear agora", "O Seeky fica bloqueado até nova autenticação", false, () -> {
                lockNow();
                a.startActivity(new Intent(a, LockActivity.class));
            });
        } else {
            panel.action("lock", "Activar bloqueio", "Confirme a sua identidade para activar", false, () -> {
                if (!deviceSecure(a)) {
                    Toast.makeText(a, AppLanguage.text(a, "Defina primeiro um bloqueio de ecrã no Android."), Toast.LENGTH_LONG).show();
                    return;
                }
                a.startActivity(new Intent(a, LockActivity.class).putExtra(LockActivity.ENABLE, true));
            });
        }
        panel.show();
    }

    static void chooseTimeout(Activity a) {
        String[] labels = new String[TIMEOUTS.length];
        int current = 0;
        for (int i = 0; i < TIMEOUTS.length; i++) {
            labels[i] = timeoutLabel(TIMEOUTS[i]);
            if (TIMEOUTS[i] == timeout(a)) current = i;
        }
        new UiDialog.Builder(a).setTitle("Pedir autenticação após")
                .setSingleChoiceItems(labels, current, (d, n) -> {
                    prefs(a).edit().putInt("timeout", TIMEOUTS[n]).apply();
                    d.dismiss();
                    show(a);
                }).setNegativeButton("Fechar", null).show();
    }
}
