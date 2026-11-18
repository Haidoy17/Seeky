# Seeky Estudos — edição 11: conta e continuidade dos estudos

24 de Outubro de 2026 · 5.0.0-alpha.15.android.11-conta

## Estado do login e do backup online

**O serviço online ainda aguarda activação.** Foram implementados a tela de login, o fluxo real Google/Facebook, o cliente de backup e a base de dados/políticas do servidor. Nenhum projecto Supabase está ligado nesta sessão e não existem credenciais de provedores configuradas no APK. Portanto, esta entrega não afirma que já é possível autenticar uma conta real ou recuperar dados pela internet.

Pode testar as melhorias offline e a apresentação da conta. Para concluir a activação, instalar e ligar a integração Supabase à conta responsável pelo Seeky e configurar os clientes Google/Facebook no projecto. O código-fonte inclui **backend/ACTIVACAO.md**, com o SQL, os callbacks e o roteiro exacto. Não é necessário colocar segredos de administração no aplicativo.

## Instalação

Instale **Seeky_Android_11_Conta_Estudos.apk** por cima de **Seeky Estudos · Teste / edição 10**, sem a desinstalar. O pacote e o certificado são os mesmos; o espaço de estudos migra de schema 1 para 2. A aplicação aparece como **Seeky Estudos**. A edição 9, instalada à parte, permanece independente.

Conserve uma exportação ZIP local dos estudos que quiser preservar. A migração foi verificada localmente, mas esta edição ainda não foi executada no Itel pelo assistente.

## Melhorias disponíveis offline

- **Rascunhos de notas:** enquanto escreve, o texto é guardado após uma pequena pausa. Ao reabrir o mesmo formulário, aparece “Rascunho recuperado”. Guardar conclui a anotação; o rascunho não deve ser confundido com uma nota já concluída.
- **Testes em pausa:** na secção Perguntas, **Retomar teste interrompido** recupera perguntas, ordem, respostas já avaliadas e a resposta aberta parcialmente escrita. Um novo teste substitui o teste em pausa, conservando os resultados concluídos.
- **Itens eliminados dos estudos:** notas, cartões, perguntas e tarefas podem ser recuperados pelas opções de Estudos. A recuperação cria um registo activo; se a disciplina/material já não existir, conserva o conteúdo em Geral/sem ligação. A eliminação de cópias físicas de materiais continua separada.
- **Leitura:** página, capítulo, deslocamento de leitura e posição de áudio passam a ser guardados para integrar o estado recuperável. A fonte, tamanho do ecrã e paginação podem alterar a apresentação noutro aparelho.
- **Consistência de dados:** contador de revisão local, ligação de proprietário, retoma de resultado sem duplicação e restauração exacta dos registos para as cópias online.
- As funções da edição 10 permanecem: biblioteca offline, disciplinas, notas Cornell, cartões e revisão espaçada, perguntas/testes, tarefas, foco, progresso, leitores e exportações locais.

## Tela de conta

O ícone de pessoa no cabeçalho de Estudos e **Opções → Conta Google / Facebook e backup** abrem a nova tela. Na primeira abertura após a actualização, também é apresentada depois da introdução de 5 segundos.

- **Continuar com Google / Facebook:** com o serviço activado, abre o navegador seguro do sistema. O Seeky não pede a palavra-passe do provedor dentro do app.
- **Continuar sem conta:** mantém o estudo offline. Login e envio de materiais são opcionais.
- **Configuração do serviço:** é para o responsável pela aplicação introduzir a URL e a chave pública do projecto já configurado. No APK entregue, os valores estão vazios e o estado é indicado no ecrã.
- **Terminar sessão:** interrompe futuros backups automáticos e conserva o estudo local. Os dados locais ficam associados ao seu proprietário depois de uma cópia concluída; entrar com outra identidade não deve enviar esse espaço para a conta errada.

Google e Facebook podem representar identidades diferentes. No novo telemóvel, use o **mesmo método e a mesma conta** usados na cópia original, até ser configurada/verificada uma ligação de identidades no serviço.

## Depois da activação: guardar e recuperar

