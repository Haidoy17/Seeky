package mz.bam.seeky.android;

/** Viewport steps overlap two lines so that a page turn never skips a sentence. */
final class TextPaging {
    static int step(int viewport,int line){return Math.max(1,viewport-Math.max(1,line)*2);}
    static int target(int y,int direction,int viewport,int content,int line){int end=Math.max(0,content-viewport);return (int)Math.max(0,Math.min(end,(long)y+(long)(direction<0?-1:1)*step(viewport,line)));}
    private TextPaging(){}
}
