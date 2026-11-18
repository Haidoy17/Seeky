# Seeky 5.15.2

Os campos Disposição e Tipo de livro herdavam a cor do tema Android da Activity, independentemente da cor do painel Seeky. Como o Spinner cria a vista seleccionada depois da abertura e volta a criá-la quando o adaptador muda, a cor aplicada uma única vez ao diálogo não corrigia o problema. ChoiceSpinner passa a aplicar a cor de texto da paleta a cada vista seleccionada durante o layout, com seta visível e espaço reservado à direita. As opções e os ouvintes de selecção conservam o comportamento original. Os diálogos também definem as cores dos indicadores marcados/desmarcados e do fundo dos cursores de progresso.

Na horizontal, o leitor passa a usar um cabeçalho de 44 dp e uma doca inferior de 44 dp. O caminho e o estado redundante ficam ocultos nessa orientação; o número da página continua na navegação. A doca reúne anterior, progresso, página, seguinte e as ferramentas numa faixa horizontal. As ferramentas conservam descrições de acessibilidade e mostram o nome ao manter o dedo; a faixa pode ser deslocada quando não cabe. Na vertical, regressam as legendas e a composição habitual. A narração activa usa uma linha de transporte compacta adicional de 44 dp com os mesmos comandos de pausa, saltos, capítulos, intervalo e paragem.

O leitor gere o próprio espaçamento. A rotina global que separa cartões já não aumenta involuntariamente as margens dos controlos do leitor. Num ensaio de 720 × 360 dp, a área de documento mede 272 dp sem narração activa e 228 dp com o transporte activo, antes das barras do sistema que o Android reserva. Também foi verificado o espaço disponível numa janela de 540 × 280 dp.

O ListView do PDF interceptava o movimento vertical quando a página estava ajustada à largura: nesse modo a imagem podia ser maior que a janela mesmo com scale == base, mas a protecção existente só cobria zoom acima da escala base. O gesto deixava de chegar à página e a lista de uma só página não tinha conteúdo vertical para deslocar. A página passa a reservar o gesto desde o início no modo de página única, libertando-o no fim. A rolagem contínua continua a pertencer à lista na escala base; o zoom continua a permitir mover a imagem e a selecção mantém os seus gestos.

A verificação de rotação detectou ainda uma reposição tardia da matriz da imagem: a medição final podia executar fit() depois da reposição da ampliação. ReaderPageView reaplica a posição guardada depois de mudar de tamanho, conservando a página e a ampliação durante a recriação do leitor.

## Validação

- Conjunto completo Android/Robolectric: 277 testes, incluindo 14 regressões desta edição.
- Ensaios com eventos MotionEvent enviados à lista PDF real: arrasto nos dois sentidos, mudança lateral de página, rolagem contínua e movimento com zoom sem mudar de página.
- Ensaios com eventos enviados à raiz do leitor de texto na horizontal e régua visível: rolagem vertical, avanço por gesto e selecção de texto.
- Recriação da Activity por mudança de configuração: capítulo/deslocação do texto e página/zoom do PDF recuperados.
- Campos claros e escuros verificados após selecção e actualização do adaptador; menu Organizar livros real verificado com indicadores desmarcados visíveis.
- Dimensões do leitor, acesso aos comandos, regresso à disposição vertical e transporte de voz activo verificados.
- Conjunto completo do núcleo: importação bíblica, formatos, WAV, armazenamento, bases de dados, extracção PDF e recuperação.
- APK verificado com a identidade instalada mz.bam.seeky.v51, nome Seeky, versão 5.15.2 / 5003800, assinatura anterior e alinhamento de 16 KB.

As capturas são desenhos nativos da interface de ensaio. O PDF usado é sintético; o renderizador PDFium é um duplo de teste no Robolectric, e a extracção de texto PDF é verificada separadamente. Não houve teste num aparelho ITEL físico. O módulo de vozes mantém-se na versão anterior; a exportação continua em WAV. As bibliotecas pessoais e os dados do utilizador não foram modificados por esta actualização.

Os registos podem conter erros injectados pelos testes de recuperação; o resultado global encontra-se no final de cada registo.

Haidoy Carlos Mautempo
