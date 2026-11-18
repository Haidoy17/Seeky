package mz.bam.seeky.android;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TrashTest {
 static int passed=0;static File root,folder;static byte[] payload="ficheiro importante — Beira".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 interface Action {void run()throws Exception;}
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static void rejected(Action action,String name)throws Exception {boolean failed=false;try{action.run();}catch(IOException e){failed=true;}check(failed,name);}
 static class Control implements SafeTrash.Control {boolean cancel=false;public boolean allowed(File f){try{return DiskWalker.inside(root,f);}catch(Exception ex){return false;}}public boolean cancelled(){return cancel;}}
 static File file(String name)throws Exception {File f=new File(folder,name);Files.write(f.toPath(),payload);return f;}
 static void clean(File directory)throws Exception {try(java.util.stream.Stream<Path> paths=Files.walk(directory.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
 public static void main(String[]args)throws Exception {
  root=Files.createTempDirectory("seeky-trash-test-").toFile();folder=new File(root,"Documents");folder.mkdir();Control control=new Control();
  try{
   File original=file("Relatório.pdf");SafeTrash.Plan plan=SafeTrash.plan(root,original,control);SafeTrash.Item item=SafeTrash.put(plan,control);
   check(!original.exists()&&item.data.isFile()&&Arrays.equals(payload,Files.readAllBytes(item.data.toPath())),"recoverable removal moves real file without changing bytes");
   check(new File(item.directory,"record.properties").isFile(),"durable original-path journal accompanies the removed file");
   List<SafeTrash.Item> restart=SafeTrash.list(root,new Control());check(restart.size()==1&&restart.get(0).original.equals(original)&&restart.get(0).name.equals("Relatório.pdf"),"fresh engine reconstructs recoverable item from disk");
   check(!FolderSnapshot.read(root,root,true).directories.contains(new File(root,SafeTrash.DIRECTORY)),"explorer hides recycle folder even with hidden files enabled");
   List<File> indexed=new ArrayList<>();DiskWalker.walk(root,true,1000,Long.MAX_VALUE,new DiskWalker.Visitor(){public boolean cancelled(){return false;}public void unreadable(File f){}public void file(File f){indexed.add(f);}});check(!indexed.contains(item.data),"recursive discovery excludes recycle data and journal");
   File restored=SafeTrash.restore(restart.get(0),control);check(restored.equals(original)&&Arrays.equals(payload,Files.readAllBytes(restored.toPath()))&&SafeTrash.list(root,control).isEmpty(),"restore after restart returns exact original and removes completed journal");
   SafeTrash.Item first=SafeTrash.put(SafeTrash.plan(root,original,control),control);file("Relatório.pdf");SafeTrash.Item second=SafeTrash.put(SafeTrash.plan(root,original,control),control);check(!first.id.equals(second.id)&&SafeTrash.list(root,control).size()==2,"same original name gets independent recycle entries");
   file("Relatório.pdf");rejected(()->SafeTrash.restore(first,control),"restore refuses an occupied original path");check(first.data.exists()&&Arrays.equals(payload,Files.readAllBytes(original.toPath())),"restore conflict preserves both files");
   File changed=file("changing.txt");SafeTrash.Plan old=SafeTrash.plan(root,changed,control);Files.write(changed.toPath(),new byte[]{1,2,3});rejected(()->SafeTrash.put(old,control),"source modified after confirmation is refused");check(changed.isFile(),"changed source remains available");
   File cancelled=file("cancel.txt");SafeTrash.Plan cp=SafeTrash.plan(root,cancelled,control);Control stop=new Control();stop.cancel=true;rejected(()->SafeTrash.put(cp,stop),"cancel before removal");check(cancelled.isFile(),"cancel preserves original");
   File midway=file("journal-interrupt.txt");SafeTrash.Plan mp=SafeTrash.plan(root,midway,control);Control revoke=new Control(){public boolean allowed(File f){if(f.equals(midway)){File trash=new File(root,SafeTrash.DIRECTORY);File[] entries=trash.listFiles();if(entries!=null)for(File dir:entries)if(new File(dir,"record.properties").exists()&&!new File(dir,"data").exists())return false;}return super.allowed(f);}};
   int previous=SafeTrash.list(root,control).size();rejected(()->SafeTrash.put(mp,revoke),"access revoked after journal creation but before move");check(midway.isFile()&&SafeTrash.list(root,control).size()==previous,"journal interruption preserves original and does not publish a false recycle item");
   File link=new File(folder,"linked.pdf");Files.createSymbolicLink(link.toPath(),original.toPath());rejected(()->SafeTrash.plan(root,link,control),"symbolic source refused");rejected(()->SafeTrash.plan(root,folder,control),"directory deletion is not recursive or implicit");
   File dataDir=new File(root,"Android/data/private");dataDir.mkdirs();File privateFile=new File(dataDir,"private.txt");Files.write(privateFile.toPath(),payload);rejected(()->SafeTrash.plan(root,privateFile,control),"protected Android application area refused");
   rejected(()->SafeTrash.plan(root,first.data,control),"recycle data cannot be recycled recursively");
   SafeTrash.Item corrupt=SafeTrash.put(SafeTrash.plan(root,file("damaged.txt"),control),control);Files.write(corrupt.data.toPath(),new byte[]{1});rejected(()->SafeTrash.purge(corrupt,control),"changed recycle payload is refused before permanent removal");check(corrupt.data.exists(),"changed recycle payload left untouched");
   SafeTrash.Item tampered=SafeTrash.put(SafeTrash.plan(root,file("tamper.txt"),control),control);File record=new File(tampered.directory,"record.properties");Properties properties=new Properties();try(InputStream in=new FileInputStream(record)){properties.load(in);}properties.setProperty("original","/outside/secret.txt");try(OutputStream out=new FileOutputStream(record)){properties.store(out,"tampered fixture");}rejected(()->SafeTrash.restore(tampered,control),"tampered journal cannot restore outside allowed volume");check(tampered.data.exists(),"tampered journal does not lose payload");
   File missingFile=file("parent.txt");SafeTrash.Item missing=SafeTrash.put(SafeTrash.plan(root,missingFile,control),control);File renamed=new File(root,"old-Documents");Files.move(folder.toPath(),renamed.toPath());rejected(()->SafeTrash.restore(missing,control),"missing original folder fails clearly");check(missing.data.exists(),"missing folder retains recoverable data");Files.move(renamed.toPath(),folder.toPath());
   SafeTrash.Item purge=SafeTrash.put(SafeTrash.plan(root,file("permanent.txt"),control),control);File unrelated=new File(purge.directory,"unrelated.keep");Files.write(unrelated.toPath(),payload);SafeTrash.purge(purge,control);check(!purge.data.exists()&&unrelated.exists(),"permanent delete only removes confirmed payload and leaves unknown extra file");
   check(original.exists()&&first.data.exists()&&second.data.exists(),"permanent delete leaves other original/recycle entries untouched");
   File unknown=new File(new File(root,SafeTrash.DIRECTORY),"unrecognised");unknown.mkdir();File recoverable=new File(unknown,"data");Files.write(recoverable.toPath(),payload);SafeTrash.list(root,control);check(recoverable.exists(),"unknown or incomplete recycle records are never auto-purged");
   SafeTrash.Item stopRestore=SafeTrash.put(SafeTrash.plan(root,file("restore-cancel.txt"),control),control);rejected(()->SafeTrash.restore(stopRestore,stop),"cancelled restore");check(stopRestore.data.exists(),"cancelled restore keeps recycle data");
   for(int width:new int[]{1,120,320,720,1080,2400})for(float density:new float[]{1,2,3}){check(ReaderLayout.width(width,density)>0&&ReaderLayout.width(width,density)<=width,"preview width remains within display "+width+" / "+density);}
   check(ReaderLayout.height(1536,2)<1536&&ReaderLayout.height(1536,2)>0,"preview leaves surrounding space for rounded card");
  }finally{clean(root);}
  System.out.println("Passed "+passed+" trash/reader geometry checks");
 }
}
