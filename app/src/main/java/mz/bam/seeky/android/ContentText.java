package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.*;

/** Bounded local extraction. No network, macros, external entities or ZIP extraction. */
public final class ContentText {
 public static final int LIMIT=524288;
 static byte[] read(InputStream in,int limit)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while(out.size()<limit&&(n=in.read(b,0,Math.min(b.length,limit-out.size())))!=-1)out.write(b,0,n);return out.toByteArray();}
 public static boolean supported(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(txt|csv|md|json|xml|html?|log|docx|xlsx|pptx)$");}
 public static String extract(InputStream in,String name)throws IOException {
  String n=name.toLowerCase(Locale.ROOT);
  if(n.matches(".*\\.(docx|xlsx|pptx)$")){
   ZipInputStream zip=new ZipInputStream(in){long expanded=0;final long started=System.nanoTime();@Override public int read(byte[] b,int off,int len)throws IOException {if(expanded>8388608||System.nanoTime()-started>3000000000L)throw new IOException("Limite de leitura do documento comprimido");int n=super.read(b,off,len);if(n>0)expanded+=n;return n;}};StringBuilder all=new StringBuilder();ZipEntry entry;int count=0;
   while((entry=zip.getNextEntry())!=null&&count++<512&&all.length()<LIMIT){String p=entry.getName();if(p.equals("word/document.xml")||p.equals("xl/sharedStrings.xml")||p.matches("xl/worksheets/sheet[0-9]+\\.xml")||p.matches("ppt/slides/slide[0-9]+\\.xml")){String xml=new String(read(zip,LIMIT-all.length()),StandardCharsets.UTF_8);all.append(xml.replaceAll("<[^>]*>"," ")).append('\n');}}
   return entities(all.toString());
  }
  String txt=new String(read(in,LIMIT),StandardCharsets.UTF_8);
  if(n.matches(".*\\.(xml|html?)$"))txt=txt.replaceAll("<[^>]*>"," ");
  return entities(txt);
 }
 static String entities(String s){return s.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&amp;","&");}
 private ContentText(){}
}
