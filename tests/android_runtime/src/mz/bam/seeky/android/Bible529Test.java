package mz.bam.seeky.android;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
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

/** 5.29.0: coloured marks with a central list, the Bible study hub with memory cards, and the Bible game. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows={SpeechServiceTest.ManualTts.class,ReaderPlusTest.Microphone.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Bible529Test {
    ReaderActivity a;ActivityController<ReaderActivity> controller;final List<ActivityController<?>> others=new ArrayList<>();

    @Before public void setup()throws Exception{
        WorkspaceEpoch.value=0;WorkspaceEpoch.restoring=false;
        Application app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("seeky",0).edit().clear().apply();app.getSharedPreferences(BibleStudyStore.PREFS,0).edit().clear().apply();
        ReaderPlusTest.source=new File(app.getCacheDir(),"bible-529.txt");
        Files.write(ReaderPlusTest.source.toPath(),"Estudo bíblico\n\nA fé é o firme fundamento das coisas que se esperam.\n\nOutro parágrafo para revisão.".getBytes("UTF-8"));
        ReaderPlusTest.Source provider=new ReaderPlusTest.Source();ProviderInfo info=new ProviderInfo();info.authority="seeky.bible529.reader";provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
        controller=Robolectric.buildActivity(ReaderActivity.class,new Intent().setDataAndType(Uri.parse("content://seeky.bible529.reader/book"),"text/plain").putExtra("name","Estudo.txt"));
        a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();drain();
        a.root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));a.root.layout(0,0,360,800);
    }
    @After public void close(){UiPanel.dismissAll(a);for(ActivityController<?> c:others)c.pause().stop().destroy();controller.pause().stop().destroy();}

    void drain()throws Exception{for(int i=0;i<5;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}}
    <T extends Activity> T launch(Class<T> type)throws Exception{ActivityController<T> c=Robolectric.buildActivity(type);T screen=c.get();screen.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();others.add(c);drain();layout(screen);return screen;}
    static void layout(Activity screen){View root=screen.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800);}
    static String labels(Activity screen){return BooksToolsTest.labels(screen.getWindow().getDecorView());}
    static String dialog(){Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);return BooksToolsTest.labels(d.getWindow().getDecorView());}
    void click(View root,String description)throws Exception{View v=PageTurnTest.findDescription(root,description);assertNotNull("view "+description,v);v.performClick();drain();}
    static TextView findText(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView f=findText(((ViewGroup)v).getChildAt(i),text);if(f!=null)return f;}return null;}
    static EditText input(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText f=input(((ViewGroup)v).getChildAt(i));if(f!=null)return f;}return null;}
    void capture(View v,String name,int height)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,360,height);
        Bitmap b=Bitmap.createBitmap(360,height,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5290-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    void bible(){
        try(PlusStore db=new PlusStore(a)){
            long id=db.save("bible_sources",StudyStore.values("title","Almeida Teste","created",1));
            for(int v=1;v<=5;v++)db.save("bible_verses",StudyStore.values("source",id,"book","Génesis","chapter",1,"verse",v,"value","No princípio, versículo "+v+" de Génesis."));
            db.save("bible_verses",StudyStore.values("source",id,"book","João","chapter",3,"verse",16,"value","Porque Deus amou o mundo de tal maneira que deu o seu Filho."));
            for(int v=1;v<=6;v++)db.save("bible_verses",StudyStore.values("source",id,"book","Isaías","chapter",3,"verse",v,"value","Isaías três, versículo "+v+"."));
        }
    }

    @Test public void colouredTextMarksAreListedAndOpenTheBookAtTheMarkedWords()throws Exception{
        String[] picked={null};
        Highlights.choose(a,"A fé é o firme fundamento",kind->picked[0]=kind);drain();
        String palette=dialog();assertTrue(palette,palette.contains("Destacar com cor")&&palette.contains("Sublinhar")&&palette.contains("Riscar")&&palette.contains("Nota"));
        capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"paleta",420);
        click(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Verde");
        assertEquals("highlight-green",picked[0]);
        String text=a.bookText.getText().toString();int start=text.indexOf("A fé"),end=text.indexOf("esperam")+"esperam".length();
        a.tools.markRange(start,end,"highlight-green");drain();
        int under=text.indexOf("Outro parágrafo");a.tools.markRange(under,under+"Outro parágrafo".length(),"underline");drain();
        Spannable shown=(Spannable)a.bookText.getText();boolean green=false;for(BackgroundColorSpan span:shown.getSpans(start,end,BackgroundColorSpan.class))green|=span.getBackgroundColor()==Highlights.COLORS[1];
        StringBuilder info=new StringBuilder();for(Object span:shown.getSpans(0,shown.length(),Object.class))info.append(span.getClass().getSimpleName()).append("@").append(shown.getSpanStart(span)).append(",");for(StudyText.Mark m:a.tools.draft.marks)info.append(" mark:").append(m.kind).append(m.start).append("-").append(m.end);info.append(" same=").append(a.tools.draft.text.equals(shown.toString()));assertTrue("green highlight is drawn: start="+start+" end="+end+" marks="+a.tools.draft.marks.size()+" committed="+(a.tools.committed==null?"null":""+a.tools.committed.marks.size())+" spans="+info+" text="+text,green);
        List<Highlights.Entry> all=Highlights.all(a,null);
        assertEquals(2,all.size());
        Highlights.Entry first=null;for(Highlights.Entry e:all)if(e.kind.equals("highlight-green"))first=e;
        assertNotNull(first);assertEquals("A fé é o firme fundamento das coisas que se esperam",first.text);assertEquals("Estudo.txt",first.name);assertTrue(first.created>0);
        Intent open=Highlights.intent(a,first);
        assertEquals(first.part,open.getIntExtra("study_segment",-1));assertEquals(start,open.getIntExtra("search_offset",-1));assertEquals("content://seeky.bible529.reader/book",open.getData().toString());
        HighlightsActivity list=launch(HighlightsActivity.class);
        String labels=labels(list);
        assertTrue(labels,labels.contains("A fé é o firme fundamento")&&labels.contains("Estudo.txt")&&labels.contains("2 marcações")&&labels.contains("Destaques")&&labels.contains("Verde"));
        capture(list.getWindow().getDecorView(),"marcacoes",800);
        list.list.performItemClick(list.list.getAdapter().getView(0,null,list.list),0,0);drain();
        String full=dialog();assertTrue(full,full.contains("Abrir no livro")&&full.contains("Mudar cor")&&full.contains("Eliminar"));
        capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"marcacao-completa",520);
        Highlights.change(a,first,"highlight-blue");
        boolean blue=false;for(Highlights.Entry e:Highlights.all(a,null))blue|=e.kind.equals("highlight-blue");assertTrue("colour changed",blue);
        for(Highlights.Entry e:Highlights.all(a,null))Highlights.change(a,e,null);
        assertTrue(Highlights.all(a,null).isEmpty());
    }

    @Test public void pdfMarksAreSavedDrawnAndOpenFromTheList()throws Exception{
        String line="Agora a fé é o firme fundamento das coisas.";
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setTextSize(13.5f);ink.setColor(Color.BLACK);
        List<PdfGlyphs.Glyph> boxes=new ArrayList<>();for(int i=0;i<line.length();i++)if(line.charAt(i)!=' ')boxes.add(new PdfGlyphs.Glyph(i,i+1,30+ink.measureText(line,0,i),118,ink.measureText(line,i,i+1),14));
        ReaderPageView page=new ReaderPageView(a);page.setTag(4);page.biblePart=4;
        Bitmap sheet=Bitmap.createBitmap(400,560,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(sheet);c.drawColor(Color.WHITE);c.drawText(line,30,130,ink);
        page.setImageBitmap(sheet);page.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(504,View.MeasureSpec.EXACTLY));page.layout(0,0,360,504);page.fit();
        page.glyphs=new PdfGlyphs(line,400,560,boxes);
        int from=line.indexOf("a fé"),to=line.indexOf(" das");
        page.markPdf(from,to,"highlight-pink","",line.substring(from,to));drain();
        page.markPdf(line.indexOf("das"),line.length()-1,"underline","",line.substring(line.indexOf("das"),line.length()-1));drain();
        assertEquals(2,page.marks.size());assertFalse(page.markRects.get(0).isEmpty());
        Bitmap shot=Bitmap.createBitmap(360,504,Bitmap.Config.ARGB_8888);page.draw(new Canvas(shot));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5290-pdf-marcas.png")){shot.compress(Bitmap.CompressFormat.PNG,100,out);}
        RectF hit=page.toView(page.markRects.get(0).get(0));
        assertTrue(page.bibleTap(hit.centerX(),hit.centerY()));drain();
        assertTrue(dialog().contains("a fé é o firme fundamento"));
        List<Highlights.Entry> all=Highlights.all(a,a.uri.toString());
        assertEquals(2,all.size());
        Highlights.Entry pink=null;for(Highlights.Entry e:all)if(e.kind.equals("highlight-pink"))pink=e;
        assertTrue(pink.pdf);assertEquals("página 5",pink.where());assertEquals("Estudo.txt",pink.name);
        Intent open=Highlights.intent(a,pink);assertEquals(4,open.getIntExtra("study_segment",-1));assertFalse(open.hasExtra("search_offset"));
    }

    @Test public void studyHubCountsTodayAndTheWeekAndReviewsMemoryCards()throws Exception{
        bible();
        BibleStudyActivity hub=launch(BibleStudyActivity.class);
        String labels=labels(hub);
        assertTrue(labels,labels.contains("Leitura de hoje")&&labels.contains("Génesis 1")&&labels.contains("Esta semana")&&labels.contains("Decorar")&&labels.contains("Jogo da Bíblia"));
        assertTrue(labels,labels.contains("Hoje: 0 de 4 capítulos"));
        click(hub.getWindow().getDecorView(),"Marcar como lido Génesis 1");layout(hub);
        labels=labels(hub);assertTrue(labels,labels.contains("Hoje: 1 de 4 capítulos")&&labels.contains("1 capítulo"));
        try(BibleStudyStore st=new BibleStudyStore(a)){assertTrue(st.readOn(BibleStudyCore.day(System.currentTimeMillis())).contains("Génesis 1"));}
        capture(hub.getWindow().getDecorView(),"estudo",800);
        BibleLinks.readChapter(a,"Isaías",3,null);drain();
        assertTrue(dialog().contains("Marcar como lido")&&dialog().contains("Isaías três, versículo 6."));
        click(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Marcar como lido");
        try(BibleStudyStore st=new BibleStudyStore(a)){assertTrue(st.readOn(BibleStudyCore.day(System.currentTimeMillis())).contains("Isaías 3"));}
        BibleLinks.open(a,BibleReferences.lookup("Jo 3:16"));drain();
        click(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"Decorar");
        List<BibleStudyStore.Card> cards;try(BibleStudyStore st=new BibleStudyStore(a)){cards=st.cards();}
        assertEquals(1,cards.size());assertEquals("João 3:16",cards.get(0).title);assertEquals("Porque Deus amou o mundo de tal maneira que deu o seu Filho.",cards.get(0).text);assertEquals("Almeida Teste",cards.get(0).version);
        hub.home();drain();layout(hub);
        click(hub.getWindow().getDecorView(),"Rever agora (1)");layout(hub);
        labels=labels(hub);assertTrue(labels,labels.contains("João 3:16")&&labels.contains("Escreva ou diga o versículo de memória")&&labels.contains("Pista"));
        hub.answer.setText("Deus amou o mundo de tal maneira");
        click(hub.getWindow().getDecorView(),"Verificar");layout(hub);
        labels=labels(hub);assertTrue(labels,labels.contains("Lembrou")&&labels.contains("das palavras")&&labels.contains("Bom"));
        capture(hub.getWindow().getDecorView(),"cartao",800);
        TextView good=findText(hub.getWindow().getDecorView(),"Bom\n3 dias");if(good==null)good=findText(hub.getWindow().getDecorView(),"Bom\n1 dia");assertNotNull(labels,good);good.performClick();drain();layout(hub);
        assertTrue(labels(hub).contains("Sessão concluída"));
        try(BibleStudyStore st=new BibleStudyStore(a)){assertEquals(1,st.cards().get(0).interval);assertEquals(1,st.amount(BibleStudyCore.day(System.currentTimeMillis()),"card"));}
        hub.cardMode="book";hub.review(null);drain();layout(hub);
        assertTrue(labels(hub).contains("Em que livro está?"));
        hub.answer.setText("evangelho de joão");click(hub.getWindow().getDecorView(),"Verificar");layout(hub);
        assertTrue(labels(hub).contains("Certo!"));
    }

    @Test public void theGamePlaysTenQuestionsAndKeepsTheNameInTheRecords()throws Exception{
        bible();
        BibleGameActivity game=launch(BibleGameActivity.class);
        String labels=labels(game);
        assertTrue(labels,labels.contains("Quanto conhece a Bíblia?")&&labels.contains("Fácil")&&labels.contains("Médio")&&labels.contains("Difícil")&&labels.contains("Recordes"));
        capture(game.getWindow().getDecorView(),"jogo-menu",800);
        game.start(BibleGame.MEDIUM);drain();layout(game);
        assertEquals(10,game.questions.size());
        labels=labels(game);assertTrue(labels,labels.contains("Pergunta 1 de 10")&&labels.contains("Dizer a resposta"));
        capture(game.getWindow().getDecorView(),"jogo-pergunta",800);
        for(int i=0;i<10;i++){
            BibleGame.Question q=game.questions.get(game.index);
            if(i==3)game.answer("resposta errada");else game.answer(q.free()?BibleGame.display(q.answer):q.answer);
            drain();layout(game);
            if(i==0){assertTrue(labels(game).contains("Certo!"));capture(game.getWindow().getDecorView(),"jogo-certo",800);}
            if(i==3)assertTrue(labels(game).contains("Era:"));
            if(i<9){game.index++;game.question();drain();}
        }
        assertEquals(9,game.correct);
        game.result();drain();layout(game);
        labels=labels(game);assertTrue(labels,labels.contains("9 de 10 certas")&&labels.contains("★★★")&&labels.contains("Guardar no Recorde")&&labels.contains("Rever respostas"));
        capture(game.getWindow().getDecorView(),"jogo-resultado",800);
        EditText name=input(game.getWindow().getDecorView());name.setText("Ana");
        click(game.getWindow().getDecorView(),"Guardar");layout(game);
        labels=labels(game);assertTrue(labels,labels.contains("Recordes")&&labels.contains("Ana")&&labels.contains(game.score+" pts"));
        capture(game.getWindow().getDecorView(),"jogo-recordes",800);
        try(BibleStudyStore st=new BibleStudyStore(a)){assertEquals(1,st.records(BibleGame.MEDIUM,10).size());assertEquals(1,st.amount(BibleStudyCore.day(System.currentTimeMillis()),"game"));}
        assertEquals("Ana",game.getSharedPreferences(BibleStudyStore.PREFS,0).getString("game_name",""));
    }

    @Test public void spokenAnswersPickTheRightOption(){
        BibleGame.Question books=new BibleGame.Question("verse","","",Arrays.asList("Mateus","1 Coríntios","Isaías","Salmos"),"1 Coríntios","","");
        assertEquals("1 Coríntios",BibleGameActivity.spoken(books,Arrays.asList("primeira carta aos coríntios")));
        assertEquals("Isaías",BibleGameActivity.spoken(books,Arrays.asList("letra c")));
        assertEquals("Salmos",BibleGameActivity.spoken(books,Arrays.asList("salmo")));
        BibleGame.Question chapters=new BibleGame.Question("chapter","","",Arrays.asList("Isaías 53","Isaías 40","Jeremias 31","Ezequiel 37"),"Isaías 53","","");
        assertEquals("Isaías 40",BibleGameActivity.spoken(chapters,Arrays.asList("Isaías 40")));
        assertNull(BibleGameActivity.spoken(books,Arrays.asList("não sei")));
    }
}
