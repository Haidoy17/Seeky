package mz.bam.seeky.android;
/** Simple transparent spaced review schedule; all intervals are elapsed days, not predicted mastery. */
final class StudySchedule {
 static final long DAY=86400000L;
 static final class Next {final long due;final int interval,reps,lapses;Next(long d,int i,int r,int l){due=d;interval=i;reps=r;lapses=l;}}
 static Next next(long now,int previous,int reps,int lapses,int grade){if(grade<0||grade>3)throw new IllegalArgumentException("Avaliação inválida.");if(grade==0)return new Next(now+60000,0,0,lapses+1);int days=grade==1?Math.max(1,(int)Math.ceil(previous*1.2)):grade==3?Math.max(4,(int)Math.ceil(previous*3.0)):reps==0?1:reps==1?3:Math.max(1,(int)Math.ceil(previous*2.2));days=Math.min(180,days);return new Next(now+days*DAY,days,reps+1,lapses);}
 static long focusSeconds(long nowElapsed,long startElapsed,long nowWall,long startWall,long accumulated,long limit){long delta=nowElapsed>=startElapsed&&Math.abs((nowWall-startWall)-(nowElapsed-startElapsed))<300000?nowElapsed-startElapsed:Math.max(0,nowWall-startWall);return Math.max(0,Math.min(limit,accumulated+delta/1000));}
 static String valid(String value,int max,String label){String text=value==null?"":value.trim();if(text.isEmpty()||text.length()>max)throw new IllegalArgumentException(label+": preencha entre 1 e "+max+" caracteres.");return text;}
 static String like(String value){return "%"+value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
}
