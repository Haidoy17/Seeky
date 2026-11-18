package mz.bam.seeky.android;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.function.BooleanSupplier;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/** Worker-owned text source. Loads one page/chapter, never an entire PDF into memory. */
final class SpeechDocument implements AutoCloseable {
    final Context context;final File snapshot;
    final Uri uri;
    final String name, kind;
    final BooleanSupplier cancelled;
    File temporary,sourceFile;
    PdfTextSource pdf;
    BookArchive book;
    ReaderStore notebook;
    String plain,sourceHash;
    boolean partial;java.util.List<PdfTextSource.Heading> outline;
    SpeechDocument(Context c,Uri u,String n,String mime,BooleanSupplier stop)throws IOException{this(c,u,n,mime,stop,null);}
    SpeechDocument(Context c, Uri u, String n, String mime, BooleanSupplier stop,File snapshot) throws IOException {
        this.snapshot=snapshot;if(snapshot!=null&&!DiskWalker.inside(new File(c.getFilesDir(),"narration-jobs").getCanonicalFile(),snapshot.getCanonicalFile()))throw new IOException("Cópia de narração inválida.");
        context = c; uri = u; name = n; kind = MediaFormats.kind(n, mime); cancelled = stop;
        try {
            if (u == null || !("file".equals(u.getScheme()) || "content".equals(u.getScheme()))) throw new IOException("Localização inválida.");
            if (snapshot==null&&"file".equals(u.getScheme()) && !StorageAccess.allowed(c, new File(u.getPath()))) throw new IOException("Ficheiro sem acesso autorizado.");
            if (kind.equals("pdf")) {
                PDFBoxResourceLoader.init(c);
                pdf = new PdfTextSource(local(67108864L), c.getCacheDir());
            } else if (kind.equals("epub") || kind.equals("docx")) {
                book = new BookArchive(local(268435456L), kind.equals("epub"));
            } else if (kind.equals("text")) {
                try (InputStream in = snapshot==null?c.getContentResolver().openInputStream(u):new FileInputStream(snapshot)) {
                    if (in == null) throw new IOException("Ficheiro indisponível.");
                    byte[] bytes = ContentText.read(in, ContentText.LIMIT + 1);
                    partial = bytes.length > ContentText.LIMIT;
                    plain = ContentText.extract(new ByteArrayInputStream(bytes), n);
                }
            } else throw new IOException("Este formato não permite leitura por voz.");
            notebook = new ReaderStore(c);
        } catch (IOException | RuntimeException | OutOfMemoryError e) { close(); throw e; }
    }
    File local(long limit) throws IOException {
        File privateBook=snapshot==null?PrivateMaterial.file(context,uri):null;if(privateBook!=null){sourceFile=privateBook;return privateBook;}
        if(snapshot!=null){if(snapshot.length()>limit)throw new IOException("Documento acima do limite de leitura por voz.");sourceFile=snapshot;return snapshot;}
        if ("file".equals(uri.getScheme())) {
            File f = new File(uri.getPath());
            if (f.length() > limit) throw new IOException("Documento acima do limite de leitura por voz.");
            sourceFile=f;return f;
        }
        temporary = new File(context.getCacheDir(), "seeky-speech-" + java.util.UUID.randomUUID() + ".cache");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Ficheiro indisponível.");
            StudyFiles.copy(in, temporary, limit, cancelled::getAsBoolean);
        }
        sourceFile=temporary;return temporary;
    }
    int count() { return pdf != null ? pdf.count() : book != null ? book.chapters.size() : 1; }
    String part(int index) throws IOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
        StudyStore.Row recognised=pdf==null?null:OcrCache.row(context,uri.toString(),index);
        boolean corrected=recognised!=null&&recognised.s("digest").equals(fingerprint());
        String source=corrected?recognised.s("value"):pdf!=null?pdf.page(index):book!=null?book.chapter(index):plain;
        if(book!=null&&source.equals(BookArchive.NO_TEXT))return "";
        if(pdf!=null)partial=corrected?false:pdf.partial;
        return notebook.load(uri.toString(), index, source).text;
    }
    java.util.List<String> speechTitles(int index){java.util.List<String> titles=new java.util.ArrayList<>();if(book!=null){java.util.List<String> values=book.speechTitles.get(index);if(values!=null)titles.addAll(values);}else if(pdf!=null)try{if(outline==null)outline=pdf.outline();for(PdfTextSource.Heading h:outline)if(h.page==index)titles.add(h.title);}catch(Exception ignored){}return titles;}
    String fingerprint()throws IOException{if(sourceHash==null)sourceHash=sourceFile!=null?StudyFiles.hash(sourceFile):VoiceIdentity.key("text",plain==null?"":plain);return sourceHash;}
    String title(){String value=book!=null?AudioTitles.clean(book.title):"";if(value.isEmpty()&&pdf!=null){try{value=AudioTitles.clean(pdf.document.getDocumentInformation().getTitle());}catch(RuntimeException ignored){}}return value.isEmpty()?AudioTitles.base(name):value;}
    String author(){if(book!=null)return AudioTitles.clean(book.author);if(pdf!=null){try{return AudioTitles.clean(pdf.document.getDocumentInformation().getAuthor());}catch(RuntimeException ignored){}}return "";}
    String partTitle(int index,String text){
        String label="";
        if(book!=null){String named=index>=0&&index<book.names.size()?book.names.get(index):"";if(!AudioTitles.generic(named))label=AudioTitles.clean(named);if(label.isEmpty())label=book.headings.getOrDefault(index,"");}
        else if(pdf!=null){try{if(outline==null)outline=pdf.outline();PdfTextSource.Heading best=null;for(PdfTextSource.Heading heading:outline)if(heading.page<=index&&(best==null||heading.page>best.page||heading.page==best.page&&heading.depth>best.depth))best=heading;if(best!=null)label=AudioTitles.clean(best.title);}catch(IOException|RuntimeException ignored){}}
        if(label.isEmpty())label=AudioTitles.firstHeading(text);
        return label.isEmpty()?AppLanguage.text(context,kind.equals("pdf")?"Página":"Capítulo"):label;
    }
    public void close() {
        try { if (pdf != null) pdf.close(); } catch (Exception ignored) {}
        try { if (book != null) book.close(); } catch (Exception ignored) {}
        try { if (notebook != null) notebook.close(); } catch (Exception ignored) {}
        if (temporary != null) temporary.delete();
    }
}
