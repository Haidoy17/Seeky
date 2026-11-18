package mz.bam.seeky.android;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.ImageView;

class ZoomImageView extends ImageView {
 Runnable tapped;final Matrix matrix=new Matrix();int turns;float scale=1,base=1,tx=0,ty=0,lastX,lastY;final ScaleGestureDetector pinch;final GestureDetector taps;
 ZoomImageView(Context c){super(c);setScaleType(ScaleType.MATRIX);pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float next=Math.max(base,Math.min(base*8,scale*d.getScaleFactor()));float factor=next/scale;tx=d.getFocusX()-(d.getFocusX()-tx)*factor;ty=d.getFocusY()-(d.getFocusY()-ty)*factor;scale=next;apply();return true;}});taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onSingleTapConfirmed(MotionEvent e){if(tapped!=null)tapped.run();return true;}public boolean onDoubleTap(MotionEvent e){if(scale>base*1.1f)fit();else zoom(2.5f);return true;}});setContentDescription("Imagem: use dois dedos para ampliar; arraste para mover; toque duplo para ajustar.");}
 void zoom(float factor){float next=Math.max(base,Math.min(base*8,scale*factor));float f=next/scale;tx=getWidth()/2f-(getWidth()/2f-tx)*f;ty=getHeight()/2f-(getHeight()/2f-ty)*f;scale=next;apply();}
 int imageWidth(Drawable d){return turns%2==0?d.getIntrinsicWidth():d.getIntrinsicHeight();}
 int imageHeight(Drawable d){return turns%2==0?d.getIntrinsicHeight():d.getIntrinsicWidth();}
 void rotate(){turns=(turns+1)%4;fit();}
 void fit(){Drawable d=getDrawable();if(d==null||getWidth()==0)return;base=Math.min((float)getWidth()/Math.max(1,imageWidth(d)),(float)getHeight()/Math.max(1,imageHeight(d)));scale=base;tx=(getWidth()-imageWidth(d)*scale)/2;ty=(getHeight()-imageHeight(d)*scale)/2;apply();}
 void apply(){Drawable d=getDrawable();if(d==null)return;float w=imageWidth(d)*scale,h=imageHeight(d)*scale;tx=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,tx));ty=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,ty));matrix.reset();matrix.postTranslate(-d.getIntrinsicWidth()/2f,-d.getIntrinsicHeight()/2f);matrix.postRotate(turns*90);matrix.postTranslate(imageWidth(d)/2f,imageHeight(d)/2f);matrix.postScale(scale,scale);matrix.postTranslate(tx,ty);setImageMatrix(matrix);}
 protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);fit();}
 public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);taps.onTouchEvent(e);switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:lastX=e.getX();lastY=e.getY();break;case MotionEvent.ACTION_MOVE:if(e.getPointerCount()==1&&!pinch.isInProgress()){tx+=e.getX()-lastX;ty+=e.getY()-lastY;apply();}lastX=e.getX();lastY=e.getY();break;case MotionEvent.ACTION_UP:performClick();break;}return true;}
 public boolean performClick(){super.performClick();return true;}
}
