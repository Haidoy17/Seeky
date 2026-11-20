package mz.bam.seeky.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Atalhos ao manter premido o ícone do Seeky: Pesquisar, Continuar leitura, Limpeza e
 * Transferir por Wi-Fi. Passam todos pela MainActivity (o único ecrã exportado), que os encaminha.
 */
final class AppShortcuts {
    private AppShortcuts() {}

    static void update(Context c) {
        try {
            ShortcutManager sm = c.getSystemService(ShortcutManager.class);
            if (sm == null) return;
            LastReading.Saved last = LastReading.get(c);
            List<ShortcutInfo> list = new ArrayList<>();
            list.add(item(c, "search", "Pesquisar", "Pesquisar no Seeky", "search"));
            list.add(item(c, "continue", "Continuar leitura", last.exists() ? last.title : "Continuar leitura", "book"));
            list.add(item(c, "cleaner", "Limpeza", "Limpeza e cache", "trash"));
            list.add(item(c, "wifi", "Wi-Fi", "Transferir por Wi-Fi", "wifi"));
            int max = Math.max(1, sm.getMaxShortcutCountPerActivity());
            sm.setDynamicShortcuts(list.size() > max ? list.subList(0, max) : list);
        } catch (Exception e) { AppDiagnostics.record(c, "shortcuts", e); }
    }

    static ShortcutInfo item(Context c, String action, String shortLabel, String longLabel, String symbol) {
        Intent i = new Intent(Intent.ACTION_VIEW).setClass(c, MainActivity.class).putExtra(SeekyWidget.EXTRA, action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String full = AppLanguage.text(c, longLabel);
        return new ShortcutInfo.Builder(c, action)
                .setShortLabel(AppLanguage.text(c, shortLabel))
                .setLongLabel(full.length() > 25 ? full.substring(0, 24) + "…" : full)
                .setIcon(Icon.createWithAdaptiveBitmap(icon(c, symbol)))
                .setIntent(i).build();
    }

    /** Ícone adaptativo: fundo na cor suave do tema e o símbolo do Seeky na cor de destaque. */
    static Bitmap icon(Context c, String symbol) {
        AppPalette p = AppPalette.of(c);
        int size = 216, glyph = 84;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawColor(p.tint);
        Icons icon = new Icons(c, symbol, p.accent);
        icon.measure(View.MeasureSpec.makeMeasureSpec(glyph, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(glyph, View.MeasureSpec.EXACTLY));
        icon.layout(0, 0, glyph, glyph);
        canvas.translate((size - glyph) / 2f, (size - glyph) / 2f);
        icon.draw(canvas);
        return b;
    }
}
