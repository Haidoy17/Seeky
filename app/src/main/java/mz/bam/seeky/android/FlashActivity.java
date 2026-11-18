package mz.bam.seeky.android;
import android.app.Activity;import android.content.*;import android.net.Uri;import android.os.*;import android.provider.Settings;import android.Manifest;
/** Permission entry for the overlay, with an independent search window on restricted devices. */
public final class FlashActivity extends Activity {
 boolean overlayAsked,storageAsked;
 protected void onCreate(Bundle b){super.onCreate(b);if(b!=null){overlayAsked=b.getBoolean("overlay");storageAsked=b.getBoolean("storage");}}
 protected void onResume(){super.onResume();proceed();}
 void proceed(){if(isFinishing())return;
  if(!StorageAccess.all(this)&&!storageAsked){storageAsked=true;try{if(Build.VERSION.SDK_INT>=30)startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},9);}catch(Exception e){fallback();}return;}
  if(!Settings.canDrawOverlays(this)&&!overlayAsked){overlayAsked=true;try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){fallback();}return;}
  if(!Settings.canDrawOverlays(this)){fallback();return;}launch(true);
 }
 void launch(boolean overlay){android.content.SharedPreferences.Editor p=getSharedPreferences("seeky",0).edit().putBoolean("monitor_initialized",true);if(overlay)p.putBoolean("float_enabled",true);if(StorageAccess.all(this))p.putBoolean("monitor_enabled",true);p.apply();try{if(overlay||StorageAccess.all(this))startForegroundService(new Intent(this,QuickSearchService.class).setAction(overlay?QuickSearchService.SHOW:QuickSearchService.MONITOR));}catch(Exception e){AppDiagnostics.record(this,"flash-start",e);}finish();}
 void fallback(){startActivity(new Intent(this,FlashSearchActivity.class));launch(false);}
 public void onRequestPermissionsResult(int code,String[] names,int[] results){super.onRequestPermissionsResult(code,names,results);if(code==9)proceed();}
 protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putBoolean("overlay",overlayAsked);b.putBoolean("storage",storageAsked);}
}
