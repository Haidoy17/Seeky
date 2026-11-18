# Verificação — Seeky 5.6.0

Alterações feitas sobre a versão 5.5 estável, mantendo a correcção de abertura do leitor no Android 13. Não foram adicionados modelos de voz ou bibliotecas de execução.

- Nova opção Livros com estante, grelha e lista; capas existentes/extraídas ou título de substituição; percentagens com contraste; menus de livro, favoritos, título/autor e estado de leitura.
- Consulta paginada de doze registos, fora da interface principal. Mesmos registos e ficheiros dos materiais de estudo, sem migração de esquema nem duplicação para a nova biblioteca.
- Progresso de texto considera a deslocação dentro do capítulo, conservando a política de confirmação manual de 100% dos materiais. PDF visual conserva a medição por página.
- Exportação de capítulo completo, intervalo ou livro inteiro num serviço visível em segundo plano; WAVs separados, memória limitada a uma parte e temporários limitados a um ficheiro de até 128 MB mais o trecho em geração. Sem limite global de 24000 caracteres para capítulos; mantidos os limites de segurança da extracção.
- Cancelamento, limpeza de parciais, preservação dos concluídos e geração sem alterar o progresso. O motor escolhido tem de oferecer síntese WAV para a voz escolhida.
- Leitura externa no Edge escolhida pelo utilizador. Texto escapado, página sem recursos externos, servidor apenas em 127.0.0.1, caminho aleatório, apenas GET/HEAD, limite de cabeçalhos e notificação de terminar. Nenhuma API privada de voz Microsoft ou credencial Azure.
- Traduções novas para a interface inglesa; assinatura Haidoy Carlos Mautempo.

## Validação

A compilação passa com Android SDK 36, ECJ e D8. São verificados alinhamento, integridade ZIP, assinatura e correspondência de DEX/recursos entre variantes. O APK instalável conserva o pacote e o certificado da variante independente 5.5, com versão superior 5.6.0 / 5002100.

A suíte local existente conclui todos os testes. Os testes sobre SQLite JDBC incluem 76 verificações de cursos/migração/backup, 25 do temporizador e 59 da biblioteca, filtros, paginação, progresso e intervalos. O servidor de leitura passa 11 verificações com sockets reais de loopback, incluindo texto completo, escape, caminho errado, método inválido e limpeza.

23 testes no Robolectric com Android 13/API 33 passam: 3 de montagem/desenho do leitor PDF, 9 de vozes/seleção/exportação de trechos, 1 do serviço de leitura, 5 da biblioteca/navegação e tradução, 4 de exportação de capítulos e 1 do serviço para o Edge. Os testes de capítulo cobrem livro inteiro, intervalo, amostras WAV idênticas, falha do motor e cancelamento durante a cópia para uma pasta SAF.

Robolectric não implementa PDFium: os três testes visuais de PDF usam páginas determinísticas para isolar o ciclo de vida. Vozes e síntese nestes testes usam dados determinísticos. As capturas de ecrã são de disposição de teste, sem livros pessoais nem áudio real.

Não foi feito teste num Itel físico nem avaliação auditiva das vozes Google ou Microsoft. O teste do Edge verifica a preparação e entrega local do texto; os comandos e as vozes reais do navegador precisam de verificação na versão instalada no telefone. As evidências e os testes reproduzíveis acompanham o código-fonte.
