package mz.bam.seeky.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe os alarmes dos lembretes; não exportado, só o próprio Seeky o agenda. */
public final class SeekyAlarms extends BroadcastReceiver {
    static final String READING = "mz.bam.seeky.alarm.READING", AGENDA = "mz.bam.seeky.alarm.AGENDA";

    @Override public void onReceive(Context c, Intent i) {
        String action = i == null ? null : i.getAction();
        try {
            if (READING.equals(action)) ReadingStats.remind(c);
            else if (AGENDA.equals(action)) StudyAgenda.fire(c, i.getLongExtra("id", 0));
        } catch (Exception e) { AppDiagnostics.record(c, "alarm-receive", e); }
    }

    /** Volta a agendar tudo (arranque do telefone, actualização do Seeky, abertura da aplicação). */
    static void reschedule(Context c) {
        try { ReadingStats.schedule(c); } catch (Exception e) { AppDiagnostics.record(c, "reading-schedule", e); }
        try { StudyAgenda.scheduleAll(c); } catch (Exception e) { AppDiagnostics.record(c, "agenda-schedule", e); }
    }
}
