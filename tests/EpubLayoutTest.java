package mz.bam.seeky.android;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;
public final class EpubLayoutTest {
 static int checks;static void ok(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
 static String source(){return "<!doctype html><html><head><meta content='width=740' NAME='viewport'><link rel='stylesheet' href='book.css'></head><body><div class='chapter'><h1><u>Capítulo 1 — Um título que ocupa várias linhas</u></h1><p class='intro'><b>Uma passagem introdutória em negrito.</b></p><p class='first'>Este é um documento de ensaio para verificar a leitura com formatação. O texto deve caber no ecrã e continuar visível até à última palavra da linha.</p><p>Uma segunda passagem mantém a fonte, os estilos e a ordem do livro. <i>As palavras em itálico também permanecem.</i> A leitura chega até ao MARCADOR_FINAL.</p><img alt='Imagem de ensaio' width='900' height='300' src='image.svg'><table><tr><th>Disciplina</th><th>Conteúdo</th></tr><tr><td>Ciências</td><td>Um conteúdo suficientemente longo para verificar a quebra dentro da célula.</td></tr></table><pre>linha de código com um identificador_muito_longo_sem_espaços_que_deve_continuar_visível</pre><script>window.bookScriptRan=true</script></div></body></html>";}
 static String bookCss(){return "html{width:740px}body{width:700px;margin:0;padding:24px;font-family:Georgia,serif;color:#222}.chapter{width:680px;min-width:680px}h1{width:640px;color:#174aa4;text-align:center;font-size:32px}p{width:640px;font-size:21px;line-height:1.45;margin-left:12px;margin-right:12px}.intro{text-align:center}.first:first-letter{float:left;font-size:3em;line-height:1}table{width:800px;border-collapse:collapse}td,th{border:1px solid #555;padding:8px}pre{width:900px}";}
 public static void main(String[] args)throws Exception{
  EpubLayout.Options defaults=new EpubLayout.Options(true,16,100,0,0);String input=source(),html=EpubLayout.html(input,defaults),css=EpubLayout.css(defaults);
  ok(html.contains("<u>Capítulo 1")&&html.contains("<b>Uma passagem")&&html.contains("<i>As palavras")&&html.contains("MARCADOR_FINAL"),"EPUB adaptation retains rich markup and complete text");
  ok(html.contains("book.css")&&html.contains("image.svg")&&html.contains("<table>"),"EPUB author stylesheet, image and table remain in the reading document");
  ok(html.toLowerCase().split("name=\"viewport\"",-1).length==2&&!html.contains("width=740"),"reader viewport replaces conflicting book viewport");
  ok(html.indexOf("Content-Security-Policy")<html.indexOf("<script>")&&html.contains("script-src 'none'"),"book scripts remain denied before document content");
  ok(html.indexOf(EpubLayout.CSS_URL)>html.indexOf("book.css"),"reader width stylesheet follows author stylesheet");
  ok(!css.contains("overflow:hidden")&&!css.contains("overflow-x:hidden"),"fit does not conceal horizontal overflow as a clipping workaround");
  ok(!css.contains("font-family")&&!css.contains("font-size")&&!css.contains("text-align")&&!css.contains("line-height"),"default mobile fit keeps author typography, alignment and line spacing");
  String custom=EpubLayout.css(new EpubLayout.Options(true,28,150,160,2));ok(custom.contains("28px")&&custom.contains("line-height:1.6")&&custom.contains("text-align:justify"),"explicit margins, spacing and alignment are applied to presentation only");
  ok(EpubLayout.css(new EpubLayout.Options(false,16,100,0,0)).isEmpty(),"book-layout mode leaves author geometry available");
  ok(EpubLayout.html("<html><body><p>Sem head</p></body></html>",defaults).contains("<head><meta"),"EPUB without head receives a valid reader head before body");
  EpubLayout.Options bounded=new EpubLayout.Options(true,-1,999,999,999);ok(bounded.margin==8&&bounded.zoom==175&&bounded.spacing==220&&bounded.align==2,"saved layout settings are bounded");
  String folder=System.getProperty("seeky.epub.fixture","");if(!folder.isEmpty()){Path root=Paths.get(folder);Files.createDirectories(root);write(root,"source.html",input);write(root,"fitted.html",html);write(root,"book.css",bookCss());write(root,"fit.css",css);write(root,"custom.css",custom);write(root,"image.svg","<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 900 300'><rect width='900' height='300' fill='#cde6ed'/><circle cx='450' cy='150' r='90' fill='#174aa4'/></svg>");}
  System.out.println("PASS "+checks+" EPUB presentation and viewport checks");
 }
 static void write(Path root,String name,String data)throws IOException{Files.write(root.resolve(name),data.getBytes(StandardCharsets.UTF_8));}
}
