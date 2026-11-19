package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Tipografia dos títulos. Nos temas modernos (Areia, Floresta, Carvão) os títulos usam uma
 * serifa elegante do sistema (Noto Serif), como nas referências de design; o corpo do texto
 * continua em sans-serif para leitura confortável. Os temas clássicos mantêm o negrito.
 */
final class AppFonts {
    private static final Typeface SERIF = Typeface.create("serif", Typeface.NORMAL);
    private static final Typeface SERIF_BOLD = Typeface.create("serif", Typeface.BOLD);

    static Typeface title(Context c, float sizeSp) {
        if (!AppPalette.of(c).modern) return Typeface.DEFAULT_BOLD;
        return sizeSp >= 18 ? SERIF : SERIF_BOLD;
    }

    private AppFonts() {}
}
