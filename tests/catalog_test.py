"""Real SQLite checks against exact Catalog DDL. Not Android provider integration."""
import json,re,sqlite3,tempfile
from pathlib import Path
source=(Path(__file__).parents[1]/'app/src/main/java/mz/bam/seeky/android/Catalog.java').read_text()
ddl=[json.loads(s) for s in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',source)]
upgrade=[json.loads(s) for s in re.findall(r'd\.execSQL\(("(?:[^"\\]|\\.)*")\)',source)]
db=sqlite3.connect(':memory:')
for sql in ddl:db.execute(sql)
def add(target,uri,name,body='logistica beira',parent='/storage/Download'):
 target.execute('INSERT INTO files(uri,root,name,path,mime,category,size,modified,norm,body,gen,parent) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',(uri,'file:///storage',name,parent+'/'+name,'text/plain','Documentos',20,100,name.lower(),body,1,parent))
add(db,'uri:a','orcamento.txt');add(db,'uri:b','100%_final.txt','foto');db.commit()
assert list(db.execute('SELECT docid FROM search WHERE search MATCH ?',('"logistica*" AND "beira*"',)))==[(1,)]
assert list(db.execute("SELECT name FROM files WHERE norm LIKE ? ESCAPE '\\'",('%100\\%\\_final%',)))==[('100%_final.txt',)]
assert list(db.execute('SELECT docid FROM search WHERE name MATCH ?',('"orcamento*"',)))==[(1,)]
assert not list(db.execute('SELECT docid FROM search WHERE name MATCH ?',('"logistica*"',)))
db.execute("UPDATE files SET gen=2 WHERE uri='uri:a'");assert db.execute('SELECT body FROM files WHERE uri=?',('uri:a',)).fetchone()[0]=='logistica beira'
db.execute("UPDATE files SET norm='novo.txt',body='conteudo novo' WHERE uri='uri:a'")
assert not list(db.execute('SELECT docid FROM search WHERE search MATCH ?',('logistica',)))
assert list(db.execute('SELECT docid FROM search WHERE search MATCH ?',('conteudo',)))==[(1,)]
db.commit()
try:
 db.execute('BEGIN');db.execute("DELETE FROM files WHERE uri='uri:a'");raise InterruptedError()
except InterruptedError:db.rollback()
assert list(db.execute('SELECT docid FROM search WHERE search MATCH ?',('conteudo',)))==[(1,)]
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS exact schema, FTS insert/update/delete, name-only FTS, literal LIKE, body preservation and rollback')
for version in [1,2,3,4,5]:
 legacy=sqlite3.connect(':memory:')
 for sql in ddl:
  if any(part in sql for part in ['CREATE TABLE collections','CREATE TABLE collection_items','CREATE INDEX collection_uri','CREATE TABLE activity','CREATE INDEX activity_created']):continue
  if version<5 and 'CREATE TABLE metadata' in sql:continue
  if version<4 and ('CREATE TABLE folders' in sql or 'CREATE INDEX files_parent' in sql):continue
  if version==1 and 'CREATE TABLE recent' in sql:continue
  if version<3 and any('CREATE INDEX '+n+' ' in sql for n in ['files_modified','files_size','files_category_modified']):continue
  legacy.execute(sql.replace(',parent TEXT)',')') if version<4 else sql)
 legacy.execute("INSERT INTO files(uri,root,name,path,norm,body) VALUES('file:///storage/Download/relatorio.pdf','file:///storage','relatorio.pdf','/storage/Download/relatorio.pdf','relatorio.pdf','texto preservado')")
 if version>=4:legacy.execute("UPDATE files SET parent='/storage/Download'")
 if version>=5:legacy.execute("INSERT INTO metadata(uri,title,author) VALUES('file:///storage/Download/relatorio.pdf','Titulo preservado','Autor preservado')")
 legacy.execute("INSERT INTO favorites VALUES('file:///storage/Download/relatorio.pdf')")
 legacy.execute("INSERT INTO saved(title,query,category,content) VALUES('Trabalho','relatorio','Tudo',0)")
 legacy.execute("INSERT INTO roots(uri,name,scanned) VALUES('file:///storage','Memória',1)")
 if version>=2:legacy.execute("INSERT INTO recent VALUES('file:///storage/Download/relatorio.pdf',123)")
 for sql in upgrade:
  if 'recent' in sql and version>=2:continue
  if 'CREATE TABLE IF NOT EXISTS metadata' in sql and version>=5:continue
  if version>=4 and any(x in sql for x in ['ADD COLUMN parent','SET parent=','files_parent','CREATE TABLE IF NOT EXISTS folders']):continue
  if any(n in sql for n in ['files_modified','files_size','files_category_modified']) and version>=3:continue
  legacy.execute(sql)
 assert legacy.execute('SELECT count(*) FROM favorites').fetchone()[0]==1
 assert legacy.execute('SELECT title FROM saved').fetchone()[0]=='Trabalho'
 assert legacy.execute('SELECT parent FROM files').fetchone()[0]=='/storage/Download'
 assert legacy.execute('SELECT count(*) FROM search WHERE search MATCH ?',('preservado',)).fetchone()[0]==1
 if version>=2:assert legacy.execute('SELECT opened FROM recent').fetchone()[0]==123
 assert legacy.execute('SELECT count(*) FROM folders').fetchone()[0]==0
 if version>=5:assert legacy.execute('SELECT title,author FROM metadata').fetchone()==('Titulo preservado','Autor preservado')
 assert legacy.execute('SELECT count(*) FROM collections').fetchone()[0]==0
 assert legacy.execute('SELECT count(*) FROM activity').fetchone()[0]==0
 assert legacy.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
 print(f'PASS migration {version}→6 preserves files/FTS/favorites/searches/roots/history and builds parent index')
with tempfile.TemporaryDirectory(prefix='seeky-batches-') as folder:
 filename=Path(folder)/'catalog.db';writer=sqlite3.connect(filename);writer.execute('PRAGMA journal_mode=WAL')
 for sql in ddl:writer.execute(sql)
 writer.commit();reader=sqlite3.connect(filename)
 add(writer,'file:///storage/Download/first.psd','first.psd','');writer.commit()
 assert reader.execute('SELECT count(*) FROM files').fetchone()[0]==1
 writer.execute('BEGIN');add(writer,'file:///storage/Download/second.pdf','second.pdf','')
 assert reader.execute('SELECT count(*) FROM files').fetchone()[0]==1
 writer.commit();assert reader.execute('SELECT count(*) FROM files').fetchone()[0]==2
 assert reader.execute('SELECT count(*) FROM files WHERE parent=?',('/storage/Download',)).fetchone()[0]==2
 reader.close();writer.close()
print('PASS committed names visible to a second connection before subsequent batches complete')
print('PASS catalog integrity and directory-local index lookups')
# Move keeps bookmarks/history even if a filesystem observer removed the old file row first.
for observer_removed in [False,True]:
 old='file:///storage/Download/move.txt';new='file:///storage/Documents/move.txt'
 db.execute('DELETE FROM files WHERE uri IN (?,?)',(old,new))
 db.execute('DELETE FROM favorites WHERE uri IN (?,?)',(old,new))
 db.execute('DELETE FROM recent WHERE uri IN (?,?)',(old,new))
 add(db,old,'move.txt');db.execute('INSERT INTO favorites VALUES(?)',(old,));db.execute('INSERT INTO recent VALUES(?,321)',(old,));db.commit()
 if observer_removed:db.execute('DELETE FROM files WHERE uri=?',(old,));db.commit()
 with db:
  add(db,new,'move.txt',parent='/storage/Documents')
  db.execute('UPDATE OR REPLACE favorites SET uri=? WHERE uri=?',(new,old))
  db.execute('UPDATE OR REPLACE recent SET uri=? WHERE uri=?',(new,old))
  db.execute('DELETE FROM files WHERE uri=?',(old,))
 assert db.execute('SELECT uri FROM favorites WHERE uri=?',(new,)).fetchone()==(new,)
 assert db.execute('SELECT opened FROM recent WHERE uri=?',(new,)).fetchone()==(321,)
 assert not db.execute('SELECT 1 FROM files WHERE uri=?',(old,)).fetchone()
 assert db.execute('SELECT parent FROM files WHERE uri=?',(new,)).fetchone()==('/storage/Documents',)
print('PASS move metadata preserves favorites/recent with both filesystem-observer orderings (host SQL, not Android)')
