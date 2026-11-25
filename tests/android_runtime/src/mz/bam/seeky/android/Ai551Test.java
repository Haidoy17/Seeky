package mz.bam.seeky.android;
import android.app.*;import java.io.*;import java.util.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.annotation.*;import static org.junit.Assert.*;

/** 5.51: whole book summarised into a general summary (with pauses at the request limit) and a study plan saved in the Studies. */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=33,application=Application.class) @LooperMode(LooperMode.Mode.PAUSED)
public class Ai551Test {
    WorkTestSupport w;SummaryWork.Clients originalClients;SummaryWork.Vaults originalVaults;int requests;final List<String> systems=new ArrayList<>();
    @Before public void before()throws Exception{w=new WorkTestSupport();originalClients=SummaryWork.clients;originalVaults=SummaryWork.vaults;SummaryWork.vaults=c->new SeekyAiVault(c){synchronized String get(String provider){return "fixture-summary-credential";}};w.app.getSharedPreferences("seeky_ai",0).edit().putBoolean("configured_gemini",true).putBoolean("terms_gemini",true).apply();
        SummaryWork.clients=()->new SeekyAiClient((provider,key,payload,call)->{requests++;systems.add(payload.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"));return Summary513Test.reply("Resumo "+requests+" [T1].");});}
    @After public void close(){SummaryWork.clients=originalClients;SummaryWork.vaults=originalVaults;w.close();}
    String job(int allowance)throws Exception{StringBuilder text=new StringBuilder();for(int i=0;i<700;i++)text.append("Parágrafo ").append(i).append(": uma explicação para resumir com conceitos e fontes.\n\n");long material=w.book("Livro.txt",text.toString());return w.job("summary",new JSONObject().put("uri",StudyFileProvider.uri(w.app,material).toString()).put("name","Livro.txt").put("title","Livro").put("mime","text/plain").put("material",material).put("first",0).put("last",0).put("whole",true).put("prepared_part",0).put("requests",allowance));}
    @Test public void theWholeBookEndsWithAGeneralSummary()throws Exception{
        String id=job(60);w.run(id);long parts=w.items(id,"done");assertTrue(parts>1);assertEquals(parts+1,requests);
        String last=systems.get(systems.size()-1);assertTrue(last.contains("[R1]")&&last.contains("[R"+parts+"]")&&!last.contains("[T1]"));
        try(TaskStore db=new TaskStore(w.app)){JSONObject data=db.payload(id);assertTrue(data.optBoolean("overall_done"));assertEquals("Resumo "+requests+".",data.getString("overall"));}
        try(StudyStore notes=new StudyStore(w.app)){List<StudyStore.Row> general=notes.rows("notes","tags=?",new String[]{"Seeky resumo geral "+id},null,5);assertEquals(1,general.size());assertTrue(general.get(0).s("title").startsWith("Resumo geral · Livro"));assertTrue(general.get(0).s("body").contains("livro inteiro"));assertTrue(general.get(0).n("material")>0);}
    }
    @Test public void theGeneralSummaryWaitsForTheNextRunWhenTheAllowanceIsSpent()throws Exception{
        String id=job(1);try{w.run(id);fail();}catch(InterruptedIOException expected){}
        long total;try(TaskStore db=new TaskStore(w.app)){total=db.count(id,null);JSONObject data=db.payload(id);data.put("requests",(int)total-1);db.payload(id,data);db.state(id,"paused","");}
        try{w.run(id);fail();}catch(InterruptedIOException expected){}
        assertEquals(total,requests);assertEquals(total,w.items(id,"done"));
        try(TaskStore db=new TaskStore(w.app)){JSONObject data=db.payload(id);assertFalse(data.optBoolean("overall_done"));assertEquals(total,data.getJSONArray("reduce").length());assertTrue(db.job(id).s("message").contains("resumo geral"));}
        w.run(id);assertEquals(total+1,requests);
        try(TaskStore db=new TaskStore(w.app)){assertTrue(db.payload(id).optBoolean("overall_done"));}
    }
    @Test public void aStudyPlanBecomesASubjectWithDatedSessionsCardsAndANote()throws Exception{
        SeekyAiDeep.Plan plan=SeekyAiDeep.parse("TÍTULO: Evangelho de João\nOBJECTIVO: Conhecer os sinais.\nDIA 1 | Introdução | Ler João 1\nDIA 2 | Sinais | Ler João 2\nDIA 3 | Revisão | Rever\nCARTÕES\nPergunta: Quantos sinais?\nResposta: Sete.",6);
        long start=System.currentTimeMillis();long sid,again;
        try(StudyStore db=new StudyStore(w.app)){long course=db.save("courses",0,StudyStore.values("title","Bacharelato","description","","created",start));sid=StudyPlanAi.save(db,plan,start,3,45,course,"module");assertEquals(course,db.get("subjects",sid).n("course"));assertEquals("module",db.get("subjects",sid).s("kind"));
            assertEquals("Evangelho de João",db.get("subjects",sid).s("title"));
            List<StudyStore.Row> tasks=db.rows("tasks","subject=?",new String[]{""+sid},"due",10);assertEquals(3,tasks.size());assertTrue(tasks.get(0).s("title").startsWith("Sessão 1 · Introdução"));assertEquals(45,tasks.get(0).n("estimate"));assertTrue(tasks.get(1).n("due")>tasks.get(0).n("due"));
            assertEquals(1,db.count("cards","subject=?",new String[]{""+sid}));
            assertEquals(1,db.rows("notes","subject=? AND title LIKE 'Plano de estudo%'",new String[]{""+sid},null,5).size());
            SeekyAiDeep.Plan second=SeekyAiDeep.parse("TÍTULO: Evangelho de João\nDIA 1 | A | B",6);again=StudyPlanAi.save(db,second,start,3,30,0,"subject");assertEquals("subject",db.get("subjects",again).s("kind"));
            assertEquals("Evangelho de João (2)",db.get("subjects",again).s("title"));}
    }
}
