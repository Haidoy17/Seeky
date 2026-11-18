package mz.bam.seeky.android;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Portable active notebooks/bookmarks. Does not contain originals, accounts or full app data. */
final class NotebookBackup {
    static final int MAX=33554432;
    static final String[] TABLES={"draft","bookmark"};
    static void write(SQLiteDatabase db,OutputStream out)throws Exception {
        boolean own=!db.inTransaction();if(own)db.beginTransactionNonExclusive();
        long bytes=0;
        try{
            byte[] start="{\"format\":\"seeky-notebook-1\",\"schema\":1,\"tables\":{".getBytes(StandardCharsets.UTF_8);out.write(start);bytes+=start.length;
            for(int table=0;table<TABLES.length;table++){
                byte[] header=((table>0?",":"")+JSONObject.quote(TABLES[table])+ ":[").getBytes(StandardCharsets.UTF_8);out.write(header);bytes+=header.length;int count=0;
                try(Cursor c=db.rawQuery("SELECT * FROM "+TABLES[table]+" ORDER BY "+(table==0?"uri,segment,digest":"id"),null)){
                    while(c.moveToNext()){
                        if(++count>20000)throw new IOException("Mais de 20000 registos num caderno; a cópia não foi concluída.");
                        JSONObject row=new JSONObject();for(int i=0;i<c.getColumnCount();i++)row.put(c.getColumnName(i),c.isNull(i)?JSONObject.NULL:c.getString(i));
                        byte[] value=((count>1?",":"")+row.toString()).getBytes(StandardCharsets.UTF_8);bytes+=value.length;
                        if(bytes>MAX-8)throw new IOException("Cadernos acima do limite de 32 MB; a cópia não foi concluída.");out.write(value);
                    }
                }out.write(']');bytes++;
            }out.write("}}".getBytes(StandardCharsets.UTF_8));out.flush();if(own)db.setTransactionSuccessful();
        }finally{if(own)db.endTransaction();}
    }
    static File recovery(Context c,SQLiteDatabase db,String name)throws Exception {
        File folder=new File(c.getFilesDir(),"notebook-recovery");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Pasta de recuperação indisponível.");
        File target=new File(folder,name),staged=new File(folder,name+".part");
        try{
            try(FileOutputStream raw=new FileOutputStream(staged);BufferedOutputStream out=new BufferedOutputStream(raw)){write(db,out);out.flush();raw.getFD().sync();}
            Files.move(staged.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            c.getSharedPreferences("seeky",0).edit().putString("notebook_snapshot",target.getPath()).putLong("notebook_snapshot_time",System.currentTimeMillis()).apply();
            return target;
        }finally{Files.deleteIfExists(staged.toPath());}
    }
    static String uri(JSONObject row)throws Exception {
        String value=row.getString("uri");java.net.URI parsed=new java.net.URI(value);
        if(value.length()>4096||!("content".equals(parsed.getScheme())||"file".equals(parsed.getScheme()))||parsed.getUserInfo()!=null)throw new IOException("Referência de documento inválida.");return value;
    }
    static int bounded(JSONObject row,String key,int max)throws Exception{int value=row.getInt(key);if(value<0||value>max)throw new IOException("Posição inválida.");return value;}
    static final class Result {int added,kept;}
    static Result restore(Context context,ReaderStore store,File file)throws Exception {
        if(file.length()>MAX)throw new IOException("Cópia do caderno acima de 32 MB.");JSONObject root;
        try(InputStream in=new FileInputStream(file)){String json=new String(ContentText.read(in,MAX+1),StandardCharsets.UTF_8);JsonBudget.check(json,8,50000);root=new JSONObject(json);}
        if(!root.getString("format").equals("seeky-notebook-1")||root.getInt("schema")!=1)throw new IOException("Formato de caderno incompatível.");
        JSONObject tables=root.getJSONObject("tables");List<ContentValues> drafts=new ArrayList<>(),bookmarks=new ArrayList<>();Set<String> keys=new HashSet<>();
        JSONArray rows=tables.getJSONArray("draft");if(rows.length()>20000)throw new IOException("Demasiados cadernos.");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String uri=uri(row),digest=row.getString("digest");int segment=bounded(row,"segment",10000000);
            if(!digest.matches("[a-f0-9]{64}")||!keys.add(uri+"\n"+segment+"\n"+digest))throw new IOException("Identidade repetida ou inválida.");
            ReaderStore.decode(row.getString("value"),row.getString("marks"));ContentValues value=new ContentValues();value.put("uri",uri);value.put("segment",segment);value.put("digest",digest);value.put("value",row.getString("value"));value.put("marks",row.getString("marks"));drafts.add(value);
        }
        rows=tables.getJSONArray("bookmark");if(rows.length()>20000)throw new IOException("Demasiados marcadores.");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);ContentValues value=new ContentValues();value.put("uri",uri(row));value.put("segment",bounded(row,"segment",10000000));value.put("position",bounded(row,"position",1048576));String label=row.getString("label");if(label.length()>500)throw new IOException("Marcador demasiado longo.");value.put("label",label);value.put("created",row.optLong("created",0));bookmarks.add(value);
        }
        SQLiteDatabase db=store.getWritableDatabase();Result result=new Result();db.beginTransaction();try{
            // Active state is saved before importing; conflicts preserve it rather than overwrite it.
            recovery(context,db,"before-notebook-import.json");
            for(ContentValues value:drafts){String[] identity={value.getAsString("uri"),value.getAsString("segment"),value.getAsString("digest")};boolean exists;try(Cursor c=db.rawQuery("SELECT 1 FROM draft WHERE uri=? AND segment=? AND digest=?",identity)){exists=c.moveToFirst();}if(exists){result.kept++;continue;}db.insertOrThrow("draft",null,value);result.added++;}
            for(ContentValues value:bookmarks){String[] identity={value.getAsString("uri"),value.getAsString("segment"),value.getAsString("position"),value.getAsString("label")};boolean exists;try(Cursor c=db.rawQuery("SELECT 1 FROM bookmark WHERE uri=? AND segment=? AND position=? AND label=?",identity)){exists=c.moveToFirst();}if(exists){result.kept++;continue;}db.insertOrThrow("bookmark",null,value);result.added++;}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}if(result.added>0)store.dirty();return result;
    }
    private NotebookBackup(){}
}
