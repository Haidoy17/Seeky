package mz.bam.seeky.android;
import android.content.Context;import android.graphics.*;import android.view.View;

/** Small scalable illustration, rendered as a few native paths. */
final class FolderArtwork extends View {
 final Paint p=new Paint(3);final Path path=new Path();final int accent;final boolean search;
 FolderArtwork(Context c,boolean search){super(c);accent=AppPalette.of(c).accent;this.search=search;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 protected void onDraw(Canvas c){super.onDraw(c);int save=c.save();float size=Math.min(getWidth()/180f,getHeight()/160f);c.translate((getWidth()-180*size)/2,(getHeight()-160*size)/2);c.scale(size,size);
  p.setShader(null);p.setColor(0x10000000);c.drawOval(18,137,170,151,p);
  p.setColor(Color.WHITE);p.setShader(new LinearGradient(20,20,140,130,ThemeColors.blend(accent,Color.WHITE,.45f),ThemeColors.blend(accent,Color.BLACK,.20f),Shader.TileMode.CLAMP));path.reset();path.moveTo(20,43);path.quadTo(20,31,33,29);path.lineTo(70,22);path.quadTo(83,20,88,32);path.lineTo(143,25);path.quadTo(154,24,154,39);path.lineTo(154,121);path.lineTo(31,143);path.quadTo(20,142,20,129);path.close();c.drawPath(path,p);
  for(int i=0;i<3;i++){int x=38+i*8,y=44+i*10;p.setShader(new LinearGradient(x,y,x+94,y+72,0xffffffff,0xffe6e4f3,Shader.TileMode.CLAMP));c.drawRoundRect(x,y,x+105,y+83,8,8,p);}
  p.setShader(new LinearGradient(27,77,152,146,ThemeColors.blend(accent,Color.WHITE,.20f),ThemeColors.blend(accent,0xff200644,.34f),Shader.TileMode.CLAMP));path.reset();path.moveTo(31,82);path.lineTo(77,82);path.lineTo(90,68);path.quadTo(94,64,104,64);path.lineTo(164,64);path.quadTo(174,64,170,78);path.lineTo(152,133);path.quadTo(151,140,140,142);path.lineTo(40,151);path.quadTo(31,150,31,140);path.close();c.drawPath(path,p);
  if(search){p.setShader(null);p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);c.drawCircle(117,101,18,p);c.drawLine(130,115,147,132,p);p.setStyle(Paint.Style.FILL);}c.restoreToCount(save);
 }
}
