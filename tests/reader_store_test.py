"""ReaderStore schema persistence on host SQLite; relocation methods run in notebook-host."""
from pathlib import Path
import hashlib,json,re,sqlite3,tempfile
r=Path(__file__).parents[1];s=(r/'app/src/main/java/mz/bam/seeky/android/ReaderStore.java').read_text()
ddl=[json.loads(x) for x in re.findall(r'd\.execSQL\(("CREATE TABLE[^;]+?")\)',s)]
assert len(ddl)==4
with tempfile.TemporaryDirectory() as temp:
 path=Path(temp)/'reader.db';db=sqlite3.connect(path)
 for statement in ddl:db.execute(statement)
 old='file:///original.pdf';new='file:///renamed.pdf';source='Primeiro texto';digest=hashlib.sha256(source.encode()).hexdigest();marks=json.dumps([{'s':0,'e':8,'k':'highlight','n':''},{'s':9,'e':14,'k':'note','n':'Rever amanhã'}])
 db.execute('INSERT INTO draft VALUES(?,?,?,?,?)',(old,0,digest,source,marks));db.execute('INSERT INTO draft VALUES(?,?,?,?,?)',(old,1,digest,'Outro capítulo','[]'));db.commit();db.close();db=sqlite3.connect(path)
 row=db.execute('SELECT value,marks FROM draft WHERE uri=? AND segment=? AND digest=?',(old,0,digest)).fetchone();assert row[0]==source and json.loads(row[1])[1]['n']=='Rever amanhã'
 print('PASS notebook text, Unicode notes and ranges survive database reopen')
 assert not db.execute('SELECT * FROM draft WHERE uri=? AND segment=? AND digest=?',(old,0,hashlib.sha256(b'changed').hexdigest())).fetchall()
 print('PASS source digest isolates stale annotations after source content changes')
 db.execute('INSERT OR REPLACE INTO draft VALUES(?,?,?,?,?)',(old,0,digest,'Novo texto','[]'));assert db.execute('SELECT COUNT(*) FROM draft').fetchone()[0]==2
 print('PASS updating one draft leaves other chapters intact')
 db.executemany('INSERT INTO bookmark(uri,segment,position,label,created) VALUES(?,?,?,?,?)',[(old,0,4,'Revisão',100),(old,1,7,'Estudar',101)])
 # Set up a renamed reference for the remaining schema checks.
 # The actual relocation method, conflicts and rollback run in PersistenceSafetyCheck.
 db.execute('UPDATE draft SET uri=? WHERE uri=?',(new,old))
 db.execute('UPDATE bookmark SET uri=? WHERE uri=?',(new,old))
 db.execute('DELETE FROM draft WHERE uri=? AND segment=? AND digest=?',(new,0,digest));assert db.execute('SELECT COUNT(*) FROM draft').fetchone()[0]==1 and db.execute('SELECT COUNT(*) FROM bookmark').fetchone()[0]==2
 print('PASS resetting one part removes only its draft and retains bookmarks')
 db.execute('DELETE FROM bookmark WHERE id=?',(1,));assert db.execute('SELECT position FROM bookmark').fetchone()[0]==7
 print('PASS removing a bookmark retains other positions')
 assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
 print('PASS reader notebook SQLite integrity')
