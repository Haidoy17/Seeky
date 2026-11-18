package mz.bam.seeky.android;

/** Monotonic reading progress. Reaching the end does not mark a course as learned. */
final class StudyReading {
    static boolean advance(StudyStore store,long material,int completedParts,int total) {
        if(material<=0||total<=0)return false;
        android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();boolean own=!db.inTransaction();
        if(own)db.beginTransaction();
        try {
        StudyStore.Row row=store.get("materials",material);if(row==null)return false;
        int progress=(int)Math.min(99,Math.max(0,100L*completedParts/total));
        int next=Math.max((int)row.n("progress"),progress);
        String status=next>=100?"Concluído":"A estudar";
        if(next==row.n("progress")&&status.equals(row.s("status")))return false;
        store.save("materials",material,StudyStore.values("progress",next,"status",status,"updated",System.currentTimeMillis()));
        if(own)db.setTransactionSuccessful();return true;
        }finally{if(own)db.endTransaction();}
    }
    private StudyReading(){}
}
