package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.text.*;import android.text.style.*;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.38: the notebook (linked to Estudos, books and the Bible) and the Bible game with four levels at 60 s. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h800dp-mdpi",application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Notepad538Test {
    final Application app=RuntimeEnvironment.getApplication();
    final List<ActivityController<?>> open=new ArrayList<>();
    @After public void close(){for(ActivityController<?> c:open)try{c.pause().stop().destroy();}catch(Exception ignored){}UiDialog.dismissAll(null);}
    void drain(NotepadActivity a)throws Exception{for(int i=0;i<5;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idleFor(800,TimeUnit.MILLISECONDS);}}
    NotepadActivity launch(Intent i)throws Exception{ActivityController<NotepadActivity> c=Robolectric.buildActivity(NotepadActivity.class,i);NotepadActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();open.add(c);drain(a);return a;}
    View root(Activity a){return a.getWindow().getDecorView();}
    void capture(View v,String name,int height)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,360,height);
        Bitmap b=Bitmap.createBitmap(360,height,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5380-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    static int spans(CharSequence text,Class<?> type){return text instanceof Spanned?((Spanned)text).getSpans(0,text.length(),type).length:0;}

    @Test public void notesRecogniseScripturesTicksTasksAndLiveInEstudos()throws Exception{
        NotepadActivity a=launch(new Intent(app,NotepadActivity.class));
        String labels=BooksToolsTest.labels(root(a));
        assertTrue(labels,labels.contains("Todas as notas")&&labels.contains("Comece o seu bloco de notas")&&labels.contains("Tarefas"));
        capture(root(a),"bloco-vazio",800);
        // A passage sent from the passage viewer starts a new note.
        NotepadActivity n=launch(new Intent(app,NotepadActivity.class).putExtra("passage_title","João 3:16").putExtra("passage_text","Porque Deus amou o mundo de tal maneira…").putExtra("passage_version","Almeida"));
        assertTrue(n.body.getText().toString().startsWith("> João 3:16 (Almeida)"));
        n.body.append("# O amor de Deus\nVer também Romanos 8:28 e Isaías 40:31.\n[ ] Orar pela família\n[ ] Ler 1 João 4\n");
        shadowOf(Looper.getMainLooper()).idleFor(2000,TimeUnit.MILLISECONDS);drain(n);
        try(StudyStore db=new StudyStore(app)){List<StudyStore.Row> rows=db.rows("notes",null,null,null,10);assertEquals("guardada nos Estudos",1,rows.size());assertTrue(rows.get(0).s("body").contains("Romanos 8:28"));assertEquals("João 3:16",rows.get(0).s("title"));}
        String chips=BooksToolsTest.labels(n.passages);
        assertTrue(chips,chips.contains("João 3:16")&&chips.contains("Romanos 8:28")&&chips.contains("Isaías 40:31"));
        String info=n.info.getText().toString();assertTrue(info,info.contains("☑ 0/2")&&info.contains("palavras"));
        capture(root(n),"bloco-editar",1100);
        // Bold around a selection.
        String before=n.body.getText().toString();int at=before.indexOf("família");n.body.setSelection(at,at+7);n.tool("bold");
        assertTrue(n.body.getText().toString().contains("**família**"));n.tool("undo");assertEquals(before,n.body.getText().toString());
        // Reading view: passages become links, tasks can be ticked.
        n.tool("read");drain(n);
        CharSequence shown=n.reading.getText();
        assertTrue("passagens tocáveis: "+spans(shown,BibleLinks.Link.class),spans(shown,BibleLinks.Link.class)>=3);
        assertFalse("sem marcas visíveis",shown.toString().contains("# O amor")||shown.toString().contains("[ ]"));
        assertTrue(shown.toString(),shown.toString().contains("☐")&&shown.toString().contains("Orar pela família"));
        ClickableSpan tick=null;for(ClickableSpan c:((Spanned)shown).getSpans(0,shown.length(),ClickableSpan.class))if(!(c instanceof BibleLinks.Link)){tick=c;break;}
        assertNotNull(tick);tick.onClick(n.reading);
        assertTrue(n.body.getText().toString().contains("[x] Orar pela família"));
        capture(root(n),"bloco-ler",1100);
        // Inserting a passage without an imported Bible keeps the reference.
        n.insertPassage(BibleReferences.lookup("Salmos 23:1"));drain(n);
        assertTrue(n.body.getText().toString().contains("> Salmos 23:1"));
        n.saveNow();drain(n);
        // The list shows the note with tasks and passages; trash and restore.
        NotepadActivity l=launch(new Intent(app,NotepadActivity.class));
        labels=BooksToolsTest.labels(root(l));
        assertTrue(labels,labels.contains("João 3:16")&&labels.contains("☑ 1/2")&&labels.contains("📖"));
        capture(root(l),"bloco-lista",800);
        long id;try(StudyStore db=new StudyStore(app)){id=db.rows("notes",null,null,null,1).get(0).id();}
        NotepadStore.trash(app,id);
        try(StudyStore db=new StudyStore(app)){assertEquals(0,db.count("notes",null,null));}
        List<NotepadStore.Trashed> bin=NotepadStore.trashed(app);assertEquals(1,bin.size());
        long back=NotepadStore.restore(app,bin.get(0).id);
        try(StudyStore db=new StudyStore(app)){assertNotNull(db.get("notes",back));}
        assertTrue(NotepadStore.trashed(app).isEmpty());
    }
    @Test public void templatesColoursAndFoldersAreKept()throws Exception{
        NotepadStore.Note n=new NotepadStore.Note();n.body=NotepadText.TEMPLATES[0][1];n.folder="Estudo bíblico";n.color=NotepadStore.COLORS[2];n.pinned=true;n.tags="fé, oração";
        long id=NotepadStore.save(app,n);
        NotepadStore.Note back=NotepadStore.load(app,id);
        assertEquals("Estudo bíblico",back.folder);assertEquals(NotepadStore.COLORS[2],back.color);assertTrue(back.pinned);assertEquals("fé, oração",back.tags);assertEquals("Passagem",back.shownTitle());
        try(NotepadStore pad=new NotepadStore(app)){assertEquals(Collections.singletonList("Estudo bíblico"),pad.folders());}
        NotepadActivity a=launch(new Intent(app,NotepadActivity.class));
        a.filter="pinned";a.drawList();assertTrue(BooksToolsTest.labels(a.listBox).contains("Passagem"));
        a.filter="books";a.drawList();assertTrue(BooksToolsTest.labels(a.listBox).contains("Nenhuma nota encontrada"));
    }
    @Test public void bibleGameHasFourLevelsOfSixtySeconds()throws Exception{
        ActivityController<BibleGameActivity> c=Robolectric.buildActivity(BibleGameActivity.class);BibleGameActivity g=c.get();g.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();open.add(c);
        for(int i=0;i<5;i++){PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        String labels=BooksToolsTest.labels(root(g));
        for(String s:new String[]{"Fácil","Médio","Difícil","Lendário","60 s por pergunta"})assertTrue(s+" em "+labels,labels.contains(s));
        capture(root(g),"jogo-niveis",1000);
        g.start(BibleGame.LEGEND);
        for(int i=0;i<5;i++){PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        assertEquals(10,g.questions.size());
        Set<String> kinds=new HashSet<>();for(BibleGame.Question q:g.questions)kinds.add(q.kind);
        assertTrue(kinds.toString(),kinds.contains("reflect")&&kinds.contains("blankwrite"));
        int first=0;for(int i=0;i<g.questions.size();i++)if(g.questions.get(i).kind.equals("reflect")){first=i;break;}
        g.index=first;g.question();shadowOf(Looper.getMainLooper()).idle();
        labels=BooksToolsTest.labels(root(g));assertTrue(labels,labels.contains("Para meditar"));
        capture(root(g),"jogo-lendario",1000);
        g.answer(g.questions.get(first).answer);shadowOf(Looper.getMainLooper()).idle();
        labels=BooksToolsTest.labels(root(g));assertTrue("explicação depois de responder: "+labels,labels.contains(g.questions.get(first).explain));
        capture(root(g),"jogo-explicacao",1000);
        PlusUi.MAIN.removeCallbacksAndMessages(null);
    }
}
