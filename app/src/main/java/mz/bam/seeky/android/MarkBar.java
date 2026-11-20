package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.function.Consumer;

/**
 * Selection panel of the reader, next to the selected words: style (highlight, underline, strike,
 * wavy), six colours, and Note / Copy / Dictionary / Share / More. A colour applies the chosen style;
 * "More" brings back the full selection menu.
 */
final class MarkBar {
    interface Host {
        /** Mark the selection with this kind ("highlight-green", "underline-blue"…). */
        void mark(String kind);
        /** "note", "copy", "dictionary", "share" or "more". */
        void action(String what);
        /** Selection bounds on screen, or null to sit at the bottom. */
        Rect anchor();
    }
    static final String[] STYLES={"highlight","underline","strike","squiggle"};
    static final String[] STYLE_NAMES={"Destacar","Sublinhar","Riscar","Ondulado"};

    final Activity a;final Host host;final PopupWindow window;final LinearLayout row,card;
    final Glyph[] glyphs=new Glyph[STYLES.length];final View[] pills=new View[Highlights.KEYS.length];
    String style;int colour;

    private MarkBar(Activity a,Host host){
        this.a=a;this.host=host;
        android.content.SharedPreferences p=a.getSharedPreferences("seeky",0);
        style=p.getString("mark_style","highlight");if(java.util.Arrays.asList(STYLES).indexOf(style)<0)style="highlight";
        colour=Math.max(0,Math.min(Highlights.KEYS.length-1,p.getInt("mark_colour",0)));
        AppPalette pal=AppPalette.of(a);
        card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(8),dp(6),dp(8),dp(4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(pal.surface);bg.setCornerRadius(dp(18));bg.setStroke(dp(1),(pal.ink&0xffffff)|0x18000000);card.setBackground(bg);card.setElevation(dp(12));
        LinearLayout styles=new LinearLayout(a);
        for(int i=0;i<STYLES.length;i++){final String s=STYLES[i];Glyph g=new Glyph(a,s,pal);g.setContentDescription(AppLanguage.text(a,STYLE_NAMES[i]));g.setOnClickListener(v->pickStyle(s));Motion.touch(g);glyphs[i]=g;styles.addView(g,new LinearLayout.LayoutParams(0,dp(46),1));}
        card.addView(styles);
        row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(4),0,dp(6));
        for(int i=0;i<pills.length;i++){final int c=i;View pill=new View(a);pill.setOnClickListener(v->pickColour(c));Motion.touch(pill);pills[i]=pill;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(26),1);lp.setMargins(dp(5),0,dp(5),0);row.addView(pill,lp);}
        card.addView(row);
        View line=new View(a);line.setBackgroundColor((pal.ink&0xffffff)|0x1f000000);card.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout actions=new LinearLayout(a);
        String[][] items={{"note","Nota","note"},{"copy","Copiar","copy"},{"translate","Dicionário","dictionary"},{"share","Partilhar","share"},{"more","Mais","more"}};
        for(String[] it:items){
            LinearLayout cell=new LinearLayout(a);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);
            cell.addView(new Icons(a,it[0],pal.ink),new LinearLayout.LayoutParams(dp(20),dp(20)));
            TextView t=new TextView(a);t.setText(AppLanguage.text(a,it[1]));t.setTextSize(11.5f);t.setTextColor(pal.ink);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setPadding(0,dp(3),0,0);cell.addView(t);
            final String what=it[2];cell.setContentDescription(AppLanguage.text(a,it[1]));cell.setOnClickListener(v->{dismiss();host.action(what);});Motion.touch(cell);
            actions.addView(cell,new LinearLayout.LayoutParams(0,dp(54),1));
        }
        card.addView(actions);
        refresh();
        int width=Math.min(a.getResources().getDisplayMetrics().widthPixels-dp(16),dp(460));
        card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        FrameLayout frame=new FrameLayout(a);frame.setPadding(0,dp(4),0,dp(12));frame.setClipToPadding(false);frame.addView(card,new FrameLayout.LayoutParams(width,-2));
        window=new PopupWindow(frame,width,ViewGroup.LayoutParams.WRAP_CONTENT,false);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setOutsideTouchable(false);window.setTouchable(true);window.setClippingEnabled(true);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
    }
    int dp(float n){return PlusUi.dp(a,n);}

    static MarkBar show(Activity a,Host host){
        if(a==null||a.isFinishing())return null;
        MarkBar bar=new MarkBar(a,host);
        View anchor=a.getWindow().getDecorView();
        if(anchor.getWindowToken()!=null)bar.place();else anchor.post(bar::place);
        return bar;
    }
    /** Simple form: every mark and the note go to {@code pick}; the other actions do nothing. */
    static MarkBar show(Activity a,Consumer<String> pick){
        return show(a,new Host(){public void mark(String kind){pick.accept(kind);}public void action(String what){if(what.equals("note"))pick.accept("note");}public Rect anchor(){return null;}});
    }
    /** Above the selection when there is room, otherwise below it (clear of the handles). */
    void place(){
        if(a.isFinishing())return;
        int screenW=a.getResources().getDisplayMetrics().widthPixels,screenH=a.getResources().getDisplayMetrics().heightPixels;
        int h=card.getMeasuredHeight()+dp(16),w=window.getWidth(),x=(screenW-w)/2,y;
        Rect r;try{r=host.anchor();}catch(RuntimeException e){r=null;}
        if(r==null||r.isEmpty())y=screenH-h-dp(28);
        else{int above=r.top-h-dp(6);y=above>=dp(40)?above:Math.min(screenH-h-dp(8),r.bottom+dp(34));}
        try{if(window.isShowing())window.update(x,y,-1,-1);else window.showAtLocation(a.getWindow().getDecorView(),Gravity.TOP|Gravity.START,x,y);}catch(RuntimeException ignored){}
    }
    void pickStyle(String s){
        if(s.equals(style)){apply();return;}
        style=s;a.getSharedPreferences("seeky",0).edit().putString("mark_style",s).apply();refresh();
    }
    void pickColour(int c){colour=c;a.getSharedPreferences("seeky",0).edit().putInt("mark_colour",c).apply();apply();}
    void apply(){String kind=Highlights.kind(style,colour);dismiss();host.mark(kind);}
    /** Highlights the chosen style and shows the colours in that style (pastel fills, strong lines). */
    void refresh(){
        AppPalette pal=AppPalette.of(a);
        for(int i=0;i<glyphs.length;i++){glyphs[i].selected=STYLES[i].equals(style);glyphs[i].color=Highlights.color(Highlights.kind(STYLES[i],colour));glyphs[i].invalidate();}
        String verb=STYLE_NAMES[java.util.Arrays.asList(STYLES).indexOf(style)];
        for(int i=0;i<pills.length;i++){
            GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(13));g.setColor(Highlights.color(Highlights.kind(style,i)));
            if(i==colour)g.setStroke(dp(3),pal.ink);else g.setStroke(dp(1),(pal.ink&0xffffff)|0x22000000);
            pills[i].setBackground(g);
            pills[i].setContentDescription(AppLanguage.text(a,verb)+" "+AppLanguage.text(a,Highlights.NAMES[i]).toLowerCase(java.util.Locale.ROOT));
        }
    }
    boolean showing(){return window.isShowing();}
    void dismiss(){try{if(window.isShowing())window.dismiss();}catch(RuntimeException ignored){}}

    /** A "T" showing a mark style in its colour. */
    static final class Glyph extends View {
        final String style;final AppPalette pal;int color;boolean selected;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path wave=new Path();
        Glyph(Context c,String style,AppPalette pal){super(c);this.style=style;this.pal=pal;}
        @Override protected void onDraw(Canvas c){
            float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f;
            if(selected){p.setStyle(Paint.Style.FILL);p.setColor(pal.tint);c.drawRoundRect(cx-30*d,3*d,cx+30*d,getHeight()-3*d,10*d,10*d,p);}
            p.setTextSize(22*d);p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm=p.getFontMetrics();float base=cy-(fm.ascent+fm.descent)/2,half=p.measureText("T")/2+5*d;
            p.setStyle(Paint.Style.FILL);
            if(style.equals("highlight")){p.setColor(color);c.drawRoundRect(cx-half,base+fm.ascent+3*d,cx+half,base+fm.descent,4*d,4*d,p);}
            p.setColor(style.equals("highlight")?0xff272237:pal.ink);c.drawText("T",cx,base,p);
            p.setColor(color);float t=2.4f*d;
            if(style.equals("underline"))c.drawRect(cx-half,base+3*d,cx+half,base+3*d+t,p);
            if(style.equals("strike"))c.drawRect(cx-half,base+(fm.ascent/2.6f)-t/2,cx+half,base+(fm.ascent/2.6f)+t/2,p);
            if(style.equals("squiggle")){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f*d);wave.reset();float y=base+5*d;wave.moveTo(cx-half,y);for(float x=cx-half;x<cx+half;x+=4*d)wave.quadTo(x+1*d,y+((int)((x-cx+half)/(4*d))%2==0?-2.5f*d:2.5f*d),x+4*d,y);c.drawPath(wave,p);}
        }
    }
}
