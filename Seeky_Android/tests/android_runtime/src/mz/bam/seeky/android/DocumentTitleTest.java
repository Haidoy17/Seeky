package mz.bam.seeky.android;

import android.app.Application;import android.content.*;import android.content.pm.ProviderInfo;import android.database.Cursor;import android.os.ParcelFileDescriptor;import android.net.Uri;import java.io.*;
import com.tom_roush.pdfbox.pdmodel.*;import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*;import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,application=Application.class)
public class DocumentTitleTest {
 static File source;
 public static class Provider extends ContentProvider {public boolean onCreate(){return true;}public String getType(Uri uri){return "application/pdf";}public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);}public Cursor query(Uri uri,String[] p,String s,String[] a,String o){return null;}public Uri insert(Uri uri,ContentValues v){return null;}public int update(Uri uri,ContentValues v,String s,String[] a){return 0;}public int delete(Uri uri,String s,String[] a){return 0;}}

 @Test public void pdfMetadataAndMostSpecificOutlineSupplyBookAndSubtitle()throws Exception{
  Application app=RuntimeEnvironment.getApplication();File file=new File(app.getCacheDir(),"physics.pdf");
  try(PDDocument pdf=new PDDocument()){for(int i=0;i<3;i++)pdf.addPage(new PDPage());pdf.getDocumentInformation().setTitle("Física e natureza");pdf.getDocumentInformation().setAuthor("Ana Silva");PDDocumentOutline outline=new PDDocumentOutline();pdf.getDocumentCatalog().setDocumentOutline(outline);PDOutlineItem intro=new PDOutlineItem();intro.setTitle("Introdução");PDPageFitDestination first=new PDPageFitDestination();first.setPage(pdf.getPage(0));intro.setDestination(first);outline.addLast(intro);PDOutlineItem section=new PDOutlineItem();section.setTitle("Energia e movimento");PDPageFitDestination second=new PDPageFitDestination();second.setPage(pdf.getPage(1));section.setDestination(second);intro.addLast(section);pdf.save(file);}
  source=file;Provider provider=new Provider();ProviderInfo info=new ProviderInfo();info.authority="seeky.pdf.title";provider.attachInfo(app,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(info.authority,provider);
  try(SpeechDocument document=new SpeechDocument(app,Uri.parse("content://seeky.pdf.title/book"),"physics.pdf","application/pdf",()->false)){assertEquals("Física e natureza",document.title());assertEquals("Ana Silva",document.author());assertEquals("Introdução",document.partTitle(0,"Outra primeira linha"));assertEquals("Energia e movimento",document.partTitle(1,"Outra primeira linha"));assertEquals("Energia e movimento",document.partTitle(2,"Outra primeira linha"));assertEquals("Física e natureza — Energia e movimento — 0003.wav",AudioTitles.file(document.title(),document.partTitle(2,""),2,1));}
 }
}
