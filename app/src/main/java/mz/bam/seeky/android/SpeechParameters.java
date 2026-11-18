package mz.bam.seeky.android;
import android.os.Bundle;import android.speech.tts.TextToSpeech;
/** The same network-only/embedded-only request policy is used for samples, reading and WAVs. */
final class SpeechParameters {
 static Bundle create(boolean online){Bundle p=new Bundle();p.putString(online?TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS:TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS,"true");if(online){p.putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS,"60000");p.putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT,"2");}return p;}
}
