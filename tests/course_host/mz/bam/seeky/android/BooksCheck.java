package mz.bam.seeky.android;
import android.content.Context;import java.nio.file.Files;import java.util.*;
public final class BooksCheck {
 static int checks;
 static void ok(boolean result,String name){if(!result)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static long book(StudyStore s,String name,String title,String author,int progress,String status,String hash){return s.save("materials",0,StudyStore.values("subject",0,"name",name,"title",title,"author",author,"mime",MediaFormats.mime(name,null),"bytes",12,"path","/private/"+name,"hash",hash,"progress",progress,"status",status,"created",1,"updated",1));}
 static long count(StudyStore s,BookQuery q){return s.count("materials",q.where,q.args);}
 public static void main(String[] args)throws Exception{
  Context c=new Context(Files.createTempDirectory("seeky-books-").toFile());String hash=String.join("",Collections.nCopies(64,"a"));Set<String> favorites=new HashSet<>(Arrays.asList(hash,"' OR 1=1--"));
  try(StudyStore s=new StudyStore(c)){
   long first=book(s,"Um.pdf","100% Paz","Carlos",0,"Por estudar",hash);book(s,"Dois.epub","Outro","Joana",40,"A estudar","h2");book(s,"Tres.docx","Final","Ana",100,"Concluído","h3");book(s,"Quatro.txt","Aberto","Z",0,"A estudar","h4");book(s,"Som.mp3","Som","",0,"Por estudar","h5");
   ok(count(s,new BookQuery("",7,0,0,false,favorites))==4,"library includes books, excludes media");
   ok(count(s,new BookQuery("",1,0,0,false,favorites))==1,"unread excludes opened books at zero percent");ok(count(s,new BookQuery("",2,0,0,false,favorites))==2,"reading includes a just-opened book");ok(count(s,new BookQuery("",4,0,0,false,favorites))==1,"finished filter uses shared progress");ok(count(s,new BookQuery("",0,0,0,false,favorites))==0,"empty reading selection returns no books");
   ok(count(s,new BookQuery("%",7,0,0,false,favorites))==1,"search percent is literal");ok(count(s,new BookQuery("' OR 1=1--",7,0,0,false,favorites))==0,"search cannot change query structure");ok(count(s,new BookQuery("Carlos",7,0,0,false,favorites))==1,"author search is bound");ok(count(s,new BookQuery("",7,0,0,true,favorites))==1,"favorites hashes are validated");ok(count(s,new BookQuery("",7,1,0,false,favorites))==1,"PDF format filter");
   BookQuery ordered=new BookQuery("",7,0,1,false,favorites);ok(s.page("materials",ordered.where,ordered.args,ordered.order,12,0).get(0).s("author").equals("Ana"),"author order is stable");
   StudyReading.advance(s,first,ReadingProgress.units(0,1,400,1000,200),1000);ok(s.get("materials",first).n("progress")==50,"scrolling advances actual shared book progress");
   for(int i=0;i<30;i++)book(s,"page"+i+".pdf","Page "+i,"",0,"Por estudar","extra"+i);BookQuery q=new BookQuery("",7,0,0,false,favorites);Set<Long> ids=new HashSet<>();for(int offset=0;offset<36;offset+=12)for(StudyStore.Row r:s.page("materials",q.where,q.args,q.order,12,offset))ok(ids.add(r.id()),"paged library has no duplicate");ok(ids.size()==34,"all book pages are reachable");book(s,"note.md","Markdown","",0,"Por estudar","md");ok(count(s,new BookQuery("",7,4,0,false,favorites))==2,"text format includes Markdown");book(s,"semextensao","Sem extensão","",0,"Por estudar","none");s.save("materials",s.rows("materials","hash=?",new String[]{"none"},null,1).get(0).id(),StudyStore.values("mime","application/pdf"));ok(count(s,new BookQuery("",7,1,0,false,favorites))==32,"PDF filter also recognizes MIME without filename extension");
  }
  ok(ReadingProgress.units(2,10,400,1000,200)==2500,"fractional chapter progress");ok(ReadingProgress.units(0,1,0,300,600)==1000,"short text fully visible");ok(ReadingProgress.units(0,1,0,0,600)==0,"unmeasured text cannot imply completion");ok(ReadingProgress.units(Integer.MAX_VALUE,Integer.MAX_VALUE,0,1,1)==Integer.MAX_VALUE,"fixed point progress cannot overflow");
  ok(ChapterRange.next(2,3,7).last==4,"chapter count is inclusive");ok(new ChapterRange(0,6,7).size()==7,"whole book range");for(int[] r:new int[][]{{-1,2,7},{2,8,7},{3,2,7},{0,0,0}}){try{new ChapterRange(r[0],r[1],r[2]);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
  System.out.println("PASS "+checks+" book filtering, reading progress and chapter range checks");
 }
}
