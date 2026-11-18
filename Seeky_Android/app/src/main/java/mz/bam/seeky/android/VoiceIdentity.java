package mz.bam.seeky.android;

import java.util.*;

/** Android has no standard voice gender field. Accept explicit tags, never infer gender from pitch or opaque IDs. */
final class VoiceIdentity {
    static int explicitGender(Set<String> features){
        if(features==null)return 0;
        for(String feature:features){String tag=feature.toLowerCase(Locale.ROOT).trim().replace(':','=');if(tag.equals("female")||tag.equals("gender=female")||tag.equals("gender_female"))return 1;if(tag.equals("male")||tag.equals("gender=male")||tag.equals("gender_male"))return 2;}
        return 0;
    }
    static String key(String engine,String voice){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest((engine+"\n"+voice).getBytes("UTF-8"));char[] out=new char[hash.length*2];char[] hex="0123456789abcdef".toCharArray();for(int n=0;n<hash.length;n++){int b=hash[n]&255;out[n*2]=hex[b>>>4];out[n*2+1]=hex[b&15];}return new String(out);}catch(Exception e){throw new IllegalStateException(e);}}
    private VoiceIdentity(){}
}
