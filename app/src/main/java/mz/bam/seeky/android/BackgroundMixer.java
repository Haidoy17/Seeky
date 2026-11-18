package mz.bam.seeky.android;

import java.io.*;

/** Sample-accurate streaming mix. Background gain never changes the voice gain. */
final class BackgroundMixer {
 static final class Source implements AutoCloseable {
  final RandomAccessFile in;final int rate,channels;final long frames;final boolean complete;final byte[] cache=new byte[65536];long start=-1;int count;
  Source(File file,int rate,int channels)throws IOException{this(file,rate,channels,true);}
  Source(File file,int rate,int channels,boolean complete)throws IOException{in=new RandomAccessFile(file,"r");this.rate=rate;this.channels=channels;this.complete=complete;frames=in.length()/Math.max(1,channels*2);if(frames<1||rate<1||channels<1||channels>8)throw new IOException("Fundo inválido.");}
  double sample(long frame)throws IOException{if(!complete&&frame>=frames)throw new EOFException("O fundo ainda não foi preparado até esta posição.");frame=Math.floorMod(frame,frames);if(frame<start||frame>=start+count){start=frame;int bytes=(int)Math.min(cache.length/(channels*2),frames-frame)*channels*2;in.seek(frame*channels*2);in.readFully(cache,0,bytes);count=bytes/(channels*2);}int offset=(int)(frame-start)*channels*2;double value=0;for(int channel=0;channel<channels;channel++){int at=offset+channel*2;value+=(short)((cache[at]&255)|(cache[at+1]<<8))/32768.0;}value/=channels;long fade=Math.min(frames/4,rate/100);if(fade>0)value*=Math.min(1.0,Math.min(frame/(double)fade,complete?(frames-1-frame)/(double)fade:1.0));return value;}
  public void close()throws IOException{in.close();}
 }
 static void mix(File voice,File target,File background,int bgRate,int bgChannels,long firstFrame,int volume,WaveJoiner.Cancel cancelled)throws IOException{
  mix(voice,target,background,bgRate,bgChannels,firstFrame,volume,cancelled,true);
 }
 static void mix(File voice,File target,File background,int bgRate,int bgChannels,long firstFrame,int volume,WaveJoiner.Cancel cancelled,boolean complete)throws IOException{
  WaveJoiner.Format f=WaveJoiner.inspect(voice);try(Source bg=new Source(background,bgRate,bgChannels,complete);RandomAccessFile input=new RandomAccessFile(voice,"r");RandomAccessFile out=new RandomAccessFile(target,"rw")){out.setLength(0);out.write(new byte[44]);input.seek(f.offset);long left=f.length,frame=firstFrame;byte[] buffer=new byte[65536/f.align*f.align];double gain=Math.max(0,Math.min(100,volume))/100.0;
   while(left>0){if(cancelled.cancelled())throw new InterruptedIOException();int count=(int)Math.min(left,buffer.length);input.readFully(buffer,0,count);for(int at=0;at<count;at+=f.align,frame++){double position=frame*(double)bgRate/f.rate;long index=(long)position;double blend=position-index,bed=(bg.sample(index)*(1-blend)+bg.sample(index+1)*blend)*gain;for(int channel=0;channel<f.channels;channel++){int offset=at+channel*f.bits/8;if(gain!=0)encode(buffer,offset,f.codec,f.bits,Math.max(-1,Math.min(1,decode(buffer,offset,f.codec,f.bits)+bed)));}}out.write(buffer,0,count);left-=count;}
   if((f.length&1)!=0)out.write(0);out.seek(0);out.writeInt(0x52494646);WaveJoiner.write32(out,36+f.length+(f.length&1));out.writeInt(0x57415645);out.writeInt(0x666d7420);WaveJoiner.write32(out,16);WaveJoiner.write16(out,f.codec);WaveJoiner.write16(out,f.channels);WaveJoiner.write32(out,f.rate);WaveJoiner.write32(out,(long)f.rate*f.align);WaveJoiner.write16(out,f.align);WaveJoiner.write16(out,f.bits);out.writeInt(0x64617461);WaveJoiner.write32(out,f.length);out.getFD().sync();}
 }
 static double decode(byte[] b,int at,int codec,int bits){if(codec==3){int value=0;for(int n=0;n<4;n++)value|=(b[at+n]&255)<<(8*n);return Float.intBitsToFloat(value);}if(bits==8)return ((b[at]&255)-128)/128.0;long value=0;for(int n=0;n<bits/8;n++)value|=(long)(b[at+n]&255)<<(8*n);if((value&(1L<<(bits-1)))!=0)value-=1L<<bits;return value/(double)(1L<<(bits-1));}
 static void encode(byte[] b,int at,int codec,int bits,double value){long sample;if(codec==3)sample=Integer.toUnsignedLong(Float.floatToIntBits((float)value));else if(bits==8)sample=Math.max(0,Math.min(255,Math.round(value*128+128)));else{long scale=1L<<(bits-1);sample=Math.max(-scale,Math.min(scale-1,Math.round(value*scale)));}for(int n=0;n<bits/8;n++)b[at+n]=(byte)(sample>>>(n*8));}
}
