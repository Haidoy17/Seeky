# Seeky — verificação e melhorias 14.1

Data: 27 de Outubro de 2026. Pacote `mz.bam.seeky.estudos`, versão `5.0.0-alpha.15.android.14.1-revisado`, código `5001515`. Android mínimo 8 / API 26; compilação e alvo API 36. Os esquemas das bases permanecem iguais.

## Resultado

O APK original passou a verificação de integridade ZIP e assinatura Android v2/v3. O seu SHA-256 coincide com o `release.json` recebido: `264ecdfd3ed128d0179f421bba017bfb35d943913751a1cc05f6850fd5cee793`. Isso confirma a integridade e a assinatura do ficheiro analisado; não comprova ausência de malware ou funcionamento em todos os aparelhos.

Foram reproduzidas três falhas nas fontes originais e aplicadas as seguintes correções:

| Problema | Comportamento na revisão |
| --- | --- |
| A importação de um ficheiro vazio ignorava o cancelamento; um cancelamento recebido no fim da leitura também podia publicar o ficheiro. | O cancelamento é verificado antes de começar, antes/depois de cada leitura e antes da publicação. O ficheiro parcial é removido quando a operação não termina. |
| Um caminho `.part` com ligação simbólica quebrada podia passar por livre; a abertura podia escrever no destino da ligação. | O parcial é reservado com `CREATE_NEW`; destinos e parciais já existentes são recusados, incluindo ligações quebradas. A limpeza só remove o parcial criado por esta operação. A escrita é sincronizada antes de publicar. |
| Ao mudar a referência de um documento, `INSERT OR IGNORE` seguido de `DELETE` descartava o caderno da origem se o destino já tivesse a mesma identidade. | A mudança detecta o conflito e conserva os dois textos, os marcadores, o histórico e os recortes nas referências existentes. Uma mudança sem conflito actualiza tudo na mesma transação. Uma mudança para a própria referência não altera dados. |
| O aviso de falha ao acompanhar cadernos ficava guardado sem ser apresentado. | Um botão de aviso aparece no ecrã principal da aplicação e dá acesso à exportação dos cadernos. O aviso só é removido quando o utilizador o marca como revisto. |
| A cópia para a lixeira de Estudos era gravada antes da transação que eliminava o registo. Uma falha podia deixar o original activo e uma cópia restaurável duplicada. | A cópia recuperável, a desvinculação de revisões de cartões e a eliminação são confirmadas ou revertidas juntas. Operações que já têm uma transação conservam o controlo dessa transação. |

Os conflitos de cadernos não são fundidos automaticamente: o ficheiro pode já ter sido movido/renomeado, enquanto os cadernos permanecem nas referências anteriores. A exportação JSON preserva as duas referências para revisão. Esta correção evita descartar texto; não implementa uma interface de fusão ou relocalização manual de cadernos.

## Validação executada

- Suíte local existente executada antes das alterações e novamente após as correções: Java, operações com ficheiros, parser de livros/PDF, pesquisa e contratos SQLite. A execução final terminou com `ALL CHECKS COMPLETED`.
- 23 verificações de importação de materiais, incluindo 5 casos novos: cancelamento de ficheiro vazio, cancelamento no EOF, parcial ocupado, ligação quebrada no parcial e ligação quebrada no destino.
- 40 verificações existentes do código real de ReaderStore/NotebookBackup/NotebookNotes sobre SQLite JDBC passaram.
- 17 verificações novas executam os métodos reais de ReaderStore/StudyStore sobre SQLite JDBC: colisão de cadernos, mudança para a própria referência, undo após mudança, falha numa actualização posterior, eliminação de notas/cartões, rollback e transações iniciadas pelo chamador.
- As regressões de cancelamento, colisão de cadernos e duplicação da lixeira falharam nas fontes originais, conforme esperado. As fontes corrigidas passaram.
- 27 verificações existentes de conta/retoma de backup passaram com contratos locais de transporte. Nenhuma chamada autenticada ao serviço remoto foi feita.
- Compilação completa dos recursos, Java e DEX com SDK/Build Tools 36 e ECJ 3.41.0. CRC, alinhamento de 4 bytes, pacote, versão e APIs do APK revisado foram conferidos.

