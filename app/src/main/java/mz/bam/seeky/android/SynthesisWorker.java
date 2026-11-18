package mz.bam.seeky.android;

import android.content.Context;
import android.os.*;
import android.speech.tts.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Worker-only synthesis; a cancelled job never starts another utterance. */
final class SynthesisWorker implements AutoCloseable {
    final Handler main=new Handler(Looper.getMainLooper());final BooleanSupplier cancelled;
    final java.util.List<PronunciationRules.Rule> pronunciation;
    volatile boolean closed;volatile TextToSpeech engine;volatile String current="",error="";volatile CountDownLatch done;volatile int errorCode;
    SynthesisWorker(Context context,SpeechProfile profile,BooleanSupplier cancelled)throws IOException{this(context,profile,cancelled,VoiceText.rules(context));}
    SynthesisWorker(Context context,SpeechProfile profile,BooleanSupplier cancelled,java.util.List<PronunciationRules.Rule> rules)throws IOException{
        this.cancelled=cancelled;pronunciation=rules;CountDownLatch init=new CountDownLatch(1);int[] status={TextToSpeech.ERROR};
        main.post(()->{if(closed||cancelled.getAsBoolean()){init.countDown();return;}engine=SpeechEngines.create(context,s->{status[0]=s;init.countDown();},profile.engine);if(closed){engine.shutdown();engine=null;}});
        try{await(init,30000);if(status[0]!=TextToSpeech.SUCCESS||engine==null)throw new IOException("Motor de voz indisponível.");Voice voice=SpeechVoices.resolve(engine.getVoices(),profile.online,profile.language,profile.voice);if(voice==null)throw new IOException("Voz indisponível. Instale os dados ou escolha outra voz.");SpeechVoices.apply(engine,voice,profile.rate);engine.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){finish(id,"");}public void onError(String id){finish(id,"A voz não conseguiu gerar áudio.");}public void onError(String id,int code){if(id.equals(current))errorCode=code;finish(id,"A voz não conseguiu gerar áudio ("+code+").");}void finish(String id,String message){if(id.equals(current)){error=message;CountDownLatch latch=done;if(latch!=null)latch.countDown();}}});}catch(IOException|RuntimeException e){close();throw e;}
    }
    void await(CountDownLatch latch,long timeout)throws IOException {long deadline=SystemClock.elapsedRealtime()+timeout;try{while(!latch.await(200,TimeUnit.MILLISECONDS)){if(cancelled.getAsBoolean())throw new InterruptedIOException();if(SystemClock.elapsedRealtime()>=deadline)throw new IOException("A voz demorou demasiado a responder.");}if(cancelled.getAsBoolean())throw new InterruptedIOException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}}
    void synthesize(String text,File destination,SpeechProfile profile)throws IOException{
        if(cancelled.getAsBoolean())throw new InterruptedIOException();text=VoiceText.prepare(text,profile.language,pronunciation).text;done=new CountDownLatch(1);error="";errorCode=0;current=java.util.UUID.randomUUID().toString();SpeechVoices.verify(engine,profile);Bundle parameters=SpeechParameters.create(profile.online);if(engine.synthesizeToFile(text,parameters,destination,current)!=TextToSpeech.SUCCESS)throw new IOException("Esta voz não permite guardar áudio.");await(done,SpeechPolicy.timeout(text.length(),profile.rate));if(!error.isEmpty())throw new IOException(error);
    }
    public void close(){closed=true;TextToSpeech old=engine;engine=null;current="";if(old!=null){try{old.stop();old.shutdown();}catch(RuntimeException ignored){}}}
}
