# Revisão Seeky 5.11.1

Base: 5.11.0. Assinatura visual: Haidoy Carlos Mautempo.

BibleReferences reconhece citações com nomes/abreviaturas dos livros, capítulo e versículo separados por dois pontos, incluindo intervalos, listas e continuidade entre capítulos. Mantém um catálogo local com os 73 livros católicos e nomes ingleses. Os prefixos de livros numerados aceitam algarismos, ordinais e numeração romana. Não há bibliotecas, serviços de rede ou credenciais novos.

SpeechText.forVoice aplica a expansão depois da normalização de disposição e conserva o mapa UTF-16 até ao documento. A referência completa é uma unidade no mapa: os eventos de pausa durante as palavras acrescentadas remetem ao início do nome do livro. SpeechCursor ajusta uma posição carregada que esteja dentro de uma referência, evitando retomar em números isolados pronunciados como horário. Os offsets posteriores permanecem exactos.

ReadAloudService usa a preparação na reprodução corrente e na fila antecipada. SynthesisWorker aplica a mesma regra imediatamente antes de synthesizeToFile. A preparação usada pelo hash/checkpoint de NarrationExportService permanece sem a expansão; assim a actualização não invalida trabalhos apenas por ter mudado a pronúncia. Não se alteram documentos, dados de estudo ou áudios já concluídos.

SpeechChunks e SpeechLayout procuram manter a referência inteira e contabilizam o aumento de texto falado antes do limite do motor. O tamanho final dos trechos com muitas citações é verificado por testes, preservando a reconstrução integral do texto de origem. Os limites dos títulos e a pausa já existente continuam activos.

Testes locais: 100 verificações, incluindo todos os 73 livros, acentos, variantes, abreviaturas, ordinais, intervalos de versículos/capítulos, inglês, horários e números inválidos, Unicode, offsets, pausa/retoma e limites de entrada em documentos com muitas citações. A suite Android acrescenta casos reais do serviço com vozes simuladas online/offline, texto enviado à síntese WAV e retoma de um checkpoint mantendo o título/PCM já concluídos.

Limites: reconhecimento textual, sem OCR ou inferência do livro quando o número aparece isolado. A lista de abreviaturas cobre as variantes cadastradas, não todas as convenções editoriais possíveis. O teste de TTS verifica as chamadas e os textos destinados ao motor; não certifica a qualidade auditiva do Google nem ensaio no Itel físico. Mantêm-se o pacote mz.bam.seeky.v51, certificado e esquemas de dados; instalação sobre a anterior.

Verificação final: **106 testes Android/API 33 simulados** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
