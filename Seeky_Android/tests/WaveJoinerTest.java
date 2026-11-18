package mz.bam.seeky.android;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class WaveJoinerTest {
    static int checks;
    interface Work{void run()throws Exception;}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void rejects(Work work,String label)throws Exception{boolean rejected=false;try{work.run();}catch(IOException expected){rejected=true;}check(rejected,label);}
    static void wav(File f,int rate,int channels,int bits,byte[] data)throws IOException{
        try(RandomAccessFile out=new RandomAccessFile(f,"rw")){out.setLength(0);out.writeInt(0x52494646);WaveJoiner.write32(out,36+data.length+(data.length&1));out.writeInt(0x57415645);out.writeInt(0x666d7420);WaveJoiner.write32(out,16);WaveJoiner.write16(out,1);WaveJoiner.write16(out,channels);WaveJoiner.write32(out,rate);WaveJoiner.write32(out,rate*channels*bits/8);WaveJoiner.write16(out,channels*bits/8);WaveJoiner.write16(out,bits);out.writeInt(0x64617461);WaveJoiner.write32(out,data.length);out.write(data);if((data.length&1)!=0)out.write(0);}
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("seeky-audio-test-");
        try{
            File a=dir.resolve("a.wav").toFile(),b=dir.resolve("b.wav").toFile(),target=dir.resolve("joined.wav").toFile();
            byte[] one={1,2,3,4,5,6},two={7,8,9,10};wav(a,24000,1,16,one);wav(b,24000,1,16,two);
            try(WaveJoiner joiner=new WaveJoiner(target)){joiner.append(a,()->false);joiner.append(b,()->false);check(!target.exists(),"incomplete narration is never published as a ready file");joiner.finish(()->false);}
            WaveJoiner.Format fmt=WaveJoiner.inspect(target);check(fmt.rate==24000&&fmt.channels==1&&fmt.bits==16&&fmt.length==10,"joined narration retains the selected engine PCM format");
            byte[] data=Files.readAllBytes(target.toPath());check(Arrays.equals(Arrays.copyOfRange(data,44,54),new byte[]{1,2,3,4,5,6,7,8,9,10}),"offline narration preserves every audio sample without recompression");
            rejects(()->new WaveJoiner(target),"existing audio files are never overwritten");
            File changed=dir.resolve("changed.wav").toFile();wav(b,22050,1,16,two);try(WaveJoiner joiner=new WaveJoiner(changed)){joiner.append(a,()->false);rejects(()->joiner.append(b,()->false),"voice format changes abort instead of changing pitch or speed");}check(!changed.exists()&&!new File(changed+".part").exists(),"failed audio generation cleans its own partial output");
            File cancelled=dir.resolve("cancel.wav").toFile();try(WaveJoiner joiner=new WaveJoiner(cancelled)){rejects(()->joiner.append(a,()->true),"audio generation respects cancellation before copying");}check(!cancelled.exists(),"cancelled narration is not published");
            File damaged=dir.resolve("damaged.wav").toFile();Files.write(damaged.toPath(),new byte[44]);rejects(()->WaveJoiner.inspect(damaged),"non-WAV engine output fails clearly");
            File truncated=dir.resolve("truncated.wav").toFile();wav(truncated,24000,1,16,one);try(RandomAccessFile file=new RandomAccessFile(truncated,"rw")){file.setLength(45);}rejects(()->WaveJoiner.inspect(truncated),"truncated engine output cannot create a playable-looking file");
            File odd=dir.resolve("odd.wav").toFile(),oddOut=dir.resolve("odd-out.wav").toFile();wav(odd,16000,1,8,new byte[]{1,2,3});try(WaveJoiner joiner=new WaveJoiner(oddOut)){joiner.append(odd,()->false);joiner.finish(()->false);}check(WaveJoiner.inspect(oddOut).length==3&&oddOut.length()==48,"odd PCM8 payloads receive a valid RIFF pad byte");
            File occupied=dir.resolve("occupied.wav").toFile(),part=new File(occupied+".part");Files.write(part.toPath(),new byte[]{42});rejects(()->new WaveJoiner(occupied),"another operation's partial file is not truncated");check(Files.readAllBytes(part.toPath())[0]==42,"unowned partial audio is preserved");
            File large=dir.resolve("large.wav").toFile();wav(large,24000,1,16,new byte[]{0,0});try(RandomAccessFile file=new RandomAccessFile(large,"rw")){file.setLength(WaveJoiner.MAX_BYTES+1048577);}rejects(()->WaveJoiner.inspect(large),"oversized engine files are rejected before allocation");
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(dir)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
        System.out.println("PASS "+checks+" WAV preservation and file safety checks");
    }
}
