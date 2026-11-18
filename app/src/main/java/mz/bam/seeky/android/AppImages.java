package mz.bam.seeky.android;
import android.content.Context;import android.graphics.*;import android.widget.ImageView;
/** Cache only the visible wordmark, without inflating its transparent 2362px canvas. */
final class AppImages {
 private static Bitmap logo;
 static Bitmap visible(Bitmap source){if(source==null)return null;int width=source.getWidth(),height=source.getHeight(),left=width,top=height,right=-1,bottom=-1;int[] row=new int[width];for(int y=0;y<height;y++){source.getPixels(row,0,width,0,y,width,1);for(int x=0;x<width;x++)if((row[x]>>>24)>8){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}}if(right<left)return source;left=Math.max(0,left-2);top=Math.max(0,top-2);right=Math.min(width-1,right+2);bottom=Math.min(height-1,bottom+2);return Bitmap.createBitmap(source,left,top,right-left+1,bottom-top+1);}
 static synchronized void wordmark(ImageView view){if(logo==null){BitmapFactory.Options o=new BitmapFactory.Options();o.inScaled=false;o.inJustDecodeBounds=true;BitmapFactory.decodeResource(view.getResources(),R.drawable.wordmark,o);o.inSampleSize=ImageBudget.sample(o.outWidth,o.outHeight,640);o.inJustDecodeBounds=false;logo=visible(BitmapFactory.decodeResource(view.getResources(),R.drawable.wordmark,o));}view.setImageBitmap(logo);AppPalette p=AppPalette.of(view.getContext());if(p.light)view.clearColorFilter();else view.setColorFilter(p.ink,PorterDuff.Mode.SRC_IN);}
 private AppImages(){}
}
