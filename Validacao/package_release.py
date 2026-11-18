from pathlib import Path
import zipfile,json,hashlib,shutil
root=Path(__file__).resolve().parent;src=root/'source/Seeky_Android'
assert 'OK (368 tests)' in (root/'runtime-tests.log').read_text()
assert 'ALL CHECKS COMPLETED' in (root/'host-tests.log').read_text()
meta=json.loads((root/'release.json').read_text());apk=root/'Seeky_5_18_0.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()==meta['apk_sha256']
assert meta['version']=='5.18.0' and meta['version_code']==5004200
preview=root/'Previa_Importacao_USX.png';shutil.copyfile(root/'evidence/layout/viewer-layout-double-5150-importar-usx-5180.png',preview)
installer=root/'Seeky_5_18_0_Instalar.zip'
with zipfile.ZipFile(installer,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for path in [apk,root/'LEIA_PRIMEIRO.txt',root/'Relatorio_5_18_0.md',root/'release.json',preview]:z.write(path,path.name)
sourceZip=root/'Seeky_5_18_0_Codigo_e_Relatorio.zip'
with zipfile.ZipFile(sourceZip,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for path in sorted(src.rglob('*')):
  rel=path.relative_to(src)
  if not path.is_file() or any(p in {'build','.git','__pycache__','.gradle','signing'} for p in rel.parts) or path.suffix.lower() in {'.p12','.jks','.keystore','.apk','.idsig','.pyc'}:continue
  z.write(path,'Seeky_Android/'+str(rel))
 for name in ['runtime-tests.log','host-tests.log','build.log','sign-release.log','Relatorio_5_18_0.md','LEIA_PRIMEIRO.txt','release.json','Previa_Importacao_USX.png','sign_release.py','package_release.py']:z.write(root/name,'Validacao/'+name)
 for path in sorted((root/'evidence').rglob('*')):
  if path.is_file():z.write(path,'Validacao/evidence/'+str(path.relative_to(root/'evidence')))
files=[apk,installer,sourceZip,preview]
for path in [installer,sourceZip]:
 with zipfile.ZipFile(path) as z:assert z.testzip() is None
(root/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in files))
print(json.dumps([{'name':p.name,'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files],indent=2))
