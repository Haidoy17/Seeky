package mz.bam.seeky.android;

import java.util.*;

/**
 * Os temas do Seeky no computador: os mesmos do telemóvel (Areia, Floresta, Aurora, Ouro, Néon,
 * Vidro, Gelo, Carvão, Ciano e as dez paletas), o Seeky escuro e claro de sempre e "Horário"
 * (Areia de dia, Floresta à noite). Cada tema define as cores que o seeky.css usa.
 */
final class DeskThemes {
    private DeskThemes() {}
    static final class Theme {
        final String name, about; final boolean light; final int bg, surface, ink, muted, line, accent, onAccent;
        Theme(String name, String about, boolean light, int bg, int surface, int ink, int muted, int line, int accent, int onAccent) {
            this.name = name; this.about = about; this.light = light; this.bg = bg; this.surface = surface; this.ink = ink; this.muted = muted; this.line = line; this.accent = accent; this.onAccent = onAccent;
        }
        int raised() { return light ? blend(surface, 0xffffff, .55f) : blend(surface, ink, .06f); }
        /** Looked-up colours for seeky.css. */
        String css() {
            return "-seeky-bg: " + hex(bg) + "; -seeky-surface: " + hex(surface) + "; -seeky-raised: " + hex(raised()) + "; -seeky-ink: " + hex(ink) + "; -seeky-muted: " + hex(muted)
                + "; -seeky-accent: " + hex(accent) + "; -seeky-on-accent: " + hex(onAccent) + "; -seeky-line: " + hex(line) + ";";
        }
    }
    static final String SCHEDULE = "Horário", DEFAULT = "Seeky escuro";
    static final List<Theme> ALL = Arrays.asList(
        new Theme("Seeky escuro", "grafite e dourado", false, 0x1c1c1c, 0x262626, 0xeeeae2, 0xa8a39a, 0x3a3a3a, 0xd6b24c, 0x1c1c1c),
        new Theme("Seeky claro", "papel e sálvia", true, 0xf2eee6, 0xfbf8f2, 0x23231f, 0x6b675e, 0xddd6c8, 0x3d5a40, 0xffffff),
        new Theme("Areia", "creme e oliva, editorial", true, 0xf3eee6, 0xfbf8f2, 0x23251f, 0x686358, 0xe2d9cb, 0x3f4b3a, 0xffffff),
        new Theme("Floresta", "verde floresta, escuro", false, 0x161d18, 0x212a23, 0xeef2ea, 0xa9b4a8, 0x334036, 0xb5cda3, 0x161d18),
        new Theme("Aurora", "bordô e cinza, papel", true, 0xe8e3e0, 0xf4f1ef, 0x2a2426, 0x655c5f, 0xd9d1cd, 0x7a2e43, 0xffffff),
        new Theme("Ouro", "grafite e mostarda", false, 0x1d1d1d, 0x282828, 0xf2efe6, 0xb3ada0, 0x3a3936, 0xd1ad45, 0x1d1d1d),
        new Theme("Néon", "preto com néon laranja", false, 0x121212, 0x1c1c1d, 0xf4f4f4, 0xa8a8a8, 0x343436, 0xff7a2f, 0x121212),
        new Theme("Vidro", "vidro escuro e violeta", false, 0x111116, 0x1b1b22, 0xf1f0f5, 0xa5a3b0, 0x2c2c36, 0xb08cff, 0x111116),
        new Theme("Gelo", "cinza claro minimalista", true, 0xebebee, 0xf8f8f9, 0x111113, 0x5f5f66, 0xd9d9de, 0x111113, 0xffffff),
        new Theme("Carvão", "grafite e coral", false, 0x1b1b1c, 0x262627, 0xf2f2f2, 0xababab, 0x3a3a3b, 0xe06b60, 0x1b1b1c),
        new Theme("Ciano", "azul-noite e ciano", false, 0x272c37, 0x313744, 0xeef3f8, 0xaab4c2, 0x414959, 0x3fd3f2, 0x0f222b),
        new Theme("Ardósia", "ardósia e pêssego", false, 0x3a3e48, 0x474c58, 0xf4f5f7, 0xc9ccd3, 0x595e6b, 0xf4a088, 0x2a2f38),
        new Theme("Sálvia", "sálvia e verde-petróleo", true, 0xf0efe6, 0xfbfaf4, 0x23302f, 0x53625f, 0xdcdacb, 0x405a5c, 0xffffff),
        new Theme("Névoa", "névoa e azul-acinzentado", true, 0xf4f1f4, 0xffffff, 0x283041, 0x5b6578, 0xe4dde3, 0x5b6c88, 0xffffff),
        new Theme("Hortelã", "hortelã e azul-petróleo", true, 0xe7f0e5, 0xffffff, 0x17353b, 0x4a6569, 0xcde0c9, 0x2c6975, 0xffffff),
        new Theme("Pinheiro", "verde pinheiro", true, 0xe8eae5, 0xffffff, 0x10241c, 0x4f5c55, 0xd5d9d2, 0x00412e, 0xffffff),
        new Theme("Menta", "menta fresca", true, 0xeef8f0, 0xffffff, 0x1b4332, 0x466556, 0xd8f3dc, 0x2d6a4f, 0xffffff),
        new Theme("Petróleo", "petróleo e damasco", false, 0x384d4b, 0x455e5b, 0xf4f7f6, 0xcbd8d5, 0x587270, 0xf7c9a1, 0x2b3a39),
        new Theme("Quartzo", "quartzo rosa e verde", true, 0xf4efed, 0xffffff, 0x27322f, 0x5f6b69, 0xe6dfdc, 0x4a766e, 0xffffff),
        new Theme("Folha", "verde folha", true, 0xeef5f0, 0xffffff, 0x1c2a22, 0x56665c, 0xdbe8de, 0x4d7d5d, 0xffffff),
        new Theme("Produtivo", "azul-petróleo e laranja", true, 0xf3f5f6, 0xffffff, 0x1c3742, 0x56676f, 0xe3e8eb, 0xd0801a, 0x10141d));

    /** The theme in use now (also read by the note preview and the book pages). */
    static Theme current = ALL.get(0);

    static Theme find(String name) {
        if (SCHEDULE.equals(name)) { int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return find(h >= 7 && h < 19 ? "Areia" : "Floresta"); }
        if ("dark".equals(name)) name = "Seeky escuro"; else if ("light".equals(name)) name = "Seeky claro";
        for (Theme t : ALL) if (t.name.equals(name)) return t;
        return ALL.get(0);
    }
    static String hex(int c) { return String.format("#%06x", c & 0xffffff); }
    static int blend(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
