package mz.bam.seeky.android;
import android.app.*;import android.content.*;import android.content.pm.ProviderInfo;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.*;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import org.json.*;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.shadows.*;

/** Local-only work fixtures. No requests to Gemini or a real TTS engine. */
final class WorkTestSupport implements AutoCloseable {
    final Application app=RuntimeEnvironment.getApplication();final ServiceController<TaskService> controller;final TaskService service;
    static final Map<String,File> sourceFiles=new HashMap<>();static final Map<String,String> sourceNames=new HashMap<>();
    WorkTestSupport()throws Exception{WorkspaceEpoch.value=0;WorkspaceEpoch.restoring=false;sourceFiles.clear();sourceNames.clear();ProviderInfo info=new ProviderInfo();info.authority="seeky.work.input";Inputs provider=new Inputs();provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);ProviderInfo study=new ProviderInfo();study.authority=app.getPackageName()+".study";StudyFileProvider books=new StudyFileProvider();books.attachInfo(app,study);ShadowContentResolver.registerProviderInternal(study.authority,books);controller=Robolectric.buildService(TaskService.class).create();service=controller.get();}
    public static final class Inputs extends ContentProvider {
        public boolean onCreate(){return true;}public String getType(Uri uri){return MediaFormats.mime(sourceNames.getOrDefault(uri.getLastPathSegment(),"file.txt"),null);}
        public Cursor query(Uri uri,String[] columns,String where,String[] args,String order){String key=uri.getLastPathSegment();String[] names=columns==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:columns;MatrixCursor out=new MatrixCursor(names);Object[] values=new Object[names.length];for(int i=0;i<names.length;i++)values[i]=names[i].equals(OpenableColumns.DISPLAY_NAME)?sourceNames.getOrDefault(key,"Missing.txt"):names[i].equals(OpenableColumns.SIZE)?sourceFiles.containsKey(key)?sourceFiles.get(key).length():0:null;out.addRow(values);return out;}
        public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{File file=sourceFiles.get(uri.getLastPathSegment());if(file==null)throw new FileNotFoundException("Fixture source unavailable");return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);}
        public Uri insert(Uri u,ContentValues v){return null;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}public int delete(Uri u,String s,String[] a){return 0;}
    }
    Uri source(String key,String name,String text)throws Exception{File f=new File(app.getCacheDir(),"source-"+key+".fixture");Files.write(f.toPath(),text.getBytes("UTF-8"));return source(key,name,f);}
    Uri source(String key,String name,File file){sourceFiles.put(key,file);sourceNames.put(key,name);return Uri.parse("content://seeky.work.input/"+key);}
    String job(String type,JSONObject data)throws Exception{try(TaskStore db=new TaskStore(app)){return db.create(type,"Fixture "+type,data);}}
    String importJob(int count)throws Exception{String id=job("import",new JSONObject().put("subject",0).put("books",true).put("scan_complete",true));try(TaskStore db=new TaskStore(app)){android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{for(int i=0;i<count;i++){Uri u=source("book-"+i,"Livro "+i+".txt","Texto original do livro "+i+".\n");db.add(id,i,u.toString(),"",0,0);}db.totals(id);sql.setTransactionSuccessful();}finally{sql.endTransaction();}}return id;}
    TaskService.Control control(String id){TaskService.Control control=new TaskService.Control(service,id);service.current=control;try(TaskStore db=new TaskStore(app)){control.attach(db);db.state(id,"running","Fixture running");}return control;}
    void run(String id)throws Exception{TaskService.Control control=control(id);try(TaskStore db=new TaskStore(app)){TaskWork.run(service,db.job(id),db,control);control.check();db.state(id,"complete",db.job(id).s("message"));}}
    long items(String id,String state){try(TaskStore db=new TaskStore(app)){return db.count(id,state);}}
    long materials(){try(StudyStore db=new StudyStore(app)){return db.count("materials",null,null);}}
    long book(String name,String text)throws Exception{File file=new File(StudyFileProvider.directory(app),UUID.randomUUID()+".txt");Files.write(file.toPath(),text.getBytes("UTF-8"));try(StudyStore db=new StudyStore(app)){return db.save("materials",0,StudyStore.values("subject",0,"title",name,"author","","name",name,"mime","text/plain","path",file.getPath(),"hash",StudyFiles.hash(file),"bytes",file.length(),"created",1,"updated",1));}}
    StudyStore.Row book(long id){try(StudyStore db=new StudyStore(app)){return db.get("materials",id);}}
    public void close(){controller.destroy();TaskEvents.listeners.clear();WorkLeases.activities.clear();SpeechEvents.publish(new SpeechEvents.State("","","",0,0,0,false,true));}
}
