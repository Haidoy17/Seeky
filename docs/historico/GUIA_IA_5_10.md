# Seeky 5.10.2 — resumo e diagnóstico Gemini

Assinatura: Haidoy Carlos Mautempo.

Instale `Seeky_5_10_2_Gemini.apk` sobre a entrega anterior, sem desinstalar. O rodapé de Estudar com Seeky mostra 5.10.2.

## Usar

Abra Estudos → Estudar com Seeky → Ligação da IA. Introduza uma chave pessoal Gemini, confirme as condições e toque em **Testar e guardar**. Use um projecto elegível sem facturação para ficar no gratuito. O Seeky não consegue verificar elegibilidade, saldo ou facturação.

O teste mudou: agora gera uma resposta curta ao texto fixo «Responde apenas: OK.», sem enviar documentos. Pode consumir quota/créditos de API e só ocorre ao tocar no botão. Uma consulta de metadados, usada anteriormente, não demonstrava que a conta conseguia gerar texto.

Depois abra uma matéria e toque em Resumir. Se falhar, aparece um cartão na conversa com o código HTTP, estado e mensagem do Google (quando o servidor os fornecer). Use **Copiar erro**; a aplicação remove a chave do diagnóstico. A pergunta fica conservada e pode tentar novamente. O erro também se mantém ao reabrir a conversa pendente. Não é repetido automaticamente.

O serviço pode impor limites de quota, modelo, região ou projecto. Esta versão não contorna esses limites nem activa pagamentos. Se uma chave foi exposta, substitua-a; introduza a nova apenas na aplicação.

## Limites da verificação

Não foi utilizada a chave publicada nesta conversa, nem foi feita qualquer chamada real ao Google nesta sessão. Os testes de geração usam transporte simulado; verificam que o APK envia o mesmo contrato de geração e só aceita respostas com texto. A causa exacta do HTTP 400 no aparelho ainda não está confirmada. As alterações corrigem a verificação insuficiente e a apresentação do erro; não garantem que o Google autorize uma conta ou chave.

Referências oficiais: [erros e elegibilidade](https://ai.google.dev/gemini-api/docs/troubleshooting) e [generateContent](https://ai.google.dev/api/generate-content).
