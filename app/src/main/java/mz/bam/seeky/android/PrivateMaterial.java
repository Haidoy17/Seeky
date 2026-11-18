package mz.bam.seeky.android;
import android.content.*;import android.net.Uri;import java.io.*;
final class PrivateMaterial {
    static File file(Context c,Uri uri)throws IOException{if(uri==null||!"content".equals(uri.getScheme())||!(c.getPackageName()+".study").equals(uri.getAuthority()))return null;try{if(uri.getPathSegments().size()!=2||!uri.getPathSegments().get(0).equals("material"))return null;try(StudyStore db=new StudyStore(c)){StudyStore.Row row=db.get("materials",Long.parseLong(uri.getLastPathSegment()));if(row==null)throw new IOException("Livro indisponível.");File file=new File(row.s("path")).getCanonicalFile();if(!DiskWalker.inside(StudyFileProvider.directory(c).getCanonicalFile(),file)||!file.isFile())throw new IOException("Livro indisponível.");return file;}}catch(NumberFormatException e){throw new IOException("Livro indisponível.",e);}}
    private PrivateMaterial(){}
}
