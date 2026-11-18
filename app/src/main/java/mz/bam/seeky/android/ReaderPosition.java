package mz.bam.seeky.android;

import android.content.SharedPreferences;
import android.os.Handler;

/** Throttles scroll writes, flushing the final position when the Activity pauses. */
final class ReaderPosition {
    final long epoch=WorkspaceEpoch.value;
    final SharedPreferences prefs;
    final Handler handler;
    SharedPreferences.Editor pending;
    boolean scheduled;
    final Runnable save=this::flush;
    ReaderPosition(SharedPreferences prefs,Handler handler){this.prefs=prefs;this.handler=handler;}
    void put(String key,int value){if(epoch!=WorkspaceEpoch.value)return;
        if(pending==null)pending=prefs.edit();
        pending.putInt(key,value);
        if(!scheduled){scheduled=true;handler.postDelayed(save,1500);}
    }
    void flush(){
        handler.removeCallbacks(save);scheduled=false;if(epoch!=WorkspaceEpoch.value){pending=null;return;}
        if(pending!=null){pending.apply();pending=null;}
    }
}
