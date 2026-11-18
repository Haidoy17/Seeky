package mz.bam.seeky.android;
import java.util.*;
public final class PerformanceCoreTest {
 static int checks;static void ok(boolean v,String n){if(!v)throw new AssertionError(n);checks++;System.out.println("PASS "+n);}
 public static void main(String[] args){
  int sample=ImageBudget.sample(2362,2362,384);ok(sample==8,"brand decodes at 1/8 dimensions without density scaling");ok((2362/sample)*(long)(2362/sample)*4<400000,"brand bitmap remains below 400KB");
  Random random=new Random(51);for(int i=0;i<10000;i++){int w=1+random.nextInt(50000),h=1+random.nextInt(50000);int[] fitted=ImageBudget.fit(w,h,1080,600000);if(fitted[0]<1||fitted[1]<1||fitted[0]>1080||fitted[1]>1080||(long)fitted[0]*fitted[1]>600000)throw new AssertionError("image budget");}ok(true,"10000 random PDF dimensions respect pixel and edge budgets");
  ok(ImageBudget.cache(32L*1048576,32,3*1048576)==1048576,"thumbnail cache adapts to 32MB heap");ok(ImageBudget.cache(512L*1048576,32,3*1048576)==3*1048576,"thumbnail cache has a fixed upper cap");
  ok(FlashQuery.expression("Álgebra  revisão").equals("\"algebra*\" AND \"revisao*\""),"Flash normalizes accents and quotes every search token");
  ok(FlashQuery.expression("\" OR 1=1 --").equals("\"or*\" AND \"1*\" AND \"1*\""),"operators and quotes cannot escape a Flash token");ok(FlashQuery.expression("***???").isEmpty(),"symbols-only query has no expression");
  ok(FlashQuery.expression(String.join(" ",Collections.nCopies(99,"abc"))).split(" AND ").length==12,"Flash caps query complexity at twelve tokens");
  ok(MediaFormats.kind("clip.mp4","application/octet-stream").equals("video"),"MP4 routes to native media player");ok(MediaFormats.kind("clip.webm","video/webm").equals("video"),"WebM routes to native media player");ok(MediaFormats.kind("sound.mp3","audio/mpeg").equals("audio"),"audio routing is retained");
  ok(Translations.english("Idioma: Português").equals("Language: Portuguese"),"English translates compound preference label");ok(Translations.english("Haidoy Carlos Mautempo").equals("Haidoy Carlos Mautempo"),"signature is preserved");ok(Translations.english("My own notes.pdf").equals("My own notes.pdf"),"unknown user text is preserved");
  try(LatestWork work=new LatestWork()){work.close();work.submit(g->{throw new AssertionError("closed executor ran a task");});ok(work.queued()==0,"search submitted after close is harmless");}
  System.out.println("PASS "+checks+" performance, Flash, translation and routing checks");
 }
}
