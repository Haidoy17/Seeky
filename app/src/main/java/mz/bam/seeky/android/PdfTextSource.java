package mz.bam.seeky.android;

import java.io.*;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*;
import java.util.*;

/** Serial, bounded PDF text extraction shared by the reader and read-aloud service. */
final class PdfTextSource implements AutoCloseable {
    final PDDocument document;
    boolean partial;
    PdfTextSource(File file, File cache) throws IOException {
        if (file.length() > 67108864L) throw new IOException("Extracção de texto limitada a PDFs de 64 MB.");
        document = PDDocument.load(file, MemoryUsageSetting.setupMixed(4194304).setTempDir(cache));
        if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
            document.close();
            throw new IOException("Este PDF restringe a extracção de texto.");
        }
    }
    int count() { return document.getNumberOfPages(); }
    static final class Heading {
        final String title; final int page,depth;
        Heading(String title,int page,int depth){this.title=title;this.page=page;this.depth=depth;}
    }
    List<Heading> outline() throws IOException {
        List<Heading> result=new ArrayList<>();PDDocumentOutline root=document.getDocumentCatalog().getDocumentOutline();
        if(root!=null)outline(root.getFirstChild(),0,result,new HashSet<>());
        return result;
    }
    void outline(PDOutlineItem item,int depth,List<Heading> result,Set<Object> seen) throws IOException {
        if(depth>12)return;
        while(item!=null&&seen.size()<300&&seen.add(item.getCOSObject())) {
            try{PDPage destination=item.findDestinationPage(document);int page=destination==null?-1:document.getPages().indexOf(destination);String title=item.getTitle();if(page>=0&&title!=null&&!title.trim().isEmpty())result.add(new Heading(title.substring(0,Math.min(240,title.length())),page,depth));}catch(IOException | IllegalArgumentException ignored){}
            outline(item.getFirstChild(),depth+1,result,seen);item=item.getNextSibling();
        }
    }
    String page(int part) throws IOException {
        if (part < 0 || part >= count()) throw new IOException("Página indisponível.");
        partial = false;
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(part + 1); stripper.setEndPage(part + 1); stripper.setSortByPosition(true);
        LimitedWriter out = new LimitedWriter();
        try { stripper.writeText(document, out); } catch (TextLimit e) { partial = true; }
        return out.value.toString();
    }
    PdfGlyphs mappedPage(int part)throws IOException {
        if(part<0||part>=count())throw new IOException("Página indisponível.");
        final LimitedWriter out=new LimitedWriter();final List<PdfGlyphs.Glyph> boxes=new ArrayList<>();
        PDFTextStripper stripper=new PDFTextStripper(){
            protected void writeString(String value,List<com.tom_roush.pdfbox.text.TextPosition> positions)throws IOException {
                int start=out.value.length();super.writeString(value,positions);int from=0;
                for(com.tom_roush.pdfbox.text.TextPosition p:positions){if(boxes.size()>=25000)break;String glyph=p.getUnicode();if(glyph==null||glyph.isEmpty())continue;int at=value.indexOf(glyph,from);if(at<0){glyph=java.text.Normalizer.normalize(glyph,java.text.Normalizer.Form.NFKC);at=value.indexOf(glyph,from);}if(at<0)continue;int end=at+glyph.length();from=end;float h=Math.max(1,p.getHeight());boxes.add(new PdfGlyphs.Glyph(start+at,start+end,p.getX(),p.getY()-h,p.getWidth(),h*1.2f));}
            }
        };
        stripper.setStartPage(part+1);stripper.setEndPage(part+1);stripper.setSortByPosition(true);try{stripper.writeText(document,out);}catch(TextLimit truncated){}
        PDPage page=document.getPage(part);float width=page.getCropBox().getWidth(),height=page.getCropBox().getHeight();if(Math.abs(page.getRotation())%180==90){float swap=width;width=height;height=swap;}
        return new PdfGlyphs(out.value.toString(),width,height,boxes);
    }
    static final class TextLimit extends IOException { private static final long serialVersionUID = 1L; }
    static final class LimitedWriter extends Writer {
        final StringBuilder value = new StringBuilder();
        public void write(char[] b, int off, int n) throws IOException {
            int room = 524288 - value.length();
            value.append(b, off, Math.min(n, Math.max(0, room)));
            if (n > room) throw new TextLimit();
        }
        public void flush() {} public void close() {}
    }
    public void close() throws IOException { document.close(); }
}
