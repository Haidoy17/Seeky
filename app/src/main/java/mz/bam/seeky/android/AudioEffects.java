package mz.bam.seeky.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Efeitos de áudio do leitor de música, no estilo Poweramp: equalizador gráfico de 10 bandas
 * (31 Hz–16 kHz, ±15 dB) com pré-amplificação, controlos de graves e agudos, virtualização
 * (som espacial nos auscultadores), volume extra, balanço esquerdo/direito e limitador contra a
 * distorção.
 *
 * <p>Android 9+: {@link DynamicsProcessing} (10 bandas reais, pré-amplificação, balanço e
 * limitador). Android 8: {@link Equalizer} do aparelho (normalmente 5 bandas, interpoladas) e
 * {@link BassBoost}. Virtualizador e volume extra em todos. Se o aparelho não suportar um efeito,
 * os restantes continuam a funcionar.
 */
final class AudioEffects {
    static final int[] FREQ = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    static final String[] LABELS = {"31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"};
    static final float MAX_DB = 15f;
    static final float[] BASS_WEIGHT = {1f, .85f, .55f, .2f, 0, 0, 0, 0, 0, 0}, TREBLE_WEIGHT = {0, 0, 0, 0, 0, 0, .2f, .55f, .85f, 1f};
    static final Map<String, float[]> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("Plano", new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        PRESETS.put("Graves", new float[]{6, 5, 4, 2, 0, 0, 0, 0, 0, 0});
        PRESETS.put("Graves extremos", new float[]{9, 8, 6, 3, 1, 0, 0, 0, 0, 0});
        PRESETS.put("Agudos", new float[]{0, 0, 0, 0, 0, 1, 2, 4, 5, 6});
        PRESETS.put("Rock", new float[]{5, 4, 3, 1, -1, -1, 1, 3, 4, 5});
        PRESETS.put("Pop", new float[]{-1, 0, 2, 4, 5, 4, 2, 0, -1, -1});
        PRESETS.put("Jazz", new float[]{4, 3, 1, 2, -2, -2, 0, 1, 3, 4});
        PRESETS.put("Clássica", new float[]{5, 4, 3, 2, -1, -1, 0, 2, 3, 4});
        PRESETS.put("Gospel", new float[]{3, 3, 2, 0, 1, 3, 3, 2, 2, 2});
        PRESETS.put("Vocal", new float[]{-2, -3, -2, 1, 4, 5, 4, 2, 0, -1});
        PRESETS.put("Voz falada", new float[]{-5, -4, -1, 2, 4, 5, 4, 2, -1, -3});
        PRESETS.put("Electrónica", new float[]{6, 5, 1, 0, -2, 2, 1, 1, 5, 6});
        PRESETS.put("Hip-hop", new float[]{5, 5, 2, 3, -1, -1, 1, -1, 2, 3});
        PRESETS.put("Kizomba", new float[]{6, 5, 3, 1, 0, 1, 2, 2, 3, 3});
        PRESETS.put("Acústico", new float[]{4, 4, 3, 1, 2, 2, 3, 3, 2, 1});
    }

    static final class Settings {
        boolean enabled = true, limiter = true;
        float[] gains = new float[10];
        float preamp, treble;
        int bass, virtualizer, boost, balance;
        String preset = "Plano";
    }

    static int session;
    static DynamicsProcessing dynamics;
    static Equalizer equalizer;
    static BassBoost bassBoost;
    static Virtualizer virtualizerFx;
    static LoudnessEnhancer loudness;
    static String problem = "";

    private AudioEffects() {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("audio_fx", Context.MODE_PRIVATE); }

    static Settings load(Context c) {
        SharedPreferences p = prefs(c);
        Settings s = new Settings();
        s.enabled = p.getBoolean("enabled", true);
        s.limiter = p.getBoolean("limiter", true);
        for (int i = 0; i < 10; i++) s.gains[i] = p.getFloat("g" + i, 0);
        s.preamp = p.getFloat("preamp", 0);
        s.treble = p.getFloat("treble", 0);
        s.bass = p.getInt("bass", 0);
        s.virtualizer = p.getInt("virtualizer", 0);
        s.boost = p.getInt("boost", 0);
        s.balance = p.getInt("balance", 0);
        s.preset = p.getString("preset", "Plano");
        return s;
    }

