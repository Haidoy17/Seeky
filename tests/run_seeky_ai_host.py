"""Bounded AI protocol/transport/storage tests. No real API, credential, billing or Internet request."""
from pathlib import Path
import argparse,os,subprocess
p=argparse.ArgumentParser();p.add_argument('--ecj',type=Path,required=True);p.add_argument('--android-jar',type=Path,required=True);p.add_argument('--json-jar',type=Path,required=True);a=p.parse_args();r=Path(__file__).parents[1];src=r/'app/src/main/java/mz/bam/seeky/android';out=r/'build/ai-host';out.mkdir(parents=True,exist_ok=True);cp=os.pathsep.join([str(a.json_jar),str(a.android_jar)])
files=[src/(n+'.java') for n in ['StudyEvidence','SeekyAiCore','SeekyAiClient','SeekyAiStore','AudioTitles','JsonBudget']]+[r/'tests/ai_host/SeekyAiTest.java'];subprocess.run(['java','-jar',str(a.ecj),'-8','-proc:none','-warn:none','-classpath',cp,'-d',str(out)]+list(map(str,files)),check=True);subprocess.run(['java','-Xmx128m','-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.SeekyAiTest'],check=True)
