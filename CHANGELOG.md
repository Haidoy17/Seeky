# Registo de alterações

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
