package mz.bam.seeky.android;

/** Remove layout-only line breaks for speech, while mapping engine offsets to the source. */
final class SpeechText {
    static final class Prepared {
        final String text; final int[] offsets; final int sourceLength;
        Prepared(String text,int[] offsets,int length){this.text=text;this.offsets=offsets;sourceLength=length;}
        int sourceOffset(int index){return index>=text.length()?sourceLength:offsets[Math.max(0,index)];}
    }
    static Prepared prepare(String source){
        StringBuilder out=new StringBuilder();int[] map=new int[Math.max(1,source.length()*2)];int size=0;
        for(int at=0;at<source.length();){
            char c=source.charAt(at);
            if(c=='\u00ad'||c=='\u200b'||c=='\ufeff'){at++;continue;}
            if(Character.isWhitespace(c)||Character.isISOControl(c)||c=='\u00a0'){
                int start=at,lines=0;boolean paragraph=false;
                while(at<source.length()){
                    c=source.charAt(at);
                    if(!(Character.isWhitespace(c)||Character.isISOControl(c)||c=='\u00a0'))break;
                    if(c=='\r'){lines++;if(at+1<source.length()&&source.charAt(at+1)=='\n')at++;}
                    else if(c=='\n'||c=='\u2028')lines++;
                    else if(c=='\f'||c=='\u2029')paragraph=true;
                    at++;
                }
                if(out.length()==0||at==source.length())continue;
                if(paragraph||lines>=2){out.append('\n');map[size++]=start;out.append('\n');map[size++]=start;}
                else {
                    // A discretionary hyphen is not part of the word. Ordinary hyphens remain.
                    boolean soft=start>0&&source.charAt(start-1)=='\u00ad';
                    boolean hyphen=lines>0&&out.charAt(out.length()-1)=='-'&&Character.isLetter(source.charAt(at));
                    if(!soft&&!hyphen){out.append(' ');map[size++]=start;}
                }
                continue;
            }
            out.append(c);map[size++]=at++;
        }
        return new Prepared(out.toString(),java.util.Arrays.copyOf(map,size),source.length());
    }
    static Prepared forVoice(String source,String language){return forVoice(source,language,java.util.Collections.<PronunciationRules.Rule>emptyList());}
    /** Texto pronto para a voz: referências bíblicas por extenso e normalização de leitura;
     *  as palavras do dicionário pessoal ficam intactas para a regra do utilizador. */
    static Prepared forVoice(String source,String language,java.util.List<PronunciationRules.Rule> personal){return SpeechNormalizer.apply(BibleReferences.expand(prepare(source),language),language,personal);}
    private SpeechText(){}
}
