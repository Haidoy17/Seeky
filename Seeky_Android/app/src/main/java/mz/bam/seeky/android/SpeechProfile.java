package mz.bam.seeky.android;

import android.content.SharedPreferences;

/** A confirmed choice. Preview changes remain separate until the user applies them. */
final class SpeechProfile {
    final String engine,voice,language;
    final boolean online;
    final float rate;
    SpeechProfile(String engine,String voice,String language,boolean online,float rate){this.engine=engine;this.voice=voice;this.language=language;this.online=online;this.rate=rate;}
    void save(SharedPreferences prefs){prefs.edit().putString("speech_engine",engine).putString("speech_voice",voice).putString("speech_language",language).putBoolean("speech_online",online).putFloat("speech_rate",rate).putString(SpeechVoices.key(engine,language,online),voice).apply();}
}
