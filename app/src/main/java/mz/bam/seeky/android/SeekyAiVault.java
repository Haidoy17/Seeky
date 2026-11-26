package mz.bam.seeky.android;
import android.content.*;import android.security.keystore.*;import android.util.Base64;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;import java.security.*;import java.util.Arrays;import org.json.*;
/** Personal API keys are encrypted locally; no shared credential is shipped or backed up. */
class SeekyAiVault {
 static final String ALIAS="seeky-ai-personal-keys-v1";final SharedPreferences prefs;
 SeekyAiVault(Context c){prefs=c.getSharedPreferences("seeky_ai_vault",0);}
 javax.crypto.SecretKey key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias(ALIAS)){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}return (javax.crypto.SecretKey)store.getKey(ALIAS,null);}
 synchronized JSONObject read()throws Exception{String value=prefs.getString("keys","");if(value.isEmpty())return new JSONObject();byte[] data=Base64.decode(value,Base64.NO_WRAP);if(data.length<28)throw new GeneralSecurityException();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(data,0,12)));c.updateAAD(ALIAS.getBytes("UTF-8"));return new JSONObject(new String(c.doFinal(Arrays.copyOfRange(data,12,data.length)),"UTF-8"));}
 synchronized String get(String provider)throws Exception{String own=read().optString(provider,"");return own.isEmpty()&&SeekyAiService.available()?SeekyAiService.KEY:own;}
 /** The person's own key only (empty when the Seeky service would be used). */
 synchronized String own(String provider)throws Exception{return read().optString(provider,"");}
 synchronized void put(String provider,String key)throws Exception{JSONObject data=new JSONObject();data.put(SeekyAiCore.GEMINI,key);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(ALIAS.getBytes("UTF-8"));byte[] encrypted=c.doFinal(data.toString().getBytes("UTF-8")),iv=c.getIV(),all=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(encrypted,0,all,iv.length,encrypted.length);if(!prefs.edit().putString("keys",Base64.encodeToString(all,Base64.NO_WRAP)).commit())throw new java.io.IOException("Não foi possível guardar a chave.");}
 void clear(){prefs.edit().clear().commit();}
}
