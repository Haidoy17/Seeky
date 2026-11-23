package mz.bam.seeky.android;

import android.os.Binder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;

/**
 * Runs inside the Shizuku service (shell permissions, the same as a computer connected by USB),
 * started by the Seeky only when the user opens the apps' folders. It answers a few file requests
 * — list, open to read, open to write, new folder, rename, delete — and only for paths inside
 * Android/data and Android/obb (checked again here). Nothing else of the system is reachable.
 */
public final class SeekyShellService extends Binder {
    static final String DESCRIPTOR = "mz.bam.seeky.android.SeekyShellService";
    static final int LIST = 1, READ = 2, WRITE = 3, MKDIR = 4, RENAME = 5, DELETE = 6, PING = 7;
    /** Shizuku's "destroy" transaction for user services. */
    static final int DESTROY = 16777115;
    static final int OK = 0, FAILED = 1;
    /** The last opened files stay open a moment (the reply carries a copy of the descriptor); older ones are closed. */
    final java.util.ArrayDeque<ParcelFileDescriptor> held = new java.util.ArrayDeque<>();
    synchronized void hold(ParcelFileDescriptor pfd) { held.addLast(pfd); while (held.size() > 4) { try { held.removeFirst().close(); } catch (IOException ignored) {} } }

    /** Shizuku creates the service with this constructor, in its own process. */
    public SeekyShellService() {
        int user = android.os.Process.myUid() / 100000;
        ShellPaths.roots = new String[]{"/storage/emulated/" + user};
    }
    /** Tests run the service in the app's process with their own roots. */
    SeekyShellService(String[] roots) { ShellPaths.roots = roots; }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == DESTROY) { System.exit(0); return true; }
        if (code < LIST || code > PING) { try { return super.onTransact(code, data, reply, flags); } catch (Exception e) { return false; } }
        try {
            data.enforceInterface(DESCRIPTOR);
            switch (code) {
                case PING: ok(reply); reply.writeInt(android.os.Process.myUid()); break;
                case LIST: {
                    File dir = new File(ShellPaths.check(data.readString()));
                    File[] kids = dir.listFiles();
                    if (kids == null) throw new IOException(dir.exists() ? "Sem acesso a esta pasta." : "A pasta já não existe.");
                    ok(reply); reply.writeInt(kids.length);
                    for (File f : kids) { reply.writeString(f.getName()); reply.writeInt(f.isDirectory() ? 1 : 0); reply.writeLong(f.isDirectory() ? 0 : f.length()); reply.writeLong(f.lastModified()); }
                    break;
                }
                case READ: case WRITE: {
                    File f = new File(ShellPaths.check(data.readString()));
                    if (code == READ && !f.isFile()) throw new IOException("O ficheiro já não existe.");
                    if (code == WRITE) { File parent = f.getParentFile(); if (parent == null || !parent.isDirectory()) throw new IOException("A pasta de destino não existe."); if (f.isDirectory()) throw new IOException("Já existe uma pasta com este nome."); }
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, code == READ ? ParcelFileDescriptor.MODE_READ_ONLY : ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
                    ok(reply); reply.writeLong(f.length()); reply.writeFileDescriptor(pfd.getFileDescriptor()); hold(pfd);
                    break;
                }
                case MKDIR: {
                    File f = new File(ShellPaths.check(data.readString()));
                    if (f.exists()) throw new IOException("Já existe um item com este nome.");
                    if (!f.mkdirs()) throw new IOException("Não foi possível criar a pasta.");
                    ok(reply); break;
                }
                case RENAME: {
                    File from = new File(ShellPaths.check(data.readString())), to = new File(ShellPaths.check(data.readString()));
                    if (ShellPaths.isArea(from.getPath()) || ShellPaths.isArea(to.getPath())) throw new IOException("Android/data e Android/obb não podem mudar de nome.");
                    if (!from.exists()) throw new IOException("O item já não existe.");
                    if (to.exists()) throw new IOException("Já existe um item com este nome.");
                    if (!from.renameTo(to)) throw new IOException("Não foi possível mudar o nome.");
                    ok(reply); break;
                }
                case DELETE: {
                    File f = new File(ShellPaths.check(data.readString()));
                    if (ShellPaths.isArea(f.getPath())) throw new IOException("Android/data e Android/obb não podem ser apagadas.");
                    int[] count = {0};
                    delete(f, count);
                    ok(reply); reply.writeInt(count[0]); break;
                }
                default: return false;
            }
        } catch (Exception e) {
            reply.setDataPosition(0); reply.setDataSize(0);
            reply.writeInt(FAILED); reply.writeString(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return true;
    }
    static void ok(Parcel reply) { reply.writeInt(OK); }
    /** Deletes a file or a whole folder, without following links out of the folder. */
    static void delete(File f, int[] count) throws IOException {
        if (f.isDirectory() && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) delete(k, count);
        }
        if (!f.delete() && f.exists()) throw new IOException("Não foi possível apagar " + f.getName() + ".");
        count[0]++;
    }
}
