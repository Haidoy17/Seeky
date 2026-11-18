package mz.bam.seeky.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/** Local, bounded diagnostics: no paths, document text, tokens or exception messages. */
final class AppDiagnostics {
    static synchronized void record(Context context, String operation, Throwable error) {
        String safe = operation.replaceAll("[^a-zA-Z0-9_-]", "");
        String line = System.currentTimeMillis()+" · "+safe+" · "+DiagnosticTrace.describe(error);
        Log.w("Seeky", line);
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("diagnostics",0);
        String previous = prefs.getString("events", "");
        String[] lines = previous.split("\n");
        StringBuilder text = new StringBuilder(line);
        for (int i=0;i<Math.min(49,lines.length);i++) if(!lines[i].isEmpty()) text.append('\n').append(lines[i]);
        if(operation.equals("uncaught"))prefs.edit().putString("events",text.toString()).commit();else prefs.edit().putString("events",text.toString()).apply();
    }
    static void readerStage(Context context,String stage){Runtime runtime=Runtime.getRuntime();long used=(runtime.totalMemory()-runtime.freeMemory())/1048576;context.getSharedPreferences("diagnostics",0).edit().putString("reader_stage",stage+" · heap "+used+" MB").apply();}
    static String report(Context context) {
        String version=MainActivity.VERSION;long code=5002800;try{android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);if(info.versionName!=null)version=info.versionName;code=android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}catch(Exception ignored){}return "Seeky "+version+" ("+code+") · diagnóstico local\nAndroid API "+android.os.Build.VERSION.SDK_INT+
            "\nModelo: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nHeap máximo: "+Runtime.getRuntime().maxMemory()/1048576+" MB\nLeitor: "+context.getSharedPreferences("diagnostics",0).getString("reader_stage","sem abertura registada")+"\nMemória máxima observada: "+context.getSharedPreferences("operation_metrics",0).getLong("heap_high_mb",0)+" MB\nÚltima tarefa: "+context.getSharedPreferences("operation_metrics",0).getLong("work_last_ms",0)+" ms\nEventos recentes\n"+
            context.getSharedPreferences("diagnostics",0).getString("events","Sem erros registados.");
    }
    private AppDiagnostics() {}
}
