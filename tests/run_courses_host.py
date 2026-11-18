"""Actual course schema, migration, backup and snapshot methods over SQLite JDBC.
Android lifecycle, SAF, WAL pool and network are not simulated.
"""
import argparse,os,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--ecj',required=True,type=Path);p.add_argument('--test-jars',required=True,type=Path);a=p.parse_args()
r=Path(__file__).parents[1];src=r/'app/src/main/java/mz/bam/seeky/android';out=r/'build/courses-host';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(str(a.test_jars.resolve()/n) for n in ['json-test.jar','sqlite-test.jar','slf4j-test.jar','slf4j-nop-test.jar'])
files=[src/(n+'.java') for n in ['WorkspaceEpoch','BibleReferences','SpeechChunks','SpeechText','SpeechNormalizer','PronunciationRules','SpeechLayout','BookQuery','ReadingProgress','ChapterRange','StudyReading','FocusClock','FocusSessions','StudyStore','StudyState','StudySchedule','StudyBackup','StudySnapshot','StudyFiles','ReaderStore','NotebookBackup','StudyText','ContentText','JsonBudget','DiskWalker','Rules','SafeTrash','MediaFormats','FileOrder','FileRecord','ExplorerOrder']]+list((r/'tests/notebook_host/android').rglob('*.java'))+list((r/'tests/course_host').rglob('*.java'))
subprocess.run(['java','-jar',str(a.ecj.resolve()),'-8','-proc:none','-warn:none','-classpath',cp,'-d',str(out)]+list(map(str,files)),check=True)
subprocess.run(['java','-Xmx128m','-Dseeky.legacy_schema='+str(r/'tests/course_host/legacy_schema.json'),'-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.CourseCheck'],check=True)

subprocess.run(['java','-Xmx128m','-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.TimerCheck'],check=True)

subprocess.run(['java','-Xmx128m','-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.BooksCheck'],check=True)
