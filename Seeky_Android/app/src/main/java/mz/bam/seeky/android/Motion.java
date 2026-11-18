package mz.bam.seeky.android;

import android.animation.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.PowerManager;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;

/** Finite, interruptible transitions. Actions remain available with motion disabled. */
final class Motion {
    static boolean enabled(Context c){
        PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
        return !PerformanceMode.light(c)&&c.getSharedPreferences("seeky",0).getBoolean("motion",true)
                &&ValueAnimator.areAnimatorsEnabled()&&(power==null||!power.isPowerSaveMode());
    }
    static void reset(View v){v.animate().cancel();v.setAlpha(1);v.setTranslationX(0);v.setTranslationY(0);v.setScaleX(1);v.setScaleY(1);}
    static void enter(View v,int delay){
        if(!enabled(v.getContext())){reset(v);return;}
        v.animate().cancel();v.setAlpha(0);v.setTranslationY(8*v.getResources().getDisplayMetrics().density);
        v.animate().alpha(1).translationY(0).setStartDelay(Math.min(delay,90)).setDuration(180).setInterpolator(new DecelerateInterpolator()).withEndAction(null).start();
    }
    static void openBook(View v){
        if(!enabled(v.getContext())){reset(v);v.setRotationY(0);return;}
        v.post(()->{if(!v.isAttachedToWindow())return;v.animate().cancel();v.setCameraDistance(10000*v.getResources().getDisplayMetrics().density);v.setPivotX(v.getWidth()*.06f);v.setPivotY(v.getHeight()/2f);v.setRotationY(-58);v.setScaleX(.96f);v.setScaleY(.96f);v.setAlpha(.15f);v.animate().rotationY(0).scaleX(1).scaleY(1).alpha(1).setDuration(430).setInterpolator(new DecelerateInterpolator()).withEndAction(null).start();});
    }
    static void leave(View v,Runnable complete){
        if(!enabled(v.getContext())){reset(v);complete.run();return;}
        v.animate().cancel();v.animate().alpha(0).translationY(-4*v.getResources().getDisplayMetrics().density).setStartDelay(0).setDuration(75).withEndAction(complete).start();
    }
    static void pop(View v){
        if(!enabled(v.getContext()))return;
        v.animate().cancel();v.setScaleX(.8f);v.setScaleY(.8f);v.animate().scaleX(1).scaleY(1).setStartDelay(0).setDuration(150).withEndAction(null).start();
    }
    static void touch(View v){
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(20*v.getResources().getDisplayMetrics().density);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf((AppPalette.of(v.getContext()).accent&0xffffff)|0x22000000),v.getBackground(),mask));
        v.setFocusable(true);
    }
    static void rows(AbsListView list){
        if(!enabled(list.getContext()))return;
        list.post(()->{for(int i=0;i<Math.min(list.getChildCount(),5);i++)enter(list.getChildAt(i),i*18);});
    }
    private Motion(){}
}
