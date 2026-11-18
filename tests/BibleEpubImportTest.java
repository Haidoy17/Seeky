package mz.bam.seeky.android;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;

public final class BibleEpubImportTest {
 static int checks;static void ok(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 static void entry(ZipOutputStream z,String name,String text)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(text.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
 static File archive(String body)throws Exception {
  File f=File.createTempFile("seeky-bible-", ".epub");f.deleteOnExit();
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){
   entry(z,"META-INF/container.xml","<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>");
   entry(z,"book.opf","<package><metadata/><manifest><item id='g' href='GEN.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='g'/></spine></package>");
   entry(z,"GEN.xhtml",body);
  }return f;
 }
 public static void main(String[] args)throws Exception {
  List<String> output=new ArrayList<>();BiblePlainText parser=new BiblePlainText((b,c,v,t)->output.add(b+" "+c+":"+v+" "+t));
  File f=archive("<!DOCTYPE html><html><head><title>Índice</title></head><body><div class='mt'>Gênesis</div><div class='c'>1</div><p><span class='v'>1</span>\n Primeiro <i>texto</i>.<a class='notemark' href='#FN1'>a</a><span class='v'>2&#160;</span>Segundo texto.</p><div class='s'>Subtítulo editorial</div><p><span class='verse'>3</span>Terceiro texto.</p><div class='c'>2</div><p><span class='verse'>1</span>\n Outro capítulo.</p><div class='footnotes'><p>1 Uma nota que não é versículo.</p></div></body></html>");
  try(BookArchive b=new BookArchive(f,true)){BibleEpubText.read(b,0,parser,()->false,()->{});}parser.flush();
  ok(output.equals(Arrays.asList("Génesis 1:1 Primeiro texto.","Génesis 1:2 Segundo texto.","Génesis 1:3 Terceiro texto.","Génesis 2:1 Outro capítulo.")),"inline labels, nested typography, pretty-print whitespace, headings and excluded footnotes");
  try(BookArchive b=new BookArchive(f,true)){try{BibleEpubText.read(b,0,new BiblePlainText((bk,c,v,t)->{}),()->true,()->{});throw new AssertionError("Ignored cancellation");}catch(InterruptedIOException expected){checks++;}}
  File unsafe=archive("<!DOCTYPE html [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><html><body>&secret;</body></html>");
  try(BookArchive b=new BookArchive(unsafe,true)){try{BibleEpubText.read(b,0,new BiblePlainText((bk,c,v,t)->{}),()->false,()->{});throw new AssertionError("Accepted entity");}catch(IOException expected){checks++;}}
  String[] codes={"GEN","EXO","LEV","NUM","DEU","JOS","JDG","RUT","1SA","2SA","1KI","2KI","1CH","2CH","EZR","NEH","EST","JOB","PSA","PRO","ECC","SNG","ISA","JER","LAM","EZK","DAN","HOS","JOL","AMO","OBA","JON","MIC","NAM","HAB","ZEP","HAG","ZEC","MAL","MAT","MRK","LUK","JHN","ACT","ROM","1CO","2CO","GAL","EPH","PHP","COL","1TH","2TH","1TI","2TI","TIT","PHM","HEB","JAS","1PE","2PE","1JN","2JN","3JN","JUD","REV"};
  File large=File.createTempFile("seeky-bible-69-", ".epub");large.deleteOnExit();
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(large))){
   entry(z,"META-INF/container.xml","<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>");StringBuilder manifest=new StringBuilder(),spine=new StringBuilder();
   List<String> parts=new ArrayList<>(Arrays.asList("cover","preface"));parts.addAll(Arrays.asList(codes));parts.add("copyright");
   for(String part:parts){manifest.append("<item id='"+part+"' href='"+part+".xhtml' media-type='application/xhtml+xml'/>");spine.append("<itemref idref='"+part+"'/>");}
   entry(z,"book.opf","<package><metadata/><manifest>"+manifest+"</manifest><spine>"+spine+"</spine></package>");
   for(String part:parts){StringBuilder html=new StringBuilder("<html><body>");if(Arrays.asList(codes).contains(part)){html.append("<div class='mt'>"+BibleText.code(part)+"</div>");for(int c=1;c<=20;c++){html.append("<div class='chapterlabel'>"+c+"</div><p>");for(int v=1;v<=25;v++)html.append("<span class='verse'>"+v+"&#160;</span>Texto de ensaio para verificar a separação exacta dos versículos. ");html.append("</p>");}}entry(z,part+".xhtml",html+"</body></html>");}
  }
  Set<String> addresses=new HashSet<>();int[] pulses={0};BiblePlainText full=new BiblePlainText((b,c,v,t)->{if(!addresses.add(b+" "+c+":"+v))throw new AssertionError("Repeated verse");if(!t.equals("Texto de ensaio para verificar a separação exacta dos versículos."))throw new AssertionError(t);});
  long start=System.nanoTime();try(BookArchive b=new BookArchive(large,true)){ok(b.chapters.size()==69,"69 spine entries");for(int i=0;i<b.chapters.size();i++)BibleEpubText.read(b,i,full,()->false,()->{pulses[0]++;});}full.flush();
  ok(full.count==33000&&addresses.size()==33000,"all 33000 synthetic verses indexed without joins or duplicates");
  ok(addresses.contains("Génesis 1:1")&&addresses.contains("Apocalipse 20:25"),"first and last book remain queryable");ok(pulses[0]>33000,"intra-book progress/cancellation callbacks");
  System.out.println("PASS "+checks+" EPUB Bible checks; 69 parts / 33000 synthetic verses in "+(System.nanoTime()-start)/1000000+" ms");
 }
}
