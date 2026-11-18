# Revisão Seeky 5.10.3

Versão Android 5002503; base 5.10.2; assinatura visual Haidoy Carlos Mautempo.

Causa confirmada no código: StudyHub.imported parava de recolher URIs quando files.size() chegava a 50. Os restantes elementos do ClipData eram descartados sem aviso. Se sete dos primeiros cinquenta fossem repetidos ou inválidos, o resultado seria 43 importados; essa distribuição específica não foi confirmada nos ficheiros do utilizador.

StudyImporter.selected percorre todo o ClipData recebido, sem corte por quantidade. O motor comum a Livros e Estudos faz cópia privada com SHA-256, staging .part, validação de formato e tamanho e commit SQLite por documento. Usa o mecanismo StudyFiles existente com buffer de 64 KiB. Nenhum livro é aberto no leitor nem são geradas capas durante a cópia. As entradas URI e um máximo de dez erros ficam na memória; o CSV é escrito sequencialmente.

O progresso nativo tem total e contagem de processados, importados, repetidos e falhados. Publicações na UI são limitadas a uma tarefa pendente e intervalos de 200 ms. Erros por ficheiro não interrompem o lote; cancelamento pára e remove a cópia incompleta, conservando os commits anteriores. Reimportar salta conteúdos já existentes no mesmo destino sem substituir progresso/notas. Uma segunda importação simultânea é recusada com aviso.

O relatório inclui nomes e motivos, sem conteúdos dos documentos. Guarda cinco relatórios locais e exporta CSV para um destino escolhido pelo utilizador; exportação valida o caminho privado e neutraliza células que poderiam ser interpretadas como fórmulas. O esquema de Estudos não foi alterado. Gemini e correcções anteriores continuam incluídos; nenhuma chamada real a IA foi feita durante este trabalho.

Limites: 128 MiB por documento; o Android pode limitar a selecção antes de o Seeky recebê-la. A tarefa não é um serviço persistente: requer manter a aplicação aberta, e pára na destruição da Activity. A continuidade é feita voltando a seleccionar os ficheiros, com deduplicação dos já concluídos. Não se promete retoma automática após encerramento ou teste de desempenho no Itel.

Verificação final: **70 testes Android/API 33 simulados** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
