# Seeky 5.9 — Leitura e narração

Assinatura: **Haidoy Carlos Mautempo**.

## Instalar

Instale `Seeky_5_9_Leitura_e_Narracao.apk` sobre a variante independente anterior do Seeky. Mantém o identificador `mz.bam.seeky.v51` e o certificado das entregas anteriores. Não desinstale a aplicação para actualizar, pois isso apaga os seus dados privados e as gerações por terminar. A variante original de outro autor/certificado pode continuar instalada separadamente.

## Explorar e ler

Os botões Nova pasta, Exibir e Actualizar têm espaçamento e contornos próprios. Foram retiradas acções repetidas dos menus de navegação e TTS.

EPUB e DOCX abrem por defeito em **Original**. O EPUB utiliza o HTML, as folhas de estilo, as imagens e as fontes incluídas no livro. O DOCX utiliza um novo visualizador local de páginas, com estilos, tabelas, imagens, cabeçalhos e rodapés. O documento de origem não é regravado.

Use **Visualizar** para ajustar o zoom. Pode avançar e recuar pelos botões ou arrastando horizontalmente o dedo, desde que não esteja a seleccionar texto ou a ampliar uma área. **Texto fluido** continua disponível como escolha explícita para adaptar tamanho e espaçamento das letras.

Um DOCX complexo pode apresentar diferenças em relação ao Word, sobretudo com fontes ausentes, objectos especiais ou paginação avançada. Para fidelidade de impressão, use Abrir noutra aplicação ou uma versão PDF. Documentos demasiado grandes mostram alternativas em vez de serem convertidos silenciosamente para texto. O visualizador original DOCX aceita até 32 MB de ficheiro e 64 MB de conteúdo descomprimido. Não executa scripts nem carrega recursos da Internet presentes nos documentos.

## Guardar e retomar narração

1. No leitor, abra **TTS → Guardar narração**.
2. Escolha documento/livro inteiro, parte actual, intervalo ou selecção, conforme o documento.
3. Escolha **Em partes** ou **Um único ficheiro WAV**, com ou sem fundo.
4. Seleccione uma pasta de destino com acesso de escrita.

Não é necessário que o livro tenha capítulos. Um documento sem capítulos pode ser guardado inteiro. Os nomes mantêm livro, título/subtítulo e numeração; os WAVs incluem também metadados de livro, autor e faixa quando disponíveis.

Cada trecho de voz concluído é confirmado no armazenamento privado. Se ocorrer um erro, abra **TTS → Gerações**, seleccione a geração e toque em **Retomar**. Também existe Retomar na notificação de falha. O último trecho ainda não confirmado é repetido; o áudio confirmado e os ficheiros concluídos são conservados.

Pode pausar pela notificação ou em **TTS → Gerações → Pausar**. Não é necessário começar uma nova exportação. Se a pasta deixar de permitir escrita, use **Mais opções → Escolher outra pasta**. Um WAV já sintetizado é novamente copiado sem voltar a gerar a voz. Os ficheiros já guardados na pasta anterior permanecem lá.

**A retoma aplica-se às gerações iniciadas na versão 5.9.** As versões anteriores não guardavam o estado necessário para recuperar os seus trechos incompletos. Para um trabalho antigo, escolha um intervalo a partir da primeira parte que falta.

Enquanto uma geração estiver por terminar, o Seeky conserva uma cópia privada do documento de origem e os áudios temporários. Estes são removidos ao concluir ou ao escolher **Descartar parcial**. Descartar não elimina os WAVs já exportados. Não limpe os dados da aplicação durante uma geração pendente.

## Livros longos

Já não existe um limite de uma hora para o WAV completo. O áudio é escrito em disco em pequenos blocos, sem carregar a gravação inteira na memória. **Em partes** divide por capítulo e, se necessário, em segmentos de aproximadamente 128 MB. **Um único ficheiro WAV** junta o intervalo escolhido até ao limite do formato RIFF, próximo de 4 GB.

É necessário espaço para o áudio temporário e a cópia final. Por exemplo, uma hora de voz mono PCM16 a 24 kHz ocupa aproximadamente 173 MB. O fundo descodificado também ocupa espaço temporário. Se a duração prevista for muito grande, prefira partes desde o início.

Páginas digitalizadas sem texto exigem OCR, que esta versão não acrescenta. Os limites existentes de extracção de documentos muito grandes continuam activos; uma parte truncada provoca um aviso em vez de guardar uma narração incompleta como se estivesse completa.

## Fundo de áudio

Em **TTS → Fundo de áudio**, escolha um ficheiro de música/ambiente do telefone. Use **Ouvir fundo** para experimentar e o controlo **Volume do fundo** para ajustar. Este controlo não altera o volume da voz nem o volume geral do telefone.

**Usar na leitura por voz** acompanha o TTS, incluindo em segundo plano, e pausa com a narração. O fundo repete em ciclo se for mais curto que a leitura. O botão Parar fundo termina a pré-escuta.

Ao guardar, marque ou desmarque **Guardar com fundo**. Esta escolha é independente da leitura ao vivo. A faixa e o volume ficam associados à geração para que uma retoma use a mesma mistura. Alterações no painel passam a valer para novas gerações. O limite de importação do fundo é 256 MB; o áudio descomprimido tem um limite próprio para proteger o armazenamento.

## Vozes e falhas do motor

Mantêm-se a comparação de amostras e as vozes online/offline do motor Google instalado, sem chave de API. A qualidade depende da voz disponibilizada pelo Google no telefone. Não são instalados Edge ou RHVoice.

Algumas vozes que conseguem falar ao vivo não permitem gerar um ficheiro. O Seeky tenta novamente com trechos menores e conserva o progresso se o motor continuar a falhar. Em Mais opções de uma geração sem áudio parcial em curso, pode retomar com a voz seleccionada. Não há substituição silenciosa por uma voz de qualidade diferente.

## Verificação

Foram aprovados os testes locais, 42 testes de ciclo de vida e exportação no Android 13 simulado e um teste de apresentação DOCX em Chromium. O WAV com mais de uma hora foi verificado com um ficheiro de teste esparso. Não foi realizada uma gravação real de uma hora nem um teste físico no Itel A17; a reprodução dos codecs e a voz Google devem ser confirmadas nesse aparelho.
