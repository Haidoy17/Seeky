package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.pdf.PdfRenderer;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Local, offline viewers. Unsupported codecs/legacy formats retain an external-open option. */
public final class ReaderActivity extends Activity {
 final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
 final long workspaceEpoch=WorkspaceEpoch.value;boolean fullScreen=false;int screenWidth,screenHeight;View expandButton;TextView expandText;final String overlayHold="reader-"+java.util.UUID.randomUUID();LinearLayout root,body,controls;TextView status;Uri uri;String name,mime,kind;boolean dead=false,resumed=false;int ink,muted,surface,accent,bg;
 NarrationPreview preview;ReaderExtras extras;OriginalDocument original;ReaderChrome chrome;int readerTheme=0,pdfFit=0,currentPage=0;boolean continuous=false;ReaderTools tools;ReaderPosition positions;boolean plainTruncated=false;int pendingLanding;boolean recentRegistered;
 File temporary;BookArchive book;int chapter=0;float font=18;ScrollView bookScroll;TextView bookText;
 PdfRenderer pdf;ParcelFileDescriptor descriptor;int pages=0;PdfPages pageAdapter;ListView pageList;
 final LruCache<Integer,Bitmap> pageCache=new LruCache<Integer,Bitmap>(ImageBudget.cache(Runtime.getRuntime().maxMemory(),16,6*1024*1024)){protected int sizeOf(Integer i,Bitmap b){return b.getAllocationByteCount();}};final Set<Integer> pendingPages=new HashSet<>();
 android.webkit.WebView svgView;Drawable imageDrawable;
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 TextView text(String s,int sp){TextView t=new TextView(this);t.setText(AppLanguage.text(this,s));t.setTextSize(sp);t.setTextColor(ink);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;}
 TextView button(String s,Runnable action){TextView b=text(s,13);b.setTextColor(accent);b.setContentDescription(s.equals("−")?"Diminuir zoom ou tamanho do texto":s.equals("+")?"Aumentar zoom ou tamanho do texto":s.equals("‹")?"Página ou capítulo anterior":s.equals("›")?"Página ou capítulo seguinte":s);b.setMinHeight(dp(48));b.setGravity(Gravity.CENTER);b.setOnClickListener(v->action.run());Motion.touch(b);return b;}
 void ui(Runnable task){main.post(()->{if(!dead&&workspaceEpoch==WorkspaceEpoch.value)task.run();});}
 protected void attachBaseContext(android.content.Context c){super.attachBaseContext(AppLanguage.wrap(c));}
 ReadingProfiles profiles;boolean searchOpened;int scrollRestoreSerial;
 public void onCreate(Bundle state) {
  super.onCreate(state);
  AppDiagnostics.readerStage(this,"create");
  pageCache.resize(PerformanceMode.pdfCache(this));
  positions=new ReaderPosition(getSharedPreferences("seeky",0),main);
  AppPalette palette=AppPalette.of(this);bg=palette.bg;surface=palette.surface;ink=palette.ink;muted=palette.muted;accent=palette.accent;
  try {
   uri=getIntent().getData();
   if(uri==null||(!"file".equals(uri.getScheme())&&!"content".equals(uri.getScheme())))throw new IOException("Localização inválida.");
   if("file".equals(uri.getScheme())&&!StorageAccess.allowed(this,new File(uri.getPath())))throw new IOException("Ficheiro sem acesso autorizado.");
   name=getIntent().getStringExtra("name");if(name==null)name="Ficheiro";
   mime=MediaFormats.mime(name,getIntent().getType());kind=MediaFormats.kind(name,mime);if(BookQuery.accepts(name,mime)){profiles=new ReadingProfiles(this);profiles.resume();}
   chapter=state==null?getSharedPreferences("seeky",0).getInt("reader_chapter_"+uri,0):state.getInt("chapter",0);
   font=Math.max(12,Math.min(34,getSharedPreferences("seeky",0).getFloat("reader_font",18)));
   if(getIntent().hasExtra("study_segment"))chapter=Math.max(0,getIntent().getIntExtra("study_segment",0));
   readerTheme=ReaderAppearance.theme(getSharedPreferences("seeky",0).getInt("reader_paper",0));
   pdfFit=Math.max(0,Math.min(2,getSharedPreferences("seeky",0).getInt("reader_fit",0)));
   continuous=getSharedPreferences("seeky",0).getBoolean("reader_continuous",false);
   screenWidth=getResources().getDisplayMetrics().widthPixels;screenHeight=getResources().getDisplayMetrics().heightPixels;
   fullScreen=state==null?getIntent().getBooleanExtra("full_screen",true):state.getBoolean("full_screen",true);
   boolean document=kind.equals("pdf")||kind.equals("epub")||kind.equals("docx")||kind.equals("text");
   if(document)tools=new ReaderTools(this);
   if(kind.equals("epub")||kind.equals("docx"))original=new OriginalDocument(this);
   extras=new ReaderExtras(this);chrome=new ReaderChrome(this);chrome.install();UiSpacing.ignore(body);
   if(tools!=null)tools.toolbar();else controls.setVisibility(View.VISIBLE);
   setContentView(root);applyReaderWindow();chrome.brightness();chrome.keepAwake();chrome.speech.export.restore(state);chrome.speech.narration.restore(state);extras.restore(state);
   if(kind.equals("audio")||kind.equals("video")){startActivity(new Intent(this,MediaActivity.class).setDataAndType(uri,mime).putExtra("name",name));finish();return;}
   if(kind.equals("image"))image();else if(kind.equals("pdf"))pdf();else if(kind.equals("epub")||kind.equals("docx"))book();else if(kind.equals("text"))plain();else failed(new IOException("Este formato necessita de uma aplicação compatível."));
  }catch(Exception | OutOfMemoryError e){new UiDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("Fechar",(d,w)->finish()).show();}
 }
 GradientDrawable shape(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
 View readerIcon(String symbol,String label,Runnable task){FrameLayout view=new FrameLayout(this);Icons icon=new Icons(this,symbol,accent);view.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));view.setContentDescription(label);view.setOnClickListener(v->task.run());Motion.touch(view);return view;}
 void toggleFullScreen(){fullScreen=!fullScreen;applyReaderWindow();if(pageAdapter!=null)pageList.post(()->pageAdapter.notifyDataSetChanged());}
 void applyReaderWindow(){Window window=getWindow();window.setStatusBarColor(surface);window.setNavigationBarColor(surface);window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=fullScreen?0:.30f;params.gravity=Gravity.CENTER;params.width=fullScreen?WindowManager.LayoutParams.MATCH_PARENT:ReaderLayout.width(screenWidth,getResources().getDisplayMetrics().density);params.height=fullScreen?WindowManager.LayoutParams.MATCH_PARENT:ReaderLayout.height(screenHeight,getResources().getDisplayMetrics().density);window.setAttributes(params);GradientDrawable rounded=new GradientDrawable();rounded.setColor(surface);rounded.setCornerRadius(fullScreen?0:dp(24));root.setBackground(rounded);root.setElevation(fullScreen?0:dp(14));if(expandText!=null)expandText.setText(fullScreen?"Reduzir":"Expandir");if(expandButton!=null){expandButton.setContentDescription(fullScreen?"Reduzir para pré-visualização":"Expandir para ecrã inteiro");((Icons)((FrameLayout)expandButton).getChildAt(0)).setSymbol(fullScreen?"collapse":"expand",accent);}
  if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=window.getInsetsController();if(controller!=null){controller.setSystemBarsAppearance(AppPalette.of(this).light?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(fullScreen&&chrome!=null&&chrome.hidden)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());}}else window.getDecorView().setSystemUiVisibility(fullScreen&&chrome!=null&&chrome.hidden?View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION:AppPalette.of(this).light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
 }
 @Override protected void onStart(){super.onStart();UiDialog.visibility(this,true);UiPanel.visibility(this,true);QuickSearchService.holdOverlay(overlayHold,true);}
 @Override protected void onStop(){UiDialog.visibility(this,false);UiPanel.visibility(this,false);QuickSearchService.holdOverlay(overlayHold,false);super.onStop();}
 @Override public void onBackPressed(){if(chrome!=null&&chrome.hidden){chrome.toggleChrome();return;}super.onBackPressed();}
 protected void onResume(){super.onResume();if(workspaceEpoch!=WorkspaceEpoch.value){finish();return;}resumed=true;if(profiles!=null)profiles.resume();rememberReading();if(chrome!=null){chrome.changed();chrome.speech.resume();}if(imageDrawable instanceof Animatable)((Animatable)imageDrawable).start();}
 protected void onPause(){if(profiles!=null)profiles.save();ReadingPlan.remember(this);ReadingWidget.update(this);if(preview!=null)preview.close();if(extras!=null)extras.pause();saveReadingPosition();rememberReading();if(chrome!=null)chrome.speech.pause();if(positions!=null)positions.flush();resumed=false;if(imageDrawable instanceof Animatable)((Animatable)imageDrawable).stop();super.onPause();}
 boolean readingReady(){return uri!=null&&kind!=null&&(kind.equals("pdf")?pages>0&&pageList!=null:kind.equals("epub")||kind.equals("docx")||kind.equals("text")?tools!=null&&tools.draft!=null||original!=null&&original.active&&!original.loading&&!original.failed:false);}
 void rememberReading(){if(workspaceEpoch!=WorkspaceEpoch.value||!readingReady())return;LastReading.remember(this,uri,name,mime,getIntent().getStringExtra("study_title"),getIntent().getStringExtra("study_path"),getIntent().getLongExtra("study_material",0),fullScreen);recentRegistered=true;ReadingWidget.update(this);}
 void saveReadingPosition(){if(workspaceEpoch!=WorkspaceEpoch.value||!readingReady()||positions==null)return;int part=chrome==null?chapter:chrome.current();if(kind.equals("pdf")){positions.put("reader_page_"+uri,part);if(pageList!=null)for(int i=0;i<pageList.getChildCount();i++)if(pageList.getChildAt(i) instanceof ReaderPageView)((ReaderPageView)pageList.getChildAt(i)).savePlacement();}if(original!=null&&original.active&&!original.loading&&original.view!=null)positions.put(original.key(),original.scroll());else if(bookScroll!=null&&bookText!=null)positions.put("reader_scroll_"+uri+":"+part,bookScroll.getScrollY());}
 protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("chapter",chapter);out.putBoolean("full_screen",fullScreen);if(chrome!=null){chrome.speech.export.saveState(out);chrome.speech.narration.save(out);if(extras!=null)extras.save(out);}}
 @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)pageCache.evictAll();}
 @Override public void onLowMemory(){super.onLowMemory();pageCache.evictAll();}
 void failed(Throwable e){pendingLanding=0;AppDiagnostics.record(this,"reader-open",e);ui(()->{status.setText(AppLanguage.text(status.getContext(),"Não foi possível abrir neste leitor."));body.removeAllViews();controls.removeAllViews();TextView message=text((e.getMessage()==null?"Formato indisponível.":e.getMessage())+"\n\nPode usar uma aplicação compatível para este formato ou codec.",15);body.addView(message);body.addView(button("Abrir noutra aplicação",this::external));});}
 void external(){try{Uri shared=uri;if("file".equals(uri.getScheme())){Catalog.Entry e=new Catalog.Entry();e.uri=uri.toString();e.name=name;e.mime=mime;shared=StorageAccess.shareUri(this,e);}Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(shared,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);i.setClipData(ClipData.newRawUri(name,shared));startActivity(Intent.createChooser(i,"Abrir "+name));}catch(Exception e){Toast.makeText(this,"Não existe uma aplicação compatível instalada para este formato.",Toast.LENGTH_LONG).show();}}
 File local()throws IOException {
  File privateBook=PrivateMaterial.file(this,uri);if(privateBook!=null)return privateBook;if(temporary!=null&&temporary.isFile())return temporary;
  if("file".equals(uri.getScheme()))return new File(uri.getPath());
  File target=new File(getCacheDir(),"seeky-reader-"+UUID.randomUUID()+".cache");
  try(InputStream in=getContentResolver().openInputStream(uri)){
   if(in==null)throw new IOException("Ficheiro indisponível.");
   StudyFiles.copy(in,target,DocumentBudget.available(getCacheDir(),name,mime),()->dead);temporary=target;return target;
  }catch(IOException | RuntimeException e){target.delete();throw e;}
 }
 void book(){worker.execute(()->{try{book=new BookArchive(local(),kind.equals("epub"));chapter=Math.max(0,Math.min(chapter,book.chapters.size()-1));ui(()->{bookScroll=new ReaderScrollView(this);bookText=text("",(int)font);bookText.setTextIsSelectable(true);tools.setupText();bookText.setLineSpacing(dp(5),1.15f);bookScroll.addView(bookText);body.addView(bookScroll,new LinearLayout.LayoutParams(-1,-1));controls.addView(button("‹",()->chapter(chapter-1)),new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(button("Capítulos",this::chapters),new LinearLayout.LayoutParams(0,dp(48),2));controls.addView(button("A−",()->font(-2)),new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(button("A+",()->font(2)),new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(button("›",()->chapter(chapter+1)),new LinearLayout.LayoutParams(0,dp(48),1));chapter(chapter);});}catch(Exception e){failed(e);}});}
 void chapter(int index){if(book==null||index<0||index>=book.chapters.size())return;tools.loading=true;chapter=index;final int request=index;status.setText(AppLanguage.text(status.getContext(),"A abrir capítulo…"));worker.execute(()->{if(dead||request!=chapter)return;try{String text=book.chapter(request);ui(()->{if(request!=chapter)return;bookScroll.setOnScrollChangeListener(null);positions.flush();bookText.setText(text);tools.accept(text,request);final int savedY=pendingLanding==0?getSharedPreferences("seeky",0).getInt("reader_scroll_"+uri+":"+request,0):0;ReaderScrollRestore.restore(this,savedY,request);status.setText((book.title.isEmpty()?name:book.title)+(book.author.isEmpty()?"":" · "+book.author)+"\n"+(request+1)+" / "+book.chapters.size()+" · leitura de texto");getSharedPreferences("seeky",0).edit().putInt("reader_chapter_"+uri,request).apply();chrome.applyText();chrome.update();if(original!=null)original.install();});}catch(Exception e){if(original!=null&&getSharedPreferences("seeky",0).getBoolean("reader_original_"+uri,true)){AppDiagnostics.record(this,"book-text-extraction",e);ui(()->{if(request==chapter){tools.loading=false;original.install();}});}else failed(e);}});}
 void landText(){if(original!=null&&original.active)return;if(pendingLanding==0||bookText==null||bookScroll==null)return;final int landing=pendingLanding;pendingLanding=0;final TextView text=bookText;final ScrollView scroll=bookScroll;text.post(()->{if(dead||text!=bookText||scroll!=bookScroll)return;scroll.scrollTo(0,landing<0?Integer.MAX_VALUE:0);scroll.setAlpha(.75f);scroll.animate().alpha(1f).setDuration(120).start();chrome.update();});}
 void chapters(){if(book==null)return;new UiDialog.Builder(this).setTitle("Índice do livro").setSingleChoiceItems(book.names.toArray(new String[0]),chapter,(d,n)->{d.dismiss();chapter(n);}).setNegativeButton("Fechar",null).show();}
 void font(int delta){font=Math.max(12,Math.min(34,font+delta));if(chrome!=null)chrome.applyText();getSharedPreferences("seeky",0).edit().putFloat("reader_font",font).apply();}
 void plain(){worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Ficheiro indisponível.");byte[] plainBytes=ContentText.read(in,ContentText.LIMIT+1);plainTruncated=plainBytes.length>ContentText.LIMIT;String value=ContentText.extract(new ByteArrayInputStream(plainBytes),name);ui(()->{bookScroll=new ReaderScrollView(this);bookText=text("",(int)font);bookText.setText(value);bookText.setTextIsSelectable(true);tools.setupText();tools.accept(value,0);bookScroll.addView(bookText);body.addView(bookScroll,new LinearLayout.LayoutParams(-1,-1));ReaderScrollRestore.restore(this,getSharedPreferences("seeky",0).getInt("reader_scroll_"+uri+":0",0),0);status.setText(AppLanguage.text(status.getContext(),plainTruncated?"Texto · pré-visualização parcial":"Texto"));chrome.applyText();chrome.update();controls.addView(button("A−",()->font(-2)));controls.addView(button("A+",()->font(2)));});}catch(Exception e){failed(e);}});}
 /** Lado máximo da imagem descodificada, conforme a memória do telefone (nítida sem esgotar a RAM). */
 int imageCap(){android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);int mb=am==null?128:am.getMemoryClass();boolean low=am!=null&&am.isLowRamDevice();return low||mb<128?1600:mb<256?2560:3584;}
 long imageBytes(){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.SIZE},null,null,null)){int col=c==null?-1:c.getColumnIndex(android.provider.OpenableColumns.SIZE);if(c!=null&&col>=0&&c.moveToFirst()&&!c.isNull(col))return c.getLong(col);}catch(Exception ignored){}try{if("file".equals(uri.getScheme()))return new File(uri.getPath()).length();}catch(Exception ignored){}return -1;}
 String imageDetails(int w,int h,long bytes){StringBuilder b=new StringBuilder(MediaFormats.ext(name).toUpperCase(java.util.Locale.ROOT));if(w>0&&h>0)b.append(" · ").append(w).append(" × ").append(h).append(" px");if(w>0&&h>0&&(long)w*h>=1000000)b.append(" · ").append(String.format(java.util.Locale.ROOT,"%.1f MP",w*(double)h/1e6));if(bytes>0)b.append(" · ").append(android.text.format.Formatter.formatShortFileSize(this,bytes));return b.toString();}
 void image(){if(MediaFormats.ext(name).equals("svg")){svg();return;}worker.execute(()->{try{
   final int cap=imageCap();final int[] size={0,0};
   Drawable drawable;
   if(Build.VERSION.SDK_INT>=28){ImageDecoder.Source source=ImageDecoder.createSource(getContentResolver(),uri);drawable=ImageDecoder.decodeDrawable(source,(decoder,info,src)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();size[0]=w;size[1]=h;float factor=Math.min(1,(float)cap/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*factor)),Math.max(1,(int)(h*factor)));});}
   else{BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}size[0]=options.outWidth;size[1]=options.outHeight;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>cap||options.outHeight/options.inSampleSize>cap)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new IOException("Imagem não suportada pelo descodificador do aparelho.");drawable=new BitmapDrawable(getResources(),bitmap);}
   imageDrawable=drawable;final long bytes=imageBytes();ui(()->{
    /* Visualizador de imagens: fundo escuro, imagem em alta resolução e controlos com ícones. */
    body.setBackgroundColor(0xff0b0d12);ZoomImageView image=new ZoomImageView(this);image.setImageDrawable(drawable);body.addView(image,new LinearLayout.LayoutParams(-1,-1));image.post(image::fit);if(drawable instanceof Animatable)((Animatable)drawable).start();
    String details=imageDetails(size[0],size[1],bytes);status.setText(details);
    controls.addView(readerIcon("minus","Diminuir zoom",()->image.zoom(.8f)),new LinearLayout.LayoutParams(0,dp(52),1));
    controls.addView(readerIcon("fit","Ajustar ao ecrã",image::fit),new LinearLayout.LayoutParams(0,dp(52),1));
    controls.addView(readerIcon("add","Aumentar zoom",()->image.zoom(1.25f)),new LinearLayout.LayoutParams(0,dp(52),1));
    controls.addView(readerIcon("rotate","Rodar",image::rotate),new LinearLayout.LayoutParams(0,dp(52),1));
    controls.addView(readerIcon("info","Informação da imagem",()->new UiDialog.Builder(this).setTitle(name).setMessage(details+"\n"+AppLanguage.text(this,"Toque duplo para ampliar; dois dedos para zoom; arraste para mover.")).setPositiveButton("Fechar",null).show()),new LinearLayout.LayoutParams(0,dp(52),1));
    controls.addView(readerIcon("share","Abrir ou partilhar",this::external),new LinearLayout.LayoutParams(0,dp(52),1));});
  }catch(Exception|OutOfMemoryError e){failed(new IOException("Não foi possível descodificar esta imagem. Formatos profissionais e RAW podem exigir outra aplicação.",e));}});}
 void svg(){worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("SVG indisponível.");byte[] bytes=ContentText.read(in,2097153);if(bytes.length>2097152)throw new IOException("Limite de 2 MB para SVG.");String encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);ui(()->{try{svgView=new android.webkit.WebView(this);android.webkit.WebSettings settings=svgView.getSettings();settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setDomStorageEnabled(false);settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);svgView.setWebViewClient(new android.webkit.WebViewClient(){public boolean shouldOverrideUrlLoading(android.webkit.WebView view,android.webkit.WebResourceRequest request){return true;}public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest request){String scheme=request.getUrl().getScheme();if(!"data".equals(scheme)&&!"about".equals(scheme))return new android.webkit.WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}});body.addView(svgView,new LinearLayout.LayoutParams(-1,-1));svgView.loadDataWithBaseURL("https://seeky.invalid/","<html><meta name='viewport' content='width=device-width, initial-scale=1'><body style='margin:0;background:#fff'><img style='width:100%;height:auto' src='data:image/svg+xml;base64,"+encoded+"'></body></html>","text/html","UTF-8",null);status.setText(AppLanguage.text(status.getContext(),"SVG"));}catch(Exception ex){failed(ex);}});}catch(Exception e){failed(e);}});}
 void pdf(){
  worker.execute(()->{
   try {
    AppDiagnostics.readerStage(this,"pdf-open");
    try{descriptor=getContentResolver().openFileDescriptor(uri,"r");if(descriptor==null)throw new IOException("PDF indisponível.");pdf=new PdfRenderer(descriptor);}
    catch(Exception seekError){if(descriptor!=null)descriptor.close();descriptor=ParcelFileDescriptor.open(local(),ParcelFileDescriptor.MODE_READ_ONLY);pdf=new PdfRenderer(descriptor);}
    pages=pdf.getPageCount();if(pages<1)throw new IOException("PDF sem páginas.");AppDiagnostics.readerStage(this,"pdf-ready");
    ui(()->{
     pageList=new ListView(this);pageList.setDivider(null);pageList.setDividerHeight(dp(12));pageList.setClipToPadding(false);pageList.setPadding(dp(8),dp(8),dp(8),dp(8));pageList.setSelector(new ColorDrawable(Color.TRANSPARENT));
     int saved=getIntent().hasExtra("study_segment")?getIntent().getIntExtra("study_segment",0):getSharedPreferences("seeky",0).getInt("reader_page_"+uri,0);
     currentPage=Math.max(0,Math.min(saved,pages-1));pageAdapter=new PdfPages();pageList.setAdapter(pageAdapter);body.addView(pageList,new LinearLayout.LayoutParams(-1,-1));
     pageList.setSelectionFromTop(continuous?currentPage:0,continuous&&!getIntent().hasExtra("study_segment")?getSharedPreferences("seeky",0).getInt("reader_offset_"+uri,0):0);
     pageList.setOnScrollListener(new AbsListView.OnScrollListener(){
      public void onScrollStateChanged(AbsListView view,int state){if(state==SCROLL_STATE_IDLE&&pageAdapter!=null)pageAdapter.notifyDataSetChanged();}
      public void onScroll(AbsListView view,int first,int count,int total){if(count<=0)return;if(continuous)currentPage=first;status.setText(AppLanguage.text(ReaderActivity.this,"Página ")+(currentPage+1)+" / "+pages);positions.put("reader_page_"+uri,currentPage);positions.put("reader_offset_"+uri,continuous&&view.getChildAt(0)!=null?view.getChildAt(0).getTop():0);chrome.update();}
     });
     pageList.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot||r-l!=or-ol)pageList.post(()->{if(!dead)pageAdapter.notifyDataSetChanged();});});
     chrome.update();status.setText(AppLanguage.text(this,"Página ")+(currentPage+1)+" / "+pages);
     if(getSharedPreferences("seeky",0).getBoolean("reader_text_mode_"+uri,false))tools.pageText(currentPage);
    });
   }catch(Exception | OutOfMemoryError e){failed(new IOException("PDF protegido, danificado ou sem acesso de leitura.",e));}
  });
 }
 void setPage(int page){
  if(page<0||page>=pages||pageList==null)return;currentPage=page;
  if(continuous)pageList.setSelection(page);else{pageAdapter.notifyDataSetChanged();pageList.setSelection(0);}
  positions.put("reader_page_"+uri,page);positions.put("reader_offset_"+uri,0);
  status.setText(AppLanguage.text(this,"Página ")+(page+1)+" / "+pages);chrome.update();
 }
 void jump(){EditText number=new EditText(this);number.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);number.setHint("1 – "+pages);new UiDialog.Builder(this).setTitle("Ir para página").setView(number).setPositiveButton("Abrir",(d,w)->{try{int page=Integer.parseInt(number.getText().toString())-1;if(page<0||page>=pages)throw new IllegalArgumentException();chrome.go(page);}catch(Exception ignored){Toast.makeText(this,AppLanguage.text(this,"Página inválida."),Toast.LENGTH_SHORT).show();}}).setNegativeButton("Cancelar",null).show();}
 class PdfPages extends BaseAdapter {
  public int getCount(){return continuous?pages:1;}public Object getItem(int position){return continuous?position:currentPage;}public long getItemId(int position){return (Integer)getItem(position);}
  public View getView(int index,View recycled,ViewGroup parent){
   final int position=continuous?index:currentPage;
   ReaderPageView image=recycled instanceof ReaderPageView?(ReaderPageView)recycled:new ReaderPageView(ReaderActivity.this);
   boolean samePage=Integer.valueOf(position).equals(image.getTag());boolean changed=!samePage||image.lastFit!=pdfFit;if(!samePage)image.resetDocument();image.lastFit=pdfFit;image.setTag(position);image.appearance();
   Bitmap cached=pageCache.get(position);if(cached==null&&samePage&&image.getDrawable() instanceof BitmapDrawable)cached=((BitmapDrawable)image.getDrawable()).getBitmap();image.setImageBitmap(cached);image.setLayoutParams(new AbsListView.LayoutParams(-1,height(cached)));if(changed||Math.abs(image.scale-image.base)<.01f)image.post(()->{image.fit();image.restorePlacement();});
   if(cached==null&&!pendingPages.contains(position)&&pendingPages.size()<2){
    pendingPages.add(position);worker.execute(()->{
     if(dead)return;
     try(PdfRenderer.Page page=pdf.openPage(position)){
      int[] size=ImageBudget.fit(page.getWidth(),page.getHeight(),1080,600000);Bitmap rendered=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);rendered.eraseColor(Color.WHITE);page.render(rendered,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);pageCache.put(position,rendered);
      ui(()->{pendingPages.remove(position);if(Integer.valueOf(position).equals(image.getTag())){if(position==currentPage)AppDiagnostics.readerStage(ReaderActivity.this,"pdf-rendered");image.setImageBitmap(rendered);image.setLayoutParams(new AbsListView.LayoutParams(-1,height(rendered)));image.post(()->{image.fit();image.restorePlacement();});}for(int n=0;n<pageList.getChildCount();n++){View visible=pageList.getChildAt(n);if(visible instanceof ReaderPageView&&((ReaderPageView)visible).getDrawable()==null&&visible.getTag() instanceof Integer){int part=(Integer)visible.getTag();pageAdapter.getView(continuous?part:0,visible,pageList);}}});
     }catch(Exception | OutOfMemoryError e){ui(()->{pendingPages.remove(position);status.setText(AppLanguage.text(ReaderActivity.this,"Não foi possível renderizar esta página."));});}
    });
   }
   return image;
  }
  int height(Bitmap bitmap){
   if(!continuous)return Math.max(dp(100),body.getHeight()>0?body.getHeight()-dp(16):screenHeight-dp(240));
   int width=Math.max(1,pageList!=null&&pageList.getWidth()>0?pageList.getWidth()-dp(16):screenWidth-dp(24));
   return Math.min(dp(2000),Math.max(dp(120),(int)(width*(bitmap==null?1.414:(double)bitmap.getHeight()/Math.max(1,bitmap.getWidth())))));
  }
 }
 static String clock(int millis){int seconds=Math.max(0,millis/1000);return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(extras!=null)extras.result(request,result,data);if(tools!=null)tools.result(request,result,data);if(chrome!=null){chrome.speech.export.result(request,result,data);chrome.speech.narration.result(request,result,data);chrome.speech.background.result(request,result,data);}}
 public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(extras!=null)extras.notes.permission(request,results);}
 protected void onDestroy(){if(preview!=null)preview.close();if(extras!=null)extras.close();saveReadingPosition();if(positions!=null)positions.flush();final long studyId=getIntent().getLongExtra("study_material",0);if(workspaceEpoch==WorkspaceEpoch.value&&studyId>0&&uri!=null&&kind!=null){final int current=kind.equals("pdf")?(tools!=null&&tools.pdfText?tools.segment:currentPage):chapter;final int total=kind.equals("pdf")?pages:book!=null?book.chapters.size():1;final boolean originalMode=original!=null&&original.active;final boolean textMode=originalMode||bookScroll!=null&&bookScroll.getChildCount()>0;final int unit=originalMode?ReadingProgress.units(current,total,original.scroll(),original.height(),original.viewport()):textMode?ReadingProgress.units(current,total,bookScroll.getScrollY(),bookScroll.getChildAt(0).getHeight(),bookScroll.getHeight()):current;final int scale=textMode?(int)Math.min(Integer.MAX_VALUE,(long)total*1000):total;worker.execute(()->{try(StudyStore study=new StudyStore(getApplicationContext())){if(StudyReading.advance(study,studyId,unit,scale))StudyEvents.changed();}catch(Exception ignored){}});}UiDialog.dismissAll(this);UiPanel.dismissAll(this);if(original!=null)original.close();if(chrome!=null)chrome.close();dead=true;QuickSearchService.holdOverlay(overlayHold,false);if(root!=null)root.animate().cancel();main.removeCallbacksAndMessages(null);if(svgView!=null){svgView.stopLoading();svgView.destroy();svgView=null;}if(imageDrawable instanceof Animatable)((Animatable)imageDrawable).stop();worker.execute(()->{if(tools!=null)tools.close();CloudJobs.schedule(this);try{if(book!=null)book.close();}catch(Exception ignored){}try{if(pdf!=null)pdf.close();}catch(Exception ignored){}try{if(descriptor!=null)descriptor.close();}catch(Exception ignored){}if(temporary!=null)temporary.delete();pageCache.evictAll();});worker.shutdown();super.onDestroy();}
}
