# Seeky 5.12.0 — relatório da entrega

O pedido autorizado foi implementar as melhorias de biblioteca, leitura, voz e estudos, conservando o áudio em WAV. Esta versão usa o pacote de actualização `mz.bam.seeky.v51`, código `5002700`, e mantém a assinatura visual Haidoy Carlos Mautempo.

## Alterações

| Área | Resultado |
| --- | --- |
| Memória | Modo leve automático para aparelhos com pouca memória, opções manuais, caches limitados e conservação da imagem do PDF que continua visível. |
| Biblioteca | Colecções, assunto, série e autor; organização partilhada por cópias com o mesmo hash; filtros e operações existentes preservados. |
| Pronúncia | Dicionário pessoal aplicado à reprodução e à síntese; correspondência por palavra, sem reescrever o original; pronúncia fotografada na preparação de cada trabalho de narração. |
| Narração | Prévia do trecho real até 30 s; título e pausas de 2 s; índice RIFF cue/adtl nos WAVs; acesso às narrações do livro; índice recuperável do ficheiro. |
| Sono | Paragem por minutos ou fronteira do capítulo, incluindo o fundo; integração com serviços de voz e áudio. |
| Notas de voz | AudioRecord PCM/WAV mono 16 kHz, até 10 min por nota, ligado à origem e posição; ouvir, renomear, abrir origem e eliminar. |
| Acessibilidade | Régua móvel, destaque de parágrafo e espaço entre letras no texto fluido. |
| OCR | Tesseract local para uma página PDF ou imagem, modelos opcionais português/inglês, resultado ligado ao hash da origem e reutilizado no leitor/TTS/IA. |
| Bíblia | Importação TXT/CSV/TSV/USFM; ligações de referências no texto e EPUB; consulta das traduções importadas; lista de referências nos restantes visualizadores. |
| Estudos/IA | Trechos identificados pelo aplicativo, ligações de fonte e abertura da origem; cartões revistos e seleccionados antes de guardar; PDF de revisão por livro, módulo ou conjunto de estudos. |
| Cópias | ZIP completo da biblioteca privada, três bases SQLite, preferências públicas e ficheiros; SHA-256, validação antes da substituição, recuperação local anterior e registo durável das preferências pendentes. |
| Navegação | Ferramentas opcionais em menus, selecção múltipla funcional, cancelamento bloqueado na fase de aplicação do restauro, traduções inglesas dos controlos novos e créditos OCR. |

As narrações e as notas de voz continuam em WAV. O índice acrescenta metadados; não recomprime as amostras PCM. Os saltos TTS continuam aproximados e os saltos no WAV usam a posição do ficheiro. Foram conservadas as funcionalidades de importação em lote, duplicados verificados, eliminação, botão de última leitura e referências bíblicas faladas da base 5.11.1.

## Validação

- Suite Android/API 33 em Robolectric: **138 testes**. Inclui os oito casos de cópia/restauro, mic/PCM, prévia, limites de memória, relógio de sono, selecção múltipla, biblioteca, importação, retoma de narrações, índices WAV, dicionário imutável por trabalho, fontes e fluxos da IA existentes.
- Regressão local completa: passou, incluindo os 35 casos novos de ferramentas, 100 referências bíblicas e mapeamento, 16 títulos/navegação, 17 normalização da fala, limites de texto e ficheiros, WAV/fundo, JSON e leitura PDF.
- SQLite JDBC: passaram 76 casos de cursos/migrações/cópias, 25 de persistência do temporizador e 59 de filtros, progresso e intervalos de capítulos da biblioteca.
- IA: passaram 34 casos locais de contexto, protocolo, transporte e persistência. As respostas são simuladas; não houve chamada à API real.
- OCR: reconhecimento real de duas páginas de teste em português e inglês com Tesseract no computador; modelos com o mesmo SHA-256 usado pela aplicação. Quatro testes Android usam uma ponte simulada do motor para verificar integração, cancelamento, libertação de recursos e invalidação do cache por hash/página.
- Conferência visual: ecrã Ferramentas a 320 × 640 e desenho do caderno de revisão. Robolectric não implementa o codificador nativo PdfDocument desta versão; a ponte exclusiva de testes desenha o conteúdo real do escritor num bitmap Skia e embala esse desenho num PDF de teste. O teste verifica layout, texto escolhido, assinatura e paginação, sem comprovar o codificador nativo Android.
- Pacote: assinatura criptográfica, certificado de actualização, versão crescente, alinhamento ZIP, alinhamento ELF ARM64 a 16 KB e ARM32 a 4 KB, integridade, igualdade do DEX e bibliotecas entre a compilação e o APK assinado, e ausência dos fornecedores removidos e credenciais de teste no DEX.

Os registos, capturas e manifesto das dependências acompanham o código-fonte. As ferramentas e substitutos de teste não são incluídos no APK.

## Limites práticos

Não foi disponibilizado um Itel físico nem outra sessão Android real. Não foi ouvida uma voz Google real, exercitado o microfone físico, o cancelamento nativo do OCR ARM ou a gravação PDF nativa Android. Estes pontos continuam sujeitos ao comportamento do aparelho; os testes simulados não os substituem.

O OCR ARM acrescenta bibliotecas ao pacote, mas não activa um serviço permanente. Os modelos só são descarregados quando pedidos. O reconhecimento tem limites de resolução e processa uma página de cada vez, podendo exigir revisão do texto resultante. Bíblias/traduções não estão incluídas.

A cópia completa inclui ficheiros privados importados, não todos os ficheiros do armazenamento. Exclui chaves e palavras-passe, conversas do assistente, trabalhos de narração pendentes e WAVs externos. As credenciais já presentes ficam no aparelho. As recuperações locais retêm os ficheiros anteriores e consomem espaço; mantenha a janela aberta durante operações grandes. Ao recuperar uma biblioteca de outra cópia, os trabalhos de narração da biblioteca substituída ficam isolados para impedir associação ao livro errado.

As referências `[Tn]` apontam para trechos fornecidos, não certificam uma conclusão da IA. O serviço Gemini depende da configuração e quota da conta do utilizador. A voz depende do motor TTS e dos dados de voz instalados.

A exportação PDF limita cada caderno a 150 páginas; cada nota de voz a dez minutos; o dicionário a 100 entradas. WAV continua sujeito ao limite RIFF de aproximadamente 4 GB por ficheiro, com divisão quando necessário. Não foi feita conversão para M4A.

Certificado SHA-256 da variante de actualização: `a96f8caec94509f4aeb9abe560f23a09bc416d44e5f06d1e93e4e1cdcdc974fe`.
