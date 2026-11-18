package mz.bam.seeky.android;

import java.util.Locale;import java.util.regex.*;

/** Presentation-only EPUB adaptation. The archive, text, images and author styles remain intact. */
final class EpubLayout {
 static final String CSS_URL="https://seeky-document.invalid/reader/epub.css";
 static final class Options {
  final boolean fitWidth;final int margin,zoom,spacing,align;
  Options(boolean fitWidth,int margin,int zoom,int spacing,int align){this.fitWidth=fitWidth;this.margin=clamp(margin,8,40);this.zoom=clamp(zoom,75,175);this.spacing=spacing==0?0:clamp(spacing,100,220);this.align=clamp(align,0,2);}
 }
 static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 static String css(Options o){
  if(!o.fitWidth)return "";
  String blocks="body :where(article,section,main,header,footer,div,p,h1,h2,h3,h4,h5,h6,figure,figcaption,blockquote,ul,ol,li,dl,dt,dd)";
  StringBuilder css=new StringBuilder("html{box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;-webkit-text-size-adjust:100%;column-count:auto!important;column-width:auto!important;}body{box-sizing:border-box!important;width:auto!important;min-width:0!important;max-width:100%!important;margin:0!important;padding:16px ").append(o.margin).append("px 28px!important;column-count:auto!important;column-width:auto!important;}");
  css.append(blocks).append("{box-sizing:border-box!important;min-width:0!important;max-width:100%!important;overflow-wrap:anywhere!important;column-count:auto!important;column-width:auto!important;}");
  css.append("body :where(p,h1,h2,h3,h4,h5,h6,blockquote,figure){margin-left:0!important;margin-right:0!important;white-space:normal!important;}body :where(img,svg,video,canvas){box-sizing:border-box!important;max-width:100%!important;height:auto!important;}body pre{box-sizing:border-box!important;max-width:100%!important;white-space:pre-wrap!important;overflow-wrap:anywhere!important;}body table{box-sizing:border-box!important;width:100%!important;min-width:0!important;max-width:100%!important;table-layout:fixed!important;}body :where(td,th){min-width:0!important;overflow-wrap:anywhere!important;white-space:normal!important;}");
  if(o.spacing>0)css.append("body :where(p,li,blockquote,td,th){line-height:").append(o.spacing/100.0).append("!important;}");
  if(o.align>0)css.append("body :where(p,li,blockquote){text-align:").append(o.align==1?"left":"justify").append("!important;}");
  return css.toString();
 }
 static String html(String source,Options o){
  // A book viewport following ours could restore a desktop width. Keep only the reader viewport in fit mode.
  if(o.fitWidth)source=source.replaceAll("(?is)<meta\\b(?=[^>]*\\bname\\s*=\\s*(?:\"viewport\"|'viewport'|viewport\\b))[^>]*>","");
  String security="<meta charset=\"UTF-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self' data:; script-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'\">"+(o.fitWidth?"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">":"");
  Matcher head=Pattern.compile("(?is)<head(?:\\s[^>]*)?>").matcher(source);
  if(head.find()){int end=head.end();source=source.substring(0,end)+security+source.substring(end);}
  else{Matcher body=Pattern.compile("(?is)<body(?:\\s[^>]*)?>").matcher(source);int at=body.find()?body.start():-1;if(at>=0)source=source.substring(0,at)+"<head>"+security+"</head>"+source.substring(at);else{Matcher root=Pattern.compile("(?is)<html(?:\\s[^>]*)?>").matcher(source);if(root.find()){int end=root.end();source=source.substring(0,end)+"<head>"+security+"</head>"+source.substring(end);}else source="<html><head>"+security+"</head><body>"+source+"</body></html>";}}
  String link="<link rel=\"stylesheet\" href=\""+CSS_URL+"\">";int close=source.toLowerCase(Locale.ROOT).lastIndexOf("</head>");return close>=0?source.substring(0,close)+link+source.substring(close):source+link;
 }
 private EpubLayout(){}
}
