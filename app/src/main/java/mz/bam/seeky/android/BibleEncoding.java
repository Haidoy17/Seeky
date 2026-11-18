package mz.bam.seeky.android;
import java.io.*;import java.nio.*;import java.nio.charset.*;

/** Bounded prefix detection, followed by strict decoding: never silently replace Bible letters. */
final class BibleEncoding {
 static DecodedReader reader(InputStream input)throws IOException {
  PushbackInputStream in=new PushbackInputStream(input,8192);byte[] head=new byte[8192];int count=0,n;
  while(count<head.length&&(n=in.read(head,count,head.length-count))>0)count+=n;
  int skip=0;Charset charset=StandardCharsets.UTF_8;
  if(count>=3&&(head[0]&255)==239&&(head[1]&255)==187&&(head[2]&255)==191)skip=3;
  else if(count>=2&&(head[0]&255)==255&&(head[1]&255)==254){skip=2;charset=StandardCharsets.UTF_16LE;}
  else if(count>=2&&(head[0]&255)==254&&(head[1]&255)==255){skip=2;charset=StandardCharsets.UTF_16BE;}
  else {CharsetDecoder test=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);CoderResult result=test.decode(ByteBuffer.wrap(head,0,count),CharBuffer.allocate(8192),false);if(result.isError())charset=Charset.forName("windows-1252");}
  if(count>skip)in.unread(head,skip,count-skip);
  return new DecodedReader(in,charset==StandardCharsets.UTF_8&&skip==0,charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
 }
 interface Opener{InputStream open()throws IOException;}
 interface Reset{void run()throws Exception;}
 static final class DecodedReader extends InputStreamReader{final boolean retry;DecodedReader(InputStream in,boolean retry,CharsetDecoder decoder){super(in,decoder);this.retry=retry;}}
 static InputStream bounded(InputStream raw){return new FilterInputStream(raw){long size;public int read()throws IOException{int n=in.read();if(n>=0&&++size>67108864)throw new IOException("Bíblia acima de 64 MB.");return n;}public int read(byte[] b,int off,int length)throws IOException{int n=in.read(b,off,length);if(n>0&&(size+=n)>67108864)throw new IOException("Bíblia acima de 64 MB.");return n;}};}
 static int parse(Opener opener,BibleText.Sink sink,Reset reset,java.util.function.BooleanSupplier stop)throws Exception {
  boolean fallback=false;try(InputStream raw=bounded(opener.open());DecodedReader decoded=reader(raw)){try{return BibleText.parse(decoded,sink,stop);}catch(CharacterCodingException encoding){if(!decoded.retry)throw encoding;fallback=true;}}
  if(fallback){reset.run();try(InputStream raw=bounded(opener.open());Reader decoded=new InputStreamReader(raw,Charset.forName("windows-1252").newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))){return BibleText.parse(decoded,sink,stop);}}
  throw new IOException("Não foi possível descodificar a Bíblia.");
 }
 private BibleEncoding(){}
}
