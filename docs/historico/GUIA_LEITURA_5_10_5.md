# Seeky 5.10.5 — menu dos livros e leitura por voz

Assinatura: Haidoy Carlos Mautempo. Instale Seeky_5_10_5_Leitura_Fluida.apk sobre a versão anterior, sem desinstalar.

## Livros

O menu da capa separa Ler e Ouvir com espaço horizontal e deixa uma margem antes de Marcar como favorito. A opção **Eliminar** fica destacada no fim e pede confirmação.

Uma capa pode representar um ficheiro partilhado por vários módulos após a limpeza de duplicados. Eliminar essa capa remove os respectivos registos e ligações ao livro. As notas, cartões e perguntas dos Estudos ficam conservados, sem ligação ao material eliminado. O ficheiro privado só é apagado se já não tiver referências; ficheiros originais fora da pasta privada nunca são apagados. Anotações históricas do leitor permanecem na sua base, mas não constituem uma cópia do livro e não são ligadas automaticamente a uma futura importação.

Foi acrescentado um registo técnico de identidades de materiais para impedir que um livro novo receba o ID de outro eliminado e herde indevidamente as posições/marcadores antigos. Esse registo não muda as identidades dos livros já existentes.

## Ouvir com menos interrupções

A leitura começa a preparar o próximo trecho assim que entrega o primeiro ao motor, sem esperar pelo início do som. A fila mantém apenas um trecho antecipado, e volta a preenchê-lo na transição mesmo se os eventos de início/fim chegarem fora da ordem esperada. Pausar descarta a fila antecipada e mantém a posição no texto original.

No modo online, o primeiro pedido é mais curto, até 1200 caracteres, para reduzir o trabalho inicial; os seguintes conservam maior contexto. A separação por frases evita cortar imediatamente depois de abreviaturas como Dr., Prof. e iniciais. Não são alteradas as palavras do livro.

Quando há memória disponível, o próximo capítulo/página pequeno é preparado em segundo plano. Só fica guardado um texto de até 64000 caracteres. Uma alteração nos Estudos/cadernos invalida o texto preparado; capítulos maiores são lidos normalmente sem permanecer nessa cache. A preparação não avança o progresso de leitura.

Em **Vozes e narração**, o atalho Google online · melhor voz continua a seleccionar uma voz de rede disponível com a qualidade indicada pelo motor e velocidade neutra de 1×. Use Ouvir amostra para comparar. Os controlos e amostras também receberam espaçamento. A app não adiciona uma nova voz comercial ou uma API de TTS; a naturalidade depende das vozes Google disponíveis, da língua e da ligação. Não há garantia de ausência de pausas em rede instável.

## Verificação

Os testes exercitam a interface e os eventos TTS com motores simulados, além de cache, notas, exclusão e importações. Não foi feita avaliação auditiva das vozes Google nem ensaio no Itel físico. O relatório técnico inclui os resultados finais.
