package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** User-enabled local file monitor and floating search, with visible stop controls. */
public final class QuickSearchService extends Service {
    static final String STOP="mz.bam.seeky.android.STOP_FLOAT";
    static final String SHOW="mz.bam.seeky.android.SHOW_FLOAT",MONITOR="mz.bam.seeky.android.MONITOR",REFRESH="mz.bam.seeky.android.REFRESH",STOP_ALL="mz.bam.seeky.android.STOP_ALL",EXPAND="mz.bam.seeky.android.EXPAND_FLOAT";
    static volatile boolean running=false,floating=false,monitoring=false;static volatile QuickSearchService instance;
    FileTracker tracker;SharedPreferences prefs;final OverlayRecovery recovery=new OverlayRecovery();
    static void visibleFolder(File folder){QuickSearchService service=instance;if(service!=null&&service.tracker!=null)service.tracker.folderVisible(folder);}
    static final Set<String> overlayOwners=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static void holdOverlay(String token,boolean hold){if(hold)overlayOwners.add(token);else overlayOwners.remove(token);QuickSearchService service=instance;if(service!=null)service.handler.post(service::syncOverlayHold);}
    static boolean overlayHeld(){return !overlayOwners.isEmpty();}
    void syncOverlayHold(){if(dead)return;if(overlayHeld()){serial++;if(signal!=null)signal.cancel();searches.cancel();if(debounce!=null)handler.removeCallbacks(debounce);expanded=false;remove();updateNotification();}else handler.postDelayed(()->{if(!dead&&!overlayHeld()&&window==null&&prefs.getBoolean("float_enabled",false)&&Settings.canDrawOverlays(this))try{bubble();}catch(Exception e){overlayProblem(e);}},100);}
    static final int NOTIFICATION=81;
    final Handler handler=new Handler(Looper.getMainLooper());
    final LatestWork searches=new LatestWork();android.animation.ValueAnimator snap;boolean transitioning=false;String lastPublished="";
    WindowManager wm;WindowManager.LayoutParams params;View window;Catalog db;
    boolean tucked=false,draggingBubble=false;final Runnable tuckIdle=this::tuck;
    boolean expanded=false,dead=false;int serial=0;CancellationSignal signal;
    FlashPanelView panelView;TextView status;LinearLayout results;EditText input;Runnable debounce;
    int ink,purple,sub,panelSurface,fieldSurface;boolean directMode=false;String quickCategory="Tudo";
    void colors(){AppPalette p=AppPalette.of(this);ink=p.ink;purple=p.accent;sub=p.muted;panelSurface=p.surface;fieldSurface=p.tint;}
    final SharedPreferences.OnSharedPreferenceChangeListener themeListener=(preferences,key)->{if(key==null||"theme".equals(key)||key.startsWith("theme_custom_")||key.startsWith("flash_")||"icon_size".equals(key))handler.post(()->{if(dead||window==null)return;try{if("icon_size".equals(key)&&expanded){for(int i=0;i<results.getChildCount();i++)if(results.getChildAt(i) instanceof FileRowView)((FileRowView)results.getChildAt(i)).resizeIcons();return;}if(key!=null&&key.startsWith("flash_")&&expanded)return;if(expanded){String query=input==null?"":input.getText().toString();panel();input.setText(query);input.setSelection(input.length());}else bubble();}catch(Exception e){overlayProblem(e);}});};
    final CatalogEvents.Listener catalogListener=message->{if(!dead&&expanded&&input!=null&&!directMode){handler.removeCallbacks(this.refreshPanel);handler.postDelayed(this.refreshPanel,450);}};
    final Runnable refreshPanel=()->{if(!dead&&expanded&&input!=null&&!directMode)search(input.getText().toString());};
    final BroadcastReceiver screenReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())&&expanded)try{bubble();}catch(Exception e){overlayProblem(e);}}};

    int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(AppLanguage.text(this,s));t.setTextSize(size);t.setTextColor(color);return t;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    View action(String icon,String description,Runnable task){FrameLayout f=new FrameLayout(this);f.setContentDescription(description);f.setBackground(background(fieldSurface,14));Icons i=new Icons(this,icon,purple);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);f.addView(i,ip);f.setOnClickListener(v->task.run());Motion.touch(f);return f;}
    public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);db=new Catalog(getApplicationContext());prefs=getSharedPreferences("seeky",0);instance=this;colors();prefs.registerOnSharedPreferenceChangeListener(themeListener);CatalogEvents.add(catalogListener);registerReceiver(screenReceiver,new IntentFilter(Intent.ACTION_SCREEN_OFF));}
    @Override public int onStartCommand(Intent i,int flags,int id){
        String action=i==null?MONITOR:i.getAction();
        if(STOP_ALL.equals(action)){prefs.edit().putBoolean("monitor_enabled",false).putBoolean("float_enabled",false).apply();stopSelf();return START_NOT_STICKY;}
        if(SHOW.equals(action)){prefs.edit().putBoolean("float_enabled",true).apply();FlashTileService.refresh(this);recovery.succeeded();}
        if(STOP.equals(action)){prefs.edit().putBoolean("float_enabled",false).apply();FlashTileService.refresh(this);serial++;if(signal!=null)signal.cancel();searches.cancel();expanded=false;remove();}
        boolean watch=prefs.getBoolean("monitor_enabled",true)&&StorageAccess.all(this);
        boolean wantedOverlay=prefs.getBoolean("float_enabled",false)&&Settings.canDrawOverlays(this);boolean overlay=wantedOverlay&&!overlayHeld();
        if(!watch&&!wantedOverlay){stopSelf();return START_NOT_STICKY;}
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("seeky_float","Detecção e pesquisa Seeky",NotificationManager.IMPORTANCE_LOW));
            Notification notification=notification();if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(NOTIFICATION,notification);running=true;prefs.edit().remove("service_warning").apply();
            if(watch&&tracker==null){tracker=new FileTracker(getApplicationContext(),db);tracker.start();monitoring=true;}
            if(!watch&&tracker!=null){tracker.close();tracker=null;monitoring=false;}
            if(REFRESH.equals(action)&&tracker!=null)tracker.refresh();
            if(overlay&&window==null)try{bubble();}catch(Exception e){overlayProblem(e);}
            if(EXPAND.equals(action)&&overlay&&window!=null&&!expanded)expand();
            if(!overlay&&window!=null)remove();
            updateNotification();handler.removeCallbacks(permissionCheck);handler.postDelayed(permissionCheck,15000);
        }catch(Exception e){prefs.edit().putString("service_warning",e.toString()).apply();Toast.makeText(this,"O Android impediu a activação do Seeky: "+e.getMessage(),Toast.LENGTH_LONG).show();stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    Notification notification(){
        PendingIntent home=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent show=PendingIntent.getForegroundService(this,2,new Intent(this,QuickSearchService.class).setAction(floating?STOP:SHOW),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,QuickSearchService.class).setAction(STOP_ALL),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"seeky_float").setSmallIcon(R.drawable.ic_seeky_notification).setContentTitle("Seeky Flash").setContentText(AppLanguage.text(this,floating?"Pesquisa flutuante":"Mostrar bolha")).setContentIntent(home).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).addAction(new Notification.Action.Builder(null,floating?"Ocultar bolha":"Mostrar bolha",show).build()).addAction(new Notification.Action.Builder(null,"Parar Seeky",stop).build()).build();
    }
    void updateNotification(){if(!dead&&running)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification());}
    final Runnable permissionCheck=new Runnable(){public void run(){
        if(dead)return;
        if(!Settings.canDrawOverlays(QuickSearchService.this)&&window!=null){prefs.edit().putBoolean("float_enabled",false).apply();remove();updateNotification();}
        if(!StorageAccess.all(QuickSearchService.this)&&tracker!=null){tracker.close();tracker=null;monitoring=false;CatalogEvents.publish("Detecção pausada: falta acesso geral");}
        if(window!=null&&!transitioning&&!window.isAttachedToWindow()){remove();}
        if(window==null&&!overlayHeld()&&prefs.getBoolean("float_enabled",false)&&Settings.canDrawOverlays(QuickSearchService.this)&&recovery.due(SystemClock.elapsedRealtime()))try{bubble();}catch(Exception e){overlayProblem(e);}
        if(tracker==null&&window==null&&!prefs.getBoolean("float_enabled",false)){stopSelf();return;}handler.postDelayed(this,15000);
    }};
    void overlayProblem(Exception error){prefs.edit().putString("overlay_warning",error.toString()).apply();remove();recovery.failed(SystemClock.elapsedRealtime());if(error instanceof SecurityException){prefs.edit().putBoolean("float_enabled",false).apply();}updateNotification();}
    void remove(){handler.removeCallbacks(tuckIdle);tucked=false;draggingBubble=false;panelView=null;floating=false;if(snap!=null)snap.cancel();if(window!=null){window.animate().cancel();try{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(window.getWindowToken(),0);wm.removeView(window);}catch(Exception ignored){}window=null;}}
    void bubble(){
        if(dead||overlayHeld())return;colors();if(debounce!=null)handler.removeCallbacks(debounce);if(signal!=null)signal.cancel();searches.cancel();serial++;transitioning=false;expanded=false;remove();
        final FlashBubbleView bubble=new FlashBubbleView(this);
        params=new WindowManager.LayoutParams(dp(IconSizing.bubbleTouch(IconSizing.bubble(this))),dp(IconSizing.bubbleTouch(IconSizing.bubble(this))),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        params.alpha=FlashSettings.opacity(this)/100f;params.gravity=Gravity.TOP|Gravity.START;SharedPreferences p=getSharedPreferences("seeky",0);params.x=p.getInt("float_x",getResources().getDisplayMetrics().widthPixels-params.width-dp(8));params.y=p.getInt("float_y",dp(150));clamp();
        bubble.setOnClickListener(v->expand());
        bubble.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;boolean moved,held;VelocityTracker velocity;
/* Toque longo sem mover: abre os ajustes do botão Flash na aplicação. */
final Runnable longPress=()->{if(dead||moved||!draggingBubble)return;held=true;bubble.press(false);bubble.longPress();openFlashSettings();};
void release(){handler.removeCallbacks(longPress);if(velocity!=null){velocity.recycle();velocity=null;}}
public boolean onTouch(View v,android.view.MotionEvent e){if(velocity!=null)velocity.addMovement(e);switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:handler.removeCallbacks(tuckIdle);draggingBubble=true;untuck();v.setPressed(true);bubble.press(true);if(snap!=null)snap.cancel();x=e.getRawX();y=e.getRawY();px=params.x;py=params.y;moved=false;held=false;release();velocity=VelocityTracker.obtain();velocity.addMovement(e);handler.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());return true;
case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-x,dy=e.getRawY()-y;if(!held&&!moved&&Math.abs(dx)+Math.abs(dy)>dp(7)){moved=true;handler.removeCallbacks(longPress);v.setPressed(false);bubble.press(false);bubble.lift(true);}if(moved){params.x=px+(int)dx;params.y=py+(int)dy;clamp();safeUpdate();}return true;
case MotionEvent.ACTION_CANCEL:draggingBubble=false;v.setPressed(false);bubble.press(false);bubble.lift(false);release();scheduleTuck();return true;
case MotionEvent.ACTION_UP:draggingBubble=false;v.setPressed(false);bubble.press(false);bubble.lift(false);float vx=0,vy=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);vx=velocity.getXVelocity();vy=velocity.getYVelocity();}release();if(held){scheduleTuck();return true;}if(!moved){bubble.tap();v.performClick();}else{int width=getResources().getDisplayMetrics().widthPixels;float fling=dp(900);
/* Um lançamento rápido escolhe a margem pela direcção; um arrasto lento, pela posição. */
boolean right=Math.abs(vx)>fling?vx>0:params.x+params.width/2>=width/2;if(Math.abs(vy)>fling){params.y+=(int)Math.max(-dp(220),Math.min(dp(220),vy*.12f));clamp();}int target=right?width-params.width-dp(8):dp(8);dock(target,p);bubble.docked();scheduleTuck();}return true;
default:return true;}}});
        window=bubble;wm.addView(window,params);floating=true;recovery.succeeded();prefs.edit().remove("overlay_warning").apply();updateNotification();Motion.enter(window,0);scheduleTuck();
    }
    /** Abre "Ícones e botão Flash" na aplicação principal (sem dados do chamador). */
    void openFlashSettings(){Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(FlashSettings.OPEN,true);try{startActivity(i);}catch(Exception ex){Toast.makeText(this,AppLanguage.text(this,"Abra o Seeky para ajustar o botão Flash."),Toast.LENGTH_SHORT).show();}}
    void scheduleTuck(){handler.removeCallbacks(tuckIdle);if(!dead&&!expanded&&!draggingBubble&&window!=null&&FlashSettings.autoHide(this))handler.postDelayed(tuckIdle,FlashSettings.delay(this)*1000L);}
    void untuck(){if(!tucked)return;tucked=false;if(window instanceof FlashBubbleView)((FlashBubbleView)window).tucked(false,false);if(snap!=null)snap.cancel();int width=getResources().getDisplayMetrics().widthPixels;params.x=params.x<width/2?dp(6):width-params.width-dp(6);clamp();safeUpdate();}
    void tuck(){if(dead||expanded||draggingBubble||window==null||overlayHeld()||!FlashSettings.autoHide(this))return;if(snap!=null)snap.cancel();int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels,corner=Math.max(0,Math.min(4,prefs.getInt("flash_corner",0)));boolean right=corner==2||corner==4||corner==0&&params.x+params.width/2>=width/2;tucked=true;params.x=right?width-params.width/2:-params.width/2;if(window instanceof FlashBubbleView)((FlashBubbleView)window).tucked(true,right);if(corner>0)params.y=corner<=2?dp(30):height-params.height-dp(30);safeUpdate();}
    void expand(){if(transitioning||expanded||window==null)return;
/* Bloqueio da aplicação: o painel mostra nomes de ficheiros, por isso pede autenticação primeiro. */
if(AppLock.required(this)){try{startActivity(new Intent(this,LockActivity.class).putExtra(LockActivity.FLASH,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception e){AppDiagnostics.record(this,"lock-start",e);}return;}
AppLock.touch();transitioning=true;Motion.leave(window,()->{if(dead)return;transitioning=false;try{panel();}catch(Exception e){overlayProblem(e);}});}
    void collapse(Runnable after){if(transitioning||window==null)return;AppLock.touch();transitioning=true;serial++;if(signal!=null)signal.cancel();searches.cancel();Motion.leave(window,()->{if(dead)return;transitioning=false;try{bubble();}catch(Exception e){overlayProblem(e);}if(after!=null)after.run();});}
    void dock(int target,SharedPreferences prefs){
        if(snap!=null)snap.cancel();final int end=Math.max(dp(6),Math.min(target,getResources().getDisplayMetrics().widthPixels-params.width-dp(6)));
        prefs.edit().putInt("float_x",end).putInt("float_y",params.y).apply();
        if(!Motion.enabled(this)){params.x=end;safeUpdate();return;}
        snap=android.animation.ValueAnimator.ofInt(params.x,end);snap.setDuration(180);snap.setInterpolator(new android.view.animation.DecelerateInterpolator());snap.addUpdateListener(a->{if(!dead&&!expanded&&window!=null){params.x=(Integer)a.getAnimatedValue();safeUpdate();}});snap.start();
    }
    void clamp(){int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;params.x=Math.max(dp(6),Math.min(params.x,w-params.width-dp(6)));params.y=Math.max(dp(30),Math.min(params.y,h-params.height-dp(30)));}
    void safeUpdate(){try{if(window!=null)wm.updateViewLayout(window,params);}catch(Exception e){overlayProblem(e);}}
    void stopFloating(){prefs.edit().putBoolean("float_enabled",false).apply();serial++;if(signal!=null)signal.cancel();searches.cancel();expanded=false;remove();updateNotification();if(tracker==null)stopSelf();}
    void chooseMode(boolean flash){directMode=false;prefs.edit().putBoolean("flash_mode",flash).apply();if(panelView!=null)panelView.setMode(flash,false);search(input.getText().toString());}
    void chooseCategory(String category){quickCategory=category;directMode=false;if(panelView!=null){panelView.setCategory(category);panelView.setMode(prefs.getBoolean("flash_mode",true),false);}search(input.getText().toString());}
    void panel(){
        if(dead)return;colors();directMode=false;remove();expanded=true;lastPublished="";
        FlashPanelView card=new FlashPanelView(this,new FlashPanelView.Actions(){
            public void close(){collapse(null);}public void open(){collapse(()->launchApp(null));}public void stop(){stopFloating();}
            public void mode(boolean flash){chooseMode(flash);}public void category(String category){chooseCategory(category);}
            public void folders(){directMode=true;panelView.setMode(false,true);search(input.getText().toString());}
        });
        panelView=card;card.setElevation(dp(6));card.setCategory(quickCategory);card.setMode(prefs.getBoolean("flash_mode",true),false);input=card.input;status=card.status;results=card.results;
        int width=getResources().getDisplayMetrics().widthPixels,height=getResources().getDisplayMetrics().heightPixels;
        int available=Math.max(dp(120),height-dp(72));int panelHeight=Math.min(dp(620),(int)(available*.84f));card.compact(panelHeight<dp(410));
        params=new WindowManager.LayoutParams(Math.min(width-dp(24),dp(440)),panelHeight,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;params.x=(width-params.width)/2;params.y=dp(40);params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        card.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE){collapse(null);return true;}return false;});
        if(Build.VERSION.SDK_INT>=30)card.setOnApplyWindowInsetsListener((v,insets)->{if(!expanded||window!=card||params==null)return insets;android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=insets.isVisible(WindowInsets.Type.ime())?Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom):bars.bottom;int room=Math.max(dp(120),getResources().getDisplayMetrics().heightPixels-bars.top-bottom-dp(24));int desired=insets.isVisible(WindowInsets.Type.ime())?Math.min(dp(620),room):Math.min(dp(620),(int)(room*.84f));card.compact(desired<dp(410));if(params.height!=desired||params.y!=bars.top+dp(12)){params.height=desired;params.y=bars.top+dp(12);safeUpdate();}return insets;});
        window=card;wm.addView(window,params);floating=true;Motion.enter(window,0);input.requestFocus();card.requestApplyInsets();handler.postDelayed(()->{if(!dead&&expanded&&panelView==card)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);},180);
        input.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){collapse(null);return true;}return false;});
        input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){if(debounce!=null)handler.removeCallbacks(debounce);search(input.getText().toString());((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);return true;}return false;});
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){if(debounce!=null)handler.removeCallbacks(debounce);if(signal!=null)signal.cancel();searches.cancel();serial++;directMode=false;card.setMode(prefs.getBoolean("flash_mode",true),false);debounce=()->search(input.getText().toString());handler.postDelayed(debounce,80);}});
        search("");
    }
    void launchApp(Catalog.Entry e){if(e!=null&&!MediaFormats.kind(e.name,e.mime).equals("external")){Intent reader=new Intent(this,ReaderActivity.class).setDataAndType(android.net.Uri.parse(e.uri),MediaFormats.mime(e.name,e.mime)).putExtra("name",e.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(reader);return;}catch(Exception ignored){}}Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);if(e!=null)i.putExtra("quick_uri",e.uri);try{startActivity(i);}catch(Exception ex){Toast.makeText(this,"Abra o Seeky pela notificação.",Toast.LENGTH_LONG).show();}}
    void search(String q){
        FlashPassages.update(this,panelView,q,intent->collapse(()->{try{startActivity(intent);}catch(Exception e){AppDiagnostics.record(this,"flash-passage",e);}}));
        final String category=quickCategory;final boolean deep=directMode;final boolean flash=prefs.getBoolean("flash_mode",true)&&!deep;final long began=SystemClock.elapsedRealtime();final int request=++serial;if(signal!=null)signal.cancel();final CancellationSignal token=new CancellationSignal();signal=token;
        searches.submit(job->{
            LinkedHashMap<String,Catalog.Entry> matches=new LinkedHashMap<>();
            try {
                List<Catalog.Entry> found=q.trim().isEmpty()?db.recent():flash?db.flash(q,category,token):db.query(q,category,false,false,0,30,0,0,"",token);
                if(q.trim().isEmpty()&&(found.isEmpty()||!category.equals("Tudo")))found=db.query("",category,false,false,0,20,0,0,"",token);
                for(Catalog.Entry e:found)if(category.equals("Tudo")||category.equals(e.category))matches.put(e.uri,e);
                publish(request,matches,(flash?"Flash":"Catálogo")+" · "+matches.size()+" · "+(SystemClock.elapsedRealtime()-began)+" ms");
                if(!flash&&!q.trim().isEmpty()&&StorageAccess.all(this)&&matches.size()<30){
                    try{List<Catalog.Entry> system=SystemFiles.search(this,db,q,30,token);for(Catalog.Entry e:system)if(category.equals("Tudo")||category.equals(e.category))matches.putIfAbsent(e.uri,e);publish(request,matches,matches.size()+" resultados · Seeky + catálogo Android");}catch(OperationCanceledException e){throw e;}catch(Exception ignored){}
                }
                if(deep&&!q.trim().isEmpty()&&StorageAccess.all(this)&&matches.size()<30){
                    final String name=Rules.normalize(q.trim());final long[] last={0};boolean limited=false;long deadline=System.nanoTime()+8000000000L;
                    for(File root:StorageAccess.volumes(this)){
                        if(token.isCanceled())return;
                        DiskWalker.Summary sum=DiskWalker.walk(root,getSharedPreferences("seeky",0).getBoolean("hidden",true),300000,deadline,new DiskWalker.Visitor(){
                            public boolean cancelled(){return token.isCanceled()||dead||matches.size()>=30;}
                            public void unreadable(File f){}
                            public void file(File f){if(Rules.nameMatches(f.getName(),name)){Catalog.Entry e=StorageAccess.entry(f,root);if(!category.equals("Tudo")&&!category.equals(e.category))return;matches.put(e.uri,e);if(System.currentTimeMillis()-last[0]>180){last[0]=System.currentTimeMillis();publish(request,matches,"A procurar no armazenamento…");}}}
                        });limited|=sum.limited;if(System.nanoTime()>deadline)break;
                    }
                    publish(request,matches,matches.size()+" resultados"+(limited?" · pesquisa directa parcial (8 s)":" · catálogo + armazenamento"));
                }
            }catch(InterruptedIOException ignored){if(!token.isCanceled())publish(request,matches,"Primeiros "+matches.size()+" resultados");}
            catch(OperationCanceledException ignored){}
            catch(Exception ex){handler.post(()->{if(valid(request))status.setText(AppLanguage.text(status.getContext(),"Não foi possível pesquisar. Confirme o acesso no Seeky."));});}
        });
    }
    String categoryCaption(){return quickCategory.equals("Tudo")?"":quickCategory+" · ";}
    boolean valid(int request){return !dead&&expanded&&request==serial;}
    void publish(int request,Map<String,Catalog.Entry> data,String message){
        List<Catalog.Entry> copy=new ArrayList<>(data.values());handler.post(()->{
            if(!valid(request))return;status.setText(input.getText().toString().trim().isEmpty()?AppLanguage.text(this,"Recentes"):copy.size()+" "+AppLanguage.text(this,copy.size()==1?"resultado":"resultados")+(message.contains("parcial")?" · "+AppLanguage.text(this,"pesquisa parcial"):""));
            StringBuilder key=new StringBuilder();for(Catalog.Entry e:copy)key.append(e.uri).append('\n');String current=key.toString();boolean changed=!current.equals(lastPublished);lastPublished=current;
            if(copy.isEmpty()){if(results.getChildCount()==1&&results.getChildAt(0) instanceof TextView)return;results.removeAllViews();TextView empty=text(StorageAccess.all(this)?"Sem resultados. Experimente a pesquisa completa.":"Active o acesso geral no Seeky para pesquisar no armazenamento partilhado.",14,sub);empty.setPadding(0,dp(16),0,dp(12));results.addView(empty);return;}
            for(int i=0;i<copy.size();i++){
                FileRowView row=results.getChildCount()>i&&results.getChildAt(i) instanceof FileRowView?(FileRowView)results.getChildAt(i):null;
                if(row==null){if(results.getChildCount()>i)results.removeViewAt(i);row=new FileRowView(this,(entry,target)->{});LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-1,-2);item.bottomMargin=dp(8);results.addView(row,i,item);}
                row.bind(copy.get(i),fieldSurface,ink,sub,purple,true,input.getText().toString());row.star.setVisibility(View.GONE);row.arrow.setVisibility(View.VISIBLE);final FileRowView target=row;row.setOnClickListener(v->collapse(()->launchApp(target.entry)));
                if(changed&&i<4)Motion.enter(row,i*18);
            }
            while(results.getChildCount()>copy.size())results.removeViewAt(results.getChildCount()-1);
        });
    }
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);transitioning=false;if(window!=null)Motion.reset(window);if(expanded||window!=null&&prefs!=null&&AppPalette.SYSTEM.equals(prefs.getString("theme",AppPalette.DEFAULT))){try{bubble();}catch(Exception e){overlayProblem(e);}}else if(params!=null){tucked=false;clamp();safeUpdate();scheduleTuck();}}
    @Override public void onTaskRemoved(Intent rootIntent){super.onTaskRemoved(rootIntent);prefs.edit().putLong("last_task_removed",System.currentTimeMillis()).apply();if(!overlayHeld()&&prefs.getBoolean("float_enabled",false)&&Settings.canDrawOverlays(this)){if(expanded||window==null)try{bubble();}catch(Exception e){overlayProblem(e);}handler.removeCallbacks(permissionCheck);handler.postDelayed(permissionCheck,1000);}updateNotification();}
    @Override public void onDestroy(){dead=true;running=false;FlashTileService.refresh(this);if(prefs!=null)prefs.unregisterOnSharedPreferenceChangeListener(themeListener);CatalogEvents.remove(catalogListener);try{unregisterReceiver(screenReceiver);}catch(Exception ignored){}running=false;floating=false;monitoring=false;instance=null;if(tracker!=null){tracker.close();tracker=null;}serial++;if(signal!=null)signal.cancel();handler.removeCallbacksAndMessages(null);remove();searches.close(db::close);stopForeground(STOP_FOREGROUND_REMOVE);prefs.edit().putLong("service_last_stop",System.currentTimeMillis()).putString("tracker_status",prefs.getBoolean("monitor_enabled",true)?"Serviço interrompido; o Android poderá restaurá-lo":"Detecção automática pausada").apply();super.onDestroy();}
}
