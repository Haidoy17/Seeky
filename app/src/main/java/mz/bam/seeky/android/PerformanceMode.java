package mz.bam.seeky.android;
import android.app.ActivityManager;import android.content.*;
/** Conservative defaults on 128 MB heaps; users can explicitly choose a mode. */
final class PerformanceMode {
 static boolean automatic(boolean lowRam,int memoryClass,long heap){return lowRam||memoryClass<=128||heap<=134217728L;}
 static boolean light(Context c){int mode=c.getSharedPreferences("seeky",0).getInt("performance_mode",0);if(mode!=0)return mode==1;ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);return automatic(am!=null&&am.isLowRamDevice(),am==null?128:am.getMemoryClass(),Runtime.getRuntime().maxMemory());}
 static int thumbnailCache(Context c){return ImageBudget.cache(Runtime.getRuntime().maxMemory(),light(c)?64:32,light(c)?1572864:3145728);}
 static int pdfCache(Context c){return ImageBudget.cache(Runtime.getRuntime().maxMemory(),light(c)?32:16,light(c)?3145728:6291456);}
 static void choose(Context c,int mode){if(mode<0||mode>2)throw new IllegalArgumentException();SharedPreferences p=c.getSharedPreferences("seeky",0);p.edit().putInt("performance_mode",mode).apply();if(light(c))p.edit().putBoolean("motion",false).putBoolean("compact",true).apply();}
 private PerformanceMode(){}
}
