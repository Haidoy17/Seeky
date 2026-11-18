package mz.bam.seeky.android;

/** Fixed-point units retain reading within a long chapter without floats in persistence. */
final class ReadingProgress {
    static int units(int part,int total,int scroll,int content,int viewport){
        if(total<=0)return 0;int range=Math.max(0,content-viewport);int fraction=range==0&&content>0?1000:range<=0?0:(int)Math.min(1000,Math.max(0,1000L*scroll/range));
        return (int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.min((long)total*1000,(long)Math.max(0,part)*1000+fraction)));
    }
    private ReadingProgress(){}
}
