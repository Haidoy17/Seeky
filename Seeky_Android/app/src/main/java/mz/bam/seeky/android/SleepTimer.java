package mz.bam.seeky.android;
import android.app.*;import android.content.*;import android.os.*;import java.util.*;
/** Shared sleep deadline: foreground audio owners register and release their callbacks. */
final class SleepTimer {
 static final Handler MAIN=new Handler(Looper.getMainLooper());static final Map<Context,Runnable> OWNERS=new IdentityHashMap<>(),REFRESH=new IdentityHashMap<>();static Context app;static long fired;
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("sleep_timer",0);}
 static long remaining(Context c){SharedPreferences p=prefs(c);if(p.getInt("mode",0)!=1)return -1;long now=SystemClock.elapsedRealtime(),created=p.getLong("elapsed",0),deadline=p.getLong("deadline",0);return Math.max(0,now>=created?deadline-now:p.getLong("wall",0)-System.currentTimeMillis());}
 static void watch(Context c,Runnable callback){app=c.getApplicationContext();OWNERS.put(c,callback);schedule();}
 static void watch(Context c,Runnable callback,Runnable refresh){watch(c,callback);REFRESH.put(c,refresh);refresh.run();}
 static void unwatch(Context c){OWNERS.remove(c);REFRESH.remove(c);schedule();}
 static void refresh(){for(Runnable callback:new ArrayList<>(REFRESH.values()))callback.run();}
 static void schedule(){MAIN.removeCallbacks(TICK);if(app!=null&&!OWNERS.isEmpty()&&prefs(app).getInt("mode",0)==1)MAIN.postDelayed(TICK,Math.min(1000,Math.max(0,remaining(app))));}
 static final Runnable TICK=()->{if(app!=null&&remaining(app)==0)fire(app);else schedule();};
 static void arm(Context c,int minutes){if(minutes<1||minutes>240)throw new IllegalArgumentException();long duration=minutes*60000L;prefs(c).edit().putInt("mode",1).putLong("elapsed",SystemClock.elapsedRealtime()).putLong("deadline",SystemClock.elapsedRealtime()+duration).putLong("wall",System.currentTimeMillis()+duration).apply();app=c.getApplicationContext();refresh();schedule();}
 static void end(Context c,String uri,int part,long mediaEnd){prefs(c).edit().putInt("mode",2).putString("uri",uri).putInt("part",part).putLong("media_end",mediaEnd).apply();app=c.getApplicationContext();refresh();schedule();}
 static void cancel(Context c){prefs(c).edit().clear().apply();refresh();schedule();}
 static void fire(Context c){fired++;cancel(c);for(Runnable r:new ArrayList<>(OWNERS.values()))r.run();BackgroundAudioService.stop(c);}
 static boolean endsAt(Context c,String uri,int part){SharedPreferences p=prefs(c);return p.getInt("mode",0)==2&&p.getString("uri","").equals(uri)&&p.getInt("part",-1)==part;}
 static boolean boundary(Context c,String uri,int part){if(endsAt(c,uri,part)){fire(c);return true;}return false;}
 static void media(Context c,String uri,long position){SharedPreferences p=prefs(c);if(p.getInt("mode",0)==2&&p.getString("uri","").equals(uri)&&p.getLong("media_end",-1)>0&&position>=p.getLong("media_end",Long.MAX_VALUE))fire(c);}
 static String label(Context c){int mode=prefs(c).getInt("mode",0);return mode==0?"Desligado":mode==2?"Fim do capítulo":Math.max(0,(remaining(c)+59999)/60000)+" min";}
 static void show(Activity a,String uri,int part,long mediaEnd){new UiDialog.Builder(a).setTitle("Temporizador de sono · "+label(a)).setItems(new String[]{"Desligar","15 minutos","30 minutos","45 minutos","60 minutos","90 minutos","Fim do capítulo","Outro tempo"},(d,n)->{if(n==0)cancel(a);else if(n<=5)arm(a,new int[]{0,15,30,45,60,90}[n]);else if(n==6)end(a,uri,part,mediaEnd);else{android.widget.EditText input=PlusUi.input(a,"Minutos (1 a 240)","20",3);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);new UiDialog.Builder(a).setTitle("Temporizador de sono").setView(input).setPositiveButton("Activar",(x,w)->{try{arm(a,Integer.parseInt(input.getText().toString()));}catch(Exception e){PlusUi.toast(a,"Use de 1 a 240 minutos.");}}).setNegativeButton("Cancelar",null).show();}}).setNegativeButton("Fechar",null).show();}
 private SleepTimer(){}
}
