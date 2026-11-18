package mz.bam.seeky.android;
import android.content.*;import android.os.*;import android.text.*;import android.widget.*;import org.json.*;import java.util.*;
/** Debounced note drafts survive a closed form/process without claiming the note was saved. */
final class StudyDraft {
 static final java.util.concurrent.ExecutorService WRITES=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"seeky-drafts");t.setDaemon(true);return t;});
 final MainActivity host;
 final Context c;final SharedPreferences prefs;final Handler handler;final String key;final EditText[] fields;volatile boolean cleared=false;final Runnable write=this::persist;
 StudyDraft(MainActivity a,String key,LinearLayout root,EditText... fields){host=a;c=a.getApplicationContext();prefs=c.getSharedPreferences("studies",0);handler=a.handler;this.key="note_draft_"+key;this.fields=fields;String previous=prefs.getString(this.key,"");for(StudyDraft old:new java.util.ArrayList<>(a.noteDrafts))if(old.key.equals(this.key)){previous=old.snapshot();old.cleared=true;handler.removeCallbacks(old.write);a.noteDrafts.remove(old);}a.noteDrafts.add(this);if(!previous.isEmpty())try{JSONArray saved=new JSONArray(previous);for(int i=0;i<fields.length;i++)fields[i].setText(saved.optString(i,fields[i].getText().toString()));root.addView(a.label("Rascunho recuperado. Toque em Guardar para concluir a anotação.",12,a.accent),0);}catch(Exception ignored){}for(EditText f:fields)f.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!cleared){handler.removeCallbacks(write);handler.postDelayed(write,600);}}public void afterTextChanged(Editable e){}});}
 String snapshot(){JSONArray data=new JSONArray();for(EditText f:fields)data.put(f.getText().toString());return data.toString();}
 void persist(){if(cleared)return;handler.removeCallbacks(write);final String value=snapshot();WRITES.execute(()->{if(cleared||value.equals(prefs.getString(key,"")))return;if(prefs.edit().putString(key,value).commit())StudyState.bump(c);});}
 void clear(){cleared=true;host.noteDrafts.remove(this);handler.removeCallbacks(write);WRITES.execute(()->{if(prefs.contains(key)&&prefs.edit().remove(key).commit())StudyState.bump(c);});}
}
