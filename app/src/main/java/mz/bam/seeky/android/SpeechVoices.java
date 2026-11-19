package mz.bam.seeky.android;

import android.speech.tts.*;
import java.util.*;

/** Shared voice catalogue for preview, playback and audio export. No inferred neural labels. */
final class SpeechVoices {
    /** Tom escolhido pelo utilizador (Leitura em voz alta → Tom), aplicado em leitura, amostras e WAV. */
    static volatile float pitch=1f;
    static void loadPitch(android.content.Context c){pitch=SpeechPolicy.pitch(c.getSharedPreferences("seeky",0).getInt("speech_pitch",100));}
    static boolean missing(Voice voice){return voice.getFeatures()!=null&&voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);}
    static boolean usable(Voice voice,boolean online,String language){return SpeechPolicy.accepts(online,language,voice.isNetworkConnectionRequired(),voice.getLocale().getLanguage(),missing(voice));}
    static List<Voice> list(Set<Voice> available,boolean online,String language){
        Map<String,Voice> unique=new LinkedHashMap<>();
        if(available!=null)for(Voice voice:available)if(voice.isNetworkConnectionRequired()==online&&voice.getLocale().getLanguage().equalsIgnoreCase(language)){Voice prior=unique.get(voice.getName());if(prior==null||missing(prior)&&!missing(voice)||missing(prior)==missing(voice)&&voice.getQuality()>prior.getQuality())unique.put(voice.getName(),voice);}
        List<Voice> voices=new ArrayList<>(unique.values());
        String device=Locale.getDefault().getCountry();
        // Qualidade primeiro; em empate, a variante regional do aparelho (pt-PT em Moçambique) e depois a menor latência.
        voices.sort((x,y)->{if(missing(x)!=missing(y))return missing(x)?1:-1;if(x.getQuality()!=y.getQuality())return Integer.compare(y.getQuality(),x.getQuality());int region=Integer.compare(SpeechPolicy.regionScore(y.getLocale().getCountry(),device,language),SpeechPolicy.regionScore(x.getLocale().getCountry(),device,language));if(region!=0)return region;if(x.getLatency()!=y.getLatency())return Integer.compare(x.getLatency(),y.getLatency());return x.getName().compareTo(y.getName());});
        return voices;
    }
    static Voice resolve(Set<Voice> available,boolean online,String language,String selected){
        List<Voice> voices=list(available,online,language);
        for(Voice voice:voices)if(usable(voice,online,language)&&(selected.isEmpty()||voice.getName().equals(selected)))return voice;
        return null;
    }
    static void apply(TextToSpeech engine,Voice voice,float rate){
        if(engine.setVoice(voice)!=TextToSpeech.SUCCESS||engine.getVoice()==null||!engine.getVoice().getName().equals(voice.getName())||engine.getVoice().isNetworkConnectionRequired()!=voice.isNetworkConnectionRequired())throw new IllegalStateException("Não foi possível activar a voz escolhida.");
        engine.setSpeechRate(Math.max(.5f,Math.min(2f,rate)));engine.setPitch(pitch);
    }
    static void verify(TextToSpeech engine,SpeechProfile profile){Voice actual=engine.getVoice();if(actual==null||!usable(actual,profile.online,profile.language)||!profile.voice.isEmpty()&&!actual.getName().equals(profile.voice))throw new IllegalStateException("O motor mudou a voz escolhida. Escolha novamente a voz.");}
    static String key(String engine,String language,boolean online){return "speech_choice_"+engine+"_"+language+"_"+(online?"network":"local");}
    private SpeechVoices(){}
}
