"""Fault injection against actual CloudClient. Host contracts are NOT Android or OAuth tests.
Requires ECJ/JDK and pinned org.json:json:20250517 for tests only (not shipped in APK).
"""
import argparse,subprocess,os
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--ecj',type=Path);p.add_argument('--json-jar',required=True,type=Path);a=p.parse_args()
r=Path(__file__).parents[1];src=r/'app/src/main/java/mz/bam/seeky/android';out=r/'build/cloud-host';out.mkdir(parents=True,exist_ok=True)
files=[src/(n+'.java') for n in ['CloudClient','CloudProtocol','CloudChunks','StudyFiles','CloudJobs']]+list((r/'tests/cloud_host').rglob('*.java'))
compiler=['java','-jar',str(a.ecj.resolve()),'-8','-proc:none','-warn:none'] if a.ecj else ['javac','-source','8','-target','8']
subprocess.run(compiler+['-classpath',str(a.json_jar.resolve()),'-d',str(out)]+list(map(str,files)),check=True)
subprocess.run(['java','-Xmx128m','-cp',str(out)+os.pathsep+str(a.json_jar.resolve()),'mz.bam.seeky.android.CloudResumeCheck'],check=True)
