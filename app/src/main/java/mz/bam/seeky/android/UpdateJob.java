package mz.bam.seeky.android;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Procura actualizações em segundo plano (a cada 6 horas, só com rede) e avisa com uma
 * notificação quando há uma versão nova. Cada versão é anunciada uma só vez.
 */
public final class UpdateJob extends JobService {
    static final int ID = 7711, NOTIFICATION = 530;
    static final long PERIOD = 6 * 3600 * 1000L;
    private Thread worker;

    static void schedule(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js == null) return;
            if (!AppUpdates.notifications(c)) { js.cancel(ID); return; }
            if (js.getPendingJob(ID) != null) return;
            js.schedule(new JobInfo.Builder(ID, new ComponentName(c, UpdateJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(PERIOD).setPersisted(true).build());
        } catch (Exception e) { AppDiagnostics.record(c, "update-job", e); }
    }

    /** Verifica e notifica; devolve true se anunciou uma versão nova. */
    static boolean checkAndNotify(Context c) throws Exception {
        if (!AppUpdates.notifications(c)) return false;
        long installed = AppUpdates.installedVersion(c);
        if (installed <= 0) return false;
        AppUpdates.Release r = AppUpdates.fetch();
        AppUpdates.prefs(c).edit().putLong("last_background_check", System.currentTimeMillis()).apply();
        if (r.versionCode <= installed || AppUpdates.prefs(c).getLong("notified_code", 0) == r.versionCode) return false;
        AppUpdates.prefs(c).edit().putLong("notified_code", r.versionCode).apply();
        String text = "Seeky " + r.versionName + " " + AppLanguage.text(c, "está disponível. Toque para actualizar.") + (r.notes.isEmpty() ? "" : "\n\n" + r.notes);
        Notify.show(c, NOTIFICATION, "Nova versão do Seeky", text, new Intent(c, MainActivity.class).putExtra(SeekyWidget.EXTRA, "update"));
        return true;
    }

    @Override public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            try { checkAndNotify(this); } catch (Throwable e) { AppDiagnostics.record(this, "update-check", e); }
            jobFinished(params, false);
        }, "seeky-update-job");
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (worker != null) worker.interrupt();
        return true;
    }
}
