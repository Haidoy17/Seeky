package mz.bam.seeky.android;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;

/** Explicit, bounded PDF indexing. Never invokes OCR or runs during file discovery. */
final class PdfIndexText {
    interface Control {boolean cancelled();}
    static final int MAX_CHARS=262144,MAX_PAGES=80;
    static final class Result {
        final String text;final int pages,total;final boolean truncated;
        Result(String text,int pages,int total,boolean truncated){this.text=text;this.pages=pages;this.total=total;this.truncated=truncated;}
    }
    static final class Limit extends IOException {}
    static final class Output extends Writer {
        final StringBuilder text=new StringBuilder();
        public void write(char[] value,int start,int length)throws IOException {
            int count=Math.min(length,MAX_CHARS-text.length());
            text.append(value,start,count);
            if(count<length)throw new Limit();
        }
        public void flush(){}public void close(){}
    }
    static Result read(File file,File cache,Control control)throws IOException {
        if(file.length()>33554432L)throw new IOException("Indexação de PDF limitada a 32 MB por documento.");
        long deadline=System.nanoTime()+15000000000L;Output output=new Output();int processed=0;
        try(PDDocument document=PDDocument.load(file,MemoryUsageSetting.setupMixed(4194304).setTempDir(cache))){
            if(document.isEncrypted()||!document.getCurrentAccessPermission().canExtractContent())throw new IOException("O PDF restringe a extracção de texto.");
            int total=document.getNumberOfPages();boolean limited=false;
            PDFTextStripper stripper=new PDFTextStripper();stripper.setSortByPosition(true);
            for(int page=1;page<=Math.min(MAX_PAGES,total);page++){
                if(control.cancelled())throw new InterruptedIOException("Indexação cancelada; texto anterior conservado.");
                if(System.nanoTime()>deadline){limited=true;break;}
                stripper.setStartPage(page);stripper.setEndPage(page);
                try{stripper.writeText(document,output);processed=page;}catch(Limit limit){limited=true;break;}
            }
            return new Result(output.text.toString(),processed,total,limited||processed<total);
        }
    }
    private PdfIndexText(){}
}
