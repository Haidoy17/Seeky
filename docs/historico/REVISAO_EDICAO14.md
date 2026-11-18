# Seeky Android 14 — funções escolhidas e verificação

25/10/2026 · Versão 5.0.0-alpha.15 · Edição Android 14

Foram escolhidas alterações que protegem o caderno e reduzem erros de utilização. As funcionalidades já presentes não foram reconstruídas: pesquisa global do catálogo, indexação PDF a pedido, lixeira com verificação de conflitos, comparação SHA-256, isolamento do SVG e retoma do envio de backup mantêm a implementação da edição 13.

## Implementado nesta fase

| Sugestão | Resultado concreto |
|---|---|
| Undo/redo do caderno | Histórico SQLite por URI/parte/impressão do original; mantém texto e anotações antes/depois, com limites de retenção |
| Corte mais seguro | Cópia interna persistente na mesma transacção da remoção; uma falha reverte o corte e o histórico |
| Reposição protegida | Contagem de anotações afectadas, exportação antes de repor e reposição que também permite desfazer |
| Original vs cópia | Banner persistente, Ver original e comparação dos dois textos; sem escrita no documento original |
| Backup do caderno | Exportação/importação dos cadernos activos e marcadores, com formato/schema explícitos e importação sem substituir conflitos |
| Protecção da migração | Snapshot do leitor antes do esquema 1→2; falha de snapshot cancela a migração; recuperação local por parte com confirmação |
| Eliminação no fim | Acção movida para o fim da barra de selecção; Desfazer envio para lixeira após o lote |
| Pesquisa sem acentos | Opções de acentos/palavra inteira; resultados mapeados para as posições do texto original |
| Pesquisa de anotações | Notas, destaques, sublinhados e marcadores incluídos opcionalmente; tipos identificados nos resultados |
| Painel do documento | Filtros de anotações; verificação da impressão antes de localizar anotações de outra extracção |
| Exportação de anotações | TXT/HTML com passagens completas e notas; preparação antes de escolher o destino; HTML escapado |
| Página PDF como imagem | PNG da página original inteira, com tamanho limitado, separado do recorte visível |
| Apresentação acessível | Saltar e respeito por animações desactivadas/poupança de energia; descrições nos controlos do leitor |
| Privacidade do relatório | CSV de actividade redigido por escolha, omitindo nomes e detalhes que possam conter caminhos |

O texto da UI e o guia esclarecem que inserir usa o fim da selecção ou do texto; não é um editor de PDF/Word. O timeout de 45 segundos é um orçamento total verificado entre partes, não um intervalo entre partes. TXT continua limitado a 512 KiB; PDFs acima de 64 MiB não têm procura profunda por extracção neste leitor.

## O alcance do backup

Não foi criado um backup completo de todo o Seeky. O ZIP de Estudos existente cobre materiais privados e progresso; o novo JSON cobre os cadernos activos do leitor e marcadores de todos os documentos guardados. Não cobre documentos externos, lixeira, preferências, colecções, pesquisas, credenciais, histórico de desfazer ou recortes internos. A documentação e as confirmações apresentam este alcance.

A importação do JSON é conservadora: identidades já existentes não são substituídas e a repetição não as duplica. A recuperação de uma parte de um snapshot local é uma acção separada e explícita, com cópia do estado anterior e histórico quando já havia uma parte activa. A disponibilidade das referências depende dos mesmos documentos/URIs; não há relocalização automática.

A base do leitor passou ao esquema 2. A do catálogo e a dos Estudos mantêm os seus esquemas anteriores. A recuperação de Estudos limpa o histórico/recortes dos URIs de materiais privados que substitui, conservando os de outros documentos, para não oferecer desfazer de outro estado recuperado.

## Verificação efectuada

- Compilação Java/DEX e recursos com SDK 36; verificação de assinatura v2/v3, pacote, versão e CRC do APK.
- Suites existentes JVM/SQLite/PDF da edição anterior, repetidas sobre as novas fontes.
- 16 casos de pesquisa mapeada/limites JSON: acentos compostos e decompostos, emoji, fronteiras de palavra, limites e estruturas truncadas/profundas.
- 40 verificações executam ReaderStore, NotebookBackup e NotebookNotes reais sobre SQLite JDBC do host, com pequenos contratos Android. Incluem upgrade 1→2, falha do snapshot com manutenção do esquema antigo, corte/undo/redo após reabrir a BD, conflito de edição, limite do histórico, repetição da importação sem duplicados, conservação de conflitos, recuperação por parte e HTML escapado.
- Uma falha de escrita foi injectada com trigger SQLite: o corte e o histórico foram revertidos juntos. Foram rejeitadas oito cópias truncadas e uma referência inválida antes de aplicar a importação.
- 27 verificações anteriores do CloudClient/CloudJobs com contratos de transporte/Android continuam a passar. Não são HTTP autenticado nem OAuth real.
- Teste SQL local de recuperação confirma limpeza do histórico apenas para os URIs dos materiais dos Estudos, com rollback e conservação do histórico externo.

O SQLite JDBC é uma execução do código de persistência, mas não simula o ciclo de vida, o pool SQLite Android, o armazenamento/SAF de um OEM, o Keystore ou a UI. Não houve instalação num aparelho/emulador. Os testes físicos propostos na revisão recebida continuam necessários; não foram substituídos por uma contagem de testes locais.

Evidências: `evidence/validation14_local.txt`, `validation14_notebook_jdbc.txt`, `validation14_cloud_host.txt`, `apk14_signature.txt` e `apk14_package.txt`. Ficheiros com números de outras edições são históricos.

## Sugestões que ficam para outra fase

1. Backup/restauro de toda a configuração e dados do gestor, com snapshot global e recuperação transaccional; o novo snapshot do leitor não deve ser apresentado como solução global.
2. Copiar/mover pastas, conflitos e movimentos reversíveis. A solução precisa de journal de operações, tratamento de volumes removíveis e ensaios físicos antes de assumir funções destrutivas adicionais.
3. Transferências num serviço com pausa/retoma e notificações, com testes de cancelamento e de remoção de cartão/USB.
4. OCR offline, exportação integral/por intervalo de livros, áudio em segundo plano/TTS e bloqueio da aplicação.
5. Lixeira com retenção/restauro alternativo, filtros de actividade, colecções com relocalização e política de bolha por aplicação.
6. Auditoria de acessibilidade e desempenho em Android 8/9 e Android recente, incluindo Itel, fonte grande, rotação, TalkBack, Doze e ecrã desligado.

A chave comercial de assinatura e o ciclo real Google/Facebook/Storage/recuperação em dois telemóveis continuam etapas separadas. Não houve alteração de plano pago, credenciais ou backend remoto.

## Retorno e recomendação de teste

Exporte Estudos antes de actualizar e os cadernos depois de a migração ser concluída. Mantenha uma cópia externa dos documentos únicos. A edição 13 foi conservada para comparação, mas um APK com código inferior não pode simplesmente ser instalado por cima. O esquema 2 do leitor exige uma compilação de compatibilidade num eventual retorno. O rollback Android não foi ensaiado.

O primeiro teste deve confirmar os dados existentes, experimentar corte/undo/redo num documento descartável e usar a recuperação de uma parte. Depois, validar a importação repetida do JSON, procura sem acentos e lixeira/Desfazer com ficheiros de ensaio. Só após isso faz sentido avançar para transferências recursivas e um backup global.
