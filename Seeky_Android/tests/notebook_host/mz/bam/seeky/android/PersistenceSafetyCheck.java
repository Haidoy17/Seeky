package mz.bam.seeky.android;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.nio.file.Files;

/** Real ReaderStore/StudyStore methods on host SQLite; Android lifecycle is not simulated. */
public class PersistenceSafetyCheck {
 static int passed;
 interface Work {void run()throws Exception;}
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static void fails(Work work,String name)throws Exception {try{work.run();throw new AssertionError(name);}catch(IllegalStateException|SQLiteException expected){check(true,name);}}
 static long count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
 static Context context()throws Exception{return new Context(Files.createTempDirectory("seeky-safety-").toFile());}
 static void relocation()throws Exception {
  Context c=context();String old="file:///old.pdf",target="file:///new.pdf",source="source";
  try(ReaderStore reader=new ReaderStore(c)){
   StudyText a=new StudyText(source),b=new StudyText("source notebook"),d=new StudyText("destination notebook");
   reader.saveEdit(old,0,source,a,b,"Source edit","source");
   reader.saveEdit(target,0,source,a,d,"Destination edit",null);
   reader.bookmark(old,0,1,"Source bookmark");reader.bookmark(target,0,2,"Destination bookmark");
   SQLiteDatabase db=reader.getReadableDatabase();
   fails(()->ReaderStore.relocate(c,old,target),"colliding notebook relocation is refused");
   check(reader.load(old,0,source).text.equals(b.text)&&reader.load(target,0,source).text.equals(d.text),"relocation conflict preserves both distinct notebook texts");
   check(reader.bookmarks(old).size()==1&&reader.bookmarks(target).size()==1&&count(db,"edit_history")==2&&count(db,"cut_clip")==1,"conflict does not relocate bookmarks history or clips partially");
   ReaderStore.relocate(c,old,old);
   check(reader.load(old,0,source).text.equals(b.text)&&count(db,"draft")==2,"same-URI relocation leaves notebooks intact");
   String clear="file:///clear.pdf";ReaderStore.relocate(c,old,clear);
   check(reader.load(clear,0,source).text.equals(b.text)&&reader.bookmarks(clear).size()==1&&reader.bookmarks(old).isEmpty(),"non-conflicting relocation transfers notebook and bookmarks");
   check(reader.travel(clear,0,source,b,false).text.equals(source),"relocated edit history still supports undo");
   // Abort a later table update after draft and bookmark updates to exercise rollback.
   db.execSQL("CREATE TRIGGER reject_relocation BEFORE UPDATE ON edit_history BEGIN SELECT RAISE(ABORT,'simulated failure');END");
   fails(()->ReaderStore.relocate(c,clear,"file:///failed.pdf"),"late relocation failure aborts the transaction");
   check(reader.bookmarks(clear).size()==1&&reader.bookmarks("file:///failed.pdf").isEmpty()&&count(db,"draft")==2,"late failure rolls back notebook and bookmark relocation together");
  }
 }
 static void deletion()throws Exception {
  Context c=context();try(StudyStore store=new StudyStore(c)){
   SQLiteDatabase db=store.getWritableDatabase();
   long note=store.save("notes",0,StudyStore.values("title","Important","body","Original text","created",1,"updated",1));
   db.execSQL("CREATE TRIGGER reject_note_delete BEFORE DELETE ON notes BEGIN SELECT RAISE(ABORT,'simulated failure');END");
   fails(()->store.delete("notes",note),"note deletion failure is reported");
   check(store.get("notes",note)!=null&&count(db,"study_trash")==0,"failed note deletion retains original without publishing duplicate trash");
   db.execSQL("DROP TRIGGER reject_note_delete");
   store.delete("notes",note);
   check(store.get("notes",note)==null&&count(db,"study_trash")==1,"successful note deletion publishes exactly one recoverable record");
   store.restoreTrash(store.rows("study_trash",null,null,null,1).get(0).id());
   check(count(db,"notes")==1&&count(db,"study_trash")==0,"deleted note restores once with original content");
   long card=store.save("cards",0,StudyStore.values("front","Q","back","A","due",1,"updated",1));
   store.save("reviews",0,StudyStore.values("card",card,"grade",2,"reviewed",1,"token","review-token"));
   db.execSQL("CREATE TRIGGER reject_card_delete BEFORE DELETE ON cards BEGIN SELECT RAISE(ABORT,'simulated failure');END");
   fails(()->store.delete("cards",card),"card deletion failure is reported");
   check(store.get("cards",card)!=null&&count(db,"study_trash")==0&&store.rows("reviews",null,null,null,1).get(0).n("card")==card,"failed card deletion rolls back trash and review detachment");
   db.execSQL("DROP TRIGGER reject_card_delete");store.delete("cards",card);
   check(store.get("cards",card)==null&&count(db,"study_trash")==1&&store.rows("reviews",null,null,null,1).get(0).n("card")==0,"successful card deletion commits trash and review detachment together");
   long subject=store.save("subjects",0,StudyStore.values("title","Math","color",1,"created",1));
   long task=store.save("tasks",0,StudyStore.values("subject",subject,"title","Study","updated",1));
   store.deleteSubject(subject);
   check(store.get("subjects",subject)==null&&store.get("tasks",task).n("subject")==0,"subject deletion remains compatible with its existing outer transaction");
   long task2=store.save("tasks",0,StudyStore.values("title","Retain","updated",1));
   long before=count(db,"study_trash");db.beginTransaction();try{store.delete("tasks",task2);}finally{db.endTransaction();}
   check(store.get("tasks",task2)!=null&&count(db,"study_trash")==before,"caller rollback restores both original task and trash state");
  }
 }
 public static void main(String[] args)throws Exception {
  if(args.length==0||args[0].equals("relocation"))relocation();
  if(args.length==0||args[0].equals("deletion"))deletion();
  System.out.println("PASS "+passed+" persistence safety checks over host SQLite JDBC");
 }
}
