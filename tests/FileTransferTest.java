package mz.bam.seeky.android;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FileTransferTest {
 static int passed=0;
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
 static File root,from,to;static byte[] data=new byte[900000];
 static File source(String name)throws Exception {File f=new File(from,name);Files.write(f.toPath(),data);return f;}
 static class Control implements FileTransfer.Control {boolean cancel=false;Runnable progress;public boolean cancelled(){return cancel;}public boolean allowed(File f){try{return DiskWalker.inside(root,f);}catch(Exception e){return false;}}public void progress(long n,String phase){if(progress!=null)progress.run();}}
 interface Work {void run()throws Exception;}
 static void fails(Work w,String name)throws Exception{boolean thrown=false;try{w.run();}catch(IOException e){thrown=true;}check(thrown,name);}
 static long staged(){return Arrays.stream(to.listFiles()).filter(f->f.getName().startsWith(".seeky-transfer-")).count();}
 static FileTransfer.Result verified(File f,File d,boolean move,FileTransfer.Control c)throws IOException{return FileTransfer.run(f,d,move,c,false);}
 public static void main(String[] args)throws Exception{
  new Random(315).nextBytes(data);root=Files.createTempDirectory("seeky-transfer-").toFile();from=new File(root,"origem");to=new File(root,"destino");from.mkdir();to.mkdir();
  try{
   File f=source("Gestão 100%_?.psd");FileTransfer.Result copy=verified(f,to,false,new Control());check(f.exists()&&!copy.moved&&Arrays.equals(data,Files.readAllBytes(copy.target.toPath())),"copy arbitrary extension and Unicode name preserves original and exact bytes");
   fails(()->verified(f,to,false,new Control()),"existing destination refused");check(Arrays.equals(data,Files.readAllBytes(copy.target.toPath())),"conflict preserves destination");
   fails(()->verified(f,from,true,new Control()),"move onto itself refused");check(f.exists(),"same-folder refusal preserves source");
   File m=source("move.bin");FileTransfer.Result moved=verified(m,to,true,new Control());check(moved.moved&&!m.exists()&&Arrays.equals(data,Files.readAllBytes(moved.target.toPath())),"verified move publishes destination before removing original");
   File direct=source("direct.bin");Object inode=Files.readAttributes(direct.toPath(),java.nio.file.attribute.BasicFileAttributes.class).fileKey();FileTransfer.Result fast=FileTransfer.run(direct,to,true,new Control());check(fast.direct&&!direct.exists()&&Objects.equals(inode,Files.readAttributes(fast.target.toPath(),java.nio.file.attribute.BasicFileAttributes.class).fileKey()),"same-volume direct movement retains inode and bytes without copying");
   File fastCancel=source("direct-cancel.bin");Control fastControl=new Control();fastControl.progress=()->fastControl.cancel=true;fails(()->FileTransfer.run(fastCancel,to,true,fastControl),"cancellation after direct publish");check(fastCancel.exists()&&!new File(to,fastCancel.getName()).exists(),"direct cancellation keeps original and removes only matching alias");
   File empty=new File(from,"zero");empty.createNewFile();check(verified(empty,to,false,new Control()).target.length()==0,"empty file copy");
   File cancelFile=source("cancel.bin");Control c=new Control();c.progress=()->c.cancel=true;fails(()->verified(cancelFile,to,true,c),"cancel while copying");check(cancelFile.exists()&&!new File(to,cancelFile.getName()).exists()&&staged()==0,"cancel keeps source and cleans unpublished temporary file");
   File changed=source("changing.bin");Control mutable=new Control();boolean[] once={false};mutable.progress=()->{if(!once[0]){once[0]=true;try{Files.write(changed.toPath(),new byte[]{1,2,3});}catch(Exception e){throw new RuntimeException(e);}}};fails(()->verified(changed,to,true,mutable),"source changed during copy refused");check(changed.exists()&&!new File(to,changed.getName()).exists()&&staged()==0,"changed source preserved without publishing incomplete copy");
   File late=source("late.bin");Control lateCancel=new Control(){public void progress(long n,String phase){if(phase.equals("A verificar origem"))cancel=true;}};fails(()->verified(late,to,true,lateCancel),"cancel after publish during source verification");check(late.exists()&&Arrays.equals(data,Files.readAllBytes(new File(to,late.getName()).toPath())),"late cancellation retains both complete files");
   File raced=source("race.bin");Control race=new Control(){public void progress(long n,String phase){if(phase.equals("A verificar cópia"))try{Files.write(new File(to,"race.bin").toPath(),new byte[]{7},StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}catch(IOException e){throw new RuntimeException(e);}}};fails(()->verified(raced,to,true,race),"destination appearing during verification refused");check(raced.exists()&&Files.readAllBytes(new File(to,"race.bin").toPath())[0]==7&&staged()==0,"concurrent destination never overwritten");
   File denied=source("denied");Control gate=new Control(){public boolean allowed(File file){return false;}};fails(()->verified(denied,to,false,gate),"revoked access refused");
   File linked=new File(from,"linked");Files.createSymbolicLink(linked.toPath(),f.toPath());fails(()->verified(linked,to,false,new Control()),"symbolic source refused");
   File outside=Files.createTempDirectory("seeky-outside-").toFile();try{fails(()->verified(f,outside,false,new Control()),"destination outside root refused");}finally{outside.delete();}
   fails(()->verified(from,to,false,new Control()),"recursive folder transfer explicitly unsupported");
   File partial=new File(to,".seeky-transfer-example.part");Files.write(partial.toPath(),data);check(!FolderSnapshot.read(root,to,true).paths.contains(partial.getPath()),"discovery excludes staging files even with hidden files enabled");
   File a=source("Árvore.txt"),b=source("beira.txt");File dir=new File(from,"Z_folder");dir.mkdir();check(ExplorerOrder.by(0).compare(dir,a)<0&&ExplorerOrder.by(0).compare(a,b)<0,"folders first and accent-aware name order");
   a.setLastModified(100000);b.setLastModified(200000);check(ExplorerOrder.by(1).compare(b,a)<0,"recent folder sort");Files.write(b.toPath(),new byte[1]);check(ExplorerOrder.by(2).compare(a,b)<0,"size sort");
   check(staged()==1,"only fixture staging file remains after failed operations");
  } finally {try(java.util.stream.Stream<Path> stream=Files.walk(root.toPath())){stream.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
  System.out.println("Passed "+passed+" checks");
 }
}
