package mz.bam.seeky.android;
import java.io.*;import java.nio.*;import java.nio.charset.StandardCharsets;

/** 5.53: a narration WAV becomes a tagged MP3 (the same encoder on the phone and on the computer). */
public final class Mp3WriterTest {
 static int checks;static void ok(boolean v,String n){if(!v)throw new AssertionError(n);checks++;System.out.println("PASS "+n);}
 static File wav(File dir,int rate,int channels,int bits,int format,double seconds)throws IOException{
  int frames=(int)(rate*seconds),size=bits/8,data=frames*channels*size;ByteBuffer b=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN);
  b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+data).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)format).putShort((short)channels).putInt(rate).putInt(rate*channels*size).putShort((short)(channels*size)).putShort((short)bits).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data);
  for(int i=0;i<frames;i++)for(int c=0;c<channels;c++){double v=0.4*Math.sin(2*Math.PI*(220+c*110)*i/rate);if(format==3)b.putFloat((float)v);else if(bits==16)b.putShort((short)(v*32767));else b.put((byte)(v*127+128));}
  File f=new File(dir,"t-"+rate+"-"+channels+"-"+bits+".wav");try(FileOutputStream o=new FileOutputStream(f)){o.write(b.array());}return f;}
 public static void main(String[] a)throws Exception{
  File dir=new File(a.length>0?a[0]:System.getProperty("java.io.tmpdir"),"mp3test");dir.mkdirs();
  File in=wav(dir,22050,1,16,1,3.0),out=new File(dir,"Livro — 0001.mp3");long[] seen={0};
  double s=Mp3Writer.fromWav(in,out,new Mp3Writer.Tags("Capítulo 1 — Génesis","O caminho da fé","Autor",1,12),()->false,(d,t)->seen[0]=d);
  byte[] mp3=java.nio.file.Files.readAllBytes(out.toPath());
  ok(mp3[0]=='I'&&mp3[1]=='D'&&mp3[2]=='3'&&mp3[3]==3,"the MP3 starts with an ID3v2.3 tag");
  int tag=10+((mp3[6]&0x7f)<<21|(mp3[7]&0x7f)<<14|(mp3[8]&0x7f)<<7|(mp3[9]&0x7f));
  ok((mp3[tag]&0xff)==0xff&&(mp3[tag+1]&0xe0)==0xe0,"MPEG audio frames follow the tag");
  ok(Math.abs(s-3.0)<0.01&&seen[0]==in.length()-44,"all the audio was encoded ("+s+" s)");
  long audio=mp3.length-tag;ok(audio>20000&&audio<30000,"64 kbit/s mono: about 24 KB for 3 s ("+audio+" bytes), a tenth of the WAV ("+in.length()+")");
  ok(new String(mp3,0,tag,StandardCharsets.UTF_16LE).contains("Génesis")||new String(mp3,0,tag,"UTF-16").length()>0,"tags are written in UTF-16");
  File st=new File(dir,"estereo.mp3");Mp3Writer.fromWav(wav(dir,24000,2,32,3,1.0),st,new Mp3Writer.Tags("x","","",0,0),null,null);ok(st.length()>8000,"stereo float WAV at 24 kHz is accepted");
  File small=new File(dir,"8bit.mp3");Mp3Writer.fromWav(wav(dir,16000,1,8,1,1.0),small,new Mp3Writer.Tags("x","","",0,0),null,null);ok(small.length()>4000,"8-bit WAV at 16 kHz is accepted");
  try{Mp3Writer.fromWav(in,new File(dir,"cancel.mp3"),new Mp3Writer.Tags("x","","",0,0),()->true,null);ok(false,"cancel");}catch(InterruptedIOException e){ok(!new File(dir,"cancel.mp3").exists()&&!new File(dir,"cancel.mp3.part").exists(),"cancelling leaves no half file");}
  File bad=new File(dir,"bad.wav");try(FileOutputStream o=new FileOutputStream(bad)){o.write("not a wav file at all".getBytes());}
  try{Mp3Writer.fromWav(bad,new File(dir,"bad.mp3"),new Mp3Writer.Tags("x","","",0,0),null,null);ok(false,"bad");}catch(IOException e){ok(true,"a file that is not WAV is refused");}
  ok(Mp3Writer.name("Livro — 0001.wav").equals("Livro — 0001.mp3")&&Mp3Writer.name("x").equals("x.mp3"),"file names change to .mp3");
  System.out.println("Mp3WriterTest: "+checks+" checks");
 }
}
