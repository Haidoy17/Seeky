package mz.bam.seeky.android;
/** Countdown is derived from saved timestamps, never from the number of UI ticks. */
final class FocusClock {
 static long seconds(boolean paused,long accumulated,long limit,long elapsed,long startElapsed,long wall,long startWall){long safe=Math.max(1,Math.min(7200,limit));return paused?Math.max(0,Math.min(safe,accumulated)):StudySchedule.focusSeconds(elapsed,startElapsed,wall,startWall,Math.max(0,accumulated),safe);}
 static long remaining(long seconds,long limit){return Math.max(0,Math.min(7200,limit)-seconds);}
}