1. No telemóvel antigo, entrar e escolher **Guardar backup agora**. O envio inclui materiais importados, disciplinas, notas, cartões, revisões, perguntas, resultados, tarefas, sessões, teste em pausa, rascunhos, anotações/marcadores e posição de leitura.
2. Esperar pela mensagem de conclusão. O ecrã mostra estado e data; não considere uma cópia interrompida como concluída.
3. No novo telemóvel, entrar com a mesma identidade e escolher **Recuperar de outro telemóvel**.
4. Rever a data, aparelho e versão da cópia. Confirmar a recuperação. Os registos são substituídos pela versão escolhida **sem acrescentar duplicados**; não há fusão automática de dois espaços.
5. Confirmar os materiais, notas e posições. Uma sessão de foco em curso regressa **pausada**, para não contar como estudo o tempo decorrido durante a troca de aparelho.

Antes da substituição, o app conserva uma cópia local. **Recuperar estado local anterior** permite voltar ao estado desse telemóvel sem descarregar outra cópia. Essa recuperação local não altera a versão online e conserva o estado que está a substituir para permitir nova reversão.

Se dois telefones foram alterados separadamente, o app bloqueia a substituição silenciosa de uma versão remota mais recente. Conserve/exporte o trabalho local e reveja a cópia remota antes de decidir. A recuperação corresponde à **última cópia concluída escolhida**, não a alterações que ficaram exclusivamente no aparelho antigo.

## Automático, rede e limites

O backup automático só começa depois de o activar. Por defeito exige uma rede que o Android considere não medida, normalmente Wi-Fi. O intervalo mínimo é 15 minutos e o sistema pode adiar o trabalho por bateria, rede ou outras restrições. Não há garantia de envio instantâneo ao sair do app.

O armazenamento é privado por conta. Os ficheiros são divididos em blocos de 8 MiB, com verificação de integridade. O arquivo completo pode ter até 512 MiB e cada material até 128 MiB. As quotas e os custos dependem do projecto/serviço contratado. O app não promete espaço online ilimitado.

As três versões concluídas mais recentes ficam protegidas; o cliente tenta limpar cópias anteriores depois de concluir uma nova. Backups locais ZIP continuam disponíveis, inclusive quando não há serviço online activado. A importação ZIP manual continua a acrescentar registos; a recuperação da conta usa substituição exacta.

Só o espaço **Estudos** entra neste serviço. Não envia automaticamente todos os ficheiros do gestor, nem os dados da aplicação Seeky da edição 9.

## O que falta para declarar o processo funcional de ponta a ponta

- Ligar/configurar um projecto Supabase real e executar o schema.
- Registar e activar as aplicações Google e Facebook e os respectivos retornos.
- Testar autenticação, renovação de sessão e logout com contas reais.
- Fazer um backup no Itel e recuperar num segundo aparelho, conferindo materiais, notas, teste em pausa e leitura.
- Confirmar migração da edição 10, teclado, aparência, gestos, cancelamento, rede, quotas, conflito e retorno de navegador.

**Verificação local concluída:** 409 verificações — 325 JVM, 56 grupos SQLite, 3 PDF e 25 PostgreSQL em ambiente de ensaio — além da compilação, assinatura e integridade do APK. Esses testes não substituem OAuth real nem validação Android.


---

# Activar Google, Facebook e backup do Seeky — edição 11

## Estado desta entrega

O APK e a integração foram implementados; o SQL foi executado e testado num motor PostgreSQL local (WASM/PGlite). **Não existe um projecto Supabase ligado nesta sessão, nem aplicações Google/Facebook registadas para este APK. O login online ainda não está activado e o processo completo com contas reais não foi testado.** O APK mostra esse estado e permite continuar offline, sem inventar utilizadores ou backups.

Esta entrega usa `mz.bam.seeky.estudos`, com o mesmo certificado da edição 10. O serviço pode ser configurado sem recompilar o APK: apenas URL e chave pública são introduzidas no aplicativo. As credenciais privadas dos provedores permanecem no serviço de autenticação.

## 1. Ligar o serviço que vai alojar os dados

Instalar e ligar a integração **Supabase** na conta do ChatGPT permite continuar a configuração do projecto e da base. Isso ainda não aconteceu nesta sessão. Usar um projecto dedicado ao Seeky permite rever as políticas sem interferir com outras aplicações.

Se o responsável fizer a configuração directamente:

