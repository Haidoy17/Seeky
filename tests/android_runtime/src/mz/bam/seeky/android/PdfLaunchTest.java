package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import org.robolectric.shadow.api.Shadow;
import android.net.Uri;
import android.os.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE, sdk=33, qualifiers="w320dp-h640dp-mdpi", application=Application.class, shadows={PdfLaunchTest.RendererDouble.class,PdfLaunchTest.PageDouble.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PdfLaunchTest {
 // Robolectric 4.14 does not implement PDFium. This double isolates lifecycle/layout;
 // real PDF extraction is separately tested with PDFBox in the host suite.
 @Implements(PdfRenderer.class) public static class RendererDouble {
  @Implementation protected void __constructor__(ParcelFileDescriptor fd){}
  @Implementation protected int getPageCount(){return 3;}
  @Implementation protected PdfRenderer.Page openPage(int index){PdfRenderer.Page page=Shadow.newInstanceOf(PdfRenderer.Page.class);PageDouble state=Shadow.extract(page);state.index=index;return page;}
  @Implementation protected void close(){}
 }
 @Implements(PdfRenderer.Page.class) public static class PageDouble {
  int index;
  @Implementation protected int getWidth(){return 600;}
  @Implementation protected int getHeight(){return 800;}
  @Implementation protected int getIndex(){return index;}
  @Implementation protected void close(){}
  @Implementation protected void render(Bitmap bitmap,Rect clip,Matrix matrix,int mode){Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.BLACK);p.setTextSize(24);canvas.drawText("PDF lifecycle test / page "+(index+1),40,80,p);}
 }

 public static class PdfProvider extends ContentProvider {
  public boolean onCreate(){return true;}
  public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(new File(System.getProperty("seeky.pdf.fixture")),ParcelFileDescriptor.MODE_READ_ONLY);}
  public String getType(Uri uri){return "application/pdf";}
  public Cursor query(Uri uri,String[] p,String s,String[] args,String sort){return null;}
  public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
  public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
  public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
 }
 @Test public void fullScreenPdfMountsAndDrawsOnAndroid13()throws Exception{openPdf(true,false);}
 @Test public void previewPdfMountsAndDrawsOnAndroid13()throws Exception{openPdf(false,false);}
 @Test public void continuousPdfMountsAndDrawsOnAndroid13()throws Exception{openPdf(true,true);}
 void openPdf(boolean full,boolean continuous)throws Exception {
  RuntimeEnvironment.getApplication().getSharedPreferences("seeky",0).edit().putBoolean("reader_continuous",continuous).apply();
  PdfProvider provider=new PdfProvider();ProviderInfo info=new ProviderInfo();info.authority="seeky.test";provider.attachInfo(RuntimeEnvironment.getApplication(),info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
  Intent intent=new Intent(RuntimeEnvironment.getApplication(),ReaderActivity.class).setDataAndType(Uri.parse("content://seeky.test/document.pdf"),"application/pdf").putExtra("name","Documento.pdf").putExtra("full_screen",full);
  ActivityController<ReaderActivity> controller=Robolectric.buildActivity(ReaderActivity.class,intent);
  ReaderActivity activity=controller.get();activity.setTheme(android.R.style.Theme_Material_NoActionBar);
  try {
   controller.create().start().resume().visible();
   for(int n=0;n<3;n++){activity.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
   assertNotNull("reader chrome mounted",activity.root);
   assertNotNull("PDF pages mounted; status="+(activity.status==null?"null":activity.status.getText())+" diagnostics="+AppDiagnostics.report(activity),activity.pageList);
   assertTrue("test renderer page count",activity.pages>0);
   activity.root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));
   activity.root.layout(0,0,480,960);
   for(int n=0;n<3;n++){activity.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
   Bitmap screen=Bitmap.createBitmap(activity.root.getWidth(),activity.root.getHeight(),Bitmap.Config.ARGB_8888);activity.root.draw(new Canvas(screen));
   try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-"+full+"-"+continuous+".png")){screen.compress(Bitmap.CompressFormat.PNG,100,out);}
   assertNotNull("visible page retains its bitmap even after cache eviction",((ReaderPageView)activity.pageList.getChildAt(0)).getDrawable());assertTrue(activity.pageCache.size()<=PerformanceMode.pdfCache(activity));
   if(!continuous){activity.setPage(1);activity.root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));activity.root.layout(0,0,480,960);activity.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();assertEquals(1,activity.currentPage);ReaderPageView page=(ReaderPageView)activity.pageList.getChildAt(0);long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,300,100,0),up=MotionEvent.obtain(now,now+180,MotionEvent.ACTION_UP,80,105,0);try{page.onTouchEvent(down);page.onTouchEvent(up);}finally{down.recycle();up.recycle();}assertEquals("horizontal PDF turn",2,activity.currentPage);}
   assertEquals(full,activity.fullScreen);
  }finally{controller.pause().stop().destroy();}
 } @Test public void continueReadingRestoresPdfPageAndZoomPlacement()throws Exception{Application app=RuntimeEnvironment.getApplication();PdfProvider provider=new PdfProvider();ProviderInfo pi=new ProviderInfo();pi.authority="seeky.test";provider.attachInfo(app,pi);ShadowContentResolver.registerProviderInternal(pi.authority,provider);Intent open=new Intent(app,ReaderActivity.class).setDataAndType(Uri.parse("content://seeky.test/document.pdf"),"application/pdf").putExtra("name","Documento.pdf");ActivityController<ReaderActivity> controller=Robolectric.buildActivity(ReaderActivity.class,open);ReaderActivity a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();pdfLayout(a);a.setPage(1);pdfLayout(a);ReaderPageView page=(ReaderPageView)a.pageList.getChildAt(0);page.zoom(2.4f);page.ty-=80;page.apply();page.savePlacement();float ratio=page.scale/page.base;float centre=(page.getHeight()/2f-page.ty)/(page.imageHeight(page.getDrawable())*page.scale);controller.pause().stop().destroy();Intent resume=LastReading.get(app).intent(app);assertFalse(resume.hasExtra("study_segment"));controller=Robolectric.buildActivity(ReaderActivity.class,resume);a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);try{controller.create().start().resume().visible();pdfLayout(a);assertEquals(1,a.currentPage);page=(ReaderPageView)a.pageList.getChildAt(0);assertEquals(ratio,page.scale/page.base,.02f);float restored=(page.getHeight()/2f-page.ty)/(page.imageHeight(page.getDrawable())*page.scale);assertEquals(centre,restored,.02f);}finally{controller.pause().stop().destroy();}}
 void pdfLayout(ReaderActivity a)throws Exception{for(int n=0;n<4;n++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();a.root.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));a.root.layout(0,0,480,960);shadowOf(Looper.getMainLooper()).idle();}}

}
