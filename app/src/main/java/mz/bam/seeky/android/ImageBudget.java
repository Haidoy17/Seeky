package mz.bam.seeky.android;
/** Pure allocation limits shared by logo, PDF and image decoders. */
final class ImageBudget {
 static int sample(int w,int h,int edge){int n=1;while(w/n>edge||h/n>edge){if(n>=1<<29)break;n*=2;}return n;}
 static int[] fit(int w,int h,int edge,long pixels){if(w<1||h<1)return new int[]{1,1};double f=Math.min(1,Math.min((double)edge/Math.max(w,h),Math.sqrt((double)pixels/((double)w*h))));return new int[]{Math.max(1,(int)(w*f)),Math.max(1,(int)(h*f))};}
 static int cache(long heap,int divisor,int cap){return (int)Math.max(512*1024,Math.min(cap,heap/divisor));}
 private ImageBudget(){}
}
