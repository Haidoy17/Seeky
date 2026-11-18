package mz.bam.seeky.android;
import java.io.*;
final class CsvFields {
 static String cell(String value){String text=value==null?"":value;if(text.matches("(?s)^[\\s\\ufeff]*[=+@-].*")||text.startsWith("\t")||text.startsWith("\r"))text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
 static void row(Writer out,String... cells)throws IOException {for(int i=0;i<cells.length;i++){if(i>0)out.write(',');out.write(cell(cells[i]));}out.write("\r\n");}
 private CsvFields(){}
}
