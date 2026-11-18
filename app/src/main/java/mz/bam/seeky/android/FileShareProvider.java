package mz.bam.seeky.android;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Read-only provider. Android enforces explicit grants; canonical roots are revalidated. */
public final class FileShareProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File resolve(Uri uri)throws FileNotFoundException {
        List<String> parts=uri.getPathSegments();
        if(parts.size()==2&&parts.get(0).equals("apk")){try{File root=ApkInstallActivity.directory(getContext()).getCanonicalFile(),file=new File(root,parts.get(1)).getCanonicalFile();if(!parts.get(1).matches("[a-f0-9-]{36}\\.apk")||!DiskWalker.inside(root,file)||!file.isFile())throw new FileNotFoundException();return file;}catch(IOException e){throw new FileNotFoundException("APK indisponível.");}}
        if(parts.size()!=2||!parts.get(0).equals("file"))throw new FileNotFoundException("URI inválido");
        File f=new File(parts.get(1));
        if(!StorageAccess.allowed(getContext(),f)||!f.isFile())throw new FileNotFoundException("Ficheiro indisponível ou sem acesso");
        return f;
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Partilha apenas para leitura");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public String getType(Uri uri){try{return uri.getPathSegments().contains("apk")?ApkInstallActivity.MIME:StorageAccess.mime(resolve(uri));}catch(Exception e){return "application/octet-stream";}}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try {File f=resolve(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor result=new MatrixCursor(cols);Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++)row[i]=cols[i].equals(OpenableColumns.DISPLAY_NAME)?f.getName():cols[i].equals(OpenableColumns.SIZE)?f.length():null;result.addRow(row);return result;}catch(Exception e){return null;}
    }
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
}
