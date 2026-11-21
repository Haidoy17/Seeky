package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/** 5.36: FB2, MOBI/AZW3/PRC, ODT, RTF, MHTML and CBZ books become EPUBs the reader opens like any other. */
public final class EbookConvert536Test {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    static File dir;

    public static void main(String[] args)throws Exception{
        dir=java.nio.file.Files.createTempDirectory("seeky-ebook").toFile();
        names();fb2();fb2Cyrillic();fbz();mobi();mobiTrailingAndLz77();kf8();drm();palmdoc();odt();rtf();mhtml();cbz();html();deterministic();
        System.out.println("EbookConvert536Test OK · "+checks+" verificações");
    }
    static File write(String name,byte[] data)throws IOException{File f=new File(dir,name);try(FileOutputStream o=new FileOutputStream(f)){o.write(data);}return f;}
    static File epub(File in,String name)throws IOException{File out=new File(dir,name+".epub");EbookConvert.convert(in,name,out);check(out.length()>0,"EPUB escrito para "+name);return out;}
    static String all(BookArchive book)throws IOException{StringBuilder s=new StringBuilder();for(int i=0;i<book.chapters.size();i++)s.append(book.chapter(i)).append('\n');return s.toString();}
    static boolean hasEntry(File epub,String prefix)throws IOException{try(ZipFile z=new ZipFile(epub)){for(Enumeration<? extends ZipEntry> e=z.entries();e.hasMoreElements();)if(e.nextElement().getName().startsWith(prefix))return true;}return false;}
    static String opf(File epub)throws IOException{try(ZipFile z=new ZipFile(epub)){return new String(EbookConvert.zipRead(z,z.getEntry("OEBPS/content.opf"),1<<20),StandardCharsets.UTF_8);}}

