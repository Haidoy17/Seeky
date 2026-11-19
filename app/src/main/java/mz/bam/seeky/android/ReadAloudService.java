package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.net.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import java.util.concurrent.*;

/** User-started foreground TTS. Engine state stays on main; documents stay on one worker. */
public final class ReadAloudService extends Service {
    static final String START="seeky.speech.START", PAUSE="seeky.speech.PAUSE", PLAY="seeky.speech.PLAY", STOP="seeky.speech.STOP", NEXT="seeky.speech.NEXT", CONFIGURE="seeky.speech.CONFIGURE",PREVIOUS="seeky.speech.PREVIOUS",REWIND="seeky.speech.REWIND",FORWARD="seeky.speech.FORWARD",REFRESH="seeky.speech.REFRESH";
    static final int ID=101;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    SpeechCursor cursor=new SpeechCursor();
    TextToSpeech tts;SpeechText.Prepared spoken,queuedSpoken;
    MediaSession media;
    AudioManager audio;
    PowerManager.WakeLock wake;
    SharedPreferences prefs;
    volatile Request request;
    SpeechDocument document; // worker only
    Request documentOwner; // worker only
    static final int PREFETCH_CHARS=64000;
    volatile Prefetched prefetched; // immutable worker result; consumed by main or worker
    static final class Prefetched {
        final Request owner;final int part;final String value,hash;final boolean partial;
        final long revision;final SpeechLayout.Plan structure;
        Prefetched(Request owner,int part,String value,boolean partial,long revision,String hash,SpeechLayout.Plan structure){this.owner=owner;this.part=part;this.value=value;this.partial=partial;this.revision=revision;this.hash=hash;this.structure=structure;}
    }
    static final class QueuedPage {
        final Prefetched page;final SpeechCursor cursor;final SpeechText.Prepared spoken;final int generation;
        QueuedPage(Prefetched page,SpeechCursor cursor,SpeechText.Prepared spoken,int generation){this.page=page;this.cursor=cursor;this.spoken=spoken;this.generation=generation;}
    }
    QueuedPage queuedPage;SharedPreferences readingState;
    String token="", message="", digest="";
    final long workspaceEpoch=WorkspaceEpoch.value;int part, total, emptyParts, engineGeneration;volatile int generation;
    volatile boolean closed;boolean foreground, engineReady, loading, resumePosition,seeking;
    String firstAudioId="";
    final Runnable firstAudioTimeout=()->{if(!closed&&request!=null&&!cursor.paused&&!firstAudioId.isEmpty()){firstAudioId="";pause("A voz demorou a iniciar. Teste o módulo de vozes e tente novamente.");}};
    int rangeEndOffset=-1;long lastRange;
    final Set<String> silences=new HashSet<>(),fallbackTitles=new HashSet<>();String waitingGap="";Runnable afterGap;
    void clearGaps(){queuedPage=null;silences.clear();fallbackTitles.clear();waitingGap="";if(afterGap!=null)main.removeCallbacks(afterGap);afterGap=null;}
    void addTitlePause(SpeechCursor.Chunk chunk){if(chunk.pauseAfter<=0)return;String id="gap:"+chunk.id;silences.add(id);int status=TextToSpeech.ERROR;try{status=tts.playSilentUtterance(chunk.pauseAfter,TextToSpeech.QUEUE_ADD,id);}catch(RuntimeException ignored){}if(status!=TextToSpeech.SUCCESS){silences.remove(id);fallbackTitles.add(chunk.id);}}
    static String titlePauseLabel(int pause){return "Pausa após título · "+(pause%1000==0?Integer.toString(pause/1000):Float.toString(pause/1000f))+" s";}
    static final class Request {
        final Uri uri;final String name,mime,engine,voice,language,selection;
        final boolean online,continuous;final int titlePause;final float rate;final int selectionStart;final long studyMaterial;final String studyTitle,studyPath;
        volatile boolean cancelled;
        Request(Intent i,SharedPreferences prefs) {
            uri=i.getData();name=i.getStringExtra("name")==null?"Documento":i.getStringExtra("name");mime=i.getType();
            engine=prefs.getString("speech_engine","com.google.android.tts");voice=prefs.getString("speech_voice","");
            language=prefs.getString("speech_language","pt");online=prefs.getBoolean("speech_online",false);
            continuous=i.getBooleanExtra("continuous",true);selection=i.getStringExtra("selection");selectionStart=Math.max(0,i.getIntExtra("selection_start",0));studyMaterial=i.getLongExtra("study_material",0);studyTitle=i.getStringExtra("study_title");studyPath=i.getStringExtra("study_path");
            titlePause=Math.max(500,Math.min(5000,prefs.getInt("speech_title_pause_ms",2000)));rate=Math.max(.5f,Math.min(2f,prefs.getFloat("speech_rate",1f)));
        }
    }
    public IBinder onBind(Intent intent) { return null; }
    ReadingAudioFocus sharedFocus;boolean backgroundFollowing;
    public void onCreate() {
        super.onCreate();prefs=getSharedPreferences("seeky",0);readingState=getSharedPreferences("reading_tools",0);
        readingState.registerOnSharedPreferenceChangeListener(textChanges);
        SleepTimer.watch(this,()->pause("Temporizador de sono terminado"),this::sleepChanged);sharedFocus=ReadingAudioFocus.get(this);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Seeky:readAloud");wake.setReferenceCounted(false);
        media=new MediaSession(this,"Seeky Read Aloud");media.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        media.setCallback(new MediaSession.Callback(){public void onPlay(){resume();}public void onPause(){pause("Pausado");}public void onStop(){stopReading("Leitura por voz terminada");}public void onSkipToNext(){nextPart();}public void onSkipToPrevious(){previousPart();}public void onRewind(){seekSeconds(-seekStep());}public void onFastForward(){seekSeconds(seekStep());}});media.setActive(true);
        registerReceiver(noisy,new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("seeky_speech","Seeky · TTS",NotificationManager.IMPORTANCE_LOW));
    }
    final BroadcastReceiver noisy=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){pause("Pausado");}};
    public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        String action=intent.getAction();
        if(!START.equals(action) && (request==null || !token.equals(intent.getStringExtra("token")))) { if(request==null)stopSelf();return START_NOT_STICKY; }
        try {
            if(START.equals(action)) {
                if(request!=null)request.cancelled=true;save();generation++;engineGeneration++;
                clearGaps();if(tts!=null){TextToSpeech oldEngine=tts;tts=null;ReaderSpeech.shutdownAsync(oldEngine);}
                cursor.clear();cursor.resume();engineReady=false;loading=seeking=false;emptyParts=0;
                request=new Request(intent,prefs);cursor.titlePause=request.titlePause;
                if(request.uri==null)throw new IllegalArgumentException("Documento indisponível.");
                if(request.selection!=null && request.selection.length()>20000)throw new IllegalArgumentException("Seleccione até 20000 caracteres para ouvir.");
                token=UUID.randomUUID().toString();part=Math.max(0,intent.getIntExtra("part",0));total=0;digest="";resumePosition=intent.getBooleanExtra("resume",false);
                message="A preparar leitura por voz…";ensureForeground();publish();
                media.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,request.name).build());
                initEngine();loadPart();
            } else if(STOP.equals(action))stopReading("Leitura por voz terminada");
            else if(PAUSE.equals(action))pause("Pausado");
            else if(CONFIGURE.equals(action))configure(intent.getBooleanExtra("resume",false));
            else if(NEXT.equals(action))nextPart();
            else if(PREVIOUS.equals(action))previousPart();
            else if(REWIND.equals(action))seekSeconds(-seekStep());
            else if(FORWARD.equals(action))seekSeconds(seekStep());
            else if(REFRESH.equals(action))publish();
            else if(PLAY.equals(action))resume();
        } catch(Exception | OutOfMemoryError e) { failure(e); }
        return START_NOT_STICKY;
    }
    void configure(){configure(false);}
    void configure(boolean playNow){
        if(request==null)return;save();cursor.pause();clearGaps();queuedSpoken=null;releaseAudio();main.removeCallbacks(timeout);
        Request previous=request;previous.cancelled=true;generation++;engineGeneration++;
        clearGaps();if(tts!=null){TextToSpeech oldEngine=tts;tts=null;ReaderSpeech.shutdownAsync(oldEngine);}
        Intent settings=new Intent().setDataAndType(previous.uri,previous.mime).putExtra("name",previous.name).putExtra("continuous",previous.continuous).putExtra("selection",previous.selection).putExtra("selection_start",previous.selectionStart).putExtra("study_material",previous.studyMaterial).putExtra("study_title",previous.studyTitle).putExtra("study_path",previous.studyPath);
        request=new Request(settings,prefs);cursor.titlePause=request.titlePause;token=UUID.randomUUID().toString();engineReady=false;loading=seeking=false;message=playNow?"A preparar leitura por voz…":"Voz alterada. Toque em Continuar para retomar.";if(playNow)cursor.resume();publish();initEngine();prepareFollowing();
    }
    final java.util.concurrent.ExecutorService engineWorker=java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"seeky-read-engine");t.setDaemon(true);return t;});
    java.util.concurrent.Future<?> engineTask;
    final Runnable engineConnectTimeout=()->{if(closed||request==null||engineReady)return;engineGeneration++;TextToSpeech old=tts;tts=null;if(engineTask!=null)engineTask.cancel(true);pause("O motor de voz não respondeu. Escolha outra voz ou tente novamente.");AppDiagnostics.record(this,"speech-connect-timeout",new java.util.concurrent.TimeoutException());ReaderSpeech.shutdownAsync(old);};
    void initEngine() {
        final int expected=++engineGeneration;final Request owner=request;main.removeCallbacks(engineConnectTimeout);main.postDelayed(engineConnectTimeout,15000);
        tts=SpeechEngines.create(getApplicationContext(),status->main.post(()->{
            if(closed||owner!=request||expected!=engineGeneration)return;
            if(status!=TextToSpeech.SUCCESS){main.removeCallbacks(engineConnectTimeout);pause("Motor de voz indisponível. Abra as definições de voz.");return;}
            final TextToSpeech engine=tts;
            engineTask=engineWorker.submit(()->{try{
                Voice chosen=SpeechVoices.resolve(engine.getVoices(),owner.online,owner.language,owner.voice);
                if(chosen==null){main.post(()->{if(owner==request&&expected==engineGeneration){main.removeCallbacks(engineConnectTimeout);pause("A voz escolhida não está disponível. Escolha ou instale outra voz.");}});return;}
                
                SpeechVoices.apply(engine,chosen,owner.rate);
                engine.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());engine.setOnUtteranceProgressListener(progress);
                main.post(()->{if(closed||owner!=request||expected!=engineGeneration)return;main.removeCallbacks(engineConnectTimeout);engineReady=true;if(!cursor.paused)message="A ler em voz alta";publish();speak();});
            }catch(Exception error){main.post(()->{if(owner==request&&expected==engineGeneration){main.removeCallbacks(engineConnectTimeout);failure(error);}});}});
        }),owner.engine);
    }
    static boolean accepts(Request request,Voice voice) {
        return SpeechPolicy.accepts(request.online,request.language,voice.isNetworkConnectionRequired(),voice.getLocale().getLanguage(),voice.getFeatures()!=null&&voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED));
    }
    long contentRevision(){return getSharedPreferences("reading_tools",0).getLong("speech_text_revision",0);}
    final SharedPreferences.OnSharedPreferenceChangeListener textChanges=(preferences,key)->{
        if("speech_text_revision".equals(key))main.post(()->{
            if(closed||request==null)return;
            if(prefetched!=null&&prefetched.revision!=contentRevision())prefetched=null;
            if(queuedPage!=null&&queuedPage.page.revision!=contentRevision()){
                boolean playing=!cursor.paused;pause("A actualizar o texto da leitura…");if(playing)resume();
            }
            prepareFollowing();
        });
    };
    void sleepChanged(){
        if(request!=null&&queuedPage!=null&&SleepTimer.endsAt(this,request.uri.toString(),part)){
            boolean playing=!cursor.paused;pause("Temporizador de sono activo");if(playing)resume();
        }
    }
    void openDocument(Request owner)throws java.io.IOException{
        if(documentOwner!=owner){if(document!=null)document.close();document=null;documentOwner=owner;prefetched=null;}
        if(document==null)document=new SpeechDocument(getApplicationContext(),owner.uri,owner.name,owner.mime,()->owner.cancelled||closed);
    }
    void loadPart() {
        if(request==null||closed)return;
        final Request owner=request;final int expected=++generation;final int target=part;
        clearGaps();loading=true;if(!cursor.paused)wake.acquire(120000L);cursor.load("",0);digest="";message="A preparar leitura por voz…";publish();
        worker.execute(()->{
            try {
                if(owner.cancelled)return;
                final String value,hash;final int count;final boolean partial;final SpeechLayout.Plan structure;
                final long revision=contentRevision();
                if(owner.selection!=null){value=owner.selection;count=1;partial=false;hash=ReaderStore.digest(value);structure=SpeechLayout.of(value);}
                else {
                    openDocument(owner);count=document.count();
                    if(target>=count)throw new IllegalArgumentException("Página ou capítulo indisponível.");
                    Prefetched cached=prefetched;prefetched=null;
                    if(cached!=null&&cached.owner==owner&&cached.part==target&&cached.revision==revision){value=cached.value;partial=cached.partial;hash=cached.hash;structure=cached.structure;}
                    else{value=document.part(target);partial=document.partial;hash=ReaderStore.digest(value);structure=SpeechLayout.of(value,document.speechTitles(target));}
                }
                main.post(()->{
                    if(closed||owner!=request||expected!=generation)return;
                    if(owner.selection==null&&revision!=contentRevision()){loadPart();return;}
                    loading=false;total=count;digest=hash;
                    int offset=0;
                    if(resumePosition&&owner.selection==null&&prefs.getInt("speech_part_"+owner.uri,-1)==part&&hash.equals(prefs.getString("speech_digest_"+owner.uri,"")))offset=prefs.getInt("speech_offset_"+owner.uri,0);
                    resumePosition=false;cursor.load(value,offset,structure);
                    if(!SpeechCursor.hasText(value,0)) {
                        if(owner.continuous&&owner.selection==null&&part+1<total&&++emptyParts<=30){part++;loadPart();return;}
                        pause("Página sem texto extraível. Digitalizações precisam de OCR.");return;
                    }
                    emptyParts=0;if(!cursor.paused)message=partial?"A ler · texto parcial":"A ler em voz alta";else message="Pausado";publish();speak();
                });
                prefetch(owner,target+1,count);saveProgress(owner,target,count);
            }catch(Exception | OutOfMemoryError e){main.post(()->{if(owner==request&&expected==generation&&!closed){loading=false;failure(e);}});}
        });
    }
    void prepareFollowing(){
        final Request owner=request;final int next=part+1,count=total;
        if(owner==null||closed||loading||!owner.continuous||owner.selection!=null||next>=count)return;
        worker.execute(()->prefetch(owner,next,count));
    }
    void prefetch(Request owner,int next,int count){
        if(owner!=request||owner.cancelled||closed||!owner.continuous||owner.selection!=null||next>=count)return;
        Prefetched cached=prefetched;
        if(cached!=null&&cached.owner==owner&&cached.part==next&&cached.revision==contentRevision()){main.post(this::queueNextPage);return;}
        Runtime memory=Runtime.getRuntime();if(memory.maxMemory()-(memory.totalMemory()-memory.freeMemory())<16777216L)return;
        try{
            openDocument(owner);long before=contentRevision();String value=document.part(next);boolean partial=document.partial;
            if(value.length()>PREFETCH_CHARS)return;
            String hash=ReaderStore.digest(value);SpeechLayout.Plan plan=SpeechLayout.of(value,document.speechTitles(next));
            if(!owner.cancelled&&!closed&&owner==request&&before==contentRevision()){
                prefetched=new Prefetched(owner,next,value,partial,before,hash,plan);main.post(this::queueNextPage);
            }
        }catch(Exception|OutOfMemoryError ignored){/* A normal transition retries extraction if read-ahead is unavailable. */}
    }
    boolean finalChunk(SpeechCursor.Chunk chunk){
        return chunk!=null&&!SpeechCursor.hasText(cursor.text,chunk.end);
    }
    void queueNextPage(){
        if(request==null||closed||loading||cursor.paused||!engineReady||queuedPage!=null||afterGap!=null||!waitingGap.isEmpty()||!request.continuous||request.selection!=null)return;
        SpeechCursor.Chunk tail=cursor.queued==null?cursor.pending:cursor.queued;
        if(!finalChunk(tail)||fallbackTitles.contains(tail.id)||SleepTimer.endsAt(this,request.uri.toString(),part))return;
        Prefetched page=prefetched;
        if(page==null||page.owner!=request||page.part!=part+1||page.revision!=contentRevision()||!SpeechCursor.hasText(page.value,0))return;
        if(request.online&&!connected())return;
        try{
            SpeechCursor next=new SpeechCursor();next.titlePause=request.titlePause;next.load(page.value,0,page.structure);
            SpeechCursor.Chunk first=next.next(Math.min(SpeechPolicy.startupLimit(request.rate,request.online),TextToSpeech.getMaxSpeechInputLength()-1));
            if(first==null)return;
            first=next.trim(false,VoiceText.fit(next.text,first.start,first.end,TextToSpeech.getMaxSpeechInputLength()-1,request.language,VoiceText.rules(this)));
            SpeechText.Prepared prepared=VoiceText.prepare(this,first.text,request.language);
            if(prepared.text.isEmpty())return;
            SpeechVoices.verify(tts,new SpeechProfile(request.engine,request.voice,request.language,request.online,request.rate));
            Bundle parameters=SpeechParameters.create(request.online);parameters.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,AudioManager.STREAM_MUSIC);
            QueuedPage ready=new QueuedPage(page,next,prepared,generation);queuedPage=ready;
            if(tts.speak(prepared.text,TextToSpeech.QUEUE_ADD,parameters,first.id)!=TextToSpeech.SUCCESS){queuedPage=null;return;}
            addTitlePause(first);
        }catch(Exception error){queuedPage=null;AppDiagnostics.record(this,"speech-next-page",error);}
    }
    boolean activateQueuedPage(String id){
        QueuedPage ready=queuedPage;
        if(ready==null||id!=null&&!ready.cursor.pending.id.equals(id))return false;
        Prefetched page=ready.page;
        if(closed||request==null||cursor.paused||page.owner!=request||ready.generation!=generation)return false;
        if(SleepTimer.boundary(this,request.uri.toString(),part))return false;
        if(page.revision!=contentRevision()){
            tts.stop();save();clearGaps();part=page.part;loadPart();return false;
        }
        queuedPage=null;if(prefetched==page)prefetched=null;
        if(afterGap!=null)main.removeCallbacks(afterGap);afterGap=null;waitingGap="";
        String first=ready.cursor.pending.id;silences.retainAll(Collections.singleton("gap:"+first));fallbackTitles.retainAll(Collections.singleton(first));
        generation++;cursor=ready.cursor;part=page.part;digest=page.hash;spoken=ready.spoken;queuedSpoken=null;loading=false;resumePosition=false;emptyParts=0;
        message=page.partial?"A preparar voz · texto parcial":"A preparar a voz…";wake.acquire(600000L);main.removeCallbacks(timeout);main.postDelayed(timeout,SpeechPolicy.timeout(spoken.text.length(),request.rate));
        save();publish();queueAhead();prepareFollowing();
        final Request owner=request;final int current=part,count=total;worker.execute(()->saveProgress(owner,current,count));
        return true;
    }
    void speak() {
        if(closed||request==null||!engineReady||loading||cursor.paused||cursor.pending!=null||afterGap!=null||!waitingGap.isEmpty())return;
        if(cursor.ended()) {
            if(SleepTimer.boundary(this,request.uri.toString(),part))return;
            if(request.continuous&&request.selection==null&&part+1<total){if(activateQueuedPage(null))return;if(loading)return;save();part++;loadPart();}
            else stopReading("Leitura por voz concluída");
            return;
        }
        if(request.online&&!connected()){pause("Sem Internet. Escolha uma voz offline para continuar.");return;}
        if(!sharedFocus.acquire(this,()->pause("Pausado"))){pause("Som ocupado por outra aplicação.");return;}
        try {
            ensureForeground();wake.acquire(600000L);
            SpeechCursor.Chunk chunk=cursor.next(Math.min(SpeechPolicy.startupLimit(request.rate,request.online),TextToSpeech.getMaxSpeechInputLength()-1));
            if(chunk==null)return;
            chunk=cursor.trim(false,VoiceText.fit(cursor.text,chunk.start,chunk.end,TextToSpeech.getMaxSpeechInputLength()-1,request.language,VoiceText.rules(this)));
            spoken=VoiceText.prepare(this,chunk.text,request.language);if(spoken.text.isEmpty()){cursor.done(chunk.id);main.post(this::speak);return;}
            SpeechVoices.verify(tts,new SpeechProfile(request.engine,request.voice,request.language,request.online,request.rate));Bundle params=SpeechParameters.create(request.online);params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,AudioManager.STREAM_MUSIC);
            firstAudioId=chunk.id;main.removeCallbacks(firstAudioTimeout);main.postDelayed(firstAudioTimeout,45000);
            if(tts.speak(spoken.text,TextToSpeech.QUEUE_FLUSH,params,chunk.id)!=TextToSpeech.SUCCESS){pause("Não foi possível reproduzir esta voz.");return;}
            addTitlePause(chunk);main.removeCallbacks(timeout);main.postDelayed(timeout,SpeechPolicy.timeout(spoken.text.length(),request.rate));queueAhead();publish();
        }catch(Exception e){failure(e);}
    }
    void queueAhead(){
        if(request==null||closed||cursor.paused||loading||!engineReady||cursor.pending!=null&&fallbackTitles.contains(cursor.pending.id))return;
        SpeechCursor.Chunk next=cursor.lookAhead(Math.min(SpeechPolicy.chunkLimit(request.rate,request.online),TextToSpeech.getMaxSpeechInputLength()-1));if(next==null){queueNextPage();return;}next=cursor.trim(true,VoiceText.fit(cursor.text,next.start,next.end,TextToSpeech.getMaxSpeechInputLength()-1,request.language,VoiceText.rules(this)));
        try{queuedSpoken=VoiceText.prepare(this,next.text,request.language);SpeechVoices.verify(tts,new SpeechProfile(request.engine,request.voice,request.language,request.online,request.rate));Bundle params=SpeechParameters.create(request.online);params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,AudioManager.STREAM_MUSIC);if(tts.speak(queuedSpoken.text,TextToSpeech.QUEUE_ADD,params,next.id)!=TextToSpeech.SUCCESS){cursor.queued=null;queuedSpoken=null;}else addTitlePause(next);}catch(Exception error){cursor.queued=null;queuedSpoken=null;AppDiagnostics.record(this,"speech-lookahead",error);}queueNextPage();
    }
    boolean connected() {
        ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    final Runnable timeout=()->pause("A voz não respondeu. Tente novamente ou escolha outra voz.");
    final UtteranceProgressListener progress=new UtteranceProgressListener(){
        public void onStart(String id) {main.post(()->{if(queuedPage!=null&&queuedPage.cursor.pending.id.equals(id)&&!activateQueuedPage(id))return;if(!closed&&silences.contains(id)&&!cursor.paused){message=titlePauseLabel(request==null?2000:request.titlePause);publish();return;}if(!closed&&cursor.accepts(id)){firstAudioId="";main.removeCallbacks(firstAudioTimeout);rangeEndOffset=-1;wake.acquire(600000L);message="A ler em voz alta";main.removeCallbacks(timeout);main.postDelayed(timeout,SpeechPolicy.timeout(spoken==null?cursor.pending.text.length():spoken.text.length(),request.rate));queueAhead();publish();}});}
        public void onDone(String id){main.post(()->{if(closed)return;if(queuedPage!=null&&queuedPage.cursor.pending.id.equals(id)&&!activateQueuedPage(id))return;if(silences.remove(id)){if(waitingGap.equals(id)&&!cursor.paused){waitingGap="";main.removeCallbacks(timeout);message="A ler em voz alta";speak();publish();}return;}if(!cursor.accepts(id))return;firstAudioId="";main.removeCallbacks(firstAudioTimeout);int pause=cursor.pending.pauseAfter;boolean fallback=fallbackTitles.remove(id);if(!cursor.done(id))return;main.removeCallbacks(timeout);spoken=queuedSpoken;queuedSpoken=null;save();if(cursor.pending==null&&pause>0&&(fallback||silences.contains("gap:"+id))){message=titlePauseLabel(request==null?2000:request.titlePause);if(fallback){final Request owner=request;final int expected=generation;afterGap=()->{afterGap=null;if(owner==request&&generation==expected&&!cursor.paused&&!closed){waitingGap="";message="A ler em voz alta";speak();publish();}};main.postDelayed(afterGap,pause);}else{waitingGap="gap:"+id;main.postDelayed(timeout,15000);}publish();return;}publish();if(cursor.pending==null)speak();else{main.postDelayed(timeout,SpeechPolicy.timeout(spoken==null?cursor.pending.text.length():spoken.text.length(),request.rate));queueAhead();}});}
        public void onError(String id){onError(id,TextToSpeech.ERROR);}
        public void onError(String id,int error){main.post(()->{if(queuedPage!=null&&queuedPage.cursor.pending.id.equals(id)){queuedPage=null;silences.remove("gap:"+id);fallbackTitles.remove(id);return;}if(!closed&&silences.remove(id)){pause("A voz interrompeu a pausa. Pode continuar a leitura.");return;}if(!closed&&cursor.queued!=null&&cursor.queued.id.equals(id)){cursor.queued=null;queuedSpoken=null;return;}if(!closed&&cursor.accepts(id))pause(request!=null&&request.online?"Voz online indisponível. Verifique a ligação ou escolha offline.":"Voz indisponível. Verifique os dados de voz instalados.");});}
        public void onRangeStart(String id,int start,int end,int frame){main.post(()->{if(queuedPage!=null&&queuedPage.cursor.pending.id.equals(id)&&!activateQueuedPage(id))return;if(closed||!cursor.accepts(id))return;cursor.range(id,spoken==null?start:spoken.sourceOffset(start));rangeEndOffset=spoken==null?end:spoken.sourceOffset(end);if(SystemClock.elapsedRealtime()-lastRange>120){lastRange=SystemClock.elapsedRealtime();publish(false);}});}
    };
    void pause(String reason) {firstAudioId="";main.removeCallbacks(firstAudioTimeout);
        if(seeking){generation++;loading=seeking=false;}cursor.pause();clearGaps();queuedSpoken=null;if(tts!=null&&engineReady)tts.stop();main.removeCallbacks(timeout);releaseAudio();save();message=reason;publish();
    }
    void resume() {
        if(request==null||seeking)return;
        cursor.resume();message="A ler em voz alta";
        if(!engineReady){if(tts!=null){tts.shutdown();tts=null;}initEngine();}
        if(cursor.text.isEmpty()&&!loading)loadPart();else speak();publish();
    }
    void nextPart() {
        if(request==null||request.selection!=null||loading||part+1>=total)return;
        boolean playing=!cursor.paused;cursor.pause();clearGaps();queuedSpoken=null;if(tts!=null&&engineReady)tts.stop();main.removeCallbacks(timeout);save();if(playing)cursor.resume();part++;loadPart();
    }
    int seekStep(){int step=prefs.getInt("speech_seek_seconds",10);return step==20||step==30?step:10;}
    void previousPart(){if(request==null||request.selection!=null||loading||part<=0)return;boolean playing=!cursor.paused;save();cursor.pause();clearGaps();queuedSpoken=null;if(tts!=null&&engineReady)tts.stop();main.removeCallbacks(timeout);if(playing)cursor.resume();part--;loadPart();}
    void seekSeconds(int seconds){if(request==null||loading||!engineReady||seconds==0||seconds < -30||seconds > 30)return;final Request owner=request;final boolean playing=!cursor.paused;final int origin=part;final String current=cursor.text;final SpeechLayout.Plan currentPlan=cursor.layout;final int offset=cursor.pending==null?cursor.offset:cursor.spoken;save();cursor.pause();clearGaps();queuedSpoken=null;if(tts!=null&&engineReady)tts.stop();main.removeCallbacks(timeout);releaseAudio();loading=seeking=true;message=seconds<0?"A recuar na leitura…":"A avançar na leitura…";final int expected=++generation;publish();worker.execute(()->{try{int target=origin;String value=current;SpeechLayout.Plan plan=currentPlan;long remaining=seconds*1000L;if(owner.selection==null&&documentOwner!=owner){if(document!=null)document.close();document=new SpeechDocument(getApplicationContext(),owner.uri,owner.name,owner.mime,()->owner.cancelled||closed);documentOwner=owner;}SpeechSeek.Move moved=SpeechSeek.move(value,offset,remaining,owner.rate,plan);int traversed=0;while(moved.remaining!=0&&owner.selection==null&&document!=null&&traversed++<50){if(owner.cancelled||closed||expected!=generation)return;int direction=moved.remaining<0?-1:1;if(target+direction<0||target+direction>=document.count())break;target+=direction;value=document.part(target);plan=SpeechLayout.of(value,document.speechTitles(target));remaining=moved.remaining;moved=SpeechSeek.move(value,direction<0?value.length():0,remaining,owner.rate,plan);}final int chosen=target,at=moved.offset;final String source=value,hash=ReaderStore.digest(value);final SpeechLayout.Plan structure=plan;prefetched=null;main.post(()->{if(closed||owner!=request||expected!=generation)return;part=chosen;digest=hash;loading=seeking=false;cursor.load(source,at,structure);if(playing)cursor.resume();else cursor.pause();message="Salto aproximado · "+(seconds>0?"+":"")+seconds+" s";save();publish();if(playing)speak();});}catch(Exception|OutOfMemoryError error){main.post(()->{if(owner==request&&expected==generation&&!closed){loading=seeking=false;cursor.load(current,offset,currentPlan);failure(error);}});}});}
    void save() {if(workspaceEpoch!=WorkspaceEpoch.value)return;
        if(request!=null&&request.selection==null&&!digest.isEmpty())prefs.edit().putInt("speech_part_"+request.uri,part).putInt("speech_offset_"+request.uri,cursor.pending==null?cursor.offset:cursor.spoken).putString("speech_digest_"+request.uri,digest).apply();
    }
    void updateBackground(boolean on){if(on!=backgroundFollowing){backgroundFollowing=on;BackgroundAudioService.follow(this,on);}}
    void releaseAudio() { updateBackground(false); if(wake!=null&&wake.isHeld())wake.release();if(audio!=null)sharedFocus.release(this); }
    void ensureForeground() {
        if(foreground)return;
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(ID,notification());
        foreground=true;
    }
    void publish(){publish(true);}
    void publish(boolean updateNotification) {
        if(closed)return;
        updateBackground(request!=null&&!cursor.paused&&!loading&&engineReady);
        SpeechEvents.publish(new SpeechEvents.State(request==null?"":request.uri.toString(),token,message,part,total,(cursor.pending==null?cursor.offset:cursor.spoken)+(request!=null&&request.selection!=null?request.selectionStart:0),Math.max(0,rangeEndOffset)+(request!=null&&request.selection!=null?request.selectionStart:0),request!=null,cursor.paused,loading||!engineReady));
        if(updateNotification)media.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_REWIND|PlaybackState.ACTION_FAST_FORWARD).setState(cursor.paused?PlaybackState.STATE_PAUSED:loading||!engineReady?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PLAYING,PlaybackState.PLAYBACK_POSITION_UNKNOWN,1f).build());
        if(foreground&&updateNotification)try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(ID,notification());}catch(Exception e){AppDiagnostics.record(this,"speech-notification",e);}
    }
    PendingIntent action(String action,int id) {
        Intent intent=new Intent(this,ReadAloudService.class).setAction(action).setData(Uri.parse("seeky-speech://control/"+token)).putExtra("token",token);
        return PendingIntent.getService(this,id,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    Notification notification() {
        Intent screen=new Intent(this,ReaderActivity.class);
        if(request!=null)screen.setDataAndType(request.uri,request.mime).putExtra("name",request.name).putExtra("study_segment",part).putExtra("open_speech",true).putExtra("study_material",request.studyMaterial).putExtra("study_title",request.studyTitle).putExtra("study_path",request.studyPath);
        PendingIntent open=PendingIntent.getActivity(this,101,screen,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String subtitle=AppLanguage.text(this,message)+(request!=null?" · "+AppLanguage.text(this,request.online?"Voz de rede":"Voz offline"):"")+(total>1?" · "+(part+1)+" / "+total:"");
        return new Notification.Builder(this,"seeky_speech").setSmallIcon(R.drawable.ic_seeky_notification).setContentTitle(request==null?"Seeky TTS":request.name).setContentText(subtitle).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(!cursor.paused).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setDeleteIntent(action(STOP,104))
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_rew,AppLanguage.text(this,"Recuar")+" "+seekStep()+" s",action(REWIND,105)).build())
            .addAction(new Notification.Action.Builder(cursor.paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,AppLanguage.text(this,cursor.paused?"Continuar":"Pausar"),action(cursor.paused?PLAY:PAUSE,102)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_ff,AppLanguage.text(this,"Avançar")+" "+seekStep()+" s",action(FORWARD,106)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,AppLanguage.text(this,"Seguinte"),action(NEXT,103)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,AppLanguage.text(this,"Parar"),action(STOP,104)).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();
    }
    void failure(Throwable e) { AppDiagnostics.record(this,"read-aloud",e);pause(e.getMessage()==null?"Não foi possível iniciar a leitura por voz.":e.getMessage());if(!foreground){android.widget.Toast.makeText(this,AppLanguage.text(this,message),android.widget.Toast.LENGTH_LONG).show();stopReading(message);} }
    void saveProgress(Request owner,int completed,int count){if(owner.studyMaterial<=0||owner.selection!=null)return;try(StudyStore store=new StudyStore(getApplicationContext())){if(StudyReading.advance(store,owner.studyMaterial,completed,count)){StudyEvents.changed();CloudJobs.schedule(this);}}catch(Exception e){AppDiagnostics.record(this,"speech-study-progress",e);}}
    void stopReading(String reason) {
        if(request!=null&&total>0){final Request owner=request;final int completed=!loading&&!digest.isEmpty()&&cursor.ended()?part+1:part;final int count=total;worker.execute(()->saveProgress(owner,completed,count));}
        save();clearGaps();generation++;engineGeneration++;if(request!=null)request.cancelled=true;cursor.clear();if(tts!=null&&engineReady)tts.stop();main.removeCallbacks(timeout);releaseAudio();
        SpeechEvents.publish(new SpeechEvents.State(request==null?"":request.uri.toString(),token,reason,part,total,0,false,true));request=null;
        stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();
    }
    public void onDestroy() {engineWorker.shutdownNow();SleepTimer.unwatch(this);if(readingState!=null)readingState.unregisterOnSharedPreferenceChangeListener(textChanges);
        updateBackground(false);closed=true;generation++;engineGeneration++;save();if(request!=null)request.cancelled=true;
        main.removeCallbacksAndMessages(null);clearGaps();if(tts!=null){TextToSpeech oldEngine=tts;tts=null;ReaderSpeech.shutdownAsync(oldEngine);}releaseAudio();
        try{unregisterReceiver(noisy);}catch(Exception ignored){}
        media.setActive(false);media.release();
        SpeechEvents.State state=SpeechEvents.state;if(state.active&&state.token.equals(token))SpeechEvents.publish(new SpeechEvents.State(state.uri,token,"Leitura por voz terminada",part,total,state.offset,false,true));
        worker.execute(()->{if(document!=null)document.close();document=null;documentOwner=null;prefetched=null;});worker.shutdown();super.onDestroy();
    }
    static void control(Context context,String action,String token) {
        try{context.startService(new Intent(context,ReadAloudService.class).setAction(action).putExtra("token",token));}catch(Exception e){AppDiagnostics.record(context,"speech-control",e);}
    }
}
