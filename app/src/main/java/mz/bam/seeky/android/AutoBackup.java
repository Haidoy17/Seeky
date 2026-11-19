package mz.bam.seeky.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.widget.Toast;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Cópia de segurança automática do espaço de trabalho (estudos, notas, leitura, preferências)
 * para uma pasta escolhida pelo utilizador, por exemplo no cartão SD, numa pasta sincronizada
 * ou no Google Drive. Usa o mesmo formato verificado da cópia manual e guarda só as últimas
 * {@link #KEEP} cópias automáticas; as cópias manuais nunca são apagadas.
 */
final class AutoBackup {
    static final String PREFS = "auto_backup", PREFIX = "Seeky-auto-";
    static final int REQUEST_FOLDER = 7301, JOB_PERIODIC = 7301, JOB_NOW = 7302, KEEP = 3;
    static final int[] DAYS = {1, 7};

    private AutoBackup() {}

    static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false) && folder(c) != null; }
    static int days(Context c) { return prefs(c).getInt("days", 7) == 1 ? 1 : 7; }
    static Uri folder(Context c) { String v = prefs(c).getString("folder", null); return v == null ? null : Uri.parse(v); }

    // ---- Agendamento ----

    static void schedule(Context c) {
        JobScheduler jobs = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobs == null) return;
        if (!enabled(c)) { jobs.cancel(JOB_PERIODIC); return; }
        long period = days(c) * 24L * 60 * 60 * 1000;
        JobInfo job = new JobInfo.Builder(JOB_PERIODIC, new ComponentName(c, AutoBackupJob.class))
                .setPeriodic(period, Math.min(period / 4, 6L * 60 * 60 * 1000))
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setPersisted(true)
                .build();
        jobs.schedule(job);
    }

    static void runNow(Context c) {
        JobScheduler jobs = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobs == null) return;
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("manual", true);
        jobs.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(c, AutoBackupJob.class))
                .setOverrideDeadline(1000).setExtras(extras).build());
    }

    // ---- Execução (thread de fundo) ----

    /** Cria uma cópia na pasta escolhida e apaga as cópias automáticas mais antigas. */
    static String run(Context c, BooleanSupplier cancelled) throws Exception {
        Uri tree = folder(c);
        if (tree == null) throw new IllegalStateException("Escolha a pasta das cópias automáticas.");
        ContentResolver resolver = c.getContentResolver();
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String name = PREFIX + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(new Date()) + ".zip";
        Uri target = DocumentsContract.createDocument(resolver, parent, "application/zip", name);
        if (target == null) throw new IllegalStateException("Sem acesso à pasta das cópias. Escolha-a novamente.");
        boolean ok = false;
        long size;
        try (OutputStream out = resolver.openOutputStream(target, "wt")) {
            if (out == null) throw new IllegalStateException("Sem acesso à pasta das cópias.");
            CountingStream counting = new CountingStream(out);
            WorkspaceBackup.write(c, counting, false, cancelled, value -> {}, new BackupExtras.Options(true, false));
            counting.flush();
            size = counting.count;
            ok = true;
        } finally {
            if (!ok) try { DocumentsContract.deleteDocument(resolver, target); } catch (Exception ignored) {}
        }
        prune(resolver, tree, name);
        String summary = name + " · " + Formatter.formatShortFileSize(c, size);
        prefs(c).edit().putLong("last_time", System.currentTimeMillis()).putString("last_result", summary).remove("last_error").apply();
        return summary;
    }

    static void prune(ContentResolver resolver, Uri tree, String keepName) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        List<String[]> autos = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                String id = cursor.getString(0), name = cursor.getString(1);
                if (name != null && name.startsWith(PREFIX) && name.endsWith(".zip")) autos.add(new String[]{name, id});
            }
        } catch (RuntimeException e) { return; }
        autos.sort((a, b) -> b[0].compareTo(a[0])); // o nome tem a data: mais recente primeiro
        for (int i = KEEP; i < autos.size(); i++) {
            if (autos.get(i)[0].equals(keepName)) continue;
            try { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, autos.get(i)[1])); }
            catch (Exception ignored) {}
        }
    }

    static void notify(Context c, boolean success, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("seeky_backup", AppLanguage.text(c, "Cópias de segurança"), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(c, "seeky_backup")
                .setSmallIcon(R.drawable.ic_seeky_notification)
                .setContentTitle(AppLanguage.text(c, success ? "Cópia de segurança concluída" : "Cópia de segurança falhou"))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).build();
        try { nm.notify(7301, n); } catch (RuntimeException ignored) {}
    }

    // ---- Definições ----

    static void show(Activity a) {
        SharedPreferences p = prefs(a);
        Uri tree = folder(a);
        boolean on = enabled(a);
        UiPanel panel = new UiPanel(a, "Cópia automática", on ? (days(a) == 1 ? "Activa · diária" : "Activa · semanal") : "Desligada", "restore");
        panel.note("Guarda estudos, notas, marcadores, leitura e preferências numa pasta à sua escolha. Mantém as últimas " + KEEP + " cópias automáticas. Os livros e ficheiros originais não são copiados.");
        long last = p.getLong("last_time", 0);
        if (last > 0) panel.note(AppLanguage.text(a, "Última cópia:") + " " + DateFormat.getMediumDateFormat(a).format(new Date(last)) + " " + DateFormat.getTimeFormat(a).format(new Date(last)) + "\n" + p.getString("last_result", ""));
        String error = p.getString("last_error", null);
        if (error != null) panel.note(AppLanguage.text(a, "Último erro:") + " " + error);
        panel.action("folder", tree == null ? "Escolher pasta" : "Mudar pasta", tree == null ? "Cartão SD, Download ou Google Drive" : label(tree), false, () -> chooseFolder(a));
        if (tree != null) {
            panel.action("refresh", on ? "Desligar cópia automática" : "Activar cópia automática", null, false, () -> {
                p.edit().putBoolean("enabled", !on).apply();
                schedule(a);
                show(a);
            });
            panel.action("calendar", "Frequência", days(a) == 1 ? "Diária" : "Semanal", false, () -> {
                p.edit().putInt("days", days(a) == 1 ? 7 : 1).apply();
                schedule(a);
                show(a);
            });
            panel.action("restore", "Fazer cópia agora", "Resultado nas notificações", false, () -> {
                runNow(a);
                Toast.makeText(a, AppLanguage.text(a, "Cópia iniciada em segundo plano."), Toast.LENGTH_SHORT).show();
            });
        }
        panel.show();
    }

    static String label(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        return id.replace("primary:", "Memória interna/").replace(':', '/');
    }

    static void chooseFolder(Activity a) {
        try {
            a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQUEST_FOLDER);
        } catch (Exception e) {
            Toast.makeText(a, AppLanguage.text(a, "Não foi possível abrir o selector de pastas."), Toast.LENGTH_LONG).show();
        }
    }

    /** Resultado do selector de pastas; devolve verdadeiro quando o pedido era deste módulo. */
    static boolean result(Activity a, int request, int result, Intent data) {
        if (request != REQUEST_FOLDER) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri tree = data.getData();
        try {
            a.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(a, AppLanguage.text(a, "Esta pasta não permite acesso permanente. Escolha outra."), Toast.LENGTH_LONG).show();
            return true;
        }
        Uri previous = folder(a);
        if (previous != null && !previous.equals(tree)) {
            try { a.getContentResolver().releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
            catch (SecurityException ignored) {}
        }
        prefs(a).edit().putString("folder", tree.toString()).putBoolean("enabled", true).apply();
        schedule(a);
        show(a);
        return true;
    }

    /** Conta os bytes escritos sem copiar dados para memória. */
    static final class CountingStream extends java.io.FilterOutputStream {
        long count;
        CountingStream(OutputStream out) { super(out); }
        @Override public void write(int b) throws java.io.IOException { out.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws java.io.IOException { out.write(b, off, len); count += len; }
    }
}