1. Criar/seleccionar o projecto Supabase da entidade responsável pelo Seeky.
2. Conservar a região, plano e quotas escolhidos pelo titular. Esta entrega não contrata serviços pagos nem garante armazenamento gratuito ilimitado.
3. Executar **backend/schema.sql** como proprietário no SQL Editor. O script cria cabeçalhos de revisão, metadados de backup, funções de publicação e bucket privado `seeky-private`, com limite de 8 MiB por bloco.
4. Confirmar as políticas RLS aplicadas às duas tabelas e ao bucket. Se o projecto já tiver políticas permissivas adicionais, devem ser revistas: políticas permissivas do PostgreSQL combinam-se e podem contrariar o isolamento pretendido.
5. Obter a URL `https://REF_DO_PROJECTO.supabase.co` e a chave **publishable**, ou a antiga chave **anon**. Não usar `service_role`/`sb_secret_` no APK.

O schema não deve ser instalado numa base PostgreSQL genérica sem os serviços Supabase Auth e Storage. Os schemas de testes locais apenas emulam os contratos SQL dessas partes, não implementam um servidor OAuth.

## 2. Registar o Google

No Google Cloud do responsável:

1. Configurar o consentimento OAuth com o nome, dados de contacto e domínio reais da aplicação.
2. Criar um cliente OAuth de aplicação Web para o fluxo através do Supabase.
3. Adicionar o callback exacto mostrado no painel Supabase, normalmente `https://REF_DO_PROJECTO.supabase.co/auth/v1/callback`.
4. Guardar o Client ID e Client Secret em **Supabase → Authentication → Providers → Google**, e activar o provedor.
5. Em modo de teste, autorizar as contas de teste necessárias. A disponibilização a outros utilizadores depende das exigências/aprovações do Google; não foi apresentada como concluída.

O fluxo desta edição abre o navegador e usa autorização por código com PKCE. Não pede a palavra-passe Google no Seeky nem incorpora o Client Secret no APK.

Referência oficial: https://supabase.com/docs/guides/auth/social-login/auth-google

## 3. Registar o Facebook

No Meta for Developers do responsável:

1. Criar/configurar a aplicação com Facebook Login, incluindo dados reais de contacto e URLs públicas exigidas pelo Meta (por exemplo, privacidade e instruções de eliminação).
2. Configurar o callback Supabase exacto como URI de redireccionamento OAuth válido.
3. Activar as permissões de identidade/email necessárias ao Supabase e os utilizadores de teste.
4. Guardar App ID e App Secret em **Supabase → Authentication → Providers → Facebook**, e activar o provedor.
5. O acesso público depende do estado e das aprovações da aplicação Meta. Nesta entrega não foram criadas essas credenciais nem obtidas aprovações.

Referência oficial: https://supabase.com/docs/guides/auth/social-login/auth-facebook

## 4. Autorizar o retorno ao Android

Em **Supabase → Authentication → URL Configuration**, adicionar à lista de redirect URLs:

`seekyestudos://auth/callback**`

O aplicativo valida scheme, host, caminho, nonce de estado, prazo e código. A expressão final permite ao servidor devolver o parâmetro aleatório `state` anexado ao redirect. O código só é trocado com o verificador PKCE guardado no mesmo aparelho; fragmentos com tokens e retornos sem estado correspondente são rejeitados.

PKCE oficial: https://supabase.com/docs/guides/auth/sessions/pkce-flow
Contrato REST Auth: https://github.com/supabase/auth/blob/master/openapi.yaml

## 5. Ligar o APK

No Seeky: **ícone Conta → Configuração do serviço**.

- Introduzir apenas a URL HTTPS do projecto e a chave pública publishable/anon.
- Alternativa de distribuição: preencher `app/src/main/assets/cloud.json` com os mesmos dois valores e recompilar.
- Os campos estão vazios no APK entregue, porque nenhum projecto real foi ligado. O ecrã informa que o serviço aguarda activação.
- O APK rejeita HTTP, hosts fora do formato de projecto Supabase, chaves secretas e JWT com papel diferente de anon.

Não é necessário transmitir palavras-passe Google/Facebook ao ChatGPT nem inseri-las no aplicativo. Os segredos dos clientes OAuth pertencem aos painéis dos provedores/Supabase.

## 6. Ensaiar em dois aparelhos antes de disponibilizar

