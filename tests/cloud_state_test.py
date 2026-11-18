"""Execute exact v2 table DDL + the documented trigger/attached DB transaction behaviour on host SQLite.
Does not emulate Android Keystore, JobScheduler, UI, or SQLiteDatabase's connection pool.
"""
from pathlib import Path
import sqlite3,re,json,tempfile
r=Path(__file__).parents[1];store=(r/'app/src/main/java/mz/bam/seeky/android/StudyStore.java').read_text();state=(r/'app/src/main/java/mz/bam/seeky/android/StudyState.java').read_text();reader=(r/'app/src/main/java/mz/bam/seeky/android/ReaderStore.java').read_text()
tables=['subjects','materials','notes','cards','questions','tasks','sessions','quiz_runs','reviews','practice','study_trash']
with tempfile.TemporaryDirectory() as directory:
 d=sqlite3.connect(Path(directory)/'main.db')
 ddl=[json.loads(encoded) for encoded in re.findall(r'd\.execSQL\(("CREATE[^;]+?")\)',store)]
 for sql in sorted(ddl,key=lambda sql:not sql.startswith("CREATE TABLE")):d.execute(sql)
 d.execute("INSERT INTO subjects(id,title,color,created) VALUES(1,'Disciplina anterior',123,100)");d.commit()
 for encoded in re.findall(r'd\.execSQL\(("(?:CREATE TABLE|INSERT OR IGNORE)[^;]+?")\)',state):d.execute(json.loads(encoded))
 assert d.execute('SELECT title FROM subjects').fetchone()[0]=='Disciplina anterior';print('PASS study v1 to v2 adds cloud state without changing prior study records')
 for table in tables:
  for action in ['INSERT','UPDATE','DELETE']:d.execute(f'CREATE TRIGGER IF NOT EXISTS dirty_{table}_{action} AFTER {action} ON {table} BEGIN UPDATE study_meta SET revision=revision+1 WHERE id=1; END')
 d.execute("INSERT INTO notes(id,subject,title,body,created,updated) VALUES(7,1,'Nota','Conteúdo',100,100)");d.commit();revision=d.execute('SELECT revision FROM study_meta').fetchone()[0];assert revision==1;print('PASS a committed study write marks workspace revision dirty')
 d.execute('BEGIN');d.execute("UPDATE notes SET body='bad' WHERE id=7");d.rollback();assert d.execute('SELECT revision FROM study_meta').fetchone()[0]==revision;print('PASS rolled-back edits do not advance committed local revision')
 d.execute("INSERT INTO practice(id,payload,updated) VALUES(1,'{\"at\":2,\"typed\":\"Resposta parcial\"}',100)");d.commit();d.close();d=sqlite3.connect(Path(directory)/'main.db');assert json.loads(d.execute('SELECT payload FROM practice').fetchone()[0])['typed']=='Resposta parcial';print('PASS paused test position and partial response survive a database reopen')
 rs=sqlite3.connect(Path(directory)/'reader.db')
 for encoded in re.findall(r'd\.execSQL\(("CREATE TABLE[^;]+?")\)',reader):rs.execute(json.loads(encoded))
 rs.execute("INSERT INTO bookmark VALUES(1,'content://mz.bam.seeky.estudos.study/material/5',2,31,'Marcador',100)");rs.commit();rs.close();d.execute('ATTACH DATABASE ? AS reader_sync',(str(Path(directory)/'reader.db'),));d.commit()
 d.execute('BEGIN');d.execute('DELETE FROM notes');d.execute('DELETE FROM reader_sync.bookmark');d.rollback();assert d.execute('SELECT id FROM notes').fetchone()[0]==7 and d.execute('SELECT position FROM reader_sync.bookmark').fetchone()[0]==31;print('PASS attached-database restore rollback retains study records and reader marks together')
 before=d.execute('SELECT revision FROM study_meta').fetchone()[0];d.execute('BEGIN');d.execute('DELETE FROM notes');d.execute("INSERT INTO notes(id,subject,title,body,created,updated) VALUES(17,1,'Recuperada','Novo texto',100,100)");d.execute('DELETE FROM reader_sync.bookmark');d.execute("INSERT INTO reader_sync.bookmark(uri,segment,position,label,created) VALUES('content://mz.bam.seeky.estudos.study/material/5',8,77,'Restaurado',100)");prefs=json.dumps({'positions':{'5':{'page':8,'chapter':2,'offset':-31,'scrolls':{'2':812}}},'studies':{'daily_goal':60}});d.execute('UPDATE study_meta SET revision=?,owner=?,remote_revision=?,remote_head=?,pending_prefs=? WHERE id=1',(before+1,'accountA',9,'backup9',prefs));d.commit();assert d.execute('SELECT id FROM notes').fetchall()==[(17,)];assert d.execute('SELECT count(*) FROM reader_sync.bookmark').fetchone()[0]==1;print('PASS exact cloud restore replaces records once and preserves original IDs without appending duplicates')
 data=json.loads(d.execute('SELECT pending_prefs FROM study_meta').fetchone()[0]);assert data['positions']['5']['page']==8 and data['positions']['5']['scrolls']['2']==812;print('PASS reading page offset, chapter scroll and preferences are durably staged for recovery')
 assert d.execute('SELECT remote_revision,remote_head,owner FROM study_meta').fetchone()==(9,'backup9','accountA');print('PASS restored workspace records the authenticated owner and known remote revision')
 expected=d.execute('SELECT revision FROM study_meta').fetchone()[0];d.execute("UPDATE notes SET body='edit after download' WHERE id=17");d.commit();assert d.execute('SELECT revision FROM study_meta').fetchone()[0]!=expected;print('PASS edit after download is detected before destructive cloud application')
 d.execute("INSERT INTO study_trash(table_name,payload,deleted) VALUES('notes','{\"title\":\"Recover me\"}',100)");d.commit();assert d.execute('SELECT table_name FROM study_trash').fetchone()[0]=='notes';print('PASS recoverable study trash is part of the portable workspace')
 assert d.execute('PRAGMA integrity_check').fetchone()[0]=='ok' and d.execute('PRAGMA reader_sync.integrity_check').fetchone()[0]=='ok';print('PASS both workspace databases retain SQLite integrity after restore and rollback')
