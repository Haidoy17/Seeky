package mz.bam.seeky.android;
import java.io.*;
final class DocumentBudget {
    static long format(String name,String mime){String kind=MediaFormats.kind(name,mime);return kind.equals("text")?64L*1048576:kind.equals("epub")||kind.equals("docx")?2L*1024*1048576:8L*1024*1048576;}
    static long available(File folder,String name,String mime)throws IOException{long available=folder.getUsableSpace()-32L*1048576;if(available<=0)throw new IOException("Sem espaço livre para importar. Liberte espaço e retome.");return Math.min(format(name,mime),available);}
    private DocumentBudget(){}
}
