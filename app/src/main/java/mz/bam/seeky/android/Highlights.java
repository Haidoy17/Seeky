package mz.bam.seeky.android;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.text.style.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Coloured highlights, underline, strike-through and notes in every reader, plus the shared index
 * behind "As minhas marcações". Text pages keep their marks in the reading notebook (ReaderStore);
 * PDF pages keep them here, by page and character offset of the page text.
 */
final class Highlights {
    static final String[] KINDS={"highlight","highlight-green","highlight-blue","highlight-pink","highlight-orange","highlight-purple"};
    static final int[] COLORS={0xffffe58a,0xffc5ecb0,0xffbfdcff,0xffffc7df,0xffffd3a6,0xffdccdff};
    static final String[] NAMES={"Amarelo","Verde","Azul","Rosa","Laranja","Lilás"};

    static boolean filled(String kind){return kind.startsWith("highlight")||kind.equals("note");}
    static int color(String kind){
        for(int i=0;i<KINDS.length;i++)if(KINDS[i].equals(kind))return COLORS[i];
        if("note".equals(kind))return 0xffcce7fa;if("underline".equals(kind))return 0xff3b82f6;if("strike".equals(kind))return 0xffd9534f;
        return COLORS[0];
    }
    static String label(String kind){
        if(kind.equals("underline"))return "Sublinhado";if(kind.equals("strike"))return "Riscado";if(kind.equals("note"))return "Nota";
        for(int i=0;i<KINDS.length;i++)if(KINDS[i].equals(kind))return i==0?"Destaque":"Destaque "+NAMES[i].toLowerCase(Locale.ROOT);
        return "Destaque";
    }
    /** Undo label in the notebook journal. */
    static String verb(String kind){return kind.equals("underline")?"Sublinhar":kind.equals("strike")?"Riscar":kind.equals("note")?"Adicionar nota":"Destacar";}
    /** Spans of one mark in a text reader. */
    static void apply(Spannable text,int start,int end,String kind){
        int f=Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        if(kind.equals("underline")){text.setSpan(new UnderlineSpan(),start,end,f);return;}
        if(kind.equals("strike")){text.setSpan(new StrikethroughSpan(),start,end,f);text.setSpan(new ForegroundColorSpan(0xff8a8f99),start,end,f);return;}
        text.setSpan(new BackgroundColorSpan(color(kind)),start,end,f);text.setSpan(new ForegroundColorSpan(0xff272237),start,end,f);
    }

