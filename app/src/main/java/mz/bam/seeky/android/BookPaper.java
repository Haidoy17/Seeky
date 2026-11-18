package mz.bam.seeky.android;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Subtle paper and spine. Does not change document page rendering. */
final class BookPaper extends Drawable {
 final boolean light;final float density;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 BookPaper(boolean light,float density){this.light=light;this.density=density;}
 public void draw(Canvas c){Rect b=getBounds();paint.setShader(null);paint.setColor(light?0xfffffdf9:0xff211f26);c.drawRoundRect(b.left,b.top,b.right,b.bottom,14*density,14*density,paint);paint.setShader(new LinearGradient(b.left,b.top,b.left+16*density,b.top,new int[]{light?0x22765136:0x66000000,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));c.drawRoundRect(b.left,b.top,b.right,b.bottom,14*density,14*density,paint);paint.setShader(null);paint.setColor(light?0x18604c37:0x22ffffff);c.drawLine(b.left+4*density,b.top+12*density,b.left+4*density,b.bottom-12*density,paint);}
 public void getOutline(Outline outline){Rect b=getBounds();outline.setRoundRect(b,14*density);}
 public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
