# Registo de alterações

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
