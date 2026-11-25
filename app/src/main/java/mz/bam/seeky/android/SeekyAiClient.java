package mz.bam.seeky.android;

import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;

/** HTTPS only, fixed provider hosts, bounded responses, explicit cancellation, no automatic retries. */
final class SeekyAiClient {
 interface Transport {String post(String provider,String key,JSONObject payload,Call call)throws Exception;}
 static class HttpFailure extends IOException {final int status;HttpFailure(int status,String message){super(message);this.status=status;}}
 static final class Call {final AtomicBoolean cancelled=new AtomicBoolean();volatile HttpURLConnection connection;void cancel(){cancelled.set(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}void check()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}}
 final Transport transport;SeekyAiClient(){this(new Network());}SeekyAiClient(Transport transport){this.transport=transport;}
 SeekyAiCore.Result ask(String provider,String key,String context,String label,List<SeekyAiCore.Message> history,String question,boolean english,Call call)throws Exception{call.check();JSONObject body=SeekyAiCore.request(provider,context,label,history,question,english);String raw=transport.post(provider,key,body,call);call.check();try{JsonBudget.check(raw,32,5000);}catch(IllegalArgumentException e){throw new IOException("Resposta do serviço inválida ou demasiado complexa.");}return SeekyAiCore.response(provider,new JSONObject(raw));}
 /** 5.51: a question over labelled excerpts of several documents (SeekyAiDeep.block) or partial summaries. */
 SeekyAiCore.Result askMaterials(String provider,String key,String block,List<SeekyAiCore.Message> history,String question,boolean english,Call call)throws Exception{call.check();JSONObject body=SeekyAiCore.requestMaterials(provider,block,history,question,english);String raw=transport.post(provider,key,body,call);call.check();try{JsonBudget.check(raw,32,5000);}catch(IllegalArgumentException e){throw new IOException("Resposta do serviço inválida ou demasiado complexa.");}return SeekyAiCore.response(provider,new JSONObject(raw));}
 static final String BASE="https://generativelanguage.googleapis.com/v1beta/models";
 static String endpoint(String provider){return endpoint(provider,SeekyAiModels.current());}
 /** The model is a parameter (never fixed here); only safe model names reach the URL. */
 static String endpoint(String provider,String model){if(!SeekyAiCore.GEMINI.equals(provider))throw new IllegalArgumentException("Apenas Gemini está disponível.");String m=SeekyAiModels.clean(model);if(m.isEmpty())throw new IllegalArgumentException("Modelo inválido.");return BASE+"/"+m+":generateContent";}
 /** A model Google retired or this key cannot use. */
 static final class ModelMissing extends HttpFailure {final boolean quota;ModelMissing(int status,String message,boolean quota){super(status,message);this.quota=quota;}}
 /** The model refused the thinking setting (HTTP 400 about thinking): ask again without it. */
 static final class ThinkingRefused extends HttpFailure {ThinkingRefused(String message){super(400,message);}}
 /** A free-tier quota of zero for this model ("limit: 0"): the key cannot use it without a paid plan. */
 static boolean noFreeQuota(int code,String raw){return code==429&&raw!=null&&raw.matches("(?s).*limit:\\s*0\\b.*");}
 /** The request for one model: Gemini 4 thinks at the LOW level with room for thinking plus the answer. */
 static JSONObject adapt(JSONObject payload,String model,boolean thinking)throws JSONException{JSONObject copy=new JSONObject(payload.toString());if(!thinking||!SeekyAiModels.thinks(model))return copy;JSONObject config=copy.optJSONObject("generationConfig");if(config==null){config=new JSONObject();copy.put("generationConfig",config);}config.remove("thinkingBudget");config.put("thinkingConfig",new JSONObject().put("thinkingLevel",SeekyAiModels.THINKING));config.put("maxOutputTokens",Math.max(config.optInt("maxOutputTokens",0),2048));return copy;}
 static boolean missingModel(int code,String raw){if(code==404)return true;if(code!=400||raw==null)return false;String r=raw.toLowerCase(Locale.ROOT);return r.contains("is not found")||r.contains("not supported for generatecontent")||r.contains("no longer available")||r.contains("has been deprecated")||r.contains("was deprecated");}
 static String status(int code){return failure(code,"");}
 static String summary(int code,String raw){
  String reason="";try{JsonBudget.check(raw,16,1000);JSONObject e=new JSONObject(raw).optJSONObject("error");if(e!=null){reason=e.optString("status","");JSONArray ds=e.optJSONArray("details");if(ds!=null)for(int n=0;n<ds.length();n++){JSONObject d=ds.optJSONObject(n);if(d!=null&&d.has("reason")){reason=d.optString("reason","");break;}}}}catch(Exception ignored){}
  if(reason.equals("API_KEY_INVALID")||reason.equals("API_KEY_EXPIRED"))return "Gemini: chave inválida ou expirada. Crie uma nova chave no Google AI Studio.";
  if(reason.contains("BLOCKED")||reason.equals("API_KEY_SERVICE_BLOCKED"))return "Gemini: chave bloqueada ou restrita. Verifique as restrições e se a chave foi revogada ou exposta no Google AI Studio.";
  if(reason.equals("SERVICE_DISABLED"))return "Gemini: active a Generative Language API no projecto desta chave.";
  if(code==401||code==403)return "Gemini: acesso recusado (HTTP "+code+"). Verifique a chave, permissões do projecto, restrições da aplicação e região da conta.";
  if(code==429)return "Gemini: quota ou limite de pedidos atingido (HTTP 429). Verifique a quota gratuita no AI Studio e tente mais tarde.";
  if(code==400)return "Gemini: pedido recusado (HTTP 400). Verifique se a chave pertence ao Gemini e se a conta/região tem acesso ao modelo.";
  if(code==404)return "Gemini: este modelo foi descontinuado ou não está disponível para esta chave. O Seeky tenta outro modelo automaticamente.";
  if(code>=500)return "Gemini: serviço temporariamente indisponível (HTTP "+code+"). Tente mais tarde.";
  return "Gemini: pedido recusado (HTTP "+code+").";
 }
 static String failure(int code,String raw){return failure(code,raw,"");}
 static String failure(int code,String raw,String key){String message="",state="";try{JsonBudget.check(raw,16,1000);JSONObject e=new JSONObject(raw).optJSONObject("error");if(e!=null){message=e.optString("message","");state=e.optString("status","");}}catch(Exception ignored){}
  if(code==404)return summary(code,raw);String description=summary(code,raw)+"\nHTTP "+code;if(state.matches("[A-Z_]{1,64}"))description+=" · "+state;if(!message.isEmpty())description+="\nGoogle: "+redact(message,key);return description;
 }
 static String redact(String text,String key){if(text==null)return "";if(key!=null&&!key.isEmpty())text=text.replace(key,"[chave ocultada]");text=text.replaceAll("(?i)(?:AIza[A-Za-z0-9_-]{10,}|AQ\\.[A-Za-z0-9._~+/=:-]{10,})","[chave ocultada]").replaceAll("(?i)(?:x-goog-api-key|api[_ -]?key|key|authorization)\\s*[:=]\\s*[^\\s,;]+","[credencial ocultada]").replaceAll("[\\p{Cc}&&[^\\n\\t]]","");if(text.length()>1600)text=text.substring(0,1600)+"…";return text;}
 static String errorBody(HttpURLConnection c){try(InputStream in=c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)return "";byte[] b=new byte[1024];for(int n;(n=in.read(b))!=-1;){if(out.size()+n>16384)return "";out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
 static String normaliseKey(String value){return value==null?"":value.trim();}
 static boolean validKey(String key){return key!=null&&key.length()>=16&&key.length()<=2048&&key.matches("[A-Za-z0-9._~+/=:-]+");}
 /** Testar e guardar: ask the API which models this key has, keep the text ones, then test a short answer. */
 static void testConnection(String key,Call call)throws Exception{Network n=new Network();try{SeekyAiModels.list(n.models(key,call));}catch(HttpFailure e){if(e.status==400||e.status==401||e.status==403)throw e;}catch(InterruptedIOException e){throw e;}catch(Exception ignored){}new SeekyAiClient(n).verifyGeneration(key,call);}
 /** Names of the models that support generateContent, from the API's list (several pages at most). */
 static List<String> generating(String raw)throws JSONException{List<String> out=new ArrayList<>();JSONArray models=new JSONObject(raw).optJSONArray("models");if(models==null)return out;for(int i=0;i<models.length();i++){JSONObject m=models.optJSONObject(i);if(m==null)continue;JSONArray ways=m.optJSONArray("supportedGenerationMethods");boolean text=false;if(ways!=null)for(int k=0;k<ways.length();k++)if("generateContent".equals(ways.optString(k)))text=true;String name=SeekyAiModels.clean(m.optString("name"));if(text&&!name.isEmpty())out.add(name);}return out;}
 void verifyGeneration(String key,Call call)throws Exception{call.check();JSONObject payload=SeekyAiCore.request(SeekyAiCore.GEMINI,"","",Collections.emptyList(),"Responde apenas: OK.",false);payload.getJSONObject("generationConfig").put("maxOutputTokens",128);String raw=transport.post(SeekyAiCore.GEMINI,key,payload,call);call.check();try{JsonBudget.check(raw,32,5000);}catch(IllegalArgumentException e){throw new IOException("Resposta de teste inválida ou demasiado complexa.");}SeekyAiCore.response(SeekyAiCore.GEMINI,new JSONObject(raw));}

 interface Connections {HttpURLConnection open(URL url)throws IOException;}
 static final class Network implements Transport {
  final Connections connections;Network(){this(url->(HttpURLConnection)url.openConnection());}Network(Connections connections){this.connections=connections;}
  void verify(String key,Call call)throws Exception{new SeekyAiClient(this).verifyGeneration(key,call);}
  /** GET the key's model list (key in a header, never in the address). */
  List<String> models(String key,Call call)throws Exception{call.check();if(!validKey(key))throw new IOException("Configure uma chave válida.");List<String> all=new ArrayList<>();String token="";
   for(int page=0;page<4;page++){HttpURLConnection c=connections.open(new URL(BASE+"?pageSize=100"+(token.isEmpty()?"":"&pageToken="+URLEncoder.encode(token,"UTF-8"))));call.connection=c;
    try{c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestMethod("GET");c.setRequestProperty("Accept","application/json");c.setRequestProperty("x-goog-api-key",key);call.check();int code=c.getResponseCode();if(code<200||code>=300)throw new HttpFailure(code,failure(code,errorBody(c),key));
     String raw;try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;){call.check();if(out.size()+n>1048576)throw new IOException("Lista de modelos demasiado grande.");out.write(b,0,n);}raw=new String(out.toByteArray(),StandardCharsets.UTF_8);}
     all.addAll(generating(raw));token=new JSONObject(raw).optString("nextPageToken","");if(token.isEmpty()||!token.matches("[A-Za-z0-9_\\-=+/.]{1,512}"))break;}
    catch(SocketTimeoutException e){throw new IOException("A lista de modelos demorou demasiado.");}catch(UnknownHostException e){throw new IOException("Sem ligação ao serviço. Verifique a Internet.");}finally{call.connection=null;c.disconnect();}}
   return all;}
  /** Tries the models in order; a retired model (404) passes to the next one, any other failure stops. */
  public String post(String provider,String key,JSONObject payload,Call call)throws Exception{call.check();if(!validKey(key))throw new IOException("Configure uma chave válida.");List<String> models=SeekyAiModels.order();List<String> tried=new ArrayList<>();
   boolean quota=false;
   for(String model:models){tried.add(model);try{String raw;try{raw=postTo(provider,model,key,adapt(payload,model,true),call);}catch(ThinkingRefused e){raw=postTo(provider,model,key,adapt(payload,model,false),call);}SeekyAiModels.worked(model,models.get(0),quota);return raw;}catch(ModelMissing e){if(model.equals(models.get(0)))quota=e.quota;SeekyAiModels.missing(model);call.check();}}
   throw new HttpFailure(404,SeekyAiModels.none(tried));
  }
  String postTo(String provider,String model,String key,JSONObject payload,Call call)throws Exception{HttpURLConnection c=connections.open(new URL(endpoint(provider,model)));call.connection=c;long deadline=System.nanoTime()+120000000000L;
   try{c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-store");c.setConnectTimeout(15000);c.setReadTimeout(75000);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");c.setRequestProperty("x-goog-api-key",key);c.setDoOutput(true);byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);call.check();try(OutputStream out=c.getOutputStream()){out.write(body);}call.check();int code=c.getResponseCode();if(code<200||code>=300){String raw=errorBody(c);if(missingModel(code,raw))throw new ModelMissing(code,summary(404,raw),false);if(noFreeQuota(code,raw))throw new ModelMissing(code,summary(429,raw),true);if(code==400&&raw.toLowerCase(Locale.ROOT).contains("thinking"))throw new ThinkingRefused(failure(code,raw,key));throw new HttpFailure(code,failure(code,raw,key));}try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;){call.check();if(System.nanoTime()>deadline)throw new SocketTimeoutException();if(out.size()+n>262144)throw new IOException("Resposta acima do limite. Peça uma resposta mais curta.");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
   catch(SocketTimeoutException e){throw new IOException("A resposta demorou demasiado. Pode tentar novamente.");}catch(UnknownHostException e){throw new IOException("Sem ligação ao serviço. Verifique a Internet.");}finally{call.connection=null;c.disconnect();}
  }
 }
}
