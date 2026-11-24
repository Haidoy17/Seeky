package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.graphics.*;import android.net.Uri;import android.os.*;import android.view.*;import java.io.*;import java.nio.file.Files;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.46: notes between the phone and Seeky for Windows (".seekynotes"). */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h800dp-mdpi",application=Application.class,shadows={PdfCanvasHost.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Notepad546Test {
    final Application app=RuntimeEnvironment.getApplication();
    final List<ActivityController<?>> open=new ArrayList<>();
    @After public void close(){for(ActivityController<?> c:open)try{c.pause().stop().destroy();}catch(Exception ignored){}UiDialog.dismissAll(null);PlusUi.MAIN.removeCallbacksAndMessages(null);}
    void drain(NotepadActivity a)throws Exception{for(int i=0;i<5;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);PlusUi.WORK.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idleFor(800,TimeUnit.MILLISECONDS);}}
    NotepadActivity launch(Intent i)throws Exception{ActivityController<NotepadActivity> c=Robolectric.buildActivity(NotepadActivity.class,i);NotepadActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();open.add(c);drain(a);return a;}
    UiPanel panel(Activity a){Set<UiPanel> all=UiPanel.open.get(a);assertNotNull("painel aberto",all);UiPanel p=null;for(UiPanel x:all)if(x.dialog.isShowing())p=x;assertNotNull(p);return p;}
    static NotepadStore.Note note(String title,String body,String folder){NotepadStore.Note n=new NotepadStore.Note();n.title=title;n.body=body;n.folder=folder;return n;}

    @Test public void notesGoToTheComputerAndComeBackWithPicturesOnce()throws Exception{
        Files.write(NotepadStore.media(app,"img_546.png").toPath(),new byte[]{(byte)0x89,'P','N','G',5,4,6});
        NotepadStore.Note sermon=note("Sermão de domingo","## Fé\nLer Hebreus 11:1\n![Foto](note-img:img_546.png)","Sermões");sermon.pinned=true;sermon.spacing=150;NotepadStore.save(app,sermon);
        NotepadStore.Note secret=note("Diário","Só no telemóvel","");secret.locked=true;NotepadStore.save(app,secret);
        NotepadTransfer.Result out=new NotepadTransfer.Result();byte[] data=NotepadTransfer.export(app,out);
        assertEquals("notas enviadas",1,out.added);assertEquals("notas bloqueadas ficam no telemóvel",1,out.locked);
        NotesTransfer.Parsed parsed=NotesTransfer.read(data);
        assertEquals("android",parsed.from);assertEquals(1,parsed.notes.size());assertEquals("Sermões",parsed.notes.get(0).folder);assertTrue(parsed.notes.get(0).pinned);
        assertArrayEquals(Files.readAllBytes(NotepadStore.media(app,"img_546.png").toPath()),parsed.media.get("img_546.png"));
        assertFalse("o texto de notas bloqueadas não sai do telemóvel",new String(data,"UTF-8").contains("Só no telemóvel"));
        // Importing on the same phone does not repeat the note.
        NotepadTransfer.Result again=NotepadTransfer.restore(app,data);assertEquals(0,again.added);assertEquals(1,again.skipped);
        // A note written on the computer, with a picture whose name already exists here with other content.
        NotesTransfer.Item pc=new NotesTransfer.Item();pc.title="Do computador";pc.body="Escrita no Windows\n![Imagem](note-img:img_546.png)";pc.folder="Estudos";pc.created=1700000000000L;pc.updated=1700000000000L;
        Map<String,byte[]> files=new HashMap<>();files.put("img_546.png",new byte[]{1,2,3});
        File pack=new File(app.getCacheDir(),NotesTransfer.fileName());Files.write(pack.toPath(),NotesTransfer.write(Collections.singletonList(pc),files::get,"windows"));
        NotepadActivity n=launch(new Intent(app,NotepadActivity.class));
        n.transferResult(NotepadActivity.TRANSFER_IN,Uri.fromFile(pack));drain(n);
        NotepadStore.Note imported=null;for(NotepadStore.Note x:NotepadStore.all(app))if(x.title.equals("Do computador"))imported=x;
        assertNotNull("a nota do computador foi importada",imported);assertEquals("Estudos",imported.folder);assertEquals(1700000000000L,imported.created);
        assertFalse("a imagem do telemóvel não foi substituída",imported.body.contains("note-img:img_546.png)"));
        String renamed=NotesTransfer.media(imported.body).iterator().next();
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(NotepadStore.media(app,renamed).toPath()));
        assertArrayEquals(new byte[]{(byte)0x89,'P','N','G',5,4,6},Files.readAllBytes(NotepadStore.media(app,"img_546.png").toPath()));
        // Saving to a file chosen by the user.
        File saved=new File(app.getCacheDir(),"guardado.seekynotes");
        n.transferResult(NotepadActivity.TRANSFER_OUT,Uri.fromFile(saved));drain(n);
        assertEquals(2,NotesTransfer.read(Files.readAllBytes(saved.toPath())).notes.size());
        // A file that is not a Seeky notes file changes nothing.
        File other=new File(app.getCacheDir(),"outro.json");Files.write(other.toPath(),"{\"a\":1}".getBytes("UTF-8"));
        int before=NotepadStore.all(app).size();n.transferResult(NotepadActivity.TRANSFER_IN,Uri.fromFile(other));drain(n);assertEquals(before,NotepadStore.all(app).size());
    }

    @Test public void foldersPanelOffersTheComputer()throws Exception{
        NotepadStore.save(app,note("Uma nota","Texto",""));
        NotepadActivity n=launch(new Intent(app,NotepadActivity.class));
        n.folders();drain(n);
        String labels=BooksToolsTest.labels(panel(n).root);
        for(String s:new String[]{"Enviar notas para o computador","Importar notas do computador","Lixo"})assertTrue(s+" em "+labels,labels.contains(s));
        View root=panel(n).root;root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));root.layout(0,0,360,800);
        Bitmap b=Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);root.draw(new Canvas(b));
        String shot=System.getProperty("seeky.screenshot");if(shot!=null)try(FileOutputStream o=new FileOutputStream(shot+"-5460-pastas.png")){b.compress(Bitmap.CompressFormat.PNG,100,o);}
        UiPanel.dismissAll(n);
        n.sendToComputer();drain(n);
        String send=BooksToolsTest.labels(panel(n).root);
        assertTrue(send,send.contains("Partilhar")&&send.contains("Guardar num ficheiro"));
    }
}
