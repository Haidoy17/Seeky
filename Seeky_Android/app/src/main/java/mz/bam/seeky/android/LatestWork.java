package mz.bam.seeky.android;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** A single worker with no backlog of superseded searches. */
final class LatestWork implements AutoCloseable {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>());
    private final AtomicLong generation=new AtomicLong();
    private Future<?> pending;
    interface Work {void run(long generation);}
    synchronized long submit(Work work){
        long id=cancel();if(worker.isShutdown())return id;pending=worker.submit(()->{if(current(id))work.run(id);});return id;
    }
    synchronized long cancel(){long id=generation.incrementAndGet();if(pending!=null)pending.cancel(true);worker.purge();return id;}
    boolean current(long id){return generation.get()==id&&!worker.isShutdown();}
    int queued(){return worker.getQueue().size();}
    synchronized void close(Runnable cleanup){if(worker.isShutdown())return;cancel();worker.execute(cleanup);worker.shutdown();}
    public synchronized void close(){cancel();worker.shutdownNow();}
}
