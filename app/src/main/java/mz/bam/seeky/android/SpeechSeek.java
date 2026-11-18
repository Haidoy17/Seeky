package mz.bam.seeky.android;

/** Estimated spoken-time navigation for Android TTS, which has no absolute seek API. */
final class SpeechSeek {
 static final class Move {final int offset;final long remaining;Move(int offset,long remaining){this.offset=offset;this.remaining=remaining;}}
 static long tokenMillis(String text,int start,int end,float rate){int letters=0;for(int at=start;at<end;at++)if(Character.isLetterOrDigit(text.charAt(at)))letters++;long spoken=letters==0?0:Math.max(100,Math.round(letters*68.0/Math.max(.5f,Math.min(2f,rate))));char last=text.charAt(end-1);long pause=".!?".indexOf(last)>=0?350:",;:".indexOf(last)>=0?180:0;return spoken+pause;}
 static long at(String text,int offset,float rate,SpeechLayout.Plan plan){long time=0;for(int start=0;start<text.length();){while(start<text.length()&&Character.isWhitespace(text.charAt(start)))start++;if(start>=text.length()||start>=offset)break;int end=start+1;while(end<text.length()&&!Character.isWhitespace(text.charAt(end)))end++;long duration=tokenMillis(text,start,end,rate)+plan.pauseAfter(end);if(offset<end){time+=duration*(offset-start)/Math.max(1,end-start);break;}time+=duration;start=end;}return time;}
 static Move move(String text,int offset,long delta,float rate,SpeechLayout.Plan plan){long current=at(text,offset,rate,plan),total=at(text,text.length(),rate,plan),target=current+delta;if(target<0)return new Move(0,target);if(target>total)return new Move(text.length(),target-total);long time=0;for(int start=0;start<text.length();){while(start<text.length()&&Character.isWhitespace(text.charAt(start)))start++;if(start>=text.length())break;int end=start+1;while(end<text.length()&&!Character.isWhitespace(text.charAt(end)))end++;long duration=tokenMillis(text,start,end,rate)+plan.pauseAfter(end);if(target<time+duration)return new Move(start,0);time+=duration;start=end;}return new Move(text.length(),0);}
 private SpeechSeek(){}
}
