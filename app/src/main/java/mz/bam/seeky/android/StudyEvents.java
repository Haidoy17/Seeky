package mz.bam.seeky.android;

import android.os.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Notify the visible library after a reader/service commits progress. */
final class StudyEvents {
    interface Listener {void changed();}
    static final Handler main=new Handler(Looper.getMainLooper());
    static final List<WeakReference<Listener>> listeners=new ArrayList<>();
    static void add(Listener listener){remove(listener);listeners.add(new WeakReference<>(listener));}
    static void remove(Listener listener){listeners.removeIf(ref->ref.get()==null||ref.get()==listener);}
    static void changed(){main.post(()->{for(WeakReference<Listener> ref:new ArrayList<>(listeners)){Listener listener=ref.get();if(listener!=null)listener.changed();}listeners.removeIf(ref->ref.get()==null);});}
    private StudyEvents(){}
}
