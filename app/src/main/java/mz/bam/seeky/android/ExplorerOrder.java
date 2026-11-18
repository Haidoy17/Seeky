package mz.bam.seeky.android;

import java.io.File;
import java.util.Comparator;

final class ExplorerOrder {
    static Comparator<File> by(int order){return (a,b)->{
        if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;
        int result=order==1?Long.compare(b.lastModified(),a.lastModified()):order==2?Long.compare(b.length(),a.length()):Rules.normalize(a.getName()).compareTo(Rules.normalize(b.getName()));
        return result==0?a.getAbsolutePath().compareTo(b.getAbsolutePath()):result;
    };}
    private ExplorerOrder(){}
}
