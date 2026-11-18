package mz.bam.seeky.android;
import java.io.*;import java.util.function.BooleanSupplier;
/** PCM WAV backgrounds do not need a device codec. Conversion is only for the background. */
final class BackgroundWave {
 static WaveJoiner.Format decode(File source,File target,BooleanSupplier cancel)throws IOException{WaveJoiner.Format f=WaveJoiner.inspect(source,268435456L);try(RandomAccessFile in=new RandomAccessFile(source,"r");FileOutputStream out=new FileOutputStream(target)){in.seek(f.offset);byte[] input=new byte[65536/f.align*f.align];long left=f.length;while(left>0){if(cancel.getAsBoolean())throw new InterruptedIOException();int count=(int)Math.min(left,input.length);in.readFully(input,0,count);byte[] output=new byte[count/(f.bits/8)*2];int at=0;for(int n=0;n<count;n+=f.bits/8){int value=(int)Math.max(-32768,Math.min(32767,Math.round(BackgroundMixer.decode(input,n,f.codec,f.bits)*32768)));output[at++]=(byte)value;output[at++]=(byte)(value>>8);}out.write(output);left-=count;}out.getFD().sync();}return f;}
}
