# Registo de alterações

## 5.43.0 — 2026-11-23

- **Gemini 4 Hydrogen** (`gemini-4-hydrogen`, o modelo mais recente da Google) no Seeky:
  - aparece em **Ligação da IA → Modelo** como "Gemini 4 Hydrogen · mais recente" (antes do primeiro
    teste, e depois sempre que a lista da chave o tiver);
  - entra na lista de alternativas automáticas, a seguir a `gemini-3.5-flash-lite` e `gemini-3.5-flash`;
  - os modelos Gemini 4 recebem `thinkingConfig.thinkingLevel = LOW` e espaço de pelo menos 2048
    tokens, para o raciocínio não esgotar a resposta (o nível por defeito do Hydrogen é HIGH);
  - se a API recusar a definição de raciocínio (HTTP 400), o pedido repete-se uma vez sem ela;
  - se a chave não tiver quota gratuita para o modelo (HTTP 429 com "limit: 0"), o Seeky usa o
    modelo seguinte e explica: "O Gemini 4 Hydrogen não tem quota gratuita nesta chave…". Um limite
    de pedidos normal (429) continua a parar sem trocar de modelo.
- O modelo automático continua a ser `gemini-3.5-flash-lite` (mais rápido e com mais quota gratuita).

## 5.42.0 — 2026-11-23

- **IA (Gemini) a funcionar com chaves novas**: o modelo `gemini-2.5-flash-lite`, que estava fixo no
  código, já não aceita chaves criadas agora (HTTP 404). O modelo por defeito passa a
  `gemini-3.5-flash-lite`.
- O modelo deixa de estar fixo:
  - **Ligação da IA** tem um seletor "Modelo" ("Automático (recomendado)" ou um modelo concreto);
  - **Testar e guardar** pergunta à API (`GET /v1beta/models`, chave no cabeçalho) quais modelos a
    chave tem e oferece só os que fazem `generateContent` (sem voz, imagem nem embeddings);
  - se um modelo der 404 (ou 400 "não encontrado/não suportado"), o Seeky tenta o seguinte
    automaticamente (`gemini-3.5-flash-lite` → `gemini-3.5-flash` → os da lista da chave) e passa a
    usar o que funcionou;
  - mensagem clara em vez do texto em bruto da Google: "O modelo … foi descontinuado ou não está
    disponível para esta chave. O Seeky mudou para o …".
- Vale para o assistente, os resumos, a IA nas notas e a transcrição de letras.
- O relatório "Copiar erro" passa a indicar a versão real do Seeky e o modelo usado.

## 5.41.0 — 2026-11-22

- **Novos ícones nas notas**: um conjunto próprio, em dois tons (contorno com preenchimento
  suave), desenhado para o bloco de notas — lista, cartões e grelha, pastas, modelos, nova nota,
  pesquisa, desfazer/refazer, ver, Seeky IA, formatar, imagem, câmara, desenho, tarefas, passagem,
  tabela, gravar voz, ditar e data.
- O painel Aa troca os símbolos de texto (⫷ ≡ ⫸ ❝ ▬) por ícones claros para listas, alinhamento,
  recuo, citação, destaque, linha divisória e tirar a cor.
- Nos cartões, os emojis dão lugar a ícones para tarefas feitas, passagens, imagens, gravações,
  lembrete e nota bloqueada.

## 5.40.0 — 2026-11-22

- **Seeky IA nas notas** (botão ✨ no editor e no menu ⋮), com a chave Gemini do utilizador:
  resumir, corrigir o texto, organizar em tópicos, perguntas de estudo, extrair tarefas,
  passagens bíblicas relacionadas, continuar a escrever e pedido livre. Funciona na nota inteira
  ou só no texto seleccionado; mostra a resposta antes de a inserir ou substituir, pode-se
  desfazer, e as imagens e gravações da nota ficam sempre.
- **Espaçamento entre linhas** no painel Aa (Normal, 1,0, 1,15, 1,5, 2,0), guardado em cada nota
  e aplicado também na exportação para PDF e Word.
- **Bloco** no painel de selecção do leitor (PDF página a página, EPUB e Word originais e texto):
  abre o painel "Notas deste livro" com as palavras seleccionadas citadas.

## 5.39.0 — 2026-11-22

- **Bloco de notas renovado** (inspirado nas referências enviadas, adaptado ao Seeky):
  - separadores **Notas** e **Tarefas** (todas as tarefas de todas as notas, para marcar ali);
  - secções por data (Fixadas, Hoje, Ontem, 7 dias anteriores, 30 dias anteriores, meses);
  - vistas em **lista**, **cartão** e **grelha** (com fita nas fixadas e miniatura da imagem);
  - barra flutuante em baixo com pesquisa, **Modelos** e lápis para nova nota; pastas e lixo;
  - editor com voltar, desfazer/refazer, ver/editar e menu ⋮; data, pasta, livro e lembrete à vista;
  - barra de ferramentas flutuante: formatar (Aa), imagem, câmara, desenho, tarefa, passagem,
    tabela, gravar voz, ditar e data.
- **Texto rico**: títulos H1–H4, negrito, itálico, sublinhado, riscado, destaque, 6 cores,
  alinhar à esquerda/centro/direita, recuo, listas numeradas e com marcas, tarefas, citações,
  linhas divisórias e tabelas — formatado enquanto escreve.
- **Imagens, desenhos e voz** dentro das notas (galeria, câmara, desenho à mão com cores e
  borracha, gravação de áudio que toca na leitura).
- Menu ⋮: fixar, **bloquear** (impressão digital/PIN do telefone), **fundo** (papel, linhas,
  quadriculado, pontos, amarelo, lilás, céu, menta, noite), **lembrete** com notificação,
  **adicionar ao ecrã inicial**, mover para pasta, ligar a livro ou módulo, etiquetas.