    static final class Store extends SQLiteOpenHelper {
        Store(Context c){super(c.getApplicationContext(),"seeky-marks.db",null,2);}
        @Override public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE pdf_marks(id INTEGER PRIMARY KEY,uri TEXT NOT NULL,page INTEGER NOT NULL,start INTEGER NOT NULL,finish INTEGER NOT NULL,kind TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',value TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");
            d.execSQL("CREATE INDEX pdf_marks_page ON pdf_marks(uri,page)");
            d.execSQL("CREATE TABLE books(uri TEXT PRIMARY KEY,name TEXT,mime TEXT,updated INTEGER)");
            d.execSQL("CREATE TABLE text_times(uri TEXT,segment INTEGER,start INTEGER,finish INTEGER,kind TEXT,created INTEGER,PRIMARY KEY(uri,segment,start,finish,kind))");
            web(d);
        }
        /** EPUB and Word pages in their original layout: offsets in the page's visible text. */
        static void web(SQLiteDatabase d){d.execSQL("CREATE TABLE web_marks(id INTEGER PRIMARY KEY,uri TEXT NOT NULL,part INTEGER NOT NULL,start INTEGER NOT NULL,finish INTEGER NOT NULL,kind TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',value TEXT NOT NULL DEFAULT '',created INTEGER NOT NULL)");d.execSQL("CREATE INDEX web_marks_part ON web_marks(uri,part)");}
        @Override public void onUpgrade(SQLiteDatabase d,int o,int n){if(o<2)web(d);}
    }
    static void remember(Context c,String uri,String name,String mime){
        try(Store s=new Store(c)){ContentValues v=new ContentValues();v.put("uri",uri);v.put("name",name==null?"":name);v.put("mime",mime==null?"":mime);v.put("updated",System.currentTimeMillis());s.getWritableDatabase().insertWithOnConflict("books",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    }
    /** A text-reader mark was just created; keeps the book name and the time for the index. */
    static void textMarked(Context c,String uri,String name,String mime,int segment,int start,int end,String kind){
        remember(c,uri,name,mime);
        try(Store s=new Store(c)){ContentValues v=new ContentValues();v.put("uri",uri);v.put("segment",segment);v.put("start",start);v.put("finish",end);v.put("kind",kind);v.put("created",System.currentTimeMillis());s.getWritableDatabase().insertWithOnConflict("text_times",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    }
    static long addPdf(Context c,String uri,String name,String mime,int page,int start,int end,String kind,String note,String value){
        remember(c,uri,name,mime);
        try(Store s=new Store(c)){
            SQLiteDatabase db=s.getWritableDatabase();
            // The same colour twice over the same words is one mark; another style is added on top.
            db.delete("pdf_marks","uri=? AND page=? AND start=? AND finish=? AND kind=?",new String[]{uri,""+page,""+start,""+end,kind});
            ContentValues v=new ContentValues();v.put("uri",uri);v.put("page",page);v.put("start",start);v.put("finish",end);v.put("kind",kind);v.put("note",note==null?"":note);v.put("value",clean(value));v.put("created",System.currentTimeMillis());
            return db.insert("pdf_marks",null,v);
        }
    }

    static final class Entry {
        /** Mark made in the original EPUB/Word view (offsets in that page's text). */
        boolean web;
        final boolean pdf;final long id,created;final String uri,name,mime,kind,note,text,digest;final int part,start,end;
        Entry(boolean pdf,long id,String uri,String name,String mime,int part,int start,int end,String kind,String note,String text,String digest,long created){this.pdf=pdf;this.id=id;this.uri=uri;this.name=name;this.mime=mime;this.part=part;this.start=start;this.end=end;this.kind=kind;this.note=note==null?"":note.trim();this.text=clean(text);this.digest=digest;this.created=created;}
        /** "página 12", "capítulo 3" or "parte 2" in the language of the document. */
        String where(){String m=mime==null?"":mime;if(web&&!m.contains("epub"))return "documento";return (pdf||m.contains("pdf")?"página ":m.contains("epub")?"capítulo ":"parte ")+(part+1);}
    }
    static String name(String uri,String stored){if(stored!=null&&!stored.isEmpty())return stored;String last=Uri.parse(uri).getLastPathSegment();return last==null||last.isEmpty()?"Documento":last.substring(last.lastIndexOf('/')+1);}
    static long addWeb(Context c,String uri,String name,String mime,int part,int start,int end,String kind,String note,String value){
        remember(c,uri,name,mime);
        try(Store s=new Store(c)){
            SQLiteDatabase db=s.getWritableDatabase();
            db.delete("web_marks","uri=? AND part=? AND start=? AND finish=? AND kind=?",new String[]{uri,""+part,""+start,""+end,kind});
            ContentValues v=new ContentValues();v.put("uri",uri);v.put("part",part);v.put("start",start);v.put("finish",end);v.put("kind",kind);v.put("note",note==null?"":note);v.put("value",clean(value));v.put("created",System.currentTimeMillis());
            return db.insert("web_marks",null,v);
        }
    }
    static List<Entry> web(Context c,String uri,int part){
        List<Entry> out=new ArrayList<>();
        try(Store s=new Store(c);Cursor q=s.getReadableDatabase().rawQuery("SELECT id,start,finish,kind,note,value,created FROM web_marks WHERE uri=? AND part=? ORDER BY start",new String[]{uri,""+part})){
            while(q.moveToNext()){Entry e=new Entry(false,q.getLong(0),uri,"","",part,q.getInt(1),q.getInt(2),q.getString(3),q.getString(4),q.getString(5),"",q.getLong(6));e.web=true;out.add(e);}
        }
        return out;
    }
    static List<Entry> pdf(Context c,String uri,int page){
        List<Entry> out=new ArrayList<>();
        try(Store s=new Store(c);Cursor q=s.getReadableDatabase().rawQuery("SELECT id,start,finish,kind,note,value,created FROM pdf_marks WHERE uri=? AND page=? ORDER BY start",new String[]{uri,""+page})){
            while(q.moveToNext())out.add(new Entry(true,q.getLong(0),uri,"","application/pdf",page,q.getInt(1),q.getInt(2),q.getString(3),q.getString(4),q.getString(5),"",q.getLong(6)));
        }
        return out;
    }
    /** Every mark of every book (or of one book), newest first. */
    static List<Entry> all(Context c,String only){
        List<Entry> out=new ArrayList<>();Map<String,String[]> books=new HashMap<>();Map<String,Long> times=new HashMap<>();
        try(Store s=new Store(c)){
            SQLiteDatabase db=s.getReadableDatabase();
            try(Cursor q=db.rawQuery("SELECT uri,name,mime FROM books",null)){while(q.moveToNext())books.put(q.getString(0),new String[]{q.getString(1),q.getString(2)});}
            try(Cursor q=db.rawQuery("SELECT uri,segment,start,finish,kind,created FROM text_times",null)){while(q.moveToNext())times.put(q.getString(0)+"|"+q.getInt(1)+"|"+q.getInt(2)+"|"+q.getInt(3)+"|"+q.getString(4),q.getLong(5));}
            try(Cursor q=only==null?db.rawQuery("SELECT id,uri,page,start,finish,kind,note,value,created FROM pdf_marks",null):db.rawQuery("SELECT id,uri,page,start,finish,kind,note,value,created FROM pdf_marks WHERE uri=?",new String[]{only})){
                while(q.moveToNext()){String uri=q.getString(1);String[] b=books.get(uri);out.add(new Entry(true,q.getLong(0),uri,name(uri,b==null?null:b[0]),b==null||b[1].isEmpty()?"application/pdf":b[1],q.getInt(2),q.getInt(3),q.getInt(4),q.getString(5),q.getString(6),q.getString(7),"",q.getLong(8)));}
            }
            try(Cursor q=only==null?db.rawQuery("SELECT id,uri,part,start,finish,kind,note,value,created FROM web_marks",null):db.rawQuery("SELECT id,uri,part,start,finish,kind,note,value,created FROM web_marks WHERE uri=?",new String[]{only})){
                while(q.moveToNext()){String uri=q.getString(1);String[] b=books.get(uri);Entry e=new Entry(false,q.getLong(0),uri,name(uri,b==null?null:b[0]),b==null||b[1]==null?"":b[1],q.getInt(2),q.getInt(3),q.getInt(4),q.getString(5),q.getString(6),q.getString(7),"",q.getLong(8));e.web=true;out.add(e);}
            }
        }
        try(ReaderStore r=new ReaderStore(c);Cursor q=only==null?r.getReadableDatabase().rawQuery("SELECT uri,segment,digest,value,marks FROM draft WHERE marks<>'[]'",null):r.getReadableDatabase().rawQuery("SELECT uri,segment,digest,value,marks FROM draft WHERE marks<>'[]' AND uri=?",new String[]{only})){
            while(q.moveToNext()&&out.size()<20000){
                String uri=q.getString(0);int segment=q.getInt(1);StudyText value;
                try{value=ReaderStore.decode(q.getString(3),q.getString(4));}catch(RuntimeException broken){continue;}
                String[] b=books.get(uri);String mime=b==null||b[1]==null?"":b[1];
                for(StudyText.Mark m:value.marks){
                    if(m.start<0||m.end>value.text.length()||m.start>=m.end)continue;
                    Long at=times.get(uri+"|"+segment+"|"+m.start+"|"+m.end+"|"+m.kind);
                    out.add(new Entry(false,0,uri,name(uri,b==null?null:b[0]),mime,segment,m.start,m.end,m.kind,m.note,value.text.substring(m.start,m.end),q.getString(2),at==null?0:at));
                }
            }
        }
        Collections.sort(out,(x,y)->x.created!=y.created?Long.compare(y.created,x.created):x.name.compareToIgnoreCase(y.name)!=0?x.name.compareToIgnoreCase(y.name):x.part!=y.part?Integer.compare(x.part,y.part):Integer.compare(x.start,y.start));
        return out;
    }
    /** Changes the style of a mark (kind) or removes it (null). */
    static void change(Context c,Entry e,String kind){
        if(e.pdf||e.web){String table=e.web?"web_marks":"pdf_marks";try(Store s=new Store(c)){if(kind==null)s.getWritableDatabase().delete(table,"id=?",new String[]{""+e.id});else{ContentValues v=new ContentValues();v.put("kind",kind);s.getWritableDatabase().update(table,v,"id=?",new String[]{""+e.id});}}return;}
        try(ReaderStore r=new ReaderStore(c)){
            SQLiteDatabase db=r.getWritableDatabase();
            db.beginTransaction();
            try{
                StudyText value;
                try(Cursor q=db.rawQuery("SELECT value,marks FROM draft WHERE uri=? AND segment=? AND digest=?",new String[]{e.uri,""+e.part,e.digest})){if(!q.moveToFirst())return;value=ReaderStore.decode(q.getString(0),q.getString(1));}
                for(Iterator<StudyText.Mark> it=value.marks.iterator();it.hasNext();){StudyText.Mark m=it.next();if(m.start==e.start&&m.end==e.end&&m.kind.equals(e.kind)){if(kind==null)it.remove();else m.kind=kind;break;}}
                r.writeDraft(db,e.uri,e.part,e.digest,value);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            r.dirty();
        }
        try(Store s=new Store(c)){s.getWritableDatabase().delete("text_times","uri=? AND segment=? AND start=? AND finish=? AND kind=?",new String[]{e.uri,""+e.part,""+e.start,""+e.end,e.kind});}
        if(kind!=null)try(Store s=new Store(c)){ContentValues v=new ContentValues();v.put("uri",e.uri);v.put("segment",e.part);v.put("start",e.start);v.put("finish",e.end);v.put("kind",kind);v.put("created",e.created>0?e.created:System.currentTimeMillis());s.getWritableDatabase().insertWithOnConflict("text_times",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    }
    /** Opens the book on the marked page and, for text pages, selects the marked words. */
    static Intent intent(Context c,Entry e){
        String mime=e.mime==null||e.mime.isEmpty()?MediaFormats.mime(e.name,null):e.mime;
        Intent i=new Intent(c,ReaderActivity.class).setDataAndType(Uri.parse(e.uri),mime).putExtra("name",e.name).putExtra("study_segment",e.part);
        // Original EPUB/Word marks open in the original view and scroll to the marked words.
        if(e.web){c.getSharedPreferences("seeky",0).edit().putBoolean("reader_original_"+e.uri,true).apply();i.putExtra("original_mark",e.start);}
        else if(!e.pdf)i.putExtra("search_offset",e.start).putExtra("search_length",Math.max(1,e.end-e.start));
        return i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    static void open(Activity a,Entry e){try{a.startActivity(intent(a,e));}catch(RuntimeException failed){PlusUi.toast(a,"Não foi possível abrir este livro. Abra-o de novo na biblioteca.");}}

    /** Colour palette and other marks for a selection; "note" asks the caller for the note text. */
    static void choose(Activity a,String preview,Consumer<String> pick){
        Screens s=new Screens(a);LinearLayout root=s.column();
        TextView quote=s.text(preview.length()>220?preview.substring(0,220)+"…":preview,15,s.p.ink,false);quote.setMaxLines(4);quote.setEllipsize(TextUtils.TruncateAt.END);quote.setPadding(s.dp(12),s.dp(10),s.dp(12),s.dp(10));quote.setBackground(s.round(s.p.tint,14));
        root.addView(quote,new LinearLayout.LayoutParams(-1,-2));
        TextView colours=s.text("Destacar com cor",14,s.p.ink,true);colours.setPadding(s.dp(2),s.dp(14),0,s.dp(8));root.addView(colours);
        LinearLayout dots=s.row();root.addView(dots,new LinearLayout.LayoutParams(-1,-2));
        final UiDialog[] dialog=new UiDialog[1];
        for(int i=0;i<KINDS.length;i++){
            final String kind=KINDS[i];View dot=new View(a);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(COLORS[i]);g.setStroke(s.dp(2),(s.p.ink&0xffffff)|0x33000000);dot.setBackground(g);
            dot.setContentDescription(AppLanguage.text(a,NAMES[i]));dot.setOnClickListener(v->{dialog[0].dismiss();pick.accept(kind);});Motion.touch(dot);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,s.dp(44),1);lp.rightMargin=s.dp(i==KINDS.length-1?0:8);dots.addView(dot,lp);
        }
        TextView other=s.text("Outras marcas",14,s.p.ink,true);other.setPadding(s.dp(2),s.dp(16),0,s.dp(8));root.addView(other);
        LinearLayout styles=s.row();
        s.addChip(styles,s.chip("type","Sublinhar",false,()->{dialog[0].dismiss();pick.accept("underline");}));
        s.addChip(styles,s.chip("minus","Riscar",false,()->{dialog[0].dismiss();pick.accept("strike");}));
        s.addChip(styles,s.chip("note","Nota",false,()->{dialog[0].dismiss();pick.accept("note");}));
        root.addView(s.scroller(styles),new LinearLayout.LayoutParams(-1,-2));
        TextView hint=s.text("Encontre todas as marcações em Mais → As minhas marcações.",14,s.p.muted,false);hint.setPadding(s.dp(2),s.dp(14),0,0);root.addView(hint);
        dialog[0]=new UiDialog.Builder(a).setTitle("Marcar selecção").setView(root).setNegativeButton("Cancelar",null).create();
        dialog[0].show();
    }
    /** Full text of a mark with open, copy, share, colour and delete. */
    static void view(Activity a,Entry e,Runnable changed){
        Screens s=new Screens(a);LinearLayout root=s.column();
        LinearLayout head=s.row();head.addView(pill(s,e));
        TextView when=s.text(e.created>0?java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new Date(e.created)):"",12,s.p.muted,false);when.setGravity(Gravity.END);
        head.addView(when,new LinearLayout.LayoutParams(0,-2,1));root.addView(head);
        LinearLayout book=s.row();book.setPadding(0,s.dp(10),0,s.dp(10));
        book.addView(new Icons(a,"book",s.p.accent),new LinearLayout.LayoutParams(s.dp(18),s.dp(18)));
        TextView where=s.text(e.name+" · "+e.where(),14,s.p.ink,true);where.setPadding(s.dp(8),0,0,0);where.setMaxLines(2);where.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        book.addView(where,new LinearLayout.LayoutParams(0,-2,1));root.addView(book);
        root.addView(quote(s,e,0,18),new LinearLayout.LayoutParams(-1,-2));
        if(!e.note.isEmpty()){
            LinearLayout note=s.row();note.setGravity(Gravity.TOP);note.setPadding(s.dp(12),s.dp(10),s.dp(12),s.dp(10));note.setBackground(s.round(blend(s.p.surface,0xff2f80ed,s.p.light?.12f:.18f),14));
            note.addView(new Icons(a,"note",0xff2f80ed),new LinearLayout.LayoutParams(s.dp(18),s.dp(18)));
            TextView n=s.text(e.note,15,s.p.ink,false);n.setPadding(s.dp(8),0,0,0);n.setTextIsSelectable(true);note.addView(n,new LinearLayout.LayoutParams(0,-2,1));
            root.addView(note,s.gap(10));
        }
        LinearLayout actions=s.row();
        final UiDialog[] dialog=new UiDialog[1];
        s.addChip(actions,s.chip("copy","Copiar",false,()->{((android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(e.name,share(e)));PlusUi.toast(a,"Texto copiado.");}));
        s.addChip(actions,s.chip("share","Partilhar",false,()->ShareTools.text(a,share(e))));
        s.addChip(actions,s.chip("palette","Mudar cor",false,()->choose(a,e.text,kind->{if(kind.equals("note"))return;PlusUi.run(a,()->{change(a,e,kind);PlusUi.post(a,()->{dialog[0].dismiss();if(changed!=null)changed.run();});});})));
        s.addChip(actions,s.chip("trash","Eliminar",false,()->new UiDialog.Builder(a).setTitle("Eliminar esta marcação?").setMessage("O texto do livro não é alterado.").setPositiveButton("Eliminar",(d,w)->PlusUi.run(a,()->{change(a,e,null);PlusUi.post(a,()->{dialog[0].dismiss();PlusUi.toast(a,"Marcação eliminada.");if(changed!=null)changed.run();});})).setNegativeButton("Cancelar",null).show()));
        root.addView(s.scroller(actions),s.gap(14));
        dialog[0]=new UiDialog.Builder(a).setTitle(label(e.kind)).setView(root).setPositiveButton("Abrir no livro",(d,w)->open(a,e)).setNegativeButton("Fechar",null).create();
        dialog[0].show();
    }
    static String share(Entry e){return "“"+e.text+"”"+(e.note.isEmpty()?"":"\n"+e.note)+"\n— "+e.name+", "+e.where();}
    /**
     * Readable text of a mark. PDF text often carries tabs, private-use or control characters between
     * words (drawn as gaps or boxes) and hyphenated line breaks; they become single spaces or are joined.
     */
    static String clean(String value){
        if(value==null)return "";
        String t=value.replaceAll("(\\p{L})[-\u00ad]\\s*[\\r\\n]+\\s*(\\p{Ll})","$1$2");
        StringBuilder out=new StringBuilder(t.length());boolean gap=false;
        for(int i=0;i<t.length();i++){
            char c=t.charAt(i);int type=Character.getType(c);
            // A zero-width space separates words in some PDFs; other format marks (soft hyphen, joiners) vanish.
            if(type==Character.FORMAT&&c!='\u200b')continue;
            if(Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.isISOControl(c)||type==Character.PRIVATE_USE||c=='\ufffd'||c=='\u200b'){gap=true;continue;}
            if(gap&&out.length()>0)out.append(' ');gap=false;out.append(c);
        }
        return out.toString();
    }
    /** {@code color} mixed into {@code base}: soft card tints that stay readable in dark themes. */
    static int blend(int base,int color,float amount){
        int r=Math.round(((base>>16)&255)*(1-amount)+((color>>16)&255)*amount),g=Math.round(((base>>8)&255)*(1-amount)+((color>>8)&255)*amount),b=Math.round((base&255)*(1-amount)+(color&255)*amount);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    /** "Destaque · amarelo", "Sublinhado"… with the colour name for coloured highlights. */
    static String colourName(String kind){for(int i=0;i<KINDS.length;i++)if(KINDS[i].equals(kind))return NAMES[i];return "";}
    /** The marked words as a quote: colour bar, softly tinted card, serif text in the page's ink. */
    static LinearLayout quote(Screens s,Entry e,int maxLines,float size){
        int color=color(e.kind);
        LinearLayout card=new LinearLayout(s.a);card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(s.round(blend(s.p.surface,color,s.p.light?.22f:.13f),16));
        View bar=new View(s.a);bar.setBackground(s.round(color,3));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(s.dp(5),-1);bp.setMargins(s.dp(10),s.dp(12),0,s.dp(12));card.addView(bar,bp);
        TextView text=new TextView(s.a);text.setText(e.text);text.setTextSize(size);text.setTextColor(s.p.ink);text.setTypeface(android.graphics.Typeface.SERIF);text.setLineSpacing(s.dp(3),1.1f);
        if(e.kind.equals("underline")){text.setPaintFlags(text.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);}
        if(e.kind.equals("strike")){text.setPaintFlags(text.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);}
        if(maxLines>0){text.setMaxLines(maxLines);text.setEllipsize(TextUtils.TruncateAt.END);}else text.setTextIsSelectable(true);
        text.setPadding(s.dp(12),s.dp(12),s.dp(14),s.dp(12));
        card.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        return card;
    }
    /** Small pill with the mark's colour and kind. */
    static LinearLayout pill(Screens s,Entry e){
        LinearLayout p=s.row();p.setPadding(s.dp(8),s.dp(3),s.dp(10),s.dp(3));p.setBackground(s.round(s.p.tint,12));
        View dot=new View(s.a);GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color(e.kind));dot.setBackground(g);p.addView(dot,new LinearLayout.LayoutParams(s.dp(10),s.dp(10)));
        String name=colourName(e.kind);
        TextView t=s.text(AppLanguage.text(s.a,e.kind.startsWith("highlight")?"Destaque":label(e.kind))+(name.isEmpty()?"":" · "+AppLanguage.text(s.a,name).toLowerCase(Locale.ROOT)),13.5f,s.p.ink,true);t.setPadding(s.dp(6),0,0,0);
        p.addView(t);return p;
    }
    private Highlights(){}
}
