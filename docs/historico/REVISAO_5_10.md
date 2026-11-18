# Revisão Seeky 5.10.0 — Estudar com Seeky

Data: 30 de Outubro de 2026. Base: 5.9.2. Versão Android: 5002500. Assinatura visual: Haidoy Carlos Mautempo.

## Implementação

Foi acrescentada uma Activity nativa não exportada, acessível pelos Estudos, materiais, anotações, barra do leitor e selecção de texto fluido. A área apresenta-se como **Estudar com Seeky**. Nomes dos fornecedores aparecem na configuração e no aviso de tratamento de dados, não no título do produto. Não existe WebView de chat ou abertura de site externo para conversar.

A conversa permite resumir, explicar, criar perguntas e preparar revisão, com perguntas livres e seguimento. Pode copiar respostas e guardá-las como anotações normais dos Estudos após revisão. O processamento ocorre nos serviços remotos, usando chave pessoal; a aplicação não inclui uma credencial comum ou um modelo de IA local.

SeekyAiCore implementa os contratos verificados nas fontes oficiais: Gemini `generateContent` com `gemini-2.5-flash-lite`; DeepSeek Chat Completions com `deepseek-flash` e thinking desactivado; OpenAI Chat Completions com `gpt-5.6-terra`, reasoning effort none e store false. As respostas têm limite de 2048 tokens de saída e de 14000 caracteres aceites. O modelo inicial Gemini foi escolhido pela quota gratuita documentada; não se afirma que OpenAI ou DeepSeek ofereçam uma API universalmente gratuita.

## Contexto, dados e custos

- Só uma pergunta explícita ou um atalho inicia o pedido. Abrir a ferramenta, um ficheiro ou a configuração não faz uma chamada ao fornecedor.
- O texto é carregado numa tarefa de fundo, uma página/capítulo de cada vez, utilizando os limites existentes de extracção. Trechos até 12000 caracteres são escolhidos explicitamente. Não se envia uma biblioteca ou um livro inteiro em segundo plano.
- Cada conversa guarda o seu contexto fixo. Alterar um contexto com conversa existente cria outra conversa, conservando a anterior. A rede recebe até três pares recentes, num orçamento de 16000 caracteres, mais contexto e pergunta.
- As ligações HTTPS têm hosts fixos. As chaves são enviadas em headers, nunca na URL ou no payload. Redirecções e cache HTTP estão desactivadas. Não há fallback entre fornecedores nem repetição automática de pedidos. Erros de autorização, saldo, quota, tempo e conteúdo vazio são apresentados como falhas reais.
- Há limites de bytes e de complexidade JSON antes do parsing. O material é tratado como evidência citada nas instruções; o modelo não recebe ferramentas para executar código, abrir ficheiros ou navegar. Respostas são apresentadas como texto nativo, sem execução de HTML ou instruções.
- Chaves pessoais são cifradas com AES-GCM/Android Keystore, sem alternativa em texto simples quando a protecção falha. A Activity e os campos de segredo não exportam a chave. Manifesto mantém allowBackup=false.
- Conversas são snapshots privados com escrita atómica, pergunta pendente e pares concluídos. A Activity guarda apenas identificador e rascunho no Bundle, evitando transportar uma conversa/documento grande durante a recriação.
- Cancelar interrompe a espera, mas não garante estorno de consumo já feito pelo fornecedor. A aplicação não lê saldo/facturação nem garante que uma chave introduzida pertença a uma conta gratuita. Esta limitação é indicada na configuração e no guia; o utilizador confirma quota/créditos sem recarga automática.

## Verificação

**30 verificações específicas** exercitam divisão Unicode, reconstrução integral do contexto, limites de pergunta/histórico, payloads dos três serviços, exclusão de raciocínio interno, respostas limitadas/bloqueadas, headers, redirecções, quota, cancelamento, limites de bytes/profundidade JSON e conservação atómica da conversa. Executam com heap de 128 MB e transporte determinístico, sem chamadas reais.

**61 testes Android 13/API 33** cobrem as regressões anteriores e os novos fluxos: abrir material sem pedido à IA, abrir somente Activity interna, exigir configuração, envio com contexto seleccionado, guardar resposta como anotação, recuperar conversa e rascunho sem reler a origem, cancelar/tentar de novo sem duplicar respostas, segredo cifrado/corrompido e interface inglesa em ecrã pequeno. As capturas `ai-conversation` e `ai-english-small` mostram a interface real com respostas de ensaio.

O teste de cifragem utiliza AES-GCM com uma chave de teste em JCE, pois o Robolectric não fornece o Android Keystore físico. O código de produção usa exclusivamente Android Keystore. As limitações anteriores de PDFium/WebView e codecs mantêm-se descritas nos relatórios das versões anteriores.

Não se realizou uma chamada real a Gemini, DeepSeek ou OpenAI por faltar uma chave autorizada. Não se certificam nesta entrega a elegibilidade de conta/quota, o saldo, a qualidade real dos modelos, a latência, o Android Keystore físico ou o funcionamento no Itel. Não houve instalação de dependências de execução, alterações de backend, deploy, activação de facturação ou compra de créditos.

O APK assinado mantém pacote `mz.bam.seeky.v51` e certificado da 5.9.2. O APK sem assinatura conserva `mz.bam.seeky.estudos`. Chaves privadas de assinatura não entram no arquivo de código. Os esquemas de Estudos, leitor e catálogo permanecem em 3, 2 e 6; conversas da IA usam armazenamento privado separado. As melhorias de EPUB, fundo e TTS da base continuam incluídas.

## Fontes oficiais consultadas

- [Gemini: preços/quota gratuita](https://ai.google.dev/gemini-api/docs/pricing), [generateContent](https://ai.google.dev/api/generate-content), [chaves](https://ai.google.dev/gemini-api/docs/api-key) e [regiões](https://ai.google.dev/gemini-api/docs/available-regions).
- [DeepSeek: primeiro pedido e modelo actual](https://api-docs.deepseek.com/quick_start/pricing).
- [OpenAI: GPT-5.6 Terra](https://developers.openai.com/api/docs/models/gpt-5.6-terra), [Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) e [separação de créditos API/ChatGPT](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform).
