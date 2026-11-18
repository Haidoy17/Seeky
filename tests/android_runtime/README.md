# Reader Activity launch regression

Robolectric 4.14.1, Android 13/API 33, native Android graphics on the JVM. Runs the production ReaderActivity/ReaderChrome/PageView and verifies initial launch, mounted page views, drawing, navigation and teardown in full screen, preview and continuous mode.

Robolectric 4.14 does not implement Android PDFium. RendererDouble/PageDouble supply bounded deterministic pages to isolate lifecycle/layout from that engine. These tests do **not** certify native PDFium decoding, a physical Itel, voices or device memory limits. Real PDF text extraction and document outlines remain covered separately by tests/pdf_host_check.py with PDFBox.

The same tests fail on 5.4.0 because ReaderActivity calls PhoneWindow.getInsetsController while its decor is null. They pass on 5.4.1 after moving system-bar setup after setContentView.

Use Maven and pom.xml to resolve the test-only dependencies to a classpath file, then compile the application normally. For offline execution, provide these Robolectric SDK artifacts (not shipped here):

- android-all-instrumented-13-robolectric-9030017-i7.jar
- android-all-instrumented-15-robolectric-12650502-i7.jar (framework resources used by Robolectric)

Example, with installed SDK and test dependencies:

```sh
mvn -f tests/android_runtime/pom.xml dependency:build-classpath -Dmdep.outputFile=/absolute/path/classpath.txt
python3 tests/android_runtime/run.py --ecj /path/ecj.jar --android-jar /path/android.jar --classpath-file /absolute/path/classpath.txt --sdk-dir /path/robolectric-sdks
```

The runner extracts classes.jar from the AndroidX AAR dependencies into build/, uses the normal precompiled application classes and writes layout captures labelled `viewer-layout-double`. No test dependency is included in the APK.

## Voice tools added in 5.5

VoiceToolsTest exercises preview without committing, explicit application, installer targeting, missing offline data, exact voice selection, cancellation, WAV export through the document picker and source byte preservation. SpeechServiceTest checks mapped source offsets and switching voice while paused. TTS voices and synthesis output are deterministic test fixtures; these tests do not assess the audible quality of Google voices or certify their downloads on a phone.

## Library, gestures and free Android engines in 5.7

BooksToolsTest covers shelf/grid/list, percentages, shared progress and English navigation. ChapterExportTest uses a WAV-producing TTS fixture and DocumentsProvider to test whole book, range, failure and cancellation. PageTurnTest sends touch sequences through the production ReaderScrollView and explicitly advances ScrollView.computeScroll for simulated frames. It verifies viewport turns, chapter boundaries in both directions, selection, vertical scrolling and persistence. The PDF tests also exercise the existing horizontal turn in single-page mode.

VoiceToolsTest covers regions, gender metadata/manual identification, favorites, sample comparison, migration away from the removed engine, its exclusion from discovery, and the Google online shortcut without an offline fallback. ChapterExportTest verifies book/chapter filenames, RIFF tags and unchanged PCM through the production SAF exporter. DocumentTitleTest builds a real PDF with nested outlines and checks PDF title, author and most-specific section naming. Voice synthesis remains a deterministic fixture. Tests do not assess audible Google voice quality on a phone. Default runner: 61 tests.


## Document fidelity and durable narration in 5.9

ChapterExportTest now covers failed chapters and intra-chapter resume, retained private source snapshots, deletion of the original source, already-synthesized publication recovery, a real plain-text document without chapters, single-file output, and PCM WAV background decode/mixing through the production service. The provider and TTS are deterministic fixtures; compressed MediaCodec decoding and real Google synthesis require an Android device. PageTurnTest also checks the original EPUB presentation, included CSS, denied remote resources and explicit text-flow mode.

StudioAudioTest, part of run_tests.py, checks checkpoint truncation, continuous mixing, zero background gain, format preservation, WAV decoding and a sparse WAV beyond one hour/128 MB. This is a size/streaming test, not a timed one-hour synthesis.

For actual DOCX rendering, install Python Playwright and Chromium headless shell, then run `python3 tests/document_layout_check.py --browsers /path/playwright-browsers --evidence /path/evidence`. All browser responses are intercepted locally. The check asserts font, styles, colour, table, image, header, footer, mobile fit and blocked script-bearing altChunk. It is not a pixel comparison with Word or a physical Android WebView test.


## Continuous background and network voice application in 5.9.1

VoiceToolsTest checks applying the Google online shortcut at neutral rate, explicit voice verification, persistent ambient playback after closing the actual settings sheet and destroying ReaderActivity, independent gain, and shared audio focus. SpeechServiceTest verifies QUEUE_ADD look-ahead before completion and exact source position after cancelling queued speech.

BackgroundPcmTest checks zero initial decoding, bounded prefix preparation, byte comparison with a fully decoded WAV through real loop boundaries, cancellation and complete-cache reuse. Its compressed-codec test exercises the production pull loop with deterministic Robolectric extractor/codec data, including zero-wait input polling. This does not measure physical MP3/AAC codec speed or audible Google quality.


## EPUB layout controls in 5.9.2

PageTurnTest uses a deterministic WebView scale (the Robolectric provider returns an incompatible boxed integer for getScale). It drives the actual sheet listeners for zoom/margin/spacing/alignment, verifies original mode stays active, confirms persisted options after activity recreation and reset, opens settings through Organize, and compares every original EPUB byte. This is not a WebView rendering certification.

EpubLayoutTest checks production HTML/CSS adaptation. `tests/epub_layout_check.py` renders its generated output with a separately installed Chromium and Playwright. The final environment blocked Chromium startup on a sandbox shutdown syscall; the delivery retains the initial pre-charset-correction geometry measurement explicitly as a reference. See REVISAO_5_9_2.md.


## Internal study assistant in 5.10

StudyAssistantTest exercises native entry without automatic provider requests, explicit key configuration, actual context extraction, completed question/answer persistence, saving into the real study database, draft/activity recreation after original-source removal, cancellation/retry without duplicate messages, encrypted/corrupt credentials and English phone layouts. BooksToolsTest/VoiceToolsTest verify internal launch from Studies and the reader. These cases use a deterministic provider transport. API-key cryptography is exercised using JCE AES-GCM with a test-only key; real Android Keystore generation, real model quality, account quotas/credits and Internet availability are not certified. Screenshots use fixture responses and are labelled accordingly in REVISAO_5_10.md.
