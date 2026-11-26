# Seeky IA — serviço sem chave

Com este serviço, quem instala o Seeky (Android ou Windows) pode usar a Seeky IA **sem criar uma chave**.
A sua chave do Google Gemini fica **só no Cloudflare**, como segredo — nunca dentro do APK nem do instalador
(de lá qualquer pessoa a conseguiria tirar e gastar a sua quota).

## Pôr a funcionar (uma vez, cerca de 10 minutos)

1. Crie uma conta gratuita em https://dash.cloudflare.com e instale o Node.js.
2. Nesta pasta (`server/seeky-ia`):
   ```
   npx wrangler login
   npx wrangler kv namespace create LIMITS     # copie o "id" para wrangler.toml
   npx wrangler secret put GEMINI_KEY          # cole a chave do Google AI Studio (não fica no GitHub)
   npx wrangler deploy                         # mostra o endereço: https://seeky-ia.<o-seu-nome>.workers.dev
   ```
3. No repositório do Seeky, edite `entrega/seeky-ia.json`:
   ```json
   { "enabled": true, "service": "https://seeky-ia.<o-seu-nome>.workers.dev" }
   ```
   e junte-o ao `main`. As aplicações lêem este ficheiro (no máximo de 12 em 12 horas) e passam a usar o
   serviço sozinhas quando a pessoa não tem chave própria. Para desligar: `"enabled": false`.

## Limites e custos

- Por omissão: 40 pedidos por aparelho, 120 por rede e 1000 no total por dia (`wrangler.toml`).
- A quota gratuita do Gemini é limitada e partilhada por todos os utilizadores; com muitos utilizadores
  considere um plano pago no Google AI Studio e aumente os limites.
- Só passam pedidos de texto `generateContent` para os modelos em `MODELS`; quando um modelo está ocupado
  (pico de uso) a aplicação tenta o seguinte.
- Quem usa a sua própria chave continua a falar directamente com a Google.
