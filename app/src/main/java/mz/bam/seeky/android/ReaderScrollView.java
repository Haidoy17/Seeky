package mz.bam.seeky.android;

import android.view.*;
import android.widget.ScrollView;

/** A horizontal turn owns only its drag; vertical gestures and selection stay with Android. */
final class ReaderScrollView extends ScrollView {
    final ReaderActivity reader;final PageSwipe swipe;boolean intercepted;
    ReaderScrollView(ReaderActivity reader){super(reader);this.reader=reader;swipe=new PageSwipe(Math.max(reader.dp(24),ViewConfiguration.get(reader).getScaledTouchSlop()*2),reader.dp(56),ViewConfiguration.getLongPressTimeout());setContentDescription(AppLanguage.text(reader,"Deslize para a esquerda ou direita para mudar de página."));}
    boolean selecting(){return reader.bookText!=null&&reader.bookText.getSelectionStart()>=0&&reader.bookText.getSelectionStart()!=reader.bookText.getSelectionEnd();}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){swipe.begin(event.getX(),event.getY(),event.getEventTime());swipe.blocked=selecting();intercepted=false;}
        if(event.getPointerCount()>1)swipe.blocked=true;
        if(action==MotionEvent.ACTION_MOVE&&swipe.move(event.getX(),event.getY(),event.getEventTime(),event.getPointerCount(),selecting())){
            if(!intercepted){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();if(reader.bookText!=null)reader.bookText.clearFocus();setFocusableInTouchMode(true);requestFocus();intercepted=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}return true;
        }
        if(action==MotionEvent.ACTION_UP){
            int direction=swipe.end(event.getX(),event.getY(),event.getEventTime(),event.getPointerCount(),selecting());
            if(intercepted){intercepted=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(direction!=0)reader.chrome.turn(direction);return true;}
        }
        if(action==MotionEvent.ACTION_CANCEL){swipe.reset();intercepted=false;}
        if(intercepted)return true;
        return super.dispatchTouchEvent(event);
    }
}
