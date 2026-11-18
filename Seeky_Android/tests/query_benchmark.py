"""Deterministic host SQLite benchmark. This does not measure Android UI or FPS."""
import json, re, sqlite3, statistics, time
from pathlib import Path
root=Path(__file__).parents[1]
source=(root/'app/src/main/java/mz/bam/seeky/android/Catalog.java').read_text()
ddl=[json.loads(sql) for sql in re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\)',source)]
indexes=[sql for sql in ddl if any('CREATE INDEX '+name+' ' in sql for name in ['files_modified','files_size','files_category_modified'])]
before=sqlite3.connect(':memory:')
for sql in ddl:
    if sql not in indexes: before.execute(sql)
count=40000
before.executemany('INSERT INTO files(uri,root,name,path,mime,category,size,modified,norm,body,gen) VALUES(?,?,?,?,?,?,?,?,?,?,?)',
    ((f'file:///fixture/{i}.txt','file:///fixture',f'ficheiro_{i}.txt',f'/fixture/{i}.txt','text/plain','Documentos' if i%2 else 'Outros',i*1024,1700000000000+i,f'ficheiro_{i}.txt','',1) for i in range(count)))
before.commit()
after=sqlite3.connect(':memory:');before.backup(after)
for sql in indexes: after.execute(sql)
queries={
 'recentes':"SELECT uri,name,path,size FROM files WHERE uri LIKE 'file:%' ORDER BY modified DESC,uri LIMIT 100",
 'grandes':"SELECT uri,name,path,size FROM files WHERE uri LIKE 'file:%' AND size>=1048576 ORDER BY size DESC,uri LIMIT 100",
 'categoria':"SELECT uri,name,path,size FROM files WHERE uri LIKE 'file:%' AND category='Documentos' ORDER BY modified DESC,uri LIMIT 100"
}
results={}
for name,sql in queries.items():
    expected=list(before.execute(sql));assert expected==list(after.execute(sql))
    def measure(db):
        for _ in range(3):list(db.execute(sql))
        samples=[]
        for _ in range(15):
            start=time.perf_counter();list(db.execute(sql));samples.append((time.perf_counter()-start)*1000)
        return round(statistics.median(samples),3)
    plan=[r[3] for r in after.execute('EXPLAIN QUERY PLAN '+sql)]
    assert not any('TEMP B-TREE' in p for p in plan),plan
    assert any('files_' in p for p in plan),plan
    results[name]={'before_median_ms':measure(before),'after_median_ms':measure(after),'plan_after':plan,'same_results':True}
report={'environment':'Host Python SQLite in memory, not Android','fixture_rows':count,'warmups':3,'samples':15,'queries':results}
target=root/'build/query_benchmark.json';target.parent.mkdir(exist_ok=True);target.write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
