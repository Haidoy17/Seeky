package mz.bam.seeky.android;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ecrã opaco que cobre o Seeky até a autenticação do Android ser confirmada.
 * Também confirma a identidade antes de activar o bloqueio ({@link #ENABLE}) e,
 * a pedido do botão Flash ({@link #FLASH}), reabre o painel de pesquisa depois.
 */
public final class LockActivity extends Activity {
    static final String ENABLE = "mz.bam.seeky.lock.enable", FLASH = "mz.bam.seeky.lock.flash";
    private static final int CREDENTIAL = 71;
    private CancellationSignal cancel;
    private TextView message;
    private boolean asking;

    @Override protected void attachBaseContext(android.content.Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        AppPalette p = AppPalette.of(this);
        getWindow().setStatusBarColor(p.bg);
        getWindow().setNavigationBarColor(p.bg);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setBackgroundColor(p.bg);
        int pad = dp(32);
        column.setPadding(pad, pad, pad, pad);

        FrameLayout badge = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(p.tint);
        circle.setStroke(dp(1), p.line);
        badge.setBackground(circle);
        badge.addView(new Icons(this, "lock", p.accent), new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        column.addView(badge, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView title = text(getIntent().getBooleanExtra(ENABLE, false) ? "Activar bloqueio" : "Seeky bloqueado", 22, p.ink);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.topMargin = dp(24);
        column.addView(title, gap);

        message = text("Confirme a sua identidade com a impressão digital, o rosto ou o PIN do telefone.", 14, p.muted);
        LinearLayout.LayoutParams small = new LinearLayout.LayoutParams(-2, -2);
        small.topMargin = dp(10);
        column.addView(message, small);

        TextView unlock = text("Desbloquear", 16, p.onAccent);
        unlock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(p.accent);
        pill.setCornerRadius(dp(28));
        unlock.setBackground(pill);
        unlock.setPadding(dp(32), dp(14), dp(32), dp(14));
        unlock.setFocusable(true);
        unlock.setOnClickListener(v -> authenticate());
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(-2, -2);
        button.topMargin = dp(32);
        column.addView(unlock, button);

        setContentView(column);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!getIntent().getBooleanExtra(ENABLE, false) && !AppLock.required(this)) { done(); return; }
        if (!asking) column().post(this::authenticate);
    }

    private View column() { return getWindow().getDecorView(); }

    private void authenticate() {
        if (asking || isFinishing()) return;
        if (!AppLock.deviceSecure(this)) {
            message.setText(AppLanguage.text(this, "Este telefone não tem bloqueio de ecrã. Defina um PIN, padrão ou impressão digital nas definições do Android."));
            if (!getIntent().getBooleanExtra(ENABLE, false)) { AppLock.unlocked(); done(); }
            return;
        }
        asking = true;
        String title = AppLanguage.text(this, "Desbloquear o Seeky");
        if (Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this).setTitle(title)
                    .setSubtitle(AppLanguage.text(this, "Os seus ficheiros, livros e estudos estão protegidos."));
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else builder.setDeviceCredentialAllowed(true);
            cancel = new CancellationSignal();
            try {
                builder.build().authenticate(cancel, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { asking = false; success(); }
                    @Override public void onAuthenticationError(int code, CharSequence error) {
                        asking = false;
                        if (error != null) message.setText(error);
                    }
                });
            } catch (RuntimeException e) { asking = false; legacy(title); }
        } else legacy(title);
    }

    /** Android 8–8.1: confirmação de credencial do ecrã de bloqueio. */
    @SuppressWarnings("deprecation")
    private void legacy(String title) {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        Intent confirm = keyguard == null ? null : keyguard.createConfirmDeviceCredentialIntent(title, null);
        if (confirm == null) { asking = false; return; }
        asking = true;
        startActivityForResult(confirm, CREDENTIAL);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CREDENTIAL) return;
        asking = false;
        if (result == RESULT_OK) success();
    }

    private void success() {
        AppLock.unlocked();
        if (getIntent().getBooleanExtra(ENABLE, false)) {
            AppLock.prefs(this).edit().putBoolean("enabled", true).apply();
            android.widget.Toast.makeText(this, AppLanguage.text(this, "Bloqueio activado."), android.widget.Toast.LENGTH_SHORT).show();
        }
        if (getIntent().getBooleanExtra(FLASH, false)) {
            try { startService(new Intent(this, QuickSearchService.class).setAction(QuickSearchService.EXPAND)); }
            catch (RuntimeException e) { AppDiagnostics.record(this, "lock-flash", e); }
        }
        done();
    }

    private void done() {
        if (cancel != null) cancel.cancel();
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    /** Voltar atrás não revela o conteúdo: o Seeky vai para segundo plano. */
    @Override public void onBackPressed() {
        if (getIntent().getBooleanExtra(ENABLE, false)) { finish(); return; }
        moveTaskToBack(true);
    }

    @Override protected void onDestroy() { if (cancel != null) cancel.cancel(); super.onDestroy(); }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(AppLanguage.text(this, value));
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
