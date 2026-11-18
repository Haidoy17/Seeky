package mz.bam.seeky.android;
import java.util.*;
/** Normalização de leitura: o que a voz diz e o mapa de posições para o destaque. */
public final class SpeechNormalizerTest {
 static int checks;
 static void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 static String say(String text,String language){return SpeechText.forVoice(text,language).text;}
 static void same(String text,String language,String expected,String label){String actual=say(text,language);if(!actual.equals(expected))throw new AssertionError(label+"\n expected: "+expected+"\n actual:   "+actual);checks++;System.out.println("PASS "+label);}
 public static void main(String[] args){
  same("O Dr. Silva e a Sra. Maria chegaram.","pt","O Doutor Silva e a Senhora Maria chegaram.","Portuguese courtesy titles are read in full");
  same("Veja a pág. 12 e o vol. 3.","pt","Veja a página 12 e o volume 3.","page and volume abbreviations expanded");
  same("Livros, cadernos, etc. Tudo pronto.","pt","Livros, cadernos, etcétera. Tudo pronto.","etc keeps its sentence stop");
  same("Capítulo IV","pt","Capítulo 4","Roman chapter number becomes Arabic");
  same("Século XXI e Parte II","pt","Século 21 e Parte 2","century and part numerals converted");
  same("Chapter XII begins","en","Chapter 12 begins","English chapter numerals converted");
  same("O rei Henrique VIII","pt","O rei Henrique VIII","Roman numerals outside section words are left alone");
  same("Capítulo IIII","pt","Capítulo IIII","non-canonical Roman numerals are not guessed");
  same("Visite https://www.exemplo.co.mz/livros?id=4 hoje.","pt","Visite exemplo.co.mz hoje.","URL read by domain only and sentence stop preserved");
  same("Introdução ........ 5","pt","Introdução   5","table of contents leaders are silent");
  same("Isto é verdade!!!","pt","Isto é verdade!","repeated exclamation collapsed");
  same("• Primeiro\n\n• Segundo","pt","Primeiro\n\nSegundo","list bullets at line start are silent");
  same("Uma subida de 15% e 30 °C","pt","Uma subida de 15 por cento e 30 graus Celsius","percent and Celsius in Portuguese");
  same("A growth of 15%","en","A growth of 15 percent","percent in English");
  same("Ver § 3","pt","Ver parágrafo 3","section sign read in Portuguese");
  same("INTRODUÇÃO GERAL","pt","Introdução GERAL","long upper-case headings are not spelled; short words kept");
  same("A UNESCO e a ONU","pt","A Unesco e a ONU","six-letter acronyms read as words, short ones untouched");
  same("Mr. Smith vs. Jones, e.g. today","en","Mister Smith versus Jones, for example today","English abbreviations expanded");
  same("O Dr. Silva","en","O Doctor Silva","language selects the abbreviation set");
  same("O Dr. Silva","fr","O Dr. Silva","unknown languages keep language-specific abbreviations");
  same("texto *importante* aqui e 2*3","pt","texto importante aqui e 2*3","emphasis asterisks removed but multiplication kept");
  // Mapa de posições: cada carácter dito aponta para dentro do original e é monótono.
  String source="O Dr. Silva leu o Capítulo IV em https://exemplo.pt/x.";
  SpeechText.Prepared p=SpeechText.forVoice(source,"pt");
  int previous=0;boolean monotonic=true,inside=true;
  for(int i=0;i<p.text.length();i++){int o=p.sourceOffset(i);if(o<previous)monotonic=false;if(o<0||o>source.length())inside=false;previous=o;}
  ok(monotonic&&inside,"offset map stays monotonic and inside the source");
  int doctor=p.text.indexOf("Doutor");ok(p.sourceOffset(doctor)==source.indexOf("Dr."),"expanded word highlights the original abbreviation");
  int silva=p.text.indexOf("Silva");ok(p.sourceOffset(silva)==source.indexOf("Silva"),"text after a replacement keeps exact offsets");
  // O dicionário pessoal vence a regra automática.
  List<PronunciationRules.Rule> rules=Collections.singletonList(new PronunciationRules.Rule("Dr.","Doutorzinho"));
  ok(PronunciationRules.apply(SpeechText.forVoice("O Dr. Silva","pt",rules),rules).text.equals("O Doutorzinho Silva"),"personal pronunciation overrides automatic expansion");
  ok(SpeechNormalizer.roman("MCMXCIV")==1994&&SpeechNormalizer.roman("IC")==0&&SpeechNormalizer.roman("")==0,"Roman parser accepts only canonical numerals");
  ok(SpeechNormalizer.host("https://user@www.site.org:8080/a#b").equals("site.org"),"URL host strips credentials, port and path");
  ok(SpeechText.forVoice("","pt").text.isEmpty(),"empty text stays empty");
  ok(SpeechPolicy.regionScore("PT","MZ","pt")>SpeechPolicy.regionScore("BR","MZ","pt"),"Mozambique prefers European Portuguese voices");
  ok(SpeechPolicy.regionScore("BR","BR","pt")>SpeechPolicy.regionScore("PT","BR","pt"),"Brazil prefers Brazilian Portuguese voices");
  ok(SpeechPolicy.regionScore("GB","MZ","en")>SpeechPolicy.regionScore("US","MZ","en"),"Mozambique prefers British English voices");
  ok(SpeechPolicy.regionScore("US","US","en")==3&&SpeechPolicy.regionScore("","","fr")==1,"own country wins and unknown regions stay neutral");
  System.out.println(checks+" speech normalizer checks");
 }
}
