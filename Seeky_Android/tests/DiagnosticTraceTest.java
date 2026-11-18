package mz.bam.seeky.android;

public final class DiagnosticTraceTest {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        NullPointerException cause=new NullPointerException("private document /storage/secret.pdf");
        cause.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.android.internal.policy.PhoneWindow","getInsetsController","PhoneWindow.java",3924),new StackTraceElement("mz.bam.seeky.android.ReaderActivity","onCreate","ReaderActivity.java",37)});
        RuntimeException wrapper=new RuntimeException("Unable to start activity containing private URI",cause);
        wrapper.setStackTrace(new StackTraceElement[]{new StackTraceElement("android.app.ActivityThread","performLaunchActivity","ActivityThread.java",1)});
        String trace=DiagnosticTrace.describe(wrapper);
        check(trace.contains("RuntimeException > NullPointerException"),"launch wrappers retain the actual nested error type");
        check(trace.contains("mz.bam.seeky.android.ReaderActivity.onCreate:37"),"launch wrappers expose the actual app location in their cause");
        check(!trace.contains("private")&&!trace.contains("storage")&&!trace.contains("secret.pdf")&&!trace.contains("URI"),"exception messages and private document paths never enter diagnostics");
        Throwable deep=cause;for(int n=0;n<50;n++)deep=new RuntimeException("private",deep);
        check(DiagnosticTrace.describe(deep).split(" > ",-1).length==8,"cause traversal is bounded to eight entries");
        Throwable a=new RuntimeException(),b=new RuntimeException();a.initCause(b);b.initCause(a);
        check(DiagnosticTrace.describe(a).split(" > ",-1).length==2,"cyclic exception causes cannot loop forever");
        check(DiagnosticTrace.describe(new Exception()).startsWith("Exception"),"errors without app frames still report their type");
        check(DiagnosticTrace.describe(null).isEmpty(),"missing error remains safe");
        System.out.println("PASS "+checks+" diagnostic cause and privacy checks");
    }
}