O teste antigo em Python que extraía a instrução `INSERT OR IGNORE` foi retirado da parte de relocalização; os testes da operação real em Java/JDBC cobrem agora esse comportamento. Os restantes testes de esquema e persistência foram mantidos.

Os contratos JDBC não reproduzem o pool SQLite, o ciclo de vida, o armazenamento, as permissões, a UI nem o Keystore do Android. O contrato de StudyState usado nesses testes cria apenas as tabelas auxiliares de prática/lixeira; o sincronismo remoto e os triggers de revisão não são cobertos pelos 17 testes novos.

## Assinatura e instalação

O certificado original é `CN=Seeky Android Test, OU=Development, O=BAM, C=MZ`, RSA 3072, SHA-256 `0c88e3b4ebaabd0098dd948d00dd9fb3e2f17d1712ee4f1b89f8b901519040c1`. A chave privada correspondente não foi fornecida.

O ficheiro `Seeky_Android_14_1_Revisado_sem_assinatura.apk` foi compilado e alinhado, mas **não pode ser instalado enquanto não for assinado**. Para actualizar uma instalação existente, assine com a chave original. Uma chave nova não permite actualizar por cima da instalação antiga. Não desinstale a aplicação para contornar isso sem cópias externas verificadas dos seus dados.

Foi acrescentada a opção `--unsigned` ao script de compilação. O modo de assinatura externa continua disponível; nenhuma chave privada foi criada ou incluída nesta entrega. Consulte README.md para os comandos.

## Verificações e melhorias ainda necessárias

1. Testar a actualização assinada por cima da instalação existente em Android 8/9 e num Android recente/Itel A70. Exportar Estudos e cadernos antes do ensaio.
2. Ensaiar os novos cancelamentos e avisos pela interface, rotação, morte do processo, pouco espaço, SAF, cartão/USB removível e revogação de permissões. Testar fonte grande e TalkBack.
3. O restauro ZIP de Estudos é aditivo e separado do JSON dos cadernos. A revisão estática identificou que os IDs dentro dos payloads da lixeira e da prática pausada não são remapeados como as relações das tabelas principais. Esse fluxo precisa de correção e testes próprios antes de importar esses itens para uma biblioteca já preenchida; não foi alterado nesta revisão. A importação repetida de Estudos também não deve ser tratada como deduplicação global.
4. Os manifestos ZIP de Estudos ainda usam parsing JSON sem o mesmo pré-limite estrutural aplicado ao JSON dos cadernos. O limite de bytes existe, mas a defesa contra estruturas excessivamente profundas merece extensão e testes.
5. O alvo é API 36 e o manifesto pede acesso geral aos ficheiros, sobreposição e serviço especial em primeiro plano. Essas permissões correspondem às funções do gestor/bolha, mas o comportamento e a elegibilidade nas lojas precisam de validação própria. A versão do plugin Gradle recebida é 8.7.3; este build usou o script offline, não Gradle/Android Studio.
6. Acesso Google/Facebook, permissões por utilizador no servidor, Storage real e recuperação entre dois telemóveis não foram revalidados. Não houve alteração do backend nem de credenciais.

As permissões `allowBackup=false`, `usesCleartextTraffic=false` e os providers não exportados continuam presentes. Esta revisão não é uma auditoria completa de segurança, acessibilidade ou desempenho.

## Evidências

Os ficheiros `evidence/revisao14_1_*` são desta revisão. As outras evidências e os documentos das edições 9–14 são históricos. `release_14_referencia.json` conserva os metadados da edição recebida; `release.json` descreve a revisão actual.
