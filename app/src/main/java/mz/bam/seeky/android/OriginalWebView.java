package mz.bam.seeky.android;
import android.view.*;import android.webkit.WebView;
final class OriginalWebView extends WebView {
 final OriginalDocument owner;final PageSwipe swipe;boolean selecting,drag;float baseScale;
 OriginalWebView(OriginalDocument owner){super(owner.a);this.owner=owner;swipe=new PageSwipe(owner.a.dp(24),owner.a.dp(56),ViewConfiguration.getLongPressTimeout());}
 /** Selection menu of the original view: the page's own actions plus Seeky's marks and marking bar. */
 public ActionMode startActionMode(ActionMode.Callback callback,int type){ActionMode mode=super.startActionMode(new ActionMode.Callback2(){
  public boolean onCreateActionMode(ActionMode m,Menu menu){selecting=true;systemMenu=false;boolean ok=callback.onCreateActionMode(m,menu);marks(menu);hide(menu);owner.showMarkBar(m);return ok;}
  public boolean onPrepareActionMode(ActionMode m,Menu menu){callback.onPrepareActionMode(m,menu);marks(menu);hide(menu);return true;}
  public boolean onActionItemClicked(ActionMode m,MenuItem item){int id=item.getItemId();if(id==301){owner.markSelection("highlight",m);return true;}if(id==302){owner.chooseSelection(m);return true;}if(id==303){owner.markSelection("note",m);return true;}return callback.onActionItemClicked(m,item);}
  public void onDestroyActionMode(ActionMode m){selecting=false;owner.hideMarkBar();callback.onDestroyActionMode(m);}
  // Keeps the floating menu next to the selected words instead of over the toolbar.
  public void onGetContentRect(ActionMode m,View v,android.graphics.Rect out){if(callback instanceof ActionMode.Callback2)((ActionMode.Callback2)callback).onGetContentRect(m,v,out);else super.onGetContentRect(m,v,out);int[] at=new int[2];v.getLocationOnScreen(at);android.graphics.Rect screen=new android.graphics.Rect(out);screen.offset(at[0],at[1]);if(!screen.equals(contentRect)){contentRect=screen;owner.placeMarkBar();}}
 },type);return mode;}
 boolean systemMenu;android.graphics.Rect contentRect;
 /** The page's own menu and Seeky's items stay hidden until "Mais" in the selection panel. */
 void hide(Menu menu){if(systemMenu)return;for(int i=0;i<menu.size();i++)menu.getItem(i).setVisible(false);}
 void showMenu(ActionMode mode){systemMenu=true;if(mode==null)return;Menu m=mode.getMenu();for(int i=0;i<m.size();i++)m.getItem(i).setVisible(true);mode.invalidate();}
 void marks(Menu menu){if(menu.findItem(301)!=null)return;android.content.Context c=getContext();menu.add(0,301,1,AppLanguage.text(c,"Destacar"));menu.add(0,302,2,AppLanguage.text(c,"Cores e mais marcas"));menu.add(0,303,3,AppLanguage.text(c,"Nota"));}
 public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){swipe.begin(event.getX(),event.getY(),event.getEventTime());swipe.blocked=selecting||canScrollHorizontally(-1)||canScrollHorizontally(1)||baseScale>0&&getScale()>baseScale*1.05f;drag=false;}if(event.getPointerCount()>1)swipe.blocked=true;if(action==MotionEvent.ACTION_MOVE&&swipe.move(event.getX(),event.getY(),event.getEventTime(),event.getPointerCount(),selecting)){if(!drag){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();drag=true;}return true;}if(action==MotionEvent.ACTION_UP){int direction=swipe.end(event.getX(),event.getY(),event.getEventTime(),event.getPointerCount(),selecting);if(drag){drag=false;if(direction!=0)owner.turn(direction);return true;}}if(action==MotionEvent.ACTION_CANCEL){swipe.reset();drag=false;}return drag||super.dispatchTouchEvent(event);}
}
