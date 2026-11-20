package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

/**
 * "As minhas marcações": highlights, underlines, strike-throughs and notes of every book as readable
 * quote cards, by date or grouped by book, with search, type and colour filters and quick actions.
 */
public final class HighlightsActivity extends Activity {
    Screens s;List<Highlights.Entry> all=new ArrayList<>();final List<Object> rows=new ArrayList<>();
    String filter="all",colour=null,query="",only;boolean byBook;
    TextView count,sort,empty;ListView list;Adapter adapter;LinearLayout types,colours;EditText search;View clear;

    static void open(Context c,String uri){Intent i=new Intent(c,HighlightsActivity.class);if(uri!=null)i.putExtra("uri",uri);if(!(c instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        s=new Screens(this);only=getIntent().getStringExtra("uri");byBook=getSharedPreferences("seeky",0).getBoolean("marks_by_book",false);
        LinearLayout root=s.column();root.setBackgroundColor(s.p.bg);root.setPadding(s.dp(16),s.dp(10),s.dp(16),0);
        root.addView(s.header(only==null?"As minhas marcações":"Marcações deste livro",null,this::finish));

        LinearLayout searchBox=s.row();searchBox.setBackground(s.round(s.p.surface,16));searchBox.setPadding(s.dp(14),0,s.dp(6),0);
        searchBox.addView(new Icons(this,"search",s.p.muted),new LinearLayout.LayoutParams(s.dp(20),s.dp(20)));
        search=new EditText(this);search.setHint(AppLanguage.text(this,"Procurar texto, nota ou livro"));search.setSingleLine(true);search.setTextSize(15);search.setTextColor(s.p.ink);search.setHintTextColor(s.p.muted);search.setBackground(null);search.setPadding(s.dp(10),0,s.dp(4),0);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c,int d){}public void onTextChanged(CharSequence a,int b,int c,int d){}public void afterTextChanged(Editable e){query=e.toString();clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);refresh();}});
        searchBox.addView(search,new LinearLayout.LayoutParams(0,s.dp(48),1));
        FrameLayout x=new FrameLayout(this);x.addView(new Icons(this,"close",s.p.muted),new FrameLayout.LayoutParams(s.dp(18),s.dp(18),Gravity.CENTER));x.setContentDescription(AppLanguage.text(this,"Limpar pesquisa"));x.setOnClickListener(v->search.setText(""));x.setVisibility(View.GONE);clear=x;
        searchBox.addView(x,new LinearLayout.LayoutParams(s.dp(40),s.dp(40)));
        root.addView(searchBox,new LinearLayout.LayoutParams(-1,s.dp(48)));

        types=s.row();root.addView(s.scroller(types),s.gap(10));
        colours=s.row();root.addView(colours,s.gap(8));

        LinearLayout status=s.row();status.setPadding(s.dp(2),s.dp(8),0,s.dp(6));
        count=s.text("",13,s.p.muted,false);status.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        sort=s.text("",13,s.p.accent,true);sort.setPadding(s.dp(10),s.dp(6),s.dp(4),s.dp(6));sort.setOnClickListener(v->{byBook=!byBook;getSharedPreferences("seeky",0).edit().putBoolean("marks_by_book",byBook).apply();refresh();});
        status.addView(sort);
        FrameLayout share=new FrameLayout(this);share.setBackground(s.round(s.p.tint,16));share.addView(new Icons(this,"share",s.p.accent),new FrameLayout.LayoutParams(s.dp(16),s.dp(16),Gravity.CENTER));
        share.setContentDescription(AppLanguage.text(this,"Partilhar todas as marcações"));share.setOnClickListener(v->export());Motion.touch(share);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(s.dp(32),s.dp(32));sp.leftMargin=s.dp(4);status.addView(share,sp);
        root.addView(status);

        list=new ListView(this);list.setDivider(null);list.setDividerHeight(s.dp(10));list.setClipToPadding(false);list.setPadding(0,0,0,s.dp(24));list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        adapter=new Adapter();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->{Object row=rows.get(pos);if(row instanceof Highlights.Entry)Highlights.view(this,(Highlights.Entry)row,this::load);});
        list.setOnItemLongClickListener((p,v,pos,id)->{Object row=rows.get(pos);if(row instanceof Highlights.Entry){Highlights.open(this,(Highlights.Entry)row);return true;}return false;});
        FrameLayout body=new FrameLayout(this);body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=s.text("",15,s.p.muted,false);empty.setGravity(Gravity.CENTER);empty.setPadding(s.dp(24),s.dp(48),s.dp(24),0);body.addView(empty,new FrameLayout.LayoutParams(-1,-2));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);getWindow().setStatusBarColor(s.p.bg);getWindow().setNavigationBarColor(s.p.bg);
        buildFilters();refresh();
    }
    @Override protected void onResume(){super.onResume();load();}
    void load(){PlusUi.run(this,()->{List<Highlights.Entry> found=Highlights.all(this,only);PlusUi.post(this,()->{all=found;if(colour!=null){boolean used=false;int ci=java.util.Arrays.asList(Highlights.KINDS).indexOf(colour);for(Highlights.Entry e:all)used|=Highlights.colourIndex(e.kind)==ci;if(!used)colour=null;}buildFilters();refresh();});});}

    void buildFilters(){
        types.removeAllViews();
        String[][] kinds={{"all","Todas"},{"color","Destaques"},{"underline","Sublinhados"},{"squiggle","Ondulados"},{"strike","Riscados"},{"note","Notas"}};
        for(String[] k:kinds){
            int n=0;for(Highlights.Entry e:all)if(matches(e,k[0]))n++;
            if(n==0&&!k[0].equals("all")&&!filter.equals(k[0]))continue;
            final String key=k[0];
            s.addChip(types,s.chip(null,AppLanguage.text(this,k[1])+" "+n,filter.equals(key),()->{filter=key;buildFilters();refresh();}));
        }
        colours.removeAllViews();
        for(int i=0;i<Highlights.KINDS.length;i++){
            final String key=Highlights.KINDS[i];final int index=i;int n=0;for(Highlights.Entry e:all)if(Highlights.colourIndex(e.kind)==index&&!Highlights.style(e.kind).equals("note"))n++;if(n==0)continue;
            boolean on=key.equals(colour);
            FrameLayout dot=new FrameLayout(this);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Highlights.COLORS[i]);g.setStroke(s.dp(on?3:1),on?s.p.accent:(s.p.ink&0xffffff)|0x33000000);dot.setBackground(g);
            if(on)dot.addView(new Icons(this,"check",0xff272237),new FrameLayout.LayoutParams(s.dp(16),s.dp(16),Gravity.CENTER));
            dot.setContentDescription(AppLanguage.text(this,Highlights.NAMES[i])+" "+n);dot.setOnClickListener(v->{colour=on?null:key;buildFilters();refresh();});Motion.touch(dot);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(s.dp(30),s.dp(30));lp.rightMargin=s.dp(10);colours.addView(dot,lp);
        }
        colours.setVisibility(colours.getChildCount()>1||colour!=null?View.VISIBLE:View.GONE);
    }
    boolean matches(Highlights.Entry e,String f){if(f.equals("all"))return true;String st=Highlights.style(e.kind);if(f.equals("color"))return st.equals("highlight");return st.equals(f);}

    void refresh(){
        String q=BibleStudyCore.plain(query);
        List<Highlights.Entry> shown=new ArrayList<>();
        for(Highlights.Entry e:all){
            if(!matches(e,filter)||colour!=null&&(Highlights.colourIndex(e.kind)!=java.util.Arrays.asList(Highlights.KINDS).indexOf(colour)||Highlights.style(e.kind).equals("note")))continue;
            if(!q.isEmpty()&&!BibleStudyCore.plain(e.text+" "+e.note+" "+e.name).contains(q))continue;
            shown.add(e);
        }
        rows.clear();Set<String> books=new LinkedHashSet<>();for(Highlights.Entry e:shown)books.add(e.uri);
        if(byBook){
            Map<String,List<Highlights.Entry>> groups=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for(Highlights.Entry e:shown){List<Highlights.Entry> g=groups.get(e.name+"\u0000"+e.uri);if(g==null)groups.put(e.name+"\u0000"+e.uri,g=new ArrayList<>());g.add(e);}
            for(List<Highlights.Entry> g:groups.values()){Collections.sort(g,(x,y)->x.part!=y.part?Integer.compare(x.part,y.part):Integer.compare(x.start,y.start));rows.add(new String[]{g.get(0).name,""+g.size()});rows.addAll(g);}
        }else rows.addAll(shown);
        count.setText(shown.size()+" "+AppLanguage.text(this,shown.size()==1?"marcação":"marcações")+" · "+books.size()+" "+AppLanguage.text(this,books.size()==1?"livro":"livros"));
        sort.setText("⇅ "+AppLanguage.text(this,byBook?"Por livro":"Recentes"));sort.setContentDescription(AppLanguage.text(this,byBook?"Ordenar por data":"Agrupar por livro"));
        if(all.isEmpty())empty.setText(AppLanguage.text(this,"Ainda não tem marcações.")+"\n\n"+AppLanguage.text(this,"No leitor do Seeky, seleccione um texto e toque numa cor da barra de marcar. Funciona em PDF, EPUB, Word e texto."));
        else empty.setText(AppLanguage.text(this,"Nenhuma marcação com este filtro."));
        empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
        adapter.notifyDataSetChanged();
    }
    void export(){
        List<Highlights.Entry> shown=new ArrayList<>();for(Object r:rows)if(r instanceof Highlights.Entry)shown.add((Highlights.Entry)r);
        if(shown.isEmpty()){PlusUi.toast(this,"Não há marcações para partilhar.");return;}
        List<Highlights.Entry> sorted=new ArrayList<>(shown);Collections.sort(sorted,(x,y)->x.name.compareToIgnoreCase(y.name)!=0?x.name.compareToIgnoreCase(y.name):x.part!=y.part?Integer.compare(x.part,y.part):Integer.compare(x.start,y.start));
        StringBuilder out=new StringBuilder();String book="";
        for(Highlights.Entry e:sorted){if(!e.name.equals(book)){book=e.name;out.append(out.length()>0?"\n":"").append("■ ").append(book).append('\n');}out.append("• ").append(e.where()).append(" — “").append(e.text).append("”");if(!e.note.isEmpty())out.append("\n  ✎ ").append(e.note);out.append('\n');}
        ShareTools.text(this,out.toString());
    }
    void copy(Highlights.Entry e){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(e.name,Highlights.share(e)));PlusUi.toast(this,"Texto copiado.");}

    View action(String icon,String label,Runnable run){
        LinearLayout b=s.row();b.setPadding(s.dp(10),0,s.dp(12),0);b.setBackground(s.round(s.p.tint,15));
        b.addView(new Icons(this,icon,s.p.accent),new LinearLayout.LayoutParams(s.dp(16),s.dp(16)));
        if(label!=null){TextView t=s.text(label,13,s.p.ink,true);t.setPadding(s.dp(6),0,0,0);b.addView(t);}
        b.setContentDescription(AppLanguage.text(this,label==null?icon:label));b.setOnClickListener(v->run.run());b.setFocusable(false);Motion.touch(b);
        return b;
    }
    final class Adapter extends BaseAdapter {
        public int getCount(){return rows.size();}
        public Object getItem(int p){return rows.get(p);}
        public long getItemId(int p){return p;}
        public int getViewTypeCount(){return 2;}
        public int getItemViewType(int p){return rows.get(p) instanceof Highlights.Entry?1:0;}
        public boolean isEnabled(int p){return rows.get(p) instanceof Highlights.Entry;}
        public View getView(int position,View recycled,ViewGroup parent){
            Object row=rows.get(position);
            if(!(row instanceof Highlights.Entry)){
                String[] h=(String[])row;LinearLayout head=s.row();head.setPadding(s.dp(2),s.dp(position==0?2:14),0,s.dp(2));
                FrameLayout tile=new FrameLayout(HighlightsActivity.this);tile.setBackground(s.round(s.p.tint,10));tile.addView(new Icons(HighlightsActivity.this,"book",s.p.accent),new FrameLayout.LayoutParams(s.dp(18),s.dp(18),Gravity.CENTER));
                head.addView(tile,new LinearLayout.LayoutParams(s.dp(32),s.dp(32)));
                TextView name=s.text(h[0],15,s.p.ink,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.MIDDLE);name.setPadding(s.dp(10),0,s.dp(8),0);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                head.addView(s.text(h[1],13,s.p.muted,true));
                return head;
            }
            Highlights.Entry e=(Highlights.Entry)row;
            LinearLayout card=s.column();card.setBackground(s.round(s.p.surface,20));card.setPadding(s.dp(14),s.dp(12),s.dp(14),s.dp(12));
            LinearLayout top=s.row();top.addView(Highlights.pill(s,e));
            TextView where=s.text(e.where(),12.5f,s.p.muted,true);where.setGravity(Gravity.END);top.addView(where,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(top);
            card.addView(Highlights.quote(s,e,5,16),s.gap(10));
            if(!e.note.isEmpty()){
                LinearLayout note=s.row();note.setGravity(Gravity.TOP);note.setPadding(s.dp(2),s.dp(8),0,0);
                note.addView(new Icons(HighlightsActivity.this,"note",0xff2f80ed),new LinearLayout.LayoutParams(s.dp(16),s.dp(16)));
                TextView n=s.text(e.note,14,s.p.ink,false);n.setMaxLines(3);n.setEllipsize(TextUtils.TruncateAt.END);n.setPadding(s.dp(8),0,0,0);note.addView(n,new LinearLayout.LayoutParams(0,-2,1));
                card.addView(note);
            }
            LinearLayout bottom=s.row();bottom.setPadding(0,s.dp(10),0,0);
            String meta=(byBook?"":e.name+" · ")+(e.created>0?DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(e.created)):"");
            TextView m=s.text(meta,12,s.p.muted,false);m.setMaxLines(1);m.setEllipsize(TextUtils.TruncateAt.MIDDLE);bottom.addView(m,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,s.dp(32));ap.leftMargin=s.dp(6);
            bottom.addView(action("copy",null,()->copy(e)),new LinearLayout.LayoutParams(s.dp(38),s.dp(32)));
            bottom.addView(action("share",null,()->ShareTools.text(HighlightsActivity.this,Highlights.share(e))),apWidth(38));
            bottom.addView(action("book",AppLanguage.text(HighlightsActivity.this,"Abrir"),()->Highlights.open(HighlightsActivity.this,e)),ap);
            card.addView(bottom);
            card.setContentDescription(AppLanguage.text(HighlightsActivity.this,Highlights.label(e.kind))+": "+e.text);
            return card;
        }
        LinearLayout.LayoutParams apWidth(int w){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(s.dp(w),s.dp(32));lp.leftMargin=s.dp(6);return lp;}
    }
}
