package mz.bam.seeky.android;
import android.os.*;import java.lang.ref.WeakReference;import java.util.concurrent.CopyOnWriteArrayList;
final class FocusEvents {
 interface Listener{void changed();}static final Handler main=new Handler(Looper.getMainLooper());static final CopyOnWriteArrayList<WeakReference<Listener>> listeners=new CopyOnWriteArrayList<>();
 static void add(Listener l){remove(l);listeners.add(new WeakReference<>(l));}static void remove(Listener l){for(WeakReference<Listener> w:listeners)if(w.get()==null||w.get()==l)listeners.remove(w);}
 static void publish(){main.post(()->{for(WeakReference<Listener> w:listeners){Listener l=w.get();if(l!=null)l.changed();else listeners.remove(w);}});}
}
