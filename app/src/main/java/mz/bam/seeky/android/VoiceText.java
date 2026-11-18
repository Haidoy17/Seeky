package mz.bam.seeky.android;
import android.content.*;import org.json.*;import java.util.*;
/** One immutable dictionary per saved narration; live reading uses the current dictionary. */
final class VoiceText {
 static final String PREF="pronunciation";private static String cached="";private static List<PronunciationRules.Rule> rules=Collections.emptyList();
 static synchronized List<PronunciationRules.Rule> rules(Context c){String json=c.getSharedPreferences(PREF,0).getString("rules","[]");if(!json.equals(cached)){try{rules=parse(json);}catch(Exception e){rules=Collections.emptyList();AppDiagnostics.record(c,"pronunciation",e);}cached=json;}return rules;}
 static List<PronunciationRules.Rule> parse(String json){try{if(json.length()>65536)throw new IllegalArgumentException("Dicionário demasiado extenso.");JsonBudget.check(json,6,200);JSONArray values=new JSONArray(json);if(values.length()>100)throw new IllegalArgumentException("Use até 100 pronúncias.");List<PronunciationRules.Rule> next=new ArrayList<>();for(int i=0;i<values.length();i++){JSONObject r=values.getJSONObject(i);next.add(new PronunciationRules.Rule(r.getString("word"),r.getString("say")));}return Collections.unmodifiableList(next);}catch(JSONException e){throw new IllegalArgumentException("Dicionário inválido.",e);}}
 static SpeechText.Prepared prepare(Context c,String source,String language){return prepare(source,language,rules(c));}
 static SpeechText.Prepared prepare(String source,String language,List<PronunciationRules.Rule> rules){return PronunciationRules.apply(SpeechText.forVoice(source,language,rules),rules);}
 static int fit(String source,int start,int end,int budget,String language,List<PronunciationRules.Rule> rules){int at=end;for(int tries=0;tries<24;tries++){String part=source.substring(start,at);int length=prepare(part,language,rules).text.length();if(length<=budget||at-start<=2)return at;int size=Math.max(2,(int)((long)(at-start)*budget/Math.max(1,length)));int next=SpeechChunks.naturalEnd(source,start,Math.max(2,Math.min(at-start-1,size)));if(next>=at)next=Math.max(start+1,at-1);if(next<source.length()&&next>start&&Character.isHighSurrogate(source.charAt(next-1))&&Character.isLowSurrogate(source.charAt(next)))next--;at=Math.max(start+1,next);}throw new IllegalArgumentException("A pronúncia personalizada excede o limite da voz.");}
 private VoiceText(){}
}
