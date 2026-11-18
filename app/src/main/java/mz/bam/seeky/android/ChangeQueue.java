package mz.bam.seeky.android;

import java.util.*;

/** Coalesces bursts for the same path without postponing unrelated changes. */
final class ChangeQueue {
    private final LinkedHashMap<String,Long> pending=new LinkedHashMap<>();
    private final int limit;private boolean overflow;
    ChangeQueue(int limit){this.limit=limit;}
    synchronized void offer(String path,long readyAt){if(!pending.containsKey(path)&&pending.size()>=limit){overflow=true;return;}pending.put(path,readyAt);}
    synchronized List<String> ready(long now,int max){List<String> out=new ArrayList<>();Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator();while(it.hasNext()&&out.size()<max){Map.Entry<String,Long> e=it.next();if(e.getValue()<=now){out.add(e.getKey());it.remove();}}return out;}
    synchronized boolean overflowed(){boolean value=overflow;overflow=false;return value;}
    synchronized int size(){return pending.size();}
}
