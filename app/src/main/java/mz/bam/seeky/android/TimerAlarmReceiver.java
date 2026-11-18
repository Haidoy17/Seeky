package mz.bam.seeky.android;
import android.content.*;
public final class TimerAlarmReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(!FocusSessions.prefs(c).getBoolean("timer_active",false)||!FocusSessions.matches(FocusSessions.prefs(c),i.getStringExtra("token")))return;try{Intent check=new Intent(c,StudyTimerService.class).setAction(StudyTimerService.CHECK).putExtra("token",i.getStringExtra("token"));if(StudyTimerService.running)c.startService(check);else c.startForegroundService(check);}catch(Exception e){AppDiagnostics.record(c,"timer-wakeup",e);}}
}
