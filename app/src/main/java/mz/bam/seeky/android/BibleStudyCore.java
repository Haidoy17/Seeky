package mz.bam.seeky.android;

import java.text.Normalizer;
import java.util.*;

/** Platform-free rules of the Bible study: reading plans, memory-card scheduling, answer checking and streaks. */
final class BibleStudyCore {
    /** {id, name, days, description}. */
    static final String[][] PLANS={
        {"year","Bíblia em 1 ano","365","Toda a Bíblia, de Génesis a Apocalipse"},
        {"nt","Novo Testamento em 3 meses","90","De Mateus a Apocalipse"},
        {"gospels","Evangelhos em 30 dias","30","Mateus, Marcos, Lucas e João"},
        {"wisdom","Salmos e Provérbios em 2 meses","60","Oração e sabedoria para cada dia"},
        {"free","Um capítulo por dia","0","Toda a Bíblia ao seu ritmo"}};
    static String[] plan(String id){for(String[] p:PLANS)if(p[0].equals(id))return p;return PLANS[0];}
    /** Chapters of a plan in reading order, as {book, chapter}. */
    static List<String[]> chapters(String id){
        List<String> books=BibleReferences.books(false),chosen=new ArrayList<>();
        if(id.equals("nt"))chosen=books.subList(books.indexOf("Mateus"),books.size());
        else if(id.equals("gospels"))chosen=Arrays.asList("Mateus","Marcos","Lucas","João");
        else if(id.equals("wisdom"))chosen=Arrays.asList("Salmos","Provérbios");
        else chosen=books;
        List<String[]> out=new ArrayList<>();
        for(String b:chosen)for(int c=1;c<=BibleReferences.chapters(b);c++)out.add(new String[]{b,""+c});
        return out;
    }
    /** Chapters a day for a plan (at least one). */
    static int daily(String id){String[] p=plan(id);int days=Integer.parseInt(p[2]);if(days<=0)return 1;return Math.max(1,(int)Math.ceil(chapters(id).size()/(double)days));}
    /** The next unread chapters of a plan, at most {@code count}. */
    static List<String[]> next(String id,Set<String> read,int count){List<String[]> out=new ArrayList<>();for(String[] c:chapters(id)){if(out.size()>=count)break;if(!read.contains(c[0]+" "+c[1]))out.add(c);}return out;}
    static int progress(String id,Set<String> read){int n=0;for(String[] c:chapters(id))if(read.contains(c[0]+" "+c[1]))n++;return n;}

    /** "2026-11-20" in local time. */
    static String day(Calendar c){return String.format(Locale.ROOT,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));}
    static String day(long time){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);return day(c);}
    /** Monday to Sunday of the week that contains {@code time}. */
    static List<String> week(long time){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);int shift=(c.get(Calendar.DAY_OF_WEEK)+5)%7;c.add(Calendar.DAY_OF_MONTH,-shift);List<String> out=new ArrayList<>();for(int i=0;i<7;i++){out.add(day(c));c.add(Calendar.DAY_OF_MONTH,1);}return out;}
    /** Days in a row with study, ending today (or yesterday when today is still empty). */
    static int streak(Set<String> active,long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);if(!active.contains(day(c)))c.add(Calendar.DAY_OF_MONTH,-1);int n=0;while(active.contains(day(c))&&n<10000){n++;c.add(Calendar.DAY_OF_MONTH,-1);}return n;}

    static String plain(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]"," ").trim();}
    static List<String> words(String s){List<String> out=new ArrayList<>();for(String w:plain(s).split("\\s+"))if(!w.isEmpty())out.add(w);return out;}
    /** Which words of the verse were recalled in order (longest common subsequence of words). */
    static boolean[] recalled(String expected,String given){
        List<String> a=words(expected),b=words(given);int[][] l=new int[a.size()+1][b.size()+1];
        for(int i=a.size()-1;i>=0;i--)for(int j=b.size()-1;j>=0;j--)l[i][j]=a.get(i).equals(b.get(j))?l[i+1][j+1]+1:Math.max(l[i+1][j],l[i][j+1]);
        boolean[] hit=new boolean[a.size()];int i=0,j=0;
        while(i<a.size()&&j<b.size()){if(a.get(i).equals(b.get(j))){hit[i]=true;i++;j++;}else if(l[i+1][j]>=l[i][j+1])i++;else j++;}
        return hit;
    }
    /** 0..100: share of the verse recalled, minus a little for extra words. */
    static int similarity(String expected,String given){
        List<String> a=words(expected),b=words(given);if(a.isEmpty())return 0;
        int hits=0;for(boolean h:recalled(expected,given))if(h)hits++;
        int extra=Math.max(0,b.size()-hits);
        return Math.max(0,Math.min(100,Math.round(100f*hits/a.size()-Math.min(15f,100f*extra/(a.size()*4f)))));
    }
    /** Suggested grade from the similarity: 0 Errei, 1 Difícil, 2 Bom, 3 Fácil. */
    static int grade(int similarity){return similarity>=92?3:similarity>=70?2:similarity>=40?1:0;}
    /** First letters of each word: a gentle hint for recalling a verse. */
    static String initials(String text){StringBuilder out=new StringBuilder();for(String w:text.trim().split("\\s+")){if(w.isEmpty())continue;int i=0;while(i<w.length()&&!Character.isLetterOrDigit(w.charAt(i)))i++;if(out.length()>0)out.append(' ');out.append(i<w.length()?w.substring(0,i+1):w).append(w.length()>i+1?"…":"");}return out.toString();}

    /** Card schedule after a grade: {interval days, ease×100, due time}. Interval 0 means again in ten minutes. */
    static long[] schedule(int interval,int ease,int grade,long now){
        int e=Math.max(130,ease<=0?250:ease);int next;
        if(grade<=0){next=0;e=Math.max(130,e-20);}
        else if(grade==1){next=Math.max(1,Math.round(interval*1.2f));e=Math.max(130,e-15);}
        else if(grade==2){next=interval<=0?1:interval==1?3:Math.round(interval*e/100f);}
        else{next=interval<=0?3:Math.round(interval*e/100f*1.3f);e+=15;}
        next=Math.min(next,3650);
        long due=next==0?now+10*60*1000L:now+next*86400000L;
        return new long[]{next,e,due};
    }
    /** A card counts as memorised once it waits three weeks between reviews. */
    static boolean memorised(int interval){return interval>=21;}
    static String wait(int interval){return interval<=0?"10 min":interval==1?"1 dia":interval+" dias";}
    private BibleStudyCore(){}
}
