package mz.bam.seeky.android;
import android.content.*;import android.net.Uri;
/** Internal entry points only. No external chat URLs and no automatic document upload. */
final class SeekyAi {
 static void open(Context c,long subject){c.startActivity(new Intent(c,StudyAssistantActivity.class).putExtra("subject",subject));}
 static void reader(ReaderActivity a,String selection){if(selection!=null&&selection.length()>20000){android.widget.Toast.makeText(a,AppLanguage.text(a,"Escolha uma passagem até 20000 caracteres."),android.widget.Toast.LENGTH_LONG).show();return;}Intent i=new Intent(a,StudyAssistantActivity.class).setDataAndType(a.uri,a.mime).putExtra("name",a.name).putExtra("part",a.chrome.current()).putExtra("material",a.getIntent().getLongExtra("study_material",0)).putExtra("title",a.getIntent().getStringExtra("study_title"));if(selection!=null)i.putExtra("selection",selection);a.startActivity(i);}
 static void material(Context c,StudyStore.Row row){c.startActivity(new Intent(c,StudyAssistantActivity.class).setDataAndType(StudyFileProvider.uri(c,row.id()),row.s("mime")).putExtra("name",row.s("name")).putExtra("title",row.s("title")).putExtra("material",row.id()).putExtra("subject",row.n("subject")));}
 static void note(Context c,long id){c.startActivity(new Intent(c,StudyAssistantActivity.class).putExtra("note",id));}
}
