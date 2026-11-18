package mz.bam.seeky.android;
import android.app.Application;import android.content.Context;import android.graphics.Bitmap;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import com.googlecode.tesseract.android.TessBaseAPI;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
/** ARM OCR is exercised separately on the host with the same pinned language models. */
@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=33,application=Application.class,instrumentedPackages={"com.googlecode.tesseract.android"},shadows={OcrToolsTest.Engine.class})
public class OcrToolsTest {
 @Implements(value=TessBaseAPI.class,isInAndroidSdk=false) public static class Engine{
 static boolean released;static Bitmap image;static String language,output;static Runnable during;
 @Implementation protected static void __staticInitializer__(){} @Implementation protected void __constructor__(){}
 @Implementation protected boolean init(String root,String lang,int mode){language=lang;return true;} @Implementation protected void setImage(Bitmap bitmap){image=bitmap;}
 @Implementation protected String getUTF8Text(){if(during!=null)during.run();return output;} @Implementation protected int meanConfidence(){return 95;} @Implementation protected void recycle(){released=true;} @Implementation protected void stop(){}
 }
 Context context;Bitmap image;
 @Before public void setup()throws Exception{context=RuntimeEnvironment.getApplication();Engine.released=false;Engine.image=null;Engine.output="  Isaías 5:6\nTexto reconhecido.  ";Engine.during=null;File model=new File("tests/ocr/models/por.traineddata");assertTrue(model.isFile());Files.copy(model.toPath(),OcrEngine.model(context,"por").toPath(),StandardCopyOption.REPLACE_EXISTING);image=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);}
 @After public void close(){image.recycle();}
 @Test public void installedModelRecognisesOnePageAndReleasesNativeState()throws Exception{List<String> states=new ArrayList<>();String value=OcrEngine.create().recognise(context,image,"por",()->false,states::add);assertEquals("Isaías 5:6\nTexto reconhecido.",value);assertEquals("por",Engine.language);assertSame(image,Engine.image);assertTrue(Engine.released);assertTrue(states.contains("A reconhecer esta página…"));}
 @Test public void cancellingRecognitionDiscardsResultAndReleasesNativeState()throws Exception{AtomicBoolean stop=new AtomicBoolean();Engine.during=()->stop.set(true);try{OcrEngine.create().recognise(context,image,"por",stop::get,s->{});fail();}catch(InterruptedIOException expected){}assertTrue(Engine.released);}
 @Test public void unreadablePageDoesNotProduceASilentEmptyResult()throws Exception{Engine.output="  \n ";try{OcrEngine.create().recognise(context,image,"por",()->false,s->{});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("Não foi reconhecido texto"));}assertTrue(Engine.released);}
 @Test public void recognisedTextIsBoundToTheDocumentHashAndPage(){OcrCache.save(context,"file:///book.pdf",3,"hash-a","Texto reconhecido");assertEquals("Texto reconhecido",OcrCache.get(context,"file:///book.pdf",3,"hash-a"));assertEquals("",OcrCache.get(context,"file:///book.pdf",3,"hash-b"));assertEquals("",OcrCache.get(context,"file:///book.pdf",4,"hash-a"));}
}
