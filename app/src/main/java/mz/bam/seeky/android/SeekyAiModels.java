package mz.bam.seeky.android;

import java.util.*;

/**
 * Which Gemini model the Seeky asks. Nothing is fixed in the requests: the user's choice (or
 * "automatic"), the model that answered last, a short list of fallbacks and the models the key
 * really has (from the API's list) decide the order. When Google retires a model (HTTP 404) the next
 * one is tried and the user is told about the change in plain words.
 */
final class SeekyAiModels {
    private SeekyAiModels() {}
    static final String DEFAULT = "gemini-3.5-flash-lite";
    /** The newest Gemini (12/11/2026): a thinking model, text output, may need paid quota on some keys. */
    static final String NEWEST = "gemini-4-hydrogen";
    static final String[] FALLBACKS = {"gemini-3.5-flash-lite", "gemini-3.5-flash", NEWEST};
    /** Gemini 4 models think before answering: Seeky asks for the LOW level so answers stay quick and fit the token limit. */
    static boolean thinks(String model) { return clean(model).startsWith("gemini-4"); }
    static final String THINKING = "LOW";
    /** What the model selector shows. */
    static String label(String model) { String m = clean(model); return m.equals(NEWEST) ? "Gemini 4 Hydrogen · mais recente" : m; }
    static final int ATTEMPTS = 4;

    /** Where the choice is kept (the phone's settings); null in tests. */
    interface Store { void save(String chosen, String working, List<String> available); }
    static volatile Store store;
    /** "" means automatic. */
    static volatile String chosen = "";
    static volatile String working = "";
    static volatile List<String> available = Collections.emptyList();
    private static volatile String notice = "";

    /** "models/gemini-3.5-flash" → "gemini-3.5-flash"; anything unsafe → "". */
    static String clean(String name) {
        if (name == null) return "";
        String n = name.trim(); if (n.startsWith("models/")) n = n.substring(7);
        return n.matches("[a-z0-9][a-z0-9.\\-]{2,63}") ? n : "";
    }
    /** Text models only: no speech, image, embedding, live or robotics models. */
    static boolean usable(String name) {
        String n = clean(name);
        if (n.isEmpty() || !n.startsWith("gemini")) return false;
        for (String bad : new String[]{"tts", "image", "embedding", "aqa", "live", "audio", "robotics", "computer-use", "veo", "imagen"}) if (n.contains(bad)) return false;
        return true;
    }
    /** 5.55: newest Gemini first; within a version flash-lite (largest free quota), then flash, then the rest; stable before preview. */
    static List<String> rank(Collection<String> names) {
        List<String> out = new ArrayList<>();
        for (String n : names) { String c = clean(n); if (usable(c) && !out.contains(c)) out.add(c); }
        out.sort((a, b) -> {
            int va = Double.compare(version(b), version(a)); if (va != 0) return va;
            int ta = tier(a), tb = tier(b); if (ta != tb) return Integer.compare(ta, tb);
            int pa = (a.contains("preview") || a.contains("exp") ? 1 : 0) - (b.contains("preview") || b.contains("exp") ? 1 : 0); if (pa != 0) return pa;
            return a.compareTo(b);
        });
        return out;
    }
    static int tier(String n) { return n.contains("flash-lite") ? 0 : n.contains("flash") ? 1 : n.contains("pro") ? 3 : 2; }
    static double version(String n) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("gemini-(\\d+(?:\\.\\d+)?)").matcher(n); return m.find() ? Double.parseDouble(m.group(1)) : 0; }

    /** The models to try, in order, for one request. */
    static List<String> order(String chosen, String working, List<String> available) {
        LinkedHashSet<String> want = new LinkedHashSet<>();
        String c = clean(chosen), w = clean(working);
        if (!c.isEmpty()) want.add(c);
        List<String> known = rank(available == null ? Collections.emptyList() : available);
        want.addAll(known);          // what the API says this key has, newest first (5.55)
        if (!w.isEmpty()) want.add(w);
        want.add(DEFAULT);
        for (String f : FALLBACKS) want.add(f);
        List<String> out = new ArrayList<>();
        for (String m : want) {
            if (!known.isEmpty() && !known.contains(m)) continue; // the key's own list rules out retired models
            out.add(m); if (out.size() == ATTEMPTS) break;
        }
        if (out.isEmpty()) out.add(DEFAULT);
        // A model at a usage peak (429/5xx) waits at the end for two minutes.
        long now = System.currentTimeMillis(); List<String> free = new ArrayList<>(), later = new ArrayList<>();
        for (String m : out) { Long until = BUSY.get(m); if (until != null && until > now) later.add(m); else free.add(m); }
        if (free.isEmpty()) return out; // all busy: the usual order
        free.addAll(later); return free;
    }
    static final Map<String, Long> BUSY = new java.util.concurrent.ConcurrentHashMap<>();
    /** The model answered "too many requests" or "overloaded": others go first for a while. */
    static void busy(String model) { BUSY.put(clean(model), System.currentTimeMillis() + 120_000L); }
    static synchronized void busySwitch(String gone, String now) { notice = "O modelo " + gone + " está com muito uso neste momento; respondeu o " + now + "."; }
    static List<String> order() { return order(chosen, working, available); }

    /** The model in the requests when nothing failed yet. */
    static String current() { return order().get(0); }

    /** A model that does not exist for this key (HTTP 404): forget it. */
    static synchronized void missing(String model) {
        String m = clean(model);
        if (!available.isEmpty()) { List<String> keep = new ArrayList<>(available); keep.remove(m); available = Collections.unmodifiableList(keep); }
        if (m.equals(clean(working))) working = "";
        if (m.equals(clean(chosen))) chosen = "";
        save();
    }
    /** The model that answered; when it is not the first one tried, the user is told. */
    static synchronized void worked(String model, String first) { worked(model, first, false); }
    /** quota: the first model was refused for lack of free quota, not retired. */
    static synchronized void worked(String model, String first, boolean quota) {
        String m = clean(model), f = clean(first);
        if (!m.equals(f) && !f.isEmpty()) notice = quota ? noQuota(f, m) : switched(f, m);
        if (!m.equals(clean(working))) { working = m; save(); }
    }
    static String noQuota(String gone, String now) { return "O " + label(gone) + " não tem quota gratuita nesta chave (a Google pode exigir um plano pago). O Seeky usou o " + now + "."; }
    static String switched(String gone, String now) { return "O modelo " + gone + " foi descontinuado ou não está disponível para esta chave. O Seeky mudou para o " + now + "."; }
    static String none(List<String> tried) { return "Gemini: nenhum modelo disponível para esta chave (tentados: " + String.join(", ", tried) + "). Abra Ligação da IA e toque em Testar e guardar para actualizar a lista de modelos."; }
    /** The change to tell the user, once. */
    static synchronized String takeNotice() { String n = notice; notice = ""; return n; }
    /** The key's models from the API (generateContent only). */
    static synchronized void list(List<String> names) { available = Collections.unmodifiableList(rank(names)); if (!available.isEmpty() && !clean(chosen).isEmpty() && !available.contains(clean(chosen))) chosen = ""; save(); }
    static synchronized void choose(String model) { chosen = clean(model); save(); }
    static synchronized void restore(String chosenModel, String workingModel, List<String> known) { chosen = clean(chosenModel); working = clean(workingModel); available = Collections.unmodifiableList(rank(known)); }
    static void save() { Store s = store; if (s != null) try { s.save(chosen, working, available); } catch (Exception ignored) {} }
}
