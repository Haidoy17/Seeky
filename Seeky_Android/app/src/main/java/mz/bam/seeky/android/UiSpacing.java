package mz.bam.seeky.android;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;

/** Spacing belongs to application controls; document pages and WebViews keep their layout. */
final class UiSpacing {
    static final java.util.WeakHashMap<View,Boolean> ignored=new java.util.WeakHashMap<>();static void ignore(View view){if(view!=null)ignored.put(view,true);}
    static final java.util.WeakHashMap<View,Boolean> watched=new java.util.WeakHashMap<>();
    static int dp(View v,int n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
    static boolean box(View v){
        if(v.getVisibility()==View.GONE||v instanceof WebView||v instanceof Icons)return false;
        if(v.getBackground()!=null&&(v.isClickable()||v instanceof TextView||v instanceof Spinner||v instanceof Button||v instanceof LinearLayout||v instanceof FrameLayout||v instanceof TableRow))return true;
        if(v instanceof LinearLayout){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i).isClickable()&&g.getChildAt(i).getBackground()!=null)return true;}
        return false;
    }
    static void apply(View v){apply(v,0,new int[]{0});}
    static void apply(View v,int depth,int[] count){
        if(v==null||ignored.containsKey(v)||depth>18||++count[0]>1600||v instanceof WebView||v instanceof ZoomImageView||v instanceof ReaderRuler)return;
        if(v instanceof ListView){ListView list=(ListView)v;if(list.getDividerHeight()<dp(v,10)){list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));list.setDividerHeight(dp(v,10));}return;}
        if(!(v instanceof ViewGroup))return;
        ViewGroup g=(ViewGroup)v;
        if(g instanceof LinearLayout){boolean vertical=((LinearLayout)g).getOrientation()==LinearLayout.VERTICAL;View previous=null;
            for(int i=0;i<g.getChildCount();i++){View child=g.getChildAt(i);if(child.getVisibility()==View.GONE)continue;
                if(previous!=null&&(g instanceof TableRow||g instanceof TableLayout||box(previous)&&box(child))&&child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams&&previous.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){
                    ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)child.getLayoutParams(),before=(ViewGroup.MarginLayoutParams)previous.getLayoutParams();int gap=dp(v,vertical?10:10);boolean changed=false;
                    if(vertical&&p.topMargin+before.bottomMargin<gap){p.topMargin=gap-before.bottomMargin;changed=true;}
                    if(!vertical&&p.leftMargin+before.rightMargin<gap){p.leftMargin=gap-before.rightMargin;changed=true;}
                    if(changed)child.setLayoutParams(p);
                }previous=child;
            }
        }
        for(int i=0;i<g.getChildCount();i++)apply(g.getChildAt(i),depth+1,count);
    }
    static void watch(View root){if(root==null||watched.containsKey(root))return;watched.put(root,true);root.getViewTreeObserver().addOnGlobalLayoutListener(()->apply(root));apply(root);}
    static void install(Application app){app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity a){watch(a.getWindow().getDecorView());}
        public void onActivityCreated(Activity a,Bundle b){WorkLeases.created(a);}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){WorkLeases.destroyed(a);}
    });}
    private UiSpacing(){}
}
