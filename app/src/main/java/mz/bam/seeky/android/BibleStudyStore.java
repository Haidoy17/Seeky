package mz.bam.seeky.android;

import android.content.*;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.*;
import java.util.*;

/** Bible study records on the device: chapters read, daily activity, memory cards and game records. */
final class BibleStudyStore extends SQLiteOpenHelper {
    BibleStudyStore(Context c){super(c.getApplicationContext(),"seeky-bible-study.db",null,1);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE readings(day TEXT NOT NULL,book TEXT NOT NULL,chapter INTEGER NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(book,chapter,day))");
        d.execSQL("CREATE TABLE activity(day TEXT NOT NULL,kind TEXT NOT NULL,amount INTEGER NOT NULL,PRIMARY KEY(day,kind))");
        d.execSQL("CREATE TABLE cards(id INTEGER PRIMARY KEY,title TEXT NOT NULL,book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,last_verse INTEGER NOT NULL,text TEXT NOT NULL,version TEXT NOT NULL DEFAULT '',interval INTEGER NOT NULL DEFAULT 0,ease INTEGER NOT NULL DEFAULT 250,due INTEGER NOT NULL,reps INTEGER NOT NULL DEFAULT 0,lapses INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE records(id INTEGER PRIMARY KEY,name TEXT NOT NULL,level INTEGER NOT NULL,score INTEGER NOT NULL,correct INTEGER NOT NULL,seconds INTEGER NOT NULL,at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int o,int n){}

    static final String PREFS="bible_study";
    static String plan(Context c){return c.getSharedPreferences(PREFS,0).getString("plan","year");}
    static void plan(Context c,String id){c.getSharedPreferences(PREFS,0).edit().putString("plan",id).apply();}

    // Reading log and activity --------------------------------------------------------------
    void count(String kind,int amount,long now){
        String day=BibleStudyCore.day(now);
        // Android 8 ships SQLite without UPSERT: update, then insert the first row of the day.
        SQLiteDatabase db=getWritableDatabase();
        SQLiteStatement update=db.compileStatement("UPDATE activity SET amount=amount+? WHERE day=? AND kind=?");update.bindLong(1,amount);update.bindString(2,day);update.bindString(3,kind);
        if(update.executeUpdateDelete()==0){ContentValues v=new ContentValues();v.put("day",day);v.put("kind",kind);v.put("amount",amount);db.insertWithOnConflict("activity",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    }
    /** Marks a chapter as read today; reading it again on another day counts again for that day. */
    boolean read(String book,int chapter,long now){
        String day=BibleStudyCore.day(now);
        ContentValues v=new ContentValues();v.put("day",day);v.put("book",book);v.put("chapter",chapter);v.put("at",now);
        if(getWritableDatabase().insertWithOnConflict("readings",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0)return false;
        count("chapter",1,now);return true;
    }
    void unread(String book,int chapter,long now){
        if(getWritableDatabase().delete("readings","book=? AND chapter=? AND day=?",new String[]{book,""+chapter,BibleStudyCore.day(now)})>0)count("chapter",-1,now);
    }
    Set<String> readChapters(){Set<String> out=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT book,chapter FROM readings",null)){while(c.moveToNext())out.add(c.getString(0)+" "+c.getInt(1));}return out;}
    Set<String> readOn(String day){Set<String> out=new LinkedHashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT book,chapter FROM readings WHERE day=? ORDER BY at",new String[]{day})){while(c.moveToNext())out.add(c.getString(0)+" "+c.getInt(1));}return out;}
    int amount(String day,String kind){return (int)DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT IFNULL(SUM(amount),0) FROM activity WHERE day=? AND kind=?",new String[]{day,kind});}
    Set<String> activeDays(){Set<String> out=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT day FROM activity WHERE amount>0 GROUP BY day",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}

    // Memory cards -----------------------------------------------------------------------------
    static final class Card {
        long id;String title,book,text,version;int chapter,verse,lastVerse,interval,ease,reps,lapses;long due,created;
        boolean memorised(){return BibleStudyCore.memorised(interval);}
    }
    static Card card(Cursor c){Card k=new Card();k.id=c.getLong(0);k.title=c.getString(1);k.book=c.getString(2);k.chapter=c.getInt(3);k.verse=c.getInt(4);k.lastVerse=c.getInt(5);k.text=c.getString(6);k.version=c.getString(7);k.interval=c.getInt(8);k.ease=c.getInt(9);k.due=c.getLong(10);k.reps=c.getInt(11);k.lapses=c.getInt(12);k.created=c.getLong(13);return k;}
    static final String CARD="SELECT id,title,book,chapter,verse,last_verse,text,version,interval,ease,due,reps,lapses,created FROM cards";
    /** Adds a passage to memorise (once per reference); returns false when it was already there. */
    boolean addCard(BibleReferences.Reference ref,String text,String version,long now){
        if(DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM cards WHERE title=?",new String[]{ref.title})>0)return false;
        ContentValues v=new ContentValues();v.put("title",ref.title);v.put("book",ref.book);v.put("chapter",ref.chapter);v.put("verse",Math.max(1,ref.verse));v.put("last_verse",Math.max(ref.verse,ref.lastVerse));
        v.put("text",text.trim());v.put("version",version==null?"":version);v.put("due",now);v.put("created",now);
        return getWritableDatabase().insert("cards",null,v)>0;
    }
    List<Card> cards(){List<Card> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(CARD+" ORDER BY due",null)){while(c.moveToNext())out.add(card(c));}return out;}
    List<Card> due(long now){List<Card> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(CARD+" WHERE due<=? ORDER BY due LIMIT 50",new String[]{""+now})){while(c.moveToNext())out.add(card(c));}return out;}
    void review(Card k,int grade,long now){
        long[] next=BibleStudyCore.schedule(k.interval,k.ease,grade,now);
        ContentValues v=new ContentValues();v.put("interval",next[0]);v.put("ease",next[1]);v.put("due",next[2]);v.put("reps",k.reps+1);v.put("lapses",k.lapses+(grade==0?1:0));
        getWritableDatabase().update("cards",v,"id=?",new String[]{""+k.id});
        k.interval=(int)next[0];k.ease=(int)next[1];k.due=next[2];k.reps++;
        count("card",1,now);
    }
    void removeCard(long id){getWritableDatabase().delete("cards","id=?",new String[]{""+id});}

    // Game records ------------------------------------------------------------------------------
    static final class Record {String name;int level,score,correct,seconds;long at;long id;}
    long record(String name,int level,int score,int correct,int seconds,long now){
        ContentValues v=new ContentValues();v.put("name",name);v.put("level",level);v.put("score",score);v.put("correct",correct);v.put("seconds",seconds);v.put("at",now);
        return getWritableDatabase().insert("records",null,v);
    }
    List<Record> records(int level,int limit){
        List<Record> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,level,score,correct,seconds,at FROM records WHERE level=? ORDER BY score DESC,seconds ASC,at ASC LIMIT ?",new String[]{""+level,""+limit})){
            while(c.moveToNext()){Record r=new Record();r.id=c.getLong(0);r.name=c.getString(1);r.level=c.getInt(2);r.score=c.getInt(3);r.correct=c.getInt(4);r.seconds=c.getInt(5);r.at=c.getLong(6);out.add(r);}
        }
        return out;
    }
    /** Position (1-based) a score would take in the level's table. */
    int rank(int level,int score){return 1+(int)DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM records WHERE level=? AND score>?",new String[]{""+level,""+score});}

    /** Verses for the quiz and the memory cards from the imported Bible the reader prefers. */
    static final class Verses implements BibleGame.Verses {
        final Context c;long source;
        Verses(Context c){
            this.c=c.getApplicationContext();long wanted=c.getSharedPreferences("seeky",0).getLong("bible_quick_source",-1);
            try(PlusStore db=new PlusStore(c)){
                List<StudyStore.Row> sources=db.rows("bible_sources",null,null,"title COLLATE NOCASE",200);
                for(StudyStore.Row s:sources)if(s.id()==wanted)source=wanted;
                if(source==0&&!sources.isEmpty())source=sources.get(0).id();
            }
        }
        public boolean available(){return source>0;}
        public List<BibleGame.Verse> random(List<String> books,int count,Random random){
            List<BibleGame.Verse> out=new ArrayList<>();if(source<=0)return out;
            StringBuilder where=new StringBuilder("source=? AND length(value) BETWEEN 30 AND 280");List<String> args=new ArrayList<>();args.add(""+source);
            if(books!=null&&!books.isEmpty()){where.append(" AND book IN (");for(int i=0;i<books.size();i++){where.append(i==0?"?":",?");args.add(books.get(i));}where.append(')');}
            try(PlusStore db=new PlusStore(c)){
                long total=DatabaseUtils.longForQuery(db.getReadableDatabase(),"SELECT COUNT(*) FROM bible_verses WHERE "+where,args.toArray(new String[0]));
                for(int i=0;i<count&&total>0;i++){
                    List<String> a=new ArrayList<>(args);a.add(""+(long)(random.nextDouble()*total));
                    try(Cursor q=db.getReadableDatabase().rawQuery("SELECT book,chapter,verse,value FROM bible_verses WHERE "+where+" LIMIT 1 OFFSET ?",a.toArray(new String[0]))){if(q.moveToFirst())out.add(new BibleGame.Verse(q.getString(0),q.getInt(1),q.getInt(2),q.getString(3)));}
                }
            }
            return out;
        }
        public BibleGame.Verse get(String book,int chapter,int verse){
            if(source<=0)return null;
            try(PlusStore db=new PlusStore(c);Cursor q=db.getReadableDatabase().rawQuery("SELECT value FROM bible_verses WHERE source=? AND book=? AND chapter=? AND verse=?",new String[]{""+source,book,""+chapter,""+verse})){return q.moveToFirst()?new BibleGame.Verse(book,chapter,verse,q.getString(0)):null;}
        }
        /** Text of a reference (several verses joined), or "" when the Bible lacks it. */
        String text(BibleReferences.Reference ref){
            if(source<=0||ref==null)return "";StringBuilder out=new StringBuilder();
            try(PlusStore db=new PlusStore(c)){for(StudyStore.Row r:BibleLinks.Passage.verses(db,source,ref.book,ref.ranges)){if(out.length()>0)out.append(' ');out.append(r.s("value").trim());if(out.length()>4000)break;}}
            return out.toString();
        }
        String version(){if(source<=0)return "";try(PlusStore db=new PlusStore(c)){for(StudyStore.Row s:db.rows("bible_sources","id=?",new String[]{""+source},null,1))return s.s("title");}return "";}
    }
}
