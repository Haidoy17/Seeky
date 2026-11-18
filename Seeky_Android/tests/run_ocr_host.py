"""Real host recognition with the app's pinned models; no Android native-load claim."""
from pathlib import Path
import argparse,hashlib,json,subprocess,unicodedata
p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path);a=p.parse_args()
base=Path(__file__).resolve().parent/'ocr'
expected={'por':('c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb',['seeky','leitura e estudos','isaias','compreender']), 'eng':('7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2',['seeky','reading and study','isaiah','understand'])}
results=[]
for lang,(digest,terms) in expected.items():
 model=base/'models'/(lang+'.traineddata')
 assert hashlib.sha256(model.read_bytes()).hexdigest()==digest
 text=subprocess.run(['tesseract',str(base/(lang+'.png')),'stdout','--tessdata-dir',str(base/'models'),'-l',lang,'--psm','6'],check=True,capture_output=True,text=True).stdout.strip()
 plain=''.join(c for c in unicodedata.normalize('NFD',text.lower()) if unicodedata.category(c)!='Mn')
 for term in terms:assert term in plain,(lang,term,text)
 results.append({'language':lang,'text':text,'real_ocr':True,'android_native_tested':False})
if a.evidence:a.evidence.parent.mkdir(parents=True,exist_ok=True);a.evidence.write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print('PASS real host OCR in Portuguese and English with pinned models')
