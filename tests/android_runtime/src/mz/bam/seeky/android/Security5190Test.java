package mz.bam.seeky.android;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** 5.19.0: bloqueio da aplicação, tom de voz, cópia automática e fronteira de URIs recebidos. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33, application = Application.class, shadows = {ReferenceDesign5150Test.LocalStorage.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class Security5190Test {
    public static class Probe extends Activity {}
    Application app;

    @Before public void before() {
        app = RuntimeEnvironment.getApplication();
        AppLock.reset();
        AppLock.install(app); // cada teste recebe uma Application nova
        shadowOf((KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE)).setIsDeviceSecure(true);
        AppLock.prefs(app).edit().clear().putBoolean("enabled", true).putInt("timeout", 60).apply();
        AppLock.lockNow();
    }

    @After public void after() { AppLock.prefs(app).edit().clear().apply(); AppLock.lockNow(); }

    Intent next(Activity a) { return shadowOf(a).getNextStartedActivity(); }
    void clock(long seconds) { shadowOf(Looper.getMainLooper()).idleFor(seconds, TimeUnit.SECONDS); }

    @Test public void lockedAppIsCoveredUntilAuthentication() {
        ActivityController<Probe> first = Robolectric.buildActivity(Probe.class).setup();
        Intent cover = next(first.get());
        assertNotNull("a locked app must start the lock screen", cover);
        assertEquals(LockActivity.class.getName(), cover.getComponent().getClassName());
        first.pause().stop();

        AppLock.unlocked();
        ActivityController<Probe> second = Robolectric.buildActivity(Probe.class).setup();
        assertNull("after authentication no lock screen is shown", next(second.get()));
        second.pause().stop().destroy();
        first.destroy();
    }

    @Test public void shortBackgroundKeepsSessionButTimeoutLocksAgain() {
        AppLock.unlocked();
        ActivityController<Probe> c = Robolectric.buildActivity(Probe.class).setup();
        assertNull(next(c.get()));
        c.pause().stop();
        clock(20);
        c.restart().resume();
        assertNull("returning within the timeout does not ask again", next(c.get()));
        c.pause().stop();
        clock(61);
        assertTrue("the Flash panel also needs authentication after the timeout", AppLock.required(app));
        c.restart().resume();
        Intent cover = next(c.get());
        assertNotNull("returning after the timeout asks again", cover);
        assertEquals(LockActivity.class.getName(), cover.getComponent().getClassName());
        c.pause().stop().destroy();
    }

    @Test public void disabledOrInsecureDeviceNeverLocks() {
        AppLock.prefs(app).edit().putBoolean("enabled", false).apply();
        assertFalse(AppLock.required(app));
        AppLock.prefs(app).edit().putBoolean("enabled", true).apply();
        shadowOf((KeyguardManager) app.getSystemService(Context.KEYGUARD_SERVICE)).setIsDeviceSecure(false);
        assertFalse("without a screen lock there is no way to authenticate, so the app stays usable", AppLock.required(app));
    }

    @Test public void pitchPreferenceIsLoadedAndBounded() {
        app.getSharedPreferences("seeky", 0).edit().putInt("speech_pitch", 130).apply();
        SpeechVoices.loadPitch(app);
        assertEquals(1.3f, SpeechVoices.pitch, .001f);
        app.getSharedPreferences("seeky", 0).edit().putInt("speech_pitch", 400).apply();
        SpeechVoices.loadPitch(app);
        assertEquals(1.5f, SpeechVoices.pitch, .001f);
        app.getSharedPreferences("seeky", 0).edit().remove("speech_pitch").apply();
        SpeechVoices.loadPitch(app);
        assertEquals(1f, SpeechVoices.pitch, .001f);
        assertEquals("Mais grave", ReaderSpeech.toneLabel(80));
        assertEquals("Natural", ReaderSpeech.toneLabel(100));
        assertEquals("Mais agudo", ReaderSpeech.toneLabel(120));
    }

    @Test public void automaticBackupIsScheduledOnlyWithAFolder() {
        JobScheduler jobs = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        AutoBackup.prefs(app).edit().clear().putBoolean("enabled", true).apply();
        AutoBackup.schedule(app);
        assertNull("no folder, no job", jobs.getPendingJob(AutoBackup.JOB_PERIODIC));
        AutoBackup.prefs(app).edit().putString("folder", "content://com.android.externalstorage.documents/tree/primary%3ABackups").putInt("days", 1).apply();
        AutoBackup.schedule(app);
        JobInfo job = jobs.getPendingJob(AutoBackup.JOB_PERIODIC);
        assertNotNull(job);
        assertTrue(job.isPeriodic());
        assertEquals(24L * 60 * 60 * 1000, job.getIntervalMillis());
        assertTrue(job.isPersisted());
        AutoBackup.prefs(app).edit().putBoolean("enabled", false).apply();
        AutoBackup.schedule(app);
        assertNull("turning it off cancels the job", jobs.getPendingJob(AutoBackup.JOB_PERIODIC));
        AutoBackup.prefs(app).edit().clear().apply();
    }

    @Test public void incomingUrisRejectOwnProvidersAndPrivateFiles() {
        String pkg = app.getPackageName();
        assertFalse(IncomingUris.acceptable(app, Uri.parse("content://" + pkg + ".study/material/5")));
        assertFalse(IncomingUris.acceptable(app, Uri.parse("content://10@" + pkg + ".files/file/x")));
        assertFalse(IncomingUris.acceptable(app, Uri.parse("file://" + app.getFilesDir() + "/../databases/catalog.db")));
        assertFalse(IncomingUris.acceptable(app, Uri.parse("https://example.org/a.pdf")));
        assertTrue(IncomingUris.acceptable(app, Uri.parse("content://com.android.providers.downloads.documents/document/12")));
        Intent signed = IncomingUris.sign(app, new Intent());
        assertTrue(IncomingUris.trusted(app, signed));
        assertFalse(IncomingUris.trusted(app, new Intent().putExtra(IncomingUris.TOKEN_EXTRA, "guess")));
        assertFalse(IncomingUris.trusted(app, new Intent()));
    }
}
