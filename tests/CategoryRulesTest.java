package mz.bam.seeky.android;
/** Cada ficheiro na sua categoria (5.22). */
public final class CategoryRulesTest {
 static int checks;
 static void is(String name,String mime,String expected){String got=Rules.category(name,mime);if(!got.equals(expected))throw new AssertionError(name+" ("+mime+") -> "+got+", expected "+expected);checks++;}
 public static void main(String[] args){
  is("filme.mkv","application/octet-stream","Vídeos");is("aula.avi","","Vídeos");is("clip.webm","video/webm","Vídeos");is("video.3gp","","Vídeos");is("x.mp4","video/mp4","Vídeos");
  is("canção.mp3","","Áudio");is("voz.opus","application/octet-stream","Áudio");is("livro.m4b","","Áudio");is("nota.amr","","Áudio");
  is("foto.jpg","","Imagens");is("foto.HEIC","","Imagens");is("arte.webp","image/webp","Imagens");is("logo.svg","","Imagens");
  is("relatorio.pdf","","PDF");is("sem_extensao","application/pdf","PDF");
  is("app.apk","","Aplicações");is("jogo.xapk","","Aplicações");is("pacote.apkm","","Aplicações");is("instalar","application/vnd.android.package-archive","Aplicações");
  is("fotos.zip","","Arquivos");is("copia.tar.gz","","Arquivos");is("dados.7z","","Arquivos");is("disco.iso","","Arquivos");is("x.rar","","Arquivos");
  is("carta.docx","","Documentos");is("contas.xlsx","","Documentos");is("slides.pptx","","Documentos");is("livro.epub","","Documentos");is("livro.mobi","","Documentos");
  is("texto.rtf","","Documentos");is("folha.ods","","Documentos");is("notas.txt","text/plain","Documentos");is("legendas.srt","","Documentos");is("joao.usx","","Documentos");
  is("desconhecido.xyz","","Outros");is("dados.bin","application/octet-stream","Outros");
  System.out.println(checks+" category checks");
 }
}
