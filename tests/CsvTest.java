package mz.bam.seeky.android;
import java.io.*;
public class CsvTest {
 static int passed=0;static void check(boolean b,String name){if(!b)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception {
  check(CsvFields.cell("Relatório, Beira").equals("\"Relatório, Beira\""),"CSV preserves commas and Unicode in one field");
  check(CsvFields.cell("Diz \"olá\"").equals("\"Diz \"\"olá\"\"\""),"CSV escapes embedded quotes");
  for(String s:new String[]{"=1+1","+SUM(A1)","-1+2","@command","  =formula","\tunsafe","\runsafe"})check(CsvFields.cell(s).startsWith("\"'"),"CSV neutralises formula/control prefix "+s.replace('\r','?').replace('\t','?'));
  StringWriter out=new StringWriter();CsvFields.row(out,"Data","Nome","linha\nseguinte");check(out.toString().equals("\"Data\",\"Nome\",\"linha\nseguinte\"\r\n"),"CSV row has stable separators, quoted newlines and CRLF");
  check(CsvFields.cell(null).equals("\"\""),"CSV handles absent detail");System.out.println("Passed "+passed+" CSV checks");
 }
}
