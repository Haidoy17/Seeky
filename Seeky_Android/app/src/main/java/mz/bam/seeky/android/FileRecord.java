package mz.bam.seeky.android;

/** Common catalogue/presentation record. Personal metadata never modifies file bytes. */
class FileRecord {
 String uri,root,name,path,mime,category,body;
 String title="",author="",tags="",customCategory="",snippet="";
 long size,modified,customDate,manualOrder;
 boolean favorite,directory;
 String displayTitle(){return title.isEmpty()?name:title;}
 String displayCategory(){return customCategory.isEmpty()?category:customCategory;}
}
