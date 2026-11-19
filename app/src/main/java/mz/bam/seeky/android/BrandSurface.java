package mz.bam.seeky.android;
import android.content.Context;import android.graphics.*;import android.graphics.drawable.Drawable;

/** Smooth native Bezier waves. Header curves are drawn once, outside the scrolling content. */
final class BrandSurface extends Drawable {
 static final int BACKGROUND=0,HEADER=1,SHEET=2,HERO=3,BOOK_HEADER=4;
 final AppPalette colors;final int kind;final float density;final Paint paint=new Paint(3);final Path path=new Path(),clip=new Path();
 LinearGradient gradient,footerGradient;final int start,end,center;
 BrandSurface(Context c,int kind){colors=AppPalette.of(c);this.kind=kind;density=c.getResources().getDisplayMetrics().density;boolean custom=c.getSharedPreferences("seeky",0).getString("theme",AppPalette.DEFAULT).equals("Personalizado");if(colors.modern){start=colors.heroStart;center=ThemeColors.blend(colors.heroStart,colors.heroEnd,.5f);end=colors.heroEnd;}else{center=custom?colors.heroStart:colors.accent;start=ThemeColors.blend(center,0xff15013f,.68f);end=custom?colors.heroEnd:ThemeColors.blend(colors.accent,0xffcf2beb,.24f);}}
 protected void onBoundsChange(Rect b){gradient=new LinearGradient(b.left,b.top,b.right,b.top,new int[]{start,center,end},new float[]{0,.65f,1},Shader.TileMode.CLAMP);footerGradient=new LinearGradient(b.left,b.bottom,b.right,b.top+b.height()*.72f,ThemeColors.blend(colors.bg,colors.accent,.12f),ThemeColors.blend(colors.bg,colors.accent,colors.light?.70f:.38f),Shader.TileMode.CLAMP);}
 void color(int value){paint.setShader(null);paint.setColor(value);}
 void top(Canvas c,float baseline,float amplitude,int fill,boolean shaded){Rect b=getBounds();float w=b.width(),x=b.left,y=b.top;path.reset();path.moveTo(x,y);path.lineTo(b.right,y);path.lineTo(b.right,y+baseline+amplitude*.1f);path.cubicTo(x+w*.83f,y+baseline-amplitude*.70f,x+w*.69f,y+baseline-amplitude*1.2f,x+w*.53f,y+baseline-amplitude*.18f);path.cubicTo(x+w*.35f,y+baseline+amplitude*1.1f,x+w*.16f,y+baseline+amplitude*.9f,x,y+baseline-amplitude*.15f);path.close();color(fill);if(shaded)paint.setShader(gradient);c.drawPath(path,paint);}
 void bottom(Canvas c,float lift,int fill,boolean shaded){Rect b=getBounds();float w=b.width(),x=b.left,y=b.bottom,h=b.height();path.reset();path.moveTo(x,y-lift*.45f);path.cubicTo(x+w*.25f,y-lift*.9f,x+w*.43f,y+lift*.14f,x+w*.62f,y-lift*.45f);path.cubicTo(x+w*.78f,y-lift*1.1f,x+w*.87f,y-lift*1.22f,b.right,y-lift*1.14f);path.lineTo(b.right,y);path.lineTo(x,y);path.close();color(fill);if(shaded)paint.setShader(footerGradient);c.drawPath(path,paint);}
 public void draw(Canvas c){Rect b=getBounds();float h=b.height();int save=c.save();
  if(kind==SHEET||kind==HERO){clip.reset();clip.addRoundRect(new RectF(b),22*density,22*density,Path.Direction.CW);c.clipPath(clip);}
  color(kind==SHEET?colors.surface:colors.bg);c.drawRect(b,paint);
  if(colors.modern){modern(c,b,h);c.restoreToCount(save);return;}
  if(kind==BACKGROUND){color(0xffffffff);paint.setShader(gradient);c.drawRect(b.left,b.top,b.right,b.top+Math.min(32*density,h*.05f),paint);bottom(c,h*.26f,ThemeColors.blend(colors.bg,colors.accent,.10f),false);bottom(c,h*.19f,colors.bg,true);color(0xffffffff);paint.setShader(new LinearGradient(0,b.bottom-38*density,0,b.bottom,new int[]{colors.bg&0xffffff,colors.bg},null,Shader.TileMode.CLAMP));c.drawRect(b.left,b.bottom-38*density,b.right,b.bottom,paint);}
  else if(kind==HEADER||kind==BOOK_HEADER){boolean books=kind==BOOK_HEADER;top(c,h*(books?.995f:.94f),h*(books?.018f:.08f),ThemeColors.blend(colors.bg,colors.accent,.12f),false);top(c,h*(books?.965f:.875f),h*(books?.05f:.12f),ThemeColors.blend(colors.bg,colors.accent,.28f),false);top(c,h*(books?.90f:.79f),h*(books?.10f:.18f),0xffffffff,true);}
  else if(kind==HERO){color(0xffffffff);paint.setShader(gradient);c.drawRect(b,paint);bottom(c,h*.18f,ThemeColors.blend(colors.accent,start,.23f),false);}
  else{top(c,Math.min(90*density,h*.20f),Math.min(35*density,h*.08f),ThemeColors.blend(colors.surface,colors.accent,.055f),false);bottom(c,32*density,ThemeColors.blend(colors.surface,colors.accent,.11f),false);}
  c.restoreToCount(save);
 }
 /** Visual moderno: cabeçalho em bloco sólido com cantos arredondados, sem ondas; fundo liso. */
 void modern(Canvas c,Rect b,float h){
  if(kind==HEADER||kind==BOOK_HEADER){float r=28*density,bottom=b.top+h*(kind==BOOK_HEADER?.97f:.90f);
   path.reset();path.addRoundRect(new RectF(b.left,b.top,b.right,bottom),new float[]{0,0,0,0,r,r,r,r},Path.Direction.CW);
   paint.setShader(new LinearGradient(b.left,b.top,b.right,bottom,start,end,Shader.TileMode.CLAMP));c.drawPath(path,paint);
   color(0x0dffffff);c.drawCircle(b.right-40*density,b.top+h*.15f,h*.55f,paint);}
  else if(kind==HERO){paint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,start,end,Shader.TileMode.CLAMP));c.drawRect(b,paint);
   color(0x12ffffff);c.drawCircle(b.right-b.width()*.08f,b.top+b.height()*.2f,b.height()*.75f,paint);color(0x0bffffff);c.drawCircle(b.right-b.width()*.08f,b.top+b.height()*.2f,b.height()*1.1f,paint);}
 }
 public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
