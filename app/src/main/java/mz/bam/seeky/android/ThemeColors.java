package mz.bam.seeky.android;
/** Small pure color operations shared by cards and JVM tests. */
final class ThemeColors {
 static int blend(int a,int b,float f){f=Math.max(0,Math.min(1,f));int r=Math.round(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=Math.round(((a>>8)&255)*(1-f)+((b>>8)&255)*f),v=Math.round((a&255)*(1-f)+(b&255)*f);return 0xff000000|(r<<16)|(g<<8)|v;}
 static int hero(int accent,boolean light,int bg){return light?accent:blend(accent,bg,.62f);}
}
