package mz.bam.seeky.android;
import java.util.*;import java.util.regex.*;
/** App-generated excerpt IDs. No model-supplied page number becomes a navigation target. */
final class StudyEvidence {
 static final Pattern CITATION=Pattern.compile("\\[T([1-9][0-9]{0,2})\\]");
 static final class Part{final int number,start,end;final String text;Part(int number,int start,int end,String source){this.number=number;this.start=start;this.end=end;text=source.substring(start,end);}}
 static List<Part> parts(String source){List<Part> out=new ArrayList<>();int at=0;while(at<source.length()&&out.size()<80){while(at<source.length()&&Character.isWhitespace(source.charAt(at)))at++;if(at>=source.length())break;int end=Math.min(source.length(),at+600);if(end<source.length()){int paragraph=source.lastIndexOf("\n\n",end);if(paragraph>at+150)end=paragraph;else{int space=source.lastIndexOf(' ',end);if(space>at+300)end=space;}}if(out.size()==79)end=source.length();if(end<source.length()&&Character.isLowSurrogate(source.charAt(end)))end--;int trim=end;while(trim>at&&Character.isWhitespace(source.charAt(trim-1)))trim--;out.add(new Part(out.size()+1,at,trim,source));at=Math.max(at+1,end);}return out;}
 static String numbered(String source){StringBuilder out=new StringBuilder();for(Part part:parts(source))out.append("[T").append(part.number).append("]\n").append(part.text).append("\n\n");return out.toString().trim();}
 static List<Integer> valid(String answer,String source){int count=parts(source).size();LinkedHashSet<Integer> numbers=new LinkedHashSet<>();Matcher m=CITATION.matcher(answer);while(m.find()){int n=Integer.parseInt(m.group(1));if(n<=count)numbers.add(n);}return new ArrayList<>(numbers);}
 private StudyEvidence(){}
}
