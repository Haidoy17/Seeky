package mz.bam.seeky.android;
import android.content.Context;
final class TaskWork {
    static void run(Context c,StudyStore.Row job,TaskStore db,TaskService.Control control)throws Exception{switch(job.s("type")){
        case "import":ImportWork.run(c,job,db,control);break;
        case "backup":case "restore":BackupWork.run(c,job,db,control);break;
        case "index":LibraryIndex.run(c,job,db,control);break;
        case "metadata":MetadataWork.run(c,job,db,control);break;
        case "model":case "ocr":OcrWork.run(c,job,db,control);break;
        case "summary":SummaryWork.run(c,job,db,control);break;
        case "bible":BibleWork.run(c,job,db,control);break;
        default:throw new java.io.IOException("Tipo de tarefa indisponível.");
    }}
    private TaskWork(){}
}
