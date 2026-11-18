package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Same-volume recoverable removal. A durable journal precedes the move; never overwrites. */
final class SafeTrash {
 static final String DIRECTORY=".seeky-trash";
 interface Control {boolean allowed(File file);boolean cancelled();}
 static final class Plan {
  final File root,source;final long size,modified;final Object key;
  Plan(File r,File s,BasicFileAttributes a){root=r;source=s;size=a.size();modified=a.lastModifiedTime().toMillis();key=a.fileKey();}
 }
 static final class Item {
  final File root,directory,data,original;final String id,name;final long size,modified,created;
  Item(File root,File directory,File original,long size,long modified,long created){this.root=root;this.directory=directory;data=new File(directory,"data");this.original=original;id=directory.getName();name=original.getName();this.size=size;this.modified=modified;this.created=created;}
 }
 static boolean internal(File file){for(File p=file;p!=null;p=p.getParentFile())if(p.getName().equals(DIRECTORY))return true;return false;}
 static void check(Control c)throws IOException {if(c.cancelled())throw new InterruptedIOException("Operação interrompida.");}
 static void permit(File root,File file,Control c)throws IOException {
  check(c);if(!c.allowed(file)||Files.isSymbolicLink(file.toPath())||!DiskWalker.inside(root,file)||DiskWalker.protectedPath(root,file.getCanonicalFile()))throw new IOException("Caminho sem acesso autorizado: "+file);
  // Do not traverse symbolic parents, including a replaced trash container.
  for(File p=file.getAbsoluteFile();p!=null&&!p.equals(root.getAbsoluteFile());p=p.getParentFile())if(Files.isSymbolicLink(p.toPath()))throw new IOException("Ligação simbólica recusada.");
 }
 static Plan plan(File root,File source,Control c)throws IOException {
  File volume=root.getCanonicalFile();permit(volume,source,c);if(internal(source))throw new IOException("Use os controlos próprios da lixeira.");
  BasicFileAttributes a=Files.readAttributes(source.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!a.isRegularFile())throw new IOException("Apenas ficheiros regulares podem ser enviados para a lixeira.");return new Plan(volume,source.getCanonicalFile(),a);
 }
 static void same(Plan plan)throws IOException {BasicFileAttributes now=Files.readAttributes(plan.source.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!now.isRegularFile()||now.size()!=plan.size||now.lastModifiedTime().toMillis()!=plan.modified||!Objects.equals(now.fileKey(),plan.key))throw new IOException("O ficheiro mudou depois da confirmação. Reveja a selecção.");}
 static Item put(Plan plan,Control c)throws IOException {
  permit(plan.root,plan.source,c);same(plan);File trash=new File(plan.root,DIRECTORY);permit(plan.root,trash,c);if(!trash.exists())Files.createDirectory(trash.toPath());if(!trash.isDirectory())throw new IOException("Lixeira indisponível.");
  File directory=new File(trash,UUID.randomUUID().toString());Files.createDirectory(directory.toPath());File journal=new File(directory,"record.properties");boolean moved=false;
  try{Properties p=new Properties();p.setProperty("original",plan.source.getPath());p.setProperty("size",Long.toString(plan.size));p.setProperty("modified",Long.toString(plan.modified));long created=System.currentTimeMillis();p.setProperty("created",Long.toString(created));
   try(FileOutputStream out=new FileOutputStream(journal)){p.store(out,"Seeky recoverable removal v1");out.getFD().sync();}
   permit(plan.root,plan.source,c);permit(plan.root,directory,c);same(plan);Files.move(plan.source.toPath(),new File(directory,"data").toPath());moved=true;return new Item(plan.root,directory,plan.source,plan.size,plan.modified,created);
  }finally{if(!moved){Files.deleteIfExists(journal.toPath());Files.deleteIfExists(directory.toPath());}}
 }
 static Item read(File root,File directory,Control c)throws IOException {
  root=root.getCanonicalFile();File trash=new File(root,DIRECTORY);permit(root,trash,c);permit(root,directory,c);
  if(!directory.getParentFile().getCanonicalFile().equals(trash.getCanonicalFile())||!directory.getName().matches("[a-f0-9-]{36}"))throw new IOException("Registo de lixeira inválido.");
  File journal=new File(directory,"record.properties"),data=new File(directory,"data");permit(root,journal,c);permit(root,data,c);if(!journal.isFile()||journal.length()>16384||!data.isFile())throw new IOException("Registo de lixeira incompleto.");
  Properties p=new Properties();try(InputStream in=new FileInputStream(journal)){p.load(in);}File original=new File(p.getProperty("original",""));permit(root,original,c);if(internal(original)||!Rules.validName(original.getName()))throw new IOException("Origem inválida.");
  try{long size=Long.parseLong(p.getProperty("size")),modified=Long.parseLong(p.getProperty("modified")),created=Long.parseLong(p.getProperty("created"));if(size<0||data.length()!=size||data.lastModified()!=modified)throw new IOException("O ficheiro da lixeira mudou. Não foi tocado.");return new Item(root,directory,original,size,modified,created);}catch(NumberFormatException e){throw new IOException("Registo de lixeira danificado.",e);}
 }
 static List<Item> list(File root,Control c)throws IOException {
  List<Item> items=new ArrayList<>();File trash=new File(root.getCanonicalFile(),DIRECTORY);permit(root.getCanonicalFile(),trash,c);if(!trash.exists())return items;File[] entries=trash.listFiles();if(entries==null)throw new IOException("Sem acesso à lixeira deste volume.");int n=0;for(File entry:entries){check(c);if(n++>=10000)break;try{items.add(read(root,entry,c));}catch(InterruptedIOException e){throw e;}catch(IOException ignored){/* Unknown/damaged records are never deleted automatically. */}}
  items.sort((a,b)->Long.compare(b.created,a.created));return items;
 }
 static Item current(Item item,Control c)throws IOException {Item now=read(item.root,item.directory,c);if(!now.original.equals(item.original)||now.size!=item.size||now.modified!=item.modified||now.created!=item.created)throw new IOException("O registo mudou depois da confirmação.");return now;}
 static File restore(Item item,Control c)throws IOException {
  item=current(item,c);File parent=item.original.getParentFile();if(parent==null||!parent.isDirectory())throw new IOException("A pasta original está indisponível. Reponha-a antes de restaurar.");permit(item.root,parent,c);permit(item.root,item.original,c);if(Files.exists(item.original.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Já existe um ficheiro no destino. Nada foi substituído.");Files.move(item.data.toPath(),item.original.toPath());cleanup(item);return item.original;
 }
 static void purge(Item item,Control c)throws IOException {item=current(item,c);permit(item.root,item.data,c);Files.delete(item.data.toPath());cleanup(item);}
 private static void cleanup(Item item){try{Files.deleteIfExists(new File(item.directory,"record.properties").toPath());Files.deleteIfExists(item.directory.toPath());}catch(IOException ignored){/* Operation already committed; do not touch extra files. */}}
 private SafeTrash(){}
}
