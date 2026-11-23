package mz.bam.seeky.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

/**
 * The apps' folders (Android/data, Android/obb) through Shizuku. Android 11+ hides them from every
 * app; Shizuku — a free app the user starts once with wireless debugging — lends shell permissions,
 * and the Seeky's own small service (SeekyShellService) does the file work there.
 */
final class ShizukuFiles {
    private ShizukuFiles() {}
    static final String PACKAGE = "moe.shizuku.privileged.api";
    static final String PLAY = "https://play.google.com/store/apps/details?id=" + PACKAGE;
    static final String RELEASES = "https://github.com/RikkaApps/Shizuku/releases";
    static final int REQUEST = 4711;
    enum State { NOT_INSTALLED, NOT_RUNNING, OLD, NO_PERMISSION, READY }

    static boolean installed(Context c) { try { c.getPackageManager().getPackageInfo(PACKAGE, 0); return true; } catch (Exception e) { return false; } }
    static boolean running() { try { return Shizuku.pingBinder(); } catch (Throwable e) { return false; } }
    static State state(Context c) {
        if (!running()) return installed(c) ? State.NOT_RUNNING : State.NOT_INSTALLED;
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return State.OLD;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ? State.READY : State.NO_PERMISSION;
        } catch (Throwable e) { return State.NOT_RUNNING; }
    }
    static void request(Consumer<Boolean> done) {
        Shizuku.OnRequestPermissionResultListener[] holder = new Shizuku.OnRequestPermissionResultListener[1];
        holder[0] = (code, result) -> { if (code != REQUEST) return; Shizuku.removeRequestPermissionResultListener(holder[0]); done.accept(result == PackageManager.PERMISSION_GRANTED); };
        Shizuku.addRequestPermissionResultListener(holder[0]);
        Shizuku.requestPermission(REQUEST);
    }
    /** Storage roots as the service sees them, so both sides check the same paths. */
    static void roots() { ShellPaths.roots = new String[]{Environment.getExternalStorageDirectory().getPath()}; }
    static String data() { return ShellPaths.roots[0] + "/Android/data"; }
    static String obb() { return ShellPaths.roots[0] + "/Android/obb"; }

    // ---- the connection to the service ----
    static volatile IBinder service;
    static ServiceConnection connection;
    static Shizuku.UserServiceArgs args(Context c) {
        return new Shizuku.UserServiceArgs(new ComponentName(c.getPackageName(), SeekyShellService.class.getName()))
            .daemon(false).processNameSuffix("ficheiros").debuggable(false).version(AppUpdates.installedVersion(c) > 0 ? (int) Math.min(Integer.MAX_VALUE, AppUpdates.installedVersion(c)) : 1);
    }
    static void bind(Context c, Consumer<String> done) {
        if (service != null && service.isBinderAlive()) { done.accept(null); return; }
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) { if (binder != null && binder.pingBinder()) { service = binder; done.accept(null); } else done.accept("O serviço de ficheiros não respondeu."); }
            @Override public void onServiceDisconnected(ComponentName name) { service = null; }
        };
        try { Shizuku.bindUserService(args(c), connection); } catch (Throwable e) { done.accept(e.getMessage() == null ? "Não foi possível ligar ao Shizuku." : e.getMessage()); }
    }
    static void unbind(Context c) {
        try { if (connection != null) Shizuku.unbindUserService(args(c), connection, true); } catch (Throwable ignored) {}
        connection = null; service = null;
    }

    // ---- file calls (on a background thread) ----
    interface Body { void write(Parcel p); }
    static Parcel call(IBinder binder, int code, Body body) throws IOException {
        if (binder == null) throw new IOException("O Shizuku não está ligado. Abra o Shizuku e toque em Iniciar.");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SeekyShellService.DESCRIPTOR); body.write(data);
            if (!binder.transact(code, data, reply, 0)) throw new IOException("O serviço de ficheiros não aceitou o pedido.");
            reply.setDataPosition(0);
            if (reply.readInt() != SeekyShellService.OK) { String why = reply.readString(); reply.recycle(); throw new IOException(why == null ? "Falhou." : why); }
            return reply;
        } catch (android.os.RemoteException e) { reply.recycle(); throw new IOException("O Shizuku foi desligado. Abra o Shizuku e toque em Iniciar."); }
        finally { data.recycle(); }
    }
    static List<ShellPaths.Entry> list(IBinder b, String folder) throws IOException {
        String path = ShellPaths.check(folder);
        Parcel r = call(b, SeekyShellService.LIST, p -> p.writeString(path));
        try {
            int n = r.readInt(); List<ShellPaths.Entry> out = new ArrayList<>(Math.max(0, Math.min(n, 100000)));
            for (int i = 0; i < n; i++) out.add(new ShellPaths.Entry(r.readString(), r.readInt() != 0, r.readLong(), r.readLong()));
            ShellPaths.sort(out); return out;
        } finally { r.recycle(); }
    }
    static ParcelFileDescriptor open(IBinder b, String file, boolean write) throws IOException {
        String path = ShellPaths.check(file);
        Parcel r = call(b, write ? SeekyShellService.WRITE : SeekyShellService.READ, p -> p.writeString(path));
        try { r.readLong(); ParcelFileDescriptor pfd = r.readFileDescriptor(); if (pfd == null) throw new IOException("O ficheiro não abriu."); return pfd; } finally { r.recycle(); }
    }
    static void mkdir(IBinder b, String folder) throws IOException { String path = ShellPaths.check(folder); call(b, SeekyShellService.MKDIR, p -> p.writeString(path)).recycle(); }
    static void rename(IBinder b, String from, String to) throws IOException { String a = ShellPaths.check(from), z = ShellPaths.check(to); call(b, SeekyShellService.RENAME, p -> { p.writeString(a); p.writeString(z); }).recycle(); }
    static int delete(IBinder b, String path) throws IOException { String p0 = ShellPaths.check(path); Parcel r = call(b, SeekyShellService.DELETE, p -> p.writeString(p0)); try { return r.readInt(); } finally { r.recycle(); } }

    /** Copies a file out of the apps' folders. */
    static long copyOut(IBinder b, String from, File to) throws IOException {
        try (ParcelFileDescriptor pfd = open(b, from, false); InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd.dup()); OutputStream out = new FileOutputStream(to)) { return pipe(in, out); }
    }
    /** Copies a whole folder out, keeping its structure; returns the number of files. */
    static int copyFolderOut(IBinder b, String from, File to, int[] budget) throws IOException {
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Não foi possível criar " + to.getName() + ".");
        int files = 0;
        for (ShellPaths.Entry e : list(b, from)) {
            if (budget[0]-- <= 0) throw new IOException("Pasta demasiado grande (mais de 20000 itens).");
            String child = ShellPaths.child(from, e.name); File target = new File(to, e.name);
            if (e.folder) files += copyFolderOut(b, child, target, budget); else { copyOut(b, child, target); files++; }
        }
        return files;
    }
    /** Writes a stream into the apps' folders. */
    static long copyIn(IBinder b, InputStream in, String to) throws IOException {
        try (ParcelFileDescriptor pfd = open(b, to, true); OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pfd.dup())) { return pipe(in, out); }
    }
    static long pipe(InputStream in, OutputStream out) throws IOException { byte[] buf = new byte[256 * 1024]; long total = 0; for (int n; (n = in.read(buf)) > 0; ) { out.write(buf, 0, n); total += n; } out.flush(); return total; }
}
