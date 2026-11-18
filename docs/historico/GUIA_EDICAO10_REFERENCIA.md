# Seeky Estudos — edição 10 de teste

24 de Outubro de 2026 · 5.0.0-alpha.15.android.10-estudos

## Instalar e experimentar sem substituir o Seeky anterior

Instale **Seeky_Android_10_Estudos_Teste.apk**. Aparece como **Seeky Estudos · Teste**, com o seu próprio ícone e dados. O pacote é diferente do Seeky da edição 9, que permanece instalado e disponível. Não é necessário desinstalar o Seeky actual.

A barra inferior passa a: **Início · Pesquisa · Explorar · Favoritos · Estudos · Ferramentas**. Esta edição de teste abre em Estudos, depois da apresentação de 5 segundos. Mantém o logótipo e BAM - SERVICE no fundo.

Os dados do teste são separados: os favoritos, catálogo e anotações já existentes no outro Seeky não são importados automaticamente. Para experimentar Estudos basta importar materiais através do selector Android; não precisa de autorizar acesso geral a todas as pastas. A bolha e a monitorização geral não são activadas por defeito neste teste, para não duplicar os serviços da edição anterior. Continuam disponíveis nas Preferências se as quiser testar.

Se não gostar, feche **Seeky Estudos · Teste** e abra o Seeky anterior. Antes de desinstalar o teste, exporte uma cópia se quiser conservar o trabalho de estudo. A edição 9 e o seu APK anterior foram conservados, sem alteração.

## O ambiente de Estudos

Sete secções internas, com o mesmo tema e menus arredondados do Seeky:

| Secção | O que permite fazer |
|---|---|
| Visão geral | Ver materiais, notas, cartões devidos, tarefas pendentes e iniciar uma sessão de foco. |
| Biblioteca | Importar materiais, abrir o leitor, editar título/autor/disciplina e acompanhar a posição/progresso. |
| Cadernos | Apontamentos, perguntas-chave e síntese pelo método Cornell, etiquetas, origem e exportação. |
| Cartões | Pergunta/verso, lacunas escritas pelo utilizador, importação TSV e revisão espaçada. |
| Perguntas | Banco de respostas abertas e escolha múltipla, testes aleatórios e criação de cartões para revisão. |
| Agenda | Tarefas, prazos, prioridades, duração prevista, conclusão e sessões de foco/pausa. |
| Progresso | Foco diário/semanal, meta, gráfico de sete dias, sequência de dias e histórico de testes/sessões. |

Use o filtro de disciplina para organizar o trabalho. **Todas as disciplinas** mostra o conjunto; **Geral** conserva registos sem disciplina. A lupa pesquisa os registos da secção e disciplina escolhidas, por título, conteúdo ou etiquetas. A pesquisa integral de um livro continua dentro do leitor.

Não foram incluídos livros, perguntas, resultados ou progresso fictícios. As contagens começam a partir dos seus próprios dados.

## Primeiro estudo — roteiro de cinco passos

1. Em **+ → Criar disciplina**, crie por exemplo a disciplina que está a estudar.
2. Use **Importar livros e materiais** e escolha ficheiros seus. Pode seleccionar vários no selector Android.
3. Abra um material. Num PDF com camada de texto, use **Ferramentas → Texto da página**; num EPUB/DOCX, o leitor mostra o texto por partes.
4. Seleccione uma passagem e escolha **Guardar em Estudos** para criar uma anotação, cartão de pergunta/resposta ou cartão de lacuna. Preencha o título/pergunta com as suas palavras e guarde.
5. Regresse a Estudos: consulte o caderno, reveja cartões, faça um teste do seu banco de perguntas e registe uma sessão de foco.

## Biblioteca e leitura

