package mz.bam.seeky.android;
import android.app.job.JobScheduler;import android.content.Context;
/** Compatibility for old callers. Social-account backup is no longer offered in Seeky. */
final class CloudJobs {
 static final int ID=7311;
 static void schedule(Context c){/* Intentionally inactive; local backups use TaskService. */}
 static void cancel(Context c){JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(jobs!=null)jobs.cancel(ID);}
}
