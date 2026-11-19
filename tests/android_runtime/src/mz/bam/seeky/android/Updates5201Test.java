package mz.bam.seeky.android;

import android.app.Application;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** 5.20.1: actualizações dentro da aplicação — só GitHub/HTTPS, manifesto validado, mesma assinatura. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class)
public class Updates5201Test {
    static final String SHA = "ec129ab8db570997b593e3b8573586e3b651dcf5ada121941c4675ed233268ee";

    String json(String apk, String sha, long code) {
        return "{\"versionCode\":" + code + ",\"versionName\":\"5.20.1\",\"apk\":\"" + apk + "\",\"sha256\":\"" + sha + "\",\"bytes\":13000000,\"notes\":\"Correcções\"}";
    }

    @Test public void onlyHttpsGithubHostsAreAccepted() {
        assertTrue(AppUpdates.allowed("https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/Seeky_5.20.1.apk"));
        assertTrue(AppUpdates.allowed("https://objects.githubusercontent.com/x"));
        assertFalse("cleartext", AppUpdates.allowed("http://raw.githubusercontent.com/x.apk"));
        assertFalse("other host", AppUpdates.allowed("https://evil.example/x.apk"));
        assertFalse("lookalike", AppUpdates.allowed("https://raw.githubusercontent.com.evil.example/x.apk"));
        assertFalse("credentials", AppUpdates.allowed("https://user@raw.githubusercontent.com/x.apk"));
        assertFalse("port", AppUpdates.allowed("https://raw.githubusercontent.com:8443/x.apk"));
        assertFalse(AppUpdates.allowed("not a url"));
        assertTrue(AppUpdates.allowed(AppUpdates.MANIFEST));
    }

    @Test public void manifestIsValidated() throws Exception {
        AppUpdates.Release r = AppUpdates.parse(json("https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/Seeky_5.20.1.apk", SHA, 5004401));
        assertEquals(5004401, r.versionCode);
        assertEquals("5.20.1", r.versionName);
        assertEquals(SHA, r.sha256);
        for (String bad : new String[]{
                json("https://evil.example/a.apk", SHA, 5004401),
                json("https://raw.githubusercontent.com/a.apk", "1234", 5004401),
                json("https://raw.githubusercontent.com/a.apk", SHA, 0),
                "{\"versionCode\":5004401}", "[]", "not json"}) {
            try { AppUpdates.parse(bad); fail("accepted: " + bad); } catch (Exception expected) {}
        }
    }

    @Test public void signerMustMatchInstalledCertificate() {
        Set<String> mine = new HashSet<>(Collections.singletonList("98ce323b"));
        assertTrue(AppUpdates.sameSigner(new HashSet<>(Collections.singletonList("98ce323b")), mine));
        assertFalse(AppUpdates.sameSigner(new HashSet<>(Collections.singletonList("a96f8cae")), mine));
        assertFalse("unsigned archive", AppUpdates.sameSigner(Collections.emptySet(), mine));
        assertFalse(AppUpdates.sameSigner(mine, Collections.emptySet()));
    }

    @Test public void automaticCheckIsOnByDefaultAndCanBeTurnedOff() {
        Application app = RuntimeEnvironment.getApplication();
        assertTrue(AppUpdates.automatic(app));
        AppUpdates.prefs(app).edit().putBoolean("automatic", false).apply();
        assertFalse(AppUpdates.automatic(app));
        AppUpdates.prefs(app).edit().clear().apply();
    }
}
