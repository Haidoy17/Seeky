# Revisão Seeky 5.11.0

Base: 5.10.5. Assinatura visual: Haidoy Carlos Mautempo.

LastReading guarda a identidade do último documento apresentado, o nome/título, URI, referência de material e modo da janela. A actividade captura a posição ao pausar e destruir, respeitando os registos por URI do leitor. BookLibrary monta o FAB circular de 60 dp fora da área rolável, com margem reservada no conteúdo. O intent de retoma não transporta study_segment, que antes poderia anular o capítulo guardado. Há recuperação inicial a partir dos materiais com posição e aviso quando o destino deixou de existir. Eliminar um material limpa a sua referência de última leitura; a consolidação de duplicados conserva os IDs.

ReaderPageView guarda zoom relativo e centro da imagem por página/modo de ajuste. O PDF restaura esse estado depois de ajustar o bitmap. As imagens gerais não foram alteradas. No EPUB/DOCX original mantém-se a posição do WebView; no texto fluido e PDF de texto mantém-se o deslocamento correspondente.

SpeechLayout é um planeador local de títulos com offsets UTF-16. BookArchive conserva até 128 cabeçalhos por capítulo (orçamento de 16 KiB) sem alterar o texto de extracção, HTML ou ficheiros originais. SpeechDocument disponibiliza esses cabeçalhos ou entradas de índice PDF. Regras conservadoras tratam blocos curtos isolados e linhas de capítulo/maiúsculas; pontuação e abreviaturas continuam no fluxo de texto. A detecção não infere toda a formatação tipográfica nem OCR.

SpeechCursor separa os limites de título/conteúdo e marca a pausa final do título. ReadAloudService intercala playSilentUtterance(2000, QUEUE_ADD) antes do trecho seguinte, mantendo um único trecho antecipado. Títulos no fim da parte aguardam o silêncio antes de mudar de página/parar. Há alternativa por Handler se o motor recusar a fila de silêncio; eventos antigos são descartados depois de pausar/parar/saltar. O fundo mantém-se activo durante a pausa.

SpeechTransport fornece os mesmos controlos no leitor e na janela TTS. ReadAloudService aceita comandos com token para anterior, recuar/avançar e actualizar o intervalo da notificação. Os saltos usam SpeechSeek, um modelo de tempo por palavras, velocidade, pontuação e silêncio explícito. São estimativas, nunca apresentadas como pesquisa exacta de áudio. O worker pode atravessar até 50 partes por salto para limitar trabalho em documentos vazios; limites do documento são respeitados. Uma pausa durante o salto cancela o resultado pendente, e eventos antigos não retomam a reprodução.

NarrationExportService aplica a estrutura às novas gravações, insere silêncio PCM no formato da voz, mistura o fundo no intervalo e confirma num só checkpoint texto/PCM/frames. O estado speech_layout_version preserva a retoma de trabalhos anteriores sem mudar o layout já produzido. Não se renomeiam nem regeneram ficheiros antigos.

Testes: 103 Android simulados; controlos PT/EN, EPUB em capítulo e deslocamento, PDF em página/zoom, saída e reabertura do leitor, limpeza de referência ao eliminar, selecção de 30 s e intents, saltos pausados entre capítulos, silêncio real/fallback e final de título, posição depois de pausa, ficheiros WAV e fundo durante a pausa, retomada sem duplicar silêncio e compatibilidade anterior. O suite local também verifica reconstrução Unicode e PCM unsigned/16-bit.

Limites: sem ensaio no Itel, sem qualidade auditiva Google validada, sem PDFium real no teste de interface e sem chamada real à IA. O tempo de salto TTS é aproximado; a identificação de títulos depende da estrutura/texto disponíveis. Não houve alterações em backend, serviços pagos ou dependências de execução. Mantêm-se certificado, pacote independente mz.bam.seeky.v51 e versões dos esquemas; a actualização não exige desinstalar.

Verificação final: **103 testes Android/API 33 simulados** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
