package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.widget.LinearLayout;

/** Procedural timber and ledge, without bitmap textures or extra image memory. */
final class BookShelfRow extends LinearLayout {
    final Paint paint=new Paint();final boolean light;final float density;
    BookShelfRow(Context context){super(context);light=AppPalette.of(context).light;density=getResources().getDisplayMetrics().density;setWillNotDraw(false);setOrientation(HORIZONTAL);setGravity(android.view.Gravity.BOTTOM);setPadding((int)(8*density),(int)(14*density),(int)(8*density),(int)(14*density));}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);int top=light?0xffc9b28f:0xff443c33,bottom=light?0xff947655:0xff29241e;paint.setShader(new LinearGradient(0,0,getWidth(),getHeight(),top,bottom,Shader.TileMode.CLAMP));canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);paint.setColor(light?0x1070583c:0x12c2a47a);paint.setStrokeWidth(Math.max(1,density*.5f));for(int y=0;y<getHeight();y+=(int)(19*density))canvas.drawLine(0,y,getWidth(),y+getWidth()*.012f,paint);float ledge=12*density;paint.setShader(new LinearGradient(0,getHeight()-ledge,0,getHeight(),light?0xffe8d4b3:0xff7b6851,light?0xff9e805d:0xff342a20,Shader.TileMode.CLAMP));canvas.drawRect(0,getHeight()-ledge,getWidth(),getHeight(),paint);paint.setShader(null);paint.setColor(0x35000000);canvas.drawRect(0,getHeight()-ledge-3*density,getWidth(),getHeight()-ledge,paint);}
}
