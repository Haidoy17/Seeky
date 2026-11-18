package mz.bam.seeky.android;

import java.util.*;

/** Bounded cause chain without exception messages, paths or document content. */
final class DiagnosticTrace {
    static String describe(Throwable error) {
        StringBuilder out=new StringBuilder();
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        int causes=0;
        for(Throwable current=error;current!=null&&causes<8&&seen.add(current);current=current.getCause()) {
            if(causes++>0)out.append(" > ");
            out.append(current.getClass().getSimpleName());
            int frames=0;
            for(StackTraceElement frame:current.getStackTrace()) {
                if(!frame.getClassName().startsWith("mz.bam.seeky."))continue;
                out.append(frames++==0?" @ ":" / ").append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
                if(frames==3)break;
            }
        }
        return out.length()>1800?out.substring(0,1800):out.toString();
    }
    private DiagnosticTrace(){}
}
