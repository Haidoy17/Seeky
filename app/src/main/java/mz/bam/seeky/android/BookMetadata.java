package mz.bam.seeky.android;
import android.content.Context;import java.io.*;import java.util.*;import java.util.zip.*;import org.w3c.dom.*;

/** Read container metadata once, without rendering pages or loading book bodies. */
final class BookMetadata {
    String title="",author="",series="";
    static BookMetadata read(Context c,File file,String name,String mime){BookMetadata out=new BookMetadata();String kind=MediaFormats.kind(name,mime);try{
        if(kind.equals("epub")){try(ZipFile zip=new ZipFile(file)){ZipEntry entry=zip.getEntry("META-INF/container.xml");if(entry!=null){String path="";try(InputStream in=zip.getInputStream(entry)){Document doc=BookArchive.xml(ContentText.read(in,1048576));NodeList roots=doc.getElementsByTagNameNS("*","rootfile");if(roots.getLength()>0)path=((Element)roots.item(0)).getAttribute("full-path");}ZipEntry opf=zip.getEntry(path);if(opf!=null)try(InputStream in=zip.getInputStream(opf)){Document doc=BookArchive.xml(ContentText.read(in,2097152));NodeList all=doc.getElementsByTagName("*");for(int i=0;i<all.getLength();i++){Element e=(Element)all.item(i);String local=e.getLocalName()==null?e.getTagName().replaceFirst(".*:",""):e.getLocalName();if(local.equals("title")&&out.title.isEmpty())out.title=AudioTitles.clean(e.getTextContent());if(local.equals("creator")&&out.author.isEmpty())out.author=AudioTitles.clean(e.getTextContent());if(local.equals("meta")){if(e.getAttribute("name").equals("calibre:series"))out.series=AudioTitles.clean(e.getAttribute("content"));if(e.getAttribute("property").equals("belongs-to-collection"))out.series=AudioTitles.clean(e.getTextContent());}}}}}}
        else if(kind.equals("pdf")){com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c);try(com.tom_roush.pdfbox.pdmodel.PDDocument doc=com.tom_roush.pdfbox.pdmodel.PDDocument.load(file,com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())){out.title=AudioTitles.clean(doc.getDocumentInformation().getTitle());out.author=AudioTitles.clean(doc.getDocumentInformation().getAuthor());}}
    }catch(Exception|OutOfMemoryError e){AppDiagnostics.record(c,"book-metadata",e);}if(out.title.isEmpty()||AudioTitles.generic(out.title))out.title=AudioTitles.base(name);out.title=AudioTitles.limit(out.title,240,720);out.author=AudioTitles.limit(out.author,200,600);out.series=AudioTitles.limit(out.series,120,360);return out;}
    private BookMetadata(){}
}
