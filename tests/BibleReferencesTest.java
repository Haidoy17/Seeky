package mz.bam.seeky.android;

public final class BibleReferencesTest {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static String voice(String text){return SpeechText.forVoice(text,"pt").text;}
    public static void main(String[] args){
        check(voice("Isaías 5:6").equals("Isaías capítulo 5, versículo 6"),"Isaiah is a chapter and verse, not a time");
        String[] books={"Gênesis","Êxodo","Levítico","Números","Deuteronômio","Josué","Juízes","Rute","1 Samuel","2 Samuel","1 Reis","2 Reis","1 Crônicas","2 Crônicas","Esdras","Neemias","Tobias","Judite","Ester","1 Macabeus","2 Macabeus","Jó","Salmos","Provérbios","Eclesiastes","Cântico dos Cânticos","Sabedoria","Eclesiástico","Isaías","Jeremias","Lamentações","Baruque","Ezequiel","Daniel","Oseias","Joel","Amós","Obadias","Jonas","Miqueias","Naum","Habacuque","Sofonias","Ageu","Zacarias","Malaquias","Mateus","Marcos","Lucas","João","Atos","Romanos","1 Coríntios","2 Coríntios","Gálatas","Efésios","Filipenses","Colossenses","1 Tessalonicenses","2 Tessalonicenses","1 Timóteo","2 Timóteo","Tito","Filemom","Hebreus","Tiago","1 Pedro","2 Pedro","1 João","2 João","3 João","Judas","Apocalipse"};
        check(books.length==73,"independent complete 73-book fixture");
        for(String book:books)check(voice(book+" 1:2").contains("capítulo 1, versículo 2"),"book recognised: "+book);
        check(voice("(Is. 5:6). Jo 3:16; Jó 2:3.").equals("(Isaías capítulo 5, versículo 6). João capítulo 3, versículo 16; Jó capítulo 2, versículo 3."),"abbreviations, Job and John stay distinct; punctuation retained");
        check(voice("ISAIAS 5 : 6").equals("Isaías capítulo 5, versículo 6"),"case, absent accent and spaces around colon");
        check(voice("Is.5:6").equals("Isaías capítulo 5, versículo 6"),"compact dotted abbreviation");
        check(voice("II Timóteo 3:16").equals("Segunda Timóteo capítulo 3, versículo 16"),"Roman numbered epistle");
        check(voice("1ª Coríntios 13:4–7").equals("Primeira Coríntios capítulo 13, versículos 4 a 7"),"ordinal prefix and verse range");
        check(voice("Sl 119:105,107–109").equals("Salmos capítulo 119, versículos 105 e 107 a 109"),"three-digit chapter and verse list");
        check(voice("João 3:16–4:2; 5:6").equals("João capítulo 3, versículo 16 a capítulo 4, versículo 2; capítulo 5, versículo 6"),"ranges across chapters and continued citations");
        check(voice("Tg 1：5").equals("Tiago capítulo 1, versículo 5"),"fullwidth colon");
        check(voice("Jn 1:2").equals("Jonas capítulo 1, versículo 2"),"Portuguese Jn abbreviation means Jonah");
        check(SpeechText.forVoice("Jn 3:16","en").text.equals("John chapter 3, verse 16"),"English Jn abbreviation means John");
        check(voice("Isaías\u00a05:\u00a06").equals("Isaías capítulo 5, versículo 6"),"nonbreaking document spaces");
        check(SpeechText.forVoice("John 3:16 and 1 Corinthians 13:4-7.","en-US").text.equals("John chapter 3, verse 16 and First Corinthians chapter 13, verses 4 to 7."),"English voice uses chapter and verse");
        String untouched="Reunião às 5:06. Razão 5:6, dose 1:2. Modelo MicroIsaías 5:6, abcJo 3:16. João 5:16:30. Isaías 0:6. Isaías 5:0. Isaías 1000:6.";
        check(voice(untouched).equals(untouched),"ordinary times, ratios, identifiers and invalid numbers remain unchanged");
        String source="Antes 😀\nIsaías 5:6, depois segue o texto.";
        SpeechText.Prepared spoken=SpeechText.forVoice(source,"pt-PT");
        check(source.equals("Antes 😀\nIsaías 5:6, depois segue o texto."),"source remains original");
        check(SpeechText.prepare(source).text.equals("Antes 😀 Isaías 5:6, depois segue o texto."),"layout/hash preparation still uses original citation");
        check(spoken.sourceOffset(spoken.text.indexOf("versículo"))==source.indexOf("Isaías"),"pause inside expanded reference resumes at complete citation");
        check(spoken.sourceOffset(spoken.text.indexOf("depois"))==source.indexOf("depois"),"following words keep exact source offset");
        int previous=-1;boolean monotonic=true;for(int i=0;i<=spoken.text.length();i++){int at=spoken.sourceOffset(i);if(at<previous||at>source.length())monotonic=false;previous=at;}
        check(monotonic&&spoken.sourceOffset(999999)==source.length(),"UTF16 source map is monotonic and bounded");
        String many="";for(int i=0;i<300;i++)many+="Isaías 5:6; João 3:16. ";
        StringBuilder reconstructed=new StringBuilder();int at=0,count=0;boolean bounded=true,whole=true;
        while(at<many.length()){
            int end=SpeechChunks.naturalEnd(many,at,3400);if(end<=at)throw new AssertionError("No progress");
            String part=many.substring(at,end),out=voice(part);if(out.length()>3400)bounded=false;
            if(part.matches("(?s).*Isaías\\s*$")||part.matches("(?s).*João\\s*$")||part.startsWith("5:6")||part.startsWith("3:16"))whole=false;
            reconstructed.append(part);at=end;count++;
        }
        check(bounded&&count>1,"expanded citations stay within the engine request budget");
        check(whole&&reconstructed.toString().equals(many),"chunking does not lose source text or split a reference");
        String edge="Um texto antes de Isaías 5:6 e uma frase posterior.";
        int end=SpeechChunks.naturalEnd(edge,0,edge.indexOf(":6"));
        check(end<=edge.indexOf("Isaías"),"hard limit before verse moves entire reference to next utterance");
        SpeechCursor cursor=new SpeechCursor();cursor.load(source,0);SpeechCursor.Chunk chunk=cursor.next(2200);spoken=SpeechText.forVoice(chunk.text,"pt");cursor.range(chunk.id,spoken.sourceOffset(spoken.text.indexOf("versículo")));cursor.pause();
        check(cursor.offset==source.indexOf("Isaías")&&voice(source.substring(cursor.offset)).startsWith("Isaías capítulo 5, versículo 6"),"paused cursor can restart the complete spoken citation");
        cursor.load(source,source.indexOf("5:6")+2);check(cursor.offset==source.indexOf("Isaías"),"loaded positions and time skips inside a reference snap to its start");
        check(BibleReferences.safeStart(source,source.indexOf("depois"))==source.indexOf("depois"),"ordinary saved offsets remain unchanged");
        String shortText="Leia Isaías 5:6, depois confirme João 3:16.";check(SpeechLayout.of(shortText).end(0,1200)==shortText.length(),"short structured prose remains a single fluent utterance after expansion");
        System.out.println("PASS "+checks+" Bible citation pronunciation and source mapping checks");
    }
}
