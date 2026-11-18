package mz.bam.seeky.android;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Reusable row: scrolling binds data instead of rebuilding the entire hierarchy. */
final class FileRowView extends LinearLayout {
    interface Favorite {void toggle(Catalog.Entry e,View target);}
    final ImageView appIcon;final TextView name,path,meta,star;final FrameLayout iconBox;final Icons icon,arrow;
    final GradientDrawable background=new GradientDrawable(),iconBackground=new GradientDrawable();
    Catalog.Entry entry;
    int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(int size){TextView v=new TextView(getContext());v.setTextSize(size);v.setSingleLine(true);v.setIncludeFontPadding(false);v.setPadding(0,dp(3),0,dp(3));return v;}
    FileRowView(Context c,Favorite callback){
        super(c);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        background.setCornerRadius(dp(17));setBackground(background);
        iconBox=new FrameLayout(c);iconBackground.setCornerRadius(dp(13));iconBox.setBackground(iconBackground);icon=new Icons(c,"document",0);iconBox.addView(icon,new FrameLayout.LayoutParams(dp(25),dp(25),Gravity.CENTER));appIcon=new ImageView(c);appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);iconBox.addView(appIcon,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER));addView(iconBox,new LayoutParams(dp(46),dp(48)));
        LinearLayout info=new LinearLayout(c);info.setOrientation(VERTICAL);info.setPadding(dp(12),0,dp(4),0);name=text(14);name.setTypeface(null,Typeface.BOLD);name.setEllipsize(TextUtils.TruncateAt.END);path=text(10);path.setEllipsize(TextUtils.TruncateAt.MIDDLE);meta=text(10);info.addView(name);info.addView(path);info.addView(meta);addView(info,new LayoutParams(0,-2,1));
        FrameLayout trailing=new FrameLayout(c);star=text(25);star.setGravity(Gravity.CENTER);star.setFocusable(false);trailing.addView(star,new FrameLayout.LayoutParams(-1,-1));arrow=new Icons(c,"arrow",0);trailing.addView(arrow,new FrameLayout.LayoutParams(dp(18),dp(22),Gravity.CENTER));addView(trailing,new LayoutParams(dp(44),dp(48)));star.setOnClickListener(v->{if(entry!=null)callback.toggle(entry,star);});resizeIcons();
    }
    void resizeIcons(){int size=IconSizing.icons(getContext()),box=new int[]{40,48,58}[size],glyph=IconSizing.glyph(size);iconBox.setLayoutParams(new LayoutParams(dp(box),dp(box)));icon.setLayoutParams(new FrameLayout.LayoutParams(dp(glyph),dp(glyph),Gravity.CENTER));int art=new int[]{28,34,42}[size];appIcon.setLayoutParams(new FrameLayout.LayoutParams(dp(art),dp(art),Gravity.CENTER));}
    void bind(Catalog.Entry e,int surface,int ink,int muted,int accent,boolean compact,String query){
        resizeIcons();entry=e;star.animate().cancel();animate().cancel();setAlpha(1);setTranslationY(0);setPadding(dp(12),dp(compact?7:12),dp(9),dp(compact?7:12));background.setColor(surface);
        int tint=e.directory?0xffba9551:e.category.equals("PDF")?0xffd56c76:e.category.equals("Imagens")?0xff9573cf:0xff6d92c5;
        iconBackground.setColor((tint&0xffffff)|0x16000000);icon.setSymbol(FileVisual.symbol(e.name,e.mime,e.directory),tint);appIcon.setTag(e.uri);appIcon.setImageDrawable(null);appIcon.setVisibility(GONE);if(!e.directory)Thumbnails.apk(appIcon,e);arrow.setSymbol("arrow",muted);
        CharSequence caption=e.name;String q=query.trim();int index=q.isEmpty()||q.indexOf('*')>=0||q.indexOf('?')>=0?-1:e.name.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if(index>=0){SpannableString hit=new SpannableString(e.name);hit.setSpan(new ForegroundColorSpan(accent),index,index+q.length(),0);hit.setSpan(new StyleSpan(Typeface.BOLD),index,index+q.length(),0);caption=hit;}
        name.setText(caption);name.setTextColor(ink);path.setText(e.path);path.setTextColor(muted);meta.setText((e.size<0?"Tamanho desconhecido":Rules.bytes(e.size))+"  ·  "+e.category);meta.setTextColor(muted);meta.setVisibility(compact||e.directory?GONE:VISIBLE);
        star.setVisibility(e.directory?GONE:VISIBLE);arrow.setVisibility(e.directory?VISIBLE:GONE);star.setTag(e.uri);star.setText(e.favorite?"★":"☆");star.setTextColor(e.favorite?accent:muted);star.setContentDescription(e.favorite?"Remover dos favoritos":"Adicionar aos favoritos");star.setScaleX(1);star.setScaleY(1);
        setContentDescription(e.name+". "+AppLanguage.text(getContext(),e.category)+". "+(e.directory?"Pasta":Rules.bytes(e.size)));
    }
}
