package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.util.*;import java.util.concurrent.TimeUnit;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.android.controller.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;

/** 5.44: Android/data and Android/obb through Shizuku — the file service, its limits and the screens. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w360dp-h800dp-mdpi",application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppData544Test {
    final Application app=RuntimeEnvironment.getApplication();
    File root;SeekyShellService service;String data,obb;
    @Before public void setup()throws Exception{
        root=Files.createTempDirectory("seeky-storage").toFile();
        File files=new File(root,"Android/data/com.example.jogo/files");files.mkdirs();
        Files.write(new File(files,"save.dat").toPath(),"nivel=7".getBytes(StandardCharsets.UTF_8));
        new File(root,"Android/data/com.example.jogo/cache").mkdirs();
        new File(root,"Android/obb/com.example.jogo").mkdirs();
        Files.write(new File(root,"Android/obb/com.example.jogo/main.1.obb").toPath(),new byte[300000]);
        new File(root,"DCIM").mkdirs();Files.write(new File(root,"DCIM/foto.jpg").toPath(),new byte[]{1,2,3});
        service=new SeekyShellService(new String[]{root.getPath()});
        data=root.getPath()+"/Android/data";obb=root.getPath()+"/Android/obb";
    }
    @After public void close(){ShizukuFiles.service=null;UiDialog.dismissAll(null);}

    @Test public void theServiceWorksOnlyInsideTheAppsFolders()throws Exception{
        List<ShellPaths.Entry> apps=ShizukuFiles.list(service,data);
        assertEquals(1,apps.size());assertEquals("com.example.jogo",apps.get(0).name);assertTrue(apps.get(0).folder);
        List<ShellPaths.Entry> inside=ShizukuFiles.list(service,data+"/com.example.jogo");
        assertEquals(Arrays.asList("cache","files"),Arrays.asList(inside.get(0).name,inside.get(1).name));
        ShellPaths.Entry save=ShizukuFiles.list(service,data+"/com.example.jogo/files").get(0);assertEquals("save.dat",save.name);assertEquals(7,save.size);
        // Read and write go through a file descriptor from the service.
        File out=new File(app.getCacheDir(),"save-copia.dat");ShizukuFiles.copyOut(service,data+"/com.example.jogo/files/save.dat",out);
        assertEquals("nivel=7",new String(Files.readAllBytes(out.toPath()),StandardCharsets.UTF_8));
        ShizukuFiles.copyIn(service,new ByteArrayInputStream("nivel=99".getBytes(StandardCharsets.UTF_8)),data+"/com.example.jogo/files/save.dat");
        assertEquals("nivel=99",new String(Files.readAllBytes(new File(root,"Android/data/com.example.jogo/files/save.dat").toPath()),StandardCharsets.UTF_8));
        // New folder, rename, copy a whole folder out, delete.
        ShizukuFiles.mkdir(service,data+"/com.example.jogo/files/copias");
        ShizukuFiles.rename(service,data+"/com.example.jogo/files/copias",data+"/com.example.jogo/files/backups");
        assertTrue(new File(root,"Android/data/com.example.jogo/files/backups").isDirectory());
        File folderOut=new File(app.getCacheDir(),"jogo-obb");int copied=ShizukuFiles.copyFolderOut(service,obb+"/com.example.jogo",folderOut,new int[]{100});
        assertEquals(1,copied);assertEquals(300000,new File(folderOut,"main.1.obb").length());
        assertEquals(1,ShizukuFiles.delete(service,data+"/com.example.jogo/files/backups"));
        assertFalse(new File(root,"Android/data/com.example.jogo/files/backups").exists());
        // Nothing outside Android/data and Android/obb, and the two areas themselves are protected.
        for(String bad:new String[]{root.getPath()+"/DCIM",root.getPath()+"/DCIM/foto.jpg",data+"/../../DCIM","/data/data/mz.bam.seeky.v51"}){
            try{ShizukuFiles.list(service,bad);fail("listou "+bad);}catch(IllegalArgumentException|IOException expected){}
        }
        // Even a raw request that skips the Seeky's own check is refused by the service.
        Parcel d=Parcel.obtain(),r=Parcel.obtain();d.writeInterfaceToken(SeekyShellService.DESCRIPTOR);d.writeString(root.getPath()+"/DCIM/foto.jpg");
        service.transact(SeekyShellService.DELETE,d,r,0);r.setDataPosition(0);assertEquals(SeekyShellService.FAILED,r.readInt());assertTrue(r.readString().contains("Android/data"));d.recycle();r.recycle();
        assertTrue("a foto fora da área continua lá",new File(root,"DCIM/foto.jpg").exists());
        try{ShizukuFiles.delete(service,data);fail();}catch(IOException e){assertTrue(e.getMessage().contains("não podem ser apagadas"));}
        try{ShizukuFiles.rename(service,obb,data+"/x");fail();}catch(IOException e){assertTrue(e.getMessage().contains("não podem mudar de nome"));}
        assertTrue(new File(root,"Android/data").isDirectory()&&new File(root,"Android/obb").isDirectory());
    }

    void drain(AppDataActivity a)throws Exception{for(int i=0;i<5;i++){a.worker.submit(()->{}).get(20,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idleFor(500,TimeUnit.MILLISECONDS);}}
    static void capture(View v,String name,int height)throws Exception{
        v.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,360,height);
        Bitmap b=Bitmap.createBitmap(360,height,Bitmap.Config.ARGB_8888);new Canvas(b).drawColor(0xff2a2d33);v.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(System.getProperty("seeky.screenshot")+"-5440-"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}
    }

    @Test public void withoutShizukuTheScreenGuidesTheSetUpAndThenBrowsesTheAppsFolders()throws Exception{
        ActivityController<AppDataActivity> c=Robolectric.buildActivity(AppDataActivity.class);AppDataActivity a=c.get();a.setTheme(android.R.style.Theme_Material_NoActionBar);c.create().start().resume().visible();
        try{
            drain(a);
            String labels=BooksToolsTest.labels(a.getWindow().getDecorView());
            for(String s:new String[]{"Pastas das aplicações","Porque é preciso o Shizuku","Instalar o Shizuku","Google Play","GitHub","Iniciar o Shizuku","Autorizar o Seeky","Verificar outra vez"})assertTrue(s+" em "+labels,labels.contains(s));
            assertEquals(ShizukuFiles.State.NOT_INSTALLED,ShizukuFiles.state(a));
            capture(a.getWindow().getDecorView(),"shizuku-passos",1100);
            // Once Shizuku is ready, the folders are listed with the app's own service.
            ShellPaths.roots=new String[]{root.getPath()};ShizukuFiles.service=service;a.folder=ShizukuFiles.data();a.browse();drain(a);
            labels=BooksToolsTest.labels(a.getWindow().getDecorView());
            assertTrue(labels,labels.contains("Android/data")&&labels.contains("Android/obb")&&labels.contains("com.example.jogo"));
            a.folder=ShellPaths.child(ShizukuFiles.data(),"com.example.jogo");a.browse();drain(a);
            labels=BooksToolsTest.labels(a.getWindow().getDecorView());
            assertTrue(labels,labels.contains("files")&&labels.contains("cache")&&labels.contains("Importar para aqui")&&labels.contains("Nova pasta"));
            a.folder=ShellPaths.child(a.folder,"files");a.browse();drain(a);
            labels=BooksToolsTest.labels(a.getWindow().getDecorView());assertTrue(labels,labels.contains("save.dat")&&labels.contains("7 B"));
            capture(a.getWindow().getDecorView(),"android-data",800);
            a.onBackPressed();drain(a);assertEquals(ShellPaths.child(ShizukuFiles.data(),"com.example.jogo"),a.folder);
        }finally{c.pause().stop().destroy();}
    }
}
