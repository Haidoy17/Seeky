package mz.bam.seeky.android;

import android.content.*;import android.database.Cursor;import android.net.Uri;import android.provider.OpenableColumns;
import java.io.*;import java.util.*;

/** Sequential private-copy imports. Never truncates the document picker's selection. */
final class StudyImporter {
 interface Progress {void changed(Result result,String name);}
 static final class Result {
  final int selected;int imported,duplicates,failed;boolean cancelled;File report;final List<String> errors=new ArrayList<>();
  Result(int selected){this.selected=selected;}
  int processed(){return imported+duplicates+failed;}int remaining(){return selected-processed();}
  String summary(){return selected+" seleccionados · "+processed()+" processados\n"+imported+" importados · "+duplicates+" repetidos · "+failed+" falhados"+(remaining()>0?"\n"+remaining()+" por processar":"")+(cancelled?" · cancelado":"")+(errors.isEmpty()?"":"\n\n"+android.text.TextUtils.join("\n",errors))+(failed>errors.size()?"\n… consulte o relatório completo.":"");}
 }
 static List<Uri> selected(Intent intent){List<Uri> result=new ArrayList<>();if(intent==null)return result;ClipData clip=intent.getClipData();if(clip!=null&&clip.getItemCount()>0){for(int i=0;i<clip.getItemCount();i++)result.add(clip.getItemAt(i).getUri());}else if(intent.getData()!=null)result.add(intent.getData());return result;}
 static File reports(Context context){File dir=new File(context.getFilesDir(),"import-reports");dir.mkdirs();return dir;}
 static String safe(String value,int max){if(value==null||value.trim().isEmpty())return "Sem detalhe.";value=value.replace('\n',' ').replace('\r',' ');return value.length()>max?value.substring(0,max):value;}
 static String cell(String value){if(!value.isEmpty()&&"=+-@\t\r\n".indexOf(value.charAt(0))>=0)value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
 static void record(Writer out,int index,String name,String state,String detail)throws IOException{out.write(index+","+cell(safe(name,500))+","+cell(state)+","+cell(detail)+"\r\n");}
 static Result run(Context context,StudyStore store,List<Uri> files,long group,boolean books,StudyFiles.Cancel stop,Progress progress)throws IOException{
  Result result=new Result(files.size());File folder=reports(context);result.report=new File(folder,System.currentTimeMillis()+"-"+UUID.randomUUID()+".csv");
  try(Writer report=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(result.report),"UTF-8"))){report.write("\ufeffnumero,nome,resultado,detalhe\r\n");
   for(Uri uri:files){if(stop.cancelled()){result.cancelled=true;break;}int index=result.processed()+1;String name="Material "+index;File target=null;String outcome="",detail="";
    try{if(uri==null)throw new IOException("O selector não forneceu a localização deste ficheiro.");if(group>0&&store.get("subjects",group)==null)throw new IOException("Módulo indisponível. Escolha o destino novamente.");
     try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&!c.isNull(0)&&!c.getString(0).trim().isEmpty())name=c.getString(0);}
     progress.changed(result,name);String title=name.trim();if(title.length()>240)title=title.substring(0,240);String mime=MediaFormats.mime(name,context.getContentResolver().getType(uri));
     if(books&&!BookQuery.accepts(name,mime))throw new IOException("Formato não suportado na estante: use PDF, EPUB, DOCX ou texto.");
     if(MediaFormats.kind(name,mime).equals("external")&&!Arrays.asList("doc","ppt","pptx","xls","xlsx","odt","rtf").contains(MediaFormats.ext(name)))throw new IOException("Formato não reconhecido como material didáctico.");
     target=new File(StudyFileProvider.directory(context),UUID.randomUUID()+"."+StudyFiles.extension(name));String hash;
     try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Sem acesso ao material.");hash=StudyFiles.copy(in,target,DocumentBudget.available(target.getParentFile(),name,mime),stop);}
     StudyFiles.check(stop);if(target.length()==0)throw new IOException("Material vazio.");
     List<StudyStore.Row> existing=store.rows("materials","subject=? AND hash=?",new String[]{""+group,hash},null,1);if(!existing.isEmpty()){File previous=BookDuplicates.privatePath(context,existing.get(0).s("path"));if(!previous.isFile()||!BookDuplicates.equal(previous,target,stop))throw new IOException("A cópia já registada está indisponível ou alterada; não foi tratada como duplicado.");if(!target.delete())throw new IOException("Não foi possível limpar a cópia repetida.");target=null;result.duplicates++;outcome="repetido";detail="Conteúdo já existente neste destino.";}
     else{long now=System.currentTimeMillis();store.save("materials",0,StudyStore.values("subject",group,"title",title,"author","","name",name,"mime",mime,"path",target.getPath(),"hash",hash,"bytes",target.length(),"created",now,"updated",now));target=null;result.imported++;outcome="importado";}
    }catch(Exception error){if(target!=null)target.delete();if(stop.cancelled()){result.cancelled=true;break;}result.failed++;detail=safe(error.getMessage(),500);outcome="falhou";if(result.errors.size()<10)result.errors.add(safe(name,120)+": "+detail);}
    record(report,index,name,outcome,detail);progress.changed(result,name);
   }
   report.write("\r\nseleccionados,importados,repetidos,falhados,por_processar,cancelado\r\n"+result.selected+","+result.imported+","+result.duplicates+","+result.failed+","+result.remaining()+","+result.cancelled+"\r\n");
  }
  // Keep the current report and at most four older logs; no document contents are logged.
  File[] old=folder.listFiles((dir,name)->name.endsWith(".csv"));if(old!=null){Arrays.sort(old,(a,b)->Long.compare(b.lastModified(),a.lastModified()));int kept=0;for(File f:old)if(!f.equals(result.report)&&++kept>4)f.delete();}
  return result;
 }
 private StudyImporter(){}
}
