package mz.bam.seeky.android;

import java.util.*;
import java.util.regex.*;

/**
 * Bible citations as people write them: "Isaías 3.1-7, 12", "Jo 3,16", "Gn 1:1-3; 2:4", "Salmo 23".
 * One parser gives tappable passages in every reader and speech-friendly expansion.
 * Source documents and durable narration offsets stay intact.
 */
final class BibleReferences {
    private static final class Book {
        final String pt,en;final int chapters,verses;
        Book(String pt,String en,int chapters,int verses){this.pt=pt;this.en=en;this.chapters=chapters;this.verses=verses;}
    }
    private static final Map<String,Book> BOOKS=new HashMap<>();
    /** Per alias key: characters and letters of the name part, and whether a number prefixes it. */
    private static final Map<String,int[]> INFO=new HashMap<>();
    private static final List<String> ALIASES=new ArrayList<>();
    /** Abbreviations that are also everyday words or chemical symbols ("os 3,5 milhões", "Na 2,3 g"). */
    private static final Set<String> AMBIGUOUS=new HashSet<>(Arrays.asList("os","na","am","is","at","ex","ti","et","ed","ne","sal","mar","ag","sb","br","cl","pr","fm","lv","fl"));
    /** Names that are only books: "o Salmo 23 consola" links, "o João 3 vezes" does not. */
    private static final Set<String> FREE=new HashSet<>(Arrays.asList("génesis","gênesis","genesis","êxodo","éxodo","exodo","exodus","levítico","levitico","leviticus","deuteronómio","deuteronômio","deuteronomio","deuteronomy","salmos","salmo","psalms","psalm","eclesiastes","ecclesiastes","eclesiástico","eclesiastico","isaías","isaias","isaiah","apocalipse","revelation","efésios","efesios","ephesians","filipenses","philippians","colossenses","colossians","hebreus","hebrews"));
    private static final Set<String> CONNECTORS=new HashSet<>(Arrays.asList("e","a","até","ao","ou","cf","and","to","or"));
    private static final String SPACE="[\\s\\p{Zs}]*";
    /** Last verse of a whole chapter in a range. */
    static final int WHOLE=999;
    private static final int COLON=1,DOT=2,COMMA=3;
    private static final Pattern CANDIDATE;
    static {
        add("Génesis","Genesis","Génesis|Gênesis|Genesis|Gn|Gen",50,67);
        add("Êxodo","Exodus","Êxodo|Éxodo|Exodo|Ex|Exod|Exodus",40,51);
        add("Levítico","Leviticus","Levítico|Levitico|Lv|Lev|Leviticus",27,59);
        add("Números","Numbers","Números|Numeros|Nm|Num|Numbers",36,89);
        add("Deuteronómio","Deuteronomy","Deuteronómio|Deuteronômio|Deuteronomio|Dt|Deut|Deuteronomy",34,69);
        add("Josué","Joshua","Josué|Josue|Js|Jos|Joshua",24,63);
        add("Juízes","Judges","Juízes|Juizes|Jz|Judg|Judges",21,57);
        add("Rute","Ruth","Rute|Rt|Ruth",4,23);
        numbered(1,"Primeiro Samuel","First Samuel","Samuel|Sm|Sam",31,58);
        numbered(2,"Segundo Samuel","Second Samuel","Samuel|Sm|Sam",24,44);
        numbered(1,"Primeiro Reis","First Kings","Reis|Rs|Re|Kings|Kgs",22,66);
        numbered(2,"Segundo Reis","Second Kings","Reis|Rs|Re|Kings|Kgs",25,37);
        numbered(1,"Primeiro Crónicas","First Chronicles","Crónicas|Crônicas|Cronicas|Cr|Cron|Chronicles|Chr",29,81);
        numbered(2,"Segundo Crónicas","Second Chronicles","Crónicas|Crônicas|Cronicas|Cr|Cron|Chronicles|Chr",36,42);
        add("Esdras","Ezra","Esdras|Esd|Ed|Ezra",10,70);
        add("Neemias","Nehemiah","Neemias|Ne|Neh|Nehemiah",13,73);
        add("Tobias","Tobit","Tobias|Tobit|Tb|Tob",14,30);
        add("Judite","Judith","Judite|Jdt|Judith",16,35);
        add("Ester","Esther","Ester|Est|Et|Esther",16,35);
        numbered(1,"Primeiro Macabeus","First Maccabees","Macabeus|Mac|Mc|Maccabees|Macc",16,70);
        numbered(2,"Segundo Macabeus","Second Maccabees","Macabeus|Mac|Mc|Maccabees|Macc",15,50);
        add("Jó","Job","Jó|Job",42,41);
        add("Salmos","Psalms","Salmos|Salmo|Sl|Sal|Psalms|Psalm|Ps",151,176);
        add("Provérbios","Proverbs","Provérbios|Proverbios|Pr|Pv|Prov|Proverbs",31,36);
        add("Eclesiastes","Ecclesiastes","Eclesiastes|Ecl|Ec|Coélet|Qohelet|Ecclesiastes|Eccles",12,29);
        add("Cântico dos Cânticos","Song of Solomon","Cântico dos Cânticos|Cântico de Salomão|Cânticos|Cantico dos Canticos|Cantico de Salomao|Canticos|Cantares de Salomão|Cantares de Salomao|Cantares|Ct|Cant|Song of Solomon|Song of Songs",8,17);
        add("Sabedoria","Wisdom","Sabedoria de Salomão|Sabedoria de Salomao|Sabedoria|Sb|Sab|Wisdom of Solomon|Wisdom|Wis",19,30);
        add("Eclesiástico","Sirach","Eclesiástico|Eclesiastico|Sirácida|Siracida|Sirach|Sir|Eclo",51,50);
        add("Isaías","Isaiah","Isaías|Isaias|Is|Isa|Isaiah",66,38);
        add("Jeremias","Jeremiah","Jeremias|Jr|Jer|Jeremiah",52,64);
        add("Lamentações","Lamentations","Lamentações de Jeremias|Lamentacoes de Jeremias|Lamentações|Lamentacoes|Lm|Lam|Lamentations",5,66);
        add("Baruque","Baruch","Baruque|Baruc|Br|Bar|Baruch",6,73);
        add("Ezequiel","Ezekiel","Ezequiel|Ez|Eze|Ezekiel|Ezek",48,63);
        add("Daniel","Daniel","Daniel|Dn|Dan",14,100);
        add("Oseias","Hosea","Oseias|Oséias|Os|Hos|Hosea",14,25);
        add("Joel","Joel","Joel|Jl",4,32);
        add("Amós","Amos","Amós|Amos|Am",9,27);
        add("Obadias","Obadiah","Obadias|Abdias|Ob|Abd|Obadiah",1,21);
        add("Jonas","Jonah","Jonas|Jn|Jon|Jonah",4,17);
        add("Miqueias","Micah","Miqueias|Miquéias|Mq|Miq|Micah|Mic",7,20);
        add("Naum","Nahum","Naum|Na|Nah|Nahum",3,19);
        add("Habacuque","Habakkuk","Habacuque|Habacuc|Hc|Hab|Habakkuk",3,20);
        add("Sofonias","Zephaniah","Sofonias|Sf|Sof|Zephaniah|Zeph",3,20);
        add("Ageu","Haggai","Ageu|Ag|Hag|Haggai",2,23);
        add("Zacarias","Zechariah","Zacarias|Zc|Zac|Zechariah|Zech",14,23);
        add("Malaquias","Malachi","Malaquias|Ml|Mal|Malachi",4,24);
        add("Mateus","Matthew","Mateus|Mt|Mat|Matthew|Matt",28,75);
        add("Marcos","Mark","Marcos|Mc|Mar|Mark|Mk",16,72);
        add("Lucas","Luke","Lucas|Lc|Luc|Luke|Lk",24,80);
        add("João","John","João|Joao|Jo|Joa|John|Jn",21,71);
        add("Actos","Acts","Actos dos Apóstolos|Atos dos Apóstolos|Actos dos Apostolos|Atos dos Apostolos|Actos|Atos|At|Acts",28,60);
        add("Romanos","Romans","Romanos|Rm|Rom|Romans",16,39);
        numbered(1,"Primeira Coríntios","First Corinthians","Coríntios|Corintios|Co|Cor|Corinthians",16,58);
        numbered(2,"Segunda Coríntios","Second Corinthians","Coríntios|Corintios|Co|Cor|Corinthians",13,33);
        add("Gálatas","Galatians","Gálatas|Galatas|Gl|Gal|Galatians",6,31);
        add("Efésios","Ephesians","Efésios|Efesios|Ef|Eph|Ephesians",6,33);
        add("Filipenses","Philippians","Filipenses|Fl|Fp|Fil|Phil|Philippians",4,30);
        add("Colossenses","Colossians","Colossenses|Cl|Col|Colossians",4,29);
        numbered(1,"Primeira Tessalonicenses","First Thessalonians","Tessalonicenses|Ts|Tes|Thessalonians|Thess",5,28);
        numbered(2,"Segunda Tessalonicenses","Second Thessalonians","Tessalonicenses|Ts|Tes|Thessalonians|Thess",3,18);
        numbered(1,"Primeira Timóteo","First Timothy","Timóteo|Timoteo|Tm|Tim|Timothy",6,25);
        numbered(2,"Segunda Timóteo","Second Timothy","Timóteo|Timoteo|Tm|Tim|Timothy",4,26);
        add("Tito","Titus","Tito|Tt|Tit|Titus",3,16);
        add("Filémon","Philemon","Filémon|Filemom|Filemon|Fm|Flm|Philemon|Phlm",1,25);
        add("Hebreus","Hebrews","Hebreus|Hb|Heb|Hebrews",13,40);
        add("Tiago","James","Tiago|Tg|Ti|James|Jas",5,27);
        numbered(1,"Primeira Pedro","First Peter","Pedro|Pe|Pd|Peter|Pet",5,25);
        numbered(2,"Segunda Pedro","Second Peter","Pedro|Pe|Pd|Peter|Pet",3,22);
        numbered(1,"Primeira João","First John","João|Joao|Jo|John|Jn",5,29);
        numbered(2,"Segunda João","Second John","João|Joao|Jo|John|Jn",1,13);
        numbered(3,"Terceira João","Third John","João|Joao|Jo|John|Jn",1,15);
        add("Judas","Jude","Judas|Jd|Jude",1,25);
        add("Apocalipse","Revelation","Apocalipse de João|Apocalipse de Joao|Apocalipse|Ap|Apoc|Revelation|Revelations|Rev",22,29);
        Collections.sort(ALIASES,(a,b)->Integer.compare(b.length(),a.length()));
        CANDIDATE=Pattern.compile("(?<![\\p{L}\\p{N}_])("+String.join("|",ALIASES)+")(\\.?)("+SPACE+")(?=[1-9])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    }
    private static String key(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static void add(String pt,String en,String names,int chapters,int verses){
        Book book=new Book(pt,en,chapters,verses);
        for(String name:names.split("\\|"))register(book,name,name,false);
    }
    private static void numbered(int number,String pt,String en,String names,int chapters,int verses){
        Book book=new Book(pt,en,chapters,verses);
        String[] prefixes=number==1?new String[]{"1","1º","1ª","I","Primeiro","Primeira","First"}:number==2?new String[]{"2","2º","2ª","II","Segundo","Segunda","Second"}:new String[]{"3","3º","3ª","III","Terceiro","Terceira","Third"};
        for(String name:names.split("\\|"))for(String prefix:prefixes)register(book,prefix+" "+name,name,true);
    }
    private static void register(Book book,String alias,String name,boolean numbered){
        String k=key(alias);
        // Ambiguous abbreviations are resolved in the speech language below.
        BOOKS.put(k,book);
        int letters=0;for(int i=0;i<name.length();i++)if(Character.isLetter(name.charAt(i)))letters++;
        INFO.put(k,new int[]{name.length(),letters,numbered?1:0});
        String[] words=alias.split(" ");StringBuilder pattern=new StringBuilder();
        for(int i=0;i<words.length;i++){if(i>0)pattern.append(numbered&&i==1?SPACE:"[\\s\\p{Zs}]+");pattern.append(Pattern.quote(words[i]));}
        ALIASES.add(pattern.toString());
    }
    static String canonical(String name){Book book=BOOKS.get(key(name));if(book==null)return "";return key(name).equals("jn")?"Jonas":book.pt;}

    /** One chapter of a citation. A part joined by '-' continues the previous part as a range. */
    static final class Part {
        final int chapter;final char join;final boolean whole;
        /** {first verse, last verse, 1 when "and following"}. */
        final List<int[]> verses=new ArrayList<>();
        Part(int chapter,char join,boolean whole){this.chapter=chapter;this.join=join;this.whole=whole;}
    }
    static final class Reference {
        final int start,end,chapter,verse,lastChapter,lastVerse;
        /** Canonical Portuguese book, English book, original text and a normalised title such as "Isaías 3:1-7, 12". */
        final String book,english,label,title;
        /** True when chapter and verse are both written; a lone "Salmo 23" opens the whole chapter. */
        final boolean explicit;
        final List<Part> parts;
        final List<int[]> ranges;
        Reference(int start,int end,String book,String english,String label,List<Part> parts,boolean explicit){
            this.start=start;this.end=end;this.book=book;this.english=english;this.label=label;this.parts=Collections.unmodifiableList(parts);this.explicit=explicit;
            Part first=parts.get(0),last=parts.get(parts.size()-1);
            chapter=first.chapter;verse=first.whole||first.verses.isEmpty()?0:first.verses.get(0)[0];
            lastChapter=last.chapter;lastVerse=last.whole||last.verses.isEmpty()?0:last.verses.get(last.verses.size()-1)[1];
            ranges=Collections.unmodifiableList(ranges(parts));title=title(book,parts);
        }
        /** Every verse number written, for highlighting inside a whole chapter. */
        boolean contains(int c,int v){for(int[] r:ranges)if((c>r[0]||c==r[0]&&v>=r[1])&&(c<r[2]||c==r[2]&&v<=r[3]))return true;return false;}
    }
    private static List<int[]> ranges(List<Part> parts){
        List<int[]> out=new ArrayList<>();
        for(Part p:parts){
            List<int[]> current=new ArrayList<>();
            if(p.whole)current.add(new int[]{p.chapter,1,p.chapter,WHOLE});
            else for(int[] v:p.verses)current.add(new int[]{p.chapter,Math.min(v[0],v[1]),p.chapter,v[2]==1?WHOLE:Math.max(v[0],v[1])});
            if(p.join=='-'&&!out.isEmpty()&&!current.isEmpty()){int[] from=out.remove(out.size()-1),to=current.remove(0);out.add(new int[]{from[0],from[1],to[2],to[3]});}
            out.addAll(current);
        }
        return out;
    }
    private static String title(String book,List<Part> parts){
        StringBuilder out=new StringBuilder(book);
        for(int i=0;i<parts.size();i++){
            Part p=parts.get(i);out.append(i==0?" ":p.join==';'?"; ":p.join=='-'?"-":", ").append(p.chapter);
            if(p.whole)continue;
            out.append(':');
            for(int j=0;j<p.verses.size();j++){int[] v=p.verses.get(j);if(j>0)out.append(", ");out.append(v[0]);if(v[1]!=v[0])out.append('-').append(v[1]);if(v[2]==1)out.append("ss");}
        }
        return out.toString();
    }
    /** Commas and semicolons select separate verses; only a dash creates a continuous range. */
    static List<int[]> intervals(Reference ref){return new ArrayList<>(ref.ranges);}

    private static int skip(String t,int at){while(at<t.length()&&(Character.isWhitespace(t.charAt(at))||Character.isSpaceChar(t.charAt(at))))at++;return at;}
    private static boolean dash(char c){return c=='-'||c=='–'||c=='—'||c=='‐'||c=='‑'||c=='−';}
    private static boolean digit(char c){return c>='0'&&c<='9';}
    /** End of a one-to-three digit number without a leading zero, or -1. */
    private static int number(String t,int at){
        if(at>=t.length()||t.charAt(at)<'1'||t.charAt(at)>'9')return -1;
        int end=at+1;while(end<t.length()&&end-at<3&&digit(t.charAt(end)))end++;
        return end<t.length()&&digit(t.charAt(end))?-1:end;
    }
    /** Start of the verse after a chapter-verse separator in this style, or -1. */
    private static int separator(String t,int end,int style){
        if(style==COLON){int q=skip(t,end);if(q<t.length()&&(t.charAt(q)==':'||t.charAt(q)=='：')){int r=skip(t,q+1);return number(t,r)>0?r:-1;}return -1;}
        char s=style==DOT?'.':',';
        return end<t.length()&&t.charAt(end)==s&&number(t,end+1)>0?end+1:-1;
    }
    /** Skips "a"/"b" half verses and "s"/"ss"/"f"/"ff" (and following); -1 when a word is glued to the number. */
    private static int suffix(String t,int at,int[] item){
        if(at>=t.length()||!Character.isLetter(t.charAt(at)))return at;
        int end=at;while(end<t.length()&&Character.isLetter(t.charAt(end)))end++;
        String word=t.substring(at,end);
        if(word.equals("s")||word.equals("ss")||word.equals("f")||word.equals("ff")){if(item!=null)item[2]=1;return end;}
        return word.length()==1&&word.charAt(0)>='a'&&word.charAt(0)<='c'?end:-1;
    }
    /** A lone chapter must end like a citation, not like "João 3 vezes". */
    private static boolean closes(String t,int end){
        if(end>=t.length())return true;
        char c=t.charAt(end);
        if(Character.isLetterOrDigit(c)||c=='_'||c=='%'||c=='°'||c=='º'||c=='ª')return false;
        int q=skip(t,end);if(q>=t.length())return true;
        for(int i=end;i<q;i++)if(t.charAt(i)=='\n'||t.charAt(i)=='\r')return true;
        char n=t.charAt(q);
        if(")]}»”\"'!?;([".indexOf(n)>=0)return true;
        if(n==','||n=='.'||n==':'||n=='：'||dash(n)){int r=n==','||n=='.'?q+1:skip(t,q+1);return r>=t.length()||!digit(t.charAt(r));}
        return word(t,q);
    }
    private static boolean word(String t,int q){
        if(q>=t.length()||!Character.isLetter(t.charAt(q)))return false;
        int w=q;while(w<t.length()&&Character.isLetter(t.charAt(w)))w++;
        return CONNECTORS.contains(t.substring(q,w).toLowerCase(Locale.ROOT));
    }
    private static Reference parse(String t,Matcher m,boolean strict){
        String alias=m.group(1),k=key(alias);Book book=BOOKS.get(k);int[] info=INFO.get(k);
        if(book==null||info==null)return null;
        boolean shortName=info[1]<=3,numbered=info[2]==1;
        if(strict){
            char first=alias.charAt(numbered?Math.max(0,alias.length()-info[0]):0);
            if(shortName&&Character.isLowerCase(first))return null;
            if(m.group(2).isEmpty()&&m.group(3).isEmpty()&&info[1]<4)return null;
        }
        int bound=strict?book.verses+3:176;
        int at=m.end(),ce=number(t,at);if(ce<0)return null;
        int chapter=Integer.parseInt(t.substring(at,ce));
        int style=0,sep=-1;
        for(int s=COLON;s<=COMMA&&sep<0;s++){sep=separator(t,ce,s);if(sep>=0)style=s;}
        List<Part> parts=new ArrayList<>();int end;
        if(style==0){
            if(strict&&shortName&&!numbered)return null;
            int last=chapter;end=ce;
            int q=skip(t,ce);
            if(q<t.length()&&dash(t.charAt(q))){int r=skip(t,q+1),rn=number(t,r);if(rn>0&&separator(t,rn,COLON)<0&&separator(t,rn,DOT)<0&&separator(t,rn,COMMA)<0){int value=Integer.parseInt(t.substring(r,rn));if(value>chapter&&value<=(book.chapters==1?bound:book.chapters)){last=value;end=rn;}}}
            if(book.chapters==1){
                // Obadias, Filémon, 2 and 3 João and Judas: "Judas 3" is a verse.
                if(chapter>bound)return null;
                Part p=new Part(1,(char)0,false);p.verses.add(new int[]{chapter,last,0});parts.add(p);
            }else{
                if(chapter>book.chapters)return null;
                parts.add(new Part(chapter,(char)0,true));
                if(last!=chapter)parts.add(new Part(last,'-',true));
            }
            if(strict){
                // A lone chapter needs a capital and, unless the name is only a book, a citation-like ending.
                if(Character.isLowerCase(alias.charAt(numbered?Math.max(0,alias.length()-info[0]):0)))return null;
                if(!(numbered||FREE.contains(k))&&!closes(t,end))return null;
                if(end<t.length()){char c=t.charAt(end);if(Character.isLetterOrDigit(c)||c=='_'||c=='%'||c=='°'||c=='º'||c=='ª')return null;}
            }
            return new Reference(m.start(),end,canonical(alias),book.en,t.substring(m.start(),end),parts,false);
        }
        if(chapter>book.chapters)return null;
        Part part=new Part(chapter,(char)0,false);parts.add(part);
        int p=sep,current=chapter;char join=0;end=-1;
        while(true){
            int n=number(t,p);if(n<0)break;
            int value=Integer.parseInt(t.substring(p,n));
            int next=separator(t,n,style);
            Part target=null;int verse,after;
            if(next>=0){
                if(join==0)return null; // "João 5:16:30", "Ne 2.3.1"
                int vn=number(t,next);
                if(value>book.chapters||separator(t,vn,style)>=0)break;
                target=new Part(value,join,false);verse=Integer.parseInt(t.substring(next,vn));after=vn;
            }else if(join==';'){
                // "Is 3.1-7; 5" adds the whole of chapter 5.
                if(value>book.chapters||strict&&!closes(t,n))break;
                parts.add(new Part(value,';',true));end=n;break;
            }else{verse=value;after=n;}
            if(verse>bound){if(join==0)return null;break;}
            int[] item={verse,verse,0};
            int tail=suffix(t,after,item);if(tail<0){if(join==0)return null;break;}
            int itemEnd=tail;Part range=null;
            int q=skip(t,tail);
            if(item[2]==0&&q<t.length()&&dash(t.charAt(q))){
                int r=skip(t,q+1),rn=number(t,r);
                if(rn>0){
                    int to=Integer.parseInt(t.substring(r,rn)),cross=separator(t,rn,style);
                    if(cross>=0){
                        // "João 3:16-4:2" runs into the next chapter.
                        int vn=number(t,cross),v2=Integer.parseInt(t.substring(cross,vn));int[] last={v2,v2,0};int ct=suffix(t,vn,last);
                        if(to>(target!=null?target.chapter:current)&&to<=book.chapters&&v2<=bound&&ct>=0&&separator(t,vn,style)<0){range=new Part(to,'-',false);range.verses.add(last);itemEnd=ct;}
                    }else{int[] last={to,to,0};int rt=suffix(t,rn,last);if(to<=bound&&rt>=0){item[0]=Math.min(verse,to);item[1]=Math.max(verse,to);item[2]=last[2];itemEnd=rt;}}
                }
            }
            if(target!=null){parts.add(target);part=target;current=target.chapter;}
            part.verses.add(item);end=itemEnd;
            if(range!=null){parts.add(range);part=range;current=range.chapter;}
            q=skip(t,end);
            char list=style==COMMA?'.':',';
            if(q<t.length()&&t.charAt(q)==list&&(style!=COMMA||q==end)){int r=style==COMMA?q+1:skip(t,q+1);if(number(t,r)>0){join=',';p=r;continue;}}
            if(q<t.length()&&t.charAt(q)==';'){int r=skip(t,q+1);if(number(t,r)>0){join=';';p=r;continue;}}
            break;
        }
        if(end<0)return null;
        if(end<t.length()){char c=t.charAt(end);if(Character.isLetterOrDigit(c)||c=='_'||c=='%'||c=='°'||c=='º'||c=='ª')return null;}
        if(style==COLON){int q=skip(t,end);if(q<t.length()&&(t.charAt(q)==':'||t.charAt(q)=='：')){int r=skip(t,q+1);if(r<t.length()&&digit(t.charAt(r)))return null;}}
        if(strict&&style!=COLON&&!numbered&&AMBIGUOUS.contains(k)){
            // "Os 3,5 milhões" is a number, "Os 3,5" alone or "Os 3,5 e 7" stays a citation.
            int q=skip(t,end);if(q>end&&q<t.length()&&Character.isLetter(t.charAt(q))&&!word(t,q))return null;
        }
        return new Reference(m.start(),end,canonical(alias),book.en,t.substring(m.start(),end),parts,true);
    }
    private static List<Reference> scan(String text,int from,int to,boolean strict,int limit){
        List<Reference> out=new ArrayList<>();if(text==null||text.isEmpty())return out;
        Matcher m=CANDIDATE.matcher(text);m.region(Math.max(0,from),Math.min(text.length(),Math.max(from,to)));m.useTransparentBounds(true);
        int last=0;
        while(out.size()<limit&&m.find()){if(m.start()<last)continue;Reference r=parse(text,m,strict);if(r!=null){out.add(r);last=r.end;}}
        return out;
    }
    /** Citations a reader can tap. */
    static List<Reference> references(String text){return scan(text,0,text==null?0:text.length(),true,2000);}
    /** A typed or linked passage: lower case, short abbreviations and lone chapters are accepted. */
    static Reference lookup(String text){List<Reference> found=scan(text,0,text==null?0:text.length(),false,1);return found.isEmpty()?null:found.get(0);}
    /** Imported Bible lines such as "Gênesis 1:1 No princípio". */
    static List<Reference> atStart(String text){Matcher m=CANDIDATE.matcher(text);if(!m.lookingAt())return Collections.emptyList();Reference r=parse(text,m,false);return r==null||!r.explicit?Collections.<Reference>emptyList():Collections.singletonList(r);}

    private static String spoken(Reference r,boolean english){
        StringBuilder out=new StringBuilder(english?r.english:r.book);
        for(int i=0;i<r.parts.size();i++){
            Part p=r.parts.get(i);
            out.append(i==0?" ":p.join==';'?"; ":p.join=='-'?(english?" to ":" a "):(english?" and ":" e "));
            out.append(english?"chapter ":"capítulo ").append(p.chapter);
            if(p.whole)continue;
            boolean plural=p.verses.size()>1;for(int[] v:p.verses)if(v[0]!=v[1]||v[2]==1)plural=true;
            out.append(english?(plural?", verses ":", verse "):(plural?", versículos ":", versículo "));
            for(int j=0;j<p.verses.size();j++){int[] v=p.verses.get(j);if(j>0)out.append(english?" and ":" e ");out.append(v[0]);if(v[1]!=v[0])out.append(english?" to ":" a ").append(v[1]);if(v[2]==1)out.append(english?" and following":" e seguintes");}
        }
        return out.toString();
    }
    private static List<Reference> spoken(String text,int from,int to){List<Reference> out=new ArrayList<>();for(Reference r:scan(text,from,to,true,4000))if(r.explicit)out.add(r);return out;}
    static SpeechText.Prepared expand(SpeechText.Prepared source,String language){
        List<Reference> refs=spoken(source.text,0,source.text.length());if(refs.isEmpty())return source;
        boolean english=language!=null&&language.toLowerCase(Locale.ROOT).startsWith("en");
        StringBuilder out=new StringBuilder();int[] offsets=new int[Math.max(32,source.text.length()*2)];int size=0,at=0;
        for(Reference ref:refs){
            String replacement=spoken(ref,english);int needed=size+ref.start-at+replacement.length();
            if(needed>offsets.length)offsets=Arrays.copyOf(offsets,Math.max(needed,offsets.length*2));
            out.append(source.text,at,ref.start);while(at<ref.start)offsets[size++]=source.sourceOffset(at++);
            out.append(replacement);
            // A citation is a speech atom: resuming inside it repeats the full reference,
            // rather than starting at an orphan "5:6" and pronouncing a time again.
            int anchor=source.sourceOffset(ref.start);for(int i=0;i<replacement.length();i++)offsets[size++]=anchor;
            at=ref.end;
        }
        int needed=size+source.text.length()-at;if(needed>offsets.length)offsets=Arrays.copyOf(offsets,needed);
        out.append(source.text,at,source.text.length());while(at<source.text.length())offsets[size++]=source.sourceOffset(at++);
        return new SpeechText.Prepared(out.toString(),Arrays.copyOf(offsets,size),source.sourceLength);
    }
    /** Keep a normal-sized reference in one utterance and budget its spoken expansion. */
    static int safeEnd(String text,int start,int end,int budget){
        if(end<=start)return end;
        int extra=0,last=start;
        for(Reference match:spoken(text,start,Math.min(text.length(),end+1024))){
            if(match.start>=end)break;
            if(match.start-start+extra>budget)return wordEnd(text,start+budget-extra,last);
            if(match.end>end){if(match.start>start)return match.start;if(match.end-start<=budget)end=match.end;else return end;}
            int spoken=Math.max(spoken(match,false).length(),spoken(match,true).length());
            if(match.start-start+extra+spoken>budget&&match.start>start)return match.start;
            extra+=Math.max(0,spoken-(match.end-match.start));
            last=match.end;
            if(last-start+extra>budget)return last;
        }
        return end-start+extra>budget?wordEnd(text,start+budget-extra,last):end;
    }
    static int safeStart(String text,int offset){
        if(offset<=0||offset>=text.length())return offset;
        for(Reference match:spoken(text,Math.max(0,offset-1024),Math.min(text.length(),offset+1024))){if(match.start>=offset)break;if(match.end>offset)return match.start;}
        return offset;
    }
    private static int wordEnd(String text,int at,int floor){int end=Math.max(floor,at);if(end<text.length()&&end>floor&&Character.isLowSurrogate(text.charAt(end))&&Character.isHighSurrogate(text.charAt(end-1)))end--;int p=end;while(p>floor&&!Character.isWhitespace(text.charAt(p-1))&&!Character.isSpaceChar(text.charAt(p-1)))p--;return p>floor?p:end;}
    private BibleReferences(){}
}
