package mz.bam.seeky.android;
import android.graphics.*;import android.graphics.pdf.PdfDocument;import java.io.*;import java.util.*;import org.robolectric.annotation.*;import org.robolectric.util.ReflectionHelpers;import org.robolectric.util.ReflectionHelpers.ClassParameter;
/** Robolectric 4.14 has no PdfDocument native implementation. This test-only bridge captures
 * production page layout on a real Skia bitmap, then packages that bitmap as a host PDF.
 * It does not validate Android's native PDF encoder. Never included in the APK. */
@Implements(PdfDocument.class)
public class PdfCanvasHost {
 static final List<String> text=new ArrayList<>();final List<Bitmap> pages=new ArrayList<>();
 @Implementation protected void __constructor__(){text.clear();}
 @Implementation protected PdfDocument.Page startPage(PdfDocument.PageInfo info){Bitmap image=Bitmap.createBitmap(info.getPageWidth(),info.getPageHeight(),Bitmap.Config.ARGB_8888);pages.add(image);Canvas canvas=new Canvas(image){@Override public void drawText(String value,float x,float y,Paint paint){text.add(value);super.drawText(value,x,y,paint);}};return ReflectionHelpers.callConstructor(PdfDocument.Page.class,ClassParameter.from(Canvas.class,canvas),ClassParameter.from(PdfDocument.PageInfo.class,info));}
 @Implementation protected void finishPage(PdfDocument.Page page){}
 @Implementation protected void writeTo(OutputStream out)throws IOException{try(com.tom_roush.pdfbox.pdmodel.PDDocument doc=new com.tom_roush.pdfbox.pdmodel.PDDocument()){for(Bitmap image:pages){com.tom_roush.pdfbox.pdmodel.PDPage page=new com.tom_roush.pdfbox.pdmodel.PDPage(new com.tom_roush.pdfbox.pdmodel.common.PDRectangle(image.getWidth(),image.getHeight()));doc.addPage(page);ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject picture=com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc,bytes.toByteArray(),"revision-layout");try(com.tom_roush.pdfbox.pdmodel.PDPageContentStream stream=new com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc,page)){stream.drawImage(picture,0,0,image.getWidth(),image.getHeight());}}doc.save(out);}}
 @Implementation protected void close(){for(Bitmap page:pages)page.recycle();pages.clear();}
 @Implementation protected void finalize(){}
}
