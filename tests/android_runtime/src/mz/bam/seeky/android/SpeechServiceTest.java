package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,application=Application.class,shadows={SpeechServiceTest.ManualTts.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class SpeechServiceTest {
 @Implements(TextToSpeech.class) public static class ManualTts extends ShadowTextToSpeech {
  static final List<Integer> queues=new ArrayList<>();static final List<String> texts=new ArrayList<>();static final List<Long> silences=new ArrayList<>();static final List<String> gapIds=new ArrayList<>();static boolean silentFails;static final List<Bundle> parameters=new ArrayList<>();
  @Implementation protected int speak(CharSequence text,int queue,Bundle parameters,String id){queues.add(queue);texts.add(text.toString());ManualTts.parameters.add(new Bundle(parameters));return super.speak(text,queue,parameters,null);}
  @Implementation protected int playSilentUtterance(long duration,int queue,String id){silences.add(duration);gapIds.add(id);return silentFails?TextToSpeech.ERROR:TextToSpeech.SUCCESS;}
 }
 static void ready(ReadAloudService service){TextToSpeech current=service.tts;shadowOf(current).getOnInitListener().onInit(TextToSpeech.SUCCESS);shadowOf(Looper.getMainLooper()).idle();try{if(service.engineTask!=null)service.engineTask.get(15,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}shadowOf(Looper.getMainLooper()).idle();}
 @Test public void normalisedSpeechKeepsPositionWhenApplyingAnotherVoice()throws Exception{
  Application app=RuntimeEnvironment.getApplication();Voice local=new Voice("pt-local",new Locale("pt","PT"),400,200,false,Collections.emptySet()),network=new Voice("pt-network",new Locale("pt","PT"),500,200,true,Collections.emptySet());ShadowTextToSpeech.addVoice(local);ShadowTextToSpeech.addVoice(network);new SpeechProfile("com.google.android.tts",local.getName(),"pt",false,1).save(app.getSharedPreferences("seeky",0));
  ServiceController<ReadAloudService> controller=Robolectric.buildService(ReadAloudService.class).create();ReadAloudService service=controller.get();shadowOf(service.audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
  try{
   String source="Uma linha\ncontinua.\n\nSegundo parágrafo.";
   service.onStartCommand(new Intent().setAction(ReadAloudService.START).setDataAndType(Uri.parse("content://seeky.test/text.txt"),"text/plain").putExtra("name","Texto.txt").putExtra("selection",source),0,1);
   service.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();SpeechServiceTest.ready(service);String spoken=shadowOf(service.tts).getLastSpokenText();assertEquals("Uma linha continua.\n\nSegundo parágrafo.",spoken);String oldId=service.cursor.pending.id;service.progress.onRangeStart(oldId,spoken.indexOf("Segundo"),spoken.indexOf("Segundo")+7,0);shadowOf(Looper.getMainLooper()).idle();service.pause("Pausado");assertEquals(source.indexOf("Segundo"),service.cursor.offset);
   String token=service.token;new SpeechProfile("com.google.android.tts",network.getName(),"pt",true,1).save(app.getSharedPreferences("seeky",0));service.onStartCommand(new Intent().setAction(ReadAloudService.CONFIGURE).putExtra("token",token),0,2);SpeechServiceTest.ready(service);assertNotEquals(token,service.token);assertEquals(network.getName(),service.request.voice);assertTrue(service.cursor.paused);assertEquals(source.indexOf("Segundo"),service.cursor.offset);assertNull(shadowOf(service.tts).getLastSpokenText());service.progress.onDone(oldId);shadowOf(Looper.getMainLooper()).idle();assertEquals(source.indexOf("Segundo"),service.cursor.offset);
  }finally{controller.destroy();}
 }
 @Test public void nextUtteranceIsQueuedBeforeTheCurrentOneFinishesWithoutSkippingOnPause()throws Exception{
  Application app=RuntimeEnvironment.getApplication();Voice local=new Voice("pt-local",new Locale("pt","PT"),400,200,false,Collections.emptySet());ShadowTextToSpeech.addVoice(local);new SpeechProfile("com.google.android.tts",local.getName(),"pt",false,1).save(app.getSharedPreferences("seeky",0));ManualTts.queues.clear();ServiceController<ReadAloudService> controller=Robolectric.buildService(ReadAloudService.class).create();ReadAloudService service=controller.get();shadowOf(service.audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
  try{StringBuilder source=new StringBuilder();for(int n=0;n<250;n++)source.append("Uma frase de leitura com ritmo e pontuação. ");service.onStartCommand(new Intent().setAction(ReadAloudService.START).setDataAndType(Uri.parse("content://seeky.test/long.txt"),"text/plain").putExtra("name","Texto.txt").putExtra("selection",source.toString()),0,1);service.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();SpeechServiceTest.ready(service);SpeechCursor.Chunk first=service.cursor.pending;assertNotNull(first);assertNotNull(service.cursor.queued);service.progress.onStart(first.id);shadowOf(Looper.getMainLooper()).idle();SpeechCursor.Chunk next=service.cursor.queued;assertNotNull(next);assertEquals(first.end,next.start);assertEquals(Arrays.asList(TextToSpeech.QUEUE_FLUSH,TextToSpeech.QUEUE_ADD),ManualTts.queues);assertEquals(0,service.cursor.offset);service.progress.onStart(next.id);shadowOf(Looper.getMainLooper()).idle();service.progress.onDone(first.id);shadowOf(Looper.getMainLooper()).idle();assertSame(next,service.cursor.pending);assertEquals(3,ManualTts.queues.size());service.progress.onStart(next.id);shadowOf(Looper.getMainLooper()).idle();assertNotNull(service.cursor.queued);service.progress.onRangeStart(next.id,4,10,0);shadowOf(Looper.getMainLooper()).idle();service.pause("Pausado");int at=service.cursor.offset;assertEquals(source.indexOf("frase",next.start),at);assertNull(service.cursor.pending);assertNull(service.cursor.queued);service.progress.onDone(next.id);shadowOf(Looper.getMainLooper()).idle();assertEquals(at,service.cursor.offset);}finally{controller.destroy();}
 }
 @Test public void onlineStartupIsShortAndNextRequestIsBufferedBeforeAnyStartCallback()throws Exception{Application app=RuntimeEnvironment.getApplication();Voice network=new Voice("pt-natural-network",new Locale("pt","PT"),500,200,true,Collections.emptySet());ShadowTextToSpeech.addVoice(network);new SpeechProfile("com.google.android.tts",network.getName(),"pt",true,1).save(app.getSharedPreferences("seeky",0));ManualTts.queues.clear();ManualTts.texts.clear();ManualTts.parameters.clear();ServiceController<ReadAloudService> controller=Robolectric.buildService(ReadAloudService.class).create();ReadAloudService service=controller.get();android.net.ConnectivityManager cm=(android.net.ConnectivityManager)service.getSystemService(Context.CONNECTIVITY_SERVICE);shadowOf(cm).setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_WIFI,0,true,true));shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(),shadowOf(new android.net.NetworkCapabilities()).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET));shadowOf(service.audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);try{StringBuilder source=new StringBuilder();for(int i=0;i<200;i++)source.append("Uma frase completa para uma leitura natural. ");service.onStartCommand(new Intent().setAction(ReadAloudService.START).setDataAndType(Uri.parse("content://seeky.test/online.txt"),"text/plain").putExtra("selection",source.toString()),0,1);service.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();SpeechServiceTest.ready(service);assertEquals(Arrays.asList(TextToSpeech.QUEUE_FLUSH,TextToSpeech.QUEUE_ADD),ManualTts.queues);assertTrue(ManualTts.texts.get(0).length()<=1200);assertTrue(ManualTts.texts.get(1).length()>ManualTts.texts.get(0).length());assertEquals("true",ManualTts.parameters.get(0).getString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS));assertEquals(network.getName(),service.tts.getVoice().getName());assertNotNull(service.cursor.pending);assertNotNull(service.cursor.queued);assertEquals(0,service.cursor.offset);}finally{controller.destroy();}}

 @Test public void bibleReferencesReachBothVoiceModesAndResumeWithoutReadingTimes()throws Exception{
  Application app=RuntimeEnvironment.getApplication();
  for(boolean online:new boolean[]{false,true}){
   String voice=online?"pt-bible-online":"pt-bible-offline";ShadowTextToSpeech.addVoice(new Voice(voice,new Locale("pt","PT"),500,200,online,Collections.emptySet()));new SpeechProfile("com.google.android.tts",voice,"pt",online,1).save(app.getSharedPreferences("seeky",0));
   ManualTts.texts.clear();ManualTts.queues.clear();ManualTts.silentFails=false;
   ServiceController<ReadAloudService> controller=Robolectric.buildService(ReadAloudService.class).create();ReadAloudService service=controller.get();shadowOf(service.audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
   android.net.ConnectivityManager cm=(android.net.ConnectivityManager)service.getSystemService(Context.CONNECTIVITY_SERVICE);shadowOf(cm).setActiveNetworkInfo(org.robolectric.shadows.ShadowNetworkInfo.newInstance(android.net.NetworkInfo.DetailedState.CONNECTED,android.net.ConnectivityManager.TYPE_WIFI,0,true,true));shadowOf(cm).setNetworkCapabilities(cm.getActiveNetwork(),shadowOf(new android.net.NetworkCapabilities()).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET));
   try{
    String source="Leia Isaías 5:6, depois confirme João 3:16.";
    service.onStartCommand(new Intent().setAction(ReadAloudService.START).setDataAndType(Uri.parse("content://seeky.test/bible.txt"),"text/plain").putExtra("selection",source),0,1);service.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();SpeechServiceTest.ready(service);
    String sent=ManualTts.texts.get(0);assertEquals("Leia Isaías capítulo 5, versículo 6, depois confirme João capítulo 3, versículo 16.",sent);assertEquals(source,service.cursor.text);
    service.progress.onRangeStart(service.cursor.pending.id,sent.indexOf("versículo"),sent.indexOf("versículo")+9,0);shadowOf(Looper.getMainLooper()).idle();service.pause("Pausado");assertEquals(source.indexOf("Isaías"),service.cursor.offset);service.resume();assertTrue(ManualTts.texts.get(ManualTts.texts.size()-1).startsWith("Isaías capítulo 5, versículo 6"));
   }finally{controller.destroy();}
  }
 }
}
