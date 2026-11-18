package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Real breadth-first traversal, independent of Android for filesystem regression tests. */
final class DiskWalker {
    interface Visitor {
        void file(File file) throws Exception;
        void unreadable(File directory) throws Exception;
        boolean cancelled();
    }
    static final class Summary { int files, folders, skipped; boolean limited; }
    static boolean inside(File root, File child) throws IOException {
        String r=root.getCanonicalPath(), c=child.getCanonicalPath();
        return c.equals(r)||c.startsWith(r+File.separator);
    }
    static boolean protectedPath(File root, File file) {
        String rel=root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');
        return rel.equals("Android/data")||rel.startsWith("Android/data/")||rel.equals("Android/obb")||rel.startsWith("Android/obb/");
    }
    static Summary walk(File input, boolean hidden, int maxFiles, long deadline, Visitor visitor) throws Exception {
        File root=input.getCanonicalFile();
        if(!root.isDirectory()||!root.canRead())throw new IOException("Armazenamento indisponível: "+root);
        ArrayDeque<File> queue=new ArrayDeque<>();queue.add(root);
        Summary result=new Summary();
        while(!queue.isEmpty()) {
            if(visitor.cancelled())throw new InterruptedIOException("Operação cancelada");
            if(System.nanoTime()>deadline){result.limited=true;break;}
            File dir=queue.removeFirst();result.folders++;
            File[] children=dir.listFiles();
            if(children==null){result.skipped++;visitor.unreadable(dir);continue;}
            // Favor common locations naturally through breadth-first traversal.
            for(File f:children) {
                if(visitor.cancelled())throw new InterruptedIOException("Operação cancelada");
                if(System.nanoTime()>deadline){result.limited=true;return result;}
                if(SafeTrash.internal(f)||f.getName().startsWith(".seeky-transfer-")||(!hidden&&f.getName().startsWith("."))||Files.isSymbolicLink(f.toPath())||protectedPath(root,f))continue;
                if(f.isDirectory()) {
                    if(queue.size()+result.folders>=100000)throw new IOException("Limite de pastas atingido; catálogo anterior conservado.");
                    queue.addLast(f);
                } else if(f.isFile()) {
                    if(result.files>=maxFiles){result.limited=true;return result;}
                    visitor.file(f);result.files++;
                }
            }
        }
        return result;
    }
}
