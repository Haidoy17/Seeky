package mz.bam.seeky.android;
import android.view.*;import android.widget.*;

/** Restore after the new text has a real layout; an early post can clamp a saved position. */
final class ReaderScrollRestore {
    static void whenReady(ReaderActivity a,Runnable action){TextView text=a.bookText;ScrollView scroll=a.bookScroll;if(text==null||scroll==null)return;int token=++a.scrollRestoreSerial;ViewTreeObserver observer=a.root.getViewTreeObserver();ViewTreeObserver.OnGlobalLayoutListener listener=new ViewTreeObserver.OnGlobalLayoutListener(){
        void remove(){if(observer.isAlive())observer.removeOnGlobalLayoutListener(this);}
        public void onGlobalLayout(){if(a.dead||token!=a.scrollRestoreSerial||text!=a.bookText||scroll!=a.bookScroll){remove();return;}if(a.original!=null&&a.original.active){remove();return;}if(text.getLayout()==null||text.isLayoutRequested()||scroll.isLayoutRequested()||text.getHeight()==0||scroll.getHeight()==0)return;remove();action.run();}
    };observer.addOnGlobalLayoutListener(listener);a.main.post(listener::onGlobalLayout);}
    static void restore(ReaderActivity a,int saved,int part){if(a.getIntent().hasExtra("search_offset")||a.tools!=null&&a.tools.pendingOffset>=0)return;whenReady(a,()->{if(saved>0){a.bookScroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);a.bookScroll.setFocusableInTouchMode(true);a.bookScroll.requestFocus();}a.bookScroll.scrollTo(0,saved);a.bookScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{a.positions.put("reader_scroll_"+a.uri+":"+part,y);a.chrome.update();});});}
    private ReaderScrollRestore(){}
}
