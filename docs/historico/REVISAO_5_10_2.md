# Revisão 5.10.2

Base 5.10.1; versionCode 5002502; pacote independente mz.bam.seeky.v51, certificado anterior mantido.

Corrigida a falsa equivalência entre acesso a metadados do modelo e geração. Testar e guardar passou a enviar um prompt fixo sem documentos ao generateContent, com limite de 128 tokens de saída; usa o mesmo contrato, parser, host HTTPS, header, cancelamento e timeouts da conversa. A interface informa o consumo de quota. Não há teste automático ao abrir a aplicação nem fallback/repetição automática.

Os erros HTTP preservam o estado e a mensagem fornecidos pelo Google, com limite de tamanho/complexidade JSON e de texto, substituição da chave exacta e de padrões de credenciais. O diagnóstico é texto nativo seleccionável, não HTML. Não contém headers nem o payload do documento e não é enviado automaticamente a terceiros. Copiar erro acrescenta a versão e modelo para distinguir instalações.

O cartão de erro fica na área rolável da conversa; o campo de estado aponta para os detalhes. A pergunta pendente e o erro são guardados no armazenamento privado da conversa para retomar. Não se escreve uma resposta fictícia em caso de falha.

Testes: transporte de geração real (simulado, sem rede), rejeição de HTTP 200 sem texto, HTTP 400 com INVALID_ARGUMENT e mensagem accionável, remoção da chave, substituição de registo cifrado danificado, configuração falhada sem guardar chave/activar ligação, nova tentativa e sucesso, erro visível/copiável e conservado depois da recriação. O parser e payload foram comparados à documentação oficial; não foi detectado um campo inválido que permita atribuir-lhe o HTTP 400 relatado.

Não foi testada uma conta ou chave real; a chave exposta não foi utilizada ou incluída nos artefactos. Sem validação em dispositivo físico. A causa da recusa do Google continua por confirmar; não se afirma ter resolvido permissões, quota ou elegibilidade do projecto. Nenhum plano, pagamento ou serviço externo foi activado.

Fontes: https://ai.google.dev/gemini-api/docs/troubleshooting e https://ai.google.dev/api/generate-content.

Verificação final: **64 testes Android/API 33 simulados** e **34 verificações de IA** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
