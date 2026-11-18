package mz.bam.seeky.android;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.DocumentsContract;
import java.io.*;
import java.security.*;
import java.util.*;

final class Catalog extends SQLiteOpenHelper {
 final ContentResolver resolver; final Context app;String duplicateSummary="";
 static class Entry extends FileRecord {}
 interface Progress {void update(String message);}
 Catalog(Context c){super(c,"seeky-android.db",null,6);app=c.getApplicationContext();resolver=c.getContentResolver();setWriteAheadLoggingEnabled(true);}
 public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE roots(uri TEXT PRIMARY KEY,name TEXT,scanned INTEGER DEFAULT 0)");
  db.execSQL("CREATE TABLE files(_id INTEGER PRIMARY KEY,uri TEXT UNIQUE,root TEXT,name TEXT,path TEXT,mime TEXT,category TEXT,size INTEGER,modified INTEGER,norm TEXT,body TEXT,gen INTEGER,parent TEXT)");
  db.execSQL("CREATE INDEX files_root ON files(root)");db.execSQL("CREATE INDEX files_category ON files(category)");
  db.execSQL("CREATE INDEX files_parent ON files(parent)");
  db.execSQL("CREATE TABLE folders(path TEXT PRIMARY KEY,root TEXT,modified INTEGER,listed INTEGER)");
  db.execSQL("CREATE INDEX files_modified ON files(modified DESC,uri)");
  db.execSQL("CREATE INDEX files_size ON files(size DESC,uri)");
  db.execSQL("CREATE INDEX files_category_modified ON files(category,modified DESC,uri)");
  db.execSQL("CREATE VIRTUAL TABLE search USING fts4(name,body,tokenize=unicode61)");
  db.execSQL("CREATE TRIGGER file_insert AFTER INSERT ON files BEGIN INSERT INTO search(docid,name,body) VALUES(new._id,new.norm,new.body); END");
  db.execSQL("CREATE TRIGGER file_update AFTER UPDATE OF norm,body ON files BEGIN DELETE FROM search WHERE docid=old._id; INSERT INTO search(docid,name,body) VALUES(new._id,new.norm,new.body); END");
  db.execSQL("CREATE TRIGGER file_delete AFTER DELETE ON files BEGIN DELETE FROM search WHERE docid=old._id; END");
  db.execSQL("CREATE TABLE favorites(uri TEXT PRIMARY KEY)");
  db.execSQL("CREATE TABLE saved(id INTEGER PRIMARY KEY,title TEXT,query TEXT,category TEXT,content INTEGER)");
  db.execSQL("CREATE TABLE recent(uri TEXT PRIMARY KEY,opened INTEGER)");
  db.execSQL("CREATE TABLE metadata(uri TEXT PRIMARY KEY,title TEXT NOT NULL DEFAULT '',author TEXT NOT NULL DEFAULT '',tags TEXT NOT NULL DEFAULT '',custom_category TEXT NOT NULL DEFAULT '',custom_date INTEGER NOT NULL DEFAULT 0,manual_order INTEGER NOT NULL DEFAULT 0,tags_norm TEXT NOT NULL DEFAULT '',category_norm TEXT NOT NULL DEFAULT '',norm TEXT NOT NULL DEFAULT '')");
  db.execSQL("CREATE TABLE collections(id INTEGER PRIMARY KEY,title TEXT NOT NULL,created INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE collection_items(collection_id INTEGER NOT NULL,uri TEXT NOT NULL,added INTEGER NOT NULL,PRIMARY KEY(collection_id,uri))");
  db.execSQL("CREATE INDEX collection_uri ON collection_items(uri)");
  db.execSQL("CREATE TABLE activity(id INTEGER PRIMARY KEY,action TEXT NOT NULL,label TEXT NOT NULL,detail TEXT NOT NULL,created INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX activity_created ON activity(created DESC,id DESC)");
  db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY,oldname TEXT,newname TEXT,uri TEXT,done INTEGER DEFAULT 0)");
 }
 public void onUpgrade(SQLiteDatabase d,int o,int n){if(o<2)d.execSQL("CREATE TABLE IF NOT EXISTS recent(uri TEXT PRIMARY KEY,opened INTEGER)");if(o<3){d.execSQL("CREATE INDEX IF NOT EXISTS files_modified ON files(modified DESC,uri)");d.execSQL("CREATE INDEX IF NOT EXISTS files_size ON files(size DESC,uri)");d.execSQL("CREATE INDEX IF NOT EXISTS files_category_modified ON files(category,modified DESC,uri)");}if(o<4){d.execSQL("ALTER TABLE files ADD COLUMN parent TEXT");d.execSQL("UPDATE files SET parent=substr(path,1,length(path)-length(name)-1) WHERE uri LIKE 'file:%'");d.execSQL("CREATE INDEX IF NOT EXISTS files_parent ON files(parent)");d.execSQL("CREATE TABLE IF NOT EXISTS folders(path TEXT PRIMARY KEY,root TEXT,modified INTEGER,listed INTEGER)");}if(o<5)d.execSQL("CREATE TABLE IF NOT EXISTS metadata(uri TEXT PRIMARY KEY,title TEXT NOT NULL DEFAULT '',author TEXT NOT NULL DEFAULT '',tags TEXT NOT NULL DEFAULT '',custom_category TEXT NOT NULL DEFAULT '',custom_date INTEGER NOT NULL DEFAULT 0,manual_order INTEGER NOT NULL DEFAULT 0,tags_norm TEXT NOT NULL DEFAULT '',category_norm TEXT NOT NULL DEFAULT '',norm TEXT NOT NULL DEFAULT '')");if(o<6){d.execSQL("CREATE TABLE IF NOT EXISTS collections(id INTEGER PRIMARY KEY,title TEXT NOT NULL,created INTEGER NOT NULL)");d.execSQL("CREATE TABLE IF NOT EXISTS collection_items(collection_id INTEGER NOT NULL,uri TEXT NOT NULL,added INTEGER NOT NULL,PRIMARY KEY(collection_id,uri))");d.execSQL("CREATE INDEX IF NOT EXISTS collection_uri ON collection_items(uri)");d.execSQL("CREATE TABLE IF NOT EXISTS activity(id INTEGER PRIMARY KEY,action TEXT NOT NULL,label TEXT NOT NULL,detail TEXT NOT NULL,created INTEGER NOT NULL)");d.execSQL("CREATE INDEX IF NOT EXISTS activity_created ON activity(created DESC,id DESC)");}}
 List<String[]> roots(){List<String[]> a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,name,scanned FROM roots ORDER BY name",null)){while(c.moveToNext())a.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});}return a;}
 void addRoot(Uri uri)throws Exception {String name="Pasta";String id=DocumentsContract.getTreeDocumentId(uri);for(String[] r:roots()){if(r[0].startsWith("file:"))continue;Uri old=Uri.parse(r[0]);String previous=DocumentsContract.getTreeDocumentId(old);if(uri.equals(old))return;if(Objects.equals(old.getAuthority(),uri.getAuthority())&&(id.startsWith(previous+"/")||previous.startsWith(id+"/")||id.equals(previous)||previous.endsWith(":")&&id.startsWith(previous)||id.endsWith(":")&&previous.startsWith(id)))throw new IOException("Esta pasta sobrepõe outra já ligada. Escolha pastas separadas para evitar contar os mesmos ficheiros duas vezes.");}Uri doc=DocumentsContract.buildDocumentUriUsingTree(uri,id);try(Cursor c=resolver.query(doc,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}ContentValues v=new ContentValues();v.put("uri",uri.toString());v.put("name",name);getWritableDatabase().insertWithOnConflict("roots",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 void removeRoot(String uri){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("files","root=?",new String[]{uri});d.delete("roots","uri=?",new String[]{uri});d.setTransactionSuccessful();}finally{d.endTransaction();}}
 int scan(String root,CancellationSignal cancel,Progress progress)throws Exception {
  SQLiteDatabase d=getWritableDatabase();Uri tree=Uri.parse(root);long gen=System.currentTimeMillis();int count=0,changed=0,unreadable=0;
  ArrayDeque<String[]> queue=new ArrayDeque<>();queue.add(new String[]{DocumentsContract.getTreeDocumentId(tree),""});Set<String> seen=new HashSet<>();
  d.beginTransaction();try{
   while(!queue.isEmpty()){
    cancel.throwIfCanceled();String[] node=queue.removeFirst();if(!seen.add(node[0]))continue;
    Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,node[0]);
    try(Cursor c=resolver.query(children,new String[]{"document_id","_display_name","mime_type","_size","last_modified"},null,null,null,cancel)){
     if(c==null)throw new IOException("Não foi possível ler a pasta "+node[1]);
     while(c.moveToNext()){
      cancel.throwIfCanceled();String id=c.getString(0),name=c.getString(1),mime=c.getString(2);if(name==null)name="Sem nome";if(mime==null)mime="application/octet-stream";String path=node[1]+name;
      if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)){queue.addLast(new String[]{id,path+"/"});if(seen.size()+queue.size()>100000)throw new IOException("Demasiadas pastas; escolha uma subpasta.");continue;}
      if(++count>100000)throw new IOException("Limite de 100 000 ficheiros por pasta. Escolha subpastas.");
      String uri=DocumentsContract.buildDocumentUriUsingTree(tree,id).toString();long size=c.isNull(3)?-1:c.getLong(3),modified=c.isNull(4)?0:c.getLong(4);String body="";boolean same=false;
      try(Cursor old=d.rawQuery("SELECT size,modified,body,name FROM files WHERE uri=?",new String[]{uri})){if(old.moveToFirst()&&modified>0&&old.getLong(0)==size&&old.getLong(1)==modified&&name.equals(old.getString(3))){body=old.getString(2);same=true;}}
      if(!same&&app.getSharedPreferences("seeky",0).getBoolean("deep_content",false)&&ContentText.supported(name)&&size>=0&&size<=16777216){try(InputStream in=resolver.openInputStream(Uri.parse(uri))){if(in!=null)body=Rules.normalize(ContentText.extract(in,name));}catch(Exception e){unreadable++;}}
      ContentValues v=new ContentValues();v.put("uri",uri);v.put("root",root);v.put("name",name);v.put("path",path);v.put("mime",mime);v.put("category",Rules.category(name,mime));v.put("size",size);v.put("modified",modified);v.put("norm",Rules.normalize(name));v.put("body",body);v.put("gen",gen);
      if(same){ContentValues g=new ContentValues();g.put("gen",gen);g.put("root",root);g.put("path",path);d.update("files",g,"uri=?",new String[]{uri});}else{if(d.update("files",v,"uri=?",new String[]{uri})==0)d.insertOrThrow("files",null,v);changed++;}
      if(count%25==0)progress.update(count+" ficheiros verificados · "+changed+" actualizados");
     }
    }
   }
   cancel.throwIfCanceled();d.delete("files","root=? AND gen<>?",new String[]{root,Long.toString(gen)});ContentValues v=new ContentValues();v.put("scanned",gen);d.update("roots",v,"uri=?",new String[]{root});d.setTransactionSuccessful();
  }finally{d.endTransaction();}
  progress.update(count+" ficheiros · "+changed+" actualizados"+(unreadable>0?" · "+unreadable+" conteúdos sem leitura":""));return count;
 }
 String scope(){
  if(!StorageAccess.all(app))return "uri NOT LIKE 'file:%'";
  try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM roots WHERE uri LIKE 'file:%' AND scanned>0 LIMIT 1",null)){if(c.moveToFirst())return "uri LIKE 'file:%'";}
  return "1=1";
 }
 /** Explicit repair only. Results are committed per 128 files, without reading bodies. */
 int scanDisk(File root,CancellationSignal signal,Progress progress,boolean hidden)throws Exception {
  final List<Entry> batch=new ArrayList<>();final int[] count={0};
  DiskWalker.Summary result=DiskWalker.walk(root,hidden,300000,Long.MAX_VALUE,new DiskWalker.Visitor(){
   public boolean cancelled(){return signal.isCanceled()||!StorageAccess.all(app);}
   public void unreadable(File directory){}
   public void file(File f){batch.add(StorageAccess.entry(f,root));if(batch.size()>=128){metadata(batch);count[0]+=batch.size();batch.clear();progress.update(count[0]+" nomes disponíveis; sem leitura de conteúdo");}}
  });
  if(!batch.isEmpty()){metadata(batch);count[0]+=batch.size();}progress.update(count[0]+" nomes actualizados"+(result.limited?" · limite atingido":""));return count[0];
 }
 /** Short transaction: new names become searchable immediately after each batch. */
 int metadata(List<Entry> entries){if(entries.isEmpty())return 0;SQLiteDatabase d=getWritableDatabase();int changes=0;Set<String> roots=new HashSet<>();d.beginTransaction();try{
  for(Entry e:entries){boolean same=false;try(Cursor c=d.rawQuery("SELECT name,size,modified,mime FROM files WHERE uri=?",new String[]{e.uri})){same=c.moveToFirst()&&e.name.equals(c.getString(0))&&e.size==c.getLong(1)&&e.modified==c.getLong(2)&&e.mime.equals(c.getString(3));}
   if(!same){put(e,"",System.currentTimeMillis());changes++;}
   roots.add(e.root);
  }for(String rootUri:roots){ContentValues root=new ContentValues();root.put("uri",rootUri);root.put("name",rootUri.startsWith("file:")?StorageAccess.location(new File(Uri.parse(rootUri).getPath())):"Armazenamento");root.put("scanned",System.currentTimeMillis());d.insertWithOnConflict("roots",null,root,SQLiteDatabase.CONFLICT_IGNORE);ContentValues stamp=new ContentValues();stamp.put("scanned",System.currentTimeMillis());d.update("roots",stamp,"uri=?",new String[]{rootUri});}d.setTransactionSuccessful();}finally{d.endTransaction();}return changes;
 }
 void folder(File root,File dir,long modified){ContentValues v=new ContentValues();v.put("path",dir.getAbsolutePath());v.put("root",root.getAbsolutePath());v.put("modified",modified);v.put("listed",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("folders",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 List<String[]> folders(){List<String[]> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT path,root,modified,listed FROM folders ORDER BY listed DESC LIMIT 60000",null)){while(c.moveToNext())out.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3)});}return out;}
 int removeMissingInFolder(File directory,Set<String> present){int changed=0;List<String> missing=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,path FROM files WHERE parent=? AND uri LIKE 'file:%'",new String[]{directory.getAbsolutePath()})){while(c.moveToNext())if(!present.contains(c.getString(1))&&!new File(c.getString(1)).exists())missing.add(c.getString(0));}for(String uri:missing)changed+=getWritableDatabase().delete("files","uri=?",new String[]{uri});return changed;}
 void removePath(File file){String path=file.getAbsolutePath();getWritableDatabase().delete("files","uri=? OR substr(path,1,?)=?",new String[]{Uri.fromFile(file).toString(),Integer.toString(path.length()+1),path+"/"});getWritableDatabase().delete("folders","path=? OR substr(path,1,?)=?",new String[]{path,Integer.toString(path.length()+1),path+"/"});}
 void relocated(String oldUri,Entry target){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{metadata(Collections.singletonList(target));copyDetails(oldUri,target.uri,true);ContentValues v=new ContentValues();v.put("uri",target.uri);d.updateWithOnConflict("favorites",v,"uri=?",new String[]{oldUri},SQLiteDatabase.CONFLICT_REPLACE);d.updateWithOnConflict("recent",v,"uri=?",new String[]{oldUri},SQLiteDatabase.CONFLICT_REPLACE);d.update("history",v,"uri=?",new String[]{oldUri});d.delete("files","uri=?",new String[]{oldUri});d.setTransactionSuccessful();}finally{d.endTransaction();}}
 void decorate(List<Entry> entries){if(entries.isEmpty())return;Map<String,Entry> by=new HashMap<>();for(Entry e:entries)by.put(e.uri,e);List<String> keys=new ArrayList<>(by.keySet());for(int offset=0;offset<keys.size();offset+=400){List<String> part=keys.subList(offset,Math.min(keys.size(),offset+400));String placeholders=String.join(",",Collections.nCopies(part.size(),"?"));try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,title,author,tags,custom_category,custom_date,manual_order FROM metadata WHERE uri IN ("+placeholders+")",part.toArray(new String[0]))){while(c.moveToNext()){Entry e=by.get(c.getString(0));e.title=c.getString(1);e.author=c.getString(2);e.tags=c.getString(3);e.customCategory=c.getString(4);e.customDate=c.getLong(5);e.manualOrder=c.getLong(6);}}if(app.getSharedPreferences("seeky",0).getInt("view_mode",0)==2)try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,substr(body,1,180) FROM files WHERE uri IN ("+placeholders+")",part.toArray(new String[0]))){while(c.moveToNext()){Entry e=by.get(c.getString(0));e.snippet=c.isNull(1)?"":c.getString(1);}}}}
 void saveDetails(Entry e){ContentValues v=new ContentValues();v.put("uri",e.uri);v.put("title",e.title);v.put("author",e.author);v.put("tags",e.tags);v.put("custom_category",e.customCategory);v.put("custom_date",e.customDate);v.put("manual_order",e.manualOrder);v.put("tags_norm",Rules.normalize(e.tags));v.put("category_norm",Rules.normalize(e.customCategory));v.put("norm",Rules.normalize(e.title+" "+e.author+" "+e.tags+" "+e.customCategory));getWritableDatabase().insertWithOnConflict("metadata",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 void copyDetails(String oldUri,String newUri,boolean moved){if(oldUri.equals(newUri))return;SQLiteDatabase d=getWritableDatabase();d.execSQL("INSERT OR REPLACE INTO metadata(uri,title,author,tags,custom_category,custom_date,manual_order,tags_norm,category_norm,norm) SELECT ?,title,author,tags,custom_category,custom_date,manual_order,tags_norm,category_norm,norm FROM metadata WHERE uri=?",new Object[]{newUri,oldUri});if(moved){try{ReaderStore.relocate(app,oldUri,newUri);}catch(Exception ex){app.getSharedPreferences("seeky",0).edit().putString("reader_warning","Não foi possível acompanhar anotações: "+ex.getMessage()).apply();}d.delete("metadata","uri=?",new String[]{oldUri});d.execSQL("INSERT OR IGNORE INTO collection_items(collection_id,uri,added) SELECT collection_id,?,added FROM collection_items WHERE uri=?",new Object[]{newUri,oldUri});d.delete("collection_items","uri=?",new String[]{oldUri});}}
 static final class CollectionInfo {long id,total;String title;CollectionInfo(long id,String title,long total){this.id=id;this.title=title;this.total=total;}}
 long createCollection(String title){title=title.trim();if(title.isEmpty()||title.length()>80)throw new IllegalArgumentException("Use um nome entre 1 e 80 caracteres.");ContentValues v=new ContentValues();v.put("title",title);v.put("created",System.currentTimeMillis());return getWritableDatabase().insertOrThrow("collections",null,v);}
 List<CollectionInfo> collections(){List<CollectionInfo> result=new ArrayList<>();try(Cursor cursor=getReadableDatabase().rawQuery("SELECT id,title,(SELECT count(*) FROM collection_items i WHERE i.collection_id=collections.id) FROM collections ORDER BY title COLLATE NOCASE,id",null)){while(cursor.moveToNext())result.add(new CollectionInfo(cursor.getLong(0),cursor.getString(1),cursor.getLong(2)));}return result;}
 void addCollection(long id,List<Entry> entries){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{try(Cursor exists=sql.rawQuery("SELECT 1 FROM collections WHERE id=?",new String[]{Long.toString(id)})){if(!exists.moveToFirst())throw new IllegalArgumentException("Colecção indisponível.");}metadata(entries);for(Entry e:entries){if(e.directory)continue;ContentValues v=new ContentValues();v.put("collection_id",id);v.put("uri",e.uri);v.put("added",System.currentTimeMillis());sql.insertWithOnConflict("collection_items",null,v,SQLiteDatabase.CONFLICT_IGNORE);}sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
 List<Entry> collectionEntries(long id,int limit){List<Entry> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,EXISTS(SELECT 1 FROM favorites f WHERE f.uri=files.uri) FROM files WHERE "+scope()+" AND uri IN (SELECT uri FROM collection_items WHERE collection_id=?) ORDER BY name COLLATE NOCASE,uri LIMIT "+Math.min(Math.max(1,limit),500),new String[]{Long.toString(id)})){while(c.moveToNext())result.add(entry(c));}decorate(result);return result;}
 void renameCollection(long id,String title){title=title.trim();if(title.isEmpty()||title.length()>80)throw new IllegalArgumentException("Use um nome entre 1 e 80 caracteres.");ContentValues v=new ContentValues();v.put("title",title);getWritableDatabase().update("collections",v,"id=?",new String[]{Long.toString(id)});}
 void removeCollection(long id){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{sql.delete("collection_items","collection_id=?",new String[]{Long.toString(id)});sql.delete("collections","id=?",new String[]{Long.toString(id)});sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
 void unlinkCollection(long id,String uri){getWritableDatabase().delete("collection_items","collection_id=? AND uri=?",new String[]{Long.toString(id),uri});}
 void activity(String action,String label,String detail){SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{ContentValues v=new ContentValues();v.put("action",action);v.put("label",label);v.put("detail",detail);v.put("created",System.currentTimeMillis());sql.insertOrThrow("activity",null,v);sql.execSQL("DELETE FROM activity WHERE id NOT IN (SELECT id FROM activity ORDER BY created DESC,id DESC LIMIT 500)");sql.setTransactionSuccessful();}finally{sql.endTransaction();}}
 List<String[]> activity(){List<String[]> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,action,label,detail,created FROM activity ORDER BY created DESC,id DESC LIMIT 500",null)){while(c.moveToNext())rows.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)});}return rows;}
 String content(Entry e,CancellationSignal cancel)throws Exception {
  String value,message="Texto preparado para pesquisa.";File staged=null;
  if(MediaFormats.ext(e.name).equals("pdf")){
   if(e.size>33554432L)throw new IOException("Indexação de PDF limitada a 32 MB por documento.");
   com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app);
   File file;if(e.uri.startsWith("file:")){file=StorageAccess.file(e);if(!StorageAccess.allowed(app,file))throw new IOException("Sem acesso ao PDF.");}
   else{staged=new File(app.getCacheDir(),"seeky-index-"+java.util.UUID.randomUUID()+".pdf");file=staged;}
   try{if(staged!=null)try(InputStream in=resolver.openInputStream(Uri.parse(e.uri))){if(in==null)throw new IOException("PDF indisponível.");StudyFiles.copy(in,staged,33554432,()->cancel.isCanceled());}
    PdfIndexText.Result result=PdfIndexText.read(file,app.getCacheDir(),cancel::isCanceled);value=result.text;
    if(value.trim().isEmpty())throw new IOException("Este PDF não contém texto extraível nas páginas analisadas. OCR ainda não disponível; índice anterior conservado.");
    message="Texto PDF preparado · "+result.pages+" / "+result.total+" páginas"+(result.truncated?" · índice parcial (limite de páginas, tempo ou texto).":".");
   }finally{if(staged!=null)staged.delete();}
  }else{
   if(!ContentText.supported(e.name))throw new IOException("Extracção deste formato ainda não disponível");
   if(e.size>16777216)throw new IOException("Limite de 16 MB para extracção de texto");
   try(InputStream in=resolver.openInputStream(Uri.parse(e.uri))){if(in==null)throw new IOException("Ficheiro indisponível");value=ContentText.extract(in,e.name);}
  }
  cancel.throwIfCanceled();put(e,Rules.normalize(value),System.currentTimeMillis());return message;
 }

 void put(Entry e,String body,long gen){ContentValues v=new ContentValues();v.put("uri",e.uri);v.put("root",e.root);v.put("name",e.name);v.put("path",e.path);v.put("mime",e.mime);v.put("category",e.category);v.put("size",e.size);v.put("modified",e.modified);v.put("norm",Rules.normalize(e.name));v.put("body",body);v.put("gen",gen);v.put("parent",e.uri.startsWith("file:")?new File(e.path).getParent():"");SQLiteDatabase d=getWritableDatabase();if(d.update("files",v,"uri=?",new String[]{e.uri})==0)d.insertOrThrow("files",null,v);}
 void opened(Entry e){try(Cursor check=getReadableDatabase().rawQuery("SELECT 1 FROM files WHERE uri=?",new String[]{e.uri})){if(!check.moveToFirst())put(e,"",System.currentTimeMillis());}ContentValues v=new ContentValues();v.put("uri",e.uri);v.put("opened",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("recent",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 Entry byUri(String uri){try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,EXISTS(SELECT 1 FROM favorites f WHERE f.uri=files.uri) FROM files WHERE uri=? AND ("+scope()+") LIMIT 1",new String[]{uri})){if(!c.moveToFirst())return null;Entry e=entry(c);decorate(Collections.singletonList(e));return e;}}
 List<Entry> recent(){List<Entry>a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,EXISTS(SELECT 1 FROM favorites f WHERE f.uri=files.uri) FROM files WHERE "+scope()+" AND uri IN (SELECT uri FROM recent) ORDER BY (SELECT opened FROM recent WHERE recent.uri=files.uri) DESC LIMIT 12",null)){while(c.moveToNext())a.add(entry(c));}decorate(a);return a;}
 void migrateFavorites(){
  List<String> old=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM favorites WHERE uri LIKE 'content://com.android.externalstorage.documents/%'",null)){while(c.moveToNext())old.add(c.getString(0));}
  for(String uri:old)try{String id=DocumentsContract.getDocumentId(Uri.parse(uri));int split=id.indexOf(':');if(split<0)continue;File root=null;if(id.startsWith("primary:"))root=android.os.Environment.getExternalStorageDirectory();else for(File volume:StorageAccess.volumes(app))if(volume.getName().equals(id.substring(0,split)))root=volume;if(root==null)continue;File f=new File(root,id.substring(split+1));if(!StorageAccess.allowed(app,f)||!f.isFile())continue;ContentValues v=new ContentValues();v.put("uri",Uri.fromFile(f).toString());getWritableDatabase().insertWithOnConflict("favorites",null,v,SQLiteDatabase.CONFLICT_IGNORE);}catch(Exception ignored){}
 }
 List<Entry> query(String q,String cat,boolean fav,boolean content,int sort,int limit){return query(q,cat,fav,content,sort,limit,0,0,"");}
 List<Entry> query(String q,String category,boolean favorites,boolean content,int sort,int limit,long minSize,int days,String extension){return query(q,category,favorites,content,sort,limit,minSize,days,extension,null);}
 List<Entry> query(String q,String category,boolean favorites,boolean content,int sort,int limit,long minSize,int days,String extension,CancellationSignal signal){return query(q,category,favorites,content,sort,limit,minSize,days,extension,signal,sort!=1,"","");}
 List<Entry> query(String q,String category,boolean favorites,boolean content,int sort,int limit,long minSize,int days,String extension,CancellationSignal signal,boolean descending,String personalCategory,String tagFilter){
  if(signal!=null)signal.throwIfCanceled();
  String where=scope();ArrayList<String> args=new ArrayList<>();
  if(!q.trim().isEmpty()){
   where+=" AND (norm LIKE ? ESCAPE '\\'";args.add(Rules.likeQuery(q.trim()));
   if(q.indexOf('*')<0&&q.indexOf('?')<0){StringBuilder fts=new StringBuilder();for(String token:Rules.normalize(q).split("[^\\p{L}\\p{N}]+")){if(!token.isEmpty()){if(fts.length()>0)fts.append(" AND ");fts.append('"').append(token).append("*\"");}}if(fts.length()>0){where+=" OR _id IN (SELECT docid FROM search WHERE "+(content?"search":"name")+" MATCH ?)";args.add(fts.toString());}}
   where+=" OR EXISTS(SELECT 1 FROM metadata m WHERE m.uri=files.uri AND m.norm LIKE ? ESCAPE '\\')";args.add(Rules.likeQuery(q.trim()));where+=")";
  }
  if(!category.equals("Tudo")){where+=" AND category=?";args.add(category);}
  if(favorites)where+=" AND EXISTS(SELECT 1 FROM favorites f WHERE f.uri=files.uri)";
  if(minSize>0){where+=" AND size>=?";args.add(Long.toString(minSize));}
  if(days>0){where+=" AND modified>=?";args.add(Long.toString(System.currentTimeMillis()-days*86400000L));}
  if(!extension.trim().isEmpty()){where+=" AND norm LIKE ? ESCAPE '\\'";args.add("%"+Rules.like("."+extension.replace(".","").trim()).substring(1).replaceAll("%$",""));}
  if(!personalCategory.isEmpty()){where+=" AND coalesce(nullif("+FileOrder.metadata("category_norm")+",''),lower(category)) LIKE ? ESCAPE '\\'";args.add(Rules.like(personalCategory));}
  if(!tagFilter.isEmpty()){where+=" AND "+FileOrder.metadata("tags_norm")+" LIKE ? ESCAPE '\\'";args.add(Rules.like(tagFilter));}
  String order=FileOrder.sql(sort,descending);
  List<Entry>a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,EXISTS(SELECT 1 FROM favorites f WHERE f.uri=files.uri) FROM files WHERE "+where+" ORDER BY "+order+" LIMIT "+Math.min(limit,50000),args.toArray(new String[0]),signal)){while(c.moveToNext()){if(signal!=null)signal.throwIfCanceled();a.add(entry(c));}}decorate(a);return a;
 }
 List<Entry> flash(String text,String category,CancellationSignal signal){
  String expression=FlashQuery.expression(text);if(expression.isEmpty())return text.trim().isEmpty()?recent():Collections.emptyList();
  String where=scope()+" AND _id IN (SELECT docid FROM search WHERE name MATCH ?)";List<String> args=new ArrayList<>();args.add(expression);
  if(!category.equals("Tudo")){where+=" AND category=?";args.add(category);}
  List<Entry> result=new ArrayList<>();
  try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,0 FROM files WHERE "+where+" LIMIT 30",args.toArray(new String[0]),signal)){while(c.moveToNext()){signal.throwIfCanceled();result.add(entry(c));}}return result;
 }
 private Entry entry(Cursor c){Entry e=new Entry();e.uri=c.getString(0);e.root=c.getString(1);e.name=c.getString(2);e.path=c.getString(3);e.mime=c.getString(4);e.category=c.getString(5);e.size=c.getLong(6);e.modified=c.getLong(7);e.favorite=c.getInt(8)>0;return e;}
 long[] totals(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*),coalesce(sum(max(size,0)),0) FROM files WHERE "+scope(),null)){c.moveToFirst();return new long[]{c.getLong(0),c.getLong(1)};}}
 List<String[]> stats(){List<String[]>a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT category,count(*),sum(max(size,0)) FROM files WHERE "+scope()+" GROUP BY category ORDER BY sum(max(size,0)) DESC",null)){while(c.moveToNext())a.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});}return a;}
 Set<String> favoriteUris(){Set<String> out=new HashSet<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri FROM favorites",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
 void favorite(Entry e){if(!e.favorite&&!e.directory){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM files WHERE uri=?",new String[]{e.uri})){if(!c.moveToFirst())put(e,"",System.currentTimeMillis());}}if(e.favorite)getWritableDatabase().delete("favorites","uri=?",new String[]{e.uri});else{ContentValues v=new ContentValues();v.put("uri",e.uri);getWritableDatabase().insertWithOnConflict("favorites",null,v,SQLiteDatabase.CONFLICT_IGNORE);}e.favorite=!e.favorite;}
 long save(String title,String q,String cat,boolean content){ContentValues v=new ContentValues();v.put("title",title);v.put("query",q);v.put("category",cat);v.put("content",content?1:0);return getWritableDatabase().insertOrThrow("saved",null,v);}
 List<String[]> saved(){List<String[]> a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,query,category,content FROM saved ORDER BY id DESC",null)){while(c.moveToNext())a.add(new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)});}return a;}
 void rename(Entry e,String name,boolean log)throws Exception {
  if(!Rules.validName(name))throw new IOException("Nome inválido");try(Cursor exists=getReadableDatabase().rawQuery("SELECT 1 FROM files WHERE uri=?",new String[]{e.uri})){if(!exists.moveToFirst())put(e,"",System.currentTimeMillis());}Uri changed;
  if(e.uri.startsWith("file:")){File original=StorageAccess.file(e);if(!StorageAccess.allowed(app,original))throw new SecurityException("Autorize o acesso geral aos ficheiros.");File target=new File(original.getParentFile(),name);if(!StorageAccess.allowed(app,target))throw new SecurityException("Destino fora do armazenamento autorizado.");java.nio.file.Files.move(original.toPath(),target.toPath());changed=Uri.fromFile(target);MediaUpdates.changed(app,original,target);}
  else changed=DocumentsContract.renameDocument(resolver,Uri.parse(e.uri),name);if(changed==null)throw new IOException("Esta pasta não permite renomear");
  if(!"file".equals(changed.getScheme()))try(Cursor actual=resolver.query(changed,new String[]{"_display_name"},null,null,null)){if(actual!=null&&actual.moveToFirst()&&actual.getString(0)!=null)name=actual.getString(0);}
  SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{copyDetails(e.uri,changed.toString(),true);ContentValues v=new ContentValues();v.put("uri",changed.toString());v.put("name",name);v.put("norm",Rules.normalize(name));String newmime="file".equals(changed.getScheme())?StorageAccess.mime(new File(changed.getPath())):e.mime;v.put("mime",newmime);v.put("category",Rules.category(name,newmime));int slash=e.path.lastIndexOf('/');v.put("path",(slash<0?"":e.path.substring(0,slash+1))+name);v.put("parent","file".equals(changed.getScheme())?new File(v.getAsString("path")).getParent():"");int updated=d.update("files",v,"uri=?",new String[]{e.uri});if(updated==0){Entry replacement=new Entry();replacement.uri=changed.toString();replacement.root=e.root;replacement.name=name;replacement.path=v.getAsString("path");replacement.mime=newmime;replacement.category=Rules.category(name,newmime);replacement.size=e.size;replacement.modified=e.modified;put(replacement,"",System.currentTimeMillis());}ContentValues fav=new ContentValues();fav.put("uri",changed.toString());d.updateWithOnConflict("favorites",fav,"uri=?",new String[]{e.uri},SQLiteDatabase.CONFLICT_REPLACE);ContentValues moved=new ContentValues();moved.put("uri",changed.toString());d.updateWithOnConflict("recent",moved,"uri=?",new String[]{e.uri},SQLiteDatabase.CONFLICT_REPLACE);d.update("history",moved,"uri=?",new String[]{e.uri});if(log){ContentValues h=new ContentValues();h.put("uri",changed.toString());h.put("oldname",e.name);h.put("newname",name);d.insertOrThrow("history",null,h);}d.setTransactionSuccessful();}finally{d.endTransaction();}
 }
 String[] lastRename(){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,uri,oldname,newname FROM history WHERE done=0 ORDER BY id DESC LIMIT 1",null)){if(c.moveToFirst())return new String[]{c.getString(0),c.getString(1),c.getString(2),c.getString(3)};}return null;}
 void undoRename(String[] h)throws Exception {Entry e=null;try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,0 FROM files WHERE uri=?",new String[]{h[1]})){if(c.moveToFirst())e=entry(c);}if(e==null)throw new IOException("Ficheiro não encontrado no catálogo");rename(e,h[2],false);ContentValues v=new ContentValues();v.put("done",1);getWritableDatabase().update("history",v,"id=?",new String[]{h[0]});}
 List<List<Entry>> duplicates(CancellationSignal cancel,Progress progress)throws Exception {
  Map<Long,List<Entry>> sizes=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT uri,root,name,path,mime,category,size,modified,0 FROM files WHERE size>0 AND "+scope()+" AND size IN (SELECT size FROM files WHERE size>0 AND "+scope()+" GROUP BY size HAVING count(*)>1)",null)){while(c.moveToNext()){Entry e=entry(c);sizes.computeIfAbsent(e.size,k->new ArrayList<>()).add(e);}}
  Map<String,List<Entry>> hashes=new LinkedHashMap<>();int count=0,failed=0;
  for(List<Entry> list:sizes.values())for(Entry e:list){cancel.throwIfCanceled();progress.update("A comparar "+(++count)+" · "+e.name);try(InputStream in=resolver.openInputStream(Uri.parse(e.uri))){if(in==null)continue;MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;long bytes=0;while((n=in.read(b))!=-1){cancel.throwIfCanceled();digest.update(b,0,n);bytes+=n;}if(bytes!=e.size){failed++;continue;}String hash=Base64.getEncoder().encodeToString(digest.digest());hashes.computeIfAbsent(hash,k->new ArrayList<>()).add(e);}catch(OperationCanceledException ex){throw ex;}catch(Exception ex){failed++;}}
  List<List<Entry>> result=new ArrayList<>();for(List<Entry> list:hashes.values())if(list.size()>1)result.add(list);duplicateSummary=result.size()+" grupos de duplicados"+(failed>0?" · "+failed+" ficheiros não comparados":"");progress.update(duplicateSummary);return result;
 }
}
