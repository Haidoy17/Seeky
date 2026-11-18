package mz.bam.seeky.android;
/** Safe FTS4 prefix expression; input is always a bound SQL value. */
final class FlashQuery {
 static String expression(String value){StringBuilder b=new StringBuilder();int count=0;for(String t:Rules.normalize(value).split("[^\\p{L}\\p{N}]+")){if(t.isEmpty())continue;if(++count>12)break;if(b.length()>0)b.append(" AND ");b.append('"').append(t).append("*\"");}return b.toString();}
}
