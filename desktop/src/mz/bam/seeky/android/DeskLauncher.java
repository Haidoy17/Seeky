package mz.bam.seeky.android;

/** Entry point: "--self-test" runs the checks without a window; otherwise the Seeky window opens. */
public final class DeskLauncher {
    private DeskLauncher() {}
    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--self-test")) { System.exit(DeskSelfTest.run() ? 0 : 1); return; }
        javafx.application.Application.launch(DeskMain.class, args);
    }
}
