package mz.bam.seeky.android;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Uses Android's file database, not a third-party file manager's private data. */
final class SystemFiles {
    final Context app;final Catalog db;final SharedPreferences prefs;int lastChanges=0;
    SystemFiles(Context c,Catalog catalog){app=c;db=catalog;prefs=c.getSharedPreferences("seeky",0);}
    static File rootOf(File file,List<File> roots)throws IOException {
        File canonical=file.getCanonicalFile();if(SafeTrash.internal(canonical))return null;for(File root:roots)if(DiskWalker.inside(root,canonical)&&!DiskWalker.protectedPath(root,canonical))return root;return null;
    }
    static File readableRoot(File file,List<File> roots){try{return rootOf(file,roots);}catch(Exception ignored){return null;}}
    static List<Catalog.Entry> search(Context app,Catalog db,String query,int limit,CancellationSignal signal)throws Exception {
        List<Catalog.Entry> out=new ArrayList<>();if(!StorageAccess.all(app))return out;
        String raw=query.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_");boolean glob=raw.indexOf('*')>=0||raw.indexOf('?')>=0;raw=glob?raw.replace('*','%').replace('?','_'):"%"+raw+"%";
        Bundle args=new Bundle();args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"_display_name LIKE ? ESCAPE '\\'");args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,new String[]{raw});args.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,new String[]{"date_modified"});args.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);args.putInt(ContentResolver.QUERY_ARG_LIMIT,Math.min(limit*3,600));
        List<File> roots=StorageAccess.volumes(app);Set<String> stars=db.favoriteUris();
        try(Cursor cursor=app.getContentResolver().query(MediaStore.Files.getContentUri("external"),new String[]{"_data"},args,signal)){
            if(cursor==null)return out;
            while(cursor.moveToNext()&&out.size()<limit){if(signal!=null)signal.throwIfCanceled();String path=cursor.getString(0);if(path==null)continue;File file=new File(path);File root=readableRoot(file,roots);if(root!=null&&file.isFile()&&!file.getName().startsWith(".seeky-transfer-")){Catalog.Entry e=StorageAccess.entry(file.getCanonicalFile(),root);e.favorite=stars.contains(e.uri);out.add(e);}}
        }return out;
    }
    Set<String> volumes(){return Build.VERSION.SDK_INT>=29?MediaStore.getExternalVolumeNames(app):Collections.singleton("external");}
    /** Processes one page per volume, allowing filesystem events to run between pages. */
    boolean pump(CancellationSignal signal)throws Exception {
        lastChanges=0;if(!StorageAccess.all(app))return false;boolean more=false;List<File> roots=StorageAccess.volumes(app);
        for(String volume:volumes()){
            signal.throwIfCanceled();String key="media_"+volume;boolean modern=Build.VERSION.SDK_INT>=30;
            String version=modern?MediaStore.getVersion(app,volume):"legacy";boolean reset=!version.equals(prefs.getString(key+"_version",""));
            long previous=version.equals(prefs.getString(key+"_version",""))?prefs.getLong(key+"_done",0):0;
            long upper=prefs.getLong(key+"_upper",0),lastId=prefs.getLong(key+"_cursor",0);
            if(!version.equals(prefs.getString(key+"_version",""))){upper=0;lastId=0;}
            if(upper==0){upper=modern?MediaStore.getGeneration(app,volume):System.currentTimeMillis()/1000;lastId=0;}
            if(modern){GenerationWindow window=GenerationWindow.open(version,prefs.getString(key+"_version",""),prefs.getLong(key+"_done",0),prefs.getLong(key+"_upper",0),prefs.getLong(key+"_cursor",0),MediaStore.getGeneration(app,volume));previous=window.from;upper=window.through;lastId=window.afterId;reset=window.reset;}
            if(modern&&previous==upper){continue;}
            String selection=modern?"generation_modified>? AND generation_modified<=? AND _id>?":"(date_modified>=? OR date_added>=?) AND _id>?";
            String[] values=modern?new String[]{Long.toString(previous),Long.toString(upper),Long.toString(lastId)}:new String[]{Long.toString(Math.max(0,previous-2)),Long.toString(Math.max(0,previous-2)),Long.toString(lastId)};
            Bundle args=new Bundle();args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,selection);args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,values);args.putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS,new String[]{"_id"});args.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);args.putInt(ContentResolver.QUERY_ARG_LIMIT,256);
            List<Catalog.Entry> batch=new ArrayList<>();int rows=0;
            try(Cursor c=app.getContentResolver().query(MediaStore.Files.getContentUri(volume),new String[]{"_id","_data"},args,signal)){
                if(c==null)throw new IOException("Catálogo Android indisponível");
                while(rows<256&&c.moveToNext()){signal.throwIfCanceled();rows++;lastId=c.getLong(0);String path=c.getString(1);if(path==null)continue;File f=new File(path);File root=readableRoot(f,roots);if(root!=null&&f.isFile()&&!f.getName().startsWith(".seeky-transfer-"))batch.add(StorageAccess.entry(f.getCanonicalFile(),root));}
            }
            lastChanges+=db.metadata(batch);
            SharedPreferences.Editor editor=prefs.edit().putString(key+"_version",version);if(reset)editor.putLong(key+"_done",0);
            if(rows==256){more=true;editor.putLong(key+"_upper",upper).putLong(key+"_cursor",lastId);}
            else editor.putLong(key+"_done",upper).remove(key+"_upper").remove(key+"_cursor");
            editor.apply();
        }return more;
    }
}
