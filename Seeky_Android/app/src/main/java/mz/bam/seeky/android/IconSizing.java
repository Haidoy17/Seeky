package mz.bam.seeky.android;
import android.content.Context;import android.content.SharedPreferences;
final class IconSizing {
 static int bounded(int size){return Math.max(0,Math.min(2,size));}
 static int icons(Context c){return bounded(c.getSharedPreferences("seeky",0).getInt("icon_size",1));}
 static int bubble(Context c){return bubbleBound(c.getSharedPreferences("seeky",0).getInt("flash_button_size",1));}
 static int glyph(int step){return new int[]{20,25,32}[bounded(step)];}
 static int bubbleBound(int step){return Math.max(-3,Math.min(2,step));}
 static int bubbleVisual(int step){return new int[]{16,24,32,44,52,64}[bubbleBound(step)+3];}
 static int bubbleTouch(int step){return Math.max(44,bubbleVisual(step)+8);}
 static void set(Context c,String key,int step){if(!key.equals("icon_size")&&!key.equals("flash_button_size"))throw new IllegalArgumentException("Tamanho inválido.");c.getSharedPreferences("seeky",0).edit().putInt(key,key.equals("flash_button_size")?bubbleBound(step):bounded(step)).apply();}
 private IconSizing(){}
}
