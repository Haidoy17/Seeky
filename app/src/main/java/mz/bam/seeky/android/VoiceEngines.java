package mz.bam.seeky.android;
import android.content.*;import android.content.pm.*;import android.speech.tts.TextToSpeech;import java.util.*;

/** Discover the Android TTS service contract, including engines installed after Seeky. */
final class VoiceEngines {
    static final class Engine {final String id,label;Engine(String id,String label){this.id=id;this.label=label;}}
    static List<Engine> scan(Context c){PackageManager pm=c.getPackageManager();List<Engine> found=new ArrayList<>();Set<String> seen=new HashSet<>();for(ResolveInfo r:pm.queryIntentServices(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),0)){ServiceInfo s=r.serviceInfo;if(s==null||!s.enabled||s.packageName==null||s.packageName.isEmpty()||!GoogleVoiceSetup.allowed(s.packageName)||s.applicationInfo!=null&&!s.applicationInfo.enabled||!seen.add(s.packageName))continue;String name=s.packageName;try{CharSequence label=r.loadLabel(pm);if(label!=null&&!label.toString().trim().isEmpty())name=label.toString();}catch(RuntimeException ignored){}found.add(new Engine(s.packageName,s.packageName.equals(GoogleVoiceSetup.GOOGLE)?"Google · Speech Services":name));}found.sort((x,y)->{int a=x.id.equals(GoogleVoiceSetup.GOOGLE)?0:1,b=y.id.equals(GoogleVoiceSetup.GOOGLE)?0:1;return a!=b?Integer.compare(a,b):x.label.compareToIgnoreCase(y.label);});return found;}
    static Intent settings(){return new Intent("com.android.settings.TTS_SETTINGS");}
    static void settings(android.app.Activity a){try{a.startActivity(settings());}catch(Exception e){try{a.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception ignored){PlusUi.toast(a,"Abra as definições de texto para voz do Android.");}}}
    private VoiceEngines(){}
}
