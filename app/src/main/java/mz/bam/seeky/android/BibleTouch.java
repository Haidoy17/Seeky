package mz.bam.seeky.android;
import android.text.*;import android.text.style.ClickableSpan;import android.view.*;import android.widget.TextView;

/** A short, stationary tap opens a citation; long press and scroll remain native selection gestures. */
final class BibleTouch implements View.OnTouchListener {
    float x,y;long down;boolean moved;final int slop;
    BibleTouch(TextView view){slop=ViewConfiguration.get(view.getContext()).getScaledTouchSlop();}
    public boolean onTouch(View v,MotionEvent e){TextView text=(TextView)v;switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:x=e.getX();y=e.getY();down=e.getEventTime();moved=e.getPointerCount()!=1;break;case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_CANCEL:moved=true;break;case MotionEvent.ACTION_MOVE:if(Math.abs(e.getX()-x)>slop||Math.abs(e.getY()-y)>slop)moved=true;break;case MotionEvent.ACTION_UP:if(moved||e.getPointerCount()!=1||e.getEventTime()-down>=ViewConfiguration.getLongPressTimeout()||!(text.getText() instanceof Spannable)||text.getLayout()==null)break;Layout layout=text.getLayout();float atX=e.getX()-text.getTotalPaddingLeft()+text.getScrollX();int atY=(int)e.getY()-text.getTotalPaddingTop()+text.getScrollY();if(atY<0||atY>=layout.getHeight())break;int line=layout.getLineForVertical(atY);if(atX<layout.getLineLeft(line)||atX>layout.getLineRight(line))break;int offset=layout.getOffsetForHorizontal(line,atX);Spannable spans=(Spannable)text.getText();for(ClickableSpan link:spans.getSpans(Math.max(0,offset-1),Math.min(spans.length(),offset+1),ClickableSpan.class)){int start=spans.getSpanStart(link),end=spans.getSpanEnd(link);if(offset>=start&&offset<end){Selection.removeSelection(spans);link.onClick(text);return true;}}break;}return false;}
}
