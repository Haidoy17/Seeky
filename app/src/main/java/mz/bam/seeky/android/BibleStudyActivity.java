package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.*;
import android.text.style.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.json.JSONObject;

/** Bible study: today's reading and the week, the reading plan, memory cards, saved passages and the game. */
public final class BibleStudyActivity extends Activity {
    static final int SPEECH=41;
    Screens s;String mode="home";
    // Review session
    List<BibleStudyStore.Card> queue=new ArrayList<>();int index,reviewed,good;boolean checked;String cardMode;
    EditText answer;LinearLayout result;

    static void open(Context c){Intent i=new Intent(c,BibleStudyActivity.class);if(!(c instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}

    @Override protected void onCreate(Bundle saved){super.onCreate(saved);s=new Screens(this);cardMode=getSharedPreferences(BibleStudyStore.PREFS,0).getString("card_mode","text");home();}
    @Override protected void onResume(){super.onResume();if(mode.equals("home"))home();}
    @Override public void onBackPressed(){if(mode.equals("home"))super.onBackPressed();else home();}

    /** Everything the home screen shows, read off the main thread. */
    static final class Day {
        String plan;int daily,progress,total,streak,due,cards,memorised,saved,weekChapters,weekCards,weekGames;boolean bible;
        List<String> today=new ArrayList<>();List<String[]> next=new ArrayList<>();List<String> week=new ArrayList<>();int[] perDay=new int[7];int[] cardsDay=new int[7];
    }
    static Day day(Context c,long now){
        Day d=new Day();d.plan=BibleStudyStore.plan(c);d.daily=BibleStudyCore.daily(d.plan);d.total=BibleStudyCore.chapters(d.plan).size();
        try(BibleStudyStore st=new BibleStudyStore(c)){
            Set<String> read=st.readChapters();d.progress=BibleStudyCore.progress(d.plan,read);
            d.today=new ArrayList<>(st.readOn(BibleStudyCore.day(now)));
            d.next=BibleStudyCore.next(d.plan,read,Math.max(1,d.daily-d.today.size()));
            d.week=BibleStudyCore.week(now);
            for(int i=0;i<7;i++){d.perDay[i]=st.amount(d.week.get(i),"chapter");d.cardsDay[i]=st.amount(d.week.get(i),"card");d.weekChapters+=d.perDay[i];d.weekCards+=d.cardsDay[i];d.weekGames+=st.amount(d.week.get(i),"game");}
            d.streak=BibleStudyCore.streak(st.activeDays(),now);
            List<BibleStudyStore.Card> cards=st.cards();d.cards=cards.size();for(BibleStudyStore.Card k:cards){if(k.due<=now)d.due++;if(k.memorised())d.memorised++;}
        }
        d.saved=BibleLinks.savedItems(c).size();
        try(PlusStore db=new PlusStore(c)){d.bible=!db.rows("bible_sources",null,null,null,1).isEmpty();}
        return d;
    }

    void home(){
        mode="home";
        LinearLayout content=s.page();
        content.addView(s.header("Estudo bíblico","Leitura diária, semana, decorar e jogar",this::finish));
        TextView loading=s.text("A preparar…",14,s.p.muted,false);content.addView(loading);
        PlusUi.run(this,()->{Day d=day(this,System.currentTimeMillis());PlusUi.post(this,()->{if(!mode.equals("home"))return;content.removeView(loading);render(content,d);});});
    }
    void render(LinearLayout content,Day d){
        // Today
        LinearLayout hero=s.column();hero.setPadding(s.dp(18),s.dp(16),s.dp(18),s.dp(18));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{s.p.heroStart,s.p.heroEnd});g.setCornerRadius(s.dp(22));hero.setBackground(g);
        int ink=AppPalette.contrast(0xffffffff,s.p.heroStart)>=3?0xffffffff:0xff141820;
        TextView streak=s.text(d.streak>0?"🔥 "+d.streak+" "+AppLanguage.text(this,d.streak==1?"dia seguido":"dias seguidos"):AppLanguage.text(this,"Comece hoje a sua sequência"),22,ink,true);hero.addView(streak);
        TextView today=s.text(AppLanguage.text(this,"Hoje")+": "+d.today.size()+" "+AppLanguage.text(this,"de")+" "+d.daily+" "+AppLanguage.text(this,d.daily==1?"capítulo":"capítulos")+" · "+AppLanguage.text(this,BibleStudyCore.plan(d.plan)[1]),14,ink,false);today.setPadding(0,s.dp(4),0,s.dp(10));hero.addView(today);
        hero.addView(bar(Math.min(1f,d.today.size()/(float)Math.max(1,d.daily)),ink,(ink&0xffffff)|0x40000000),new LinearLayout.LayoutParams(-1,s.dp(10)));
        content.addView(hero,new LinearLayout.LayoutParams(-1,-2));

        content.addView(s.section("Leitura de hoje"));
        LinearLayout reading=s.card();
        for(String done:d.today){String[] bc=split(done);reading.addView(chapterRow(bc[0],Integer.parseInt(bc[1]),true,d.bible));}
        for(String[] n:d.next)reading.addView(chapterRow(n[0],Integer.parseInt(n[1]),false,d.bible));
        if(d.next.isEmpty()&&d.today.size()>=d.daily){TextView done=s.text("✓ "+AppLanguage.text(this,"Leitura de hoje concluída. Pode adiantar o plano."),14,s.p.accent,true);done.setPadding(0,s.dp(6),0,0);reading.addView(done);}
        if(!d.bible){TextView note=s.text("Sem Bíblia importada: leia na sua Bíblia (PDF ou papel) e marque aqui o capítulo. Para ler dentro do Seeky, descarregue uma Bíblia.",13,s.p.muted,false);note.setPadding(0,s.dp(10),0,s.dp(6));reading.addView(note);reading.addView(s.button("Descarregar uma Bíblia",false,()->BibleCatalog.show(this)),s.gap(4));}
        LinearLayout planRow=s.row();
        s.addChip(planRow,s.chip("calendar",AppLanguage.text(this,"Plano")+": "+AppLanguage.text(this,BibleStudyCore.plan(d.plan)[1]),false,this::choosePlan));
        reading.addView(s.scroller(planRow),s.gap(10));
        content.addView(reading,new LinearLayout.LayoutParams(-1,-2));

        content.addView(s.section("Esta semana"));
        LinearLayout week=s.card();week.addView(weekBars(d));
        TextView summary=s.text(d.weekChapters+" "+AppLanguage.text(this,d.weekChapters==1?"capítulo":"capítulos")+" · "+d.weekCards+" "+AppLanguage.text(this,d.weekCards==1?"cartão revisto":"cartões revistos")+" · "+d.weekGames+" "+AppLanguage.text(this,d.weekGames==1?"jogo":"jogos"),14,s.p.ink,true);summary.setPadding(0,s.dp(10),0,s.dp(4));week.addView(summary);
        int goal=d.daily*7;TextView meta=s.text(AppLanguage.text(this,"Meta da semana")+": "+Math.min(d.weekChapters,goal)+" / "+goal+" "+AppLanguage.text(this,"capítulos"),13,s.p.muted,false);week.addView(meta);
        week.addView(bar(Math.min(1f,d.weekChapters/(float)Math.max(1,goal)),s.p.accent,s.p.tint),barParams(8));
        TextView plan=s.text(AppLanguage.text(this,"Plano")+": "+d.progress+" "+AppLanguage.text(this,"de")+" "+d.total+" "+AppLanguage.text(this,"capítulos")+" ("+Math.round(100f*d.progress/Math.max(1,d.total))+"%)",13,s.p.muted,false);plan.setPadding(0,s.dp(12),0,s.dp(6));week.addView(plan);
        week.addView(bar(d.progress/(float)Math.max(1,d.total),s.p.accent,s.p.tint),barParams(0));
        content.addView(week,new LinearLayout.LayoutParams(-1,-2));

        content.addView(s.section("Decorar"));
        LinearLayout memo=s.card();
        memo.addView(s.text(d.cards==0?"Ainda não tem cartões. Na leitura rápida de uma passagem toque em Decorar, ou adicione um versículo aqui.":d.due+" "+AppLanguage.text(this,"para rever hoje")+" · "+d.memorised+" "+AppLanguage.text(this,d.memorised==1?"decorado":"decorados")+" · "+d.cards+" "+AppLanguage.text(this,d.cards==1?"cartão":"cartões"),15,s.p.ink,d.cards>0));
        if(d.cards>0)memo.addView(s.button(d.due>0?AppLanguage.text(this,"Rever agora")+" ("+d.due+")":AppLanguage.text(this,"Treinar mesmo assim"),true,()->review(null)),s.gap(12));
        LinearLayout memoRow=s.row();
        s.addChip(memoRow,s.chip("add","Adicionar versículo",false,this::addVerse));
        if(d.cards>0)s.addChip(memoRow,s.chip("cards","Ver cartões",false,this::cardList));
        memo.addView(s.scroller(memoRow),s.gap(10));
        content.addView(memo,new LinearLayout.LayoutParams(-1,-2));

        content.addView(s.section("Mais"));
        content.addView(s.entry("bookmark","Passagens guardadas",d.saved==0?"Guarde passagens na leitura rápida":d.saved+" "+AppLanguage.text(this,d.saved==1?"passagem":"passagens"),this::savedList),s.gap(0));
        content.addView(s.entry("star","Jogo da Bíblia","Passagens, livros e histórias em três níveis",()->BibleGameActivity.open(this)),s.gap(8));
        content.addView(s.entry("edit","As minhas marcações","Destaques, sublinhados e notas dos livros",()->HighlightsActivity.open(this,null)),s.gap(8));
        content.addView(s.entry("search","Consultar passagem","Isaías 3.1-7, 12 · Jo 3:16 · Salmo 23",()->BibleLinks.query(this)),s.gap(8));
    }
    static String[] split(String bookChapter){int at=bookChapter.lastIndexOf(' ');return new String[]{bookChapter.substring(0,at),bookChapter.substring(at+1)};}
    LinearLayout.LayoutParams barParams(int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,s.dp(8));lp.topMargin=s.dp(top);return lp;}
    View bar(float fraction,int fill,int track){
        FrameLayout f=new FrameLayout(this);f.setBackground(s.round(track,6));
        View v=new View(this);v.setBackground(s.round(fill,6));f.addView(v,new FrameLayout.LayoutParams(0,-1));
        f.addOnLayoutChangeListener((x,l,t,r,b,ol,ot,or,ob)->{int w=Math.round((r-l)*Math.max(0,Math.min(1,fraction)));if(v.getLayoutParams().width!=w){v.getLayoutParams().width=Math.max(fraction>0?s.dp(10):0,w);v.post(v::requestLayout);}});
        return f;
    }
    View chapterRow(String book,int chapter,boolean done,boolean bible){
        LinearLayout r=s.row();r.setPadding(0,s.dp(6),0,s.dp(6));
        TextView check=s.text(done?"✓":"",18,done?s.p.onAccent:s.p.muted,true);check.setGravity(Gravity.CENTER);
        GradientDrawable box=new GradientDrawable();box.setCornerRadius(s.dp(10));box.setColor(done?s.p.accent:0);box.setStroke(s.dp(2),done?s.p.accent:s.p.line);check.setBackground(box);
        check.setContentDescription(AppLanguage.text(this,done?"Desmarcar":"Marcar como lido")+" "+BibleGame.display(book)+" "+chapter);
        check.setOnClickListener(v->PlusUi.run(this,()->{try(BibleStudyStore st=new BibleStudyStore(this)){if(done)st.unread(book,chapter,System.currentTimeMillis());else st.read(book,chapter,System.currentTimeMillis());}PlusUi.post(this,this::home);}));
        r.addView(check,new LinearLayout.LayoutParams(s.dp(34),s.dp(34)));
        TextView name=s.text(BibleGame.display(book)+" "+chapter,17,s.p.ink,true);name.setPadding(s.dp(12),0,0,0);if(done)name.setPaintFlags(name.getPaintFlags());
        r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        if(bible){LinearLayout read=s.chip("book","Ler",false,()->BibleLinks.readChapter(this,book,chapter,this::home));r.addView(read,new LinearLayout.LayoutParams(-2,s.dp(38)));}
        return r;
    }
    View weekBars(Day d){
        LinearLayout row=s.row();row.setGravity(Gravity.BOTTOM);
        String[] letters={"S","T","Q","Q","S","S","D"};String today=BibleStudyCore.day(System.currentTimeMillis());
        int max=Math.max(d.daily,1);for(int n:d.perDay)max=Math.max(max,n);
        for(int i=0;i<7;i++){
            LinearLayout col=s.column();col.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);
            boolean isToday=d.week.get(i).equals(today);
            TextView n=s.text(d.perDay[i]>0?""+d.perDay[i]:"",12,s.p.muted,true);n.setGravity(Gravity.CENTER);col.addView(n);
            View b=new View(this);b.setBackground(s.round(d.perDay[i]>0||isToday?s.p.accent:s.p.tint,6));if(d.perDay[i]==0&&isToday)b.setAlpha(.35f);
            int h=Math.max(s.dp(6),Math.round(s.dp(76)*d.perDay[i]/(float)max));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(s.dp(22),h);bp.topMargin=s.dp(4);col.addView(b,bp);
            TextView dot=s.text(d.cardsDay[i]>0?"•":" ",14,s.p.accent,true);dot.setGravity(Gravity.CENTER);col.addView(dot);
            TextView letter=s.text(letters[i],13,isToday?s.p.accent:s.p.muted,true);letter.setGravity(Gravity.CENTER);col.addView(letter);
            col.setContentDescription(d.week.get(i)+": "+d.perDay[i]+" "+AppLanguage.text(this,"capítulos"));
            row.addView(col,new LinearLayout.LayoutParams(0,s.dp(126),1));
        }
        return row;
    }
    void choosePlan(){
        String[] names=new String[BibleStudyCore.PLANS.length];int selected=0;String current=BibleStudyStore.plan(this);
        for(int i=0;i<names.length;i++){String[] p=BibleStudyCore.PLANS[i];names[i]=AppLanguage.text(this,p[1])+" — "+BibleStudyCore.daily(p[0])+" "+AppLanguage.text(this,"por dia");if(p[0].equals(current))selected=i;}
        new UiDialog.Builder(this).setTitle("Plano de leitura").setMessage("Os capítulos já lidos contam em qualquer plano.").setSingleChoiceItems(names,selected,(dlg,n)->{BibleStudyStore.plan(this,BibleStudyCore.PLANS[n][0]);dlg.dismiss();home();}).setNegativeButton("Fechar",null).show();
    }

    EditText input(String hint,int lines){
        EditText e=new EditText(this);e.setHint(AppLanguage.text(this,hint));e.setTextSize(16);e.setTextColor(s.p.ink);e.setHintTextColor(s.p.muted);e.setMinLines(lines);e.setGravity(Gravity.TOP|Gravity.START);
        e.setBackground(s.round(s.p.surface,16));e.setPadding(s.dp(14),s.dp(12),s.dp(14),s.dp(12));if(lines<=1)e.setSingleLine(true);return e;
    }
    void addVerse(){
        LinearLayout box=s.column();
        EditText reference=PlusUi.input(this,"Ex.: João 3:16 ou Salmo 23:1-3","",120);box.addView(reference);
        EditText text=PlusUi.input(this,"Texto (só se não tiver Bíblia importada)","",1500);text.setMinLines(3);box.addView(text,s.gap(10));
        UiDialog dialog=new UiDialog.Builder(this).setTitle("Adicionar versículo para decorar").setView(box).setPositiveButton("Adicionar",null).setNegativeButton("Cancelar",null).create();dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            BibleReferences.Reference ref=BibleReferences.lookup(reference.getText().toString());
            if(ref==null||!ref.explicit){reference.setError(AppLanguage.text(this,"Indique o livro, o capítulo e o versículo."));return;}
            String typed=text.getText().toString().trim();
            PlusUi.run(this,()->{BibleStudyStore.Verses bible=new BibleStudyStore.Verses(this);String found=typed.isEmpty()?bible.text(ref):typed;String version=typed.isEmpty()?bible.version():"";
                boolean added=false;if(!found.isEmpty())try(BibleStudyStore st=new BibleStudyStore(this)){added=st.addCard(ref,found,version,System.currentTimeMillis());}
                final boolean ok=added,empty=found.isEmpty();
                PlusUi.post(this,()->{if(empty){text.setError(AppLanguage.text(this,"Esta passagem não está na Bíblia importada. Escreva o texto."));return;}dialog.dismiss();PlusUi.toast(this,ok?"Cartão criado: "+ref.title:"Este versículo já está nos cartões.");home();});});
        });
    }
    void cardList(){
        mode="cards";LinearLayout content=s.page();
        content.addView(s.header("Cartões para decorar","Toque num cartão para rever, ler ou eliminar",this::home));
        PlusUi.run(this,()->{List<BibleStudyStore.Card> cards;try(BibleStudyStore st=new BibleStudyStore(this)){cards=st.cards();}PlusUi.post(this,()->{
            if(!mode.equals("cards"))return;long now=System.currentTimeMillis();
            for(BibleStudyStore.Card k:cards){
                long days=Math.max(0,(k.due-now+86399999L)/86400000L);
                String status=k.memorised()?"✓ "+AppLanguage.text(this,"Decorado"):k.due<=now?AppLanguage.text(this,"Para rever hoje"):AppLanguage.text(this,"Próxima revisão em")+" "+days+" "+AppLanguage.text(this,days==1?"dia":"dias");
                content.addView(s.entry(k.memorised()?"check":"cards",k.title,status+" · "+BibleGame.clip(k.text,90),()->{
                    UiPanel panel=new UiPanel(this,k.title,BibleGame.clip(k.text,160),"cards");
                    panel.action("cards","Rever este cartão",null,false,()->review(Collections.singletonList(k)));
                    panel.action("book","Ler a passagem",k.version,false,()->BibleLinks.open(this,BibleReferences.lookup(k.title)));
                    panel.danger("Eliminar cartão",()->PlusUi.run(this,()->{try(BibleStudyStore st=new BibleStudyStore(this)){st.removeCard(k.id);}PlusUi.post(this,this::cardList);}));
                    panel.show();
                }),s.gap(8));
            }
        });});
    }
    void savedList(){
        mode="saved";LinearLayout content=s.page();
        content.addView(s.header("Passagens guardadas","Da leitura rápida: ler, decorar ou remover",this::home));
        List<JSONObject> items=BibleLinks.savedItems(this);
        if(items.isEmpty())content.addView(s.text("Ainda não guardou passagens. Abra uma passagem e toque em Guardar.",15,s.p.muted,false));
        for(JSONObject o:items){
            String title=o.optString("title"),version=o.optString("version"),text=o.optString("text");
            content.addView(s.entry("bookmark",title,(version.isEmpty()?"":version+" · ")+BibleGame.clip(text.replace('\n',' '),120),()->{
                UiPanel panel=new UiPanel(this,title,version,"bookmark");
                panel.action("book","Ler a passagem",null,false,()->BibleLinks.open(this,BibleReferences.lookup(title)));
                panel.action("cards","Decorar",null,false,()->PlusUi.run(this,()->{BibleReferences.Reference ref=BibleReferences.lookup(title);BibleStudyStore.Verses bible=new BibleStudyStore.Verses(this);String t=bible.text(ref);if(t.isEmpty())t=text.replaceAll("(?m)^\\d+(:\\d+)?\\s+","").replaceFirst("^[^\\n]*\\n","").replace('\n',' ').trim();boolean added=false;if(ref!=null&&!t.isEmpty())try(BibleStudyStore st=new BibleStudyStore(this)){added=st.addCard(ref,t,version,System.currentTimeMillis());}final boolean ok=added;PlusUi.post(this,()->PlusUi.toast(this,ok?"Adicionada aos cartões para decorar.":"Já está nos cartões ou sem texto."));}));
                panel.danger("Remover das guardadas",()->{BibleLinks.unsave(this,title);savedList();});
                panel.show();
            }),s.gap(8));
        }
    }

    // Memory review -----------------------------------------------------------------------------
    void review(List<BibleStudyStore.Card> only){
        PlusUi.run(this,()->{List<BibleStudyStore.Card> cards;try(BibleStudyStore st=new BibleStudyStore(this)){cards=only!=null?new ArrayList<>(only):st.due(System.currentTimeMillis());if(cards.isEmpty())cards=st.cards();}
            Collections.shuffle(cards);final List<BibleStudyStore.Card> list=cards.subList(0,Math.min(20,cards.size()));
            PlusUi.post(this,()->{queue=new ArrayList<>(list);index=0;reviewed=0;good=0;if(queue.isEmpty()){PlusUi.toast(this,"Não há cartões.");return;}showCard();});});
    }
    boolean bookMode(BibleStudyStore.Card k){return cardMode.equals("book")||cardMode.equals("mix")&&(k.id+index)%2==0;}
    void showCard(){
        mode="review";checked=false;
        LinearLayout content=s.page();
        if(index>=queue.size()){finishReview(content);return;}
        BibleStudyStore.Card k=queue.get(index);boolean book=bookMode(k);
        content.addView(s.header("Decorar",AppLanguage.text(this,"Cartão")+" "+(index+1)+" "+AppLanguage.text(this,"de")+" "+queue.size(),this::home));
        LinearLayout modes=s.row();
        String[][] all={{"text","Escrever o versículo"},{"book","Dizer o livro"},{"mix","Misto"}};
        for(String[] m:all)s.addChip(modes,s.chip(null,m[1],cardMode.equals(m[0]),()->{cardMode=m[0];getSharedPreferences(BibleStudyStore.PREFS,0).edit().putString("card_mode",m[0]).apply();showCard();}));
        content.addView(s.scroller(modes),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout card=s.card();card.setPadding(s.dp(18),s.dp(20),s.dp(18),s.dp(20));
        if(book){
            TextView q=s.text("Em que livro está?",14,s.p.muted,true);card.addView(q);
            TextView body=s.text("«"+k.text+"»",19,s.p.ink,false);body.setTypeface(Typeface.SERIF);body.setPadding(0,s.dp(8),0,0);card.addView(body);
        }else{
            TextView ref=s.text(k.title,28,s.p.ink,true);ref.setTypeface(Screens.serif());ref.setGravity(Gravity.CENTER);card.addView(ref);
            TextView hint=s.text("Escreva ou diga o versículo de memória",14,s.p.muted,false);hint.setGravity(Gravity.CENTER);hint.setPadding(0,s.dp(6),0,0);card.addView(hint);
            if(!k.version.isEmpty()){TextView v=s.text(k.version,12,s.p.muted,false);v.setGravity(Gravity.CENTER);card.addView(v);}
        }
        content.addView(card,s.gap(12));
        answer=input(book?"Livro (ex.: João)":"O versículo…",book?1:4);content.addView(answer,s.gap(12));
        LinearLayout tools=s.row();
        s.addChip(tools,s.chip("mic","Dizer",false,this::listen));
        if(!book)s.addChip(tools,s.chip("info","Pista",false,()->{TextView h=s.text(BibleStudyCore.initials(k.text),15,s.p.ink,false);h.setPadding(s.dp(12),s.dp(10),s.dp(12),s.dp(10));h.setBackground(s.round(s.p.tint,12));content.addView(h,content.indexOfChild(answer));}));
        s.addChip(tools,s.chip("eye","Mostrar resposta",false,()->check(k,book,true)));
        content.addView(s.scroller(tools),s.gap(10));
        content.addView(s.button("Verificar",true,()->check(k,book,false)),s.gap(12));
        result=s.column();content.addView(result,s.gap(12));
    }
    void check(BibleStudyStore.Card k,boolean book,boolean reveal){
        if(checked)return;checked=true;result.removeAllViews();
        String given=answer.getText().toString();int suggested;
        LinearLayout r=s.card();
        if(book){
            String named=BibleReferences.bookNamed(given);boolean right=!reveal&&named.equals(k.book);
            r.addView(s.text(right?"✓ "+AppLanguage.text(this,"Certo!"):reveal?AppLanguage.text(this,"Resposta"):"✗ "+AppLanguage.text(this,"Não é")+(named.isEmpty()?"":" "+BibleGame.display(named)),20,right?0xff2e9d57:reveal?s.p.ink:0xffd9534f,true));
            TextView ref=s.text(k.title,22,s.p.ink,true);ref.setTypeface(Screens.serif());ref.setPadding(0,s.dp(6),0,0);r.addView(ref);
            suggested=right?2:0;
        }else{
            int score=reveal?0:BibleStudyCore.similarity(k.text,given);suggested=reveal?0:BibleStudyCore.grade(score);
            if(!reveal){r.addView(s.text(AppLanguage.text(this,"Lembrou")+" "+score+"% "+AppLanguage.text(this,"das palavras"),20,score>=70?0xff2e9d57:score>=40?0xffd08a1e:0xffd9534f,true));r.addView(bar(score/100f,score>=70?0xff2e9d57:score>=40?0xffd08a1e:0xffd9534f,s.p.tint),barParams(8));}
            SpannableStringBuilder diff=new SpannableStringBuilder();boolean[] hit=BibleStudyCore.recalled(k.text,given);int w=0;
            for(String word:k.text.trim().split("\\s+")){if(word.isEmpty())continue;int start=diff.length();diff.append(word).append(' ');boolean counted=!BibleStudyCore.words(word).isEmpty();
                if(!reveal&&counted&&w<hit.length&&!hit[w]){diff.setSpan(new ForegroundColorSpan(0xffd9534f),start,start+word.length(),0);diff.setSpan(new StyleSpan(Typeface.BOLD),start,start+word.length(),0);diff.setSpan(new UnderlineSpan(),start,start+word.length(),0);}
                if(counted)w+=BibleStudyCore.words(word).size();}
            TextView text=new TextView(this);text.setText(diff);text.setTextSize(18);text.setTextColor(s.p.ink);text.setTypeface(Typeface.SERIF);text.setLineSpacing(s.dp(3),1.1f);text.setPadding(0,s.dp(10),0,0);text.setTextIsSelectable(true);
            r.addView(text);
            if(!reveal){TextView legend=s.text("Palavras a vermelho: as que faltaram.",12,s.p.muted,false);legend.setPadding(0,s.dp(6),0,0);r.addView(legend);}
        }
        TextView ask=s.text("Como correu?",14,s.p.muted,true);ask.setPadding(0,s.dp(14),0,s.dp(8));r.addView(ask);
        LinearLayout grades=s.row();String[] names={"Errei","Difícil","Bom","Fácil"};
        for(int gr=0;gr<4;gr++){
            final int grade=gr;long[] next=BibleStudyCore.schedule(k.interval,k.ease,gr,0);
            TextView b=s.button(AppLanguage.text(this,names[gr])+"\n"+AppLanguage.text(this,BibleStudyCore.wait((int)next[0])),gr==suggested,()->grade(k,grade));
            b.setTextSize(13);b.setMinHeight(s.dp(56));b.setPadding(s.dp(4),s.dp(6),s.dp(4),s.dp(6));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=gr<3?s.dp(6):0;grades.addView(b,lp);
        }
        r.addView(grades);
        result.addView(r,new LinearLayout.LayoutParams(-1,-2));
        ((ScrollView)result.getParent().getParent()).post(()->((ScrollView)result.getParent().getParent()).smoothScrollTo(0,result.getTop()));
    }
    void grade(BibleStudyStore.Card k,int grade){
        PlusUi.run(this,()->{try(BibleStudyStore st=new BibleStudyStore(this)){st.review(k,grade,System.currentTimeMillis());}PlusUi.post(this,()->{reviewed++;if(grade>=2)good++;if(grade==0&&queue.size()<40)queue.add(k);index++;showCard();});});
    }
    void finishReview(LinearLayout content){
        content.addView(s.header("Decorar","Sessão concluída",this::home));
        LinearLayout card=s.card();card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(s.dp(18),s.dp(26),s.dp(18),s.dp(26));
        TextView big=s.text("🎉",48,s.p.ink,false);big.setGravity(Gravity.CENTER);card.addView(big);
        TextView t=s.text(reviewed+" "+AppLanguage.text(this,reviewed==1?"cartão revisto":"cartões revistos")+" · "+good+" "+AppLanguage.text(this,"bem lembrados"),18,s.p.ink,true);t.setGravity(Gravity.CENTER);card.addView(t);
        TextView n=s.text("Os cartões voltam quando for altura de os rever. Aos 21 dias de intervalo ficam decorados.",14,s.p.muted,false);n.setGravity(Gravity.CENTER);n.setPadding(0,s.dp(8),0,0);card.addView(n);
        content.addView(card,s.gap(8));
        content.addView(s.button("Voltar ao estudo",true,this::home),s.gap(14));
    }
    void listen(){
        try{startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag()).putExtra(RecognizerIntent.EXTRA_PROMPT,AppLanguage.text(this,"Diga a resposta")),SPEECH);}
        catch(ActivityNotFoundException e){PlusUi.toast(this,"O reconhecimento de voz não está disponível neste telefone.");}
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request!=SPEECH||code!=RESULT_OK||data==null||answer==null)return;
        ArrayList<String> heard=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(heard==null||heard.isEmpty())return;
        String best=heard.get(0);for(String h:heard)if(!BibleReferences.bookNamed(h).isEmpty()){best=h;break;}
        String old=answer.getText().toString().trim();answer.setText(old.isEmpty()||answer.getMinLines()<=1?best:old+" "+best);answer.setSelection(answer.length());
    }
}
