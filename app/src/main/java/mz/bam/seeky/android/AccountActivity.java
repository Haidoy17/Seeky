package mz.bam.seeky.android;
import android.app.Activity;import android.os.Bundle;
/** Legacy internal destination now opens the local backup tools. Social login was removed. */
public final class AccountActivity extends Activity {
 public void onCreate(Bundle state){super.onCreate(state);ToolsActivity.open(this,"backup");finish();}
}
