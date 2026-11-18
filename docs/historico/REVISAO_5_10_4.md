# Revisão Seeky 5.10.4

Base 5.10.3; versionCode 5002504; esquema de Estudos mantido em 3.

Acrescentado BookDuplicates, limitado aos ficheiros directos da pasta privada study-materials. Agrupa por tamanho real e confirma SHA-256 dos candidatos contra as identidades guardadas. A exclusão exige comparação integral dos bytes com a cópia conservada e existência de uma referência viva a essa cópia. Não apaga por nome ou apenas por hash guardado. Caminhos exteriores e symlinks são rejeitados.

A limpeza consolida caminhos em transacções SQLite sem apagar IDs de materiais, disciplinas ou cursos e sem modificar bases do leitor, notas, cartões, perguntas ou preferências de leitura. BookQuery mostra uma capa por caminho/hash partilhado, escolhendo maior progresso e actualização; antes da verificação, caminhos distintos continuam visíveis. Um índice adicional de hash/caminho/progresso é criado ao abrir a base para suportar essa consulta. Sem nova versão de esquema ou bibliotecas.

O ficheiro redundante só é apagado depois do commit e se nenhuma referência ainda o utiliza, inclusive referências com representação de caminho diferente que resolve para o mesmo ficheiro. Um diário privado de operações pendentes é persistido antes da alteração da base; após interrupção, a próxima limpeza revalida a cópia conservada e a redundante antes de apagar. Se a cópia principal faltar, o último ficheiro é conservado.

Remover posteriormente um material de curso respeita as referências partilhadas. As acções globais de progresso da estante actualizam as referências do mesmo caminho/hash e a reposição de leitura limpa as posições das respectivas URIs. A limpeza inicial conserva todos os progressos individuais; as identidades URI não mudam.

A importação continua sem o corte de 50 elementos. A deduplicação no mesmo destino compara agora a cópia guardada com a nova em bytes antes de ignorar. Cópias alteradas ou indisponíveis são falhas explícitas, sem substituir anotações.

Testes novos: conteúdos iguais com nomes diferentes em módulos distintos; retenção de notas, marcadores, posições, IDs e progresso; nomes e tamanhos iguais com bytes diferentes; hash guardado adulterado; originais externos e symlinks; cancelamento; diário após commit; protecção da última cópia; remoção de referência partilhada; reposição global de progresso; botão da interface e importação perante cópia alterada.

Limites: não é uma limpeza do armazenamento geral nem uma fusão de documentos parecidos ou edições diferentes. O teste de identidade é de conteúdo binário. A tarefa exige manter a aplicação aberta; cancelamento não desfaz grupos já concluídos. A sincronização de dados usa apenas a configuração pré-existente. Nenhuma biblioteca real do utilizador foi lida, apagada ou transmitida nesta sessão; não houve teste físico no Itel.

Verificação final: **81 testes Android/API 33 simulados** passaram. Certificado, pacote, versão, integridade ZIP e alinhamento do APK confirmados.
