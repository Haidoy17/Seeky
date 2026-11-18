package mz.bam.seeky.android;

/** A stable upper boundary prevents missing changes arriving during pagination. */
final class GenerationWindow {
    final long from,through,afterId;final boolean reset;
    private GenerationWindow(long from,long through,long id,boolean reset){this.from=from;this.through=through;this.afterId=id;this.reset=reset;}
    static GenerationWindow open(String current,String saved,long done,long pending,long cursor,long latest){
        boolean reset=!current.equals(saved)||latest<done;
        return new GenerationWindow(reset?0:done,reset||pending==0?latest:pending,reset||pending==0?0:cursor,reset);
    }
    boolean contains(long generation){return generation>from&&generation<=through;}
}
