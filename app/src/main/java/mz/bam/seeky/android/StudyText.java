package mz.bam.seeky.android;
import java.util.*;
/** Offset based local draft. The source document is never written by this model. */
final class StudyText {
 static final class Mark {int start,end;String kind,note;Mark(int s,int e,String k,String n){start=s;end=e;kind=k;note=n==null?"":n;}}
 String text;final List<Mark> marks=new ArrayList<>();
 StudyText(String t){text=t==null?"":t;}
 StudyText copy(){StudyText value=new StudyText(text);for(Mark m:marks)value.marks.add(new Mark(m.start,m.end,m.kind,m.note));return value;}
 void mark(int start,int end,String kind,String note){range(start,end);if(start==end)throw new IllegalArgumentException("Seleccione texto.");marks.add(new Mark(start,end,kind,note));}
 void range(int s,int e){if(s<0||e<s||e>text.length())throw new IllegalArgumentException("Selecção inválida.");}
 void replace(int start,int end,String value){range(start,end);if(value==null)value="";int shift=value.length()-(end-start);List<Mark> keep=new ArrayList<>();for(Mark m:marks){if(m.end<=start){keep.add(m);}else if(m.start>=end){m.start+=shift;m.end+=shift;keep.add(m);}else { // Annotation crossing an edit is discarded instead of attaching it to different words.
 }}marks.clear();marks.addAll(keep);text=text.substring(0,start)+value+text.substring(end);}
 static List<Integer> find(String hay,String needle,int cap){List<Integer> out=new ArrayList<>();if(needle==null||needle.isEmpty())return out;for(int i=0;i<=hay.length()-needle.length()&&out.size()<cap;i++)if(hay.regionMatches(true,i,needle,0,needle.length())){out.add(i);i+=needle.length()-1;}return out;}
 static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
 String html(int start,int end){range(start,end);TreeSet<Integer> edges=new TreeSet<>();edges.add(start);edges.add(end);for(Mark m:marks){if(m.start>start&&m.start<end)edges.add(m.start);if(m.end>start&&m.end<end)edges.add(m.end);}StringBuilder out=new StringBuilder();int previous=start;for(int edge:edges){if(edge==previous)continue;boolean h=false,u=false,k=false;for(Mark m:marks)if(m.start<=previous&&m.end>previous){h|=m.kind.startsWith("highlight");u|=m.kind.equals("underline")||m.kind.startsWith("underline-")||m.kind.startsWith("squiggle");k|=m.kind.startsWith("strike");}if(h)out.append("<mark style=\"background-color:#ffe58a;color:#272237\">");if(u)out.append("<u>");if(k)out.append("<s>");out.append(escape(text.substring(previous,edge)).replace("\n","<br>\n"));if(k)out.append("</s>");if(u)out.append("</u>");if(h)out.append("</mark>");previous=edge;}return out.toString();}
}
