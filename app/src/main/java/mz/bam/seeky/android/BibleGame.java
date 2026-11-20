package mz.bam.seeky.android;

import java.util.*;

/**
 * Bible quiz rules: ten questions per round in three levels. Verse questions use the Bibles the person
 * imported; story, order, group, author and fact questions work without any Bible.
 */
final class BibleGame {
    static final int EASY=0,MEDIUM=1,HARD=2,ROUND=10;
    static final String[] LEVELS={"Fácil","Médio","Difícil"};
    static final int[] SECONDS={30,25,20};
    static final int[] POINTS={100,150,200};

    static final class Verse {final String book,text;final int chapter,verse;Verse(String book,int chapter,int verse,String text){this.book=book;this.chapter=chapter;this.verse=verse;this.text=text;}String reference(){return display(book)+" "+chapter+":"+verse;}}
    /** Verses from an imported Bible; {@link #available()} is false without one. */
    interface Verses {
        boolean available();
        /** Random verses of the given books (any book when null), short enough to read on a card. */
        List<Verse> random(List<String> books,int count,Random random);
        Verse get(String book,int chapter,int verse);
    }
    static final class Question {
        final String kind,prompt,body,answer,reference,explain;final List<String> options;
        Question(String kind,String prompt,String body,List<String> options,String answer,String reference,String explain){this.kind=kind;this.prompt=prompt;this.body=body;this.options=options;this.answer=answer;this.reference=reference;this.explain=explain;}
        /** Written or spoken answer instead of choices. */
        boolean free(){return options.isEmpty();}
        boolean correct(String given){
            if(given==null)return false;
            if(free()){String book=BibleReferences.bookNamed(given);return !book.isEmpty()&&book.equals(answer);}
            return answer.equals(given);
        }
        /** The answer as shown to the person. */
        String shown(){return free()?display(answer):answer;}
    }

