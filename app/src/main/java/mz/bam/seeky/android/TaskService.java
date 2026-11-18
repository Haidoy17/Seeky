package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.concurrent.*;

/** One heavy foreground task at a time. UI lifetime never owns a job or its cancellation. */
public final class TaskService extends Service {
    static final int NOTIFICATION=5313;static final String CHANNEL="seeky-work-v1";
    static volatile TaskService instance;final ExecutorService worker=Executors.newSingleThreadExecutor();
    volatile Control current;volatile boolean running,destroyed;final Handler main=new Handler(Looper.getMainLooper());int lastStartId;
    static void open(Context c){c.startActivity(new Intent(c,TaskActivity.class));}
    static void start(Context c,String id){command(c,id,"resume");}
    static synchronized void recoverOrphans(TaskStore db){if(instance==null)db.recover();}
    static void wake(Context c){try(TaskStore db=new TaskStore(c)){recoverOrphans(db);if(db.next()!=null)c.startForegroundService(new Intent(c,TaskService.class));}catch(RuntimeException e){AppDiagnostics.record(c,"work-wake",e);}}
    static void launch(Activity a,String id){try{start(a.getApplicationContext(),id);}catch(RuntimeException e){try(TaskStore db=new TaskStore(a)){db.state(id,"paused","A tarefa ficou guardada. Abra Tarefas para continuar.");}AppDiagnostics.record(a,"work-start",e);}PlusUi.post(a,()->open(a));}
    static void command(Context c,String id,String command){
        try(TaskStore db=new TaskStore(c)){recoverOrphans(db);StudyStore.Row row=db.job(id);if(row==null)return;if(instance!=null&&instance.current!=null&&instance.current.id.equals(id)&&instance.current.critical){android.widget.Toast.makeText(c,AppLanguage.text(c,"A aplicar alterações. Aguarde terminar."),android.widget.Toast.LENGTH_SHORT).show();return;}
            if(command.equals("resume")){if(!row.s("workspace").equals(TaskStore.workspace(c))&&!row.s("type").equals("restore")){db.state(id,"failed","A biblioteca mudou. Crie uma tarefa para a biblioteca actual.");return;}if((row.s("state").equals("running")||row.s("state").equals("pausing"))&&instance!=null&&instance.current!=null&&instance.current.id.equals(id))return;db.retryFailed(id);db.state(id,"queued","Em espera");}
            else{boolean active=instance!=null&&instance.current!=null&&instance.current.id.equals(id);db.state(id,active?(command.equals("cancel")?"cancelling":"pausing"):(command.equals("cancel")?"cancelled":"paused"),command.equals("cancel")?"Cancelado":"Pausado");if(active){instance.current.stopped=true;Runnable interrupt=instance.current.interrupt;if(interrupt!=null)interrupt.run();}}
        }
        if(command.equals("resume"))c.startForegroundService(new Intent(c,TaskService.class));
    }
    public void onCreate(){super.onCreate();synchronized(TaskService.class){instance=this;try(TaskStore db=new TaskStore(this)){db.recover();}}NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel(CHANNEL,AppLanguage.text(this,"Tarefas do Seeky"),NotificationManager.IMPORTANCE_LOW));}
    public int onStartCommand(Intent i,int flags,int startId){
        lastStartId=startId;try{foreground("A preparar tarefas…",null,0,0);}catch(RuntimeException e){AppDiagnostics.record(this,"work-foreground",e);try(TaskStore db=new TaskStore(this)){db.getWritableDatabase().execSQL("UPDATE jobs SET state='paused',message='O Android suspendeu as tarefas. Abra o Seeky para retomar.' WHERE state='queued'");}stopSelf();return START_NOT_STICKY;}
        if(i!=null&&i.hasExtra("job")){String id=i.getStringExtra("job"),command=i.getStringExtra("command");if(command!=null)command(this,id,command);}
        if(!running&&!destroyed){running=true;worker.execute(this::loop);}return START_STICKY;
    }
    void foreground(String message,String id,int progress,int total){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,TaskActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.seeky).setContentTitle("Seeky · "+AppLanguage.text(this,"Tarefas")).setContentText(AppLanguage.text(this,message)).setStyle(new Notification.BigTextStyle().bigText(AppLanguage.text(this,message))).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true);
        if(total>0)b.setProgress(total,progress,false);else b.setProgress(0,0,true);
        if(id!=null&&(current==null||!current.critical)){Intent pause=new Intent(this,TaskService.class).putExtra("job",id).putExtra("command","pause");PendingIntent p=PendingIntent.getService(this,id.hashCode(),pause,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);b.addAction(0,AppLanguage.text(this,"Pausar"),p);}
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,b.build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,b.build());
    }
    void loop(){android.os.PowerManager.WakeLock wake=null;try{PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Seeky:work");wake.acquire(6*60*60*1000L);
        while(!destroyed){String id;try(TaskStore db=new TaskStore(this)){id=db.next();}if(id==null)break;Control control=new Control(this,id);current=control;
            long started=SystemClock.elapsedRealtime();try(TaskStore db=new TaskStore(this)){
                StudyStore.Row job=db.job(id);if(job==null)continue;control.attach(db);db.state(id,"running","A preparar…");control.progress(job.s("title"),job.n("progress"),job.n("total"));
                if(!job.s("workspace").equals(TaskStore.workspace(this))&&!job.s("type").equals("restore"))throw new IOException("A biblioteca mudou. Crie a tarefa novamente.");
                TaskWork.run(this,job,db,control);control.check();db.state(id,"complete",db.job(id).s("message"));
            }catch(InterruptedIOException e){try(TaskStore db=new TaskStore(this)){StudyStore.Row state=db.job(id);if(state!=null&&state.s("lease").equals(control.lease))db.state(id,state.s("state").equals("cancelling")?"cancelled":"paused",state.s("message").isEmpty()?"Pausado. Pode retomar.":state.s("message"));}}
            catch(Exception|OutOfMemoryError e){AppDiagnostics.record(this,"persistent-work",e);try(TaskStore db=new TaskStore(this)){if(control.owns(db))db.state(id,"failed",e instanceof OutOfMemoryError?"Pouca memória. Pode retomar no modo leve.":e.getMessage()==null?"Não foi possível concluir. Pode retomar.":e.getMessage());}}
            finally{try(TaskStore reports=new TaskStore(this)){StudyStore.Row finished=reports.job(id);if(finished!=null&&finished.s("type").equals("import"))ImportReport.save(this,reports,id);}catch(Exception error){AppDiagnostics.record(this,"import-report",error);}OperationMetrics.record(this,"work",SystemClock.elapsedRealtime()-started);current=null;TaskEvents.changed();}
        }
    }finally{if(wake!=null&&wake.isHeld())wake.release();main.post(this::finishLoop);}}
    void finishLoop(){running=false;if(destroyed)return;try(TaskStore db=new TaskStore(this)){if(db.next()!=null){running=true;worker.execute(this::loop);return;}}stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(lastStartId);}
    public void onTimeout(int startId,int type){destroyed=true;if(current!=null){current.stopped=true;if(current.interrupt!=null)current.interrupt.run();}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){destroyed=true;if(current!=null){current.stopped=true;if(current.interrupt!=null)current.interrupt.run();}worker.shutdown();main.removeCallbacksAndMessages(null);if(instance==this)instance=null;super.onDestroy();}
    static final class Control {
        final TaskService service;final String id;final String lease=java.util.UUID.randomUUID().toString();volatile boolean stopped,critical;volatile Runnable interrupt;long shown;
        Control(TaskService service,String id){this.service=service;this.id=id;}
        void attach(TaskStore db){db.update(id,StudyStore.values("lease",lease));}boolean owns(TaskStore db){StudyStore.Row job=db.job(id);return job!=null&&job.s("lease").equals(lease);}
        void check()throws InterruptedIOException{if(stopped||service.destroyed||instance!=service||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        boolean cancelled(){return stopped||service.destroyed||instance!=service||Thread.currentThread().isInterrupted();}
        void progress(String text,long value,long total){long now=SystemClock.elapsedRealtime();if(now-shown<350&&value!=total)return;shown=now;try(TaskStore db=new TaskStore(service)){if(!owns(db))return;db.update(id,StudyStore.values("message",text,"progress",value,"total",total));}service.foreground(text,id,(int)Math.min(Integer.MAX_VALUE,value),(int)Math.min(Integer.MAX_VALUE,total));TaskEvents.changed();}
    }
}
