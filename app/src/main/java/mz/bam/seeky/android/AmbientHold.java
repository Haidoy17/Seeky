package mz.bam.seeky.android;
import android.content.Context;
/** Temporarily silence an existing ambient player without changing its manual/follow settings. */
final class AmbientHold {
 final Context context;final BackgroundAudioService owner;final boolean manual,following,paused,playing;final long sleep;
 AmbientHold(Context c){context=c;owner=BackgroundAudioService.instance;sleep=SleepTimer.fired;manual=owner!=null&&owner.manual;following=owner!=null&&owner.following;paused=owner!=null&&owner.paused;playing=owner!=null&&owner.desired;if(playing){owner.paused=true;owner.sync();}}
 void release(){if(playing&&sleep==SleepTimer.fired&&BackgroundAudioService.instance==owner&&owner.paused&&owner.manual==manual&&owner.following==following){owner.paused=paused;owner.sync();}}
}
