package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.content.pm.ProviderInfo;import android.graphics.*;import android.net.Uri;import android.os.*;import android.text.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.file.Files;import java.util.*;import java.util.concurrent.TimeUnit;import java.util.zip.*;
import org.json.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.40: Seeky IA in notes, line spacing, and "Bloco" in the selection panel of the reader. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h800dp-mdpi",application=Application.class,shadows={PdfCanvasHost.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Notepad540Test {
    final Application app=RuntimeEnvironment.getApplication();
    final List<ActivityController<?>> open=new ArrayList<>();
    NotepadAi.Clients clients;NotepadAi.Vaults vaults;final List<JSONObject> sent=new ArrayList<>();String reply="";
    @Before public void before(){clients=NotepadAi.clients;vaults=NotepadAi.vaults;
        app.getSharedPreferences("seeky_ai",0).edit().putBoolean("configured_gemini",true).putBoolean("terms_gemini",true).apply();
        NotepadAi.vaults=c->new SeekyAiVault(c){synchronized String get(String provider){return "fixture-notepad-credential";}};
        NotepadAi.clients=()->new SeekyAiClient((p,k,b,c)->{sent.add(b);return new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("finishReason","STOP").put("content",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",reply)))))).toString();});}
    @After public void close(){NotepadAi.clients=clients;NotepadAi.vaults=vaults;for(ActivityController<?> c:open)try{c.pause().stop().destroy();}catch(Exception ignored){}UiDialog.dismissAll(null);PlusUi.MAIN.removeCallbacksAndMessages(null);}
    void drain(NotepadActivity a)throws Exception{for(int i=0;i<5;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idleFor(800,TimeUnit.MILLISECONDS);}}
    NotepadActivity launch(Intent i)throws Exception{ActivityController<NotepadActivity> c=Robolectric.buildActivity(NotepadActivity.class,i);NotepadActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();open.add(c);drain(a);return a;}
    static void capture(View v,String name,int height)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,360,height);
        Bitmap b=Bitmap.createBitmap(360,height,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5400-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    UiDialog last(Activity a){Set<UiDialog> all=UiDialog.open.get(a);assertNotNull("diálogo aberto",all);UiDialog d=null;for(UiDialog x:all)if(x.isShowing())d=x;assertNotNull(d);return d;}
    UiPanel panel(Activity a){Set<UiPanel> all=UiPanel.open.get(a);assertNotNull("painel aberto",all);UiPanel p=null;for(UiPanel x:all)if(x.dialog.isShowing())p=x;assertNotNull(p);return p;}

    @Test public void seekyAiSummarisesCorrectsASelectionAndKeepsPictures()throws Exception{
        NotepadActivity n=launch(new Intent(app,NotepadActivity.class).putExtra("new",true));
        n.title.setText("A fé");
        n.body.setText("A fé é a certeza das coisas que se esperam.\nsem fé é impossivel agradar a Deus.\n![Foto](note-img:img_1.png)\n");
        shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS);
        // The IA panel lists the actions.
        n.tool("ai");drain(n);
        String labels=BooksToolsTest.labels(panel(n).root);
        for(String s:new String[]{"Seeky IA","Resumir","Corrigir o texto","Organizar em tópicos","Perguntas de estudo","Extrair tarefas","Passagens relacionadas","Continuar a escrever","Pedir outra coisa…"})assertTrue(s+" em "+labels,labels.contains(s));
        capture(panel(n).root,"ia-painel",800);
        UiPanel.dismissAll(n);
        // Summarise: the note goes without the picture; the answer is shown, then added under "Resumo".
        reply="```\nA fé confia no que Deus prometeu.\n* certeza\n* agradar a Deus\n```";
        NotepadAi.run(n,NotepadAiText.action("resumir"),"");drain(n);
        String request=sent.get(0).toString();
        assertTrue(request,request.contains("certeza das coisas")&&request.contains("Resume esta nota")&&!request.contains("note-img"));
        assertFalse("a chave não vai no pedido",request.contains("fixture-notepad-credential"));
        UiDialog d=last(n);
        assertTrue(BooksToolsTest.labels(d.getWindow().getDecorView()).contains("A fé confia"));
        capture(d.getWindow().getDecorView(),"ia-resposta",800);
        d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();drain(n);
        String t=n.body.getText().toString();
        assertTrue(t,t.contains("## Resumo\nA fé confia no que Deus prometeu.\n- certeza\n- agradar a Deus")&&t.contains("note-img:img_1.png"));
        n.tool("undo");assertFalse(n.body.getText().toString().contains("## Resumo"));
        // Correct only the selected line.
        t=n.body.getText().toString();int at=t.indexOf("sem fé");n.body.setSelection(at,t.indexOf('\n',at));
        reply="Sem fé é impossível agradar a Deus.";
        NotepadAi.run(n,NotepadAiText.action("corrigir"),"");drain(n);
        assertTrue(sent.get(1).toString().contains("sem fé é impossivel"));assertFalse(sent.get(1).toString().contains("certeza das coisas"));
        last(n).getButton(DialogInterface.BUTTON_POSITIVE).performClick();drain(n);
        t=n.body.getText().toString();
        assertTrue(t,t.startsWith("A fé é a certeza das coisas que se esperam.\nSem fé é impossível agradar a Deus.\n![Foto](note-img:img_1.png)"));
        // Without the Gemini set-up, the IA explains how to connect it.
        app.getSharedPreferences("seeky_ai",0).edit().clear().apply();
        n.tool("ai");drain(n);
        assertTrue(BooksToolsTest.labels(last(n).getWindow().getDecorView()).contains("Estudar com Seeky"));
    }

    @Test public void lineSpacingIsKeptAndExported()throws Exception{
        NotepadActivity n=launch(new Intent(app,NotepadActivity.class).putExtra("new",true));
        n.title.setText("Espaços");n.body.setText("Linha um\nLinha dois\nLinha três");shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS);
        n.tool("format");drain(n);
        String sheet=BooksToolsTest.labels(n.formatSheet);assertTrue(sheet,sheet.contains("Espaçamento entre linhas")&&sheet.contains("1,5")&&sheet.contains("2,0")&&sheet.contains("Normal"));
        View option=PageTurnTest.findDescription(n.formatSheet,"Espaçamento entre linhas 1,5");assertNotNull(option);option.performClick();drain(n);
        assertEquals(150,n.note.spacing);assertEquals(1.5f,n.body.getLineSpacingMultiplier(),.001f);
        capture(n.getWindow().getDecorView(),"espacamento",1000);
        assertEquals(150,NotepadStore.load(app,n.note.id).spacing);
        ByteArrayOutputStream word=new ByteArrayOutputStream();n.write("docx",word);
        String styles="";try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(word.toByteArray()))){ZipEntry e;while((e=in.getNextEntry())!=null)if(e.getName().equals("word/styles.xml"))styles=new String(readAll(in),"UTF-8");}
        assertTrue(styles.contains("w:line=\"360\""));
        ByteArrayOutputStream pdf=new ByteArrayOutputStream();n.write("pdf",pdf);assertTrue(pdf.size()>100);
        // The bin keeps the spacing.
        NotepadStore.trash(app,n.note.id);long back=NotepadStore.restore(app,NotepadStore.trashed(app).get(0).id);assertEquals(150,NotepadStore.load(app,back).spacing);
    }
    static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}

    @Test public void selectionPanelSendsTheWordsToANoteOfTheBook()throws Exception{
        WorkspaceEpoch.value=0;WorkspaceEpoch.restoring=false;
        app.getSharedPreferences("seeky",0).edit().clear().apply();
        ReaderPlusTest.source=new File(app.getCacheDir(),"livro-540.txt");
        Files.write(ReaderPlusTest.source.toPath(),"Confissões\n\nO nosso coração anda inquieto enquanto não descansa em ti.\n\nTarde te amei.".getBytes("UTF-8"));
        ReaderPlusTest.Source provider=new ReaderPlusTest.Source();ProviderInfo info=new ProviderInfo();info.authority="seeky.notes540.reader";provider.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
        ActivityController<ReaderActivity> rc=Robolectric.buildActivity(ReaderActivity.class,new Intent().setDataAndType(Uri.parse("content://seeky.notes540.reader/book"),"text/plain").putExtra("name","Confissões.txt"));
        ReaderActivity r=rc.get();r.setTheme(android.R.style.Theme_Material_NoActionBar);rc.create().start().resume().visible();open.add(rc);
        for(int i=0;i<5;i++){r.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        MarkBar bar=MarkBar.show(r,kind->{});shadowOf(Looper.getMainLooper()).idle();
        assertNotNull("Bloco no painel de selecção",PageTurnTest.findDescription(bar.card,"Bloco"));
        assertTrue("cabe num telefone de 360 dp",bar.card.getMeasuredWidth()<=344);
        capture(bar.card,"painel-seleccao-bloco",bar.card.getMeasuredHeight());
        bar.dismiss();
        String text=r.tools.draft.text;int at=text.indexOf("O nosso coração");
        Selection.setSelection((Spannable)r.bookText.getText(),at,at+"O nosso coração anda inquieto".length());
        r.tools.selectionAction("notepad",null);
        for(int i=0;i<4;i++){r.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
        UiPanel p=panel(r);
        EditText input=null;for(View v:Notepad539Test.all(p.content))if(v instanceof EditText)input=(EditText)v;
        assertNotNull(input);assertTrue(input.getText().toString(),input.getText().toString().startsWith("> O nosso coração anda inquieto"));
        UiPanel.dismissAll(r);
    }

    @Test public void everyNoteIconIsDrawn()throws Exception{
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        int cols=6,cell=60,rows=(NoteIcons.NAMES.length+cols-1)/cols;
        Bitmap sheet=Bitmap.createBitmap(cols*cell,rows*(cell+14),Bitmap.Config.ARGB_8888);Canvas board=new Canvas(sheet);board.drawColor(0xfffaf7f0);
        Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);label.setTextSize(9);label.setColor(0xff555555);label.setTextAlign(Paint.Align.CENTER);
        for(int i=0;i<NoteIcons.NAMES.length;i++){
            String name=NoteIcons.NAMES[i];View v=NoteIcons.of(a,name,0xff2f3a33);assertTrue(name,v instanceof NoteIcons);
            v.measure(View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));v.layout(0,0,48,48);
            Bitmap one=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);v.draw(new Canvas(one));
            int ink=0;for(int y=0;y<48;y++)for(int x=0;x<48;x++)if(Color.alpha(one.getPixel(x,y))>60)ink++;
            assertTrue("o ícone "+name+" tem desenho ("+ink+")",ink>25);
            int cx=(i%cols)*cell,cy=(i/cols)*(cell+14);board.drawBitmap(one,cx+6,cy+4,null);board.drawText(name,cx+cell/2f,cy+cell+6,label);
        }
        assertTrue("nomes desconhecidos usam os ícones gerais",NoteIcons.of(a,"settings",0xff000000) instanceof Icons);
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5410-icones-notas.png")){sheet.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
}
