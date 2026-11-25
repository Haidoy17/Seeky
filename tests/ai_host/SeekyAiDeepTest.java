package mz.bam.seeky.android;
import java.util.*;import org.json.*;

/** 5.51: questions over several documents, a whole book summarised in stages, a study plan from a theme. */
public final class SeekyAiDeepTest {
 static int checks;static void ok(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
 public static void main(String[] args)throws Exception{
  String grace=repeat("Introdução geral ao livro sem assunto especial. ",80)+"\n\nA graça é o favor de Deus que não merecemos; pela graça somos salvos.\n\n"+repeat("Outro capítulo sobre história da igreja antiga. ",80);
  String just=repeat("Prefácio do autor e agradecimentos. ",60)+"\n\nA justificação é o acto de Deus que declara justo o pecador pela fé.\n\n"+repeat("Notas finais. ",50);
  String food=repeat("Receita de bolo com farinha, ovos e açúcar. ",90);
  List<SeekyAiDeep.Doc> docs=Arrays.asList(new SeekyAiDeep.Doc("Teologia da graça",grace),new SeekyAiDeep.Doc("Justificação.pdf",just),new SeekyAiDeep.Doc("Receitas",food));
  String block=SeekyAiDeep.block(docs,"O que é a graça e a justificação?",6000);
  ok(block.contains("pela graça somos salvos")&&block.contains("declara justo o pecador"),"the passages that answer are found in each document");
  ok(block.contains("[D1·T")&&block.contains("[D2·T")&&!block.contains("[D3·T"),"each excerpt is labelled with its document; an unrelated document sends nothing");
  ok(block.contains("[D3] Receitas — nenhum trecho relevante"),"the header says honestly which document had nothing relevant");
  ok(block.length()<6000+1200,"the excerpts stay within the budget");
  String general=SeekyAiDeep.block(docs,"O que dizem estes documentos?",9000);
  ok(general.contains("[D1·T")&&general.contains("[D2·T")&&general.contains("[D3·T")&&general.contains("even sample"),"a general question sends an even sample of every document and says so");
  ok(SeekyAiDeep.terms("Graças às Justificações").equals(Arrays.asList("graca","justif")),"words are folded without accents and cut to a stem");
  ok(SeekyAiDeep.query("e no segundo?",Arrays.asList(new SeekyAiCore.Message("user","Fala da graça"),new SeekyAiCore.Message("assistant","…"))).contains("graça"),"a follow-up question keeps the words of the previous one");
  ok(SeekyAiDeep.cited("Ver [D2·T7] e [D9·T1] e [D1·T3]",3).equals(new TreeSet<>(Arrays.asList(1,2))),"only cited documents that exist are counted");
  try{SeekyAiDeep.block(new ArrayList<>(),"x",1000);ok(false,"no documents");}catch(IllegalArgumentException e){ok(true,"a question without documents is refused");}

  final JSONObject[] sent=new JSONObject[1];
  SeekyAiClient client=new SeekyAiClient((provider,key,payload,call)->{sent[0]=payload;return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Segundo [D1·T3], a graça é favor imerecido.\"}]}}]}";});
  SeekyAiCore.Result r=client.askMaterials(SeekyAiCore.GEMINI,"AIzaTESTKEY0123456789",block,Collections.emptyList(),"O que é a graça?",false,new SeekyAiClient.Call());
  String system=sent[0].getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text");
  ok(r.text.contains("[D1·T3]")&&system.contains("pela graça somos salvos")&&system.contains("quoted evidence"),"the excerpts travel as quoted evidence and the answer comes back");
  ok(sent[0].getJSONObject("generationConfig").getInt("maxOutputTokens")==3072,"answers over several documents may be longer");
  try{SeekyAiCore.requestMaterials(SeekyAiCore.GEMINI,repeat("x",SeekyAiCore.MATERIALS+1),Collections.emptyList(),"q",false);ok(false,"too long");}catch(IllegalArgumentException e){ok(true,"a material block over the limit is refused");}

  String book=repeat("Capítulo com palavras importantes e ideias. ",900);
  final int[] parts={0},joins={0};final List<String> steps=new ArrayList<>();
  String summary=SeekyAiDeep.book("O livro",book,false,(b,q)->{parts[0]++;ok(q.contains("Resume este trecho"),"each excerpt is asked for a summary");return "• ideia "+parts[0]+" [T1]";},(b,q)->{joins[0]++;ok(b.contains("[R1]")&&!b.contains("[T1]")&&q.contains("resumo geral de «O livro»"),"the partial summaries are joined, in order, into a general summary");return "Visão geral do livro.";},(done,total,what)->steps.add(what));
  int expect=SeekyAiDeep.parts(book).size();
  ok(parts[0]==expect&&expect>=3&&joins[0]==1&&summary.equals("Visão geral do livro."),"the whole book is read in "+expect+" excerpts and joined with one more request");
  ok(steps.get(steps.size()-1).equals("A escrever o resumo geral"),"progress says what is happening");
  List<String> many=new ArrayList<>();for(int i=0;i<60;i++)many.add(repeat("y",3000));
  final int[] asked={0};List<String> cur=many;int rounds=0;while(true){boolean last=SeekyAiDeep.groups(cur,SeekyAiDeep.GROUP).size()==1;cur=SeekyAiDeep.step(cur,"Grande",false,(b,q)->{asked[0]++;ok(b.length()<=SeekyAiCore.MATERIALS,"every join fits one request");return repeat("z",5000);});rounds++;if(last)break;}
  ok(cur.size()==1&&rounds>=2&&asked[0]==SeekyAiDeep.cost(many),"a long book is joined in steps and the cost estimate matches ("+asked[0]+" requests)");
  ok(SeekyAiDeep.cost(Collections.singletonList("a"))==1&&SeekyAiDeep.cost(new ArrayList<>())==0,"cost of trivial lists");

  String q=SeekyAiDeep.planQuestion("Evangelho de João",2,3,30,0,false);
  ok(q.contains("6 sessões")&&q.contains("DIA n |")&&q.contains("Iniciante"),"the plan request asks for dated sessions and cards");
  try{SeekyAiDeep.planQuestion("  ",2,3,30,0,false);ok(false,"empty");}catch(IllegalArgumentException e){ok(true,"a plan without a theme is refused");}
  try{SeekyAiDeep.sessions(13,3);ok(false,"weeks");}catch(IllegalArgumentException e){ok(true,"more than 12 weeks is refused");}
  String answer="**TÍTULO:** Evangelho de João\nOBJECTIVO: Conhecer os sinais e discursos.\nDIA 1 | Introdução | Ler João 1.\nDia 2 | Os sinais | Listar os sete sinais.\n- DIA 3: Eu sou | Estudar as afirmações.\nDIA 99 | Fora | não conta\nCARTÕES\nPergunta: Quantos sinais?\nResposta: Sete.\nPergunta: Quem escreveu?\nResposta: João.";
  SeekyAiDeep.Plan plan=SeekyAiDeep.parse(answer,6);
  ok(plan.title.equals("Evangelho de João")&&plan.goal.startsWith("Conhecer"),"title and goal are read");
  ok(plan.sessions.size()==3&&plan.sessions.get(2).title.equals("Eu sou")&&plan.sessions.get(1).detail.equals("Listar os sete sinais."),"sessions are read, including other spellings; impossible days are dropped");
  ok(plan.cards.size()==2&&plan.cards.get(0).back.equals("Sete."),"the cards after CARTÕES are read");
  Calendar c=Calendar.getInstance();c.set(2026,10,23,9,0);long start=c.getTimeInMillis();long day=86400000L;
  long[] at=new long[4];for(int i=0;i<4;i++)at[i]=SeekyAiDeep.due(start,i+1,3);
  ok(Math.round((at[1]-at[0])/(double)day)==2&&Math.round((at[2]-at[0])/(double)day)==5&&Math.round((at[3]-at[0])/(double)day)==7,"three sessions a week are spread over the week");
  ok(plan.note(start,3).contains("Objectivo: Conhecer")&&plan.note(start,3).contains("— Os sinais: Listar"),"the plan becomes a readable note");
  System.out.println("SeekyAiDeepTest: "+checks+" checks");
 }
}
