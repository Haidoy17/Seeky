package mz.bam.seeky.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * 5.60 · The passage window opened from the Flash (a floating bubble cannot show windows itself): the passage found
 * from a phrase, with its options, "Capítulo inteiro" for the whole chapter with the verse marked. Closes with it.
 */
public final class PassageActivity extends Activity {
    static Intent intent(Context c, String reference, String words, boolean ai) {
        Intent i = new Intent(c, PassageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (reference != null) i.putExtra("ref", reference); if (words != null) i.putExtra("find", words); return i.putExtra("ai", ai);
    }
    @Override protected void attachBaseContext(Context c) { super.attachBaseContext(AppLanguage.wrap(c)); }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Runnable done = () -> { if (!isFinishing()) finish(); };
        String ref = getIntent().getStringExtra("ref"), words = getIntent().getStringExtra("find");
        if (ref != null) { BibleReferences.Reference r = BibleReferences.lookup(ref); if (r == null) { finish(); return; } BibleFind.open(this, r, done); }
        else if (words != null) BibleFind.find(this, words, getIntent().getBooleanExtra("ai", false), done);
        else finish();
    }
    @Override protected void onDestroy() { UiDialog.dismissAll(this); super.onDestroy(); }
}
