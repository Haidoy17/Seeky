package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

/** Jogo da Bíblia: ten timed questions per round, three levels, helps, spoken answers and a records table. */
public final class BibleGameActivity extends Activity {
    static final int SPEECH=42;
    static final int[] LEVEL_COLORS={0xff2e9d57,0xffd08a1e,0xffd9534f,0xff7b3fbf};
    static final String[] LEVEL_TEXT={
        "Passagens famosas, histórias conhecidas e os dois Testamentos",
        "Qualquer versículo: livro e capítulo, resposta escrita ou falada, a passagem certa de cada referência, ordem e capítulos",
        "Completar passagens, quem disse, ordem dos acontecimentos e perguntas que pedem raciocínio, com explicação",
        "Para meditar: tipologia, citações do Antigo no Novo Testamento, doutrina, escrever a palavra que falta e o último de cada época"};
    Screens s;String state="menu";
    BibleGame game;List<BibleGame.Question> questions=new ArrayList<>();
    int level,index,score,correct,streak,bestStreak,lastPoints;long started,deadline,remaining;boolean answered,fiftyUsed,paused,bible;
    final List<String[]> log=new ArrayList<>();
    // Play screen views
    LinearLayout optionsBox,feedback,dots;View timerFill;TextView timerText,scoreText;EditText freeAnswer;final List<TextView> optionViews=new ArrayList<>();TextView fiftyChip;
    final Runnable tick=this::tick;

