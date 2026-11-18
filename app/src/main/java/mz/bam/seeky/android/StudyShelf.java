package mz.bam.seeky.android;

import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A bounded bookshelf: twelve materials per page, with covers or compact rows. */
final class StudyShelf {
    final StudyHub hub;
    final MainActivity a;
    final StudyUi ui;
    final boolean covers;
    StudyShelf(StudyHub hub){this.hub=hub;a=hub.a;ui=new StudyUi(a);covers=hub.prefs.getBoolean("library_covers",true);}
    void show(){
        LinearLayout controls=a.row();TextView count=a.label(hub.totals[0]+" "+AppLanguage.text(a,"materiais"),12,a.muted);controls.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        TextView mode=a.button(covers?"Lista":"Capas",()->{hub.prefs.edit().putBoolean("library_covers",!covers).apply();hub.draw();});UiBox.style(mode);controls.addView(mode,new LinearLayout.LayoutParams(a.dp(84),a.dp(44)));
        controls.addView(a.iconButton("add","Importar materiais",hub::pickMaterials),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));hub.content.addView(controls);a.gap(hub.content,12);
        List<StudyStore.Row> materials=hub.rows("materials");
        if(materials.isEmpty()){if(!hub.loading)hub.empty("A sua biblioteca começa aqui","Importe ficheiros para estudar offline.","Importar materiais",hub::pickMaterials);return;}
        if(covers){int columns=a.getResources().getConfiguration().screenWidthDp>=650?3:2;for(int start=0;start<materials.size();start+=columns){LinearLayout row=a.row();row.setGravity(Gravity.TOP);for(int n=0;n<columns;n++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(n<columns-1)lp.rightMargin=a.dp(10);if(start+n<materials.size())row.addView(tile(materials.get(start+n)),lp);else row.addView(new View(a),lp);}hub.content.addView(row);a.gap(hub.content,12);}}
        else for(StudyStore.Row material:materials){hub.content.addView(list(material));a.gap(hub.content,10);}
        int totalPages=Math.max(1,(int)((hub.totals[0]+11)/12));LinearLayout paging=a.row();
        TextView back=a.button("Anterior",()->{if(hub.libraryPage>0){hub.libraryPage--;hub.reload();}});UiBox.style(back);back.setEnabled(hub.libraryPage>0);back.setAlpha(hub.libraryPage>0?1f:.4f);paging.addView(back,new LinearLayout.LayoutParams(0,a.dp(46),1));
        TextView position=a.label((hub.libraryPage+1)+" / "+totalPages,12,a.muted);position.setGravity(Gravity.CENTER);paging.addView(position,new LinearLayout.LayoutParams(a.dp(60),a.dp(46)));
        TextView next=a.button("Seguinte",()->{if(hub.libraryPage+1<totalPages){hub.libraryPage++;hub.reload();}});UiBox.style(next);next.setEnabled(hub.libraryPage+1<totalPages);next.setAlpha(hub.libraryPage+1<totalPages?1f:.4f);paging.addView(next,new LinearLayout.LayoutParams(0,a.dp(46),1));hub.content.addView(paging);
    }
    FrameLayout cover(StudyStore.Row material){
        FrameLayout frame=new FrameLayout(a);frame.setBackground(a.shape(AppPalette.of(a).tint,9));frame.setClipToOutline(true);frame.setPadding(a.dp(7),a.dp(7),a.dp(7),a.dp(7));
        frame.addView(new Icons(a,FileVisual.symbol(material.s("name"),material.s("mime"),false),a.accent),new FrameLayout.LayoutParams(a.dp(38),a.dp(48),Gravity.CENTER));
        ImageView image=new ImageView(a);image.setScaleType(MediaFormats.kind(material.s("name"),material.s("mime")).equals("image")?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Catalog.Entry entry=new Catalog.Entry();entry.uri=StudyFileProvider.uri(a,material.id()).toString();entry.name=material.s("name");entry.mime=material.s("mime");entry.modified=material.n("updated");entry.size=material.n("bytes");
        if(FileVisual.preview(entry.name,entry.mime))a.thumbnails.bind(image,entry);else image.setVisibility(View.GONE);
        BookProgressBadge badge=new BookProgressBadge(a,(int)material.n("progress"));frame.addView(badge,new FrameLayout.LayoutParams(a.dp(34),a.dp(34),Gravity.BOTTOM|Gravity.END));
        FrameLayout menu=new FrameLayout(a);menu.setBackground(a.shape(AppPalette.of(a).surface,10));menu.addView(new Icons(a,"settings",a.accent),new FrameLayout.LayoutParams(a.dp(18),a.dp(18),Gravity.CENTER));menu.setContentDescription(AppLanguage.text(a,"Detalhes do material"));menu.setOnClickListener(v->hub.materialMenu(material));frame.addView(menu,new FrameLayout.LayoutParams(a.dp(40),a.dp(40),Gravity.TOP|Gravity.END));
        frame.setOnClickListener(v->hub.openMaterial(material,true,-1));frame.setOnLongClickListener(v->{hub.materialMenu(material);return true;});return frame;
    }
    boolean canSpeak(StudyStore.Row material){return Arrays.asList("pdf","epub","docx","text").contains(MediaFormats.kind(material.s("name"),material.s("mime")));}
    LinearLayout tile(StudyStore.Row material){
        LinearLayout card=ui.card();card.setPadding(a.dp(10),a.dp(10),a.dp(10),a.dp(9));card.addView(cover(material),new LinearLayout.LayoutParams(-1,a.dp(154)));a.gap(card,9);
        TextView title=ui.raw(material.s("title"),14,a.text);title.setTypeface(null,Typeface.BOLD);title.setMinLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title);
        TextView type=ui.raw(MediaFormats.ext(material.s("name")).toUpperCase(Locale.ROOT)+" · "+material.n("progress")+"%",10,a.muted);type.setMaxLines(1);card.addView(type);ui.progress(card,(int)material.n("progress"));
        LinearLayout actions=a.row();TextView open=a.button("Estudar",()->hub.openMaterial(material,true,-1));open.setTextSize(12);actions.addView(open,new LinearLayout.LayoutParams(0,a.dp(44),1));
        actions.addView(a.iconButton(canSpeak(material)?"speaker":"settings",canSpeak(material)?"Ouvir material":"Detalhes do material",()->{if(canSpeak(material))hub.openMaterial(material,true,-1,true);else hub.materialMenu(material);}),new LinearLayout.LayoutParams(a.dp(40),a.dp(44)));card.addView(actions);
        card.setOnLongClickListener(v->{hub.materialMenu(material);return true;});return card;
    }
    LinearLayout list(StudyStore.Row material){
        LinearLayout card=ui.card();LinearLayout row=a.row();row.addView(cover(material),new LinearLayout.LayoutParams(a.dp(62),a.dp(86)));LinearLayout caption=a.column();caption.setPadding(a.dp(12),0,a.dp(8),0);
        TextView title=ui.raw(material.s("title"),15,a.text);title.setTypeface(null,Typeface.BOLD);caption.addView(title);caption.addView(ui.raw(hub.subjectName(material.n("subject")),11,a.muted));caption.addView(ui.raw(MediaFormats.ext(material.s("name")).toUpperCase(Locale.ROOT)+" · "+Rules.bytes(material.n("bytes")),10,a.muted));row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(a.iconButton("settings","Detalhes do material",()->hub.materialMenu(material)),new LinearLayout.LayoutParams(a.dp(40),a.dp(44)));card.addView(row);ui.progress(card,(int)material.n("progress"));ui.actions(card,"Estudar",()->hub.openMaterial(material,true,-1),canSpeak(material)?"Ouvir":"Detalhes",()->{if(canSpeak(material))hub.openMaterial(material,true,-1,true);else hub.materialMenu(material);});return card;
    }
}
