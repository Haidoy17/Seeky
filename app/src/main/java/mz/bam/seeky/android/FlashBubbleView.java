package mz.bam.seeky.android;
import android.content.Context;import android.content.res.ColorStateList;import android.graphics.Color;import android.graphics.drawable.*;import android.view.*;import android.widget.FrameLayout;

/** A generous touch target around a restrained, theme-aware circular search button. */
final class FlashBubbleView extends FrameLayout {
 final FrameLayout circle;
 FlashBubbleView(Context c){super(c);AppPalette p=AppPalette.of(c);int diameter=IconSizing.bubbleVisual(IconSizing.bubble(c));int size=PlusUi.dp(c,diameter);setClipChildren(false);setClipToPadding(false);setFocusable(true);setContentDescription(AppLanguage.text(c,"Abrir pesquisa rápida Seeky. Arraste para mover."));
  circle=new FrameLayout(c);circle.setDuplicateParentStateEnabled(true);GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{p.accent,ThemeColors.blend(p.accent,Color.BLACK,.09f)});fill.setShape(GradientDrawable.OVAL);fill.setStroke(PlusUi.dp(c,1),ThemeColors.blend(p.accent,Color.WHITE,.28f));GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setShape(GradientDrawable.OVAL);circle.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30ffffff),fill,mask));circle.setElevation(PlusUi.dp(c,4));circle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);circle.addView(new Icons(c,"search",p.onAccent),new FrameLayout.LayoutParams(PlusUi.dp(c,diameter>=64?30:diameter<32?Math.max(10,diameter-6):diameter<=44?22:25),PlusUi.dp(c,diameter>=64?30:diameter<32?Math.max(10,diameter-6):diameter<=44?22:25),Gravity.CENTER));addView(circle,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
 }
}
