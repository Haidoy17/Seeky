package mz.bam.seeky.android;
import java.nio.charset.StandardCharsets;import java.util.*;

/** 5.46: the ".seekynotes" file shared by Seeky for Android and Seeky for Windows. */
public final class NotesTransferTest {
 static int checks;static void ok(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
 static void refused(byte[] data,String name){try{NotesTransfer.read(data);throw new AssertionError(name);}catch(AssertionError e){throw e;}catch(Exception e){checks++;System.out.println("PASS "+name+" ("+e.getMessage()+")");}}
 public static void main(String[] args)throws Exception{
  NotesTransfer.Item a=new NotesTransfer.Item();a.title="Sermão";a.body="## Fé\nLer João 3:16\n![Foto](note-img:img_1.png)\n[🎙 Áudio](note-audio:rec_2.m4a)";a.folder="Sermões";a.pinned=true;a.color=0xfff6d365;a.spacing=150;a.created=1000;a.updated=2000;a.source="O caminho da fé";
  NotesTransfer.Item b=new NotesTransfer.Item();b.title="";b.body="Só texto";b.created=3000;b.updated=3000;
  ok(NotesTransfer.media(a.body).equals(new LinkedHashSet<>(Arrays.asList("img_1.png","rec_2.m4a"))),"pictures and recordings used by a note are found");
  Map<String,byte[]> files=new HashMap<>();files.put("img_1.png",new byte[]{1,2,3});
  byte[] data=NotesTransfer.write(Arrays.asList(a,b),files::get,"android");
  String json=new String(data,StandardCharsets.UTF_8);
  ok(json.contains("\"format\":\"seeky-notes-1\"")&&json.contains("\"from\":\"android\""),"the file says what it is and where it came from");
  NotesTransfer.Parsed p=NotesTransfer.read(data);
  ok(p.notes.size()==2&&p.from.equals("android"),"both notes come back");
  NotesTransfer.Item r=p.notes.get(0);
  ok(r.title.equals("Sermão")&&r.body.equals(a.body)&&r.folder.equals("Sermões")&&r.pinned&&r.color==0xfff6d365&&r.spacing==150&&r.created==1000&&r.updated==2000&&r.source.equals("O caminho da fé"),"title, text, folder, pin, colour, spacing, dates and book survive");
  ok(Arrays.equals(p.media.get("img_1.png"),new byte[]{1,2,3})&&!p.media.containsKey("rec_2.m4a"),"the picture travels with the note; a missing file is simply left out");
  ok(NotesTransfer.key("Sermão",a.body).equals(NotesTransfer.key(" Sermão ",a.body+"\n"))&&!NotesTransfer.key("Sermão","outro").equals(NotesTransfer.key("Sermão",a.body)),"the same note is recognised so it is not imported twice");
  Set<String> taken=new HashSet<>(Arrays.asList("img_1.png","img_1_2.png"));
  String fresh=NotesTransfer.fresh("img_1.png",taken);
  ok(fresh.equals("img_1_3.png")&&NotesTransfer.rename(a.body,"img_1.png",fresh).contains("(note-img:img_1_3.png)"),"a picture with the same name but other content gets a new name in the note");
  refused("{\"format\":\"outra-coisa\",\"schema\":1,\"notes\":[]}".getBytes(StandardCharsets.UTF_8),"other JSON files are refused");
  refused("isto não é JSON".getBytes(StandardCharsets.UTF_8),"text that is not a Seeky file is refused");
  refused(new byte[0],"an empty file is refused");
  NotesTransfer.Parsed unsafe=NotesTransfer.read("{\"format\":\"seeky-notes-1\",\"schema\":1,\"notes\":[{\"title\":\"x\",\"body\":\"y\",\"created\":99999999999999,\"spacing\":9999}],\"media\":{\"../../evil.png\":\"AQID\",\".hidden\":\"AQID\",\"ok.png\":\"%%%\"}}".getBytes(StandardCharsets.UTF_8));
  ok(unsafe.media.isEmpty(),"file names that leave the notes folder, hidden names and broken data are ignored");
  ok(unsafe.notes.get(0).created<=System.currentTimeMillis()&&unsafe.notes.get(0).spacing==300,"dates in the future and impossible spacing are corrected");
  NotesTransfer.Parsed empty=NotesTransfer.read("{\"format\":\"seeky-notes-1\",\"schema\":1,\"notes\":[{\"title\":\" \",\"body\":\"\"}]}".getBytes(StandardCharsets.UTF_8));
  ok(empty.notes.isEmpty(),"empty notes are skipped");
  ok(NotesTransfer.fileName().endsWith(".seekynotes")&&NotesTransfer.fileName().startsWith("Notas do Seeky "),"the file is named after the date");
  System.out.println("NotesTransferTest "+checks+" checks");
 }
}
