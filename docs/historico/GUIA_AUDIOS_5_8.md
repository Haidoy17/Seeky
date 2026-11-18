# Seeky 5.8 — Google online e áudios com títulos

Instale **Seeky_5_8_Audios.apk** por cima da variante independente anterior, sem desinstalar. O pacote e o certificado continuam iguais. A assinatura visual é **Haidoy Carlos Mautempo**.

## Voz online gratuita, sem chave de API

O RHVoice foi removido dos controlos e da lista de motores do Seeky. Se era a escolha guardada, o Seeky passa essa preferência para Google online.

No leitor, abra **TTS → Google online · melhor voz**. O Seeky selecciona o motor Google instalado no Android, activa Online, limpa os filtros de voz e escolhe uma voz de rede pela maior qualidade declarada pelo motor. Use **Ouvir amostra** e confirme em **Usar esta voz** ou **Ler a partir daqui**. A comparação não altera a voz guardada antes da confirmação.

A síntese usa o Google Speech Services do Android: não precisa de chave de API nem de conta Azure/ElevenLabs. O modo Online exige Internet. A disponibilidade, os sotaques e a naturalidade das vozes dependem da versão do motor Google e das vozes que ele disponibiliza no telefone; não foi criado outro serviço gratuito externo. Se não houver uma voz de rede no idioma escolhido, o Seeky informa a indisponibilidade e mantém Online, sem trocar silenciosamente para uma voz offline.

Para outras variantes, use **Mais vozes Google** e **Actualizar lista de vozes**. As amostras, os favoritos e a identificação feminina/masculina da versão anterior são conservados.

## Nomes dos novos áudios

As novas narrações deixam de usar apenas nomes genéricos. Exemplos:

- `Estudos de Ciências — Introdução — 0001.wav`
- `Estudos de Ciências — Energia e movimento — 0002.wav`

O título do livro aparece primeiro, seguido do título do capítulo ou subtítulo e da numeração. Quando o título é muito longo, é encurtado no nome do ficheiro para conservar o número e a extensão. A identificação completa, dentro dos limites de metadados, também é guardada no WAV.

Para EPUB, o Seeky usa o nome do índice ou um cabeçalho do capítulo. Para DOCX, reconhece estilos de título/subtítulo. Nos PDF, usa o título do documento e o cabeçalho mais específico do índice que corresponde à página. Quando não existem esses dados, usa uma primeira linha curta que pareça um título; caso contrário, conserva o título do livro e uma indicação de página/capítulo.

O título editado por si na biblioteca tem prioridade sobre o título interno. Quando a biblioteca ainda usa o nome original do ficheiro, o título interno do livro tem prioridade. Numa selecção, o nome identifica o livro e a selecção.

Partes de áudio extensas continuam a ser divididas automaticamente. A primeira e a segunda parte de um capítulo dividido ficam, por exemplo:

- `Estudos — Introdução — 0001-01.wav`
- `Estudos — Introdução — 0001-02.wav`

Os números permitem identificar o capítulo original e a parte. Os títulos vêm primeiro, conforme solicitado; para ordenar a reprodução por capítulos, use o número de faixa num leitor que o suporte.

## Informações dentro do WAV

Cada novo áudio inclui etiquetas RIFF INFO com título, livro/álbum, autor quando o documento o fornece, número de faixa e identificação do Seeky. Essas informações podem aparecer nos leitores que reconhecem metadados WAV. O autor do documento é conservado como autor; a assinatura do aplicativo fica na identificação do software.

A gravação conserva as amostras de áudio produzidas pela voz, sem recompressão. O áudio já guardado pode ser ouvido sem Internet. Gerar áudio com uma voz online exige Internet e depende de o motor permitir síntese em ficheiro.

Os nomes novos e as etiquetas aplicam-se às narrações geradas nesta versão. Ficheiros de áudio antigos não são renomeados automaticamente. A biblioteca, os gestos de leitura, os estudos e a exportação de capítulo, intervalo ou livro inteiro são conservados.
