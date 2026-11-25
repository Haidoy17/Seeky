# Seeky para Windows (64 bits)

O mesmo Seeky do Android, no computador. A lógica partilhada (Bíblias, referências, estudo, jogo,
formato das notas, Word, IA, livros) é o código de `app/src/main/java` sem Android; os ecrãs são
JavaFX, em `desktop/src`.

## Instalar

1. Descarregue `entrega/windows/Seeky-Setup-<versão>-x64.exe` (cerca de 66 MB, já traz o Java).
2. Abra-o. O Windows pode mostrar **"O Windows protegeu o seu PC"** porque o instalador ainda não tem
   assinatura de programador: toque em **Mais informações → Executar mesmo assim**.
3. Não precisa de ser administrador: o Seeky instala-se em `%LOCALAPPDATA%\Programs\Seeky`, com atalhos
   no menu Iniciar e no ambiente de trabalho. Desinstala-se em Definições do Windows → Aplicações.

Os dados (Bíblias importadas, notas, livros, estudo, recordes) ficam em `%APPDATA%\Seeky` e não se
perdem ao actualizar nem ao desinstalar.

## O que já tem

- **Bíblia**: importar MyBible (.SQLite3), USX, USFM, CSV/TSV, texto e EPUB; ler por capítulo com os
  **títulos de secção** do MyBible; **comentários** MyBible (.commentaries.SQLite3) com ✎ junto aos
  versículos; procurar passagens (João 3:16, Salmo 23) e palavras; copiar, ouvir, decorar, criar nota.
- **Estudo bíblico**: planos de leitura, leitura de hoje, sequência de dias, semana, decorar versículos
  com a mesma repetição espaçada do Android.
- **Seeky IA**: perguntar a vários documentos (até 12, com fontes), resumir um livro inteiro num resumo
  geral (retoma onde parou) e plano de estudo a partir de um tema (disciplina, sessões, cartões, nota).
- **Estudos**: Hoje, Disciplinas (cartões, perguntas, materiais, tarefas, notas), Cartões com revisão
  espaçada, Perguntas, Agenda, Progresso e Seeky IA para criar cartões e perguntas; sincroniza com o
  telemóvel.
- **Jogo da Bíblia**: Fácil, Médio, Difícil e Lendário, 60 s por pergunta, recordes.
- **Bloco de notas**: o mesmo formato do Android (títulos, negrito, itálico, sublinhado, riscado, destaque,
  cores, listas, tarefas, citações, tabelas, imagens), pré-visualização, passagens tocáveis, exportar
  Word (.docx), PDF e Markdown, ouvir, pastas, fixar e **Seeky IA** (Gemini, chave protegida pelo Windows).
- **Livros**: Estante, Grelha ou Lista, com capas; importar livros ou uma pasta com as subpastas; PDF
  (página a página, zoom), EPUB, Word, FB2, MOBI, AZW3, ODT, RTF, MHTML, CBZ e TXT, com o ponto onde parou;
  **marcações** com cores e notas, páginas marcadas nos PDF e *As minhas marcações*; **Seeky IA**.
- **Foco**: Pomodoro por disciplina (25 + 5, 50 + 10, 15 + 3), visível na barra da janela, com o tempo
  de cada dia e de cada disciplina na semana.
- **Notas do telemóvel**: Bloco de notas → Telemóvel → importar ou enviar um ficheiro `.seekynotes`
  (o Android tem o mesmo em Bloco de notas → Pastas).
- **Telemóvel (Seeky ligado)**: liga o telemóvel com um código de 6 números; notas, leitura, estudo
  bíblico e música ficam iguais nos dois; ficheiros nos dois sentidos (Transferências\Seeky). Na primeira
  vez o Windows pode perguntar se permite o Seeky na rede: escolha redes privadas.