- **Exportar e partilhar** como **PDF** e **Word (.docx)** — com títulos, cores, listas,
  tabelas e imagens —, além de Markdown e texto.
- **Notas dentro dos livros**: no leitor, a ferramenta **Notas** abre um painel para escrever
  sem sair do livro (com formatação rápida, página/capítulo e o texto seleccionado), mostra as
  notas desse livro e acrescenta a uma nota existente; na estante, ⋮ do livro → "Notas do livro"
  e "Escrever uma nota sobre o livro"; no Bloco de notas, as notas aparecem com o nome do livro.
- Jogo da Bíblia: as etiquetas "Passagem · livro" e "Referência · passagem" já não mostram um
  símbolo estranho no lugar da seta.

## 5.38.0 — 2026-11-22

- **Jogo da Bíblia renovado**:
  - 60 segundos por pergunta em todos os níveis; a dificuldade está no conteúdo;
  - Médio passa a ter o conteúdo do antigo Difícil (qualquer versículo, livro e capítulo,
    respostas escritas ou faladas);
  - Difícil: completar passagens (escolha da palavra), "quem disse?", ordem dos acontecimentos
    bíblicos e perguntas que pedem raciocínio, sempre com explicação e referência;
  - novo nível **Lendário** (300 pontos): perguntas para meditar — tipologia (serpente de
    bronze, cordeiro pascal), citações do Antigo no Novo Testamento, Paulo e Tiago sobre a fé,
    nova aliança —, escrever a palavra que falta e "o último" de acontecimentos próximos.
- **Bloco de notas** (novo):
  - lista com procura, filtros (fixadas, tarefas, com passagens, ligadas a livros, pastas),
    ordenação, cores e fixar; lixo com restaurar;
  - editor com gravação automática, desfazer/refazer, negrito, itálico, títulos, listas,
    tarefas, destaque, data, ditado por voz, ouvir a nota, partilhar, copiar e exportar (.md/.txt);
  - modelos: estudo bíblico (OIA), sermão, devocional, lista de tarefas e resumo de livro;
  - as passagens escritas (João 3:16, Rm 8.28, Salmo 23…) são reconhecidas: ficam tocáveis na
    leitura, aparecem em "Passagens nesta nota", podem trazer o texto da Bíblia importada para a
    nota e ser guardadas no estudo bíblico;
  - ligação a livros (abre o livro) e a módulos dos Estudos — as notas aparecem também em
    Estudos → Anotações; "Anotar" na leitura rápida de passagens e "Bloco de notas" no menu de
    selecção do leitor.

## 5.37.0 — 2026-11-22

- **Letras para todos os áudios** do leitor de música:
  - a letra é procurada sozinha ao abrir o leitor e a cada faixa nova (antes só ao abrir o
    separador Letra);
  - nomes limpos para a pesquisa (números de faixa, "Official Video", "[Lyric Video]", sites,
    sublinhados, "feat."), várias tentativas (etiquetas, "Artista - Título" e o inverso) e
    escolha segura: título, artista e duração têm de coincidir;
  - segunda fonte: Lyrics.ovh (texto simples) quando a LRCLIB não tem.
- **Transcrição com IA (Gemini)** quando não há letra — pregações, cultos, aulas, notas de voz e
  músicas sem letra na internet:
  - o som é descodificado no telefone, convertido para mono 16 kHz e enviado em partes de 6 min
    (AAC 32 kb/s, cerca de 0,25 MB por minuto; WAV se o telefone não tiver codificador AAC);
  - o Gemini escreve o que é dito ou cantado com os tempos; a letra fica sincronizada e aparece
    aos poucos, parte a parte; corre em segundo plano (Tarefas) e pausa se a quota acabar;
  - autorização pedida uma vez (Sempre automático / Só esta vez); modo Automático, Perguntar ou
    Desligado e "só em Wi-Fi" no menu da biblioteca de música; nova tentativa automática no
    máximo uma vez por dia por faixa.
- **Letras para todas as músicas** (biblioteca de música → Mais): procura todas em segundo plano
  e envia para transcrição as que faltarem (até 25 de cada vez).

## 5.36.0 — 2026-11-21

- **Estante de madeira** (inspirada no modelo enviado), agora o modo normal dos Livros:
  - prateleiras com tábuas de fundo, veios, juntas, sombras da prateleira de cima, postes laterais
    e frente da prateleira iluminada; as prateleiras vazias continuam até ao fundo do ecrã;
  - livros de pé com lombada, bloco de folhas à direita, brilho suave e sombra; capas geradas
    mostram o título mesmo nos livros estreitos; percentagem, menu ⋮ e estrela de favorito;
  - madeiras: Nogueira, Carvalho, Cerejeira, Ébano e Laca branca (Ordenar e filtrar);
  - 30 livros por página, com botões Anterior/Seguinte dentro da estante; seta ao lado de
    "Todos os livros" para escolher grupos.
- **Importar livros** (como no modelo enviado):
  - Uma pasta (com ou sem subpastas, com caminho tipo /sdcard/Livros, pastas sugeridas —
    Transferências, Documentos, WhatsApp, Telegram — e recentes, ou um navegador de pastas) ou
    Escolher ficheiros;
  - formatos a incluir, tamanho mínimo do ficheiro (20 KB por omissão), Favorito e Colecção;
  - a pasta é verificada e importada logo, em segundo plano, sem passo extra; repetidos ignorados.
- **Novos formatos de livro**, convertidos para EPUB ao importar ou ao abrir dos Ficheiros:
  FB2 (também .fbz/.fb2.zip, com capa, notas e codificações antigas), MOBI/AZW/AZW3/PRC (sem DRM,
  com compressão PalmDOC ou Huffman, imagens e capa), ODT, RTF, MHTML (páginas web guardadas) e CBZ
  (banda desenhada, uma página por imagem). Livros com DRM mostram uma mensagem clara.
  DJVU, CHM, UMD e CBR não são suportados.

