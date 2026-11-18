package mz.bam.seeky.android;
import java.io.*;import java.util.*;
public class CoreStabilityTest {
 static int n;static void check(boolean b,String s){if(!b)throw new AssertionError(s);n++;}
 static boolean reference(String text,String pattern){boolean[][] m=new boolean[text.length()+1][pattern.length()+1];m[0][0]=true;for(int j=1;j<=pattern.length();j++)m[0][j]=m[0][j-1]&&pattern.charAt(j-1)=='*';for(int i=1;i<=text.length();i++)for(int j=1;j<=pattern.length();j++){char p=pattern.charAt(j-1);m[i][j]=p=='*'?(m[i-1][j]||m[i][j-1]):m[i-1][j-1]&&(p=='?'||p==text.charAt(i-1));}return m[text.length()][pattern.length()];}
 public static void main(String[] a)throws Exception {
  check(CloudChunks.read(new ByteArrayInputStream(new byte[0]),16).length==0,"empty chunk");
  byte[] data=new byte[37];new Random(15).nextBytes(data);InputStream in=new ByteArrayInputStream(data);check(Arrays.equals(Arrays.copyOfRange(data,0,16),CloudChunks.read(in,16)),"first exact chunk");check(Arrays.equals(Arrays.copyOfRange(data,16,32),CloudChunks.read(in,16)),"second exact chunk");check(Arrays.equals(Arrays.copyOfRange(data,32,37),CloudChunks.read(in,16)),"partial tail");check(CloudChunks.read(in,16).length==0,"EOF after tail");
  InputStream zero=new ByteArrayInputStream(data){boolean alternating;public int read(byte[] b,int off,int len){alternating=!alternating;return alternating?0:super.read(b,off,Math.min(2,len));}};check(Arrays.equals(Arrays.copyOfRange(data,0,16),CloudChunks.read(zero,16)),"short and zero reads make progress without losing bytes");
  Random random=new Random(3);for(int k=0;k<3000;k++){StringBuilder text=new StringBuilder(),pattern=new StringBuilder();for(int i=random.nextInt(25);i>0;i--)text.append("ab_[]".charAt(random.nextInt(5)));for(int i=random.nextInt(14);i>0;i--)pattern.append("ab*?[]_".charAt(random.nextInt(7)));if(pattern.indexOf("*")<0&&pattern.indexOf("?")<0)pattern.append('*');check(Rules.nameMatches(text.toString(),pattern.toString())==reference(text.toString(),pattern.toString()),"wildcard matches DP reference "+k);}
  StringBuilder longText=new StringBuilder(),pattern=new StringBuilder();for(int i=0;i<100000;i++)longText.append('a');for(int i=0;i<100;i++)pattern.append("*a");pattern.append('b');long start=System.nanoTime();check(!Rules.nameMatches(longText.toString(),pattern.toString()),"adversarial glob does not match");check(System.nanoTime()-start<5000000000L,"adversarial glob completes without regex backtracking explosion");
  System.out.println("PASS "+n+" block-boundary and wildcard regression checks");
 }
}