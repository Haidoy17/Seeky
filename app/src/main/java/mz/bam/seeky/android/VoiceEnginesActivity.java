package mz.bam.seeky.android;
import android.content.*;import android.os.*;import android.speech.tts.TextToSpeech;import java.util.*;

public final class VoiceEnginesActivity extends WorkspaceActivity {
    boolean registered;
    static void open(Context c){c.startActivity(new Intent(c,VoiceEnginesActivity.class));}
    String title(){return "Motores TTS instalados";}
    final BroadcastReceiver packages=new BroadcastReceiver(){public void onReceive(Context c,Intent i){load();}};
    void load(){content.removeAllViews();List<VoiceEngines.Engine> found=VoiceEngines.scan(this);String selected=getSharedPreferences("seeky",0).getString("speech_engine",GoogleVoiceSetup.GOOGLE);action("Detectar novos motores TTS",this::load);action("Baixar vozes naturais",()->NaturalVoicesActivity.open(this));action("Definições de voz do Android",()->VoiceEngines.settings(this));note(found.size()+" "+AppLanguage.text(this,"motores detectados"));for(VoiceEngines.Engine e:found)row("speaker",e.label,(e.id.equals(selected)?AppLanguage.text(this,"Seleccionado")+" · ":"")+e.id,()->choose(e));if(found.isEmpty())note("Nenhum motor de voz instalado");}
    void choose(VoiceEngines.Engine e){UiPanel p=new UiPanel(this,e.label,e.id,"speaker");p.action("check","Usar este motor",null,false,()->{getSharedPreferences("seeky",0).edit().putString("speech_engine",e.id).putString("speech_voice","").putBoolean("speech_online",false).apply();PlusUi.toast(this,"Motor seleccionado. Escolha e ouça uma voz no leitor.");load();});p.action("download","Baixar vozes offline",null,false,()->{try{startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(e.id));}catch(Exception error){VoiceEngines.settings(this);}});p.action("settings","Definições de voz do Android",null,false,()->VoiceEngines.settings(this));p.show();}
    protected void onStart(){super.onStart();IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");if(Build.VERSION.SDK_INT>=33)registerReceiver(packages,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(packages,filter);registered=true;load();}
    protected void onStop(){if(registered){unregisterReceiver(packages);registered=false;}super.onStop();}
    protected void onResume(){super.onResume();load();}
}
