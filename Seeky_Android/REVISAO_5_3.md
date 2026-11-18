# Seeky 5.3 — apresentação, prévias e foco em segundo plano

Revisão sobre a 5.2, em 27/10/2026. A assinatura visual continua a ser Haidoy Carlos Mautempo.

## Alterações pedidas

- Retirado o título SEEKY FLASH ao lado do logótipo no painel de pesquisa rápida. O atalho e a função Flash permanecem; a janela alternativa também mostra apenas a marca.
- Flash/mudar para completa e Pesquisar nas pastas passam a ter caixas com borda, fundo e cantos de acordo com o tema. Procurar também fora do catálogo usa o mesmo tratamento.
- O aviso de novos nomes disponíveis passa a ocupar uma caixa própria, centralizada. Mantém a atualização de estado do índice.
- O cartão de armazenamento usa um gradiente derivado da paleta escolhida, incluindo Oceano, Coral, Jade, Rosa, Âmbar e Grafite; deixa de ter o roxo fixo. Os destaques de Estudos seguem a paleta também.
- Retirados do início os blocos Sempre à mão/Encontre ficheiros sem sair de outra aplicação e Acesso rápido. As funções continuam acessíveis pelos separadores, preferências e atalhos existentes.
- Abertos recentemente passa a mostrar até seis linhas agrupadas num cartão, com ícone, nome e metadados alinhados.
- Retirados os títulos Encontre o que procura e Nome, extensão ou palavras do documento da pesquisa, e O seu explorador/Use a caixa para seleccionar… do explorador. Os campos e as ações continuam disponíveis.
- Removida da apresentação a informação técnica Projecto Seeky ligado/Google e Facebook aguardam ativação, incluindo o bloco solto de estado nos Estudos. Os botões de conta continuam a respeitar a disponibilidade real do serviço; não foi alterada a autenticação nem o backend.

## Ficheiros e grelhas

Os itens distinguem PDF, Word, Excel, apresentações, imagem, áudio, vídeo e APK, em vez do mesmo documento genérico. PDF tem a identificação PDF; Word usa W; imagens mantêm símbolo próprio. Para APKs legíveis, o ícone é carregado do próprio arquivo pelo PackageManager, sem instalar nem executar a aplicação. Quando não é possível obter o ícone, mantém-se o símbolo Android.

Grelhas, blocos e ícones grandes dão espaço à prévia. As imagens mostram o seu conteúdo; PDFs mostram a primeira página; EPUBs usam a capa declarada no livro. DOCX usa a miniatura incorporada ou uma prévia do texto, marcada com W. Essa prévia de texto não é a paginação exata do Microsoft Word. DOC antigo, documentos protegidos, PDFs não pesquisáveis pelo descritor, EPUB sem capa e formatos/ficheiros que excedem os limites podem conservar o ícone de formato.

O processamento usa um descodificador, fila limitada e cache de até 3 MiB; ícones de APK partilhados têm cache separada de 512 KiB. As imagens são reduzidas a 320 px na prévia e as páginas PDF a 76.800 píxeis. Cópias temporárias de documentos recebidos por content URI têm limite de 16 MiB e são apagadas. Partes de imagem dentro dos ZIPs são limitadas a 2 MiB; XML usado para obter capa/texto é limitado. Não há extração de caminhos arbitrários para o disco nem acesso a recursos remotos no EPUB/DOCX.

## Estudos com estrutura visual

- Cabeçalho do curso/módulo dentro de uma superfície própria, com símbolo, título destacado, gestão e navegação entre níveis.
- Cursos em cartões com descrição, contadores separados e ação Abrir curso.
- Módulos/disciplinas em cartões com tipo, título, número de materiais, progresso e ações Materiais/Importar.
- Biblioteca com capa/prévia, título, unidade, tipo/tamanho, progresso e botões Estudar/Detalhes.
- Separadores contextuais agrupados, sem o estado técnico de conta misturado com o conteúdo.

Os cursos, materiais e atividades existentes são preservados. Os esquemas continuam Estudos 3, leitor 2 e catálogo 6; não foi introduzida outra migração de dados nesta revisão.

## Temporizador fora da janela

A sessão de foco passa a ter um serviço em primeiro plano, com notificação de contagem, pausa/continuação e registo do tempo. A janela de Estudos deixa de ser responsável por concluir a sessão. A duração é calculada a partir dos timestamps guardados, não da quantidade de atualizações do ecrã.

O estado é persistido; o token da sessão impede duplicações se o processo terminar entre gravar no SQLite e atualizar as preferências. Ações de sessões anteriores não devem afetar uma nova sessão. Se uma disciplina desaparecer entretanto, o tempo é conservado em Geral. Uma falha de escrita conserva o temporizador para recuperação.

Um alarme inexato complementa o serviço durante suspensão. Não se promete um alerta ao segundo em Doze: restrições de bateria/Android podem atrasar a entrega da notificação, embora o tempo registado seja limitado à duração da sessão. As notificações têm de ser permitidas para ver os controlos/alerta. Forçar paragem nas definições interrompe o funcionamento até à próxima abertura, conforme as regras do Android.

## Verificações realizadas

- Compilação completa de recursos, Java e DEX com API 36 e ECJ 3.41.0.
- Suíte local anterior passou; 14 verificações novas cobrem identificação de formatos, política de prévias, mudança de cor e limites do contador.
- 38 verificações de cursos/migração/backups, 40 anteriores de cadernos e 17 de persistência passaram.
- 25 verificações executam o motor real FocusSessions sobre SQLite JDBC: iniciar, rejeitar uma segunda sessão, pausar/retomar, tempo sem ticks da Activity, limite da duração, recuperação, ações antigas, descarte, falha/repetição de escrita, disciplina removida e validação.
- APK instalável verificado por assinatura, alinhamento e CRC, com pacote/certificado iguais aos da instalação separada 5.2 e código de versão superior.

Não houve emulador nem instalação no Itel A17. Os testes JVM não comprovam o desenho de píxeis, a qualidade das capas/ícones, o ciclo de vida do Service, permissões/SAF, interação das notificações, arranque, Doze ou latência num aparelho. Essas verificações estão em TESTE_5_3.md. As prévias devem ser verificadas com PDF, EPUB, DOCX, imagens e APKs reais; a implementação conserva sempre um ícone de formato quando a prévia não está disponível.

## Entrega

Seeky_5_3_Visual.apk atualiza a instalação separada anterior (`mz.bam.seeky.v51`), sem precisar de a desinstalar. O APK do pacote original (`mz.bam.seeky.estudos`) continua sem assinatura e precisa da chave original. Nenhuma chave privada é incluída no código-fonte.

As evidências desta revisão têm prefixo evidence/v53_. Relatórios de outras versões são históricos. Mantêm-se as limitações gerais de aceitação no aparelho descritas nas revisões anteriores.
