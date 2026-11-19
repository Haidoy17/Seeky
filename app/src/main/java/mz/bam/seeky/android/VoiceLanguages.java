package mz.bam.seeky.android;

import java.util.*;

/**
 * Idiomas oferecidos na leitura em voz alta, pela ordem do menu, com nome no próprio idioma e
 * uma frase de amostra natural. As vozes vêm do motor instalado (normalmente o Google); um
 * idioma sem vozes no telefone mostra a opção de as baixar.
 */
final class VoiceLanguages {
    static final List<String> CODES = Collections.unmodifiableList(Arrays.asList(
            "pt", "en", "es", "fr", "it", "de", "nl", "sw", "af", "ar", "hi", "zh", "ja", "ru", "tr"));

    private static final Map<String, String[]> INFO = new HashMap<>();
    static {
        put("pt", "Português", "Olá! Esta é uma amostra do Seeky. Uma boa leitura respeita as pausas, as perguntas e o ritmo de cada frase. Qual voz prefere ouvir?");
        put("en", "English", "Hello! This is Seeky. A good reading voice respects pauses, questions and the rhythm of each sentence. Which voice do you prefer?");
        put("es", "Español", "Hola. Esta es una voz de Seeky. Una buena lectura respeta las pausas y el ritmo de cada frase.");
        put("fr", "Français", "Bonjour. Voici une voix de Seeky. Une bonne lecture respecte les pauses et le rythme de chaque phrase.");
        put("it", "Italiano", "Ciao! Questa è una voce di Seeky. Una buona lettura rispetta le pause e il ritmo di ogni frase.");
        put("de", "Deutsch", "Hallo! Das ist eine Stimme von Seeky. Gutes Vorlesen achtet auf Pausen und den Rhythmus jedes Satzes.");
        put("nl", "Nederlands", "Hallo! Dit is een stem van Seeky. Goed voorlezen respecteert de pauzes en het ritme van elke zin.");
        put("sw", "Kiswahili", "Habari! Hii ni sauti ya Seeky. Usomaji mzuri huheshimu vituo na mdundo wa kila sentensi.");
        put("af", "Afrikaans", "Hallo! Dit is 'n stem van Seeky. Goeie voorlees respekteer die pouses en die ritme van elke sin.");
        put("ar", "العربية", "مرحبًا! هذا صوت من سيكي. القراءة الجيدة تحترم التوقفات وإيقاع كل جملة.");
        put("hi", "हिन्दी", "नमस्ते! यह सीकी की आवाज़ है। अच्छी पढ़ाई हर वाक्य के विराम और लय का सम्मान करती है।");
        put("zh", "中文", "你好！这是 Seeky 的声音。好的朗读会尊重每个句子的停顿和节奏。");
        put("ja", "日本語", "こんにちは。Seeky の音声です。良い読み上げは、文ごとの間とリズムを大切にします。");
        put("ru", "Русский", "Здравствуйте! Это голос Seeky. Хорошее чтение соблюдает паузы и ритм каждого предложения.");
        put("tr", "Türkçe", "Merhaba! Bu bir Seeky sesidir. İyi bir okuma, her cümlenin duraklarına ve ritmine saygı gösterir.");
    }

    private static void put(String code, String name, String sample) { INFO.put(code, new String[]{name, sample}); }

    /** Nome do idioma no próprio idioma; o português é traduzido pela interface. */
    static String name(String code) { String[] v = INFO.get(code); return v == null ? code : v[0]; }

    static String sample(String code) { String[] v = INFO.get(code); return (v == null ? INFO.get("pt") : v)[1]; }

    static List<String> names() {
        List<String> out = new ArrayList<>();
        for (String code : CODES) out.add(name(code));
        return out;
    }

    /** "Voz 2 · " quando há várias vozes do mesmo país, para as distinguir na lista. */
    static String ordinal(List<String> localeTags, int index) {
        String tag = localeTags.get(index);
        int same = 0, position = 0;
        for (int i = 0; i < localeTags.size(); i++) if (localeTags.get(i).equals(tag)) { same++; if (i <= index) position++; }
        return same > 1 ? "Voz " + position + " · " : "";
    }

    private VoiceLanguages() {}
}
