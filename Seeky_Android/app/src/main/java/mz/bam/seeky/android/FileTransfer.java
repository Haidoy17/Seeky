package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** File-only transfers. Stage, verify, publish without replacement, then remove source on move. */
final class FileTransfer {
    interface Control { boolean cancelled(); boolean allowed(File file); void progress(long bytes,String phase); }
    static final class Result { final File target; final boolean moved,direct; Result(File t,boolean m){this(t,m,false);}Result(File t,boolean m,boolean direct){target=t;moved=m;this.direct=direct;} }
    static void check(Control c)throws IOException {if(c.cancelled())throw new InterruptedIOException("Operação cancelada");}
    static void permit(Control c,File f)throws IOException {if(!c.allowed(f)||Files.isSymbolicLink(f.toPath()))throw new IOException("Caminho fora do acesso autorizado ou ligação simbólica: "+f);}
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static byte[] hash(File file,Control c,String phase)throws IOException {MessageDigest d=digest();byte[] buffer=new byte[262144];long read=0;try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(buffer))!=-1){check(c);d.update(buffer,0,n);read+=n;c.progress(read,phase);}}return d.digest();}
    static boolean unchanged(File source,BasicFileAttributes original)throws IOException {
        BasicFileAttributes now=Files.readAttributes(source.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        return now.isRegularFile()&&now.size()==original.size()&&now.lastModifiedTime().equals(original.lastModifiedTime())&&Objects.equals(now.fileKey(),original.fileKey());
    }
    static Result run(File source,File directory,boolean move,Control c)throws IOException {
        return run(source,directory,move,c,true);
    }
    static Result run(File source,File directory,boolean move,Control c,boolean direct)throws IOException {
        check(c);permit(c,source);permit(c,directory);
        if(!source.isFile()||!directory.isDirectory())throw new IOException("Seleccione ficheiros e uma pasta de destino disponível.");
        File target=new File(directory,source.getName());permit(c,target);
        if(source.getCanonicalFile().equals(target.getCanonicalFile()))throw new IOException("A origem e o destino são iguais.");
        if(Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Já existe: "+target.getName()+". Nenhum ficheiro foi substituído.");
        BasicFileAttributes original=Files.readAttributes(source.toPath(),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!original.isRegularFile())throw new IOException("A origem não é um ficheiro regular.");
        if(move&&direct){Result linked=directMove(source,target,original,c);if(linked!=null)return linked;}
        if(directory.getUsableSpace()<original.size())throw new IOException("Espaço insuficiente no destino.");
        File staged=File.createTempFile(".seeky-transfer-",".part",directory);boolean published=false;
        try {
            MessageDigest d=digest();byte[] buffer=new byte[262144];long copied=0;
            try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(staged)) {
                int n;while((n=in.read(buffer))!=-1){check(c);out.write(buffer,0,n);d.update(buffer,0,n);copied+=n;c.progress(copied,"A copiar");}
                out.getFD().sync();
            }
            check(c);permit(c,source);permit(c,directory);permit(c,target);
            byte[] expected=d.digest();
            if(copied!=original.size()||!unchanged(source,original)||!Arrays.equals(expected,hash(staged,c,"A verificar cópia")))throw new IOException("A origem mudou ou a cópia não pôde ser verificada. Origem conservada.");
            check(c);Files.move(staged.toPath(),target.toPath());published=true;
            // Preserve timestamp when supported. Failure does not invalidate verified contents.
            target.setLastModified(original.lastModifiedTime().toMillis());
            if(move){
                if(c.cancelled())throw new IOException("Cancelado após a cópia: ambos os ficheiros foram conservados em "+target);
                permit(c,source);permit(c,target);
                if(!unchanged(source,original)||!Arrays.equals(expected,hash(source,c,"A verificar origem"))||!unchanged(source,original))throw new IOException("A origem mudou. Cópia verificada em "+target+"; origem conservada.");
                // The published destination may have been changed by another app while verifying the source.
                if(!Arrays.equals(expected,hash(target,c,"A confirmar destino")))throw new IOException("O destino mudou. Origem conservada; reveja "+target);
                check(c);permit(c,source);permit(c,target);
                if(!unchanged(source,original))throw new IOException("A origem mudou; ambos conservados.");
                try{Files.delete(source.toPath());}catch(IOException e){throw new IOException("Cópia concluída em "+target+"; não foi possível remover a origem.",e);}
            }
            return new Result(target,move);
        } catch(InterruptedIOException e){if(published)throw new IOException("Operação interrompida após cópia: origem conservada e cópia em "+target,e);throw e;} finally {if(!published)Files.deleteIfExists(staged.toPath());}
    }
    /** Atomically publishes a new hard link without replacement, then unlinks the source.
     * Unlike rename(2), createLink cannot replace a racing destination. Android shared
     * storage may reject hard links; that case keeps the verified copy fallback. */
    static Result directMove(File source,File target,BasicFileAttributes original,Control c)throws IOException {
        check(c);permit(c,source);permit(c,target);
        try{Files.createLink(target.toPath(),source.toPath());}
        catch(FileAlreadyExistsException e){throw new IOException("Já existe: "+target.getName()+". Nenhum ficheiro foi substituído.",e);}
        catch(UnsupportedOperationException|IOException unavailable){return null;}
        boolean removed=false;
        try{
            c.progress(original.size(),"A mover no mesmo volume");check(c);permit(c,source);permit(c,target);
            if(!unchanged(source,original)||!Files.isSameFile(source.toPath(),target.toPath()))throw new IOException("A origem mudou; movimento interrompido.");
            Files.delete(source.toPath());removed=true;return new Result(target,true,true);
        }finally{
            // Remove only our alias while a matching source still exists. Keep the
            // destination if another application has removed or replaced the source.
            if(!removed&&Files.exists(source.toPath(),LinkOption.NOFOLLOW_LINKS)&&Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS)&&Files.isSameFile(source.toPath(),target.toPath()))Files.delete(target.toPath());
        }
    }
    private FileTransfer(){}
}
