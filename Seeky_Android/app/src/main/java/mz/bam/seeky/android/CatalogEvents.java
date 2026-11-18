package mz.bam.seeky.android;

import android.os.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process updates, without exported receivers or retained Activity references. */
final class CatalogEvents {
    interface Listener {void changed(String status);}
    static final Handler main=new Handler(Looper.getMainLooper());
    static final CopyOnWriteArrayList<WeakReference<Listener>> listeners=new CopyOnWriteArrayList<>();
    static volatile String status="Detecção ainda não iniciada";
    static void add(Listener l){remove(l);listeners.add(new WeakReference<>(l));}
    static void remove(Listener l){for(WeakReference<Listener> w:listeners)if(w.get()==null||w.get()==l)listeners.remove(w);}
    static void publish(String text){status=text;main.post(()->{for(WeakReference<Listener> w:listeners){Listener l=w.get();if(l==null)listeners.remove(w);else l.changed(text);}});}
}