    static final String[][] GROUPS={
        {"Pentateuco","Génesis","Êxodo","Levítico","Números","Deuteronómio"},
        {"Livros históricos","Josué","Juízes","Rute","Primeiro Samuel","Segundo Samuel","Primeiro Reis","Segundo Reis","Primeiro Crónicas","Segundo Crónicas","Esdras","Neemias","Tobias","Judite","Ester","Primeiro Macabeus","Segundo Macabeus"},
        {"Livros poéticos e sapienciais","Jó","Salmos","Provérbios","Eclesiastes","Cântico dos Cânticos","Sabedoria","Eclesiástico"},
        {"Profetas maiores","Isaías","Jeremias","Lamentações","Baruque","Ezequiel","Daniel"},
        {"Profetas menores","Oseias","Joel","Amós","Obadias","Jonas","Miqueias","Naum","Habacuque","Sofonias","Ageu","Zacarias","Malaquias"},
        {"Evangelhos","Mateus","Marcos","Lucas","João"},
        {"História da Igreja","Actos"},
        {"Cartas de Paulo","Romanos","Primeira Coríntios","Segunda Coríntios","Gálatas","Efésios","Filipenses","Colossenses","Primeira Tessalonicenses","Segunda Tessalonicenses","Primeira Timóteo","Segunda Timóteo","Tito","Filémon"},
        {"Cartas gerais","Hebreus","Tiago","Primeira Pedro","Segunda Pedro","Primeira João","Segunda João","Terceira João","Judas"},
        {"Profecia","Apocalipse"}};
    static final String[] POPULAR={"Génesis","Êxodo","Josué","Juízes","Rute","Primeiro Samuel","Segundo Samuel","Primeiro Reis","Jó","Salmos","Provérbios","Eclesiastes","Isaías","Jeremias","Daniel","Jonas","Mateus","Marcos","Lucas","João","Actos","Romanos","Primeira Coríntios","Gálatas","Efésios","Filipenses","Hebreus","Tiago","Primeira Pedro","Primeira João","Apocalipse"};
    static final String[] FAMOUS={"Génesis 1:1","Génesis 1:27","Êxodo 20:3","Deuteronómio 6:5","Josué 1:9","Primeiro Samuel 16:7","Salmos 23:1","Salmos 46:1","Salmos 91:1","Salmos 119:105","Salmos 121:1","Provérbios 3:5","Provérbios 22:6","Eclesiastes 3:1","Isaías 9:6","Isaías 40:31","Isaías 53:5","Jeremias 29:11","Lamentações 3:22","Miqueias 6:8","Malaquias 3:10","Mateus 5:9","Mateus 5:14","Mateus 6:33","Mateus 7:7","Mateus 11:28","Mateus 28:19","Marcos 16:15","Lucas 2:11","João 1:1","João 3:16","João 8:32","João 11:25","João 13:34","João 14:6","João 15:5","Actos 1:8","Romanos 3:23","Romanos 6:23","Romanos 8:28","Romanos 12:2","Primeira Coríntios 13:4","Segunda Coríntios 5:17","Gálatas 5:22","Efésios 2:8","Efésios 6:11","Filipenses 4:6","Filipenses 4:13","Colossenses 3:23","Segunda Timóteo 3:16","Hebreus 11:1","Hebreus 13:8","Tiago 1:5","Primeira Pedro 5:7","Primeira João 4:8","Apocalipse 3:20","Apocalipse 21:4"};
    /** {level, book, story, books that must not be offered as wrong answers}. */
    static final String[][] STORIES={
        {"0","Génesis","A criação do mundo, do primeiro homem e da primeira mulher",""},
        {"0","Génesis","A arca de Noé e o dilúvio",""},
        {"0","Génesis","A torre de Babel e a confusão das línguas",""},
        {"0","Génesis","Caim e Abel, os primeiros irmãos",""},
        {"0","Êxodo","Moisés e as pragas do Egipto",""},
        {"0","Êxodo","A travessia do mar Vermelho",""},
        {"0","Êxodo","Moisés recebe os Dez Mandamentos no monte Sinai","Deuteronómio"},
        {"0","Josué","A queda das muralhas de Jericó",""},
        {"0","Juízes","Sansão e Dalila",""},
        {"0","Rute","Rute fica com Noemi: «o teu povo será o meu povo»",""},
        {"0","Primeiro Samuel","O jovem David vence o gigante Golias",""},
        {"0","Salmos","«O Senhor é o meu pastor, nada me faltará»",""},
        {"0","Daniel","Daniel na cova dos leões",""},
        {"0","Jonas","Jonas é engolido por um grande peixe",""},
        {"0","Ester","A rainha Ester salva o seu povo",""},
        {"0","Jó","Um homem justo perde tudo e mantém a fé",""},
        {"0","Lucas","O nascimento de Jesus e os pastores de Belém","Mateus"},
        {"0","Mateus","Os magos do Oriente seguem a estrela",""},
        {"0","Mateus","O Sermão do Monte e as bem-aventuranças","Lucas"},
        {"0","João","A água transformada em vinho nas bodas de Caná",""},
        {"0","João","A ressurreição de Lázaro",""},
        {"0","Lucas","A parábola do filho pródigo",""},
        {"0","Lucas","A parábola do bom samaritano",""},
        {"0","Actos","O Espírito Santo desce no dia de Pentecostes",""},
        {"0","Apocalipse","Um novo céu, uma nova terra e a nova Jerusalém",""},
        {"1","Génesis","José é vendido pelos irmãos e chega a governador do Egipto",""},
        {"1","Êxodo","Deus fala a Moisés numa sarça que arde sem se consumir",""},
        {"1","Juízes","Gideão vence com apenas trezentos homens",""},
        {"1","Primeiro Samuel","O menino Samuel ouve Deus chamá-lo durante a noite",""},
        {"1","Primeiro Reis","Salomão julga o caso das duas mães e do bebé",""},
        {"1","Primeiro Reis","Elias desafia os profetas de Baal no monte Carmelo",""},
        {"1","Segundo Reis","Elias é levado ao céu num carro de fogo",""},
        {"1","Neemias","A reconstrução das muralhas de Jerusalém","Esdras"},
        {"1","Daniel","Três jovens saem ilesos de uma fornalha ardente",""},
        {"1","Lucas","Zaqueu sobe a uma árvore para ver Jesus",""},
        {"1","João","Jesus conversa com uma mulher samaritana junto ao poço",""},
        {"1","João","Nicodemos visita Jesus de noite: é preciso nascer de novo",""},
        {"1","Actos","A conversão de Saulo no caminho de Damasco",""},
        {"1","Primeira Coríntios","O grande capítulo sobre o amor",""},
        {"1","Efésios","A armadura de Deus",""},
        {"1","Hebreus","A galeria dos heróis da fé",""},
        {"1","Gálatas","O fruto do Espírito: amor, alegria, paz…",""},
        {"1","Eclesiastes","«Tudo tem o seu tempo determinado»",""},
        {"1","Provérbios","O retrato da mulher virtuosa",""},
        {"2","Segundo Reis","Naamã é curado da lepra ao mergulhar sete vezes no Jordão",""},
        {"2","Daniel","Uma mão escreve na parede durante o banquete de Belsazar",""},
        {"2","Ezequiel","Um vale de ossos secos volta à vida",""},
        {"2","Isaías","O servo sofredor, ferido pelas nossas transgressões",""},
        {"2","Actos","Paulo naufraga e chega à ilha de Malta",""},
        {"2","Actos","Pedro é libertado da prisão por um anjo",""},
        {"2","Filémon","Paulo intercede pelo escravo fugitivo Onésimo",""},
        {"2","Números","A jumenta de Balaão fala",""},
        {"2","Juízes","Débora, juíza e profetisa, conduz Israel à vitória",""},
        {"2","Primeiro Samuel","Ana ora pelo filho no santuário de Siló",""},
        {"2","Segundo Samuel","O profeta Natã conta a David a parábola da ovelha",""},
        {"2","Esdras","O regresso do exílio e a reconstrução do templo","Neemias,Ageu"},
        {"2","Oseias","Um profeta casa com uma mulher infiel como sinal do amor de Deus",""},
        {"2","Joel","«Derramarei o meu Espírito sobre toda a carne»","Actos"},
        {"2","Amós","Um pastor de Tecoa denuncia a injustiça social",""},
        {"2","Miqueias","A profecia de que o Messias nasceria em Belém","Mateus"},
        {"2","Malaquias","«Trazei todos os dízimos à casa do tesouro»",""},
        {"2","Tiago","«A fé sem obras é morta»",""},
        {"2","Judas","O arcanjo Miguel disputa com o diabo o corpo de Moisés",""},
        {"2","Génesis","Jacob luta durante a noite e passa a chamar-se Israel",""},
        {"2","Josué","O sol detém-se sobre Gibeão",""},
        {"2","Ageu","Um apelo para reconstruir a casa do Senhor","Esdras,Neemias"},
        {"2","Zacarias","A visão do candelabro de ouro e das duas oliveiras",""}};
    /** {level, question, answer, wrong answers…}. */
    static final String[][] FACTS={
        {"0","Qual é o primeiro livro da Bíblia?","Génesis","Êxodo","Mateus","Salmos"},
        {"0","Qual é o último livro da Bíblia?","Apocalipse","Malaquias","Judas","Actos"},
        {"0","Quantos evangelhos há na Bíblia?","4","3","5","12"},
        {"0","Qual é o primeiro livro do Novo Testamento?","Mateus","Marcos","João","Actos"},
        {"2","Quantos livros tem o Novo Testamento?","27","24","29","39"},
        {"2","Quantos livros tem o Antigo Testamento na Bíblia protestante?","39","27","46","66"},
        {"2","Quantos livros tem a Bíblia protestante?","66","73","62","77"},
        {"2","Qual é o livro com mais capítulos?","Salmos","Isaías","Génesis","Jeremias"},
        {"2","Qual é o capítulo mais longo da Bíblia?","Salmo 119","Salmo 23","Isaías 53","Salmo 150"},
        {"2","Qual é o capítulo mais curto da Bíblia?","Salmo 117","Salmo 23","Salmo 1","Salmo 150"},
        {"2","Qual destes livros tem um só capítulo?","Judas","Rute","Tito","Jonas"},
        {"2","Em que língua foi escrita a maior parte do Antigo Testamento?","Hebraico","Grego","Latim","Árabe"},
        {"2","Em que língua foi escrito o Novo Testamento?","Grego","Hebraico","Latim","Árabe"},
        {"2","Qual é o último livro do Antigo Testamento na ordem protestante?","Malaquias","Zacarias","Daniel","Ageu"},
        {"2","Quantas cartas de Paulo há no Novo Testamento, sem contar Hebreus?","13","7","10","14"}};
    /** {author, books…} as tradition attributes them. */
    static final String[][] AUTHORS={
        {"Moisés","Génesis","Êxodo","Levítico","Números","Deuteronómio"},
        {"David","Salmos"},
        {"Salomão","Provérbios","Eclesiastes","Cântico dos Cânticos"},
        {"Jeremias","Lamentações"},
        {"Lucas","Actos"},
        {"João","Apocalipse","Primeira João","Segunda João","Terceira João"},
        {"Paulo","Romanos","Primeira Coríntios","Segunda Coríntios","Gálatas","Efésios","Filipenses","Colossenses","Primeira Tessalonicenses","Segunda Tessalonicenses","Primeira Timóteo","Segunda Timóteo","Tito","Filémon"},
        {"Pedro","Primeira Pedro","Segunda Pedro"}};
    static final String[] PEOPLE={"Moisés","David","Salomão","Paulo","João","Lucas","Pedro","Jeremias","Isaías","Mateus","Tiago","Samuel","Esdras"};

