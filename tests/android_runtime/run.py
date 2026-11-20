"""Offline Robolectric lifecycle regression. All SDK/test dependencies supplied explicitly.
PDFium is a deterministic test double; Android lifecycle, views and drawing run on Robolectric.
No dependencies from this directory are packaged in the application.
"""
from pathlib import Path
import argparse,subprocess,zipfile,os
p=argparse.ArgumentParser()
p.add_argument('--ecj',required=True,type=Path);p.add_argument('--android-jar',required=True,type=Path)
p.add_argument('--classpath-file',required=True,type=Path);p.add_argument('--sdk-dir',required=True,type=Path)
p.add_argument('--test-classes',nargs='+',default=['mz.bam.seeky.android.PhotoEditor5250Test','mz.bam.seeky.android.Features5240Test','mz.bam.seeky.android.Features5230Test','mz.bam.seeky.android.BoldTitles5221Test','mz.bam.seeky.android.Cleanup5220Test','mz.bam.seeky.android.Updates5201Test','mz.bam.seeky.android.Modern5200Test','mz.bam.seeky.android.Viewers5190Test','mz.bam.seeky.android.FlashTuck5190Test','mz.bam.seeky.android.Security5190Test','mz.bam.seeky.android.Presentation5190Test','mz.bam.seeky.android.BibleImportUi5180Test','mz.bam.seeky.android.Bible5180Test','mz.bam.seeky.android.Archive5170Test','mz.bam.seeky.android.FlashTheme5170Test','mz.bam.seeky.android.Study5160Test','mz.bam.seeky.android.Categories5160Test','mz.bam.seeky.android.ReaderSharp5153Test','mz.bam.seeky.android.ReaderLayout5152Test','mz.bam.seeky.android.ReaderText5152Test','mz.bam.seeky.android.ReaderContrast5152Test','mz.bam.seeky.android.Layout5151Test','mz.bam.seeky.android.ReferenceDesign5150Test','mz.bam.seeky.android.FlashDesignTest','mz.bam.seeky.android.Bible5144Test','mz.bam.seeky.android.ReaderFixes5141Test','mz.bam.seeky.android.PdfLaunchTest','mz.bam.seeky.android.VoiceToolsTest','mz.bam.seeky.android.SpeechServiceTest','mz.bam.seeky.android.BooksToolsTest','mz.bam.seeky.android.ChapterExportTest','mz.bam.seeky.android.PageTurnTest','mz.bam.seeky.android.DocumentTitleTest','mz.bam.seeky.android.BackgroundPcmTest','mz.bam.seeky.android.StudyAssistantTest','mz.bam.seeky.android.StudyImportTest','mz.bam.seeky.android.BookDuplicatesTest','mz.bam.seeky.android.BookRemovalTest','mz.bam.seeky.android.SpeechPrefetchTest','mz.bam.seeky.android.SpeechPageFlowTest','mz.bam.seeky.android.SpeechHeadingTest','mz.bam.seeky.android.PlusToolsTest','mz.bam.seeky.android.SpeechExtrasTest','mz.bam.seeky.android.WorkspaceBackupTest','mz.bam.seeky.android.ReaderPlusTest','mz.bam.seeky.android.OcrToolsTest','mz.bam.seeky.android.DurableWorkTest','mz.bam.seeky.android.FolderJournalTest','mz.bam.seeky.android.Library513Test','mz.bam.seeky.android.Backup513Test','mz.bam.seeky.android.Summary513Test','mz.bam.seeky.android.Storage513Test','mz.bam.seeky.android.Presentation513Test','mz.bam.seeky.android.Ocr513Test','mz.bam.seeky.android.AndroidIntegration513Test','mz.bam.seeky.android.Bible513Test']);p.add_argument('--project',type=Path);p.add_argument('--evidence-dir',type=Path)
a=p.parse_args();base=Path(__file__).resolve().parent;project=(a.project or base.parent.parent).resolve()
work=project/'build/runtime-tests';work.mkdir(parents=True,exist_ok=True);evidence=(a.evidence_dir or work).resolve();evidence.mkdir(parents=True,exist_ok=True)
classpath=[]
for entry in a.classpath_file.read_text().strip().split(os.pathsep):
 archive=Path(entry)
 if archive.suffix=='.aar':
  target=work/(archive.stem+'-classes.jar')
  with zipfile.ZipFile(archive) as data:target.write_bytes(data.read('classes.jar'))
  classpath.append(str(target))
 else:classpath.append(entry)
classpath.extend([str(project/'build/classes')]+[str(jar) for jar in (project/'app/libs').glob('*.jar')])
cp=os.pathsep.join(classpath);classes=work/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['java','-jar',str(a.ecj.resolve()),'-8','-proc:none','-warn:none','-classpath',str(a.android_jar.resolve())+os.pathsep+cp,'-d',str(classes)]+list(map(str,(base/'src').rglob('*.java'))),check=True)
subprocess.run(['java','--add-opens=java.base/java.io=ALL-UNNAMED','--add-opens=java.base/java.lang=ALL-UNNAMED','-Xmx768m','-Drobolectric.offline=true','-Drobolectric.dependency.dir='+str(a.sdk_dir.resolve()),'-Dseeky.pdf.fixture='+str(base/'fixture.pdf'),'-Dseeky.main.resources='+str(project/'build/resources.apk'),'-Dseeky.screenshot='+str(evidence/'viewer-layout-double'),'-cp',str(classes)+os.pathsep+cp,'org.junit.runner.JUnitCore']+a.test_classes,check=True)