- **Botão Flash**: o botão de pesquisa por cima das outras janelas, como no telemóvel (Flash, Completa,
  Pastas; Tudo, PDF, Documentos, Imagens, Vídeos, Áudio); fica com a janela fechada (ícone junto ao
  relógio), pode iniciar com o Windows e abre com Ctrl+K.
- **Aspecto**: janela própria (minimizar, maximizar, fechar, redimensionar), os temas do telemóvel e
  Horário, transições e deslocamento suave.
- **Ficheiros**: discos e pastas do utilizador, procurar por nome, abrir no Seeky ou no Windows, copiar,
  mover, mudar o nome, Reciclagem, nova pasta.
- **Música** e **Vídeos**, como no telemóvel: pastas adicionadas, Início (adicionadas recentemente, mais
  ouvidas hoje/semana/mês/sempre, ouvidas recentemente, favoritas), Faixas, Álbuns, Artistas, Pastas,
  Géneros e Anos; fila e letras sincronizadas (.lrc ou LRCLIB). Toca MP3, M4A/AAC, WAV, AIFF e MP4
  (H.264); os outros vídeos abrem no leitor do Windows.
- **Leitura em voz alta** com as vozes do Windows.

## Actualizações para os dois

Cada versão do Seeky sai para Android e Windows com o **mesmo número** (o de `app/build.gradle`):

1. Subir `versionCode`/`versionName` em `app/build.gradle` (como sempre).
2. Gerar o APK assinado e publicá-lo em `entrega/` (como sempre).
3. `python3 tools/build_desktop.py --package --notes "…"` gera `entrega/windows/latest.json` e o pacote de
   actualização `Seeky-Desktop-<versão>-app.zip` (≈ 9 MB); o instalador fica em `build/desktop` e só é
   publicado em `entrega/windows` com `--installer` (quando o Java, o `Seeky.exe` ou o instalador mudam).
4. O Seeky para Windows lê `entrega/windows/latest.json` no `main` (no arranque, no máximo a cada 6 horas,
   ou em Definições → Procurar actualizações), descarrega só o pacote da aplicação, verifica o SHA-256 e
   instala-o em `%LOCALAPPDATA%\Seeky\app\<versão>`; o `Seeky.exe` passa a usá-lo no arranque seguinte.
   O Java não é descarregado outra vez.

O instalador completo só precisa de ser trocado em `entrega/windows` quando mudar o Java, o `Seeky.exe`
ou o instalador; quem instalar um instalador mais antigo recebe logo a versão nova pela actualização.

## Compilar (Linux)

Precisa de Java 21, `xvfb-run` (capturas), `mingw-w64` e `nsis` (pacote Windows):

```
python3 tools/build_desktop.py                      # compila e corre o autoteste
python3 tools/build_desktop.py --screenshots pasta  # + capturas dos ecrãs
python3 tools/build_desktop.py --package            # + Seeky.exe, instalador e pacote de actualização
```

As bibliotecas vêm do Maven Central e o Java para Windows da Azul (Zulu 21 com JavaFX), todos fixados por
SHA-256 no script. O fluxo `.github/workflows/windows.yml` compila no Linux e, numa máquina Windows
verdadeira, instala o instalador sem perguntas, aplica o pacote de actualização como o Seeky faz, corre
`Seeky.exe --self-test`, tira capturas dos ecrãs e desinstala.

## Componentes de terceiros do Seeky para Windows

- Azul Zulu 21 (OpenJDK) com OpenJFX 21 — GPLv2 com Classpath Exception (em `runtime\` no instalador).
- Apache PDFBox e FontBox 2.0.32, Apache Commons Logging 1.2 — Apache 2.0.
- SQLite JDBC 3.46.1.3 (Xerial) — Apache 2.0; SQLite — domínio público.
- SLF4J API 1.7.36 — MIT. JSON-java 20240303 — domínio público.
- Apache Commons Compress/IO/Codec/Lang3 e XZ for Java — os mesmos do Android (`app/libs`).
- Instalador feito com NSIS (zlib/libpng).
