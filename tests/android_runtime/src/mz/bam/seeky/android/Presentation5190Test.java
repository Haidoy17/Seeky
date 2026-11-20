package mz.bam.seeky.android;

import android.app.*;
import android.os.Looper;
import android.view.View;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.19.0: entradas em Mais, painéis de Novidades/Bloqueio/Cópia automática e ecrã de bloqueio, com capturas. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Presentation5190Test {
    ReferenceDesign5150Test f;
    MainActivity a;

    @Before public void before() throws Exception { f = new ReferenceDesign5150Test(); f.before(); a = f.a; }
    @After public void after() throws Exception { UiPanel.dismissAll(a); f.after(); }

    UiPanel latest() { shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS); return UiPanel.open.get(a).iterator().next(); }

    @Test public void morePageOffersTheNewFeatures() throws Exception {
        a.tab = "Ferramentas"; a.render(); f.drain();
        String labels = BooksToolsTest.labels(a.shell);
        assertTrue(labels.contains("Novidades da 5.34"));
        assertTrue(labels.contains("Bloqueio da aplicação"));
        assertTrue(labels.contains("Cópia automática"));
    }

    @Test public void whatsNewPanelLinksToEachFeature() throws Exception {
        WhatsNew.show(a);
        UiPanel p = latest();
        String labels = BooksToolsTest.labels(p.root);
        for (String item : new String[]{"Transferir por Wi-Fi", "Pomodoro", "Bloqueio da aplicação", "Cópia automática"}) assertTrue(item, labels.contains(item));
        f.capture(p.root, "novidades-5190", 340, 760);
    }

    @Test public void lockAndBackupPanelsExplainThemselves() throws Exception {
        AppLock.show(a);
        UiPanel lock = latest();
        assertTrue(BooksToolsTest.labels(lock.root).contains("Activar bloqueio"));
        f.capture(lock.root, "bloqueio-5190", 340, 520);
        UiPanel.dismissAll(a);
        AutoBackup.show(a);
        UiPanel backup = latest();
        assertTrue(BooksToolsTest.labels(backup.root).contains("Escolher pasta"));
        f.capture(backup.root, "copia-automatica-5190", 340, 460);
    }

    @Test public void whatsNewAppearsOnceAfterUpdateOnly() {
        a.prefs.edit().remove(WhatsNew.KEY).apply();
        WhatsNew.maybeShow(a);
        assertEquals(MainActivity.VERSION, a.prefs.getString(WhatsNew.KEY, ""));
        WhatsNew.maybeShow(a);
        assertEquals(MainActivity.VERSION, a.prefs.getString(WhatsNew.KEY, ""));
    }

    @Test public void lockScreenIsOpaqueAndBranded() throws Exception {
        AppLock.reset();
        Activity lock = Robolectric.buildActivity(LockActivity.class).setup().get();
        View root = lock.getWindow().getDecorView();
        String labels = BooksToolsTest.labels(root);
        assertTrue(labels.contains("Seeky bloqueado"));
        assertTrue(labels.contains("Desbloquear"));
        assertTrue((lock.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0);
        f.capture(((android.view.ViewGroup) root.findViewById(android.R.id.content)).getChildAt(0), "ecra-bloqueio-5190", 360, 800);
    }
}
