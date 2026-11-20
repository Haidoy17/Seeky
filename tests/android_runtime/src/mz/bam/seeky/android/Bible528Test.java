package mz.bam.seeky.android;

import android.app.Application;
import android.app.Dialog;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.graphics.*;
import android.net.Uri;
import android.os.Looper;
import android.text.Spannable;
import android.view.View;
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

/** 5.28.0: citations such as "Isaías 3.1-7, 12" are tappable in text, PDF, OCR and Word pages and open a quick reader. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,application=Application.class,qualifiers="w360dp-h800dp-mdpi",shadows={SpeechServiceTest.ManualTts.class,ReaderPlusTest.Microphone.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Bible528Test {
    ReaderActivity a;ActivityController<ReaderActivity> controller;

    @Before public void setup()throws Exception{
        WorkspaceEpoch.value=0;WorkspaceEpoch.restoring=false;
        Application app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("seeky",0).edit().clear().apply();
        ReaderPlusTest.source=new File(app.getCacheDir(),"bible-528.txt");
        Files.write(ReaderPlusTest.source.toPath(),"Estudo bíblico\n\nLeia Isaías 3.1-7, 12 e compare com Jo 3,16 antes do culto.\n\nO Salmo 23 consola.".getBytes("UTF-8"));
        ReaderPlusTest.Source provider=new ReaderPlusTest.Source();ProviderInfo info=new ProviderInfo();info.authority="seeky.bible528.reader";provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
        controller=Robolectric.buildActivity(ReaderActivity.class,new Intent().setDataAndType(Uri.parse("content://seeky.bible528.reader/book"),"text/plain").putExtra("name","Estudo.txt"));
        a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);controller.create().start().resume().visible();drain();
        a.root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));a.root.layout(0,0,360,800);
    }
    @After public void close(){UiPanel.dismissAll(a);controller.pause().stop().destroy();}

    void drain()throws Exception{for(int i=0;i<4;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}}
    void bibles(){
        try(PlusStore db=new PlusStore(a)){
            long first=db.save("bible_sources",StudyStore.values("title","Almeida Teste","created",1));
            long second=db.save("bible_sources",StudyStore.values("title","Nova Versão","created",2));
            for(int v=1;v<=12;v++){db.save("bible_verses",StudyStore.values("source",first,"book","Isaías","chapter",3,"verse",v,"value","Verso "+v+" de Isaías A."));db.save("bible_verses",StudyStore.values("source",second,"book","Isaías","chapter",3,"verse",v,"value","Verso "+v+" de Isaías B."));}
            db.save("bible_verses",StudyStore.values("source",first,"book","Isaías","chapter",4,"verse",1,"value","Capítulo seguinte A."));
            db.save("bible_verses",StudyStore.values("source",first,"book","João","chapter",3,"verse",16,"value","Porque Deus amou o mundo."));
        }
    }
    String shown(){Dialog d=ShadowDialog.getLatestDialog();assertNotNull(d);return BooksToolsTest.labels(d.getWindow().getDecorView());}
    void click(String description)throws Exception{View v=PageTurnTest.findDescription(ShadowDialog.getLatestDialog().getWindow().getDecorView(),description);assertNotNull("chip "+description,v);v.performClick();drain();}
    void capture(View v,String name)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.AT_MOST));
        int h=Math.max(1,v.getMeasuredHeight());v.layout(0,0,360,h);
        Bitmap b=Bitmap.createBitmap(360,h,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5280-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }

    @Test public void textReaderLinksDottedAndCommaCitationsAndOpensTheQuickReaderInPlace()throws Exception{
        bibles();drain();
        Spannable text=(Spannable)a.bookText.getText();
        BibleLinks.Link[] links=text.getSpans(0,text.length(),BibleLinks.Link.class);
        List<String> linked=new ArrayList<>();for(BibleLinks.Link l:links)linked.add(text.subSequence(text.getSpanStart(l),text.getSpanEnd(l)).toString());
        assertTrue(linked.toString(),linked.contains("Isaías 3.1-7, 12"));
        assertTrue(linked.toString(),linked.contains("Jo 3,16"));
        assertTrue(linked.toString(),linked.contains("Salmo 23"));
        BibleLinks.Link isaiah=null;for(BibleLinks.Link l:links)if(l.ref.book.equals("Isaías"))isaiah=l;
        isaiah.onClick(a.bookText);drain();
        String labels=shown();
        assertTrue(labels,labels.contains("Isaías 3:1-7, 12"));
        assertTrue(labels.contains("Verso 1 de Isaías A.")&&labels.contains("Verso 7 de Isaías A.")&&labels.contains("Verso 12 de Isaías A."));
        assertFalse("verse 9 is outside the passage",labels.contains("Verso 9 de Isaías A."));
        for(String chip:new String[]{"Copiar","Partilhar","Capítulo inteiro","Ouvir","Comparar versões","Guardar","Almeida Teste","Nova Versão"})assertTrue(chip,labels.contains(chip));
        assertFalse(a.isFinishing());assertEquals("content://seeky.bible528.reader/book",a.uri.toString());
        capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"passagem");

        click("Copiar");
        ClipData clip=((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip();
        String copied=clip.getItemAt(0).getText().toString();
        assertTrue(copied,copied.startsWith("Isaías 3:1-7, 12 (Almeida Teste)")&&copied.contains("12 Verso 12 de Isaías A."));

        click("Capítulo inteiro");
        labels=shown();
        assertTrue(labels.contains("Verso 9 de Isaías A.")&&labels.contains("Só a passagem")&&labels.contains("Capítulo 4"));
        capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"capitulo");
        click("Capítulo 4");
        assertTrue(shown().contains("Capítulo seguinte A."));
        click("Capítulo 3");

        click("Comparar versões");
        labels=shown();
        assertTrue(labels,labels.contains("Verso 9 de Isaías B.")&&labels.contains("Uma versão"));
        capture(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"comparar");

        click("Nova Versão");
        assertTrue(shown().contains("Verso 9 de Isaías B."));
        assertEquals(2,a.getSharedPreferences("seeky",0).getLong("bible_quick_source",0));

        click("Guardar");
        assertEquals("Isaías 3:1-7, 12",BibleLinks.savedItems(a).get(0).optString("title"));
        assertEquals("Nova Versão",BibleLinks.savedItems(a).get(0).optString("version"));
    }

    @Test public void withoutABibleTheReferenceOffersDownloadAndImport()throws Exception{
        BibleLinks.open(a,BibleReferences.lookup("Isaías 3.1-7, 12"));drain();
        UiPanel panel=UiPanel.open.get(a).iterator().next();
        String labels=BooksToolsTest.labels(panel.dialog.getWindow().getDecorView());
        assertTrue(labels,labels.contains("Descarregar uma Bíblia")&&labels.contains("Importar ficheiro bíblico")&&labels.contains("Copiar referência"));
    }

    @Test public void pdfPageUnderlinesCitationsAndATapOpensThePassageInsteadOfTheToolbar()throws Exception{
        bibles();
        String line="Leia Isaías 3.1-7, 12 e Jo 3:16 hoje.";
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setTextSize(13.5f);ink.setColor(Color.BLACK);
        List<PdfGlyphs.Glyph> boxes=new ArrayList<>();for(int i=0;i<line.length();i++)if(line.charAt(i)!=' ')boxes.add(new PdfGlyphs.Glyph(i,i+1,30+ink.measureText(line,0,i),118,ink.measureText(line,i,i+1),14));
        PdfGlyphs glyphs=new PdfGlyphs(line,400,560,boxes);
        ReaderPageView page=new ReaderPageView(a);page.setTag(0);page.biblePart=0;
        Bitmap sheet=Bitmap.createBitmap(400,560,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(sheet);c.drawColor(Color.WHITE);c.drawText(line,30,130,ink);
        page.setImageBitmap(sheet);
        page.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(504,View.MeasureSpec.EXACTLY));page.layout(0,0,360,504);page.fit();
        page.glyphs=glyphs;page.bibleRefs=BibleReferences.references(line);page.bibleRects=ReaderPageView.bibleLines(glyphs,page.bibleRefs);page.pageRefs=page.bibleRefs;
        assertEquals(2,page.bibleRefs.size());assertEquals(1,page.bibleRects.get(0).size());
        Bitmap shot=Bitmap.createBitmap(360,504,Bitmap.Config.ARGB_8888);page.draw(new Canvas(shot));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5280-pdf.png")){shot.compress(Bitmap.CompressFormat.PNG,100,out);}
        assertFalse("badge lists the page's passages",page.bibleBadge.isEmpty());
        RectF hit=page.toView(page.bibleRects.get(0).get(0));
        int chrome=a.chrome.current();
        assertTrue(page.bibleTap(hit.centerX(),hit.centerY()));drain();
        assertTrue(shown().contains("Verso 12 de Isaías A."));
        assertEquals(chrome,a.chrome.current());
        assertFalse("blank page space still toggles the toolbar",page.bibleTap(350,480));
        assertTrue(page.bibleTap(page.bibleBadge.centerX(),page.bibleBadge.centerY()));drain();
        UiDialog choices=(UiDialog)ShadowDialog.getLatestDialog();assertTrue(shown().contains("Passagens bíblicas"));
        android.widget.ListAdapter items=choices.getListView().getAdapter();List<String> titles=new ArrayList<>();for(int i=0;i<items.getCount();i++)titles.add(String.valueOf(items.getItem(i)));
        assertEquals(Arrays.asList("Isaías 3:1-7, 12","João 3:16"),titles);
    }

    @Test public void ocrOnlyPagesStillListTheirPassagesWithTheBadge()throws Exception{
        bibles();
        ReaderPageView page=new ReaderPageView(a);page.setTag(0);page.biblePart=0;
        Bitmap sheet=Bitmap.createBitmap(400,560,Bitmap.Config.ARGB_8888);sheet.eraseColor(Color.WHITE);page.setImageBitmap(sheet);
        page.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(504,View.MeasureSpec.EXACTLY));page.layout(0,0,360,504);page.fit();
        page.pageRefs=BibleReferences.references("Texto reconhecido: Isaías 3.1-7, 12.");
        page.draw(new Canvas(Bitmap.createBitmap(360,504,Bitmap.Config.ARGB_8888)));
        assertTrue(page.bibleTap(page.bibleBadge.centerX(),page.bibleBadge.centerY()));drain();
        assertTrue(shown().contains("Verso 7 de Isaías A."));
    }

    @Test public void wordAndEpubOriginalViewsWrapCitationsAcrossFormattingRuns()throws Exception{
        String script=BibleLinks.plan("[[0,\"Leia \"],[0,\"Isaías\"],[0,\" 3.1-7, 12\"],[0,\" hoje.\"],[1,\"Página 5 de 9\"],[2,\"Jo 3,16\"]]",0xff3b82f6);
        assertNotNull(script);
        assertTrue(script,script.contains("[1,0,6,\"Isaías 3.1-7, 12\"]")&&script.contains("[2,0,10,\"Isaías 3.1-7, 12\"]"));
        assertTrue(script.contains("[5,0,7,\"Jo 3,16\"]"));
        assertFalse("page numbers are not citations",script.contains("[4,"));
        assertTrue(script.contains("seekybible://passage?ref=")&&script.contains("#3b82f6"));
        assertNull(BibleLinks.plan("[[0,\"Sem referências 12\"]]",0));
    }

    @Test public void recognisedTextAndSelectionsOpenPassages()throws Exception{
        bibles();
        TextView recognised=new TextView(a);recognised.setText("Página digitalizada: Isaías 3.1-7, 12.");recognised.setTextIsSelectable(true);
        BibleLinks.linkify(a,recognised,0xff3b82f6,PlusUi.WORK,false);drain();
        Spannable s=(Spannable)recognised.getText();
        assertEquals(1,s.getSpans(0,s.length(),BibleLinks.Link.class).length);
        BibleLinks.query(a);drain();
        Dialog query=ShadowDialog.getLatestDialog();
        android.widget.EditText input=(android.widget.EditText)findInput(query.getWindow().getDecorView());
        input.setText("isaias 3.1-7, 12");((UiDialog)query).getButton(DialogInterface.BUTTON_POSITIVE).performClick();drain();
        assertTrue(shown().contains("Verso 12 de Isaías A."));
    }
    static View findInput(View v){if(v instanceof android.widget.EditText)return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View f=findInput(((android.view.ViewGroup)v).getChildAt(i));if(f!=null)return f;}return null;}
}
