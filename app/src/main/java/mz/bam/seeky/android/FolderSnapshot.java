package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** A shallow metadata listing. It never opens a file's contents. */
final class FolderSnapshot {
    final File directory;
    final List<File> files=new ArrayList<>(),directories=new ArrayList<>();
    final Set<String> paths=new HashSet<>();
    final long modified;
    private FolderSnapshot(File directory,long modified){this.directory=directory;this.modified=modified;}
    static FolderSnapshot read(File root,File directory,boolean hidden)throws IOException {
        File canonical=directory.getCanonicalFile();
        if(!DiskWalker.inside(root,canonical)||DiskWalker.protectedPath(root,canonical)||Files.isSymbolicLink(directory.toPath()))throw new IOException("Pasta fora do armazenamento permitido");
        long start=canonical.lastModified();File[] list=canonical.listFiles();
        if(list==null)throw new IOException("Pasta indisponível: "+canonical);
        FolderSnapshot out=new FolderSnapshot(canonical,start);
        for(File file:list){
            if(SafeTrash.internal(file)||file.getName().startsWith(".seeky-transfer-")||(!hidden&&file.getName().startsWith("."))||Files.isSymbolicLink(file.toPath())||DiskWalker.protectedPath(root,file))continue;
            if(file.isDirectory())out.directories.add(file);
            else if(file.isFile()){out.files.add(file);out.paths.add(file.getAbsolutePath());}
        }
        return out;
    }
}
