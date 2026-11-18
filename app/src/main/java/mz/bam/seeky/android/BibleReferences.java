package mz.bam.seeky.android;

import java.util.*;
import java.util.regex.*;

/** Speech-only Bible citations. Source documents and durable narration offsets stay intact. */
final class BibleReferences {
    private static final class Book {
        final String pt,en;
        Book(String pt,String en){this.pt=pt;this.en=en;}
    }
    private static final Map<String,Book> BOOKS=new HashMap<>();
    private static final List<String> ALIASES=new ArrayList<>();
    private static final String SPACE="[\\s\\p{Zs}]*", GAP="[\\s\\p{Zs}]+";
    private static final String NUMBER="[1-9][0-9]{0,2}(?![0-9])";
    private static final String VERSE=NUMBER+"(?!"+SPACE+"[:：])";
    private static final String VERSES=VERSE+"(?:"+SPACE+"[-–—,]"+SPACE+VERSE+"){0,7}";
    private static final String PART=NUMBER+SPACE+"[:：]"+SPACE+VERSES;
    private static final Pattern REFERENCE, PARTS=Pattern.compile("("+NUMBER+")"+SPACE+"[:：]"+SPACE+"("+VERSES+")");
    static {
        add("Génesis","Genesis","Génesis|Gênesis|Genesis|Gn|Gen");
        add("Êxodo","Exodus","Êxodo|Éxodo|Exodo|Ex|Exod|Exodus");
        add("Levítico","Leviticus","Levítico|Levitico|Lv|Lev|Leviticus");
        add("Números","Numbers","Números|Numeros|Nm|Num|Numbers");
        add("Deuteronómio","Deuteronomy","Deuteronómio|Deuteronômio|Deuteronomio|Dt|Deut|Deuteronomy");
        add("Josué","Joshua","Josué|Josue|Js|Jos|Joshua");
        add("Juízes","Judges","Juízes|Juizes|Jz|Judg|Judges");
        add("Rute","Ruth","Rute|Rt|Ruth");
        numbered(1,"Primeiro Samuel","First Samuel","Samuel|Sm|Sam");
        numbered(2,"Segundo Samuel","Second Samuel","Samuel|Sm|Sam");
        numbered(1,"Primeiro Reis","First Kings","Reis|Rs|Re|Kings|Kgs");
        numbered(2,"Segundo Reis","Second Kings","Reis|Rs|Re|Kings|Kgs");
        numbered(1,"Primeiro Crónicas","First Chronicles","Crónicas|Crônicas|Cronicas|Cr|Cron|Chronicles|Chr");
        numbered(2,"Segundo Crónicas","Second Chronicles","Crónicas|Crônicas|Cronicas|Cr|Cron|Chronicles|Chr");
        add("Esdras","Ezra","Esdras|Esd|Ed|Ezra");
        add("Neemias","Nehemiah","Neemias|Ne|Neh|Nehemiah");
        add("Tobias","Tobit","Tobias|Tobit|Tb|Tob");
        add("Judite","Judith","Judite|Jdt|Judith");
        add("Ester","Esther","Ester|Est|Et|Esther");
        numbered(1,"Primeiro Macabeus","First Maccabees","Macabeus|Mac|Mc|Maccabees|Macc");
        numbered(2,"Segundo Macabeus","Second Maccabees","Macabeus|Mac|Mc|Maccabees|Macc");
        add("Jó","Job","Jó|Job");
        add("Salmos","Psalms","Salmos|Salmo|Sl|Sal|Psalms|Psalm|Ps");
        add("Provérbios","Proverbs","Provérbios|Proverbios|Pr|Pv|Prov|Proverbs");
        add("Eclesiastes","Ecclesiastes","Eclesiastes|Ecl|Ec|Coélet|Qohelet|Ecclesiastes|Eccles");
        add("Cântico dos Cânticos","Song of Solomon","Cântico dos Cânticos|Cântico de Salomão|Cânticos|Cantico dos Canticos|Cantico de Salomao|Canticos|Cantares de Salomão|Cantares de Salomao|Cantares|Ct|Cant|Song of Solomon|Song of Songs");
        add("Sabedoria","Wisdom","Sabedoria de Salomão|Sabedoria de Salomao|Sabedoria|Sb|Sab|Wisdom of Solomon|Wisdom|Wis");
        add("Eclesiástico","Sirach","Eclesiástico|Eclesiastico|Sirácida|Siracida|Sirach|Sir|Eclo");
        add("Isaías","Isaiah","Isaías|Isaias|Is|Isa|Isaiah");
        add("Jeremias","Jeremiah","Jeremias|Jr|Jer|Jeremiah");
        add("Lamentações","Lamentations","Lamentações de Jeremias|Lamentacoes de Jeremias|Lamentações|Lamentacoes|Lm|Lam|Lamentations");
        add("Baruque","Baruch","Baruque|Baruc|Br|Bar|Baruch");
        add("Ezequiel","Ezekiel","Ezequiel|Ez|Eze|Ezekiel|Ezek");
        add("Daniel","Daniel","Daniel|Dn|Dan");
        add("Oseias","Hosea","Oseias|Oséias|Os|Hos|Hosea");
        add("Joel","Joel","Joel|Jl");
        add("Amós","Amos","Amós|Amos|Am");
        add("Obadias","Obadiah","Obadias|Abdias|Ob|Abd|Obadiah");
        add("Jonas","Jonah","Jonas|Jn|Jon|Jonah");
        add("Miqueias","Micah","Miqueias|Miquéias|Mq|Miq|Micah|Mic");
        add("Naum","Nahum","Naum|Na|Nah|Nahum");
        add("Habacuque","Habakkuk","Habacuque|Habacuc|Hc|Hab|Habakkuk");
        add("Sofonias","Zephaniah","Sofonias|Sf|Sof|Zephaniah|Zeph");
        add("Ageu","Haggai","Ageu|Ag|Hag|Haggai");
        add("Zacarias","Zechariah","Zacarias|Zc|Zac|Zechariah|Zech");
        add("Malaquias","Malachi","Malaquias|Ml|Mal|Malachi");
        add("Mateus","Matthew","Mateus|Mt|Mat|Matthew|Matt");
        add("Marcos","Mark","Marcos|Mc|Mar|Mark|Mk");
        add("Lucas","Luke","Lucas|Lc|Luc|Luke|Lk");
        add("João","John","João|Joao|Jo|Joa|John|Jn");
        add("Actos","Acts","Actos dos Apóstolos|Atos dos Apóstolos|Actos dos Apostolos|Atos dos Apostolos|Actos|Atos|At|Acts");
        add("Romanos","Romans","Romanos|Rm|Rom|Romans");
        numbered(1,"Primeira Coríntios","First Corinthians","Coríntios|Corintios|Co|Cor|Corinthians");
        numbered(2,"Segunda Coríntios","Second Corinthians","Coríntios|Corintios|Co|Cor|Corinthians");
        add("Gálatas","Galatians","Gálatas|Galatas|Gl|Gal|Galatians");
        add("Efésios","Ephesians","Efésios|Efesios|Ef|Eph|Ephesians");
        add("Filipenses","Philippians","Filipenses|Fl|Fp|Fil|Phil|Philippians");
        add("Colossenses","Colossians","Colossenses|Cl|Col|Colossians");
        numbered(1,"Primeira Tessalonicenses","First Thessalonians","Tessalonicenses|Ts|Tes|Thessalonians|Thess");
        numbered(2,"Segunda Tessalonicenses","Second Thessalonians","Tessalonicenses|Ts|Tes|Thessalonians|Thess");
        numbered(1,"Primeira Timóteo","First Timothy","Timóteo|Timoteo|Tm|Tim|Timothy");
        numbered(2,"Segunda Timóteo","Second Timothy","Timóteo|Timoteo|Tm|Tim|Timothy");
        add("Tito","Titus","Tito|Tt|Tit|Titus");
        add("Filémon","Philemon","Filémon|Filemom|Filemon|Fm|Flm|Philemon|Phlm");
        add("Hebreus","Hebrews","Hebreus|Hb|Heb|Hebrews");
        add("Tiago","James","Tiago|Tg|Ti|James|Jas");
        numbered(1,"Primeira Pedro","First Peter","Pedro|Pe|Pd|Peter|Pet");
        numbered(2,"Segunda Pedro","Second Peter","Pedro|Pe|Pd|Peter|Pet");
        numbered(1,"Primeira João","First John","João|Joao|Jo|John|Jn");
        numbered(2,"Segunda João","Second John","João|Joao|Jo|John|Jn");
        numbered(3,"Terceira João","Third John","João|Joao|Jo|John|Jn");
        add("Judas","Jude","Judas|Jd|Jude");
        add("Apocalipse","Revelation","Apocalipse de João|Apocalipse de Joao|Apocalipse|Ap|Apoc|Revelation|Revelations|Rev");
        Collections.sort(ALIASES,(a,b)->Integer.compare(b.length(),a.length()));
        REFERENCE=Pattern.compile("(?<![\\p{L}\\p{N}_])("+String.join("|",ALIASES)+")(?:\\."+SPACE+"|"+GAP+")("+PART+"(?:"+SPACE+"[-–—;]"+SPACE+PART+"){0,7})(?![\\p{L}\\p{N}_:：])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    }
    private static String key(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static void add(String pt,String en,String names){
        Book book=new Book(pt,en);
        for(String name:names.split("\\|"))register(book,name,false);
    }
    private static void numbered(int number,String pt,String en,String names){
        Book book=new Book(pt,en);
        String[] prefixes=number==1?new String[]{"1","1º","1ª","I","Primeiro","Primeira","First"}:number==2?new String[]{"2","2º","2ª","II","Segundo","Segunda","Second"}:new String[]{"3","3º","3ª","III","Terceiro","Terceira","Third"};
        for(String name:names.split("\\|"))for(String prefix:prefixes)register(book,prefix+" "+name,true);
    }
    private static void register(Book book,String alias,boolean numbered){
        String k=key(alias);
        // Ambiguous abbreviations are resolved in the speech language below.
        BOOKS.put(k,book);
        String[] words=alias.split(" ");StringBuilder pattern=new StringBuilder();
        for(int i=0;i<words.length;i++){if(i>0)pattern.append(numbered&&i==1?SPACE:GAP);pattern.append(Pattern.quote(words[i]));}
        ALIASES.add(pattern.toString());
    }
    private static String spoken(Matcher reference,boolean english){
        String key=key(reference.group(1));Book book=BOOKS.get(key);StringBuilder out=new StringBuilder(!english&&key.equals("jn")?"Jonas":english?book.en:book.pt);
        String body=reference.group(2);Matcher parts=PARTS.matcher(body);int prior=0;
        while(parts.find()){
            if(prior>0){String join=body.substring(prior,parts.start());out.append(join.indexOf(';')>=0?"; ":english?" to ":" a ");}
            else out.append(' ');
            out.append(english?"chapter ":"capítulo ").append(parts.group(1));
            String verses=parts.group(2);boolean plural=verses.indexOf(',')>=0||verses.indexOf('-')>=0||verses.indexOf('–')>=0||verses.indexOf('—')>=0;
            out.append(english?(plural?", verses ":", verse "):(plural?", versículos ":", versículo "));
            for(int i=0;i<verses.length();i++){char c=verses.charAt(i);if(c=='-'||c=='–'||c=='—')out.append(english?" to ":" a ");else if(c==',')out.append(english?" and ":" e ");else if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c))out.append(c);}
            prior=parts.end();
        }
        return out.toString();
    }
    static SpeechText.Prepared expand(SpeechText.Prepared source,String language){
        Matcher match=REFERENCE.matcher(source.text);if(!match.find())return source;
        boolean english=language!=null&&language.toLowerCase(Locale.ROOT).startsWith("en");
        StringBuilder out=new StringBuilder();int[] offsets=new int[Math.max(32,source.text.length()*2)];int size=0,at=0;
        do{
            String replacement=spoken(match,english);int needed=size+match.start()-at+replacement.length();
            if(needed>offsets.length)offsets=Arrays.copyOf(offsets,Math.max(needed,offsets.length*2));
            out.append(source.text,at,match.start());while(at<match.start())offsets[size++]=source.sourceOffset(at++);
            out.append(replacement);
            // A citation is a speech atom: resuming inside it repeats the full reference,
            // rather than starting at an orphan "5:6" and pronouncing a time again.
            int anchor=source.sourceOffset(match.start());for(int i=0;i<replacement.length();i++)offsets[size++]=anchor;
            at=match.end();
        }while(match.find());
        int needed=size+source.text.length()-at;if(needed>offsets.length)offsets=Arrays.copyOf(offsets,needed);
        out.append(source.text,at,source.text.length());while(at<source.text.length())offsets[size++]=source.sourceOffset(at++);
        return new SpeechText.Prepared(out.toString(),Arrays.copyOf(offsets,size),source.sourceLength);
    }
    /** Keep a normal-sized reference in one utterance and budget its spoken expansion. */
    static int safeEnd(String text,int start,int end,int budget){
        if(end<=start)return end;
        Matcher match=REFERENCE.matcher(text);match.region(start,Math.min(text.length(),end+1024));match.useTransparentBounds(true);
        int extra=0,last=start;
        while(match.find()&&match.start()<end){
            if(match.start()-start+extra>budget)return wordEnd(text,start+budget-extra,last);
            if(match.end()>end){if(match.start()>start)return match.start();if(match.end()-start<=budget)end=match.end();else return end;}
            int spoken=Math.max(spoken(match,false).length(),spoken(match,true).length());
            if(match.start()-start+extra+spoken>budget&&match.start()>start)return match.start();
            extra+=Math.max(0,spoken-(match.end()-match.start()));
            last=match.end();
            if(last-start+extra>budget)return last;
        }
        return end-start+extra>budget?wordEnd(text,start+budget-extra,last):end;
    }
    static int safeStart(String text,int offset){
        if(offset<=0||offset>=text.length())return offset;
        Matcher match=REFERENCE.matcher(text);match.region(Math.max(0,offset-1024),Math.min(text.length(),offset+1024));match.useTransparentBounds(true);
        while(match.find()&&match.start()<offset)if(match.end()>offset)return match.start();
        return offset;
    }
    static String canonical(String name){Book book=BOOKS.get(key(name));if(book==null)return "";return key(name).equals("jn")?"Jonas":book.pt;}
    static final class Reference {final int start,end,chapter,verse,lastChapter,lastVerse;final String book,label;Reference(int start,int end,String book,String label,int chapter,int verse,int lastChapter,int lastVerse){this.start=start;this.end=end;this.book=book;this.label=label;this.chapter=chapter;this.verse=verse;this.lastChapter=lastChapter;this.lastVerse=lastVerse;}}
    /** Commas and semicolons select separate verses; only a dash creates a continuous range. */
    static List<int[]> intervals(Reference ref){List<int[]> out=new ArrayList<>();Matcher parts=PARTS.matcher(ref.label);int previousEnd=0;while(parts.find()){int chapter=Integer.parseInt(parts.group(1));List<int[]> current=new ArrayList<>();Matcher verses=Pattern.compile("([0-9]+)(?:"+SPACE+"[-–—]"+SPACE+"([0-9]+))?").matcher(parts.group(2));while(verses.find()){int first=Integer.parseInt(verses.group(1)),last=verses.group(2)==null?first:Integer.parseInt(verses.group(2));current.add(new int[]{chapter,Math.min(first,last),chapter,Math.max(first,last)});}if(previousEnd>0&&!out.isEmpty()&&!current.isEmpty()&&ref.label.substring(previousEnd,parts.start()).matches("(?s).*[-–—].*")){int[] from=out.remove(out.size()-1),to=current.remove(0);int[] range={from[0],from[1],to[2],to[3]};if(range[0]>range[2]||range[0]==range[2]&&range[1]>range[3])range=new int[]{to[0],to[1],from[2],from[3]};out.add(range);}out.addAll(current);previousEnd=parts.end();}return out;}
    static List<Reference> atStart(String text){Matcher m=REFERENCE.matcher(text);return m.lookingAt()?references(m.group()):Collections.emptyList();}
    static List<Reference> references(String text){List<Reference> out=new ArrayList<>();Matcher m=REFERENCE.matcher(text);while(m.find()&&out.size()<2000){Matcher parts=PARTS.matcher(m.group(2));int chapter=0,verse=0,lastChapter=0,lastVerse=0;while(parts.find()){int ch=Integer.parseInt(parts.group(1));Matcher numbers=Pattern.compile("[0-9]+").matcher(parts.group(2));while(numbers.find()){int v=Integer.parseInt(numbers.group());if(chapter==0){chapter=ch;verse=v;}lastChapter=ch;lastVerse=v;}}if(chapter>0)out.add(new Reference(m.start(),m.end(),canonical(m.group(1)),m.group(),chapter,verse,lastChapter,lastVerse));}return out;}
    private static int wordEnd(String text,int at,int floor){int end=Math.max(floor,at);if(end<text.length()&&end>floor&&Character.isLowSurrogate(text.charAt(end))&&Character.isHighSurrogate(text.charAt(end-1)))end--;int p=end;while(p>floor&&!Character.isWhitespace(text.charAt(p-1))&&!Character.isSpaceChar(text.charAt(p-1)))p--;return p>floor?p:end;}
    private BibleReferences(){}
}
