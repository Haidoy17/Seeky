from pathlib import Path
import subprocess,json,os,zipfile,hashlib
root=Path(__file__).resolve().parent;src=root/'source/Seeky_Android';bt=Path('/workspace/seeky-review/tools/sdk/android-16');android=Path('/workspace/seeky-review/tools/sdk/android-36/android.jar');evidence=root/'evidence';evidence.mkdir(exist_ok=True)
def run(args):return subprocess.run(list(map(str,args)),check=True,capture_output=True,text=True).stdout
run(['python3',src/'build_delivery.py','--compiled-apk',root/'main-unsigned.apk','--android-jar',android,'--build-tools',bt,'--output',root/'main-aligned.apk'])
signing=Path('/workspace/seeky-next/signing');env=dict(os.environ,SEEKY_LOCAL_BUILD_PASSWORD=json.loads((signing/'local-build.json').read_text())['password'])
apk=root/'Seeky_5_18_0.apk'
subprocess.run(list(map(str,[bt/'apksigner','sign','--ks',signing/'local-build.p12','--ks-key-alias','local-build','--ks-pass','env:SEEKY_LOCAL_BUILD_PASSWORD','--key-pass','env:SEEKY_LOCAL_BUILD_PASSWORD','--out',apk,root/'main-aligned.apk'])),env=env,check=True,capture_output=True,text=True)
sig=run([bt/'apksigner','verify','--verbose','--print-certs',apk]);assert 'a96f8caec94509f4aeb9abe560f23a09bc416d44e5f06d1e93e4e1cdcdc974fe' in sig
run([bt/'zipalign','-c','-P','16','4',apk]);badging=run([bt/'aapt','dump','badging',apk])
for token in ["name='mz.bam.seeky.v51'","versionCode='5004200'","versionName='5.18.0'","application-label:'Seeky'","label='Seeky'"]:assert token in badging,token
manifest=run([bt/'aapt','dump','xmltree',apk,'AndroidManifest.xml']);assert 'seekyestudos' not in manifest and '/callback' not in manifest and 'StudyCloudJob' not in manifest
for name,value in [('main-manifest.txt',manifest),('main-signature.txt',sig),('main-package.txt',badging)]:(evidence/name).write_text(value)
with zipfile.ZipFile(apk) as z:assert z.testzip() is None
meta=json.loads(Path('/workspace/seeky-5170/release.json').read_text());meta.update({'version':'5.18.0','version_code':5004200,'base':'5.17.0','date':'2026-11-15','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'main_apk_bytes':apk.stat().st_size,'robolectric_tests':368,'new_bible_import_tests':30,'mybible_sqlite3_import':True,'mybible_generic_bin_header_detection':True,'usx_versions':[2,3],'usx_import_modes':['file','xml-usx','zip','recursive-folder'],'mybible_comments_dictionary_plan_import':False,'bible_module_max_bytes':268435456,'bible_module_max_verses':200000,'bible_atomic_publication':True,'usx_resume_per_committed_file':True,'sqlite_resume_per_completed_module':True,'large_mybible_fixture_verses':33000,'user_original_bible_module_tested':False,'physical_device_tested':False})
for p in [root/'release.json',src/'release.json']:p.write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'apk':apk.name,'sha256':meta['apk_sha256'],'bytes':apk.stat().st_size,'version':meta['version']}))
