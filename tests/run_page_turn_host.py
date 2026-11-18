from pathlib import Path
import argparse,subprocess
p=argparse.ArgumentParser();p.add_argument('--ecj',type=Path,required=True);a=p.parse_args();r=Path(__file__).parents[1];out=r/'build/page-turn-host';out.mkdir(parents=True,exist_ok=True);src=r/'app/src/main/java/mz/bam/seeky/android'
subprocess.run(['java','-jar',str(a.ecj),'-8','-proc:none','-warn:none','-d',str(out)]+[str(src/(n+'.java')) for n in ['PageSwipe','TextPaging','VoiceIdentity']]+[str(r/'tests/PageTurnPolicyCheck.java')],check=True)
subprocess.run(['java','-Xmx128m','-cp',str(out),'mz.bam.seeky.android.PageTurnPolicyCheck'],check=True)
