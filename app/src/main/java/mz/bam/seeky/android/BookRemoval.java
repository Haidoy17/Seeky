package mz.bam.seeky.android;
import android.content.Context;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.io.*;import java.util.*;
/** One shelf cover can have multiple course references. Remove that group, never external originals. */
final class BookRemoval {
 static final class Result {int records;boolean fileRemoved;String warning="";}
 static Result remove(Context c,StudyStore store,long id,String path,String hash)throws IOException {
  Result result=new Result();List<Long> ids=new ArrayList<>();SQLiteDatabase d=store.getWritableDatabase();d.beginTransaction();try{
   StudyStore.Row row=store.get("materials",id);if(row==null)return result;if(!row.s("path").equals(path)||!row.s("hash").equals(hash))throw new IOException("O livro foi alterado. Actualize a estante antes de eliminar.");StudyStore.reserveMaterialIds(d);
   try(Cursor refs=d.query("materials",new String[]{"id"},"path=? AND hash=?",new String[]{path,hash},null,null,null)){while(refs.moveToNext())ids.add(refs.getLong(0));}
   for(long linked:ids){String[] key={""+linked};for(String table:new String[]{"notes","cards","questions"})d.update(table,StudyStore.values("material",0),"material=?",key);result.records+=d.delete("materials","id=?",key);}
   d.setTransactionSuccessful();
  }finally{d.endTransaction();}
  for(long linked:ids)LastReading.forget(c,linked);SpeechEvents.State voice=SpeechEvents.state;if(voice.active)for(long linked:ids)if(voice.uri.equals(StudyFileProvider.uri(c,linked).toString())){ReadAloudService.control(c,ReadAloudService.STOP,voice.token);break;}
  try{File file=BookDuplicates.privatePath(c,path);if(!BookDuplicates.referenced(store,file)&&file.exists()){if(file.delete())result.fileRemoved=true;else result.warning="Livro removido da estante; não foi possível apagar a cópia privada.";}}catch(IOException outside){/* An external original is outside the removal scope. */}
  return result;
 }
 private BookRemoval(){}
}
