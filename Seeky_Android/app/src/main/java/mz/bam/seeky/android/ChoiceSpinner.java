package mz.bam.seeky.android;
import android.content.Context;
import android.widget.*;
import android.view.*;

/** Keeps the standard Spinner model while presenting choices in the shared rounded sheet. */
final class ChoiceSpinner extends Spinner {
 ChoiceSpinner(Context context){super(context);setContentDescription("Escolher opção");}
 // Android creates the selected label lazily, sometimes after the panel is styled.
 // Colour each newly bound child, including adapter refreshes and later selections.
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);for(int i=0;i<getChildCount();i++)colour(getChildAt(i));}
 void colour(View v){if(v instanceof TextView){TextView text=(TextView)v;text.setTextColor(AppPalette.of(getContext()).ink);text.setPadding(text.getPaddingLeft(),text.getPaddingTop(),Math.max(text.getPaddingRight(),PlusUi.dp(getContext(),28)),text.getPaddingBottom());text.setAlpha(isEnabled()?1f:.65f);}if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)colour(group.getChildAt(i));}}
 @Override protected void dispatchDraw(android.graphics.Canvas canvas){super.dispatchDraw(canvas);android.graphics.Paint arrow=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);arrow.setColor(AppPalette.of(getContext()).muted);arrow.setStyle(android.graphics.Paint.Style.STROKE);arrow.setStrokeWidth(PlusUi.dp(getContext(),2));float x=getWidth()-PlusUi.dp(getContext(),18),y=getHeight()/2f;android.graphics.Path path=new android.graphics.Path();path.moveTo(x-PlusUi.dp(getContext(),4),y-PlusUi.dp(getContext(),2));path.lineTo(x,y+PlusUi.dp(getContext(),2));path.lineTo(x+PlusUi.dp(getContext(),4),y-PlusUi.dp(getContext(),2));canvas.drawPath(path,arrow);}
 @Override public boolean performClick(){SpinnerAdapter adapter=getAdapter();if(adapter==null)return super.performClick();CharSequence[] values=new CharSequence[adapter.getCount()];for(int i=0;i<values.length;i++)values[i]=String.valueOf(adapter.getItem(i));new UiDialog.Builder(getContext()).setTitle(getPrompt()==null?"Escolher opção":getPrompt()).setSingleChoiceItems(values,getSelectedItemPosition(),(d,index)->{setSelection(index);d.dismiss();}).setNegativeButton("Cancelar",null).show();return true;}
}
