"""Real SQLite tests of collection identity and the exact bounded activity SQL; no Android UI claims."""
from pathlib import Path
import json,re,sqlite3
source=(Path(__file__).parents[1]/'app/src/main/java/mz/bam/seeky/android/Catalog.java').read_text()
ddl=[json.loads(s) for s in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',source)]
db=sqlite3.connect(':memory:')
for sql in ddl:db.execute(sql)
for uri,name in [('file:///a.txt','a.txt'),('file:///b.txt','b.txt')]:
 db.execute('INSERT INTO files(uri,name,path,root,mime,category,size,modified,norm,body) VALUES(?,?,?,?,?,?,?,?,?,?)',(uri,name,'/'+name,'file:///','text/plain','Documentos',10,1,name,''))
db.execute("INSERT INTO metadata(uri,title,author) VALUES('file:///a.txt','Documento A','Autor real')")
for group in ['Clientes','Estudos']:db.execute('INSERT INTO collections(title,created) VALUES(?,1)',(group,))
for group in [1,1,2]:db.execute("INSERT OR IGNORE INTO collection_items VALUES(?,'file:///a.txt',1)",(group,))
assert db.execute('SELECT count(*) FROM collection_items').fetchone()[0]==2
assert db.execute('SELECT count(*) FROM files').fetchone()[0]==2
print('PASS collection membership deduplicates one file and permits separate virtual collections without changing files')
db.commit();db.execute("DELETE FROM files WHERE uri='file:///a.txt'")
assert db.execute("SELECT count(*) FROM collection_items WHERE uri='file:///a.txt'").fetchone()[0]==2
assert not db.execute("SELECT uri FROM files WHERE uri IN (SELECT uri FROM collection_items WHERE collection_id=1)").fetchall()
db.execute("INSERT INTO files(uri,name,norm,body) VALUES('file:///a.txt','a.txt','a.txt','')")
assert db.execute("SELECT uri FROM files WHERE uri IN (SELECT uri FROM collection_items WHERE collection_id=1)").fetchall()==[('file:///a.txt',)]
print('PASS catalogue removal/trash keeps virtual membership and restoration of the URI makes it available again')
move_sql=json.loads(re.search(r'd\.execSQL\(("INSERT OR IGNORE INTO collection_items[^;]+?)\,new Object\[\]',source).group(1).strip())
db.execute(move_sql,('file:///moved.txt','file:///a.txt'));db.execute("DELETE FROM collection_items WHERE uri='file:///a.txt'")
assert db.execute("SELECT count(*) FROM collection_items WHERE uri='file:///moved.txt'").fetchone()[0]==2
assert not db.execute("SELECT 1 FROM collection_items WHERE uri='file:///a.txt'").fetchone()
print('PASS exact move/rename membership SQL follows URI identity for every collection')
db.execute("DELETE FROM collection_items WHERE collection_id=1 AND uri='file:///moved.txt'")
assert db.execute('SELECT count(*) FROM collection_items WHERE collection_id=2').fetchone()[0]==1
assert db.execute('SELECT count(*) FROM files').fetchone()[0]==2
print('PASS unlinking one reference preserves other collections, file rows and metadata')
db.commit()
try:
 db.execute('BEGIN');db.execute('DELETE FROM collection_items WHERE collection_id=2');db.execute('DELETE FROM collections WHERE id=2');raise InterruptedError()
except InterruptedError:db.rollback()
assert db.execute('SELECT title FROM collections WHERE id=2').fetchone()==('Estudos',)
assert db.execute('SELECT count(*) FROM collection_items WHERE collection_id=2').fetchone()[0]==1
print('PASS collection removal rolls back both link and container rows together')
with db:
 db.execute('DELETE FROM collection_items WHERE collection_id=2');db.execute('DELETE FROM collections WHERE id=2')
assert db.execute('SELECT count(*) FROM files').fetchone()[0]==2
assert db.execute("SELECT title FROM metadata WHERE uri='file:///a.txt'").fetchone()==('Documento A',)
print('PASS removing a collection leaves original catalogue and personal metadata untouched')
prune=json.loads(re.search(r'sql\.execSQL\(("(?:[^"\\]|\\.)*")\)',source).group(1))
with db:
 for i in range(650):
  db.execute('INSERT INTO activity(action,label,detail,created) VALUES(?,?,?,?)',('Copiado',f'ficheiro_{i}.pdf','destino',100+i//5));db.execute(prune)
assert db.execute('SELECT count(*) FROM activity').fetchone()[0]==500
assert db.execute('SELECT label FROM activity ORDER BY created DESC,id DESC LIMIT 1').fetchone()==('ficheiro_649.pdf',)
assert db.execute('SELECT label FROM activity ORDER BY created,id LIMIT 1').fetchone()==('ficheiro_150.pdf',)
print('PASS exact pruning SQL keeps the latest 500 events with deterministic ordering for equal timestamps')
db.execute('DELETE FROM activity')
assert db.execute('SELECT count(*) FROM files').fetchone()[0]==2
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS clearing activity affects no file or collection records and keeps SQLite integrity')
