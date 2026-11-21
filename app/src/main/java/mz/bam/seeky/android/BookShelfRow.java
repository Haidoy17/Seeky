package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * One compartment of the bookcase: back boards with grain and seams, the shadow of the shelf above,
 * side posts, and the plank (top face and front edge) the books stand on, with a soft shadow behind
 * and under each book. Everything is drawn with paths and gradients, without bitmaps.
 */
final class BookShelfRow extends LinearLayout {
    static final String[] WOODS={"Nogueira","Carvalho","Cerejeira","Ébano","Laca branca"};
    /** back light, back dark, grain, plank top, plank front light, plank front dark, post. */
    static final int[][] TONES={
        {0xff674330,0xff4a2d1d,0x30140a04,0xffc09168,0xff95633f,0xff5b3820,0xff3e2617},
        {0xffcfa877,0xffb48a59,0x2a5a3814,0xfff0d9b0,0xffd7b27e,0xffa67c4a,0xff9a7244},
        {0xff803d27,0xff5f2a1a,0x2e1c0703,0xffcd8a64,0xffa95e3f,0xff6d301d,0xff4c2014},
        {0xff342b26,0xff221c18,0x22000000,0xff64544a,0xff4b3e35,0xff261e1a,0xff171210},
        {0xfff1ede7,0xffdfd9d0,0x103d3022,0xfffefdfb,0xfff3efe9,0xffd5cec3,0xffcec5b9},
    };
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final float d;final int[] wood;final int index;
    final boolean plank;boolean first;
    final Path grain=new Path(),streak=new Path();int grainW=-1,grainH=-1,grainTop=Integer.MIN_VALUE;

    static int wood(Context c){return Math.max(0,Math.min(WOODS.length-1,c.getSharedPreferences("books",0).getInt("shelf_wood",0)));}
    BookShelfRow(Context context){this(context,wood(context),0,true);}
    /** {@code index} varies the knots; {@code plank} false draws only the back boards (for the page buttons). */
    BookShelfRow(Context context,int woodIndex,int index,boolean plank){
        super(context);this.index=index;this.plank=plank;wood=TONES[Math.max(0,Math.min(TONES.length-1,woodIndex))];d=getResources().getDisplayMetrics().density;
        setWillNotDraw(false);setOrientation(HORIZONTAL);setGravity(android.view.Gravity.BOTTOM);setClipChildren(false);setClipToPadding(false);
        setPadding(side(),(int)(22*d),side(),plank?(int)(PLANK_FRONT*d+PLANK_TOP*d*.55f):(int)(10*d));
    }
    static final float POST=9,PLANK_FRONT=13,PLANK_TOP=8;
    int side(){return (int)((POST+12)*d);}
    void first(){first=true;setPadding(getPaddingLeft(),(int)(34*d),getPaddingRight(),getPaddingBottom());}

