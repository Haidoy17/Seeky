package mz.bam.seeky.android;

import android.content.*;
import android.net.Uri;
import android.widget.Toast;

/** Use the installed Android Google engine, without a cloud API key or external reader. */
final class GoogleVoiceSetup {
    static final String GOOGLE="com.google.android.tts";
    /** Motores retirados: RHVoice e o antigo módulo Seeky Vozes Naturais (5.19.0). */
    static final String LEGACY_NATURAL="mz.bam.seeky.vozes";
    static boolean allowed(String engine){return !"com.github.olga_yakovleva.rhvoice.android".equals(engine)&&!LEGACY_NATURAL.equals(engine);}
    static void migrate(SharedPreferences prefs){if(!allowed(prefs.getString("speech_engine",GOOGLE)))prefs.edit().putString("speech_engine",GOOGLE).putString("speech_voice","").putBoolean("speech_online",true).apply();}
    static void install(ReaderActivity a){try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id="+GOOGLE)));}catch(Exception error){Toast.makeText(a,AppLanguage.text(a,"Abra as definições de texto para voz do Android."),Toast.LENGTH_LONG).show();}}
}
