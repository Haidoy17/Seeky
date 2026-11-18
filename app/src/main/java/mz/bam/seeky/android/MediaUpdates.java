package mz.bam.seeky.android;

import android.content.Context;
import android.media.MediaScannerConnection;
import java.io.File;

final class MediaUpdates {
    static void changed(Context context,File... files){
        String[] paths=new String[files.length];
        for(int i=0;i<files.length;i++)paths[i]=files[i].getAbsolutePath();
        try{MediaScannerConnection.scanFile(context.getApplicationContext(),paths,null,null);}
        catch(Exception e){AppDiagnostics.record(context,"media-update",e);}
    }
    private MediaUpdates(){}
}
