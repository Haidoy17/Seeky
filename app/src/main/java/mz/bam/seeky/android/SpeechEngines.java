package mz.bam.seeky.android;
import android.content.Context;import android.speech.tts.TextToSpeech;
/** Google uses the platform. Seeky's companion uses explicit, signature-protected IPC. */
final class SpeechEngines {
 static TextToSpeech create(Context c,TextToSpeech.OnInitListener listener,String engine){return new TextToSpeech(c,listener,engine);}
 private SpeechEngines(){}
}
