package mz.bam.seeky.android;
/** Format-specific fallback symbols and preview policy, independent of Android UI. */
final class FileVisual {
 static String symbol(String name,String mime,boolean directory){if(directory)return "folder";String ext=MediaFormats.ext(name);if(ext.equals("apk"))return "android";if(ext.equals("pdf")||"application/pdf".equals(mime))return "pdf";if(ext.equals("doc")||ext.equals("docx")||"application/msword".equals(mime)||"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime))return "word";if(ext.equals("xls")||ext.equals("xlsx"))return "excel";if(ext.equals("ppt")||ext.equals("pptx"))return "slides";if(ext.equals("epub"))return "book";String kind=MediaFormats.kind(name,mime);return kind.equals("image")?"image":kind.equals("video")?"video":kind.equals("audio")?"audio":"document";}
 static boolean preview(String name,String mime){String kind=MediaFormats.kind(name,mime);return kind.equals("image")||kind.equals("pdf")||kind.equals("epub")||kind.equals("docx");}
 static boolean showPreview(int mode,int size){return mode==2||mode==3||mode==4||mode==5||size==2;}
}
