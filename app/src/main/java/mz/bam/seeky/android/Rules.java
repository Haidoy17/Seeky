package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.Locale;

/** Pure rules shared by indexing and UI, verified by JVM tests. */
public final class Rules {
 private static final java.util.regex.Pattern MARKS=java.util.regex.Pattern.compile("\\p{M}+");
 public static String normalize(String s) { return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT); }
 public static String category(String name, String mime) {
  String n=name.toLowerCase(Locale.ROOT);
  if(MediaFormats.kind(name,mime).equals("image"))return "Imagens";
  if(mime.startsWith("video/"))return "Vídeos";
  if(MediaFormats.kind(name,mime).equals("audio"))return "Áudio";
  if(n.endsWith(".pdf"))return "PDF";
  if(n.matches(".*\\.(zip|rar|7z|tar|gz|bz2|xz|tgz|tbz2|txz)$"))return "Arquivos";
  if(n.endsWith(".apk")||n.endsWith(".apks")||n.endsWith(".xapk"))return "Aplicações";
  if(n.matches(".*\\.(docx?|xlsx?|pptx?|txt|csv|md|json|xml|html?|odt|epub)$"))return "Documentos";
  return "Outros";
 }
 public static String like(String input) { return "%"+normalize(input).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
 public static String likeQuery(String input) {String q=normalize(input);if(q.indexOf('*')<0&&q.indexOf('?')<0)return like(q);return q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_").replace("*","%").replace("?","_");}
 public static boolean nameMatches(String name,String query) {String n=normalize(name),q=normalize(query);if(q.indexOf('*')<0&&q.indexOf('?')<0)return n.contains(q);int at=0,pattern=0,star=-1,retry=0;while(at<n.length()){if(pattern<q.length()&&(q.charAt(pattern)=='?'||q.charAt(pattern)==n.charAt(at))){at++;pattern++;}else if(pattern<q.length()&&q.charAt(pattern)=='*'){star=pattern++;retry=at;}else if(star>=0){pattern=star+1;at=++retry;}else return false;}while(pattern<q.length()&&q.charAt(pattern)=='*')pattern++;return pattern==q.length();}
 public static boolean validName(String s) {if(s==null||s.trim().isEmpty()||s.length()>200||s.equals(".")||s.equals(".."))return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c<' '||c==127||c=='/'||c=='\\')return false;}return true;}
 public static String bytes(long n) {if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.ROOT,"%.1f KB",n/1024.0);if(n<1073741824)return String.format(Locale.ROOT,"%.1f MB",n/1048576.0);return String.format(Locale.ROOT,"%.2f GB",n/1073741824.0);}
 private Rules(){}
}
