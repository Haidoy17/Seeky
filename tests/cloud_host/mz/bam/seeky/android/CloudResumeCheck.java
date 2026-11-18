package mz.bam.seeky.android;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;import org.json.*;import android.content.*;
public class CloudResumeCheck {
 static int checks;static final String USER="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 interface Work{void run()throws Exception;}
 static void failure(Work work,String name)throws Exception{try{work.run();throw new AssertionError(name);}catch(IOException expected){checks++;System.out.println("PASS "+name);}}
 static class Server {String id="";long revision;JSONObject registered;Map<String,byte[]> objects=new HashMap<>();Map<String,Integer> posts=new HashMap<>();int begins,commits,loseChunk=-1;boolean loseCommit,stopAfterLast;boolean stopped;String uid=USER;}
 static class Client extends CloudClient {
  final Server server;
  Client(Context c,Server s)throws Exception{super(c);server=s;}
  String uid(){return server.uid;}
  String access(){return "host-token";}
  JSONObject head(){return new JSONObject().put("revision",server.revision).put("backup_id",server.revision==0?JSONObject.NULL:server.id);}
  byte[] wire(String method,String path,byte[] body,String type,String token,int max)throws Exception{
   if(path.endsWith("seeky_begin_backup")){JSONObject p=new JSONObject(new String(body,"UTF-8"));server.begins++;if(server.registered!=null){if(!server.registered.getString("p_id").equals(p.getString("p_id")))throw new AssertionError("snapshot UUID changed");if(!server.registered.getString("p_sha").equals(p.getString("p_sha")))throw new AssertionError("snapshot hash changed");}else server.registered=p;return "{}".getBytes("UTF-8");}
   if(path.endsWith("seeky_commit_backup")){server.commits++;JSONArray chunks=server.registered.getJSONArray("p_chunks");MessageDigest digest=MessageDigest.getInstance("SHA-256");for(int i=0;i<chunks.length();i++){byte[] block=server.objects.get(CloudProtocol.object(USER,server.registered.getString("p_id"),i));if(block==null||block.length!=chunks.getJSONObject(i).getInt("bytes"))throw new AssertionError("incomplete commit");digest.update(block);}server.id=server.registered.getString("p_id");server.revision=1;if(server.loseCommit){server.loseCommit=false;throw new IOException("lost commit response");}return "{\"revision\":1}".getBytes("UTF-8");}
   if(path.startsWith("/rest/v1/seeky_backups?"))return "[]".getBytes("UTF-8");
   if(path.endsWith("seeky_prune_backup")){server.registered=null;return "{}".getBytes("UTF-8");}
   if(method.equals("DELETE")){JSONArray names=new JSONObject(new String(body,"UTF-8")).getJSONArray("prefixes");for(int i=0;i<names.length();i++)server.objects.remove(names.getString(i));return "{}".getBytes("UTF-8");}
   String postPrefix="/storage/v1/object/seeky-private/",getPrefix="/storage/v1/object/authenticated/seeky-private/";
   if(method.equals("GET")&&path.startsWith(getPrefix)){byte[] block=server.objects.get(path.substring(getPrefix.length()));if(block==null)throw new Failure(404,"");return block;}
   if(method.equals("POST")&&path.startsWith(postPrefix)){String object=path.substring(postPrefix.length());server.posts.put(object,server.posts.getOrDefault(object,0)+1);if(server.objects.containsKey(object))throw new Failure(409,"Duplicate");server.objects.put(object,body.clone());int part=Integer.parseInt(object.substring(object.lastIndexOf('/')+1,object.length()-4));if(part==server.loseChunk){server.loseChunk=-1;throw new IOException("lost upload response");}if(server.stopAfterLast&&part==server.registered.getJSONArray("p_chunks").length()-1)server.stopped=true;return "{}".getBytes("UTF-8");}
   throw new AssertionError(method+" "+path);
  }
 }
 static Context context()throws Exception{Context c=new Context(Files.createTempDirectory("seeky-cloud-host-").toFile());c.getSharedPreferences("cloud",0).edit().putString("url","https://test.supabase.co").putString("key","sb_publishable_abcdefghijklmnopqrstuvwxyz123456").commit();StudyState.reset();StudyBackup.writes=0;return c;}
 static void upload(Client client)throws Exception{client.upload(()->client.server.stopped,(s,n,total)->{});}
 public static void main(String[] args)throws Exception{
  Context c=context();Server s=new Server();Client client=new Client(c,s);StudyBackup.size=2*CloudProtocol.CHUNK+73;s.loseChunk=1;
  final Client first=client;failure(()->upload(first),"lost upload response interrupts before publication");String id=s.registered.getString("p_id");
  check(s.revision==0&&client.prefs.getInt("pending_next",-1)==1,"only acknowledged blocks advance the durable resume cursor");
  check(new File(c.getFilesDir(),"seeky-upload-pending.zip").isFile(),"snapshot retained after interruption");
  StudyState.revision=2;Client restarted=new Client(c,s);upload(restarted);
  check(s.id.equals(id)&&StudyBackup.writes==1,"restart resumes same UUID and snapshot despite newer local edits");
  check(s.posts.get(CloudProtocol.object(USER,id,0))==1,"acknowledged first block is not resent");
  check(s.posts.get(CloudProtocol.object(USER,id,1))==2,"ambiguous block reconciled via immutable duplicate and SHA-256");
  check(client.prefs.getLong("uploaded_revision",0)==1&&StudyState.revision==2,"newer local edits remain dirty after older snapshot commits");
  check(!new File(c.getFilesDir(),"seeky-upload-pending.zip").exists()&&client.prefs.getString("pending_id","").isEmpty(),"confirmed publication cleans local preparation");
  c=context();s=new Server();client=new Client(c,s);StudyBackup.size=700;s.loseCommit=true;final Client lost=client;
  failure(()->upload(lost),"lost commit response retains receipt for reconciliation");
  check(s.revision==1&&StudyState.remote==0,"remote committed while local head is still old");
  failure(client::discardPending,"discard refuses a snapshot already published remotely");
  upload(new Client(c,s));check(s.commits==1&&StudyBackup.writes==1&&StudyState.remote==1,"head receipt confirms commit without reupload or duplicate publication");
  c=context();s=new Server();client=new Client(c,s);StudyBackup.size=100;s.stopAfterLast=true;final Client cancelled=client;
  failure(()->upload(cancelled),"cancel before commit leaves remote head unchanged");check(s.revision==0&&s.commits==0,"all uploaded blocks stay unpublished until explicit commit");
  s.stopped=false;s.stopAfterLast=false;upload(client);check(s.commits==1&&s.posts.values().stream().mapToInt(Integer::intValue).sum()==1,"resume after cancellation publishes without resending blocks");
  c=context();s=new Server();client=new Client(c,s);StudyBackup.size=150;s.loseChunk=0;final Client damaged=client;failure(()->upload(damaged),"prepare resumable snapshot for corruption check");
  Files.write(new File(c.getFilesDir(),"seeky-upload-pending.zip").toPath(),new byte[]{7});int begins=s.begins;failure(()->upload(damaged),"tampered local preparation rejected");check(s.begins==begins&&s.revision==0,"corrupted preparation never registered again or published");
  c=context();s=new Server();s.revision=1;s.id=UUID.randomUUID().toString();client=new Client(c,s);final Client conflict=client;failure(()->upload(conflict),"newer remote head blocks local overwrite");check(StudyBackup.writes==0&&s.begins==0,"conflict resolved before preparing or uploading data");
  Context encrypted=context();Client vaultClient=new Client(encrypted,new Server());vaultClient.vault.state.put("refresh_token","preserve-host-token");vaultClient.vault.fail=true;failure(vaultClient::session,"transient Keystore failure is reported without destructive logout");check(vaultClient.vault.clears==0&&vaultClient.vault.state.has("refresh_token"),"encrypted session retained after vault failure");vaultClient.vault.fail=false;check(vaultClient.session().has("refresh_token")&&!vaultClient.vaultUnavailable,"unlock/retry can recover the same saved session");
  Context scheduled=context();android.content.SharedPreferences cp=scheduled.getSharedPreferences("cloud",0);cp.edit().putBoolean("auto",true).commit();CloudJobs.schedule(scheduled);android.app.job.JobInfo original=scheduled.scheduler.job;for(int i=0;i<200;i++)CloudJobs.schedule(scheduled);check(scheduled.scheduler.calls==1&&scheduled.scheduler.job==original,"repeated schedule requests preserve existing job and execution window");check(original.period==900000&&original.persisted&&original.getNetworkType()==android.app.job.JobInfo.NETWORK_TYPE_UNMETERED,"automatic backup is persisted, periodic at 15 minutes and unmetered by default");cp.edit().putBoolean("wifi",false).commit();CloudJobs.schedule(scheduled);check(scheduled.scheduler.calls==2&&scheduled.scheduler.job.getNetworkType()==android.app.job.JobInfo.NETWORK_TYPE_ANY,"network preference change reschedules exactly once");CloudJobs.cancel(scheduled);check(scheduled.scheduler.job==null,"logout cancellation removes scheduled backup");
  System.out.println("PASS "+checks+" cloud state-machine checks using actual CloudClient with host contracts; no Android, OAuth or real server claimed");
 }
}
