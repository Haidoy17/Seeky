package mz.bam.seeky.android;
import android.content.Context;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;

/** Explicit bounded minus/plus controls; every touch target remains at least 44 dp. */
final class SizeControl extends LinearLayout {
 interface Change{void changed(int value);}
 final TextView minus,plus,value;final String key,title;final Change change;
 SizeControl(Context c,String title,String key,Change change){super(c);this.title=title;this.key=key;this.change=change;setGravity(Gravity.CENTER_VERTICAL);UiSpacing.ignore(this);AppPalette p=AppPalette.of(c);
  TextView label=PlusUi.text(c,title,13);label.setTypeface(null,Typeface.BOLD);addView(label,new LayoutParams(0,-2,1));minus=button("−",AppLanguage.text(c,"Diminuir")+" "+AppLanguage.text(c,title));value=PlusUi.text(c,"",12);value.setGravity(Gravity.CENTER);plus=button("+",AppLanguage.text(c,"Aumentar")+" "+AppLanguage.text(c,title));addView(minus,new LayoutParams(PlusUi.dp(c,44),PlusUi.dp(c,44)));addView(value,new LayoutParams(PlusUi.dp(c,62),PlusUi.dp(c,44)));addView(plus,new LayoutParams(PlusUi.dp(c,44),PlusUi.dp(c,44)));minus.setOnClickListener(v->adjust(-1));plus.setOnClickListener(v->adjust(1));refresh();
 }
 TextView button(String text,String description){TextView b=PlusUi.text(getContext(),text,24);b.setGravity(Gravity.CENTER);b.setContentDescription(description);GradientDrawable d=new GradientDrawable();d.setColor(AppPalette.of(getContext()).tint);d.setCornerRadius(PlusUi.dp(getContext(),13));b.setBackground(d);b.setTextColor(AppPalette.of(getContext()).accent);Motion.touch(b);return b;}
 int current(){return key.equals("flash_button_size")?IconSizing.bubble(getContext()):IconSizing.icons(getContext());}
 void adjust(int direction){int before=current(),next=key.equals("flash_button_size")?IconSizing.bubbleBound(before+direction):IconSizing.bounded(before+direction);if(next==before)return;IconSizing.set(getContext(),key,next);refresh();change.changed(next);}
 void refresh(){int n=current(),min=key.equals("flash_button_size")?-3:0;value.setText(key.equals("flash_button_size")?Math.round(IconSizing.bubbleVisual(n)*100f/52)+"%":new String[]{"80%","100%","125%"}[n]);minus.setEnabled(n>min);plus.setEnabled(n<2);minus.setAlpha(n>min?1:.35f);plus.setAlpha(n<2?1:.35f);}
}
