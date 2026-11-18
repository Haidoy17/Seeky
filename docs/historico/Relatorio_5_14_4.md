# Seeky 5.14.4 — importação de Bíblias e nome da aplicação

O driver de tarefas só procurava tarefas «queued» ao despertar. Uma tarefa conservada como «running» após a morte do processo não iniciava o serviço que a recuperaria. Ao tentar retomar, o mesmo estado fazia o comando retornar imediatamente. A recuperação agora ocorre antes da selecção e do comando, quando não existe um serviço activo; um serviço vivo mantém a sua tarefa. Resumos de IA interrompidos continuam a exigir revisão, sem repetir pedidos automaticamente.

O analisador de Bíblias EPUB recebia o texto genérico do leitor, que junta números de vários versículos num único parágrafo. O novo analisador SAX mantém marcadores de versículos e capítulos do XHTML, exclui notas editoriais e actualiza o progresso dentro de cada livro. Não constrói a árvore DOM de cada livro bíblico. A leitura normal do EPUB mantém o seu visualizador e a sua formatação.

Os pontos de controlo temporários continuam transaccionais; a Bíblia só fica disponível após a publicação integral. Uma importação EPUB incompleta do analisador antigo é reconstruída para não misturar interpretações diferentes. Os pontos de controlo do novo analisador preservam o versículo ainda pendente. TXT/CSV/USFM recebem também actualizações de progresso por versículos.

O nome da aplicação é «Seeky» no manifesto de desenvolvimento e na compilação entregue; o atalho Flash conserva «Seeky Flash», sem versão. A versão interna foi incrementada para 5.14.4 / 5003400. O applicationId e o certificado da entrega anterior são mantidos.

## Validação

- 240 testes Android/Robolectric: importações, recuperação, biblioteca, leitura, IA, WAV, restauro e restantes funcionalidades.
- 7 verificações novas no núcleo para EPUB, incluindo uma Bíblia sintética com 69 partes e 33.000 versículos únicos, e extracção que respeita cancelamento e rejeita entidades externas.
- Testes anteriores do núcleo completos, incluindo 11 verificações de importação de Bíblias TXT/CSV/USFM e codificações.
- 6 testes Android novos: livro com 2.000 versículos em marcadores inline, actualização de checkpoint antigo, retoma transaccional, despertar sem serviço, comando retomar sem serviço e protecção da tarefa de um serviço activo.
- APK assinado com o mesmo certificado, alinhamento de 16 KB e rótulo «Seeky» verificados com as ferramentas Android.

Os ensaios EPUB utilizam conteúdo sintético. O download do EPUB original da Bíblia Livre ficou indisponível neste ambiente e não houve teste num ITEL físico. A confirmação final com esse ficheiro cabe à instalação no telemóvel; não se apresenta a simulação como teste do ficheiro original.

O módulo de vozes não foi alterado. A saída de narração continua em WAV.

Haidoy Carlos Mautempo
