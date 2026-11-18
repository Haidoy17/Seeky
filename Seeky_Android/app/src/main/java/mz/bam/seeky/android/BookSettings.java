package mz.bam.seeky.android;
import android.content.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import org.json.*;

/** Small immutable-at-read snapshots, keyed by content hash and remapped by portable backups. */
final class BookSettings {
    static File root(Context c){File dir=new File(c.getFilesDir(),"book-settings");dir.mkdirs();return dir;}
    static File file(Context c,String hash)throws IOException{PlusStore.hash(hash);String mapped=c.getSharedPreferences("book_settings",0).getString(hash,"");File f=mapped.isEmpty()?new File(root(c),hash+".json"):new File(mapped);if(!DiskWalker.inside(root(c).getCanonicalFile(),f.getCanonicalFile()))throw new IOException("Preferência de livro inválida.");return f;}
    static synchronized JSONObject get(Context c,String hash){try{File f=file(c,hash);if(!f.isFile()||f.length()>131072)return new JSONObject().put("hash",hash);String raw=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);JsonBudget.check(raw,12,1500);JSONObject data=new JSONObject(raw);if(!hash.equals(data.optString("hash")))throw new IOException("Perfil inválido.");return data;}catch(Exception e){return new JSONObject();}}
    interface Change{void apply(JSONObject value)throws Exception;}
    static synchronized void update(Context c,String hash,Change change)throws Exception{JSONObject data=get(c,hash);data.put("hash",PlusStore.hash(hash));change.apply(data);byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>131072)throw new IOException("Preferências de livro demasiado extensas.");File target=file(c,hash),temp=new File(root(c),UUID.randomUUID()+".part");try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);c.getSharedPreferences("book_settings",0).edit().putString(hash,target.getPath()).apply();}finally{temp.delete();}}
    static List<File> activeFiles(Context c){List<File> out=new ArrayList<>();File[] all=root(c).listFiles((d,n)->n.endsWith(".json"));if(all!=null)for(File f:all)try{if(f.length()>131072)continue;JSONObject data=new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));String hash=data.optString("hash");if(hash.matches("[a-f0-9]{64}")&&f.getCanonicalFile().equals(file(c,hash).getCanonicalFile()))out.add(f);}catch(Exception ignored){}return out;}
    static String key(Context c,android.net.Uri uri,long material){if(material>0)try(StudyStore db=new StudyStore(c)){StudyStore.Row row=db.get("materials",material);if(row!=null&&row.s("hash").matches("[a-f0-9]{64}"))return row.s("hash");}return ReaderStore.digest(uri.toString());}
    private BookSettings(){}
}
