package mz.bam.seeky.android;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.*;

/**
 * Mosaico "Seeky Flash" das Definições Rápidas, como interruptor real:
 * activo quando o botão Flash está visível; um toque esconde-o, outro volta a mostrá-lo
 * (passando pelas autorizações em falta). Nenhuma actividade é lançada em ciclo.
 */
public final class FlashTileService extends TileService {
 static boolean visible(Context c){
  return QuickSearchService.running&&c.getSharedPreferences("seeky",0).getBoolean("float_enabled",false)&&Settings.canDrawOverlays(c);
 }

 /** Pede ao sistema que actualize o mosaico depois de o botão mudar de estado. */
 static void refresh(Context c){
  try{TileService.requestListeningState(c,new ComponentName(c,FlashTileService.class));}catch(RuntimeException ignored){}
 }

 public void onStartListening(){super.onStartListening();update();}

 void update(){
  Tile t=getQsTile();
  if(t==null)return;
  boolean on=visible(this);
  t.setLabel("Seeky Flash");
  t.setState(on?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
  if(Build.VERSION.SDK_INT>=29)t.setSubtitle(AppLanguage.text(this,on?"Botão visível":"Desligado"));
  t.setContentDescription(AppLanguage.text(this,on?"Seeky Flash activo. Toque para esconder o botão.":"Seeky Flash desligado. Toque para mostrar o botão de pesquisa."));
  t.updateTile();
 }

 public void onClick(){super.onClick();
  if(visible(this)){
   Intent stop=new Intent(this,QuickSearchService.class).setAction(QuickSearchService.STOP);
   try{startService(stop);}catch(RuntimeException e){
    try{startForegroundService(stop);}catch(RuntimeException again){AppDiagnostics.record(this,"flash-tile-stop",again);}
   }
   getSharedPreferences("seeky",0).edit().putBoolean("float_enabled",false).apply();
   update();
   return;
  }
  Intent i=new Intent(this,FlashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,901,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));
  else startActivityAndCollapse(i);
 }
}
