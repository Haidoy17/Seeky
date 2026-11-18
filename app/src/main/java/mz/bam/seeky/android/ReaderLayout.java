package mz.bam.seeky.android;
/** Display-independent geometry for preview windows; all values are pixels. */
final class ReaderLayout {
 static int width(int screen,float density){return Math.max(1,Math.min(screen-Math.max(8,(int)(24*density)),(int)(600*density)));}
 static int height(int screen,float density){return Math.max(1,Math.min((int)(screen*.86),(int)(920*density)));}
 private ReaderLayout(){}
}
