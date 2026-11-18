# Seeky 5.4 — Leitura e estudos

Versão 5.4.0, código 5001900. Assinatura visual: **Haidoy Carlos Mautempo**.

## Leitor inspirado nas referências

O documento ocupa a área central sobre um fundo grafite, com página clara e controlos compactos. A barra superior reúne voltar, índice, pesquisa e marcadores. A barra inferior apresenta progresso, navegação e ferramentas: Texto, Visualizar, Rotação, TTS, Adicionar nota, Tela cheia, Definições e Organizar.

PDF: uma página de cada vez ou rolagem vertical; deslizar lateralmente no modo de página; ampliar com dois dedos ou toque duplo; ajustar à página, largura ou altura; temas branco, sépia, cinza e escuro; índice interno quando existe; pesquisa e modo de texto fluido. O modo de texto fluido permite seleccionar, anotar e acompanhar a voz. As páginas originais continuam disponíveis.

EPUB, DOCX e TXT: texto seleccionável com tamanho, margens, espaçamento, serifa e justificação ajustáveis. EPUB usa os nomes do índice de navegação quando fornecidos pelo livro. DOCX é uma leitura de texto reorganizado, sem prometer a mesma paginação do Word. DOC antigo e formatos sem suporte mantêm abertura externa.

Brilho aplicado apenas à janela do leitor, opção de manter ecrã ligado e ocultação das barras para leitura. O botão Voltar revela as barras no modo de tela cheia. As anotações, histórico, marcadores e exportação existentes foram conservados.

## Voz online e offline

A integração utiliza a API TextToSpeech do Android, com preferência pelo motor **Google Speech Services**, quando instalado. Permite escolher motor, português/inglês, voz e velocidade de 0,5× a 2×.

- **Offline:** aceita apenas vozes que o motor identifica como locais e com dados instalados. O aplicativo não muda automaticamente para uma voz de rede.
- **Online:** aceita vozes de rede, ordenadas por qualidade e latência. A naturalidade, os sotaques e a disponibilidade dependem do motor instalado; não foi configurada uma conta Google Cloud ou uma API paga. O texto é enviado ao fornecedor da voz quando este modo é escolhido; isso é indicado na interface.
- A folha de TTS dá acesso às definições do Android e à instalação dos dados de voz. O APK não inclui modelos de voz pesados.
- Pode ouvir desde a página/capítulo actual, retomar a posição guardada ou ouvir uma selecção de até 20000 caracteres. A opção de continuação carrega as páginas/capítulos seguintes conforme necessário.
- Serviço em primeiro plano com sessão multimédia, notificação de pausar/continuar, seguinte e parar. O serviço mantém a leitura ao sair do leitor e ao bloquear o ecrã, sujeito às regras do Android. Não depende de a Activity continuar aberta.
- Interrupções de áudio, auscultadores desligados e falhas de rede pausam a leitura. Eventos atrasados do motor não avançam uma nova sessão. A retoma usa a posição indicada pelo motor; motores sem eventos por palavra podem repetir parte do último trecho.
- Não há OCR nesta versão. PDFs digitalizados podem ser vistos, mas precisam de texto reconhecido para TTS e pesquisa. A extracção não contorna protecções do documento.

## Organização dos estudos

A hierarquia continua Curso → Módulo/Disciplina → Materiais. Os módulos passam a ter cartões numerados, título, contagem de materiais, progresso e acções explícitas. A biblioteca oferece **Capas** e **Lista**, com Estudar, Ouvir e detalhes. O leitor recebe o título do material e o percurso do curso/módulo.

A biblioteca apresenta 12 materiais por página, com botões anterior/seguinte e pesquisa mantida. Isso limita as imagens retidas na interface. O progresso de leitura/voz é guardado de forma crescente numa transacção e notifica a biblioteca visível. Chegar ao fim por voz não assinala automaticamente domínio do conteúdo: a conclusão a 100% continua explícita.

## Memória e dados

As páginas PDF são renderizadas por uma fila única, até duas pendentes, com bitmap de até 600000 píxeis e cache adaptativa. Temas usam filtros de cor sobre o bitmap existente. O cache de texto PDF mantém no máximo quatro páginas pequenas. O TTS envia trechos pequenos e conserva apenas a parte actual; PDFs são extraídos com armazenamento temporário e orçamento de memória de 4 MB para o PDFBox.

Limites de extracção: PDF até 64 MB, até 512K caracteres por página; TXT até 512 KB; EPUB/DOCX até 256 MB com limites de expansão já existentes. Documentos maiores continuam disponíveis pela abertura externa. Cópias temporárias são publicadas só depois de concluídas e removidas ao encerrar o leitor/serviço.

Não há alteração nos esquemas dos bancos: estudos 3, caderno 2 e catálogo 6. Cursos, materiais, anotações e cópias de segurança anteriores permanecem compatíveis com esta actualização. Nenhum serviço remoto foi alterado durante este trabalho.

## Validação

Compilação Android API 36, mínimo API 26; verificação de assinatura, alinhamento e integridade do APK. Testes locais incluem o estado real de segmentação/pausa/retoma do TTS, texto e índice PDF com PDFBox, consulta paginada sobre SQLite, progresso transaccional, migrações, cópias de segurança, cadernos e temporizador. As saídas completas ficam em `evidence/` no código-fonte.

**Ainda não validado num Android físico ou emulador:** aparência final, qualidade das vozes Google, áudio com ecrã bloqueado, notificações e políticas de bateria do Itel A17. Testes locais não demonstram esses comportamentos no aparelho. Use o roteiro fornecido para essa validação.

## Instalação

`Seeky_5_4_Leitura.apk` usa o mesmo identificador e certificado local da variante independente Seeky 5.1/5.2/5.3 e tem código de versão superior. Pode actualizar essa variante sem desinstalar. A chave privada não acompanha o código-fonte.

`Seeky_5_4_sem_assinatura.apk` conserva o identificador original `mz.bam.seeky.estudos`. Só pode ser instalado/actualizado depois de assinado com a chave apropriada; a chave da versão original enviada pelo utilizador não está disponível neste ambiente.
