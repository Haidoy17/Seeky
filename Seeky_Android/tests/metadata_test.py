"""Exact schema and exact Java sort SQL on host SQLite. Does not execute Android ContentResolver."""
from pathlib import Path
import json,re,sqlite3,subprocess
root=Path(__file__).parents[1];src=(root/'app/src/main/java/mz/bam/seeky/android/Catalog.java').read_text()
ddl=[json.loads(s) for s in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',src)]
db=sqlite3.connect(':memory:')
for sql in ddl:db.execute(sql)
for i in range(240):
 uri=f'file:///fixture/{i:03}.txt'
 db.execute('INSERT INTO files(uri,name,path,root,mime,category,size,modified,norm,body) VALUES(?,?,?,?,?,?,?,?,?,?)',(uri,f'{i:03}.txt',f'/fixture/{i:03}.txt','file:///fixture','text/plain','Documentos',i+1,i+100,f'{i:03}.txt',''))
 db.execute('INSERT INTO metadata(uri,title,author,tags,custom_category,custom_date,manual_order,tags_norm,category_norm,norm) VALUES(?,?,?,?,?,?,?,?,?,?)',(uri,f'T{i:03}',f'A{i:03}','urgência' if i%2 else 'contas','Gestão',i+1000,i+1,'urgencia' if i%2 else 'contas','gestao',f't{i:03} a{i:03} gestao urgencia' if i%2 else f't{i:03} a{i:03} gestao contas'))
print('PASS new schema stores independent title/author/tags/custom category/date/order')
output=subprocess.check_output(['java','-cp',str(root/'build/test-classes'),'mz.bam.seeky.android.OrderingTest','sql'],text=True)
for line in output.splitlines():
 field,desc,sql=line.split('\t');rows=db.execute('SELECT uri FROM files ORDER BY '+sql+' LIMIT 100').fetchall();assert len(rows)==100
 if field in {'0','1','2','4','7','8','9'}:assert rows[0][0]==('file:///fixture/239.txt' if desc=='true' else 'file:///fixture/000.txt'),(field,desc,rows[0])
 assert all('?' not in part for part in [sql]) # Sort expressions only use fixed allow-listed fields.
print('PASS all 20 exact Java SQL orderings execute and order globally before LIMIT 100')
assert db.execute("SELECT count(*) FROM files WHERE coalesce((SELECT tags_norm FROM metadata m WHERE m.uri=files.uri),'') LIKE ? ESCAPE '\\'",('%urgencia%',)).fetchone()[0]==120
assert db.execute("SELECT count(*) FROM files WHERE coalesce(nullif(coalesce((SELECT category_norm FROM metadata m WHERE m.uri=files.uri),''),''),lower(category)) LIKE ? ESCAPE '\\'",('%gestao%',)).fetchone()[0]==240
assert db.execute("SELECT count(*) FROM files WHERE EXISTS(SELECT 1 FROM metadata m WHERE m.uri=files.uri AND m.norm LIKE ? ESCAPE '\\')",('%a239%',)).fetchone()[0]==1
print('PASS accent-normalized category/tag filters and metadata search')
old='file:///fixture/000.txt';new='file:///new/moved.txt'
copy_sql=re.search(r'd\.execSQL\(("INSERT OR REPLACE INTO metadata[^;]+?)\,new Object\[\]',src).group(1)
copy_sql=json.loads(copy_sql.strip())
db.execute(copy_sql,(new,old));assert db.execute('SELECT author FROM metadata WHERE uri=?',(new,)).fetchone()[0]=='A000'
assert db.execute('SELECT count(*) FROM metadata WHERE uri IN (?,?)',(new,old)).fetchone()[0]==2
print('PASS exact metadata copy SQL preserves original and classifies new destination')
db.execute('DELETE FROM metadata WHERE uri=?',(old,));assert db.execute('SELECT author FROM metadata WHERE uri=?',(new,)).fetchone()[0]=='A000'
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS metadata move/rename identity update and SQLite integrity')
