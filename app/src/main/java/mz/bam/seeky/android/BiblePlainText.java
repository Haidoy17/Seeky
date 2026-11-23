package mz.bam.seeky.android;
import java.io.*;import java.util.*;import java.util.regex.*;
/** Stateful parser shared by TXT and extracted pages; verse numbers always come from the source. */
class BiblePlainText {
 static final Pattern BOOK_CHAPTER=Pattern.compile("(?iu)^(.+?)\\s+(?:(?:cap[ií]tulo|chapter|salmo|psalm)\\s+)?([1-9][0-9]{0,2})$");
 static final Pattern CHAPTER=Pattern.compile("(?iu)^(?:cap[ií]tulo|chapter|salmo|psalm)\\s+([1-9][0-9]{0,2})[.:]?$");
 static final Pattern VERSE_LINE=Pattern.compile("^([1-9][0-9]{0,2})[.)]?\\s+(.+)$");
 static final Pattern LOCAL=Pattern.compile("^([1-9][0-9]{0,2})[:.]([1-9][0-9]{0,2})\\s+(.+)$");
 final BibleText.Sink sink;String book="",pending="";int chapter,verse,count,candidate;
 BiblePlainText(BibleText.Sink sink){this.sink=sink;}
 boolean heading(String line)throws Exception {
  if(line.length()>256)return false;
  String title=line.replaceFirst("(?iu)^(?:o\\s+)?(?:livro\\s+de|evangelho\\s+(?:segundo|de))\\s+","");
  String b=BibleText.code(title);if(!b.isEmpty()){if(!b.equals(book)){flush();book=b;chapter=0;}candidate=0;return true;}
  Matcher both=BOOK_CHAPTER.matcher(title);
  if(both.matches()&&!(b=BibleText.code(both.group(1))).isEmpty()){flush();book=b;chapter=Integer.parseInt(both.group(2));candidate=0;return true;}
  Matcher ch=CHAPTER.matcher(line);
  if(ch.matches()&&!book.isEmpty()){flush();chapter=Integer.parseInt(ch.group(1));candidate=0;return true;}return false;
 }
 void feed(String text,java.util.function.BooleanSupplier stop)throws Exception {
  for(String raw:text.replace('\u00a0',' ').split("\\r\\n|\\r|\\n")) {
   if(stop.getAsBoolean())throw new InterruptedIOException();String line=raw.trim();if(line.isEmpty())continue;
   if(line.length()>65536)throw new IOException("Linha acima do limite de texto.");
   if(line.matches("[0-9]{1,4}")){candidate=Integer.parseInt(line);continue;}
   if(heading(line))continue;
   List<BibleReferences.Reference> refs=line.indexOf(':')<0&&line.indexOf('：')<0?Collections.emptyList():BibleReferences.atStart(line.substring(0,Math.min(200,line.length())));
   if(!refs.isEmpty()&&refs.get(0).start==0){BibleReferences.Reference r=refs.get(0);String value=line.substring(r.end).replaceFirst("^\\s*[-–—]\\s*","").trim();if(r.chapter!=r.lastChapter||r.verse!=r.lastVerse)throw new IOException("Separe os versículos por número no ficheiro de importação.");if(!value.isEmpty()){flush();book=r.book;chapter=r.chapter;verse=r.verse;pending=value;candidate=0;continue;}}
   Matcher verseLine=VERSE_LINE.matcher(line);
   if(candidate>0&&!book.isEmpty()&&(chapter==0||candidate==chapter+1)&&verseLine.matches()&&verseLine.group(1).equals("1")){flush();chapter=candidate;}
   candidate=0;
   Matcher local=LOCAL.matcher(line);
   if(local.matches()&&!book.isEmpty()){flush();chapter=Integer.parseInt(local.group(1));verse=Integer.parseInt(local.group(2));pending=local.group(3);continue;}
   if(verseLine.matches()&&!book.isEmpty()&&chapter>0){flush();verse=Integer.parseInt(verseLine.group(1));pending=verseLine.group(2);continue;}
   if(verse>0){if(pending.length()+line.length()+1>16000)throw new IOException("Não foi possível separar os versículos. Verifique o capítulo e os números de versículos.");pending+=" "+line;}
  }
 }
 void flush()throws Exception {if(verse>0){if(BibleText.emit(sink,book,chapter,verse,pending)&&++count>200000)throw new IOException("A Bíblia excede 200000 versículos.");verse=0;pending="";}}
}
