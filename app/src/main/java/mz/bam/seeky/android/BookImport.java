package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.*;
import org.json.*;

/**
 * "Importar livros": a folder (with or without its sub-folders) or chosen files; which formats; files
 * under a size are skipped; optionally favourite and a collection. Folder imports start at once and
 * run in the background task list; FB2, MOBI, ODT, RTF, MHTML and CBZ books become EPUBs on import.
 */
final class BookImport {
    /** Key, label, extensions. */
    static final String[][] GROUPS={
        {"epub","EPUB","epub"},{"pdf","PDF","pdf"},
        {"docx","DOCX/ODT/RTF","docx odt rtf"},{"txt","TXT/MD","txt md"},
        {"html","HTML/MHTML","htm html mht mhtml"},{"fb2","FB2","fb2 fbz"},
        {"mobi","MOBI/AZW3/PRC","mobi azw azw3 prc"},{"cbz","CBZ (BD)","cbz"}};
    static final long PENDING_MS=15*60*1000L;

    static String group(String name,String mime){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);if(n.endsWith(".fb2.zip"))return "fb2";
        String ext=MediaFormats.ext(n);for(String[] g:GROUPS)if(Arrays.asList(g[2].split(" ")).contains(ext))return g[0];
        if(mime==null)return "";
        switch(mime){case "application/pdf":return "pdf";case "application/epub+zip":return "epub";case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":return "docx";case "text/plain":return "txt";case "text/html":return "html";default:return "";}
    }
    /** Folder scans keep only the chosen formats and sizes. */
    static boolean accepts(JSONObject o,String name,String mime,long size){
        if(!BookQuery.accepts(name,mime))return false;
        JSONArray f=o==null?null:o.optJSONArray("formats");
        if(f!=null){String g=group(name,mime);boolean ok=false;for(int i=0;i<f.length();i++)if(f.optString(i).equals(g))ok=true;if(!ok)return false;}
        long min=o==null?0:o.optLong("min_bytes",0);return min<=0||size<0||size>=min;
    }
    /** Favourite and collection chosen in the dialog, applied to each imported book (and to ones already there). */
    static void tag(Context c,StudyStore.Row job,String hash){
        if(hash==null||!hash.matches("[a-f0-9]{64}"))return;
        try{
            JSONObject o=new JSONObject(job.s("payload"));
            if(o.optBoolean("favorite"))synchronized(BookImport.class){SharedPreferences p=c.getSharedPreferences("books",0);Set<String> s=new HashSet<>(p.getStringSet("favorites",Collections.emptySet()));if(s.add(hash))p.edit().putStringSet("favorites",s).commit();}
            String name=o.optString("collection","").trim();
            if(!name.isEmpty())try(PlusStore db=new PlusStore(c)){db.save("book_collections",StudyStore.values("hash",PlusStore.hash(hash),"collection",BookCategories.collection(db,name)));}
        }catch(Exception e){AppDiagnostics.record(c,"import-tag",e);}
    }
    /** Options the dialog left for the system picker's answer (only for a few minutes, and only once). */
    static JSONObject pending(Context c,boolean books,JSONObject base){
        if(!books)return base;SharedPreferences p=c.getSharedPreferences("books",0);String saved=p.getString("import_pending","");long at=p.getLong("import_pending_at",0);
        if(saved.isEmpty())return base;p.edit().remove("import_pending").remove("import_pending_at").apply();if(System.currentTimeMillis()-at>PENDING_MS)return base;
        try{JSONObject o=new JSONObject(saved);for(Iterator<String> k=o.keys();k.hasNext();){String key=k.next();if(!base.has(key))base.put(key,o.get(key));}}catch(JSONException ignored){}return base;
    }
    /** The last choices of the dialog (formats, size, sub-folders), for "search this folder again". */
    static JSONObject saved(Context c){try{return options(c.getSharedPreferences("books",0),null,null,null,null,null);}catch(JSONException e){return new JSONObject();}}
    static JSONObject options(SharedPreferences p,Set<String> formats,Integer minKb,Boolean recursive,Boolean favorite,String collection)throws JSONException{
        Set<String> f=formats!=null?formats:p.getStringSet("import_formats",null);JSONObject o=new JSONObject();JSONArray list=new JSONArray();
        for(String[] g:GROUPS)if(f==null||f.contains(g[0]))list.put(g[0]);o.put("formats",list);
        o.put("min_bytes",Math.max(0,minKb!=null?minKb:p.getInt("import_min_kb",20))*1024L);o.put("recursive",recursive!=null?recursive:p.getBoolean("import_recursive",true));o.put("auto",true);
        if(favorite!=null&&favorite)o.put("favorite",true);if(collection!=null&&!collection.trim().isEmpty())o.put("collection",collection.trim());return o;
    }
    /** "/sdcard/Livros" for folders in the phone memory, the full path elsewhere. */
    static String display(File dir){String path=dir.getPath(),root=Environment.getExternalStorageDirectory().getPath();if(path.equals(root))return "/sdcard";if(path.startsWith(root+"/"))return "/sdcard"+path.substring(root.length());return path;}

