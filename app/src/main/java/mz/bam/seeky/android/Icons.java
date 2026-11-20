package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Crisp native vector icons; original Seeky wordmark is left untouched. */
final class Icons extends View {
    String name; int color; final Paint p=new Paint(3);
    /** Traço fino nos temas modernos, um pouco mais firme nos clássicos. */
    final float stroke;
    Icons(Context c,String name,int color){super(c);this.name=name;this.color=color;stroke=AppPalette.of(c).modern?1.55f:1.8f;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setSymbol(String name,int color){if(!this.name.equals(name)||this.color!=color){this.name=name;this.color=color;invalidate();}}
    void line(Canvas c,float... points){for(int i=0;i+3<points.length;i+=2)c.drawLine(points[i],points[i+1],points[i+2],points[i+3],p);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();float s=Math.min(getWidth(),getHeight())/24f;canvas.translate((getWidth()-24*s)/2,(getHeight()-24*s)/2);canvas.scale(s,s);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(name){
            case "chevron":line(canvas,9,5,16,12,9,19);break;
            case "minus":line(canvas,5,12,19,12);break;
            case "phone":canvas.drawRoundRect(6,2,18,22,3,3,p);line(canvas,10,18,14,18);break;
            case "usb":canvas.drawRoundRect(5,9,19,22,3,3,p);line(canvas,8,9,8,2,16,2,16,9);line(canvas,11,4,11,6);line(canvas,14,4,14,6);break;

            case "eye":{android.graphics.Path e=new android.graphics.Path();e.moveTo(2.5f,12);e.quadTo(12,3.5f,21.5f,12);e.quadTo(12,20.5f,2.5f,12);e.close();canvas.drawPath(e,p);}canvas.drawCircle(12,12,3,p);break;
            case "motion":canvas.drawCircle(15,12,5.5f,p);line(canvas,3,8,7,8);line(canvas,2,12,7,12);line(canvas,3,16,7,16);break;
            case "dot":canvas.drawCircle(12,12,7.5f,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,2.6f,p);break;
            case "pin":line(canvas,9,3.5f,15,3.5f);line(canvas,10,3.5f,10,9,7,13,17,13,14,9,14,3.5f);line(canvas,12,13,12,20.5f);break;
            case "tasks":line(canvas,3.5f,6.5f,5.2f,8.2f,8.2f,5.2f);line(canvas,11,7,20.5f,7);line(canvas,3.5f,12.5f,5.2f,14.2f,8.2f,11.2f);line(canvas,11,13,20.5f,13);line(canvas,11,19,20.5f,19);canvas.drawCircle(5.8f,19,1.6f,p);break;
            case "import":line(canvas,12,3,12,14);line(canvas,7.5f,9.5f,12,14,16.5f,9.5f);line(canvas,4,14.5f,4,20,20,20,20,14.5f);break;
            case "collection":line(canvas,6,4.5f,20.5f,4.5f,20.5f,15.5f);canvas.drawRoundRect(3,8,17.5f,20.5f,2,2,p);line(canvas,3,11.5f,8,11.5f,9.5f,9.5f);break;
            case "widget":canvas.drawRoundRect(3,3,11,11,2.2f,2.2f,p);canvas.drawRoundRect(13,3,21,11,2.2f,2.2f,p);canvas.drawRoundRect(3,13,11,21,2.2f,2.2f,p);canvas.drawCircle(17,17,4,p);break;
            case "shield":{android.graphics.Path sh=new android.graphics.Path();sh.moveTo(12,2.8f);sh.lineTo(19.5f,5.6f);sh.lineTo(19.5f,11);sh.cubicTo(19.5f,15.8f,16.3f,19.4f,12,21.2f);sh.cubicTo(7.7f,19.4f,4.5f,15.8f,4.5f,11);sh.lineTo(4.5f,5.6f);sh.close();canvas.drawPath(sh,p);}line(canvas,8.7f,11.8f,11,14.1f,15.4f,9.6f);break;
            case "tune":line(canvas,4,7,20,7);line(canvas,4,17,20,17);p.setStyle(Paint.Style.FILL);canvas.drawCircle(9,7,2.4f,p);canvas.drawCircle(15,17,2.4f,p);break;
            case "dictionary":canvas.drawRoundRect(5,3,19,21,2,2,p);line(canvas,9,3,9,21);line(canvas,11.5f,14,14,8,16.5f,14);line(canvas,12.5f,12,15.5f,12);break;
            case "pdfbook":canvas.drawRoundRect(5,3,19,21,2,2,p);line(canvas,8.5f,8,15.5f,8);line(canvas,8.5f,12,15.5f,12);line(canvas,8.5f,16,12.5f,16);break;
            case "info":canvas.drawCircle(12,12,9,p);line(canvas,12,11,12,17);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,7.6f,1.2f,p);break;
            case "playfill":p.setStyle(Paint.Style.FILL);{android.graphics.Path t=new android.graphics.Path();t.moveTo(8.5f,4.5f);t.lineTo(19.5f,12);t.lineTo(8.5f,19.5f);t.close();canvas.drawPath(t,p);}break;
            case "pausefill":p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(6.5f,4.5f,10.5f,19.5f,1.5f,1.5f,p);canvas.drawRoundRect(13.5f,4.5f,17.5f,19.5f,1.5f,1.5f,p);break;
            case "replay10":canvas.drawArc(3,3,21,21,200,250,false,p);line(canvas,3.6f,4.5f,3.6f,9.2f,8.3f,9.2f);p.setStrokeWidth(1.4f);line(canvas,9.5f,10,9.5f,15.5f);canvas.drawOval(11.6f,10,15,15.5f,p);break;
            case "forward10":canvas.drawArc(3,3,21,21,-90,250,false,p);line(canvas,20.4f,4.5f,20.4f,9.2f,15.7f,9.2f);p.setStrokeWidth(1.4f);line(canvas,9.5f,10,9.5f,15.5f);canvas.drawOval(11.6f,10,15,15.5f,p);break;
            case "repeat":line(canvas,4,11,4,9,6,7,19,7);line(canvas,16,4,19,7,16,10);line(canvas,20,13,20,15,18,17,5,17);line(canvas,8,14,5,17,8,20);break;
            case "speed":canvas.drawArc(3,5,21,23,180,180,false,p);line(canvas,12,14,16.5f,9.5f);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,14,1.4f,p);break;
            case "fit":line(canvas,4,9,4,4,9,4);line(canvas,15,4,20,4,20,9);line(canvas,20,15,20,20,15,20);line(canvas,9,20,4,20,4,15);canvas.drawRoundRect(8,8,16,16,1.5f,1.5f,p);break;
            case "lock":canvas.drawRoundRect(5,10.5f,19,21,3,3,p);canvas.drawArc(8,3,16,15,180,180,false,p);line(canvas,8,9,8,10.5f);line(canvas,16,9,16,10.5f);line(canvas,12,14.5f,12,17);break;
            case "power":canvas.drawArc(4,4,20,21,-50,280,false,p);line(canvas,12,2,12,11);break;
            case "more":p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,5,1.7f,p);canvas.drawCircle(12,12,1.7f,p);canvas.drawCircle(12,19,1.7f,p);break;
            case "speaker":line(canvas,3,9,7,9,12,5,12,19,7,15,3,15,3,9);canvas.drawArc(9,5,22,19,-65,130,false,p);canvas.drawArc(12,8,19,16,-65,130,false,p);break;
            case "bookmark":line(canvas,6,3,18,3,18,21,12,17,6,21,6,3);break;
            case "contents":line(canvas,8,5,21,5);line(canvas,8,12,21,12);line(canvas,8,19,21,19);canvas.drawCircle(3,5,.8f,p);canvas.drawCircle(3,12,.8f,p);canvas.drawCircle(3,19,.8f,p);break;
            case "rotate":canvas.drawRoundRect(5,5,19,19,2,2,p);canvas.drawArc(1,1,23,23,190,100,false,p);line(canvas,16,1,21,3,18,7);break;
            case "note":canvas.drawRoundRect(3,3,21,18,3,3,p);line(canvas,4,18,4,22,9,18);line(canvas,12,6,12,14);line(canvas,8,10,16,10);break;
            case "type":line(canvas,3,20,9,4,15,20);line(canvas,5,14,13,14);canvas.drawArc(16,11,23,21,0,360,false,p);line(canvas,23,11,23,21);break;
            case "stop":canvas.drawRoundRect(5,5,19,19,2,2,p);break;
            case "pdf":case "word":case "excel":case "slides":
                canvas.drawRoundRect(3,2,21,22,3,3,p);line(canvas,15,2,15,8,21,8);p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(name.equals("pdf")?7:11);canvas.drawText(name.equals("pdf")?"PDF":name.equals("word")?"W":name.equals("excel")?"X":"P",12,17,p);break;
            case "android":line(canvas,8,3,6,1);line(canvas,16,3,18,1);canvas.drawArc(5,2,19,14,180,180,false,p);line(canvas,5,8,19,8,19,18,5,18,5,8);line(canvas,8,18,8,22);line(canvas,16,18,16,22);line(canvas,2,9,2,16);line(canvas,22,9,22,16);p.setStyle(Paint.Style.FILL);canvas.drawCircle(9,6,.8f,p);canvas.drawCircle(15,6,.8f,p);break;
            case "course":line(canvas,2,9,12,4,22,9,12,14,2,9);line(canvas,6,11,6,17,12,20,18,17,18,11);line(canvas,22,9,22,17);break;
            case "flash":line(canvas,14,2,5,14,11,14,10,22,20,9,14,9,14,2);break;
            case "play":line(canvas,8,4,20,12,8,20,8,4);break;
            case "pause":line(canvas,8,5,8,19);line(canvas,16,5,16,19);break;
            case "globe":canvas.drawCircle(12,12,9,p);canvas.drawOval(8,3,16,21,p);line(canvas,3,12,21,12);break;
            case "palette":canvas.drawCircle(12,12,9,p);canvas.drawCircle(8,8,1,p);canvas.drawCircle(15,7,1,p);canvas.drawCircle(17,13,1,p);canvas.drawCircle(8,15,1,p);break;
            case "account":canvas.drawCircle(12,8,4,p);canvas.drawArc(3,13,21,27,180,180,false,p);break;
            case "book":line(canvas,12,5,12,21);line(canvas,12,6,8,3,2,3,2,18,8,18,12,21,16,18,22,18,22,3,16,3,12,6);break;
            case "add":line(canvas,12,4,12,20);line(canvas,4,12,20,12);break;
            case "cards":canvas.drawRoundRect(6,5,21,21,3,3,p);line(canvas,16,2,3,2,3,16);line(canvas,10,11,17,11);line(canvas,10,15,15,15);break;
            case "quiz":canvas.drawCircle(12,12,9,p);canvas.drawArc(8,6,16,13,190,280,false,p);line(canvas,12,13,12,15);canvas.drawCircle(12,18,.5f,p);break;
            case "calendar":canvas.drawRoundRect(3,5,21,21,3,3,p);line(canvas,3,10,21,10);line(canvas,8,2,8,7);line(canvas,16,2,16,7);line(canvas,7,14,10,14);line(canvas,14,14,17,14);break;
            case "check":line(canvas,4,12,10,18,21,5);break;
            case "search":canvas.drawCircle(10.5f,10.5f,6.5f,p);line(canvas,15.5f,15.5f,21,21);break;
            case "home":line(canvas,3,10,12,3,21,10,21,21,15,21,15,14,9,14,9,21,3,21,3,10);break;
            case "folder":line(canvas,3,7,3,5,9,5,11,8,21,8,21,20,3,20,3,7);break;
            case "star":Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?9:4;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();canvas.drawPath(star,p);break;
            case "tools":case "grid":for(int x=4;x<17;x+=10)for(int y=4;y<17;y+=10)canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,p);break;
            case "settings":canvas.drawCircle(12,12,3,p);Path gear=new Path();for(int j=0;j<32;j++){double a=j*Math.PI/16;float r=(j%4==0||j%4==3)?10:8;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(j==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();canvas.drawPath(gear,p);break;
            case "image":canvas.drawRoundRect(3,4,21,20,3,3,p);canvas.drawCircle(8,9,1.6f,p);line(canvas,4,18,10,12,14,16,17,13,21,17);break;
            case "video":canvas.drawRoundRect(3,5,21,19,3,3,p);line(canvas,10,9,16,12,10,15,10,9);break;
            case "audio":line(canvas,9,17,9,5,19,3,19,15);canvas.drawOval(3,16,9,21,p);canvas.drawOval(13,14,19,19,p);break;
            case "shuffle":line(canvas,3,17,8,17,15,7,20,7);line(canvas,17,4,20,7,17,10);line(canvas,3,7,8,7,15,17,20,17);line(canvas,17,14,20,17,17,20);break;
            case "skipnext":p.setStyle(Paint.Style.FILL);{android.graphics.Path t=new android.graphics.Path();t.moveTo(5,5);t.lineTo(15.5f,12);t.lineTo(5,19);t.close();canvas.drawPath(t,p);}canvas.drawRoundRect(16.5f,5,19.5f,19,1.2f,1.2f,p);break;
            case "skipprev":p.setStyle(Paint.Style.FILL);{android.graphics.Path t=new android.graphics.Path();t.moveTo(19,5);t.lineTo(8.5f,12);t.lineTo(19,19);t.close();canvas.drawPath(t,p);}canvas.drawRoundRect(4.5f,5,7.5f,19,1.2f,1.2f,p);break;
            case "heart":{android.graphics.Path h=new android.graphics.Path();h.moveTo(12,20);h.cubicTo(4,14.5f,2.5f,10.5f,3.5f,7.5f);h.cubicTo(4.8f,3.8f,9.8f,3.2f,12,7);h.cubicTo(14.2f,3.2f,19.2f,3.8f,20.5f,7.5f);h.cubicTo(21.5f,10.5f,20,14.5f,12,20);h.close();canvas.drawPath(h,p);}break;
            case "heartfill":p.setStyle(Paint.Style.FILL);{android.graphics.Path h=new android.graphics.Path();h.moveTo(12,20);h.cubicTo(4,14.5f,2.5f,10.5f,3.5f,7.5f);h.cubicTo(4.8f,3.8f,9.8f,3.2f,12,7);h.cubicTo(14.2f,3.2f,19.2f,3.8f,20.5f,7.5f);h.cubicTo(21.5f,10.5f,20,14.5f,12,20);h.close();canvas.drawPath(h,p);}break;
            case "lyrics":line(canvas,4,6,15,6);line(canvas,4,10,15,10);line(canvas,4,14,11,14);line(canvas,4,18,9,18);canvas.drawCircle(16,18,2.2f,p);line(canvas,18.2f,18,18.2f,9,21,10);break;
            case "queue":line(canvas,4,6,16,6);line(canvas,4,11,16,11);line(canvas,4,16,10,16);p.setStyle(Paint.Style.FILL);{android.graphics.Path t=new android.graphics.Path();t.moveTo(14,14);t.lineTo(20.5f,17.5f);t.lineTo(14,21);t.close();canvas.drawPath(t,p);}break;
            case "timer":canvas.drawCircle(12,13.5f,7.5f,p);line(canvas,12,13.5f,12,9.5f);line(canvas,9.5f,3,14.5f,3);line(canvas,18.5f,6.5f,19.8f,5.2f);break;
            case "screenrotate":canvas.drawRoundRect(7,3,17,21,2.5f,2.5f,p);line(canvas,10.5f,18,13.5f,18);canvas.drawArc(1.5f,6,8.5f,14,110,140,false,p);line(canvas,2.2f,8.8f,2.6f,11.2f,5,10.6f);break;
            case "aspect":canvas.drawRoundRect(3,5,21,19,2.5f,2.5f,p);line(canvas,6.5f,10,6.5f,8.5f,9,8.5f);line(canvas,17.5f,14,17.5f,15.5f,15,15.5f);break;
            case "paste":canvas.drawRoundRect(5,5,19,21,2.5f,2.5f,p);canvas.drawRoundRect(9,3,15,7,1.5f,1.5f,p);line(canvas,8.5f,11,15.5f,11);line(canvas,8.5f,15,13.5f,15);break;
            case "wifi":canvas.drawArc(2,5,22,25,225,90,false,p);canvas.drawArc(6,9,18,21,225,90,false,p);canvas.drawArc(10,13,14,17,225,90,false,p);canvas.drawCircle(12,19,0.6f,p);break;
            case "chart":line(canvas,4,4,4,20,20,20);line(canvas,8,16,8,12);line(canvas,12,16,12,8);line(canvas,16,16,16,10);break;
            case "camera":canvas.drawRoundRect(3,7,21,19,3,3,p);line(canvas,8,7,9.5f,4.5f,14.5f,4.5f,16,7);canvas.drawCircle(12,13,3.5f,p);break;
            case "translate":line(canvas,3,6,13,6);line(canvas,8,4,8,6);line(canvas,5,6,7.5f,12,11,14);line(canvas,11,6,9,11,5,14);line(canvas,13,20,16.5f,11,20,20);line(canvas,14.2f,17,18.8f,17);break;
            case "clock":canvas.drawCircle(12,12,9,p);line(canvas,12,6,12,12,16,14);break;
            case "float":canvas.drawRoundRect(3,3,21,21,4,4,p);canvas.drawRoundRect(11,11,22,21,3,3,p);line(canvas,7,8,15,8);break;
            case "copy":canvas.drawRoundRect(8,8,21,21,3,3,p);line(canvas,16,4,16,3,3,3,3,16,4,16);break;
            case "filters":line(canvas,4,6,20,6);line(canvas,4,12,20,12);line(canvas,4,18,20,18);canvas.drawCircle(9,6,2,p);canvas.drawCircle(16,12,2,p);canvas.drawCircle(8,18,2,p);break;
            case "arrow":line(canvas,4,12,20,12,14,6);line(canvas,20,12,14,18);break;
            case "back":line(canvas,20,12,4,12,10,6);line(canvas,4,12,10,18);break;
            case "trash":line(canvas,4,6,20,6);line(canvas,9,3,15,3,16,6);line(canvas,6,6,7,21,17,21,18,6);line(canvas,10,10,10,17);line(canvas,14,10,14,17);break;
            case "edit":line(canvas,4,16,16,4,20,8,8,20,4,20,4,16);line(canvas,13,7,17,11);break;
            case "collapse":line(canvas,3,9,9,9,9,3);line(canvas,15,3,15,9,21,9);line(canvas,21,15,15,15,15,21);line(canvas,9,21,9,15,3,15);break;
            case "expand":line(canvas,9,3,3,3,3,9);line(canvas,15,3,21,3,21,9);line(canvas,21,15,21,21,15,21);line(canvas,9,21,3,21,3,15);break;
            case "close":line(canvas,6,6,18,18);line(canvas,18,6,6,18);break;
            case "refresh":canvas.drawArc(4,4,20,20,35,290,false,p);line(canvas,20,4,20,10,14,10);break;
            case "scan":line(canvas,3,8,3,3,8,3);line(canvas,16,3,21,3,21,8);line(canvas,21,16,21,21,16,21);line(canvas,8,21,3,21,3,16);line(canvas,7,8,17,8);line(canvas,12,8,12,17);break;
            case "restore":canvas.drawArc(4,4,20,20,150,300,false,p);line(canvas,3,4,3,10,9,10);line(canvas,12,7,12,12,16,14);break;
            case "mic":canvas.drawRoundRect(9,3,15,15,3,3,p);canvas.drawArc(6,7,18,19,0,180,false,p);line(canvas,12,19,12,22);line(canvas,8,22,16,22);break;
            case "share":line(canvas,7,11,16,6);line(canvas,7,13,16,18);canvas.drawCircle(5,12,2.5f,p);canvas.drawCircle(18,5,2.5f,p);canvas.drawCircle(18,19,2.5f,p);break;
            case "storage":canvas.drawRoundRect(3,4,21,20,4,4,p);line(canvas,3,14,21,14);canvas.drawCircle(17,17,0.5f,p);break;
            default:line(canvas,5,3,14,3,20,9,20,21,5,21,5,3);line(canvas,14,3,14,9,20,9);line(canvas,9,13,16,13);line(canvas,9,17,14,17);break;
        }
        canvas.restore();
    }
    static String category(String c){return c.equals("PDF")?"pdf":c.equals("Aplicações")?"android":c.equals("Imagens")?"image":c.equals("Vídeos")?"video":c.equals("Áudio")?"audio":c.equals("Pastas")?"folder":"document";}
}
