package mz.bam.seeky.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;


/**
 * Botão Flash: uma zona de toque generosa à volta de um círculo de pesquisa discreto.
 *
 * <p>Resposta física ao toque: o círculo encolhe ao premir, "levanta" (sombra maior e
 * anel de destaque) enquanto é arrastado e volta com uma pequena mola. Vibrações curtas
 * confirmam toque, toque longo e encaixe na margem. Tudo respeita o modo de movimento
 * reduzido/poupança de energia ({@link Motion#enabled}).
 */
final class FlashBubbleView extends FrameLayout {
 final FrameLayout circle;
 final View halo;
 private final float restElevation, liftElevation;
 private boolean lifted;

 FlashBubbleView(Context c){super(c);
  AppPalette p=AppPalette.of(c);
  int diameter=IconSizing.bubbleVisual(IconSizing.bubble(c));
  int size=PlusUi.dp(c,diameter);
  setClipChildren(false);
  setClipToPadding(false);
  setFocusable(true);
  setHapticFeedbackEnabled(true);
  setContentDescription(AppLanguage.text(c,"Abrir pesquisa rápida Seeky. Arraste para mover. Toque longo para ajustes."));
  restElevation=PlusUi.dp(c,4);
  liftElevation=PlusUi.dp(c,12);

  // Anel de destaque, visível apenas enquanto o botão está "levantado".
  halo=new View(c);
  GradientDrawable ring=new GradientDrawable();
  ring.setShape(GradientDrawable.OVAL);
  ring.setColor((p.accent&0x00ffffff)|0x40000000);
  ring.setStroke(PlusUi.dp(c,2),ThemeColors.blend(p.accent,Color.WHITE,.35f));
  halo.setBackground(ring);
  halo.setAlpha(0f);
  halo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  // O anel cabe na janela flutuante (zona de toque), para não ser cortado.
  int haloSize=Math.min(PlusUi.dp(c,IconSizing.bubbleTouch(IconSizing.bubble(c))),size+PlusUi.dp(c,12));
  addView(halo,new FrameLayout.LayoutParams(haloSize,haloSize,Gravity.CENTER));

  circle=new FrameLayout(c);
  circle.setDuplicateParentStateEnabled(true);
  // Degradê com brilho no topo: lê-se como um botão, não como uma mancha plana.
  GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{ThemeColors.blend(p.accent,Color.WHITE,.12f),p.accent,ThemeColors.blend(p.accent,Color.BLACK,.18f)});
  fill.setShape(GradientDrawable.OVAL);
  fill.setStroke(PlusUi.dp(c,1),ThemeColors.blend(p.accent,Color.WHITE,.32f));
  GradientDrawable mask=new GradientDrawable();
  mask.setColor(Color.WHITE);
  mask.setShape(GradientDrawable.OVAL);
  circle.setBackground(new RippleDrawable(ColorStateList.valueOf(0x38ffffff),fill,mask));
  circle.setElevation(restElevation);
  circle.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
  circle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  int icon=PlusUi.dp(c,diameter>=64?30:diameter<32?Math.max(10,diameter-6):diameter<=44?22:25);
  circle.addView(new Icons(c,"search",p.onAccent),new FrameLayout.LayoutParams(icon,icon,Gravity.CENTER));
  addView(circle,new FrameLayout.LayoutParams(size,size,Gravity.CENTER));
 }

 /** Dedo pousado/levantado: encolhe ligeiramente e volta com mola. */
 void press(boolean down){
  if(!Motion.enabled(getContext())){circle.setScaleX(1f);circle.setScaleY(1f);return;}
  circle.animate().cancel();
  if(down)circle.animate().scaleX(.9f).scaleY(.9f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
  else circle.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2.2f)).start();
 }

 /** Durante o arrasto o botão "levanta": sombra maior e anel de destaque. */
 void lift(boolean up){
  if(lifted==up)return;
  lifted=up;
  boolean motion=Motion.enabled(getContext());
  halo.animate().cancel();
  if(motion){
   halo.animate().alpha(up?1f:0f).scaleX(up?1f:.85f).scaleY(up?1f:.85f).setDuration(up?120:200).start();
   circle.animate().translationZ(up?liftElevation-restElevation:0f).setDuration(160).start();
  }else{
   halo.setAlpha(up?1f:0f);
   circle.setTranslationZ(up?liftElevation-restElevation:0f);
  }
 }

 /** Vibrações curtas e discretas; respeitam a definição de vibração ao toque do sistema. */
 void tap(){performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);}
 void longPress(){performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}
 void docked(){performHapticFeedback(android.os.Build.VERSION.SDK_INT>=30?HapticFeedbackConstants.GESTURE_END:HapticFeedbackConstants.CLOCK_TICK);}
}
