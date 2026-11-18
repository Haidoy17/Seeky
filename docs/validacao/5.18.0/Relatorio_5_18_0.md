# Seeky 5.18.0 — MyBible e USX

Esta edição acrescenta importação dos módulos bíblicos MyBible `.SQLite3` e de livros USX 2/3, individualmente, por pasta recursiva ou ZIP. O nome instalado permanece **Seeky**. A actualização usa a mesma identificação e certificado da versão 5.17.0 e conserva os dados existentes.

## Implementação

- `BibleMyBible` verifica o cabeçalho SQLite e a tabela de versículos, abre uma cópia privada em modo só de leitura e percorre um cursor. Reconhece nomes de livros e a numeração padrão MyBible, incluindo os livros deuterocanónicos suportados pelo catálogo existente. Remove marcação, Strong e notas do texto de consulta, conservando acentos. Não executa SQL recebido do módulo.
- `BibleUsx` usa SAX para evitar carregar uma Bíblia inteira na memória. Processa os marcos de capítulos/versículos USX 2/3, texto entre parágrafos e caracteres formatados; exclui notas, títulos e material auxiliar. Entidades externas e DTD são recusados. Partes 3a/3b são reunidas; intervalos combinados 3–4 mantêm o texto conjunto disponível nas duas referências.
- `BibleModuleWork` integra estes formatos nas tarefas existentes. A descoberta de pastas é persistida; cada livro USX tem uma transacção e checkpoint. Um checkpoint confirmado prevalece sobre um diário de tarefa atrasado. O módulo SQLite individual é confirmado numa transacção; uma interrupção anterior ao fim repete esse módulo, sem publicar uma tradução parcial.
- A publicação na biblioteca é atómica. A importação falhada preserva as traduções anteriores e os ficheiros originais. Os formatos TXT/CSV/TSV/USFM/EPUB/PDF/DOCX mantêm o fluxo existente.
- O menu Bíblias importadas tem acções distintas para ficheiro e pasta USX e explica por que `.search`, comentários, dicionários, planos, capas e configurações não são traduções bíblicas. A selecção de ficheiros aceita MIME genérico BIN.

## Limites e compatibilidade

MyBible exige `verses(book_number,chapter,verse,text)` com texto legível. Módulos comprimidos/cifrados, esquemas de outras aplicações, comentários, dicionários e planos não foram implementados. Seleccione uma única versão por tarefa. As traduções integrais não acompanham o Seeky.

Limites: cópia de módulo até 256 MB; cópias de pasta até 256 MB; até 1000 candidatos USX/XML; ZIP até 10000 entradas; cada livro USX de pasta/ZIP até 32 MB declarados; leitor XML limitado a 64 MB; até 200000 versículos/tradução e 16000 caracteres/versículo. ZIPs são lidos directamente, sem extrair caminhos para o armazenamento. A consulta de pastas usa a API DocumentsContract compatível com Android 26 ou superior.

A gravação continua em WAV. O módulo de voz 1.0.4, Flash, temas, estudo e leitores anteriores são preservados. RAR nativo e ZIP cifrado continuam fora do suporte integrado desta edição.

## Validação

**368 testes Android/Robolectric passaram**, incluindo 30 testes novos; a suite de testes host terminou com `ALL CHECKS COMPLETED`. Os resultados estão em `release.json`, `runtime-tests.log` e `host-tests.log`. A suite nova cobre SQLite real de ensaio, numeração e nomes, acentos, marcação, fontes originais inalteradas, módulo completo de 33000 versículos, USX 2/3, notas, intervalos, XML inválido e entidades externas, BIN, exclusão de índices/auxiliares, publicação atómica, checkpoints, retoma de ZIP e descoberta recursiva interrompida. Verifica também os selectores Android de pasta/ficheiro e o nome de tradução conservado em estado de rotação.

A suite completa inclui as regressões existentes dos leitores, contraste, layout, estudos, arquivos, Flash, TTS e importações. Android API 33 em Robolectric, com desenho nativo; PDFium usa um substituto determinístico nos testes. Não houve teste num ITEL físico nem nos módulos reais do utilizador, dos quais foram fornecidas apenas imagens.

Instale o APK sobre a aplicação actual, sem desinstalar. Créditos: **Haidoy Carlos Mautempo**.
