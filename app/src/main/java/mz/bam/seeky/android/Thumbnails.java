package mz.bam.seeky.android;
import android.content.Context;import android.graphics.Bitmap;import android.os.*;import android.util.LruCache;import android.view.View;import android.widget.ImageView;import java.lang.ref.WeakReference;import java.util.concurrent.*;
/** One bounded decoder; view identity is checked before publishing a preview. */
final class Thumbnails implements AutoCloseable {
 final Context app;final Handler main=new Handler(Looper.getMainLooper());final LruCache<String,Bitmap> cache;
 final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),new ThreadPoolExecutor.DiscardOldestPolicy());volatile boolean closed;private static Thumbnails icons;
 Thumbnails(Context c){this(c,PerformanceMode.thumbnailCache(c));}
 private Thumbnails(Context c,int bytes){app=c.getApplicationContext();cache=new LruCache<String,Bitmap>(bytes){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};worker.allowCoreThreadTimeOut(true);}
 static synchronized void apk(ImageView view,Catalog.Entry e){if(!MediaFormats.ext(e.name).equals("apk")){view.setVisibility(View.GONE);return;}if(icons==null)icons=new Thumbnails(view.getContext(),512*1024);icons.bind(view,e);}
 void bind(FileItemView view,Catalog.Entry e){boolean apk=MediaFormats.ext(e.name).equals("apk");if(e.directory||!apk&&(!FileVisual.showPreview(view.mode,view.size)||!FileVisual.preview(e.name,e.mime)))return;bind(view.thumbnail,e);}
 void bind(ImageView view,Catalog.Entry e){if(closed||e.directory)return;String key=e.uri+"|"+e.modified+"|"+e.size+"|cover2";view.setTag(key);view.setImageDrawable(null);view.setVisibility(View.GONE);Bitmap cached=cache.get(key);if(cached!=null){view.setImageBitmap(cached);view.setVisibility(View.VISIBLE);return;}WeakReference<ImageView> target=new WeakReference<>(view);try{worker.execute(()->{if(closed)return;try{Bitmap b=cache.get(key);if(b==null)b=ThumbnailSource.load(app,e);if(b==null||closed)return;cache.put(key,b);final Bitmap ready=b;main.post(()->{ImageView image=target.get();if(!closed&&image!=null&&key.equals(image.getTag())){image.setImageBitmap(ready);image.setVisibility(View.VISIBLE);}});}catch(Exception|OutOfMemoryError ignored){}});}catch(RejectedExecutionException ignored){}}
 void trim(){cache.evictAll();synchronized(Thumbnails.class){if(icons!=null&&icons!=this)icons.cache.evictAll();}}
 public void close(){closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}
