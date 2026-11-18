package mz.bam.seeky.android;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Durable work journal. Rebuildable/search data and jobs never enter the three-library restore. */
final class TaskStore extends SQLiteOpenHelper {
    final Context app;
    TaskStore(Context c){super(c,"seeky-work.db",null,1);app=c.getApplicationContext();setWriteAheadLoggingEnabled(true);}
    public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,type TEXT NOT NULL,title TEXT NOT NULL,state TEXT NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL,progress INTEGER NOT NULL DEFAULT 0,total INTEGER NOT NULL DEFAULT 0,message TEXT NOT NULL DEFAULT '',payload TEXT NOT NULL,workspace TEXT NOT NULL,lease TEXT NOT NULL DEFAULT '')");
        d.execSQL("CREATE TABLE items(job TEXT NOT NULL,seq INTEGER NOT NULL,uri TEXT NOT NULL DEFAULT '',name TEXT NOT NULL DEFAULT '',mime TEXT NOT NULL DEFAULT '',bytes INTEGER NOT NULL DEFAULT 0,modified INTEGER NOT NULL DEFAULT 0,state TEXT NOT NULL DEFAULT 'pending',message TEXT NOT NULL DEFAULT '',target TEXT NOT NULL DEFAULT '',material INTEGER NOT NULL DEFAULT 0,part INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(job,seq))");
        d.execSQL("CREATE TABLE scan_nodes(job TEXT NOT NULL,uri TEXT NOT NULL,done INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(job,uri))");
        d.execSQL("CREATE INDEX items_uri ON items(job,uri)");
        d.execSQL("CREATE INDEX pending_items ON items(job,state,seq)");
        d.execSQL("CREATE TABLE sources(uri TEXT NOT NULL,subject INTEGER NOT NULL,workspace TEXT NOT NULL,material INTEGER NOT NULL,hash TEXT NOT NULL,PRIMARY KEY(uri,subject,workspace))");
        d.execSQL("CREATE TABLE folders(uri TEXT PRIMARY KEY,name TEXT NOT NULL,subject INTEGER NOT NULL DEFAULT 0,workspace TEXT NOT NULL,checked INTEGER NOT NULL DEFAULT 0)");
    }
    public void onUpgrade(SQLiteDatabase d,int old,int version){}
    static String workspace(Context c){return c.getSharedPreferences("reading_tools",0).getString("workspace_id","");}
    String create(String type,String title,JSONObject payload)throws JSONException{
        String id=UUID.randomUUID().toString();long now=System.currentTimeMillis();
        getWritableDatabase().insertOrThrow("jobs",null,StudyStore.values("id",id,"type",type,"title",title,"state","paused","created",now,"updated",now,"payload",payload.toString(),"workspace",workspace(app)));return id;
    }
    static StudyStore.Row row(Cursor c){StudyStore.Row r=new StudyStore.Row();for(int i=0;i<c.getColumnCount();i++)r.put(c.getColumnName(i),c.getString(i));return r;}
    StudyStore.Row job(String id){try(Cursor c=getReadableDatabase().query("jobs",null,"id=?",new String[]{id},null,null,null,"1")){return c.moveToFirst()?row(c):null;}}
    JSONObject payload(String id)throws JSONException{StudyStore.Row r=job(id);if(r==null)throw new IllegalArgumentException("Tarefa indisponível.");return new JSONObject(r.s("payload"));}
    List<StudyStore.Row> jobs(int offset,int limit){List<StudyStore.Row> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("jobs",null,null,null,null,null,"created DESC",Math.max(0,offset)+","+Math.min(50,Math.max(1,limit)))){while(c.moveToNext())out.add(row(c));}return out;}
    List<StudyStore.Row> items(String id,String state,int offset,int limit){List<StudyStore.Row> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("items",null,state==null?"job=?":"job=? AND state=?",state==null?new String[]{id}:new String[]{id,state},null,null,"seq",Math.max(0,offset)+","+Math.min(100,Math.max(1,limit)))){while(c.moveToNext())out.add(row(c));}return out;}
    void add(String id,int seq,String uri,String name,long bytes,long modified){getWritableDatabase().insertOrThrow("items",null,StudyStore.values("job",id,"seq",seq,"uri",uri,"name",name,"bytes",bytes,"modified",modified));}
    void item(String id,int seq,ContentValues values){getWritableDatabase().update("items",values,"job=? AND seq=?",new String[]{id,""+seq});}
    void update(String id,ContentValues values){values.put("updated",System.currentTimeMillis());getWritableDatabase().update("jobs",values,"id=?",new String[]{id});}
    void state(String id,String state,String message){update(id,StudyStore.values("state",state,"message",message));TaskEvents.changed();}
    void payload(String id,JSONObject data){update(id,StudyStore.values("payload",data.toString()));}
    long count(String id,String state){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM items WHERE job=?"+(state==null?"":" AND state=?"),state==null?new String[]{id}:new String[]{id,state})){c.moveToFirst();return c.getLong(0);}}
    String next(){try(Cursor c=getReadableDatabase().query("jobs",new String[]{"id"},"state='queued'",null,null,null,"created","1")){return c.moveToFirst()?c.getString(0):null;}}
    void recover(){getWritableDatabase().execSQL("UPDATE items SET state='uncertain',message='Pedido interrompido. Reveja antes de repetir.' WHERE state='sent'");getWritableDatabase().execSQL("UPDATE jobs SET state=CASE WHEN state='pausing' OR type='summary' THEN 'paused' ELSE 'queued' END,lease='',message='Interrompido pelo sistema. O progresso foi conservado.' WHERE state IN ('running','pausing')");getWritableDatabase().execSQL("UPDATE jobs SET state='cancelled' WHERE state='cancelling'");getWritableDatabase().execSQL("UPDATE items SET state='pending' WHERE state='running'");}
    void retryFailed(String id){getWritableDatabase().execSQL("UPDATE items SET state='pending',message='' WHERE job=? AND state='failed'",new Object[]{id});}
    void remove(String id){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("items","job=?",new String[]{id});d.delete("jobs","id=?",new String[]{id});d.setTransactionSuccessful();}finally{d.endTransaction();}}
    void totals(String id){int done=(int)(count(id,"done")+count(id,"duplicate")+count(id,"failed"));update(id,StudyStore.values("progress",done,"total",count(id,null)));}
}
