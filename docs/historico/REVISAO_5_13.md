# Seeky 5.13 — relatório de entrega

31 de Outubro de 2026 · versão 5.13.0 / 5002800 · Haidoy Carlos Mautempo.

## Resultado

APK assinado com o mesmo certificado da entrega 5.12 e o mesmo identificador de actualização. Foram aplicadas as seis correcções e as oito extensões da revisão 5.12, mais a separação dos controlos e tabelas e as funções Android/TTS/consulta bíblica pedidas posteriormente. Os ficheiros de áudio mantêm o formato WAV.

## Alterações

- Fila persistente para importações, cópias, recuperação, indexação, OCR, metadados, Bíblia e resumos; pausa/retoma, notificação, diário por item e protecção de operações críticas.
- Importação de pastas e subpastas, comparação de conteúdo, relatório completo e limites por formato/espaço livre. Leitura directa das cópias privadas evita duplicar um livro grande no cache.
- Metadados EPUB/PDF disponíveis, edição em lote e escolha de capa. Colecções paginadas guardam apenas as mudanças, preservando associações não apresentadas.
- Pesquisa/paginação no caderno, selecção de notas antigas e PDF dividido em volumes. A selecção de todos fica ligada ao conjunto escolhido mesmo depois de mudar a pesquisa.
- Cópias opcionais de conversas e WAVs externos, perfis/fundos/capas, validação e recuperação local anterior. O diário dos movimentos permite reverter uma recuperação interrompida sem copiar novamente todos os livros grandes.
- Pesquisa FTS privada com OCR, abertura na ocorrência e verificação da fonte. Perfis por livro para voz, idioma, velocidade, fundo, volume e pausa; revisão dos títulos detectados. Correcção da restauração de scroll que podia ser limitada antes do layout terminar.
- Resumos por etapas com limite de pedidos, fontes guardadas, edição e avisos de resposta parcial. Um pedido sem resposta confirmada não volta a ser enviado automaticamente. Nenhuma chave pessoal foi usada nos testes.
- OCR por página com retoma, confiança e revisão; modelos fixados à revisão 4.1.0 e validados por SHA-256. O TTS usa o OCR corrigido sem voltar a extrair primeiro a mesma página.
- Gestão de espaço com protecção de recuperações, tarefas e perfis activos; plano de leitura e widget sem consultas periódicas.
- Detecção de motores TTS novos, actualização ao voltar às opções e ecrã dedicado. O Seeky aparece em Partilhar/Abrir com; APKs usam o instalador Android com confirmação no aparelho.
- Importação de Bíblia em segundo plano e publicação atómica; pode indexar a Bíblia já existente na estante. TXT/CSV/TSV/USFM e documentos com cabeçalhos de livro/capítulo e versículos numerados. Referências abrem sobre o leitor; vírgulas/ponto e vírgula não incluem versículos intermédios não pedidos.
- Separação das superfícies nativas, incluindo células sem botão; preservação da disposição das páginas originais. Foram acrescentadas traduções inglesas dos novos controlos.

## Validação

204 testes Android simulados aprovados na execução final. Incluem 1205 livros, cópia de 129 MiB, interrupção/retoma, pastas aninhadas, conservação da 501.ª colecção, selecção da nota mais antiga de 250, volumes PDF, cópias/restauro e remapeamento, resumos com respostas locais, perfis, OCR com motor simulado, espaçamento, partilha de 1206 URI únicos, argumentos do instalador e toque na referência bíblica sobre o leitor.

A bateria local de regressão passou; passaram 160 verificações SQLite de cursos/temporizador/livros e 34 verificações do protocolo e persistência da IA. O OCR real de Português e Inglês foi exercitado no computador. Certificado, versão, DEX, recursos, oito bibliotecas nativas, alinhamento e integridade dos arquivos foram conferidos.

As evidências finais ficam em `evidence/v513` no código-fonte. A compilação de entrega é reproduzível com `build_apk.py` e `build_delivery.py`, sem descarregar dependências. Chaves de assinatura e credenciais ficam fora do ZIP.

## Limites da validação

Não houve acesso ao Itel físico nem audição de vozes reais. O instalador real do Android, a instalação/detecção de um motor real, o launcher/widget, as permissões do fornecedor de ficheiros e o uso prolongado com pouca bateria precisam de ensaio no aparelho. Não se certificou uma qualidade de voz neural: a qualidade depende do motor/voz disponibilizado.

Os testes do PDF nativo usam uma ponte de teste que desenha a disposição em bitmaps e prepara um PDF no computador; não comprovam o codificador PDF do Android. O OCR ARM foi empacotado e verificado estaticamente, mas o seu carregamento JNI no Itel não foi exercitado. Os pedidos de IA são simulados. A indexação de uma Bíblia em PDF/EPUB depende da estrutura conservada pelo ficheiro; formatos estruturados continuam a ser os mais previsíveis.

## Instalação

Instale o APK sobre a versão actual, sem desinstalar. Use o guia para encontrar as novas opções. Preserve uma cópia de segurança dos seus dados.
