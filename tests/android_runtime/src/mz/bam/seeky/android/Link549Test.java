package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import java.io.*;import java.nio.file.Files;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.49: Seeky no computador — pairing, sync of notes/study/reading, deletions and files, against a computer in memory. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h800dp-mdpi",application=Application.class,shadows={PdfCanvasHost.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Link549Test {
    final Application app=RuntimeEnvironment.getApplication();
    /** A computer kept in memory that merges like Seeky for Windows. */
    static final class FakePc implements SyncServer.Host {
        final SyncState st=new SyncState();final Map<String,byte[]> tokens=new HashMap<>();String code="123456";final File outbox;final File inbox;final List<String[]> queue=new ArrayList<>();
        FakePc(File dir){outbox=new File(dir,"para o telemóvel.txt");inbox=new File(dir,"recebidos");inbox.mkdirs();}
        public String id(){return "pc-teste";}public String name(){return "PC de teste";}
        public synchronized String code(){return code;}public synchronized void codeUsed(){code=null;}public void codeFailed(){}
        public synchronized byte[] token(String d){return tokens.get(d);}public synchronized void paired(String d,String n,byte[] t){tokens.put(d,t);}
        public synchronized SyncState.Changes apply(String device,SyncState phone){
            SyncState.Changes ch=SyncState.merge(st,phone);
            for(Map.Entry<String,String> e:ch.relink.entrySet())for(SyncState.Note n:st.notes)if(n.uuid.equals(e.getKey()))n.uuid=e.getValue();
            for(SyncState.Note n:ch.upsert){st.notes.removeIf(x->x.uuid.equals(n.uuid));st.notes.add(n);}
            st.notes.removeIf(x->ch.delete.contains(x.uuid));st.deleted.putAll(ch.tombs);
            for(String[] r:ch.readings)st.readings.add(SyncState.reading(r[0],r[1],Integer.parseInt(r[2])));
            for(SyncState.Card c:ch.cards){st.cards.removeIf(x->x.reference.equals(c.reference));st.cards.add(c);}
            return ch;
        }
        public synchronized SyncState state(String device,long since){SyncState c=new SyncState();c.notes.addAll(st.notes);c.deleted.putAll(st.deleted);c.readings.addAll(st.readings);c.cards.addAll(st.cards);return c;}
        public void synced(String d,String n,SyncState.Changes ch){}
        public synchronized List<String[]> outbox(){return new ArrayList<>(queue);}
        public File outboxFile(String id){return outbox;}
        public synchronized void taken(String id){queue.removeIf(q->q[0].equals(id));}
        public File incoming(String name){return SyncWire.unique(inbox,name);}
        public void received(File f,String d){}
        SyncState.Note note(String title){for(SyncState.Note n:st.notes)if(n.title.equals(title))return n;return null;}
    }
    static NotepadStore.Note find(Context c,String title){for(NotepadStore.Note n:NotepadStore.all(c))if(n.title.equals(title))return n;return null;}

    @Test public void phoneAndComputerEndUpTheSame()throws Exception{
        FakePc pc=new FakePc(app.getCacheDir());SyncServer server=new SyncServer(pc,0,0);server.start();
        try{
            // Pairing: a wrong code is refused, the right one gives the phone a secret.
            try{SyncClient.pair("127.0.0.1",server.port(),PhoneSync.device(app),"Telemóvel","654321");fail();}catch(IOException e){assertTrue(e.getMessage(),e.getMessage().contains("Código errado"));}
            SyncClient.Computer computer=SyncClient.pair("127.0.0.1",server.port(),PhoneSync.device(app),"Telemóvel","123456");PhoneSync.remember(app,computer);
            assertNull("o código só serve uma vez",pc.code());
            // The phone: a note, a locked note (stays here), a chapter read. The computer: a note and a verse to memorise.
            NotepadStore.Note mine=new NotepadStore.Note();mine.title="Do telemóvel";mine.body="Texto do telemóvel";mine.folder="Sermões";NotepadStore.save(app,mine);
            NotepadStore.Note secret=new NotepadStore.Note();secret.title="Segredo";secret.body="Não sai daqui";secret.locked=true;NotepadStore.save(app,secret);
            try(BibleStudyStore b=new BibleStudyStore(app)){ContentValues v=new ContentValues();v.put("day","2026-11-20");v.put("book","João");v.put("chapter",3);v.put("at",1L);b.getWritableDatabase().insert("readings",null,v);}
            SyncState.Note theirs=new SyncState.Note();theirs.uuid=SyncState.uuid();theirs.title="Do computador";theirs.body="Escrita no Windows";theirs.created=1000;theirs.updated=2000;pc.st.notes.add(theirs);
            SyncState.Card card=new SyncState.Card();card.reference="João 3:16";card.text="Porque Deus amou o mundo.";card.interval=3;card.due=5000;pc.st.cards.add(card);
            File waiting=pc.outbox;Files.write(waiting.toPath(),"olá do computador".getBytes("UTF-8"));pc.queue.add(new String[]{"f1",waiting.getName(),""+waiting.length()});
            PhoneSync.Summary s=PhoneSync.now(app);
            assertEquals(1,s.notesHere);assertEquals(1,s.files);
            assertNotNull("a nota do computador chegou",find(app,"Do computador"));
            assertNotNull("a nota do telemóvel chegou ao computador",pc.note("Do telemóvel"));assertEquals("Sermões",pc.note("Do telemóvel").folder);
            assertNull("a nota bloqueada não saiu do telemóvel",pc.note("Segredo"));
            assertTrue(pc.st.readings.contains("2026-11-20|João|3"));
            try(BibleStudyStore b=new BibleStudyStore(app);android.database.Cursor r=b.getReadableDatabase().rawQuery("SELECT text,interval FROM cards WHERE title='João 3:16'",null)){assertTrue(r.moveToFirst());assertEquals("Porque Deus amou o mundo.",r.getString(0));assertEquals(3,r.getInt(1));}
            File got=new File(PhoneSync.downloads(),"para o telemóvel.txt");assertTrue(got.isFile());assertEquals("olá do computador",new String(Files.readAllBytes(got.toPath()),"UTF-8"));assertTrue(pc.queue.isEmpty());
            // Deleted on one side → deleted on the other; edited on the computer later → the phone gets the new text.
            pc.st.deleted.put(pc.note("Do telemóvel").uuid,System.currentTimeMillis()+1000);pc.st.notes.remove(pc.note("Do telemóvel"));
            NotepadStore.trash(app,find(app,"Do computador").id);
            SyncState.Note edit=new SyncState.Note();edit.uuid=SyncState.uuid();edit.title="Nova no PC";edit.body="x";edit.created=1;edit.updated=System.currentTimeMillis();pc.st.notes.add(edit);
            PhoneSync.now(app);
            assertNull("apagada no computador → apagada no telemóvel",find(app,"Do telemóvel"));
            assertNull("apagada no telemóvel → apagada no computador",pc.note("Do computador"));
            assertNotNull(find(app,"Nova no PC"));
            // A third sync has nothing to do.
            PhoneSync.Summary quiet=PhoneSync.now(app);assertEquals(0,quiet.notesHere);assertEquals(0,quiet.notesThere);
            // Phone → computer file.
            byte[] data=new byte[300000];new Random(5).nextBytes(data);
            String saved=SyncClient.send(PhoneSync.computer(app),PhoneSync.device(app),"Telemóvel","foto.jpg",data.length,new ByteArrayInputStream(data));
            assertArrayEquals(data,Files.readAllBytes(new File(pc.inbox,saved).toPath()));
        }finally{server.close();}
    }

    @Test public void toolsShowTheComputerPanel()throws Exception{
        ToolsActivity t=Robolectric.buildActivity(ToolsActivity.class,new Intent(app,ToolsActivity.class).putExtra("open_link",true)).setup().get();
        shadowOf(Looper.getMainLooper()).idleFor(600,TimeUnit.MILLISECONDS);
        Set<UiPanel> open=UiPanel.open.get(t);assertNotNull(open);UiPanel p=open.iterator().next();
        String labels=BooksToolsTest.labels(p.root);assertTrue(labels,labels.contains("Seeky no computador")&&labels.contains("Ligar ao computador"));
        UiPanel.dismissAll(t);
    }
}
