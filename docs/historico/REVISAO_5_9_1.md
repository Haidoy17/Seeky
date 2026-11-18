# Revisão Seeky 5.9.1

Base: entrega 5.9. Versão Android: 5002401. Assinatura visual: Haidoy Carlos Mautempo.

## Problemas e alterações

**A preparar o fundo durante muito tempo.** A 5.9 descodificava a faixa completa antes de inicializar a voz. Além disso, cada tentativa de obter um buffer de entrada podia bloquear 10 ms antes de drenar a saída, acumulando espera em milhares de blocos. BackgroundPcm passa a ser um descodificador por procura: abre a origem, mas só processa os frames necessários à mistura do próximo trecho. A entrada é consultada sem espera, a saída é drenada em lotes e só se espera brevemente por saída quando não há progresso. A notificação apresenta fases e progresso. A cache concluída continua utilizável, e uma espera sem saída por 30 segundos termina com erro recuperável.

**O fundo parava ao fechar o painel.** O botão anterior usava um MediaPlayer de pré-escuta pertencente à janela, encerrado no dismiss. BackgroundAudioService passa a ser o único proprietário da reprodução ambiente. É um serviço mediaPlayback com notificação e controlos, com wake mode apenas para reprodução. Fundo manual e fundo acompanhado pelo TTS partilham o mesmo leitor. ReadingAudioFocus partilha o foco entre voz e música, evitando que uma interrompa a outra. Perda externa de foco e retirada dos auscultadores continuam a pausar o áudio.

**Escolher online não aplicava a voz.** O atalho Google online seleccionava uma opção, e o botão Usar esta voz estava recolhido nas opções. O atalho agora aplica a melhor voz de rede anunciada pelo motor e velocidade 1×. Usar esta voz fica no painel principal. A mudança numa leitura activa conserva o ponto e o estado de pausa.

**Continuidade e identificação da voz.** Os pedidos de rede usam até 3400 caracteres, respeitando frases e limites do motor. Na leitura ao vivo há, no máximo, um trecho adicional na fila, submetido com QUEUE_ADD. A pausa cancela os dois pedidos e conserva a posição efectivamente falada, com mapeamento para o texto original. Amostra, leitura e exportação partilham parâmetros de rede; a voz anunciada pelo motor é verificada antes dos pedidos. A notificação distingue voz de rede e offline. Exportação e mistura mantêm o esquema de retoma da 5.9.

## Validação

- Compilação ECJ/D8, empacotamento aapt, alinhamento e assinatura verificados.
- Suite local aprovada, incluindo os novos casos de fila, pausa e callbacks tardios; mantêm-se as 11 verificações de WAV/mistura e 22 de nomes/metadados.
- **51 testes Android 13/API 33 aprovados**. Novos casos verificam reprodução após fechar a janela e destruir o leitor, volume independente, foco partilhado e perda externa, aplicação efectiva da voz Google online, rejeição de troca para local, antecipação sem avanço prematuro, preparação parcial, mistura comparada com a faixa completa, ciclo no fim real, cache concluída e cancelamento.
- O teste do descodificador comprimido percorre o ciclo MediaExtractor/MediaCodec da produção com dados de ensaio e confirma que não há espera por buffer de entrada. Não mede o desempenho de um codec físico.
- Os recursos do visualizador DOCX permanecem iguais aos da 5.9; conserva-se a evidência de apresentação dessa versão como referência, sem nova alegação de validação visual no aparelho.
- A variante assinada mantém pacote `mz.bam.seeky.v51` e certificado da 5.9. O APK sem assinatura mantém `mz.bam.seeky.estudos`. O código distribuído não inclui chaves privadas.

## Limites

Não foi realizado teste físico no Itel A17, síntese de voz Google real ou avaliação auditiva. Não é possível prometer timbre humano para todas as vozes Google, nem certificar que um motor processou internamente cada pedido na rede apenas a partir dos seus metadados Android. A aplicação não inclui novo modelo neural, integração paga, API privada ou chave de serviço. “Melhor voz” significa a maior qualidade declarada pelo motor entre as vozes de rede disponíveis no idioma, com os filtros limpos.

Uma cache parcial de fundo é recalculada se necessário ao retomar; o áudio de voz confirmado permanece conservado. O tempo de síntese e mistura depende do aparelho, do tamanho dos trechos e do serviço de voz. Limites de espaço, formatos e WAV descritos no guia da 5.9 continuam activos. Não houve alterações de backend ou deploy.
