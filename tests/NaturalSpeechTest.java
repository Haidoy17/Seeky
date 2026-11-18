package mz.bam.seeky.android;

public final class NaturalSpeechTest {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args){
        String source="Uma frase repartida\nem duas linhas.\n\nOutro parágrafo.";
        SpeechText.Prepared prepared=SpeechText.prepare(source);
        check(prepared.text.equals("Uma frase repartida em duas linhas.\n\nOutro parágrafo."),"PDF line wrapping no longer creates pauses inside a sentence");
        check(prepared.sourceOffset(prepared.text.indexOf("Outro"))==source.indexOf("Outro"),"engine word positions still map to original paragraph offsets");
        String soft="desenvolvi\u00ad\nmento";check(SpeechText.prepare(soft).text.equals("desenvolvimento"),"discretionary line-end hyphens do not break a spoken word");
        check(SpeechText.prepare("anti-\ninflamatório").text.equals("anti-inflamatório"),"ordinary meaningful hyphens are retained");
        check(SpeechText.prepare("  \tOlá\r\n mundo\n  ").text.equals("Olá mundo"),"CRLF, indentation and trailing layout whitespace are normalised");
        check(SpeechText.prepare("Dose: -5,5 mg.\nPreço: 1.234,56.").text.equals("Dose: -5,5 mg. Preço: 1.234,56."),"normalisation preserves numbers, signs and decimal punctuation");
        check(SpeechText.prepare("A\fB\fC\fD").text.equals("A\n\nB\n\nC\n\nD"),"multiple page separators cannot overflow the source map");
        String emoji="Um 😀\ncontinua.";prepared=SpeechText.prepare(emoji);check(prepared.text.contains("😀")&&prepared.sourceOffset(prepared.text.indexOf("continua"))==emoji.indexOf("continua"),"emoji UTF16 units retain exact source offsets");
        check(prepared.sourceOffset(999)==emoji.length()&&prepared.sourceOffset(-1)==0,"engine offsets outside the text remain bounded");
        check(SpeechText.prepare("\n\t\u00a0").text.isEmpty(),"layout-only text never becomes a spoken request");
        String wrapped="First line\ncontinuation without punctuation here. A following sentence is deliberately longer.";
        check(SpeechChunks.naturalEnd(wrapped,0,60)==49,"chunk boundaries prefer complete sentences over single layout newlines");
        SpeechCursor cursor=new SpeechCursor();cursor.load(source,0);SpeechCursor.Chunk chunk=cursor.next(2200);prepared=SpeechText.prepare(chunk.text);int word=prepared.text.indexOf("Outro");cursor.range(chunk.id,prepared.sourceOffset(word));cursor.pause();check(cursor.offset==source.indexOf("Outro"),"pausing normalised speech retains the original document position");
        check(SpeechPolicy.chunkLimit(1)==2200&&SpeechPolicy.chunkLimit(.5f)==1100,"speech uses longer sentence context with smaller requests at slow rates");
        check(SpeechPolicy.timeout(2200,1)>120000&&SpeechPolicy.timeout(999999,.5f)<=360000,"natural voice requests are not cut off by the old one-minute timeout");
        String respectful="Uma explicação detalhada sobre o tema com o Prof. João continua no texto seguinte.";int after=respectful.indexOf(" João")+1;check(SpeechChunks.naturalEnd(respectful,0,after)!=after,"chunking does not separate an abbreviation from its following name");
        check(!SpeechChunks.sentenceStop("Dr. João",2)&&!SpeechChunks.sentenceStop("A. Silva",1)&&SpeechChunks.sentenceStop("Fim. Segue",3),"titles and initials do not become artificial sentence pauses");
        check(SpeechPolicy.startupLimit(1,true)<SpeechPolicy.chunkLimit(1,true)&&SpeechPolicy.startupLimit(1,false)==SpeechPolicy.chunkLimit(1,false),"online playback starts with a shorter request while offline keeps normal context");
        System.out.println("PASS "+checks+" natural speech and source mapping checks");
    }
}
