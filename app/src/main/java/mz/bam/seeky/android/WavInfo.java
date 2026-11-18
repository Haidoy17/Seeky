package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional RIFF INFO tags appended after PCM. No encoding or sample conversion. */
final class WavInfo {
    final String title,book,author;final int track,rate;final List<WavCues.Cue> cues;
    WavInfo(String title,String book,String author,int track){this(title,book,author,track,Collections.emptyList(),0);}
    WavInfo(String title,String book,String author,int track,List<WavCues.Cue> cues,int rate){this.title=title;this.book=book;this.author=author;this.track=track;this.cues=cues;this.rate=rate;}
    byte[] chunk()throws IOException{
        Map<String,String> tags=new LinkedHashMap<>();tags.put("INAM",title);tags.put("IPRD",book);tags.put("IART",author);tags.put("ITRK",Integer.toString(Math.max(1,track)));tags.put("ISFT","Seeky 5.12.0 · Haidoy Carlos Mautempo");
        ByteArrayOutputStream payload=new ByteArrayOutputStream();payload.write("INFO".getBytes(StandardCharsets.US_ASCII));
        for(Map.Entry<String,String> entry:tags.entrySet()){String text=AudioTitles.limit(AudioTitles.clean(entry.getValue()),512,1024);if(text.isEmpty())continue;byte[] value=text.getBytes(StandardCharsets.UTF_8);payload.write(entry.getKey().getBytes(StandardCharsets.US_ASCII));le(payload,value.length+1);payload.write(value);payload.write(0);if(((value.length+1)&1)!=0)payload.write(0);}
        ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("LIST".getBytes(StandardCharsets.US_ASCII));le(out,payload.size());payload.writeTo(out);return out.toByteArray();
    }
    static void le(OutputStream out,int value)throws IOException{for(int n=0;n<4;n++)out.write(value>>>(n*8)&255);}
}
