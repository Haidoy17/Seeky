# Seeky 5.9.1 — Fundo contínuo e voz

Assinatura: **Haidoy Carlos Mautempo**.

Instale `Seeky_5_9_1_Fundo_e_Voz.apk` sobre a entrega anterior, sem desinstalar. Mantém o mesmo pacote e certificado das variantes independentes, assim como os livros, cadernos e trabalhos de narração da versão 5.9.

## Tocar fundo enquanto lê

No leitor, abra **TTS → Fundo de áudio**, importe uma faixa e toque em **Tocar durante a leitura**. Pode fechar o painel e continuar a ler: a música fica num serviço de reprodução próprio, também com a aplicação em segundo plano. A notificação **Fundo de áudio** permite pausar, continuar e parar. Pode parar também pelo botão do painel.

O volume do fundo continua separado do volume da voz. **Usar na leitura por voz** permite iniciar o fundo juntamente com o TTS. Se o fundo foi iniciado manualmente, continua ao pausar ou terminar o TTS. Há um único leitor de fundo, mesmo quando inicia a voz enquanto a música toca. Uma chamada, outra aplicação que tome o áudio ou a retirada dos auscultadores pode pausar a reprodução.

Ao fechar o painel de vozes, a amostra de voz continua a terminar; a reprodução do fundo já não depende dessa janela.

## Guardar com fundo

A opção **Guardar com fundo** permanece disponível na exportação. A aplicação prepara só o áudio necessário para cada trecho de voz, em vez de descodificar toda a faixa antes de iniciar a narração. O processo também deixou de esperar por uma entrada livre do descodificador enquanto há áudio de saída para receber.

A notificação distingue a geração da voz, a mistura e o progresso de preparação do fundo. Se ainda for necessário processar uma parte longa do fundo, esse trabalho pode demorar conforme o ficheiro e o aparelho; não existe uma promessa de tempo fixo. Uma faixa completamente preparada fica reutilizável ao retomar o mesmo trabalho. Uma preparação parcial pode precisar de ser refeita, sem regenerar os trechos de voz já confirmados.

As gerações por terminar da versão 5.9 continuam disponíveis em **TTS → Gerações**. Pause a geração antiga e retome-a depois de actualizar. Se um codec não responder durante 30 segundos sem produzir áudio, a geração dá um erro recuperável e mantém o progresso, em vez de ficar indefinidamente a preparar.

## Melhorar a voz Google

Abra **TTS → Google online · melhor voz**. Este atalho agora **aplica** a voz de rede disponível com a melhor qualidade anunciada pelo motor e repõe a velocidade em **1×**, mantendo a altura original da voz. Antes, o atalho apenas seleccionava uma opção no painel. Se já estiver a ouvir o documento, a mudança conserva a posição e retoma a leitura se estava em reprodução.

Use **Ouvir amostra** para comparar o resultado. **Usar esta voz** está agora visível no painel principal para confirmar outras escolhas. Pode continuar a escolher idioma, variante e voz nas opções.

Os pedidos online usam mais contexto e o leitor coloca o trecho seguinte na fila antes de acabar o actual, reduzindo interrupções entre pedidos. Amostras, leitura e exportação usam a mesma política de voz de rede. A aplicação verifica se o motor continua a anunciar a voz seleccionada e não aceita uma voz local como substituição silenciosa.

**A qualidade sonora depende das vozes que o Google disponibiliza nesse telefone.** Uma voz marcada como online pode continuar a ter timbre sintético. Esta actualização melhora a aplicação da voz e a continuidade; não transforma uma voz clássica num modelo neural nem acrescenta vozes premium. Actualize o Speech Services by Google e compare as amostras das variantes disponíveis. A aplicação não consegue certificar o processamento interno de rede do motor Google.

## Verificação

Foram aprovados os testes locais e 51 testes no Android 13 simulado, incluindo fundo após fechar painel/leitor, volume separado, foco de áudio partilhado, mistura progressiva, cache, cancelamento, fila de voz, mudança de voz e retoma da exportação. Os testes de descodificador comprimido usam dados determinísticos; não certificam o codec do Itel. Não foi feito teste físico nem avaliação auditiva das vozes Google neste aparelho.
