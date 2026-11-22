package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.content.pm.ProviderInfo;import android.graphics.*;import android.net.Uri;import android.os.*;import android.text.*;import android.text.style.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.file.Files;import java.util.*;import java.util.concurrent.TimeUnit;import java.util.zip.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.39: rich notes (headings, underline, colours, tables, pictures), Word and PDF export, list/card/grid views, tasks, reminders and notes inside the reader. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h800dp-mdpi",application=Application.class,shadows={PdfCanvasHost.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Notepad539Test {
    final Application app=RuntimeEnvironment.getApplication();
    final List<ActivityController<?>> open=new ArrayList<>();
    @After public void close(){for(ActivityController<?> c:open)try{c.pause().stop().destroy();}catch(Exception ignored){}UiDialog.dismissAll(null);PlusUi.MAIN.removeCallbacksAndMessages(null);}
    void drain(NotepadActivity a)throws Exception{for(int i=0;i<5;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idleFor(800,TimeUnit.MILLISECONDS);}}
    NotepadActivity launch(Intent i)throws Exception{ActivityController<NotepadActivity> c=Robolectric.buildActivity(NotepadActivity.class,i);NotepadActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();open.add(c);drain(a);return a;}
    View root(Activity a){return a.getWindow().getDecorView();}
    static void capture(View v,String name,int height)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,360,height);
        Bitmap b=Bitmap.createBitmap(360,height,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5390-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    static int spans(CharSequence text,Class<?> type){return text instanceof Spanned?((Spanned)text).getSpans(0,text.length(),type).length:0;}
    static String entry(byte[] zip,String name)throws IOException{try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){ZipEntry e;while((e=in.getNextEntry())!=null)if(e.getName().equals(name)){ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toString("UTF-8");}}return null;}
    static Set<String> names(byte[] zip)throws IOException{Set<String> out=new HashSet<>();try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){ZipEntry e;while((e=in.getNextEntry())!=null)out.add(e.getName());}return out;}

    @Test public void richNoteFormatsLiveReadsAndExportsToWordAndPdf()throws Exception{
        NotepadActivity n=launch(new Intent(app,NotepadActivity.class).putExtra("new",true));
        n.title.setText("Plano de estudo");
        n.body.setText("Introdução\nA graça de Deus é suficiente.\nPrimeiro ponto\nSegundo ponto\nCentro\n");
        String t=n.body.getText().toString();
        n.body.setSelection(0,0);n.tool("h1");
        t=n.body.getText().toString();assertTrue(t,t.startsWith("# Introdução\n"));
        int at=t.indexOf("graça");n.body.setSelection(at,at+5);n.tool("under");
        t=n.body.getText().toString();assertTrue(t,t.contains("__graça__"));
        at=t.indexOf("suficiente");n.body.setSelection(at,at+10);n.applyEdit(NotepadText.color(t,at,at+10,"azul"));
        t=n.body.getText().toString();assertTrue(t,t.contains("{cor:azul}suficiente{/cor}"));
        at=t.indexOf("Primeiro");n.body.setSelection(at,t.indexOf("Segundo")+3);n.tool("number");
        t=n.body.getText().toString();assertTrue(t,t.contains("1. Primeiro ponto\n2. Segundo ponto"));
        at=t.indexOf("Centro");n.body.setSelection(at,at);n.tool("center");
        t=n.body.getText().toString();assertTrue(t,t.contains("[c] Centro"));
        n.body.setSelection(t.length(),t.length());n.tool("table");
        t=n.body.getText().toString();assertTrue(t,t.contains("| "));
        // A picture (as if from the gallery or a drawing).
        Bitmap pic=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);new Canvas(pic).drawColor(0xff2e9d57);
        String name=NotepadMedia.save(app,pic,true);n.body.setSelection(n.body.length(),n.body.length());n.insertMedia("![Imagem](note-img:"+name+")");
        shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS);n.styleNow();
        Editable live=n.body.getText();
        assertTrue("formatação ao vivo",spans(live,NotepadRender.Mine.class)>=4);
        assertTrue("título maior",spans(live,NotepadRender.Size.class)>=1);
        assertTrue("imagem na edição",spans(live,NotepadRender.Picture.class)==1);
        n.tool("format");drain(n);
        assertEquals(View.VISIBLE,n.formatSheet.getVisibility());
        String sheet=BooksToolsTest.labels(n.formatSheet);for(String s:new String[]{"H1","H4","Corpo","B","U","S"})assertTrue(s+" em "+sheet,sheet.contains(s));
        for(String d:new String[]{"Lista numerada","Lista com marcas","Lista de tarefas","Centrar","Destacar","Aumentar recuo"})assertNotNull(d,PageTurnTest.findDescription(n.formatSheet,d));
        capture(root(n),"editor-formatar",1000);
        n.tool("format");
        // Reading view: no marks left, styled runs, picture.
        n.tool("read");drain(n);
        CharSequence shown=n.reading.getText();String plain=shown.toString();
        assertFalse(plain,plain.contains("**")||plain.contains("__")||plain.contains("{cor")||plain.contains("[c]")||plain.contains("note-img"));
        assertTrue(plain,plain.contains("Introdução")&&plain.contains("graça")&&plain.contains("1."));
        assertTrue("sublinhado",spans(shown,UnderlineSpan.class)>=1);assertTrue("cor",spans(shown,ForegroundColorSpan.class)>=1);assertTrue("imagem",spans(shown,ImageSpan.class)>=1);
        capture(root(n),"editor-ler",1000);
        // Word (.docx): title, heading, underline, colour, numbering, table and the picture.
        ByteArrayOutputStream word=new ByteArrayOutputStream();n.write("docx",word);byte[] docx=word.toByteArray();
        Set<String> parts=names(docx);assertTrue(parts.toString(),parts.contains("word/document.xml")&&parts.contains("word/styles.xml")&&parts.contains("word/numbering.xml"));
        boolean media=false;for(String p:parts)if(p.startsWith("word/media/"))media=true;assertTrue("imagem no Word: "+parts,media);
        String doc=entry(docx,"word/document.xml");
        for(String s:new String[]{"Plano de estudo","Heading1","<w:u w:val=\"single\"/>","2F6FDE","<w:numId w:val=\"2\"/>","<w:tbl>","<w:jc w:val=\"center\"/>","<a:blip"})assertTrue(s,doc.contains(s));
        // PDF.
        ByteArrayOutputStream pdf=new ByteArrayOutputStream();n.write("pdf",pdf);
        String head=new String(pdf.toByteArray(),0,Math.min(8,pdf.size()),"ISO-8859-1");assertTrue("PDF: "+head,head.startsWith("%PDF"));
        try(com.tom_roush.pdfbox.pdmodel.PDDocument d=com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf.toByteArray())){assertTrue(d.getNumberOfPages()>=1);}
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5390-exemplo.docx")){out.write(docx);}
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",NotepadActivity.mime("docx"));
        assertEquals("Plano de estudo.pdf",n.fileName("pdf"));
        // Paper backgrounds follow the note.
        n.saveNow();drain(n);
        n.note.paper="linhas";n.editor(n.note);drain(n);
        assertTrue(n.body.getBackground() instanceof NotepadActivity.Pattern);
        capture(root(n),"editor-linhas",1000);
        n.note.paper="noite";n.editor(n.note);drain(n);
        assertEquals(0xfff2f2f2,n.body.getCurrentTextColor());
        n.note.paper="amarelo";n.editor(n.note);drain(n);
        n.dirty=true;n.saveNow();drain(n);
        NotepadStore.Note back=NotepadStore.all(app).get(0);assertEquals("amarelo",back.paper);
        capture(root(n),"editor-amarelo",1000);
    }

    @Test public void listViewsSectionsTasksTabRemindersAndLock()throws Exception{
        NotepadStore.Note a=new NotepadStore.Note();a.title="Sermão de domingo";a.body="## Fé\nLer Hebreus 11:1\n[ ] Preparar slides\n[x] Escolher hinos";a.pinned=true;a.folder="Sermões";NotepadStore.save(app,a);
        NotepadStore.Note b=new NotepadStore.Note();b.title="Compras";b.body="[ ] Pão\n[ ] Leite";NotepadStore.save(app,b);
        NotepadStore.Note c=new NotepadStore.Note();c.title="Diário";c.body="Texto secreto";c.locked=true;c.color=NotepadStore.COLORS[3];long secret=NotepadStore.save(app,c);
        NotepadActivity l=launch(new Intent(app,NotepadActivity.class));
        String labels=BooksToolsTest.labels(root(l));
        for(String s:new String[]{"Notas","Tarefas","Todas as notas","Fixadas","Hoje","Sermão de domingo","Compras","Nota bloqueada","1/2"})assertTrue(s+" em "+labels,labels.contains(s));
        assertFalse("o texto bloqueado não aparece",labels.contains("Texto secreto"));
        capture(root(l),"lista-cartoes",800);
        l.view="list";l.list();drain(l);capture(root(l),"lista-linhas",800);
        assertTrue(BooksToolsTest.labels(l.listBox).contains("Compras"));
        l.view="grid";l.list();drain(l);capture(root(l),"lista-grelha",800);
        for(int i=0;i<l.listBox.getChildCount();i++)assertTrue("secção visível "+i,l.listBox.getChildAt(i).getHeight()>0);
        assertTrue(BooksToolsTest.labels(l.listBox).contains("Compras"));
        // Search finds titles and text, never locked text.
        l.query="hinos";l.drawList();assertTrue(BooksToolsTest.labels(l.listBox).contains("Sermão de domingo"));
        l.query="secreto";l.drawList();assertFalse(BooksToolsTest.labels(l.listBox).contains("Diário"));
        l.query="";
        // Tasks tab: every task of every note, ticked from the list.
        l.tab="tasks";l.list();drain(l);
        labels=BooksToolsTest.labels(l.listBox);
        assertTrue(labels,labels.contains("Por fazer · 3")&&labels.contains("Concluídas · 1")&&labels.contains("Preparar slides")&&labels.contains("Pão"));
        capture(root(l),"tarefas",800);
        NotepadStore.Note shop=null;for(NotepadStore.Note x:NotepadStore.all(app))if(x.title.equals("Compras"))shop=x;
        l.toggleTask(shop,0);drain(l);
        assertTrue(NotepadStore.load(app,shop.id).body.startsWith("[x] Pão"));
        // Sections by date.
        long now=System.currentTimeMillis();
        assertEquals("Hoje",NotepadActivity.section(now,now));assertEquals("Ontem",NotepadActivity.section(now-86400000L,now));assertEquals("7 dias anteriores",NotepadActivity.section(now-3*86400000L,now));assertEquals("30 dias anteriores",NotepadActivity.section(now-20*86400000L,now));
        // Reminder: an alarm is set and firing it shows the note.
        NotepadStore.Note r=NotepadStore.load(app,shop.id);r.reminder=now+3600000L;NotepadStore.save(app,r);
        AlarmManager alarms=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);
        assertNotNull("lembrete marcado",shadowOf(alarms).getNextScheduledAlarm());
        NotepadStore.fire(app,shop.id);
        NotificationManager nm=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        assertFalse("notificação do lembrete",shadowOf(nm).getAllNotifications().isEmpty());
        assertEquals(0,NotepadStore.load(app,shop.id).reminder);
        // The locked note keeps its lock and its colour.
        NotepadStore.Note locked=NotepadStore.load(app,secret);assertTrue(locked.locked);assertEquals(NotepadStore.COLORS[3],locked.color);
    }

    @Test public void bookNotesAreWrittenInsideTheReaderAndFoundInTheNotebook()throws Exception{
        WorkspaceEpoch.value=0;WorkspaceEpoch.restoring=false;
        app.getSharedPreferences("seeky",0).edit().clear().apply();
        ReaderPlusTest.source=new File(app.getCacheDir(),"livro-539.txt");
        Files.write(ReaderPlusTest.source.toPath(),"O Peregrino\n\nCristão saiu da Cidade da Destruição com um fardo às costas.\n\nEvangelista mostrou-lhe a porta estreita.".getBytes("UTF-8"));
        ReaderPlusTest.Source provider=new ReaderPlusTest.Source();ProviderInfo info=new ProviderInfo();info.authority="seeky.notes539.reader";provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
        ActivityController<ReaderActivity> rc=Robolectric.buildActivity(ReaderActivity.class,new Intent().setDataAndType(Uri.parse("content://seeky.notes539.reader/book"),"text/plain").putExtra("name","O Peregrino.txt"));
        ReaderActivity r=rc.get();r.setTheme(android.R.style.Theme_Material_NoActionBar);rc.create().start().resume().visible();open.add(rc);
        for(int i=0;i<5;i++){r.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        ReaderNotes.show(r,"um fardo às costas");
        for(int i=0;i<4;i++){r.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        UiPanel panel=UiPanel.open.get(r).iterator().next();
        String labels=BooksToolsTest.labels(panel.root);
        for(String s:new String[]{"Notas deste livro","O Peregrino","Guardar nota","Abrir no Bloco de notas","Ainda não há notas deste livro"})assertTrue(s+" em "+labels,labels.contains(s));
        EditText input=null;for(View v:all(panel.content))if(v instanceof EditText)input=(EditText)v;
        assertNotNull(input);assertTrue(input.getText().toString().startsWith("> um fardo às costas"));
        input.append("O fardo é o pecado; a cruz tira-o. Ver Mateus 11:28.");
        capture(panel.root,"leitor-notas",800);
        View save=null;for(View v:all(panel.content))if(v instanceof TextView&&((TextView)v).getText().toString().equals("Guardar nota"))save=v;
        save.performClick();
        for(int i=0;i<4;i++){r.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        List<NotepadStore.Note> mine=NotepadStore.forBook(app,0,"content://seeky.notes539.reader/book");
        assertEquals(1,mine.size());assertEquals("O Peregrino",mine.get(0).book());assertTrue(mine.get(0).body.contains("Mateus 11:28"));
        // The panel now lists the note.
        ReaderNotes.show(r,"");for(int i=0;i<4;i++){r.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        UiPanel second=null;for(UiPanel p:UiPanel.open.get(r))if(p!=panel)second=p;
        assertTrue(BooksToolsTest.labels(second.root).contains("NOTAS DESTE LIVRO · 1"));
        UiPanel.dismissAll(r);
        // The notebook shows the book's notes under its name.
        NotepadActivity l=launch(new Intent(app,NotepadActivity.class).putExtra("book_uri","content://seeky.notes539.reader/book").putExtra("book_title","O Peregrino"));
        labels=BooksToolsTest.labels(root(l));
        assertTrue(labels,labels.contains("O Peregrino")&&labels.contains("um fardo às costas"));
        capture(root(l),"notas-do-livro",800);
        // A new note from the book filter is linked to the book.
        NotepadStore.Note fresh=l.blank();assertEquals("content://seeky.notes539.reader/book",fresh.source);assertEquals("O Peregrino",fresh.sourceTitle);
    }
    static List<View> all(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(all(((ViewGroup)v).getChildAt(i)));return out;}

    @Test public void gameLabelsUseAGlyphTheFontHas()throws Exception{
        ActivityController<BibleGameActivity> c=Robolectric.buildActivity(BibleGameActivity.class);BibleGameActivity g=c.get();g.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();open.add(c);
        for(String k:new String[]{"verse","refverse","chapter","free"})assertFalse(g.kindLabel(k),g.kindLabel(k).contains("→"));
        assertEquals("Passagem · livro",g.kindLabel("verse"));assertEquals("Referência · passagem",g.kindLabel("refverse"));
    }
}