## 5.35.0 — 2026-11-20

- **10 temas novos**, criados a partir das paletas enviadas pelo autor:
  - **Ardósia** (ardósia e coral, escuro), **Sálvia** (#f0efe6, #5f7e7b, #c3c1af, #405a5c),
    **Névoa** (#6E7F9A, #93B5C6, #C9CCD5, #E4D8DC, #FFE3E3), **Hortelã** (#2C6975, #68B2A0,
    #CDE0C9, #E0ECDE), **Pinheiro** (#00412E, #96BF8A, #E8EAE5), **Menta** (#40916C, #D8F3DC,
    #74C69D, #1B4332), **Petróleo** (petróleo escuro e pêssego), **Quartzo** (verde-quartzo e
    rosado), **Folha** (verde-folha claro) e **Produtivo** (azul-petróleo e laranja);
  - cada tema tem cabeçalho, cartões, raio, estilo de ícones e cores das capas próprios, e passa
    nas regras de legibilidade (texto ≥ 7:1, texto secundário ≥ 4,5:1, botões ≥ 4,5:1);
  - os tons que não eram legíveis como estão nas imagens foram ajustados (por exemplo o laranja
    do Produtivo leva texto escuro nos botões).
- **Selector de cores com miniaturas**: cada tema aparece com a sua pré-visualização (página,
  cabeçalho, cartão e botão), agrupado em Novos, Modernos, Automáticos e Clássicos.

## 5.34.0 — 2026-11-20

- **Painel de selecção junto ao texto** (inspirado no modelo enviado), em PDF, EPUB/Word
  originais e texto:
  - estilos: Destacar, Sublinhar, Riscar e Ondulado (novo), cada um com 6 cores — tons suaves
    para destacar e cores fortes para as linhas;
  - toque num estilo e depois numa cor: a marcação fica logo no livro e em As minhas
    marcações; o estilo e a cor ficam lembrados;
  - Nota, Copiar, Dicionário, Partilhar e Mais (mostra o menu completo do Seeky);
  - fica por cima das palavras seleccionadas (ou por baixo quando não há espaço) e acompanha os
    selectores; o menu do sistema deixa de se sobrepor.
- Sublinhados, riscados e ondulados com cor no PDF (desenhados na página), na vista original
  (CSS) e no texto; filtros "Ondulados" e por cor em As minhas marcações.
- **Rodapé de leitura em ecrã cheio**: bateria, hora, título e página (ex.: 341/383).

## 5.33.0 — 2026-11-20

- **Leitor renovado** (inspirado no modelo de leitor de PDF enviado), na vertical:
  - barra de título com a cor da aplicação, título maior e menu ⋮ (Ferramentas de leitura); a
    barra de estado acompanha a cor;
  - régua de páginas numa só linha, entre as setas, com o número da página (‹ régua 341/383 ›);
  - todas as ferramentas visíveis numa grelha de 2 linhas, sem deslizar: Índice, Pesquisar,
    Marcadores, Ouvir, Texto, Visualizar, Rodar, Marcações (deste livro), Nota, Partilhar, Ecrã
    cheio e Mais (Estudar com Seeky, Definições, Organizar, Ferramentas de leitura, Passagens
    bíblicas, Estudo bíblico);
  - no PDF, dois botões redondos sobre a página: ajustar (página → largura → altura) e rolagem
    vertical / página a página;
  - fundo cinzento claro à volta da página nos temas claros (escuro nos temas escuros).
- Na horizontal mantém-se a barra única compacta, com as mesmas ferramentas.

## 5.32.0 — 2026-11-20

- **As minhas marcações renovadas**:
  - cada marcação é um cartão de citação: barra e fundo suave da cor, texto em serifa na cor do
    tema (legível no tema escuro), etiqueta com o tipo e a cor, página/capítulo e data;
  - acções em cada cartão: copiar, partilhar e Abrir (no livro, no sítio exacto); toque para ver
    por completo;
  - ordenar por data (Recentes) ou agrupar por livro, com o número de marcações de cada livro;
  - filtros por tipo (só os que existem) e por cor (círculos), pesquisa por texto, nota ou livro
    sem contar acentos, com botão para limpar;
  - partilhar todas as marcações agrupadas por livro e página (botão junto à contagem).
- **Texto das marcações limpo**: o texto extraído de alguns PDF traz tabulações e caracteres
  invisíveis entre as palavras, que apareciam como falhas escuras ou blocos; passam a espaços
  simples e as palavras partidas no fim da linha ("atendi-/mento") voltam a juntar-se.
- Vista completa de uma marcação redesenhada (etiqueta, livro, citação, nota e acções).

## 5.31.0 — 2026-11-20

- **Marcações na vista original do EPUB e do Word** (incluindo "EPUB ajustado"): antes a
  selecção só mostrava "Copiar · Selecionar tudo" do sistema e não era possível marcar.
  - ao seleccionar aparece a barra de marcar (6 cores, Sublinhar, Riscar, Nota) e o menu ganha
    Destacar, Cores e mais marcas e Nota;
  - as marcas ficam guardadas por capítulo e voltam a aparecer ao reabrir, ao mudar o tamanho
    da letra ou ao ajustar o EPUB; mantêm-se mesmo quando o texto está em negrito ou numa
    ligação bíblica;
  - um toque numa marca mostra-a por completo (copiar, partilhar, mudar cor, eliminar);
  - aparecem em As minhas marcações e "Abrir no livro" volta à vista original, ao capítulo e
    às palavras marcadas.
- O menu de selecção da vista original passa a aparecer junto às palavras seleccionadas (antes
  podia ficar por cima da barra de baixo).

## 5.30.0 — 2026-11-20

- **Barra de marcar ao seleccionar**: no leitor do Seeky, ao seleccionar texto (páginas de
  texto e páginas PDF) aparece em baixo uma barra com as 6 cores, Sublinhar, Riscar e Nota.
  Um toque marca a selecção, que fica logo pintada no livro e em As minhas marcações. Antes, as
  cores estavam escondidas no menu "Cores e mais marcas" do menu de selecção.
- As marcações funcionam nos livros abertos no Seeky; noutras aplicações de leitura usa-se o
  menu dessas aplicações. Para abrir no Seeky: Ficheiros/Biblioteca, ou "Abrir com" → Seeky.

## 5.29.0 — 2026-11-20

- **Marcações com cores** no texto e nas páginas PDF:
  - seleccionar → Destacar (amarelo) ou **Cores e mais marcas**: 6 cores, sublinhar, riscar e
    nota;
  - no PDF as marcas ficam desenhadas na página (efeito de marcador nas páginas claras) e um
    toque na marca mostra-a por completo;
  - o caderno de leitura passa a aceitar as cores novas e o riscado (antes rejeitava-os como
    "Anotação inválida").
- **As minhas marcações** (Mais, página inicial e menu do leitor → Marcações deste livro):
  - todas as marcações de todos os livros, com pesquisa e filtros (destaques, sublinhados,
    riscados, notas e cada cor);
  - toque para ver por completo (copiar, partilhar, mudar cor, eliminar);
  - toque longo ou "Abrir no livro" abre o livro na página e no sítio exactos (as palavras
    ficam seleccionadas no texto);
  - partilhar todas as marcações agrupadas por livro.
- **Estudo bíblico** (novo menu):
  - sequência de dias, leitura de hoje com marcar como lido (também para quem lê na sua Bíblia
    em PDF ou papel) e "Ler" dentro do Seeky;
  - controlo semanal (capítulos por dia, cartões e jogos) com meta da semana;
  - planos: Bíblia em 1 ano, Novo Testamento em 3 meses, Evangelhos em 30 dias, Salmos e
    Provérbios em 2 meses, um capítulo por dia;
  - passagens guardadas: ler, decorar ou remover.
- **Decorar** com cartões de repetição espaçada:
  - na leitura rápida, "Decorar" cria o cartão com livro, capítulo, versículo e texto;
  - modos: escrever ou dizer o versículo (com pista das iniciais e as palavras em falta a
    vermelho), dizer o livro, ou misto;
  - Errei / Difícil / Bom / Fácil marcam a próxima revisão; aos 21 dias fica decorado.
- **Jogo da Bíblia** (novo menu fora dos livros):
  - 10 perguntas, 3 níveis (30, 25 e 20 s), pontos com bónus de tempo e de sequência;
  - passagem → livro, referência → passagem, livro e capítulo, resposta escrita ou falada,
    histórias, ordem dos livros, grupos, autores, capítulos e factos;
  - ajuda 50/50, responder por voz (nome do livro ou letra);
  - resultado com estrelas e revisão das respostas; guardar o nome no Recorde (10 melhores por
    nível);
  - sem Bíblia importada, joga com histórias, livros e factos.
- A leitura rápida ganhou "Decorar" e, no capítulo inteiro, "Marcar como lido".

## 5.28.0 — 2026-11-20

- **Passagens bíblicas reconhecidas como se escrevem em português** (`BibleReferences` reescrito):
  - capítulo e versículo com dois pontos, ponto ou vírgula: `Jo 3:16`, `Isaías 3.1-7, 12`,
    `Jo 3,16-18.20`;
  - intervalos, listas, vários capítulos (`Is 3.1-4.2`, `Is 3.1-7, 12.3`, `Mt 5:3-12; 6:1`),
    "e seguintes" (`2Tm 3.16s`), meio versículo (`Gn 1.1a`);
  - capítulos inteiros (`Salmo 23`, `Isaías 53-55`) e livros de um só capítulo (`Judas 3`);
  - texto de OCR sem espaço (`Isaías3.1`) e quebras de linha de PDF entre livro e capítulo;
  - limites de capítulos e versículos por livro evitam falsos positivos ("Os 3,5 milhões",
    "na 2.1 secção", "Ne 2.3.1", "João 3 vezes", "Na 2,3 g");
  - a leitura em voz alta passa a dizer também "Isaías capítulo 3, versículos 1 a 7 e 12";
  - mais rápido do que o reconhecedor anterior.
- **Tocar na referência em todos os formatos**:
  - PDF: as referências da página ficam sublinhadas e abrem com um toque (o resto da página
    continua a mostrar/esconder a barra); um botão "N passagens bíblicas" lista as da página,
    também em páginas digitalizadas depois do OCR;
  - Word (vista original) e EPUB: as referências ficam ligadas mesmo quando o livro e o
    capítulo estão em formatações diferentes;
  - texto (TXT, Word/EPUB em texto, página PDF em texto, OCR) e o texto do digitalizador;
  - seleccionar um texto → "Abrir passagem bíblica" (PDF e texto).
- **Leitura rápida da passagem**, por cima do livro:
  - versões importadas em botões (a escolhida fica lembrada);
  - Copiar (com referência e versão), Partilhar, Ouvir, Guardar, A−/A+;
  - Capítulo inteiro com a passagem destacada e capítulo anterior/seguinte;
  - Comparar versões lado a lado;
  - sem Bíblia importada: Descarregar uma Bíblia, Importar ficheiro ou Copiar referência.
- **Passagens guardadas** em Consultar passagem → Guardadas, em Ferramentas → Bíblias e no menu
  do leitor; o leitor também permite desactivar as ligações bíblicas.
- Consulta escrita mais tolerante: `is 53`, `isaias 3.1-7, 12`.

## 5.27.0 — 2026-11-20

- **Leitor de música reorganizado** (`MediaActivity` reescrito):
  - lugares fixos: cabeçalho (voltar · "A reproduzir" e "Faixa N de M" · Mais) e separadores
    Capa · Letra · Fila (a fila passa a estar no próprio ecrã);
  - título, artista e favorita (coração);
  - controlos na ordem habitual: aleatório · anterior · reproduzir · seguinte · repetir (com "1"
    em repetir uma);
  - barra de acções com nome: Equalizador · Velocidade · Temporizador · Partilhar;
  - menu Mais por secções: Reprodução (−10 s, +10 s, temporizador, velocidade, capítulos),
    Esta faixa (favorita, letra, fila, partilhar, detalhes, abrir fora) e Biblioteca
    (biblioteca, equalizador, parar).
- **Ícones**: anterior/seguinte cheios, coração, letra, fila, temporizador, rodar ecrã, ajuste,
  colar; o mini-leitor usava setas e o botão Aleatório usava o ícone de repetir — corrigido.
- **Letras**:
  - Procurar online (pesquisa LRCLIB editável, com lista de resultados sincronizados ou em
    texto; o escolhido aplica-se logo);
  - Google: abre a pesquisa e, ao voltar, a letra copiada é aplicada automaticamente (ignora
    endereços e textos curtos);
  - Colar, Copiar (toque longo copia uma linha), Editar ou Remover, tamanho do texto A−/A+,
    partilhar a letra.
- **Vídeo**:
  - ecrã inteiro com controlos que se escondem sozinhos;
  - rodar o ecrã (automático, horizontal, vertical) e rodar a imagem 90° (vídeos gravados de
    lado);
  - ajuste Ajustar / Preencher / Esticar; velocidade, fotograma e partilhar;
  - rodar o telefone já não reinicia o ecrã.
- **Biblioteca de música e vídeo**: novo separador Início com:
  - adicionadas recentemente;
  - mais ouvidas Hoje / Esta semana / Este mês / Sempre (histórico de reproduções, com o
    número de vezes);
  - ouvidas recentemente e favoritas, cada uma com "Ver todas".
- `Media5270Test` (4 testes); testes da 5.26 actualizados.

## 5.26.0 — 2026-11-20

- **Aviso de novas versões** (`UpdateJob`): uma tarefa em segundo plano (de 6 em 6 horas, só
  com rede) mostra uma notificação quando sai uma versão nova, uma vez por versão. Toque para
  actualizar. Ligar e desligar em Mais → Actualizações.
- **Leitor de música** (`LibraryActivity`, `MediaLibrary`, Mais → Multimédia → Música):
  - procura automática no telefone (MediaStore, memória interna e cartão SD; ao abrir, no
    máximo de 12 em 12 horas); adicionar pasta (com subpastas) e adicionar músicas soltas;
  - separadores Faixas, Artistas, Álbuns, Géneros, Anos, Pastas, Favoritas, Recentes e Mais
    ouvidas, com pesquisa, ordenação e entrada em cada artista ou álbum;
  - tocar tudo e aleatório; tocar a seguir e adicionar à fila; partilhar, detalhes e remover;
  - mini-leitor no fundo do ecrã.
- **Leitor de vídeo**: a mesma biblioteca para vídeos (pastas, recentes, anos, favoritos, mais
  vistos), com miniaturas.
- **Fila de reprodução** (`MediaQueue`): faixa anterior e seguinte (no leitor, na notificação e
  nos auscultadores), aleatório, repetir (desligado, tudo, uma) e lista da fila; passa sozinha
  à faixa seguinte.
- **Equalizador ao estilo Poweramp** (`EqualizerActivity`, `AudioEffects`):
  - 10 bandas de 31 Hz a 16 kHz (±15 dB) com curva de resposta e pré-amplificação
    (`DynamicsProcessing` no Android 9+; equalizador do aparelho no Android 8);
  - 15 predefinições, incluindo Gospel, Voz falada e Kizomba, e predefinições do utilizador;
  - botões rotativos de graves, agudos, virtualização e volume extra; volume do sistema,
    balanço e limitador.
- **Letras** (`Lyrics`), no leitor (botão Aa):
  - procura pela ordem: letra guardada, ficheiro .lrc ao lado da música, letra incorporada
    (ID3, FLAC, M4A) e LRCLIB na internet (envia só título, artista, álbum e duração; pode
    desligar-se);
  - letras sincronizadas que se destacam e avançam com a música; tocar numa linha salta para
    esse momento; colar ou editar a letra.
- `Media5260Test` (11 testes).

## 5.25.0 — 2026-11-20

- **Editor de fotografias** (`PhotoEditorActivity`, `PhotoEdit`), a partir do visualizador de
  imagens (Editar):
  - Recortar: proporções Livre, Original, 1:1, 4:3, 3:4, 16:9 e 9:16, com grelha;
  - endireitar ±45° com zoom automático (sem cantos vazios), perspectiva vertical e horizontal;
  - rodar e espelhar;
  - 12 filtros com intensidade;
  - 10 ajustes (brilho, contraste, saturação, calor, matiz, realces, sombras, nitidez, vinheta,
    grão) e Automático;
  - desenho com pincel e marcador; texto com cor, tamanho e fundo, que se arrasta;
  - desfazer e refazer; manter premido mostra o original;
  - guardar como cópia (ao lado do original ou em Imagens/Seeky), substituir o original (com
    confirmação) ou partilhar. Até 3200 px, com nova tentativa mais pequena se faltar memória.
- **Partilhar nos leitores** (`ShareTools`):
  - foto aberta: Partilhar e Abrir noutra aplicação;
  - música: botão no cabeçalho, com "o que estou a ouvir" em texto;
  - vídeo: também o fotograma actual;
  - documentos: botão na barra do leitor; nos PDF, também a página actual como imagem; texto
    seleccionado com Partilhar selecção.
- Editor e digitalizador aclaram a cor de destaque nos temas claros, para se ler no fundo escuro.
- `PhotoEditor5250Test` (6 testes); `Viewers5190Test` verifica os novos botões.

## 5.24.0 — 2026-11-20

- **Bíblias para descarregar** (`BibleCatalog`): catálogo do eBible.org com mais de 1500
  traduções em mais de 1200 línguas (incluindo português — Bíblia Livre, Bíblia Portuguesa
  Mundial, Nova Bíblia Viva — e línguas de Moçambique), pesquisa por nome ou língua, licença e
  conteúdo (Bíblia completa, NT…) de cada versão. O ZIP USFM é juntado num só ficheiro sem notas,
  referências cruzadas nem atributos Strong e entra pela importação de Bíblias existente
  (servido pelo `ScanProvider`, só leitura). Testado com a Bíblia Livre real: 66 livros e
  31 102 versículos. Versões com direitos de autor continuam a importar-se do ficheiro do
  utilizador (MyBible, USX, USFM).
- **Digitalizador renovado** (`ScanActivity`, `ScanFilters`): três passos (Recortar, Melhorar,
  Páginas); detecção da folha com três candidatos (clara, escura, cor diferente da mesa); lupa
  ao arrastar os cantos; efeitos com miniaturas (Documento sem sombras, Melhorar, Sem sombras,
  Cinzento, Preto e branco, Vívido, Original); brilho, contraste e nitidez; rotação; várias
  fotos da galeria de uma vez; reordenar, duplicar e apagar páginas; exportar PDF (A4, Carta ou
  tamanho da foto) ou JPG em três qualidades, guardar e partilhar, e extrair texto por OCR. O
  botão Flash deixa de aparecer por cima.
- **Mapa do armazenamento renovado** (`StorageMap`, `UiRows`): resumo do telefone (usado, livre,
  total), espaço por tipo com barra colorida, volumes com nomes (Memória interna, Cartão SD),
  pastas maiores com barras, ficheiros grandes e antigos com ícone, tamanho, idade e pasta
  abreviada; "Analisar o arquivo" abre este mapa. Estatísticas de leitura e Pomodoro usam as
  mesmas linhas com barras.
- `Features5240Test` (11 testes).

## 5.23.0 — 2026-11-20

- **Actualização sem erro de assinatura** (`AppUpdates.verifyPackage`): no Android 9–12 o
  certificado de um APK descarregado só é devolvido quando se pede também `GET_SIGNATURES`;
  pedir apenas `GET_SIGNING_CERTIFICATES` dava uma lista vazia e a actualização era recusada
  como "não assinada". O certificado é agora lido por três vias (SigningInfo, Signature[] e
  assinatura v1 do JAR); se nenhuma funcionar, decide o instalador do Android, que recusa
  sempre outra chave. O APK é assinado com v1 + v2 + v3 (`tools/sign_release.sh` repete a assinatura com o apksigner). A mensagem de chave
  diferente mostra as impressões digitais. **Quem tem a 5.22 instala esta versão uma vez à mão;
  a partir daí, as actualizações dentro da aplicação funcionam.**
- **Transferir por Wi-Fi** (`WifiTransfer`): servidor local na porta 8080 com código de 6
  dígitos, sessão HttpOnly/SameSite=Strict, bloqueio após 5 códigos errados e desligar após 20,
  envios com cabeçalho próprio, caminhos confinados à pasta partilhada, desliga-se ao fechar ou
  após 15 min sem uso.
- **Ferramentas de PDF** (`PdfTools`): juntar, extrair páginas, rodar, comprimir imagens e fotos
  para PDF (A4, rotação EXIF); também no menu de cada PDF.
- **Digitalizar documento** (`ScanActivity`, `ScanProvider`): câmara ou galeria, detecção
  automática da folha (Otsu + maior região clara), cantos ajustáveis, correcção de perspectiva,
  filtros Documento/Cores/Original e várias páginas num PDF.
- **Mapa do armazenamento** (`StorageMap`): pastas por tamanho com barras e navegação, e
  ficheiros com mais de 50 MB sem alterações há 6 meses (para a lixeira com confirmação).
- **Pasta privada** (`PrivateFolder`, `Auth`): ficheiros na área interna do Seeky, abertos só
  com impressão digital, rosto, PIN ou padrão; restaurar, abrir ou apagar.
- **Estatísticas e metas de leitura** (`ReadingStats`): tempo por dia, dias seguidos, semana,
  mês, livros com mais tempo, meta diária e lembrete.
- **Dicionário e tradução** (`WordLookup`) na selecção do leitor (texto e PDF), com Vocabulário
  e cartões de revisão.
- **Anotações**: exportar para PDF e criar cartões de revisão a partir de destaques e notas
  (`AnnotationTools`).
- **Bibliotecas gratuitas** (`OpenLibraries`): Project Gutenberg e catálogos OPDS (incluindo
  Calibre na rede local); os livros entram directamente na biblioteca.
- **Provas e trabalhos** (`StudyAgenda`) com contagem decrescente e lembretes; **Pomodoro**
  (`Pomodoro`) sobre o temporizador de estudo, com o tempo por disciplina.
- **Tema Horário** (Areia de dia, Floresta à noite), **widget Hoje** (`TodayWidget`) e
  **atalhos do ícone** (`AppShortcuts`). Audiolivro: já existia em Ler em voz alta → Livro inteiro.
- `Features5230Test` (18 testes).

## 5.22.1 — 2026-11-20

- **Títulos em negrito** em todos os temas (pedido do autor): Areia e Floresta em serifa negrito;
  Aurora, Ouro, Néon, Vidro, Carvão e Ciano em sans-serif negrito (deixam de usar as versões
  finas/médias); Gelo mantém a condensada negrito. Inclui o valor do armazenamento e os títulos
  das capas geradas. `BoldTitles5221Test`; 392 testes Robolectric passam.

## 5.22.0 — 2026-11-19

- **Categorias certas** (`Rules.category`, regras v2): vídeos reconhecidos pela extensão (mkv,
  avi, webm, 3gp…), PDF e APK também pelo MIME, mais arquivos (zst, iso, cab…), APKs (apkm) e
  documentos (rtf, ods, odp, mobi, azw3, fb2, djvu, usx, srt…). O catálogo existente é
  reclassificado uma vez, em segundo plano. `CategoryRulesTest` (36 verificações).
- **Limpeza** (`StorageCleaner`, Mais → Armazenamento e limpeza): cache do Seeky (preserva o
  que está em uso), miniaturas da galeria, APKs duplicados (mesmo pacote e versão) ou já
  instalados — enviados para a lixeira com confirmação —, ficheiros duplicados e atalhos do
  Android para libertar espaço e para a cache de outras aplicações.
- **Widget "Seeky"** (`SeekyWidget`): barra de pesquisa e atalhos para PDF, Imagens, Vídeos,
  Áudio e Limpeza, nas cores do tema; volta a pintar-se ao mudar de tema. "Widget no ecrã
  inicial" permite escolher entre este e o de continuar leitura.
- A verificação automática de actualizações não corre quando o Seeky não consegue ler a própria
  versão (evita oferecer uma "actualização" sem termo de comparação).
- `Cleanup5220Test`; 390 testes Robolectric passam.

## 5.21.1 — 2026-11-19

- **Cabeçalho sem barra** (`AppPalette.bare`) em Areia, Floresta, Néon, Vidro e Gelo: só o
  logótipo, a legenda e os botões sobre o fundo, na cor do texto do tema; barra de estado na
  cor do fundo. Aurora, Ouro, Carvão e Ciano mantêm a barra.
- **Hierarquia e alinhamento**: margem única (16 dp) para logótipo, títulos, contagens e
  cartões; títulos de página 24 sp, secções 20 sp com subtítulo 13 sp e espaçamento fixo;
  legendas em maiúsculas espaçadas (O SEU ARQUIVO, ARMAZENAMENTO, N LIVROS); cartão de
  armazenamento alinhado à esquerda com valor 26 sp em escrita normal.
- Retirados efeitos que cortavam o fundo (Vidro) e tapavam texto (Aurora/Ouro).

## 5.21.0 — 2026-11-19

Design por tema, a partir das novas referências do autor.

- **9 temas modernos** (`AppPalette.MODERN`): Areia, Floresta, Aurora, Ouro, Néon, Vidro, Gelo,
  Carvão, Ciano — cada um com `style`, cor secundária, raio dos cartões e estilo de ícone
  (quadrado tingido, contorno, sem caixa, círculo). Selector de cores com descrição de cada tema.
- **`ThemeStyle`**: tipografia dos títulos por tema (serifa, maiúsculas espaçadas finas,
  condensada, média com brilho néon), caixas dos ícones, textura de papel (ruído em cache),
  quadrados inclinados, grelha em perspectiva, traço néon e brilho radial.
- **`BrandSurface`**: cabeçalhos, cartão de destaque e fundos próprios por tema (cartões
  empilhados com papel, grelha + linha néon, brilho violeta, padrão de quadrados, círculos).
  Corrigido o alfa herdado da sombra que deixava o bloco translúcido.
- **`BookCoverArt`**: capas geradas por tema (editorial, rótulo empilhado em papel, moldura
  néon, vidro, minimalista, quadrados, degradê); título e autor fora da zona do menu e do selo.
- `Modern5200Test` captura Início e Livros nos 9 temas e verifica o contraste de todos.

## 5.20.1 — 2026-11-19

- **Actualizações dentro da aplicação** (`AppUpdates`): ao abrir, no máximo uma vez por dia, o
  Seeky lê `entrega/latest.json` no ramo `main` do GitHub e, se houver versão mais recente,
  oferece "Actualizar agora". Descarga só por HTTPS de anfitriões do GitHub; o APK só chega ao
  instalador se o tamanho e o SHA-256 baterem certo e se for o mesmo pacote, mais recente e
  assinado com o mesmo certificado instalado. "Mais tarde" adia 3 dias; verificação automática
  pode ser desligada em Mais → Actualizações.
- `tools/publish_update.py` publica uma versão (APK + `latest.json` + SHA256SUMS).
- `Updates5201Test`; 387 testes Robolectric passam.

## 5.20.0 — 2026-11-19

Visual moderno, a partir das referências enviadas pelo autor.

- **Temas Areia, Floresta e Carvão** (`AppPalette.modern`): Areia é a nova predefinição
  (quem usava o antigo "Claro" passa uma vez para Areia; os temas clássicos mantêm-se);
  "Sistema" alterna entre Areia e Floresta. Contraste verificado (texto ≥ 7:1, secundário ≥ 4,5:1).
- **Cabeçalhos limpos** (`BrandSurface.modern`): bloco de cor com cantos arredondados, sem ondas.
- **Tipografia** (`AppFonts`): títulos em serifa (Noto Serif) nos temas modernos.
- **Ícones**: traço mais fino; categorias do Início em cartões com ícones de linha; ícones
  dos painéis seguem a cor do tema.
- **Menus** (`UiDialog`): ícone próprio por opção (~90 palavras-chave), cartões com contorno e
  seta; listas de escolha sem ícone.
- **Capas geradas** (`BookCoverArt`) para livros sem imagem: tom natural por título, título em
  serifa, moldura e autor; inicial nas capas pequenas (sem sobrepor o selo de progresso).
- Ícones novos: olho, movimento, alfinete, ponto, tarefas, importar, colecções, widget, escudo,
  ajustes, dicionário, caderno.
- `Modern5200Test` com capturas dos ecrãs nos três temas.

## 5.19.2 — 2026-11-19

- **Logótipo original reposto** como ícone do Seeky e do Seeky Flash (pedido do autor);
  o ícone adaptativo da 5.19.1 foi retirado. O atalho das Definições Rápidas mantém um
  ícone monocromático, como o Android exige.
- **Ícones dentro da aplicação**: Ferramentas e ecrãs de trabalho com o ícone de cada
  funcionalidade num quadrado arredondado tingido (estilo do painel Mais), texto centrado,
  seta "›" e botão de voltar com ícone em vez de "‹". Ícones próprios para Tarefas,
  Importação, Colecções, Motores de voz, Widget, Desempenho, Dicionário, Armazenamento,
  Caderno PDF, Cópia, Licenças e Formato.
- Versão 5.19.2 (5004302).

## 5.19.1 — 2026-11-19

- Ícone adaptativo experimental (retirado na 5.19.2 a pedido do autor); ícone monocromático
  para o atalho das Definições Rápidas.
- **Leitor de música/vídeo** redesenhado: capa e metadados incorporados (título, artista, álbum),
  fundo tingido pela cor da capa, controlos com ícones (recuar/avançar 10 s, reproduzir circular),
  velocidade, repetir, temporizador de sono, capítulos e tempo restante.
- **Visualizador de imagens**: fundo escuro, descodificação até 3584 px conforme a memória,
  detalhes (formato, píxeis, megapíxeis, tamanho), controlos com ícones e partilha.
- **Vozes**: 15 idiomas na leitura em voz alta, frase de amostra em cada um, vozes da mesma
  região numeradas ("Voz 1", "Voz 2"…). Etiqueta "TTS" passa a "Ouvir".
- Versão 5.19.1 (5004301), assinada com a chave do autor.

## 5.19.0 — 2026-11-19

### Novas funcionalidades
- **Bloqueio da aplicação** (Mais → Bloqueio da aplicação): impressão digital, rosto, PIN ou
  padrão do Android, sem o Seeky guardar códigos. Pede autenticação ao abrir e ao voltar
  após 30 s, 1, 5 ou 15 min em segundo plano; protege também o painel do botão Flash e oculta
  a pré-visualização nas aplicações recentes (Android 13+).
- **Cópia automática** (Mais → Cópia automática): estudos, notas, marcadores, leitura e
  preferências guardados diária ou semanalmente numa pasta à escolha (cartão SD, Download,
  Google Drive…), mantendo as últimas 3 cópias automáticas. "Fazer cópia agora" com
  resultado nas notificações.
- **Tom de voz** ajustável em Ler em voz alta, aplicado à leitura, amostras e gravação WAV.
- **Novidades**: painel mostrado uma vez após a actualização e disponível em Mais.
- A versão de depuração chama-se **"Seeky (teste)"** para não se confundir com a instalada.
- **Botão Flash recolhe-se sozinho**: após 10 s sem uso fica meio escondido na margem, com a
  lupa deslocada para a parte visível e ligeiramente translúcido. Ligado por omissão; o tempo
  (3–30 s) e a posição continuam ajustáveis em Ícones e botão Flash.
- **Vozes naturais do Google** substituem o módulo Seeky Vozes Naturais, que foi retirado
  (ponte `voicebridge`, permissão e testes). O ecrã "Vozes naturais do Google" escolhe entre
  online (mais naturais) e offline, baixa as vozes e ajuda a remover o módulo antigo. Quem
  usava o módulo passa automaticamente para as vozes online do Google.
- **APK assinado no GitHub Actions** quando os segredos da chave estão configurados.


### Segurança
- **Recepção de ficheiros (corrigido).** A actividade de partilha/abertura é exportada e
  aceitava URIs `file://` da área privada da aplicação e URIs `content://` dos providers
  internos do Seeky. Uma aplicação maliciosa podia levar o Seeky a copiar bases de dados,
  preferências ou materiais privados para a biblioteca ou para uma pasta pública ("confused
  deputy"). Novo `IncomingUris` recusa ambos; `ImportWork` recusa `file://` fora do
  armazenamento autorizado.
- **Lançador exportado.** O pedido de extracção (`archive_uri`) só é aceite quando o Intent
  foi assinado pelo próprio Seeky (token secreto por aparelho, comparação em tempo constante).
- **Transferência entre aparelhos (Android 12+).** `allowBackup="false"` não a impedia;
  `data_extraction_rules` exclui os cofres cifrados e o token interno, e desactiva a cópia
  na nuvem.

### Voz
- `SpeechNormalizer`: abreviaturas lidas por extenso (pt/en), numerais romanos de
  capítulos/partes/séculos em árabes, URLs lidos pelo domínio, pontos-guia de índices,
  marcadores, pontuação repetida, títulos em MAIÚSCULAS, `%`, `§`, `°C`. O destaque no ecrã
  continua alinhado com o original e o dicionário pessoal tem prioridade.
- Selecção automática da variante regional em empate de qualidade: Moçambique, Angola e
  restantes países lusófonos africanos → pt-PT (e en-GB); Brasil → pt-BR.

### Flash e aparência
- Botão Flash com resposta física: encolhe ao premir, levanta com anel ao arrastar, mola ao
  soltar, vibrações curtas; lançamento rápido escolhe a margem; toque longo abre os ajustes.
- Mosaico "Seeky Flash" das Definições Rápidas reflecte o estado real e liga/desliga o botão.
- Novo tema **Sistema** que segue o modo claro/escuro do Android em tempo real.

### Estrutura
- Projecto Android na raiz do repositório; scripts em `tools/`; documentação histórica em
  `docs/historico/`.
- Build Gradle funcional (AGP 8.13, wrapper 8.14.3); versão e identidade numa só fonte
  (`app/build.gradle`), partilhada pelo build offline.
- CI no GitHub Actions: compilação e testes do núcleo.

## 5.18.0 e anteriores

Ver `docs/historico/` e `docs/validacao/5.18.0/`.
