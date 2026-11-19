package mz.bam.seeky.android;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executa {@link AutoBackup#run} numa thread de fundo, com cancelamento quando o sistema pede. */
public final class AutoBackupJob extends JobService {
    private final AtomicBoolean stopped = new AtomicBoolean();
    private Thread worker;

    @Override public boolean onStartJob(JobParameters params) {
        stopped.set(false);
        boolean manual = params.getExtras() != null && params.getExtras().getBoolean("manual", false);
        worker = new Thread(() -> {
            boolean retry = false;
            try {
                String summary = AutoBackup.run(this, stopped::get);
                if (manual) AutoBackup.notify(this, true, summary);
            } catch (Throwable e) {
                if (stopped.get()) retry = true;
                else {
                    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    AutoBackup.prefs(this).edit().putString("last_error", message).apply();
                    AppDiagnostics.record(this, "auto-backup", e);
                    AutoBackup.notify(this, false, message);
                }
            }
            if (!stopped.get()) jobFinished(params, retry);
        }, "seeky-auto-backup");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped.set(true);
        if (worker != null) worker.interrupt();
        return true; // tentar mais tarde
    }
}
