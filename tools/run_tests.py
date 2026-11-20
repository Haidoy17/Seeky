#!/usr/bin/env python3
"""Offline regression runner. Requires JDK javac or --ecj path/to/ecj.jar; never downloads."""
from pathlib import Path
import argparse,subprocess,sys
p=argparse.ArgumentParser();p.add_argument('--ecj',type=Path);a=p.parse_args()
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/mz/bam/seeky/android';out=root/'build/test-classes';out.mkdir(parents=True,exist_ok=True)
names=['BibleStudyCore','BibleGame','BibleEpubText','StudyEvidence','StudyCards','WorkspaceEpoch','BibleHtml','WavCues','PronunciationRules','BiblePlainText','BibleEncoding','BibleText','BibleReferences','SpeechLayout','SpeechSeek','EpubLayout','BackgroundWave','CheckpointWave','BackgroundMixer','AudioTitles','WavInfo','SpeechText','SpeechNormalizer','WaveJoiner','DiagnosticTrace','SpeechCursor','SpeechChunks','SpeechPolicy','ReaderAppearance','ThemeColors','FileVisual','FocusClock','ImageBudget','FlashQuery','Translations','Rules','ContentText','DiskWalker','LatestWork','ChangeQueue','FolderSnapshot','GenerationWindow','FileTransfer','ExplorerOrder','FileRecord','FileOrder','MediaFormats','BookArchive','OverlayRecovery','SafeTrash','ReaderLayout','CsvFields','StudyText','StudySchedule','StudyFiles','CloudProtocol','CloudChunks','TextSearch','JsonBudget']
sources=[src/(n+'.java') for n in names]+sorted((root/'tests').glob('*Test.java'))
compiler=['java','-jar',str(a.ecj.resolve()),'-8','-proc:none','-warn:none'] if a.ecj else ['javac','-source','8','-target','8']
subprocess.run(compiler+['-d',str(out)]+[str(f) for f in sources],check=True)
for name in ['BibleStudy529Test','BibleReferences528Test','CategoryRulesTest','SpeechNormalizerTest','BibleEpubImportTest','BibleImportTest','PlusCoreTest','BibleReferencesTest','SpeechStructureTest','EpubLayoutTest','StudioAudioTest','AudioNamingTest','WaveJoinerTest','DiagnosticTraceTest','SpeechReadingTest','VisualPolicyTest','PerformanceCoreTest','CoreTest','DiskWalkerTest','LatestWorkTest','DiscoveryTest','GenerationWindowTest','FileTransferTest','ReaderCoreTest','OrderingTest','TrashTest','CsvTest','StudyTextTest','StudyScheduleTest','StudyFilesTest','CloudProtocolTest','CoreStabilityTest','TextSearchTest']:
 subprocess.run(['java','-cp',str(out),'mz.bam.seeky.android.'+name],check=True)
subprocess.run([sys.executable,str(root/'tests/catalog_test.py')],check=True)

subprocess.run([sys.executable,str(root/'tests/metadata_test.py')],check=True)

subprocess.run([sys.executable,str(root/'tests/collections_activity_test.py')],check=True)

subprocess.run([sys.executable,str(root/'tests/reader_store_test.py')],check=True)

subprocess.run([sys.executable,str(root/'tests/pdf_host_check.py')]+(['--ecj',str(a.ecj.resolve())] if a.ecj else []),check=True)
subprocess.run([sys.executable,str(root/'tests/study_store_test.py')],check=True)
subprocess.run([sys.executable,str(root/'tests/cloud_state_test.py')],check=True)
print('ALL CHECKS COMPLETED',flush=True)
