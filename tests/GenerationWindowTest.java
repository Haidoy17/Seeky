package mz.bam.seeky.android;
public class GenerationWindowTest {
    static int checks=0;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        GenerationWindow first=GenerationWindow.open("v1","",0,0,0,50);
        check(first.from==0&&first.through==50&&first.afterId==0&&first.reset,"first system-catalog page starts from zero");
        GenerationWindow pending=GenerationWindow.open("v1","v1",0,50,256,55);
        check(pending.through==50&&pending.afterId==256&&!pending.reset,"pending pages keep a stable generation boundary");
        check(!pending.contains(51),"change arriving during page traversal is reserved for the next window");
        GenerationWindow next=GenerationWindow.open("v1","v1",50,0,0,55);
        check(next.contains(51)&&next.contains(55)&&!next.contains(50),"next delta includes all changes after the completed boundary");
        GenerationWindow rebuilt=GenerationWindow.open("v2","v1",900,950,1000,10);
        check(rebuilt.reset&&rebuilt.from==0&&rebuilt.through==10&&rebuilt.afterId==0,"rebuilt MediaStore discards old generation and page cursor");
        GenerationWindow backwards=GenerationWindow.open("v1","v1",900,0,0,10);
        check(backwards.reset&&backwards.from==0,"unexpected generation rollback safely resets the checkpoint");
        GenerationWindow idle=GenerationWindow.open("v2","v2",10,0,0,10);
        check(idle.from==idle.through,"unchanged generation needs no full metadata query");
        System.out.println("Passed "+checks+" generation checks");
    }
}
