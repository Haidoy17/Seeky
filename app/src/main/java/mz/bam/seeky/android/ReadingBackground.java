package mz.bam.seeky.android;

import android.content.*;import android.media.*;import java.io.*;

/** A second player owns only the background, with its own gain and no system volume changes. */
final class ReadingBackground implements SharedPreferences.OnSharedPreferenceChangeListener {
 final boolean preview;final Context context;final SharedPreferences prefs;MediaPlayer player;String path="";boolean playing,ready,unavailable;Runnable changed=()->{};
 ReadingBackground(Context c){this(c,false);}
 ReadingBackground(Context c,boolean preview){this.preview=preview;context=c;prefs=c.getSharedPreferences("reading_background",0);prefs.registerOnSharedPreferenceChangeListener(this);}
 void play(){if(!playing)unavailable=false;playing=true;update();}
 void pause(){playing=false;if(player!=null)try{player.pause();}catch(Exception ignored){}}
 void update(){String next=(preview||prefs.getBoolean("enabled",false))?prefs.getString("path",""):"";if(!next.equals(path)){release();unavailable=false;path=next;}if(!playing||next.isEmpty()||unavailable)return;try{if(player==null){File file=new File(next);if(!DiskWalker.inside(new File(context.getFilesDir(),"backgrounds").getCanonicalFile(),file.getCanonicalFile()))return;player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());player.setWakeMode(context,android.os.PowerManager.PARTIAL_WAKE_LOCK);player.setDataSource(file.getPath());player.setLooping(true);player.setOnErrorListener((failed,what,extra)->{if(failed==player){unavailable=true;release();changed.run();AppDiagnostics.record(context,"background-playback",new IOException("MediaPlayer "+what+"/"+extra));try{android.widget.Toast.makeText(context,AppLanguage.text(context,"Este fundo não é compatível com o aparelho. Escolha outro áudio."),android.widget.Toast.LENGTH_LONG).show();}catch(Exception ignored){}}return true;});player.setOnPreparedListener(prepared->{if(prepared==player){ready=true;update();changed.run();}});player.prepareAsync();return;}if(!ready)return;float volume=Math.max(0,Math.min(100,prefs.getInt("volume",15)))/100f;player.setVolume(volume,volume);if(!player.isPlaying())player.start();}catch(Exception error){unavailable=true;release();changed.run();AppDiagnostics.record(context,"reading-background",error);}}
 public void onSharedPreferenceChanged(SharedPreferences p,String key){update();}
 void release(){ready=false;if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
 void close(){prefs.unregisterOnSharedPreferenceChangeListener(this);release();}
}