    static void names(){
        check(EbookConvert.supported("Livro.FB2")&&EbookConvert.supported("x.fb2.zip")&&EbookConvert.supported("a.azw3")&&EbookConvert.supported("b.prc")&&EbookConvert.supported("c.mhtml")&&EbookConvert.supported("d.cbz")&&EbookConvert.supported("e.odt")&&EbookConvert.supported("f.rtf"),"formatos novos reconhecidos");
        check(!EbookConvert.supported("a.pdf")&&!EbookConvert.supported("a.epub")&&!EbookConvert.supported("a.zip")&&!EbookConvert.supported("a.cbr")&&!EbookConvert.supported("a.djvu"),"formatos já suportados ou impossíveis ficam de fora");
        check(EbookConvert.epubName("Pasta/O livro.fb2.zip").equals("O livro.epub")&&EbookConvert.epubName("Dom Casmurro.mobi").equals("Dom Casmurro.epub"),"nome do EPUB");
        check(EbookConvert.Comic.natural("p2.png","p10.png")<0&&EbookConvert.Comic.natural("p10.png","p9.png")>0&&EbookConvert.Comic.natural("a.png","B.png")<0,"ordem natural das páginas");
    }
    static String fb2Source(String title,String encoding){
        return "<?xml version=\"1.0\" encoding=\""+encoding+"\"?>\n<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\" xmlns:l=\"http://www.w3.org/1999/xlink\">"
            +"<description><title-info><author><first-name>Machado</first-name><last-name>de Assis</last-name></author><book-title>"+title+"</book-title><lang>pt</lang><coverpage><image l:href=\"#capa.png\"/></coverpage></title-info></description>"
            +"<body><title><p>"+title+"</p></title>"
            +"<section><title><p>Capítulo I</p><p>Do título</p></title><p>Uma noite destas, vindo da cidade para o Engenho Novo, encontrei&nbsp;no trem um rapaz <emphasis>aqui do bairro</emphasis>, que eu conheço de <strong>vista</strong> &amp; de chapéu.</p><empty-line/><p>Segundo parágrafo<a l:href=\"#n1\" type=\"note\">1</a>.</p><image l:href=\"#capa.png\"/></section>"
            +"<section><title><p>Capítulo II</p></title><section><title><p>Primeira parte</p></title><p>Texto da parte um.</p></section><section><title><p>Segunda parte</p></title><poem><stanza><v>Verso um</v><v>Verso dois</v></stanza></poem></section></section>"
            +"</body><body name=\"notes\"><section id=\"n1\"><title><p>1</p></title><p>Nota de rodapé.</p></section></body>"
            +"<binary id=\"capa.png\" content-type=\"image/png\">"+Base64.getEncoder().encodeToString(PNG)+"</binary></FictionBook>";
    }
    static void fb2()throws Exception{
        File in=write("dom.fb2",fb2Source("Dom Casmurro","UTF-8").getBytes(StandardCharsets.UTF_8));File out=epub(in,"dom.fb2");
        try(BookArchive book=new BookArchive(out,true)){
            check(book.title.equals("Dom Casmurro"),"título FB2: "+book.title);check(book.author.equals("Machado de Assis"),"autor FB2: "+book.author);
            String text=all(book);check(text.contains("Uma noite destas")&&text.contains("aqui do bairro")&&text.contains("vista & de chapéu"),"texto FB2 com entidades");
            check(text.contains("Texto da parte um")&&text.contains("Verso dois")&&text.contains("Nota de rodapé"),"secções aninhadas, poemas e notas");
            check(book.chapters.size()>=4,"um capítulo por secção: "+book.chapters.size());
            check(book.names.get(0).equals("Dom Casmurro")&&book.names.contains("Capítulo I — Do título")&&book.names.contains("Primeira parte")&&book.names.contains("Notas"),"índice com os títulos das secções: "+book.names);
        }
        check(hasEntry(out,"OEBPS/images/")&&opf(out).contains("cover-image"),"capa FB2 incluída");
    }
    static void fb2Cyrillic()throws Exception{
        File in=write("war.fb2",fb2Source("Война и мир","windows-1251").getBytes(Charset.forName("windows-1251")));File out=epub(in,"war.fb2");
        try(BookArchive book=new BookArchive(out,true)){check(book.title.equals("Война и мир"),"codificação windows-1251 respeitada: "+book.title);}
    }
    static void fbz()throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){z.putNextEntry(new ZipEntry("livro.fb2"));z.write(fb2Source("Comprimido","UTF-8").getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        File out=epub(write("livro.fb2.zip",b.toByteArray()),"livro.fb2.zip");try(BookArchive book=new BookArchive(out,true)){check(book.title.equals("Comprimido"),"FB2 dentro de ZIP");}
    }

    // ----- MOBI builder -----
    static byte[] be16(int v){return new byte[]{(byte)(v>>8),(byte)v};}
    static byte[] be32(long v){return new byte[]{(byte)(v>>24),(byte)(v>>16),(byte)(v>>8),(byte)v};}
    /** Literal PalmDOC encoding (every byte is valid as-is or in a one-byte literal run). */
    static byte[] palmLiteral(byte[] in){ByteArrayOutputStream o=new ByteArrayOutputStream();for(byte x:in){int c=x&255;if(c==0||c>=0x09&&c<0x80)o.write(c);else{o.write(1);o.write(c);}}return o.toByteArray();}
    static byte[] pdb(String type,List<byte[]> records){
        ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] name=Arrays.copyOf("Livro_de_teste".getBytes(StandardCharsets.US_ASCII),32);o.write(name,0,32);
        o.write(new byte[28],0,28);o.write(type.getBytes(StandardCharsets.US_ASCII),0,8);o.write(new byte[8],0,8);o.write(be16(records.size()),0,2);
        int offset=78+records.size()*8+2;for(int i=0;i<records.size();i++){o.write(be32(offset),0,4);o.write(be32(i*2),0,4);offset+=records.get(i).length;}o.write(new byte[2],0,2);
        for(byte[] r:records)o.write(r,0,r.length);return o.toByteArray();
    }
    static byte[] exth(Object... pairs){ByteArrayOutputStream body=new ByteArrayOutputStream();for(int i=0;i<pairs.length;i+=2){byte[] v=pairs[i+1] instanceof byte[]?(byte[])pairs[i+1]:((String)pairs[i+1]).getBytes(StandardCharsets.UTF_8);body.write(be32((Integer)pairs[i]),0,4);body.write(be32(v.length+8),0,4);body.write(v,0,v.length);}ByteArrayOutputStream o=new ByteArrayOutputStream();o.write('E');o.write('X');o.write('T');o.write('H');o.write(be32(12+body.size()),0,4);o.write(be32(pairs.length/2),0,4);byte[] b=body.toByteArray();o.write(b,0,b.length);while(o.size()%4!=0)o.write(0);return o.toByteArray();}
    static byte[] record0(int compression,long textLength,int textRecords,int encryption,int version,int firstImage,int extraFlags,int fdst,byte[] exth){
        byte[] r=new byte[16+0xE8];System.arraycopy(be16(compression),0,r,0,2);System.arraycopy(be32(textLength),0,r,4,4);System.arraycopy(be16(textRecords),0,r,8,2);System.arraycopy(be16(4096),0,r,10,2);System.arraycopy(be16(encryption),0,r,12,2);
        r[16]='M';r[17]='O';r[18]='B';r[19]='I';System.arraycopy(be32(0xE8),0,r,20,4);System.arraycopy(be32(2),0,r,24,4);System.arraycopy(be32(65001),0,r,28,4);System.arraycopy(be32(version),0,r,0x68,4);System.arraycopy(be32(firstImage),0,r,0x6c,4);
        System.arraycopy(be32(exth==null?0:0x40),0,r,0x80,4);System.arraycopy(be32(fdst),0,r,0xC0,4);System.arraycopy(be16(extraFlags),0,r,0xF2,2);
        if(exth==null)return r;byte[] full=Arrays.copyOf(r,r.length+exth.length);System.arraycopy(exth,0,full,r.length,exth.length);return full;
    }
    static void mobi()throws Exception{
        String html="<html><head><guide><reference type=\"toc\"/></guide><style>p{x}</style></head><body><h2>Capítulo um</h2><p>Primeiro parágrafo com <b>negrito</b> e acentos: ação.</p><script>alert(1)</script><mbp:pagebreak/><h2>Capítulo dois</h2><p>Fim da história.</p><p><img recindex=\"00001\"/></p></body></html>";
        byte[] text=html.getBytes(StandardCharsets.UTF_8);List<byte[]> recs=new ArrayList<>();
        recs.add(record0(2,text.length,1,0,6,2,0,0,exth(100,"Ana Autora",503,"Livro MOBI",524,"pt",201,be32(0))));recs.add(palmLiteral(text));recs.add(PNG);
        File out=epub(write("livro.mobi",pdb("BOOKMOBI",recs)),"livro.mobi");
        try(BookArchive book=new BookArchive(out,true)){
            check(book.title.equals("Livro MOBI")&&book.author.equals("Ana Autora"),"metadados EXTH: "+book.title+" / "+book.author);
            check(book.chapters.size()==2,"quebra de página divide capítulos: "+book.chapters.size());
            String t=all(book);check(t.contains("Primeiro parágrafo com negrito e acentos: ação.")&&t.contains("Fim da história"),"texto MOBI em UTF-8: "+t);
            check(!t.contains("alert")&&!t.contains("p{x}"),"scripts e estilos removidos");
        }
        check(hasEntry(out,"OEBPS/images/")&&opf(out).contains("cover-image"),"imagem e capa MOBI");
    }
    static void mobiTrailingAndLz77()throws Exception{
        // "abcabcabc x" with a back-reference and the space+letter code, then trailing entries (index entry of 2 bytes, multibyte byte).
        ByteArrayOutputStream r=new ByteArrayOutputStream();r.write('<');r.write('p');r.write('>');r.write('a');r.write('b');r.write('c');int m=(3<<3)|(6-3);r.write(0x80|(m>>8));r.write(m&255);r.write(0x78^0x80);r.write('<');r.write('/');r.write('p');r.write('>');
        r.write(0x00);r.write(0xAA);r.write(0x82);
        byte[] expected="<p>abcabcabc x</p>".getBytes(StandardCharsets.US_ASCII);
        check(Arrays.equals(EbookConvert.Mobi.palmdoc(Arrays.copyOf(r.toByteArray(),r.size()-3)),expected),"LZ77 PalmDOC: "+new String(EbookConvert.Mobi.palmdoc(Arrays.copyOf(r.toByteArray(),r.size()-3)),StandardCharsets.US_ASCII));
        check(EbookConvert.Mobi.trailing(r.toByteArray(),3)==3,"entradas finais removidas: "+EbookConvert.Mobi.trailing(r.toByteArray(),3));
        List<byte[]> recs=new ArrayList<>();recs.add(record0(2,expected.length,1,0,6,-1,3,0,null));recs.add(r.toByteArray());
        File out=epub(write("lz.prc",pdb("BOOKMOBI",recs)),"lz.prc");try(BookArchive book=new BookArchive(out,true)){check(all(book).contains("abcabcabc x"),"PRC com compressão e entradas finais");check(book.title.equals("Livro de teste"),"título pelo nome do PDB: "+book.title);}
    }
    static void kf8()throws Exception{
        String html="<html><head><title>Livro KF8</title></head><body><div><p>Parte um do livro novo.</p></div></body></html><html><head></head><body><div><p>Parte dois <img src=\"kindle:embed:0001?mime=image/png\"/></p></div></body></html>";
        String css="p{color:red;}";byte[] text=(html+css).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream fd=new ByteArrayOutputStream();fd.write('F');fd.write('D');fd.write('S');fd.write('T');fd.write(be32(12),0,4);fd.write(be32(2),0,4);fd.write(be32(0),0,4);fd.write(be32(html.getBytes(StandardCharsets.UTF_8).length),0,4);fd.write(be32(html.getBytes(StandardCharsets.UTF_8).length),0,4);fd.write(be32(text.length),0,4);
        List<byte[]> recs=new ArrayList<>();recs.add(record0(1,text.length,1,0,8,2,0,3,exth(100,"K. Autor")));recs.add(text);recs.add(PNG);recs.add(fd.toByteArray());
        File out=epub(write("novo.azw3",pdb("BOOKMOBI",recs)),"novo.azw3");
        try(BookArchive book=new BookArchive(out,true)){String t=all(book);check(book.chapters.size()==2,"cada ficheiro KF8 é um capítulo: "+book.chapters.size());check(t.contains("Parte um do livro novo")&&t.contains("Parte dois"),"texto KF8");check(!t.contains("color:red"),"CSS do KF8 fora do texto");check(book.title.equals("Livro de teste"),"título KF8 sem EXTH 503 usa o nome: "+book.title);}
        check(hasEntry(out,"OEBPS/images/"),"imagem kindle:embed");
    }
    static void drm()throws Exception{
        List<byte[]> recs=new ArrayList<>();recs.add(record0(2,10,1,2,6,-1,0,0,null));recs.add(palmLiteral("<p>x</p>".getBytes(StandardCharsets.US_ASCII)));
        try{epub(write("drm.azw",pdb("BOOKMOBI",recs)),"drm.azw");check(false,"DRM devia ser recusado");}catch(IOException e){check(e.getMessage().contains("DRM"),"mensagem clara de DRM: "+e.getMessage());}
    }
    static void palmdoc()throws Exception{
        byte[] text="Linha um do texto.\nLinha dois com acentuação.\n".getBytes(Charset.forName("windows-1252"));byte[] r0=new byte[16];System.arraycopy(be16(1),0,r0,0,2);System.arraycopy(be32(text.length),0,r0,4,4);System.arraycopy(be16(1),0,r0,8,2);
        File out=epub(write("antigo.pdb.prc",pdb("TEXtREAd",Arrays.asList(r0,text))),"antigo.prc");try(BookArchive book=new BookArchive(out,true)){String t=all(book);check(t.contains("Linha um do texto.")&&t.contains("Linha dois com acentuação."),"PalmDOC simples: "+t);}
    }
    static void odt()throws Exception{
        String content="<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"><office:body><office:text>"
            +"<text:h text:outline-level=\"1\">Introdução</text:h><text:p>Olá<text:s text:c=\"2\"/>mundo <text:span>com estilo</text:span>.<text:line-break/>Nova linha.</text:p>"
            +"<text:list><text:list-item><text:p>Item A</text:p></text:list-item><text:list-item><text:p>Item B</text:p></text:list-item></text:list>"
            +"<table:table><table:table-row><table:table-cell><text:p>Célula 1</text:p></table:table-cell><table:table-cell><text:p>Célula 2</text:p></table:table-cell></table:table-row></table:table>"
            +"<text:p><draw:frame><draw:image xlink:href=\"Pictures/a.png\"/></draw:frame></text:p>"
            +"<text:h text:outline-level=\"1\">Segundo capítulo</text:h><text:p>Conteúdo final.</text:p></office:text></office:body></office:document-content>";
        String meta="<?xml version=\"1.0\"?><office:document-meta xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:meta=\"urn:oasis:names:tc:opendocument:xmlns:meta:1.0\"><office:meta><dc:title>Relatório ODT</dc:title><meta:initial-creator>Carlos</meta:initial-creator></office:meta></office:document-meta>";
        ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){for(String[] e:new String[][]{{"mimetype","application/vnd.oasis.opendocument.text"},{"content.xml",content},{"meta.xml",meta}}){z.putNextEntry(new ZipEntry(e[0]));z.write(e[1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}z.putNextEntry(new ZipEntry("Pictures/a.png"));z.write(PNG);z.closeEntry();}
        File out=epub(write("doc.odt",b.toByteArray()),"doc.odt");
        try(BookArchive book=new BookArchive(out,true)){String t=all(book);check(book.title.equals("Relatório ODT")&&book.author.equals("Carlos"),"metadados ODT");check(book.chapters.size()==2,"um capítulo por título de nível 1: "+book.chapters.size());check(t.contains("Olá   mundo com estilo.")||t.contains("Olá  mundo com estilo."),"espaços e estilos ODT: "+t);check(t.contains("Item B")&&t.contains("Célula 2")&&t.contains("Conteúdo final."),"listas, tabelas e texto ODT");}
        check(hasEntry(out,"OEBPS/images/"),"imagem ODT");
    }
    static void rtf()throws Exception{
        String src="{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\fswiss Arial;}}{\\colortbl;\\red0\\green0\\blue0;}{\\info{\\title O t\\'edtulo}{\\author Ana Maria}}{\\*\\generator Teste 1.0;}\\viewkind4\\uc1\\pard\\f0\\fs24 Primeiro par\\'e1grafo com {\\b negrito}.\\par Segundo \\u8212? fim\\par{\\pict\\pngblip 89504e47}Terceiro\\tab com tabula\\'e7\\'e3o.\\par}";
        File out=epub(write("carta.rtf",src.getBytes(StandardCharsets.ISO_8859_1)),"carta.rtf");
        try(BookArchive book=new BookArchive(out,true)){String t=all(book);check(book.title.equals("O título")&&book.author.equals("Ana Maria"),"metadados RTF: "+book.title+" / "+book.author);check(t.contains("Primeiro parágrafo com negrito.")&&t.contains("Segundo — fim")&&t.contains("tabulação"),"texto RTF: "+t);check(!t.contains("Arial")&&!t.contains("Teste 1.0")&&!t.contains("89504e47"),"tabelas e imagens RTF ignoradas");}
    }
    static void mhtml()throws Exception{
        String b64=Base64.getMimeEncoder().encodeToString(PNG);
        String src="From: <Guardado pelo navegador>\r\nSubject: =?utf-8?Q?P=C3=A1gina?=\r\nMIME-Version: 1.0\r\nContent-Type: multipart/related;\r\n\ttype=\"text/html\";\r\n\tboundary=\"----MultipartBoundary--abc\"\r\n\r\n------MultipartBoundary--abc\r\nContent-Type: text/html; charset=\"utf-8\"\r\nContent-Transfer-Encoding: quoted-printable\r\nContent-Location: https://exemplo.org/artigo/\r\n\r\n<html><head><title>P=C3=A1gina guardada</title></head><body><h1>Cora=C3=A7=C3=A3o</h1><p>Texto com uma linha muito longa que foi partida =\r\nem duas.</p><img src=3D\"img/foto.png\"></body></html>\r\n------MultipartBoundary--abc\r\nContent-Type: image/png\r\nContent-Transfer-Encoding: base64\r\nContent-Location: https://exemplo.org/artigo/img/foto.png\r\n\r\n"+b64+"\r\n------MultipartBoundary--abc--\r\n";
        File out=epub(write("pagina.mhtml",src.getBytes(StandardCharsets.ISO_8859_1)),"pagina.mhtml");
        try(BookArchive book=new BookArchive(out,true)){String t=all(book);check(book.title.equals("Página guardada"),"título MHTML: "+book.title);check(t.contains("Coração")&&t.contains("partida em duas."),"quoted-printable e linhas partidas: "+t);}
        check(hasEntry(out,"OEBPS/images/"),"imagem MHTML pela localização relativa");
    }
    static void cbz()throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){for(String n:new String[]{"pag/010.png","pag/002.png","pag/001.png","__MACOSX/._001.png"}){z.putNextEntry(new ZipEntry(n));z.write(PNG);z.closeEntry();}z.putNextEntry(new ZipEntry("ComicInfo.xml"));z.write("<?xml version=\"1.0\"?><ComicInfo><Title>Aventura</Title><Writer>Rui</Writer></ComicInfo>".getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        File out=epub(write("bd.cbz",b.toByteArray()),"bd.cbz");
        try(BookArchive book=new BookArchive(out,true)){check(book.title.equals("Aventura")&&book.author.equals("Rui"),"ComicInfo");check(book.chapters.size()==3,"três páginas: "+book.chapters.size());}
        try(ZipFile z=new ZipFile(out)){String first=new String(EbookConvert.zipRead(z,z.getEntry("OEBPS/text/c0001.xhtml"),1<<16),StandardCharsets.UTF_8);check(first.contains("images/p0001.png"),"primeira página");check(z.getEntry("OEBPS/images/p0003.png")!=null,"páginas copiadas");}
        check(opf(out).contains("cover-image"),"primeira página é a capa");
    }
    static void html()throws Exception{
        EbookConvert.Book b=new EbookConvert.Book();EbookConvert.Html h=new EbookConvert.Html(b,null,false);
        h.feed("<p>Um <b>dois<p>três</b> <i>quatro</i> &eacute; &#233; &#x41; &unknown; <a href='javascript:x'>ligação</a><table><tr><td>a<td>b<tr><td>c</table><ul><li>x<li>y</ul><!-- c --><p onclick=\"evil()\">fim < 5</p>");h.finish();
        check(b.chapters.size()==1,"um capítulo");String body=b.chapters.get(0).body;
        BookArchive.xml(("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>"+body+"</body></html>").getBytes(StandardCharsets.UTF_8));checks++;
        check(body.equals("<p>Um <b>dois</b></p><p>três <i>quatro</i> é é A &amp;unknown; ligação</p><table><tr><td>a</td><td>b</td></tr><tr><td>c</td></tr></table><ul><li>x</li><li>y</li></ul><p>fim &lt; 5</p>"),"HTML limpo e bem formado: "+body);
        check(!body.contains("onclick")&&!body.contains("javascript")&&!body.contains("href"),"sem atributos activos");
    }
    static void deterministic()throws Exception{
        File in=write("rep.fb2",fb2Source("Repetido","UTF-8").getBytes(StandardCharsets.UTF_8));File a=new File(dir,"a.epub"),b=new File(dir,"b.epub");EbookConvert.convert(in,"rep.fb2",a);EbookConvert.convert(in,"rep.fb2",b);
        check(Arrays.equals(java.nio.file.Files.readAllBytes(a.toPath()),java.nio.file.Files.readAllBytes(b.toPath())),"conversão determinística (repetidos detectados)");
        try(ZipFile z=new ZipFile(a)){ZipEntry first=z.entries().nextElement();check(first.getName().equals("mimetype")&&first.getMethod()==ZipEntry.STORED,"mimetype primeiro e sem compressão");}
    }
}
