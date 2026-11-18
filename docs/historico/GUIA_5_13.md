# Seeky 5.13 — guia de utilização

Assinatura: Haidoy Carlos Mautempo.

## Actualizar

Instale `Seeky_5_13_Continuidade_Estudos.apk` sobre a edição 5.12 já instalada, sem a desinstalar. A entrega conserva o identificador `mz.bam.seeky.v51` e o certificado das entregas anteriores. As gravações continuam em WAV. Faça uma cópia em Ferramentas de leitura e biblioteca → Cópia e recuperação antes de substituir uma instalação com dados importantes.

## Importações e tarefas

Em Livros, escolha Importar livros ou Importar pasta. A pasta inclui as subpastas; a primeira verificação apresenta a quantidade e o tamanho conhecido e pausa para rever. Em Tarefas, seleccione Retomar pendentes e falhados para iniciar a cópia. É possível pausar e continuar mais tarde. Os itens concluídos são conservados; os duplicados são comparados pelo conteúdo no destino escolhido. O relatório CSV inclui todos os itens, inclusive os falhados e pendentes.

A notificação acompanha as operações longas. Pode fechar a janela e continuar a usar o telemóvel. O Android pode suspender serviços, revogar o acesso a ficheiros partilhados ou limitar o tempo em segundo plano: ao reabrir o Seeky, consulte Tarefas. Uma autorização de pasta conservada pelo fornecedor permite continuar sem escolher todos os livros novamente. Se essa autorização se perder, escolha a pasta de novo. Forçar a paragem no Android interrompe os serviços.

Pastas de importação permite verificar novamente uma pasta. O programa verifica o conteúdo conhecido e acrescenta os novos livros. O limite fixo de 128 MiB foi removido: são usados limites de formato e espaço livre, com reserva de 32 MiB. EPUB/DOCX: até 2 GiB; PDF/outros materiais: até 8 GiB; texto simples: até 64 MiB. Estes limites de cópia não garantem que um documento malformado ou uma página excepcionalmente grande possa ser extraído num aparelho com 128 MiB de heap.

## Biblioteca e estudos

Os EPUBs passam a fornecer os metadados disponíveis de título, autor e série; PDFs fornecem os metadados disponíveis. Use Editar livros em lote para aplicar autor, série, assunto ou prefixo do título. A capa pode ser substituída por uma imagem. Colecções e o caderno de revisão têm pesquisa e paginação. As associações que não aparecem na página actual são conservadas ao guardar.

O caderno de revisão permite chegar a notas antigas, cartões e perguntas. Quando excede o tamanho de um volume, prepara vários PDFs dentro de um ZIP. A selecção de todos inclui os resultados da pesquisa. O widget Continuar leitura pode ser adicionado nas ferramentas ou pelo ecrã inicial do Android; usa a última leitura e as posições guardadas pelo leitor.

Pesquisar no conteúdo prepara um índice privado, uma parte de cada vez, incluindo o OCR guardado. A indexação automática de novos livros pode ser activada nas opções da biblioteca; também pode iniciar/actualizar o índice manualmente. Os resultados mostram livro, parte e trecho e abrem na ocorrência. Se o conteúdo tiver mudado, actualize o índice. O índice não é enviado a um serviço externo.

## Voz e leitura

No leitor, abra Perfil deste livro para guardar motor, voz, idioma, velocidade, pausa após títulos, fundo e volume. A pausa inicial é de dois segundos e pode ser ajustada de um a cinco segundos. Rever títulos detectados mostra os títulos e o texto seguinte, sem modificar o documento. A qualidade sonora continua a depender da voz disponibilizada pelo motor; seleccionar Online exige uma voz de rede efectivamente disponível.

Em Ferramentas de leitura e biblioteca → Detectar motores TTS, actualize a lista e escolha um motor Android instalado. O leitor também tem Detectar motores e actualizar vozes em Opções de voz. O Seeky não inclui pacotes de vozes neurais nem transforma uma voz básica numa voz neural. Os pacotes offline pertencem ao respectivo motor. A prévia permite ouvir antes de confirmar a voz.

## Bíblias e consulta sem sair da leitura

