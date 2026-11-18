package mz.bam.seeky.android;

import java.util.*;

/** Library predicates are allowlisted; user search is always bound as data. */
final class BookQuery {
    static final int PAGE=12;
    static final String RAW_FORMATS="(lower(name) LIKE '%.pdf' OR lower(name) LIKE '%.epub' OR lower(name) LIKE '%.docx' OR lower(name) LIKE '%.txt' OR lower(name) LIKE '%.md' OR lower(name) LIKE '%.htm' OR lower(name) LIKE '%.html' OR mime IN ('application/pdf','application/epub+zip','application/vnd.openxmlformats-officedocument.wordprocessingml.document','text/plain','text/html'))";
    static final String FORMATS=RAW_FORMATS+" AND id=(SELECT shared.id FROM materials shared WHERE shared.hash=materials.hash AND shared.path=materials.path AND "+RAW_FORMATS.replace("lower(name)","lower(shared.name)").replace(" OR mime IN"," OR shared.mime IN")+" ORDER BY shared.progress DESC,shared.updated DESC,shared.id LIMIT 1)";
    final String where,order;final String[] args;
    BookQuery(String query,int mask,int type,int sort,boolean favorites,Set<String> hashes){
        StringBuilder sql=new StringBuilder(FORMATS);List<String> parameters=new ArrayList<>();
        if((mask&7)!=7){List<String> states=new ArrayList<>();if((mask&1)!=0)states.add("(progress<=0 AND status<>'A estudar')");if((mask&2)!=0)states.add("(progress<100 AND (progress>0 OR status='A estudar'))");if((mask&4)!=0)states.add("progress>=100");sql.append(" AND (").append(states.isEmpty()?"0=1":String.join(" OR ",states)).append(')');}
        if(type>0&&type<=4){String[] kinds={"","(lower(name) LIKE '%.pdf' OR mime='application/pdf')","(lower(name) LIKE '%.epub' OR mime='application/epub+zip')","(lower(name) LIKE '%.docx' OR mime='application/vnd.openxmlformats-officedocument.wordprocessingml.document')","(lower(name) LIKE '%.txt' OR lower(name) LIKE '%.md' OR lower(name) LIKE '%.htm' OR lower(name) LIKE '%.html' OR mime IN ('text/plain','text/html'))"};sql.append(" AND ").append(kinds[type]);}
        if(query!=null&&!query.trim().isEmpty()){sql.append(" AND (title||' '||author||' '||name) LIKE ? ESCAPE '\\'");parameters.add(StudySchedule.like(query.trim()));}
        if(favorites){List<String> valid=new ArrayList<>();for(String hash:hashes)if(hash.matches("[a-f0-9]{64}"))valid.add("'"+hash+"'");sql.append(valid.isEmpty()?" AND 0=1":" AND hash IN ("+String.join(",",valid)+")");}
        String[] orders={"title COLLATE NOCASE,id","author COLLATE NOCASE,title COLLATE NOCASE,id","created DESC,id DESC","updated DESC,id DESC","progress DESC,title COLLATE NOCASE,id"};
        where=sql.toString();args=parameters.isEmpty()?null:parameters.toArray(new String[0]);order=orders[Math.max(0,Math.min(orders.length-1,sort))];
    }
    static boolean accepts(String name,String mime){String ext=MediaFormats.ext(name);return Arrays.asList("pdf","epub","docx","txt","md","htm","html").contains(ext)||Arrays.asList("application/pdf","application/epub+zip","application/vnd.openxmlformats-officedocument.wordprocessingml.document","text/plain","text/html").contains(mime);}
}
