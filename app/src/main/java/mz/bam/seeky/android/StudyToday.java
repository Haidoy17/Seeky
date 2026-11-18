package mz.bam.seeky.android;
import android.database.Cursor;import android.graphics.Typeface;import android.view.*;import android.widget.*;import java.text.DateFormat;import java.util.*;import org.json.*;
/** A bounded dashboard backed by aggregate queries, never by the current UI page. */
final class StudyToday {
 static final class Snapshot {long due,overdue,reviewed,focus;StudyStore.Row reading;List<StudyStore.Row> tasks=Collections.emptyList();boolean quiz,ready;}
 static long scalar(StudyStore db,String sql,String...args){try(Cursor c=db.getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
 static Snapshot load(StudyStore db,long course,long subject,long now){Snapshot s=new Snapshot();String scope=StudyStore.scope(course,subject);Calendar begin=Calendar.getInstance();begin.setTimeInMillis(now);begin.set(Calendar.HOUR_OF_DAY,0);begin.set(Calendar.MINUTE,0);begin.set(Calendar.SECOND,0);begin.set(Calendar.MILLISECOND,0);Calendar end=(Calendar)begin.clone();end.add(Calendar.DAY_OF_YEAR,1);String start=""+begin.getTimeInMillis(),finish=""+end.getTimeInMillis();
  s.due=db.count("cards",scope+" AND suspended=0 AND due<=?",new String[]{""+now});s.overdue=db.count("tasks",scope+" AND done=0 AND due>0 AND due<?",new String[]{start});
  s.focus=scalar(db,"SELECT COALESCE(SUM(seconds),0) FROM sessions WHERE "+scope+" AND type='Foco' AND started>=? AND started<?",start,finish);
  s.reviewed=scalar(db,"SELECT COUNT(DISTINCT card) FROM reviews WHERE reviewed>=? AND reviewed<? AND card IN (SELECT id FROM cards WHERE "+scope+")",start,finish);
  s.tasks=db.rows("tasks",scope+" AND done=0 AND due>0 AND due<?",new String[]{finish},"due,priority DESC,id",3);
  List<StudyStore.Row> last=db.rows("materials",scope+" AND progress>0 AND progress<100",null,"updated DESC,id DESC",1);if(!last.isEmpty())s.reading=last.get(0);
  StudyStore.Row quiz=db.get("practice",1);if(quiz!=null)try{long group=new JSONObject(quiz.s("payload")).optLong("group");s.quiz=subject>0?group==subject:course==0||db.count("subjects","id=? AND course=?",new String[]{""+group,""+course})>0;}catch(JSONException ignored){}
  s.ready=true;return s;
 }
 final StudyHub h;final MainActivity a;StudyToday(StudyHub h){this.h=h;a=h.a;}
 void draw(boolean compact){Snapshot s=h.today;StudyUi ui=new StudyUi(a);LinearLayout card=ui.card();TextView heading=a.title("O seu estudo de hoje",19);card.addView(heading);card.addView(a.label(DateFormat.getDateInstance(DateFormat.FULL,AppLanguage.english(a)?Locale.ENGLISH:new Locale("pt","MZ")).format(new Date()),11,a.muted));
  if(!s.ready){card.addView(a.label("A preparar o seu plano…",12,a.muted));h.content.addView(card);return;}
  ui.stats(card,s.due+" cartões por rever",s.reviewed+" cartões revistos hoje");int goal=Math.max(1,h.prefs.getInt("daily_goal",45));TextView focus=a.label((s.focus/60)+" / "+goal+" min de foco registado",12,a.text);card.addView(focus);ui.progress(card,(int)Math.min(100,s.focus*100/(goal*60L)));
  if(compact){ui.actions(card,"Plano de hoje",()->h.route("Visão geral"),null,null);h.content.addView(card);a.gap(h.content,14);return;}
  ui.actions(card,s.due>0?"Rever agora":"Preparar revisão",h::review,h.timerActive()?(h.prefs.getBoolean("timer_paused",false)?"Continuar foco":"Pausar foco"):"Iniciar foco",()->{if(h.timerActive())h.pauseTimer();else h.focusForm("",h.subject,25,"Foco");});h.content.addView(card);a.gap(h.content,12);
  if(StudyReview.pendingFor(h.prefs,h.course,h.subject)){a.actionCard(h.content,"cards","Continuar revisão interrompida","Retome a fila guardada; os cartões já avaliados são mantidos.",()->new StudyReview(h).resume());}
  if(s.quiz)a.actionCard(h.content,"quiz","Continuar teste interrompido","A sua resposta e o ponto onde parou estão guardados.",h::resumeQuiz);
  if(s.reading!=null){StudyStore.Row reading=s.reading;a.actionCard(h.content,"book","Continuar a estudar",reading.s("title")+" · "+reading.n("progress")+"%",()->h.openMaterial(reading,true,-1));}
  a.section(h.content,"Prioridades",s.overdue>0?s.overdue+" tarefas com prazo ultrapassado":"Tarefas até ao fim de hoje");
  if(s.tasks.isEmpty()){LinearLayout empty=ui.card();empty.addView(a.label("Sem tarefas pendentes para hoje.",13,a.text));ui.actions(empty,"Planear tarefa",()->h.taskForm(null),"Ver agenda",()->h.route("Agenda"));h.content.addView(empty);}else for(StudyStore.Row task:s.tasks){LinearLayout row=ui.card();TextView title=ui.raw(task.s("title"),15,a.text);title.setTypeface(null,Typeface.BOLD);row.addView(title);row.addView(a.label(h.date(task.n("due"))+" · "+task.n("estimate")+" min",11,a.muted));ui.actions(row,"Concluir",()->h.mutate(()->h.db.save("tasks",task.id(),StudyStore.values("done",1,"updated",System.currentTimeMillis()))),"Estudar",()->h.focusForm(task.s("title"),task.n("subject"),(int)task.n("estimate"),"Foco"));h.content.addView(row);a.gap(h.content,10);}
  if(!s.tasks.isEmpty())h.content.addView(a.button("Ver toda a agenda",()->h.route("Agenda")));a.gap(h.content,12);h.timerCard();a.section(h.content,"Praticar e organizar",null);a.actionCard(h.content,"edit","Cadernos e resumos","Registe perguntas-chave e explique com as suas palavras.",()->h.route("Cadernos"));a.actionCard(h.content,"quiz","Treinar com perguntas","Teste a compreensão com o seu banco de perguntas.",()->h.route("Perguntas"));a.actionCard(h.content,"clock","Ver o seu progresso","Consulte foco registado, revisões e resultados.",()->h.route("Progresso"));
 }
}
