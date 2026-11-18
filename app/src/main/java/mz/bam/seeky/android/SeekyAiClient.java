package mz.bam.seeky.android;

import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;

/** HTTPS only, fixed provider hosts, bounded responses, explicit cancellation, no automatic retries. */
final class SeekyAiClient {
 interface Transport {String post(String provider,String key,JSONObject payload,Call call)throws Exception;}
 static final class HttpFailure extends IOException {final int status;HttpFailure(int status,String message){super(message);this.status=status;}}
 static final class Call {final AtomicBoolean cancelled=new AtomicBoolean();volatile HttpURLConnection connection;void cancel(){cancelled.set(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}void check()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}}
 final Transport transport;SeekyAiClient(){this(new Network());}SeekyAiClient(Transport transport){this.transport=transport;}
 SeekyAiCore.Result ask(String provider,String key,String context,String label,List<SeekyAiCore.Message> history,String question,boolean english,Call call)throws Exception{call.check();JSONObject body=SeekyAiCore.request(provider,context,label,history,question,english);String raw=transport.post(provider,key,body,call);call.check();try{JsonBudget.check(raw,32,5000);}catch(IllegalArgumentException e){throw new IOException("Resposta do serviço inválida ou demasiado complexa.");}return SeekyAiCore.response(provider,new JSONObject(raw));}
 static String endpoint(String provider){if(!SeekyAiCore.GEMINI.equals(provider))throw new IllegalArgumentException("Apenas Gemini está disponível.");return "https://generativelanguage.googleapis.com/v1beta/models/"+SeekyAiCore.GEMINI_MODEL+":generateContent";}
 static String status(int code){return failure(code,"");}
 static String summary(int code,String raw){
  String reason="";try{JsonBudget.check(raw,16,1000);JSONObject e=new JSONObject(raw).optJSONObject("error");if(e!=null){reason=e.optString("status","");JSONArray ds=e.optJSONArray("details");if(ds!=null)for(int n=0;n<ds.length();n++){JSONObject d=ds.optJSONObject(n);if(d!=null&&d.has("reason")){reason=d.optString("reason","");break;}}}}catch(Exception ignored){}
  if(reason.equals("API_KEY_INVALID")||reason.equals("API_KEY_EXPIRED"))return "Gemini: chave inválida ou expirada. Crie uma nova chave no Google AI Studio.";
  if(reason.contains("BLOCKED")||reason.equals("API_KEY_SERVICE_BLOCKED"))return "Gemini: chave bloqueada ou restrita. Verifique as restrições e se a chave foi revogada ou exposta no Google AI Studio.";
  if(reason.equals("SERVICE_DISABLED"))return "Gemini: active a Generative Language API no projecto desta chave.";
  if(code==401||code==403)return "Gemini: acesso recusado (HTTP "+code+"). Verifique a chave, permissões do projecto, restrições da aplicação e região da conta.";
  if(code==429)return "Gemini: quota ou limite de pedidos atingido (HTTP 429). Verifique a quota gratuita no AI Studio e tente mais tarde.";
  if(code==400)return "Gemini: pedido recusado (HTTP 400). Verifique se a chave pertence ao Gemini e se a conta/região tem acesso ao modelo.";
  if(code==404)return "Gemini: modelo indisponível para esta chave (HTTP 404).";
  if(code>=500)return "Gemini: serviço temporariamente indisponível (HTTP "+code+"). Tente mais tarde.";
  return "Gemini: pedido recusado (HTTP "+code+").";
 }
 static String failure(int code,String raw){return failure(code,raw,"");}
 static String failure(int code,String raw,String key){String message="",state="";try{JsonBudget.check(raw,16,1000);JSONObject e=new JSONObject(raw).optJSONObject("error");if(e!=null){message=e.optString("message","");state=e.optString("status","");}}catch(Exception ignored){}
  String description=summary(code,raw)+"\nHTTP "+code;if(state.matches("[A-Z_]{1,64}"))description+=" · "+state;if(!message.isEmpty())description+="\nGoogle: "+redact(message,key);return description;
 }
 static String redact(String text,String key){if(text==null)return "";if(key!=null&&!key.isEmpty())text=text.replace(key,"[chave ocultada]");text=text.replaceAll("(?i)(?:AIza[A-Za-z0-9_-]{10,}|AQ\\.[A-Za-z0-9._~+/=:-]{10,})","[chave ocultada]").replaceAll("(?i)(?:x-goog-api-key|api[_ -]?key|key|authorization)\\s*[:=]\\s*[^\\s,;]+","[credencial ocultada]").replaceAll("[\\p{Cc}&&[^\\n\\t]]","");if(text.length()>1600)text=text.substring(0,1600)+"…";return text;}
 static String errorBody(HttpURLConnection c){try(InputStream in=c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)return "";byte[] b=new byte[1024];for(int n;(n=in.read(b))!=-1;){if(out.size()+n>16384)return "";out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
 static String normaliseKey(String value){return value==null?"":value.trim();}
 static boolean validKey(String key){return key!=null&&key.length()>=16&&key.length()<=2048&&key.matches("[A-Za-z0-9._~+/=:-]+");}
 static void testConnection(String key,Call call)throws Exception{new SeekyAiClient().verifyGeneration(key,call);}
 void verifyGeneration(String key,Call call)throws Exception{call.check();JSONObject payload=SeekyAiCore.request(SeekyAiCore.GEMINI,"","",Collections.emptyList(),"Responde apenas: OK.",false);payload.getJSONObject("generationConfig").put("maxOutputTokens",128);String raw=transport.post(SeekyAiCore.GEMINI,key,payload,call);call.check();try{JsonBudget.check(raw,32,5000);}catch(IllegalArgumentException e){throw new IOException("Resposta de teste inválida ou demasiado complexa.");}SeekyAiCore.response(SeekyAiCore.GEMINI,new JSONObject(raw));}

 interface Connections {HttpURLConnection open(URL url)throws IOException;}
 static final class Network implements Transport {
  final Connections connections;Network(){this(url->(HttpURLConnection)url.openConnection());}Network(Connections connections){this.connections=connections;}
  void verify(String key,Call call)throws Exception{new SeekyAiClient(this).verifyGeneration(key,call);}
  public String post(String provider,String key,JSONObject payload,Call call)throws Exception{call.check();if(!validKey(key))throw new IOException("Configure uma chave válida.");HttpURLConnection c=connections.open(new URL(endpoint(provider)));call.connection=c;long deadline=System.nanoTime()+120000000000L;
   try{c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-store");c.setConnectTimeout(15000);c.setReadTimeout(75000);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");c.setRequestProperty("x-goog-api-key",key);c.setDoOutput(true);byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(body.length);call.check();try(OutputStream out=c.getOutputStream()){out.write(body);}call.check();int code=c.getResponseCode();if(code<200||code>=300)throw new HttpFailure(code,failure(code,errorBody(c),key));try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;){call.check();if(System.nanoTime()>deadline)throw new SocketTimeoutException();if(out.size()+n>262144)throw new IOException("Resposta acima do limite. Peça uma resposta mais curta.");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
   catch(SocketTimeoutException e){throw new IOException("A resposta demorou demasiado. Pode tentar novamente.");}catch(UnknownHostException e){throw new IOException("Sem ligação ao serviço. Verifique a Internet.");}finally{call.connection=null;c.disconnect();}
  }
 }
}
