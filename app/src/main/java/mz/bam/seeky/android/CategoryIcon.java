package mz.bam.seeky.android;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Small native category illustrations; no bitmaps or extra downloads. */
final class CategoryIcon extends View {
 final String category;final Paint p=new Paint(3);final Path path=new Path();
 CategoryIcon(Context c,String category){super(c);this.category=category;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void fill(int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);}void line(int color,float width){fill(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
 void rect(float l,float t,float r,float b,float radius){canvas.drawRoundRect(l,t,r,b,radius,radius,p);}Canvas canvas;
 protected void onDraw(Canvas c){super.onDraw(c);canvas=c;c.save();float s=Math.min(getWidth(),getHeight());c.translate((getWidth()-s)/2,(getHeight()-s)/2);c.scale(s/48,s/48);
  if(category.equals("Arquivos")){for(int i=2;i>=0;i--){fill(new int[]{0xff5484f5,0xff7199ff,0xff9bb8ff}[i]);path.reset();path.moveTo(5,13+i*9);path.lineTo(24,3+i*9);path.lineTo(43,13+i*9);path.lineTo(24,24+i*9);path.close();c.drawPath(path,p);}}
  else if(category.equals("Aplicações")){fill(0xff61cf59);path.reset();path.moveTo(24,3);path.lineTo(43,13);path.lineTo(24,24);path.lineTo(5,13);path.close();c.drawPath(path,p);fill(0xff38b86d);path.reset();path.moveTo(4,17);path.lineTo(21,27);path.lineTo(21,45);path.lineTo(4,35);path.close();c.drawPath(path,p);fill(0xff72df4e);path.reset();path.moveTo(26,27);path.lineTo(44,17);path.lineTo(44,35);path.lineTo(26,45);path.close();c.drawPath(path,p);}
  else if(category.equals("Outros")){fill(0xffffb331);c.drawCircle(30,25,14,p);fill(0xfffb981d);c.drawCircle(17,25,15,p);line(0xffffffff,2);c.drawArc(13,10,43,40,115,130,false,p);}
  else{int first=category.equals("Imagens")?0xffff6871:category.equals("Documentos")?0xffffd760:category.equals("Áudio")?0xffa15cff:category.equals("Vídeos")?0xffff4e90:category.equals("PDF")?0xfff66a6c:0xffb15aff;int last=category.equals("Imagens")?0xffef3653:category.equals("Documentos")?0xfff1b52c:category.equals("Áudio")?0xff6536ec:category.equals("Vídeos")?0xffec1767:category.equals("PDF")?0xffd92345:0xff7622d9;
   fill(first);p.setShader(new LinearGradient(5,4,41,44,first,last,Shader.TileMode.CLAMP));if(category.equals("Áudio"))c.drawCircle(24,24,21,p);else rect(4,4,44,44,9);p.setShader(null);
   if(category.equals("Imagens")){fill(Color.WHITE);c.drawCircle(15,15,3.5f,p);line(Color.WHITE,2.8f);path.reset();path.moveTo(9,37);path.cubicTo(18,20,27,20,40,25);c.drawPath(path,p);}
   else if(category.equals("Áudio")){fill(Color.WHITE);c.drawOval(13,25,27,36,p);line(Color.WHITE,3.2f);c.drawLine(26,29,31,9,p);}
   else if(category.equals("Vídeos")){fill(Color.WHITE);path.reset();path.moveTo(20,15);path.lineTo(33,24);path.lineTo(20,33);path.close();c.drawPath(path,p);}
   else if(category.equals("Documentos")){line(Color.WHITE,2.7f);c.drawCircle(25,16,4,p);c.drawLine(25,20,25,33,p);c.drawLine(25,27,29,27,p);}
   else if(category.equals("PDF")){line(Color.WHITE,2.2f);rect(14,10,34,38,2);fill(Color.WHITE);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(8);p.setTextAlign(Paint.Align.CENTER);c.drawText("PDF",24,28,p);}
   else{fill(Color.WHITE);for(int y=0;y<2;y++)for(int x=0;x<2;x++)rect(13+x*13,13+y*13,22+x*13,22+y*13,2);}
  }c.restore();canvas=null;
 }
}
