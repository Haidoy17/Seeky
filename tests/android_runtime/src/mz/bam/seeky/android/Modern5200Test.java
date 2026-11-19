package mz.bam.seeky.android;

import android.app.Application;
import android.os.Looper;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.20: visual moderno (Areia, Floresta, Carvão) — contraste, ícones de menu e capturas dos ecrãs principais. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Modern5200Test {
    ReferenceDesign5150Test f;
    MainActivity a;

    @Before public void before() throws Exception { f = new ReferenceDesign5150Test(); f.before(); a = f.a; }
    @After public void after() throws Exception { UiPanel.dismissAll(a); f.after(); }

    void theme(String name) throws Exception {
        a.prefs.edit().putString("theme", name).apply();
        a.colors();
        a.render();
        f.drain();
    }

    static String slug(String theme) { return theme.equals("Carvão") ? "carvao" : theme.toLowerCase(java.util.Locale.ROOT); }

    void books() {
        String[] titles = {"A arte de ler", "Cursos e módulos", "Fé e conhecimento"};
        int[] progress = {38, 100, 7};
        try (StudyStore store = new StudyStore(a)) {
            for (int n = 0; n < titles.length; n++)
                store.save("materials", 0, StudyStore.values("subject", 0, "title", titles[n], "author", "Autor de teste", "name", titles[n] + ".pdf", "mime", "application/pdf",
                        "path", "/private/modern-" + n + ".pdf", "hash", "modern" + n, "bytes", 100, "progress", progress[n], "status", progress[n] == 100 ? "Concluído" : "A estudar", "created", n + 1, "updated", n + 1));
        }
    }

    @Test public void modernPalettesAreReadable() {
        for (String name : new String[]{AppPalette.SAND, AppPalette.FOREST, AppPalette.CHARCOAL}) {
            AppPalette p = new AppPalette(name);
            assertTrue(name, p.modern);
            assertTrue(name + " text", AppPalette.contrast(p.ink, p.bg) >= 7);
            assertTrue(name + " secondary text", AppPalette.contrast(p.muted, p.bg) >= 4.5 && AppPalette.contrast(p.muted, p.surface) >= 4.5);
            assertTrue(name + " accent", AppPalette.contrast(p.accent, p.surface) >= 3);
            assertTrue(name + " button text", AppPalette.contrast(p.onAccent, p.accent) >= 4.5);
        }
        assertEquals(AppPalette.SAND, AppPalette.DEFAULT);
        assertFalse(new AppPalette("Claro").modern);
    }

    @Test public void menuItemsGetMeaningfulIcons() {
        assertEquals("palette", UiDialog.symbol("Cores"));
        assertEquals("globe", UiDialog.symbol("Idioma: Português"));
        assertEquals("lock", UiDialog.symbol("Bloqueio da aplicação"));
        assertEquals("restore", UiDialog.symbol("Cópia automática"));
        assertEquals("speed", UiDialog.symbol("Velocidade"));
        assertEquals("repeat", UiDialog.symbol("Repetir"));
        assertEquals("share", UiDialog.symbol("Abrir fora"));
        assertEquals("stop", UiDialog.symbol("Parar reprodução"));
        assertEquals("contents", UiDialog.symbol("Capítulos e títulos"));
        assertEquals("clock", UiDialog.symbol("Temporizador de sono"));
        assertEquals("eye", UiDialog.symbol("Ficheiros ocultos"));
        assertEquals("motion", UiDialog.symbol("Animações"));
        assertEquals("trash", UiDialog.symbol("Limpar histórico de pesquisas"));
        assertEquals("dot", UiDialog.symbol("Algo sem correspondência"));
    }

    @Test public void modernScreensHaveNativePreviews() throws Exception {
        books();
        for (String name : new String[]{AppPalette.SAND, AppPalette.FOREST, AppPalette.CHARCOAL}) {
            theme(name);
            a.tab = "Início"; a.render(); f.drain();
            assertTrue(BooksToolsTest.labels(a.shell).contains("As suas categorias"));
            f.capture(a.shell, "moderno-" + slug(name) + "-inicio", 360, 800);
            a.tab = "Livros"; a.books.mode = 1; a.render(); f.drain();
            f.capture(a.shell, "moderno-" + slug(name) + "-livros", 360, 800);
        }
        theme(AppPalette.SAND);
        a.tab = "Livros"; a.books.mode = 2; a.render(); f.drain();
        f.capture(a.shell, "moderno-areia-livros-lista", 360, 800);
        a.tab = "Explorar"; a.render(); f.drain();
        f.capture(a.shell, "moderno-areia-explorar", 360, 800);
        a.tab = "Início"; a.render(); f.drain();
        a.moreNavigation();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        UiPanel more = UiPanel.open.get(a).iterator().next();
        f.capture(more.root, "moderno-areia-mais", 360, 480);
        UiPanel.dismissAll(a);
        a.settings();
        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS);
        UiDialog prefs = (UiDialog) ShadowDialog.getLatestDialog();
        String labels = BooksToolsTest.labels(prefs.root);
        assertTrue(labels.contains("Cores"));
        f.capture(prefs.root, "moderno-areia-preferencias", 360, 760);
        prefs.dismiss();
    }
}
