package mz.bam.seeky.android;
import java.util.*;
import java.text.*;

final class FileOrder {
 static final String[] NAMES={"Data de modificação","Nome","Tamanho","Tipo de ficheiro","Autor","Categoria","Marcas","Título","Data personalizada","Ordem personalizada"};
 static String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
 static String value(FileRecord e,int field){switch(field){case 1:return e.name;case 3:return e.mime;case 4:return e.author;case 5:return e.displayCategory();case 6:return e.tags;case 7:return e.displayTitle();default:return "";}}
 static long number(FileRecord e,int field){return field==2?e.size:field==8?e.customDate:field==9?e.manualOrder:e.modified;}
 static boolean numeric(int field){return field==0||field==2||field==8||field==9;}
 static Comparator<FileRecord> by(int field,boolean descending){return (a,b)->{
  if(a.directory!=b.directory)return a.directory?-1:1;
  boolean numeric=numeric(field);long x=number(a,field),y=number(b,field);String sa=value(a,field),sb=value(b,field);
  boolean missingA=numeric?((field==8||field==9)?x==0:x<0):sa.isEmpty(),missingB=numeric?((field==8||field==9)?y==0:y<0):sb.isEmpty();
  if(missingA!=missingB)return missingA?1:-1;
  int cmp=numeric?Long.compare(x,y):String.CASE_INSENSITIVE_ORDER.compare(sa,sb);if(cmp!=0)return descending?-cmp:cmp;
  return String.CASE_INSENSITIVE_ORDER.compare(a.name,b.name)!=0?String.CASE_INSENSITIVE_ORDER.compare(a.name,b.name):a.uri.compareTo(b.uri);
 };}
 static String metadata(String field){return "coalesce((SELECT "+field+" FROM metadata m WHERE m.uri=files.uri),"+(field.equals("custom_date")||field.equals("manual_order")?"0":"''")+")";}
 static String sql(int field,boolean descending){String expr;
  switch(field){case 1:expr="name COLLATE NOCASE";break;case 2:expr="size";break;case 3:expr="mime COLLATE NOCASE";break;case 4:expr=metadata("author")+" COLLATE NOCASE";break;case 5:expr="coalesce(nullif("+metadata("custom_category")+",''),category) COLLATE NOCASE";break;case 6:expr=metadata("tags")+" COLLATE NOCASE";break;case 7:expr="coalesce(nullif("+metadata("title")+",''),name) COLLATE NOCASE";break;case 8:expr=metadata("custom_date");break;case 9:expr=metadata("manual_order");break;default:expr="modified";}
  String missing=field==4||field==6?"CASE WHEN "+metadata(field==4?"author":"tags")+"='' THEN 1 ELSE 0 END,":field==8||field==9?"CASE WHEN "+metadata(field==8?"custom_date":"manual_order")+"=0 THEN 1 ELSE 0 END,":"";
  return missing+expr+(descending?" DESC":" ASC")+",name COLLATE NOCASE,uri";
 }
 static long date(String input)throws java.text.ParseException {if(input.trim().isEmpty())return 0;if(!input.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new ParseException("Use AAAA-MM-DD",0);SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);fmt.setLenient(false);return fmt.parse(input).getTime();}
 static String date(long value){return value==0?"":new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(value));}
 static boolean matches(FileRecord e,String category,String tag){return (category.isEmpty()||Rules.normalize(e.displayCategory()).contains(Rules.normalize(category)))&&(tag.isEmpty()||Rules.normalize(e.tags).contains(Rules.normalize(tag)));}
 private FileOrder(){}
}
