"""Actual notebook code over host SQLite JDBC. No Android UI/lifecycle claim.
Test-only jars: org.json 20250517; sqlite-jdbc 3.49.1.0; slf4j-api/nop 2.0.17.
"""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--ecj',required=True,type=Path);p.add_argument('--test-jars',required=True,type=Path);a=p.parse_args();r=Path(__file__).parents[1];src=r/'app/src/main/java/mz/bam/seeky/android';out=r/'build/notebook-host';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(str(a.test_jars.resolve()/n) for n in ['json-test.jar','sqlite-test.jar','slf4j-test.jar','slf4j-nop-test.jar'])
files=[src/(n+'.java') for n in ['StudyStore','StudySchedule','ReaderStore','StudyText','NotebookBackup','NotebookNotes','ContentText','JsonBudget']]+list((r/'tests/notebook_host').rglob('*.java'))
subprocess.run(['java','-jar',str(a.ecj.resolve()),'-8','-proc:none','-warn:none','-classpath',cp,'-d',str(out)]+list(map(str,files)),check=True)
subprocess.run(['java','-Xmx128m','-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.NotebookCheck'],check=True)

subprocess.run(['java','-Xmx128m','-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.PersistenceSafetyCheck'],check=True)
