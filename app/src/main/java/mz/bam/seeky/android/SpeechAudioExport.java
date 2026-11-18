package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** User-requested WAV rendering of one part, with the selected engine/voice and bounded storage. */
final class SpeechAudioExport {
    static final int REQUEST=45,MAX_TEXT=24000;
    final ReaderActivity a;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Set<File> owned=Collections.synchronizedSet(new HashSet<>());
    volatile boolean cancelled,closed;
    boolean used,busy,awaitingDestination,readyToSave;
    int generation,offset,nextEnd,completed,total,part;
    String text="",currentId="",title="";
    SpeechProfile profile;
    TextToSpeech tts;
    UiDialog progressDialog,readyDialog;
    TextView progress;
    File prepared;
    WavInfo metadata;WaveJoiner writer; // worker only
    SpeechAudioExport(ReaderActivity a){this.a=a;}
    String tr(String value){return AppLanguage.text(a,value);}
    boolean valid(int token){return !closed&&!cancelled&&!a.dead&&generation==token;}
    File directory(){File dir=new File(a.getCacheDir(),"speech-exports");dir.mkdirs();return dir;}
    File temporary(String prefix){File file=new File(directory(),prefix+UUID.randomUUID()+".wav");owned.add(file);return file;}
    void begin(SpeechProfile profile,int part,String selection){
        if(closed||a.dead)return;
        if(busy){Toast.makeText(a,tr("Termine ou cancele a narração em curso."),Toast.LENGTH_LONG).show();return;}
        this.profile=profile;this.part=part;used=true;busy=true;cancelled=false;awaitingDestination=false;readyToSave=false;offset=completed=total=0;text="";currentId="";metadata=null;title=AudioTitles.file(AudioTitles.base(a.name),tr(selection==null?"Capítulo":"Selecção"),part,1);final int token=++generation;
        SpeechEvents.State active=SpeechEvents.state;if(active.active&&!active.paused)ReadAloudService.control(a,ReadAloudService.PAUSE,active.token);
        progress=a.text("A preparar o texto…",14);progressDialog=new UiDialog.Builder(a).setTitle("Guardar narração offline").setMessage(profile.online?"Usa a voz online escolhida para gerar áudio desta parte. É necessária Internet.":"Gera áudio desta parte com a voz instalada escolhida.").setView(progress).setNegativeButton("Cancelar",(d,w)->cancel()).create();progressDialog.setOnCancelListener(d->cancel());progressDialog.show();
        worker.execute(()->{
            try{
                String source,bookTitle=AudioTitles.base(a.name),author="",chapterTitle=tr("Selecção");
                if(selection!=null){source=selection;String shown=a.getIntent().getStringExtra("study_title");if(shown!=null&&!shown.trim().isEmpty())bookTitle=AudioTitles.clean(shown);}
                else try(SpeechDocument document=new SpeechDocument(a.getApplicationContext(),a.uri,a.name,a.mime,()->!valid(token))){source=document.part(part);bookTitle=document.title();author=document.author();chapterTitle=document.partTitle(part,source);}
                bookTitle=AudioTitles.book(bookTitle,a.name,a.getIntent().getStringExtra("study_title"));metadata=new WavInfo(AudioTitles.track(bookTitle,chapterTitle,part,1),bookTitle,author,part+1);title=AudioTitles.file(bookTitle,chapterTitle,part,1);
                if(source.length()>MAX_TEXT)throw new IOException("Esta parte ultrapassa 24000 caracteres. Escolha uma selecção menor para guardar em áudio.");
                final String spoken=SpeechText.prepare(source).text;
                if(spoken.trim().isEmpty())throw new IOException("Esta parte não tem texto extraível para narrar.");
                prepared=temporary("narration-");writer=new WaveJoiner(prepared);
                a.main.post(()->{if(!valid(token))return;text=spoken;int at=0;while(at<text.length()){at=SpeechChunks.naturalEnd(text,at,SpeechPolicy.chunkLimit(profile.rate));total++;}initEngine(token);});
            }catch(Exception|OutOfMemoryError error){a.main.post(()->{if(valid(token))fail(error);});}
        });
    }
    void initEngine(int token){
        tts=SpeechEngines.create(a.getApplicationContext(),status->a.main.post(()->{
            if(!valid(token))return;
            try{
                if(status!=TextToSpeech.SUCCESS)throw new IOException("Motor de voz indisponível.");
                Voice voice=SpeechVoices.resolve(tts.getVoices(),profile.online,profile.language,profile.voice);
                if(voice==null)throw new IOException("A voz escolhida não está disponível. Escolha ou instale outra voz.");
                SpeechVoices.apply(tts,voice,profile.rate);tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    public void onStart(String id){}
                    public void onDone(String id){a.main.post(()->{if(valid(token)&&id.equals(currentId)){currentId="";a.main.removeCallbacks(timeout);append(token);}});}
                    public void onError(String id){a.main.post(()->{if(valid(token)&&id.equals(currentId))fail(new IOException("O motor não conseguiu gerar o áudio com esta voz."));});}
                });
                next(token);
            }catch(Exception error){fail(error);}
        }),profile.engine);
    }
    File chunk;
    void next(int token){
        if(!valid(token))return;
        if(offset>=text.length()){
            progress.setText(tr("A concluir o áudio…"));
            worker.execute(()->{try{writer.finish(()->!valid(token),metadata);writer.close();writer=null;a.main.post(()->{if(valid(token)){stopEngine();if(progressDialog!=null)progressDialog.dismiss();progressDialog=null;readyToSave=true;if(a.resumed)showReady();}});}catch(Exception error){a.main.post(()->{if(valid(token))fail(error);});}});return;
        }
        nextEnd=SpeechChunks.naturalEnd(text,offset,Math.min(SpeechPolicy.chunkLimit(profile.rate),TextToSpeech.getMaxSpeechInputLength()-1));
        String value=text.substring(offset,nextEnd);chunk=temporary("chunk-");currentId="export-"+token+"-"+completed;
        progress.setText(tr("A gerar áudio…")+" "+(completed+1)+" / "+total);
        Bundle parameters=new Bundle();parameters.putString(profile.online?TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS:TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS,"true");
        try{
            if(tts.synthesizeToFile(value,parameters,chunk,currentId)!=TextToSpeech.SUCCESS)throw new IOException("Esta voz não permite guardar áudio neste motor.");
            a.main.removeCallbacks(timeout);a.main.postDelayed(timeout,SpeechPolicy.timeout(value.length(),profile.rate));
        }catch(Exception error){fail(error);}
    }
    void append(int token){
        final File source=chunk;final int end=nextEnd;
        worker.execute(()->{try{writer.append(source,()->!valid(token));source.delete();owned.remove(source);a.main.post(()->{if(valid(token)){offset=end;completed++;next(token);}});}catch(Exception error){a.main.post(()->{if(valid(token))fail(error);});}});
    }
    final Runnable timeout=()->{if(busy&&!currentId.isEmpty())fail(new IOException("A voz não respondeu durante a geração de áudio."));};
    void showReady(){
        if(!readyToSave||closed||a.dead||a.isFinishing())return;readyToSave=false;
        readyDialog=new UiDialog.Builder(a).setTitle("Narração pronta").setMessage("Guarde o WAV para ouvir sem Internet. O ficheiro contém esta narração; não instala o modelo da voz.").setPositiveButton("Guardar WAV",(d,w)->chooseDestination()).setNegativeButton("Cancelar",(d,w)->cancel()).create();readyDialog.setOnCancelListener(d->cancel());readyDialog.show();
    }
    void chooseDestination(){
        if(prepared==null||!prepared.isFile()){fail(new IOException("Áudio temporário indisponível. Gere novamente a narração."));return;}
        try{awaitingDestination=true;a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/wav").putExtra(Intent.EXTRA_TITLE,title),REQUEST);}catch(Exception error){awaitingDestination=false;fail(error);}
    }
    void result(int request,int result,Intent data){
        if(request!=REQUEST)return;awaitingDestination=false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null){cancel();return;}
        if(prepared==null||!prepared.isFile()){fail(new IOException("Áudio temporário indisponível. Gere novamente a narração."));return;}
        final int token=generation;final File source=prepared;final Uri target=data.getData();
        worker.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=a.getContentResolver().openOutputStream(target,"wt")){
            if(out==null)throw new IOException("Destino indisponível.");byte[] buffer=new byte[65536];int count;
            while((count=in.read(buffer))!=-1){if(!valid(token))throw new InterruptedIOException();out.write(buffer,0,count);}out.flush();
            a.main.post(()->{if(valid(token)){try{a.getContentResolver().takePersistableUriPermission(target,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}busy=false;cleanup();new UiDialog.Builder(a).setTitle("Narração guardada").setMessage("O áudio pode ser ouvido sem Internet com a voz usada na geração.").setPositiveButton("Ouvir áudio",(d,w)->{try{Intent open=new Intent(a,MediaActivity.class).setDataAndType(target,"audio/wav").putExtra("name",title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);open.setClipData(ClipData.newRawUri(title,target));a.startActivity(open);}catch(Exception error){AppDiagnostics.record(a,"speech-export-play",error);}}).setNegativeButton("Fechar",null).show();}});
        }catch(Exception error){a.main.post(()->{if(valid(token))fail(error);});}});
    }
    void saveState(Bundle state){if(awaitingDestination&&prepared!=null&&prepared.isFile()){state.putString("speech_export_file",prepared.getPath());state.putString("speech_export_title",title);}}
    void restore(Bundle state){
        if(state==null)return;String path=state.getString("speech_export_file","");if(path.isEmpty())return;
        try{File file=new File(path).getCanonicalFile();if(!file.isFile()||!file.getName().matches("narration-[0-9a-f-]+\\.wav")||!DiskWalker.inside(directory().getCanonicalFile(),file)||file.length()>WaveJoiner.MAX_BYTES+16384)return;prepared=file;owned.add(file);used=true;busy=true;awaitingDestination=true;cancelled=false;title=state.getString("speech_export_title","Seeky.wav");generation++;}catch(IOException ignored){}
    }
    void resume(){if(readyToSave)showReady();}
    void fail(Throwable error){AppDiagnostics.record(a,"speech-audio-export",error);String message=error.getMessage()==null?"Não foi possível guardar esta narração.":error.getMessage();cancel();if(!a.dead&&!closed)new UiDialog.Builder(a).setTitle("Narração indisponível").setMessage(message).setPositiveButton("Fechar",null).show();}
    void stopEngine(){a.main.removeCallbacks(timeout);currentId="";if(tts!=null){tts.stop();tts.shutdown();tts=null;}}
    void cancel(){cancelled=true;generation++;busy=false;awaitingDestination=false;readyToSave=false;stopEngine();if(progressDialog!=null){progressDialog.dismiss();progressDialog=null;}if(readyDialog!=null){readyDialog.dismiss();readyDialog=null;}cleanup();}
    void cleanup(){if(!used||worker.isShutdown())return;worker.execute(()->{if(writer!=null){writer.close();writer=null;}synchronized(owned){for(File file:owned){file.delete();new File(file.getPath()+".part").delete();}owned.clear();}prepared=null;});}
    void close(){if(closed)return;if(a.isChangingConfigurations()&&awaitingDestination&&prepared!=null)owned.remove(prepared);cancel();closed=true;worker.shutdown();}
}
