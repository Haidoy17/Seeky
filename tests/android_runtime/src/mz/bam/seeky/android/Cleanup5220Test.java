package mz.bam.seeky.android;

import android.app.Application;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.22: limpeza (cache, APKs, duplicados, Android), widget de pesquisa e categorias, entradas em Mais. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Cleanup5220Test {
    ReferenceDesign5150Test f;
    MainActivity a;

    @Before public void before() throws Exception { f = new ReferenceDesign5150Test(); f.before(); a = f.a; }
    @After public void after() throws Exception { UiPanel.dismissAll(a); f.after(); }

    void settle() throws Exception { for (int i = 0; i < 3; i++) { a.finder.submit(() -> {}).get(15, TimeUnit.SECONDS); shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS); } }

    @Test public void cleanerPanelOffersEveryOptionAndClearsOnlyOldCache() throws Exception {
        File old = new File(a.getCacheDir(), "old-preview.bin"), fresh = new File(a.getCacheDir(), "fresh.bin");
        try (FileOutputStream o = new FileOutputStream(old)) { o.write(new byte[4096]); }
        try (FileOutputStream o = new FileOutputStream(fresh)) { o.write(new byte[16]); }
        old.setLastModified(System.currentTimeMillis() - 3_600_000);
        StorageCleaner.show(a);
        settle();
        UiPanel p = UiPanel.open.get(a).iterator().next();
        String labels = BooksToolsTest.labels(p.root);
        for (String item : new String[]{"Limpar cache do Seeky", "APKs duplicados ou já instalados", "Ficheiros duplicados", "Miniaturas da galeria", "Libertar espaço no telefone", "Cache de outras aplicações"})
            assertTrue(item, labels.contains(item));
        f.capture(p.root, "limpeza-5220", 340, 760);
        UiPanel.dismissAll(a);
        StorageCleaner.clearCache(a);
        settle();
        assertFalse("old cache removed", old.exists());
        assertTrue("files in use are kept", fresh.exists());
    }

    @Test public void morePageHasStorageSection() throws Exception {
        a.tab = "Ferramentas"; a.render(); f.drain();
        String labels = BooksToolsTest.labels(a.shell);
        assertTrue(labels.contains("Limpeza e cache"));
        assertTrue(labels.contains("APKs duplicados"));
        assertTrue(labels.contains("Widget no ecrã inicial"));
    }

    @Test public void widgetShortcutsOpenTheRightPlace() throws Exception {
        android.app.PendingIntent pdf = SeekyWidget.open(a, "PDF", 70);
        android.content.Intent sent = shadowOf(pdf).getSavedIntent();
        assertEquals("PDF", sent.getStringExtra(SeekyWidget.EXTRA));
        assertEquals(MainActivity.class.getName(), sent.getComponent().getClassName());
        assertTrue(shadowOf(pdf).isActivityIntent());
        for (String category : new String[]{"PDF", "Imagens", "Vídeos", "Áudio"}) {
            a.handleWidget(new android.content.Intent().putExtra(SeekyWidget.EXTRA, category));
            assertEquals("Pesquisa", a.tab);
            assertEquals(category, a.category);
        }
        a.handleWidget(new android.content.Intent().putExtra(SeekyWidget.EXTRA, "search"));
        assertEquals("Tudo", a.category);
        a.handleWidget(new android.content.Intent().putExtra(SeekyWidget.EXTRA, "outra-coisa"));
        assertEquals("unknown shortcuts are ignored", "Tudo", a.category);
    }
}
