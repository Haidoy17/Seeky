package mz.bam.seeky.android;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.os.storage.*;
import android.webkit.MimeTypeMap;
import java.io.*;
import java.util.*;

final class StorageAccess {
    static boolean all(Context c) {
        return Build.VERSION.SDK_INT>=30?Environment.isExternalStorageManager():c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    static List<File> volumes(Context c) {
        LinkedHashMap<String,File> roots=new LinkedHashMap<>();
        add(roots,Environment.getExternalStorageDirectory());
        if(Build.VERSION.SDK_INT>=30) {
            StorageManager sm=(StorageManager)c.getSystemService(Context.STORAGE_SERVICE);
            for(StorageVolume v:sm.getStorageVolumes())if(v.getDirectory()!=null)add(roots,v.getDirectory());
        } else {
            File[] dirs=c.getExternalFilesDirs(null);
            for(File f:dirs)if(f!=null) {String p=f.getAbsolutePath();int i=p.indexOf("/Android/");if(i>0)add(roots,new File(p.substring(0,i)));}
        }
        return new ArrayList<>(roots.values());
    }
    private static void add(Map<String,File> roots,File f) {
        if(f==null)return;try {File canonical=f.getCanonicalFile();if(canonical.isDirectory())roots.put(canonical.getPath(),canonical);}catch(IOException ignored){}
    }
    static boolean allowed(Context c, File f) {
        if(!all(c))return false;
        try {
            for(File root:volumes(c))if(DiskWalker.inside(root,f)&&!DiskWalker.protectedPath(root,f.getCanonicalFile()))return true;
        }catch(IOException ignored){}
        return false;
    }
    static String mime(File f) {
        String n=f.getName();int i=n.lastIndexOf('.');String ext=i>=0?n.substring(i+1).toLowerCase(Locale.ROOT):"";
        String type=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return MediaFormats.mime(f.getName(),type==null?"application/octet-stream":type);
    }
    static Catalog.Entry entry(File f, File root) {
        Catalog.Entry e=new Catalog.Entry();e.uri=Uri.fromFile(f).toString();e.name=f.getName();e.path=f.getAbsolutePath();e.root=Uri.fromFile(root).toString();e.mime=mime(f);e.category=Rules.category(e.name,e.mime);e.size=f.length();e.modified=f.lastModified();return e;
    }
    static File file(Catalog.Entry e)throws IOException {Uri u=Uri.parse(e.uri);if(!"file".equals(u.getScheme()))throw new IOException("Ficheiro de pasta autorizada");return new File(u.getPath());}
    static Uri shareUri(Context c,Catalog.Entry e) {
        Uri u=Uri.parse(e.uri);
        if(!"file".equals(u.getScheme()))return u;
        return new Uri.Builder().scheme("content").authority(c.getPackageName()+".files").appendPath("file").appendPath(u.getPath()).build();
    }
    static String location(File root) {
        try {if(root.getCanonicalFile().equals(Environment.getExternalStorageDirectory().getCanonicalFile()))return "Memória do telemóvel";}catch(IOException ignored){}
        return "Cartão / USB · "+root.getName();
    }
}
