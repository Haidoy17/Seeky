package mz.bam.seeky.android;
import java.io.*;import java.nio.charset.*;import java.util.*;
public final class BibleImportTest {
 static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
 static List<String> parse(String text)throws Exception{List<String> values=new ArrayList<>();BibleText.parse(new StringReader(text),(b,c,v,t)->values.add(b+" "+c+":"+v+" "+t),()->false);return values;}
 public static void main(String[] args)throws Exception{
  List<String> wrapped=parse("João 1:1 No princípio\ne o Verbo estava com Deus.\nJoão 1:2 Uma continuação.");check(wrapped.size()==2&&wrapped.get(0).endsWith("No princípio e o Verbo estava com Deus."),"wrapped verses");
  List<String> headings=parse("GÊNESIS\nCapítulo 1\n1 No princípio criou Deus.\n2 A terra era sem forma.\nCapítulo 2\n1 Assim se completou.");check(headings.size()==3&&headings.get(2).contains("2:1"),"book and chapter headings");
  List<String> numbers=parse("Gênesis\n1\n1 Primeiro versículo.\n2\n2 Segundo versículo ainda no capítulo um.\n2\n1 Primeiro versículo do capítulo dois.");check(numbers.size()==3&&numbers.get(1).contains("1:2")&&numbers.get(2).contains("2:1"),"isolated chapter without treating footer as chapter");
  String text="Joao 1:1 A criação, a bênção e o coração.\n";for(Charset cs:new Charset[]{StandardCharsets.UTF_8,StandardCharsets.UTF_16LE,StandardCharsets.UTF_16BE,Charset.forName("windows-1252")}){byte[] raw=text.getBytes(cs);if(cs==StandardCharsets.UTF_16LE||cs==StandardCharsets.UTF_16BE){byte[] bom=cs==StandardCharsets.UTF_16LE?new byte[]{(byte)255,(byte)254}:new byte[]{(byte)254,(byte)255};ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(bom);bytes.write(raw);raw=bytes.toByteArray();}final byte[] data=raw;List<String> values=new ArrayList<>();BibleEncoding.parse(()->new ByteArrayInputStream(data),(b,c,v,t)->values.add(t),values::clear,()->false);check(values.size()==1&&values.get(0).equals("A criação, a bênção e o coração."),"strict encoding "+cs.name());}
  String late="Joao 1:1 "+String.join("",Collections.nCopies(8500,"a"))+"\nJoao 1:2 Criação.";byte[] cp=late.getBytes(Charset.forName("windows-1252"));List<String> retried=new ArrayList<>();BibleEncoding.parse(()->new ByteArrayInputStream(cp),(b,c,v,t)->retried.add(t),retried::clear,()->false);check(retried.size()==2&&retried.get(1).equals("Criação."),"late encoding failure resets staged rows");
  try{parse("Isaías,5,6,Texto válido\nLinha ilegível sem referência\n");throw new AssertionError("Malformed CSV accepted");}catch(IOException expected){checks++;}
  try{BibleText.parse(new StringReader(text),(b,c,v,t)->{},()->true);throw new AssertionError("Cancel ignored");}catch(InterruptedIOException expected){checks++;}
  try{parse("Texto sem livro ou números");throw new AssertionError("Empty Bible accepted");}catch(IOException expected){checks++;}
  System.out.println("PASS "+checks+" Bible import regressions");
 }
}
