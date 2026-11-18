package mz.bam.seeky.android;

/** Structural preflight before constructing JSON objects from imported notebook data. */
final class JsonBudget {
    static void check(String text,int maxDepth,int maxContainers){
        boolean quoted=false,escape=false;int depth=0,containers=0,separators=0;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;continue;}
            if((c==','||c==':')&&++separators>maxContainers*12)throw new IllegalArgumentException("Demasiados elementos na cópia.");
            if(c=='"')quoted=true;
            else if(c=='{'||c=='['){if(++depth>maxDepth||++containers>maxContainers)throw new IllegalArgumentException("Estrutura de cópia demasiado complexa.");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Estrutura de cópia inválida.");}
        }
        if(quoted||depth!=0)throw new IllegalArgumentException("Cópia truncada ou inválida.");
    }
    private JsonBudget(){}
}
