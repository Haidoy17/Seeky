package mz.bam.seeky.android;
import android.content.*;
/** Bounded timings and high-water memory only; no names, paths, text or credentials. */
final class OperationMetrics {
    static synchronized void record(Context c,String operation,long milliseconds){if(!operation.matches("[a-z_-]{1,30}"))return;Runtime r=Runtime.getRuntime();long used=(r.totalMemory()-r.freeMemory())/1048576;android.content.SharedPreferences p=c.getSharedPreferences("operation_metrics",0);p.edit().putLong(operation+"_last_ms",Math.max(0,milliseconds)).putLong(operation+"_max_ms",Math.max(milliseconds,p.getLong(operation+"_max_ms",0))).putLong("heap_high_mb",Math.max(used,p.getLong("heap_high_mb",0))).apply();}
    private OperationMetrics(){}
}
