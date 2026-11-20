package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Document-centred controls inspired by the supplied reader references. */
final class ReaderChrome implements SpeechEvents.Listener {
    final ReaderActivity a;
    final SharedPreferences prefs;
    LinearLayout top, bottom, navigation, speechBar, dock, toolStrip, grid, floating;
    /** Title bar colour and the ink drawn on it (0 until the bar is styled). */
    int barColor, barInk;
    boolean compact;
    HorizontalScrollView actions;
    TextView title, pageLabel, speechLabel;View speechToggle;
    SeekBar seek;
    boolean dragging, hidden, following;
    int lastSpeechPart=-1;
    BackgroundColorSpan spokenSpan;
    ReaderSpeech speech;
    ReaderChrome(ReaderActivity activity) {a=activity;prefs=a.getSharedPreferences("seeky",0);speech=new ReaderSpeech(a);}
    String tr(String value){return AppLanguage.text(a,value);}
    LinearLayout row(){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);return row;}
    LinearLayout column(){LinearLayout column=new LinearLayout(a);column.setOrientation(1);return column;}
    void install() {
        a.root=column();UiSpacing.ignore(a.root);a.root.setClipToOutline(true);a.root.setFitsSystemWindows(true);
        if(Build.VERSION.SDK_INT>=35){a.root.setFitsSystemWindows(false);a.root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});}
        top=column();LinearLayout bar=row();bar.setPadding(a.dp(4),0,a.dp(4),0);
        icon(bar,"back","Voltar",a::finish);if(a.tools!=null)icon(bar,"contents","Índice",this::contents);
        title=a.text("",15);String shown=a.getIntent().getStringExtra("study_title");title.setText(shown==null?a.name:shown);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.MIDDLE);title.setGravity(Gravity.CENTER);title.setPadding(a.dp(5),0,a.dp(5),0);bar.addView(title,new LinearLayout.LayoutParams(0,a.dp(48),1));
        if(a.tools!=null){icon(bar,"search","Pesquisar no documento",()->a.tools.search(true));icon(bar,"bookmark","Marcadores",()->a.tools.bookmarks());}else{icon(bar,"expand","Expandir",a::toggleFullScreen);icon(bar,"settings","Ferramentas de leitura",a.extras::show);}top.addView(bar);
        String path=a.getIntent().getStringExtra("study_path");if(path!=null&&!path.isEmpty()){TextView trail=a.text("",10);trail.setText(path);trail.setTextColor(a.muted);trail.setMaxLines(1);trail.setEllipsize(TextUtils.TruncateAt.MIDDLE);trail.setGravity(Gravity.CENTER);trail.setPadding(a.dp(16),0,a.dp(16),a.dp(5));top.addView(trail);}
        a.status=a.text("A abrir…",11);a.status.setTextColor(a.muted);a.status.setGravity(Gravity.CENTER);a.status.setMaxLines(1);a.status.setEllipsize(TextUtils.TruncateAt.END);a.status.setPadding(a.dp(12),a.dp(2),a.dp(12),a.dp(6));top.addView(a.status);a.root.addView(top);
        a.body=column();a.body.setBackgroundColor(0xff202226);FrameLayout stage=new FrameLayout(a);stage.addView(a.body,new FrameLayout.LayoutParams(-1,-1));a.extras.ruler=new ReaderRuler(a);stage.addView(a.extras.ruler,new FrameLayout.LayoutParams(-1,-1));a.root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        a.controls=row();a.controls.setVisibility(View.GONE);a.root.addView(a.controls);
        bottom=column();speechBar=new SpeechTransport(a);bottom.addView(speechBar);dock=column();bottom.addView(dock);
        seek=new SeekBar(a);seek.setProgressTintList(ColorStateList.valueOf(a.accent));seek.setThumbTintList(ColorStateList.valueOf(a.accent));seek.setProgressBackgroundTintList(ColorStateList.valueOf(AppPalette.of(a).line));seek.setPadding(a.dp(18),0,a.dp(18),0);dock.addView(seek,new LinearLayout.LayoutParams(-1,a.dp(26)));
        navigation=row();icon(navigation,"back","Anterior",()->turn(-1));pageLabel=a.text("",13);pageLabel.setGravity(Gravity.CENTER);pageLabel.setPadding(0,0,0,0);pageLabel.setOnClickListener(v->contents());navigation.addView(pageLabel,new LinearLayout.LayoutParams(0,a.dp(34),1));icon(navigation,"arrow","Seguinte",()->turn(1));dock.addView(navigation);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){dragging=true;}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)pageLabel.setText((value+1)+" / "+count()+"  ("+ReaderAppearance.percent(value,count())+"%)");}public void onStopTrackingTouch(SeekBar bar){int target=bar.getProgress();dragging=false;go(target);}});
        actions=new HorizontalScrollView(a);actions.setHorizontalScrollBarEnabled(false);LinearLayout strip=row();toolStrip=strip;
        action(strip,"type","Texto",this::textSettings);action(strip,"palette","Visualizar",this::visualSettings);action(strip,"rotate","Rotação",this::rotate);action(strip,"quiz","Estudar com Seeky",()->SeekyAi.reader(a,null));action(strip,"speaker","Ouvir",speech::show);action(strip,"share","Partilhar",this::share);action(strip,"note","Adicionar nota",this::addNote);action(strip,"expand","Tela cheia",this::toggleChrome);action(strip,"settings","Definições",this::settings);action(strip,"edit","Organizar",this::organize);actions.addView(strip);dock.addView(actions,new LinearLayout.LayoutParams(-1,a.dp(68)));a.root.addView(bottom);
        if(a.getIntent().getBooleanExtra("open_narrations",false))a.main.postDelayed(()->{if(!a.dead)speech.narration.manage();},350);
        modernise();responsive(a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        a.root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r>l&&b>t&&compact!=(r-l>b-t))responsive(r-l>b-t);});
        SpeechEvents.add(this);update();
        if(a.getIntent().getBooleanExtra("open_speech",false))a.main.postDelayed(()->{if(!a.dead)speech.show();},350);
    }
    // A single dock in landscape leaves the page usable while keeping every tool reachable.
    void responsive(boolean landscape){
        if(dock==null||toolStrip==null)return;
        compact=landscape;
        for(int i=1;i<top.getChildCount();i++)top.getChildAt(i).setVisibility(landscape?View.GONE:View.VISIBLE);
        LinearLayout.LayoutParams titleParams=(LinearLayout.LayoutParams)title.getLayoutParams();int titleHeight=a.dp(landscape?44:48);if(titleParams.height!=titleHeight){titleParams.height=titleHeight;title.setLayoutParams(titleParams);}
        dock.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        if(seek.getParent()!=navigation){
            ((ViewGroup)seek.getParent()).removeView(seek);
            navigation.addView(seek,1,new LinearLayout.LayoutParams(0,a.dp(44),1));
        }
        navigation.setLayoutParams(new LinearLayout.LayoutParams(landscape?a.dp(270):-1,a.dp(44)));
        pageLabel.setLayoutParams(new LinearLayout.LayoutParams(landscape?a.dp(95):-2,a.dp(44)));pageLabel.setPadding(landscape?0:a.dp(6),0,landscape?0:a.dp(4),0);layoutTools();
        actions.setLayoutParams(new LinearLayout.LayoutParams(landscape?0:-1,a.dp(landscape?44:68),landscape?1:0));
        for(int i=0;i<toolStrip.getChildCount();i++){LinearLayout item=(LinearLayout)toolStrip.getChildAt(i);String label=String.valueOf(item.getContentDescription());item.setPadding(a.dp(5),landscape?0:a.dp(9),a.dp(5),landscape?0:a.dp(6));item.getChildAt(1).setVisibility(landscape?View.GONE:View.VISIBLE);item.setTooltipText(label);item.setLayoutParams(new LinearLayout.LayoutParams(a.dp(landscape?48:i==3?126:i==5?94:78),-1));}
        if(speechBar instanceof SpeechTransport)((SpeechTransport)speechBar).compact(landscape);
    }
    // Reader layout inspired by classic PDF readers --------------------------------------------------
    static int desk(int theme){return AppPalette.luminance(ReaderAppearance.paper(theme))>0.5?0xffcfd2d7:0xff202226;}
    /** Coloured title bar, one-line page slider, every tool visible in a grid and round page buttons. */
    void modernise(){
        AppPalette p=AppPalette.of(a);
        barColor=p.accent;barInk=AppPalette.contrast(0xffffffff,barColor)>=3?0xffffffff:0xff10141d;
        top.setBackgroundColor(barColor);top.setElevation(a.dp(3));
        LinearLayout bar=(LinearLayout)top.getChildAt(0);
        if(a.tools!=null)bar.addView(a.readerIcon("more",tr("Ferramentas de leitura"),a.extras::show),new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));
        for(int i=0;i<bar.getChildCount();i++){View child=bar.getChildAt(i);if(child instanceof FrameLayout&&((FrameLayout)child).getChildCount()>0&&((FrameLayout)child).getChildAt(0) instanceof Icons){Icons icon=(Icons)((FrameLayout)child).getChildAt(0);icon.setSymbol(icon.name,barInk);}}
        title.setTextColor(barInk);title.setTextSize(18);title.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);title.setPadding(a.dp(8),0,a.dp(4),0);
        for(int i=1;i<top.getChildCount();i++)if(top.getChildAt(i) instanceof TextView)((TextView)top.getChildAt(i)).setTextColor((barInk&0xffffff)|0xcc000000);
        a.body.setBackgroundColor(desk(a.readerTheme));
        bottom.setBackgroundColor(p.surface);bottom.setElevation(a.dp(6));
        grid=column();grid.setPadding(a.dp(4),a.dp(2),a.dp(4),a.dp(6));
        List<Object[]> tools=new ArrayList<>();
        if(a.tools!=null){tools.add(new Object[]{"contents","Índice","Índice",(Runnable)this::contents});tools.add(new Object[]{"search","Pesquisar no documento","Pesquisar",(Runnable)()->a.tools.search(true)});tools.add(new Object[]{"bookmark","Marcadores","Marcadores",(Runnable)()->a.tools.bookmarks()});}
        tools.add(new Object[]{"speaker","Ouvir","Ouvir",(Runnable)speech::show});
        tools.add(new Object[]{"type","Texto","Texto",(Runnable)this::textSettings});
        tools.add(new Object[]{"palette","Visualizar","Visualizar",(Runnable)this::visualSettings});
        tools.add(new Object[]{"rotate","Rotação","Rodar",(Runnable)this::rotate});
        tools.add(new Object[]{"edit","Marcações deste livro","Marcações",(Runnable)()->HighlightsActivity.open(a,a.uri.toString())});
        tools.add(new Object[]{"note","Adicionar nota","Nota",(Runnable)this::addNote});
        tools.add(new Object[]{"share","Partilhar","Partilhar",(Runnable)this::share});
        tools.add(new Object[]{"expand","Tela cheia","Ecrã cheio",(Runnable)this::toggleChrome});
        tools.add(new Object[]{"more","Mais ferramentas","Mais",(Runnable)this::more});
        int columns=6;LinearLayout line=null;
        for(int i=0;i<tools.size();i++){
            if(i%columns==0){line=row();grid.addView(line,new LinearLayout.LayoutParams(-1,a.dp(58)));}
            Object[] t=tools.get(i);final Runnable run=(Runnable)t[3];
            LinearLayout cell=column();cell.setGravity(Gravity.CENTER);
            cell.addView(new Icons(a,(String)t[0],a.ink),new LinearLayout.LayoutParams(a.dp(23),a.dp(23)));
            TextView caption=a.text(tr((String)t[2]),10);caption.setGravity(Gravity.CENTER);caption.setMaxLines(1);caption.setEllipsize(TextUtils.TruncateAt.END);caption.setPadding(a.dp(2),a.dp(5),a.dp(2),0);cell.addView(caption,new LinearLayout.LayoutParams(-1,-2));
            cell.setContentDescription(tr((String)t[1]));cell.setOnClickListener(v->run.run());Motion.touch(cell);
            line.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
        }
        if(line!=null)while(line.getChildCount()<columns)line.addView(new View(a),new LinearLayout.LayoutParams(0,-1,1));
        dock.addView(grid,dock.indexOfChild(actions));
        FrameLayout stage=(FrameLayout)a.body.getParent();
        floating=row();
        floating.addView(pageButton("fit","Ajustar página",this::cycleFit),new LinearLayout.LayoutParams(a.dp(54),a.dp(54)));
        View scroll=pageButton("motion","Rolagem vertical ou página a página",this::toggleScroll);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(a.dp(54),a.dp(54));sp.leftMargin=a.dp(14);floating.addView(scroll,sp);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);fp.setMargins(0,0,a.dp(16),a.dp(16));stage.addView(floating,fp);
        floatingVisibility();
    }
    View pageButton(String symbol,String label,Runnable action){
        FrameLayout b=new FrameLayout(a);GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);
        boolean light=AppPalette.luminance(ReaderAppearance.paper(a.readerTheme))>0.5;bg.setColor(light?0xd9b9bcc2:0xd93a3d44);b.setBackground(bg);b.setElevation(a.dp(2));
        b.addView(new Icons(a,symbol,light?0xff3c3f45:0xffe8eaee),new FrameLayout.LayoutParams(a.dp(26),a.dp(26),Gravity.CENTER));
        b.setContentDescription(tr(label));b.setOnClickListener(v->action.run());Motion.touch(b);return b;
    }
    /** Portrait shows the tool grid; landscape keeps the single scrolling strip. */
    void layoutTools(){
        // In portrait the grid already has the index, search and bookmarks: the title bar keeps the title.
        if(grid!=null&&top.getChildCount()>0&&top.getChildAt(0) instanceof LinearLayout){LinearLayout bar=(LinearLayout)top.getChildAt(0);for(int i=0;i<bar.getChildCount();i++){View c=bar.getChildAt(i);CharSequence d=c.getContentDescription();if(d!=null&&(d.toString().equals(tr("Índice"))||d.toString().equals(tr("Pesquisar no documento"))||d.toString().equals(tr("Marcadores"))))c.setVisibility(compact?View.VISIBLE:View.GONE);}}
        boolean document=a.kind.equals("pdf")||a.kind.equals("epub")||a.kind.equals("docx")||a.kind.equals("text");
        actions.setVisibility(document&&(compact||grid==null)?View.VISIBLE:View.GONE);
        if(grid!=null)grid.setVisibility(document&&!compact?View.VISIBLE:View.GONE);
        floatingVisibility();
    }
    void floatingVisibility(){if(floating!=null)floating.setVisibility(a.kind.equals("pdf")&&(a.tools==null||!a.tools.pdfText)&&!hidden&&!compact?View.VISIBLE:View.GONE);}
    void cycleFit(){
        a.pdfFit=(a.pdfFit+1)%3;prefs.edit().putInt("reader_fit",a.pdfFit).apply();if(a.pageAdapter!=null)a.pageAdapter.notifyDataSetChanged();
        PlusUi.toast(a,tr("Ajustar")+": "+tr(new String[]{"Página","Largura","Altura"}[a.pdfFit]));
    }
    void toggleScroll(){
        int part=current();a.continuous=!a.continuous;prefs.edit().putBoolean("reader_continuous",a.continuous).apply();a.currentPage=part;
        if(a.pageAdapter!=null){a.pageAdapter.notifyDataSetChanged();a.pageList.setSelection(a.continuous?part:0);}
        update();PlusUi.toast(a,tr(a.continuous?"Rolagem vertical":"Página a página"));
    }
    void more(){
        UiPanel panel=new UiPanel(a,"Mais ferramentas",a.name,"more");
        panel.action("quiz","Estudar com Seeky",null,false,()->SeekyAi.reader(a,null));
        panel.action("settings","Definições de leitura",null,false,this::settings);
        panel.action("edit","Organizar",null,false,this::organize);
        panel.action("tools","Ferramentas de leitura","Resumo, notas de voz, caderno, acessibilidade…",false,a.extras::show);
        if(a.tools!=null)panel.action("book","Passagens bíblicas","Nesta parte: toque para ler aqui mesmo",false,()->BibleLinks.list(a));
        panel.action("calendar","Estudo bíblico",null,false,()->BibleStudyActivity.open(a));
        panel.show();
    }
    void icon(LinearLayout row,String symbol,String label,Runnable action){row.addView(a.readerIcon(symbol,tr(label),action),new LinearLayout.LayoutParams(a.dp(44),a.dp(44)));}
    void action(LinearLayout strip,String symbol,String label,Runnable action){LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setPadding(a.dp(5),a.dp(9),a.dp(5),a.dp(6));item.addView(new Icons(a,symbol,a.ink),new LinearLayout.LayoutParams(a.dp(24),a.dp(24)));TextView caption=a.text(label,10);caption.setGravity(Gravity.CENTER);caption.setPadding(0,a.dp(7),0,0);caption.setMaxLines(1);item.addView(caption);item.setContentDescription(tr(label));item.setOnClickListener(v->action.run());Motion.touch(item);strip.addView(item,new LinearLayout.LayoutParams(a.dp(label.equals("Estudar com Seeky")?126:label.equals("Adicionar nota")?94:78),-1));}
    int current(){return a.kind.equals("pdf")?(a.tools!=null&&a.tools.pdfText?a.tools.segment:a.currentPage):a.chapter;}
    int count(){return a.kind.equals("pdf")?a.pages:a.book!=null?a.book.chapters.size():1;}
    void go(int part){if(part<0||part>=count())return;if(a.kind.equals("pdf")){if(a.tools.pdfText)a.tools.pageText(part);else a.setPage(part);}else if(a.book!=null)a.chapter(part);update();}
    void turn(int direction){
        if(a.dead)return;
        if(a.original!=null&&a.original.active){a.original.turn(direction);return;}
        if(a.bookScroll!=null&&a.bookText!=null){
            if(a.tools!=null&&(a.tools.loading||a.tools.saving||a.tools.draft==null))return;
            int viewport=Math.max(1,a.bookScroll.getHeight()-a.bookScroll.getPaddingTop()-a.bookScroll.getPaddingBottom());
            int y=a.bookScroll.getScrollY(),content=a.bookScroll.getChildAt(0).getHeight(),end=Math.max(0,content-viewport);
            if(direction>0&&y<end-a.dp(2)||direction<0&&y>a.dp(2)){
                int target=TextPaging.target(y,direction,viewport,content,a.bookText.getLineHeight());
                android.text.Layout layout=a.bookText.getLayout();if(layout!=null&&target>0&&target<end){int line=layout.getLineForVertical(Math.max(0,target-a.bookText.getPaddingTop()));int aligned=layout.getLineTop(line)+a.bookText.getPaddingTop();if(direction>0&&aligned>y||direction<0&&aligned<y)target=Math.min(end,Math.max(0,aligned));}
                a.bookScroll.smoothScrollTo(0,target);return;
            }
            int part=current()+direction;if(part<0||part>=count())return;a.pendingLanding=direction<0?-1:1;go(part);return;
        }
        go(current()+direction);
    }
    void update(){
        if(!a.searchOpened&&a.getIntent().hasExtra("search_offset")&&a.tools!=null&&a.readingReady()){a.searchOpened=true;final int offset=a.getIntent().getIntExtra("search_offset",0),length=a.getIntent().getIntExtra("search_length",1),part=a.getIntent().getIntExtra("study_segment",0);a.main.post(()->{if(a.dead)return;if(a.original!=null&&a.original.active)a.original.textMode();a.tools.pendingLength=Math.max(1,length);a.tools.navigate(part,offset,true);});}

        if(!a.recentRegistered&&a.readingReady())a.rememberReading();
        if(seek==null)return;int total=Math.max(1,count()),part=Math.max(0,Math.min(total-1,current()));
        if(!dragging){seek.setMax(total-1);seek.setProgress(part);pageLabel.setText((part+1)+" / "+total+"  ("+ReaderAppearance.percent(part,total)+"%)");}
        boolean document=a.kind.equals("pdf")||a.kind.equals("epub")||a.kind.equals("docx")||a.kind.equals("text");
        seek.setVisibility(total>1&&prefs.getBoolean("reader_show_progress",true)?View.VISIBLE:View.GONE);navigation.setVisibility(document&&prefs.getBoolean("reader_show_progress",true)?View.VISIBLE:View.GONE);
        if(a.original!=null&&a.original.active){int unit=ReadingProgress.units(part,total,a.original.scroll(),a.original.height(),a.original.viewport());pageLabel.setText((total==1?tr("Documento"):tr("Capítulo")+" "+(part+1)+" / "+total)+" · "+(int)Math.min(100,100L*unit/Math.max(1L,(long)total*1000))+"%");}
        else if(total>1&&a.bookScroll!=null&&a.bookText!=null&&!a.kind.equals("pdf")){int unit=ReadingProgress.units(part,total,a.bookScroll.getScrollY(),a.bookText.getHeight(),a.bookScroll.getHeight());int progress=(int)Math.min(100,100L*unit/Math.max(1L,(long)total*1000));pageLabel.setText(tr("Capítulo")+" "+(part+1)+" / "+total+" · "+progress+"%");}
        if(total==1&&!a.kind.equals("pdf")&&(a.original==null||!a.original.active)){int percent=0;if(a.bookScroll!=null&&a.bookText!=null){int range=a.bookText.getHeight()-a.bookScroll.getHeight();percent=range<=0?100:Math.min(100,Math.max(0,a.bookScroll.getScrollY()*100/range));}pageLabel.setText(tr("Leitura de texto")+" · "+percent+"%");}
        layoutTools();changed();if(a.extras!=null)a.extras.updateFocus();
    }
    void contents(){if(a.book!=null)a.chapters();else if(a.kind.equals("pdf"))a.tools.contents();else if(a.tools!=null)a.tools.bookmarks();}
    void applyText(){
        if(a.bookText==null)return;
        a.bookText.setLetterSpacing(prefs.getFloat("reader_letter_spacing",0));a.bookText.setTextColor(ReaderAppearance.ink(a.readerTheme));a.bookText.setTextSize(a.font);
        a.bookText.setTypeface(Typeface.create(prefs.getBoolean("reader_serif",false)?"serif":"sans-serif",Typeface.NORMAL));
        a.bookText.setLineSpacing(a.dp(3),prefs.getFloat("reader_spacing",1.30f));a.bookText.setPadding(a.dp(prefs.getInt("reader_margin",24)),a.dp(26),a.dp(prefs.getInt("reader_margin",24)),a.dp(30));
        a.bookText.setGravity(prefs.getBoolean("reader_center_text",false)?Gravity.CENTER_HORIZONTAL:Gravity.START);
        if(Build.VERSION.SDK_INT>=26)a.bookText.setJustificationMode(prefs.getBoolean("reader_justify",false)?android.text.Layout.JUSTIFICATION_MODE_INTER_WORD:android.text.Layout.JUSTIFICATION_MODE_NONE);
        GradientDrawable paper=new GradientDrawable();paper.setColor(ReaderAppearance.paper(a.readerTheme));paper.setCornerRadius(a.dp(2));a.bookText.setBackground(paper);a.bookText.setElevation(a.dp(4));
        if(a.bookScroll!=null){a.bookScroll.setFillViewport(true);a.bookScroll.setClipToPadding(false);a.bookScroll.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));}
    }
    void applyAppearance(){prefs.edit().putInt("reader_paper",a.readerTheme).apply();if(a.body!=null&&barColor!=0)a.body.setBackgroundColor(desk(a.readerTheme));if(floating!=null){boolean light=AppPalette.luminance(ReaderAppearance.paper(a.readerTheme))>0.5;for(int i=0;i<floating.getChildCount();i++){View b=floating.getChildAt(i);if(b.getBackground() instanceof GradientDrawable)((GradientDrawable)b.getBackground()).setColor(light?0xd9b9bcc2:0xd93a3d44);if(b instanceof FrameLayout&&((FrameLayout)b).getChildAt(0) instanceof Icons){Icons icon=(Icons)((FrameLayout)b).getChildAt(0);icon.setSymbol(icon.name,light?0xff3c3f45:0xffe8eaee);}}}applyText();if(a.pageList!=null)for(int n=0;n<a.pageList.getChildCount();n++){View page=a.pageList.getChildAt(n);if(page instanceof ReaderPageView)((ReaderPageView)page).appearance();}}
    void toggleChrome(){hidden=!hidden;top.setVisibility(hidden?View.GONE:View.VISIBLE);bottom.setVisibility(hidden?View.GONE:View.VISIBLE);floatingVisibility();a.applyReaderWindow();if(a.pageAdapter!=null)a.pageList.post(()->{if(!a.dead)a.pageAdapter.notifyDataSetChanged();});}
    void rotate(){int orientation=a.getResources().getConfiguration().orientation;a.setRequestedOrientation(orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);}
    void addNote(){
        UiPanel panel=new UiPanel(a,"Adicionar nota",null,"note");
        long id=a.getIntent().getLongExtra("study_material",0);
        if(id>0)panel.action("note","Nota desta página ou capítulo",null,false,()->StudyCapture.form(a,id,current(),"",0));
        panel.action("edit","Anotar uma passagem",null,false,()->{if(a.kind.equals("pdf")&&!a.tools.pdfText)a.tools.pageText(current());else if(a.tools.ready())a.tools.note();});
        panel.action("contents","Anotações do documento",null,false,()->a.tools.documentAnnotations());panel.show();
    }
    void textSettings(){
        if(a.original!=null&&a.original.active&&a.kind.equals("epub")){epubSettings();return;}
        UiPanel panel=new UiPanel(a,"Texto",null,"type");
        if(a.original!=null){panel.action("book",a.original.active?"Texto fluido para anotar":"Ver formato original",null,false,()->{if(a.original.active)a.original.textMode();else a.original.show();});if(a.original.active){panel.show();return;}}
        if(a.kind.equals("pdf"))panel.action("book",a.tools.pdfText?"Ver páginas originais":"Texto fluido",null,false,a.tools::togglePdfText);
        panel.section("Tamanho do texto");slider(panel,12,34,(int)a.font,value->{a.font=value;prefs.edit().putFloat("reader_font",a.font).apply();applyText();}," sp");
        panel.section("Espaçamento");slider(panel,100,180,(int)(prefs.getFloat("reader_spacing",1.30f)*100),value->{prefs.edit().putFloat("reader_spacing",value/100f).apply();applyText();},"%");
        panel.section("Margens");slider(panel,12,40,prefs.getInt("reader_margin",24),value->{prefs.edit().putInt("reader_margin",value).apply();applyText();},"");
        toggle(panel,"Fonte com serifa",prefs.getBoolean("reader_serif",false),value->{prefs.edit().putBoolean("reader_serif",value).apply();applyText();});
        toggle(panel,"Justificar texto",prefs.getBoolean("reader_justify",false),value->{prefs.edit().putBoolean("reader_justify",value).apply();applyText();});panel.show();
    }
    void epubSettings(){
        OriginalDocument original=a.original;EpubLayout.Options o=original.options();UiPanel panel=new UiPanel(a,"Ajustar leitura",null,"type");
        Switch fit=new Switch(a);fit.setText(tr("Adaptar à largura do ecrã"));fit.setTextColor(a.ink);fit.setMinHeight(a.dp(48));fit.setChecked(o.fitWidth);fit.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean(original.epubKey("fit"),on).apply();original.refreshEpub();});panel.content.addView(fit);
        panel.section("Tamanho do texto");slider(panel,75,175,o.zoom,value->{prefs.edit().putInt(original.epubKey("zoom"),value).apply();original.refreshEpub();},"%");
        panel.section("Margens");slider(panel,8,40,o.margin,value->{prefs.edit().putInt(original.epubKey("margin"),value).apply();fit.setChecked(true);original.refreshEpub();},"");
        panel.section("Espaçamento");Switch bookSpacing=new Switch(a);bookSpacing.setText(tr("Usar espaçamento do livro"));bookSpacing.setTextColor(a.ink);bookSpacing.setMinHeight(a.dp(48));bookSpacing.setChecked(o.spacing==0);panel.content.addView(bookSpacing);final int[] spacing={o.spacing==0?140:o.spacing};bookSpacing.setOnCheckedChangeListener((v,on)->{prefs.edit().putInt(original.epubKey("spacing"),on?0:spacing[0]).apply();if(!on)fit.setChecked(true);original.refreshEpub();});slider(panel,100,220,spacing[0],value->{spacing[0]=value;prefs.edit().putInt(original.epubKey("spacing"),value).apply();bookSpacing.setChecked(false);fit.setChecked(true);original.refreshEpub();},"%");
        panel.section("Alinhamento");ChoiceSpinner align=new ChoiceSpinner(a);String[] choices={tr("Do livro"),tr("À esquerda"),tr("Justificado")};align.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,choices));align.setSelection(o.align);final int[] current={o.align};align.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int at,long id){if(at==current[0])return;current[0]=at;prefs.edit().putInt(original.epubKey("align"),at).apply();fit.setChecked(true);original.refreshEpub();}});panel.content.addView(align,new LinearLayout.LayoutParams(-1,a.dp(48)));
        panel.action("refresh","Repor ajustes deste livro",null,false,()->{original.resetEpub();panel.dialog.dismiss();});panel.action("edit","Texto fluido para anotar",null,false,original::textMode);panel.show();
    }
    interface IntAction{void change(int value);}interface BoolAction{void change(boolean value);}
    void slider(UiPanel panel,int min,int max,int value,IntAction action,String suffix){
        TextView label=a.text(value+suffix,12);panel.content.addView(label);SeekBar bar=new SeekBar(a);bar.setMax(max-min);bar.setProgress(Math.max(0,Math.min(max-min,value-min)));bar.setProgressTintList(ColorStateList.valueOf(a.accent));bar.setThumbTintList(ColorStateList.valueOf(a.accent));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int p,boolean user){label.setText((p+min)+suffix);if(user)action.change(p+min);}public void onStopTrackingTouch(SeekBar bar){}});panel.content.addView(bar,new LinearLayout.LayoutParams(-1,a.dp(44)));
    }
    void toggle(UiPanel panel,String label,boolean checked,BoolAction action){Switch control=new Switch(a);control.setText(tr(label));control.setTextColor(a.ink);control.setTextSize(14);control.setMinHeight(a.dp(52));control.setPadding(a.dp(3),a.dp(8),a.dp(3),a.dp(8));control.setChecked(checked);control.setOnCheckedChangeListener((button,value)->action.change(value));panel.content.addView(control);}
    void visualSettings(){
        if(a.original!=null&&a.original.active){UiPanel original=new UiPanel(a,"Visualizar",null,"book");if(a.kind.equals("epub"))original.action("type","Texto e margens",null,false,this::textSettings);original.action("expand","Ampliar documento",null,false,()->a.original.zoom(true));original.action("collapse","Reduzir documento",null,false,()->a.original.zoom(false));original.action("refresh",a.kind.equals("epub")?"Ajustar à largura":"Ajustar documento",null,false,a.original::fit);original.show();return;}
        UiPanel panel=new UiPanel(a,"Visualizar",null,"palette");panel.section("Tema de leitura");LinearLayout colors=row();
        String[] labels={"Branco","Sépia","Cinza","Escuro"};for(int n=0;n<4;n++){final int theme=n;LinearLayout option=column();option.setGravity(Gravity.CENTER);View circle=new View(a);GradientDrawable bg=new GradientDrawable();bg.setColor(ReaderAppearance.paper(n));bg.setShape(GradientDrawable.OVAL);bg.setStroke(a.dp(a.readerTheme==n?3:1),a.readerTheme==n?a.accent:a.muted);option.addView(circle,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));circle.setBackground(bg);TextView caption=a.text(labels[n],11);caption.setGravity(Gravity.CENTER);option.addView(caption);option.setContentDescription(tr(labels[n]));option.setOnClickListener(v->{a.readerTheme=theme;applyAppearance();panel.dialog.dismiss();visualSettings();});colors.addView(option,new LinearLayout.LayoutParams(0,-2,1));}panel.content.addView(colors);
        if(a.kind.equals("pdf")){panel.section("Modo de leitura");toggle(panel,"Rolagem vertical",a.continuous,value->{int part=current();a.continuous=value;prefs.edit().putBoolean("reader_continuous",value).apply();a.currentPage=part;if(a.pageAdapter!=null){a.pageAdapter.notifyDataSetChanged();a.pageList.setSelection(value?part:0);}update();});panel.section("Ajustar página");LinearLayout fit=row();String[] fits={"Página","Largura","Altura"};for(int n=0;n<3;n++){final int mode=n;TextView choice=a.button(fits[n],()->{a.pdfFit=mode;prefs.edit().putInt("reader_fit",mode).apply();if(a.pageAdapter!=null)a.pageAdapter.notifyDataSetChanged();panel.dialog.dismiss();});UiBox.style(choice);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(a.dp(2),0,a.dp(2),0);fit.addView(choice,lp);}panel.content.addView(fit);}
        toggle(panel,"Luz nocturna",a.readerTheme==3,value->{a.readerTheme=value?3:0;applyAppearance();});
        toggle(panel,"Mostrar progresso de leitura",prefs.getBoolean("reader_show_progress",true),value->{prefs.edit().putBoolean("reader_show_progress",value).apply();update();});
        panel.section("Brilho");toggle(panel,"Brilho automático",prefs.getBoolean("reader_auto_brightness",true),value->{prefs.edit().putBoolean("reader_auto_brightness",value).apply();brightness();});
        slider(panel,5,100,prefs.getInt("reader_brightness",70),value->{prefs.edit().putInt("reader_brightness",value).putBoolean("reader_auto_brightness",false).apply();brightness();},"%");panel.show();
    }
    void brightness(){WindowManager.LayoutParams params=a.getWindow().getAttributes();params.screenBrightness=prefs.getBoolean("reader_auto_brightness",true)?WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE:prefs.getInt("reader_brightness",70)/100f;a.getWindow().setAttributes(params);}
    void settings(){UiPanel panel=new UiPanel(a,"Definições de leitura",null,"settings");toggle(panel,"Acompanhar leitura por voz",prefs.getBoolean("speech_follow",true),value->prefs.edit().putBoolean("speech_follow",value).apply());toggle(panel,"Manter ecrã ligado",prefs.getBoolean("reader_keep_awake",false),value->{prefs.edit().putBoolean("reader_keep_awake",value).apply();keepAwake();});panel.action("speaker","Voz e idioma",null,false,speech::show);panel.action("palette","Visualizar",null,false,this::visualSettings);panel.action("type","Texto",null,false,this::textSettings);panel.action("expand",a.fullScreen?"Reduzir para pré-visualização":"Expandir para ecrã inteiro",null,false,a::toggleFullScreen);panel.action("arrow","Abrir noutra aplicação",null,false,a::external);panel.show();}
    void keepAwake(){if(prefs.getBoolean("reader_keep_awake",false))a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void organize(){UiPanel panel=new UiPanel(a,"Organizar",null,"edit");panel.action("settings","Ferramentas de leitura",null,false,a.extras::show);if(a.kind.equals("epub"))panel.action("type","Ajustar leitura",null,false,this::textSettings);panel.action("bookmark","Adicionar marcador",null,false,()->a.tools.bookmark());panel.action("note","Anotações do documento",null,false,()->a.tools.documentAnnotations());panel.action("edit","Ferramentas da cópia de estudo",null,false,()->a.tools.menu());panel.action("folder","Exportar anotações",null,false,()->a.tools.exportAnnotations());panel.show();}
    public void changed(){
        if(a.dead||speechBar==null)return;SpeechEvents.State state=SpeechEvents.state;boolean matches=state.active&&a.uri.toString().equals(state.uri);speechBar.setVisibility(matches?View.VISIBLE:View.GONE);
        if(!matches){if(a.original!=null&&a.original.active)a.original.speechHighlight.update(state,false);if(a.pageList!=null)for(int i=0;i<a.pageList.getChildCount();i++){View view=a.pageList.getChildAt(i);if(view instanceof ReaderPageView)((ReaderPageView)view).speech(state,false);}lastSpeechPart=-1;if(a.bookText!=null&&spokenSpan!=null&&a.bookText.getText() instanceof Spannable)((Spannable)a.bookText.getText()).removeSpan(spokenSpan);spokenSpan=null;return;}((SpeechTransport)speechBar).changed();
        boolean userSelecting=pdfSelecting()||a.original!=null&&a.original.active&&a.original.view instanceof OriginalWebView&&((OriginalWebView)a.original.view).selecting||a.bookScroll!=null&&a.bookScroll.getParent()==a.body&&a.bookText!=null&&a.bookText.getSelectionStart()>=0&&a.bookText.getSelectionStart()!=a.bookText.getSelectionEnd();
        boolean follow=!userSelecting&&prefs.getBoolean("speech_follow",true)&&(!state.paused||state.message.startsWith("Salto aproximado"))&&a.resumed;
        if(follow&&lastSpeechPart!=state.part&&state.total>1&&state.part<count()){lastSpeechPart=state.part;if(state.part!=current())go(state.part);}
        if(a.original!=null&&a.original.active)a.original.speechHighlight.update(state,follow);
        if(a.pageList!=null)for(int n=0;n<a.pageList.getChildCount();n++){View child=a.pageList.getChildAt(n);if(child instanceof ReaderPageView)((ReaderPageView)child).speech(state,follow);}
        if(a.bookText!=null&&a.bookText.getText() instanceof Spannable){Spannable text=(Spannable)a.bookText.getText();if(spokenSpan!=null)text.removeSpan(spokenSpan);spokenSpan=null;
            if(!state.loading&&state.part==current()&&state.offset<text.length()){int start=Math.max(0,state.offset),end=Math.min(text.length(),state.end>start?state.end:start+1);if(state.end<=start)while(end<text.length()&&end-start<65&&!Character.isWhitespace(text.charAt(end)))end++;spokenSpan=new BackgroundColorSpan(0x80f5bc31);text.setSpan(spokenSpan,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);if(follow&&a.bookText.getLayout()!=null&&a.bookScroll!=null){int y=a.bookText.getLayout().getLineTop(a.bookText.getLayout().getLineForOffset(start));int top=a.bookScroll.getScrollY();if(y<top||y>top+a.bookScroll.getHeight()-a.dp(80))a.bookScroll.smoothScrollTo(0,Math.max(0,y-a.dp(60)));}}
        }
    }
    boolean pdfSelecting(){if(a.pageList!=null)for(int i=0;i<a.pageList.getChildCount();i++){View view=a.pageList.getChildAt(i);if(view instanceof ReaderPageView&&((ReaderPageView)view).selectionMode!=null)return true;}return false;}
    void close(){SpeechEvents.remove(this);speech.close();}

    /** Partilhar enquanto lê: o documento, a página actual do PDF como imagem, ou abrir noutra aplicação. */
    void share(){UiPanel p=new UiPanel(a,"Partilhar",a.name,"share");
        p.action(Icons.category(Rules.category(a.name,a.mime)),"Partilhar documento",AppLanguage.text(a,"Envia o ficheiro para WhatsApp, e-mail, Drive, Bluetooth…"),false,()->ShareTools.file(a,a.uri,a.name,a.mime));
        if("pdf".equals(a.kind))p.action("image","Partilhar esta página como imagem",AppLanguage.text(a,"Página")+" "+(a.currentPage+1),false,this::sharePage);
        p.note("Para partilhar só um trecho, seleccione o texto e escolha Partilhar selecção.");
        p.action("more","Abrir noutra aplicação",null,false,a::external);
        p.show();}

    void sharePage(){final int page=a.currentPage;PlusUi.toast(a,"A preparar a página…");a.worker.execute(()->{try{java.io.File local=a.local();try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(local,android.os.ParcelFileDescriptor.MODE_READ_ONLY);android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd);android.graphics.pdf.PdfRenderer.Page pg=pdf.openPage(Math.max(0,Math.min(page,pdf.getPageCount()-1)))){float scale=Math.min(3f,1800f/Math.max(pg.getWidth(),pg.getHeight()));android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(Math.max(1,Math.round(pg.getWidth()*scale)),Math.max(1,Math.round(pg.getHeight()*scale)),android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);pg.render(b,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);String base=a.name.replaceAll("\\.[^.]+$","");a.ui(()->ShareTools.bitmap(a,b,base+" - página "+(page+1)+".png",true));}}catch(Throwable e){a.ui(()->PlusUi.toast(a,"Não foi possível preparar esta página."));}});}
}
