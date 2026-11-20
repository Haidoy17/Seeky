package mz.bam.seeky.android;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

/** Confirmação com impressão digital, rosto, PIN ou padrão do telefone (pasta privada). */
final class Auth {
    static final int REQUEST = 7130;
    private static Runnable pending;

    private Auth() {}

    static void confirm(Activity a, String title, Runnable ok) {
        String t = AppLanguage.text(a, title);
        if (Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(a).setTitle(t);
            if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else b.setDeviceCredentialAllowed(true);
            try {
                b.build().authenticate(new CancellationSignal(), a.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { AppLock.unlocked(); ok.run(); }
                    @Override public void onAuthenticationError(int code, CharSequence message) {
                        if (message != null && code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && code != BiometricPrompt.BIOMETRIC_ERROR_CANCELED)
                            PlusUi.toast(a, message.toString());
                    }
                });
                return;
            } catch (RuntimeException ignored) {}
        }
        KeyguardManager k = a.getSystemService(KeyguardManager.class);
        @SuppressWarnings("deprecation") Intent i = k == null ? null : k.createConfirmDeviceCredentialIntent(t, null);
        if (i == null) { PlusUi.toast(a, "Não foi possível confirmar a sua identidade."); return; }
        pending = ok;
        try { a.startActivityForResult(i, REQUEST); } catch (Exception e) { pending = null; }
    }

    static boolean result(int request, int result) {
        if (request != REQUEST) return false;
        Runnable ok = pending;
        pending = null;
        if (result == Activity.RESULT_OK && ok != null) { AppLock.unlocked(); ok.run(); }
        return true;
    }
}
