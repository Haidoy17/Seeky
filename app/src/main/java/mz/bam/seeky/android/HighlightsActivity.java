package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

/** "As minhas marcações": every highlight, underline, strike and note, with search and a jump back into the book. */
public final class HighlightsActivity extends Activity {
    Screens s;List<Highlights.Entry> all=new ArrayList<>(),shown=new ArrayList<>();
    String filter="all",query="",only;
    TextView count,empty;ListView list;Adapter adapter;LinearLayout filters;

    static void open(Context c,String uri){Intent i=new Intent(c,HighlightsActivity.class);if(uri!=null)i.putExtra("uri",uri);if(!(c instanceof Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}

    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        s=new Screens(this);only=getIntent().getStringExtra("uri");
        LinearLayout root=s.column();root.setBackgroundColor(s.p.bg);root.setPadding(s.dp(16),s.dp(10),s.dp(16),0);
        root.addView(s.header(only==null?"As minhas marcações":"Marcações deste livro","Destaques, sublinhados e notas de todos os livros",this::finish));
        EditText search=new EditText(this);search.setHint(AppLanguage.text(this,"Procurar nas marcações"));search.setSingleLine(true);search.setTextSize(15);search.setTextColor(s.p.ink);search.setHintTextColor(s.p.muted);
        search.setBackground(s.round(s.p.surface,16));search.setPadding(s.dp(14),s.dp(10),s.dp(14),s.dp(10));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c,int d){}public void onTextChanged(CharSequence a,int b,int c,int d){}public void afterTextChanged(Editable e){query=e.toString();refresh();}});
        root.addView(search,new LinearLayout.LayoutParams(-1,s.dp(48)));
        filters=s.row();root.addView(s.scroller(filters),s.gap(10));
        count=s.text("",13,s.p.muted,false);count.setPadding(s.dp(4),s.dp(10),0,s.dp(6));root.addView(count);
        list=new ListView(this);list.setDivider(null);list.setDividerHeight(s.dp(8));list.setClipToPadding(false);list.setPadding(0,0,0,s.dp(24));
        adapter=new Adapter();list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,pos,id)->Highlights.view(this,shown.get(pos),this::load));
        list.setOnItemLongClickListener((p,v,pos,id)->{Highlights.open(this,shown.get(pos));return true;});
        FrameLayout body=new FrameLayout(this);body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=s.text("Ainda não tem marcações.\n\nNo leitor, seleccione um texto e escolha Destacar, Sublinhar ou Cores e mais marcas. Também funciona nas páginas PDF.",15,s.p.muted,false);
        empty.setGravity(Gravity.CENTER);empty.setPadding(s.dp(24),s.dp(40),s.dp(24),0);body.addView(empty,new FrameLayout.LayoutParams(-1,-2));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        TextView export=s.button("Partilhar todas as marcações",false,this::export);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,-2);ep.bottomMargin=s.dp(14);ep.topMargin=s.dp(6);root.addView(export,ep);
        setContentView(root);getWindow().setStatusBarColor(s.p.bg);getWindow().setNavigationBarColor(s.p.bg);
        buildFilters();
    }
    @Override protected void onResume(){super.onResume();load();}
    void load(){PlusUi.run(this,()->{List<Highlights.Entry> found=Highlights.all(this,only);PlusUi.post(this,()->{all=found;buildFilters();refresh();});});}
    void buildFilters(){
        filters.removeAllViews();
        String[][] kinds={{"all","Todas"},{"color","Destaques"},{"underline","Sublinhados"},{"strike","Riscados"},{"note","Notas"}};
        for(String[] k:kinds){int n=0;for(Highlights.Entry e:all)if(matches(e,k[0]))n++;final String key=k[0];s.addChip(filters,s.chip(null,AppLanguage.text(this,k[1])+(all.isEmpty()?"":" · "+n),filter.equals(key),()->{filter=key;buildFilters();refresh();}));}
        for(int i=0;i<Highlights.KINDS.length;i++){
            final String key=Highlights.KINDS[i];boolean used=false;for(Highlights.Entry e:all)if(e.kind.equals(key))used=true;if(!used)continue;
            LinearLayout chip=s.chip(null,Highlights.NAMES[i],filter.equals(key),()->{filter=key;buildFilters();refresh();});
            View dot=new View(this);dot.setBackground(s.round(Highlights.COLORS[i],8));chip.addView(dot,0,new LinearLayout.LayoutParams(s.dp(14),s.dp(14)));((TextView)chip.getChildAt(1)).setPadding(s.dp(7),0,0,0);
            s.addChip(filters,chip);
        }
    }
    boolean matches(Highlights.Entry e,String f){
        if(f.equals("all"))return true;if(f.equals("color"))return e.kind.startsWith("highlight");return e.kind.equals(f);
    }
    void refresh(){
        String q=query.trim().toLowerCase(Locale.ROOT);shown=new ArrayList<>();
        for(Highlights.Entry e:all)if(matches(e,filter)&&(q.isEmpty()||e.text.toLowerCase(Locale.ROOT).contains(q)||e.note.toLowerCase(Locale.ROOT).contains(q)||e.name.toLowerCase(Locale.ROOT).contains(q)))shown.add(e);
        Set<String> books=new HashSet<>();for(Highlights.Entry e:shown)books.add(e.uri);
        count.setText(shown.size()+" "+AppLanguage.text(this,shown.size()==1?"marcação":"marcações")+" · "+books.size()+" "+AppLanguage.text(this,books.size()==1?"livro":"livros")+(shown.isEmpty()?"":" · "+AppLanguage.text(this,"toque para ver, toque longo para abrir no livro")));
        empty.setVisibility(all.isEmpty()?View.VISIBLE:View.GONE);
        if(!all.isEmpty()&&shown.isEmpty()){empty.setText(AppLanguage.text(this,"Nenhuma marcação com este filtro."));empty.setVisibility(View.VISIBLE);}
        adapter.notifyDataSetChanged();
    }
    void export(){
        if(shown.isEmpty()){PlusUi.toast(this,"Não há marcações para partilhar.");return;}
        StringBuilder out=new StringBuilder();String book="";
        for(Highlights.Entry e:shown){if(!e.name.equals(book)){book=e.name;out.append(out.length()>0?"\n":"").append("■ ").append(book).append('\n');}out.append("• [").append(AppLanguage.text(this,Highlights.label(e.kind))).append(", ").append(e.where()).append("] ").append(e.text.trim().replace('\n',' '));if(!e.note.isEmpty())out.append("\n  ").append(AppLanguage.text(this,"Nota")).append(": ").append(e.note);out.append('\n');}
        ShareTools.text(this,out.toString());
    }
    final class Adapter extends BaseAdapter {
        public int getCount(){return shown.size();}
        public Object getItem(int p){return shown.get(p);}
        public long getItemId(int p){return p;}
        public View getView(int position,View recycled,ViewGroup parent){
            Highlights.Entry e=shown.get(position);
            LinearLayout row=s.row();row.setGravity(Gravity.TOP);row.setBackground(s.round(s.p.surface,16));row.setPadding(0,0,s.dp(14),0);
            View bar=new View(HighlightsActivity.this);bar.setBackground(s.round(Highlights.color(e.kind),3));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(s.dp(6),-1);bp.rightMargin=s.dp(12);row.addView(bar,bp);
            LinearLayout col=s.column();col.setPadding(0,s.dp(12),0,s.dp(12));
            SpannableString quote=new SpannableString(e.text.trim().replace('\n',' '));if(quote.length()>0)Highlights.apply(quote,0,quote.length(),e.kind);
            TextView text=new TextView(HighlightsActivity.this);text.setText(quote);text.setTextSize(16);text.setTextColor(s.p.ink);text.setMaxLines(4);text.setEllipsize(TextUtils.TruncateAt.END);text.setLineSpacing(s.dp(2),1.08f);
            col.addView(text);
            if(!e.note.isEmpty()){TextView note=s.text("✎ "+e.note,14,s.p.ink,false);note.setMaxLines(2);note.setEllipsize(TextUtils.TruncateAt.END);note.setPadding(0,s.dp(6),0,0);col.addView(note);}
            String date=e.created>0?" · "+DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(e.created)):"";
            TextView meta=s.text(e.name+" · "+e.where()+" · "+AppLanguage.text(HighlightsActivity.this,Highlights.label(e.kind))+date,12.5f,s.p.muted,false);meta.setMaxLines(2);meta.setEllipsize(TextUtils.TruncateAt.MIDDLE);meta.setPadding(0,s.dp(6),0,0);
            col.addView(meta);
            row.addView(col,new LinearLayout.LayoutParams(0,-2,1));
            return row;
        }
    }
}
