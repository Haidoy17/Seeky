# Seeky 5.15.1

As categorias do Início usavam sempre três colunas, independentemente de Exibir. Agora HomeCategories interpreta os mesmos seis modos: Lista compacta, Detalhes com formatos, Conteúdos em duas colunas, Grelha em três, Blocos horizontais em duas e Ícones em disposição mais densa. Os nomes e quantidades ficam centrados. As dimensões são menores e adaptam-se ao tamanho escolhido e à escala da fonte. Quando ainda não existe uma preferência guardada, a apresentação inicial é Grelha; preferências existentes são respeitadas.

A composição anterior da biblioteca punha o cabeçalho e as acções no mesmo ScrollView dos livros, dentro de margens. Agora há uma área fixa a toda a largura para o cabeçalho, os três controlos, o título, a contagem/importação e o eventual filtro de pesquisa. Um segundo contentor contém apenas os livros e a paginação, com margens próprias. O botão de continuar leitura continua sobre a área dos livros. A posição de deslocação é reposta depois de existir uma área de conteúdo medida, para não perder a posição ao regressar à biblioteca.

Os botões superiores têm áreas de toque de 44 dp e espaçamento explícito. O título está numa linha própria, com espaço disponível, e não concorre com os três botões. Os testes medem a visibilidade completa dos controlos num ecrã de 288 dp e verificam que o cabeçalho não muda de posição ao deslocar os livros 480 dp.

O fundo anterior desenhava um cabeçalho decorativo atrás de outro cabeçalho, causando faixas e limites rectos. O fundo passou a tratar apenas a zona das barras do sistema e as curvas inferiores. Os cabeçalhos desenham as suas próprias curvas Bézier e camadas suaves. A biblioteca usa uma variante de curva que conserva espaço para o título e controlos. Não há imagens de ecrã incorporadas nem bibliotecas novas.

O resumo de armazenamento e o campo de pesquisa do Início foram compactados; a informação redundante do catálogo não ocupa uma linha adicional no cartão. O estado e as contagens reais continuam a ser actualizados pelos trabalhos existentes.

## Validação

- Conjunto completo de 263 testes Android/Robolectric e testes do núcleo.
- Seis testes novos: os seis modos aplicados pelo diálogo real Exibir; nomes e quantidades em Grelha e Ícones, nos três tamanhos, num ecrã de 288 dp; cabeçalho fixo durante deslocação real; largura total e controlos sem corte num ecrã estreito; reposição da deslocação; curvas distintas sem segundo cabeçalho no fundo.
- Capturas nativas em 288 e 360 dp, incluindo biblioteca com a lista já deslocada e todos os menus superiores visíveis.
- Identidade mz.bam.seeky.v51, nome instalado Seeky, versão 5.15.1 / 5003700, assinatura anterior e alinhamento de 16 KB verificados no APK.

Os dados e capas das prévias são sintéticos. Não houve teste num ITEL físico. O módulo de vozes, o formato WAV e as bibliotecas pessoais não foram alterados. As contas sociais continuam retiradas e as cópias locais permanecem disponíveis.

Os registos incluem falhas simuladas dos testes de recuperação da narração; o resultado global encontra-se no fim.

Haidoy Carlos Mautempo
