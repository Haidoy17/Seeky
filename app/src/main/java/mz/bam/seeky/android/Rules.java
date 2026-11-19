package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.Locale;

/** Pure rules shared by indexing and UI, verified by JVM tests. */
public final class Rules {
 private static final java.util.regex.Pattern MARKS=java.util.regex.Pattern.compile("\\p{M}+");
 public static String normalize(String s) { return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT); }
 /** Versão das regras de categoria: ao mudar, o catálogo existente é reclassificado uma vez. */
 public static final int CATEGORY_RULES=2;
 /** Cada ficheiro na sua categoria: o tipo real (extensão e MIME) decide, por esta ordem. */
 public static String category(String name, String mime) {
  String n=name.toLowerCase(Locale.ROOT), m=mime==null?"":mime.toLowerCase(Locale.ROOT);
  if(n.endsWith(".pdf")||m.equals("application/pdf"))return "PDF";
  if(n.matches(".*\\.(apk|apks|xapk|apkm)$")||m.equals("application/vnd.android.package-archive"))return "Aplicações";
  if(n.matches(".*\\.(zip|rar|7z|tar|gz|bz2|xz|tgz|tbz2|txz|zst|lz|lzma|cab|iso)$"))return "Arquivos";
  String kind=MediaFormats.kind(name,mime);
  if(kind.equals("image"))return "Imagens";
  if(kind.equals("video"))return "Vídeos";
  if(kind.equals("audio"))return "Áudio";
  if(kind.equals("epub")||kind.equals("docx")||n.matches(".*\\.(docx?|docm|dotx?|xlsx?|xlsm|xltx|pptx?|ppsx?|potx|txt|rtf|csv|tsv|md|markdown|json|xml|html?|odt|ods|odp|odg|epub|mobi|azw3?|fb2|djvu|usfm|usx|sfm|tex|pages|numbers|key|wps|wpd|hwp|srt|vtt)$"))return "Documentos";
  return "Outros";
 }
 public static String like(String input) { return "%"+normalize(input).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; }
 public static String likeQuery(String input) {String q=normalize(input);if(q.indexOf('*')<0&&q.indexOf('?')<0)return like(q);return q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_").replace("*","%").replace("?","_");}
 public static boolean nameMatches(String name,String query) {String n=normalize(name),q=normalize(query);if(q.indexOf('*')<0&&q.indexOf('?')<0)return n.contains(q);int at=0,pattern=0,star=-1,retry=0;while(at<n.length()){if(pattern<q.length()&&(q.charAt(pattern)=='?'||q.charAt(pattern)==n.charAt(at))){at++;pattern++;}else if(pattern<q.length()&&q.charAt(pattern)=='*'){star=pattern++;retry=at;}else if(star>=0){pattern=star+1;at=++retry;}else return false;}while(pattern<q.length()&&q.charAt(pattern)=='*')pattern++;return pattern==q.length();}
 public static boolean validName(String s) {if(s==null||s.trim().isEmpty()||s.length()>200||s.equals(".")||s.equals(".."))return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c<' '||c==127||c=='/'||c=='\\')return false;}return true;}
 public static String bytes(long n) {if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.ROOT,"%.1f KB",n/1024.0);if(n<1073741824)return String.format(Locale.ROOT,"%.1f MB",n/1048576.0);return String.format(Locale.ROOT,"%.2f GB",n/1073741824.0);}
 private Rules(){}
}
