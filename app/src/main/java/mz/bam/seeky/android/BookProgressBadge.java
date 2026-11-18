package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** High contrast reading percentage over the cover, independent of cover colours. */
final class BookProgressBadge extends View {
    final int percent,accent;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    BookProgressBadge(Context context,int progress){super(context);percent=Math.max(0,Math.min(100,progress));accent=AppPalette.of(context).accent;setContentDescription(percent+"% "+AppLanguage.text(context,"lido"));}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,r=size*.46f;paint.setStyle(Paint.Style.FILL);paint.setColor(0xcc17212b);canvas.drawCircle(cx,cy,r,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*.04f);paint.setColor(0x80ffffff);canvas.drawCircle(cx,cy,r,paint);paint.setColor(percent==100?0xff51d4a3:accent);paint.setStrokeWidth(size*.065f);canvas.drawArc(cx-r,cy-r,cx+r,cy+r,-90,percent*3.6f,false,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(size*(percent==100?.28f:.31f));Paint.FontMetrics fm=paint.getFontMetrics();canvas.drawText(percent+"%",cx,cy-(fm.ascent+fm.descent)/2,paint);}
}