Uma Bíblia adicionada apenas à estante ainda precisa de ser indexada para consulta. Abra Ferramentas de leitura e biblioteca → Bíblias importadas → Usar Bíblia da biblioteca. Também pode importar um ficheiro próprio.

TXT, CSV, TSV e USFM em UTF-8 são os formatos mais previsíveis. Exemplos:

```text
Isaías 5:6 Texto do versículo
```

```csv
livro,capitulo,versiculo,texto
Isaías,5,6,"Texto do versículo"
```

EPUB, PDF e DOCX também são aceites quando o texto extraído conserva o nome do livro bíblico, os títulos de capítulo e os números dos versículos. Uma digitalização sem texto precisa de OCR; uma disposição ambígua deve ser convertida para um dos formatos estruturados. A importação corre em Tarefas e só disponibiliza a tradução quando termina. Não é distribuído texto de uma tradução bíblica com o aplicativo.

No modo Texto fluido, toque numa referência sublinhada. No EPUB original, toque na referência ligada. No PDF original, use Ferramentas de leitura → Passagens bíblicas, ou Consultar passagem bíblica para escrever a referência. O menu Mais da janela principal também tem Consultar passagem bíblica. A passagem abre num diálogo sobre a janela actual; feche-o para continuar no mesmo ponto.

## Resumos e OCR por etapas

Resumo do capítulo por etapas permite escolher páginas/partes e um máximo de 1 a 20 pedidos por execução. Usa exclusivamente o Gemini pessoal configurado, com a quota da conta. Cada resposta e a fonte fornecida ficam guardadas; ao atingir o limite, a tarefa pausa. Uma resposta perdida após envio fica assinalada para revisão e não é reenviada automaticamente, pois pode ter consumido quota. Pode ler, editar e repetir explicitamente um trecho. As notas guardadas nos Estudos mantêm a indicação da fonte e avisos de resposta parcial.

OCR em lote trabalha uma página de cada vez e conserva as páginas concluídas. Descarregue primeiro os dados do idioma em Dados de OCR. A revisão permite corrigir o texto antes de o usar na voz e nos estudos. A confiança é uma estimativa do motor, não uma garantia de transcrição correcta. Os modelos opcionais não estão no APK.

## Partilha e instalação de APKs

O Seeky aparece no menu Android de Partilhar para um ou vários ficheiros de qualquer tipo e em Abrir com para ficheiros locais. Pode abrir os formatos suportados, importar livros, guardar uma cópia ou escolher outra aplicação. Ficheiros que não sejam livros são identificados no relatório se forem enviados para importação da biblioteca.

Para instalar um `.apk`, abra-o no Explorador/partilha ou escolha Instalar aplicação Android nas ferramentas. Prepare o APK, permita a instalação desta origem se o Android pedir e toque em Abrir instalador Android. A confirmação e a instalação são feitas pelo sistema. Não há instalação silenciosa. Esta função não instala pacotes XAPK/APKS divididos.

## Cópia e espaço

As cópias usam tarefas persistentes e permitem incluir conversas do assistente e WAVs externos. Os pacotes de vozes dos motores Android e as credenciais de acesso não entram na cópia. A recuperação é verificada antes da confirmação e cria uma recuperação local dos dados anteriores. Uma cópia interrompida durante a compressão pode precisar de recomeçar essa fase; o arquivo já preparado é reutilizado quando estiver íntegro.

Armazenamento mostra livros privados, WAVs privados recuperados, notas de voz, fundos, capas, perfis, OCR, cache e recuperações. O tamanho de WAVs externos no fornecedor de documentos não entra no total dos WAVs privados. A limpeza apresenta os candidatos e volta a verificar ligações e recuperações antes de apagar. Dados que pertencem a uma recuperação conservada permanecem protegidos.

## Verificação desta entrega

Foram executados testes automáticos Android simulados/API 33 e testes locais de dados, leitura, áudio WAV, importação e protocolos com respostas simuladas. O OCR real de Português e Inglês foi exercitado no computador com os modelos fixados. Não foi usado o seu API pessoal e não houve pedidos reais ao Gemini nem audição de vozes reais. O Itel, o instalador do Android, os motores instalados e o launcher/widget ainda precisam de verificação no aparelho. O relatório acompanha os resultados e os limites dos testes.
