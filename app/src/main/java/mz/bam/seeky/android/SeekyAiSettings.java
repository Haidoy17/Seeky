package mz.bam.seeky.android;

import android.app.*;import android.content.*;import android.text.*;import android.widget.*;import java.util.concurrent.ExecutorService;

/** Gemini-only personal connection; network and storage failures are reported separately. */
final class SeekyAiSettings {
 static String provider(Context c){return SeekyAiCore.GEMINI;}
 static boolean allowed(Context c,String ignored){SharedPreferences p=c.getSharedPreferences("seeky_ai",0);return p.getBoolean("configured_gemini",false)&&p.getBoolean("terms_gemini",false);}
 static String name(String ignored){return "Google Gemini";}
 interface Verification {void run(String key,SeekyAiClient.Call call)throws Exception;}
 static void show(Activity a,SeekyAiVault vault,ExecutorService worker,Runnable changed){show(a,vault,worker,changed,SeekyAiClient::testConnection);}
 static void show(Activity a,SeekyAiVault vault,ExecutorService worker,Runnable changed,Verification verification){AppPalette colors=AppPalette.of(a);LinearLayout form=new LinearLayout(a);form.setOrientation(1);
  TextView service=new TextView(a);service.setText("Google Gemini");service.setTextColor(colors.ink);service.setTextSize(18);form.addView(service);
  EditText key=new EditText(a);key.setHint(AppLanguage.text(a,"Chave pessoal · deixe vazio para manter a guardada"));key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSingleLine(true);key.setSaveEnabled(false);key.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);key.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});form.addView(key);
  TextView info=new TextView(a);info.setTextColor(colors.muted);info.setTextSize(13);info.setText(AppLanguage.text(a,"Use uma chave do Google AI Studio num projecto sem facturação. O Seeky não verifica a facturação. A quota gratuita pode permitir o uso dos textos para melhorar os serviços Google. O teste gera uma resposta curta e pode consumir quota. Não envia documentos."));form.addView(info);
  TextView status=new TextView(a);status.setTextColor(colors.ink);status.setTextSize(14);status.setTextIsSelectable(true);form.addView(status);
  CheckBox terms=new CheckBox(a);terms.setText(AppLanguage.text(a,"Confirmo que usarei quota gratuita ou créditos promocionais, sem facturação/recarregamento automático. Compreendi o envio de texto."));terms.setTextColor(colors.ink);form.addView(terms);
  SeekyAiClient.Call call=new SeekyAiClient.Call();
  UiDialog dialog=new UiDialog.Builder(a).setTitle("Ligação da IA").setView(form).setPositiveButton("Testar e guardar",null).setNeutralButton("Remover chaves",null).setNegativeButton("Cancelar",(d,w)->call.cancel()).create();dialog.show();dialog.setOnCancelListener(d->call.cancel());
  dialog.getButton(-1).setOnClickListener(v->{if(!terms.isChecked()){status.setText(AppLanguage.text(a,"Confirme as condições antes de guardar."));return;}String entered=SeekyAiClient.normaliseKey(key.getText().toString());if(!entered.isEmpty()&&!SeekyAiClient.validKey(entered)){status.setText(AppLanguage.text(a,"Chave inválida. Copie a chave completa, sem espaços."));return;}
   dialog.getButton(-1).setEnabled(false);dialog.getButton(-3).setEnabled(false);key.setEnabled(false);status.setText(AppLanguage.text(a,"A testar uma resposta do Gemini…"));
   worker.execute(()->{String stage="read";try{String secret=entered.isEmpty()?vault.get(SeekyAiCore.GEMINI):entered;if(secret.isEmpty())throw new java.io.IOException("Introduza uma chave do Google AI Studio.");stage="network";verification.run(secret,call);call.check();stage="storage";if(!entered.isEmpty())vault.put(SeekyAiCore.GEMINI,secret);if(!a.getSharedPreferences("seeky_ai",0).edit().putString("provider",SeekyAiCore.GEMINI).putBoolean("configured_gemini",true).putBoolean("terms_gemini",true).commit())throw new java.io.IOException();a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()||call.cancelled.get())return;key.setText("");dialog.dismiss();changed.run();});}
    catch(Exception e){final String message=stage.equals("storage")?"A ligação ao Gemini funciona, mas não foi possível proteger a chave neste aparelho. Remova as chaves e tente guardar novamente.":stage.equals("read")?"Não foi possível ler a chave guardada. Cole uma nova chave para substituir o registo anterior.":e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Falha de ligação segura ao Gemini. Verifique a Internet e a data/hora do aparelho.";a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed()||call.cancelled.get())return;dialog.getButton(-1).setEnabled(true);dialog.getButton(-3).setEnabled(true);key.setEnabled(true);status.setText(AppLanguage.text(a,message));});}
   });
  });
  dialog.getButton(-3).setOnClickListener(v->new UiDialog.Builder(a).setTitle("Remover as chaves deste aparelho?").setMessage("As conversas guardadas são conservadas.").setPositiveButton("Remover",(d,w)->worker.execute(()->{vault.clear();a.getSharedPreferences("seeky_ai",0).edit().clear().commit();a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed()){dialog.dismiss();android.widget.Toast.makeText(a,AppLanguage.text(a,"Chaves removidas."),android.widget.Toast.LENGTH_SHORT).show();}});})).setNegativeButton("Cancelar",null).show());
 }
}
