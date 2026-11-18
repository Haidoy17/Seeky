package mz.bam.seeky.android;

import java.lang.ref.WeakReference;
import java.util.*;

/** Main-thread observers retain no Activity or document text. */
final class SpeechEvents {
    interface Listener { void changed(); }
    static final class State {
        final String uri, token, message; final int part, total, offset,end; final boolean active, paused,loading;
        State(String uri,String token,String message,int part,int total,int offset,boolean active,boolean paused){this(uri,token,message,part,total,offset,active,paused,false);}
        State(String uri,String token,String message,int part,int total,int offset,boolean active,boolean paused,boolean loading) {
            this(uri,token,message,part,total,offset,offset,active,paused,loading);
        }
        State(String uri,String token,String message,int part,int total,int offset,int end,boolean active,boolean paused,boolean loading){
            this.end=end;this.loading=loading;this.uri=uri;this.token=token;this.message=message;this.part=part;this.total=total;this.offset=offset;this.active=active;this.paused=paused;
        }
    }
    static volatile State state=new State("","","",0,0,0,false,false);
    static final List<WeakReference<Listener>> listeners=new ArrayList<>();
    static void add(Listener listener) { remove(listener);listeners.add(new WeakReference<>(listener)); }
    static void remove(Listener listener) { listeners.removeIf(r->r.get()==null || r.get()==listener); }
    static void publish(State next) {
        state=next;
        for(WeakReference<Listener> ref:new ArrayList<>(listeners)) { Listener listener=ref.get();if(listener!=null)listener.changed(); }
        listeners.removeIf(r->r.get()==null);
    }
    private SpeechEvents() {}
}
