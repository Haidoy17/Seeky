package mz.bam.seeky.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared native design for every internal dialog, including legacy call sites and forms. */
final class UiDialog extends AlertDialog {
 static final Map<Activity,Set<UiDialog>> open=new HashMap<>();
 static void visibility(Activity host,boolean visible){Set<UiDialog> dialogs=open.get(host);if(dialogs!=null)for(UiDialog d:new ArrayList<>(dialogs))QuickSearchService.holdOverlay(d.hold,visible&&d.isShowing());}
 static void dismissAll(Activity host){Set<UiDialog> dialogs=open.remove(host);if(dialogs!=null)for(UiDialog d:new ArrayList<>(dialogs))d.dismiss();}
 final Activity host;final Builder spec;final String hold="dialog-"+UUID.randomUUID();final Map<Integer,Button> buttons=new HashMap<>();
 volatile boolean blocked;TextView closeControl;LinearLayout root,footer;View body;ListView choiceList;int surface,ink,muted,accent,tint,line,desired=0;
 int dp(float n){return (int)(n*host.getResources().getDisplayMetrics().density+.5f);}
 UiDialog(Builder spec){super(spec.host);this.spec=spec;host=spec.host;setCancelable(spec.cancelable);setCanceledOnTouchOutside(spec.cancelable);if(spec.cancelAction!=null)setOnCancelListener(spec.cancelAction);if(spec.dismissAction!=null)setOnDismissListener(spec.dismissAction);}
 GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView text(CharSequence value,int size,int color){TextView t=new TextView(host);t.setText(AppLanguage.text(host,value));t.setTextSize(size);t.setTextColor(color);return t;}
 @Override protected void onCreate(Bundle state){super.onCreate(state);AppPalette palette=AppPalette.of(host);surface=palette.surface;ink=palette.ink;muted=palette.muted;accent=palette.accent;tint=palette.tint;line=palette.line;
  root=new LinearLayout(host);root.setOrientation(1);root.setPadding(dp(17),dp(10),dp(17),dp(14));root.setBackground(shape(surface,26));root.setClipToOutline(true);
  View handle=new View(host);handle.setBackground(shape((muted&0xffffff)|0x55000000,3));LinearLayout.LayoutParams grip=new LinearLayout.LayoutParams(dp(34),dp(4));grip.gravity=Gravity.CENTER;grip.bottomMargin=dp(14);root.addView(handle,grip);
  LinearLayout head=new LinearLayout(host);head.setGravity(Gravity.CENTER_VERTICAL);FrameLayout icon=new FrameLayout(host);icon.setBackground(shape(tint,15));icon.addView(new Icons(host,symbol(spec.title.toString()),accent),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));head.addView(icon,new LinearLayout.LayoutParams(dp(47),dp(47)));TextView heading=text(spec.title,19,ink);ThemeStyle.title(heading,19);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.MIDDLE);heading.setPadding(dp(12),0,dp(6),0);head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));TextView close=text("×",27,muted);closeControl=close;close.setGravity(Gravity.CENTER);close.setContentDescription(AppLanguage.text(close.getContext(),"Fechar menu"));close.setOnClickListener(v->cancel());close.setVisibility(spec.cancelable?View.VISIBLE:View.GONE);head.addView(close,new LinearLayout.LayoutParams(dp(42),dp(47)));root.addView(head);LinearLayout.LayoutParams main=new LinearLayout.LayoutParams(-1,0,1);main.topMargin=dp(12);
  if(spec.items!=null){LinearLayout wrapper=new LinearLayout(host);wrapper.setOrientation(1);if(spec.message!=null){TextView note=text(spec.message,12,muted);note.setPadding(dp(3),0,dp(3),dp(12));wrapper.addView(note);}ListView choices=new ListView(host);choiceList=choices;choices.setDivider(null);choices.setDividerHeight(dp(7));choices.setAdapter(new ChoiceRows());choices.setOnItemClickListener((parent,view,pos,id)->{if(spec.multi){spec.checked[pos]=!spec.checked[pos];if(spec.multiChoice!=null)spec.multiChoice.onClick(this,pos,spec.checked[pos]);choices.invalidateViews();}else{if(spec.choice!=null)spec.choice.onClick(this,pos);if(!spec.single)dismiss();}});wrapper.addView(choices,new LinearLayout.LayoutParams(-1,0,1));body=wrapper;root.addView(body,main);desired=dp(140)+Math.min(spec.items.length,10)*dp(65);}
  else{ScrollView scroll=new ScrollView(host);scroll.setFillViewport(false);LinearLayout content=new LinearLayout(host);content.setOrientation(1);if(spec.message!=null){TextView message=text(spec.message,13,muted);message.setLineSpacing(dp(3),1.05f);message.setPadding(dp(3),0,dp(3),dp(12));message.setTextIsSelectable(true);content.addView(message);}View custom=spec.view;if(custom instanceof ScrollView){ScrollView old=(ScrollView)custom;if(old.getChildCount()>0){custom=old.getChildAt(0);old.removeView(custom);}}if(custom!=null){if(custom.getParent() instanceof ViewGroup)((ViewGroup)custom.getParent()).removeView(custom);style(custom);content.addView(custom,new LinearLayout.LayoutParams(-1,-2));}scroll.addView(content);body=scroll;root.addView(body,main);int width=Math.min(host.getResources().getDisplayMetrics().widthPixels-dp(24),dp(540));content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width-dp(34)),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));desired=dp(140)+content.getMeasuredHeight();}
  footer=new LinearLayout(host);footer.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(12);root.addView(footer,fp);addButton(BUTTON_NEUTRAL,spec.neutral,spec.neutralAction);addButton(BUTTON_NEGATIVE,spec.negative,spec.negativeAction);addButton(BUTTON_POSITIVE,spec.positive,spec.positiveAction);if(buttons.isEmpty()&&spec.cancelable)addButton(BUTTON_NEGATIVE,"Fechar",null);desired+=dp(62);root.setFocusableInTouchMode(true);root.requestFocus();setContentView(root);Window w=getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);}
  root.getViewTreeObserver().addOnGlobalLayoutListener(()->resize());
 }
 void style(View view){if(view instanceof EditText){EditText input=(EditText)view;input.setTextColor(ink);input.setHintTextColor(muted);input.setTextSize(14);input.setMinHeight(dp(52));input.setPadding(dp(13),dp(10),dp(13),dp(10));GradientDrawable box=shape(tint,14);box.setStroke(dp(1),(accent&0xffffff)|0x30000000);input.setBackground(box);input.setOnFocusChangeListener((v,focused)->{box.setStroke(dp(focused?2:1),focused?accent:(accent&0xffffff)|0x30000000);});}
  else if(view instanceof SeekBar){SeekBar bar=(SeekBar)view;bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));bar.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(AppPalette.of(host).line));}
  else if(view instanceof CompoundButton){CompoundButton button=(CompoundButton)view;button.setTextColor(ink);if(button instanceof CheckBox||button instanceof RadioButton)button.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));}
  else if(view instanceof Spinner){view.setBackground(shape(tint,14));view.setPadding(dp(9),dp(3),dp(8),dp(3));view.setMinimumHeight(dp(52));}
  else if(view instanceof TextView){TextView text=(TextView)view;if(text.getTextSize()/host.getResources().getDisplayMetrics().scaledDensity<=13){text.setPadding(dp(2),dp(11),dp(2),dp(6));text.setTextColor(muted);}}
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child instanceof Spinner&&i>0&&group.getChildAt(i-1) instanceof TextView)((Spinner)child).setPrompt(((TextView)group.getChildAt(i-1)).getText());style(child);}}
 }
 void addButton(int which,CharSequence title,DialogInterface.OnClickListener callback){if(title==null)return;Button b=new Button(host);b.setText(AppLanguage.text(host,title));b.setAllCaps(false);b.setTextSize(13);b.setTypeface(null,Typeface.BOLD);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),dp(8),dp(8),dp(8));b.setMaxLines(2);b.setTextColor(which==BUTTON_POSITIVE?(AppPalette.of(host).light?Color.WHITE:0xff10141d):accent);b.setBackground(shape(which==BUTTON_POSITIVE?accent:tint,14));b.setOnClickListener(v->{if(blocked)return;if(callback!=null)callback.onClick(this,which);dismiss();});Motion.touch(b);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);if(footer.getChildCount()>0)lp.leftMargin=dp(7);footer.addView(b,lp);buttons.put(which,b);}
 void lock(){blocked=true;PlusUi.MAIN.post(()->{setCancelable(false);setCanceledOnTouchOutside(false);if(closeControl!=null)closeControl.setVisibility(View.GONE);for(Button b:buttons.values())b.setEnabled(false);});}
 @Override public void cancel(){if(!blocked)super.cancel();}
 @Override public Button getButton(int which){return buttons.get(which);}
 @Override public ListView getListView(){return choiceList;}
 void resize(){Window window=getWindow();if(window==null||root==null)return;Rect visible=new Rect();host.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);int screen=host.getResources().getDisplayMetrics().heightPixels;int available=visible.height()>dp(180)?visible.height():screen;if(android.os.Build.VERSION.SDK_INT>=30&&root.getRootWindowInsets()!=null){int ime=root.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;if(ime>0)available=Math.min(available,screen-ime-dp(20));}int height=Math.min(Math.max(dp(235),desired),Math.min((int)(screen*.84f),Math.max(dp(140),available-dp(24))));int width=Math.min(host.getResources().getDisplayMetrics().widthPixels-dp(24),dp(540));WindowManager.LayoutParams p=window.getAttributes();if(p.width==width&&p.height==height)return;p.width=width;p.height=height;p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;p.y=dp(12);p.dimAmount=.32f;window.setAttributes(p);}
 @Override protected void onStart(){super.onStart();UiSpacing.watch(root);open.computeIfAbsent(host,k->new HashSet<>()).add(this);QuickSearchService.holdOverlay(hold,true);resize();Motion.enter(root,0);}
 @Override protected void onStop(){QuickSearchService.holdOverlay(hold,false);Set<UiDialog> dialogs=open.get(host);if(dialogs!=null){dialogs.remove(this);if(dialogs.isEmpty())open.remove(host);}super.onStop();}
 class ChoiceRow extends LinearLayout {
  final Icons icon,arrow;final FrameLayout tile;final TextView title,subtitle;final RadioButton radio;final CheckBox check;
  ChoiceRow(){super(host);setGravity(Gravity.CENTER_VERTICAL);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setPadding(dp(10),dp(9),dp(10),dp(9));setMinimumHeight(dp(58));GradientDrawable card=shape(surface,16);card.setStroke(dp(1),line);setBackground(card);tile=new FrameLayout(host);tile.setBackground(ThemeStyle.tile(host,AppPalette.of(host),40));icon=new Icons(host,"dot",accent);tile.addView(icon,new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER));addView(tile,new LinearLayout.LayoutParams(dp(40),dp(40)));LinearLayout copy=new LinearLayout(host);copy.setOrientation(1);copy.setPadding(dp(12),0,dp(6),0);title=text("",14,ink);title.setTypeface(null,Typeface.BOLD);subtitle=text("",11,muted);copy.addView(title);copy.addView(subtitle);addView(copy,new LinearLayout.LayoutParams(0,-2,1));radio=new RadioButton(host);radio.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));radio.setClickable(false);radio.setFocusable(false);addView(radio,new LinearLayout.LayoutParams(dp(35),dp(35)));check=new CheckBox(host);check.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));check.setClickable(false);check.setFocusable(false);addView(check,new LinearLayout.LayoutParams(dp(35),dp(35)));arrow=new Icons(host,"chevron",muted);addView(arrow,new LinearLayout.LayoutParams(dp(18),dp(22)));}
  void bind(int position){String full=spec.items[position].toString();int colon=full.indexOf(':');String value=colon>0?full.substring(colon+1).trim():"";title.setText(colon>0?full.substring(0,colon):full);subtitle.setText(value);subtitle.setVisibility(value.isEmpty()?GONE:VISIBLE);icon.setSymbol(symbol(full),accent);tile.setVisibility(spec.single||spec.multi?GONE:VISIBLE);radio.setVisibility(spec.single?VISIBLE:GONE);radio.setChecked(position==spec.selected);check.setVisibility(spec.multi?VISIBLE:GONE);check.setChecked(spec.multi&&spec.checked[position]);arrow.setVisibility(spec.single||spec.multi?GONE:VISIBLE);setContentDescription(full);}
 }
 class ChoiceRows extends BaseAdapter {public int getCount(){return spec.items.length;}public Object getItem(int p){return spec.items[p];}public long getItemId(int p){return p;}public View getView(int position,View recycled,ViewGroup parent){ChoiceRow row=recycled instanceof ChoiceRow?(ChoiceRow)recycled:new ChoiceRow();row.bind(position);return row;}}
 /** Ícone de cada item de menu, pelo significado do texto (português e inglês). */
 static final String[][] SYMBOLS={
  {"cores","palette"},{"colors","palette"},{"tema","palette"},{"idioma","globe"},{"language","globe"},{"english","globe"},{"português","globe"},
  {"bloqueio","lock"},{"lock","lock"},{"cópia autom","restore"},{"backup","restore"},{"recupera","restore"},
  {"acesso geral","storage"},{"armazen","storage"},{"flutuante","float"},{"bolha","float"},{"ícones e botão","flash"},{"motor de pesquisa","flash"},
  {"detecção","refresh"},{"actualizar","refresh"},{"rever","refresh"},{"revisão","refresh"},{"retomar","play"},{"pausar","pause"},
  {"ocultos","eye"},{"hidden","eye"},{"animaç","motion"},{"densidade","grid"},{"exibir","grid"},{"todos os livros","grid"},{"todas","grid"},
  {"limpar","trash"},{"lixeira","trash"},{"eliminar","trash"},{"remover","trash"},{"desligar","power"},{"parar","stop"},
  {"ferramentas","tools"},{"ajuda","info"},{"versão","info"},{"formato","info"},
  {"velocidade","speed"},{"speed","speed"},{"repetir","repeat"},{"abrir fora","share"},{"partilhar","share"},{"copiar","copy"},
  {"capítulo","contents"},{"títulos","contents"},{"temporizador","clock"},{"minutos","clock"},{"outro tempo","clock"},{"recentes","clock"},{"histórico","clock"},{"data","clock"},
  {"fixar","pin"},{"favorit","star"},{"destaques","bookmark"},{"sublinhad","bookmark"},{"marcador","bookmark"},
  {"cartão","cards"},{"cartões","cards"},{"pergunta","quiz"},{"escolha múltipla","quiz"},{"resposta","quiz"},
  {"anotaç","note"},{"notas","note"},{"resumo","note"},{"caderno","pdf"},{"pdf","pdf"},{"html","document"},{"txt","type"},{"texto","type"},
  {"curso","course"},{"módulo","course"},{"disciplina","course"},{"estudar","course"},{"tarefa","tasks"},
  {"editar","edit"},{"nome","edit"},{"criar","add"},{"importar","import"},{"adicionar","add"},{"novo","add"},{"nova","add"},
  {"pasta","folder"},{"colec","collection"},{"pesquis","search"},{"filtr","filters"},{"classific","filters"},{"orden","filters"},
  {"preferênc","settings"},{"bateria","settings"},{"definiç","settings"},{"lidos","book"},{"a ler","book"},{"finalizados","check"},{"livro","book"},
  {"acf","book"},{"ara","book"},{"bíbli","book"},{"redigido","shield"},{"completo","shield"},{"abrir","arrow"},{"documento","document"},{"ficheiro","document"}};
 static String symbol(String label){String s=label.toLowerCase(Locale.ROOT);for(String[] pair:SYMBOLS)if(s.contains(pair[0]))return pair[1];return "dot";}
 static Activity activity(Context c){while(c instanceof ContextWrapper){if(c instanceof Activity)return (Activity)c;c=((ContextWrapper)c).getBaseContext();}if(c instanceof Activity)return (Activity)c;throw new IllegalArgumentException("A visible Activity is required");}
 static final class Builder extends AlertDialog.Builder {
  final Activity host;CharSequence title="Seeky",message,positive,negative,neutral;View view;CharSequence[] items;int selected=-1;boolean single=false,multi=false,cancelable=true;boolean[] checked;DialogInterface.OnMultiChoiceClickListener multiChoice;DialogInterface.OnCancelListener cancelAction;DialogInterface.OnDismissListener dismissAction;DialogInterface.OnClickListener choice,positiveAction,negativeAction,neutralAction;
  Builder(Context context){super(context);host=activity(context);}
  @Override public Builder setTitle(CharSequence value){title=value==null?"Seeky":value;return this;}
  @Override public Builder setTitle(int value){return setTitle(host.getString(value));}
  @Override public Builder setMessage(CharSequence value){message=value;return this;}
  @Override public Builder setMessage(int value){return setMessage(host.getString(value));}
  @Override public Builder setView(View value){view=value;return this;}
  @Override public Builder setItems(CharSequence[] values,DialogInterface.OnClickListener callback){items=AppLanguage.items(host,values);choice=callback;single=false;return this;}
  @Override public Builder setSingleChoiceItems(CharSequence[] values,int selected,DialogInterface.OnClickListener callback){items=AppLanguage.items(host,values);this.selected=selected;choice=callback;single=true;return this;}
  @Override public Builder setMultiChoiceItems(CharSequence[] values,boolean[] checked,DialogInterface.OnMultiChoiceClickListener callback){items=AppLanguage.items(host,values);this.checked=checked==null?new boolean[values.length]:checked;if(this.checked.length!=values.length)throw new IllegalArgumentException();multiChoice=callback;multi=true;single=false;return this;}
  @Override public Builder setPositiveButton(CharSequence title,DialogInterface.OnClickListener callback){positive=title;positiveAction=callback;return this;}
  @Override public Builder setNegativeButton(CharSequence title,DialogInterface.OnClickListener callback){negative=title;negativeAction=callback;return this;}
  @Override public Builder setNeutralButton(CharSequence title,DialogInterface.OnClickListener callback){neutral=title;neutralAction=callback;return this;}
  @Override public Builder setCancelable(boolean value){cancelable=value;return this;}
  @Override public Builder setOnCancelListener(DialogInterface.OnCancelListener value){cancelAction=value;return this;}
  @Override public Builder setOnDismissListener(DialogInterface.OnDismissListener value){dismissAction=value;return this;}
  @Override public UiDialog create(){return new UiDialog(this);}
  @Override public UiDialog show(){UiDialog dialog=create();dialog.show();return dialog;}
 }
}
