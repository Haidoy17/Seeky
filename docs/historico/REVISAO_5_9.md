# Revisão Seeky 5.9.0

Data: 29 de Outubro de 2026. Base: código da entrega 5.8. Assinatura visual: Haidoy Carlos Mautempo.

## Correcções entregues

- Espaçamento de 8 dp entre acções de Explorar, altura de 46 dp e separação da pesquisa. Remoção de comandos duplicados de navegação e ajustes de voz recolhidos em Opções de voz.
- EPUB em HTML/CSS original; DOCX com JSZip 3.10.1 e docx-preview 0.3.7 incluídos localmente. Zoom proporcional, estado de leitura, gestos e opção explícita de texto fluido. Documentos não são regravados.
- Exportação TTS com registo durável por trecho, cópia privada da origem, truncagem de caudas não confirmadas e retoma após falha/pausa. O estado também conserva a posição dentro do capítulo.
- Destino SAF recuperável: diário do ficheiro em escrita, limpeza da cópia incompleta e possibilidade de escolher outra pasta sem voltar a sintetizar o WAV concluído.
- Livros sem capítulos, selecção e intervalos usam o mesmo serviço. Saída por partes ou um WAV contínuo, sem limite artificial de uma hora; limite RIFF próximo de 4 GB.
- Fundo importado, pré-escuta, ganho independente, ciclo contínuo e opção de misturar na exportação. PCM é processado em blocos; o fundo é reamostrado para a frequência da voz, preservando a duração da fala.
- Voz online: até três tentativas com trechos menores, mantendo a voz escolhida e conservando os trechos concluídos. Edge e RHVoice continuam removidos. Não foi acrescentado serviço pago ou chave de API.
- A correcção de criação da janela do leitor no Android 13, a estrutura de cursos, os cadernos, a biblioteca e os avanços das entregas anteriores permanecem no código.

## Evidência

`evidence/` contém os registos e capturas. O arquivo de código inclui a mesma evidência, um patch relativo à versão 5.8 e as licenças dos componentes do visualizador.

| Verificação | Resultado |
| --- | --- |
| Compilação ECJ, D8, aapt e zipalign | Aprovada |
| Suite local existente, incluindo estudo/SQLite/PDF | Aprovada |
| Nomes e metadados WAV | 22 verificações aprovadas |
| Retoma PCM, WAV grande e mistura de fundo | 11 verificações aprovadas |
| Android 13/API 33 com Robolectric | 42 testes aprovados |
| DOCX em Chromium: fonte, cor, estilos, tabela, imagem, cabeçalho e rodapé | Aprovado, também a 360 px |
| Bloqueio de script altChunk no DOCX | Aprovado |
| EPUB: CSS original, acessos locais e recursos remotos | Aprovado no teste Android |
| Assinatura, pacote e versão do APK | Verificados no empacotamento |

Os testes de exportação Android usam um motor TTS determinístico e um DocumentsProvider de teste. Incluem falha entre capítulos e dentro de um capítulo, retoma após a remoção da origem, documento sem capítulos, selecção, WAV único, cancelamento e mistura real de um fundo PCM WAV. O teste de ficheiro com mais de uma hora é esparso; verifica tamanhos e escrita sem simular uma hora de síntese real. O teste de codecs comprimidos MediaCodec requer aparelho Android.

O teste de apresentação usa o renderer DOCX distribuído no APK e documentos de ensaio, com todas as respostas servidas localmente. Confirma estruturas e propriedades observáveis; não constitui comparação pixel a pixel com o Microsoft Word. O PDFium nativo não é implementado pelo Robolectric: os testes de janela/layout de PDF usam páginas determinísticas, e a extracção real de texto e índices usa PDFBox separadamente.

## Limites e compatibilidade

- Não houve teste físico no Itel A17 nem avaliação auditiva de vozes Google. Não se afirma que toda voz online consiga sintetizar para ficheiro.
- A retoma durável existe para trabalhos novos da 5.9; versões antigas não conservaram os dados necessários. WAVs antigos não são renomeados nem apagados.
- EPUB preserva os recursos incluídos no livro, com scripts e recursos de rede bloqueados. DOCX complexo pode divergir do Word. Ficheiros excessivos oferecem abertura externa ou texto fluido explícito. OCR não foi acrescentado.
- O fundo acompanha a leitura por voz; não é um leitor independente de música ambiente para leitura silenciosa. O volume de fundo do trabalho é capturado ao criar a geração.
- A importação de fundo tem limite de 256 MB, a cache PCM de 1 GB e as partes de saída de aproximadamente 128 MB. A fonte é copiada até 256 MB, respeitando ainda os limites específicos de cada leitor. O modo WAV único precisa de espaço temporário e da cópia de destino.
- A variante assinada usa `mz.bam.seeky.v51`, versão 5002400, mesmo certificado local das entregas independentes anteriores. O APK sem assinatura conserva `mz.bam.seeky.estudos` para compilação/assinatura com o certificado original. Chaves privadas não integram a entrega.
- Não houve alteração de backend, deploy ou configuração de contas externas.
