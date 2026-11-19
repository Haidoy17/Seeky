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
 /** Visual moderno: cada tema desenha o seu cabeçalho, cartão de destaque e fundo. */
 void modern(Canvas c,Rect b,float h){
  String st=colors.style;float d=density;RectF all=new RectF(b);
  boolean stacked=st.equals("aurora")||st.equals("ouro");
  if(kind==BACKGROUND){
   if(st.equals("neon"))ThemeStyle.grid(c,new RectF(b.left,b.bottom-b.height()*.42f,b.right,b.bottom),d,0x0dffffff);
   else if(st.equals("carvao"))ThemeStyle.squares(c,all,d,0x07ffffff);
   else if(stacked)ThemeStyle.paper(c,all,7,d);
   else if(st.equals("ciano")){color(0x08ffffff);c.drawCircle(b.left,b.bottom,b.width()*.7f,paint);}
   return;}
  if(kind==SHEET){if(stacked)ThemeStyle.paper(c,all,11,d);return;}
  if((kind==HEADER||kind==BOOK_HEADER)&&colors.bare)return;
  if(kind==HEADER||kind==BOOK_HEADER){
   float r=30*d,bottom=b.top+h*(kind==BOOK_HEADER?.97f:stacked?.80f:.90f);
   if(stacked){float second=Math.min(b.bottom-2*d,bottom+h*.16f);Path card=rounded(b.left,b.top,b.right,second,r);
    color(0x26000000);c.save();c.translate(0,3*d);c.drawPath(card,paint);c.restore();color(colors.secondary);c.drawPath(card,paint);
    c.save();c.clipPath(card);ThemeStyle.paper(c,new RectF(b.left,b.top,b.right,second),21,d);c.restore();}
   Path block=rounded(b.left,b.top,b.right,bottom,r);
   if(stacked){color(0x33000000);c.save();c.translate(0,4*d);c.drawPath(block,paint);c.restore();}
   color(0xffffffff);/* opaco: a sombra anterior deixava alfa no pincel */paint.setShader(new LinearGradient(b.left,b.top,b.right,bottom,start,end,Shader.TileMode.CLAMP));c.drawPath(block,paint);paint.setShader(null);
   c.save();c.clipPath(block);RectF area=new RectF(b.left,b.top,b.right,bottom);
   switch(st){
    case "aurora":case "ouro":ThemeStyle.paper(c,area,3,d);break;
    case "neon":ThemeStyle.grid(c,area,d,0x12ffffff);break;
    case "vidro":ThemeStyle.bloom(c,b.right-b.width()*.15f,b.top+h*.2f,b.width()*.75f,colors.secondary);break;
    case "carvao":ThemeStyle.squares(c,area,d,0x0fffffff);break;
    case "ciano":color(0x16ffffff);c.drawCircle(b.right-30*d,b.top+h*.1f,h*.62f,paint);color(0x0effffff);c.drawCircle(b.left+b.width()*.2f,bottom,h*.5f,paint);break;
    case "gelo":color(0x10ffffff);c.drawCircle(b.right-20*d,b.top,h*.5f,paint);break;
    default:color(0x0dffffff);c.drawCircle(b.right-40*d,b.top+h*.15f,h*.55f,paint);}
   c.restore();
   if(st.equals("neon")){Path edge=new Path();edge.moveTo(b.left,bottom-r);edge.quadTo(b.left,bottom,b.left+r,bottom);edge.lineTo(b.right-r,bottom);edge.quadTo(b.right,bottom,b.right,bottom-r);ThemeStyle.glow(c,edge,colors.accent,d);}
   else if(st.equals("carvao")){color(colors.accent);c.drawRoundRect(b.left+24*d,bottom-14*d,b.left+72*d,bottom-10*d,2*d,2*d,paint);}
   else if(st.equals("vidro")){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);color(0x22ffffff);paint.setStyle(Paint.Style.STROKE);c.drawPath(block,paint);paint.setStyle(Paint.Style.FILL);}
   return;}
  if(kind==HERO){
   color(0xffffffff);paint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,start,end,Shader.TileMode.CLAMP));c.drawRect(b,paint);paint.setShader(null);
   switch(st){
    case "aurora":case "ouro":ThemeStyle.paper(c,all,5,d);color((colors.secondary&0xffffff)|0x55000000);c.drawCircle(b.right,b.top,b.height()*.9f,paint);break;
    case "neon":ThemeStyle.grid(c,all,d,0x14ffffff);Path frame=new Path();frame.addRoundRect(new RectF(b.left+3*d,b.top+3*d,b.right-3*d,b.bottom-3*d),20*d,20*d,Path.Direction.CW);ThemeStyle.glow(c,frame,colors.accent,d*.6f);break;
    case "vidro":ThemeStyle.bloom(c,b.right-b.width()*.1f,b.top,b.width()*.8f,colors.secondary);break;
    case "carvao":ThemeStyle.squares(c,all,d,0x10ffffff);break;
    default:color(0x12ffffff);c.drawCircle(b.right-b.width()*.08f,b.top+b.height()*.2f,b.height()*.75f,paint);color(0x0bffffff);c.drawCircle(b.right-b.width()*.08f,b.top+b.height()*.2f,b.height()*1.1f,paint);}
  }
 }
 Path rounded(float l,float t,float r,float bottom,float radius){Path p=new Path();p.addRoundRect(new RectF(l,t,r,bottom),new float[]{0,0,0,0,radius,radius,radius,radius},Path.Direction.CW);return p;}
 public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
