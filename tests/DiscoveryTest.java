package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real local filesystem checks; Android FileObserver/MediaStore still need device tests. */
public class DiscoveryTest {
    static int passed=0;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception {
        ChangeQueue queue=new ChangeQueue(4);
        for(int i=0;i<1000;i++)queue.offer("/Downloads/new.pdf",100);
        check(queue.size()==1,"1000 writes coalesce into one path");
        queue.offer("/Documents/other.txt",10);
        check(queue.ready(20,10).equals(Collections.singletonList("/Documents/other.txt")),"long write does not block independent file");
        check(queue.ready(99,10).isEmpty(),"settle time avoids premature open");
        check(queue.ready(100,10).equals(Collections.singletonList("/Downloads/new.pdf")),"closed file becomes ready");
        for(int i=0;i<7;i++)queue.offer("/file"+i,0);
        check(queue.size()==4&&queue.overflowed(),"burst queue is bounded and requests reconciliation");
        check(!queue.overflowed(),"overflow notification consumed once");
        check(queue.ready(1,2).size()==2&&queue.size()==2,"drain uses bounded batches");
        Path root=Files.createTempDirectory("seeky-discovery-"),outside=Files.createTempDirectory("seeky-private-");
        try {
            Path dir=Files.createDirectory(root.resolve("Download"));
            Path nested=Files.createDirectory(dir.resolve("nested"));
            Files.write(nested.resolve("not-read.txt"),new byte[]{1});
            Path huge=dir.resolve("huge-archive.docx");try(RandomAccessFile file=new RandomAccessFile(huge.toFile(),"rw")){file.setLength(4L*1024*1024*1024);}
            Files.write(dir.resolve(".hidden.psd"),new byte[]{2});
            Files.createSymbolicLink(dir.resolve("private-link"),outside);
            long start=System.nanoTime();FolderSnapshot first=FolderSnapshot.read(root.toFile(),dir.toFile(),true);long elapsed=System.nanoTime()-start;
            check(first.files.size()==2&&first.directories.size()==1,"shallow listing returns files and directories separately");
            check(first.paths.contains(huge.toString()),"4 GiB document discovered without extracting its contents");
            check(first.files.stream().noneMatch(f->f.getName().equals("not-read.txt")),"shallow refresh avoids recursive rescan");
            check(FolderSnapshot.read(root.toFile(),dir.toFile(),false).files.size()==1,"hidden preference respected");
            try(WatchService watcher=FileSystems.getDefault().newWatchService()){
                dir.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_DELETE,StandardWatchEventKinds.ENTRY_MODIFY);
                Path fresh=dir.resolve("new-unknown.format");Files.write(fresh,new byte[]{3});
                WatchKey event=watcher.poll(3,TimeUnit.SECONDS);check(event!=null,"OS reports real new-file event");if(event!=null){event.pollEvents();event.reset();}
                FolderSnapshot second=FolderSnapshot.read(root.toFile(),dir.toFile(),true);check(second.paths.contains(fresh.toString()),"new file appears after refreshing only its parent");
                Path renamed=dir.resolve("renamed.format");Files.move(fresh,renamed);FolderSnapshot third=FolderSnapshot.read(root.toFile(),dir.toFile(),true);check(third.paths.contains(renamed.toString())&&!third.paths.contains(fresh.toString()),"rename detected by parent snapshot");
                Files.delete(renamed);check(!FolderSnapshot.read(root.toFile(),dir.toFile(),true).paths.contains(renamed.toString()),"deletion detected without reading other file contents");
            }
            boolean rejected=false;try{FolderSnapshot.read(root.toFile(),outside.toFile(),true);}catch(IOException e){rejected=true;}check(rejected,"outside volume rejected");
            check(!first.paths.stream().anyMatch(p->p.contains("private-link")),"symlink excluded from metadata discovery");
            System.out.println("Metadata-only shallow listing with 4 GiB sparse file: "+elapsed/1000000.0+" ms on host, not Android");
        }finally{for(Path base:new Path[]{root,outside})try(java.util.stream.Stream<Path> paths=Files.walk(base)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
        System.out.println("Passed "+passed+" discovery checks");
    }
}
