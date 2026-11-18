"""Real PDFBox parser on JVM with small Android value/log/asset shims; NOT an Android runtime test."""
from pathlib import Path
import argparse,os,subprocess
p=argparse.ArgumentParser();p.add_argument('--ecj');a=p.parse_args();r=Path(__file__).parents[1];out=r/'build/pdf-host';out.mkdir(parents=True,exist_ok=True)
objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R 4 0 R 5 0 R] /Count 3 >>']
for content in [7,8,9]:objects.append(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << /Font << /F1 6 0 R >> >> /Contents {content} 0 R >>'.encode())
objects.append(b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')
for text in ['Seeky searchable text','Deep search second page','']:
 stream=f'BT /F1 18 Tf 40 700 Td ({text}) Tj ET'.encode();objects.append(f'<< /Length {len(stream)} >>\nstream\n'.encode()+stream+b'\nendstream')
body=b'%PDF-1.4\n';offsets=[0]
for n,obj in enumerate(objects,1):offsets.append(len(body));body+=f'{n} 0 obj\n'.encode()+obj+b'\nendobj\n'
xref=len(body);body+=f'xref\n0 {len(objects)+1}\n0000000000 65535 f \n'.encode()+b''.join(f'{o:010} 00000 n \n'.encode() for o in offsets[1:])+f'trailer << /Size {len(objects)+1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n'.encode();fixture=out/'search.pdf';fixture.write_bytes(body)
jars=sorted((r/'app/libs').glob('*.jar'));cp=os.pathsep.join(map(str,jars));sources=list((r/'tests/pdf_host').rglob('*.java'))+[r/'app/src/main/java/mz/bam/seeky/android/PdfIndexText.java',r/'app/src/main/java/mz/bam/seeky/android/PdfTextSource.java',r/'app/src/main/java/mz/bam/seeky/android/PdfGlyphs.java']
compiler=['java','-jar',a.ecj,'-8','-proc:none','-warn:none'] if a.ecj else ['javac','-source','8','-target','8']
subprocess.run(compiler+['-classpath',cp,'-d',str(out)]+list(map(str,sources)),check=True)
subprocess.run(['java','-Dseeky.assets='+str((r/'app/src/main/assets').resolve()),'-cp',str(out)+os.pathsep+cp,'PdfExtractionCheck',str(fixture)],check=True)

subprocess.run(['java','-Dseeky.assets='+str((r/'app/src/main/assets').resolve()),'-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.PdfIndexCheck',str(fixture)],check=True)

subprocess.run(['java','-Xmx96m','-Dseeky.assets='+str((r/'app/src/main/assets').resolve()),'-cp',str(out)+os.pathsep+cp,'mz.bam.seeky.android.PdfReadingCheck',str(fixture)],check=True)