1. No telefone A, actualizar a edição 10 sem desinstalar. Confirmar estudos existentes e fazer uma cópia ZIP local.
2. Entrar com uma conta Google de teste; negar uma tentativa e confirmar que não cria sessão. Repetir o ensaio de entrada com Facebook numa instalação de ensaio própria, sem misturar os dados de outra identidade.
3. Importar um pequeno material, criar uma nota, cartão, marcador, tarefa e iniciar um teste de prática. Deixar uma resposta aberta parcialmente escrita. Ajustar posição de leitura e criar um rascunho de anotação.
4. **Guardar backup agora** e confirmar a conclusão, data e revisão no servidor. Uma tentativa interrompida não deve alterar a última cópia concluída.
5. No telefone B, entrar com a mesma conta e o mesmo método. Recuperar a cópia escolhida. Confirmar materiais por hash, notas, revisões, resultados, teste em pausa, rascunho e posições de leitura. Uma sessão de foco interrompida deve regressar pausada.
6. Restaurar a mesma cópia novamente: não deve acrescentar duplicados.
7. Fazer uma alteração e backup em B. Alterar A ainda baseado na versão anterior: o envio deve ser recusado como conflito, mantendo os dados de A e a cópia de B.
8. Testar expiração/renovação de sessão, logout, rede indisponível, quotas, migração, retorno de navegador, revogação do provedor e Android JobScheduler.
9. Usar outra conta e confirmar que não consegue listar, ler, publicar ou recuperar cópias da primeira. A base SQL local testou esta separação de políticas, mas o ensaio real Auth/Storage ainda é obrigatório.
10. Testar as três versões recentes e a limpeza das antigas sem apagar a versão actual; usar a recuperação local anterior ao restauro para voltar ao estado do aparelho.

## Como o backup funciona

O arquivo contém materiais e os registos de estudo, mais anotações do leitor, preferências seleccionadas e posição de leitura. É dividido em blocos de até 8 MiB e guardado em `UID/UUID_DA_COPIA/00000.bin` no bucket privado. O limite da cópia é 512 MiB; materiais mantêm o limite de 128 MiB.

Cada bloco e o arquivo completo têm SHA-256. O servidor só publica o novo cabeçalho quando os blocos previstos existem com o tamanho esperado; o download verifica os hashes novamente. Um cabeçalho de revisão com compare-and-swap evita substituição silenciosa por um telefone desactualizado. A confirmação de um commit pode ser repetida sem duplicar a revisão.

A restauração valida e copia os materiais antes de alterar a base. Mantém os IDs para conservar as ligações, e usa uma transacção SQLite sobre as bases anexadas de estudos/leitor. As preferências ficam em fila durável para aplicar após o commit, podendo ser reaplicadas na próxima abertura. Uma cópia local anterior à recuperação é conservada com um botão próprio para recuperar.

São backups por versão, **não edição colaborativa nem fusão automática de dois telefones**. Recupera a última cópia concluída escolhida, não alterações que nunca chegaram ao servidor. Quando surge conflito, conservar/exportar o trabalho local e rever a versão remota antes de decidir.

O automático depende de opt-in. Usa rede não medida por defeito e intervalo mínimo de 15 minutos, sujeito às decisões de rede/bateria do Android. Falhas transitórias têm reagendamento. As três versões concluídas mais recentes ficam protegidas contra eliminação pelo cliente. Após um backup concluído, o cliente tenta limpar versões mais antigas e tentativas incompletas com mais de 24 horas. Uma interrupção de limpeza é registada sem declarar falha da cópia já publicada.

As quotas, custos, retenção externa, disponibilidade, região e administração do projecto continuam sob responsabilidade do titular. Os administradores do projecto têm acesso administrativo aos dados; esta edição não afirma cifragem ponta a ponta independente do serviço.

## Verificações desta entrega

- 325 verificações JVM, incluindo PKCE/RFC7636, validação do retorno, isolamento de caminho e conflito de revisão.
- 56 grupos SQLite no anfitrião, incluindo migração do espaço de estudos, revisão local, teste em pausa, transacção em duas bases e posições duráveis.
- 3 verificações de extracção PDF no anfitrião.
- 25 verificações num motor PostgreSQL real em WASM/PGlite, com schemas Auth/Storage de ensaio. Cobrem grants, RLS entre duas identidades, publicação incompleta, CAS, imutabilidade e retenção de três versões.
- Compilação, assinatura v2/v3 e CRC do APK.

**Não verificado:** OAuth real, configuração do projecto, publicação dos provedores, HTTP Auth/Storage real, Android Keystore/JobScheduler real, migração/restauração física, aparência ou transferência entre dois aparelhos. A aceitação física da edição 10 pelo utilizador não foi extrapolada para a edição 11.
