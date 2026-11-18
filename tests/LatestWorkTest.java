package mz.bam.seeky.android;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class LatestWorkTest {
    static int count=0;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;System.out.println("PASS "+message);}
    public static void main(String[]args)throws Exception {
        LatestWork jobs=new LatestWork();CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger value=new AtomicInteger(-1),stale=new AtomicInteger();
        try {
            jobs.submit(id->{started.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}if(jobs.current(id))stale.incrementAndGet();});
            check(started.await(2,TimeUnit.SECONDS),"first search starts");
            for(int i=0;i<500;i++){final int n=i;jobs.submit(id->{if(jobs.current(id))value.set(n);});}
            check(jobs.queued()<=1,"500 fast edits leave at most one pending search");
            long last=jobs.submit(id->{if(jobs.current(id))value.set(500);done.countDown();});release.countDown();
            check(done.await(2,TimeUnit.SECONDS)&&value.get()==500,"latest request reaches the result");
            check(stale.get()==0,"cancelled old request cannot publish stale result");
            check(jobs.current(last),"current generation remains valid");jobs.cancel();
            check(!jobs.current(last),"explicit cancellation invalidates published generation");
        }finally{release.countDown();jobs.close();}
        check(jobs.queued()==0,"closing discards pending work");
        System.out.println("Passed "+count+" scheduling checks");
    }
}
