# Seeky 5.12 — biblioteca, leitura e estudos

Actualize instalando `Seeky_5_12_Biblioteca_Estudos.apk` por cima da versão independente anterior. Não desinstale: a desinstalação apaga os livros privados da aplicação. Esta entrega mantém o identificador e o certificado das versões independentes anteriores, incluindo 5.11.1.

A assinatura visual continua a ser **Haidoy Carlos Mautempo**. Todos os áudios novos permanecem **WAV**, incluindo narrações, mistura com fundo e notas de voz.

## Onde encontrar as novidades

Em **Preferências → Ferramentas** encontra desempenho, pronúncia, Bíblias, OCR, caderno de revisão e cópia completa. No leitor, abra **Organizar → Ferramentas de leitura**. As imagens têm acesso às ferramentas na barra superior. O idioma inglês também inclui os novos controlos.

### Leitura mais leve

O modo Automático reduz os caches e animações quando o Android indica pouca memória ou um limite de 128 MB. Pode escolher Leve ou Completo em Ferramentas → Desempenho. Reabra os documentos depois de mudar o modo. O cache do PDF contínuo mantém a página visível mesmo quando remove outra imagem da memória.

O botão circular com o livro continua a abrir a última leitura, incluindo a posição guardada. Os ajustes do EPUB e o visualizador Original foram preservados.

### Biblioteca organizada

Em Livros, toque no título da estante ou abra **Opções → Colecções e grupos**. Pode filtrar por colecção, assunto, série e autor, além dos estados de leitura existentes. No menu de cada capa, **Organizar livro** permite atribuir assunto, série e colecções. A mesma cópia do livro nos Estudos partilha esta organização.

Eliminar uma colecção conserva os livros. A verificação de duplicados e a eliminação de livros continuam disponíveis.

### Pronúncia e prévia

Em **Ferramentas → Dicionário de pronúncia**, acrescente uma palavra ou sigla e a forma como a voz deve lê-la. As regras aplicam-se ao ouvir e ao guardar narração, sem alterar o texto do livro. Por exemplo, uma sigla pode ser lida por extenso. Pode editar e eliminar as entradas; o limite é 100 regras.

Na preparação da narração use **Prévia da narração** para ouvir até 30 segundos do trecho real com a voz, velocidade e fundo seleccionados. A prévia não guarda um ficheiro nem confirma outra voz por si só. Pode interrompê-la imediatamente.

As vozes continuam a ser fornecidas pelo motor Android instalado, incluindo as vozes Google disponíveis online e offline. O Seeky melhora a separação do texto, pausas e pronúncia; a naturalidade final depende da voz fornecida pelo motor. Esta versão não inclui um serviço de voz pago ou vozes proprietárias descarregáveis.

### Narração em WAV com índice

Os WAVs novos incluem marcadores de capítulos e títulos no próprio ficheiro, além do título, autor e numeração existentes. Ao abrir uma narração, use **Capítulos e títulos** para saltar para o ponto escolhido. Em **Ferramentas de leitura → Narrações guardadas**, encontra os áudios deste livro.

O índice fica dentro do WAV e pode ser recuperado mesmo sem o registo local. Outros leitores podem ignorar os marcadores e continuar a reproduzir o áudio normalmente.

O progresso da geração continua a ser guardado por bloco. Ao retomar, o trabalho conserva as regras de pronúncia que tinha ao começar. Os trabalhos anteriores mantêm a compatibilidade com o formato de retoma antigo.

As pausas de dois segundos depois dos títulos, as referências bíblicas por extenso e os saltos de 10, 20 ou 30 segundos continuam disponíveis. Nos saltos de TTS o tempo é aproximado; nos WAVs a procura usa a posição do áudio. Os ficheiros WAV muito grandes continuam sujeitos ao limite RIFF de aproximadamente 4 GB e são divididos quando necessário.

### Temporizador de sono

Abra **Temporizador de sono** nas ferramentas de leitura ou no menu do áudio. Escolha 15, 30, 45, 60 ou 90 minutos, outro valor entre 1 e 240, ou Fim do capítulo. A opção actua sobre a leitura por voz e sobre o fundo, mesmo com a janela do leitor fechada. Pode cancelar o temporizador no mesmo menu.

No TTS, o fim de capítulo corresponde à parte de leitura actual do documento. Num WAV com índice, corresponde ao próximo marcador; sem índice, ao fim do ficheiro. EPUBs com vários ficheiros dentro de um capítulo podem ter fronteiras por parte do livro.

### Notas de voz

Em **Ferramentas de leitura → Notas de voz**, grave uma nota ligada ao livro, à página ou parte e à posição seleccionada. O microfone pede autorização na primeira utilização. Pode ouvir, alterar o título, voltar à passagem e eliminar a nota.

Cada nota é um WAV mono a 16 kHz e pode durar até dez minutos. Ao sair da janela, a gravação em curso termina e guarda o que já foi captado. O fundo é suspenso durante a gravação e retomado quando aplicável.

### Acessibilidade

Em **Acessibilidade da leitura**, active uma régua que pode arrastar pela margem, ajuste o espaço entre letras ou destaque o parágrafo actual. O destaque de parágrafo acompanha o modo Texto fluido e a leitura por voz. Estas opções não alteram o ficheiro original.

