package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;

/** Optional local engine, installed separately so the reader stays small. */
public final class NaturalVoicesActivity extends WorkspaceActivity {
    static final String ENGINE="mz.bam.seeky.vozes";
    static void open(Context context){context.startActivity(new Intent(context,NaturalVoicesActivity.class));}
    String title(){return "Baixar vozes naturais";}
    void load(){content.removeAllViews();note("Tugão · Faber · Jeff · Alba · DaveFX · Siwis · LJ Speech");
        if(installed()){
            action("Abrir descargas e ouvir amostras",this::launch);
            action("Usar Seeky Vozes Naturais",()->{getSharedPreferences("seeky",0).edit().putString("speech_engine",ENGINE).putString("speech_voice","").putBoolean("speech_online",false).apply();PlusUi.toast(this,"Motor seleccionado. Escolha e ouça uma voz no leitor.");});
            action("Detectar motores TTS",()->VoiceEnginesActivity.open(this));
        }else{
            note("Instale o APK Seeky Vozes Naturais fornecido com esta versão. Depois baixe apenas as vozes que pretende usar, cerca de 63 MB por voz.");
            action("Instalar módulo a partir do APK",()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(ApkInstallActivity.MIME),94);}catch(Exception e){PlusUi.error(this,e);}});
        }
        note("Sem chave de API. As vozes funcionam offline após a descarga. O primeiro arranque pode demorar em telefones modestos.");
        note("A narração continua a ser guardada em WAV.");
    }
    boolean installed(){try{getPackageManager().getApplicationInfo(ENGINE,0);return true;}catch(Exception e){return false;}}
    void launch(){try{startActivity(new Intent().setClassName(ENGINE,"mz.bam.seeky.voices.MainActivity"));}catch(Exception e){PlusUi.error(this,e);}}
    protected void onResume(){super.onResume();load();}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==94&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null)ApkInstallActivity.open(this,data.getData(),"Seeky Vozes Naturais.apk");}
}
