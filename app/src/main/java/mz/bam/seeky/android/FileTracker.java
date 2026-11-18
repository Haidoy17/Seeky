package mz.bam.seeky.android;

import android.content.*;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Incremental, event-driven discovery. No document extraction in this pipeline. */
final class FileTracker implements AutoCloseable {
    static final int WATCH_LIMIT=96;
    final Context app;final Catalog db;final SystemFiles media;final SharedPreferences prefs;
    final ScheduledThreadPoolExecutor actor=new ScheduledThreadPoolExecutor(1);
    final ChangeQueue changes=new ChangeQueue(4096);
    final AtomicBoolean scheduled=new AtomicBoolean(),mediaDirty=new AtomicBoolean(true);
    final LinkedHashMap<String,FileObserver> watches=new LinkedHashMap<>();
    final LinkedHashMap<String,String[]> known=new LinkedHashMap<>();
    final ArrayDeque<File> folders=new ArrayDeque<>();final Set<String> queued=new HashSet<>();
    final CancellationSignal signal=new CancellationSignal();List<File> roots=new ArrayList<>();
    volatile boolean closed=false;boolean mediaMore=false;int checked=0,changed=0,pollOffset=0;long lastNotice=0;String lastStatus="";
    final ContentObserver observer;
    FileTracker(Context app,Catalog db){
        this.app=app;this.db=db;this.prefs=app.getSharedPreferences("seeky",0);media=new SystemFiles(app,db);
        actor.setRemoveOnCancelPolicy(true);
        observer=new ContentObserver(new Handler(Looper.getMainLooper())){@Override public void onChange(boolean self,Uri uri){mediaDirty.set(true);wake(120);}};
    }
    void start(){
        try{app.getContentResolver().registerContentObserver(Uri.parse("content://media"),true,observer);}catch(Exception broad){try{app.getContentResolver().registerContentObserver(android.provider.MediaStore.Files.getContentUri("external"),true,observer);}catch(Exception narrow){prefs.edit().putString("media_warning","Observador Android indisponível; consulta periódica activa: "+narrow.getClass().getSimpleName()).apply();}}
        actor.execute(()->{try{
            refreshRoots();
            for(File volume:roots)for(String n:new String[]{"Download","Downloads","Documents","DCIM","Pictures","Android/media"}){File d=new File(volume,n);if(d.isDirectory())watch(d);}
            for(String[] state:db.folders()){
                File directory=new File(state[0]);File root=root(directory);if(root==null)continue;known.put(state[0],state);watch(directory);
                if(directory.isDirectory()&&directory.lastModified()!=Long.parseLong(state[2]))queue(directory,false);
            }
            for(File root:roots)for(String child:new String[]{"Download","Downloads","Documents","DCIM","Pictures","Music","Movies","Android/media","WhatsApp","Telegram"}){
                File directory=new File(root,child);if(directory.isDirectory()){watch(directory);String[] state=known.get(directory.getAbsolutePath());if(state==null||directory.lastModified()!=Long.parseLong(state[2]))queue(directory,true);}
            }
            db.migrateFavorites();wake(0);
        }catch(Exception e){notice("Detecção com aviso: "+e.getMessage(),true);}});
        actor.scheduleWithFixedDelay(()->{try{refreshRoots();reconcile(256);mediaDirty.set(true);wake(0);}catch(Exception e){notice("Acesso em revisão: "+e.getMessage(),true);}},60,60,TimeUnit.SECONDS);
    }
    File root(File file){try{return SystemFiles.rootOf(file,roots);}catch(Exception e){return null;}}
    void refreshRoots(){
        if(!StorageAccess.all(app))return;roots=StorageAccess.volumes(app);
        for(File root:roots){watch(root);String[] state=known.get(root.getAbsolutePath());if(state==null||root.lastModified()!=Long.parseLong(state[2]))queue(root,false);}
    }
    void watch(File directory){
        String path=directory.getAbsolutePath();if(closed||watches.containsKey(path)||watches.size()>=WATCH_LIMIT||!directory.isDirectory())return;
        FileObserver watcher=new FileObserver(path,FileObserver.CREATE|FileObserver.CLOSE_WRITE|FileObserver.MOVED_TO|FileObserver.MOVED_FROM|FileObserver.DELETE|FileObserver.DELETE_SELF|FileObserver.MOVE_SELF){
            public void onEvent(int event,String name){if(closed)return;File target=name==null?directory:new File(directory,name);long settle=(event&FileObserver.CREATE)!=0?180:40;changes.offer(target.getAbsolutePath(),System.currentTimeMillis()+settle);wake(settle);}
        };
        watches.put(path,watcher);watcher.startWatching();
    }
    void queue(File directory,boolean first){if(closed||!directory.isDirectory())return;String path=directory.getAbsolutePath();if(queued.size()>=60000)return;if(queued.add(path)){if(first)folders.addFirst(directory);else folders.addLast(directory);}}
    void wake(long delay){if(closed||actor.isShutdown())return;if(scheduled.compareAndSet(false,true))try{actor.schedule(this::tick,Math.max(0,delay),TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){scheduled.set(false);}}
    void tick(){
        try{
            if(closed)return;
            if(!StorageAccess.all(app)){folders.clear();queued.clear();mediaMore=false;mediaDirty.set(false);notice("Detecção pausada: falta acesso geral",true);return;}
            drainChanges();
            if(mediaDirty.getAndSet(false)||mediaMore){try{mediaMore=media.pump(signal);changed+=media.lastChanges;}catch(OperationCanceledException e){throw e;}catch(Exception e){mediaMore=false;prefs.edit().putString("media_warning",e.getMessage()).apply();}}
            File directory=folders.pollFirst();if(directory!=null){queued.remove(directory.getAbsolutePath());read(directory);}
            if(changes.overflowed())reconcile(512);
            notice((folders.isEmpty()&&!mediaMore?"Detecção activa":"A descobrir nomes em pequenos lotes")+" · "+watches.size()+" pastas observadas · "+changed+" alterações",folders.isEmpty()&&!mediaMore);
        }catch(OperationCanceledException ignored){}catch(Exception e){notice("Detecção com aviso: "+e.getMessage(),true);}
        finally{scheduled.set(false);if(!closed&&(!folders.isEmpty()||changes.size()>0||mediaMore||mediaDirty.get()))wake(changes.size()>0?80:5);}
    }
    void drainChanges()throws Exception {
        List<Catalog.Entry> batch=new ArrayList<>();
        for(String path:changes.ready(System.currentTimeMillis(),128)){
            File file=new File(path);if(file.getName().startsWith(".seeky-transfer-"))continue;File root=root(file);if(root==null)continue;
            if(file.isDirectory()){watch(file);queue(file,true);}
            else if(file.isFile()&&!file.getName().startsWith(".seeky-transfer-"))batch.add(StorageAccess.entry(file.getCanonicalFile(),root));
            else if(root.isDirectory()&&file.getParentFile()!=null&&file.getParentFile().isDirectory()){
                db.removePath(file);FileObserver old=watches.remove(path);if(old!=null)old.stopWatching();known.remove(path);changed++;
            }
        }
        changed+=db.metadata(batch);
    }
    void read(File directory)throws Exception {
        File root=root(directory);if(root==null||!directory.isDirectory())return;watch(directory);
        FolderSnapshot snapshot=FolderSnapshot.read(root,directory,prefs.getBoolean("hidden",true));
        List<Catalog.Entry> batch=new ArrayList<>();
        for(File file:snapshot.files){if(closed)throw new OperationCanceledException();batch.add(StorageAccess.entry(file,root));if(batch.size()>=128){changed+=db.metadata(batch);batch.clear();drainChanges();notice("Novos nomes já disponíveis · "+changed+" alterações",folders.isEmpty()&&!mediaMore);}}
        changed+=db.metadata(batch);changed+=db.removeMissingInFolder(directory,snapshot.paths);checked++;
        db.folder(root,directory,snapshot.modified);known.put(directory.getAbsolutePath(),new String[]{directory.getAbsolutePath(),root.getAbsolutePath(),Long.toString(snapshot.modified),Long.toString(System.currentTimeMillis())});
        snapshot.directories.sort((a,b)->Integer.compare(priority(a),priority(b)));
        for(File child:snapshot.directories){watch(child);String[] previous=known.get(child.getAbsolutePath());if(previous==null||child.lastModified()!=Long.parseLong(previous[2]))queue(child,false);}
    }
    int priority(File file){String n=file.getName().toLowerCase(Locale.ROOT);return n.contains("download")||n.equals("documents")||n.equals("dcim")||n.equals("media")?0:1;}
    void reconcile(int limit){
        if(known.isEmpty())return;List<String[]> state=new ArrayList<>(known.values());int end=Math.min(limit,state.size());
        for(int i=0;i<end;i++){String[] saved=state.get((pollOffset+i)%state.size());File directory=new File(saved[0]);File root=root(directory);if(root==null||!root.isDirectory())continue;if(!directory.exists()){if(directory.getParentFile()!=null&&directory.getParentFile().isDirectory())db.removePath(directory);}else if(directory.lastModified()!=Long.parseLong(saved[2])||(saved.length>3&&System.currentTimeMillis()-Long.parseLong(saved[3])>300000))queue(directory,true);}
        pollOffset=(pollOffset+end)%state.size();
    }
    void refresh(){if(closed)return;actor.execute(()->{try{refreshRoots();reconcile(1024);mediaDirty.set(true);wake(0);}catch(Exception e){notice(e.getMessage(),true);}});}
    void folderVisible(File folder){if(closed)return;actor.execute(()->{watch(folder);queue(folder,true);wake(0);});}
    void notice(String status,boolean force){long now=System.currentTimeMillis();if(status.equals(lastStatus)||(!force&&now-lastNotice<800))return;lastNotice=now;lastStatus=status;prefs.edit().putString("tracker_status",status).putLong("tracker_update",now).apply();CatalogEvents.publish(status);}
    public void close(){closed=true;signal.cancel();try{app.getContentResolver().unregisterContentObserver(observer);}catch(Exception ignored){}try{actor.execute(()->{for(FileObserver w:watches.values())w.stopWatching();watches.clear();});}catch(RejectedExecutionException ignored){}actor.shutdown();}
}