    static void save(Context c, Settings s) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean("enabled", s.enabled).putBoolean("limiter", s.limiter).putFloat("preamp", s.preamp).putFloat("treble", s.treble)
                .putInt("bass", s.bass).putInt("virtualizer", s.virtualizer).putInt("boost", s.boost).putInt("balance", s.balance).putString("preset", s.preset);
        for (int i = 0; i < 10; i++) e.putFloat("g" + i, s.gains[i]);
        e.apply();
        apply(c);
    }

    /** Predefinições do utilizador (nome → 10 ganhos). */
    static Map<String, float[]> custom(Context c) {
        Map<String, float[]> out = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(prefs(c).getString("custom", "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                org.json.JSONArray a = o.getJSONArray(k);
                float[] g = new float[10];
                for (int i = 0; i < 10 && i < a.length(); i++) g[i] = (float) a.getDouble(i);
                out.put(k, g);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void saveCustom(Context c, String name, float[] gains) {
        try {
            JSONObject o = new JSONObject(prefs(c).getString("custom", "{}"));
            org.json.JSONArray a = new org.json.JSONArray();
            for (float g : gains) a.put((double) g);
            o.put(name, a);
            prefs(c).edit().putString("custom", o.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ---- cálculo ----

    /** Graves de 0–100 → até +12 dB nas bandas baixas. */
    static float bassDb(int bass) { return bass * .12f; }

    /** Ganhos efectivos de cada banda (curva + graves + agudos), limitados a ±15 dB. */
    static float[] effective(Settings s) {
        float[] out = new float[10];
        float b = bassDb(s.bass);
        for (int i = 0; i < 10; i++) out[i] = Math.max(-MAX_DB, Math.min(MAX_DB, s.gains[i] + b * BASS_WEIGHT[i] + s.treble * TREBLE_WEIGHT[i]));
        return out;
    }

    /** Resposta (dB) numa frequência, por interpolação em escala logarítmica — para a curva. */
    static float response(float[] gains, float freq) {
        if (freq <= FREQ[0]) return gains[0];
        if (freq >= FREQ[9]) return gains[9];
        double lf = Math.log(freq);
        for (int i = 0; i < 9; i++) {
            if (freq <= FREQ[i + 1]) {
                double a = Math.log(FREQ[i]), b = Math.log(FREQ[i + 1]), t = (lf - a) / (b - a);
                t = t * t * (3 - 2 * t);
                return (float) (gains[i] + (gains[i + 1] - gains[i]) * t);
            }
        }
        return gains[9];
    }

    /** Limite superior de cada banda do DynamicsProcessing: média geométrica entre centros. */
    static float cutoff(int i) { return i >= 9 ? 20000f : (float) Math.sqrt(FREQ[i] * (double) FREQ[i + 1]); }

    /** Ganho de entrada por canal (dB) para o balanço: o lado oposto desce até −24 dB. */
    static float[] balance(int balance) {
        float left = balance > 0 ? -24f * balance / 100f : 0, right = balance < 0 ? -24f * -balance / 100f : 0;
        return new float[]{left, right};
    }

    // ---- ligação ao leitor ----

    static synchronized void attach(Context c, int audioSession) {
        release();
        session = audioSession;
        apply(c);
    }

    static synchronized void apply(Context c) {
        if (session == 0) return;
        Settings s = load(c);
        StringBuilder issues = new StringBuilder();
        float[] g = effective(s);
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                if (dynamics == null) {
                    DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                            true, 10, false, 0, false, 0, true).build();
                    dynamics = new DynamicsProcessing(0, session, config);
                }
                DynamicsProcessing.Eq eq = new DynamicsProcessing.Eq(true, true, 10);
                for (int i = 0; i < 10; i++) eq.setBand(i, new DynamicsProcessing.EqBand(true, cutoff(i), s.enabled ? g[i] : 0));
                dynamics.setPreEqAllChannelsTo(eq);
                float[] bal = balance(s.balance);
                dynamics.setInputGainbyChannel(0, (s.enabled ? s.preamp : 0) + bal[0]);
                dynamics.setInputGainbyChannel(1, (s.enabled ? s.preamp : 0) + bal[1]);
                dynamics.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, s.enabled && s.limiter, 0, 1f, 60f, 10f, -2f, 0f));
                dynamics.setEnabled(true);
            } catch (Throwable e) { issues.append("equalizador "); releaseDynamics(); }
        }
        if (dynamics == null) {
            try {
                if (equalizer == null) equalizer = new Equalizer(0, session);
                short[] range = equalizer.getBandLevelRange();
                for (short b = 0; b < equalizer.getNumberOfBands(); b++) {
                    float hz = equalizer.getCenterFreq(b) / 1000f, db = response(g, hz) + s.preamp;
                    equalizer.setBandLevel(b, (short) Math.max(range[0], Math.min(range[1], Math.round(db * 100))));
                }
                equalizer.setEnabled(s.enabled);
            } catch (Throwable e) { if (issues.indexOf("equalizador") < 0) issues.append("equalizador "); }
            try {
                if (bassBoost == null) bassBoost = new BassBoost(0, session);
                if (bassBoost.getStrengthSupported()) bassBoost.setStrength((short) (s.bass * 10));
                bassBoost.setEnabled(s.enabled && s.bass > 0);
            } catch (Throwable e) { issues.append("graves "); }
        }
        try {
            if (virtualizerFx == null) virtualizerFx = new Virtualizer(0, session);
            if (virtualizerFx.getStrengthSupported()) virtualizerFx.setStrength((short) (s.virtualizer * 10));
            virtualizerFx.setEnabled(s.enabled && s.virtualizer > 0);
        } catch (Throwable e) { issues.append("virtualização "); }
        try {
            if (loudness == null) loudness = new LoudnessEnhancer(session);
            loudness.setTargetGain(s.boost * 12);
            loudness.setEnabled(s.enabled && s.boost > 0);
        } catch (Throwable e) { issues.append("volume extra "); }
        problem = issues.toString().trim();
    }

    static void releaseDynamics() { try { if (dynamics != null) dynamics.release(); } catch (Throwable ignored) {} dynamics = null; }

    static synchronized void release() {
        releaseDynamics();
        try { if (equalizer != null) equalizer.release(); } catch (Throwable ignored) {}
        try { if (bassBoost != null) bassBoost.release(); } catch (Throwable ignored) {}
        try { if (virtualizerFx != null) virtualizerFx.release(); } catch (Throwable ignored) {}
        try { if (loudness != null) loudness.release(); } catch (Throwable ignored) {}
        equalizer = null; bassBoost = null; virtualizerFx = null; loudness = null; session = 0;
    }
}
