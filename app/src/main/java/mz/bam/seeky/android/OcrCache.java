package mz.bam.seeky.android;
import android.content.*;import java.io.*;import java.util.*;
final class OcrCache {
 static StudyStore.Row row(Context c,String uri,int part){if(!c.getSharedPreferences("reading_tools",0).getBoolean("ocr_present",false))return null;try(PlusStore db=new PlusStore(c)){List<StudyStore.Row> rows=db.rows("ocr_text","uri=? AND segment=?",new String[]{uri,""+part},null,1);return rows.isEmpty()?null:rows.get(0);}}
 static String get(Context c,String uri,int part,String fingerprint){StudyStore.Row row=row(c,uri,part);return row!=null&&row.s("digest").equals(fingerprint)?row.s("value"):"";}
 static void save(Context c,String uri,int part,String fingerprint,String value){if(value.length()>1048576)throw new IllegalArgumentException("Texto reconhecido demasiado longo.");try(PlusStore db=new PlusStore(c)){db.save("ocr_text",StudyStore.values("uri",uri,"segment",part,"digest",fingerprint,"value",value,"created",System.currentTimeMillis()));}c.getSharedPreferences("reading_tools",0).edit().putBoolean("ocr_present",true).apply();ReaderStore.contentChanged(c);}
 private OcrCache(){}
}
