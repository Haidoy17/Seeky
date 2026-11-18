# Seeky 5.11 — continuar leitura, títulos e controlo TTS

Assinatura: Haidoy Carlos Mautempo. Versão Android 5002600 / 5.11.0.

Instale `Seeky_5_11_Continuar_Leitura.apk` sobre a versão anterior, sem desinstalar. Livros, Estudos, progresso, configuração Gemini e trabalhos de narração permanecem.

## Botão circular com livro

Na área **Livros**, o botão circular no canto inferior direito abre o último documento que foi apresentado com sucesso no leitor. A retoma usa as posições já guardadas pelo leitor: página PDF, capítulo, deslocamento vertical, modo original/texto e formato da janela. No PDF paginado são guardados também o zoom e a posição da página ampliada. O botão não envia um comando para voltar ao primeiro capítulo.

A posição final é guardada ao sair ou colocar o leitor em segundo plano. Ao actualizar, a aplicação tenta recuperar uma leitura anterior que tenha posição guardada, pela ordem dos materiais mais recentemente usados. Sem histórico, o botão fica atenuado até abrir um livro. Se o livro foi eliminado ou deixou de estar disponível, apresenta aviso. Documentos externos continuam a depender do acesso autorizado à origem. Alterar o ficheiro, o tamanho do ecrã ou a composição do texto pode mudar a correspondência visual da posição.

## Pausas após títulos

A leitura por voz separa títulos do conteúdo e insere **2 segundos de silêncio depois de cada título identificado**. Usa cabeçalhos h1–h6 dos EPUBs, estilos de títulos DOCX, o índice disponível de PDFs e regras locais para linhas isoladas, capítulos e texto em maiúsculas. A pontuação e as palavras do documento não são substituídas. Texto sem estrutura ou PDF sem texto/índice pode não permitir identificar todos os títulos; as regras não fazem OCR.

A pausa entra na fila do motor Android, funcionando com o leitor em segundo plano. Se o motor não aceitar silêncio na fila, o Seeky usa um atraso cancelável antes de continuar. Pausar, parar, mudar de capítulo ou saltar limpa a fila antiga para evitar leitura atrasada ou repetição de comandos. O fundo de áudio acompanha a pausa.

As **novas narrações guardadas** incluem também estes 2 segundos em PCM. Com fundo, o ambiente continua durante a pausa, no volume escolhido. O checkpoint do texto e do áudio só é confirmado depois da gravação da pausa; retomar não a duplica. Trabalhos antigos mantêm o formato anterior para conservar a compatibilidade da retoma.

## Controlador de voz

No leitor e na janela de voz há capítulo anterior, recuar, pausa/continuar, avançar, capítulo seguinte e parar. Toque no selector **10 s ▾** para escolher **10, 20 ou 30 segundos**. A notificação e os controlos multimédia usam o mesmo intervalo.

**Os saltos em segundos no TTS são aproximados.** O Android TTS não fornece uma duração exacta nem pesquisa temporal no texto ainda não sintetizado. O Seeky estima o tempo pela velocidade, palavras, pontuação e pausas de títulos, e salta para limites de palavras. Pode atravessar capítulos/páginas; conserva o estado pausado se já estava em pausa. Em ficheiros de áudio gravados, a reprodução e a duração seguem o leitor de áudio habitual.

## Verificação

Passaram 103 testes Android/API 33 simulados, incluindo EPUB com deslocamento guardado, PDF com página e zoom, controlador de 30 segundos, salto entre capítulos, fila de silêncio, cancelamento, fundo durante a pausa e retoma de narração antiga. Há ainda testes locais de limites Unicode, navegação estimada e amostras PCM com exactamente 2 segundos de silêncio inserido.

Os testes TTS usam motores simulados e o teste de apresentação PDF usa um substituto de PDFium. Não houve avaliação auditiva no Itel físico nem chamada real ao motor Google online ou à IA. A naturalidade real continua dependente da voz e da ligação.
