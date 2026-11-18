package mz.bam.seeky.android;
import android.content.Context;
import android.net.Uri;
import android.media.*;
import java.io.*;

final class MetadataExtractor {
 static final class Values {String title="",author="";}
 static Values read(Context app,Catalog.Entry e)throws Exception {
  Values v=new Values();String kind=MediaFormats.kind(e.name,e.mime);
  if(kind.equals("audio")){MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(app,Uri.parse(e.uri));v.title=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));v.author=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));if(v.author.isEmpty())v.author=clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR));}finally{r.release();}}
  else if(kind.equals("epub")||kind.equals("docx")){File file,temporary=null;if(e.uri.startsWith("file:"))file=StorageAccess.file(e);else{temporary=File.createTempFile("seeky-meta-",".zip",app.getCacheDir());file=temporary;try(InputStream in=app.getContentResolver().openInputStream(Uri.parse(e.uri));OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("Ficheiro indisponível.");byte[] b=new byte[65536];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>67108864)throw new IOException("Limite de 64 MB para importar metadados de um fornecedor.");out.write(b,0,n);}}catch(Exception ex){temporary.delete();throw ex;}}try(BookArchive b=new BookArchive(file,kind.equals("epub"))){v.title=clean(b.title);v.author=clean(b.author);}finally{if(temporary!=null)temporary.delete();}}
  else if(kind.equals("image")){try(InputStream in=app.getContentResolver().openInputStream(Uri.parse(e.uri))){if(in==null)throw new IOException("Imagem indisponível.");android.media.ExifInterface exif=new android.media.ExifInterface(in);v.author=clean(exif.getAttribute("Artist"));v.title=clean(exif.getAttribute("ImageDescription"));}}
  else throw new IOException("Importação automática disponível para DOCX, EPUB, áudio e imagens com metadados suportados. Pode preencher estes dados manualmente.");
  return v;
 }
 static String clean(String s){if(s==null)return "";return s.trim().substring(0,Math.min(300,s.trim().length()));}
 private MetadataExtractor(){}
}
