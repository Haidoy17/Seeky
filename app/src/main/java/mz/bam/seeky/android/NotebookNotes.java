package mz.bam.seeky.android;

import android.database.Cursor;
import java.io.*;
import java.util.*;

final class NotebookNotes {
    static final class Item {
        final int part,start,end;final String digest,kind,note,excerpt;
        Item(int part,String digest,StudyText.Mark mark,String text){this.part=part;this.digest=digest;start=mark.start;end=mark.end;kind=mark.kind;note=mark.note;excerpt=text.substring(start,Math.min(end,start+200));}
        String label(){return kind.equals("note")?"Nota":kind.equals("underline")?"Sublinhado":"Destaque";}
    }
    static List<Item> list(ReaderStore store,String uri,String kind,int limit){
        List<Item> items=new ArrayList<>();
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT segment,digest,value,marks FROM draft WHERE uri=? AND marks!='[]' ORDER BY segment,digest LIMIT 1000",new String[]{uri})){
            while(c.moveToNext()){
                StudyText text=ReaderStore.decode(c.getString(2),c.getString(3));
                for(StudyText.Mark mark:text.marks)if(kind.isEmpty()||kind.equals(mark.kind)){items.add(new Item(c.getInt(0),c.getString(1),mark,text.text));if(items.size()>=limit)return items;}
            }
        }return items;
    }
    static int export(ReaderStore store,String uri,String title,Writer out,boolean rich)throws IOException {
        int total=0;long chars=0;
        out.write(rich?"<!doctype html><html><meta charset='utf-8'><title>Anotações</title><body><h1>"+StudyText.escape(title)+"</h1>":title+"\nANOTAÇÕES GUARDADAS\n");
        out.write(rich?"<p>Inclui as versões de texto guardadas. O original não é alterado.</p>":"Inclui versões de texto guardadas. O original não é alterado.\n\n");
        try(Cursor c=store.getReadableDatabase().rawQuery("SELECT segment,digest,value,marks FROM draft WHERE uri=? AND marks!='[]' ORDER BY segment,digest",new String[]{uri})){
            while(c.moveToNext()){
                StudyText text=ReaderStore.decode(c.getString(2),c.getString(3));
                for(StudyText.Mark mark:text.marks){if(++total>20000)throw new IOException("Mais de 20000 anotações; exportação cancelada antes de escolher o destino.");
                    String label=mark.kind.equals("note")?"Nota":mark.kind.equals("underline")?"Sublinhado":"Destaque",selected=text.text.substring(mark.start,mark.end);
                    String entry=rich?"<h2>Parte "+(c.getInt(0)+1)+" · "+label+"</h2><p>Extracção: "+c.getString(1)+"</p><blockquote>"+StudyText.escape(selected).replace("\n","<br>")+"</blockquote><p>"+StudyText.escape(mark.note).replace("\n","<br>")+"</p>":"Parte "+(c.getInt(0)+1)+" · "+label+"\nExtracção: "+c.getString(1)+"\n"+selected+"\n"+mark.note+"\n\n";
                    chars+=entry.length();if(chars>4194304)throw new IOException("Anotações acima de 4 milhões de caracteres; a exportação não foi concluída.");out.write(entry);
                }
            }
        }if(rich)out.write("</body></html>");out.flush();return total;
    }
    private NotebookNotes(){}
}
