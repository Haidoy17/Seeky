package mz.bam.seeky.android;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

/** PDF sheet with bounded bitmap, pinch zoom, page fitting and horizontal page turns. */
final class ReaderPageView extends ZoomImageView {
    final ReaderActivity activity;
    final Paint shadow=new Paint();
    float downX,downY;
    boolean multiple;int lastFit=-1;
    ReaderPageView(ReaderActivity activity) {
        super(activity);this.activity=activity;tapped=()->activity.chrome.toggleChrome();
        setContentDescription(AppLanguage.text(activity,"Página do documento. Dois dedos para ampliar; deslize para mudar de página."));
    }
    void appearance() {
        setColorFilter(new ColorMatrixColorFilter(ReaderAppearance.pdfMatrix(activity.readerTheme)));
        setBackgroundColor(0xff202226);invalidate();
    }
    @Override void fit() {
        Drawable drawable=getDrawable();if(drawable==null||getWidth()==0||getHeight()==0)return;
        float width=(getWidth()-activity.dp(10f))/Math.max(1f,imageWidth(drawable));
        float height=(getHeight()-activity.dp(10f))/Math.max(1f,imageHeight(drawable));
        base=activity.pdfFit==1?width:activity.pdfFit==2?height:Math.min(width,height);
        scale=base;tx=(getWidth()-imageWidth(drawable)*scale)/2f;ty=activity.pdfFit==1?0:(getHeight()-imageHeight(drawable)*scale)/2f;apply();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        // A final viewport measurement can happen after the bitmap's posted restore.
        // Reapply the saved placement after ZoomImageView.fit() resets its matrix.
        if(activity!=null)restorePlacement();
    }
    String placementKey(){return "reader_pdf_place_"+activity.uri+":"+getTag()+":"+activity.pdfFit;}
    void savePlacement(){Drawable d=getDrawable();if(d==null||base<=0||scale<=0||!(getTag() instanceof Integer))return;activity.getSharedPreferences("seeky",0).edit().putFloat(placementKey()+"_zoom",scale/base).putFloat(placementKey()+"_x",(getWidth()/2f-tx)/Math.max(1,imageWidth(d)*scale)).putFloat(placementKey()+"_y",(getHeight()/2f-ty)/Math.max(1,imageHeight(d)*scale)).apply();}
    void restorePlacement(){Drawable d=getDrawable();if(d==null||base<=0||getWidth()==0)return;android.content.SharedPreferences p=activity.getSharedPreferences("seeky",0);if(!p.contains(placementKey()+"_zoom"))return;float ratio=p.getFloat(placementKey()+"_zoom",1);if(!Float.isFinite(ratio))return;scale=base*Math.max(1,Math.min(8,ratio));float x=p.getFloat(placementKey()+"_x",.5f),y=p.getFloat(placementKey()+"_y",.5f);if(!Float.isFinite(x)||!Float.isFinite(y))return;tx=getWidth()/2f-Math.max(0,Math.min(1,x))*imageWidth(d)*scale;ty=getHeight()/2f-Math.max(0,Math.min(1,y))*imageHeight(d)*scale;apply();}
    @Override protected void onDraw(Canvas canvas) {
        Drawable drawable=getDrawable();
        if(drawable!=null){RectF bounds=new RectF(0,0,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight());matrix.mapRect(bounds);for(int n=4;n>=1;n--){shadow.setColor((0x08000000*n));canvas.drawRect(bounds.left-n,bounds.top,bounds.right+n,bounds.bottom+n*2,shadow);}}
        super.onDraw(canvas);drawTextOverlays(canvas);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        selectionGestures.onTouchEvent(event);if(selectionMode!=null&&event.getPointerCount()==1){if(event.getActionMasked()==MotionEvent.ACTION_MOVE){int at=character(event.getX(),event.getY());if(at>=0){selectionStart=Math.min(selectionAnchor,at);selectionEnd=Math.max(selectionAnchor+1,at+1);selectionMode.invalidateContentRect();invalidate();}}return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();multiple=false;
            // In one-page mode the sheet pans inside its viewport. ListView must not
            // steal vertical drags just because fit-to-width is the base scale.
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(!activity.continuous||scale>base*1.02f);
        }
        if(event.getPointerCount()>1){multiple=true;getParent().requestDisallowInterceptTouchEvent(true);}
        if(scale>base*1.02f)getParent().requestDisallowInterceptTouchEvent(true);
        if(event.getActionMasked()==MotionEvent.ACTION_UP&&!multiple&&!activity.continuous&&scale<=base*1.02f){
            float dx=event.getX()-downX,dy=event.getY()-downY;
            if(Math.abs(dx)>activity.dp(65)&&Math.abs(dx)>Math.abs(dy)*1.5f){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);activity.setPage(activity.currentPage+(dx<0?1:-1));return true;}
        }
        boolean handled=super.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){savePlacement();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}return handled;
    }
    Bitmap detailTile;final Paint overlayPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);int tileSerial,detailDocument;boolean detailRendering,detailScheduled;Matrix requestedMatrix;int requestedWidth,requestedHeight;final Matrix detailToPage=new Matrix();
    PdfGlyphs glyphs;boolean glyphLoading;int selectionStart=-1,selectionEnd=-1,selectionAnchor;android.view.ActionMode selectionMode;SpeechEvents.State speechState;boolean speechFollow;
    final Runnable tileRequest=()->{detailScheduled=false;renderDetail();};
    final android.view.GestureDetector selectionGestures=new android.view.GestureDetector(getContext(),new android.view.GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public void onLongPress(MotionEvent e){float x=e.getX(),y=e.getY();loadGlyphs(()->selectAt(x,y));}});
    void resetDocument(){tileSerial++;detailDocument++;detailTile=null;requestedMatrix=null;activity.main.removeCallbacks(tileRequest);detailScheduled=false;glyphs=null;glyphLoading=false;speechState=null;if(selectionMode!=null)selectionMode.finish();selectionStart=selectionEnd=-1;}
    @Override void apply(){
        super.apply();
        if(activity==null)return;
        // Keep the sharp frame while its document-to-screen transform changes.
        // Repeated identical layout/tap callbacks must not replace it with the preview.
        if(requestedMatrix!=null&&requestedMatrix.equals(matrix)&&requestedWidth==getWidth()&&requestedHeight==getHeight())return;
        requestedMatrix=new Matrix(matrix);requestedWidth=getWidth();requestedHeight=getHeight();tileSerial++;
        scheduleDetail();invalidate();
    }
    void scheduleDetail(){
        if(activity.dead||detailRendering||detailScheduled)return;
        detailScheduled=true;activity.main.postDelayed(tileRequest,48);
    }
    void renderDetail(){
        if(activity.dead||detailRendering||getWidth()<1||getHeight()<1||getDrawable()==null||!(getTag() instanceof Integer))return;
        final int serial=tileSerial,generation=detailDocument,part=(Integer)getTag();
        final int w=getWidth(),h=getHeight(),dw=getDrawable().getIntrinsicWidth(),dh=getDrawable().getIntrinsicHeight();
        final Matrix frozen=new Matrix(matrix);Runtime rt=Runtime.getRuntime();
        long reserve=rt.maxMemory()-(rt.totalMemory()-rt.freeMemory());
        int budget=reserve>24*1048576L?900000:350000;
        final float ratio=Math.min(1f,(float)Math.sqrt((double)budget/Math.max(1L,(long)w*h)));
        // Spend only spare pixels on a border; never reduce viewport resolution to add it.
        double area=budget/((double)ratio*ratio);
        int spare=(int)Math.floor((Math.sqrt((double)(w-h)*(w-h)+4*area)-w-h)/4);
        final int border=Math.max(0,Math.min(Math.min(activity.dp(96),Math.min(w,h)/3),spare));
        final int bw=Math.max(1,(int)Math.floor((w+2L*border)*ratio)),bh=Math.max(1,(int)Math.floor((h+2L*border)*ratio));
        final Matrix pageToTile=new Matrix(frozen);pageToTile.postTranslate(border,border);pageToTile.postScale(ratio,ratio);
        final Matrix tileToPage=new Matrix();if(!pageToTile.invert(tileToPage))return;
        detailRendering=true;
        activity.worker.execute(()->{
            Bitmap rendered=null;
            try(android.graphics.pdf.PdfRenderer.Page page=activity.pdf.openPage(part)){
                rendered=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);rendered.eraseColor(Color.WHITE);
                Matrix render=new Matrix(pageToTile);render.preScale((float)dw/page.getWidth(),(float)dh/page.getHeight());
                page.render(rendered,null,render,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }catch(Exception|OutOfMemoryError error){rendered=null;AppDiagnostics.record(activity,"pdf-detail",error);}
            final Bitmap completed=rendered;
            activity.ui(()->{
                detailRendering=false;
                // Even if the finger moved during rendering, this frame is usable:
                // its frozen transform lets us place it on the current page exactly.
                if(completed!=null&&generation==detailDocument&&Integer.valueOf(part).equals(getTag())&&getDrawable()!=null&&dw==getDrawable().getIntrinsicWidth()&&dh==getDrawable().getIntrinsicHeight()){
                    detailToPage.set(tileToPage);detailTile=completed;invalidate();
                }
                if(serial!=tileSerial)scheduleDetail();
            });
        });
    }
    void drawDetail(Canvas canvas){
        if(detailTile==null||getDrawable()==null)return;
        canvas.save();RectF bounds=new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());matrix.mapRect(bounds);canvas.clipRect(bounds);
        Matrix placement=new Matrix();placement.setConcat(matrix,detailToPage);
        overlayPaint.setColor(Color.WHITE);overlayPaint.setColorFilter(getColorFilter());
        canvas.drawBitmap(detailTile,placement,overlayPaint);overlayPaint.setColorFilter(null);canvas.restore();
    }
    void loadGlyphs(Runnable ready){if(glyphs!=null){ready.run();return;}if(glyphLoading||!(getTag() instanceof Integer))return;glyphLoading=true;final int part=(Integer)getTag();activity.worker.execute(()->{try{PdfGlyphs result=activity.tools.pdfMapped(part);activity.ui(()->{if(!Integer.valueOf(part).equals(getTag()))return;glyphLoading=false;glyphs=result;ready.run();invalidate();});}catch(Exception|OutOfMemoryError error){activity.ui(()->{glyphLoading=false;android.widget.Toast.makeText(activity,AppLanguage.text(activity,"O PDF não disponibilizou texto seleccionável nesta página."),android.widget.Toast.LENGTH_LONG).show();});AppDiagnostics.record(activity,"pdf-text-geometry",error);}});}
    int character(float x,float y){if(glyphs==null||getDrawable()==null)return -1;Matrix inverse=new Matrix();if(!matrix.invert(inverse))return -1;float[] p={x,y};inverse.mapPoints(p);return glyphs.at(p[0]*glyphs.width/getDrawable().getIntrinsicWidth(),p[1]*glyphs.height/getDrawable().getIntrinsicHeight());}
    void selectAt(float x,float y){int at=character(x,y);if(at<0){android.widget.Toast.makeText(activity,AppLanguage.text(activity,"Esta região não tem texto seleccionável. Use Texto ou OCR para páginas digitalizadas."),android.widget.Toast.LENGTH_LONG).show();return;}int[] range=glyphs.word(at);selectionStart=range[0];selectionEnd=range[1];selectionAnchor=selectionStart;if(selectionMode==null)selectionMode=startActionMode(new android.view.ActionMode.Callback2(){public boolean onCreateActionMode(android.view.ActionMode m,android.view.Menu menu){menu.add(0,1,0,AppLanguage.text(activity,"Copiar texto"));menu.add(0,2,1,AppLanguage.text(activity,"Seleccionar tudo"));menu.add(0,3,2,AppLanguage.text(activity,"Dicionário e tradução"));return true;}public boolean onPrepareActionMode(android.view.ActionMode m,android.view.Menu menu){return false;}public boolean onActionItemClicked(android.view.ActionMode m,android.view.MenuItem item){if(item.getItemId()==3){int from=Math.max(0,Math.min(selectionStart,selectionEnd)),to=Math.min(glyphs.text.length(),Math.max(selectionStart,selectionEnd));if(to>from)WordLookup.show(activity,glyphs.text.substring(from,to));m.finish();return true;}if(item.getItemId()==2){selectionStart=0;selectionEnd=glyphs.text.length();m.invalidateContentRect();invalidate();return true;}if(item.getItemId()==1){((android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(activity.name,glyphs.text.substring(selectionStart,selectionEnd)));m.finish();return true;}return false;}public void onDestroyActionMode(android.view.ActionMode mode){selectionMode=null;selectionStart=selectionEnd=-1;invalidate();}public void onGetContentRect(android.view.ActionMode m,android.view.View v,Rect out){RectF box=selectionBounds();out.set(Math.max(0,(int)box.left),Math.max(0,(int)box.top),Math.min(getWidth(),(int)box.right),Math.min(getHeight(),(int)box.bottom));}},android.view.ActionMode.TYPE_FLOATING);getParent().requestDisallowInterceptTouchEvent(true);invalidate();}
    RectF box(PdfGlyphs.Glyph g){RectF r=new RectF(g.left*getDrawable().getIntrinsicWidth()/glyphs.width,g.top*getDrawable().getIntrinsicHeight()/glyphs.height,g.right*getDrawable().getIntrinsicWidth()/glyphs.width,g.bottom*getDrawable().getIntrinsicHeight()/glyphs.height);matrix.mapRect(r);return r;}
    RectF selectionBounds(){RectF bounds=new RectF();if(glyphs!=null&&getDrawable()!=null)for(PdfGlyphs.Glyph g:glyphs.glyphs)if(g.end>selectionStart&&g.start<selectionEnd)bounds.union(box(g));return bounds;}
    void speech(SpeechEvents.State state,boolean follow){speechState=state;speechFollow=follow;if(state.active&&Integer.valueOf(state.part).equals(getTag()))loadGlyphs(this::invalidate);invalidate();}
    void drawTextOverlays(Canvas canvas){if(getDrawable()==null)return;drawDetail(canvas);if(glyphs==null)return;
        int start=selectionStart,end=selectionEnd;boolean speaking=start<0&&speechState!=null&&speechState.active&&!speechState.loading&&Integer.valueOf(speechState.part).equals(getTag());if(speaking){start=speechState.offset;end=speechState.end>start?speechState.end:WebSpeechHighlight.wordEnd(glyphs.text,start);}
        if(start<0||end<=start)return;overlayPaint.setColor(speaking?0x80f5bc31:0x80599df2);for(PdfGlyphs.Glyph g:glyphs.glyphs)if(g.end>start&&g.start<end)canvas.drawRoundRect(box(g),2,2,overlayPaint);
    }
}