    static void open(Context c){Intent i=new Intent(c,BibleGameActivity.class);if(!(c instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}
    @Override protected void onCreate(Bundle saved){super.onCreate(saved);s=new Screens(this);menu();}
    @Override public void onBackPressed(){if(state.equals("play"))confirmQuit();else if(!state.equals("menu"))menu();else super.onBackPressed();}
    @Override protected void onPause(){super.onPause();if(state.equals("play")&&!answered&&!paused){paused=true;remaining=Math.max(0,deadline-SystemClock.uptimeMillis());PlusUi.MAIN.removeCallbacks(tick);}}
    @Override protected void onResume(){super.onResume();if(paused){paused=false;if(state.equals("play")&&!answered){deadline=SystemClock.uptimeMillis()+remaining;PlusUi.MAIN.post(tick);}}}
    @Override protected void onDestroy(){PlusUi.MAIN.removeCallbacks(tick);super.onDestroy();}

    // Menu ----------------------------------------------------------------------------------------
    void menu(){
        state="menu";PlusUi.MAIN.removeCallbacks(tick);
        LinearLayout content=s.page();
        content.addView(s.header("Jogo da Bíblia","10 perguntas · 4 níveis · 60 s por pergunta · recordes",this::finish));
        LinearLayout hero=s.column();hero.setPadding(s.dp(20),s.dp(20),s.dp(20),s.dp(20));
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{s.p.heroStart,s.p.heroEnd});g.setCornerRadius(s.dp(24));hero.setBackground(g);
        int ink=AppPalette.contrast(0xffffffff,s.p.heroStart)>=3?0xffffffff:0xff141820;
        TextView t=s.text("Quanto conhece a Bíblia?",24,ink,true);t.setTypeface(Screens.serif());hero.addView(t);
        TextView d=s.text("Descubra o livro de cada passagem, a passagem de cada referência, histórias, a ordem dos livros e muito mais. Responda depressa: o tempo vale pontos.",14,ink,false);d.setPadding(0,s.dp(6),0,0);hero.addView(d);
        content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
        content.addView(s.section("Escolha o nível"));
        LinearLayout levels=s.column();content.addView(levels);
        LinearLayout note=s.column();content.addView(note);
        LinearLayout more=s.row();
        s.addChip(more,s.chip("star","Recordes",false,()->records(level)));
        s.addChip(more,s.chip("info","Como jogar",false,this::howTo));
        content.addView(s.scroller(more),s.gap(16));
        PlusUi.run(this,()->{int[] best=new int[BibleGame.LEVELS.length];boolean hasBible;try(BibleStudyStore st=new BibleStudyStore(this)){for(int l=0;l<BibleGame.LEVELS.length;l++){List<BibleStudyStore.Record> r=st.records(l,1);best[l]=r.isEmpty()?0:r.get(0).score;}}hasBible=new BibleStudyStore.Verses(this).available();
            PlusUi.post(this,()->{if(!state.equals("menu"))return;bible=hasBible;
                for(int l=0;l<BibleGame.LEVELS.length;l++)levels.addView(levelCard(l,best[l]),s.gap(l==0?0:10));
                if(!hasBible){LinearLayout c=s.card();c.addView(s.text("Sem Bíblia importada, o jogo usa histórias, livros e factos. Importe uma Bíblia para jogar também com versículos.",14,s.p.muted,false));c.addView(s.button("Descarregar uma Bíblia",false,()->BibleCatalog.show(this)),s.gap(10));note.addView(c,s.gap(14));}
            });});
    }
    View levelCard(int l,int best){
        LinearLayout c=s.row();c.setPadding(s.dp(14),s.dp(14),s.dp(14),s.dp(14));c.setBackground(s.round(s.p.surface,20));c.setElevation(s.dp(1));
        TextView badge=s.text(""+(l+1),20,0xffffffff,true);badge.setGravity(Gravity.CENTER);GradientDrawable o=new GradientDrawable();o.setShape(GradientDrawable.OVAL);o.setColor(LEVEL_COLORS[l]);badge.setBackground(o);
        c.addView(badge,new LinearLayout.LayoutParams(s.dp(48),s.dp(48)));
        LinearLayout col=s.column();col.setPadding(s.dp(14),0,s.dp(6),0);
        col.addView(s.text(BibleGame.LEVELS[l],18,s.p.ink,true));
        TextView desc=s.text(LEVEL_TEXT[l],13,s.p.muted,false);desc.setPadding(0,s.dp(2),0,s.dp(4));col.addView(desc);
        col.addView(s.text(BibleGame.SECONDS[l]+" s "+AppLanguage.text(this,"por pergunta")+(best>0?" · "+AppLanguage.text(this,"recorde")+" "+best+" pts":""),12.5f,LEVEL_COLORS[l],true));
        c.addView(col,new LinearLayout.LayoutParams(0,-2,1));
        c.addView(new Icons(this,"playfill",LEVEL_COLORS[l]),new LinearLayout.LayoutParams(s.dp(26),s.dp(26)));
        c.setContentDescription(AppLanguage.text(this,"Jogar nível")+" "+AppLanguage.text(this,BibleGame.LEVELS[l]));c.setOnClickListener(v->start(l));Motion.touch(c);
        return c;
    }
    void howTo(){
        new UiDialog.Builder(this).setTitle("Como jogar").setMessage(
            "• Cada partida tem 10 perguntas.\n"+
            "• Todos os níveis: 60 s por pergunta. O que muda é a profundidade das perguntas.\n"+
            "• Fácil: passagens famosas e histórias conhecidas.\n"+
            "• Médio: qualquer versículo, livro e capítulo, respostas escritas.\n"+
            "• Difícil: completar passagens, quem disse, a ordem dos acontecimentos e perguntas que pedem raciocínio.\n"+
            "• Lendário: perguntas para meditar (tipologia, citações do Antigo no Novo Testamento, doutrina) e escrever a palavra que falta.\n"+
            "• Depois de cada resposta aparece a explicação com a referência.\n"+
            "• Acerto: 100, 150, 200 ou 300 pontos, mais um bónus pelo tempo que sobrar.\n"+
            "• 3 acertos seguidos ou mais: +20%.\n"+
            "• Ajuda 50/50: tira duas respostas erradas (uma vez por partida; a pergunta vale metade).\n"+
            "• Dizer: responda em voz alta com o nome do livro ou a letra da opção.\n"+
            "• Do Médio para cima, algumas respostas escrevem-se ou dizem-se: \"primeira aos coríntios\", \"Isaías\", ou a palavra que falta.\n"+
            "• No fim, guarde o seu nome no Recorde.").setPositiveButton("Entendi",null).show();
    }

    // Play ----------------------------------------------------------------------------------------
    void start(int l){
        level=l;state="loading";LinearLayout content=s.page();content.addView(s.header("Jogo da Bíblia",AppLanguage.text(this,"Nível")+" "+AppLanguage.text(this,BibleGame.LEVELS[l]),this::menu));
        content.addView(s.text("A preparar as perguntas…",15,s.p.muted,false));
        PlusUi.run(this,()->{BibleStudyStore.Verses verses=new BibleStudyStore.Verses(this);BibleGame g=new BibleGame(l,new Random(),verses);List<BibleGame.Question> round=g.round();
            PlusUi.post(this,()->{if(!state.equals("loading"))return;game=g;questions=round;index=0;score=0;correct=0;streak=0;bestStreak=0;fiftyUsed=false;log.clear();started=SystemClock.uptimeMillis();question();});});
    }
    void question(){
        state="play";answered=false;PlusUi.MAIN.removeCallbacks(tick);
        BibleGame.Question q=questions.get(index);
        LinearLayout content=s.page();
        LinearLayout top=s.row();
        FrameLayout close=new FrameLayout(this);close.setBackground(s.round(s.p.tint,20));close.addView(new Icons(this,"close",s.p.ink),new FrameLayout.LayoutParams(s.dp(20),s.dp(20),Gravity.CENTER));close.setContentDescription(AppLanguage.text(this,"Sair do jogo"));close.setOnClickListener(v->confirmQuit());
        top.addView(close,new LinearLayout.LayoutParams(s.dp(40),s.dp(40)));
        TextView counter=s.text(AppLanguage.text(this,"Pergunta")+" "+(index+1)+" "+AppLanguage.text(this,"de")+" "+questions.size(),15,s.p.ink,true);counter.setPadding(s.dp(12),0,0,0);
        top.addView(counter,new LinearLayout.LayoutParams(0,-2,1));
        scoreText=s.text(score+" pts",16,LEVEL_COLORS[level],true);top.addView(scoreText);
        content.addView(top);
        dots=s.row();for(int i=0;i<questions.size();i++){View dot=new View(this);int color=i<log.size()?("1".equals(log.get(i)[3])?0xff2e9d57:0xffd9534f):i==index?s.p.accent:s.p.tint;dot.setBackground(s.round(color,3));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,s.dp(6),1);lp.rightMargin=i<questions.size()-1?s.dp(4):0;dots.addView(dot,lp);}
        content.addView(dots,s.gap(12));
        FrameLayout timer=new FrameLayout(this);timer.setBackground(s.round(s.p.tint,5));timerFill=new View(this);timerFill.setBackground(s.round(LEVEL_COLORS[level],5));timer.addView(timerFill,new FrameLayout.LayoutParams(-1,-1));
        content.addView(timer,new LinearLayout.LayoutParams(-1,s.dp(8)));((LinearLayout.LayoutParams)timer.getLayoutParams()).topMargin=s.dp(10);
        timerText=s.text("",12,s.p.muted,true);timerText.setGravity(Gravity.END);content.addView(timerText);
        LinearLayout card=s.card();card.setPadding(s.dp(18),s.dp(16),s.dp(18),s.dp(18));
        TextView kind=s.text(kindLabel(q.kind),12,LEVEL_COLORS[level],true);kind.setAllCaps(true);kind.setLetterSpacing(.06f);card.addView(kind);
        TextView prompt=s.text(q.prompt,18,s.p.ink,true);prompt.setPadding(0,s.dp(6),0,0);card.addView(prompt);
        if(!q.body.isEmpty()){TextView body=s.text(q.body,q.body.length()>160?18:21,s.p.ink,false);body.setTypeface(Arrays.asList("verse","free","chapter","refverse","blank","blankwrite","who").contains(q.kind)?Typeface.SERIF:Typeface.DEFAULT_BOLD);body.setPadding(0,s.dp(12),0,0);body.setLineSpacing(s.dp(3),1.08f);card.addView(body);}
        content.addView(card,s.gap(8));
        optionsBox=s.column();optionViews.clear();freeAnswer=null;
        if(q.free()){
            freeAnswer=new EditText(this);freeAnswer.setHint(AppLanguage.text(this,q.word()?"Palavra que falta":"Nome do livro"));freeAnswer.setSingleLine(true);freeAnswer.setTextSize(18);freeAnswer.setTextColor(s.p.ink);freeAnswer.setHintTextColor(s.p.muted);freeAnswer.setBackground(s.round(s.p.surface,16));freeAnswer.setPadding(s.dp(16),s.dp(12),s.dp(16),s.dp(12));
            freeAnswer.setOnEditorActionListener((v,id,e)->{answer(freeAnswer.getText().toString());return true;});
            optionsBox.addView(freeAnswer,new LinearLayout.LayoutParams(-1,s.dp(56)));
            optionsBox.addView(s.button("Responder",true,()->answer(freeAnswer.getText().toString())),s.gap(10));
        }else{
            String letters="ABCD";
            for(int i=0;i<q.options.size();i++){final String option=q.options.get(i);TextView b=option(letters.charAt(i),option);optionViews.add(b);optionsBox.addView(b,s.gap(i==0?0:10));}
        }
        content.addView(optionsBox,s.gap(14));
        LinearLayout helps=s.row();
        if(!q.free()&&q.options.size()==4){LinearLayout f=s.chip("filters","50/50",false,this::fifty);fiftyChip=(TextView)f.getChildAt(1);if(fiftyUsed)f.setAlpha(.4f);s.addChip(helps,f);}
        s.addChip(helps,s.chip("mic","Dizer a resposta",false,this::listen));
        content.addView(s.scroller(helps),s.gap(14));
        feedback=s.column();content.addView(feedback,s.gap(12));
        deadline=SystemClock.uptimeMillis()+BibleGame.SECONDS[level]*1000L;paused=false;PlusUi.MAIN.post(tick);
    }
    String kindLabel(String kind){
        switch(kind){case "verse":return AppLanguage.text(this,"Passagem · livro");case "free":return AppLanguage.text(this,"Escreva ou diga");case "chapter":return AppLanguage.text(this,"Livro e capítulo");case "refverse":return AppLanguage.text(this,"Referência · passagem");
            case "story":return AppLanguage.text(this,"História");case "order":case "first":return AppLanguage.text(this,"Ordem dos livros");case "group":return AppLanguage.text(this,"Grupos de livros");case "author":return AppLanguage.text(this,"Autores");case "chapters":return AppLanguage.text(this,"Capítulos");case "testament":return AppLanguage.text(this,"Testamentos");case "blank":case "blankwrite":return AppLanguage.text(this,"Complete a passagem");case "who":return AppLanguage.text(this,"Quem disse?");case "sequence":return AppLanguage.text(this,"Ordem dos acontecimentos");case "deep":return AppLanguage.text(this,"Pense bem");case "reflect":return AppLanguage.text(this,"Para meditar");default:return AppLanguage.text(this,"Sabia que…");}
    }
    TextView option(char letter,String text){
        TextView b=s.text(letter+"   "+text,16,s.p.ink,true);b.setMinHeight(s.dp(56));b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(s.dp(16),s.dp(12),s.dp(16),s.dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(s.dp(16));bg.setColor(s.p.surface);bg.setStroke(s.dp(2),s.p.line);b.setBackground(bg);
        b.setTag(text);b.setOnClickListener(v->answer(text));Motion.touch(b);return b;
    }
    void tick(){
        if(!state.equals("play")||answered||paused||timerFill==null)return;
        long left=deadline-SystemClock.uptimeMillis();int total=BibleGame.SECONDS[level]*1000;
        float f=Math.max(0,Math.min(1,left/(float)total));View parent=(View)timerFill.getParent();
        if(parent.getWidth()>0){timerFill.getLayoutParams().width=Math.round(parent.getWidth()*f);timerFill.requestLayout();}
        timerFill.setBackground(s.round(left<5000?0xffd9534f:LEVEL_COLORS[level],5));
        timerText.setText(Math.max(0,(left+999)/1000)+" s");
        if(left<=0){answer(null);return;}
        PlusUi.MAIN.postDelayed(tick,100);
    }
    void answer(String given){
        if(answered||!state.equals("play"))return;answered=true;PlusUi.MAIN.removeCallbacks(tick);
        BibleGame.Question q=questions.get(index);
        int secondsLeft=(int)Math.max(0,(deadline-SystemClock.uptimeMillis())/1000);
        boolean right=q.correct(given);
        streak=right?streak+1:0;bestStreak=Math.max(bestStreak,streak);
        boolean helped=fiftyUsed&&optionViews.size()==4&&countHidden()>0;
        lastPoints=BibleGame.points(level,right,secondsLeft,streak,helped);score+=lastPoints;if(right)correct++;
        log.add(new String[]{q.prompt+(q.body.isEmpty()?"":"\n"+BibleGame.clip(q.body,120)),given==null?"":given,q.shown(),right?"1":"0"});
        View root=getWindow().getDecorView();root.performHapticFeedback(right?HapticFeedbackConstants.VIRTUAL_KEY:HapticFeedbackConstants.LONG_PRESS);
        for(TextView b:optionViews){
            String text=(String)b.getTag();b.setOnClickListener(null);
            GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(s.dp(16));
            if(text.equals(q.answer)){bg.setColor(0xff2e9d57);b.setTextColor(0xffffffff);b.setText("✓   "+text);}
            else if(text.equals(given)){bg.setColor(0xffd9534f);b.setTextColor(0xffffffff);b.setText("✗   "+text);}
            else{bg.setColor(s.p.surface);bg.setStroke(s.dp(2),s.p.line);b.setAlpha(.55f);}
            b.setBackground(bg);
        }
        if(freeAnswer!=null)freeAnswer.setEnabled(false);
        scoreText.setText(score+" pts");
        LinearLayout box=s.card();
        String head=given==null&&!right?"⏱ "+AppLanguage.text(this,"O tempo acabou"):right?"✓ "+AppLanguage.text(this,"Certo!")+" +"+lastPoints+(streak>=3?"  🔥"+streak:""):"✗ "+AppLanguage.text(this,"Era")+": "+q.shown();
        box.addView(s.text(head,19,right?0xff2e9d57:0xffd9534f,true));
        if(q.free()&&given!=null&&!right){String said=BibleReferences.bookNamed(given);TextView you=s.text(AppLanguage.text(this,"A sua resposta")+": "+(said.isEmpty()?given:BibleGame.display(said)),13,s.p.muted,false);box.addView(you);}
        if(!q.explain.isEmpty()){TextView ex=s.text(q.explain,14,s.p.muted,false);ex.setPadding(0,s.dp(4),0,0);box.addView(ex);}
        LinearLayout actions=s.row();
        if(!q.reference.isEmpty()){BibleReferences.Reference ref=BibleReferences.lookup(q.reference);if(ref!=null)s.addChip(actions,s.chip("book","Ler a passagem",false,()->BibleLinks.open(this,ref)));}
        if(actions.getChildCount()>0)box.addView(s.scroller(actions),s.gap(10));
        boolean last=index>=questions.size()-1;
        box.addView(s.button(last?"Ver resultado":"Seguinte",true,()->{if(last)result();else{index++;question();}}),s.gap(12));
        feedback.addView(box,new LinearLayout.LayoutParams(-1,-2));
        View dot=dots.getChildAt(index);if(dot!=null)dot.setBackground(s.round(right?0xff2e9d57:0xffd9534f,3));
        ScrollView scroll=(ScrollView)feedback.getParent().getParent();scroll.post(()->scroll.smoothScrollTo(0,feedback.getBottom()));
    }
    int countHidden(){int n=0;for(TextView b:optionViews)if(b.getVisibility()!=View.VISIBLE)n++;return n;}
    void fifty(){
        if(answered||fiftyUsed||optionViews.size()!=4){if(fiftyUsed&&!answered)PlusUi.toast(this,"A ajuda 50/50 já foi usada nesta partida.");return;}
        fiftyUsed=true;List<String> hide=game.fifty(questions.get(index));
        for(TextView b:optionViews)if(hide.contains((String)b.getTag()))b.setVisibility(View.INVISIBLE);
        if(fiftyChip!=null)((View)fiftyChip.getParent()).setAlpha(.4f);
    }
    void confirmQuit(){
        if(!state.equals("play")){menu();return;}
        new UiDialog.Builder(this).setTitle("Sair da partida?").setMessage("A pontuação desta partida não é guardada.").setPositiveButton("Sair",(d,w)->menu()).setNegativeButton("Continuar",null).show();
    }

    // Result and records ----------------------------------------------------------------------------
    void result(){
        state="result";PlusUi.MAIN.removeCallbacks(tick);
        int seconds=(int)((SystemClock.uptimeMillis()-started)/1000);
        PlusUi.run(this,()->{int rank;try(BibleStudyStore st=new BibleStudyStore(this)){st.count("game",1,System.currentTimeMillis());rank=st.rank(level,score);}
            PlusUi.post(this,()->showResult(seconds,rank));});
    }
    void showResult(int seconds,int rank){
        LinearLayout content=s.page();
        content.addView(s.header("Resultado",AppLanguage.text(this,"Nível")+" "+AppLanguage.text(this,BibleGame.LEVELS[level]),this::menu));
        LinearLayout card=s.card();card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(s.dp(18),s.dp(22),s.dp(18),s.dp(22));
        int stars=BibleGame.stars(correct);
        TextView st=s.text(stars==3?"★★★":stars==2?"★★☆":stars==1?"★☆☆":"☆☆☆",40,0xfff2b705,true);st.setGravity(Gravity.CENTER);card.addView(st);
        TextView pts=s.text(score+" pts",34,s.p.ink,true);pts.setTypeface(Screens.serif());pts.setGravity(Gravity.CENTER);card.addView(pts);
        String msg=correct==10?"Perfeito! Conhece mesmo a Bíblia.":correct>=8?"Excelente!":correct>=6?"Muito bem!":correct>=4?"Bom esforço — continue a ler!":"Continue a estudar e tente de novo.";
        TextView m=s.text(msg,16,s.p.ink,true);m.setGravity(Gravity.CENTER);m.setPadding(0,s.dp(4),0,s.dp(6));card.addView(m);
        TextView stats=s.text(correct+" "+AppLanguage.text(this,"de")+" 10 "+AppLanguage.text(this,"certas")+" · "+AppLanguage.text(this,"melhor sequência")+" "+bestStreak+" · "+(seconds/60)+":"+String.format(Locale.ROOT,"%02d",seconds%60),14,s.p.muted,false);stats.setGravity(Gravity.CENTER);card.addView(stats);
        if(score>0){TextView r=s.text(rank==1?"🏆 "+AppLanguage.text(this,"Novo recorde do nível!"):rank<=10?AppLanguage.text(this,"Entra no Recorde em")+" "+rank+".º "+AppLanguage.text(this,"lugar"):AppLanguage.text(this,"Fora dos 10 melhores, mas pode guardar."),15,rank<=10?LEVEL_COLORS[level]:s.p.muted,true);r.setGravity(Gravity.CENTER);r.setPadding(0,s.dp(10),0,0);card.addView(r);}
        content.addView(card,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout save=s.card();
        save.addView(s.text("Guardar no Recorde",16,s.p.ink,true));
        EditText name=new EditText(this);name.setHint(AppLanguage.text(this,"O seu nome"));name.setSingleLine(true);name.setTextSize(17);name.setTextColor(s.p.ink);name.setHintTextColor(s.p.muted);
        name.setText(getSharedPreferences(BibleStudyStore.PREFS,0).getString("game_name",""));name.setBackground(s.round(s.p.tint,14));name.setPadding(s.dp(14),s.dp(10),s.dp(14),s.dp(10));
        save.addView(name,s.gap(10));
        final boolean[] saved={false};
        TextView keep=s.button("Guardar",true,()->{
            if(saved[0])return;String n=name.getText().toString().trim();if(n.isEmpty()){name.setError(AppLanguage.text(this,"Escreva o seu nome."));return;}
            if(n.length()>30)n=n.substring(0,30);final String who=n;saved[0]=true;getSharedPreferences(BibleStudyStore.PREFS,0).edit().putString("game_name",who).apply();
            PlusUi.run(this,()->{long id;try(BibleStudyStore st2=new BibleStudyStore(this)){id=st2.record(who,level,score,correct,seconds,System.currentTimeMillis());}PlusUi.post(this,()->{PlusUi.toast(this,"Guardado no Recorde.");records(level,id);});});
        });
        save.addView(keep,s.gap(10));
        content.addView(save,s.gap(12));
        LinearLayout again=s.row();
        TextView play=s.button("Jogar de novo",false,()->start(level));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=s.dp(8);again.addView(play,lp);
        TextView other=s.button("Outro nível",false,this::menu);again.addView(other,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(again,s.gap(12));
        content.addView(s.section("Rever respostas"));
        for(int i=0;i<log.size();i++){
            String[] e=log.get(i);boolean ok="1".equals(e[3]);
            LinearLayout row=s.row();row.setGravity(Gravity.TOP);row.setPadding(s.dp(12),s.dp(10),s.dp(12),s.dp(10));row.setBackground(s.round(s.p.surface,14));
            TextView mark=s.text(ok?"✓":"✗",18,ok?0xff2e9d57:0xffd9534f,true);row.addView(mark,new LinearLayout.LayoutParams(s.dp(26),-2));
            LinearLayout col=s.column();TextView qv=s.text((i+1)+". "+e[0],14,s.p.ink,false);qv.setMaxLines(4);qv.setEllipsize(TextUtils.TruncateAt.END);col.addView(qv);
            col.addView(s.text(AppLanguage.text(this,"Resposta certa")+": "+e[2]+(ok||e[1].isEmpty()?"":"  ·  "+AppLanguage.text(this,"a sua")+": "+e[1]),13,ok?0xff2e9d57:s.p.muted,true));
            row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
            content.addView(row,s.gap(i==0?0:8));
        }
    }
    void records(int show){records(show,-1);}
    void records(int show,long highlight){
        state="records";level=show;LinearLayout content=s.page();
        content.addView(s.header("Recordes","Os 10 melhores de cada nível",this::menu));
        LinearLayout tabs=s.row();for(int l=0;l<BibleGame.LEVELS.length;l++){final int ll=l;s.addChip(tabs,s.chip(null,BibleGame.LEVELS[l],l==show,()->records(ll,highlight)));}
        content.addView(s.scroller(tabs));
        LinearLayout list=s.column();content.addView(list,s.gap(12));
        PlusUi.run(this,()->{List<BibleStudyStore.Record> rows;try(BibleStudyStore st=new BibleStudyStore(this)){rows=st.records(show,10);}
            PlusUi.post(this,()->{
                if(rows.isEmpty()){list.addView(s.text("Ainda não há recordes neste nível. Jogue uma partida e guarde o seu nome.",15,s.p.muted,false));list.addView(s.button("Jogar "+BibleGame.LEVELS[show],true,()->start(show)),s.gap(14));return;}
                String[] medals={"🥇","🥈","🥉"};
                for(int i=0;i<rows.size();i++){
                    BibleStudyStore.Record r=rows.get(i);boolean mine=r.id==highlight;
                    LinearLayout row=s.row();row.setPadding(s.dp(14),s.dp(12),s.dp(14),s.dp(12));row.setBackground(s.round(mine?(s.p.accent&0xffffff)|0x33000000:s.p.surface,16));
                    TextView pos=s.text(i<3?medals[i]:(i+1)+".",i<3?22:16,s.p.ink,true);pos.setGravity(Gravity.CENTER);row.addView(pos,new LinearLayout.LayoutParams(s.dp(40),-2));
                    LinearLayout col=s.column();col.setPadding(s.dp(8),0,0,0);col.addView(s.text(r.name,16,s.p.ink,true));
                    col.addView(s.text(r.correct+"/10 · "+(r.seconds/60)+":"+String.format(Locale.ROOT,"%02d",r.seconds%60)+" · "+DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(r.at)),12.5f,s.p.muted,false));
                    row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
                    row.addView(s.text(r.score+" pts",17,LEVEL_COLORS[show],true));
                    list.addView(row,s.gap(i==0?0:8));
                }
                list.addView(s.button("Jogar "+BibleGame.LEVELS[show],true,()->start(show)),s.gap(16));
            });});
    }

    // Spoken answers ----------------------------------------------------------------------------------
    void listen(){
        if(answered)return;
        try{startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag()).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5).putExtra(RecognizerIntent.EXTRA_PROMPT,AppLanguage.text(this,"Diga o livro ou a letra da resposta")),SPEECH);}
        catch(ActivityNotFoundException e){PlusUi.toast(this,"O reconhecimento de voz não está disponível neste telefone.");}
    }
    @Override protected void onActivityResult(int request,int code,Intent data){
        super.onActivityResult(request,code,data);
        if(request!=SPEECH||code!=RESULT_OK||data==null||!state.equals("play")||answered)return;
        List<String> heard=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(heard==null||heard.isEmpty())return;
        BibleGame.Question q=questions.get(index);
        if(q.free()){String best=heard.get(0);for(String h:heard)if(!BibleReferences.bookNamed(h).isEmpty()){best=h;break;}if(freeAnswer!=null)freeAnswer.setText(best);answer(best);return;}
        String chosen=spoken(q,heard);
        if(chosen==null){PlusUi.toast(this,AppLanguage.text(this,"Não percebi")+": "+heard.get(0));return;}
        answer(chosen);
    }
    /** The option a spoken answer names: a letter, an ordinal, a book or the option text itself. */
    static String spoken(BibleGame.Question q,List<String> heard){
        String[][] letters={{"a","letra a","opção a","primeira","um","1"},{"b","bê","be","letra b","opção b","segunda","dois","2"},{"c","cê","ce","letra c","opção c","terceira","três","tres","3"},{"d","dê","de","letra d","opção d","quarta","quatro","4"}};
        for(String h:heard){
            String t=BibleStudyCore.plain(h);
            for(int i=0;i<letters.length&&i<q.options.size();i++)for(String l:letters[i])if(t.equals(BibleStudyCore.plain(l)))return q.options.get(i);
            String book=BibleReferences.bookNamed(h);
            if(!book.isEmpty())for(String o:q.options){if(BibleReferences.bookNamed(o).equals(book)&&!o.matches(".*\\s\\d+$"))return o;}
            if(!book.isEmpty())for(String o:q.options){String[] parts=o.split(" ");String num=t.replaceAll("\\D+"," ").trim();if(BibleReferences.bookNamed(o).equals(book)&&!num.isEmpty()&&o.endsWith(" "+num.substring(num.lastIndexOf(' ')+1)))return o;}
            for(String o:q.options){String p=BibleStudyCore.plain(o);if(t.length()>2&&(p.equals(t)||p.startsWith(t)||t.contains(p)))return o;}
        }
        return null;
    }
}
