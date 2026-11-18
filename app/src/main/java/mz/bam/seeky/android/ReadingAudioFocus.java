package mz.bam.seeky.android;

import android.content.Context;import android.media.*;import java.util.*;

/** Voice and ambient audio share one focus request, so they never pause each other. Main thread only. */
final class ReadingAudioFocus {
 static ReadingAudioFocus instance;final Context context;final AudioManager manager;final AudioFocusRequest request;
 final Map<Object,Runnable> owners=new IdentityHashMap<>();boolean granted;
 static ReadingAudioFocus get(Context c){Context app=c.getApplicationContext();if(instance==null||instance.context!=app)instance=new ReadingAudioFocus(app);return instance;}
 ReadingAudioFocus(Context c){context=c;manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{if(change<0){granted=false;List<Runnable> callbacks=new ArrayList<>(owners.values());owners.clear();for(Runnable callback:callbacks)callback.run();}}).build();}
 boolean acquire(Object owner,Runnable loss){if(!granted){if(manager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return false;granted=true;}owners.put(owner,loss);return true;}
 void release(Object owner){owners.remove(owner);if(owners.isEmpty()&&granted){granted=false;manager.abandonAudioFocusRequest(request);}}
}
