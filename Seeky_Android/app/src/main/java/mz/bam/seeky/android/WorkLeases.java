package mz.bam.seeky.android;
import android.app.Activity;import java.util.*;
/** Weak UI leases keep cache cleanup from touching open readers and pending exports. */
final class WorkLeases {
    static final Map<Activity,Boolean> activities=Collections.synchronizedMap(new WeakHashMap<>());
    static void created(Activity a){activities.put(a,true);}static void destroyed(Activity a){activities.remove(a);}
    static List<Activity> snapshot(){synchronized(activities){return new ArrayList<>(activities.keySet());}}
    static boolean readerOpen(){for(Activity a:snapshot())if(a instanceof ReaderActivity&&!a.isDestroyed())return true;return false;}
    private WorkLeases(){}
}
