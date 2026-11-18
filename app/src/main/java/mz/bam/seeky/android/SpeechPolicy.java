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
    private static final java.util.Set<String> EUROPEAN_PORTUGUESE=new java.util.HashSet<>(java.util.Arrays.asList("PT","MZ","AO","CV","GW","ST","TL","MO","GQ"));
    private static final java.util.Set<String> BRITISH_ENGLISH=new java.util.HashSet<>(java.util.Arrays.asList("GB","IE","ZA","MZ","ZW","ZM","MW","BW","NA","KE","TZ","UG","NG","GH","AU","NZ","IN"));
    /** Preferência regional quando várias vozes têm a mesma qualidade: a do país do aparelho,
     *  depois a norma mais próxima (Moçambique, Angola… → pt-PT; Brasil → pt-BR). Maior é melhor. */
    static int regionScore(String voiceCountry,String deviceCountry,String language){
        String voice=voiceCountry==null?"":voiceCountry.toUpperCase(java.util.Locale.ROOT),device=deviceCountry==null?"":deviceCountry.toUpperCase(java.util.Locale.ROOT),lang=language==null?"":language.toLowerCase(java.util.Locale.ROOT);
        if(!device.isEmpty()&&voice.equals(device))return 3;
        String preferred=lang.startsWith("pt")?(device.equals("BR")?"BR":EUROPEAN_PORTUGUESE.contains(device)?"PT":""):lang.startsWith("en")?(BRITISH_ENGLISH.contains(device)?"GB":"US"):"";
        if(!preferred.isEmpty()&&voice.equals(preferred))return 2;
        return voice.isEmpty()?1:0;
    }
    private SpeechPolicy() {}
}
