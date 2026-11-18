package mz.bam.seeky.android;
import android.graphics.drawable.GradientDrawable;import android.widget.TextView;import android.view.Gravity;
final class UiBox {
 static void style(TextView view){AppPalette p=AppPalette.of(view.getContext());float d=view.getResources().getDisplayMetrics().density;GradientDrawable box=new GradientDrawable();box.setColor(p.surface);box.setCornerRadius(12*d);box.setStroke(Math.max(1,(int)d),p.line);view.setBackground(box);view.setGravity(Gravity.CENTER);view.setMinHeight((int)(46*d));view.setPadding((int)(12*d),(int)(10*d),(int)(12*d),(int)(10*d));view.setTextColor(p.accent);view.setMaxLines(2);}
}
