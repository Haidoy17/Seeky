package mz.bam.seeky.android;

import java.util.*;

/** 5.44: the Shizuku file service only ever touches Android/data and Android/obb. */
public final class ShellPaths544Test {
    static int checks;
    static void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks++; }
    static boolean refused(String path) { try { ShellPaths.check(path); return false; } catch (IllegalArgumentException e) { return true; } }
    public static void main(String[] args) {
        ShellPaths.roots = new String[]{"/storage/emulated/0"};
        String data = "/storage/emulated/0/Android/data";
        check(ShellPaths.check(data).equals(data), "Android/data é permitida");
        check(ShellPaths.check("/storage/emulated/0/Android/obb/com.jogo/main.obb").equals("/storage/emulated/0/Android/obb/com.jogo/main.obb"), "Android/obb é permitida");
        check(ShellPaths.check("/storage/emulated/0//Android/data/./com.app//files/").equals(data + "/com.app/files"), "caminhos normalizados");
        check(ShellPaths.check("/sdcard/Android/data/com.app").equals(data + "/com.app"), "/sdcard aponta para o armazenamento do utilizador");
        for (String bad : new String[]{"/storage/emulated/0/DCIM", "/storage/emulated/0/Android", "/storage/emulated/0/Android/media", "/data/data/com.app", "/storage/emulated/0/Android/data/../../DCIM", "/storage/emulated/0/Android/datax", "/storage/emulated/0/Android/data\0/x", "Android/data/x", "", "/", "/storage/emulated/10/Android/data"})
            check(refused(bad), "recusado: " + bad.replace("\0", "\\0"));
        check(ShellPaths.child(data + "/com.app", "save.dat").equals(data + "/com.app/save.dat"), "nome filho");
        for (String bad : new String[]{"..", ".", "a/b", "", "x\0y"}) { boolean r; try { ShellPaths.child(data, bad); r = false; } catch (IllegalArgumentException e) { r = true; } check(r, "nome recusado: " + bad.replace("\0", "\\0")); }
        check(ShellPaths.parent(data) == null && ShellPaths.parent(data + "/com.app").equals(data) && ShellPaths.parent(data + "/com.app/files").equals(data + "/com.app"), "subir só até à área");
        check(ShellPaths.isArea(data) && !ShellPaths.isArea(data + "/com.app"), "a área é reconhecida");
        check(ShellPaths.display(data + "/com.app/files").equals("Android/data/com.app/files"), "título legível");
        check(ShellPaths.packageOf(data + "/com.whatsapp/files/x").equals("com.whatsapp") && ShellPaths.packageOf(data).isEmpty(), "pacote da aplicação");
        List<ShellPaths.Entry> list = new ArrayList<>(Arrays.asList(new ShellPaths.Entry("zeta.txt", false, 1, 0), new ShellPaths.Entry("Álbum", true, 0, 0), new ShellPaths.Entry("alfa.txt", false, 1, 0), new ShellPaths.Entry("beta", true, 0, 0)));
        ShellPaths.sort(list);
        check(list.get(0).name.equals("Álbum") && list.get(1).name.equals("beta") && list.get(2).name.equals("alfa.txt"), "pastas primeiro, por nome sem acentos");
        check(ShellPaths.free("save.dat", new HashSet<>(Arrays.asList("save.dat", "save (2).dat"))).equals("save (3).dat") && ShellPaths.free("novo", new HashSet<>()).equals("novo"), "nome livre para copiar sem apagar");
        System.out.println("PASS shell paths (" + checks + " checks)");
    }
}
