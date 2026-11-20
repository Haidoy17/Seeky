package mz.bam.seeky.android;

import java.util.*;

/** 5.28: citations as Portuguese study books and OCR text write them. */
public final class BibleReferences528Test {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static BibleReferences.Reference one(String text){List<BibleReferences.Reference> refs=BibleReferences.references(text);check(refs.size()==1,"one reference in: "+text+" -> "+refs.size());return refs.get(0);}
    static String ranges(BibleReferences.Reference r){StringBuilder out=new StringBuilder();for(int[] x:r.ranges){if(out.length()>0)out.append(' ');out.append(x[0]).append(':').append(x[1]).append('-').append(x[2]).append(':').append(x[3]);}return out.toString();}
    static void none(String text){check(BibleReferences.references(text).isEmpty(),"no reference in: "+text+" -> "+(BibleReferences.references(text).isEmpty()?"":BibleReferences.references(text).get(0).title));}
    static String voice(String text){return SpeechText.forVoice(text,"pt").text;}
    public static void main(String[] args){
        String text="Leia Isaías 3.1-7, 12 antes do culto.";
        BibleReferences.Reference r=one(text);
        check(text.substring(r.start,r.end).equals("Isaías 3.1-7, 12"),"dotted chapter.verse with range and comma list: "+r.label);
        check(r.book.equals("Isaías")&&r.title.equals("Isaías 3:1-7, 12"),"normalised title: "+r.title);
        check(ranges(r).equals("3:1-3:7 3:12-3:12"),"verse 1 to 7 and verse 12: "+ranges(r));
        check(voice("Isaías 3.1-7, 12.").equals("Isaías capítulo 3, versículos 1 a 7 e 12."),"spoken dotted citation: "+voice("Isaías 3.1-7, 12."));

        r=one("Ver Jo 3,16-18.20; 4,1.");
        check(r.label.equals("Jo 3,16-18.20; 4,1")&&ranges(r).equals("3:16-3:18 3:20-3:20 4:1-4:1"),"Catholic comma style: "+r.label+" "+ranges(r));
        check(one("Mt 5:3-12; 6:1").title.equals("Mateus 5:3-12; 6:1"),"colon style keeps working");
        check(ranges(one("João 3:16–4:2")).equals("3:16-4:2"),"range across chapters");
        check(ranges(one("Is 3.1-4.2")).equals("3:1-4:2"),"dotted range across chapters");
        check(ranges(one("Is 3.1-7, 12.3")).equals("3:1-3:7 12:3-12:3"),"comma then chapter.verse starts a new chapter");
        check(ranges(one("1 Co 13.4-7")).equals("13:4-13:7")&&one("1 Co 13.4-7").book.equals("Primeira Coríntios"),"numbered epistle with dots");
        check(ranges(one("2Tm 3.16s")).equals("3:16-3:999"),"and following");
        check(ranges(one("Gn 1.1a")).equals("1:1-1:1"),"half verse");
        check(one("Isaías3.1").book.equals("Isaías"),"OCR without the space after a full name");
        check(one("Isaías\n3.1-7").title.equals("Isaías 3:1-7"),"PDF line break between book and chapter");

        r=one("O capítulo Salmo 23 consola.");
        check(!r.explicit&&r.book.equals("Salmos")&&ranges(r).equals("23:1-23:999"),"whole chapter: "+ranges(r));
        check(ranges(one("Leia Isaías 53-55.")).equals("53:1-55:999"),"chapter range");
        check(ranges(one("Salmo 23: o bom pastor")).equals("23:1-23:999"),"heading with colon");
        check(ranges(one("Judas 3.")).equals("1:3-1:3"),"single-chapter book takes a verse");
        check(voice("Salmo 23.").equals("Salmo 23."),"a lone chapter is not rewritten for speech");
        check(ranges(one("Is 3.1-7; 5.")).equals("3:1-3:7 5:1-5:999"),"semicolon then a whole chapter");

        none("O João 3 vezes ligou.");
        none("os atos 2 e 3 de bondade.");
        none("Tinha Números 3 anos de idade.");
        none("Os 3,5 milhões de pessoas.");
        none("os 3.5 kg e na 2.1 secção.");
        none("Na 2,3 g de sódio.");
        none("Versão Ne 2.3.1 do programa.");
        none("Isaías 67.1 não existe.");
        none("Jonas 2.90 não existe.");
        none("Is 53 sozinho é curto.");
        none("Taxa de 3.5% em Mt 3.5% ao ano.");
        check(one("Os 3,5 e 7.").book.equals("Oseias"),"ambiguous abbreviation followed by a connector still links");
        check(one("(Os 3.5)").book.equals("Oseias"),"ambiguous abbreviation in brackets still links");

        List<BibleReferences.Reference> many=BibleReferences.references("Is 3.1; Jr 2.4-6 e Rm 8,28.");
        check(many.size()==3&&many.get(1).title.equals("Jeremias 2:4-6")&&many.get(2).title.equals("Romanos 8:28"),"several citations in one sentence");

        check(BibleReferences.lookup("is 53").title.equals("Isaías 53"),"typed lookup accepts lower case and a short chapter");
        check(BibleReferences.lookup("isaias 3.1-7, 12").title.equals("Isaías 3:1-7, 12"),"typed lookup with dots");
        check(BibleReferences.lookup("sem passagem")==null,"no lookup");
        check(BibleReferences.atStart("Gênesis 1.1 No princípio").size()==1,"imported dotted verse lines");
        check(BibleReferences.atStart("Salmos 23: O Senhor").isEmpty(),"import ignores headings without verses");

        r=one("Isaías 3.1-7, 12");
        check(r.contains(3,5)&&r.contains(3,12)&&!r.contains(3,9)&&!r.contains(4,1),"verse membership for highlighting");

        String html="<p>Leia <b>Isaías 3.1-7, 12</b> e Jo 3,16.</p>";
        String linked=BibleHtml.link(html);
        check(linked.contains("seekybible://passage?ref=Isa%C3%ADas+3.1-7%2C+12")&&linked.contains("Jo 3,16</a>"),"EPUB html links dotted and comma citations: "+linked);
        System.out.println("PASS "+checks+" Bible citation recognition checks (5.28)");
    }
}
