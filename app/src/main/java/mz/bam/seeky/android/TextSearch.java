package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.*;

/** Accent-insensitive matching that returns ranges in the original UTF-16 text. */
final class TextSearch {
    static final class Match {final int start,end;Match(int start,int end){this.start=start;this.end=end;}}
    static final class Fold {
        final String text;final int[] start,end;
        Fold(String input,boolean accents){
            StringBuilder out=new StringBuilder();int[] begin=new int[Math.max(16,input.length())],finish=new int[begin.length];
            for(int at=0;at<input.length();){
                int point=input.codePointAt(at),next=at+Character.charCount(point);String unit=new String(Character.toChars(point));
                if(accents)unit=Normalizer.normalize(unit,Normalizer.Form.NFD);
                for(int i=0;i<unit.length();){int p=unit.codePointAt(i);i+=Character.charCount(p);int type=Character.getType(p);
                    if(accents&&(type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK)){if(out.length()>0)finish[out.length()-1]=next;continue;}
                    String lower=new String(Character.toChars(Character.toLowerCase(p)));
                    for(int n=0;n<lower.length();n++){if(out.length()==begin.length){begin=Arrays.copyOf(begin,begin.length*2);finish=Arrays.copyOf(finish,finish.length*2);}int index=out.length();begin[index]=at;finish[index]=next;out.append(lower.charAt(n));}
                }at=next;
            }text=out.toString();start=begin;end=finish;
        }
    }
    static boolean word(int point){int type=Character.getType(point);return Character.isLetterOrDigit(point)||point=='_'||type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK;}
    static List<Match> find(String text,String query,int limit,boolean accents,boolean whole){
        List<Match> found=new ArrayList<>();if(query==null||query.isEmpty()||limit<=0)return found;
        Fold hay=new Fold(text,accents),needle=new Fold(query,accents);if(needle.text.isEmpty())return found;
        for(int at=0;at<=hay.text.length()-needle.text.length()&&found.size()<limit;){int match=hay.text.indexOf(needle.text,at);if(match<0)break;int start=hay.start[match],end=hay.end[match+needle.text.length()-1];
            boolean boundary=!whole||(start==0||!word(text.codePointBefore(start)))&&(end==text.length()||!word(text.codePointAt(end)));
            if(boundary)found.add(new Match(start,end));at=match+needle.text.length();
        }return found;
    }
    private TextSearch(){}
}
