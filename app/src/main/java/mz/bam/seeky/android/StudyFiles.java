package mz.bam.seeky.android;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;
final class StudyFiles {
 interface Cancel {boolean cancelled();}
 static void check(Cancel cancel)throws InterruptedIOException {if(cancel.cancelled())throw new InterruptedIOException("Importação cancelada.");}
 static String copy(InputStream in,File target,long max,Cancel cancel)throws IOException {
  check(cancel);if(max<0)throw new IOException("Limite de importação inválido.");
  File part=new File(target.getPath()+".part");boolean owned=false;
  if(Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Destino ocupado.");
  try {
   // CREATE_NEW also refuses dangling links and another import's staging file.
   Files.createFile(part.toPath());owned=true;
   MessageDigest hash=MessageDigest.getInstance("SHA-256");
   try(FileOutputStream out=new FileOutputStream(part)) {
    byte[] b=new byte[65536];long total=0;
    while(true){check(cancel);int n=in.read(b);check(cancel);if(n==-1)break;total+=n;if(total>max)throw new IOException("Material acima do limite de "+max/1048576+" MB.");out.write(b,0,n);hash.update(b,0,n);}
    out.getFD().sync();
   }
   check(cancel);Files.move(part.toPath(),target.toPath());owned=false;
   StringBuilder s=new StringBuilder();for(byte x:hash.digest())s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();
  }catch(Exception e){throw e instanceof IOException?(IOException)e:new IOException(e);}
  finally{if(owned)Files.deleteIfExists(part.toPath());}
 }
 static void validateArchive(java.util.zip.ZipFile zip,java.util.List<Long> ids)throws IOException {if(zip.size()>11000)throw new IOException("Demasiadas partes.");java.util.Set<String> expected=new java.util.HashSet<>(),seen=new java.util.HashSet<>();expected.add("study.json");for(long id:ids)if(id<=0||!expected.add("materials/"+id))throw new IOException("Identificador de material repetido ou inválido.");java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=zip.entries();long total=0;while(entries.hasMoreElements()){java.util.zip.ZipEntry entry=entries.nextElement();if(!expected.contains(entry.getName())||!seen.add(entry.getName()))throw new IOException("Parte desconhecida ou repetida.");long max=entry.getName().equals("study.json")?8388608:134217728;if(entry.getSize()>max)throw new IOException("Parte demasiado grande.");total+=Math.max(0,entry.getSize());if(total>536870912)throw new IOException("Cópia demasiado grande.");}if(!seen.containsAll(expected))throw new IOException("Faltam materiais na cópia.");}
 static String extension(String name){int dot=name.lastIndexOf('.');String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);return ext.matches("[a-z0-9]{1,12}")?ext:"bin";}
 static String hash(File file)throws IOException {try(InputStream in=new FileInputStream(file)){MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}catch(Exception e){throw new IOException(e);}}
}
