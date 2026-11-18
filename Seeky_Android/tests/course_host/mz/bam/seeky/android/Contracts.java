package mz.bam.seeky.android;
import android.content.Context;import java.io.File;import java.net.URI;
class CloudJobs {static void schedule(Context c){}}
class AppDiagnostics {static void record(Context c,String op,Throwable e){}}
class StudyFileProvider {
 static File directory(Context c){File d=new File(c.getFilesDir(),"study-materials");d.mkdirs();return d;}
 static URI uri(Context c,long id){return URI.create("content://"+c.getPackageName()+".study/material/"+id);}
}
