package mz.bam.seeky.android;

import android.content.Context;

/** 5.55: how far the music and video player jumps back and forward (10, 15 or 30 seconds), chosen in the library menu. */
final class MediaSkip {
    private MediaSkip() {}
    static final int[] CHOICES = {10, 15, 30};
    static int seconds(Context c) { int s = c.getSharedPreferences("media_library", 0).getInt("skip_seconds", 10); for (int x : CHOICES) if (x == s) return s; return 10; }
    static void next(Context c) { int s = seconds(c), at = 0; for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i] == s) at = i; c.getSharedPreferences("media_library", 0).edit().putInt("skip_seconds", CHOICES[(at + 1) % CHOICES.length]).apply(); }
    static long ms(Context c) { return seconds(c) * 1000L; }
    static String back(Context c) { return "−" + seconds(c) + " s"; }
    static String forward(Context c) { return "+" + seconds(c) + " s"; }
    static String backIcon(Context c) { return "replay" + seconds(c); }
    static String forwardIcon(Context c) { return "forward" + seconds(c); }
}
