package mz.bam.seeky.android;

/** Single-threaded speech state. Late engine callbacks cannot advance a newer utterance. */
final class SpeechCursor {
    private static final java.util.concurrent.atomic.AtomicLong UTTERANCES=new java.util.concurrent.atomic.AtomicLong();
    static final class Chunk {
        final String id, text; final int start, end,pauseAfter;
        Chunk(String id, String text, int start, int end,int pauseAfter) { this.id=id;this.text=text;this.start=start;this.end=end;this.pauseAfter=pauseAfter; }
    }
    static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
    static boolean hasText(String value,int at){while(at<value.length()&&space(value.charAt(at)))at++;return at<value.length();}
    int titlePause=2000;int pauseAfter(int end){return layout.pauseAfter(end)>0?titlePause:0;}
    String text = "";SpeechLayout.Plan layout=SpeechLayout.of("");
    int offset, spoken, serial;
    boolean paused;
    Chunk pending,queued;
    void load(String value,int at){load(value,at,SpeechLayout.of(value));}
    void load(String value, int at,SpeechLayout.Plan plan) {
        layout=plan;
        serial++; pending=queued=null; text=value; offset=Math.max(0,Math.min(at,value.length())); spoken=offset;
        if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset)) && Character.isHighSurrogate(text.charAt(offset-1))) offset--;
        offset=BibleReferences.safeStart(text,offset);
        spoken=offset;
    }
    Chunk next(int limit) {
        if (paused || pending!=null || ended()) return null;
        int end=layout.end(offset,limit);
        pending=new Chunk(Long.toString(UTTERANCES.incrementAndGet()),text.substring(offset,end),offset,end,pauseAfter(end));
        spoken=offset; return pending;
    }
    Chunk trim(boolean ahead,int end){Chunk old=ahead?queued:pending;if(old==null||end<=old.start||end>old.end)throw new IllegalArgumentException();Chunk fitted=new Chunk(old.id,text.substring(old.start,end),old.start,end,pauseAfter(end));if(ahead)queued=fitted;else pending=fitted;return fitted;}
    boolean accepts(String id) { return pending!=null && pending.id.equals(id) && !paused; }
    void range(String id, int start) { if(accepts(id)) spoken=Math.max(pending.start,Math.min(pending.end,pending.start+Math.max(0,start))); }
    Chunk lookAhead(int limit){if(paused||pending==null||queued!=null||pending.end>=text.length())return null;int at=pending.end;while(at<text.length()&&space(text.charAt(at)))at++;if(at==text.length())return null;int end=layout.end(pending.end,limit);queued=new Chunk(Long.toString(UTTERANCES.incrementAndGet()),text.substring(pending.end,end),pending.end,end,pauseAfter(end));return queued;}
    boolean done(String id) { if(!accepts(id))return false;offset=pending.end;spoken=offset;pending=queued;queued=null;return true; }
    void pause() { paused=true;offset=pending==null?offset:spoken;pending=queued=null;serial++; }
    void resume() { paused=false; }
    boolean ended() { if(pending==null)while(offset<text.length()&&space(text.charAt(offset)))offset++;return offset>=text.length(); }
    void clear() { pause();text="";offset=spoken=0; }
}
