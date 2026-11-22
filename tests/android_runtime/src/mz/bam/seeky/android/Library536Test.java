package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.content.pm.ProviderInfo;import android.database.Cursor;import android.graphics.*;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.util.*;import java.util.concurrent.TimeUnit;
import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.36: wooden bookcase, the "Importar livros" dialog, folder import with filters and the new book formats. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h720dp-mdpi",application=Application.class,shadows={ReferenceDesign5150Test.LocalStorage.class,PageTurnTest.EpubWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Library536Test {
    ActivityController<MainActivity> controller;MainActivity a;BookLibrary b;
    static final String[] TITLES={"Dom Casmurro","O Cântico de Maria","365 Dias de Fogo","A arte de ler","Fé e confissão","Leitura e produção de textos","Prazer em conhecer"};
    static final int[] PROGRESS={0,6,74,89,3,38,18};

    void main(){controller=Robolectric.buildActivity(MainActivity.class);a=controller.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);a.prefs=a.getSharedPreferences("seeky",0);a.colors();a.tab="Livros";a.thumbnails=new Thumbnails(a);a.body=a.column();a.body.setBackgroundColor(a.bg);a.setContentView(a.body);b=new BookLibrary(a);a.books=b;}
    void drain()throws Exception{b.worker.submit(()->{}).get(10,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
    @After public void close(){if(b!=null){b.close();a.thumbnails.close();UiDialog.dismissAll(a);UiPanel.dismissAll(a);a.dead=true;a.worker.shutdown();a.finder.shutdown();a.searchJobs.close();}}
    long material(String title,int percent){try(StudyStore store=new StudyStore(RuntimeEnvironment.getApplication())){return store.save("materials",0,StudyStore.values("subject",0,"title",title,"author","Autor","name",title+".txt","mime","text/plain","path","/private/test","hash",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""),"bytes",24,"progress",percent,"status",percent==100?"Concluído":percent==0?"Por estudar":"A estudar","created",1,"updated",1));}}
    void capture(View v,String name,int height)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,360,height);
        Bitmap bmp=Bitmap.createBitmap(360,height,Bitmap.Config.ARGB_8888);new Canvas(bmp).drawColor(0xff2a2d33);v.draw(new Canvas(bmp));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5360-"+name+".png")){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    static <T> List<T> all(View v,Class<T> type,List<T> out){if(type.isInstance(v))out.add(type.cast(v));if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)all(g.getChildAt(i),type,out);}return out;}

    @Test public void shelfIsAWoodenBookcaseWithPageEdgesAndShelvesDownToTheBottom()throws Exception{
        main();assertEquals("o modo estante passa a ser o normal",0,b.mode);
        Set<String> favorites=new HashSet<>();for(int i=0;i<TITLES.length;i++){long id=material(TITLES[i],PROGRESS[i]);if(i==1)try(StudyStore s=new StudyStore(a)){favorites.add(s.get("materials",id).s("hash"));}}
        a.getSharedPreferences("books",0).edit().putStringSet("favorites",favorites).apply();
        b.show();drain();
        assertEquals(TITLES.length,b.books.size());
        assertEquals(TITLES.length,all(a.body,BookShelfRow.Body.class,new ArrayList<>()).size());
        assertEquals(TITLES.length,all(a.body,BookShelfRow.Sheen.class,new ArrayList<>()).size());
        assertEquals(TITLES.length,all(a.body,BookProgressBadge.class,new ArrayList<>()).size());
        List<BookShelfRow> rows=all(a.body,BookShelfRow.class,new ArrayList<>());assertTrue("prateleiras vazias até ao fundo: "+rows.size(),rows.size()>=4);
        assertNull("sem botões de página quando tudo cabe",VoiceToolsTest.findText(a.body,"Seguinte  ›"));
        assertNotNull(PageTurnTest.findDescription(a.body,"Escolher grupo de livros"));
        String[] woods={"nogueira","carvalho","cerejeira","ebano","laca"};
        for(int w=0;w<woods.length;w++){a.getSharedPreferences("books",0).edit().putInt("shelf_wood",w).apply();b.draw();capture(a.body,"estante-"+woods[w],720);}
        a.getSharedPreferences("books",0).edit().putInt("shelf_wood",0).apply();b.draw();
        View cover=all(a.body,BookShelfRow.Body.class,new ArrayList<>()).get(0).getChildAt(0);assertTrue(cover.performLongClick());shadowOf(Looper.getMainLooper()).idle();
        assertFalse("o menu do livro abre com toque longo",UiPanel.open.getOrDefault(a,Collections.emptySet()).isEmpty());
    }
    @Test public void moreThanThirtyBooksGetPageSignsInsideTheBookcase()throws Exception{
        main();for(int i=0;i<34;i++)material(String.format(Locale.ROOT,"Livro %02d",i),i*3);
        b.show();drain();assertEquals(30,b.books.size());
        View next=PageTurnTest.findDescription(a.body,"Seguinte");assertNotNull(next);assertNotNull(VoiceToolsTest.findText(a.body,"1 / 2"));
        next.performClick();drain();assertEquals(4,b.books.size());assertNotNull(VoiceToolsTest.findText(a.body,"2 / 2"));
        b.mode=1;b.page=0;b.reload();drain();assertEquals(BookQuery.PAGE,b.books.size());
    }
    @Test public void importDialogOffersFolderFormatsSizeFavoriteAndCollection()throws Exception{
        main();a.studies=new StudyHub(a);
        try{
            File books=new File(Environment.getExternalStorageDirectory(),"Livros");books.mkdirs();
            b.pick();shadowOf(Looper.getMainLooper()).idle();
            UiDialog d=(UiDialog)ShadowDialog.getLatestDialog();String labels=BooksToolsTest.labels(d.root);
            for(String s:new String[]{"Importar livros","Uma pasta","Escolher ficheiros","/sdcard/Livros","Incluir subpastas","Formatos","EPUB","PDF","DOCX/ODT/RTF","TXT/MD","HTML/MHTML","FB2","MOBI/AZW3/PRC","CBZ (BD)","Tamanho do ficheiro >","KB","Favorito","Importar","Cancelar"})assertTrue(s+" em "+labels,labels.contains(s));
            capture(d.root,"importar-livros",720);
            PageTurnTest.findDescription(d.root,"Escolher ficheiros").performClick();shadowOf(Looper.getMainLooper()).idle();
            assertNull("tamanho só para pastas",VoiceToolsTest.findText(d.root,"Tamanho do ficheiro >").isShown()?"visível":null);
            for(CheckBox c:all(d.root,CheckBox.class,new ArrayList<>()))if(c.getText().toString().equals("Favorito"))c.setChecked(true);
            for(EditText e:all(d.root,EditText.class,new ArrayList<>()))if(e.getHint()!=null&&e.getHint().toString().startsWith("Colecção"))e.setText("Clássicos");
            capture(d.root,"importar-ficheiros",720);
            d.getButton(-1).performClick();shadowOf(Looper.getMainLooper()).idle();
            Intent picker=shadowOf(a).getNextStartedActivity();assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.getAction());assertTrue(picker.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));
            assertNull("FB2, MOBI e CBZ: selector sem filtro",picker.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
            JSONObject pending=BookImport.pending(a,true,new JSONObject().put("books",true));assertTrue(pending.optBoolean("favorite"));assertEquals("Clássicos",pending.optString("collection"));
            assertFalse("as opções valem uma vez",BookImport.pending(a,true,new JSONObject()).has("favorite"));
            Set<String> plain=new HashSet<>(Arrays.asList("pdf","epub","docx"));List<String> mimes=Arrays.asList(BookImport.mimes(plain));assertTrue(mimes.contains("application/pdf")&&mimes.contains("application/epub+zip")&&mimes.contains("application/vnd.oasis.opendocument.text"));
            b.pick();shadowOf(Looper.getMainLooper()).idle();d=(UiDialog)ShadowDialog.getLatestDialog();assertFalse("lembra o modo ficheiros",VoiceToolsTest.findText(d.root,"Incluir subpastas").isShown());d.dismiss();
        }finally{a.studies.close();}
    }
    @Test public void folderImportKeepsChosenFormatsSizesAndSubfoldersAndStartsAtOnce()throws Exception{
        try(WorkTestSupport w=new WorkTestSupport()){
            File root=new File(Environment.getExternalStorageDirectory(),"Livros536");File sub=new File(root,"Bíblia/Estudos");sub.mkdirs();
            Files.write(new File(root,"Dom Casmurro.fb2").toPath(),EbookFixtures.fb2("Dom Casmurro").getBytes(StandardCharsets.UTF_8));
            Files.write(new File(root,"curto.txt").toPath(),"pequeno".getBytes(StandardCharsets.UTF_8));
            StringBuilder long_=new StringBuilder();for(int i=0;i<900;i++)long_.append("Linha de um texto longo para importar. ");Files.write(new File(root,"longo.txt").toPath(),long_.toString().getBytes(StandardCharsets.UTF_8));
            Files.write(new File(root,"foto.jpg").toPath(),new byte[4000]);Files.write(new File(root,"documento.pdf").toPath(),new byte[4000]);
            Files.write(new File(sub,"Carta.rtf").toPath(),EbookFixtures.rtf().getBytes(StandardCharsets.ISO_8859_1));
            JSONObject options=new JSONObject().put("formats",new JSONArray().put("fb2").put("txt").put("docx")).put("min_bytes",1024).put("recursive",true).put("favorite",true).put("collection","Estante 5.36");
            String id=dirJob(w,root,options);w.run(id);
            try(TaskStore db=new TaskStore(w.app)){List<StudyStore.Row> items=db.items(id,null,0,20);Set<String> names=new TreeSet<>();for(StudyStore.Row r:items)names.add(r.s("name"));
                assertEquals("formatos, tamanho e subpastas: "+names,3,items.size());assertTrue(names.toString(),names.contains("Dom Casmurro.epub")&&names.contains("longo.txt")&&names.contains("Carta.epub"));
                assertEquals(3,db.count(id,"done"));assertNotEquals("paused",db.job(id).s("state"));}
            Set<String> favorites=w.app.getSharedPreferences("books",0).getStringSet("favorites",Collections.emptySet());assertEquals(3,favorites.size());
            try(StudyStore s=new StudyStore(w.app);PlusStore plus=new PlusStore(w.app)){
                List<StudyStore.Row> fb2=s.rows("materials","name=?",new String[]{"Dom Casmurro.epub"},null,1);assertEquals(1,fb2.size());assertEquals("application/epub+zip",fb2.get(0).s("mime"));assertEquals("Dom Casmurro",fb2.get(0).s("title"));assertEquals("Machado de Assis",fb2.get(0).s("author"));
                try(BookArchive book=new BookArchive(new File(fb2.get(0).s("path")),true)){assertEquals(2,book.chapters.size());assertTrue(book.chapter(0).contains("Uma noite destas"));assertTrue(book.chapter(1).contains("Segunda parte"));}
                assertEquals(3,plus.hashes("collection",""+BookCategories.collection(plus,"Estante 5.36")).size());
            }
            String again=dirJob(w,root,new JSONObject(options.toString()).put("recursive",false));w.run(again);
            try(TaskStore db=new TaskStore(w.app)){assertEquals("sem subpastas",2,db.count(again,null));assertEquals("repetidos reconhecidos, mesmo convertidos",2,db.count(again,"duplicate"));}
            assertEquals(3,w.materials());
        }
    }
    static String dirJob(WorkTestSupport w,File dir,JSONObject options)throws Exception{String id=w.job("import",new JSONObject(options.toString()).put("subject",0).put("books",true).put("dir",dir.getPath()).put("scan_complete",false).put("auto",true));try(TaskStore db=new TaskStore(w.app)){db.getWritableDatabase().insertOrThrow("scan_nodes",null,StudyStore.values("job",id,"uri",Uri.fromFile(dir).toString()));}return id;}
    @Test public void chosenFilesInNewFormatsAreConvertedAndDuplicatesFound()throws Exception{
        try(WorkTestSupport w=new WorkTestSupport()){
            File fb2=new File(w.app.getCacheDir(),"src.fb2");Files.write(fb2.toPath(),EbookFixtures.fb2("Memórias Póstumas").getBytes(StandardCharsets.UTF_8));
            String id=w.job("import",new JSONObject().put("subject",0).put("books",true).put("scan_complete",true));try(TaskStore db=new TaskStore(w.app)){db.add(id,0,w.source("fb2","Memórias Póstumas.fb2",fb2).toString(),"",0,0);db.totals(id);}
            w.run(id);assertEquals(1,w.materials());
            try(StudyStore s=new StudyStore(w.app)){StudyStore.Row row=s.rows("materials",null,null,null,1).get(0);assertEquals("Memórias Póstumas.epub",row.s("name"));assertEquals("Memórias Póstumas",row.s("title"));assertTrue(new File(row.s("path")).getName().endsWith(".epub"));}
            String again=w.job("import",new JSONObject().put("subject",0).put("books",true).put("scan_complete",true));try(TaskStore db=new TaskStore(w.app)){db.add(again,0,w.source("fb2b","Memórias Póstumas.fb2",fb2).toString(),"",0,0);db.totals(again);}
            w.run(again);assertEquals(1,w.materials());try(TaskStore db=new TaskStore(w.app)){assertEquals(1,db.count(again,"duplicate"));}
        }
    }
    public static class Provider extends ContentProvider{static File file;public boolean onCreate(){return true;}public String getType(Uri u){return "application/octet-stream";}public ParcelFileDescriptor openFile(Uri u,String m)throws FileNotFoundException{return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);}public Cursor query(Uri u,String[] p,String s,String[] args,String o){return null;}public Uri insert(Uri u,ContentValues v){return null;}public int update(Uri u,ContentValues v,String s,String[] args){return 0;}public int delete(Uri u,String s,String[] args){return 0;}}
    @Test public void readerOpensAnFb2FromTheFilesAsABook()throws Exception{
        Application app=RuntimeEnvironment.getApplication();Provider.file=new File(app.getCacheDir(),"loose.fb2");Files.write(Provider.file.toPath(),EbookFixtures.fb2("Quincas Borba").getBytes(StandardCharsets.UTF_8));
        ProviderInfo info=new ProviderInfo();info.authority="seeky.fb2.reader";Provider p=new Provider();p.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(info.authority,p);
        ActivityController<ReaderActivity> rc=Robolectric.buildActivity(ReaderActivity.class,new Intent(app,ReaderActivity.class).setDataAndType(Uri.parse("content://seeky.fb2.reader/livro"),"application/octet-stream").putExtra("name","Quincas Borba.fb2"));
        ReaderActivity r=rc.get();r.setTheme(android.R.style.Theme_Material_NoActionBar);
        try{
            app.getSharedPreferences("seeky",0).edit().putBoolean("reader_original_content://seeky.fb2.reader/livro",false).apply();
            rc.create().start().resume().visible();for(int i=0;i<6;i++){r.worker.submit(()->{}).get(15,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
            assertEquals("epub",r.kind);assertNotNull("o FB2 abre como livro",r.book);assertEquals("Quincas Borba",r.book.title);assertTrue(r.book.chapters.size()>=2);
            assertNotNull(r.converted);assertTrue(r.converted.getName().endsWith(".epub"));
        }finally{rc.pause().stop().destroy();}
        r.worker.awaitTermination(20,TimeUnit.SECONDS);
        assertFalse("cópia convertida apagada ao fechar",r.converted.exists());
    }
}

/** Small books for the tests above. */
final class EbookFixtures {
    static String fb2(String title){
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\"><description><title-info><author><first-name>Machado</first-name><last-name>de Assis</last-name></author><book-title>"+title+"</book-title><lang>pt</lang></title-info></description>"
            +"<body><section><title><p>Capítulo I</p></title><p>Uma noite destas, vindo da cidade para o Engenho Novo, encontrei no trem um rapaz aqui do bairro. Isaías 3.1-7 fala disto.</p>"+String.join("",java.util.Collections.nCopies(20,"<p>Outro parágrafo da narrativa para dar corpo ao livro de teste.</p>"))+"</section><section><title><p>Capítulo II</p></title><p>Segunda parte do livro.</p></section></body></FictionBook>";
    }
    static String rtf(){StringBuilder s=new StringBuilder("{\\rtf1\\ansi\\ansicpg1252{\\info{\\title Carta}}");for(int i=0;i<60;i++)s.append("Par\\'e1grafo n\\'famero ").append(i).append(" da carta.\\par ");return s.append("}").toString();}
}
