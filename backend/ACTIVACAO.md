# Activação Google/Facebook — edição 12

## Estado confirmado

O projecto Seeky (`wohwoyfhnfwotrzbaprx`) foi criado na organização bamservice’s Org e está activo. O schema e o bucket privado foram aplicados e verificados no servidor. O APK já inclui URL e chave pública. Google e Facebook foram consultados em /auth/v1/settings e continuam desactivados; falta configurar os provedores e o retorno ao Android.

O primeiro apply_migration não foi aplicado (erro de estado de pedido); confirmou-se ausência de tabelas e migrações antes da alternativa. O schema foi então instalado por execute_sql, com validação posterior. backend/schema.sql é o registo do DDL efectivamente usado. Não se declara uma entrada de histórico de migrações que não tenha sido criada.

## 1. Projecto e base já ligados

URL: https://wohwoyfhnfwotrzbaprx.supabase.co
Painel: https://supabase.com/dashboard/project/wohwoyfhnfwotrzbaprx

As tabelas públicas têm RLS e apenas SELECT autenticado. Funções SECURITY DEFINER ficaram no schema não exposto seeky_private, com verificações de auth.uid(); a API pública usa wrappers SECURITY INVOKER. O bucket seeky-private é privado. A chave publishable está no APK; nenhum segredo de administração foi incorporado.

Referências de instalação e ensaio seguem abaixo. A configuração Google/Facebook ainda deve ser concluída.

## 2. Registar o Google

No Google Cloud do responsável:

1. Configurar o consentimento OAuth com o nome, dados de contacto e domínio reais da aplicação.
2. Criar um cliente OAuth de aplicação Web para o fluxo através do Supabase.
3. Adicionar o callback exacto mostrado no painel Supabase, normalmente `https://wohwoyfhnfwotrzbaprx.supabase.co/auth/v1/callback`.
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
- A URL/chave pública do projecto Seeky já estão incluídas no APK. Os provedores Google/Facebook ainda aguardam as suas credenciais e activação.
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

**Não verificado:** OAuth real, configuração dos provedores, publicação dos provedores, HTTP Auth/Storage autenticado real, Android Keystore/JobScheduler real, migração/restauração física, aparência ou transferência entre dois aparelhos. A aceitação física da edição 10 pelo utilizador não foi extrapolada para a edição 12.


## Verificação adicional da edição 12

Projecto real ligado, schema/bucket aplicados, seis verificações SQL transaccionais revertidas e três verificações HTTP públicas efectuadas. Os provedores Google/Facebook permaneciam desactivados na verificação. Ver evidence/live_sql12.json e live_http12.json.
