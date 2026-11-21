package mz.bam.seeky.android;
import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.text.*;import android.view.*;import android.widget.*;
final class ThemeSettings {
 static void apply(MainActivity a,String name){a.prefs.edit().putString("theme",name).apply();a.render();SeekyWidget.update(a);ReadingWidget.update(a);}
 /** Theme picker: miniature previews grouped as the author's palettes, modern, automatic and classic. */
 static void show(MainActivity a){
  Screens s=new Screens(a);LinearLayout root=s.column();String current=a.prefs.getString("theme",AppPalette.DEFAULT);
  final UiDialog[] dialog=new UiDialog[1];
  java.util.List<String> modern=new java.util.ArrayList<>(java.util.Arrays.asList(AppPalette.MODERN));modern.removeAll(java.util.Arrays.asList(AppPalette.PALETTES));
  java.util.List<String> classic=new java.util.ArrayList<>(java.util.Arrays.asList(AppPalette.NAMES));classic.removeAll(java.util.Arrays.asList(AppPalette.MODERN));classic.remove(AppPalette.SYSTEM);classic.remove(AppPalette.SCHEDULE);
  group(a,s,root,"Novos — das suas paletas",java.util.Arrays.asList(AppPalette.PALETTES),current,dialog);
  group(a,s,root,"Modernos",modern,current,dialog);
  group(a,s,root,"Automáticos",java.util.Arrays.asList(AppPalette.SYSTEM,AppPalette.SCHEDULE),current,dialog);
  group(a,s,root,"Clássicos",classic,current,dialog);
  root.addView(s.button("Personalizar cores…",false,()->{dialog[0].dismiss();custom(a);}),s.gap(14));
  dialog[0]=(UiDialog)a.dialog().setTitle("Cores do Seeky").setView(root).setNegativeButton("Fechar",null).create();dialog[0].show();
 }
 static void group(MainActivity a,Screens s,LinearLayout root,String title,java.util.List<String> names,String current,UiDialog[] dialog){
  TextView head=s.text(title,14,s.p.ink,true);head.setPadding(s.dp(2),s.dp(root.getChildCount()==0?0:16),0,s.dp(8));root.addView(head);
  LinearLayout row=null;
  for(int i=0;i<names.size();i++){
   if(i%2==0){row=s.row();row.setGravity(Gravity.TOP);root.addView(row,s.gap(i==0?0:10));}
   final String name=names.get(i);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i%2==0)lp.rightMargin=s.dp(10);
   row.addView(card(a,s,name,name.equals(current),()->{dialog[0].dismiss();apply(a,name);}),lp);
  }
  if(row!=null&&names.size()%2==1)row.addView(new View(a),new LinearLayout.LayoutParams(0,1,1));
 }
 /** A miniature of the theme: page, title band, a card with text lines and the accent button. */
 static View card(MainActivity a,Screens s,String name,boolean selected,Runnable pick){
  AppPalette p=new AppPalette(name.equals(AppPalette.SYSTEM)||name.equals(AppPalette.SCHEDULE)?AppPalette.SAND:name);
  AppPalette night=name.equals(AppPalette.SYSTEM)||name.equals(AppPalette.SCHEDULE)?new AppPalette(AppPalette.FOREST):null;
  LinearLayout box=s.column();box.setPadding(s.dp(6),s.dp(6),s.dp(6),s.dp(8));
  GradientDrawable frame=new GradientDrawable();frame.setCornerRadius(s.dp(16));frame.setColor(s.p.surface);frame.setStroke(s.dp(selected?3:1),selected?s.p.accent:s.p.line);box.setBackground(frame);
  LinearLayout preview=new LinearLayout(a);preview.setOrientation(LinearLayout.HORIZONTAL);
  preview.addView(mini(a,s,p),new LinearLayout.LayoutParams(0,s.dp(84),1));
  if(night!=null)preview.addView(mini(a,s,night),new LinearLayout.LayoutParams(0,s.dp(84),1));
  preview.setClipToOutline(true);GradientDrawable clip=new GradientDrawable();clip.setCornerRadius(s.dp(12));preview.setBackground(clip);
  box.addView(preview,new LinearLayout.LayoutParams(-1,s.dp(84)));
  LinearLayout label=s.row();label.setPadding(s.dp(4),s.dp(7),s.dp(2),0);
  TextView t=s.text(name,14,s.p.ink,true);t.setMaxLines(1);t.setEllipsize(TextUtils.TruncateAt.END);label.addView(t,new LinearLayout.LayoutParams(0,-2,1));
  if(selected)label.addView(new Icons(a,"check",s.p.accent),new LinearLayout.LayoutParams(s.dp(18),s.dp(18)));
  box.addView(label);
  String d=AppPalette.describe(name);if(!d.isEmpty()){TextView dt=s.text(d,13.5f,s.p.muted,false);dt.setMaxLines(2);dt.setEllipsize(TextUtils.TruncateAt.END);dt.setPadding(s.dp(4),s.dp(1),s.dp(2),0);box.addView(dt);}
  box.setContentDescription(name);box.setOnClickListener(v->pick.run());Motion.touch(box);
  return box;
 }
 static View mini(MainActivity a,Screens s,AppPalette p){
  LinearLayout page=new LinearLayout(a);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(p.bg);page.setPadding(s.dp(6),s.dp(6),s.dp(6),s.dp(6));
  View hero=new View(a);GradientDrawable h=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{p.heroStart,p.heroEnd});h.setCornerRadius(s.dp(Math.min(10,p.radius/2f)));hero.setBackground(h);
  page.addView(hero,new LinearLayout.LayoutParams(-1,s.dp(24)));
  LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(s.dp(6),s.dp(5),s.dp(6),s.dp(5));
  GradientDrawable c=new GradientDrawable();c.setColor(p.surface);c.setCornerRadius(s.dp(Math.min(10,p.radius/2f)));card.setBackground(c);
  View l1=new View(a);l1.setBackground(round(s,p.ink,3));card.addView(l1,new LinearLayout.LayoutParams(s.dp(46),s.dp(5)));
  View l2=new View(a);l2.setBackground(round(s,p.muted,3));LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(s.dp(30),s.dp(4));lp2.topMargin=s.dp(4);card.addView(l2,lp2);
  LinearLayout dots=new LinearLayout(a);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-2,-2);dp.topMargin=s.dp(5);
  View pill=new View(a);pill.setBackground(round(s,p.accent,6));dots.addView(pill,new LinearLayout.LayoutParams(s.dp(26),s.dp(10)));
  if(p.secondary!=0){View sec=new View(a);sec.setBackground(round(s,p.secondary,6));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(s.dp(10),s.dp(10));sp.leftMargin=s.dp(4);dots.addView(sec,sp);}
  card.addView(dots,dp);
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,0,1);cp.topMargin=s.dp(5);page.addView(card,cp);
  return page;
 }
 static GradientDrawable round(Screens s,int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(s.dp(radius));return g;}
 static int parse(String text){String value=text.trim();if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use uma cor no formato #RRGGBB ou o botão Escolher cor.");return Color.parseColor(value);}
 static EditText field(MainActivity a,LinearLayout form,String label,int value){form.addView(a.label(label,13,a.text));EditText input=new EditText(a);input.setSingleLine();input.setText(String.format(java.util.Locale.ROOT,"#%06X",value&0xffffff));form.addView(input);TextView preview=a.button("Escolher cor",()->picker(a,label,input));preview.setTextColor(AppPalette.contrast(value,Color.WHITE)>=4.5?Color.WHITE:Color.BLACK);preview.setBackground(a.shape(value,12));input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void afterTextChanged(Editable e){try{int color=parse(e.toString());preview.setBackground(a.shape(color,12));preview.setTextColor(AppPalette.contrast(color,Color.WHITE)>=4.5?Color.WHITE:Color.BLACK);}catch(Exception ignored){}}public void onTextChanged(CharSequence s,int st,int before,int count){}});form.addView(preview);return input;}
 static void custom(MainActivity a){AppPalette current=AppPalette.of(a);LinearLayout form=a.column();EditText accent=field(a,form,"Cor de destaque",a.prefs.getInt("theme_custom_accent",current.accent));EditText bg=field(a,form,"Cor do fundo",a.prefs.getInt("theme_custom_background",current.bg));form.addView(a.label("O texto, os cartões e o destaque adaptam-se ao fundo para manter o contraste.",12,a.muted));UiDialog d=(UiDialog)a.dialog().setTitle("Personalizar tema").setView(form).setPositiveButton("Aplicar",null).setNegativeButton("Cancelar",null).create();d.show();d.getButton(-1).setOnClickListener(v->{try{int color=parse(accent.getText().toString()),background=parse(bg.getText().toString());a.prefs.edit().putInt("theme_custom_accent",color).putInt("theme_custom_background",background).putString("theme","Personalizado").apply();d.dismiss();a.render();}catch(Exception e){a.showError(e);}});}
 static void picker(MainActivity a,String title,EditText target){int initial;try{initial=parse(target.getText().toString());}catch(Exception e){initial=a.accent;}final int[] values={Color.red(initial),Color.green(initial),Color.blue(initial)};LinearLayout form=a.column();TextView preview=a.label("",15,Color.WHITE);preview.setGravity(Gravity.CENTER);preview.setMinHeight(a.dp(64));form.addView(preview);Runnable update=()->{int color=Color.rgb(values[0],values[1],values[2]);preview.setBackground(a.shape(color,12));preview.setTextColor(AppPalette.contrast(color,Color.WHITE)>=4.5?Color.WHITE:Color.BLACK);preview.setText(String.format(java.util.Locale.ROOT,"#%06X",color&0xffffff));};for(int i=0;i<3;i++){final int channel=i;FlashSettings.slider(a,form,new String[]{"Vermelho · ","Verde · ","Azul · "}[i],0,255,values[i],n->{values[channel]=n;update.run();});}update.run();a.dialog().setTitle(title).setView(form).setPositiveButton("Usar cor",(d,w)->target.setText(String.format(java.util.Locale.ROOT,"#%06X",Color.rgb(values[0],values[1],values[2])&0xffffff))).setNegativeButton("Cancelar",null).show();}
 private ThemeSettings(){}
}
