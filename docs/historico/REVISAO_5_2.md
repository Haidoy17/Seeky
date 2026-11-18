# Seeky 5.2 — cursos, módulos e disciplinas

Revisão sobre a 5.1, em 27/10/2026. Assinatura visual preservada: Haidoy Carlos Mautempo.

## Organização

Estudos passa a abrir na lista de cursos na primeira utilização desta versão. A estrutura é:

**Curso → Módulo ou disciplina → Biblioteca e atividades**

- Criar, editar e remover cursos, com nome e descrição.
- Criar unidades do tipo Módulo ou Disciplina dentro de um curso. O formulário permite também mover uma disciplina existente para outro curso ou deixá-la sem curso.
- Cada unidade abre a sua biblioteca e conserva os separadores Visão geral, Biblioteca, Cadernos, Cartões, Perguntas, Agenda e Progresso. As notas, revisão, sessões de foco e tarefas usam a mesma identidade de disciplina já existente.
- Importar um ou vários materiais pelo seletor de ficheiros do Android: PDF, livros, texto, imagens, áudio, vídeo e outros formatos didáticos já suportados. A importação faz cópias locais, não envia ficheiros para um servidor. Abrir formatos de escritório/codec não suportados pode exigir outra aplicação.
- O curso pode ser consultado como conjunto, mas a importação, as novas atividades e os testes em pausa devem ter uma unidade de destino selecionada.
- Os cartões dos cursos mostram números de unidades e materiais; as listas são limitadas a 500 cursos/unidades para conter o consumo de recursos. A biblioteca mantém a paginação existente.
- Todos os estudos dá acesso à organização anterior e aos registos sem curso. Remover um curso conserva as suas unidades e materiais fora de cursos. Remover uma unidade conserva materiais/atividades em Geral. Estas ações não eliminam os originais do armazenamento.
- Novos títulos e ações têm tradução English, mantendo os identificadores internos estáveis.

## Preservação e backups

A base de Estudos passa do esquema 2 para o 3. A migração cria cursos e acrescenta course/kind às disciplinas existentes, com valores 0/subject. Não cria cursos fictícios, não copia materiais nem remove as notas anteriores. A migração é executada dentro da transação do SQLiteOpenHelper. Os esquemas do leitor e do catálogo não mudam.

O backup ZIP passa a declarar o formato `seeky-studies-2`, contendo cursos e as ligações entre entidades. A versão 5.2 aceita também backups antigos `seeky-studies-1`; estes entram sem cursos quando não os contêm. Aplicações anteriores rejeitam o formato novo, em vez de restaurar parcialmente uma organização que não conhecem. Não faça downgrade para 5.1 depois de migrar a base.

A importação aditiva remapeia IDs de cursos antes dos módulos, e módulos antes de materiais e atividades. Também ajusta os IDs dentro da lixeira e da prática em pausa, evitando ligar um item importado a uma disciplina local com o mesmo número. Se já existir um teste em pausa no destino, a importação de outro é recusada com rollback; conclua ou descarte o teste local primeiro. A importação ZIP aditiva continua a não ser uma deduplicação global.

A recuperação exata de snapshots inclui cursos, valida referências e restaura a preferência do curso selecionado. O backend remoto não foi alterado nem testado ao vivo. O pré-limite estrutural do manifesto JSON também foi aplicado à leitura dos ZIPs de Estudos.

## Verificação

- Compilação completa com SDK/Build Tools 36 e ECJ 3.41.0, Java/recursos/DEX.
- Suíte local completa existente passou, incluindo os testes de desempenho/Flash introduzidos na 5.1.
- 40 verificações anteriores de cadernos e 17 de persistência passaram.
- **38 verificações novas executam StudyStore, StudyState, StudyBackup e StudySnapshot reais sobre SQLite JDBC.** Incluem migração 1→3 e 2→3, falha durante migração com rollback, filtros entre cursos, transferência de módulo, remoção sem apagar materiais, backup com IDs em colisão, remapeamento de marcadores/lixeira/prática, importação antiga, referências inválidas e recuperação exata.
- Assinatura, CRC, alinhamento e identidade do APK instalável foram verificados. A chave e o pacote da instalação separada são os mesmos da 5.1 enviada nesta conversa.

Os testes JVM usam contratos Android limitados; não reproduzem o pool SQLite/WAL do Android, o ciclo de vida, a UI, SAF ou o sistema de ficheiros de um OEM. Não houve instalação no Itel A17 nem emulador. Exportação/importação pela interface, rotação, escolha múltipla de ficheiros, permissões e atualização por cima devem ser conferidas no aparelho antes de uma distribuição comercial.

## Instalar

`Seeky_5_2_Cursos.apk` usa o pacote `mz.bam.seeky.v51` e o mesmo certificado local da instalação separada 5.1. Pode atualizar essa instalação sem desinstalar. A versão original anterior, de pacote `mz.bam.seeky.estudos`, continua separada; o novo APK não migra os seus dados automaticamente.

`Seeky_5_2_sem_assinatura.apk` usa o pacote original e precisa da chave original de assinatura para ser instalado como atualização. O ZIP de fontes não inclui chaves privadas.

As evidências desta revisão estão em `evidence/v52_*`. Os relatórios e evidências das outras versões são históricos.
