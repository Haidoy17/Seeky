package mz.bam.seeky.android;

import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Preview is temporary. Only Apply/Read confirms a voice; downloadable data belongs to the engine. */
final class ReaderSpeech {
    final ReaderActivity a;
    final SharedPreferences prefs;
    final SpeechAudioExport export;final NarrationOptions narration;final BackgroundSettings background;
    TextToSpeech discovery;
    UiPanel panel;
    ChoiceSpinner engines,languages,modes,voicePicker,variants,genders;
    final List<String> regions=new ArrayList<>();final SharedPreferences voiceNotes;TextView voiceCount,identify,favorite,favoritesOnly;boolean onlyFavorites;
    TextView info,start,preview,apply,download,saveAudio,retry;
    final List<String> engineIds=new ArrayList<>();
    final List<Voice> voices=new ArrayList<>();
    Set<Voice> available=Collections.emptySet();
    boolean filling,ready,closed,previewing,needsReload,preferNetwork,applyOnlineWhenReady;
    java.util.concurrent.Future<?> catalogTask;
    final java.util.concurrent.ExecutorService catalogWorker=java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"seeky-voice-catalogue");t.setDaemon(true);return t;});
    static final java.util.concurrent.ExecutorService shutdownWorker=java.util.concurrent.Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"seeky-voice-close");t.setDaemon(true);return t;});
    static java.util.concurrent.Future<?> shutdownAsync(TextToSpeech value){return value==null?null:shutdownWorker.submit(()->{try{value.shutdown();}catch(RuntimeException ignored){}});}
    java.util.concurrent.Future<?> closingEngine;
    int generation,sampleSerial;
    String selection,discoveringEngine="",sampleId="";
    int selectionStart;
    float rate;TextView speed;SeekBar speedBar;
    AudioManager audio;
    AudioFocusRequest focus;
    ReaderSpeech(ReaderActivity activity){a=activity;prefs=a.getSharedPreferences("seeky",0);GoogleVoiceSetup.migrate(prefs);export=new SpeechAudioExport(a);narration=new NarrationOptions(a);background=new BackgroundSettings(a);voiceNotes=a.getSharedPreferences("voice_catalog",0);}
    String tr(String value){return AppLanguage.text(a,value);}
    void show(){show(null,0);}
    void show(String selected,int from){
        if(a.dead)return;
        if(selected!=null&&selected.length()>20000){toast("Seleccione até 20000 caracteres para ouvir.");return;}
        if(panel!=null&&panel.dialog.isShowing())panel.dialog.dismiss();
        closed=false;selection=selected;selectionStart=from;rate=Math.max(.5f,Math.min(2f,prefs.getFloat("speech_rate",1f)));
        panel=new UiPanel(a,"Vozes e narração",selection==null?null:"Texto seleccionado","speaker");
        final UiPanel owner=panel;
        panel.dismissed=()->{if(panel==owner){release();panel=null;}};
        SpeechEvents.State state=SpeechEvents.state;
        SpeechTransport transport=null;if(state.active&&state.uri.equals(a.uri.toString())){transport=new SpeechTransport(a);panel.content.addView(transport);panel.note("Saltos aproximados no TTS.");}
        panel.section("Motor de voz");engines=picker();engineIds.clear();List<String> names=new ArrayList<>();
        filling=true;loadEngines(prefs.getString("speech_engine",GoogleVoiceSetup.GOOGLE));panel.content.addView(engines);
        TextView onlineGoogle=a.button("Google online · melhor voz",this::optimiseGoogle);UiBox.style(onlineGoogle);panel.content.addView(onlineGoogle);
        TextView natural=a.button("Baixar vozes naturais",()->{needsReload=true;stopSample();NaturalVoicesActivity.open(a);});UiBox.style(natural);panel.content.addView(natural);
        panel.section("Modo");modes=picker();set(modes,Arrays.asList(tr("Offline · vozes no telefone"),tr("Online · vozes de rede")));modes.setSelection(!engine().equals(NaturalVoicesActivity.ENGINE)&&prefs.getBoolean("speech_online",false)?1:0);panel.content.addView(modes);
        panel.section("Idioma da voz");languages=picker();set(languages,Arrays.asList(tr("Português"),"English","Español","Français"));languages.setSelection(Math.max(0,LANGUAGE_CODES.indexOf(prefs.getString("speech_language",AppLanguage.english(a)?"en":"pt"))));panel.content.addView(languages);
        panel.section("Variantes e vozes");variants=picker();regions.clear();regions.add("");set(variants,Collections.singletonList(tr("Todas as variantes")));panel.content.addView(variants);
        genders=picker();set(genders,Arrays.asList(tr("Todas as vozes"),tr("Femininas identificadas"),tr("Masculinas identificadas"),tr("Por identificar")));panel.content.addView(genders);
        voiceCount=a.text("",11);voiceCount.setTextColor(a.muted);panel.content.addView(voiceCount);
        panel.section("Escolher e ouvir");voicePicker=picker();set(voicePicker,Collections.singletonList(tr("A carregar vozes…")));panel.content.addView(voicePicker);
        preview=a.button("Ouvir amostra",this::sample);UiBox.style(preview);preview.setEnabled(false);panel.content.addView(preview);
        LinearLayout compare=new LinearLayout(a);compare.setOrientation(LinearLayout.HORIZONTAL);TextView before=a.button("Amostra anterior",()->compare(-1)),after=a.button("Próxima amostra",()->compare(1));UiBox.style(before);UiBox.style(after);compare.addView(before,new LinearLayout.LayoutParams(0,a.dp(46),1));LinearLayout.LayoutParams afterSpace=new LinearLayout.LayoutParams(0,a.dp(46),1);afterSpace.leftMargin=a.dp(10);compare.addView(after,afterSpace);panel.content.addView(compare);
        identify=a.button("Identificar voz",this::identifyVoice);UiBox.style(identify);panel.content.addView(identify);
        favorite=a.button("Guardar voz favorita",this::toggleFavorite);UiBox.style(favorite);panel.content.addView(favorite);
        favoritesOnly=a.button("Mostrar vozes favoritas",()->{onlyFavorites=!onlyFavorites;refreshVoices();});UiBox.style(favoritesOnly);panel.content.addView(favoritesOnly);
        info=a.text("A carregar vozes…",12);info.setTextColor(a.muted);panel.content.addView(info);
        retry=a.button("Tentar novamente",()->{loadEngines(engine());initEngine(true);});UiBox.style(retry);retry.setVisibility(View.GONE);panel.content.addView(retry);
        panel.section("Velocidade");speed=a.text("",12);panel.content.addView(speed);speedBar=new SeekBar(a);speedBar.setMax(150);speedBar.setProgress(Math.round((rate-.5f)*100));speed.setText(tr("Velocidade")+" · "+String.format(Locale.ROOT,"%.2f×",rate));speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int p,boolean user){if(user){stopSample();rate=SpeechPolicy.rate(p);speed.setText(tr("Velocidade")+" · "+String.format(Locale.ROOT,"%.2f×",rate));}}public void onStopTrackingTouch(SeekBar bar){}});panel.content.addView(speedBar,new LinearLayout.LayoutParams(-1,a.dp(44)));
        Switch continuous=new Switch(a);continuous.setText(tr("Continuar nas páginas seguintes"));continuous.setTextColor(a.ink);continuous.setTextSize(13);continuous.setMinHeight(a.dp(52));continuous.setChecked(prefs.getBoolean("speech_continuous",true));continuous.setEnabled(selection==null);continuous.setOnCheckedChangeListener((view,value)->prefs.edit().putBoolean("speech_continuous",value).apply());panel.content.addView(continuous);
        apply=a.button("Usar esta voz",()->{SpeechProfile profile=profile();if(profile!=null){profile.save(prefs);SpeechEvents.State active=SpeechEvents.state;if(active.active&&active.uri.equals(a.uri.toString()))ReadAloudService.control(a,ReadAloudService.CONFIGURE,active.token);toast("Voz guardada.");panel.dialog.dismiss();}});UiBox.style(apply);panel.content.addView(apply);
        start=a.button(selection==null?"Ler a partir daqui":"Ouvir selecção",()->start(false,a.chrome.current()));UiBox.style(start);panel.content.addView(start);
        if(selection==null&&prefs.contains("speech_part_"+a.uri)){TextView resume=a.button("Retomar leitura por voz",()->start(true,prefs.getInt("speech_part_"+a.uri,0)));UiBox.style(resume);panel.content.addView(resume);}
        panel.section("Usar sem Internet");
        download=a.button("Baixar vozes offline",this::installVoices);UiBox.style(download);panel.content.addView(download);
        TextView refresh=a.button("Detectar motores e actualizar vozes",()->{loadEngines(engine());initEngine(true);});UiBox.style(refresh);panel.content.addView(refresh);
        saveAudio=a.button("Guardar narração…",()->{SpeechProfile profile=profile();if(profile!=null){int part=a.chrome.current();String chosen=selection;stopSample();panel.dialog.dismiss();narration.show(profile,chosen,part);}});UiBox.style(saveAudio);panel.content.addView(saveAudio);
        
        panel.action("audio","Fundo de áudio",null,false,background::show);
        panel.action("book","Narrações guardadas",null,false,()->NarrationIndex.library(a));panel.action("clock","Temporizador de sono",null,false,()->SleepTimer.show(a,a.uri.toString(),a.chrome.current(),0));panel.action("edit","Dicionário de pronúncia",null,false,()->PlusUi.dictionary(a));panel.action("settings","Definições de voz do Android",null,false,this::systemSettings);
        filling=false;engines.setOnItemSelectedListener(listener(()->{if(engine().equals(NaturalVoicesActivity.ENGINE)){boolean was=filling;filling=true;modes.setSelection(0);filling=was;}initEngine(false);}));modes.setOnItemSelectedListener(listener(this::refreshVoices));languages.setOnItemSelectedListener(listener(this::refreshVoices));variants.setOnItemSelectedListener(listener(this::refreshVoices));genders.setOnItemSelectedListener(listener(this::refreshVoices));voicePicker.setOnItemSelectedListener(listener(this::selectionChanged));
        TextView jobs=a.button("Gerações",narration::manage);UiBox.style(jobs);panel.content.addView(jobs);
        Set<View> primary=new HashSet<>(Arrays.asList(jobs,engines,onlineGoogle,natural,voicePicker,preview,apply,info,retry,speed,speedBar,start,saveAudio,transport));LinearLayout options=new LinearLayout(a);options.setOrientation(1);List<View> visible=new ArrayList<>();for(int n=0;n<panel.content.getChildCount();n++){View child=panel.content.getChildAt(n);String description=String.valueOf(child.getContentDescription());if(primary.contains(child)||description.startsWith("Fundo de áudio")||description.startsWith("Pausar")||description.startsWith("Continuar")||description.startsWith("Parar leitura"))visible.add(child);}List<View> all=new ArrayList<>();for(int n=0;n<panel.content.getChildCount();n++)all.add(panel.content.getChildAt(n));panel.content.removeAllViews();options.removeAllViews();for(View child:all)if(visible.contains(child))panel.content.addView(child);else options.addView(child);options.setVisibility(View.GONE);TextView toggle=a.button("Opções de voz",()->options.setVisibility(options.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));UiBox.style(toggle);panel.content.addView(toggle);panel.content.addView(options);
        spaceControls(panel.content);spaceControls(options);panel.show();initEngine(false);
    }
    void spaceControls(LinearLayout group){for(int n=0;n<group.getChildCount();n++){View view=group.getChildAt(n);if(view instanceof TextView&&view.isClickable()||view instanceof ChoiceSpinner){android.view.ViewGroup.LayoutParams original=view.getLayoutParams();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(original==null?-1:original.width,original==null?-2:original.height);lp.bottomMargin=a.dp(9);view.setLayoutParams(lp);}}}
    void loadEngines(String preferred){
        engineIds.clear();List<String> names=new ArrayList<>();for(VoiceEngines.Engine result:VoiceEngines.scan(a)){engineIds.add(result.id);names.add(result.label);}
        boolean was=filling;filling=true;set(engines,names.isEmpty()?Collections.singletonList(tr("Nenhum motor de voz instalado")):names);engines.setSelection(Math.max(0,engineIds.indexOf(preferred)));filling=was;
    }
    void optimiseGoogle(){applyOnlineWhenReady=true;rate=1f;if(speedBar!=null)speedBar.setProgress(50);if(speed!=null)speed.setText(tr("Velocidade")+" · 1.00×");googleOnline();}
    void googleOnline(){
        stopSample();if(!engineIds.contains(GoogleVoiceSetup.GOOGLE)){needsReload=true;GoogleVoiceSetup.install(a);return;}
        boolean was=filling;filling=true;engines.setSelection(engineIds.indexOf(GoogleVoiceSetup.GOOGLE));modes.setSelection(1);genders.setSelection(0);variants.setSelection(0);onlyFavorites=false;preferNetwork=true;filling=was;initEngine(false);if(ready&&engine().equals(discoveringEngine))refreshVoices();
    }
    int gender(Voice voice){return voiceNotes.getInt("gender_"+VoiceIdentity.key(engine(),voice.getName()),VoiceIdentity.explicitGender(voice.getFeatures()));}
    String genderLabel(Voice voice){int gender=gender(voice);return tr(gender==1?"Feminina":gender==2?"Masculina":"Por identificar");}
    void identifyVoice(){Voice voice=chosen();if(voice==null){toast("Escolha uma voz e ouça a amostra.");return;}stopSample();new UiDialog.Builder(a).setTitle("Identificar voz").setMessage("Ouça a amostra e identifique a voz para os seus filtros.").setSingleChoiceItems(new String[]{tr("Por identificar"),tr("Feminina"),tr("Masculina")},gender(voice),(d,n)->{voiceNotes.edit().putInt("gender_"+VoiceIdentity.key(engine(),voice.getName()),n).apply();d.dismiss();refreshVoices();}).setNegativeButton("Cancelar",null).show();}
    void toggleFavorite(){Voice voice=chosen();if(voice==null)return;String key="favorite_"+VoiceIdentity.key(engine(),voice.getName());voiceNotes.edit().putBoolean(key,!voiceNotes.getBoolean(key,false)).apply();if(onlyFavorites)refreshVoices();else selectionChanged();}
    void compare(int delta){if(voices.isEmpty()){toast("Escolha uma voz disponível.");return;}stopSample();int target=voicePicker.getSelectedItemPosition();boolean found=false;for(int n=0;n<voices.size();n++){target=(target+delta+voices.size())%voices.size();if(SpeechVoices.usable(voices.get(target),online(),language())){found=true;break;}}if(!found){toast("Escolha uma voz instalada e disponível antes de iniciar.");return;}boolean was=filling;filling=true;voicePicker.setSelection(target);filling=was;selectionChanged();final int expected=generation;a.main.post(()->{if(!closed&&panel!=null&&generation==expected)sample();});}
    void moreGoogleVoices(){
        stopSample();new UiDialog.Builder(a).setTitle("Mais vozes Google").setMessage("Abra os pacotes Google e instale Português do Brasil, Português de Portugal ou Inglês. Depois volte e actualize a lista. As opções femininas e masculinas são disponibilizadas pelo motor.").setPositiveButton("Abrir pacotes Google",(d,w)->{needsReload=true;ArrayList<String> requested=new ArrayList<>(Arrays.asList("pt-BR","pt-PT","en-US","en-GB"));try{a.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GoogleVoiceSetup.GOOGLE).putStringArrayListExtra(TextToSpeech.Engine.EXTRA_CHECK_VOICE_DATA_FOR,requested));}catch(Exception e){systemSettings();}}).setNegativeButton("Cancelar",null).show();
    }
    void toast(String message){Toast.makeText(a,tr(message),Toast.LENGTH_LONG).show();}
    ChoiceSpinner picker(){ChoiceSpinner spinner=new ChoiceSpinner(a);spinner.setPadding(a.dp(10),a.dp(7),a.dp(8),a.dp(7));spinner.setMinimumHeight(a.dp(48));spinner.setBackground(a.shape(AppPalette.of(a).tint,12));return spinner;}
    void set(ChoiceSpinner picker,List<String> labels){ArrayAdapter<String> adapter=new ArrayAdapter<String>(a,android.R.layout.simple_spinner_item,labels){public View getView(int p,View recycled,android.view.ViewGroup parent){TextView label=(TextView)super.getView(p,recycled,parent);label.setTextColor(a.ink);label.setTextSize(13);label.setMaxLines(3);return label;}};picker.setAdapter(adapter);}
    AdapterView.OnItemSelectedListener listener(Runnable task){return new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(!filling&&!closed&&panel!=null)task.run();}public void onNothingSelected(AdapterView<?> parent){}};}
    boolean online(){return modes.getSelectedItemPosition()==1;}
    static final List<String> LANGUAGE_CODES=Arrays.asList("pt","en","es","fr");
    String language(){int index=languages.getSelectedItemPosition();return LANGUAGE_CODES.get(Math.max(0,Math.min(LANGUAGE_CODES.size()-1,index)));}
    String engine(){int at=engines.getSelectedItemPosition();return at>=0&&at<engineIds.size()?engineIds.get(at):"";}
    Voice chosen(){int at=voicePicker.getSelectedItemPosition();return at>=0&&at<voices.size()?voices.get(at):null;}
    SpeechProfile profile(){Voice voice=chosen();if(!ready||voice==null||!SpeechVoices.usable(voice,online(),language())){toast("Escolha uma voz instalada e disponível antes de iniciar.");return null;}return new SpeechProfile(engine(),voice.getName(),language(),online(),rate);}
    String engineFailure="";
    void engineFailed(String message){engineFailure=message;ready=false;available=Collections.emptySet();refreshVoices();if(retry!=null)retry.setVisibility(View.VISIBLE);if(info!=null)info.setText(tr(message));}
    void initEngine(boolean force){
        if(panel==null||closed)return;String engine=engine();
        if(engine.isEmpty()){ready=false;available=Collections.emptySet();refreshVoices();engineFailed("Instale um motor de voz nas definições do Android.");return;}
        if(!force&&engine.equals(discoveringEngine)&&discovery!=null)return;
        stopSample();if(catalogTask!=null)catalogTask.cancel(true);final int expected=++generation;engineFailure="";if(retry!=null)retry.setVisibility(View.GONE);discoveringEngine=engine;ready=false;available=Collections.emptySet();refreshVoices();info.setText(tr("A carregar vozes…"));
        a.main.removeCallbacks(engineTimeout);a.main.postDelayed(engineTimeout,12000);
        if(discovery!=null){closingEngine=shutdownAsync(discovery);discovery=null;}
        discovery=SpeechEngines.create(a.getApplicationContext(),status->a.main.post(()->{
            if(a.dead||closed||expected!=generation||panel==null)return;
            if(status!=TextToSpeech.SUCCESS){a.main.removeCallbacks(engineTimeout);ready=false;engineFailed(engine.equals(NaturalVoicesActivity.ENGINE)?"Não foi possível iniciar o motor de voz. Toque em Tentar novamente. Os dados da voz foram mantidos.":"Motor de voz indisponível. Abra as definições de voz.");AppDiagnostics.record(a,"speech-engine-init",new IllegalStateException("TTS init status "+status));return;}
            final TextToSpeech owner=discovery;
            catalogTask=catalogWorker.submit(()->{try{Set<Voice> values=owner.getVoices();a.main.post(()->{if(a.dead||closed||expected!=generation||panel==null||owner!=discovery)return;a.main.removeCallbacks(engineTimeout);available=values==null?Collections.emptySet():values;if(engine().equals(NaturalVoicesActivity.ENGINE)&&available.stream().noneMatch(v->v.getFeatures()!=null&&v.getFeatures().contains("seeky:piper-v1"))){available=Collections.emptySet();ready=false;refreshVoices();engineFailed("Actualize o módulo Seeky Vozes Naturais. O motor seleccionado não respondeu com as suas vozes.");return;}ready=true;refreshVoices();});}catch(Exception error){a.main.post(()->{if(a.dead||closed||expected!=generation||panel==null)return;a.main.removeCallbacks(engineTimeout);engineFailed("Não foi possível obter as vozes deste motor. Tente detectar novamente.");AppDiagnostics.record(a,"speech-engine-voices",error);});}});

        }),engine);
        if(discovery instanceof mz.bam.seeky.voicebridge.NaturalSpeech){final TextToSpeech connected=discovery;((mz.bam.seeky.voicebridge.NaturalSpeech)connected).setOnConnectionLostListener(()->{if(!a.dead&&!closed&&panel!=null&&expected==generation&&discovery==connected){generation++;a.main.removeCallbacks(engineTimeout);engineFailed("O motor foi interrompido durante a síntese. Toque em Tentar novamente. Os dados da voz foram mantidos.");}});}
    }
    final Runnable engineTimeout=this::engineTimedOut;
    void engineTimedOut(){if(panel==null||closed||ready)return;generation++;TextToSpeech stalled=discovery;discovery=null;discoveringEngine="";available=Collections.emptySet();refreshVoices();engineFailed("O motor não respondeu. Toque em Detectar motores e actualizar vozes para tentar novamente.");AppDiagnostics.record(a,"speech-engine-timeout",new java.util.concurrent.TimeoutException("TTS connection exceeded 12 seconds"));shutdownAsync(stalled);}
    void refreshVoices(){
        if(panel==null||closed)return;stopSample();String previous=chosen()==null?"":chosen().getName();String region=variants!=null&&variants.getSelectedItemPosition()>=0&&variants.getSelectedItemPosition()<regions.size()?regions.get(variants.getSelectedItemPosition()):"";
        List<Voice> all=SpeechVoices.list(available,online(),language());TreeSet<String> countries=new TreeSet<>();for(Voice voice:all)if(!voice.getLocale().getCountry().isEmpty())countries.add(voice.getLocale().getCountry());List<String> next=new ArrayList<>();next.add("");next.addAll(countries);
        if(variants!=null&&!next.equals(regions)){regions.clear();regions.addAll(next);List<String> labels=new ArrayList<>();labels.add(tr("Todas as variantes"));for(String code:countries)labels.add(new Locale(language(),code).getDisplayCountry(AppLanguage.english(a)?Locale.ENGLISH:new Locale("pt")));boolean was=filling;filling=true;set(variants,labels);variants.setSelection(Math.max(0,regions.indexOf(region)));filling=was;if(!regions.contains(region))region="";}
        voices.clear();int group=genders==null?0:genders.getSelectedItemPosition();for(Voice voice:all){int gender=gender(voice);boolean match=group==0||group==1&&gender==1||group==2&&gender==2||group==3&&gender==0;if(match&&(region.isEmpty()||voice.getLocale().getCountry().equals(region))&&(!onlyFavorites||voiceNotes.getBoolean("favorite_"+VoiceIdentity.key(engine(),voice.getName()),false)))voices.add(voice);}
        List<String> labels=new ArrayList<>();Locale ui=AppLanguage.english(a)?Locale.ENGLISH:new Locale("pt");int selected=0;String saved=previous.isEmpty()?prefs.getString(SpeechVoices.key(engine(),language(),online()),prefs.getString("speech_voice","")):previous;
        for(int n=0;n<voices.size();n++){Voice voice=voices.get(n);if(voice.getName().equals(saved))selected=n;labels.add((engine().equals(NaturalVoicesActivity.ENGINE)?voice.getName()+" · ":"")+voice.getLocale().getDisplayName(ui)+" · "+tr(voice.getQuality()>=Voice.QUALITY_VERY_HIGH?"Qualidade máxima":voice.getQuality()>=Voice.QUALITY_HIGH?"Alta qualidade":"Padrão")+" · "+genderLabel(voice)+" · "+tr(SpeechVoices.missing(voice)?"Precisa de instalação":online()?"Online":"Instalada")+" · "+tr("Voz")+" "+(n+1));}
        if(ready&&preferNetwork){for(int n=0;n<voices.size();n++)if(SpeechVoices.usable(voices.get(n),true,language())){selected=n;break;}preferNetwork=false;}
        boolean was=filling;filling=true;set(voicePicker,labels.isEmpty()?Collections.singletonList(tr(!engineFailure.isEmpty()?"Motor indisponível":ready?"Sem voz disponível neste filtro":"A carregar vozes…")):labels);voicePicker.setSelection(selected);filling=was;if(favoritesOnly!=null)favoritesOnly.setText(tr(onlyFavorites?"Mostrar todas as vozes":"Mostrar vozes favoritas"));if(voiceCount!=null)voiceCount.setText(voices.size()+" / "+all.size()+" "+tr("vozes")+(onlyFavorites?" · "+tr("Favoritas"):""));selectionChanged();if(ready&&applyOnlineWhenReady&&online()&&engine().equals(GoogleVoiceSetup.GOOGLE)){Voice selectedVoice=chosen();if(selectedVoice!=null&&SpeechVoices.usable(selectedVoice,true,language())){applyOnlineWhenReady=false;SpeechProfile chosenProfile=profile();if(chosenProfile!=null){chosenProfile.save(prefs);SpeechEvents.State state=SpeechEvents.state;if(state.active&&state.uri.equals(a.uri.toString()))try{a.startService(new Intent(a,ReadAloudService.class).setAction(ReadAloudService.CONFIGURE).putExtra("token",state.token).putExtra("resume",!state.paused));}catch(Exception e){AppDiagnostics.record(a,"speech-optimise",e);}toast("Voz Google online aplicada a 1×. Ouça a amostra para comparar.");}}else if(!preferNetwork)applyOnlineWhenReady=false;}
    }
    void selectionChanged(){
        if(panel==null||closed)return;stopSample();Voice voice=chosen();boolean usable=ready&&voice!=null&&SpeechVoices.usable(voice,online(),language());
        if(identify!=null)identify.setEnabled(voice!=null);if(favorite!=null){favorite.setEnabled(voice!=null);favorite.setText(tr(voice!=null&&voiceNotes.getBoolean("favorite_"+VoiceIdentity.key(engine(),voice.getName()),false)?"Retirar voz favorita":"Guardar voz favorita"));}
        for(TextView button:new TextView[]{start,preview,apply,saveAudio})if(button!=null){button.setEnabled(usable);button.setAlpha(usable?1f:.45f);}
        if(preview!=null&&ready&&voice!=null&&engine().equals(NaturalVoicesActivity.ENGINE)){preview.setEnabled(true);preview.setAlpha(1f);}
        if(info!=null&&!engineFailure.isEmpty())info.setText(tr(engineFailure));else if(info!=null)info.setText(tr(voice!=null&&SpeechVoices.missing(voice)?"Esta voz precisa de dados. Use Baixar vozes offline e volte para actualizar a lista.":online()?usable?"Qualidade indicada pelo motor. A amostra usa Internet.":"Nenhuma voz online disponível neste idioma.":usable?"Voz instalada para ler novos textos sem Internet.":"O motor ainda não disponibiliza uma voz offline neste idioma."));
    }
    void sample(){
        Voice candidate=chosen();if(candidate!=null&&engine().equals(NaturalVoicesActivity.ENGINE)){needsReload=true;stopSample();try{a.startActivity(new Intent().setClassName(NaturalVoicesActivity.ENGINE,"mz.bam.seeky.voices.MainActivity").putExtra("preview_voice",candidate.getName()));}catch(Exception error){toast("Actualize o módulo Seeky Vozes Naturais para ouvir esta amostra.");}return;}
        if(previewing){stopSample();selectionChanged();return;}SpeechProfile profile=profile();if(profile==null)return;
        SpeechEvents.State active=SpeechEvents.state;if(active.active&&!active.paused)ReadAloudService.control(a,ReadAloudService.PAUSE,active.token);
        try{
            Voice voice=chosen();SpeechVoices.apply(discovery,voice,profile.rate);discovery.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            if(audio==null)audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(change->{if(change<0)stopSample();}).build();
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){toast("Som ocupado por outra aplicação.");return;}
            final String id="preview-"+generation+"-"+(++sampleSerial);sampleId=id;previewing=true;
            discovery.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String utterance){}public void onDone(String utterance){done(utterance,false);}public void onError(String utterance){done(utterance,true);}void done(String utterance,boolean error){a.main.post(()->{if(!closed&&utterance.equals(sampleId)){stopSample();selectionChanged();if(error)toast("A amostra falhou. Verifique a ligação ou instale os dados da voz.");}});}});
            SpeechVoices.verify(discovery,profile);Bundle parameters=SpeechParameters.create(profile.online);
            String phrase=profile.language.equals("es")?"Hola. Esta es una voz de Seeky. Una buena lectura respeta las pausas y el ritmo de cada frase.":profile.language.equals("fr")?"Bonjour. Voici une voix de Seeky. Une bonne lecture respecte les pauses et le rythme de chaque phrase.":profile.language.equals("en")?"Hello! This is Seeky. A good reading voice respects pauses, questions and the rhythm of each sentence. Which voice do you prefer?":"Olá! Esta é uma amostra do Seeky. Uma boa leitura respeita as pausas, as perguntas e o ritmo de cada frase. Qual voz prefere ouvir?";
            if(discovery.speak(phrase,TextToSpeech.QUEUE_FLUSH,parameters,id)!=TextToSpeech.SUCCESS)throw new IllegalStateException();
            preview.setText(tr("Parar amostra"));info.setText(tr("A ouvir amostra. A voz só é guardada ao tocar em Usar esta voz."));a.main.removeCallbacks(sampleTimeout);a.main.postDelayed(sampleTimeout,45000);
        }catch(Exception e){stopSample();toast("Não foi possível ouvir esta voz.");AppDiagnostics.record(a,"speech-preview",e);}
    }
    final Runnable sampleTimeout=()->{if(previewing){stopSample();selectionChanged();toast("A voz não respondeu. Tente outra voz.");}};
    void stopSample(){sampleId="";sampleSerial++;a.main.removeCallbacks(sampleTimeout);if(previewing&&discovery!=null)discovery.stop();previewing=false;if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);focus=null;if(preview!=null)preview.setText(tr("Ouvir amostra"));}
    void start(boolean resume,int part){
        SpeechProfile profile=profile();if(profile==null)return;profile.save(prefs);stopSample();
        if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!prefs.getBoolean("speech_notification_asked",false)){prefs.edit().putBoolean("speech_notification_asked",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);}
        Intent intent=new Intent(a,ReadAloudService.class).setAction(ReadAloudService.START).setDataAndType(a.uri,a.mime).putExtra("name",a.name).putExtra("part",part).putExtra("resume",resume).putExtra("continuous",selection==null&&prefs.getBoolean("speech_continuous",true));
        intent.putExtra("study_material",a.getIntent().getLongExtra("study_material",0)).putExtra("study_title",a.getIntent().getStringExtra("study_title")).putExtra("study_path",a.getIntent().getStringExtra("study_path"));if(selection!=null)intent.putExtra("selection",selection).putExtra("selection_start",selectionStart);
        try{a.startForegroundService(intent);panel.dialog.dismiss();}catch(Exception e){toast("Não foi possível iniciar a leitura por voz.");AppDiagnostics.record(a,"speech-start",e);}
    }
    void systemSettings(){needsReload=true;stopSample();try{a.startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){try{a.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception ignored){toast("Abra as definições de texto para voz do Android.");}}}
    void installVoices(){
        String engine=engine();if(engine.equals(NaturalVoicesActivity.ENGINE)){needsReload=true;stopSample();NaturalVoicesActivity.open(a);return;}if(engine.isEmpty()){systemSettings();return;}needsReload=true;stopSample();Voice voice=chosen();ArrayList<String> requested=new ArrayList<>();if(voice!=null)requested.add(voice.getLocale().getLanguage()+"-"+voice.getLocale().getCountry());else requested.add(language());
        try{a.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(engine).putStringArrayListExtra(TextToSpeech.Engine.EXTRA_CHECK_VOICE_DATA_FOR,requested));}catch(Exception e){systemSettings();}
    }
    void resume(){export.resume();if(panel!=null&&!closed){String before=engine();List<String> previous=new ArrayList<>(engineIds);loadEngines(before);if(needsReload||!previous.equals(engineIds)){needsReload=false;initEngine(true);}}}
    void pause(){stopSample();}
    void release(){a.main.removeCallbacks(engineTimeout);stopSample();closed=true;applyOnlineWhenReady=false;generation++;ready=false;available=Collections.emptySet();discoveringEngine="";if(discovery!=null){closingEngine=shutdownAsync(discovery);discovery=null;}}
    void close(){if(panel!=null)panel.dialog.dismiss();release();catalogWorker.shutdownNow();export.close();}
}
