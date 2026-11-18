package mz.bam.seeky.android;
import android.content.*;import android.content.res.Configuration;import java.util.Locale;
final class AppLanguage {
 static boolean english(Context c){return c.getSharedPreferences("seeky",0).getString("language","pt").equals("en");}
 static String text(Context c,String value){return value==null?"":english(c)?Translations.english(value):value;}
 static CharSequence text(Context c,CharSequence value){return value==null?null:text(c,value.toString());}
 static CharSequence[] items(Context c,CharSequence[] values){if(values==null)return null;CharSequence[] a=new CharSequence[values.length];for(int i=0;i<a.length;i++)a[i]=text(c,values[i]);return a;}
 static Context wrap(Context c){Configuration cfg=new Configuration(c.getResources().getConfiguration());cfg.setLocale(english(c)?Locale.ENGLISH:new Locale("pt","MZ"));return c.createConfigurationContext(cfg);}
}
