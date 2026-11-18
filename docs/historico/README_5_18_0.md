# Seeky 5.18.0

Aplicativo Android de ficheiros, livros, leitura e estudos. Nome instalado: **Seeky**. Crédito: **Haidoy Carlos Mautempo**.

Esta edição aplica a apresentação inspirada nas referências fornecidas: ondas e degradês nativos, cartões arredondados, biblioteca com capas e progresso, e tamanhos ajustáveis. O login social Google/Facebook foi retirado; cópia e recuperação locais continuam disponíveis.

A edição anterior acrescentou criação de ZIP e extracção de ZIP/7z/TAR/GZ/BZ2/XZ, tamanhos menores e recolha parcial opcional do Flash, transparência e temas personalizados com RGB. **RAR nativo e ZIP cifrado não foram integrados**; consulte o relatório para os limites e a validação.

## Importação bíblica MyBible e USX

Em Mais > Ferramentas > Bíblias importadas, use **Importar ficheiro bíblico** para módulos MyBible `.SQLite3` (mesmo quando apresentados como BIN), um livro `.usx`, XML USX ou um ZIP com uma versão. Use **Importar pasta USX** para uma versão completa, incluindo subpastas como `release/USX_1`.

Seleccione o módulo SQLite3 principal, sem `.search`. Comentários, dicionários e planos não são Bíblias de versículos; JPG, LDML, VRS e styles.xml são auxiliares. USX 2/3 e a tabela MyBible `verses(book_number,chapter,verse,text)` com texto não comprimido são suportados. Os ficheiros originais são conservados. Pastas/ZIP retomam por livro confirmado; módulos SQLite interrompidos antes do fim são repetidos. A tradução só fica visível após publicação completa.

Os testes usam módulos sintéticos, incluindo 33000 versículos; não houve teste num ITEL físico ou nos ficheiros reais mostrados nas imagens. Consulte o relatório da entrega para limites.

## Tamanhos

- Início: botões −/+ junto às categorias.
- Exibir ficheiros e Preferências: ícones e botão Flash.
- Flash: engrenagem no painel, com ajustes independentes para resultados e bolha.
- Livros: Organizar livros > Capas.

Os ajustes são guardados no aparelho. Os limites preservam uma área de toque utilizável.

## Compilação

`build_apk.py` compila o projecto com Android SDK 36, Build Tools 36 e JDK/ECJ. `build_delivery.py` aplica a identidade de entrega `mz.bam.seeky.v51` e o rótulo `Seeky`. A versão é 5.18.0 / 5004200. As chaves privadas não são distribuídas. O APK de instalação fornecido já está assinado com o certificado anterior, permitindo actualizar sem desinstalar.

O código e as dependências locais do aplicativo principal estão completos neste pacote. O módulo opcional Seeky Vozes Naturais 1.0.4 não mudou nesta edição. A narração mantém WAV.

## Verificação

`run_tests.py` executa os testes do núcleo. `tests/android_runtime/run.py` executa os testes Robolectric com dependências locais fornecidas nos parâmetros. `Layout5151Test` verifica modos, alinhamento, limites em 288 dp e cabeçalho fixo com deslocação real dos livros. `ReferenceDesign5150Test` cobre a apresentação, tamanhos, remoção dos acessos sociais e fecho do catálogo após os leitores terminarem. Dependências e dados dos testes não entram no APK.

Consulte `Relatorio_5_18_0.md`, `release.json` e os registos de validação para evidência e limites. As prévias utilizam dados sintéticos e não representam a biblioteca real do utilizador. Não houve teste num ITEL físico.

A pasta backend e os documentos de edições anteriores são referências históricas; esta edição não oferece o login social nem altera recursos remotos. Livros, estudos e ficheiros do utilizador não são apagados pela actualização.
