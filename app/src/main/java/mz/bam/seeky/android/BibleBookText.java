package mz.bam.seeky.android;
import org.json.*;
/** Persistable checkpoints for the same parser used by plain TXT. */
final class BibleBookText extends BiblePlainText {
 BibleBookText(BibleText.Sink sink,JSONObject saved){super(sink);book=saved.optString("book");chapter=saved.optInt("chapter");verse=saved.optInt("verse");count=saved.optInt("count");candidate=saved.optInt("candidate");pending=saved.optString("pending");}
 JSONObject state()throws JSONException{return new JSONObject().put("book",book).put("chapter",chapter).put("verse",verse).put("count",count).put("pending",pending).put("candidate",candidate);}
}
