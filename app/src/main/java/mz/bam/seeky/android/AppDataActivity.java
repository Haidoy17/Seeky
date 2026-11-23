package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Pastas das aplicações": Android/data and Android/obb. On Android 11 and later the system hides
 * them from every app, even with access to all files; with Shizuku (free, started once with
 * wireless debugging) the Seeky can list, open, share, copy out, import, rename and delete there.
 */
public final class AppDataActivity extends Activity {
    static final int IMPORT = 61;
    Screens s;
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    String folder = "";
    List<ShellPaths.Entry> items = new ArrayList<>();
    String filter = "";
    LinearLayout listBox;
    boolean dead, connecting;

    static void open(Activity a) { a.startActivity(new Intent(a, AppDataActivity.class)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        s = new Screens(this);
        ShizukuFiles.roots();
        folder = state != null && state.getString("folder") != null && ShellPaths.allowed(state.getString("folder")) ? state.getString("folder") : ShizukuFiles.data();
        show();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("folder", folder); }
    @Override protected void onResume() { super.onResume(); if (listBox == null) show(); }
    @Override protected void onDestroy() { dead = true; worker.shutdownNow(); ShizukuFiles.unbind(this); super.onDestroy(); }
    @Override public void onBackPressed() { String up = listBox == null ? null : ShellPaths.parent(folder); if (up != null) { folder = up; browse(); } else super.onBackPressed(); }
    void toast(String t) { PlusUi.toast(this, t); }
    String tr(String t) { return AppLanguage.text(this, t); }

    /** Setup guide until Shizuku is ready, then the folders. */
    void show() {
        ShizukuFiles.State st = ShizukuFiles.state(this);
        if (st != ShizukuFiles.State.READY) { setup(st); return; }
        if (ShizukuFiles.service != null && ShizukuFiles.service.isBinderAlive()) { browse(); return; }
        if (connecting) return;
        connecting = true;
        LinearLayout c = s.page(); c.addView(s.header("Pastas das aplicações", tr("A ligar ao Shizuku…"), this::finish)); listBox = null;
        ShizukuFiles.bind(this, error -> PlusUi.post(this, () -> { connecting = false; if (dead) return; if (error != null) { setup(ShizukuFiles.State.NOT_RUNNING); toast(error); } else browse(); }));
    }

    void setup(ShizukuFiles.State st) {
        listBox = null;
        LinearLayout c = s.page();
        c.addView(s.header("Pastas das aplicações", "Android/data · Android/obb", this::finish));
        LinearLayout why = s.card();
        why.addView(s.text("Porque é preciso o Shizuku", 17, s.p.ink, true));
        why.addView(s.text("Desde o Android 11, o sistema esconde as pastas Android/data e Android/obb de todas as aplicações, mesmo com acesso a todos os ficheiros. O Shizuku é uma aplicação gratuita que empresta ao Seeky as mesmas permissões de um computador ligado por cabo, sem root. O Seeky só lhe pede estas duas pastas.", 14, s.p.muted, false));
        c.addView(why, s.gap(8));
        boolean installed = st != ShizukuFiles.State.NOT_INSTALLED, running = installed && st != ShizukuFiles.State.NOT_RUNNING, ready = st == ShizukuFiles.State.READY;
        step(c, 1, "Instalar o Shizuku", installed ? tr("Instalado") : tr("Gratuito, na Google Play ou no GitHub"), installed, installed ? null : new String[]{"Google Play", "GitHub"}, installed ? null : new Runnable[]{() -> web(ShizukuFiles.PLAY), () -> web(ShizukuFiles.RELEASES)});
        step(c, 2, "Iniciar o Shizuku", running ? tr("A funcionar") : tr("No Shizuku: Depuração sem fios → Emparelhar (só a primeira vez, com o código das Opções de programador) → Iniciar. Depois de reiniciar o telefone, toque outra vez em Iniciar."), running, installed && !running ? new String[]{"Abrir o Shizuku"} : null, installed && !running ? new Runnable[]{this::launchShizuku} : null);
        if (st == ShizukuFiles.State.OLD) step(c, 3, "Actualizar o Shizuku", tr("Esta versão do Shizuku é antiga. Instale a mais recente."), false, new String[]{"Google Play"}, new Runnable[]{() -> web(ShizukuFiles.PLAY)});
        else step(c, 3, "Autorizar o Seeky", ready ? tr("Autorizado") : tr("O Shizuku pergunta se permite o Seeky: escolha Permitir sempre."), ready, running && !ready ? new String[]{"Autorizar"} : null, running && !ready ? new Runnable[]{() -> ShizukuFiles.request(ok -> PlusUi.post(this, () -> { toast(ok ? "Seeky autorizado." : "O Shizuku não autorizou o Seeky."); show(); }))} : null);
        c.addView(s.button("Verificar outra vez", true, this::show), s.gap(16));
        TextView note = s.text("Sem o Shizuku, as outras pastas continuam no Explorar como sempre. Com um computador também pode copiar estas pastas pelo cabo USB.", 12.5f, s.p.muted, false); note.setPadding(s.dp(4), s.dp(12), s.dp(4), 0); c.addView(note);
    }
    void step(LinearLayout c, int n, String title, String detail, boolean done, String[] labels, Runnable[] actions) {
        LinearLayout card = s.card();
        LinearLayout top = s.row();
        TextView badge = s.text(done ? "✓" : String.valueOf(n), 15, done ? s.p.onAccent : s.p.ink, true); badge.setGravity(android.view.Gravity.CENTER); badge.setBackground(s.round(done ? s.p.accent : s.p.tint, 16));
        top.addView(badge, new LinearLayout.LayoutParams(s.dp(32), s.dp(32)));
        TextView t = s.text(title, 16, s.p.ink, true); t.setPadding(s.dp(12), 0, 0, 0); top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(top);
        TextView d = s.text(detail, 13.5f, s.p.muted, false); d.setPadding(s.dp(44), s.dp(4), 0, 0); card.addView(d);
        if (labels != null) { LinearLayout b = s.row(); b.setPadding(s.dp(44), s.dp(10), 0, 0); for (int i = 0; i < labels.length; i++) { final Runnable r = actions[i]; LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = s.dp(8); b.addView(s.button(labels[i], i == 0, r), lp); } card.addView(b); }
        c.addView(card, s.gap(8));
    }
    void web(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { toast("Não há navegador para abrir a ligação."); } }
    void launchShizuku() { Intent i = getPackageManager().getLaunchIntentForPackage(ShizukuFiles.PACKAGE); if (i == null) { toast("Abra o Shizuku a partir do ecrã inicial."); return; } startActivity(i); }

    // ---- the folders ----
    void browse() {
        IBinder b = ShizukuFiles.service; final String at = folder;
        LinearLayout c = s.page();
        c.addView(s.header(ShellPaths.display(at), tr("Pastas das aplicações · Shizuku"), () -> { String up = ShellPaths.parent(folder); if (up == null) finish(); else { folder = up; browse(); } }));
        LinearLayout areas = s.row();
        s.addChip(areas, s.chip("folder", "Android/data", at.startsWith(ShizukuFiles.data()), () -> { folder = ShizukuFiles.data(); browse(); }));
        s.addChip(areas, s.chip("folder", "Android/obb", at.startsWith(ShizukuFiles.obb()), () -> { folder = ShizukuFiles.obb(); browse(); }));
        if (!ShellPaths.isArea(at)) { s.addChip(areas, s.chip("import", "Importar para aqui", false, this::pickImport)); s.addChip(areas, s.chip("add", "Nova pasta", false, this::newFolder)); }
        s.addChip(areas, s.chip("refresh", "Actualizar", false, this::browse));
        c.addView(s.scroller(areas));
        EditText search = new EditText(this); search.setHint(tr("Filtrar nesta pasta")); search.setSingleLine(true); search.setText(filter); search.setTextColor(s.p.ink); search.setHintTextColor(s.p.muted); search.setBackground(s.round(s.p.surface, 14)); search.setPadding(s.dp(14), 0, s.dp(14), 0);
        search.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence x, int a, int b2, int d) {} public void onTextChanged(CharSequence x, int a, int b2, int d) {} public void afterTextChanged(android.text.Editable e) { filter = e.toString(); draw(); } });
        c.addView(search, new LinearLayout.LayoutParams(-1, s.dp(46)));
        listBox = s.column(); c.addView(listBox, s.gap(8));
        listBox.addView(s.text(tr("A ler…"), 14, s.p.muted, false));
        worker.execute(() -> {
            try { List<ShellPaths.Entry> found = ShizukuFiles.list(b, at); PlusUi.post(this, () -> { if (dead || !at.equals(folder)) return; items = found; draw(); }); }
            catch (Exception e) { PlusUi.post(this, () -> { if (dead) return; if (ShizukuFiles.service == null || !ShizukuFiles.running()) { show(); return; } listBox.removeAllViews(); listBox.addView(s.text(e.getMessage(), 14, s.p.muted, false)); }); }
        });
    }
    void draw() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String q = filter.trim().toLowerCase(Locale.ROOT); int shown = 0;
        boolean apps = ShellPaths.isArea(folder);
        for (ShellPaths.Entry e : items) {
            String label = apps ? appName(e.name) : "";
            if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q) && !label.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (shown++ >= 2000) { listBox.addView(s.text(tr("Mostrados os primeiros 2000 itens. Use o filtro."), 13, s.p.muted, false)); break; }
            String title = apps && !label.isEmpty() ? label : e.name;
            String sub = e.folder ? (apps && !label.isEmpty() ? e.name : tr("Pasta")) : Rules.bytes(e.size) + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(e.modified));
            LinearLayout row = s.entry(e.folder ? "folder" : "document", title, sub, () -> { if (e.folder) { folder = ShellPaths.child(folder, e.name); filter = ""; browse(); } else fileMenu(e); });
            row.setOnLongClickListener(v -> { if (e.folder) folderMenu(e); else fileMenu(e); return true; });
            listBox.addView(row, s.gap(6));
        }
        if (shown == 0) listBox.addView(s.text(items.isEmpty() ? tr("Pasta vazia.") : tr("Nada corresponde ao filtro."), 14, s.p.muted, false));
    }
    final Map<String, String> names = new HashMap<>();
    /** The app's name for a package folder, when Android lets the Seeky see that app. */
    String appName(String pkg) {
        String known = names.get(pkg); if (known != null) return known;
        String label = "";
        try { ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0); label = String.valueOf(getPackageManager().getApplicationLabel(info)); } catch (Exception ignored) {}
        names.put(pkg, label); return label;
    }

    void fileMenu(ShellPaths.Entry e) {
        String path = ShellPaths.child(folder, e.name);
        UiPanel p = new UiPanel(this, e.name, Rules.bytes(e.size) + " · " + ShellPaths.display(folder), "document");
        p.action("expand", "Abrir", null, false, () -> temp(path, e.name, false));
        p.action("share", "Partilhar", null, false, () -> temp(path, e.name, true));
        p.action("import", "Copiar para Transferências", "Download/Seeky", false, () -> copyOut(path, e.name, false));
        p.action("edit", "Mudar o nome", null, false, () -> rename(e));
        p.danger("Apagar", () -> confirmDelete(e));
        p.show();
    }
    void folderMenu(ShellPaths.Entry e) {
        UiPanel p = new UiPanel(this, e.name, ShellPaths.display(folder), "folder");
        p.action("folder", "Abrir", null, false, () -> { folder = ShellPaths.child(folder, e.name); browse(); });
        p.action("import", "Copiar a pasta para Transferências", "Download/Seeky", false, () -> copyOut(ShellPaths.child(folder, e.name), e.name, true));
        p.action("edit", "Mudar o nome", null, false, () -> rename(e));
        p.danger("Apagar a pasta", () -> confirmDelete(e));
        p.show();
    }
    /** Opens or shares a copy kept in the Seeky's temporary files. */
    void temp(String path, String name, boolean share) {
        IBinder b = ShizukuFiles.service; toast(share ? "A preparar…" : "A abrir…");
        worker.execute(() -> {
            try {
                File f = ShareTools.temp(this, name); ShizukuFiles.copyOut(b, path, f);
                String mime = mime(name); Uri uri = ShareTools.tempUri(this, f);
                PlusUi.post(this, () -> {
                    if (share) { ShareTools.send(this, uri, name, mime); return; }
                    try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), tr("Abrir com"))); } catch (Exception x) { toast("Não há aplicação para abrir este ficheiro."); }
                });
            } catch (Exception x) { PlusUi.post(this, () -> toast(x.getMessage())); }
        });
    }
    static String mime(String name) { String ext = name.lastIndexOf('.') > 0 ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : ""; String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext); return m == null ? "application/octet-stream" : m; }
    void copyOut(String path, String name, boolean whole) {
        if (!StorageAccess.all(this)) { toast("Dê primeiro ao Seeky acesso a todos os ficheiros (Explorar)."); return; }
        IBinder b = ShizukuFiles.service; toast("A copiar para Download/Seeky…");
        worker.execute(() -> {
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Seeky"); if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar Download/Seeky.");
                Set<String> taken = new HashSet<>(Arrays.asList(dir.list() == null ? new String[0] : dir.list()));
                File target = new File(dir, ShellPaths.free(name, taken));
                int files = whole ? ShizukuFiles.copyFolderOut(b, path, target, new int[]{20000}) : 1;
                if (!whole) ShizukuFiles.copyOut(b, path, target);
                android.media.MediaScannerConnection.scanFile(this, new String[]{target.getPath()}, null, null);
                PlusUi.post(this, () -> toast(tr("Copiado para") + " Download/Seeky/" + target.getName() + (whole ? " · " + files + " " + tr("ficheiros") : "")));
            } catch (Exception x) { PlusUi.post(this, () -> toast(x.getMessage())); }
        });
    }
    void rename(ShellPaths.Entry e) {
        EditText input = PlusUi.input(this, "Novo nome", e.name, 255);
        new UiDialog.Builder(this).setTitle("Mudar o nome").setView(input).setPositiveButton("Guardar", (d, w) -> {
            String name = input.getText().toString().trim(); if (name.isEmpty() || name.equals(e.name)) return;
            IBinder b = ShizukuFiles.service;
            worker.execute(() -> { try { ShizukuFiles.rename(b, ShellPaths.child(folder, e.name), ShellPaths.child(folder, name)); PlusUi.post(this, this::browse); } catch (Exception x) { PlusUi.post(this, () -> toast(x.getMessage())); } });
        }).setNegativeButton("Cancelar", null).show();
    }
    void confirmDelete(ShellPaths.Entry e) {
        String what = e.folder ? tr("A pasta e tudo o que tem dentro serão apagados.") : tr("O ficheiro será apagado.");
        new UiDialog.Builder(this).setTitle(tr("Apagar") + " " + e.name + "?").setMessage(what + " " + tr("Não há lixo nestas pastas: não pode ser desfeito. A aplicação dona destes dados pode deixar de funcionar como antes.")).setPositiveButton("Apagar", (d, w) -> {
            IBinder b = ShizukuFiles.service;
            worker.execute(() -> { try { int n = ShizukuFiles.delete(b, ShellPaths.child(folder, e.name)); PlusUi.post(this, () -> { toast(n + " " + tr(n == 1 ? "item apagado." : "itens apagados.")); browse(); }); } catch (Exception x) { PlusUi.post(this, () -> toast(x.getMessage())); } });
        }).setNegativeButton("Cancelar", null).show();
    }
    void newFolder() {
        EditText input = PlusUi.input(this, "Nome da pasta", "", 255);
        new UiDialog.Builder(this).setTitle("Nova pasta").setView(input).setPositiveButton("Criar", (d, w) -> {
            String name = input.getText().toString().trim(); if (name.isEmpty()) return; IBinder b = ShizukuFiles.service;
            worker.execute(() -> { try { ShizukuFiles.mkdir(b, ShellPaths.child(folder, name)); PlusUi.post(this, this::browse); } catch (Exception x) { PlusUi.post(this, () -> toast(x.getMessage())); } });
        }).setNegativeButton("Cancelar", null).show();
    }
    void pickImport() { try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), IMPORT); } catch (Exception e) { toast("Não há selector de ficheiros."); } }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT || result != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>(); if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri()); else if (data.getData() != null) uris.add(data.getData());
        IBinder b = ShizukuFiles.service; final String to = folder; Set<String> taken = new HashSet<>(); for (ShellPaths.Entry e : items) taken.add(e.name);
        toast("A importar…");
        worker.execute(() -> {
            int done = 0; String error = null;
            for (Uri u : uris) {
                try (InputStream in = getContentResolver().openInputStream(u)) { if (in == null) throw new IOException("Não foi possível ler o ficheiro."); String name = ShellPaths.free(displayName(u), taken); ShizukuFiles.copyIn(b, in, ShellPaths.child(to, name)); taken.add(name); done++; }
                catch (Exception x) { error = x.getMessage(); }
            }
            final int n = done; final String why = error;
            PlusUi.post(this, () -> { toast(n + " " + tr(n == 1 ? "ficheiro importado." : "ficheiros importados.") + (why == null ? "" : " " + why)); browse(); });
        });
    }
    String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) { String n = c.getString(0); if (!TextUtils.isEmpty(n)) return n.replace('/', '_'); } } catch (Exception ignored) {}
        String last = u.getLastPathSegment(); return last == null ? "ficheiro" : last.replace('/', '_').replace(':', '_');
    }
}
