package mz.bam.seeky.android;
import java.util.*;
public class OrderingTest {
 static int passed=0;
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);passed++;System.out.println("PASS "+s);}
 static FileRecord item(String name,String author,long size){FileRecord e=new FileRecord();e.name=name;e.uri=name;e.author=author;e.title=name;e.mime="audio/"+name;e.category="Áudio";e.size=size;return e;}
 public static void main(String[] args)throws Exception {
  if(args.length>0&&args[0].equals("sql")){for(int field=0;field<FileOrder.NAMES.length;field++)for(boolean desc:new boolean[]{false,true})System.out.println(field+"\t"+desc+"\t"+FileOrder.sql(field,desc));return;}
  FileRecord a=item("A","Ana",10),b=item("B","Zélia",20),missing=item("C","",30),folder=item("Z","",0);folder.directory=true;a.modified=100;b.modified=200;a.customDate=100;b.customDate=200;a.manualOrder=1;b.manualOrder=2;a.tags="clientes";b.tags="urgente";a.customCategory="Contas";b.customCategory="Trabalho";
  for(int field=0;field<10;field++){check(FileOrder.by(field,false).compare(a,b)<0,"ascending field "+FileOrder.NAMES[field]);check(FileOrder.by(field,true).compare(a,b)>0,"descending field "+FileOrder.NAMES[field]);check(FileOrder.by(field,true).compare(folder,a)<0,"folders first regardless of descending field "+field);}
  check(FileOrder.by(4,false).compare(missing,a)>0&&FileOrder.by(4,true).compare(missing,a)>0,"missing author always last");check(FileOrder.by(8,true).compare(missing,a)>0,"unset personal date always last");check(FileOrder.by(9,false).compare(missing,a)>0,"unset personal order always last");
  check(FileOrder.date(FileOrder.date("2024-02-29")).equals("2024-02-29"),"strict leap date roundtrip");boolean bad=false;try{FileOrder.date("2025-02-29");}catch(java.text.ParseException e){bad=true;}check(bad,"invalid date rejected");bad=false;try{FileOrder.date("2026-2-1");}catch(java.text.ParseException e){bad=true;}check(bad,"date format enforced");
  a.tags="Urgência, clientes";a.customCategory="Gestão";check(FileOrder.matches(a,"gestao","urgencia"),"personal category/tag filters ignore accents and case");
  OverlayRecovery recovery=new OverlayRecovery();check(recovery.due(0),"overlay initial retry available");recovery.failed(1000);check(!recovery.due(1001)&&recovery.due(3000),"overlay retry waits instead of looping");long last=3000;for(int i=0;i<20;i++){recovery.failed(last);long next=recovery.nextAttempt();check(next-last<=60000&&next>last,"overlay backoff bounded attempt "+i);last=next;}
  check(recovery.due(last),"overlay recovery continues beyond three failures");recovery.succeeded();check(recovery.due(0),"successful overlay recovery clears failure history");
  System.out.println("Passed "+passed+" ordering/recovery checks");
 }
}
