package mz.bam.seeky.android;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Offline text reader for DOCX and EPUB. No extraction to disk, scripts or remote resources. */
final class BookArchive implements AutoCloseable {
 final Map<Integer,List<String>> speechTitles=new LinkedHashMap<Integer,List<String>>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Integer,List<String>> entry){return size()>16;}};
 final Map<Integer,String> headings=new LinkedHashMap<Integer,String>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<Integer,String> entry){return size()>16;}};final ZipFile zip;final List<String> chapters=new ArrayList<>(),names=new ArrayList<>();String title="",author="";final boolean epub;
 static final String NO_TEXT="Este capítulo não contém texto legível. Use Abrir noutra aplicação para ver imagens ou o formato completo.";
 static final int XML_LIMIT=2097152,CHAPTER_LIMIT=4194304;
 BookArchive(File file,boolean epub)throws IOException {
  if(file.length()>2147483648L)throw new IOException("Limite de 2 GB para o arquivo deste leitor de livros.");this.epub=epub;zip=new ZipFile(file);
  try{if(zip.size()>12000)throw new IOException("Arquivo com demasiadas partes.");
   if(epub)loadEpub();else{chapters.add("word/document.xml");names.add("Documento");if(zip.getEntry("docProps/core.xml")!=null){Document core=xml(read("docProps/core.xml",XML_LIMIT));title=first(core,"title");author=first(core,"creator");}}
  }catch(Exception e){zip.close();throw e instanceof IOException?(IOException)e:new IOException("Documento inválido ou protegido.",e);}
 }
 static String xmlSource(byte[] data)throws IOException {
  java.nio.charset.Charset charset=StandardCharsets.UTF_8;if(data.length>=2){if((data[0]&255)==255&&(data[1]&255)==254||data[0]=='<'&&data[1]==0)charset=StandardCharsets.UTF_16LE;else if((data[0]&255)==254&&(data[1]&255)==255||data[0]==0&&data[1]=='<')charset=StandardCharsets.UTF_16BE;}
  String source=new String(data,charset);if(source.startsWith("\ufeff"))source=source.substring(1);String upper=source.toUpperCase(Locale.ROOT);if(upper.contains("<!ENTITY")||upper.matches("(?s).*<!DOCTYPE[^>]*\\[.*"))throw new IOException("Entidades e DTD internos não são permitidos.");
  source=source.replaceAll("(?is)<!DOCTYPE[^>\\[]*>","");if(source.toUpperCase(Locale.ROOT).contains("<!DOCTYPE"))throw new IOException("DTD inválido.");
  source=source.replace("&nbsp;","&#160;").replace("&mdash;","&#8212;").replace("&ndash;","&#8211;").replace("&ldquo;","&#8220;").replace("&rdquo;","&#8221;").replace("&rsquo;","&#8217;");
  depth(source);return source;
 }
 static Document xml(byte[] data)throws IOException {try{
  String source=xmlSource(data);
  // Android's DOM factory rejects SAX feature toggles. Remove all DTD declarations first,
  // reject entity definitions, disable entity expansion and resolve external identifiers to empty.
  DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);f.setValidating(false);
  DocumentBuilder builder=f.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));return builder.parse(new InputSource(new StringReader(source)));
 }catch(Exception e){throw new IOException("Não foi possível ler o XML do documento.",e);}}
 static void depth(String xml)throws IOException {int level=0;for(int i=0;i<xml.length();i++){if(xml.charAt(i)!='<')continue;if(xml.startsWith("<!--",i)){int end=xml.indexOf("-->",i+4);if(end<0)throw new IOException("Comentário XML inválido.");i=end+2;continue;}if(xml.startsWith("<![CDATA[",i)){int end=xml.indexOf("]]>",i+9);if(end<0)throw new IOException("CDATA inválido.");i=end+2;continue;}if(xml.startsWith("<?",i)){int end=xml.indexOf("?>",i+2);if(end<0)throw new IOException("Instrução XML inválida.");i=end+1;continue;}boolean close=i+1<xml.length()&&xml.charAt(i+1)=='/';char quote=0;int end=i+1;for(;end<xml.length();end++){char c=xml.charAt(end);if(quote!=0){if(c==quote)quote=0;}else if(c=='\''||c=='"')quote=c;else if(c=='>')break;}if(end>=xml.length())throw new IOException("Etiqueta XML inválida.");if(close)level--;else if(xml.charAt(end-1)!='/')level++;if(level>128)throw new IOException("Documento demasiado aninhado.");i=end;}}
 static String first(Document doc,String name){NodeList nodes=doc.getElementsByTagNameNS("*",name);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
 static String safePath(String path)throws IOException {String p=path.replace('\\','/');if(p.startsWith("/")||p.contains(":"))throw new IOException("Caminho inválido no livro.");ArrayDeque<String> parts=new ArrayDeque<>();for(String part:p.split("/")){if(part.equals("..")){if(parts.isEmpty())throw new IOException("Caminho fora do livro.");parts.removeLast();}else if(!part.equals(".")&&!part.isEmpty())parts.addLast(part);}return String.join("/",parts);}
 byte[] read(String path,int max)throws IOException {ZipEntry entry=zip.getEntry(safePath(path));if(entry==null||entry.isDirectory())throw new IOException("Parte em falta: "+path);if(entry.getSize()>max)throw new IOException("Parte demasiado grande para leitura: "+path);
  try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Limite de expansão do documento.");out.write(b,0,n);}return out.toByteArray();}
 }
 void loadEpub()throws IOException {
  Document container=xml(read("META-INF/container.xml",XML_LIMIT));NodeList roots=container.getElementsByTagNameNS("*","rootfile");if(roots.getLength()==0)throw new IOException("EPUB sem pacote.");String opf=safePath(((Element)roots.item(0)).getAttribute("full-path"));Document pkg=xml(read(opf,XML_LIMIT));title=first(pkg,"title");author=first(pkg,"creator");String base=opf.contains("/")?opf.substring(0,opf.lastIndexOf('/')+1):"";
  Map<String,String> manifest=new HashMap<>();NodeList items=pkg.getElementsByTagNameNS("*","item");for(int i=0;i<items.getLength();i++){Element item=(Element)items.item(i);String type=item.getAttribute("media-type");if(type.equals("application/xhtml+xml")||type.equals("text/html")){String href=item.getAttribute("href").split("#",2)[0];try{href=java.net.URLDecoder.decode(href.replace("+","%2B"),"UTF-8");}catch(Exception e){throw new IOException("Caminho EPUB inválido.");}manifest.put(item.getAttribute("id"),safePath(base+href));}}
  NodeList spine=pkg.getElementsByTagNameNS("*","itemref");for(int i=0;i<spine.getLength();i++){Element item=(Element)spine.item(i);String path=manifest.get(item.getAttribute("idref"));if(path!=null){chapters.add(path);names.add("Capítulo "+(chapters.size()));}}
  if(chapters.isEmpty())throw new IOException("EPUB sem capítulos legíveis. Pode estar protegido.");
  for(int n=0;n<items.getLength();n++){Element item=(Element)items.item(n);if(Arrays.asList(item.getAttribute("properties").split("\\s+")).contains("nav")||item.getAttribute("media-type").equals("application/x-dtbncx+xml")){try{String nav=safePath(base+decode(item.getAttribute("href").split("#",2)[0]));loadNames(nav);}catch(IOException ignored){}}}

 }
 static String decode(String href)throws IOException {try{return java.net.URLDecoder.decode(href.replace("+","%2B"),"UTF-8");}catch(Exception e){throw new IOException("Caminho EPUB inválido.",e);}}
 void loadNames(String path)throws IOException {Document nav=xml(read(path,XML_LIMIT));String base=path.contains("/")?path.substring(0,path.lastIndexOf('/')+1):"";NodeList links=nav.getElementsByTagNameNS("*",path.endsWith(".ncx")?"navPoint":"a");for(int n=0;n<links.getLength();n++){Element element=(Element)links.item(n);String href=element.getAttribute("href"),label=element.getTextContent().trim().replaceAll("\\s+"," ");if(path.endsWith(".ncx")){NodeList content=element.getElementsByTagNameNS("*","content"),text=element.getElementsByTagNameNS("*","navLabel");if(content.getLength()==0||text.getLength()==0)continue;href=((Element)content.item(0)).getAttribute("src");label=text.item(0).getTextContent().trim().replaceAll("\\s+"," ");}try{int chapter=chapters.indexOf(safePath(base+decode(href.split("#",2)[0])));if(chapter>=0&&!label.isEmpty()&&names.get(chapter).equals("Capítulo "+(chapter+1)))names.set(chapter,label.substring(0,Math.min(160,label.length())));}catch(IOException ignored){}}}
 String chapter(int index)throws IOException {if(index<0||index>=chapters.size())throw new IOException("Capítulo inválido.");Document doc=xml(read(chapters.get(index),CHAPTER_LIMIT));headings.put(index,heading(doc,epub));speechTitles.put(index,allHeadings(doc,epub));StringBuilder out=new StringBuilder();
  if(!epub){NodeList paragraphs=doc.getElementsByTagNameNS("*","p");for(int i=0;i<paragraphs.getLength();i++){append(paragraphs.item(i),out,true);out.append("\n\n");}}
  else{NodeList bodies=doc.getElementsByTagNameNS("*","body");append(bodies.getLength()==0?doc.getDocumentElement():bodies.item(0),out,false);}
  if(out.length()>1048576)throw new IOException("Capítulo demasiado extenso.");String text=out.toString().replaceAll("[ \\t]+\n","\n").replaceAll("\n{4,}","\n\n").trim();return text.isEmpty()?NO_TEXT:text;
 }
 static List<String> allHeadings(Document document,boolean epub){List<String> result=new ArrayList<>();int chars=0;if(epub){NodeList nodes=document.getElementsByTagNameNS("*","*");for(int i=0;i<nodes.getLength()&&result.size()<128&&chars<16384;i++){Node node=nodes.item(i);String tag=node.getLocalName();if(tag==null)tag=node.getNodeName();if(!tag.matches("(?i)h[1-6]"))continue;String title=AudioTitles.clean(node.getTextContent());if(!title.isEmpty()&&title.length()<=512){result.add(title);chars+=title.length();}}}else{NodeList paragraphs=document.getElementsByTagNameNS("*","p");for(int i=0;i<paragraphs.getLength()&&result.size()<128&&chars<16384;i++){Element paragraph=(Element)paragraphs.item(i);NodeList styles=paragraph.getElementsByTagNameNS("*","pStyle");for(int n=0;n<styles.getLength();n++){Element style=(Element)styles.item(n);String value=style.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","val");if(value.isEmpty())value=style.getAttribute("w:val");if(value.matches("(?iu)(heading|t[íi]tulo)[ _-]?[1-6]?|title|subtitle")){StringBuilder text=new StringBuilder();append(paragraph,text,true);String title=AudioTitles.clean(text.toString());if(!title.isEmpty()&&title.length()<=512){result.add(title);chars+=title.length();}break;}}}}return result;}
 static String heading(Document document,boolean epub){
  if(epub){for(int level=1;level<=6;level++){NodeList nodes=document.getElementsByTagNameNS("*","h"+level);for(int i=0;i<nodes.getLength();i++){String title=AudioTitles.clean(nodes.item(i).getTextContent());if(!title.isEmpty())return AudioTitles.limit(title,240,720);}}}
  else{NodeList paragraphs=document.getElementsByTagNameNS("*","p");for(int i=0;i<paragraphs.getLength();i++){Element paragraph=(Element)paragraphs.item(i);NodeList styles=paragraph.getElementsByTagNameNS("*","pStyle");for(int n=0;n<styles.getLength();n++){Element style=(Element)styles.item(n);String value=style.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","val");if(value.isEmpty())value=style.getAttribute("w:val");if(value.matches("(?iu)(heading|t[íi]tulo)[ _-]?[1-6]?|title|subtitle")){StringBuilder text=new StringBuilder();append(paragraph,text,true);String title=AudioTitles.clean(text.toString());if(!title.isEmpty())return AudioTitles.limit(title,240,720);}}}}
  return "";
 }
 static void append(Node node,StringBuilder out,boolean word){
  if(out.length()>1048576)return;if(node.getNodeType()==Node.TEXT_NODE){if(!word)out.append(node.getNodeValue());return;}
  String name=node.getLocalName();if(name==null)name=node.getNodeName();name=name.toLowerCase(Locale.ROOT);if(name.equals("script")||name.equals("style"))return;
  if(word&&name.equals("t")){out.append(node.getTextContent());return;}if(name.equals("br")||name.equals("tab")){out.append(name.equals("tab")?'\t':'\n');return;}
  NodeList children=node.getChildNodes();for(int i=0;i<children.getLength();i++)append(children.item(i),out,word);
  if(!word&&(name.matches("p|div|h[1-6]|li|section|tr")))out.append("\n\n");
 }
 public void close()throws IOException {zip.close();}
}
