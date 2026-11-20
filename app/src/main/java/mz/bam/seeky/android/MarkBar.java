package mz.bam.seeky.android;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.function.Consumer;

/**
 * One-tap marking bar shown at the bottom while text is selected in the reader (text pages and PDF
 * pages): six colours, underline, strike-through and note. The system selection menu keeps the rest.
 */
final class MarkBar {
    final PopupWindow window;final LinearLayout row;
    private MarkBar(PopupWindow window,LinearLayout row){this.window=window;this.row=row;}

    static MarkBar show(Activity a,Consumer<String> pick){
        if(a==null||a.isFinishing())return null;
        int dp8=PlusUi.dp(a,8),screen=a.getResources().getDisplayMetrics().widthPixels;
        // Everything fits on a 360 dp phone: circles shrink before anything scrolls.
        int tool=PlusUi.dp(a,38),gap=PlusUi.dp(a,6),free=screen-PlusUi.dp(a,20)-2*PlusUi.dp(a,10)-PlusUi.dp(a,9)-3*tool;
        int dot=Math.max(PlusUi.dp(a,24),Math.min(PlusUi.dp(a,34),free/6-gap));
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(PlusUi.dp(a,10),dp8,PlusUi.dp(a,10),dp8);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xf2202227);bg.setCornerRadius(PlusUi.dp(a,26));row.setBackground(bg);
        final MarkBar[] self=new MarkBar[1];
        for(int i=0;i<Highlights.KINDS.length;i++){
            final String kind=Highlights.KINDS[i];
            View circle=new View(a);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Highlights.COLORS[i]);g.setStroke(PlusUi.dp(a,2),0x55ffffff);circle.setBackground(g);
            circle.setContentDescription(AppLanguage.text(a,"Destacar")+" "+AppLanguage.text(a,Highlights.NAMES[i]).toLowerCase(java.util.Locale.ROOT));
            circle.setOnClickListener(v->choose(self[0],pick,kind));Motion.touch(circle);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dot,dot);lp.rightMargin=gap;row.addView(circle,lp);
        }
        View line=new View(a);line.setBackgroundColor(0x44ffffff);LinearLayout.LayoutParams sep=new LinearLayout.LayoutParams(PlusUi.dp(a,1),PlusUi.dp(a,28));sep.rightMargin=PlusUi.dp(a,6);sep.leftMargin=PlusUi.dp(a,2);row.addView(line,sep);
        row.addView(styled(a,"U",new UnderlineSpan(),"Sublinhar",()->choose(self[0],pick,"underline")),new LinearLayout.LayoutParams(tool,tool));
        row.addView(styled(a,"S",new StrikethroughSpan(),"Riscar",()->choose(self[0],pick,"strike")),new LinearLayout.LayoutParams(tool,tool));
        FrameLayout note=new FrameLayout(a);note.addView(new Icons(a,"note",0xffffffff),new FrameLayout.LayoutParams(PlusUi.dp(a,22),PlusUi.dp(a,22),Gravity.CENTER));
        note.setContentDescription(AppLanguage.text(a,"Nota"));note.setOnClickListener(v->choose(self[0],pick,"note"));Motion.touch(note);
        row.addView(note,new LinearLayout.LayoutParams(tool,tool));
        HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(row);
        row.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int width=Math.min(row.getMeasuredWidth(),screen-PlusUi.dp(a,20));
        PopupWindow window=new PopupWindow(scroll,width,ViewGroup.LayoutParams.WRAP_CONTENT,false);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setOutsideTouchable(false);window.setTouchable(true);window.setClippingEnabled(true);window.setElevation(PlusUi.dp(a,10));
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        MarkBar bar=new MarkBar(window,row);self[0]=bar;
        View anchor=a.getWindow().getDecorView();
        Runnable place=()->{if(a.isFinishing()||window.isShowing())return;try{window.showAtLocation(anchor,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,PlusUi.dp(a,34));}catch(RuntimeException ignored){}};
        if(anchor.getWindowToken()!=null)place.run();else anchor.post(place);
        return bar;
    }
    static TextView styled(Activity a,String letter,Object span,String description,Runnable action){
        SpannableString s=new SpannableString(letter);s.setSpan(span,0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t=new TextView(a);t.setText(s);t.setTextSize(19);t.setTypeface(Typeface.SERIF,Typeface.BOLD);t.setTextColor(0xffffffff);t.setGravity(Gravity.CENTER);
        t.setContentDescription(AppLanguage.text(a,description));t.setOnClickListener(v->action.run());Motion.touch(t);return t;
    }
    static void choose(MarkBar bar,Consumer<String> pick,String kind){if(bar!=null)bar.dismiss();pick.accept(kind);}
    boolean showing(){return window.isShowing();}
    void dismiss(){try{if(window.isShowing())window.dismiss();}catch(RuntimeException ignored){}}
}
