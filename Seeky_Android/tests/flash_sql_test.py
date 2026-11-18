"""SQLite FTS4 integration and host-only latency sample. Not phone latency."""
from pathlib import Path
import json,re,sqlite3,time,statistics
root=Path(__file__).parents[1]
source=(root/'app/src/main/java/mz/bam/seeky/android/Catalog.java').read_text()
db=sqlite3.connect(':memory:')
for encoded in re.findall(r'db.execSQL\(("(?:[^"\\]|\\.)*")\)',source):
 sql=json.loads(encoded)
 if sql.startswith('CREATE TABLE files(') or sql.startswith('CREATE VIRTUAL TABLE search ') or sql.startswith('CREATE TRIGGER file_'):
  db.execute(sql)
rows=[]
for i in range(100000):
 name=('algebra revisao' if i%1000==0 else 'documento')+str(i)+'.pdf'
 rows.append((i+1,'file:///files/'+name,'file:///files',name,'/files/'+name,'application/pdf','PDF',10,i,name,'',1,'/files'))
db.executemany('INSERT INTO files VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',rows)
statement='SELECT uri,root,name,path,mime,category,size,modified,0 FROM files WHERE _id IN (SELECT docid FROM search WHERE name MATCH ?) AND category=? LIMIT 30'
args=('"algebra*" AND "revisao*"','PDF')
result=db.execute(statement,args).fetchall()
assert len(result)==30 and all('algebra revisao' in row[2] for row in result)
assert not db.execute(statement,('"missing*"','PDF')).fetchall()
assert not db.execute(statement,('"algebra*"','Imagens')).fetchall()
db.execute("UPDATE files SET norm='geometria nova' WHERE _id=1")
assert db.execute(statement,('"geometria*"','PDF')).fetchone() is not None
db.execute('DELETE FROM files WHERE _id=1')
assert db.execute(statement,('"geometria*"','PDF')).fetchone() is None
plan=[row[3] for row in db.execute('EXPLAIN QUERY PLAN '+statement,args)]
assert any('VIRTUAL TABLE INDEX' in line for line in plan)
samples=[]
for _ in range(100):
 start=time.perf_counter();db.execute(statement,args).fetchall();samples.append((time.perf_counter()-start)*1000)
print(json.dumps({'host_only':True,'files':100000,'results_limit':30,'sample_count':100,'median_ms':round(statistics.median(samples),3),'p95_ms':round(sorted(samples)[94],3),'plan':plan,'checks':['prefix intersection','empty results','category filter','rename trigger','delete trigger','FTS index used']},indent=2))
print('PASS Flash FTS4 integration; host timings are not Android measurements')
