#!/usr/bin/env python3
"""Offline build with an already installed Android SDK and JDK/ECJ.
No downloads, dependency install, executable wrappers or remote execution.
"""
import argparse, os, shutil, subprocess, sys, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import app_manifest
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--android-jar', required=True, type=Path)
p.add_argument('--build-tools', required=True, type=Path)
p.add_argument('--ecj', type=Path, help='Optional Eclipse Java compiler JAR if javac is unavailable')
signing=p.add_mutually_exclusive_group(required=True)
signing.add_argument('--keystore',type=Path,help='Private signing file outside the source tree')
signing.add_argument('--unsigned',action='store_true',help='Build an aligned APK for later signing; cannot be installed yet')
p.add_argument('--key-alias')
p.add_argument('--password-env',default='SEEKY_SIGN_PASSWORD',help='Environment variable containing keystore password')
p.add_argument('--output', type=Path, default=Path('build/Seeky.apk'))
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
if not a.unsigned and (not a.keystore.is_file() or not a.key_alias or not os.environ.get(a.password_env)):
 p.error("Supply an existing external keystore, key alias and password environment variable")
build=root/'build'
for sub in ('generated','classes','dex'):
 target=build/sub
 if target.exists(): shutil.rmtree(target)
 target.mkdir(parents=True)
# Avoid incrementally reusing a stale or interrupted APK archive.
for artifact in ('resources.apk','unsigned.apk','aligned.apk','signed.apk'):
 target=build/artifact
 if target.is_file(): target.unlink()
def run(args):
 print('Running',Path(str(args[0])).name,flush=True)
 subprocess.run([str(x) for x in args],check=True,cwd=root)
tools=a.build_tools.resolve(); jar=a.android_jar.resolve()
manifest=app_manifest.write(build/'generated-manifest/AndroidManifest.xml')
run([tools/'aapt','package','-f','-m','--custom-package','mz.bam.seeky.android','-J',build/'generated','-M',manifest,'-S',root/'app/src/main/res','-A',root/'app/src/main/assets','-I',jar,'-F',build/'resources.apk'])
with zipfile.ZipFile(build/'resources.apk') as resources:
 if 'AndroidManifest.xml' not in resources.namelist() or resources.testzip() is not None:
  raise RuntimeError('Resource APK is incomplete; final APK was not replaced')
sources=sorted((root/'app/src/main/java').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
libraries=sorted((root/'app/libs').glob('*.jar'))
classpath=os.pathsep.join(str(x) for x in libraries)
boot=str(jar)+os.pathsep+str(tools/'core-lambda-stubs.jar')
if a.ecj:
 run(['java','-jar',a.ecj.resolve(),'-8','-proc:none','-warn:none','-bootclasspath',boot,'-classpath',classpath,'-d',build/'classes',*sources])
else:
 run(['javac','-source','8','-target','8','-bootclasspath',boot,'-classpath',classpath,'-d',build/'classes',*sources])
run([tools/'d8','--min-api','26','--lib',jar,'--output',build/'dex',*sorted((build/'classes').rglob('*.class')),*libraries])
shutil.copy2(build/'resources.apk',build/'unsigned.apk')
with zipfile.ZipFile(build/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
 for dex in sorted((build/'dex').glob('*.dex')): z.write(dex,dex.name)
 for native in sorted((root/'app/src/main/jniLibs').rglob('*.so')):z.write(native,'lib/'+str(native.relative_to(root/'app/src/main/jniLibs')))
run([tools/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk'])
output=a.output.resolve();output.parent.mkdir(parents=True,exist_ok=True)
artifact=build/'aligned.apk'
if not a.unsigned:
 run([tools/'apksigner','sign','--ks',a.keystore.resolve(),'--ks-key-alias',a.key_alias,'--ks-pass','env:'+a.password_env,'--key-pass','env:'+a.password_env,'--out',build/'signed.apk',build/'aligned.apk'])
 run([tools/'apksigner','verify','--verbose',build/'signed.apk'])
 artifact=build/'signed.apk'
with zipfile.ZipFile(artifact) as checked:
 if checked.testzip() is not None: raise RuntimeError('APK ZIP verification failed')
# Publish only a complete, verified APK, including across different filesystems.
staged_output=output.with_name(output.name+'.tmp')
shutil.copy2(artifact,staged_output)
os.replace(staged_output,output)
if a.unsigned: print('UNSIGNED: APK requires the original signing key before installation or upgrade.')
print(output)
