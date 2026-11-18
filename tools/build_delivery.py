#!/usr/bin/env python3
"""Repackage compiled DEX/native libraries under the delivery application ID, without signing.
No downloads or credentials. The original certificate is still required for an in-place update.
"""
from pathlib import Path
import argparse, os, re, shutil, subprocess, sys, tempfile, zipfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import app_manifest

p=argparse.ArgumentParser()
p.add_argument('--compiled-apk',required=True,type=Path)
p.add_argument('--android-jar',required=True,type=Path)
p.add_argument('--build-tools',required=True,type=Path)
p.add_argument('--application-id',default='mz.bam.seeky.v51')
p.add_argument('--label',default='Seeky')
p.add_argument('--output',required=True,type=Path)
a=p.parse_args()
if not re.fullmatch(r'[A-Za-z]\w*(?:\.[A-Za-z]\w*)+',a.application_id):p.error('Invalid application ID')
root=Path(__file__).resolve().parents[1];bt=a.build_tools.resolve()
def run(args):subprocess.run(list(map(str,args)),check=True)
with tempfile.TemporaryDirectory(prefix='seeky-delivery-') as temp:
    work=Path(temp)
    import xml.etree.ElementTree as ET
    android='http://schemas.android.com/apk/res/android';ET.register_namespace('android',android)
    manifest=ET.fromstring(app_manifest.render(a.application_id))
    manifest.find('application').set('{'+android+'}label',a.label)
    for activity in manifest.findall('./application/activity'):
        if activity.get('{'+android+'}name','').endswith('.FlashActivity'):activity.set('{'+android+'}label','Seeky Flash')
    ET.ElementTree(manifest).write(work/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    run([bt/'aapt','package','-f','--custom-package','mz.bam.seeky.android','-M',work/'AndroidManifest.xml','-S',root/'app/src/main/res','-A',root/'app/src/main/assets','-I',a.android_jar.resolve(),'-F',work/'resources.apk'])
    shutil.copy2(work/'resources.apk',work/'unsigned.apk')
    with zipfile.ZipFile(a.compiled_apk) as compiled,zipfile.ZipFile(work/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as output:
        for name in compiled.namelist():
            if name.endswith('.dex') or name.startswith('lib/') and name.endswith('.so'):output.writestr(name,compiled.read(name))
    a.output.parent.mkdir(parents=True,exist_ok=True)
    run([bt/'zipalign','-f','-P','16','4',work/'unsigned.apk',a.output.resolve()])
print('Unsigned delivery APK:',a.output)
