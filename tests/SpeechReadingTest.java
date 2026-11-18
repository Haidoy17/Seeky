package mz.bam.seeky.android;

import java.util.*;

public final class SpeechReadingTest {
    static int checks;
    static void check(boolean value,String title){if(!value)throw new AssertionError(title);checks++;System.out.println("PASS "+title);}
    static void chunks(String text,int limit){
        StringBuilder rebuilt=new StringBuilder();int at=0;
        while(at<text.length()) {int end=SpeechChunks.end(text,at,limit);if(end<=at||end-at>limit)throw new AssertionError("chunk bounds");if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))throw new AssertionError("split surrogate");rebuilt.append(text.substring(at,end));at=end;}
        if(!rebuilt.toString().equals(text))throw new AssertionError("lost text");
    }
    public static void main(String[] args){
        check(SpeechChunks.end("Primeira frase. Segunda frase muito longa.",0,24)==15,"speech prefers a complete sentence within the engine limit");
        check(SpeechChunks.end("Parágrafo um\nParágrafo dois extenso",0,24)==13,"speech breaks at paragraph boundaries");
        String emoji="ab\ud83d\ude42cd";check(SpeechChunks.end(emoji,0,3)==2,"speech never splits a surrogate pair at a hard boundary");
        chunks("Olá.\n\nThis is English! Português: acção, língua e coração. 😀",12);check(true,"mixed language punctuation and emoji are preserved");
        Random random=new Random(51);String[] parts={"a"," ","\n","!","ç","😀","漢","\t",".","e\u0301"};
        for(int n=0;n<400;n++){StringBuilder text=new StringBuilder();for(int k=0;k<n;k++)text.append(parts[random.nextInt(parts.length)]);chunks(text.toString(),2+random.nextInt(99));}
        check(true,"400 varied Unicode documents reconstruct exactly without oversized chunks");
        SpeechCursor cursor=new SpeechCursor();cursor.load("Uma leitura longa, com palavras para ouvir em sequência.",0);
        SpeechCursor.Chunk first=cursor.next(20);check(first!=null&&cursor.next(20)==null,"one utterance is in flight at a time");
        cursor.range(first.id,5);cursor.pause();check(cursor.offset==5&&cursor.next(20)==null,"pause saves the latest spoken position and prevents new speech");
        check(!cursor.done(first.id),"a late onDone after pause cannot skip unread text");
        cursor.resume();SpeechCursor.Chunk resumed=cursor.next(20);check(resumed.start==5&&!resumed.id.equals(first.id),"resume creates a fresh utterance at the saved position");
        cursor.range(first.id,18);check(cursor.spoken==5,"late range callbacks cannot move a resumed utterance");
        check(cursor.done(resumed.id)&&cursor.offset==resumed.end,"completion advances exactly to the end of the accepted chunk");
        check(!cursor.done(resumed.id),"duplicate completion cannot advance twice");
        SpeechCursor.Chunk old=cursor.next(20);cursor.load("Novo documento",0);check(!cursor.done(old.id),"changing documents invalidates callbacks from the previous text");
        SpeechCursor.Chunk next=cursor.next(100);check(next.text.equals("Novo documento"),"the replacement document supplies the actual speech text");
        cursor.done(next.id);check(cursor.ended()&&cursor.next(100)==null,"end of a part cannot enqueue an empty utterance");
        cursor.load("Texto",999);check(cursor.ended(),"saved offsets beyond updated text are clamped");
        cursor.load("ab😀cd",3);check(cursor.offset==2,"resuming inside a surrogate pair backs up to the whole character");
        cursor.load("  \n  Texto",0);check(cursor.next(100).text.equals("Texto"),"blank prefixes never become empty engine requests");
        cursor.load(" \n \t",0);check(cursor.ended()&&cursor.next(100)==null,"trailing whitespace completes without waiting for a speech callback");
        cursor.load("Primeira frase completa. Segunda frase completa. Terceira frase completa.",0);SpeechCursor.Chunk playing=cursor.next(28),queued=cursor.lookAhead(28);check(queued!=null&&queued.start==playing.end&&cursor.offset==0,"look-ahead prepares exactly the next text without marking it read");check(cursor.lookAhead(28)==null,"look-ahead holds only one additional request");check(cursor.done(playing.id)&&cursor.pending==queued&&cursor.offset==queued.start,"completed utterance promotes queued text without a second start request");cursor.range(queued.id,3);cursor.pause();check(cursor.queued==null&&cursor.pending==null&&cursor.offset==queued.start+3,"pause cancels queued speech and keeps the actual spoken position");check(!cursor.done(playing.id)&&!cursor.done(queued.id),"late callbacks cannot complete cancelled queued speech");
        cursor.clear();check(cursor.text.isEmpty()&&cursor.paused,"stop releases text and leaves the cursor paused");
        check(SpeechPolicy.accepts(false,"pt",false,"pt",false),"offline Portuguese accepts an installed local Portuguese voice");
        check(!SpeechPolicy.accepts(false,"pt",true,"pt",false),"offline rejects every network voice");
        check(!SpeechPolicy.accepts(true,"pt",false,"pt",false),"online does not silently substitute a local voice");
        check(!SpeechPolicy.accepts(false,"pt",false,"en",false),"voice selection cannot silently change language");
        check(!SpeechPolicy.accepts(false,"en",false,"en",true),"offline rejects voice data that is not installed");
        check(SpeechPolicy.accepts(true,"en",true,"EN",false),"online English matches language case independently");
        check(SpeechPolicy.rate(-100)==.5f&&SpeechPolicy.rate(500)==2f,"speech rate stays within supported limits");
        for(int theme=0;theme<4;theme++){float[] matrix=ReaderAppearance.pdfMatrix(theme);int paper=ReaderAppearance.paper(theme),ink=ReaderAppearance.ink(theme);for(int channel=0;channel<3;channel++){int shift=16-channel*8;float black=matrix[channel*5+4],white=matrix[channel*5+channel]*255+black;if(Math.abs(black-((ink>>shift)&255))>.01||Math.abs(white-((paper>>shift)&255))>.01)throw new AssertionError("theme mapping");}}
        check(true,"all four PDF themes map black and white to readable ink and paper");
        check(ReaderAppearance.theme(-20)==0&&ReaderAppearance.theme(99)==3,"invalid saved theme values are clamped");
        check(ReaderAppearance.percent(20,35)==60&&ReaderAppearance.percent(0,0)==0&&ReaderAppearance.percent(99,20)==100,"page progress matches the supplied reader example and handles boundaries");
        System.out.println("PASS "+checks+" speech and reader policy checks");
    }
}
