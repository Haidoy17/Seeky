package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.*;

/** Join matching WAV PCM streams without resampling or recompression. Publishes atomically. */
final class WaveJoiner implements AutoCloseable {
    interface Cancel {boolean cancelled();}
    static final long MAX_BYTES=134217728L;
    final File target,part;
    RandomAccessFile output;
    Format format;
    long bytes;
    boolean finished;
    static final class Format {
        final int codec,channels,rate,bits,align;final long offset,length;
        Format(int codec,int channels,int rate,int bits,int align,long offset,long length){this.codec=codec;this.channels=channels;this.rate=rate;this.bits=bits;this.align=align;this.offset=offset;this.length=length;}
        boolean matches(Format other){return codec==other.codec&&channels==other.channels&&rate==other.rate&&bits==other.bits&&align==other.align;}
    }
    WaveJoiner(File target)throws IOException {
        this.target=target;part=new File(target.getPath()+".part");
        if(Files.exists(target.toPath(),LinkOption.NOFOLLOW_LINKS))throw new IOException("Destino ocupado.");
        Files.createFile(part.toPath());
        try{output=new RandomAccessFile(part,"rw");output.write(new byte[44]);}catch(IOException e){close();throw e;}
    }
    static long u32(RandomAccessFile in)throws IOException{return Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));}
    static int u16(RandomAccessFile in)throws IOException{return Short.toUnsignedInt(Short.reverseBytes(in.readShort()));}
    static Format inspect(File source)throws IOException{return inspect(source,MAX_BYTES);}
    static Format inspect(File source,long maximum)throws IOException{
        try(RandomAccessFile in=new RandomAccessFile(source,"r")){
            long length=in.length();if(length<44||length>maximum+1048576)throw new IOException("Áudio WAV inválido ou demasiado grande.");
            if(in.readInt()!=0x52494646)throw new IOException("O motor não produziu áudio WAV.");long riff=u32(in);
            if(in.readInt()!=0x57415645||riff+8>length||riff<36)throw new IOException("Cabeçalho WAV incompleto.");
            int codec=0,channels=0,rate=0,bits=0,align=0;long offset=-1,data=0;boolean hasFormat=false;
            long end=riff+8;int chunks=0;
            while(in.getFilePointer()+8<=end&&chunks++<4096){
                int type=in.readInt();long count=u32(in),start=in.getFilePointer(),next=start+count+(count&1);
                if(next>end||next<start)throw new IOException("Parte WAV incompleta.");
                if(type==0x666d7420){if(hasFormat||count<16)throw new IOException("Formato WAV inválido.");hasFormat=true;codec=u16(in);channels=u16(in);long hz=u32(in),speed=u32(in);align=u16(in);bits=u16(in);if(hz<8000||hz>96000)throw new IOException("Frequência WAV indisponível.");rate=(int)hz;if(channels<1||channels>2||!(codec==1&&(bits==8||bits==16||bits==24||bits==32)||codec==3&&bits==32)||align!=channels*bits/8||speed!=(long)rate*align)throw new IOException("Formato WAV indisponível.");}
                else if(type==0x64617461){if(offset>=0)throw new IOException("Dados WAV repetidos.");offset=start;data=count;}
                in.seek(next);
            }
            if(!hasFormat||offset<0||data==0||data%align!=0)throw new IOException("Áudio WAV vazio ou incompleto.");
            return new Format(codec,channels,rate,bits,align,offset,data);
        }
    }
    void append(File source,Cancel cancel)throws IOException{
        if(finished||output==null)throw new IOException("Narração já encerrada.");if(cancel.cancelled())throw new InterruptedIOException();
        Format incoming=inspect(source);if(format!=null&&!format.matches(incoming))throw new IOException("O motor alterou o formato entre trechos. Escolha outra voz.");
        if(bytes+incoming.length>MAX_BYTES)throw new IOException("Narração acima de 128 MB. Escolha um trecho menor.");if(format==null)format=incoming;
        try(RandomAccessFile in=new RandomAccessFile(source,"r")){in.seek(incoming.offset);long left=incoming.length;byte[] buffer=new byte[65536];while(left>0){if(cancel.cancelled())throw new InterruptedIOException();int count=in.read(buffer,0,(int)Math.min(buffer.length,left));if(count<0)throw new EOFException();output.write(buffer,0,count);bytes+=count;left-=count;}}
    }
    void finish(Cancel cancel)throws IOException{finish(cancel,null);}
    void finish(Cancel cancel,WavInfo metadata)throws IOException{
        if(finished||output==null||format==null||bytes==0)throw new IOException("Narração vazia.");if(cancel.cancelled())throw new InterruptedIOException();
        if((bytes&1)!=0)output.write(0);byte[] info=metadata==null?new byte[0]:metadata.chunk();output.write(info);output.seek(0);output.writeInt(0x52494646);write32(output,36+bytes+(bytes&1)+info.length);output.writeInt(0x57415645);output.writeInt(0x666d7420);write32(output,16);write16(output,format.codec);write16(output,format.channels);write32(output,format.rate);write32(output,(long)format.rate*format.align);write16(output,format.align);write16(output,format.bits);output.writeInt(0x64617461);write32(output,bytes);output.getFD().sync();output.close();output=null;
        if(cancel.cancelled())throw new InterruptedIOException();Files.move(part.toPath(),target.toPath());finished=true;
    }
    static void write16(RandomAccessFile out,int n)throws IOException{out.writeShort(Short.reverseBytes((short)n));}
    static void write32(RandomAccessFile out,long n)throws IOException{out.writeInt(Integer.reverseBytes((int)n));}
    public void close(){if(output!=null)try{output.close();}catch(IOException ignored){}output=null;if(!finished)part.delete();}
}
