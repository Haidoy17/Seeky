package mz.bam.seeky.android;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/**
 * Turns FB2, MOBI/AZW3/PRC, ODT, RTF, MHTML and CBZ books into a plain EPUB, so that every reader tool
 * (text mode, original view, marks, Bible links, reading aloud, search) works on them. Pure Java, no
 * network; scripts, styles and links are dropped. The output is byte-for-byte deterministic, so the
 * same book imported twice is recognised as a duplicate.
 */
final class EbookConvert {
    static final long MAX_INPUT=300L<<20;
    static final int MAX_MEMORY=96<<20,MAX_IMAGE=24<<20,MAX_CHAPTERS=4000,CHUNK=90*1024;
    static final String EPUB="application/epub+zip";

    static String format(String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT).trim();
        if(n.endsWith(".fb2")||n.endsWith(".fbz")||n.endsWith(".fb2.zip"))return "fb2";
        if(n.endsWith(".mobi")||n.endsWith(".azw")||n.endsWith(".azw3")||n.endsWith(".prc"))return "mobi";
        if(n.endsWith(".odt"))return "odt";if(n.endsWith(".rtf"))return "rtf";
        if(n.endsWith(".mht")||n.endsWith(".mhtml"))return "mhtml";if(n.endsWith(".cbz"))return "cbz";return "";
    }
    static boolean supported(String name){return !format(name).isEmpty();}
    static String epubName(String name){String n=title(name);return (n.isEmpty()?"Livro":n)+".epub";}
    /** File name without folders and extension (".fb2.zip" counts as one). */
    static String title(String name){
        String n=name==null?"":name.trim();int slash=Math.max(n.lastIndexOf('/'),n.lastIndexOf('\\'));if(slash>=0)n=n.substring(slash+1);
        if(n.toLowerCase(Locale.ROOT).endsWith(".fb2.zip"))return n.substring(0,n.length()-8).trim();int dot=n.lastIndexOf('.');return (dot>0?n.substring(0,dot):n).trim();
    }

    interface Source{InputStream open()throws IOException;}
    static final class Resource{final String id,path,mime;final byte[] data;final Source source;Resource(String id,String path,String mime,byte[] data,Source source){this.id=id;this.path=path;this.mime=mime;this.data=data;this.source=source;}}
    static final class Chapter{final String title,body;Chapter(String title,String body){this.title=title;this.body=body;}}
    static final class Book implements Closeable {
        String title="",author="",language="pt";String cover;Closeable open;
        final List<Chapter> chapters=new ArrayList<>();final List<Resource> resources=new ArrayList<>();final Map<String,String> byKey=new HashMap<>();
        /** Adds an image once per key; returns its path inside the EPUB ("images/i0003.jpg") or null. */
        String image(String key,byte[] data){
            if(key!=null&&byKey.containsKey(key))return byKey.get(key);
            String mime=data==null?null:imageMime(data);if(mime==null||data.length>MAX_IMAGE)return null;
            String path="images/i"+String.format(Locale.ROOT,"%04d",resources.size()+1)+"."+imageExt(mime);
            resources.add(new Resource("img"+(resources.size()+1),path,mime,data,null));if(key!=null)byKey.put(key,path);return path;
        }
        void chapter(String title,String body)throws IOException{if(chapters.size()>=MAX_CHAPTERS)throw new IOException("O livro tem demasiadas partes.");chapters.add(new Chapter(title==null?"":title,body));}
        @Override public void close()throws IOException{if(open!=null)open.close();}
    }

    static void convert(File in,String name,File out)throws IOException{try(Book book=read(in,name)){write(book,out);}}
    static Book read(File in,String name)throws IOException{
        if(!in.isFile())throw new IOException("Livro indisponível.");if(in.length()>MAX_INPUT)throw new IOException("Livro demasiado grande para converter (máximo 300 MB).");
        String f=format(name);Book b;
        switch(f){
            case "fb2":b=Fb2.read(in);break;
            case "mobi":b=Mobi.read(bytes(in));break;
            case "odt":b=Odt.read(in);break;
            case "rtf":b=Rtf.read(bytes(in));break;
            case "mhtml":b=Mhtml.read(bytes(in));break;
            case "cbz":b=Comic.read(in);break;
            default:throw new IOException("Formato de livro não suportado.");
        }
        if(b.title.trim().isEmpty())b.title=title(name);if(b.title.trim().isEmpty())b.title="Livro";
        if(b.chapters.isEmpty()){b.close();throw new IOException("O livro não contém texto legível.");}
        return b;
    }
    static byte[] bytes(File f)throws IOException{if(f.length()>MAX_MEMORY)throw new IOException("Livro demasiado grande para este formato (máximo 96 MB).");try(InputStream in=new FileInputStream(f)){return read(in,MAX_MEMORY);}}
    static byte[] read(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Parte do livro demasiado grande.");out.write(b,0,n);}return out.toByteArray();}

    // ---------- EPUB writer ----------
    static final long TIME=new GregorianCalendar(2020,Calendar.JANUARY,1,12,0,0).getTimeInMillis();
    static final String CSS="body{line-height:1.55;margin:0 4%;}p{margin:.35em 0;text-indent:0;}h1,h2,h3{line-height:1.25;margin:1em 0 .5em;}h1{font-size:1.5em;}h2{font-size:1.3em;}h3{font-size:1.15em;}img{max-width:100%;height:auto;}blockquote{margin:.6em 1.2em;font-style:italic;}div.page{text-align:center;margin:0;}div.page img{max-height:98vh;}table{border-collapse:collapse;}td,th{border:1px solid #9994;padding:.2em .4em;}";
    static void write(Book b,File out)throws IOException{
        File parent=out.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Pasta de destino indisponível.");
        String uid=uid(b);
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))){
            byte[] mime="application/epub+zip".getBytes(StandardCharsets.US_ASCII);ZipEntry m=new ZipEntry("mimetype");m.setMethod(ZipEntry.STORED);m.setSize(mime.length);m.setCompressedSize(mime.length);CRC32 crc=new CRC32();crc.update(mime);m.setCrc(crc.getValue());m.setTime(TIME);zip.putNextEntry(m);zip.write(mime);zip.closeEntry();
            put(zip,"META-INF/container.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            put(zip,"OEBPS/style.css",CSS);
            StringBuilder manifest=new StringBuilder(),spine=new StringBuilder(),nav=new StringBuilder(),ncx=new StringBuilder();
            for(int i=0;i<b.chapters.size();i++){
                Chapter c=b.chapters.get(i);String file=String.format(Locale.ROOT,"text/c%04d.xhtml",i+1),title=c.title.trim().isEmpty()?"Parte "+(i+1):c.title.trim();
                put(zip,"OEBPS/"+file,"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\""+esc(b.language)+"\" lang=\""+esc(b.language)+"\"><head><meta charset=\"UTF-8\"/><title>"+esc(title)+"</title><link rel=\"stylesheet\" type=\"text/css\" href=\"../style.css\"/></head><body>"+c.body+"</body></html>");
                manifest.append("<item id=\"c").append(i+1).append("\" href=\"").append(file).append("\" media-type=\"application/xhtml+xml\"/>");
                spine.append("<itemref idref=\"c").append(i+1).append("\"/>");
                nav.append("<li><a href=\"").append(file).append("\">").append(esc(title)).append("</a></li>");
                ncx.append("<navPoint id=\"n").append(i+1).append("\" playOrder=\"").append(i+1).append("\"><navLabel><text>").append(esc(title)).append("</text></navLabel><content src=\"").append(file).append("\"/></navPoint>");
            }
            for(Resource r:b.resources){
                ZipEntry e=new ZipEntry("OEBPS/"+r.path);e.setTime(TIME);zip.putNextEntry(e);
                if(r.data!=null)zip.write(r.data);else try(InputStream in=r.source.open()){byte[] buf=new byte[65536];int n;long total=0;while((n=in.read(buf))!=-1){total+=n;if(total>MAX_IMAGE*2L)throw new IOException("Imagem demasiado grande no livro.");zip.write(buf,0,n);}}
                zip.closeEntry();
                boolean cover=r.path.equals(b.cover);
                manifest.append("<item id=\"").append(cover?"cover":r.id).append("\" href=\"").append(r.path).append("\" media-type=\"").append(r.mime).append('"').append(cover?" properties=\"cover-image\"":"").append("/>");
            }
            put(zip,"OEBPS/nav.xhtml","<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><meta charset=\"UTF-8\"/><title>"+esc(b.title)+"</title></head><body><nav epub:type=\"toc\" id=\"toc\"><h1>Índice</h1><ol>"+nav+"</ol></nav></body></html>");
            put(zip,"OEBPS/toc.ncx","<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\"><head><meta name=\"dtb:uid\" content=\""+uid+"\"/></head><docTitle><text>"+esc(b.title)+"</text></docTitle><navMap>"+ncx+"</navMap></ncx>");
            put(zip,"OEBPS/content.opf","<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"uid\">"+uid+"</dc:identifier><dc:title>"+esc(b.title.trim())+"</dc:title>"+(b.author.trim().isEmpty()?"":"<dc:creator>"+esc(b.author.trim())+"</dc:creator>")+"<dc:language>"+esc(b.language)+"</dc:language><meta property=\"dcterms:modified\">2020-01-01T12:00:00Z</meta>"+(b.cover==null?"":"<meta name=\"cover\" content=\"cover\"/>")+"</metadata><manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/><item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>"+manifest+"</manifest><spine toc=\"ncx\">"+spine+"</spine></package>");
        }catch(IOException|RuntimeException e){out.delete();throw e;}
    }
    static void put(ZipOutputStream zip,String path,String text)throws IOException{ZipEntry e=new ZipEntry(path);e.setTime(TIME);zip.putNextEntry(e);zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    static String uid(Book b){try{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update((b.title+"\u0000"+b.author).getBytes(StandardCharsets.UTF_8));for(Chapter c:b.chapters)d.update(c.body.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder("urn:seeky:");byte[] h=d.digest();for(int i=0;i<16;i++)s.append(String.format(Locale.ROOT,"%02x",h[i]&255));return s.toString();}catch(Exception e){return "urn:seeky:book";}}

    // ---------- shared helpers ----------
    /** XML text: escapes markup and drops characters XML cannot hold. */
    static String esc(String s){
        if(s==null)return "";StringBuilder o=new StringBuilder(s.length()+16);
        for(int i=0;i<s.length();i++){char c=s.charAt(i);
            if(Character.isHighSurrogate(c)&&i+1<s.length()&&Character.isLowSurrogate(s.charAt(i+1))){o.append(c).append(s.charAt(++i));continue;}
            if(Character.isSurrogate(c)||c<0x20&&c!='\t'&&c!='\n'&&c!='\r'||c==0xfffe||c==0xffff)continue;
            switch(c){case '&':o.append("&amp;");break;case '<':o.append("&lt;");break;case '>':o.append("&gt;");break;case '"':o.append("&quot;");break;default:o.append(c);}
        }return o.toString();
    }
    static String imageMime(byte[] d){
        if(d.length>3&&(d[0]&255)==0xff&&(d[1]&255)==0xd8)return "image/jpeg";
        if(d.length>8&&(d[0]&255)==0x89&&d[1]=='P'&&d[2]=='N'&&d[3]=='G')return "image/png";
        if(d.length>6&&d[0]=='G'&&d[1]=='I'&&d[2]=='F')return "image/gif";
        if(d.length>12&&d[0]=='R'&&d[1]=='I'&&d[2]=='F'&&d[3]=='F'&&d[8]=='W'&&d[9]=='E'&&d[10]=='B'&&d[11]=='P')return "image/webp";
        if(d.length>2&&d[0]=='B'&&d[1]=='M')return "image/bmp";
        return null;
    }
    static String imageExt(String mime){switch(mime){case "image/png":return "png";case "image/gif":return "gif";case "image/webp":return "webp";case "image/bmp":return "bmp";default:return "jpg";}}
    /** Plain paragraphs, cut into parts of a comfortable size. */
    static void paragraphs(Book b,List<String> paragraphs)throws IOException{
        StringBuilder body=new StringBuilder();String title="";int part=0;
        for(String p:paragraphs){String t=p.replaceAll("[ \\t\\u00a0]+"," ").trim();if(t.isEmpty())continue;if(title.isEmpty()&&t.length()<=90)title=t;body.append("<p>").append(esc(t)).append("</p>\n");if(body.length()>=CHUNK){b.chapter(part==0?title:"Parte "+(part+1),body.toString());part++;body.setLength(0);}}
        if(body.length()>0)b.chapter(part==0?title:"Parte "+(part+1),body.toString());
    }
    static int u16(byte[] d,int o){return o<0||o+2>d.length?0:((d[o]&255)<<8)|(d[o+1]&255);}
    static long u32(byte[] d,int o){return o<0||o+4>d.length?0:((long)(d[o]&255)<<24)|((d[o+1]&255)<<16)|((d[o+2]&255)<<8)|(d[o+3]&255);}
    static Charset charset(String name,Charset fallback){try{return name==null||name.trim().isEmpty()?fallback:Charset.forName(name.trim());}catch(Exception e){return fallback;}}

    /** XML without DTDs or entity definitions (the same guard the EPUB reader uses). */
    static Document xml(String source)throws IOException{
        try{
            if(source.startsWith("\ufeff"))source=source.substring(1);String upper=source.toUpperCase(Locale.ROOT);
            if(upper.contains("<!ENTITY"))throw new IOException("Entidades XML não são permitidas.");
            source=source.replaceAll("(?is)<!DOCTYPE[^>\\[]*>","");if(source.toUpperCase(Locale.ROOT).contains("<!DOCTYPE"))throw new IOException("DTD inválido.");
            source=Html.xmlEntities(source);
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);f.setValidating(false);
            DocumentBuilder builder=f.newDocumentBuilder();builder.setEntityResolver((p,s)->new InputSource(new StringReader("")));builder.setErrorHandler(null);
            return builder.parse(new InputSource(new StringReader(source)));
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("O ficheiro do livro está danificado ou incompleto.",e);}
    }
    static String local(Node n){String l=n.getLocalName();if(l==null){l=n.getNodeName();int c=l.indexOf(':');if(c>=0)l=l.substring(c+1);}return l;}
    /** Attribute by local name, whatever its namespace prefix (l:href, xlink:href, href). */
    static String attr(Element e,String name){NamedNodeMap all=e.getAttributes();for(int i=0;i<all.getLength();i++){Node a=all.item(i);if(local(a).equals(name))return a.getNodeValue();}return "";}
    static List<Element> children(Node n){List<Element> out=new ArrayList<>();for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())if(c instanceof Element)out.add((Element)c);return out;}
    static Element child(Node n,String name){for(Element e:children(n))if(local(e).equals(name))return e;return null;}
    static String text(Node n){return n==null?"":n.getTextContent().replaceAll("\\s+"," ").trim();}
    static ZipEntry zipEntry(ZipFile zip,String name){ZipEntry e=zip.getEntry(name);return e==null||e.isDirectory()?null:e;}
    static byte[] zipRead(ZipFile zip,ZipEntry e,int max)throws IOException{try(InputStream in=zip.getInputStream(e)){return read(in,max);}}

    // ---------- lenient HTML to XHTML ----------
    /** Keeps the text structure of (possibly broken) HTML as well-formed XHTML; drops scripts, styles, links and unknown tags. */
    static final class Html {
        interface Images{String src(Map<String,String> attrs);}
        static final Set<String> BLOCK=new HashSet<>(Arrays.asList("p","div","h1","h2","h3","h4","h5","h6","blockquote","ul","ol","li","pre","table","tr","td","th","dl","dt","dd","figure","figcaption"));
        static final Set<String> INLINE=new HashSet<>(Arrays.asList("b","strong","i","em","u","s","sup","sub","small","span","code"));
        static final Set<String> SKIP=new HashSet<>(Arrays.asList("script","style","noscript","iframe","object","embed","template","svg","math","select","textarea","button","guide"));
        static final Map<String,String> RENAME=new HashMap<>();
        static{String[] pairs={"center","div","font","span","section","div","article","div","header","div","footer","div","aside","div","nav","div","main","div","address","div","strike","s","del","s","ins","u","big","span","cite","i","q","span","caption","p","blockquote","blockquote","tt","code","kbd","code","var","i","dfn","i","abbr","span","mark","span","small","small","bdo","span","bdi","span"};for(int i=0;i<pairs.length;i+=2)RENAME.put(pairs[i],pairs[i+1]);}
        final Book book;final Images images;final boolean splitHtml;
        final StringBuilder out=new StringBuilder();final ArrayDeque<String> open=new ArrayDeque<>();
        String skipTag;int skip;boolean capture,lastBlock=true;final StringBuilder captured=new StringBuilder();String chapterTitle="";int headingAt=-1;int parts;boolean seenHtml;String docTitle="";
        Html(Book book,Images images,boolean splitHtml){this.book=book;this.images=images;this.splitHtml=splitHtml;}

        void feed(String s)throws IOException{
            int i=0,n=s.length();
            while(i<n){
                char c=s.charAt(i);
                if(c!='<'){int e=s.indexOf('<',i);if(e<0)e=n;text(s.substring(i,e));i=e;continue;}
                if(s.startsWith("<!--",i)){int e=s.indexOf("-->",i+4);i=e<0?n:e+3;continue;}
                if(s.startsWith("<![CDATA[",i)){int e=s.indexOf("]]>",i+9);text(s.substring(i+9,e<0?n:e));i=e<0?n:e+3;continue;}
                if(i+1<n&&(s.charAt(i+1)=='!'||s.charAt(i+1)=='?')){int e=s.indexOf('>',i);i=e<0?n:e+1;continue;}
                int j=i+1;boolean close=false;if(j<n&&s.charAt(j)=='/'){close=true;j++;}
                int k=j;while(k<n){char d=s.charAt(k);if(Character.isLetterOrDigit(d)||d==':'||d=='-'||d=='_')k++;else break;}
                if(k==j){text("<");i++;continue;}
                String tag=s.substring(j,k).toLowerCase(Locale.ROOT);
                int e=k;char q=0;int limit=Math.min(n,k+16384);
                while(e<limit){char d=s.charAt(e);if(q!=0){if(d==q)q=0;}else if(d=='"'||d=='\'')q=d;else if(d=='>')break;e++;}
                if(e>=limit&&limit<n){text("<");i++;continue;}
                String rawAttrs=s.substring(k,Math.min(e,n));i=Math.min(n,e+1);
                if(close)end(tag);else start(tag,attributes(rawAttrs),rawAttrs.trim().endsWith("/"));
            }
        }
        static Map<String,String> attributes(String raw){
            Map<String,String> map=new LinkedHashMap<>();int i=0,n=raw.length();
            while(i<n){
                while(i<n&&(Character.isWhitespace(raw.charAt(i))||raw.charAt(i)=='/'))i++;int s=i;
                while(i<n&&!Character.isWhitespace(raw.charAt(i))&&raw.charAt(i)!='='&&raw.charAt(i)!='/')i++;
                if(s==i){i++;continue;}String name=raw.substring(s,i).toLowerCase(Locale.ROOT);String value="";
                while(i<n&&Character.isWhitespace(raw.charAt(i)))i++;
                if(i<n&&raw.charAt(i)=='='){i++;while(i<n&&Character.isWhitespace(raw.charAt(i)))i++;if(i<n&&(raw.charAt(i)=='"'||raw.charAt(i)=='\'')){char q=raw.charAt(i++);int e=raw.indexOf(q,i);if(e<0)e=n;value=raw.substring(i,e);i=Math.min(n,e+1);}else{int v=i;while(i<n&&!Character.isWhitespace(raw.charAt(i)))i++;value=raw.substring(v,i);}}
                map.put(name,decode(value));
            }return map;
        }
        void start(String tag,Map<String,String> attrs,boolean empty)throws IOException{
            if(skip>0){if(tag.equals(skipTag)&&!empty)skip++;return;}
            if(SKIP.contains(tag)){if(!empty){skip=1;skipTag=tag;}return;}
            if(tag.equals("title")){if(!empty){capture=true;captured.setLength(0);}return;}
            if(tag.equals("mbp:pagebreak")){page(false);return;}
            if(tag.equals("html")){if(splitHtml&&seenHtml)page(false);seenHtml=true;return;}
            if(tag.equals("br")){out.append("<br/>");lastBlock=true;return;}
            if(tag.equals("hr")){out.append("<hr/>");lastBlock=true;return;}
            if(tag.equals("img")){String src=images==null?null:images.src(attrs);if(src!=null){out.append("<img src=\"../").append(esc(src)).append("\" alt=\"").append(esc(attrs.containsKey("alt")?attrs.get("alt"):"")).append("\"/>");lastBlock=false;}return;}
            String name=RENAME.containsKey(tag)?RENAME.get(tag):tag;
            if(empty||!BLOCK.contains(name)&&!INLINE.contains(name))return;
            implicitClose(name);
            open.push(name);out.append('<').append(name);lastBlock=BLOCK.contains(name);
            if(name.equals("td")||name.equals("th")){for(String span:new String[]{"colspan","rowspan"}){String v=attrs.get(span);if(v!=null&&v.matches("[1-9][0-9]?"))out.append(' ').append(span).append("=\"").append(v).append('"');}}
            out.append('>');
            if(name.matches("h[1-3]")&&chapterTitle.isEmpty())headingAt=out.length();
        }
        /** HTML's implied end tags: a paragraph ends where another block starts, list items and cells end at the next one. */
        void implicitClose(String name){
            String stop=null;
            if(name.equals("li"))stop="ul|ol";else if(name.equals("dt")||name.equals("dd"))stop="dl";else if(name.equals("tr"))stop="table";else if(name.equals("td")||name.equals("th"))stop="tr|table";
            if(stop!=null){String own=name.equals("dd")||name.equals("dt")?"dt|dd":name.equals("td")||name.equals("th")?"td|th":name;
                for(String t:open){if(t.matches(stop))break;if(t.matches(own)){closeTo(t);break;}}return;}
            if(BLOCK.contains(name)){for(String t:open){if(t.matches("p|h[1-6]")){closeTo(t);break;}if(!INLINE.contains(t))break;}}
        }
        void closeTo(String tag){while(!open.isEmpty()){String t=open.pop();out.append("</").append(t).append('>');lastBlock=BLOCK.contains(t);closed(t);if(t.equals(tag))break;}}
        void closed(String t){if(headingAt>=0&&t.matches("h[1-3]")){String h=out.substring(headingAt).replaceAll("<[^>]*>"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replaceAll("\\s+"," ").trim();if(!h.isEmpty())chapterTitle=h.length()>120?h.substring(0,120)+"…":h;headingAt=-1;}}
        void end(String tag)throws IOException{
            if(skip>0){if(tag.equals(skipTag)&&--skip==0)skipTag=null;return;}
            if(tag.equals("title")){if(capture&&docTitle.isEmpty())docTitle=captured.toString().replaceAll("\\s+"," ").trim();capture=false;return;}
            String name=RENAME.containsKey(tag)?RENAME.get(tag):tag;
            if(!open.contains(name))return;closeTo(name);
            if(out.length()>=CHUNK&&BLOCK.contains(name)&&!open.contains("table"))page(true);
        }
        void text(String raw){
            if(skip>0)return;if(capture){captured.append(decode(raw));return;}
            String t=decode(raw);boolean pre=open.contains("pre");if(!pre)t=t.replaceAll("[\\t\\r\\n\\f ]+"," ");
            if(t.isEmpty()||t.equals(" ")&&(out.length()==0||lastBlock))return;
            if(lastBlock&&!pre)t=t.replaceFirst("^ ","");
            out.append(esc(t));lastBlock=false;
        }
        /** Ends the current part; {@code reopen} keeps the open tags going in the next one (size cuts). */
        void page(boolean reopen)throws IOException{
            List<String> stack=new ArrayList<>(open);Collections.reverse(stack);
            while(!open.isEmpty()){String t=open.pop();out.append("</").append(t).append('>');closed(t);}
            String body=out.toString().trim();
            if(!body.replaceAll("<[^>]*>","").trim().isEmpty()||body.contains("<img")){parts++;book.chapter(chapterTitle.isEmpty()?"Parte "+parts:chapterTitle,body);}
            out.setLength(0);chapterTitle="";headingAt=-1;lastBlock=true;
            if(reopen)for(String t:stack){open.push(t);out.append('<').append(t).append('>');}
        }
        void finish()throws IOException{skip=0;capture=false;page(false);}

        static final Map<String,Integer> ENTITIES=new HashMap<>();
        static{
            String[] latin={"nbsp","iexcl","cent","pound","curren","yen","brvbar","sect","uml","copy","ordf","laquo","not","shy","reg","macr","deg","plusmn","sup2","sup3","acute","micro","para","middot","cedil","sup1","ordm","raquo","frac14","frac12","frac34","iquest","Agrave","Aacute","Acirc","Atilde","Auml","Aring","AElig","Ccedil","Egrave","Eacute","Ecirc","Euml","Igrave","Iacute","Icirc","Iuml","ETH","Ntilde","Ograve","Oacute","Ocirc","Otilde","Ouml","times","Oslash","Ugrave","Uacute","Ucirc","Uuml","Yacute","THORN","szlig","agrave","aacute","acirc","atilde","auml","aring","aelig","ccedil","egrave","eacute","ecirc","euml","igrave","iacute","icirc","iuml","eth","ntilde","ograve","oacute","ocirc","otilde","ouml","divide","oslash","ugrave","uacute","ucirc","uuml","yacute","thorn","yuml"};
            for(int i=0;i<latin.length;i++)ENTITIES.put(latin[i],160+i);
            Object[] more={"amp",38,"lt",60,"gt",62,"quot",34,"apos",39,"mdash",8212,"ndash",8211,"hellip",8230,"lsquo",8216,"rsquo",8217,"sbquo",8218,"ldquo",8220,"rdquo",8221,"bdquo",8222,"bull",8226,"trade",8482,"euro",8364,"dagger",8224,"Dagger",8225,"permil",8240,"lsaquo",8249,"rsaquo",8250,"OElig",338,"oelig",339,"Scaron",352,"scaron",353,"Yuml",376,"fnof",402,"circ",710,"tilde",732,"ensp",8194,"emsp",8195,"thinsp",8201,"zwnj",8204,"zwj",8205,"lrm",8206,"rlm",8207,"prime",8242,"Prime",8243,"larr",8592,"rarr",8594,"uarr",8593,"darr",8595,"harr",8596,"minus",8722,"le",8804,"ge",8805,"ne",8800,"infin",8734,"alpha",945,"beta",946,"Omega",937,"omega",969,"pi",960};
            for(int i=0;i<more.length;i+=2)ENTITIES.put((String)more[i],(Integer)more[i+1]);
        }
        /** HTML character references to text. */
        static String decode(String s){
            if(s.indexOf('&')<0)return s;StringBuilder o=new StringBuilder(s.length());int i=0,n=s.length();
            while(i<n){char c=s.charAt(i);if(c!='&'){o.append(c);i++;continue;}int semi=s.indexOf(';',i);if(semi<0||semi-i>12){o.append(c);i++;continue;}String ref=s.substring(i+1,semi);Integer code=null;
                try{if(ref.startsWith("#x")||ref.startsWith("#X"))code=Integer.parseInt(ref.substring(2),16);else if(ref.startsWith("#"))code=Integer.parseInt(ref.substring(1));else code=ENTITIES.get(ref);}catch(NumberFormatException ignored){}
                if(code==null||code<=0||code>0x10ffff){o.append(c);i++;continue;}o.appendCodePoint(code);i=semi+1;}
            return o.toString();
        }
        /** Named HTML entities in an XML source become numeric references; stray ampersands are escaped. */
        static String xmlEntities(String s){
            if(s.indexOf('&')<0)return s;StringBuilder o=new StringBuilder(s.length()+64);int i=0,n=s.length();
            while(i<n){char c=s.charAt(i);if(c!='&'){o.append(c);i++;continue;}int semi=s.indexOf(';',i);String ref=semi<0||semi-i>12?null:s.substring(i+1,semi);
                if(ref!=null&&(ref.matches("#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|amp|lt|gt|quot|apos"))){o.append('&').append(ref).append(';');i=semi+1;continue;}
                if(ref!=null&&ENTITIES.containsKey(ref)){o.append("&#").append(ENTITIES.get(ref)).append(';');i=semi+1;continue;}
                o.append("&amp;");i++;}
            return o.toString();
        }
    }

    // ---------- FB2 ----------
    static final class Fb2 {
        final Book book=new Book();final Map<String,byte[]> binaries=new HashMap<>();int depthGuard;
        static Book read(File in)throws IOException{
            byte[] data;
            if(isZip(in))try(ZipFile zip=new ZipFile(in)){ZipEntry pick=null;for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();){ZipEntry z=e.nextElement();if(!z.isDirectory()&&z.getName().toLowerCase(Locale.ROOT).endsWith(".fb2")){pick=z;break;}}if(pick==null)throw new IOException("O arquivo não contém um livro FB2.");data=zipRead(zip,pick,64<<20);}
            else{if(in.length()>(64<<20))throw new IOException("Livro FB2 demasiado grande (máximo 64 MB).");try(InputStream s=new FileInputStream(in)){data=EbookConvert.read(s,64<<20);}}
            return new Fb2().parse(data);
        }
        static boolean isZip(File f)throws IOException{try(InputStream in=new FileInputStream(f)){return in.read()=='P'&&in.read()=='K';}}
        Book parse(byte[] data)throws IOException{
            String head=new String(data,0,Math.min(data.length,300),StandardCharsets.ISO_8859_1);java.util.regex.Matcher m=java.util.regex.Pattern.compile("encoding=[\"']([A-Za-z0-9._-]+)[\"']").matcher(head);
            Charset cs=StandardCharsets.UTF_8;if(data.length>=2&&((data[0]&255)==0xff&&(data[1]&255)==0xfe))cs=StandardCharsets.UTF_16LE;else if(data.length>=2&&(data[0]&255)==0xfe&&(data[1]&255)==0xff)cs=StandardCharsets.UTF_16BE;else if(m.find())cs=charset(m.group(1),StandardCharsets.UTF_8);
            String source=new String(data,cs);source=source.replaceFirst("^\\ufeff?<\\?xml[^>]*\\?>","");
            Document doc=xml(source);Element root=doc.getDocumentElement();
            for(Element e:children(root))if(local(e).equals("binary")){String id=attr(e,"id");try{binaries.put(id,Base64.getMimeDecoder().decode(e.getTextContent().trim()));}catch(IllegalArgumentException ignored){}}
            Element info=child(child(root,"description"),"title-info");
            if(info!=null){book.title=text(child(info,"book-title"));List<String> authors=new ArrayList<>();for(Element a:children(info))if(local(a).equals("author")){String name=(text(child(a,"first-name"))+" "+text(child(a,"middle-name"))+" "+text(child(a,"last-name"))).replaceAll("\\s+"," ").trim();if(name.isEmpty())name=text(child(a,"nickname"));if(!name.isEmpty())authors.add(name);}book.author=String.join(", ",authors);String lang=text(child(info,"lang"));if(lang.matches("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})?"))book.language=lang.replace('_','-');
                Element cover=child(info,"coverpage");if(cover!=null)for(Element img:children(cover)){String path=image(attr(img,"href"));if(path!=null){book.cover=path;break;}}}
            if(book.cover==null){for(Map.Entry<String,byte[]> b:new TreeMap<>(binaries).entrySet())if(b.getKey().toLowerCase(Locale.ROOT).contains("cover")){String p=image("#"+b.getKey());if(p!=null){book.cover=p;break;}}}
            boolean main=true;
            for(Element body:children(root)){
                if(!local(body).equals("body"))continue;String kind=attr(body,"name");
                if(!main&&(kind.equals("notes")||kind.equals("comments")||!kind.isEmpty())){StringBuilder notes=new StringBuilder();for(Element e:children(body))block(e,notes,2);if(notes.length()>0)book.chapter(kind.equals("comments")?"Comentários":"Notas",notes.toString());continue;}
                main=false;section(body,0,book.title.isEmpty()?"Início":book.title);
            }
            return book;
        }
        /** A section with sub-sections becomes its own short part (title, epigraph) followed by one part per sub-section; long parts are cut between whole blocks. */
        void section(Element s,int level,String fallback)throws IOException{
            if(level>40)return;String title=titleText(child(s,"title"));if(title.isEmpty())title=fallback;
            StringBuilder html=new StringBuilder();int part=0;
            for(Element c:children(s)){
                if(local(c).equals("section")){part=emit(title,part,html);section(c,level+1,"");continue;}
                block(c,html,Math.max(1,level));
                if(html.length()>=CHUNK)part=emit(title,part,html);
            }
            emit(title,part,html);
        }
        static String titleText(Element title){if(title==null)return "";List<String> lines=new ArrayList<>();for(Element p:children(title)){String t=text(p);if(!t.isEmpty())lines.add(t);}String all=lines.isEmpty()?text(title):String.join(" — ",lines);return all.length()>140?all.substring(0,140)+"…":all;}
        int emit(String title,int part,StringBuilder html)throws IOException{String body=html.toString();html.setLength(0);if(body.replaceAll("<[^>]*>","").trim().isEmpty()&&!body.contains("<img"))return part;book.chapter(part==0?title:title+" ("+(part+1)+")",body);return part+1;}
        String image(String href){if(href==null||!href.startsWith("#"))return null;String id=href.substring(1);byte[] data=binaries.get(id);return data==null?null:book.image("fb2:"+id,data);}
        /** FB2 block elements to XHTML; {@code level} is the section depth (titles become h1…h4). */
        void block(Element e,StringBuilder o,int level){
            if(++depthGuard>400){depthGuard--;return;}
            try{
                switch(local(e)){
                    case "title":{int h=Math.min(4,level);o.append("<h").append(h).append('>');boolean firstLine=true;for(Element p:children(e)){if(!local(p).equals("p"))continue;if(!firstLine)o.append("<br/>");inline(p,o);firstLine=false;}o.append("</h").append(h).append(">\n");break;}
                    case "section":for(Element c:children(e))block(c,o,level+1);break;
                    case "p":o.append("<p>");inline(e,o);o.append("</p>\n");break;
                    case "subtitle":o.append("<h5>");inline(e,o);o.append("</h5>\n");break;
                    case "empty-line":o.append("<p>\u00a0</p>\n");break;
                    case "epigraph":case "cite":case "annotation":o.append("<blockquote>");for(Element c:children(e))block(c,o,level);o.append("</blockquote>\n");break;
                    case "text-author":o.append("<p><i>");inline(e,o);o.append("</i></p>\n");break;
                    case "poem":case "stanza":o.append("<div>");for(Element c:children(e))block(c,o,level);o.append("</div>\n");break;
                    case "v":o.append("<p>");inline(e,o);o.append("</p>\n");break;
                    case "image":{String src=image(attr(e,"href"));if(src!=null)o.append("<p><img src=\"../").append(src).append("\" alt=\"").append(esc(attr(e,"alt"))).append("\"/></p>\n");break;}
                    case "table":o.append("<table>");for(Element tr:children(e)){if(!local(tr).equals("tr"))continue;o.append("<tr>");for(Element td:children(tr)){String t=local(td).equals("th")?"th":"td";o.append('<').append(t).append('>');inline(td,o);o.append("</").append(t).append('>');}o.append("</tr>");}o.append("</table>\n");break;
                    default:if(!children(e).isEmpty())for(Element c:children(e))block(c,o,level);else{String t=text(e);if(!t.isEmpty())o.append("<p>").append(esc(t)).append("</p>\n");}
                }
            }finally{depthGuard--;}
        }
        void inline(Node n,StringBuilder o){
            if(++depthGuard>400){depthGuard--;return;}
            try{
                for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling()){
                    if(c.getNodeType()==Node.TEXT_NODE||c.getNodeType()==Node.CDATA_SECTION_NODE){o.append(esc(c.getNodeValue().replaceAll("[\\t\\r\\n ]+"," ")));continue;}
                    if(!(c instanceof Element))continue;Element e=(Element)c;
                    switch(local(e)){
                        case "strong":o.append("<b>");inline(e,o);o.append("</b>");break;
                        case "emphasis":o.append("<i>");inline(e,o);o.append("</i>");break;
                        case "strikethrough":o.append("<s>");inline(e,o);o.append("</s>");break;
                        case "sub":case "sup":case "code":{String t=local(e);o.append('<').append(t).append('>');inline(e,o);o.append("</").append(t).append('>');break;}
                        case "a":{boolean note="note".equals(attr(e,"type"));if(note)o.append("<sup>");inline(e,o);if(note)o.append("</sup>");break;}
                        case "image":{String src=image(attr(e,"href"));if(src!=null)o.append("<img src=\"../").append(src).append("\" alt=\"\"/>");break;}
                        default:inline(e,o);
                    }
                }
            }finally{depthGuard--;}
        }
    }

    // ---------- MOBI / AZW3 / PRC ----------
    static final class Mobi {
        final byte[] f;final int[] offsets;
        Mobi(byte[] f,int[] offsets){this.f=f;this.offsets=offsets;}
        byte[] record(int i){if(i<0||i+1>=offsets.length)return new byte[0];int a=offsets[i],b=offsets[i+1];if(a<0||b<a||b>f.length)return new byte[0];return Arrays.copyOfRange(f,a,b);}
        int records(){return offsets.length-1;}
        static Book read(byte[] f)throws IOException{
            if(f.length<86)throw new IOException("Ficheiro MOBI/PRC danificado.");
            String type=new String(f,60,8,StandardCharsets.ISO_8859_1);int count=u16(f,76);
            if(count<2||78+count*8L>f.length)throw new IOException("Ficheiro MOBI/PRC danificado.");
            int[] offsets=new int[count+1];for(int i=0;i<count;i++){long o=u32(f,78+i*8);if(o>f.length||i>0&&o<offsets[i-1])throw new IOException("Ficheiro MOBI/PRC danificado.");offsets[i]=(int)o;}offsets[count]=f.length;
            Mobi m=new Mobi(f,offsets);Book book=new Book();String pdbName=new String(f,0,32,StandardCharsets.ISO_8859_1);int nul=pdbName.indexOf(0);if(nul>=0)pdbName=pdbName.substring(0,nul);
            byte[] r0=m.record(0);int compression=u16(r0,0),records=u16(r0,8),encryption=u16(r0,12);long textLength=u32(r0,4);
            if(type.equals("TEXtREAd")){
                book.title=pdbName.replace('_',' ').trim();byte[] text=m.text(1,records,compression,0,textLength,null);
                paragraphs(book,Arrays.asList(new String(text,charset("windows-1252",StandardCharsets.ISO_8859_1)).split("\r?\n")));return book;
            }
            if(!type.equals("BOOKMOBI"))throw new IOException("Este ficheiro não é um livro MOBI/AZW3/PRC.");
            if(encryption!=0)throw new IOException("Este livro tem protecção DRM e não pode ser aberto. Use uma cópia sem protecção.");
            boolean header=r0.length>=0x84&&r0[16]=='M'&&r0[17]=='O'&&r0[18]=='B'&&r0[19]=='I';
            int headerLength=header?(int)u32(r0,20):0;long encoding=header?u32(r0,28):1252,version=header?u32(r0,0x68):0;long firstImage=header?u32(r0,0x6c):-1;
            int extra=header&&headerLength>=0xE4&&r0.length>=0xF4?u16(r0,0xF2):0;
            Map<Integer,List<byte[]>> exth=new HashMap<>();
            if(header&&(u32(r0,0x80)&0x40)!=0){int at=16+headerLength;if(at+12<=r0.length&&r0[at]=='E'&&r0[at+1]=='X'&&r0[at+2]=='T'&&r0[at+3]=='H'){long n=u32(r0,at+8);int p=at+12;for(long i=0;i<n&&p+8<=r0.length;i++){int t=(int)u32(r0,p);long len=u32(r0,p+4);if(len<8||p+len>r0.length)break;exth.computeIfAbsent(t,k->new ArrayList<>()).add(Arrays.copyOfRange(r0,p+8,(int)(p+len)));p+=len;}}}
            Charset cs=encoding==65001?StandardCharsets.UTF_8:charset("windows-1252",StandardCharsets.ISO_8859_1);
            String title=exthText(exth,503,cs);if(title.isEmpty()&&header){long off=u32(r0,0x54),len=u32(r0,0x58);if(off>0&&len>0&&off+len<=r0.length)title=new String(r0,(int)off,(int)len,cs).trim();}
            book.title=title.isEmpty()?pdbName.replace('_',' ').trim():title;
            List<String> authors=new ArrayList<>();if(exth.containsKey(100))for(byte[] a:exth.get(100)){String s=new String(a,cs).trim();if(!s.isEmpty()&&!authors.contains(s))authors.add(s);}book.author=String.join(", ",authors);
            String lang=exthText(exth,524,cs);if(lang.matches("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})?"))book.language=lang.replace('_','-');
            Huff huff=null;if(compression==17480){long ho=u32(r0,0x70),hn=u32(r0,0x74);if(hn<1||ho+hn>m.records())throw new IOException("Compressão MOBI desconhecida.");List<byte[]> tables=new ArrayList<>();for(long i=ho;i<ho+hn;i++)tables.add(m.record((int)i));huff=new Huff(tables);}
            else if(compression!=1&&compression!=2)throw new IOException("Compressão MOBI não suportada.");
            boolean kf8=version==8;
            byte[] text=m.text(1,records,compression,extra,textLength,huff);
            if(kf8&&r0.length>=0xC4){long fdst=u32(r0,0xC0);if(fdst>0&&fdst<m.records()){byte[] fd=m.record((int)fdst);if(fd.length>=20&&fd[0]=='F'&&fd[1]=='D'&&fd[2]=='S'&&fd[3]=='T'){long end=u32(fd,16);if(end>0&&end<text.length)text=Arrays.copyOf(text,(int)end);}}}
            final long images=firstImage;final Mobi file=m;
            Html html=new Html(book,attrs->{
                int index=-1;String rec=attrs.containsKey("recindex")?attrs.get("recindex"):attrs.containsKey("hirecindex")?attrs.get("hirecindex"):attrs.get("lowrecindex");
                try{if(rec!=null)index=Integer.parseInt(rec.trim());}catch(NumberFormatException ignored){}
                String src=attrs.get("src");if(index<0&&src!=null&&src.startsWith("kindle:embed:")){String code=src.substring(13);int q=code.indexOf('?');if(q>=0)code=code.substring(0,q);index=base32(code);}
                if(index<=0||images<0||images+index-1>=file.records())return null;
                int r=(int)(images+index-1);byte[] data=file.record(r);if(r==0)return null;
                String path=book.image("mobi:"+r,data);return path;
            },kf8);
            html.feed(new String(text,cs));html.finish();
            if(book.title.isEmpty()&&!html.docTitle.isEmpty())book.title=html.docTitle;
            if(exth.containsKey(201)&&firstImage>=0){long r=firstImage+u32(exth.get(201).get(0),0);if(r>0&&r<m.records()){String p=book.image("mobi:"+r,m.record((int)r));if(p!=null)book.cover=p;}}
            return book;
        }
        static String exthText(Map<Integer,List<byte[]>> exth,int key,Charset cs){List<byte[]> v=exth.get(key);return v==null||v.isEmpty()?"":new String(v.get(0),cs).trim();}
        static int base32(String s){int v=0;for(char c:s.toUpperCase(Locale.ROOT).toCharArray()){int d=c>='0'&&c<='9'?c-'0':c>='A'&&c<='V'?c-'A'+10:-1;if(d<0)return -1;v=v*32+d;if(v>1000000)return -1;}return v;}
        byte[] text(int from,int count,int compression,int extra,long length,Huff huff)throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();long cap=Math.min(length>0?length:Long.MAX_VALUE,64L<<20);
            for(int i=from;i<from+count&&i<records()&&out.size()<cap;i++){
                byte[] r=record(i);int trail=extra==0?0:trailing(r,extra);r=Arrays.copyOf(r,Math.max(0,r.length-trail));
                byte[] d=compression==2?palmdoc(r):compression==17480?huff.unpack(r,0):r;out.write(d,0,(int)Math.min(d.length,cap-out.size()));
            }
            return out.toByteArray();
        }
        /** Size of the trailing entries appended to a text record (multibyte overlap and index data). */
        static int trailing(byte[] d,int flags){
            int size=d.length,num=0;int f=flags>>1;
            while(f!=0){if((f&1)!=0){int psize=size-num,bit=0,result=0;while(psize>0){int v=d[psize-1]&255;result|=(v&0x7f)<<bit;bit+=7;psize--;if((v&0x80)!=0||bit>=28||psize==0)break;}num+=result;if(num>=size)return size;}f>>=1;}
            if((flags&1)!=0&&size-num-1>=0)num+=(d[size-num-1]&3)+1;return Math.min(num,size);
        }
        /** PalmDOC LZ77. */
        static byte[] palmdoc(byte[] in){
            byte[] out=new byte[Math.max(64,in.length*8)];int o=0,i=0;
            while(i<in.length){
                if(o+16>out.length)out=Arrays.copyOf(out,out.length*2);
                int c=in[i++]&255;
                if(c>=1&&c<=8){for(int k=0;k<c&&i<in.length;k++){if(o>=out.length)out=Arrays.copyOf(out,out.length*2);out[o++]=in[i++];}}
                else if(c<0x80)out[o++]=(byte)c;
                else if(c>=0xc0){out[o++]=' ';out[o++]=(byte)(c^0x80);}
                else{if(i>=in.length)break;int m=((c<<8)|(in[i++]&255))&0x3fff;int dist=m>>3,len=(m&7)+3;if(dist<1||dist>o)continue;for(int k=0;k<len;k++){if(o>=out.length)out=Arrays.copyOf(out,out.length*2);out[o]=out[o-dist];o++;}}
            }
            return Arrays.copyOf(out,o);
        }
    }
    /** MOBI Huffman/CDIC decompression (used by some Kindle books without DRM). */
    static final class Huff {
        final long[][] dict1=new long[256][];final long[] mincode=new long[33],maxcode=new long[33];final List<byte[]> slices=new ArrayList<>();final List<Boolean> done=new ArrayList<>();
        Huff(List<byte[]> tables)throws IOException{
            byte[] h=tables.get(0);if(h.length<24||h[0]!='H'||h[1]!='U'||h[2]!='F'||h[3]!='F')throw new IOException("Tabela MOBI inválida.");
            int off1=(int)u32(h,8),off2=(int)u32(h,12);if(off1+1024>h.length||off2+256>h.length)throw new IOException("Tabela MOBI inválida.");
            for(int i=0;i<256;i++){long v=u32(h,off1+i*4);long codelen=v&0x1f,term=v&0x80,max=v>>>8;if(codelen==0)throw new IOException("Tabela MOBI inválida.");max=((max+1)<<(32-codelen))-1;dict1[i]=new long[]{codelen,term,max};}
            mincode[0]=0;maxcode[0]=0xffffffffL;
            for(int cl=1;cl<=32;cl++){mincode[cl]=u32(h,off2+(cl-1)*8)<<(32-cl);maxcode[cl]=((u32(h,off2+(cl-1)*8+4)+1)<<(32-cl))-1;}
            for(int t=1;t<tables.size();t++){
                byte[] c=tables.get(t);if(c.length<16||c[0]!='C'||c[1]!='D'||c[2]!='I'||c[3]!='C')throw new IOException("Dicionário MOBI inválido.");
                long phrases=u32(c,8),bits=u32(c,12);if(bits>31)throw new IOException("Dicionário MOBI inválido.");long n=Math.min(1L<<bits,phrases-slices.size());
                for(int i=0;i<n;i++){int off=u16(c,16+i*2);int blen=u16(c,16+off);int start=18+off,len=blen&0x7fff;if(start+len>c.length)throw new IOException("Dicionário MOBI inválido.");slices.add(Arrays.copyOfRange(c,start,start+len));done.add((blen&0x8000)!=0);}
            }
        }
        byte[] unpack(byte[] input,int depth)throws IOException{
            if(depth>20)throw new IOException("Compressão MOBI demasiado profunda.");
            long bitsLeft=input.length*8L;byte[] data=Arrays.copyOf(input,input.length+8);int pos=0;long x=readLong(data,0);int n=32;ByteArrayOutputStream out=new ByteArrayOutputStream();
            while(true){
                if(n<=0){pos+=4;x=readLong(data,pos);n+=32;}
                long code=(x>>>n)&0xffffffffL;long[] d1=dict1[(int)(code>>>24)];long codelen=d1[0],max=d1[2];
                if(d1[1]==0){while(codelen<32&&code<mincode[(int)codelen])codelen++;max=maxcode[(int)codelen];}
                n-=codelen;bitsLeft-=codelen;if(bitsLeft<0)break;
                long r=(max-code)>>>(32-codelen);if(r<0||r>=slices.size())throw new IOException("Texto MOBI danificado.");
                int idx=(int)r;byte[] slice=slices.get(idx);
                if(!done.get(idx)){slice=unpack(slice,depth+1);slices.set(idx,slice);done.set(idx,true);}
                out.write(slice,0,slice.length);if(out.size()>(8<<20))throw new IOException("Texto MOBI danificado.");
            }
            return out.toByteArray();
        }
        static long readLong(byte[] d,int o){long v=0;for(int i=0;i<8;i++)v=(v<<8)|(o+i<d.length?d[o+i]&255:0);return v;}
    }

    // ---------- ODT ----------
    static final class Odt {
        final Book book=new Book();final ZipFile zip;StringBuilder body=new StringBuilder();String title="";int depth;
        Odt(ZipFile zip){this.zip=zip;}
        static Book read(File in)throws IOException{
            try(ZipFile zip=new ZipFile(in)){
                Odt o=new Odt(zip);ZipEntry content=zipEntry(zip,"content.xml");if(content==null)throw new IOException("Documento ODT sem conteúdo.");
                ZipEntry meta=zipEntry(zip,"meta.xml");
                if(meta!=null)try{Document m=xml(new String(zipRead(zip,meta,4<<20),StandardCharsets.UTF_8));NodeList all=m.getElementsByTagNameNS("*","*");String creator="",initial="";for(int i=0;i<all.getLength();i++){Node n=all.item(i);String l=local(n);if(l.equals("title")&&o.book.title.isEmpty())o.book.title=text(n);else if(l.equals("creator")&&creator.isEmpty())creator=text(n);else if(l.equals("initial-creator")&&initial.isEmpty())initial=text(n);else if(l.equals("language")){String lang=text(n);if(lang.matches("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})?"))o.book.language=lang;}}o.book.author=initial.isEmpty()?creator:initial;}catch(IOException ignored){}
                Document doc=xml(new String(zipRead(zip,content,48<<20),StandardCharsets.UTF_8));NodeList texts=doc.getElementsByTagNameNS("*","text");Element root=null;
                for(int i=0;i<texts.getLength();i++){Node n=texts.item(i);if(n.getParentNode()!=null&&local(n.getParentNode()).equals("body")){root=(Element)n;break;}}
                if(root==null)throw new IOException("Documento ODT sem texto.");
                o.walk(root);o.flush();return o.book;
            }
        }
        int part;
        void flush()throws IOException{String b=body.toString();body.setLength(0);if(!b.replaceAll("<[^>]*>","").trim().isEmpty()||b.contains("<img")){String name=title.isEmpty()?(book.title.isEmpty()?"Documento":book.title):title;book.chapter(part==0?name:name+" ("+(part+1)+")",b);part++;}}
        void newChapter()throws IOException{flush();title="";part=0;}
        void walk(Element e)throws IOException{
            if(++depth>300){depth--;return;}
            try{for(Element c:children(e)){
                switch(local(c)){
                    case "h":{int level=1;try{level=Integer.parseInt(attr(c,"outline-level"));}catch(NumberFormatException ignored){}level=Math.max(1,Math.min(6,level));String t=text(c);if(level==1&&depth==1&&body.length()>0)newChapter();if(level<=2&&title.isEmpty())title=t.length()>120?t.substring(0,120)+"…":t;body.append("<h").append(level).append('>');inline(c);body.append("</h").append(level).append(">\n");break;}
                    case "p":body.append("<p>");inline(c);body.append("</p>\n");break;
                    case "list":body.append("<ul>");for(Element item:children(c)){if(!local(item).equals("list-item")&&!local(item).equals("list-header"))continue;body.append("<li>");walk(item);body.append("</li>");}body.append("</ul>\n");break;
                    case "table":body.append("<table>");rows(c);body.append("</table>\n");break;
                    case "section":case "index-body":case "table-of-content":case "alphabetical-index":case "illustration-index":case "user-index":case "bibliography":case "change":case "list-item":walk(c);break;
                    case "frame":image(c);break;
                    default:break;
                }
                if(body.length()>=CHUNK&&depth==1)flush();
            }}finally{depth--;}
        }
        void rows(Element table)throws IOException{for(Element r:children(table)){String l=local(r);if(l.equals("table-row")){body.append("<tr>");for(Element cell:children(r)){if(!local(cell).equals("table-cell"))continue;body.append("<td>");walk(cell);body.append("</td>");}body.append("</tr>");}else if(l.equals("table-header-rows")||l.equals("table-rows")||l.equals("table-row-group"))rows(r);}}
        void image(Element frame)throws IOException{for(Element img:children(frame)){if(!local(img).equals("image"))continue;String href=attr(img,"href");if(href.startsWith("./"))href=href.substring(2);if(href.isEmpty()||href.contains("..")||href.startsWith("/")||href.contains(":"))continue;ZipEntry e=zipEntry(zip,href);if(e==null)continue;byte[] data=zipRead(zip,e,MAX_IMAGE);String path=book.image("odt:"+href,data);if(path!=null){body.append("<p><img src=\"../").append(path).append("\" alt=\"\"/></p>\n");if(book.cover==null)book.cover=path;}break;}}
        void inline(Node n)throws IOException{
            if(++depth>300){depth--;return;}
            try{for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling()){
                if(c.getNodeType()==Node.TEXT_NODE||c.getNodeType()==Node.CDATA_SECTION_NODE){body.append(esc(c.getNodeValue()));continue;}
                if(!(c instanceof Element))continue;Element e=(Element)c;
                switch(local(e)){
                    case "s":{int count=1;try{count=Integer.parseInt(attr(e,"c"));}catch(NumberFormatException ignored){}for(int i=0;i<Math.min(count,40);i++)body.append(' ');break;}
                    case "tab":body.append(' ');break;
                    case "line-break":body.append("<br/>");break;
                    case "note":case "annotation":case "bookmark-ref":case "reference-mark":break;
                    case "frame":{StringBuilder saved=body;body=new StringBuilder();image(e);String img=body.toString().replace("<p>","").replace("</p>\n","");body=saved;body.append(img);break;}
                    default:inline(e);
                }
            }}finally{depth--;}
        }
    }

    // ---------- RTF ----------
    static final class Rtf {
        static final Set<String> SKIP=new HashSet<>(Arrays.asList("fonttbl","colortbl","stylesheet","pict","object","header","headerl","headerr","headerf","footer","footerl","footerr","footerf","footnote","xe","tc","listtable","listoverridetable","rsidtbl","generator","themedata","colorschememapping","datastore","latentstyles","pgdsctbl","xmlnstbl","mmathPr","revtbl","filetbl","nonshppict","shppict","fldinst","bkmkstart","bkmkend","private","userprops","docvar","operator","company","category","keywords","comment","doccomm","subject","manager","hlinkbase","buptim","creatim","printim","revtim","listpicture","wgrffmtfilter","pnseclvl","sp","shp","shpinst","atnid","atnauthor","annotation","pntext","pntxta","pntxtb"));
        static final class State{boolean skip;int uc=1;StringBuilder capture;String field;State copy(){State c=new State();c.skip=skip;c.uc=uc;c.capture=capture;c.field=field;return c;}}
        final Book book=new Book();final List<String> paras=new ArrayList<>();final StringBuilder para=new StringBuilder();final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Charset cs=charset("windows-1252",StandardCharsets.ISO_8859_1);State st=new State();final ArrayDeque<State> stack=new ArrayDeque<>();int pendingSkip;boolean groupStart;
        static Book read(byte[] data)throws IOException{
            String s=new String(data,StandardCharsets.ISO_8859_1);if(!s.startsWith("{\\rtf"))throw new IOException("Este ficheiro não é um documento RTF.");
            Rtf r=new Rtf();r.parse(s);List<String> lines=new ArrayList<>();for(String p:r.paras)lines.addAll(Arrays.asList(p.split("\n")));paragraphs(r.book,lines);return r.book;
        }
        void emit(String t){if(t.isEmpty())return;if(st.capture!=null)st.capture.append(t);else if(!st.skip)para.append(t);}
        void flushBytes(){if(bytes.size()==0)return;String t=new String(bytes.toByteArray(),cs);bytes.reset();emit(t);}
        void paragraph(){if(st.capture==null&&!st.skip){paras.add(para.toString());para.setLength(0);}}
        void parse(String s){
            int i=0,n=s.length();
            while(i<n){
                char c=s.charAt(i);
                if(c=='\\'&&i+1<n&&s.charAt(i+1)=='\''){if(i+3<n){try{int v=Integer.parseInt(s.substring(i+2,i+4),16);if(pendingSkip>0)pendingSkip--;else if(!st.skip||st.capture!=null)bytes.write(v);}catch(NumberFormatException ignored){}}i+=4;groupStart=false;continue;}
                flushBytes();
                if(c=='{'){stack.push(st);st=st.copy();groupStart=true;i++;continue;}
                if(c=='}'){State closed=st;st=stack.isEmpty()?new State():stack.pop();if(closed.field!=null&&closed.capture!=null&&closed.capture!=st.capture){String v=closed.capture.toString().trim();if(closed.field.equals("title")&&book.title.isEmpty())book.title=v;else if(closed.field.equals("author")&&book.author.isEmpty())book.author=v;}groupStart=false;i++;continue;}
                if(c=='\r'||c=='\n'){i++;continue;}
                if(c!='\\'){if(pendingSkip>0){pendingSkip--;i++;continue;}emit(c>127?new String(new byte[]{(byte)c},cs):String.valueOf(c));groupStart=false;i++;continue;}
                char d=i+1<n?s.charAt(i+1):' ';
                if(!Character.isLetter(d)){i+=2;if(d=='*'){st.skip=true;continue;}groupStart=false;if(d=='\n'||d=='\r'){paragraph();continue;}emit(d=='~'?"\u00a0":d=='_'?"-":d=='\\'||d=='{'||d=='}'?String.valueOf(d):"");continue;}
                int w=i+1;i=w;while(i<n&&Character.isLetter(s.charAt(i)))i++;String word=s.substring(w,i);Integer param=null;int p=i;
                if(i<n&&(s.charAt(i)=='-'||Character.isDigit(s.charAt(i)))){i++;while(i<n&&Character.isDigit(s.charAt(i)))i++;try{param=Integer.parseInt(s.substring(p,i));}catch(NumberFormatException ignored){}}
                if(i<n&&s.charAt(i)==' ')i++;
                groupStart=false;
                if(word.equals("bin")&&param!=null&&param>0){i=(int)Math.min(n,(long)i+param);continue;}
                control(word,param);
            }
            flushBytes();if(para.length()>0)paras.add(para.toString());
        }
        void control(String word,Integer param){
            switch(word){
                case "ansicpg":if(param!=null)cs=param==65001?StandardCharsets.UTF_8:param==932?charset("Shift_JIS",cs):param==936?charset("GBK",cs):param==949?charset("EUC-KR",cs):param==950?charset("Big5",cs):charset("windows-"+param,cs);break;
                case "mac":cs=charset("x-MacRoman",cs);break;case "pc":cs=charset("IBM437",cs);break;case "pca":cs=charset("IBM850",cs);break;
                case "uc":st.uc=param==null?1:Math.max(0,Math.min(8,param));break;
                case "u":if(param!=null){int code=param<0?param+65536:param;if(Character.isValidCodePoint(code))emit(new String(Character.toChars(code)));}pendingSkip=st.uc;break;
                case "par":case "sect":case "page":case "row":paragraph();break;
                case "line":emit("\n");break;
                case "tab":emit("\t");break;case "cell":emit("  ");break;
                case "emdash":emit("\u2014");break;case "endash":emit("\u2013");break;case "lquote":emit("\u2018");break;case "rquote":emit("\u2019");break;case "ldblquote":emit("\u201c");break;case "rdblquote":emit("\u201d");break;case "bullet":emit("\u2022");break;
                case "title":case "author":st.field=word;st.capture=new StringBuilder();break;
                default:if(SKIP.contains(word))st.skip=true;
            }
        }
    }

    // ---------- MHTML ----------
    static final class Mhtml {
        static Book read(byte[] data)throws IOException{
            String s=new String(data,StandardCharsets.ISO_8859_1);int split=headerEnd(s,0);if(split<0)throw new IOException("Página MHTML danificada.");
            Map<String,String> top=headers(s.substring(0,split));String type=top.getOrDefault("content-type","text/html");
            List<Map<String,String>> partHeaders=new ArrayList<>();List<byte[]> bodies=new ArrayList<>();
            String boundary=param(type,"boundary");
            if(type.toLowerCase(Locale.ROOT).startsWith("multipart/")&&boundary!=null){
                String marker="--"+boundary;int at=s.indexOf(marker,split);
                while(at>=0){int start=s.indexOf('\n',at);if(start<0)break;start++;int next=s.indexOf(marker,start);int end=next<0?s.length():next;String part=s.substring(start,end);int h=headerEnd(part,0);if(h>=0){Map<String,String> ph=headers(part.substring(0,h));partHeaders.add(ph);bodies.add(body(part.substring(h).replaceFirst("\\r?\\n$",""),ph));}if(next<0||s.startsWith(marker+"--",next))break;at=next;}
            }else{partHeaders.add(top);bodies.add(body(s.substring(split),top));}
            Book book=new Book();int html=-1;for(int i=0;i<partHeaders.size();i++)if(partHeaders.get(i).getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("text/html")){html=i;break;}
            if(html<0)throw new IOException("A página MHTML não tem texto HTML.");
            Map<String,String> hh=partHeaders.get(html);String charsetName=param(hh.getOrDefault("content-type",""),"charset");byte[] raw=bodies.get(html);
            if(charsetName==null){String probe=new String(raw,0,Math.min(raw.length,4096),StandardCharsets.ISO_8859_1);java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)charset=[\"']?([A-Za-z0-9._-]+)").matcher(probe);if(m.find())charsetName=m.group(1);}
            String source=new String(raw,charset(charsetName,StandardCharsets.UTF_8));
            final Map<String,Integer> byLocation=new HashMap<>();for(int i=0;i<partHeaders.size();i++){Map<String,String> ph=partHeaders.get(i);String loc=ph.get("content-location");if(loc!=null)byLocation.put(loc.trim(),i);String id=ph.get("content-id");if(id!=null)byLocation.put("cid:"+id.trim().replaceAll("^<|>$",""),i);}
            final String base=hh.getOrDefault("content-location",top.getOrDefault("snapshot-content-location",""));
            Html h=new Html(book,attrs->{String src=attrs.get("src");if(src==null||src.isEmpty())return null;Integer part=byLocation.get(src);if(part==null)try{part=byLocation.get(new java.net.URI(base).resolve(src).toString());}catch(Exception ignored){}if(part==null)return null;return book.image("mhtml:"+part,bodies.get(part));},false);
            h.feed(source);h.finish();if(!h.docTitle.isEmpty())book.title=h.docTitle;
            String subject=top.get("subject");if(book.title.isEmpty()&&subject!=null)book.title=subject;
            return book;
        }
        static int headerEnd(String s,int from){int a=s.indexOf("\r\n\r\n",from),b=s.indexOf("\n\n",from);if(a<0&&b<0)return -1;if(a>=0&&(b<0||a<b))return a+4;return b+2;}
        static Map<String,String> headers(String block){Map<String,String> map=new HashMap<>();String key=null;for(String line:block.split("\\r?\\n")){if(line.isEmpty())continue;if((line.startsWith(" ")||line.startsWith("\t"))&&key!=null){map.put(key,map.get(key)+" "+line.trim());continue;}int c=line.indexOf(':');if(c<=0)continue;key=line.substring(0,c).trim().toLowerCase(Locale.ROOT);map.put(key,line.substring(c+1).trim());}return map;}
        static String param(String header,String name){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)"+name+"\\s*=\\s*(\"([^\"]*)\"|([^;\\s]+))").matcher(header);return m.find()?(m.group(2)!=null?m.group(2):m.group(3)):null;}
        static byte[] body(String part,Map<String,String> h){
            String enc=h.getOrDefault("content-transfer-encoding","").toLowerCase(Locale.ROOT).trim();
            if(enc.equals("base64"))try{return Base64.getMimeDecoder().decode(part.replaceAll("[^A-Za-z0-9+/=]",""));}catch(IllegalArgumentException e){return new byte[0];}
            if(enc.equals("quoted-printable")){ByteArrayOutputStream o=new ByteArrayOutputStream();String t=part.replaceAll("=\\r?\\n","");for(int i=0;i<t.length();i++){char c=t.charAt(i);if(c=='='&&i+2<t.length()){try{o.write(Integer.parseInt(t.substring(i+1,i+3),16));i+=2;continue;}catch(NumberFormatException ignored){}}o.write(c&255);}return o.toByteArray();}
            return part.getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    // ---------- CBZ (comics) ----------
    static final class Comic {
        static Book read(File in)throws IOException{
            ZipFile zip=new ZipFile(in);
            try{
                Book book=new Book();book.open=zip;List<ZipEntry> pages=new ArrayList<>();
                for(Enumeration<? extends ZipEntry> e=zip.entries();e.hasMoreElements();){ZipEntry z=e.nextElement();String n=z.getName();String low=n.toLowerCase(Locale.ROOT);String leaf=low.substring(low.lastIndexOf('/')+1);if(z.isDirectory()||low.startsWith("__macosx/")||leaf.startsWith("."))continue;if(low.matches(".*\\.(jpe?g|png|gif|webp|bmp)$"))pages.add(z);else if(leaf.equals("comicinfo.xml")&&z.getSize()<(1<<20))try{Document d=xml(new String(zipRead(zip,z,1<<20),StandardCharsets.UTF_8));Element r=d.getDocumentElement();String t=text(child(r,"Title")),series=text(child(r,"Series")),writer=text(child(r,"Writer"));book.title=!t.isEmpty()?t:series;book.author=writer;}catch(IOException ignored){}}
                if(pages.isEmpty())throw new IOException("O arquivo CBZ não contém páginas de imagem.");
                pages.sort((x,y)->natural(x.getName(),y.getName()));if(pages.size()>MAX_CHAPTERS)throw new IOException("Banda desenhada com demasiadas páginas.");
                for(int i=0;i<pages.size();i++){
                    ZipEntry z=pages.get(i);String low=z.getName().toLowerCase(Locale.ROOT);String ext=low.substring(low.lastIndexOf('.')+1);String mime=ext.startsWith("jp")?"image/jpeg":"image/"+ext;
                    String path=String.format(Locale.ROOT,"images/p%04d.%s",i+1,ext.equals("jpeg")?"jpg":ext);
                    book.resources.add(new Resource("p"+(i+1),path,mime,null,()->zip.getInputStream(z)));if(i==0)book.cover=path;
                    book.chapter("Página "+(i+1),"<div class=\"page\"><img src=\"../"+path+"\" alt=\"Página "+(i+1)+"\"/></div>");
                }
                return book;
            }catch(IOException|RuntimeException e){zip.close();throw e;}
        }
        /** "page2" before "page10". */
        static int natural(String a,String b){
            int i=0,j=0;while(i<a.length()&&j<b.length()){char x=a.charAt(i),y=b.charAt(j);
                if(Character.isDigit(x)&&Character.isDigit(y)){int si=i,sj=j;while(i<a.length()&&Character.isDigit(a.charAt(i)))i++;while(j<b.length()&&Character.isDigit(b.charAt(j)))j++;String na=a.substring(si,i).replaceFirst("^0+(?=.)",""),nb=b.substring(sj,j).replaceFirst("^0+(?=.)","");if(na.length()!=nb.length())return na.length()-nb.length();int c=na.compareTo(nb);if(c!=0)return c;continue;}
                int c=Character.compare(Character.toLowerCase(x),Character.toLowerCase(y));if(c!=0)return c;i++;j++;}
            return (a.length()-i)-(b.length()-j);
        }
    }
    private EbookConvert(){}
}
