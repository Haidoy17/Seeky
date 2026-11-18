# Revisão Seeky 5.10.5

Base 5.10.4; versão Android 5002505; assinatura visual Haidoy Carlos Mautempo.

UiPanel.primary passou a dar 12 dp entre Ler/Ouvir e 12 dp abaixo da linha. O menu do livro inclui Eliminar destacado no rodapé. ReaderSpeech aplica margens aos controlos e às amostras anterior/próxima. A confirmação de eliminação explicita o âmbito das referências partilhadas.

BookRemoval elimina em transacção os materiais correspondentes ao caminho/hash da capa seleccionada, conservando notas, cartões e perguntas e desligando apenas a referência ao material. Valida que a selecção ainda corresponde ao livro actual. Só apaga o ficheiro se for uma cópia privada directa e não houver outras referências, inclusive caminhos equivalentes. Não apaga originais exteriores. Pára a leitura por voz desse material.

A tabela técnica material_identity usa AUTOINCREMENT; é inicializada com o máximo ID activo e aloca novas identidades na transacção da importação. Também retém a marca anterior à exclusão. Evita reutilizar IDs de livros eliminados que ainda tenham anotações históricas no ReaderStore. As tabelas de dados e versões dos esquemas existentes permanecem, com este auxiliar adicional que não integra os backups de conteúdo. A importação de lotes e os dados existentes mantêm-se.

ReadAloudService antecipa QUEUE_ADD imediatamente após o pedido inicial e preenche a fila ao concluir cada trecho, cobrindo callbacks de início que cheguem antes da conclusão anterior. Mantém um único trecho adicional. O primeiro pedido de rede limita-se a 1200 caracteres; os pedidos seguintes mantêm o orçamento anterior. SpeechChunks evita separar abreviaturas e iniciais do nome seguinte. A normalização e mapeamento das posições mantêm-se.

Pré-extracção no worker do documento, uma página/capítulo à frente, com cache máxima de 64000 caracteres e verificação de pelo menos 16 MiB de margem no heap. A revisão dos Estudos/cadernos é conferida antes/depois da preparação e no consumo, invalidando alterações concorrentes. Erros de pré-extracção são tratados na transição efectiva; o progresso não é avançado antecipadamente. Nenhum modelo ou voz nova foi incluído e o motor confirmado nunca muda silenciosamente entre online/offline.

Testes novos: medidas reais dos espaços e menu Eliminar/cancelar/confirmar; exclusão de referências partilhadas e retenção de notas; originais exteriores, referências remanescentes e selecção alterada; persistência da identidade após reabrir; fila antecipada antes do primeiro callback, callback de início adiantado, pedidos online curtos, modo de rede preservado, cache pequena, invalidação por edição e descarte de capítulos grandes. Os testes do motor são determinísticos e não avaliam timbre ou qualidade audível.

Sem teste em hardware Itel, sem medição auditiva de vozes Google, sem chamadas reais a TTS de rede ou à IA. A fluidez acústica real e o tempo de rede continuam dependentes do motor, da ligação e do dispositivo; não se promete ausência absoluta de pausas.

Verificação final: **90 testes Android/API 33 simulados** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
