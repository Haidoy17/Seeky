package mz.bam.seeky.android;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;

/** Gemini models are never fixed: order, filtering, fallback when Google retires a model, and the key's model list. No real network. */
public final class SeekyAiModelsTest {
 static int checks;static void ok(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static final String KEY="test-only-personal-api-key-not-shipped";
 static final class Connection extends HttpURLConnection {
  int code=200;byte[] reply;final ByteArrayOutputStream sent=new ByteArrayOutputStream();Connection(URL url,int code,String reply){super(url);this.code=code;this.reply=reply.getBytes(StandardCharsets.UTF_8);}
  public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}public int getResponseCode(){return code;}public OutputStream getOutputStream(){return sent;}
  public InputStream getInputStream(){return new ByteArrayInputStream(reply);}public InputStream getErrorStream(){return new ByteArrayInputStream(reply);}
 }
 static final String OK="{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}";
 static final String GONE="{\"error\":{\"code\":404,\"message\":\"models/gemini-2.5-flash-lite is not found for API version v1beta, or is not supported for generateContent.\",\"status\":\"NOT_FOUND\"}}";
 static void reset(){SeekyAiModels.restore("","",Collections.emptyList());SeekyAiModels.takeNotice();SeekyAiModels.store=null;}
 public static void main(String[] args)throws Exception{
  ok(SeekyAiModels.DEFAULT.equals("gemini-3.5-flash-lite"),"default model is gemini-3.5-flash-lite");
  ok(SeekyAiModels.clean("models/gemini-3.5-flash").equals("gemini-3.5-flash")&&SeekyAiModels.clean("../x?key=1").isEmpty(),"model names are cleaned and unsafe names refused");
  List<String> ranked=SeekyAiModels.rank(Arrays.asList("models/gemini-2.5-flash","models/gemini-3.5-pro","models/gemini-3.5-flash","models/gemini-2.5-flash-preview-tts","models/text-embedding-004","models/gemini-3.5-flash-lite-preview-09","models/gemini-3.5-flash-lite","models/gemini-2.0-flash-live"));
  ok(ranked.equals(Arrays.asList("gemini-3.5-flash-lite","gemini-3.5-flash-lite-preview-09","gemini-3.5-flash","gemini-2.5-flash","gemini-3.5-pro")),"text models ranked lite, flash, then pro; speech, embedding and live left out: "+ranked);
  reset();
  ok(SeekyAiModels.order().equals(Arrays.asList("gemini-3.5-flash-lite","gemini-3.5-flash","gemini-4-hydrogen")),"without a list: default, flash, then the newest Gemini 4 Hydrogen: "+SeekyAiModels.order());
  ok(SeekyAiClient.endpoint(SeekyAiCore.GEMINI).equals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"),"the request uses the chosen model, not a fixed one");
  SeekyAiModels.list(Arrays.asList("models/gemini-3.5-flash","models/gemini-4.0-flash-lite"));
  ok(SeekyAiModels.order().equals(Arrays.asList("gemini-4.0-flash-lite","gemini-3.5-flash")),"the key's own list rules out models it does not have: "+SeekyAiModels.order());
  SeekyAiModels.choose("gemini-3.5-flash");ok(SeekyAiModels.order().get(0).equals("gemini-3.5-flash"),"the user's choice is tried first");
  // A retired model (404) moves on to the next one and the user is told in plain words.
  reset();List<String> urls=new ArrayList<>();
  SeekyAiClient.Network fallback=new SeekyAiClient.Network(url->{urls.add(url.toString());return url.toString().contains("gemini-3.5-flash-lite")?new Connection(url,404,GONE):new Connection(url,200,OK);});
  String raw=fallback.post(SeekyAiCore.GEMINI,KEY,new JSONObject().put("contents",new JSONArray()),new SeekyAiClient.Call());
  ok(raw.contains("OK")&&urls.size()==2&&urls.get(1).contains("/gemini-3.5-flash:generateContent"),"after a 404 the next model answers: "+urls);
  String notice=SeekyAiModels.takeNotice();
  ok(notice.contains("descontinuado")&&notice.contains("gemini-3.5-flash")&&!notice.contains("NOT_FOUND"),"the switch is explained without Google's raw text: "+notice);
  ok(SeekyAiModels.working.equals("gemini-3.5-flash")&&SeekyAiModels.order().get(0).equals("gemini-3.5-flash"),"the model that worked is used first next time");
  ok(SeekyAiModels.takeNotice().isEmpty(),"the notice is shown once");
  // 400 "is not found / not supported for generateContent" also counts as a retired model.
  reset();urls.clear();
  SeekyAiClient.Network old=new SeekyAiClient.Network(url->{urls.add(url.toString());return urls.size()==1?new Connection(url,400,GONE.replace("404","400").replace("NOT_FOUND","INVALID_ARGUMENT")):new Connection(url,200,OK);});
  old.post(SeekyAiCore.GEMINI,KEY,new JSONObject(),new SeekyAiClient.Call());ok(urls.size()==2,"HTTP 400 for an unsupported model also moves on");
  // Every model gone: one clear message, no raw Google text.
  reset();urls.clear();
  SeekyAiClient.Network none=new SeekyAiClient.Network(url->{urls.add(url.toString());return new Connection(url,404,GONE);});
  try{none.post(SeekyAiCore.GEMINI,KEY,new JSONObject(),new SeekyAiClient.Call());throw new AssertionError("should fail");}catch(SeekyAiClient.HttpFailure e){ok(e.status==404&&e.getMessage().contains("nenhum modelo disponível")&&e.getMessage().contains("Testar e guardar")&&!e.getMessage().contains("v1beta"),"no model left: plain message: "+e.getMessage());}
  ok(urls.size()==3,"only the known models are tried, without endless retries");
  // A wrong key is not a model problem: one attempt only.
  reset();urls.clear();String bad=new JSONObject().put("error",new JSONObject().put("status","INVALID_ARGUMENT").put("message","API key not valid").put("details",new JSONArray().put(new JSONObject().put("reason","API_KEY_INVALID")))).toString();
  SeekyAiClient.Network badKey=new SeekyAiClient.Network(url->{urls.add(url.toString());return new Connection(url,400,bad);});
  try{badKey.post(SeekyAiCore.GEMINI,KEY,new JSONObject(),new SeekyAiClient.Call());throw new AssertionError();}catch(SeekyAiClient.HttpFailure e){ok(urls.size()==1&&e.getMessage().contains("inválida"),"an invalid key stops at once with its own message");}
  // The key's model list: only generateContent, key in a header and never in the address.
  String list=new JSONObject().put("models",new JSONArray()
   .put(new JSONObject().put("name","models/gemini-3.5-flash-lite").put("supportedGenerationMethods",new JSONArray().put("generateContent").put("countTokens")))
   .put(new JSONObject().put("name","models/text-embedding-004").put("supportedGenerationMethods",new JSONArray().put("embedContent")))
   .put(new JSONObject().put("name","models/gemini-3.5-flash").put("supportedGenerationMethods",new JSONArray().put("generateContent")))).toString();
  final Connection[] seen={null};
  SeekyAiClient.Network lister=new SeekyAiClient.Network(url->{seen[0]=new Connection(url,200,list);return seen[0];});
  List<String> got=lister.models(KEY,new SeekyAiClient.Call());
  ok(got.equals(Arrays.asList("gemini-3.5-flash-lite","gemini-3.5-flash")),"only models with generateContent are offered: "+got);
  ok(!seen[0].getURL().toString().contains(KEY)&&seen[0].getURL().toString().startsWith("https://generativelanguage.googleapis.com/v1beta/models?")&&KEY.equals(seen[0].getRequestProperty("x-goog-api-key"))&&"GET".equals(seen[0].getRequestMethod()),"model list is a GET with the key in a header, not in the address");
  // Choices are kept.
  final String[] saved={null};SeekyAiModels.store=(c,w,av)->saved[0]=c+"|"+w+"|"+String.join(",",av);SeekyAiModels.list(got);SeekyAiModels.choose("gemini-3.5-flash");
  ok("gemini-3.5-flash||gemini-3.5-flash-lite,gemini-3.5-flash".equals(saved[0]),"choice, working model and list are saved: "+saved[0]);
  SeekyAiModels.list(Collections.singletonList("models/gemini-3.5-flash-lite"));ok(SeekyAiModels.chosen.isEmpty(),"a chosen model the key no longer has falls back to automatic");
  // Gemini 4 Hydrogen: offered by name, asked to think at the LOW level, and replaced when the key has no free quota for it.
  ok(SeekyAiModels.label("models/gemini-4-hydrogen").equals("Gemini 4 Hydrogen · mais recente")&&SeekyAiModels.thinks("gemini-4-hydrogen")&&!SeekyAiModels.thinks("gemini-3.5-flash"),"Hydrogen has a readable name and is known as a thinking model");
  JSONObject plain=new JSONObject().put("contents",new JSONArray()).put("generationConfig",new JSONObject().put("temperature",.35).put("maxOutputTokens",128));
  JSONObject hydro=SeekyAiClient.adapt(plain,"gemini-4-hydrogen",true);
  ok("LOW".equals(hydro.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))&&hydro.getJSONObject("generationConfig").getInt("maxOutputTokens")>=2048,"Hydrogen asks for LOW thinking with room for the answer");
  ok(!SeekyAiClient.adapt(plain,"gemini-3.5-flash-lite",true).getJSONObject("generationConfig").has("thinkingConfig")&&plain.getJSONObject("generationConfig").getInt("maxOutputTokens")==128,"other models' requests are unchanged and the original payload is not modified");
  reset();SeekyAiModels.list(Arrays.asList("models/gemini-4-hydrogen","models/gemini-3.5-flash-lite"));SeekyAiModels.choose("gemini-4-hydrogen");urls.clear();final List<String> bodies=new ArrayList<>();
  String quota=new JSONObject().put("error",new JSONObject().put("code",429).put("status","RESOURCE_EXHAUSTED").put("message","Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-4-hydrogen")).toString();
  SeekyAiClient.Network paid=new SeekyAiClient.Network(url->{urls.add(url.toString());Connection c=url.toString().contains("hydrogen")?new Connection(url,429,quota):new Connection(url,200,OK);return c;});
  paid.post(SeekyAiCore.GEMINI,KEY,plain,new SeekyAiClient.Call());
  String told=SeekyAiModels.takeNotice();
  ok(urls.get(0).contains("/gemini-4-hydrogen:generateContent")&&urls.get(1).contains("/gemini-3.5-flash-lite:")&&told.contains("Gemini 4 Hydrogen")&&told.contains("quota gratuita"),"Hydrogen without free quota: the next model answers and the reason is explained: "+told);
  ok(SeekyAiModels.chosen.isEmpty(),"the choice goes back to automatic after a key without quota for it");
  // An ordinary quota limit (not zero) is not a model problem: no switch.
  reset();SeekyAiModels.choose("gemini-4-hydrogen");urls.clear();
  SeekyAiClient.Network busy=new SeekyAiClient.Network(url->{urls.add(url.toString());return new Connection(url,429,quota.replace("limit: 0","limit: 20"));});
  try{busy.post(SeekyAiCore.GEMINI,KEY,plain,new SeekyAiClient.Call());throw new AssertionError();}catch(SeekyAiClient.HttpFailure e){ok(urls.size()==1&&e.status==429,"a normal rate limit stops without switching models");}
  // The thinking setting refused (HTTP 400): asked again once, without it.
  reset();SeekyAiModels.choose("gemini-4-hydrogen");urls.clear();final List<Connection> conns=new ArrayList<>();
  String refuse=new JSONObject().put("error",new JSONObject().put("code",400).put("status","INVALID_ARGUMENT").put("message","Invalid value at 'generation_config.thinking_config.thinking_level'")).toString();
  SeekyAiClient.Network strict=new SeekyAiClient.Network(url->{Connection c=new Connection(url,conns.isEmpty()?400:200,conns.isEmpty()?refuse:OK);conns.add(c);return c;});
  strict.post(SeekyAiCore.GEMINI,KEY,plain,new SeekyAiClient.Call());
  ok(conns.size()==2&&conns.get(0).sent.toString("UTF-8").contains("thinkingConfig")&&!conns.get(1).sent.toString("UTF-8").contains("thinkingConfig")&&conns.get(1).getURL().toString().contains("gemini-4-hydrogen"),"a refused thinking setting is retried once on the same model without it");
  reset();
  System.out.println("PASS "+checks+" Gemini model choice and fallback checks");
 }
}
