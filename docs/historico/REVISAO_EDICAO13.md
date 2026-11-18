# Seeky Android — revisão do diagnóstico e execução da primeira fase

25 de Outubro de 2026 · Edição 13 · BAM - SERVICE

A revisão recebida foi usada como lista de trabalho, confrontada com o código da edição 12. Foi produzida uma edição 13 de teste, mantendo a edição 12 localmente para comparação. Não foi declarada concluída a lista de 90 dias: algumas mudanças exigem arquitectura nova, credenciais dos fornecedores e testes em aparelhos reais.

## Correcções ao diagnóstico recebido

- `android_runtime_tested: false` significa ausência de teste físico/emulador nesta sessão. Não significa que o utilizador nunca tenha experimentado o Seeky: já tinha comunicado funcionamento positivo de uma edição anterior. Não se extrapola esse relato para a edição 13.
- `allowBackup=false` desactiva o Auto Backup do Android. Não desactiva o ZIP manual nem, quando a conta está operacional, o backup próprio do Supabase. Em 25/10, Google e Facebook continuavam desligados no endpoint real do projecto.
- Três versões de 100 MB somam cerca de 300 MB. Não esgotam, por si só, 1 GB. Já três versões de 512 MiB excedem 1 GB; vários utilizadores ou cópias pendentes também consomem o espaço partilhado do projecto.
- O limite de upload por objecto não é o mesmo que o tamanho total do snapshot. Os objectos desta implementação têm até 8 MiB. O custo de guardar e descarregar várias cópias completas continua relevante.
- O problema da assinatura é uma chave **privada** de teste distribuída no ZIP antigo. O certificado e a chave pública podem ser públicos; a chave privada de assinatura não. Remover o ficheiro do novo ZIP não invalida as cópias já distribuídas.
- `StudyHub.reload()` já encaminhava a leitura para um worker; não fazia toda essa consulta na thread principal. O problema confirmado era partilhar a fila com transferências e carregar corpos de várias secções sem necessidade. `StudyState.applyPrefs` no arranque continua um ponto a medir.
- A transferência existente é por ficheiro. A comparação com um movimento de pasta inteira não descreve uma função já implementada.
- O limite de 10 mil linhas/8 MiB de metadados do backup já produz erros explícitos; não foi confirmado como falha silenciosa. A importação manual repetida pode, contudo, duplicar registos e continua por corrigir.
- As suspeitas sobre WAL, fuga de memória nos diálogos e comportamento específico do fabricante precisam de reprodução. Não foram convertidas em bugs confirmados sem essa evidência.

## Alterações executadas

| Área | Edição 13 | Alcance e limite |
|---|---|---|
| Estudos | Executor próprio; dados carregados por secção; contagens gerais separadas | Não é ainda paginação completa nem refactorização dos ecrãs |
| Rascunhos | Uma fila de gravação, agrupamento, comparação do texto e eliminação de instâncias antigas da mesma nota | Um rascunho continua diferente de uma nota confirmada |
| Leitura | Gravação da posição agrupada em 1,5 s; descarga ao pausar; caches esvaziadas sob pressão de memória | Não implementa mosaicos nem reduz todo o texto de capítulos |
| Pesquisa rápida | Consulta indexada por URI | Retira o carregamento de 50 mil entradas desse caminho |
| Wildcards | Algoritmo sem regex para `*`/`?`; normalização reutiliza padrão compilado | Pesquisa SQL LIKE/FTS continua com a arquitectura anterior |
| Movimento | Publicação sem substituição por ligação física e remoção da origem, quando suportado | Android/FUSE pode recusar; nesse caso mantém cópia verificada. Não é rename atómico universal |
| Media | Notificação do scanner nas operações de ficheiros | Galeria e fabricantes precisam de ensaio físico |
| Duplicados | Escolher a cópia a conservar e rever envio das restantes para a lixeira | Hash parcial/cache e comparação repetida continuam pendentes; não elimina automaticamente |
| Serviço | Até 512 observações; reconciliação de 60 s; revisão de permissão de 15 s | Reduz actividade pedida; não prova autonomia de bateria e pode aumentar latência em pastas sem observador |
| CSV/exportação | Escape CSV partilhado; truncagem em exportações de Estudos | Validado em regras locais, não em todos os fornecedores SAF |
| Diagnóstico | Erros locais limitados a 50, só operação/classe da excepção; opção de copiar | Não substitui todos os catches nem é crash reporting remoto |
| PDF | Preparar pesquisa no conteúdo extrai e indexa texto a pedido | 32 MiB, 80 páginas, 262144 caracteres; orçamento verificado entre páginas; sem OCR |
| Backup | ZIP pendente persistido, mesma identidade, cursor de blocos, reconciliação de respostas perdidas e opção de descartar envio incompleto | Continua snapshot completo. Não reutiliza blocos entre versões nem faz merge |
| Agendamento | Job periódico persistente e idempotente; mudar rede actualiza a restrição | Momento efectivo depende do Android |
| Sessão | Falha de descodificação/cofre conserva a sessão cifrada e permite nova tentativa | Não elimina a necessidade de reautenticar tokens inválidos |
| Protecção dos estudos | Lembrete discreto de exportação quando não há cópia registada ou passou uma semana | Sem notificações e sem presumir que ficheiros antigos não existem |
| SDK | APK compilado com compile/target 36 e min 26 | Ainda não passou revisão Play nem teste de insets num Android recente |
| Assinatura das fontes | Keystore e password removidos do projecto entregue; build exige assinatura externa | APK conserva identidade de teste. Rotação comercial está pendente |