### OCR de páginas digitalizadas e imagens

Num PDF ou imagem, escolha **Reconhecer texto desta página** e seleccione português ou inglês. Na primeira utilização, descarrega o modelo do idioma: cerca de 2 MB para português ou 4 MB para inglês. Também pode gerir esses modelos em **Ferramentas → Dados de OCR**.

Depois da instalação dos modelos, o reconhecimento funciona no aparelho, sem Internet. Não envia a página para um serviço externo. Reconhece uma página de cada vez para limitar a memória. O texto fica disponível para copiar, pesquisar, ouvir ou usar no assistente, e o original é conservado. Em imagens, abre uma cópia de texto; no PDF, abre o modo de texto dessa página.

O OCR pode trocar caracteres e precisa de revisão, sobretudo em fotografias inclinadas, páginas desfocadas, tabelas ou manuscritos. Esta entrega inclui o motor para ARM de 32 e 64 bits. Os modelos não são incluídos no APK.

### Passagens bíblicas dentro do Seeky

Importe uma Bíblia em **Ferramentas → Bíblias importadas**. Aceita ficheiros UTF-8 TXT, CSV, TSV e USFM, com um versículo por linha. Exemplos:

```text
Isaías 5:6 Texto do versículo
João 3:16 Texto do versículo
```

```csv
Isaías,5,6,"Texto do versículo"
João,3,16,"Texto do versículo"
```

Depois, toque nas referências reconhecidas no modo Texto fluido ou no EPUB Original. O Seeky mostra a passagem da tradução importada. Também existe **Passagens bíblicas** nas ferramentas do leitor, útil nos visualizadores em que o texto original não permite ligações internas. Pode escolher a tradução se importar mais de uma.

Os 73 livros e as regras de leitura de referências da versão anterior foram conservados. O texto de uma tradução bíblica não vem incluído nesta entrega.

### Estudar com Seeky: fontes e cartões

As respostas podem indicar trechos como `[T1]`. Estes identificadores são criados pelo Seeky a partir do material que realmente enviou. Toque num identificador válido ou no botão **Fonte** para ler o trecho original e abrir a sua origem. Identificadores inexistentes não se tornam ligações para páginas inventadas.

Use **Cartões** para pedir perguntas e respostas estruturadas. Em **Criar cartões desta resposta**, reveja e escolha os cartões antes de os guardar no sistema de revisão dos Estudos. Também pode criar um cartão manualmente a partir de uma resposta.

A ligação à IA mantém a configuração pessoal do Gemini e depende da disponibilidade e quota da sua conta. A referência confirma qual trecho foi fornecido; não garante que a interpretação da IA esteja correcta. Esta entrega não activa pagamentos nem inclui uma chave partilhada.

### Caderno de revisão em PDF

Abra **Caderno de revisão em PDF**, seleccione as notas, destaques, perguntas e cartões que pretende e escolha onde guardar. No leitor, a selecção inclui as anotações do livro; nos Estudos pode preparar a revisão da disciplina. O PDF inclui títulos, origem, paginação e assinatura.

Para limitar a memória e o tamanho, prepare cadernos com até 150 páginas; selecções maiores pedem menos itens. O documento original continua intacto.

### Cópia completa e recuperação

Em **Ferramentas → Cópia e recuperação**, guarde um ZIP completo e escolha uma pasta com espaço suficiente. Inclui livros copiados para a biblioteca privada, cursos, módulos, cadernos, perguntas, cartões, anotações, marcadores, progresso, colecções, dicionário, OCR reconhecido, notas de voz e o fundo importado.

Não inclui palavras-passe ou chaves da IA, conversas privadas do assistente, trabalhos de narração pendentes nem os WAVs de narração que guardou em pastas externas. Copie também essas pastas de áudio se mudar de aparelho. Os ficheiros apenas abertos fora da biblioteca não são importados automaticamente pela cópia.

Para recuperar, seleccione o ZIP. O Seeky verifica a integridade antes de apresentar a quantidade de livros, cursos e notas. A recuperação substitui os dados locais após tocar em Recuperar, pausa os serviços e guarda antes um ponto de recuperação local. Pode voltar a esse ponto em **Recuperações locais anteriores**. Esses pontos conservam os ficheiros privados anteriores e ocupam espaço no aparelho.

Mantenha a janela Ferramentas aberta durante cópias ou recuperações grandes. Pode cancelar a preparação; o cancelamento fica bloqueado na fase curta em que os dados são aplicados. Depois de recuperar, o Seeky reabre a biblioteca. As credenciais pessoais já configuradas no aparelho não são exportadas nem substituídas pela cópia.

## Verificação desta entrega

Passaram 138 testes Android simulados em API 33 e as suites locais de biblioteca, estudos, voz, importação, retoma e IA. Os modelos OCR reconheceram páginas de teste reais em português e inglês no computador de verificação.

Não houve acesso ao Itel físico, audição de vozes Google reais ou chamada real ao Gemini. O desenho do PDF foi conferido em Skia com uma ponte de teste para a codificação do PDF; o codificador PDF nativo do Android e as bibliotecas OCR ARM precisam ainda de validação no aparelho. Consulte o relatório técnico para os limites dos testes.
