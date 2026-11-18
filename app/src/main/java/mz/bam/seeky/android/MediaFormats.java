package mz.bam.seeky.android;
import java.util.*;
final class MediaFormats {
 static String ext(String name){return FileOrder.extension(name);}
 static String kind(String name,String mime){if(mime==null)mime="application/octet-stream";String e=ext(name);if(e.equals("pdf")||"application/pdf".equals(mime))return "pdf";if(e.equals("epub")||"application/epub+zip".equals(mime))return "epub";if(e.equals("docx")||"application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime))return "docx";
  if(mime.startsWith("video/")||Arrays.asList("mp4","mkv","webm","3gp","avi","mov","m4v","ts").contains(e))return "video";
  if(mime.startsWith("audio/")||Arrays.asList("mp3","m4a","m4b","mka","aac","flac","wav","wave","ogg","oga","opus","amr","3ga","aiff","aif","wma","ape","mid","midi").contains(e))return "audio";
  if(mime.startsWith("image/")||Arrays.asList("jpg","jpeg","png","gif","webp","bmp","heic","heif","avif","tif","tiff","svg","ico","psd","raw","dng","cr2","nef").contains(e))return "image";
  if(ContentText.supported(name))return "text";return "external";
 }
 static String mime(String name,String provided){String e=ext(name);switch(e){case "zip":return "application/zip";case "rar":return "application/vnd.rar";case "7z":return "application/x-7z-compressed";case "tar":return "application/x-tar";case "gz":case "tgz":return "application/gzip";case "bz2":case "tbz2":return "application/x-bzip2";case "xz":case "txz":return "application/x-xz";case "mp4":case "m4v":return "video/mp4";case "mkv":return "video/x-matroska";case "webm":return "video/webm";case "3gp":return "video/3gpp";case "epub":return "application/epub+zip";case "pdf":return "application/pdf";case "doc":return "application/msword";case "docx":return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";case "mp3":return "audio/mpeg";case "flac":return "audio/flac";case "wav":case "wave":return "audio/wav";case "ogg":case "oga":case "opus":return "audio/ogg";case "aac":return "audio/aac";case "m4a":case "m4b":return "audio/mp4";case "amr":return "audio/amr";case "aiff":case "aif":return "audio/aiff";case "wma":return "audio/x-ms-wma";case "tiff":case "tif":return "image/tiff";case "svg":return "image/svg+xml";case "heic":case "heif":return "image/heif";case "avif":return "image/avif";case "psd":return "image/vnd.adobe.photoshop";default:return provided==null?"application/octet-stream":provided;}}
 private MediaFormats(){}
}
