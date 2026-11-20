package mz.bam.seeky.android;

import java.util.*;

/** 5.29: book names typed or spoken, reading plans, memory cards and the Bible quiz rounds. */
public final class BibleStudy529Test {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    /** A small imported Bible: a few verses in every Protestant book. */
    static final class Fake implements BibleGame.Verses {
        final List<BibleGame.Verse> all=new ArrayList<>();
        Fake(){for(String b:BibleReferences.books(false))for(int c=1;c<=Math.min(3,BibleReferences.chapters(b));c++)for(int v=1;v<=4;v++)all.add(new BibleGame.Verse(b,c,v,"Texto de exemplo de "+b+" "+c+":"+v+" para o jogo da Bíblia."));
            all.add(new BibleGame.Verse("João",3,16,"Porque Deus amou o mundo de tal maneira que deu o seu Filho unigénito."));}
        public boolean available(){return true;}
        public List<BibleGame.Verse> random(List<String> books,int count,Random r){List<BibleGame.Verse> pool=new ArrayList<>();for(BibleGame.Verse v:all)if(books==null||books.contains(v.book))pool.add(v);Collections.shuffle(pool,r);return pool.subList(0,Math.min(count,pool.size()));}
        public BibleGame.Verse get(String book,int chapter,int verse){for(BibleGame.Verse v:all)if(v.book.equals(book)&&v.chapter==chapter&&v.verse==verse)return v;return new BibleGame.Verse(book,chapter,verse,"Versículo famoso de "+book+" "+chapter+":"+verse+" para memorizar.");}
    }
    public static void main(String[] args){
        // Book names as people write or say them.
        check(BibleReferences.bookNamed("Isaías").equals("Isaías"),"plain name");
        check(BibleReferences.bookNamed("isaias").equals("Isaías"),"without accent");
        check(BibleReferences.bookNamed("primeira carta aos coríntios").equals("Primeira Coríntios"),"spoken epistle");
        check(BibleReferences.bookNamed("1 Corintios").equals("Primeira Coríntios"),"typed numbered");
        check(BibleReferences.bookNamed("o evangelho segundo João").equals("João"),"gospel phrase");
        check(BibleReferences.bookNamed("Evangelho de Mateus").equals("Mateus"),"gospel of");
        check(BibleReferences.bookNamed("Atos").equals("Actos")&&BibleReferences.bookNamed("Salmo").equals("Salmos"),"variants");
        check(BibleReferences.bookNamed("Livro de Jó").equals("Jó"),"book of Job");
        check(BibleReferences.bookNamed("Isaías 53").equals("Isaías"),"name with chapter");
        check(BibleReferences.bookNamed("banana").isEmpty()&&BibleReferences.bookNamed("").isEmpty(),"not a book");
        // Ordered books and Protestant chapter counts.
        List<String> books=BibleReferences.books(false);
        check(books.size()==66&&books.get(0).equals("Génesis")&&books.get(65).equals("Apocalipse")&&books.indexOf("Mateus")==39,"66 books in order");
        check(BibleReferences.books(true).size()==73,"73 with deuterocanonical books");
        check(BibleReferences.chapters("Salmos")==150&&BibleReferences.chapters("Daniel")==12&&BibleReferences.chapters("Judas")==1&&BibleReferences.chapters("Génesis")==50,"chapter counts");
        check(BibleReferences.newTestament("Romanos")&&!BibleReferences.newTestament("Malaquias"),"testaments");
        // Reading plans.
        check(BibleStudyCore.chapters("year").size()==1189,"whole Bible has 1189 chapters");
        check(BibleStudyCore.chapters("nt").size()==260&&BibleStudyCore.chapters("gospels").size()==89&&BibleStudyCore.chapters("wisdom").size()==181,"plan sizes");
        check(BibleStudyCore.daily("year")==4&&BibleStudyCore.daily("gospels")==3&&BibleStudyCore.daily("free")==1,"daily portions");
        Set<String> read=new HashSet<>(Arrays.asList("Génesis 1","Génesis 2"));
        List<String[]> next=BibleStudyCore.next("year",read,3);
        check(next.get(0)[0].equals("Génesis")&&next.get(0)[1].equals("3")&&next.size()==3,"next unread chapters");
        check(BibleStudyCore.progress("year",read)==2,"plan progress");
        // Week and streak.
        Calendar c=Calendar.getInstance();c.set(2026,Calendar.NOVEMBER,20,12,0,0);long now=c.getTimeInMillis();
        List<String> week=BibleStudyCore.week(now);
        check(week.get(0).equals("2026-11-16")&&week.get(6).equals("2026-11-22"),"Monday to Sunday: "+week);
        check(BibleStudyCore.streak(new HashSet<>(Arrays.asList("2026-11-20","2026-11-19","2026-11-18","2026-11-16")),now)==3,"streak");
        check(BibleStudyCore.streak(new HashSet<>(Arrays.asList("2026-11-19","2026-11-18")),now)==2,"streak continues until today ends");
        // Recalling a verse.
        String verse="Porque Deus amou o mundo de tal maneira que deu o seu Filho unigénito";
        check(BibleStudyCore.similarity(verse,verse)==100,"exact recall");
        check(BibleStudyCore.similarity(verse,"porque deus amou o mundo de tal maneira que deu o seu filho unigenito")==100,"case and accents are ignored");
        int partial=BibleStudyCore.similarity(verse,"Deus amou o mundo e deu o seu Filho");
        check(partial>40&&partial<80,"partial recall: "+partial);
        check(BibleStudyCore.similarity(verse,"")==0,"nothing recalled");
        boolean[] hit=BibleStudyCore.recalled(verse,"Deus amou o mundo");check(!hit[0]&&hit[1]&&hit[2],"missing words are marked");
        check(BibleStudyCore.grade(100)==3&&BibleStudyCore.grade(75)==2&&BibleStudyCore.grade(50)==1&&BibleStudyCore.grade(10)==0,"suggested grades");
        check(BibleStudyCore.initials("Porque Deus amou").equals("P… D… a…"),"initials hint: "+BibleStudyCore.initials("Porque Deus amou"));
        long[] s=BibleStudyCore.schedule(0,250,2,0);check(s[0]==1&&s[2]==86400000L,"good first review waits a day");
        s=BibleStudyCore.schedule(10,250,2,0);check(s[0]==25,"interval grows with ease");
        s=BibleStudyCore.schedule(10,250,0,0);check(s[0]==0&&s[2]==600000L&&s[1]==230,"again in ten minutes");
        check(BibleStudyCore.memorised(21)&&!BibleStudyCore.memorised(20),"memorised after three weeks");
        // Quiz rounds.
        for(int level=0;level<3;level++)for(int seed=0;seed<40;seed++){
            for(BibleGame.Verses source:new BibleGame.Verses[]{new Fake(),null}){
                List<BibleGame.Question> round=new BibleGame(level,new Random(seed),source).round();
                check(round.size()==BibleGame.ROUND,"ten questions level "+level+" seed "+seed+" bible "+(source!=null)+": "+round.size());
                Set<String> seen=new HashSet<>();
                for(BibleGame.Question q:round){
                    check(seen.add(q.prompt+"|"+q.body+"|"+q.answer),"no repeated question");
                    if(q.free()){check(!q.answer.isEmpty()&&q.correct(BibleGame.display(q.answer))&&!q.correct("banana"),"free answer accepts the book: "+q.answer);}
                    else{check(q.options.contains(q.answer),"answer is offered: "+q.prompt+" "+q.options+" "+q.answer);check(new HashSet<>(q.options).size()==q.options.size(),"options are distinct: "+q.options);check(q.options.size()==4||q.kind.equals("testament")&&q.options.size()==2,"four options: "+q.options);}
                }
            }
        }
        List<BibleGame.Question> hard=new BibleGame(BibleGame.HARD,new Random(7),new Fake()).round();
        Set<String> kinds=new HashSet<>();for(BibleGame.Question q:hard)kinds.add(q.kind);
        check(kinds.contains("free")&&kinds.contains("chapter"),"hard level asks to write the book and the chapter: "+kinds);
        List<BibleGame.Question> easy=new BibleGame(BibleGame.EASY,new Random(3),new Fake()).round();
        int verses=0;for(BibleGame.Question q:easy)if(q.kind.equals("verse"))verses++;check(verses==4,"easy level uses famous verses");
        BibleGame.Question q=new BibleGame.Question("free","","",Collections.<String>emptyList(),"Primeira Coríntios","","");
        check(q.correct("primeira coríntios")&&q.correct("1 Co")&&!q.correct("Coríntios 2"),"spoken numbered book");
        check(BibleGame.points(BibleGame.EASY,true,20,1,false)==140&&BibleGame.points(BibleGame.HARD,true,10,3,false)==312&&BibleGame.points(BibleGame.HARD,false,10,3,false)==0,"points");
        check(BibleGame.points(BibleGame.MEDIUM,true,0,0,true)==75,"help halves the points");
        check(BibleGame.stars(10)==3&&BibleGame.stars(7)==2&&BibleGame.stars(5)==1&&BibleGame.stars(4)==0,"stars");
        check(BibleGame.display("Primeira Coríntios").equals("1 Coríntios")&&BibleGame.display("Terceira João").equals("3 João"),"display names");
        System.out.println("PASS "+checks+" Bible study and quiz checks (5.29)");
    }
}
