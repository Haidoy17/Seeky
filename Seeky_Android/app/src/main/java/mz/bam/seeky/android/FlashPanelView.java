package mz.bam.seeky.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Lightweight native search surface shared by the overlay and Android Go fallback. */
final class FlashPanelView extends LinearLayout {
    interface Actions {void close();void open();void stop();void mode(boolean flash);void folders();void category(String category);default void sizesChanged(){}}
    final AppPalette palette;final EditText input;final TextView status,flash,complete,folders;
    final View clear,sizeButton;final LinearLayout sizeTools;boolean compactLayout;final LinearLayout results,footer;final ScrollView scroll;final HorizontalScrollView categories;
    final Map<String,TextView> chips=new LinkedHashMap<>();final Actions actions;
    int dp(float n){return PlusUi.dp(getContext(),n);}
    GradientDrawable shape(int color,int radius,boolean border){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border)d.setStroke(dp(1),palette.line);return d;}
    TextView label(String text,int size,int color){TextView t=new TextView(getContext());t.setText(AppLanguage.text(getContext(),text));t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    void ripple(View v){GradientDrawable mask=shape(Color.WHITE,14,false);v.setBackground(new RippleDrawable(ColorStateList.valueOf((palette.accent&0xffffff)|0x24000000),v.getBackground(),mask));v.setFocusable(true);}
    View icon(String symbol,String description,Runnable click){FrameLayout v=new FrameLayout(getContext());v.addView(new Icons(getContext(),symbol,palette.muted),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));v.setContentDescription(AppLanguage.text(getContext(),description));v.setBackground(shape(palette.tint,14,false));v.setOnClickListener(w->click.run());ripple(v);return v;}
    TextView choice(String text,Runnable click){TextView v=label(text,12,palette.muted);v.setTypeface(Typeface.create("sans-serif-medium",0));v.setGravity(Gravity.CENTER);v.setMaxLines(1);v.setOnClickListener(w->click.run());return v;}
    LinearLayout.LayoutParams block(int height,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(height));p.bottomMargin=dp(bottom);return p;}
    FlashPanelView(Context c,Actions actions){
        super(c);this.actions=actions;palette=AppPalette.of(c);setOrientation(VERTICAL);setPadding(dp(16),dp(12),dp(16),dp(10));setBackground(new BrandSurface(c,BrandSurface.SHEET));
        // This surface owns its spacing, including the compact keyboard layout.
        UiSpacing.ignore(this);
        LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);ImageView brand=new ImageView(c);AppImages.wordmark(brand);brand.setScaleType(ImageView.ScaleType.FIT_START);brand.setContentDescription("Seeky");head.addView(brand,new LinearLayout.LayoutParams(dp(84),dp(32)));head.addView(new View(c),new LinearLayout.LayoutParams(0,1,1));sizeButton=icon("settings","Tamanhos",this::toggleSizes);LinearLayout.LayoutParams settingsBox=new LinearLayout.LayoutParams(dp(44),dp(44));settingsBox.rightMargin=dp(8);head.addView(sizeButton,settingsBox);head.addView(icon("close","Recolher pesquisa",actions::close),new LinearLayout.LayoutParams(dp(44),dp(44)));addView(head,block(44,10));sizeTools=new LinearLayout(c);sizeTools.setOrientation(VERTICAL);sizeTools.addView(new SizeControl(c,"Ícones","icon_size",n->actions.sizesChanged()));sizeTools.addView(new SizeControl(c,"Botão Flash","flash_button_size",n->actions.sizesChanged()));TextView advanced=choice("Transparência e recolha",()->FlashSettings.open(getContext()));advanced.setMinHeight(dp(44));sizeTools.addView(advanced);sizeTools.setVisibility(GONE);addView(sizeTools,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout field=new LinearLayout(c);field.setGravity(Gravity.CENTER_VERTICAL);field.setBackground(shape(palette.tint,16,false));field.setPadding(dp(12),0,dp(4),0);field.addView(new Icons(c,"search",palette.accent),new LinearLayout.LayoutParams(dp(21),dp(24)));
        input=new EditText(c);input.setSingleLine(true);input.setTextSize(16);input.setTextColor(palette.ink);input.setHintTextColor(palette.muted);input.setHint(AppLanguage.text(c,"Pesquisar ficheiros"));input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(10),0,dp(4),0);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_FILTER);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI);field.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        clear=icon("close","Limpar pesquisa",()->input.setText(""));clear.setVisibility(GONE);field.addView(clear,new LinearLayout.LayoutParams(dp(44),dp(44)));addView(field,block(52,8));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int f){}public void onTextChanged(CharSequence s,int a,int b,int n){clear.setVisibility(s.length()==0?GONE:VISIBLE);}public void afterTextChanged(Editable e){}});
        categories=new HorizontalScrollView(c);categories.setHorizontalScrollBarEnabled(false);categories.setOverScrollMode(OVER_SCROLL_NEVER);LinearLayout options=new LinearLayout(c);options.setGravity(Gravity.CENTER_VERTICAL);
        for(String category:new String[]{"Tudo","PDF","Documentos","Imagens","Vídeos","Áudio"}){TextView chip=choice(category,()->actions.category(category));chip.setPadding(dp(13),0,dp(13),0);chip.setMinWidth(dp(48));chips.put(category,chip);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.rightMargin=dp(8);options.addView(chip,p);}categories.addView(options);addView(categories,block(44,4));
        status=label("Recentes",12,palette.muted);status.setGravity(Gravity.CENTER_VERTICAL);status.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);addView(status,block(24,4));
        scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);results=new LinearLayout(c);results.setOrientation(VERTICAL);scroll.addView(results);addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tools=new LinearLayout(c);tools.setGravity(Gravity.CENTER_VERTICAL);LinearLayout modes=new LinearLayout(c);modes.setPadding(dp(3),dp(3),dp(3),dp(3));modes.setBackground(shape(palette.tint,14,false));
        flash=choice("Flash",()->actions.mode(true));complete=choice("Completa",()->actions.mode(false));modes.addView(flash,new LinearLayout.LayoutParams(0,-1,1));modes.addView(complete,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(46),1);mp.rightMargin=dp(8);tools.addView(modes,mp);
        folders=choice("Pastas",actions::folders);folders.setCompoundDrawablePadding(dp(4));folders.setContentDescription(AppLanguage.text(c,"Pesquisar nas pastas"));folders.setBackground(shape(palette.surface,14,true));tools.addView(folders,new LinearLayout.LayoutParams(dp(80),dp(46)));LinearLayout.LayoutParams tp=block(46,0);tp.topMargin=dp(8);addView(tools,tp);
        footer=new LinearLayout(c);footer.setGravity(Gravity.CENTER_VERTICAL);TextView open=label("Abrir Seeky",12,palette.accent);open.setGravity(Gravity.CENTER_VERTICAL);open.setMinHeight(dp(44));open.setPadding(dp(4),0,0,0);open.setOnClickListener(v->actions.open());ripple(open);footer.addView(open,new LinearLayout.LayoutParams(0,dp(44),1));footer.addView(icon("power","Desligar flutuante",actions::stop),new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout.LayoutParams fp=block(44,0);fp.topMargin=dp(6);addView(footer,fp);
        setCategory("Tudo");setMode(true,false);
    }
    void setCategory(String category){for(Map.Entry<String,TextView> item:chips.entrySet()){TextView v=item.getValue();boolean active=item.getKey().equals(category);v.setSelected(active);v.setTextColor(active?palette.accent:palette.muted);v.setBackground(shape(active?ThemeColors.blend(palette.surface,palette.accent,palette.light?.10f:.18f):palette.surface,12,!active));ripple(v);}}
    void setMode(boolean quick,boolean deep){for(TextView v:new TextView[]{flash,complete}){boolean selected=!deep&&(v==flash)==quick;v.setSelected(selected);v.setTextColor(selected?palette.ink:palette.muted);v.setBackground(shape(selected?palette.surface:Color.TRANSPARENT,11,false));ripple(v);}folders.setSelected(deep);folders.setTextColor(deep?palette.accent:palette.muted);folders.setBackground(shape(deep?palette.tint:palette.surface,14,true));ripple(folders);}
    void toggleSizes(){boolean show=sizeTools.getVisibility()!=VISIBLE;sizeTools.setVisibility(show?VISIBLE:GONE);sizeButton.setSelected(show);if(show)((android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);compact(compactLayout);}
    void compact(boolean compact){compactLayout=compact;categories.setVisibility(compact||sizeTools.getVisibility()==VISIBLE?GONE:VISIBLE);footer.setVisibility(compact?GONE:VISIBLE);}
}
