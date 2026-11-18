package mz.bam.seeky.android;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.function.BooleanSupplier;

/** Only creates new documents; never overwrites an existing user document. */
final class NarrationDestination {
    final ContentResolver resolver;final Uri folder;
    NarrationDestination(Context c,Uri tree,String title)throws IOException{resolver=c.getContentResolver();Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));folder=DocumentsContract.createDocument(resolver,root,DocumentsContract.Document.MIME_TYPE_DIR,clean(title));if(folder==null)throw new IOException("Não foi possível criar a pasta de áudio.");}
    static String clean(String title){String value=(title==null?"Seeky":title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();return value.isEmpty()?"Seeky":AudioTitles.limit(value,120,230);}
    void save(File audio,String name,BooleanSupplier cancelled)throws IOException{
        if(cancelled.getAsBoolean())throw new InterruptedIOException();Uri target=DocumentsContract.createDocument(resolver,folder,"audio/wav",clean(name));if(target==null)throw new IOException("Não foi possível criar o ficheiro de áudio.");boolean complete=false;
        try{try(InputStream in=new FileInputStream(audio);OutputStream out=resolver.openOutputStream(target,"w")){if(out==null)throw new IOException("Pasta sem acesso de escrita.");byte[] buffer=new byte[65536];for(int count;(count=in.read(buffer))!=-1;){if(cancelled.getAsBoolean())throw new InterruptedIOException();out.write(buffer,0,count);}out.flush();}if(cancelled.getAsBoolean())throw new InterruptedIOException();complete=true;}finally{if(!complete)try{DocumentsContract.deleteDocument(resolver,target);}catch(Exception ignored){}}
    }
}
