package mz.bam.seeky.android;
import android.content.Context;import android.graphics.*;import android.view.View;
final class StudyBars extends View {
 final long[] seconds;final int accent,muted;final Paint p=new Paint(3);
 StudyBars(Context c,long[] s,int a,int m){super(c);seconds=s;accent=a;muted=m;StringBuilder description=new StringBuilder("Foco dos últimos 7 dias: ");for(long v:s)description.append(v/60).append(" minutos; ");setContentDescription(description.toString());}
 protected void onDraw(Canvas c){float density=getResources().getDisplayMetrics().density,max=60;for(long s:seconds)max=Math.max(max,s/60f);float width=getWidth()/7f,base=getHeight()-23*density,top=20*density;java.util.Calendar day=java.util.Calendar.getInstance();day.add(java.util.Calendar.DAY_OF_YEAR,-6);java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("EE",new java.util.Locale("pt","MZ"));for(int i=0;i<7;i++){float center=(i+.5f)*width,bar=Math.max(2*density,(base-top)*seconds[i]/60f/max);p.setColor(accent);c.drawRoundRect(center-width*.25f,base-bar,center+width*.25f,base,5*density,5*density,p);p.setColor(muted);p.setTextSize(10*density);p.setTextAlign(Paint.Align.CENTER);c.drawText(""+seconds[i]/60,center,base-bar-5*density,p);c.drawText(date.format(day.getTime()),center,getHeight()-5*density,p);day.add(java.util.Calendar.DAY_OF_YEAR,1);}}
}
