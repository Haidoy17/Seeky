package mz.bam.seeky.android;
import java.util.*;import java.text.BreakIterator;
/** Page coordinates and text offsets; independent from display zoom and bitmap resolution. */
final class PdfGlyphs {
 static final class Glyph {final int start,end;final float left,top,right,bottom;Glyph(int start,int end,float x,float y,float w,float h){this.start=start;this.end=end;left=x;top=y;right=x+w;bottom=y+h;}}
 final String text;final float width,height;final List<Glyph> glyphs;
 PdfGlyphs(String text,float width,float height,List<Glyph> glyphs){this.text=text;this.width=width;this.height=height;this.glyphs=glyphs;}
 int at(float x,float y){Glyph best=null;float score=Float.MAX_VALUE;for(Glyph g:glyphs){float dx=Math.max(Math.max(g.left-x,0),x-g.right),dy=Math.max(Math.max(g.top-y,0),y-g.bottom);float d=dx*dx+dy*dy;if(d<score){best=g;score=d;}}return best!=null&&score<1600?best.start:-1;}
 int[] word(int offset){offset=Math.max(0,Math.min(Math.max(0,text.length()-1),offset));if(text.isEmpty())return new int[]{0,0};BreakIterator words=BreakIterator.getWordInstance(Locale.getDefault());words.setText(text);int start=words.preceding(offset+1),end=words.following(offset);return new int[]{Math.max(0,start),end<0?text.length():end};}
}
