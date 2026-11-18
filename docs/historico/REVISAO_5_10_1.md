# Revisão 5.10.1

Base: 5.10.0. Versão Android 5002501.

- Removidos os provedores OpenAI/DeepSeek da UI, dos endpoints e da geração de pedidos. Gemini é sempre o destino, mesmo após configuração antiga de outro provedor.
- Testar e guardar consulta GET /v1beta/models/gemini-2.5-flash-lite, com x-goog-api-key, HTTPS, sem redirecções/cache ou envio de documentos. Tem timeouts e cancelamento explícitos; não gera uma conclusão nem testa quota de geração.
- O formato AQ com ponto já era aceite na versão anterior. Não se atribui a rejeição relatada ao formato, nem se afirma ter verificado a chave publicada.
- Erros passam a distinguir rede, acesso, quota e armazenamento. Corpo de erro com tamanho/complexidade limitados; só motivos conhecidos são convertidos em texto local, sem expor mensagens ou chaves do servidor.
- Substituição de chave explícita grava um novo cofre Gemini cifrado, sem depender da leitura do registo antigo danificado. Não remove conversas ou notas nem altera esquemas. Não há fallback em texto simples.
- Testes de transporte verificam chaves fictícias com ponto, consulta sem geração, ausência de repetição/fallback e motivos de rejeição sem segredos. Testes Android cobrem migração da configuração antiga e substituição de cofre corrompido.

A chave publicada não foi usada, gravada no projecto ou distribuída. Nenhuma chamada real, facturação ou conta foi activada. Sem validação de hardware Keystore ou do Itel físico. A causa exacta do erro no aparelho permanece por confirmar; as falhas detectadas no tratamento local foram corrigidas.

Verificação final: **62 testes Android/API 33 simulados** e **31 verificações de IA** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
