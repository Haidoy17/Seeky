# Seeky 5.10.4 — verificar e remover duplicados

Assinatura: Haidoy Carlos Mautempo. Instale Seeky_5_10_4_Duplicados.apk sobre a entrega anterior, sem desinstalar.

Abra **Livros → opções (⋮) → Verificar e remover duplicados**. A opção verifica e limpa as cópias privadas da biblioteca. No fim mostra o número de cópias apagadas, espaço libertado e avisos. Pode cancelar; as operações concluídas permanecem e uma nova verificação termina a limpeza pendente. Mantenha o aplicativo aberto durante a operação.

Ficheiros com o mesmo nome mas conteúdos diferentes são conservados. A verificação agrupa tamanhos iguais, calcula SHA-256 dos candidatos, confere a identidade guardada e compara os bytes antes de apagar uma cópia. Ficheiros indisponíveis, com identidade alterada, externos à pasta privada ou através de ligações simbólicas não são apagados. Os originais fora do Seeky permanecem intactos.

Quando um livro está em vários módulos, o Seeky conserva os registos de cada módulo, com os seus IDs, notas, cartões, perguntas, marcadores e posições. Esses registos passam a apontar para um único ficheiro. A estante apresenta uma só capa para essas referências verificadas, escolhendo a de maior progresso. As ligações nos cursos continuam disponíveis.

As opções globais da estante para marcar lido/não lido passam a actuar sobre essas referências partilhadas. A limpeza, por si só, não altera as percentagens ou posições individuais existentes. Remover posteriormente uma referência de um módulo só apaga o ficheiro privado quando não restarem referências a ele.

Nas importações seguintes, o Seeky verifica o conteúdo da cópia já existente no mesmo destino antes de a considerar repetida. Uma cópia ausente ou alterada é indicada como falha, em vez de ignorar silenciosamente o livro novo. Não se substitui automaticamente essa cópia.

A limpeza limita-se às cópias privadas da biblioteca; não percorre o armazenamento do telemóvel. Não activa serviços de IA nem envia livros à Internet. As alterações locais podem ser sincronizadas pelas definições de sincronização já existentes no aplicativo.

O relatório técnico inclui os testes executados. Não houve ensaio no Itel físico nem acesso à biblioteca instalada no telemóvel nesta sessão: a limpeza é executada ao tocar na opção no aplicativo actualizado.
