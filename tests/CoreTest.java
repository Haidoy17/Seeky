package mz.bam.seeky.android;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
public class CoreTest {
 static int passed=0;
 static void check(boolean result,String name){if(!result)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static InputStream text(String s){return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));}
 static byte[] zip(String path,String body)throws Exception {ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){z.putNextEntry(new ZipEntry(path));z.write(body.getBytes(StandardCharsets.UTF_8));z.closeEntry();}return b.toByteArray();}
 public static void main(String[]args)throws Exception {
  check(Rules.normalize("Gestão de Logística — BEIRA").equals("gestao de logistica — beira"),"accents and case");
  check(Rules.category("RELATORIO.PDF","application/pdf").equals("PDF"),"PDF case");
  check(Rules.category("foto.jpg","image/jpeg").equals("Imagens"),"image MIME");
  check(Rules.category("teste.docx","application/octet-stream").equals("Documentos"),"Office category");
  check(Rules.like("a%b_c\\d").equals("%a\\%b\\_c\\\\d%"),"LIKE metacharacters escaped");
  check(Rules.validName("Relatório final 2026.pdf"),"valid Portuguese filename");
  for(String s:new String[]{"","  ","..",".","a/b","a\\b","a\nb\nc","a\u0000b"})check(!Rules.validName(s),"reject unsafe filename "+s.replace('\n','?'));
  check(ContentText.extract(text("Olá Beira"),"a.txt").equals("Olá Beira"),"UTF8 text");
  check(ContentText.extract(text("<p>Gestão &amp; logística</p>"),"a.html").contains("Gestão & logística"),"HTML entities");
  check(ContentText.extract(new ByteArrayInputStream(zip("word/document.xml","<w:p><w:t>Factura Beira</w:t></w:p>")),"a.docx").contains("Factura Beira"),"DOCX extraction");
  check(ContentText.extract(new ByteArrayInputStream(zip("xl/sharedStrings.xml","<t>Armazém</t>")),"a.xlsx").contains("Armazém"),"XLSX extraction");
  check(ContentText.extract(new ByteArrayInputStream(zip("ppt/slides/slide1.xml","<a:t>Apresentação</a:t>")),"a.pptx").contains("Apresentação"),"PPTX extraction");
  check(ContentText.extract(new ByteArrayInputStream(zip("secret.txt","invisible")),"a.docx").trim().isEmpty(),"only known Office parts");
  byte[] big=new byte[ContentText.LIMIT+100];java.util.Arrays.fill(big,(byte)'x');check(ContentText.extract(new ByteArrayInputStream(big),"a.txt").length()==ContentText.LIMIT,"text extraction bounded");
  byte[] bomb=zip("ignored.bin",new String(new char[9000000]).replace('\0','x'));boolean rejected=false;try{ContentText.extract(new ByteArrayInputStream(bomb),"a.docx");}catch(IOException e){rejected=true;}check(rejected,"compressed expansion bounded");
  check(!ContentText.supported("scan.pdf"),"PDF content unsupported is explicit");
  check(Rules.bytes(1048576).equals("1.0 MB"),"size formatting");
  System.out.println("Passed "+passed+" checks");
 }
}