    // ---------- dialog ----------
    final MainActivity a;final BookLibrary library;final SharedPreferences prefs;final AppPalette pal;
    boolean folderMode;File dir;UiDialog dialog;
    final CheckBox[] boxes=new CheckBox[GROUPS.length];CheckBox sub,favorite;EditText size,collection;TextView path,tabFolder,tabFiles,note,all,grant;LinearLayout folderBox,sizeRow;

    static BookImport show(MainActivity a,BookLibrary library,boolean folder){BookImport b=new BookImport(a,library);b.folderMode=folder||b.prefs.getBoolean("import_folder_mode",true);b.open();return b;}
    BookImport(MainActivity a,BookLibrary library){this.a=a;this.library=library;prefs=a.getSharedPreferences("books",0);pal=AppPalette.of(a);}
    String tr(String s){return AppLanguage.text(a,s);}
    int dp(float n){return a.dp((int)n);}
    boolean direct(){return StorageAccess.all(a);}

    void open(){
        dir=startFolder();
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tabs=new LinearLayout(a);tabs.setPadding(dp(4),dp(4),dp(4),dp(4));GradientDrawable track=new GradientDrawable();track.setColor(pal.tint);track.setCornerRadius(dp(22));tabs.setBackground(track);
        tabFolder=tab("Uma pasta",()->mode(true));tabFiles=tab("Escolher ficheiros",()->mode(false));tabs.addView(tabFolder,new LinearLayout.LayoutParams(0,dp(40),1));tabs.addView(tabFiles,new LinearLayout.LayoutParams(0,dp(40),1));body.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        folderBox=new LinearLayout(a);folderBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout where=new LinearLayout(a);where.setGravity(Gravity.CENTER_VERTICAL);where.setPadding(dp(4),dp(14),0,0);
        where.addView(new Icons(a,"folder",pal.accent),new LinearLayout.LayoutParams(dp(24),dp(24)));
        path=new TextView(a);path.setTextSize(16);path.setTextColor(pal.ink);path.setSingleLine(true);path.setEllipsize(android.text.TextUtils.TruncateAt.START);path.setPadding(dp(4),dp(10),dp(6),dp(8));path.setOnClickListener(v->browse());Motion.touch(path);
        LinearLayout pathBox=new LinearLayout(a);pathBox.setOrientation(LinearLayout.VERTICAL);pathBox.addView(path,new LinearLayout.LayoutParams(-1,-2));View rule=new View(a);rule.setBackgroundColor(pal.accent);pathBox.addView(rule,new LinearLayout.LayoutParams(-1,dp(2)));
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(8);where.addView(pathBox,wp);
        FrameLayout places=new FrameLayout(a);GradientDrawable ring=new GradientDrawable();ring.setShape(GradientDrawable.OVAL);ring.setStroke(dp(2),(pal.muted&0xffffff)|0x99000000);places.setBackground(ring);Icons down=new Icons(a,"chevron",pal.muted);down.setRotation(90);places.addView(down,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER));places.setContentDescription(tr("Pastas sugeridas e recentes"));places.setOnClickListener(v->places());Motion.touch(places);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(40),dp(40));pp.leftMargin=dp(8);where.addView(places,pp);folderBox.addView(where);
        sub=check("Incluir subpastas",prefs.getBoolean("import_recursive",true));folderBox.addView(sub);
        body.addView(folderBox);
        LinearLayout formatsHead=new LinearLayout(a);formatsHead.setGravity(Gravity.CENTER_VERTICAL);formatsHead.setPadding(dp(2),dp(12),0,dp(2));
        TextView formats=new TextView(a);formats.setText(tr("Formatos"));formats.setTextSize(15);formats.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));formats.setTextColor(pal.ink);formatsHead.addView(formats,new LinearLayout.LayoutParams(0,-2,1));
        all=new TextView(a);all.setTextSize(14);all.setTextColor(pal.accent);all.setPadding(dp(10),dp(8),dp(6),dp(8));all.setOnClickListener(v->toggleAll());Motion.touch(all);formatsHead.addView(all);body.addView(formatsHead);
        Set<String> chosen=prefs.getStringSet("import_formats",null);
        for(int i=0;i<GROUPS.length;i+=2){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);for(int k=i;k<Math.min(GROUPS.length,i+2);k++){boxes[k]=check(GROUPS[k][1],chosen==null||chosen.contains(GROUPS[k][0]));boxes[k].setOnCheckedChangeListener((b,on)->refresh());row.addView(boxes[k],new LinearLayout.LayoutParams(0,-2,1));}body.addView(row);}
        sizeRow=new LinearLayout(a);sizeRow.setGravity(Gravity.CENTER_VERTICAL);sizeRow.setPadding(dp(2),dp(10),0,0);
        TextView larger=new TextView(a);larger.setText(tr("Tamanho do ficheiro >"));larger.setTextSize(15);larger.setTextColor(pal.ink);sizeRow.addView(larger,new LinearLayout.LayoutParams(0,-2,1));
        size=new EditText(a);size.setInputType(InputType.TYPE_CLASS_NUMBER);size.setSingleLine(true);size.setText(String.valueOf(prefs.getInt("import_min_kb",20)));size.setGravity(Gravity.CENTER);size.setContentDescription(tr("Tamanho mínimo em KB"));size.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});sizeRow.addView(size,new LinearLayout.LayoutParams(dp(96),-2));
        TextView kb=new TextView(a);kb.setText("KB");kb.setTextSize(15);kb.setTextColor(pal.ink);kb.setPadding(dp(10),0,dp(4),0);sizeRow.addView(kb);body.addView(sizeRow);
        LinearLayout tagRow=new LinearLayout(a);tagRow.setGravity(Gravity.CENTER_VERTICAL);tagRow.setPadding(0,dp(10),0,0);
        favorite=check("Favorito",false);tagRow.addView(favorite,new LinearLayout.LayoutParams(-2,-2));
        collection=new EditText(a);collection.setSingleLine(true);collection.setHint(tr("Colecção (opcional)"));collection.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=dp(8);tagRow.addView(collection,cp);
        FrameLayout pick=new FrameLayout(a);GradientDrawable ring2=new GradientDrawable();ring2.setShape(GradientDrawable.OVAL);ring2.setStroke(dp(2),(pal.muted&0xffffff)|0x99000000);pick.setBackground(ring2);Icons down2=new Icons(a,"chevron",pal.muted);down2.setRotation(90);pick.addView(down2,new FrameLayout.LayoutParams(dp(18),dp(18),Gravity.CENTER));pick.setContentDescription(tr("Escolher colecção"));pick.setOnClickListener(v->collections());Motion.touch(pick);
        LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(dp(40),dp(40));kp.leftMargin=dp(8);tagRow.addView(pick,kp);body.addView(tagRow);
        note=new TextView(a);note.setTextSize(12);note.setLineSpacing(dp(2),1f);body.addView(note);
        grant=new TextView(a);grant.setText(tr("Autorizar acesso geral para escolher a pasta aqui"));grant.setTextSize(14);grant.setTextColor(pal.accent);grant.setPadding(dp(2),dp(10),dp(2),dp(6));grant.setOnClickListener(v->{dialog.dismiss();a.requestGlobal();});Motion.touch(grant);body.addView(grant);
        dialog=new UiDialog.Builder(a).setTitle("Importar livros").setView(body).setPositiveButton("Importar",null).setNegativeButton("Cancelar",null).create();
        dialog.show();dialog.getButton(-1).setOnClickListener(v->start());
        mode(folderMode);
    }
    TextView tab(String label,Runnable action){TextView t=new TextView(a);t.setText(tr(label));t.setTextSize(14);t.setGravity(Gravity.CENTER);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(dp(6),0,dp(6),0);t.setOnClickListener(v->action.run());t.setContentDescription(tr(label));Motion.touch(t);return t;}
    CheckBox check(String label,boolean on){CheckBox c=new CheckBox(a);c.setText(tr(label));c.setTextSize(15);c.setTextColor(pal.ink);c.setChecked(on);c.setMinHeight(dp(40));c.setMinimumHeight(dp(40));c.setPadding(c.getPaddingLeft(),0,c.getPaddingRight(),0);c.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{pal.accent,pal.muted}));return c;}
    void mode(boolean folder){
        folderMode=folder;
        for(TextView t:new TextView[]{tabFolder,tabFiles}){boolean on=(t==tabFolder)==folder;GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(18));g.setColor(on?pal.accent:0);t.setBackground(g);t.setTextColor(on?pal.onAccent:pal.ink);t.setSelected(on);}
        folderBox.setVisibility(folder?View.VISIBLE:View.GONE);sizeRow.setVisibility(folder?View.VISIBLE:View.GONE);refresh();
    }
    void refresh(){
        boolean every=true;for(CheckBox b:boxes)every&=b.isChecked();all.setText(tr(every?"Nenhum":"Todos"));
        path.setText(direct()?(dir==null?tr("Escolher pasta…"):display(dir)):tr("Escolher pasta no Android…"));path.setContentDescription(tr("Pasta")+": "+path.getText());
        String text=folderMode?(direct()?"Os livros desta pasta são importados já, em segundo plano. Os repetidos são ignorados.":"Toque em Importar e escolha a pasta; a importação começa logo a seguir."):"Abre o selector do Android para escolher um ou mais livros.";
        note.setText(tr(text)+" "+tr("FB2, MOBI, AZW3, PRC, ODT, RTF, MHTML e CBZ são convertidos para EPUB para usar todas as ferramentas do leitor."));
        note.setTextColor(pal.muted);note.setPadding(dp(2),dp(12),dp(2),dp(2));
        grant.setVisibility(folderMode&&!direct()?View.VISIBLE:View.GONE);
        if(dialog!=null&&dialog.getButton(-1)!=null)dialog.getButton(-1).setText(tr(folderMode&&!direct()?"Escolher pasta":"Importar"));
        if(dialog!=null)dialog.refit();
    }
    void toggleAll(){boolean every=true;for(CheckBox b:boxes)every&=b.isChecked();for(CheckBox b:boxes)b.setChecked(!every);refresh();}
    Set<String> formats(){Set<String> f=new LinkedHashSet<>();for(int i=0;i<GROUPS.length;i++)if(boxes[i].isChecked())f.add(GROUPS[i][0]);return f;}
    File startFolder(){
        String last=prefs.getString("import_dir","");if(!last.isEmpty()){File f=new File(last);if(f.isDirectory())return f;}
        File root=Environment.getExternalStorageDirectory();for(String name:new String[]{"Livros","Books","eBooks","Download"}){File f=new File(root,name);if(f.isDirectory())return f;}
        return root;
    }
    /** Common places for books on phones here: downloads, documents, WhatsApp and Telegram documents, plus recent folders. */
    static List<File> suggestions(Context c){
        File root=Environment.getExternalStorageDirectory();List<File> out=new ArrayList<>();
        String[] names={"Livros","Books","eBooks","Download","Documents","Kindle","Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents","WhatsApp/Media/WhatsApp Documents","Android/media/org.telegram.messenger/Telegram/Telegram Documents","Telegram/Telegram Documents","Download/Telegram"};
        for(String n:names){File f=new File(root,n);if(f.isDirectory()&&!out.contains(f))out.add(f);}
        for(File v:StorageAccess.volumes(c))if(!out.contains(v))out.add(v);return out;
    }
    String label(File f){String p=f.getPath();if(p.contains("WhatsApp Documents"))return "WhatsApp · Documentos";if(p.contains("Telegram"))return "Telegram · Documentos";if(p.endsWith("/Download"))return tr("Transferências");if(p.endsWith("/Documents"))return tr("Documentos");try{for(File v:StorageAccess.volumes(a))if(v.getCanonicalFile().equals(f.getCanonicalFile()))return tr(StorageAccess.location(v));}catch(java.io.IOException ignored){}return f.getName();}
    void places(){
        if(!direct()){browse();return;}
        UiPanel panel=new UiPanel(a,"Pastas",tr("Sugeridas e recentes"),"folder");Set<String> seen=new HashSet<>();
        try{JSONArray recent=new JSONArray(prefs.getString("import_recent","[]"));for(int i=0;i<recent.length();i++){File f=new File(recent.getString(i));if(f.isDirectory()&&seen.add(f.getPath()))panel.action("clock",f.getName().isEmpty()?display(f):f.getName(),display(f),false,()->{dir=f;refresh();});}}catch(JSONException ignored){}
        for(File f:suggestions(a))if(seen.add(f.getPath()))panel.action("folder",label(f),display(f),false,()->{dir=f;refresh();});
        panel.action("search",tr("Procurar outra pasta…"),null,false,this::browse);panel.show();
    }
    void browse(){
        if(!direct()){start();return;}
        new FolderPicker(a,dir==null?Environment.getExternalStorageDirectory():dir,f->{dir=f;refresh();}).show();
    }
    void collections(){
        PlusUi.run(a,()->{List<String> names=new ArrayList<>();try(PlusStore db=new PlusStore(a);android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT name FROM collections ORDER BY name COLLATE NOCASE LIMIT 60",null)){while(c.moveToNext())names.add(c.getString(0));}
            PlusUi.post(a,()->{UiPanel panel=new UiPanel(a,"Colecções",tr("Os livros importados entram nesta colecção"),"collection");for(String n:names)panel.action("collection",n,null,false,()->collection.setText(n));if(names.isEmpty())panel.note(tr("Ainda não há colecções. Escreva um nome para criar uma nova."));panel.show();});});
    }
    void start(){
        Set<String> f=formats();if(f.isEmpty()){PlusUi.toast(a,"Escolha pelo menos um formato.");return;}
        int kb;try{kb=Math.max(0,Math.min(999999,Integer.parseInt(size.getText().toString().trim())));}catch(NumberFormatException e){kb=0;}
        String group=collection.getText().toString().trim();
        prefs.edit().putStringSet("import_formats",f).putInt("import_min_kb",kb).putBoolean("import_recursive",sub.isChecked()).putBoolean("import_folder_mode",folderMode).apply();
        JSONObject o;try{o=options(prefs,f,kb,sub.isChecked(),favorite.isChecked(),group);}catch(JSONException e){a.showError(e);return;}
        if(folderMode&&direct()){
            if(dir==null||!dir.isDirectory()){PlusUi.toast(a,"Escolha uma pasta.");return;}
            remember(dir);ImportWork.enqueueDir(a,dir,o);PlusUi.toast(a,tr("A importar livros de")+" "+display(dir)+" · "+tr("acompanhe em Tarefas em segundo plano."));
        }else{
            prefs.edit().putString("import_pending",o.toString()).putLong("import_pending_at",System.currentTimeMillis()).apply();
            if(folderMode)ImportWork.pickFolder(a,true);else library.pickFiles(mimes(f));
        }
        dialog.dismiss();
    }
    void remember(File f){try{JSONArray old=new JSONArray(prefs.getString("import_recent","[]")),list=new JSONArray();list.put(f.getPath());for(int i=0;i<old.length()&&list.length()<6;i++)if(!old.getString(i).equals(f.getPath()))list.put(old.getString(i));prefs.edit().putString("import_dir",f.getPath()).putString("import_recent",list.toString()).apply();}catch(JSONException ignored){}}
    /** System picker filter; formats that phones rarely label (FB2, MOBI, CBZ, MHTML) open it without a filter. */
    static String[] mimes(Set<String> f){
        if(f.contains("fb2")||f.contains("mobi")||f.contains("cbz")||f.contains("html"))return null;
        List<String> m=new ArrayList<>();
        if(f.contains("pdf"))m.add("application/pdf");if(f.contains("epub"))m.add("application/epub+zip");
        if(f.contains("docx"))m.addAll(Arrays.asList("application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.oasis.opendocument.text","application/rtf","text/rtf"));
        if(f.contains("txt"))m.addAll(Arrays.asList("text/plain","text/markdown"));return m.toArray(new String[0]);
    }

    /** Simple folder browser over the device storage (all-files access). */
    static final class FolderPicker {
        final MainActivity a;final java.util.function.Consumer<File> chosen;File current;UiDialog dialog;LinearLayout rows;TextView where,count;final AppPalette pal;
        FolderPicker(MainActivity a,File start,java.util.function.Consumer<File> chosen){this.a=a;this.chosen=chosen;current=start;pal=AppPalette.of(a);}
        void show(){
            LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
            where=new TextView(a);where.setTextSize(15);where.setTextColor(pal.ink);where.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));where.setPadding(a.dp(4),0,a.dp(4),a.dp(2));body.addView(where);
            count=new TextView(a);count.setTextSize(13);body.addView(count);
            rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);
            dialog=new UiDialog.Builder(a).setTitle("Escolher pasta").setView(body).setPositiveButton("Usar esta pasta",(d,w)->{if(current!=null)chosen.accept(current);}).setNegativeButton("Cancelar",null).create();dialog.show();
            go(current);
        }
        void go(File dir){
            current=dir;rows.removeAllViews();
            if(dir==null){where.setText(AppLanguage.text(a,"Armazenamento"));count.setText("");for(File v:StorageAccess.volumes(a))rows.addView(row("storage",AppLanguage.text(a,StorageAccess.location(v)),()->go(v)));if(dialog.getButton(-1)!=null)dialog.getButton(-1).setEnabled(false);dialog.refit();return;}
            if(dialog.getButton(-1)!=null)dialog.getButton(-1).setEnabled(true);
            where.setText(display(dir));
            File[] list=StorageAccess.allowed(a,dir)?dir.listFiles():null;List<File> folders=new ArrayList<>();int books=0;
            if(list!=null)for(File f:list){if(f.getName().startsWith("."))continue;if(f.isDirectory())folders.add(f);else if(BookQuery.accepts(f.getName(),StorageAccess.mime(f)))books++;}
            folders.sort((x,y)->x.getName().compareToIgnoreCase(y.getName()));
            count.setText(books==0?AppLanguage.text(a,"Sem livros nesta pasta (pode haver nas subpastas)."):books==1?AppLanguage.text(a,"1 livro nesta pasta"):books+" "+AppLanguage.text(a,"livros nesta pasta"));
            boolean root=false;for(File v:StorageAccess.volumes(a))if(v.getPath().equals(dir.getPath()))root=true;
            final File up=root?null:dir.getParentFile();rows.addView(row("back",AppLanguage.text(a,"Pasta acima"),()->go(up)));
            int shown=0;for(File f:folders){if(shown++>=300)break;rows.addView(row("folder",f.getName(),()->go(f)));}
            dialog.refit();
        }
        View row(String icon,String label,Runnable action){
            LinearLayout r=new LinearLayout(a);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(a.dp(8),a.dp(10),a.dp(8),a.dp(10));r.setMinimumHeight(a.dp(48));
            r.addView(new Icons(a,icon,pal.accent),new LinearLayout.LayoutParams(a.dp(22),a.dp(22)));TextView t=new TextView(a);t.setText(label);t.setTextSize(15);t.setTextColor(pal.ink);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setPadding(a.dp(12),0,0,0);r.addView(t,new LinearLayout.LayoutParams(0,-2,1));
            r.setContentDescription(label);r.setOnClickListener(v->action.run());Motion.touch(r);return r;
        }
    }
}
