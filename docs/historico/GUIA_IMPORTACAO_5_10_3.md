# Seeky 5.10.3 — importação de livros

Assinatura: Haidoy Carlos Mautempo. Base: 5.10.2.

Instale Seeky_5_10_3_Importacao_Livros.apk sobre a versão anterior, sem desinstalar. Os livros, progresso, Estudos, narrações e configuração Gemini existentes permanecem.

## O que mudou

Foi removido o corte silencioso de 50 ficheiros na recepção da selecção, que afectava Livros e Estudos. Todos os documentos que o selector Android efectivamente entregar são agora processados, um de cada vez, sem carregar o conteúdo do lote inteiro na memória. Não existe limite por quantidade imposto neste fluxo do Seeky.

O progresso mostra seleccionados/processados e as contagens de importados, repetidos e falhados. Um ficheiro com erro não impede os seguintes. No resultado aparecem até dez falhas com nome e motivo; o relatório CSV completo inclui o resultado de cada ficheiro processado. Pode guardar o CSV no fim ou reabri-lo por Livros → opções → Relatório de importação. Os cinco relatórios mais recentes ficam guardados localmente; não contêm texto dos livros nem são enviados automaticamente.

Conteúdo já existente no mesmo destino é contado como repetido e não cria outra cópia. Cancelar mantém os livros concluídos. Para continuar um lote interrompido, volte a seleccionar os mesmos ficheiros: os já presentes serão reconhecidos pelo conteúdo, e os restantes serão importados. Não é retoma automática de uma fila em segundo plano.

## Lotes grandes

Mantenha o Seeky aberto e espaço livre suficiente até terminar. Fechar a aplicação ou a sua destruição pelo Android pode interromper o trabalho; as cópias já concluídas permanecem. O processamento é sequencial, com blocos de 64 KiB e progresso visual limitado a cinco actualizações por segundo. O número mostrado em seleccionados é o que o selector Android entregou; se esse número já for inferior ao que marcou, a limitação está no selector/provedor e pode dividir a selecção em lotes.

Mantém-se o limite de 128 MiB por ficheiro e os formatos aceites pela estante: PDF, EPUB, DOCX e texto. Um ficheiro sem acesso, vazio, acima desse limite, de formato não suportado ou sem espaço para a cópia é indicado como falha. Os originais nunca são removidos.

## Verificação

O ensaio inclui 1205 documentos de texto distintos e pequenos fornecidos por um ContentProvider simulado, repetição do mesmo lote sem duplicar registos, cancelamento/reselecção, erros de acesso e formato, itens inválidos e o fluxo visível de importação. Não representa um teste de 1205 livros grandes no Itel físico. O relatório técnico acompanha os resultados finais.
