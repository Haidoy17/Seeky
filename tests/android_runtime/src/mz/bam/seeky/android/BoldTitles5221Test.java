package mz.bam.seeky.android;

import android.app.Application;
import android.graphics.Typeface;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** 5.22.1: os títulos principais ficam em negrito em todos os temas (pedido do autor). */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BoldTitles5221Test {
    @Test public void everyThemeUsesBoldTitlesAtEverySize() {
        Application app = RuntimeEnvironment.getApplication();
        for (String name : AppPalette.MODERN) {
            app.getSharedPreferences("seeky", 0).edit().putString("theme", name).commit();
            for (float size : new float[]{14, 20, 24, 26}) {
                TextView t = new TextView(app);
                ThemeStyle.title(t, size);
                Typeface face = t.getTypeface();
                assertNotNull(name, face);
                assertTrue(name + " " + size + "sp", face.isBold());
            }
        }
    }

    @Test public void titleTypefacesAreBold() {
        assertTrue(ThemeStyle.BOLD.isBold());
        assertTrue(ThemeStyle.SERIF_BOLD.isBold());
        assertTrue(ThemeStyle.CONDENSED.isBold());
    }
}
