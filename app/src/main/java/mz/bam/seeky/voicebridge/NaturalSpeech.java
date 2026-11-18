package mz.bam.seeky.voicebridge;

import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Public, signature-protected IPC to the optional companion. No hidden Android APIs. */
public class NaturalSpeech extends TextToSpeech {
 public static final String ENGINE="mz.bam.seeky.vozes",SERVICE="mz.bam.seeky.voices.NaturalBridgeService";
 public static final int HELLO=1,SYNTH=2,CANCEL=3,BYE=4,CATALOG=101,RANGE=103,DONE=104,FAILED=105;
 static final ExecutorService CONTROL=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"seeky-natural-control");t.setDaemon(true);return t;});
 final Context context;final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService work=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"seeky-natural-audio");t.setDaemon(true);return t;});
 final AtomicInteger generation=new AtomicInteger(),sequence=new AtomicInteger(),pending=new AtomicInteger();
 final OnInitListener init;final Messenger inbox;Messenger remote;boolean bound;volatile boolean closed,ready,notified;
 volatile Set<Voice> voices=Collections.emptySet();volatile Voice voice;volatile float speed=1;volatile UtteranceProgressListener progress;volatile AudioAttributes attributes;
 volatile Runnable connectionLost;
 public void setOnConnectionLostListener(Runnable listener){connectionLost=listener;}
 volatile String lastFailure="";
 public String getFailureReason(){return lastFailure;}
 volatile Job active;final Map<String,Job> jobs=new ConcurrentHashMap<>();
 static final class Mark {final int frame,start,end;Mark(Bundle b){frame=b.getInt("frame");start=b.getInt("start");end=b.getInt("end");}}
 static final class Job {
  final String key,id,text;final File file;final int generation;final long silence;final Voice voice;final float speed;
  final CountDownLatch result=new CountDownLatch(1);final Queue<Mark> marks=new ConcurrentLinkedQueue<>();
  volatile String stage="queued";volatile boolean success,started,cancelled;volatile ParcelFileDescriptor input;volatile AudioTrack track;long frames;
  Job(String key,String id,String text,File file,int generation,long silence,Voice voice,float speed){this.key=key;this.id=id;this.text=text;this.file=file;this.generation=generation;this.silence=silence;this.voice=voice;this.speed=speed;}
 }
 public NaturalSpeech(Context c,OnInitListener listener){this(c,listener,new ComponentName(ENGINE,SERVICE));}
 NaturalSpeech(Context c,OnInitListener listener,ComponentName component){
  // TextToSpeech has no uninitialised public constructor. Its connection is unused and closed.
  super(c.getApplicationContext(),ignored->{},ENGINE);
  context=c.getApplicationContext();init=listener;attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
  inbox=new Messenger(new Handler(Looper.getMainLooper(),this::receive));
  CONTROL.execute(()->{try{super.shutdown();}catch(RuntimeException ignored){}});
  main.postDelayed(()->{lastFailure="connection-timeout";initialised(ERROR);},12000);
  try{bound=context.bindService(new Intent(Engine.INTENT_ACTION_TTS_SERVICE).setComponent(component),connection,Context.BIND_AUTO_CREATE);if(!bound){lastFailure="bind-rejected";main.post(()->initialised(ERROR));}}catch(RuntimeException e){lastFailure="bind-"+e.getClass().getSimpleName();main.post(()->initialised(ERROR));}
 }
 final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;remote=new Messenger(binder);try{Message m=Message.obtain(null,HELLO);m.replyTo=inbox;remote.send(m);}catch(RemoteException e){initialised(ERROR);}}
  public void onServiceDisconnected(ComponentName name){disconnected();}
  public void onNullBinding(ComponentName name){disconnected();}
  public void onBindingDied(ComponentName name){disconnected();}
 };
 void disconnected(){boolean wasReady=ready;lastFailure="engine-process-ended";ready=false;remote=null;voices=Collections.emptySet();for(Job j:jobs.values()){j.success=false;j.result.countDown();cancelPipe(j);}initialised(ERROR);if(wasReady&&connectionLost!=null)connectionLost.run();}
 void initialised(int result){if(closed||notified)return;notified=true;ready=result==SUCCESS;main.removeCallbacksAndMessages(null);if(init!=null)init.onInit(result);}
 boolean receive(Message m){if(closed)return true;Bundle b=m.getData();b.setClassLoader(Voice.class.getClassLoader());
  if(m.what==CATALOG){ArrayList<Voice> list=b.getParcelableArrayList("voices");voices=list==null?Collections.emptySet():Collections.unmodifiableSet(new LinkedHashSet<>(list));for(Voice v:voices)if(!v.getFeatures().contains(Engine.KEY_FEATURE_NOT_INSTALLED)){voice=v;break;}initialised(SUCCESS);return true;}
  Job j=jobs.get(b.getString("key",""));if(j==null||j.cancelled)return true;
  if(m.what==RANGE)j.marks.add(new Mark(b));else if(m.what==DONE||m.what==FAILED){j.success=m.what==DONE;j.result.countDown();if(m.what==FAILED)cancelPipe(j);}
  return true;
 }
 @Override public Set<Voice> getVoices(){return voices;}
 @Override public Set<Locale> getAvailableLanguages(){Set<Locale> out=new HashSet<>();for(Voice v:voices)out.add(v.getLocale());return out;}
 @Override public Voice getVoice(){return voice;}
 @Override public int setVoice(Voice chosen){if(!ready||chosen==null)return ERROR;for(Voice v:voices)if(v.getName().equals(chosen.getName())&&!v.getFeatures().contains(Engine.KEY_FEATURE_NOT_INSTALLED)){voice=v;return SUCCESS;}return ERROR;}
 @Override public Locale getLanguage(){return voice==null?null:voice.getLocale();}
 @Override public int setLanguage(Locale locale){if(locale==null)return LANG_NOT_SUPPORTED;for(Voice v:voices)if(v.getLocale().getLanguage().equals(locale.getLanguage())&&setVoice(v)==SUCCESS)return LANG_AVAILABLE;return LANG_MISSING_DATA;}
 @Override public int setSpeechRate(float rate){if(!Float.isFinite(rate)||rate<=0)return ERROR;speed=Math.max(.5f,Math.min(2f,rate));return SUCCESS;}
 @Override public int setPitch(float pitch){return pitch==1f?SUCCESS:ERROR;}
 @Override public int setAudioAttributes(AudioAttributes value){if(value!=null)attributes=value;return SUCCESS;}
 @Override public int setOnUtteranceProgressListener(UtteranceProgressListener listener){progress=listener;return SUCCESS;}
 @Override public boolean isSpeaking(){return pending.get()>0;}
 @Override public int speak(CharSequence text,int mode,Bundle params,String id){return enqueue(text,null,id,mode,-1);}
 @Override public int synthesizeToFile(CharSequence text,Bundle params,File file,String id){return file==null?ERROR:enqueue(text,file,id,QUEUE_ADD,-1);}
 @Override public int playSilentUtterance(long ms,int mode,String id){return enqueue("",null,id,mode,Math.max(0,Math.min(15000,ms)));}
 int enqueue(CharSequence value,File file,String id,int mode,long silence){if(!ready||closed||value==null||value.length()>10000)return ERROR;if(mode==QUEUE_FLUSH)stop();if(pending.incrementAndGet()>16){pending.decrementAndGet();return ERROR;}Job j=new Job("n"+sequence.incrementAndGet(),id==null?"":id,value.toString(),file,generation.get(),silence,voice,speed);try{work.execute(()->run(j));return SUCCESS;}catch(RejectedExecutionException e){pending.decrementAndGet();return ERROR;}}
 boolean valid(Job j){return !closed&&!j.cancelled&&j.generation==generation.get();}
 void callbackError(Job j){UtteranceProgressListener p=progress;if(p!=null&&valid(j))p.onError(j.id,ERROR_SYNTHESIS);}
 void started(Job j,int rate){if(j.started)return;j.started=true;UtteranceProgressListener p=progress;if(p!=null){p.onBeginSynthesis(j.id,rate,AudioFormat.ENCODING_PCM_16BIT,1);p.onStart(j.id);}}
 void run(Job j){try{if(!valid(j))return;active=j;if(j.silence>=0){UtteranceProgressListener p=progress;if(p!=null)p.onStart(j.id);long until=SystemClock.elapsedRealtime()+j.silence;while(valid(j)&&SystemClock.elapsedRealtime()<until)Thread.sleep(Math.min(20,Math.max(1,until-SystemClock.elapsedRealtime())));if(valid(j)&&progress!=null)progress.onDone(j.id);return;}if(j.voice==null)throw new IOException("No installed voice");
   j.stage="pipe-setup";int rate=22050;for(Voice v:voices)if(v.getName().equals(j.voice.getName()))for(String f:v.getFeatures())if(f.startsWith("seeky:rate="))rate=Integer.parseInt(f.substring(11));
   final int sampleRate=rate;ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();j.input=pipe[0];jobs.put(j.key,j);
   Message request=Message.obtain(null,SYNTH);Bundle data=new Bundle();data.putString("key",j.key);data.putString("voice",j.voice.getName());data.putString("text",j.text);data.putFloat("speed",j.speed);data.putParcelable("output",pipe[1]);request.setData(data);request.replyTo=inbox;
   try{Messenger engine=remote;if(engine==null)throw new IOException("Engine disconnected");engine.send(request);}finally{pipe[1].close();}
   Runnable deadline=()->{if(jobs.get(j.key)==j){j.success=false;j.result.countDown();Messenger engine=remote;if(engine!=null)try{Message cancel=Message.obtain(null,CANCEL);cancel.replyTo=inbox;engine.send(cancel);}catch(RemoteException ignored){}cancelPipe(j);}};main.postDelayed(deadline,120000);
   try(DataInputStream input=new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]))){
    j.stage="pcm-read";RandomAccessFile wav=j.file==null?null:new RandomAccessFile(j.file,"rw");try{if(wav!=null){wav.setLength(0);wav.write(new byte[44]);}
     byte[] buffer=new byte[8192];while(valid(j)){byte[] prefix=new byte[4];readPacket(j,input,prefix,4);int n=java.nio.ByteBuffer.wrap(prefix).getInt();if(n==0)break;if(n<2||n>buffer.length||(n&1)!=0)throw new IOException("Invalid PCM packet");readPacket(j,input,buffer,n);main.removeCallbacks(deadline);main.postDelayed(deadline,120000);if(wav==null){if(j.track==null){int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<0)throw new IOException("Audio output unavailable");j.stage="audio-open";j.track=new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(Math.max(min,2048)).setTransferMode(AudioTrack.MODE_STREAM).build();if(Build.VERSION.SDK_INT>=31)j.track.setStartThresholdInFrames(1);j.track.play();started(j,rate);main.post(new Runnable(){public void run(){if(!valid(j)||active!=j||j.track==null)return;try{long frame=Integer.toUnsignedLong(j.track.getPlaybackHeadPosition());Mark mark;while((mark=j.marks.peek())!=null&&mark.frame<=frame){j.marks.poll();UtteranceProgressListener p=progress;if(p!=null)p.onRangeStart(j.id,mark.start,mark.end,mark.frame);}main.postDelayed(this,40);}catch(IllegalStateException ignored){}}});}started(j,rate);j.stage="audio-write";int at=0;while(at<n&&valid(j)){int wrote=j.track.write(buffer,at,n-at);if(wrote<=0)throw new IOException("Audio playback failed");at+=wrote;}}
      else{started(j,rate);wav.write(buffer,0,n);}j.frames+=n/2;if(j.frames*2>4294967200L)throw new IOException("WAV limit exceeded");}
     if(!valid(j))return;if(!j.result.await(5,TimeUnit.SECONDS)||!j.success)throw new IOException("Synthesis failed");
     if(wav!=null){header(wav,sampleRate,j.frames*2);wav.getFD().sync();}else if(j.track!=null){if(Build.VERSION.SDK_INT<31&&j.frames<j.track.getBufferSizeInFrames()){int padding=(int)Math.min(131072,(j.track.getBufferSizeInFrames()-j.frames)*2);byte[] silence=new byte[padding];for(int at=0;at<padding&&valid(j);){int wrote=j.track.write(silence,at,padding-at);if(wrote<=0)break;at+=wrote;}}j.stage="audio-drain";long until=SystemClock.elapsedRealtime()+Math.max(5000,1000*j.frames/rate+5000);while(valid(j)&&Integer.toUnsignedLong(j.track.getPlaybackHeadPosition())<j.frames){if(SystemClock.elapsedRealtime()>until)throw new IOException("Audio drain timeout");Thread.sleep(20);}}
     if(valid(j)&&progress!=null)progress.onDone(j.id);
    }finally{if(wav!=null)wav.close();}
   }finally{main.removeCallbacks(deadline);}
  }catch(Exception e){Messenger engine=remote;lastFailure=engine==null||!engine.getBinder().isBinderAlive()?"engine-process-ended":j.stage+"-"+e.getClass().getSimpleName();callbackError(j);}finally{jobs.remove(j.key);cancelPipe(j);if(j.track!=null){try{j.track.release();}catch(RuntimeException ignored){}j.track=null;}if(active==j)active=null;pending.decrementAndGet();}}
 void readPacket(Job j,InputStream input,byte[] bytes,int length)throws Exception{int offset=0;while(offset<length){if(!valid(j))throw new InterruptedIOException();if(j.result.getCount()==0&&!j.success)throw new IOException("Synthesis failed");ParcelFileDescriptor descriptor=j.input;if(descriptor==null)throw new InterruptedIOException();android.system.StructPollfd poll=new android.system.StructPollfd();poll.fd=descriptor.getFileDescriptor();poll.events=(short)(android.system.OsConstants.POLLIN|android.system.OsConstants.POLLHUP);if(android.system.Os.poll(new android.system.StructPollfd[]{poll},250)==0)continue;if((poll.revents&android.system.OsConstants.POLLNVAL)!=0)throw new IOException("Audio channel closed");int count=input.read(bytes,offset,length-offset);if(count<0)throw new EOFException("Incomplete audio packet");offset+=count;}}
 static void little(RandomAccessFile f,long value,int bytes)throws IOException{for(int i=0;i<bytes;i++)f.write((int)(value>>(8*i))&255);}
 static void header(RandomAccessFile f,int rate,long bytes)throws IOException{f.seek(0);f.writeBytes("RIFF");little(f,bytes+36,4);f.writeBytes("WAVEfmt ");little(f,16,4);little(f,1,2);little(f,1,2);little(f,rate,4);little(f,rate*2,4);little(f,2,2);little(f,16,2);f.writeBytes("data");little(f,bytes,4);}
 void cancelPipe(Job j){ParcelFileDescriptor pipe=j.input;j.input=null;if(pipe!=null)try{pipe.close();}catch(IOException ignored){}AudioTrack track=j.track;if(track!=null)CONTROL.execute(()->{try{track.pause();track.flush();}catch(RuntimeException ignored){}});}
 @Override public int stop(){generation.incrementAndGet();Job j=active;if(j!=null){j.cancelled=true;cancelPipe(j);}Messenger engine=remote;if(engine!=null)try{Message m=Message.obtain(null,CANCEL);m.replyTo=inbox;engine.send(m);}catch(RemoteException ignored){}return SUCCESS;}
 @Override public void shutdown(){if(closed)return;stop();Messenger closing=remote;if(closing!=null)try{Message m=Message.obtain(null,BYE);m.replyTo=inbox;closing.send(m);}catch(RemoteException ignored){}closed=true;ready=false;main.removeCallbacksAndMessages(null);if(bound){bound=false;try{context.unbindService(connection);}catch(RuntimeException ignored){}}work.shutdownNow();}
}
