# Seeky 5.9.2 — Ajustes do EPUB

Assinatura: **Haidoy Carlos Mautempo**.

Instale `Seeky_5_9_2_EPUB_Ajustado.apk` sobre a versão anterior, sem desinstalar. Mantém o pacote e o certificado das entregas independentes anteriores.

## Ler sem corte lateral

Os EPUBs abrem com a apresentação ajustada à largura do ecrã. A aplicação limita os blocos, imagens e tabelas à área de leitura e permite quebrar linhas muito largas. Mantém o HTML do livro, os títulos, as imagens, o negrito, o itálico e as cores; não converte o EPUB automaticamente em texto simples nem regrava o ficheiro original.

## Ajustar a apresentação

No leitor, toque em **Texto** ou em **Organizar → Ajustar leitura**. Estão disponíveis:

- **Adaptar à largura do ecrã**: activado por defeito; desactive para consultar a geometria definida pelo livro.
- **Tamanho do texto**: de 75% a 175%, mantendo a proporção entre títulos e parágrafos.
- **Margens**: espaço à esquerda e à direita. Alterar as margens activa a adaptação à largura.
- **Espaçamento**: pode manter o do livro ou escolher um valor entre 100% e 220%.
- **Alinhamento**: Do livro, À esquerda ou Justificado.
- **Repor ajustes deste livro**: volta ao tamanho 100%, margens de leitura e espaçamento/alinhamento do livro.

As preferências ficam guardadas por livro. Ao reorganizar a página, o Seeky tenta conservar a posição proporcional dentro do capítulo; o ponto exacto pode variar com o novo tamanho e a nova quebra de linhas.

Em **Visualizar → Ajustar à largura**, pode recuperar o enquadramento depois de ampliar com os dedos. Quando há ampliação ou conteúdo horizontal para deslocar, o gesto horizontal permite mover a página em vez de avançar acidentalmente.

**Texto fluido para anotar** continua disponível como escolha explícita. Os ajustes acima funcionam na apresentação EPUB, sem exigir essa mudança.

## Validação e limites

Passaram a suite local, 11 verificações específicas da adaptação EPUB e 53 testes no Android 13 simulado. Estes incluem os controlos reais de tamanho/margens/espaçamento/alinhamento, a reabertura com preferências guardadas, a reposição dos ajustes e a confirmação de que os bytes do EPUB original permanecem iguais.

Os testes utilizam livros de ensaio. Não foi feito teste físico no Itel nem com o EPUB concreto da captura. Livros com diagramação especial podem precisar de reduzir o tamanho ou consultar a geometria original. As melhorias de fundo e voz da 5.9.1 permanecem incluídas.
