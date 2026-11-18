package mz.bam.seeky.android;
import java.util.*;import java.util.regex.*;
/** Literal whole-word substitutions with stable offsets; no regular expressions from users. */
final class PronunciationRules {
 static final class Rule {final String word,say;final Pattern match;Rule(String word,String say){this.word=word.trim();this.say=say.trim();if(this.word.isEmpty()||this.word.length()>80||this.say.isEmpty()||this.say.length()>120||this.word.indexOf('\n')>=0||this.say.indexOf('\n')>=0)throw new IllegalArgumentException("Use uma palavra até 80 caracteres e pronúncia até 120.");match=Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(this.word)+"(?![\\p{L}\\p{N}_])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);}}
 static SpeechText.Prepared apply(SpeechText.Prepared in,List<Rule> rules){
  if(rules.isEmpty())return in;List<int[]> spans=new ArrayList<>();List<String> replacements=new ArrayList<>();
  for(Rule rule:rules){Matcher m=rule.match.matcher(in.text);while(m.find()){boolean overlaps=false;for(int[] span:spans)if(m.start()<span[1]&&m.end()>span[0]){overlaps=true;break;}if(!overlaps){spans.add(new int[]{m.start(),m.end(),replacements.size()});replacements.add(rule.say);}}}
  if(spans.isEmpty())return in;spans.sort((a,b)->Integer.compare(a[0],b[0]));StringBuilder out=new StringBuilder();int extra=0;for(int[] span:spans)extra+=replacements.get(span[2]).length()-(span[1]-span[0]);int[] map=new int[Math.max(0,in.text.length()+extra)];int at=0,n=0;
  for(int[] span:spans){out.append(in.text,at,span[0]);while(at<span[0])map[n++]=in.sourceOffset(at++);String value=replacements.get(span[2]);out.append(value);int anchor=in.sourceOffset(span[0]);for(int k=0;k<value.length();k++)map[n++]=anchor;at=span[1];}out.append(in.text,at,in.text.length());while(at<in.text.length())map[n++]=in.sourceOffset(at++);return new SpeechText.Prepared(out.toString(),map,in.sourceLength);
 }
 private PronunciationRules(){}
}