    /** "Primeira Coríntios" → "1 Coríntios". */
    static String display(String book){return book.replaceFirst("^Primeir[oa] ","1 ").replaceFirst("^Segund[oa] ","2 ").replaceFirst("^Terceir[oa] ","3 ");}
    static String group(String book){for(String[] g:GROUPS)for(int i=1;i<g.length;i++)if(g[i].equals(book))return g[0];return "";}
    static List<String> inGroup(String name){for(String[] g:GROUPS)if(g[0].equals(name)){List<String> out=new ArrayList<>(Arrays.asList(g).subList(1,g.length));out.removeIf(BibleReferences::deuterocanonical);return out;}return new ArrayList<>();}
    static String author(String book){for(String[] a:AUTHORS)for(int i=1;i<a.length;i++)if(a[i].equals(book))return a[0];return "";}

    final Random random;final Verses verses;final int level;final Set<String> used=new HashSet<>();
    BibleGame(int level,Random random,Verses verses){this.level=Math.max(EASY,Math.min(HARD,level));this.random=random;this.verses=verses;}

    /** Ten questions, mixed and never repeated within the round. */
    List<Question> round(){
        boolean bible=verses!=null&&verses.available();
        String[] plan;
        if(level==EASY)plan=bible?new String[]{"verse","verse","verse","verse","refverse","story","story","story","testament","fact"}:new String[]{"story","story","story","story","story","story","testament","testament","fact","fact"};
        else if(level==MEDIUM)plan=bible?new String[]{"verse","verse","verse","refverse","refverse","story","story","order-next","group","author"}:new String[]{"story","story","story","story","order-next","order-next","group","group","author","author"};
        else plan=bible?new String[]{"verse","verse","chapter","chapter","free","free","refverse","chapters","first","story"}:new String[]{"story","story","story","chapters","chapters","first","first","fact","fact","order-next"};
        List<Question> out=new ArrayList<>();
        String[] spare=level==EASY?new String[]{"story","testament","fact","order-next"}:level==MEDIUM?new String[]{"story","group","author","order-next","first"}:new String[]{"story","first","chapters","fact","order-next","group"};
        for(String kind:plan){Question q=null;for(int tries=0;tries<12&&q==null;tries++)q=make(kind);for(int s=0;q==null&&s<spare.length*3;s++)q=make(spare[s%spare.length]);if(q!=null)out.add(q);}
        Collections.shuffle(out,random);
        return out;
    }
    Question make(String kind){
        Question q;
        switch(kind){
            case "verse":q=verseToBook();break;
            case "refverse":q=referenceToVerse();break;
            case "chapter":q=bookAndChapter();break;
            case "free":q=writeTheBook();break;
            case "story":q=story();break;
            case "testament":q=testament();break;
            case "fact":q=fact();break;
            case "order-next":q=neighbour();break;
            case "group":q=groupQuestion();break;
            case "author":q=authorQuestion();break;
            case "chapters":q=chapterCount();break;
            default:q=firstInOrder();
        }
        if(q==null||!used.add(q.prompt+"|"+q.body+"|"+q.answer))return null;
        return q;
    }
    <T> T pick(List<T> list){return list.get(random.nextInt(list.size()));}
    List<String> books(){return BibleReferences.books(false);}
    /** The right answer plus three different wrong ones, shuffled. */
    List<String> options(String right,List<String> pool){
        List<String> wrong=new ArrayList<>(new LinkedHashSet<>(pool));wrong.remove(right);Collections.shuffle(wrong,random);
        List<String> out=new ArrayList<>();out.add(right);for(String w:wrong){if(out.size()>=4)break;if(!out.contains(w))out.add(w);}
        if(out.size()<4)return null;Collections.shuffle(out,random);return out;
    }
    /** Wrong books for a level: other groups (easy), same testament (medium), same group first (hard). */
    List<String> distractors(String book){
        List<String> pool=new ArrayList<>();boolean nt=BibleReferences.newTestament(book);String g=group(book);
        if(level==EASY){for(String b:POPULAR)if(!group(b).equals(g))pool.add(b);}
        else if(level==MEDIUM){for(String b:POPULAR)if(BibleReferences.newTestament(b)==nt)pool.add(b);if(pool.size()<6)for(String b:books())if(BibleReferences.newTestament(b)==nt)pool.add(b);}
        else{List<String> same=inGroup(g);same.remove(book);Collections.shuffle(same,random);pool.addAll(same.subList(0,Math.min(3,same.size())));if(pool.size()<3)for(String b:books())if(BibleReferences.newTestament(b)==nt)pool.add(b);}
        pool.remove(book);return pool;
    }
    List<String> displayed(List<String> books){List<String> out=new ArrayList<>();for(String b:books)out.add(display(b));return out;}
    Verse verse(){
        if(verses==null||!verses.available())return null;
        if(level==EASY){for(int i=0;i<8;i++){BibleReferences.Reference r=BibleReferences.lookup(pick(Arrays.asList(FAMOUS)));if(r==null)continue;Verse v=verses.get(r.book,r.chapter,r.verse);if(v!=null&&v.text.length()>=20)return v;}return null;}
        List<Verse> found=verses.random(level==MEDIUM?Arrays.asList(POPULAR):null,3,random);
        for(Verse v:found)if(v.text.length()>=30&&v.text.length()<=280)return v;
        return found.isEmpty()?null:found.get(0);
    }
    static String clip(String text,int max){String t=text.trim().replaceAll("\\s+"," ");return t.length()<=max?t:t.substring(0,Math.max(0,t.lastIndexOf(' ',max))).trim()+"…";}
    Question verseToBook(){
        Verse v=verse();if(v==null)return null;
        List<String> o=options(display(v.book),displayed(distractors(v.book)));if(o==null)return null;
        return new Question("verse","Em que livro está esta passagem?","«"+clip(v.text,300)+"»",o,display(v.book),v.reference(),v.reference()+" · "+group(v.book));
    }
    Question writeTheBook(){
        Verse v=verse();if(v==null)return null;
        return new Question("free","Escreva ou diga o livro desta passagem","«"+clip(v.text,300)+"»",Collections.<String>emptyList(),v.book,v.reference(),v.reference()+" · "+group(v.book));
    }
    Question bookAndChapter(){
        Verse v=verse();if(v==null)return null;
        String right=display(v.book)+" "+v.chapter;List<String> pool=new ArrayList<>();int max=BibleReferences.chapters(v.book);
        for(int i=0;i<6&&max>1;i++){int c=1+random.nextInt(max);if(c!=v.chapter)pool.add(display(v.book)+" "+c);}
        for(String b:distractors(v.book)){int m=Math.max(1,BibleReferences.chapters(b));pool.add(display(b)+" "+(1+random.nextInt(m)));}
        List<String> mixed=new ArrayList<>();for(String p:pool)if(!mixed.contains(p)&&!p.equals(right))mixed.add(p);Collections.shuffle(mixed,random);
        List<String> wrong=new ArrayList<>();for(String p:mixed){if(wrong.size()>=3)break;if(p.startsWith(display(v.book)+" ")){if(wrong.size()<2)wrong.add(p);}else wrong.add(p);}
        for(String p:mixed){if(wrong.size()>=3)break;if(!wrong.contains(p))wrong.add(p);}
        List<String> o=options(right,wrong);if(o==null)return null;
        return new Question("chapter","Em que livro e capítulo está esta passagem?","«"+clip(v.text,300)+"»",o,right,v.reference(),v.reference());
    }
    Question referenceToVerse(){
        Verse v=verse();if(v==null)return null;
        List<Verse> others=verses.random(level==HARD?Collections.singletonList(v.book):level==MEDIUM?Arrays.asList(POPULAR):null,8,random);
        List<String> pool=new ArrayList<>();for(Verse o:others)if(!(o.book.equals(v.book)&&o.chapter==v.chapter&&o.verse==v.verse)&&o.text.length()>=20)pool.add(clip(o.text,150));
        List<String> o=options(clip(v.text,150),pool);if(o==null)return null;
        return new Question("refverse","Qual destes textos é "+v.reference()+"?",v.reference(),o,clip(v.text,150),v.reference(),v.reference()+" · "+group(v.book));
    }
    Question story(){
        List<String[]> pool=new ArrayList<>();for(String[] s:STORIES)if(Integer.parseInt(s[0])==level)pool.add(s);
        if(pool.isEmpty())return null;String[] s=pick(pool);
        List<String> wrong=distractors(s[1]);for(String x:s[3].split(","))wrong.remove(x.trim());
        if(level==HARD&&random.nextBoolean())return new Question("free","Escreva ou diga o livro desta história",s[2],Collections.<String>emptyList(),s[1],"",display(s[1])+" · "+group(s[1]));
        List<String> o=options(display(s[1]),displayed(wrong));if(o==null)return null;
        return new Question("story","Em que livro está esta história?",s[2],o,display(s[1]),"",display(s[1])+" · "+group(s[1]));
    }
    Question testament(){
        String b=pick(Arrays.asList(POPULAR));String right=BibleReferences.newTestament(b)?"Novo Testamento":"Antigo Testamento";
        List<String> o=new ArrayList<>(Arrays.asList("Antigo Testamento","Novo Testamento"));
        return new Question("testament","Em que parte da Bíblia está este livro?",display(b),o,right,"",display(b)+" · "+group(b));
    }
    Question fact(){
        List<String[]> pool=new ArrayList<>();for(String[] f:FACTS)if(level==EASY?f[0].equals("0"):!f[0].equals("0"))pool.add(f);
        String[] f=pick(pool);List<String> o=options(f[2],Arrays.asList(f).subList(3,f.length));if(o==null)return null;
        return new Question("fact",f[1],"",o,f[2],"",f[2]);
    }
    Question neighbour(){
        List<String> all=books();int at=1+random.nextInt(all.size()-2);boolean after=random.nextBoolean();
        String b=all.get(at),right=all.get(after?at+1:at-1);
        List<String> pool=new ArrayList<>();for(int d=-3;d<=3;d++){int i=at+d;if(i>=0&&i<all.size()&&i!=at)pool.add(all.get(i));}
        List<String> o=options(display(right),displayed(pool));if(o==null)return null;
        return new Question("order",after?"Que livro vem logo a seguir?":"Que livro vem logo antes?",display(b),o,display(right),"",(after?display(b)+" → "+display(right):display(right)+" → "+display(b)));
    }
    Question groupQuestion(){
        String b=pick(books());String right=group(b);List<String> pool=new ArrayList<>();for(String[] g:GROUPS)pool.add(g[0]);
        List<String> o=options(right,pool);if(o==null)return null;
        return new Question("group","A que grupo de livros pertence?",display(b),o,right,"",display(b)+" · "+right);
    }
    Question authorQuestion(){
        String[] a=pick(Arrays.asList(AUTHORS));String b=a[1+random.nextInt(a.length-1)];
        List<String> o=options(a[0],Arrays.asList(PEOPLE));if(o==null)return null;
        return new Question("author","Segundo a tradição, quem escreveu este livro?",display(b),o,a[0],"",display(b)+" · "+a[0]);
    }
    Question chapterCount(){
        String b=pick(Arrays.asList(POPULAR));int n=BibleReferences.chapters(b);if(n<3)return null;
        Set<String> pool=new LinkedHashSet<>();int[] steps={1,2,3,4,5,6,8,10};
        while(pool.size()<6){int d=steps[random.nextInt(steps.length)]*(random.nextBoolean()?1:-1);int v=n+d;if(v>0&&v!=n)pool.add(""+v);}
        List<String> o=options(""+n,new ArrayList<>(pool));if(o==null)return null;
        return new Question("chapters","Quantos capítulos tem este livro?",display(b),o,""+n,"",display(b)+" · "+n+" capítulos");
    }
    Question firstInOrder(){
        List<String> all=books();List<String> four=new ArrayList<>();int base=random.nextInt(all.size()-12);
        while(four.size()<4){String b=all.get(base+random.nextInt(12));if(!four.contains(b))four.add(b);}
        String right=four.get(0);for(String b:four)if(all.indexOf(b)<all.indexOf(right))right=b;
        List<String> o=displayed(four);Collections.shuffle(o,random);
        return new Question("first","Qual destes livros aparece primeiro na Bíblia?","",o,display(right),"",display(right));
    }

    /** Points for one answer: level base, time bonus and a streak bonus; help halves it. */
    static int points(int level,boolean correct,int secondsLeft,int streak,boolean helped){
        if(!correct)return 0;int total=POINTS[level]+Math.max(0,secondsLeft)*(level+1)*2;
        if(streak>=3)total=Math.round(total*1.2f);if(helped)total/=2;return total;
    }
    static int stars(int correct){return correct>=9?3:correct>=7?2:correct>=5?1:0;}
    /** Two wrong options to hide with the 50/50 help. */
    List<String> fifty(Question q){List<String> wrong=new ArrayList<>(q.options);wrong.remove(q.answer);Collections.shuffle(wrong,random);return wrong.subList(0,Math.min(2,wrong.size()));}
}
