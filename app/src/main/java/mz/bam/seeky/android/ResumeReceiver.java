package mz.bam.seeky.android;
import android.content.*;

/** Respects force-stop/battery restrictions; no alarm-based restart loop. */
public final class ResumeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){try{SeekyAlarms.reschedule(context);}catch(Exception e){AppDiagnostics.record(context,"alarms-boot",e);}
        if(context.getSharedPreferences("studies",0).getBoolean("timer_active",false))StudyTimerService.command(context,StudyTimerService.CHECK);
        SharedPreferences p=context.getSharedPreferences("seeky",0);
        boolean watch=p.getBoolean("monitor_enabled",false)&&StorageAccess.all(context);boolean bubble=p.getBoolean("float_enabled",false)&&android.provider.Settings.canDrawOverlays(context);if(!watch&&!bubble)return;
        try{context.startForegroundService(new Intent(context,QuickSearchService.class).setAction(QuickSearchService.MONITOR));}
        catch(Exception e){p.edit().putString("service_warning","Abra o Seeky para retomar: "+e.getClass().getSimpleName()).apply();}
    }
}
