package mz.bam.seeky.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Small building blocks shared by the full-screen study, marks and game pages, in the app's palette. */
final class Screens {
    final Activity a;final AppPalette p;
    Screens(Activity a){this.a=a;p=AppPalette.of(a);}
    int dp(float n){return PlusUi.dp(a,n);}
    GradientDrawable round(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    TextView text(String s,float size,int color,boolean bold){
        TextView t=new TextView(a);t.setText(AppLanguage.text(a,s));t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(ThemeStyle.BOLD);t.setLineSpacing(dp(2),1.05f);return t;
    }
    LinearLayout column(){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(a);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    /** Scrolling page with the app background; content is returned for adding sections. */
    LinearLayout page(){
        ScrollView scroll=new ScrollView(a);scroll.setBackgroundColor(p.bg);scroll.setFillViewport(true);
        LinearLayout content=column();content.setPadding(dp(16),dp(10),dp(16),dp(28));scroll.addView(content);
        a.setContentView(scroll);a.getWindow().setStatusBarColor(p.bg);a.getWindow().setNavigationBarColor(p.bg);
        return content;
    }
    View header(String title,String subtitle,Runnable back){
        LinearLayout top=row();top.setPadding(0,dp(4),0,dp(12));
        FrameLayout b=new FrameLayout(a);b.setBackground(round(p.tint,22));
        b.addView(new Icons(a,"back",p.ink),new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
        b.setContentDescription(AppLanguage.text(a,"Voltar"));b.setOnClickListener(v->back.run());Motion.touch(b);
        top.addView(b,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout titles=column();titles.setPadding(dp(12),0,0,0);
        TextView t=text(title,21,p.ink,true);ThemeStyle.title(t,21);titles.addView(t);
        if(subtitle!=null&&!subtitle.isEmpty())titles.addView(text(subtitle,13,p.muted,false));
        top.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        return top;
    }
    LinearLayout card(){LinearLayout c=column();c.setBackground(round(p.surface,20));c.setPadding(dp(16),dp(14),dp(16),dp(16));c.setElevation(dp(1));return c;}
    LinearLayout.LayoutParams gap(int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);return lp;}
    TextView section(String s){TextView t=text(s,14,p.muted,true);t.setAllCaps(true);t.setLetterSpacing(.06f);t.setPadding(dp(4),dp(18),0,dp(8));return t;}
    /** Wide button; primary uses the accent. */
    TextView button(String label,boolean primary,Runnable action){
        TextView b=text(label,15,primary?p.onAccent:p.ink,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(50));b.setPadding(dp(14),dp(10),dp(14),dp(10));
        b.setBackground(round(primary?p.accent:p.tint,16));b.setContentDescription(AppLanguage.text(a,label));b.setOnClickListener(v->action.run());Motion.touch(b);return b;
    }
    LinearLayout chip(String icon,String label,boolean selected,Runnable action){
        LinearLayout c=row();c.setPadding(dp(icon==null?14:11),0,dp(14),0);c.setBackground(round(selected?p.accent:p.tint,19));
        if(icon!=null)c.addView(new Icons(a,icon,selected?p.onAccent:p.accent),new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView t=text(label,14,selected?p.onAccent:p.ink,true);t.setSingleLine(true);t.setPadding(icon==null?0:dp(7),0,0,0);c.addView(t,new LinearLayout.LayoutParams(-2,-2));
        c.setContentDescription(AppLanguage.text(a,label));c.setOnClickListener(v->action.run());Motion.touch(c);return c;
    }
    LinearLayout chips(){return row();}
    HorizontalScrollView scroller(LinearLayout row){HorizontalScrollView h=new HorizontalScrollView(a);h.setHorizontalScrollBarEnabled(false);h.addView(row);return h;}
    void addChip(LinearLayout row,View chip){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.rightMargin=dp(8);row.addView(chip,lp);}
    /** Icon tile, title and subtitle in one tappable row. */
    LinearLayout entry(String icon,String title,String subtitle,Runnable action){
        LinearLayout r=row();r.setPadding(dp(12),dp(12),dp(12),dp(12));r.setBackground(round(p.surface,18));
        FrameLayout tile=new FrameLayout(a);tile.setBackground(round(p.tint,14));tile.addView(new Icons(a,icon,p.accent),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
        r.addView(tile,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout t=column();t.setPadding(dp(12),0,dp(6),0);TextView title_=text(title,16,p.ink,true);t.addView(title_);
        if(subtitle!=null&&!subtitle.isEmpty()){TextView s=text(subtitle,13,p.muted,false);s.setMaxLines(3);s.setEllipsize(TextUtils.TruncateAt.END);t.addView(s);}
        r.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(new Icons(a,"chevron",p.muted),new LinearLayout.LayoutParams(dp(18),dp(18)));
        r.setContentDescription(AppLanguage.text(a,title));r.setOnClickListener(v->action.run());Motion.touch(r);return r;
    }
    static Typeface serif(){return ThemeStyle.SERIF_BOLD;}
}
