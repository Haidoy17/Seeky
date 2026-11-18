package mz.bam.seeky.android;

/** Reader themes are independent of the application theme. ARGB colours. */
final class ReaderAppearance {
    static final int[] PAPER = {0xffffffff, 0xfff3e6cc, 0xffd4d5d8, 0xff27292d};
    static final int[] INK = {0xff23252a, 0xff392f24, 0xff24262a, 0xffe1e3e8};
    static int theme(int value) { return Math.max(0, Math.min(3, value)); }
    static int paper(int value) { return PAPER[theme(value)]; }
    static int ink(int value) { return INK[theme(value)]; }
    /** Map white to paper and black to ink, retaining document colours. */
    static float[] pdfMatrix(int value) {
        int p = paper(value), i = ink(value);
        return new float[] {
            (((p>>16)&255)-((i>>16)&255))/255f,0,0,0,(i>>16)&255,
            0,(((p>>8)&255)-((i>>8)&255))/255f,0,0,(i>>8)&255,
            0,0,((p&255)-(i&255))/255f,0,i&255,
            0,0,0,1,0
        };
    }
    static int percent(int part, int total) { return total <= 0 ? 0 : (int)Math.min(100, Math.max(0, 100L * (part + 1) / total)); }
    private ReaderAppearance() {}
}
