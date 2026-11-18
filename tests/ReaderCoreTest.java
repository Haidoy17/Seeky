package mz.bam.seeky.android;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public class ReaderCoreTest {
 static int passed=0;
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 interface Work {void run()throws Exception;}
 static void rejected(Work w,String name)throws Exception{boolean failed=false;try{w.run();}catch(IOException e){failed=true;}check(failed,name);}
 static void zip(File file,Map<String,String> entries)throws Exception {try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){for(Map.Entry<String,String> e:entries.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();}}}
 public static void main(String[] args)throws Exception {
  File folder=Files.createTempDirectory("seeky-books-").toFile();try{
   Map<String,String> epub=new LinkedHashMap<>();epub.put("META-INF/container.xml","<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>");
   epub.put("OPS/book.opf","<package xmlns='http://www.idpf.org/2007/opf'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>Gestão e logística</dc:title><dc:creator>Autor Beira</dc:creator></metadata><manifest><item id='b' href='chapter%202.xhtml' media-type='application/xhtml+xml'/><item id='a' href='chapter1.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>");
   epub.put("OPS/book.opf",epub.get("OPS/book.opf").replace("</manifest>","<item id='navigation' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/></manifest>"));
   epub.put("OPS/nav.xhtml","<html xmlns='http://www.w3.org/1999/xhtml'><body><nav><a href='chapter1.xhtml#begin'>Introdução real</a><a href='chapter%202.xhtml'>Conclusão real</a><a href='https://example.invalid/file'>Remote link ignored</a></nav></body></html>");
   epub.put("OPS/chapter1.xhtml","<?xml version='1.0'?><!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.1//EN' 'https://example.invalid/external.dtd'><html xmlns='http://www.w3.org/1999/xhtml'><head><title>Primeiro</title><script>bad head script</script></head><body><h1>Capítulo um</h1><p>Olá&nbsp;Beira.</p><p>Segundo parágrafo.</p><script>bad script</script><style>bad style</style></body></html>");
   epub.put("OPS/chapter 2.xhtml","<html xmlns='http://www.w3.org/1999/xhtml'><body><p>Fim do livro.</p></body></html>");
   File book=new File(folder,"book.epub");zip(book,epub);try(BookArchive b=new BookArchive(book,true)){
    check(b.title.equals("Gestão e logística")&&b.author.equals("Autor Beira"),"EPUB real OPF title and creator");
    check(b.chapters.size()==2&&b.chapters.get(0).equals("OPS/chapter1.xhtml"),"EPUB spine order takes precedence over ZIP/manifest order");
    check(b.chapter(0).contains("Olá\u00a0Beira.")&&b.chapter(0).contains("\n\n"),"EPUB XHTML doctype/entity compatibility and paragraphs");
    check(!b.chapter(0).contains("bad script")&&!b.chapter(0).contains("bad style")&&!b.chapter(0).contains("bad head"),"EPUB scripts/styles/head not rendered or executed");
    check(b.names.get(0).equals("Introdução real")&&b.names.get(1).equals("Conclusão real"),"EPUB navigation uses actual chapter labels and ignores remote links");
    check(b.chapter(1).equals("Fim do livro."),"URI-encoded chapter names resolve within archive");
    rejected(()->b.chapter(10),"invalid chapter gives a clear failure");
   }
   Map<String,String> docx=new LinkedHashMap<>();docx.put("word/document.xml","<w:document xmlns:w='urn:word'><w:body><w:p><w:r><w:t>Contrato</w:t></w:r><w:r><w:t> Beira</w:t></w:r></w:p><w:p><w:r><w:t>Segunda linha</w:t><w:br/><w:t>continuação</w:t></w:r></w:p></w:body></w:document>");docx.put("docProps/core.xml","<cp:coreProperties xmlns:cp='urn:core' xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>Documento de ensaio</dc:title><dc:creator>Haidoy</dc:creator></cp:coreProperties>");
   File word=new File(folder,"test.docx");zip(word,docx);try(BookArchive b=new BookArchive(word,false)){check(b.title.equals("Documento de ensaio")&&b.author.equals("Haidoy"),"DOCX core properties read");check(b.chapter(0).contains("Contrato Beira\n\nSegunda linha\ncontinuação"),"DOCX runs concatenate with paragraph/break structure");}
   rejected(()->BookArchive.xml("<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><x>&secret;</x>".getBytes(StandardCharsets.UTF_8)),"external entity definition rejected before DOM parse");
   rejected(()->BookArchive.xml("<!DOCTYPE x [<!ENTITY a 'aaaaaaaa'>]><x>&a;</x>".getBytes(StandardCharsets.UTF_16LE)),"UTF16 entity declarations rejected");
   check(BookArchive.xml("<?xml version='1.0' encoding='UTF-16'?><r>Olá</r>".getBytes(StandardCharsets.UTF_16)).getDocumentElement().getTextContent().equals("Olá"),"UTF16 book XML correctly decoded");
   rejected(()->BookArchive.safePath("../../secret"),"EPUB traversal outside archive refused");rejected(()->BookArchive.safePath("https://example.invalid/a"),"remote EPUB resources refused");
   rejected(()->BookArchive.xml((new String(new char[140]).replace("\0","<r>")+new String(new char[140]).replace("\0","</r>")).getBytes(StandardCharsets.UTF_8)),"excessively deep document rejected before DOM recursion");
   check(BookArchive.safePath("OPS/part/../chapter.xhtml").equals("OPS/chapter.xhtml"),"safe relative archive path normalisation");
   Map<String,String> broken=new HashMap<>(epub);broken.remove("OPS/book.opf");File missing=new File(folder,"missing.epub");zip(missing,broken);rejected(()->new BookArchive(missing,true),"missing EPUB package fails instead of showing invented content");
   Map<String,String> bomb=new HashMap<>();bomb.put("word/document.xml",new String(new char[BookArchive.CHAPTER_LIMIT+1]).replace('\0','x'));File big=new File(folder,"big.docx");zip(big,bomb);try(BookArchive b=new BookArchive(big,false)){rejected(()->b.chapter(0),"compressed chapter expansion limit enforced");}
   for(String ext:new String[]{"mp3","aac","flac","wav","ogg","opus","amr","m4a","m4b","wma"})check(MediaFormats.kind("song."+ext,"application/octet-stream").equals("audio"),"audio routing "+ext);
   for(String ext:new String[]{"jpg","png","gif","webp","bmp","heic","avif","tiff","psd","raw"})check(MediaFormats.kind("picture."+ext,"application/octet-stream").equals("image"),"image routing with decoder fallback "+ext);
   check(MediaFormats.mime("a.doc","application/octet-stream").equals("application/msword")&&MediaFormats.kind("a.doc","application/msword").equals("external"),"legacy DOC honestly routes to compatible external reader");
   check(MediaFormats.kind("a.epub","application/epub+zip").equals("epub")&&Rules.category("a.epub","application/epub+zip").equals("Documentos"),"EPUB reader and category routing");
   check(MediaFormats.mime("a.docx","").contains("wordprocessingml")&&MediaFormats.kind("a.docx","").equals("docx"),"Word DOCX reader and MIME routing");
  }finally{try(java.util.stream.Stream<Path> paths=Files.walk(folder.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});}}
  System.out.println("Passed "+passed+" reader checks");
 }
}
