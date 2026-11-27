package mz.bam.seeky.android;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.60: a remembered phrase finds its verse in the Flash; the passage opens with its options and the whole chapter marks the verse. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows={SpeechServiceTest.ManualTts.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class Find560Test {
    Application app;
    @Before public void setup(){
        WorkspaceEpoch.value=0;WorkspaceEpoch.restoring=false;BibleFinder.forget();
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("seeky",0).edit().clear().apply();
        try(PlusStore db=new PlusStore(app)){
            db.getWritableDatabase().delete("bible_verses",null,null);db.getWritableDatabase().delete("bible_sources",null,null);
            long s=db.save("bible_sources",StudyStore.values("title","Almeida Teste","created",1));
            String[][] v={{"João","1","14","E o Verbo se fez carne, e habitou entre nós, cheio de graça e de verdade."},{"João","1","16","E todos nós recebemos também da sua plenitude, e graça por graça."},{"João","1","17","Porque a lei foi dada por Moisés; a graça e a verdade vieram por Jesus Cristo."},
                {"João","3","16","Porque Deus amou o mundo de tal maneira que deu o seu Filho unigénito."},{"Colossenses","2","9","Porque nele habita corporalmente toda a plenitude da divindade."},{"Salmos","23","1","O Senhor é o meu pastor; nada me faltará."}};
            for(String[] x:v)db.save("bible_verses",StudyStore.values("source",s,"book",x[0],"chapter",Integer.parseInt(x[1]),"verse",Integer.parseInt(x[2]),"value",x[3]));
        }
    }
    void drain()throws Exception{for(int i=0;i<5;i++){FlashPassages.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}}

    static TextView find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return (TextView)v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=find(g.getChildAt(i),text);if(t!=null)return t;}}return null;}
    @Test public void aPhraseFindsItsVerseInTheFlashAndOpensThePassageWithTheVerseMarked()throws Exception{
        List<BibleFinder.Hit> hits=BibleFind.local(app,"da sua plenitude nos tem dado graça sobre graça",3);
        assertFalse(hits.isEmpty());assertEquals("João 1:16",hits.get(0).title());assertTrue(BibleFinder.confident(hits));

        ActivityController<Activity> host=Robolectric.buildActivity(Activity.class).setup();Activity a=host.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);
        android.widget.LinearLayout box=new android.widget.LinearLayout(a);box.setOrientation(android.widget.LinearLayout.VERTICAL);a.setContentView(box);AppPalette palette=AppPalette.of(a);
        List<Intent> launched=new ArrayList<>();
        FlashPassages.update(a,box,palette,"da sua plenitude nos tem dado graça sobre graça",launched::add);drain();
        assertTrue("a passage row in the Flash",box.getChildCount()>=1);
        String first=((TextView)box.getChildAt(0)).getText().toString();
        assertTrue(first,first.startsWith("João 1:16")&&first.contains("plenitude"));
        box.getChildAt(0).performClick();
        assertEquals(1,launched.size());assertEquals("João 1:16",launched.get(0).getStringExtra("ref"));
        FlashPassages.update(a,box,palette,"relatorio.pdf",launched::add);drain();
        assertEquals("a file name is not a passage",0,box.getChildCount());

        ActivityController<PassageActivity> pc=Robolectric.buildActivity(PassageActivity.class,launched.get(0));PassageActivity p=pc.get();p.setTheme(android.R.style.Theme_Material_NoActionBar);pc.setup();drain();
        Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);String labels=BooksToolsTest.labels(d.getWindow().getDecorView());
        assertTrue(labels,labels.contains("João 1:16")&&labels.contains("da sua plenitude")&&labels.contains("Capítulo inteiro"));
        View whole=PageTurnTest.findDescription(d.getWindow().getDecorView(),"Capítulo inteiro");assertNotNull(whole);whole.performClick();drain();
        labels=BooksToolsTest.labels(ShadowDialog.getLatestDialog().getWindow().getDecorView());
        assertTrue("the whole chapter",labels.contains("o Verbo se fez carne")&&labels.contains("a lei foi dada por Moisés"));
        TextView body=find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"o Verbo se fez carne");
        assertNotNull(body);android.text.Spanned sp=(android.text.Spanned)body.getText();boolean marked=false;
        for(android.text.style.BackgroundColorSpan b:sp.getSpans(0,sp.length(),android.text.style.BackgroundColorSpan.class))if(sp.subSequence(sp.getSpanStart(b),sp.getSpanEnd(b)).toString().contains("plenitude"))marked=true;
        assertTrue("the verse found is marked in the chapter",marked);
        ShadowDialog.getLatestDialog().dismiss();drain();
        assertTrue("the passage screen closes with the window",p.isFinishing());
        pc.pause().stop().destroy();host.pause().stop().destroy();
    }
}
