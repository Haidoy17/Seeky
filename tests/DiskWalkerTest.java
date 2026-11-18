package mz.bam.seeky.android;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class DiskWalkerTest {
    static int passed=0;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    static void make(Path root,String path)throws IOException{Path file=root.resolve(path);Files.createDirectories(file.getParent());Files.write(file,path.getBytes("UTF-8"));}
    static List<String> walk(Path root,boolean hidden,int max,long deadline)throws Exception{
        List<String> files=new ArrayList<>();DiskWalker.walk(root.toFile(),hidden,max,deadline,new DiskWalker.Visitor(){public void file(File f){files.add(root.relativize(f.toPath()).toString());}public void unreadable(File f){}public boolean cancelled(){return false;}});return files;
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("seeky-files-"),outside=Files.createTempDirectory("seeky-outside-");
        try{
            make(root,"Documentos/relatorio.PDF");make(root,"Desenho/projecto.psd");make(root,"Instaladores/app.apk");make(root,".ocultos/.secret");make(root,"Android/data/outro.app/privado.txt");make(root,"Android/obb/outro.app/data.bin");make(root,"Android/media/com.whatsapp/documento.pdf");make(outside,"fora.txt");
            Files.createSymbolicLink(root.resolve("atalho"),outside);
            List<String> all=walk(root,true,100,Long.MAX_VALUE);
            check(all.size()==5,"all accessible extensions, hidden files and Android/media");
            check(all.stream().anyMatch(s->s.endsWith(".psd")),"unknown binary extension is found");
            check(all.stream().noneMatch(s->s.contains("privado")||s.contains("data.bin")),"protected app directories excluded");
            check(all.stream().noneMatch(s->s.contains("fora")),"symlink cannot escape volume");
            check(walk(root,false,100,Long.MAX_VALUE).size()==4,"hidden preference");
            check(DiskWalker.inside(root.toFile(),root.resolve("Documentos/relatorio.PDF").toFile()),"canonical containment allows child");
            check(!DiskWalker.inside(root.toFile(),outside.toFile()),"canonical containment rejects external directory");
            check(walk(root,true,2,Long.MAX_VALUE).size()==2,"result count bounded");
            List<String> got=new ArrayList<>();boolean interrupted=false;try{DiskWalker.walk(root.toFile(),true,100,Long.MAX_VALUE,new DiskWalker.Visitor(){public void file(File f){got.add(f.getName());}public void unreadable(File f){}public boolean cancelled(){return got.size()>=2;}});}catch(InterruptedIOException e){interrupted=true;}
            check(interrupted&&got.size()==2,"cancellation stops real traversal");
            DiskWalker.Summary timed=DiskWalker.walk(root.toFile(),true,100,System.nanoTime()-1,new DiskWalker.Visitor(){public void file(File f){throw new AssertionError();}public void unreadable(File f){}public boolean cancelled(){return false;}});
            check(timed.limited&&timed.files==0,"deadline reports partial traversal");
            boolean missing=false;try{walk(root.resolve("missing"),true,100,Long.MAX_VALUE);}catch(IOException e){missing=true;}check(missing,"disconnected or missing root fails explicitly");
            check(Rules.category("backup.7z","application/octet-stream").equals("Arquivos"),"archive category");
            check(Rules.category("programa.XAPK","application/octet-stream").equals("Aplicações"),"installer category");
            check(Rules.nameMatches("Relatório.PDF","*.pdf"),"wildcard extension search");
            check(Rules.nameMatches("foto1.jpg","foto?.jpg"),"single-character wildcard");
            check(!Rules.nameMatches("foto12.jpg","foto?.jpg"),"wildcard length remains exact");
            check(Rules.nameMatches("[1] imposto%.pdf","[1] imposto%.pdf"),"punctuation is literal");
            check(Rules.likeQuery("*.pdf").equals("%.pdf"),"glob to bound SQL parameter");
            System.out.println("Passed "+passed+" filesystem checks");
        }finally{for(Path base:new Path[]{root,outside})try(java.util.stream.Stream<Path> paths=Files.walk(base)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    }
}
