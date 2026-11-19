# Arquitectura do Seeky

O código vive num único pacote Java (`mz.bam.seeky.android`), com classes e membros
*package-private* partilhados entre áreas. Dividi-lo em subpacotes obrigaria a tornar
públicos centenas de membros e arriscaria regressões sem ganho funcional; em vez disso,
os prefixos dos nomes identificam a área. Este documento é o mapa.

Regras gerais do código:
- Lógica pura (sem `android.*`) em classes pequenas, testadas na JVM por `tools/run_tests.py`
  (ex.: `SpeechNormalizer`, `SpeechChunks`, `FileOrder`, `CloudProtocol`, `ThemeColors`).
- Trabalho pesado fora da thread principal (`ExecutorService`, `TaskService`), com
  cancelamento e limites explícitos de tamanho (`DocumentBudget`, `ImageBudget`, `JsonBudget`).
- Persistência em SQLite (`Catalog`, `StudyStore`, `ReaderStore`, `TaskStore`, `PlusStore`)
  e `SharedPreferences` por área.

## Mapa por área

| Área | Classes principais |
| --- | --- |
| Entrada / navegação | `SeekyApplication`, `MainActivity` (separadores Início, Pesquisa, Explorar, Livros, Estudos, Mais), `WorkspaceActivity`, `UiPanel`, `UiDialog`, `PlusUi` |
| Entrada de outras apps | `IncomingActivity`, **`IncomingUris`** (fronteira de confiança), `ApkInstallActivity` |
| Catálogo de ficheiros | `Catalog`, `DiskWalker`, `FileTracker`, `FolderSnapshot`, `ChangeQueue`, `StorageAccess`, `SafeTrash`, `FileTransfer`, `MediaFormats`, `Thumbnails` |
| Arquivos | `ArchiveFiles` (extracção com protecção Zip Slip/ligações), `ArchiveUi`, `BookArchive` |
| Flash (pesquisa rápida) | `QuickSearchService` (serviço em primeiro plano + janela flutuante), `FlashBubbleView` (botão), `FlashPanelView` (painel), `FlashQuery`, `FlashSettings`, `FlashActivity` (autorizações), `FlashSearchActivity` (alternativa sem sobreposição), `FlashTileService` (Definições Rápidas), `OverlayRecovery` |
| Aparência | `AppPalette` (temas, incluindo *Sistema*), `ThemeColors`, `ThemeSettings`, `BrandSurface`, `Icons`, `IconSizing`, `Motion`, `CategoryIcon` |
| Leitor | `ReaderActivity`, `ReaderChrome`, `ReaderPageView`, `ReaderScrollView`, `OriginalDocument`/`OriginalWebView` (EPUB/DOCX com CSP), `EpubLayout`, `PdfTextSource`, `TextPaging`, `ReaderStore` |
| Voz (TTS) | `ReadAloudService`, `ReaderSpeech`, `SpeechVoices`, `SpeechPolicy`, `SpeechParameters`, `SpeechText` → `BibleReferences` → **`SpeechNormalizer`** → `PronunciationRules` (via `VoiceText`), `SpeechChunks`, `SpeechCursor`, `WebSpeechHighlight` |
| Exportação de áudio | `NarrationExportService`, `SpeechAudioExport`, `WaveJoiner`, `BackgroundMixer`, `WavInfo`, `WavCues` |
| Vozes naturais (opcional) | `voicebridge.NaturalSpeech`, `NaturalVoicesActivity`, `VoiceEngines`, `GoogleVoiceSetup` |
| Livros e Bíblia | `BookLibrary`, `BookQuery`, `BookCovers`, `BookCategories`, `Bible*` (MyBible, USX, USFM, EPUB, texto), `ImportWork` |
| Estudos | `StudyHub`, `StudyStore`, `StudyCards`, `StudySchedule`, `StudyTimerService`, `FocusClock`, `ReadingPlan`, `StudyAssistantActivity` + `SeekyAi*` (chave pessoal cifrada) |
| Cópias de segurança | `WorkspaceBackup`, `BackupTransfers`, `BackupExtras`, `StudyBackup`, `NotebookBackup`, `AutoBackup` + `AutoBackupJob` (JobScheduler, pasta SAF) |
| Bloqueio | `AppLock` (ciclo de vida das actividades, tempo limite), `LockActivity` (BiometricPrompt / credencial do ecrã de bloqueio) |
| Novidades | `WhatsNew` |
| OCR | `OcrEngine` (Tesseract), `OcrWork`, `OcrCache` |
| Tarefas duráveis | `TaskService`, `TaskStore`, `TaskWork`, `WorkLeases`, `ResumeReceiver` |
| Idioma | `AppLanguage`, `Translations` (dicionário pt → en) |

## Fluxos principais

### Texto para a voz
```
texto da página
  └─ SpeechChunks.naturalEnd      corta em frases/parágrafos (≤ limite do motor)
      └─ SpeechText.prepare        junta quebras de linha de paginação, mantém mapa de posições
          └─ BibleReferences.expand   "Jo 3:16" → "João capítulo 3 versículo 16"
              └─ SpeechNormalizer     abreviaturas, romanos, URLs, símbolos… (protege o dicionário pessoal)
                  └─ PronunciationRules   dicionário do utilizador
                      └─ TextToSpeech     voz escolhida por SpeechVoices (qualidade → região → latência)
```
Cada carácter falado aponta para a posição original, o que mantém o destaque sincronizado.

### Botão Flash
`FlashActivity` pede as autorizações (ficheiros e sobreposição) e inicia `QuickSearchService`.
O serviço desenha `FlashBubbleView` numa janela `TYPE_APPLICATION_OVERLAY`; toque abre
`FlashPanelView`, arrasto/lançamento encaixa na margem, toque longo abre os ajustes na
`MainActivity`. `FlashTileService` mostra o estado e liga/desliga o botão.

### Ficheiros recebidos
`IncomingActivity` → `IncomingUris.acceptable` (recusa área privada e providers próprios)
→ abrir, importar (`ImportWork`) ou extrair (Intent assinado com `IncomingUris.sign`,
verificado na `MainActivity`).
