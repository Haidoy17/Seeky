package mz.bam.seeky.android;
/** Bounded-frequency retries for an enabled overlay; explicit stop/permission always wins. */
final class OverlayRecovery {
 private int failures=0;private long next=0;
 boolean due(long now){return now>=next;}
 void failed(long now){failures=Math.min(6,failures+1);next=now+Math.min(60000L,1000L*(1L<<failures));}
 void succeeded(){failures=0;next=0;}
 long nextAttempt(){return next;}
}
