# Seeky 5.16.0

A revisão da interface e do módulo de Estudos identificou quatro problemas concretos: as categorias ainda tinham cartões pesados para o modelo fornecido; a navegação de Estudos dispersava o próximo passo; as revisões perdiam a fila ao sair; e as descrições passadas a actionCard() não chegavam a ser adicionadas à vista. Foi ainda corrigido um rascunho de teste aberto que podia ser gravado depois da conclusão.

## Categorias

HomeCategories usa ícones vectoriais nativos coloridos, com transparência nos cantos e nomes centrados. Os formatos e as contagens reais são mantidos. Arquivos e Aplicações aparecem como ZIPs e APKs; continuam a encaminhar para as categorias internas correspondentes. Os cartões perderam o preenchimento e a elevação, conservando o feedback de toque e as descrições de acessibilidade.

A grelha normal usa quatro colunas quando existe largura suficiente e a fonte permite; usa três num ecrã estreito ou com letra maior, e seis em ecrãs grandes. Os seis modos de Exibir continuam disponíveis, assim como os três tamanhos de ícones. Os desenhos não usam bitmaps novos nem bibliotecas externas.

## Hoje nos Estudos

O painel Hoje apresenta cartões devidos, número de cartões distintos revistos hoje, foco já registado face à meta diária, tarefas até ao fim do dia, leitura em curso e testes ou revisões em pausa. Um resumo aparece na entrada dos cursos. O botão de calendário e o separador Hoje dão acesso ao plano completo. Retirou-se o segundo acesso repetido às preferências; o controlo do cabeçalho continua disponível.

As consultas usam o curso ou disciplina actual. Os totais diários vêm de consultas agregadas, independentemente dos limites e paginação da interface. Assim, o painel não perde sessões por haver mais de mil no histórico. O foco conta apenas sessões Foco registadas; uma pausa não aumenta a meta. As datas usam a língua do aplicativo e os limites do dia seguem o calendário local. O histórico Progresso conserva o limite de mil sessões já indicado na interface.

As prioridades mostram até três tarefas com prazo até hoje, com acesso à agenda completa, conclusão e preparação de foco. As tarefas concluídas, futuras ou sem prazo não são tratadas como atrasadas. A leitura em curso abre o material sem enviar um capítulo inicial, conservando as posições que o leitor já guarda. Uma sessão de foco activa recebe o controlo de pausar/continuar em vez de tentar iniciar outra.

## Revisão com retoma

A revisão passa a oferecer filas de até 10, 20 ou 40 cartões devidos do filtro actual, com a resposta oculta até ser revelada. As avaliações mostram os intervalos calculados pelo agendador existente: por exemplo, Recordei pode indicar um dia e Fácil quatro dias numa primeira revisão. O resumo separa as quatro avaliações e explicita que são auto-avaliações, sem estimar domínio da matéria.

A fila guarda identificadores, data de alteração e uma impressão SHA-256 do conteúdo, sem duplicar respostas antigas. Ao retomar, lê os cartões actuais e ignora os que foram eliminados, pausados ou alterados. A fila é guardada antes de abrir e depois de cada avaliação. Um token estável por avaliação usa a idempotência já existente em reviews: se o processo parar entre gravar a avaliação e avançar a fila, a recuperação detecta a avaliação e avança sem voltar a alterar o intervalo.

O avanço só é mostrado depois de persistir a posição. O toque repetido nos botões de avaliação é bloqueado. Existe ligação ao material de origem e ao segmento guardado, mantendo a revisão em pausa. Criar uma fila nova exige escolher essa opção e mantém o histórico de avaliações.

A fila fica nas preferências locais de Estudos, incluídas na Cópia completa. A exportação portátil de Estudos mantém os cartões e o histórico como antes; não transporta a fila temporária entre instalações. Não há alterações de esquema das bases de dados nem migrações destrutivas.

## Outros ajustes

O construtor partilhado dos cartões de navegação ignorava o subtítulo recebido. Passa a mostrá-lo com duas linhas e elipse, permitindo identificar o livro de continuação e compreender as acções. Os controlos do cabeçalho de Estudos têm espaçamento explícito.

A gravação atrasada da resposta de um teste é cancelada ao avaliar e ao concluir, evitando recriar um teste em pausa depois de guardar o resultado final.

## Validação

- Conjunto completo de 306 testes Android/Robolectric e verificações do núcleo.
- Vinte novos testes de categorias e Estudos: adaptação a ecrã estreito, encaminhamento de ZIPs/APKs, ícones nativos, legendas, agregação de mais de mil sessões, âmbito por disciplina, contagem distinta de cartões, retoma da leitura, prioridades, conclusão de tarefas, foco activo, resposta oculta, limite da fila, intervalos reais, pausa/retoma, toque duplicado, recuperação de avaliação interrompida, cartões alterados/pausados/eliminados, origem do cartão, estado inválido e conclusão de teste sem ressurgimento do rascunho.
- O teste anterior dos seis modos foi actualizado para a grelha de quatro colunas solicitada; os restantes modos e os ensaios de ecrã estreito permanecem.
- Capturas nativas com dados sintéticos de categorias, Hoje e revisão.
- APK com nome instalado Seeky, identidade mz.bam.seeky.v51, versão 5.16.0 / 5004000, assinatura anterior e alinhamento de 16 KB.

Não houve teste num ITEL físico. Não foram adicionadas dependências, contas sociais ou serviços pagos. O motor de vozes e a exportação WAV mantêm-se. Os testes anteriores de PDF, nitidez, rotação, importações, cópias e narração continuam incluídos.

Os registos incluem erros deliberados dos testes de recuperação; o resultado global encontra-se no final.

Haidoy Carlos Mautempo
