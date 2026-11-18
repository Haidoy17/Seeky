package mz.bam.seeky.android;

/** Sentence-sized TTS requests with stable UTF-16 offsets and no lost characters. */
final class SpeechChunks {
    static int naturalEnd(String text,int start,int limit){
        return BibleReferences.safeEnd(text,start,naturalBoundary(text,start,limit),limit);
    }
    static int naturalBoundary(String text,int start,int limit){
        if(start<0||start>text.length()||limit<2)throw new IllegalArgumentException();
        int hard=Math.min(text.length(),start+limit);
        if(hard==text.length())return hard;
        if(Character.isHighSurrogate(text.charAt(hard-1))&&Character.isLowSurrogate(text.charAt(hard)))hard--;
        int minimum=start+Math.max(1,(hard-start)/3);
        for(int i=hard-1;i>=minimum;i--){char c=text.charAt(i);if((sentenceStop(text,i))&&i+1<text.length()&&Character.isWhitespace(text.charAt(i+1)))return i+1;if(c=='\n'&&i>start&&text.charAt(i-1)=='\n')return i+1;}
        for(int i=hard-1;i>=minimum;i--)if(Character.isWhitespace(text.charAt(i))){int prior=i-1;while(prior>=start&&Character.isWhitespace(text.charAt(prior)))prior--;if(prior>=start&&text.charAt(prior)=='.'&&!sentenceStop(text,prior))continue;return i+1;}
        return hard;
    }
    static boolean sentenceStop(String text,int at){char c=text.charAt(at);if(c=='!'||c=='?'||c==';')return true;if(c!='.')return false;int start=at;while(start>0&&(Character.isLetter(text.charAt(start-1))||text.charAt(start-1)=='.'))start--;String word=text.substring(start,at).toLowerCase(java.util.Locale.ROOT);if(word.length()==1&&Character.isLetter(word.charAt(0)))return false;return !java.util.Arrays.asList("sr","sra","srta","dr","dra","prof","profa","eng","av","vol","cap","pág","pag","pp","ex","etc","mr","mrs","ms","st","jr","vs","e.g","i.e").contains(word);}
    static int end(String text, int start, int limit) {
        if (start < 0 || start > text.length() || limit < 2) throw new IllegalArgumentException();
        int end = Math.min(text.length(), start + limit);
        if (end == text.length()) return end;
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        int minimum = start + Math.max(1, (end - start) / 3);
        for (int i = end - 1; i >= minimum; i--) {
            char c = text.charAt(i);
            if (c == '\n' || ((c == '.' || c == '!' || c == '?' || c == ';') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1)))) return i + 1;
        }
        for (int i = end - 1; i >= minimum; i--) if (Character.isWhitespace(text.charAt(i))) return i + 1;
        return end;
    }
    private SpeechChunks() {}
}
