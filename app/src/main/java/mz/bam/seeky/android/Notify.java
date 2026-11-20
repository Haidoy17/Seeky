package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

/** Notificações dos lembretes (meta de leitura, provas e trabalhos, Pomodoro) e os alarmes que as disparam. */
final class Notify {
    static final String CHANNEL = "seeky_reminders";

    private Notify() {}

    static void channel(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m != null) m.createNotificationChannel(new NotificationChannel(CHANNEL, AppLanguage.text(c, "Lembretes do Seeky"), NotificationManager.IMPORTANCE_DEFAULT));
    }

    static void show(Context c, int id, String title, String text, Intent open) {
        try {
            channel(c);
            PendingIntent click = PendingIntent.getActivity(c, id, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_seeky_notification)
                    .setContentTitle(AppLanguage.text(c, title)).setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true).setContentIntent(click).build();
            NotificationManager m = c.getSystemService(NotificationManager.class);
            if (m != null) m.notify(id, n);
        } catch (Exception e) { AppDiagnostics.record(c, "notify", e); }
    }

    /** Android 13+: pede a autorização de notificações quando o utilizador activa um lembrete. */
    static void ask(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            try { a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 77); } catch (Exception ignored) {}
    }

    static PendingIntent alarm(Context c, String action, int request, long id) {
        Intent i = new Intent(c, SeekyAlarms.class).setAction(action).putExtra("id", id);
        return PendingIntent.getBroadcast(c, request, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Alarme aproximado: dispensa a autorização de alarmes exactos e respeita a bateria. */
    static void at(Context c, long when, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); } catch (Exception e) { AppDiagnostics.record(c, "alarm", e); }
    }

    static void cancel(Context c, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pi);
    }
}
