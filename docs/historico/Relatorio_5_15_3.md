# Seeky 5.15.3

O leitor PDF mostrava uma imagem base limitada a 600 mil píxeis e acrescentava um recorte detalhado da área visível. Cada chamada a apply() apagava imediatamente esse recorte. A criação de outro detalhe era adiada 180 ms após o último movimento, e qualquer resultado cujo número de movimento já tivesse mudado era rejeitado. Assim, mesmo pequenos movimentos do dedo podiam fazer o texto regressar à prévia e só recuperar a qualidade ao parar.

A actualização conserva a imagem detalhada e a transformação da página usada para a produzir. Ao mover ou ampliar a página, aplica a transformação relativa ao recorte existente. A imagem segue a posição correcta do documento e é recortada aos limites da página. Um fundo branco no recorte substitui os píxeis da prévia nessa região, evitando a sobreposição dos traços de menor resolução com os traços detalhados. A transformação de cor do tema de leitura é mantida.

Os pedidos deixam de esperar pelo fim de todos os movimentos. O primeiro pedido aguarda 48 ms e, enquanto é executado, os novos movimentos apenas actualizam a posição pretendida. Existe no máximo um trabalho de detalhe em curso por vista. Um resultado da mesma página pode ser usado mesmo que o dedo tenha avançado: a sua transformação permite reposicioná-lo. Se ainda houver uma posição mais recente, é agendado outro recorte. Ao mudar de página, uma geração independente invalida os detalhes anteriores, incluindo os que terminam atrasados.

Movimentos ou medições que não alteram a matriz nem as dimensões não provocam substituição do detalhe. Se a renderização falhar, o último recorte válido permanece visível e não há um ciclo de tentativas sem movimento novo.

O orçamento anterior de 350 mil ou 900 mil píxeis por detalhe é conservado, segundo a memória disponível. Uma margem de até 96 dp usa somente os píxeis que sobram depois de reservar a área visível, para antecipar pequenos arrastos sem reduzir a resolução da área visível para obter essa margem. A área total permanece limitada; a imagem anterior e a nova podem coexistir durante a substituição. Regiões que ainda não foram renderizadas usam a prévia enquanto o detalhe seguinte termina. A correcção elimina a degradação artificial de toda a área já detalhada; não transforma imagens de origem de baixa resolução em texto vectorial.

## Validação

- Conjunto completo de 286 testes Android/Robolectric e verificações do núcleo.
- Nove testes novos com Canvas nativo e um duplo de PDF que respeita a matriz de renderização.
- Toque sem movimento: a imagem detalhada e os píxeis desenhados permanecem iguais.
- Arrasto real enviado à lista PDF: detalhe mantido, marcador na posição correcta e posição antiga limpa, sem imagem fantasma.
- Zoom: detalhe alinhado antes e depois da substituição.
- Actualizações durante um gesto ainda activo, antes de levantar o dedo.
- Renderização bloqueada para simular um dispositivo lento, seguida de vinte movimentos: resultado reaproveitado na posição correcta e um pedido posterior agendado.
- Falha de renderização simulada: último detalhe mantido, sem repetição infinita.
- Mudança de página, incluindo uma renderização anterior que termina atrasada: a imagem da página anterior não aparece na nova.
- Janela grande: detalhe limitado a 900 mil píxeis / 3,6 MB na configuração de teste.
- Regressões anteriores de rotação, zoom, rolagem, selecção, narração, importação bíblica, temas e biblioteca continuam incluídas.
- Identidade instalada mz.bam.seeky.v51, nome Seeky, versão 5.15.3 / 5003900, assinatura de actualização anterior e alinhamento de 16 KB verificados no APK.

A prévia é uma captura nativa durante o arrasto de um documento sintético. O PDFium é um duplo de teste no Robolectric; a extracção de texto PDF é validada separadamente pelo conjunto do núcleo. Não foi possível medir fluidez ou consumo no ITEL físico. O módulo de vozes e a exportação WAV não foram alterados.

Os registos incluem erros deliberados dos testes de recuperação; o resultado global encontra-se no final.

Haidoy Carlos Mautempo