- Importa até **50 ficheiros por escolha**, com limite de **128 MB por material**. O selector do aparelho precisa de permitir selecção múltipla. A operação pode ser cancelada; os ficheiros já importados são conservados.
- A biblioteca guarda **cópias privadas locais**, disponíveis offline. Os originais não são movidos ou apagados pela área de Estudos.
- A importação verifica SHA-256 e ignora conteúdos já existentes na mesma disciplina. A mesma fonte pode ser usada noutra disciplina.
- PDF, DOCX, EPUB, texto, imagens e áudio usam os leitores existentes, conforme o formato/codec. DOC antigo, apresentações e folhas de cálculo podem precisar de uma aplicação externa instalada.
- Título e autor podem ser editados; não são inventados dados ausentes. O nome original e o tipo são conservados.
- A posição de leitura em página/capítulo actualiza uma percentagem aproximada. Isso não prova compreensão nem leitura completa; a conclusão a 100% é indicada manualmente em **Estado e progresso**. Num documento de uma só parte, a percentagem automática pode manter-se em 0%.
- Remover uma cópia da biblioteca exige confirmação. Conserva notas/cartões/perguntas, retirando a ligação ao material removido. O original fora da biblioteca permanece.

As ferramentas da edição 9 foram mantidas: selecção no modo texto, clipboard simples/HTML, destaques, sublinhados, notas do leitor, marcadores, busca na parte/documento, zoom e recorte PNG visível. As anotações do leitor e os cadernos de Estudos são registos distintos; use **Guardar em Estudos** para levar uma passagem ao caderno ou aos cartões.

## Cadernos e cartões

Em **Nova anotação**, preencha título, apontamentos, perguntas-chave, síntese e etiquetas. Uma nota ligada a um material permite voltar à parte de origem enquanto a cópia existir. Pode seleccionar/copiar o texto, editar, transformar em cartão, exportar em TXT ou eliminar após confirmação.

Nos cartões, a frente contém a pergunta e o verso a resposta. Para lacunas, escreva a frase com `[...]` na frente e a expressão omitida no verso. O cartão criado a partir do leitor precisa da sua pergunta, sem gerar uma resposta fictícia.

**Rever cartões devidos** apresenta até 30 por sessão. Tente responder antes de tocar em Mostrar resposta. A sua avaliação define o próximo intervalo:

- **Outra vez:** volta em 1 minuto; regista esquecimento.
- **Difícil:** intervalo curto, mínimo 1 dia.
- **Recordei:** começa em 1 dia, depois 3 dias e cresce gradualmente.
- **Fácil:** começa em 4 dias e aumenta mais depressa.

O intervalo máximo é 180 dias. É uma regra simples de agendamento, não uma previsão científica do seu domínio do assunto. Pode pausar, retomar, editar, marcar para rever hoje ou eliminar cartões. O histórico de revisões concluídas é conservado sem duplicar uma avaliação por toque repetido.

**Importar TSV:** cole até 500 linhas. Cada linha usa pergunta, resposta e etiqueta opcional, separadas por tabulações. As linhas são validadas antes de guardar o lote.

## Perguntas, respostas e testes

Crie perguntas **abertas** ou de **escolha múltipla com quatro opções**, resposta correcta e explicação. As opções de escolha múltipla devem ser diferentes. Pode organizar por disciplina e etiquetas, editar ou eliminar.

Os testes sorteiam até 5, 10 ou 20 perguntas da disciplina escolhida. Escolha múltipla tem correcção automática. Nas respostas abertas, escreva antes de mostrar a referência e indique se acertou ou precisa de rever. O resultado distingue perguntas auto-avaliadas.

Depois da explicação, **Guardar cartão e continuar** cria um cartão com a pergunta/resposta e prossegue o teste. Ao concluir, guarda a pontuação e as perguntas para consulta no Progresso. Terminar antes da conclusão não guarda um resultado completo. Não existe avaliação automática por IA de respostas abertas nem geração automática de perguntas nesta edição.

## Agenda, foco e progresso

As tarefas têm disciplina, prazo em `AAAA-MM-DD` (até ao fim do dia), prioridade e duração prevista. A caixa marca a conclusão. Tarefas pendentes com prazo aparecem antes das sem data e das concluídas. Pode iniciar uma sessão ligada ao objectivo de uma tarefa.