# Journal cleanup on cloud restore is scoped to the private study-material provider.
snapshot=(r/'app/src/main/java/mz/bam/seeky/android/StudySnapshot.java').read_text()
match=re.search(r'for\(String journal:new String\[\]\{([^}]+)\}\)sql.delete\("reader_sync\."\+journal,"uri LIKE \?"',snapshot)
assert match
journals=json.loads('['+match.group(1)+']');assert journals==['edit_history','cut_clip']
with tempfile.TemporaryDirectory() as folder:
 db=sqlite3.connect(':memory:');db.execute('ATTACH DATABASE ? AS reader_sync',(str(Path(folder)/'reader.db'),))
 for encoded in re.findall(r'd\.execSQL\(("CREATE TABLE[^;]+?")\)',reader):db.execute(json.loads(encoded).replace('CREATE TABLE ','CREATE TABLE reader_sync.',1))
 study='content://mz.bam.seeky.estudos.study/material/1';external='file:///Download/livro.pdf'
 for uri in [study,external]:
  db.execute("INSERT INTO reader_sync.edit_history(uri,segment,digest,before_value,before_marks,after_value,after_marks,label,created) VALUES(?,0,'hash','a','[]','b','[]','edit',1)",(uri,))
  db.execute('INSERT INTO reader_sync.cut_clip(uri,segment,value,created) VALUES(?,0,?,1)',(uri,'recorte'))
 db.commit();db.execute('BEGIN')
 for table in journals:db.execute('DELETE FROM reader_sync.'+table+' WHERE uri LIKE ?',('content://mz.bam.seeky.estudos.study/%',))
 db.rollback();assert db.execute('SELECT count(*) FROM reader_sync.edit_history').fetchone()[0]==2
 for table in journals:db.execute('DELETE FROM reader_sync.'+table+' WHERE uri LIKE ?',('content://mz.bam.seeky.estudos.study/%',))
 db.commit()
 for table in journals:assert db.execute('SELECT uri FROM reader_sync.'+table).fetchall()==[(external,)]
print('PASS cloud restore clears only study-material journal/cut clips and rolls cleanup back on failure')
