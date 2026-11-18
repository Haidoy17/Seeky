package mz.bam.seeky.android;

import java.io.*;
import java.util.*;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*;

public final class PdfReadingCheck {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        PDFBoxResourceLoader.init(new android.content.Context());File fixture=new File(args[0]),cache=fixture.getParentFile();
        try(PdfTextSource source=new PdfTextSource(fixture,cache)){
            check(source.count()==3,"reader text source reports the actual PDF page count");
            check(source.page(0).contains("Seeky searchable text")&&!source.partial,"reader extracts the first page for TTS without marking complete text partial");
            check(source.page(1).contains("Deep search second page"),"TTS can load the next page independently");
            check(source.page(2).trim().isEmpty(),"a blank PDF page remains blank and never receives invented narration");
            boolean rejected=false;try{source.page(3);}catch(IOException expected){rejected=true;}check(rejected,"page requests outside the document fail clearly");
            check(source.outline().isEmpty(),"PDFs without an embedded outline keep page navigation available");
        }
        File outlined=new File(cache,"reader-outline.pdf");
        try(PDDocument doc=PDDocument.load(fixture)){
            PDDocumentOutline root=new PDDocumentOutline();doc.getDocumentCatalog().setDocumentOutline(root);
            PDOutlineItem one=new PDOutlineItem();one.setTitle("Primeiro módulo");one.setDestination(doc.getPage(0));root.addLast(one);
            PDOutlineItem child=new PDOutlineItem();child.setTitle("Material do módulo");child.setDestination(doc.getPage(1));one.addLast(child);
            PDOutlineItem link=new PDOutlineItem();link.setTitle("No destination");root.addLast(link);
            doc.save(outlined);
        }
        try(PdfTextSource source=new PdfTextSource(outlined,cache)){
            List<PdfTextSource.Heading> headings=source.outline();check(headings.size()==2,"reader index excludes entries without a real page destination");
            check(headings.get(1).page==1&&headings.get(1).depth==1,"nested PDF headings retain their hierarchy and page");
        }
        PdfTextSource.LimitedWriter writer=new PdfTextSource.LimitedWriter();boolean limited=false;try{writer.write(new char[600000],0,600000);}catch(PdfTextSource.TextLimit expected){limited=true;}check(limited&&writer.value.length()==524288,"PDF text extraction stops at 512K characters per page");
        System.out.println("PASS "+checks+" actual PDF reading checks");
    }
}
