package mz.bam.seeky.android;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import android.provider.OpenableColumns;import java.io.*;
/** Read-only access to explicitly imported private study copies. */
public final class StudyFileProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 static File directory(Context c){File f=new File(c.getFilesDir(),"study-materials");f.mkdirs();return f;}
 static Uri uri(Context c,long id){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".study").appendPath("material").appendPath(""+id).build();}
 StudyStore.Row record(Uri uri)throws FileNotFoundException{try{if(uri.getPathSegments().size()!=2||!uri.getPathSegments().get(0).equals("material"))throw new IOException();try(StudyStore db=new StudyStore(getContext())){StudyStore.Row row=db.get("materials",Long.parseLong(uri.getLastPathSegment()));if(row==null)throw new IOException();File f=new File(row.s("path")).getCanonicalFile();if(!DiskWalker.inside(directory(getContext()).getCanonicalFile(),f)||!f.isFile())throw new IOException();return row;}}catch(Exception e){throw new FileNotFoundException("Material indisponível.");}}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!mode.equals("r"))throw new FileNotFoundException("Apenas leitura.");return ParcelFileDescriptor.open(new File(record(uri).s("path")),ParcelFileDescriptor.MODE_READ_ONLY);}
 public String getType(Uri uri){try{return record(uri).s("mime");}catch(Exception e){return "application/octet-stream";}}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{StudyStore.Row r=record(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor out=new MatrixCursor(cols);Object[] v=new Object[cols.length];for(int i=0;i<cols.length;i++)v[i]=cols[i].equals(OpenableColumns.DISPLAY_NAME)?r.s("name"):cols[i].equals(OpenableColumns.SIZE)?r.n("bytes"):null;out.addRow(v);return out;}catch(Exception e){return null;}}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String w,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String w,String[] a){throw new UnsupportedOperationException();}
}
