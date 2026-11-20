package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.database.DatabaseUtils;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.json.*;

/** Tappable Bible citations in every reader and a quick passage reader above the page. */
final class BibleLinks {
    static final String PREFS="seeky";

    static boolean enabled(Context c){return c.getSharedPreferences(PREFS,0).getBoolean("bible_links",true);}
    static void enabled(Context c,boolean on){c.getSharedPreferences(PREFS,0).edit().putBoolean("bible_links",on).apply();}

    static void toggle(ReaderActivity a){
        boolean on=!enabled(a);enabled(a,on);
        if(a.bookText!=null&&a.bookText.getText() instanceof Spannable){Spannable text=(Spannable)a.bookText.getText();for(Link link:text.getSpans(0,text.length(),Link.class))text.removeSpan(link);if(on)bind(a);a.bookText.invalidate();}
        PlusUi.toast(a,on?"Ligações bíblicas activadas.":"Ligações bíblicas desactivadas.");
    }
    /** Text readers: TXT, Word and EPUB as text, PDF text pages and OCR pages. */
    static void bind(ReaderActivity a){if(a.bookText!=null)linkify(a,a.bookText,a.accent,a.worker,true);}

    /** Underlines every citation of a text view; a short tap opens the passage, long press still selects. */
    static void linkify(Activity a,TextView view,int color,Executor worker,boolean movement){
        if(view==null||!enabled(a))return;
        if(!(view.getText() instanceof Spannable))view.setText(view.getText(),TextView.BufferType.SPANNABLE);
        final Spannable shown=(Spannable)view.getText();final String text=shown.toString();
        if(text.isEmpty())return;
        worker.execute(()->{
            List<BibleReferences.Reference> refs=BibleReferences.references(text);
            if(refs.isEmpty())return;
            PlusUi.MAIN.post(()->{
                if(view.getText()!=shown||a.isFinishing())return;
                for(BibleReferences.Reference ref:refs)shown.setSpan(new Link(a,ref,color),ref.start,ref.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                view.setLinksClickable(true);
                if(movement)view.setMovementMethod(LinkMovementMethod.getInstance());
                view.setOnTouchListener(new BibleTouch(view));
            });
        });
    }
    static final class Link extends ClickableSpan {
        final Activity a;final BibleReferences.Reference ref;final int color;
        Link(Activity a,BibleReferences.Reference ref,int color){this.a=a;this.ref=ref;this.color=color;}
        @Override public void onClick(View v){open(a,ref);}
        @Override public void updateDrawState(TextPaint ds){ds.setColor(color);ds.setUnderlineText(true);}
    }

    /** Word (original layout) and EPUB pages: wraps citations, even across bold or italic runs. */
    static final String COLLECT="(function(){var root=document.getElementById('document')||document.body;if(!root)return [];"
        +"var w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,null,false);var nodes=[],out=[],n,total=0,last=null,id=-1;"
        +"while((n=w.nextNode())&&nodes.length<30000&&total<2000000){var p=n.parentNode;if(!p||!p.closest||p.closest('a,script,style,svg,math,head,title'))continue;"
        +"var b=p.closest('p,li,td,th,h1,h2,h3,h4,h5,h6,dd,dt,div,section,article,blockquote,pre,body');if(b!==last){id++;last=b;}"
        +"nodes.push(n);out.push([id,n.nodeValue]);total+=n.nodeValue.length;}window.__seekyBibleNodes=nodes;return out;})()";
    static final String APPLY="(function(list){var nodes=window.__seekyBibleNodes||[];window.__seekyBibleNodes=null;var by={},count=0;"
        +"for(var i=0;i<list.length;i++){var x=list[i];(by[x[0]]=by[x[0]]||[]).push(x);}"
        +"for(var k in by){var node=nodes[k];if(!node||!node.parentNode)continue;var items=by[k].sort(function(a,b){return b[1]-a[1];});"
        +"for(var j=0;j<items.length;j++){var x=items[j];try{var r=document.createRange();r.setStart(node,x[1]);r.setEnd(node,x[2]);"
        +"var a=document.createElement('a');a.href='seekybible://passage?ref='+encodeURIComponent(x[3]);a.className='seeky-bible-reference';"
        +"a.style.color='inherit';a.style.textDecoration='underline';a.style.textDecorationColor='__COLOR__';a.style.textDecorationThickness='2px';"
        +"r.surroundContents(a);count++;}catch(e){}}}return count;})(__DATA__)";
    static void linkWeb(ReaderActivity a,WebView view){
        if(view==null||!enabled(a))return;
        view.evaluateJavascript(COLLECT,value->{
            if(value==null||!value.startsWith("["))return;
            a.worker.execute(()->{
                String script=plan(value,a.accent);
                if(script!=null)a.ui(()->{if(view.getParent()!=null)view.evaluateJavascript(script,null);});
            });
        });
    }
    /** Groups text nodes per paragraph, finds citations and returns the wrapping script (null when none). */
    static String plan(String json,int color){
        try{
            JSONArray nodes=new JSONArray(json),found=new JSONArray();
            int i=0;
            while(i<nodes.length()){
                int block=nodes.getJSONArray(i).getInt(0),j=i;
                StringBuilder text=new StringBuilder();List<int[]> map=new ArrayList<>();boolean digits=false;
                while(j<nodes.length()&&nodes.getJSONArray(j).getInt(0)==block){
                    String value=nodes.getJSONArray(j).getString(1);map.add(new int[]{j,text.length(),value.length()});text.append(value);
                    for(int c=0;c<value.length()&&!digits;c++)if(value.charAt(c)>='1'&&value.charAt(c)<='9')digits=true;
                    j++;
                }
                if(digits)for(BibleReferences.Reference r:BibleReferences.references(text.toString()))for(int[] m:map){
                    int from=Math.max(r.start,m[1]),to=Math.min(r.end,m[1]+m[2]);
                    if(to>from)found.put(new JSONArray().put(m[0]).put(from-m[1]).put(to-m[1]).put(r.label));
                }
                i=j;
            }
            if(found.length()==0)return null;
            return APPLY.replace("__COLOR__",String.format(Locale.ROOT,"#%06x",color&0xffffff)).replace("__DATA__",found.toString());
        }catch(JSONException e){return null;}
    }

    static void query(Activity a){
        EditText input=PlusUi.input(a,"Ex.: Isaías 3.1-7, 12 ou Jo 3:16","",160);
        UiDialog dialog=new UiDialog.Builder(a).setTitle("Consultar passagem bíblica").setView(input).setPositiveButton("Abrir",null).setNeutralButton("Guardadas",(d,w)->saved(a)).setNegativeButton("Voltar à leitura",null).create();
        dialog.show();
        dialog.getButton(-1).setOnClickListener(v->{BibleReferences.Reference ref=BibleReferences.lookup(input.getText().toString());if(ref==null){input.setError(AppLanguage.text(a,"Indique o livro, o capítulo e, se quiser, os versículos."));return;}dialog.dismiss();open(a,ref);});
    }
    static void open(Activity a,BibleReferences.Reference ref){if(ref!=null)new Passage(a,ref,-1).load();}
    static void passage(Activity a,StudyStore.Row source,BibleReferences.Reference ref){if(ref!=null)new Passage(a,ref,source.id()).load();}
    static void list(ReaderActivity a){a.worker.execute(()->{try{String text=a.tools!=null&&a.tools.draft!=null?a.tools.draft.text:"";if(text.isEmpty())try(SpeechDocument d=new SpeechDocument(a,a.uri,a.name,a.mime,()->a.dead)){text=d.part(a.chrome.current());}List<BibleReferences.Reference> refs=BibleReferences.references(text);a.ui(()->choose(a,refs));}catch(Exception e){a.ui(()->PlusUi.error(a,e));}});}
    /** Lists the citations of a page; one citation opens directly. */
    static void choose(Activity a,List<BibleReferences.Reference> refs){
        if(refs.isEmpty()){PlusUi.toast(a,"Nenhuma referência bíblica reconhecida nesta parte.");return;}
        if(refs.size()==1){open(a,refs.get(0));return;}
        List<String> names=new ArrayList<>();List<BibleReferences.Reference> unique=new ArrayList<>();
        for(BibleReferences.Reference r:refs)if(!names.contains(r.title)){names.add(r.title);unique.add(r);}
        new UiDialog.Builder(a).setTitle("Passagens bíblicas").setItems(names.toArray(new String[0]),(d,n)->open(a,unique.get(n))).setNegativeButton("Fechar",null).show();
    }

    static List<JSONObject> savedItems(Context c){List<JSONObject> out=new ArrayList<>();try{JSONArray all=new JSONArray(c.getSharedPreferences(PREFS,0).getString("bible_saved","[]"));for(int i=0;i<all.length();i++)out.add(all.getJSONObject(i));}catch(JSONException ignored){}return out;}
    static void save(Context c,BibleReferences.Reference ref,String version,String text){
        try{
            JSONArray next=new JSONArray().put(new JSONObject().put("title",ref.title).put("version",version).put("text",text.length()>400?text.substring(0,400)+"…":text).put("at",System.currentTimeMillis()));
            for(JSONObject old:savedItems(c))if(next.length()<300&&!old.optString("title").equals(ref.title))next.put(old);
            c.getSharedPreferences(PREFS,0).edit().putString("bible_saved",next.toString()).apply();
        }catch(JSONException ignored){}
    }
    static void saved(Activity a){
        List<JSONObject> items=savedItems(a);
        if(items.isEmpty()){PlusUi.toast(a,"Ainda não guardou passagens. Use Guardar na leitura rápida.");return;}
        String[] names=new String[items.size()];for(int i=0;i<names.length;i++){JSONObject o=items.get(i);names[i]=o.optString("title")+(o.optString("version").isEmpty()?"":" · "+o.optString("version"));}
        new UiDialog.Builder(a).setTitle("Passagens guardadas").setItems(names,(d,n)->open(a,BibleReferences.lookup(items.get(n).optString("title")))).setNeutralButton("Limpar lista",(d,w)->new UiDialog.Builder(a).setTitle("Limpar passagens guardadas?").setPositiveButton("Limpar",(x,y)->a.getSharedPreferences(PREFS,0).edit().remove("bible_saved").apply()).setNegativeButton("Cancelar",null).show()).setNegativeButton("Fechar",null).show();
    }

    /** Quick reader: the passage in place, with versions, whole chapter, copy, share, listen and save. */
    static final class Passage {
        final Activity a;final BibleReferences.Reference ref;final AppPalette colors;
        long source;boolean whole,compare;int chapter,lastChapter,serial;
        List<StudyStore.Row> sources=Collections.emptyList();
        UiDialog dialog;LinearLayout versions,navigation;TextView meta,body,wholeLabel,voiceLabel,compareChip;
        String plain="",speakable="",version="";
        TextToSpeech voice;

        Passage(Activity a,BibleReferences.Reference ref,long source){
            this.a=a;this.ref=ref;colors=AppPalette.of(a);
            this.source=source>0?source:a.getSharedPreferences(PREFS,0).getLong("bible_quick_source",-1);
            whole=!ref.explicit&&ref.chapter==ref.lastChapter;chapter=ref.chapter;
        }
        int dp(float n){return PlusUi.dp(a,n);}
        List<int[]> ranges(){return whole?Collections.singletonList(new int[]{chapter,1,chapter,BibleReferences.WHOLE}):ref.ranges;}
        static List<StudyStore.Row> verses(PlusStore db,long source,String book,List<int[]> ranges){
            StringBuilder where=new StringBuilder("source=? AND book=? AND (");List<String> args=new ArrayList<>();args.add(""+source);args.add(book);
            for(int n=0;n<ranges.size();n++){int[] range=ranges.get(n);if(n>0)where.append(" OR ");where.append("((chapter>? OR (chapter=? AND verse>=?)) AND (chapter<? OR (chapter=? AND verse<=?)))");for(int number:new int[]{range[0],range[0],range[1],range[2],range[2],range[3]})args.add(""+number);}
            where.append(')');
            return db.rows("bible_verses",where.toString(),args.toArray(new String[0]),"chapter,verse",501);
        }
        void load(){
            final int token=++serial;final long wanted=source;final boolean all=compare;final List<int[]> ranges=new ArrayList<>(ranges());
            PlusUi.run(a,()->{
                List<StudyStore.Row> found;Map<Long,List<StudyStore.Row>> passages=new LinkedHashMap<>();long chosen=0,max=0;
                try(PlusStore db=new PlusStore(a)){
                    found=db.rows("bible_sources",null,null,"title COLLATE NOCASE",200);
                    for(StudyStore.Row s:found)if(s.id()==wanted)chosen=wanted;
                    if(chosen==0&&!found.isEmpty())chosen=found.get(0).id();
                    if(chosen>0){
                        passages.put(chosen,verses(db,chosen,ref.book,ranges));
                        if(all)for(StudyStore.Row s:found)if(s.id()!=chosen&&passages.size()<6)passages.put(s.id(),verses(db,s.id(),ref.book,ranges));
                        max=DatabaseUtils.longForQuery(db.getReadableDatabase(),"SELECT IFNULL(MAX(chapter),0) FROM bible_verses WHERE source=? AND book=?",new String[]{""+chosen,ref.book});
                    }
                }
                final long picked=chosen;final int last=(int)max;
                PlusUi.post(a,()->{if(token==serial&&!a.isFinishing())show(found,picked,passages,last);});
            });
        }
        String name(long id){for(StudyStore.Row s:sources)if(s.id()==id)return s.s("title");return "";}
        void show(List<StudyStore.Row> found,long picked,Map<Long,List<StudyStore.Row>> passages,int last){
            if(found.isEmpty()){if(dialog!=null)dialog.dismiss();missing();return;}
            sources=found;source=picked;lastChapter=last;version=name(picked);
            SpannableStringBuilder out=new SpannableStringBuilder();StringBuilder copy=new StringBuilder(),speak=new StringBuilder();int count=0;
            copy.append(whole?ref.book+" "+chapter:ref.title).append(compare?"":" ("+version+")").append('\n');
            for(Map.Entry<Long,List<StudyStore.Row>> e:passages.entrySet()){
                List<StudyStore.Row> rows=e.getValue();
                if(compare){if(out.length()>0){out.append('\n');copy.append('\n');}heading(out,name(e.getKey()),1.05f);copy.append(name(e.getKey())).append('\n');}
                if(rows.isEmpty()){int at=out.length();out.append(AppLanguage.text(a,whole?"Este capítulo não consta desta Bíblia.":"Esta passagem não consta desta Bíblia.")).append('\n');out.setSpan(new ForegroundColorSpan(colors.muted),at,out.length(),0);continue;}
                boolean chapters=whole||rows.get(0).n("chapter")!=rows.get(rows.size()-1).n("chapter");int shown=-1;
                for(StudyStore.Row row:rows.subList(0,Math.min(500,rows.size()))){
                    int c=(int)row.n("chapter"),v=(int)row.n("verse");String value=row.s("value").trim();
                    if(chapters&&c!=shown&&!whole){heading(out,AppLanguage.text(a,"Capítulo")+" "+c,.95f);shown=c;}
                    int start=out.length();
                    out.append(String.valueOf(v));out.setSpan(new StyleSpan(Typeface.BOLD),start,out.length(),0);out.setSpan(new ForegroundColorSpan(colors.accent),start,out.length(),0);out.setSpan(new RelativeSizeSpan(.78f),start,out.length(),0);
                    out.append(' ').append(value);
                    if(whole&&ref.explicit&&ref.contains(c,v))out.setSpan(new BackgroundColorSpan((colors.accent&0xffffff)|0x2e000000),start,out.length(),0);
                    out.append('\n');
                    copy.append(chapters&&!whole?c+":"+v:""+v).append(' ').append(value).append('\n');
                    if(e.getKey()==picked){speak.append(value).append('\n');count++;}
                }
                if(rows.size()>500){int at=out.length();out.append(AppLanguage.text(a,"Mostrados 500 versículos. Consulte um intervalo menor.")).append('\n');out.setSpan(new ForegroundColorSpan(colors.muted),at,out.length(),0);}
            }
            while(out.length()>0&&out.charAt(out.length()-1)=='\n')out.delete(out.length()-1,out.length());
            plain=copy.toString().trim();speakable=speak.toString();
            String detail=compare?AppLanguage.text(a,"Comparação de")+" "+passages.size()+" "+AppLanguage.text(a,"versões"):version+" · "+(whole?AppLanguage.text(a,"capítulo")+" "+chapter+" "+AppLanguage.text(a,"completo"):count+" "+AppLanguage.text(a,count==1?"versículo":"versículos"));
            if(dialog==null)build();
            meta.setText(detail);body.setText(out);
            fillVersions();
            wholeLabel.setText(AppLanguage.text(a,whole?"Só a passagem":"Capítulo inteiro"));
            ((View)wholeLabel.getParent()).setVisibility(ref.explicit?View.VISIBLE:View.GONE);
            compareChip.setText(AppLanguage.text(a,compare?"Uma versão":"Comparar versões"));
            ((View)compareChip.getParent()).setVisibility(sources.size()>1?View.VISIBLE:View.GONE);
            fillNavigation();
            if(dialog.isShowing())dialog.refit();else dialog.show();
        }
        void heading(SpannableStringBuilder out,String text,float size){int at=out.length();out.append(text).append('\n');out.setSpan(new StyleSpan(Typeface.BOLD),at,out.length(),0);out.setSpan(new RelativeSizeSpan(size),at,out.length(),0);out.setSpan(new ForegroundColorSpan(colors.ink),at,out.length(),0);}
        void build(){
            LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);
            versions=new LinearLayout(a);versions.setGravity(Gravity.CENTER_VERTICAL);
            HorizontalScrollView versionScroll=new HorizontalScrollView(a);versionScroll.setHorizontalScrollBarEnabled(false);versionScroll.addView(versions);
            LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.bottomMargin=dp(8);root.addView(versionScroll,vp);
            LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);
            HorizontalScrollView actionScroll=new HorizontalScrollView(a);actionScroll.setHorizontalScrollBarEnabled(false);actionScroll.addView(actions);
            root.addView(actionScroll,new LinearLayout.LayoutParams(-1,-2));
            chip(actions,"copy","Copiar",false,this::copy);
            chip(actions,"share","Partilhar",false,()->ShareTools.text(a,plain));
            wholeLabel=chip(actions,"book","Capítulo inteiro",false,()->{whole=!whole;chapter=whole?ref.chapter:chapter;load();});
            voiceLabel=chip(actions,"speaker","Ouvir",false,this::listen);
            compareChip=chip(actions,"collection","Comparar versões",false,()->{compare=!compare;load();});
            chip(actions,"bookmark","Guardar",false,()->{save(a,ref,version,plain);PlusUi.toast(a,"Passagem guardada. Veja em Consultar passagem → Guardadas.");});
            chip(actions,null,"A−",false,()->size(-1));
            chip(actions,null,"A+",false,()->size(1));
            meta=new TextView(a);meta.setTextSize(13);meta.setTextColor(colors.muted);
            root.addView(meta,new LinearLayout.LayoutParams(-1,-2));
            body=new TextView(a);body.setTextColor(colors.ink);body.setTextSize(a.getSharedPreferences(PREFS,0).getFloat("bible_quick_size",17));
            body.setLineSpacing(dp(4),1.15f);body.setTextIsSelectable(true);body.setPadding(dp(2),dp(2),dp(2),dp(4));
            root.addView(body,new LinearLayout.LayoutParams(-1,-2));
            navigation=new LinearLayout(a);navigation.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(10);root.addView(navigation,np);
            dialog=new UiDialog.Builder(a).setTitle(ref.title).setView(root).setPositiveButton("Voltar à leitura",null).setOnDismissListener(d->quiet()).create();
        }
        TextView chip(LinearLayout row,String icon,String label,boolean selected,Runnable action){
            LinearLayout c=new LinearLayout(a);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(icon==null?14:11),0,dp(14),0);
            GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(19));bg.setColor(selected?colors.accent:colors.tint);c.setBackground(bg);
            if(icon!=null)c.addView(new Icons(a,icon,selected?colors.onAccent:colors.accent),new LinearLayout.LayoutParams(dp(18),dp(18)));
            TextView t=new TextView(a);t.setText(AppLanguage.text(a,label));t.setTextSize(14);t.setTypeface(ThemeStyle.BOLD);t.setTextColor(selected?colors.onAccent:colors.ink);t.setSingleLine(true);
            t.setPadding(icon==null?0:dp(7),0,0,0);c.addView(t,new LinearLayout.LayoutParams(-2,-2));
            c.setContentDescription(AppLanguage.text(a,label));c.setOnClickListener(v->action.run());Motion.touch(c);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.rightMargin=dp(8);row.addView(c,lp);
            return t;
        }
        void fillVersions(){
            versions.removeAllViews();
            ((View)versions.getParent()).setVisibility(sources.size()>1?View.VISIBLE:View.GONE);
            if(sources.size()<2)return;
            for(StudyStore.Row s:sources){final long id=s.id();chip(versions,id==source&&!compare?"check":null,s.s("title"),id==source,()->{source=id;compare=false;a.getSharedPreferences(PREFS,0).edit().putLong("bible_quick_source",id).apply();load();});}
        }
        void fillNavigation(){
            navigation.removeAllViews();
            if(!whole)return;
            if(chapter>1)chip(navigation,"back","Capítulo "+(chapter-1),false,()->{chapter--;load();});
            if(chapter<lastChapter)chip(navigation,"arrow","Capítulo "+(chapter+1),false,()->{chapter++;load();});
        }
        void copy(){((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(ref.title,plain));PlusUi.toast(a,"Passagem copiada.");}
        void size(int step){float next=Math.max(13,Math.min(28,body.getTextSize()/a.getResources().getDisplayMetrics().scaledDensity+step*1.5f));body.setTextSize(next);a.getSharedPreferences(PREFS,0).edit().putFloat("bible_quick_size",next).apply();dialog.refit();}
        void listen(){
            if(voice!=null){quiet();return;}
            if(speakable.trim().isEmpty())return;
            voiceLabel.setText(AppLanguage.text(a,"Parar"));
            final String[] lines=speakable.split("\n");
            voice=SpeechEngines.create(a,status->{
                TextToSpeech engine=voice;if(engine==null)return;
                if(status!=TextToSpeech.SUCCESS){PlusUi.post(a,()->{PlusUi.toast(a,"A voz do sistema não está disponível.");quiet();});return;}
                engine.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){if(("verse"+(lines.length-1)).equals(id))PlusUi.post(a,()->quiet());}public void onError(String id){PlusUi.post(a,()->quiet());}});
                for(int i=0;i<lines.length;i++)if(!lines[i].trim().isEmpty()||i==lines.length-1)engine.speak(lines[i],TextToSpeech.QUEUE_ADD,null,"verse"+i);
            },null);
        }
        void quiet(){TextToSpeech engine=voice;voice=null;if(engine!=null){try{engine.stop();engine.shutdown();}catch(RuntimeException ignored){}}if(voiceLabel!=null)voiceLabel.setText(AppLanguage.text(a,"Ouvir"));}
        void missing(){
            UiPanel panel=new UiPanel(a,ref.title,"Importe uma Bíblia uma vez para ler as passagens aqui mesmo, sem sair do livro.","book");
            panel.action("globe","Descarregar uma Bíblia","Mais de 1500 versões (eBible.org)",false,()->BibleCatalog.show(a));
            panel.action("add","Importar ficheiro bíblico","MyBible, USX, ZIP, TXT, EPUB ou PDF",false,()->ToolsActivity.open(a,"bibles"));
            panel.action("copy","Copiar referência",ref.title,false,()->{((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(ref.title,ref.title));PlusUi.toast(a,"Referência copiada.");});
            panel.show();
        }
    }
    private BibleLinks(){}
}
