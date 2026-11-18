package mz.bam.seeky.android;
import android.os.*;import java.util.*;
final class TaskEvents {
    static final Handler main=new Handler(Looper.getMainLooper());static final Set<Runnable> listeners=new HashSet<>();
    static void add(Runnable r){listeners.add(r);}static void remove(Runnable r){listeners.remove(r);}
    static void changed(){main.post(()->{for(Runnable r:new ArrayList<>(listeners))r.run();});}
    private TaskEvents(){}
}
