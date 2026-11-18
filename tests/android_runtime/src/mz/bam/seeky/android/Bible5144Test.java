package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.database.*;import android.database.sqlite.*;import android.net.Uri;
import java.io.*;import java.util.*;import java.util.zip.*;import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=33,application=Application.class) @LooperMode(LooperMode.Mode.PAUSED)
public class Bible5144Test {
 WorkTestSupport w;@Before public void before()throws Exception{w=new WorkTestSupport();}@After public void close(){w.close();}
 File epub()throws Exception{
  File file=new File(w.app.getCacheDir(),"Livre-layout.epub");
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
   ChapterExportTest.entry(z,"META-INF/container.xml","<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>");
   ChapterExportTest.entry(z,"book.opf","<package><metadata/><manifest><item id='cover' href='cover.xhtml' media-type='application/xhtml+xml'/><item id='preface' href='preface.xhtml' media-type='application/xhtml+xml'/><item id='gen' href='GEN.xhtml' media-type='application/xhtml+xml'/><item id='exo' href='EXO.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='cover'/><itemref idref='preface'/><itemref idref='gen'/><itemref idref='exo'/></spine></package>");
   ChapterExportTest.entry(z,"cover.xhtml","<html><body><p>Capa de teste</p></body></html>");ChapterExportTest.entry(z,"preface.xhtml","<html><body><p>Prefácio de teste</p></body></html>");
   StringBuilder gen=new StringBuilder("<html><body><div class='mt'>Gênesis</div>");for(int c=1;c<=50;c++){gen.append("<div class='c'>"+c+"</div><p>");for(int v=1;v<=40;v++)gen.append("<span class='verse'>"+v+"&#160;</span>Texto "+c+":"+v+". ");gen.append("</p>");}ChapterExportTest.entry(z,"GEN.xhtml",gen+"</body></html>");
   ChapterExportTest.entry(z,"EXO.xhtml","<html><body><h1>Êxodo</h1><div class='chapterlabel'>1</div><p><span class='v'>1</span>Texto final um. <span class='v'>2</span>Texto final dois.</p></body></html>");
  }return file;
 }
 String job()throws Exception{return w.job("bible",new JSONObject().put("uri",w.source("epub","Livre-layout.epub",epub()).toString()).put("title","Bíblia de ensaio").put("name","Livre-layout.epub").put("mime","application/epub+zip"));}
 @Test public void largeInlineVerseBookPassesTheTwoFrontMatterEntriesAndPublishesEveryVerse()throws Exception {
  String id=job();w.run(id);try(PlusStore db=new PlusStore(w.app);TaskStore tasks=new TaskStore(w.app)){
   assertEquals(2002,DatabaseUtils.longForQuery(db.getReadableDatabase(),"SELECT COUNT(*) FROM bible_verses",null));
   assertEquals("Texto 50:40.",db.rows("bible_verses","book=? AND chapter=50 AND verse=40",new String[]{"Génesis"},null,1).get(0).s("value"));
   assertEquals("Texto final dois.",db.rows("bible_verses","book=? AND chapter=1 AND verse=2",new String[]{"Êxodo"},null,1).get(0).s("value"));
   assertEquals("complete",tasks.job(id).s("state"));assertEquals(2,tasks.payload(id).getInt("epub_parser"));
  }
 }
 @Test public void stalePlainTextCheckpointIsRebuiltByTheNewExtractorWithoutDuplicateOrMissingRows()throws Exception {
  String id=job();File file=new File(SummaryWork.root(w.app,id),"bible.db");
  try(SQLiteDatabase stage=SQLiteDatabase.openOrCreateDatabase(file,null)){
   stage.execSQL("CREATE TABLE verses(book TEXT,chapter INTEGER,verse INTEGER,value TEXT,PRIMARY KEY(book,chapter,verse))");stage.execSQL("CREATE TABLE checkpoint(id INTEGER PRIMARY KEY,position INTEGER,value TEXT)");
   stage.execSQL("INSERT INTO verses VALUES('Génesis',1,1,'Old merged text')");stage.execSQL("INSERT INTO checkpoint VALUES(1,3,'{}')");
  }
  w.run(id);try(PlusStore db=new PlusStore(w.app)){assertEquals(2002,DatabaseUtils.longForQuery(db.getReadableDatabase(),"SELECT COUNT(*) FROM bible_verses",null));assertEquals("Texto 1:1.",db.rows("bible_verses","book=? AND chapter=1 AND verse=1",new String[]{"Génesis"},null,1).get(0).s("value"));}
 }
 @Test public void wakingWithoutALiveServiceRecoversRunningJobsAndPreservesSummaryReview()throws Exception {
  String id=job(),summary=w.job("summary",new JSONObject());try(TaskStore db=new TaskStore(w.app)){db.state(id,"running","2 / 69");db.state(summary,"running","");}
  TaskService.instance=null;try{TaskService.wake(w.app);try(TaskStore db=new TaskStore(w.app)){assertEquals("queued",db.job(id).s("state"));assertEquals("paused",db.job(summary).s("state"));}assertNotNull(shadowOf(w.app).getNextStartedService());}finally{TaskService.instance=w.service;}
 }
 @Test public void committedEpubCheckpointWinsOverTheOlderJournalAndKeepsThePendingVerse()throws Exception {
  String id=job();File file=new File(SummaryWork.root(w.app,id),"bible.db");
  try(SQLiteDatabase stage=SQLiteDatabase.openOrCreateDatabase(file,null)){
   stage.execSQL("CREATE TABLE verses(book TEXT,chapter INTEGER,verse INTEGER,value TEXT,PRIMARY KEY(book,chapter,verse))");stage.execSQL("CREATE TABLE checkpoint(id INTEGER PRIMARY KEY,position INTEGER,value TEXT)");
   stage.execSQL("INSERT INTO verses VALUES('Génesis',1,1,'Versículo já guardado')");
   JSONObject saved=new JSONObject().put("book","Génesis").put("chapter",1).put("verse",2).put("count",1).put("pending","Versículo pendente");
   stage.execSQL("INSERT INTO checkpoint VALUES(1,3,?)",new Object[]{saved.toString()});
  }
  try(TaskStore db=new TaskStore(w.app)){JSONObject data=db.payload(id);data.put("epub_parser",2).put("next",0);db.payload(id,data);}
  w.run(id);try(PlusStore db=new PlusStore(w.app)){assertEquals(4,DatabaseUtils.longForQuery(db.getReadableDatabase(),"SELECT COUNT(*) FROM bible_verses",null));assertEquals("Versículo já guardado",db.rows("bible_verses","book=? AND chapter=1 AND verse=1",new String[]{"Génesis"},null,1).get(0).s("value"));assertEquals("Versículo pendente",db.rows("bible_verses","book=? AND chapter=1 AND verse=2",new String[]{"Génesis"},null,1).get(0).s("value"));}
 }
 @Test public void resumeStartsAnOrphanedRunningTaskInsteadOfSilentlyReturning()throws Exception {
  String id=job();try(TaskStore db=new TaskStore(w.app)){db.state(id,"running","2 / 69");}TaskService.instance=null;
  try{TaskService.start(w.app,id);try(TaskStore db=new TaskStore(w.app)){assertEquals("queued",db.job(id).s("state"));}assertNotNull(shadowOf(w.app).getNextStartedService());}finally{TaskService.instance=w.service;}
 }
 @Test public void wakingWithALiveServiceNeverRequeuesItsActiveTask()throws Exception {
  String id=job();w.control(id);TaskService.wake(w.app);try(TaskStore db=new TaskStore(w.app)){assertEquals("running",db.job(id).s("state"));}assertNull(shadowOf(w.app).getNextStartedService());
 }
}
