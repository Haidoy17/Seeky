package mz.bam.seeky.android;

/** Entry point: "--self-test" runs the checks without a window; otherwise the Seeky window opens. */
public final class DeskLauncher {
    private DeskLauncher() {}
    public static void main(String[] args) {
        // O Seeky fala português: datas e números em português mesmo num Windows noutra língua.
        if (!"pt".equals(java.util.Locale.getDefault().getLanguage())) java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-PT"));
        if (java.util.Arrays.asList(args).contains("--note-test")) { boolean good = DeskSelfTest.notes(); javafx.application.Platform.exit(); System.exit(good ? 0 : 1); return; }
        if (java.util.Arrays.asList(args).contains("--self-test")) { System.exit(DeskSelfTest.run() ? 0 : 1); return; }
        // Só um Seeky de cada vez: um segundo arranque mostra o que já está aberto (ou o seu Flash).
        // 5.57: "Reiniciar agora" depois de actualizar: espera que o Seeky antigo feche antes de abrir.
        boolean after = java.util.Arrays.toString(args).contains("--after=");
        if (after) DeskUpdates.waitFor(args);
        if (!java.util.Arrays.toString(args).contains("--screenshots")) {
            // After a restart, an old Seeky that is still closing may answer for a moment: try again for a few seconds.
            for (int tries = 0; DeskTray.handOver(after ? "" : String.join(" ", args)); tries++) {
                if (!after || tries >= 20) { System.exit(0); return; }
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }
        javafx.application.Application.launch(DeskMain.class, args);
    }
}