    @Override protected void dispatchDraw(Canvas c){
        int w=getWidth(),h=getHeight();if(w<=0||h<=0){super.dispatchDraw(c);return;}
        float post=POST*d,front=plank?PLANK_FRONT*d:0,top=plank?PLANK_TOP*d:0,backBottom=h-front-top;
        // Back boards: darker towards the posts, then seams and grain.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0,0,w,0,new int[]{wood[1],wood[0],wood[0],wood[1]},new float[]{0,.2f,.8f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);paint.setShader(null);
        float[] seams={.335f,.665f};
        paint.setColor(0x0d000000);c.drawRect(0,0,w*seams[0],backBottom,paint);paint.setColor(0x0affffff);c.drawRect(w*seams[0],0,w*seams[1],backBottom,paint);
        if(grainW!=w||grainH!=h||grainTop!=getTop())buildGrain(w,h);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,.7f*d));paint.setColor(wood[2]);c.drawPath(grain,paint);
        paint.setStrokeWidth(Math.max(1,1.4f*d));paint.setColor(0x0fffffff);c.drawPath(streak,paint);
        for(float s:seams){float x=w*s;paint.setStrokeWidth(Math.max(1,d));paint.setColor(0x33000000);c.drawLine(x,0,x,backBottom,paint);paint.setColor(0x14ffffff);c.drawLine(x+d,0,x+d,backBottom,paint);}
        if(index%3==1){float kx=w*(.18f+.64f*((index*37)%100)/100f),ky=h*.42f;paint.setColor(wood[2]);paint.setStrokeWidth(Math.max(1,.9f*d));for(int r=1;r<=3;r++)c.drawOval(kx-5*d*r,ky-2*d*r,kx+5*d*r,ky+2*d*r,paint);}
        paint.setStyle(Paint.Style.FILL);
        // The shelf above (or the top of the case) casts a shadow on the boards.
        float shade=first?30*d:22*d;
        if(first){paint.setShader(new LinearGradient(0,0,0,10*d,wood[3],wood[6],Shader.TileMode.CLAMP));c.drawRect(0,0,w,10*d,paint);paint.setShader(null);}
        float from=first?10*d:0;
        paint.setShader(new LinearGradient(0,from,0,from+shade,0x99000000,0x00000000,Shader.TileMode.CLAMP));c.drawRect(0,from,w,from+shade,paint);paint.setShader(null);
        // Soft shadows of the books on the boards and on the shelf.
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()!=VISIBLE||!(child instanceof Body))continue;
            float l=child.getLeft(),t=child.getTop(),r=child.getRight(),b=child.getBottom();
            for(int k=3;k>=1;k--){paint.setColor(0x1a000000);float g=k*2.2f*d;c.drawRoundRect(l+3*d-g*.4f,t+4*d-g*.2f,r+5*d+g*.6f,b+g*.1f,4*d+g,4*d+g,paint);}
        }
        // Side posts.
        paint.setShader(new LinearGradient(0,0,post,0,wood[6],blend(wood[6],wood[3],.35f),Shader.TileMode.CLAMP));c.drawRect(0,0,post,h,paint);
        paint.setShader(new LinearGradient(w-post,0,w,0,blend(wood[6],wood[3],.35f),wood[6],Shader.TileMode.CLAMP));c.drawRect(w-post,0,w,h,paint);paint.setShader(null);
        paint.setColor(0x40000000);c.drawRect(post,0,post+2*d,backBottom,paint);c.drawRect(w-post-2*d,0,w-post,backBottom,paint);
        if(plank){
            // Top face in perspective, the books' contact shadow, then the front edge with a lit rim.
            Path face=new Path();face.moveTo(post*.6f,backBottom);face.lineTo(w-post*.6f,backBottom);face.lineTo(w,backBottom+top);face.lineTo(0,backBottom+top);face.close();
            paint.setShader(new LinearGradient(0,backBottom,0,backBottom+top,blend(wood[3],wood[1],.25f),wood[3],Shader.TileMode.CLAMP));c.drawPath(face,paint);paint.setShader(null);
            for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()!=VISIBLE||!(child instanceof Body))continue;paint.setShader(new LinearGradient(0,child.getBottom()-2*d,0,child.getBottom()+4*d,0x66000000,0x00000000,Shader.TileMode.CLAMP));c.drawRect(child.getLeft()-d,child.getBottom()-2*d,child.getRight()+7*d,child.getBottom()+4*d,paint);paint.setShader(null);}
            float y=h-front;
            paint.setShader(new LinearGradient(0,y,0,h,wood[4],wood[5],Shader.TileMode.CLAMP));c.drawRect(0,y,w,h,paint);paint.setShader(null);
            paint.setColor(0x59ffffff);c.drawRect(0,y,w,y+Math.max(1,d),paint);
            paint.setColor(0x26ffffff);c.drawRect(0,y+2*d,w,y+2*d+Math.max(1,.6f*d),paint);
            paint.setColor(0x4d000000);c.drawRect(0,h-Math.max(1,d),w,h,paint);
        }
        super.dispatchDraw(c);
    }
    /** Soft vertical grain with lighter streaks; the phase follows the row position, so it runs on between rows. */
    void buildGrain(int w,int h){
        grain.reset();streak.reset();grainW=w;grainH=h;grainTop=getTop();float top=grainTop,step=16*d;int lines=Math.max(8,(int)(w/(5.5f*d)));
        for(int k=0;k<lines;k++){
            Path p=k%5==2?streak:grain;
            float x0=(k+.5f)*w/lines+((k*7919)%7-3)*.7f*d,amp=(.6f+(k*37%5)*.25f)*d,freq=(90+(k*53%70))*d,phase=(k*977%628)/100f;
            float y=0,x=x0+amp*(float)Math.sin(top/freq*6.2832f+phase);p.moveTo(x,0);
            while(y<h){float y2=Math.min(h,y+step),mid=(y+y2)/2;float xm=x0+amp*(float)Math.sin((top+mid)/freq*6.2832f+phase),x2=x0+amp*(float)Math.sin((top+y2)/freq*6.2832f+phase);p.quadTo(xm,mid,x2,y2);y=y2;}
        }
    }
    static int blend(int a,int b,float t){return Color.argb(255,(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}

    /** A book standing on the shelf: the page block shows on the right edge of the cover. */
    static final class Body extends FrameLayout {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final float d;final RectF r=new RectF();
        Body(Context c,View cover){super(c);d=getResources().getDisplayMetrics().density;setWillNotDraw(false);setClipChildren(false);LayoutParams lp=new LayoutParams(-1,-1);lp.rightMargin=(int)(4*d);addView(cover,lp);}
        @Override protected void dispatchDraw(Canvas c){
            float w=getWidth(),h=getHeight(),edge=4*d;
            r.set(edge,2*d,w,h-.5f*d);p.setShader(new LinearGradient(w-edge,0,w,0,0xfffbf8f1,0xffcfc5b3,Shader.TileMode.CLAMP));c.drawRoundRect(r,2*d,2*d,p);p.setShader(null);
            p.setColor(0x1f6b5b45);p.setStrokeWidth(Math.max(1,.5f*d));for(float y=4*d;y<h-2*d;y+=2.2f*d)c.drawLine(w-edge,y,w-.6f*d,y,p);
            p.setColor(0x33000000);c.drawRect(w-.8f*d,2*d,w,h-.5f*d,p);
            super.dispatchDraw(c);
        }
    }
    /** Light over the cover: hinge crease near the spine, a soft sheen and a darker right edge. */
    static final class Sheen extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Sheen(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight();if(w<=0)return;
            p.setShader(new LinearGradient(0,0,w*.1f,0,new int[]{0x66000000,0x14000000,0x38ffffff,0x00ffffff},new float[]{0,.35f,.5f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w*.1f,h,p);
            p.setShader(new LinearGradient(0,0,w,h,new int[]{0x22ffffff,0x00ffffff,0x00000000,0x1f000000},new float[]{0,.35f,.7f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
            p.setShader(new LinearGradient(w-w*.05f,0,w,0,0x00000000,0x40000000,Shader.TileMode.CLAMP));c.drawRect(w-w*.05f,0,w,h,p);p.setShader(null);
        }
    }
}
