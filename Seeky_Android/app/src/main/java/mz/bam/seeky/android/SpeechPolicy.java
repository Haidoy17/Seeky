package mz.bam.seeky.android;

/** Exact mode/language matching: offline must never silently use a network voice. */
final class SpeechPolicy {
    static boolean accepts(boolean online, String language, boolean network, String voiceLanguage, boolean missing) {
        return !missing && online == network && language != null && language.equalsIgnoreCase(voiceLanguage);
    }
    static float rate(int progress) { return Math.max(.5f, Math.min(2f, .5f + progress / 100f)); }
    static int chunkLimit(float rate){return Math.max(600,Math.min(2200,Math.round(2200*Math.max(.5f,Math.min(1f,rate)))));}
    static int chunkLimit(float rate,boolean online){return online?Math.max(1200,Math.min(3400,Math.round(3400*Math.max(.5f,Math.min(1f,rate))))):chunkLimit(rate);}
    static int startupLimit(float rate,boolean online){return online?Math.max(600,Math.min(1200,Math.round(1200*Math.max(.5f,Math.min(1f,rate))))):chunkLimit(rate,false);}
    static long timeout(int characters,float rate){return Math.min(360000L,Math.max(60000L,(long)(characters*140/Math.max(.5f,rate))+45000));}
    private SpeechPolicy() {}
}
