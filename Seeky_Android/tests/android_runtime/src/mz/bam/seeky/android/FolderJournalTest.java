package mz.bam.seeky.android;
import android.app.*;import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.*;import java.io.*;import java.util.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.annotation.*;import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=33,application=Application.class) @LooperMode(LooperMode.Mode.PAUSED)
public class FolderJournalTest {
    WorkTestSupport w;static final Map<String,File> files=new HashMap<>();static String pauseFolder;static Runnable pause;
    public static class Tree extends DocumentsProvider {
        public boolean onCreate(){return true;}public boolean isChildDocument(String parent,String child){return child.startsWith(parent+"/");}public Cursor queryRoots(String[] columns){return new MatrixCursor(new String[]{"root_id","document_id"});}
        static String[] columns(){return new String[]{"document_id","_display_name","mime_type","_size","last_modified"};}
        static void add(MatrixCursor c,String id){boolean directory=!files.containsKey(id);c.addRow(new Object[]{id,id.substring(id.lastIndexOf('/')+1),directory?DocumentsContract.Document.MIME_TYPE_DIR:"text/plain",directory?0:files.get(id).length(),1});}
        public Cursor queryDocument(String id,String[] projection){MatrixCursor c=new MatrixCursor(columns());add(c,id);return c;}
        public Cursor queryChildDocuments(String parent,String[] projection,String order){if(parent.equals(pauseFolder)&&pause!=null){Runnable r=pause;pause=null;r.run();}MatrixCursor c=new MatrixCursor(columns());if(parent.equals("root")){add(c,"root/first.txt");add(c,"root/sub");}else if(parent.equals("root/sub")){add(c,"root/sub/second.txt");add(c,"root/sub/deep");}else if(parent.equals("root/sub/deep"))add(c,"root/sub/deep/third.txt");return c;}
        public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{File f=files.get(id);if(f==null)throw new FileNotFoundException();return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}
    }
    @Before public void setup()throws Exception{w=new WorkTestSupport();files.clear();pause=null;pauseFolder="";int i=0;for(String id:new String[]{"root/first.txt","root/sub/second.txt","root/sub/deep/third.txt"}){File f=new File(w.app.getCacheDir(),"tree-"+(i++)+".txt");java.nio.file.Files.write(f.toPath(),("Conteúdo de "+id).getBytes("UTF-8"));files.put(id,f);}ChapterExportTest.register(w.app,"seeky.work.tree",new Tree(),true);}
    @After public void close(){w.close();}
    String job()throws Exception{Uri root=DocumentsContract.buildTreeDocumentUri("seeky.work.tree","root");String id=w.job("import",new JSONObject().put("subject",0).put("books",true).put("tree",root.toString()).put("scan_complete",false));try(TaskStore db=new TaskStore(w.app)){db.getWritableDatabase().insertOrThrow("scan_nodes",null,StudyStore.values("job",id,"uri",DocumentsContract.buildDocumentUriUsingTree(root,"root").toString()));}return id;}
    void scan(String id)throws Exception{try{w.run(id);fail("Folder discovery must pause for review");}catch(InterruptedIOException expected){}}
    @Test public void nestedFolderPreviewCountsEverythingBeforeImporting()throws Exception{String id=job();scan(id);assertEquals(3,w.items(id,null));assertEquals(0,w.materials());try(TaskStore db=new TaskStore(w.app)){assertTrue(db.payload(id).getBoolean("scan_complete"));assertEquals("paused",db.job(id).s("state"));}w.run(id);assertEquals(3,w.materials());assertEquals(3,w.items(id,"done"));}
    @Test public void interruptedDiscoveryResumesWithoutDuplicatingDiscoveredItems()throws Exception{String id=job();TaskService.Control control=w.control(id);pauseFolder="root/sub";pause=()->control.stopped=true;try(TaskStore db=new TaskStore(w.app)){try{TaskWork.run(w.service,db.job(id),db,control);fail();}catch(InterruptedIOException expected){}}assertEquals(1,w.items(id,null));scan(id);assertEquals(3,w.items(id,null));w.run(id);assertEquals(3,w.materials());}
    @Test public void checkingTheFolderAgainAddsOnlyNewContent()throws Exception{String first=job();scan(first);w.run(first);int stored=StudyFileProvider.directory(w.app).listFiles().length;String second=job();scan(second);w.run(second);assertEquals(3,w.materials());assertEquals(3,w.items(second,"duplicate"));assertEquals(stored,StudyFileProvider.directory(w.app).listFiles().length);}
}
