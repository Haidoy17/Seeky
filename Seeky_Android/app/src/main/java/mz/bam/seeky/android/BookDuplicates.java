package mz.bam.seeky.android;

import android.content.*;import android.database.*;import android.database.sqlite.*;import java.io.*;import java.nio.file.Files;import java.security.MessageDigest;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;

/** Consolidate verified private copies without deleting course or reader identities. */
final class BookDuplicates {
 static final AtomicBoolean RUNNING=new AtomicBoolean();
 interface Progress {void update(String name);}
 static final class Result {int checked,removed,issues;long bytes;boolean cancelled;final List<String> details=new ArrayList<>();void issue(String s){issues++;if(details.size()<10)details.add(s);}String summary(){return checked+" ficheiros verificados · "+removed+" cópias removidas\n"+Rules.bytes(bytes)+" libertados · "+issues+" avisos"+(cancelled?" · cancelado":"")+"\n\nCursos, anotações e progresso conservados."+(details.isEmpty()?"":"\n\n"+android.text.TextUtils.join("\n",details));}}
 static final class Copy {final File file;final List<StudyStore.Row> refs=new ArrayList<>();Copy(File f){file=f;}int progress(){int max=0;for(StudyStore.Row r:refs)max=Math.max(max,(int)r.n("progress"));return max;}}
 static File privatePath(Context c,String path)throws IOException{File original=new File(path),f=original.getCanonicalFile(),root=StudyFileProvider.directory(c).getCanonicalFile();if(!root.equals(f.getParentFile())||Files.isSymbolicLink(original.toPath()))throw new IOException("Localização fora das cópias privadas.");return f;}
 static String hash(File file,StudyFiles.Cancel stop)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;){StudyFiles.check(stop);md.update(b,0,n);}}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 static boolean equal(File a,File b,StudyFiles.Cancel stop)throws IOException{if(a.length()!=b.length())return false;try(DataInputStream x=new DataInputStream(new FileInputStream(a));DataInputStream y=new DataInputStream(new FileInputStream(b))){byte[] left=new byte[65536],right=new byte[65536];long remaining=a.length();while(remaining>0){StudyFiles.check(stop);int n=(int)Math.min(left.length,remaining);x.readFully(left,0,n);y.readFully(right,0,n);for(int i=0;i<n;i++)if(left[i]!=right[i])return false;remaining-=n;}return x.read()==-1&&y.read()==-1;}}
 static boolean referenced(StudyStore store,File file)throws IOException{if(store.count("materials","path=?",new String[]{file.getPath()})>0)return true;try(Cursor rows=store.getReadableDatabase().query("materials",new String[]{"path"},null,null,null,null,null)){while(rows.moveToNext())if(new File(rows.getString(0)).getCanonicalFile().equals(file))return true;}return false;}
 static void pending(Context c,Set<String> entries)throws IOException{if(!c.getSharedPreferences("book_duplicates",0).edit().putStringSet("pending",new HashSet<>(entries)).commit())throw new IOException("Não foi possível guardar a limpeza pendente.");}
 static void finishPending(Context c,StudyStore store,Set<String> entries,StudyFiles.Cancel stop,Result result)throws IOException{
  for(String entry:new ArrayList<>(entries)){if(stop.cancelled()){result.cancelled=true;return;}boolean done=false;
   try{JsonBudget.check(entry,4,32);JSONObject item=new JSONObject(entry);File from=privatePath(c,item.getString("from")),to=privatePath(c,item.getString("to"));String digest=item.getString("hash");if(from.equals(to)||!digest.matches("[a-f0-9]{64}"))throw new IOException("Registo de limpeza inválido.");
    if(!from.exists()||referenced(store,from))done=true;
    else if(!to.isFile()||!referenced(store,to))throw new IOException("A cópia conservada está indisponível; nenhuma cópia foi apagada.");
    else{if(!hash(to,stop).equals(digest)||!equal(from,to,stop))throw new IOException("Conteúdo alterado; a cópia pendente foi conservada.");long bytes=from.length();if(!from.delete())throw new IOException("Não foi possível apagar a cópia repetida.");result.removed++;result.bytes+=bytes;done=true;}
   }catch(Exception e){if(stop.cancelled()){result.cancelled=true;return;}result.issue(StudyImporter.safe(e.getMessage(),240));}
   if(done){entries.remove(entry);pending(c,entries);}
  }
 }
 static Result clean(Context c,StudyStore store,StudyFiles.Cancel stop,Progress progress)throws Exception{
  if(!RUNNING.compareAndSet(false,true))throw new IOException("Já existe uma verificação de duplicados em curso.");Result result=new Result();
  try{Set<String> journal=new HashSet<>(c.getSharedPreferences("book_duplicates",0).getStringSet("pending",Collections.emptySet()));finishPending(c,store,journal,stop,result);Map<String,Copy> paths=new LinkedHashMap<>();
   try(Cursor cursor=store.getReadableDatabase().query("materials",null,BookQuery.RAW_FORMATS,null,null,null,"progress DESC,updated DESC,id")){String[] columns=cursor.getColumnNames();while(cursor.moveToNext()){if(stop.cancelled()){result.cancelled=true;return result;}StudyStore.Row row=new StudyStore.Row();for(int n=0;n<columns.length;n++)row.put(columns[n],cursor.isNull(n)?"":cursor.getString(n));try{File f=privatePath(c,row.s("path"));if(!f.isFile()||f.length()==0)throw new IOException("Cópia indisponível ou vazia.");Copy copy=paths.get(f.getPath());if(copy==null){copy=new Copy(f);paths.put(f.getPath(),copy);}copy.refs.add(row);}catch(IOException e){result.issue(StudyImporter.safe(row.s("title"),100)+": "+e.getMessage());}}}
   Map<Long,List<Copy>> sizes=new LinkedHashMap<>();for(Copy copy:paths.values())sizes.computeIfAbsent(copy.file.length(),key->new ArrayList<>()).add(copy);
   for(List<Copy> sameSize:sizes.values()){Map<String,List<Copy>> hashes=new LinkedHashMap<>();for(Copy copy:sameSize){if(stop.cancelled()){result.cancelled=true;return result;}progress.update(copy.refs.get(0).s("title"));result.checked++;if(sameSize.size()<2)continue;try{String digest=hash(copy.file,stop);boolean valid=true;for(StudyStore.Row ref:copy.refs)if(!ref.s("hash").equals(digest))valid=false;if(!valid){result.issue(StudyImporter.safe(copy.refs.get(0).s("title"),100)+": identidade alterada; ficheiro conservado.");continue;}hashes.computeIfAbsent(digest,key->new ArrayList<>()).add(copy);}catch(Exception e){if(stop.cancelled()){result.cancelled=true;return result;}result.issue(StudyImporter.safe(e.getMessage(),240));}}
    for(Map.Entry<String,List<Copy>> group:hashes.entrySet()){List<Copy> copies=group.getValue();if(copies.size()<2)continue;copies.sort((a,b)->Integer.compare(b.progress(),a.progress()));Copy keep=copies.get(0);String digest=group.getKey();if(!hash(keep.file,stop).equals(digest)){result.issue("Conteúdo alterado durante a verificação; grupo conservado.");continue;}
     for(int i=1;i<copies.size();i++){if(stop.cancelled()){result.cancelled=true;return result;}Copy duplicate=copies.get(i);if(!equal(keep.file,duplicate.file,stop)){result.issue("Conteúdo diferente; cópia conservada.");continue;}String task=new JSONObject().put("from",duplicate.file.getPath()).put("to",keep.file.getPath()).put("hash",digest).toString();journal.add(task);pending(c,journal);
      SQLiteDatabase sql=store.getWritableDatabase();sql.beginTransaction();try{if(!referenced(store,keep.file))throw new IOException("A cópia principal foi alterada. Repita a verificação.");for(StudyStore.Row ref:duplicate.refs){int changed=sql.update("materials",StudyStore.values("path",keep.file.getPath()),"id=? AND path=? AND hash=?",new String[]{""+ref.id(),ref.s("path"),digest});if(changed!=1)throw new IOException("Um livro foi alterado durante a limpeza. Repita a verificação.");}sql.setTransactionSuccessful();}finally{sql.endTransaction();}
      finishPending(c,store,journal,stop,result);
     }
    }
   }
   return result;
  }catch(InterruptedIOException e){if(stop.cancelled()){result.cancelled=true;return result;}throw e;}finally{RUNNING.set(false);}
 }
 static void removeMaterial(Context c,StudyStore store,long id)throws IOException{StudyStore.Row row=store.get("materials",id);if(row==null)return;store.removeMaterial(id);LastReading.forget(c,id);File file;try{file=privatePath(c,row.s("path"));}catch(IOException outside){return;}if(!referenced(store,file)&&file.exists()&&!file.delete())throw new IOException("O registo foi removido; não foi possível apagar a cópia privada sem referências.");}
 private BookDuplicates(){}
}
