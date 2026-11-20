package mz.bam.seeky.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Widget "Seeky" do ecrã inicial: barra de pesquisa e atalhos para PDF, Imagens, Vídeos,
 * Áudio e Limpeza. As cores seguem o tema escolhido no Seeky. Não faz actualizações periódicas.
 */
public final class SeekyWidget extends AppWidgetProvider {
    static final String EXTRA = "mz.bam.seeky.widget_action";

    static void pin(android.app.Activity a) {
        AppWidgetManager manager = AppWidgetManager.getInstance(a);
        if (manager.isRequestPinAppWidgetSupported()) manager.requestPinAppWidget(new ComponentName(a, SeekyWidget.class), null, null);
        else PlusUi.toast(a, "Mantenha premido o ecrã inicial, toque em Widgets e escolha Seeky.");
    }

    /** Volta a pintar os widgets (por exemplo, depois de mudar de tema). */
    static void update(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            for (int id : m.getAppWidgetIds(new ComponentName(c, SeekyWidget.class))) render(c, m, id);
        } catch (RuntimeException ignored) {}
    }

    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) { for (int id : ids) render(c, manager, id); }

    static void render(Context c, AppWidgetManager m, int id) {
        AppPalette p = AppPalette.of(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.seeky_widget);
        v.setInt(R.id.seeky_widget_bg, "setColorFilter", p.surface);
        v.setInt(R.id.seeky_widget_search_bg, "setColorFilter", p.tint);
        v.setInt(R.id.seeky_widget_search_icon, "setColorFilter", p.accent);
        v.setTextColor(R.id.seeky_widget_search_text, p.muted);
        v.setTextViewText(R.id.seeky_widget_search_text, AppLanguage.text(c, "Pesquisar no Seeky"));
        int[][] items = {
                {R.id.seeky_widget_pdf, R.id.seeky_widget_pdf_icon, R.id.seeky_widget_pdf_text},
                {R.id.seeky_widget_image, R.id.seeky_widget_image_icon, R.id.seeky_widget_image_text},
                {R.id.seeky_widget_video, R.id.seeky_widget_video_icon, R.id.seeky_widget_video_text},
                {R.id.seeky_widget_audio, R.id.seeky_widget_audio_icon, R.id.seeky_widget_audio_text},
                {R.id.seeky_widget_clean, R.id.seeky_widget_clean_icon, R.id.seeky_widget_clean_text}};
        String[] actions = {"PDF", "Imagens", "Vídeos", "Áudio", "cleaner"};
        String[] labels = {"PDF", "Imagens", "Vídeos", "Áudio", "Limpeza"};
        for (int i = 0; i < items.length; i++) {
            v.setInt(items[i][1], "setColorFilter", p.accent);
            v.setTextColor(items[i][2], p.ink);
            v.setTextViewText(items[i][2], AppLanguage.text(c, labels[i]));
            v.setOnClickPendingIntent(items[i][0], open(c, actions[i], 70 + i));
        }
        v.setOnClickPendingIntent(R.id.seeky_widget_search, open(c, "search", 69));
        m.updateAppWidget(id, v);
    }

    static PendingIntent open(Context c, String action, int request) {
        Intent i = new Intent(c, MainActivity.class).putExtra(EXTRA, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, request, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