## Segurança e backend que se mantêm

Não houve alteração destrutiva de esquema, de políticas ou dos dados remotos. Mantêm-se a validação do ZIP, verificação de hashes, cofre AES-GCM/Keystore, PKCE/state, bucket privado, isolamento por utilizador e publicação condicional do head.

A retoma não altera os objectos existentes: se uma resposta se perdeu e o objecto já existe, verifica tamanho/hash antes de continuar. O commit permanece condicionado à versão remota conhecida. Descartar um envio recusa apagar uma cópia que já é o head publicado.

A chave de teste antiga tem de ser substituída antes de distribuição comercial. Não se rodou a chave de forma silenciosa porque uma alteração sem plano pode impedir actualizações, exigir reinstalação e causar perda de dados se não houver exportação. Uma nova chave deve ser guardada fora das fontes, sob controlo do proprietário. O plano deverá incluir teste de lineage e dos dispositivos mínimos, além de Play App Signing se a distribuição usar a Play.

## Validação efectuada

A compilação offline com SDK 36 passou. O APK foi verificado por assinatura v2/v3 e integridade ZIP. O pacote e o certificado correspondem à linha Estudos da edição 12.

Passaram as suites locais JVM/SQLite/PDF. A retoma e o agendamento têm 27 verificações de falhas injectadas contra o CloudClient e CloudJobs reais, usando contratos locais de Android/servidor. Não são testes HTTP autenticados e não provam o ciclo de vida de um telefone. Os 3008 casos de blocos/wildcards são comparações automatizadas de regras; não devem ser confundidos com 3008 testes de interface.

Não se executou aparelho/emulador. OAuth, Storage autenticado, restauro com várias ligações Android, Doze, scanner da galeria e recuperação em dois telefones continuam por provar. Consulte VALIDACAO.md para os ambientes e os ficheiros de evidência.

## Próximas prioridades concretas

1. **Validar no Itel A70 e activar uma identidade real.** Instalar por cima, conferir os dados, medir abertura/pesquisa/gravação e activar Google com as credenciais do proprietário. Depois fazer envio e recuperação num segundo aparelho, incluindo interrupção de rede. Resultado: roteiro preenchido e falhas reproduzíveis, antes de mudar novamente o formato de backup.
2. **Fechar o ciclo de dados e custos.** Desenhar backup incremental endereçado por conteúdo com migração dos snapshots existentes, quotas, expiração de pendentes e limpeza pelo Storage API. Acrescentar eliminação de conta/dados com reautenticação e verificar requisitos de SMTP antes de oferecer OTP. O dono escolhe capacidade/plano; não foi feita subscrição paga nem ping para contornar pausas.
3. **Evoluir o estudo e o leitor após estabilizar.** Indexação PDF em fila persistente e incremental; OCR offline; exportação Anki; avaliação de FSRS/cloze; alerta de foco e lembretes sem pedir alarmes exactos desnecessários; áudio com MediaSession; PDF com índice, zoom e mosaicos. Implementar por módulos e medir memória em vez de acrescentar tudo à MainActivity.

Ficam também pendentes: idempotência do restauro manual, cache/hash parcial dos duplicados, política de expiração da lixeira, SQL de pesquisa sem grande OR de LIKE, externalização de strings/temas, divisão da MainActivity e validação R8. A opção de gestor de ficheiros e a opção Estudos devem ser apresentadas sem pedir permissões globais a quem usa apenas os materiais privados.

## Referências oficiais consultadas

- Android/Google Play, requisito de target API: https://developer.android.com/google/play/requirements/target-sdk
- Supabase, limites e planos: https://supabase.com/pricing
- Supabase, Google: https://supabase.com/docs/guides/auth/social-sign-in/auth-google
- Supabase, redireccionamento móvel: https://supabase.com/docs/guides/auth/native-mobile-deep-linking

Os requisitos de publicação e a capacidade do serviço devem ser reconfirmados antes do lançamento comercial. A edição 13 é uma primeira fase implementada e verificável, não uma certificação de produto final.
