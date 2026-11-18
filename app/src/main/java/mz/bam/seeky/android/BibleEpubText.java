package mz.bam.seeky.android;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Bible-specific XHTML extraction. Keeps inline verse labels and chapter boundaries. */
final class BibleEpubText extends DefaultHandler {
    static final int NORMAL=0, SKIP=1, CHAPTER=2, HEADING=3, VERSE=4;
    static final class Frame {
        final String tag;final int kind;final StringBuilder label=new StringBuilder();
        Frame(String tag,int kind){this.tag=tag;this.kind=kind;}
    }
    final BiblePlainText parser;final BooleanSupplier stop;final Runnable pulse;
    final Deque<Frame> stack=new ArrayDeque<>();final StringBuilder line=new StringBuilder();
    BibleEpubText(BiblePlainText parser,BooleanSupplier stop,Runnable pulse){this.parser=parser;this.stop=stop;this.pulse=pulse;}
    static void read(BookArchive archive,int part,BiblePlainText parser,BooleanSupplier stop,Runnable pulse)throws Exception {
        String path=archive.chapters.get(part),base=path.substring(path.lastIndexOf('/')+1).replaceFirst("(?i)\\.(?:x?html?)$","");
        // eBible uses USFM book codes in its spine filenames. Do not infer verse numbers.
        String code=BibleText.code(base.replaceFirst("^[0-9]{2,3}[-_]", ""));
        if(!code.isEmpty())parser.heading(code);
        String named=archive.names.get(part);if(!AudioTitles.generic(named))parser.heading(named);
        String source=BookArchive.xmlSource(archive.read(path,BookArchive.CHAPTER_LIMIT));
        SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setValidating(false);
        XMLReader reader=factory.newSAXParser().getXMLReader();
        BibleEpubText handler=new BibleEpubText(parser,stop,pulse);
        reader.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
        reader.setContentHandler(handler);reader.setErrorHandler(handler);
        try{reader.parse(new InputSource(new StringReader(source)));handler.flushLine();}
        catch(SAXException e){if(e.getException()!=null)throw e.getException();throw new IOException("Não foi possível ler esta parte da Bíblia EPUB.",e);}
    }
    static boolean token(String value,String... names){for(String item:value.toLowerCase(Locale.ROOT).split("\\s+"))for(String name:names)if(item.equals(name))return true;return false;}
    Frame capture(){for(Frame frame:stack)if(frame.kind==SKIP||frame.kind==CHAPTER||frame.kind==HEADING)return frame;return null;}
    void check()throws Exception {if(stop.getAsBoolean())throw new InterruptedIOException();pulse.run();}
    void flushLine()throws Exception {
        if(line.length()==0)return;String text=line.toString().replaceAll("[\\s\\p{Zs}]+"," ").trim();line.setLength(0);parser.feed(text,stop);
    }
    public void startElement(String uri,String local,String qualified,Attributes attrs)throws SAXException {
        try{
            check();String tag=(local.isEmpty()?qualified:local).toLowerCase(Locale.ROOT);
            if(stack.size()>=128)throw new IOException("Documento demasiado aninhado.");
            String cls=attrs.getValue("class");if(cls==null)cls="";
            String type=attrs.getValue("http://www.idpf.org/2007/ops","type");if(type==null)type="";
            String href=attrs.getValue("href");if(href==null)href="";
            Frame parent=capture();int kind=NORMAL;
            if(parent==null){
                if(Arrays.asList("head","script","style","nav","footer","svg").contains(tag)||token(cls,"f","footnote","footnotes","endnote","endnotes","xref","x","notemark")||token(type,"footnote","endnote","noteref","toc")||tag.equals("a")&&href.matches("(?i).*#(?:fn|note|footnote)[^#]*"))kind=SKIP;
                else if(token(cls,"c","chapterlabel","chapternum","chapter-number","chapternumber"))kind=CHAPTER;
                else if(tag.matches("h[1-6]")||token(cls,"mt","mt1","mt2","booktitle","book-name","s","s1","s2","ms","ms1"))kind=HEADING;
                else if(token(cls,"v","verse","versenum","verse-num","versenumber","verse-number","vn"))kind=VERSE;
                if(kind!=NORMAL||tag.matches("p|div|section|li|tr|br"))flushLine();
            }
            stack.push(new Frame(tag,kind));
        }catch(Exception e){throw new SAXException(e);}
    }
    public void characters(char[] chars,int at,int length)throws SAXException {
        try{
            check();Frame capture=capture();if(capture!=null){
                if(capture.kind!=SKIP){if(capture.label.length()+length>65536)throw new IOException("Título demasiado extenso na Bíblia.");capture.label.append(chars,at,length);}
            }else{if(line.length()+length>65536)throw new IOException("Não foi possível separar os versículos desta parte.");line.append(chars,at,length);}
        }catch(Exception e){throw new SAXException(e);}
    }
    public void endElement(String uri,String local,String qualified)throws SAXException {
        try{
            check();Frame frame=stack.pop();if(capture()!=null)return;
            if(frame.kind==CHAPTER||frame.kind==HEADING){
                String title=frame.label.toString().replace('\u00a0',' ').trim();
                if(title.matches("[1-9][0-9]{0,2}"))title="Capítulo "+title;
                if(!parser.heading(title)&&!title.isEmpty())parser.flush();
            }else if(frame.kind==VERSE){line.append(' ');}
            else if(frame.kind==NORMAL&&frame.tag.matches("p|div|section|li|tr|br"))flushLine();
        }catch(Exception e){throw new SAXException(e);}
    }
    public void error(SAXParseException e)throws SAXException{throw e;}
    public void fatalError(SAXParseException e)throws SAXException{throw e;}
}
