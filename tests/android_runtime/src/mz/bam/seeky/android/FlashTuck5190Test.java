package mz.bam.seeky.android;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.19.0: o botão Flash recolhe-se sozinho para a margem após 10 s, com a lupa visível. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, qualifiers = "w360dp-h800dp-mdpi", shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FlashTuck5190Test {
    ReferenceDesign5150Test f;
    MainActivity a;
    ServiceController<QuickSearchService> driver;
    QuickSearchService s;

    @Before public void before() throws Exception { f = new ReferenceDesign5150Test(); f.before(); a = f.a; }
    @After public void after() throws Exception { if (driver != null) driver.destroy(); f.after(); }

    void clock(long millis) { shadowOf(Looper.getMainLooper()).idleFor(millis, TimeUnit.MILLISECONDS); }

    @Test public void bubbleTucksAfterTenSecondsByDefault() throws Exception {
        a.prefs.edit().remove("flash_auto_hide").remove("flash_idle_seconds").remove("flash_corner").apply();
        assertTrue("auto-hide is on by default", FlashSettings.autoHide(a));
        assertEquals(10, FlashSettings.delay(a));
        driver = Robolectric.buildService(QuickSearchService.class).create();
        s = driver.get();
        s.bubble();
        clock(9000);
        assertFalse("still fully visible before 10 s", s.tucked);
        clock(1500);
        assertTrue("tucked after 10 s of inactivity", s.tucked);
        int width = s.getResources().getDisplayMetrics().widthPixels;
        assertTrue("half of the button is beyond the edge", s.params.x + s.params.width > width || s.params.x < 0);
        FlashBubbleView bubble = (FlashBubbleView) s.window;
        clock(300);
        View icon = bubble.circle.getChildAt(0);
        assertNotEquals("the magnifier moves into the visible half", 0f, icon.getTranslationX(), .01f);
        assertTrue("tucked button is slightly translucent", bubble.circle.getAlpha() < 1f);
        bubble.measure(View.MeasureSpec.makeMeasureSpec(s.params.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(s.params.height, View.MeasureSpec.EXACTLY));
        bubble.layout(0, 0, s.params.width, s.params.height);
        f.capture(bubble, "flash-recolhido-5190", s.params.width, s.params.height);
    }

    @Test public void userCanStillDisableTucking() {
        a.prefs.edit().putBoolean("flash_auto_hide", false).apply();
        driver = Robolectric.buildService(QuickSearchService.class).create();
        s = driver.get();
        s.bubble();
        clock(31000);
        assertFalse(s.tucked);
    }
}
