package mz.bam.seeky.android;

import java.util.*;

/** Stable title-first names: preserve Unicode and keep the identifying number and extension. */
final class AudioTitles {
    static String clean(String value){return value==null?"":value.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]"," ").replaceAll("[\\s\\u00a0]+"," ").trim().replaceAll("[. ]+$","");}
    static String book(String metadata,String name,String shown){String edited=clean(shown),file=clean(name);if(!edited.isEmpty()&&!edited.equals(file)&&!edited.equals(base(name)))return edited;String title=clean(metadata);return title.isEmpty()?base(name):title;}
    static String base(String name){String value=clean(name);int dot=value.lastIndexOf('.');if(dot>0)value=value.substring(0,dot);return value.isEmpty()?"Livro":value;}
    static String limit(String text,int units,int utf8){StringBuilder out=new StringBuilder();int bytes=0;for(int at=0;at<text.length();){int cp=text.codePointAt(at),n=Character.charCount(cp),size=cp<128?1:cp<2048?2:cp<65536?3:4;if(out.length()+n>units||bytes+size>utf8)break;out.appendCodePoint(cp);bytes+=size;at+=n;}return out.toString().trim();}
    static boolean generic(String value){return clean(value).matches("(?iu)(cap[íi]tulo|chapter|parte|part|p[áa]gina|page|documento|document)([ .:_-]*[0-9]+)?");}
    static String firstHeading(String source){if(source==null)return "";int stop=Math.min(source.length(),2048);String prefix=source.substring(0,stop);for(String line:prefix.split("[\\r\\n]+")){String raw=line.trim();boolean markdown=raw.matches("^#{1,6}\\s+.*");String text=clean(raw.replaceFirst("^\\s*#{1,6}\\s*",""));if(text.isEmpty())continue;if(text.length()>=3&&text.length()<=120&&(markdown||!raw.matches(".*[.!?;:,]$")))return text;break;}return "";}
    static String track(String book,String chapter,int part,int piece){return track(book,chapter,part,piece,piece>1);}
    static String track(String book,String chapter,int part,int piece,boolean split){String parent=clean(book),section=clean(chapter);String title=section.isEmpty()||section.equalsIgnoreCase(parent)?parent:parent.isEmpty()?section:parent+" — "+section;return (title.isEmpty()?"Narração":title)+" — "+String.format(Locale.ROOT,"%04d",part+1)+(split?"-"+String.format(Locale.ROOT,"%02d",piece):"");}
    static String file(String book,String chapter,int part,int piece){return file(book,chapter,part,piece,piece>1);}
    static String file(String book,String chapter,int part,int piece,boolean split){if(part<0||piece<1)throw new IllegalArgumentException("Numeração inválida.");String parent=limit(clean(book),36,96),section=limit(clean(chapter),64,160);String text=section.isEmpty()||section.equalsIgnoreCase(parent)?parent:parent.isEmpty()?section:parent+" — "+section;if(text.isEmpty())text="Narração";String number=" — "+String.format(Locale.ROOT,"%04d",part+1)+(split?"-"+String.format(Locale.ROOT,"%02d",piece):"")+".wav";return limit(text,118-number.length(),210-number.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)+number;}
    private AudioTitles(){}
}
