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
            for(SyncState.Course x:ch.courses){st.courses.removeIf(y->y.key().equals(x.key()));st.courses.add(x);}for(SyncState.Subject x:ch.subjects){st.subjects.removeIf(y->y.key().equals(x.key()));st.subjects.add(x);}
            for(SyncState.StudyCard x:ch.studyCards){st.studyCards.removeIf(y->y.key().equals(x.key()));st.studyCards.add(x);}
            for(SyncState.Task x:ch.tasks){st.tasks.removeIf(y->y.key().equals(x.key()));st.tasks.add(x);}
            st.courses.removeIf(y->ch.remove.contains(y.key()));st.subjects.removeIf(y->ch.remove.contains(y.key()));st.studyCards.removeIf(y->ch.remove.contains(y.key()));st.tasks.removeIf(y->ch.remove.contains(y.key()));st.gone.putAll(ch.goneTombs);
            return ch;
        }
        public synchronized SyncState state(String device,long since){SyncState c=new SyncState();c.notes.addAll(st.notes);c.deleted.putAll(st.deleted);c.readings.addAll(st.readings);c.cards.addAll(st.cards);c.subjects.addAll(st.subjects);c.courses.addAll(st.courses);c.studyCards.addAll(st.studyCards);c.tasks.addAll(st.tasks);c.gone.putAll(st.gone);return c;}
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

    @Test public void coursesWithModulesAndSubjectsTravelBothWays()throws Exception{
        // 5.52: Curso › Módulo ou Disciplina, the same on the phone and on the computer.
        FakePc pc=new FakePc(app.getCacheDir());SyncServer server=new SyncServer(pc,0,0);server.start();
        try{
            PhoneSync.remember(app,SyncClient.pair("127.0.0.1",server.port(),PhoneSync.device(app),"Telemóvel","123456"));
            long now=System.currentTimeMillis(),course,subject;
            try(StudyStore db=new StudyStore(app)){course=db.save("courses",0,StudyStore.values("title","Bacharelato","description","Quatro anos","created",now));subject=db.save("subjects",0,StudyStore.values("title","Hermenêutica","color",0xff3f6b4f,"created",now,"course",course,"kind","module"));}
            SyncState.Course pcCourse=new SyncState.Course();pcCourse.title="Curso do computador";pcCourse.updated=now;pc.st.courses.add(pcCourse);
            SyncState.Subject greek=new SyncState.Subject();greek.title="Grego";greek.course="Curso do computador";greek.kind="module";greek.updated=now;pc.st.subjects.add(greek);
            PhoneSync.now(app);
            try(StudyStore db=new StudyStore(app);android.database.Cursor r=db.getReadableDatabase().rawQuery("SELECT s.kind FROM subjects s JOIN courses c ON c.id=s.course WHERE s.title='Grego' AND c.title='Curso do computador'",null)){assertTrue("o módulo do computador chegou dentro do curso",r.moveToFirst());assertEquals("module",r.getString(0));}
            SyncState.Subject mine=null;for(SyncState.Subject x:pc.st.subjects)if(x.title.equals("Hermenêutica"))mine=x;assertNotNull("o módulo do telemóvel chegou ao computador",mine);assertEquals("Bacharelato",mine.course);assertEquals("module",mine.kind);
            boolean described=false;for(SyncState.Course x:pc.st.courses)if(x.title.equals("Bacharelato")&&x.description.equals("Quatro anos"))described=true;assertTrue("o curso com a descrição chegou ao computador",described);
            Thread.sleep(5);
            try(StudyStore db=new StudyStore(app)){db.getWritableDatabase().execSQL("UPDATE subjects SET kind='subject' WHERE id=?",new Object[]{subject});}
            PhoneSync.now(app);
            for(SyncState.Subject x:pc.st.subjects)if(x.title.equals("Hermenêutica"))mine=x;assertEquals("mudar de módulo para disciplina também chega",  "subject",mine.kind);
            try(StudyStore db=new StudyStore(app)){db.deleteCourse(course);}
            PhoneSync.now(app);
            boolean still=false;for(SyncState.Course x:pc.st.courses)if(x.title.equals("Bacharelato"))still=true;assertFalse("o curso apagado no telemóvel é apagado no computador",still);
            for(SyncState.Subject x:pc.st.subjects)if(x.title.equals("Hermenêutica"))mine=x;assertEquals("o módulo fica, sem curso","",mine.course);
        }finally{server.close();}
    }
    @Test public void studiesTravelBothWays()throws Exception{
        // 5.50: subjects, cards and tasks of Estudos; deletions on either side.
        FakePc pc=new FakePc(app.getCacheDir());SyncServer server=new SyncServer(pc,0,0);server.start();
        try{
            PhoneSync.remember(app,SyncClient.pair("127.0.0.1",server.port(),PhoneSync.device(app),"Telemóvel","123456"));
            long now=System.currentTimeMillis(),subject,card,task;
            try(StudyStore db=new StudyStore(app)){
                subject=db.save("subjects",0,StudyStore.values("title","Teologia","color",0xff3f6b4f,"created",now));
                card=db.save("cards",0,StudyStore.values("subject",subject,"front","O que é a graça?","back","Favor imerecido","due",now,"updated",now));
                task=db.save("tasks",0,StudyStore.values("subject",subject,"title","Ler Romanos","due",0L,"updated",now));
            }
            SyncState.Subject eng=new SyncState.Subject();eng.title="Inglês";eng.created=1;eng.updated=1;pc.st.subjects.add(eng);
            SyncState.StudyCard faith=new SyncState.StudyCard();faith.subject="Inglês";faith.front="Faith";faith.back="Fé";faith.due=now;faith.updated=now;pc.st.studyCards.add(faith);
            SyncState.Task essay=new SyncState.Task();essay.subject="Teologia";essay.title="Resumo da Reforma";essay.due=now+86400000L;essay.updated=now;pc.st.tasks.add(essay);
            PhoneSync.now(app);
            try(StudyStore db=new StudyStore(app);android.database.Cursor r=db.getReadableDatabase().rawQuery("SELECT c.back,s.title FROM cards c JOIN subjects s ON s.id=c.subject WHERE c.front='Faith'",null)){assertTrue("o cartão do computador chegou",r.moveToFirst());assertEquals("Fé",r.getString(0));assertEquals("Inglês",r.getString(1));}
            try(StudyStore db=new StudyStore(app);android.database.Cursor r=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM tasks WHERE title='Resumo da Reforma' AND subject=?",new String[]{""+subject})){r.moveToFirst();assertEquals("a tarefa ficou na disciplina com o mesmo nome",1,r.getInt(0));}
            boolean got=false;for(SyncState.StudyCard x:pc.st.studyCards)if(x.front.equals("O que é a graça?")&&x.subject.equals("Teologia"))got=true;assertTrue("o cartão do telemóvel chegou ao computador",got);
            // Deleted on the phone → deleted on the computer; deleted on the computer → deleted on the phone.
            try(StudyStore db=new StudyStore(app)){db.delete("tasks",task);}
            pc.st.gone.put(faith.key(),System.currentTimeMillis()+1000);pc.st.studyCards.removeIf(x->x.front.equals("Faith"));
            PhoneSync.now(app);
            boolean taskThere=false;for(SyncState.Task x:pc.st.tasks)if(x.title.equals("Ler Romanos"))taskThere=true;assertFalse("tarefa apagada no telemóvel saiu do computador",taskThere);
            try(StudyStore db=new StudyStore(app);android.database.Cursor r=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cards WHERE front='Faith'",null)){r.moveToFirst();assertEquals("cartão apagado no computador saiu do telemóvel",0,r.getInt(0));}
            assertEquals(0,PhoneSync.now(app).otherHere);
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
