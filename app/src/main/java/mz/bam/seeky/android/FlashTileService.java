package mz.bam.seeky.android;
import android.service.quicksettings.*;import android.content.Intent;import android.app.PendingIntent;import android.os.Build;
/** User-invoked Quick Settings entry; no background Activity launch loop. */
public final class FlashTileService extends TileService {
 public void onStartListening(){super.onStartListening();Tile t=getQsTile();if(t!=null){t.setLabel("Seeky Flash");t.setState(Tile.STATE_INACTIVE);t.updateTile();}}
 public void onClick(){super.onClick();Intent i=new Intent(this,FlashActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,901,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));else startActivityAndCollapse(i);}
}
