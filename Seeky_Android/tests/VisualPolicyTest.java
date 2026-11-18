package mz.bam.seeky.android;
public final class VisualPolicyTest {
 static int n;static void ok(boolean v,String label){if(!v)throw new AssertionError(label);n++;System.out.println("PASS "+label);}
 public static void main(String[] args){
  ok(FileVisual.symbol("report.PDF","application/pdf",false).equals("pdf"),"PDF has its own recognisable symbol");ok(FileVisual.symbol("notes.docx","application/octet-stream",false).equals("word"),"Word is distinguished from generic documents");ok(FileVisual.symbol("app.apk","application/octet-stream",false).equals("android"),"APK has Android fallback while its icon loads");ok(FileVisual.symbol("photo.jpg","image/jpeg",false).equals("image"),"images keep an image symbol without thumbnail");ok(FileVisual.symbol("anything.pdf","application/pdf",true).equals("folder"),"folders cannot be treated as document covers");
  ok(FileVisual.showPreview(3,0)&&FileVisual.showPreview(5,1)&&FileVisual.showPreview(0,2),"grid and large modes enable actual previews");ok(!FileVisual.showPreview(0,0),"small list mode avoids document cover decoding");ok(FileVisual.preview("book.epub","application/epub+zip")&&FileVisual.preview("book.docx","application/octet-stream"),"EPUB and DOCX support bounded cover previews");
  ok(ThemeColors.hero(0xff0369a1,true,0xfff4f6fa)==0xff0369a1,"storage card uses the selected Ocean accent");ok(ThemeColors.hero(0xffc24136,true,0xfff4f6fa)!=0xff0369a1,"storage gradient changes with Coral theme");ok(ThemeColors.hero(0xff66dab0,false,0xff0c1c18)!=0xff66dab0,"dark-theme card uses a readable darker accent");
  ok(FocusClock.remaining(90,120)==30,"focus countdown computes remaining time");ok(FocusClock.remaining(999,120)==0,"focus countdown never becomes negative");ok(FocusClock.seconds(true,900,120,100,10,100,10)==120,"paused corrupt accumulation stays within duration");
  System.out.println("PASS "+n+" format, cover policy, theme and timer checks");
 }
}