O temporizador permite **1 a 120 minutos**, pausa, continuação e registo do tempo decorrido. Pode terminar antecipadamente e guardar o tempo ou descartar só a sessão. As pausas de descanso são registadas à parte e não contam como minutos de foco.

O estado do temporizador é guardado para retomar ao regressar ao app. Nesta edição, **não envia notificação/alarme fora da aplicação**: a conclusão é mostrada quando o espaço de Estudos estiver visível novamente. O tempo é limitado à duração prevista e não é uma medição da sua atenção.

A meta diária aceita 5 a 480 minutos de foco. O Progresso usa sessões/testes registados, com gráfico dos últimos sete dias e sequência de dias com pelo menos um minuto de foco. A apresentação considera até 1000 sessões/testes mais recentes por filtro. Não atribui tempo fictício por abrir um livro.

## Cópias de segurança e dados

Abra o botão de gestão no cabeçalho de Estudos:

- **Exportar cópia de segurança** cria ZIP escolhido por si, com materiais importados, disciplinas, notas, cartões, revisões, perguntas, resultados, tarefas, sessões e anotações/marcadores do leitor ligados aos materiais.
- Limites: **512 MB** no total, **8 MB de metadados**, até **10000 registos por tabela**. Materiais continuam limitados a 128 MB cada. Se exceder, a operação avisa; não declara uma cópia parcial como concluída.
- Mantenha a aplicação aberta durante a operação. Se a exportação falhar, elimine o ZIP incompleto e repita após resolver o aviso.
- **Restaurar / importar cópia** acrescenta dados e remapeia as ligações. Não substitui os dados actuais. Materiais iguais na mesma disciplina podem ser reutilizados; repetir uma cópia pode duplicar notas, cartões, resultados e outras entradas.
- Os materiais são verificados por hash/tamanho. Partes desconhecidas, caminhos impróprios, relações ausentes e ficheiros em falta são rejeitados. O lote principal de dados usa uma transacção; as anotações do leitor são aplicadas numa etapa separada. Se esta última falhar, o aviso identifica os dados principais já importados, para não repetir o lote inadvertidamente.
- O estado de uma sessão de temporizador ainda em curso, preferências e permissões Android não integram o ZIP. Termine/registe a sessão antes de fazer a cópia.

O teste não tem sincronização online, contas, grupos de estudantes ou envio automático dos materiais para servidores. A cópia de segurança é guardada no destino que escolher. Desinstalar o teste ou limpar os seus dados remove os materiais privados e os estudos que não tiver exportado.

## O que validar no Itel

1. Confirmar dois aplicativos separados e a edição anterior intacta.
2. Confirmar Estudos antes de Ferramentas, secções, deslizamento, teclado, fonte maior e temas Claro/Escuro/Jade.
3. Criar disciplina; importar dois materiais, repetir um e cancelar uma importação maior. Abrir PDF, EPUB/DOCX e um formato externo.
4. Criar nota e cartão a partir do leitor; fechar/reabrir e verificar as ligações à origem. Testar as ferramentas de leitura existentes.
5. Rever cartões, escolher dificuldades, pausar/retomar e consultar datas. Importar um pequeno TSV.
6. Criar uma pergunta aberta e outra de escolha múltipla; concluir um teste, criar cartão de um erro e consultar o resultado.
7. Agendar tarefa, marcar conclusão, iniciar foco, pausar, sair/regressar, terminar e verificar minutos reais sem duplicação.
8. Exportar ZIP, testar restauro num conjunto de ensaio, abrir os materiais restaurados e verificar notas, cartões e anotações do leitor.
9. Remover uma disciplina confirmando que os registos passam para Geral; remover uma cópia confirmando que o original permanece.

**Validação local:** 303 verificações JVM, 45 grupos SQLite e 3 verificações de extracção PDF no anfitrião, total 351; compilação, assinatura v2/v3 e CRC do APK. Pacote/autoridades separados e APK da edição 9 sem alteração. Não houve execução num aparelho/emulador Android; instalação, aparência, IME, selecção, importação via selector, cópia/restauro integral no Android e continuidade do temporizador precisam de teste físico.
