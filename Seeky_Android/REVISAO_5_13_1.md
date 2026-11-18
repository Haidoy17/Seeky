# Seeky 5.13.1 — correcção da demora entre páginas

Base: Seeky 5.13.0 · versão 5.13.1 / 5002900 · Haidoy Carlos Mautempo.

## Causa verificada no código

A preparação antecipada guardava apenas o texto da parte seguinte. O motor TTS recebia o primeiro trecho dessa parte depois de terminar a anterior, através de um novo QUEUE_FLUSH. Assim, a preparação do áudio online só começava na mudança, e a fila era reiniciada. O cálculo do hash e da identificação dos títulos ainda ocorria nessa passagem. A validade do texto antecipado dependia também da revisão global dos estudos, que muda ao actualizar o progresso.

Dois testes novos falharam no código da 5.13, demonstrando a ausência do envio antecipado e o reinício da fila. O registo está em `evidence/v5131/before-fix.log`.

## Correcção

- Prepara texto, hash e títulos da próxima parte uma única vez; mantém apenas uma parte seguinte, com limite de 64.000 caracteres e reserva de memória.
- Envia o primeiro trecho seguinte com QUEUE_ADD enquanto os últimos trechos da parte actual ainda tocam. A passagem automática usa o áudio já colocado na fila, sem nova submissão ou acesso obrigatório ao trabalhador de documentos.
- Actualiza a página e a posição guardada após a parte anterior terminar ou o motor confirmar o início da próxima. Preparar texto não avança o progresso.
- Usa identificadores de fala únicos entre cursores para rejeitar eventos atrasados depois de pausar, recuar ou trocar de parte.
- Preserva as pausas após títulos e o temporizador Fim do capítulo, incluindo quando é activado depois da antecipação. Mantém as reacções à perda de foco e aos controlos do utilizador.
- Invalida o áudio antecipado quando o caderno ou OCR muda. O progresso de estudo deixa de invalidar essa preparação. Mantém uma passagem normal se o motor recusar o áudio antecipado ou a parte não estiver preparada.
- Trata espaços inseparáveis no fim das páginas como espaços; renova a protecção de execução durante as falas e as passagens para manter a leitura prolongada com o ecrã apagado.

O formato WAV, a base de dados e as funções da 5.13 foram mantidos. Esta correcção actua no leitor TTS em directo e não altera as gravações existentes.

## Validação

A bateria final contém 222 testes Android simulados: os 204 da entrega anterior e 18 testes específicos da passagem de páginas. Inclui voz online simulada, EPUB com várias partes, PDF de duas páginas com OCR guardado, trabalhador de documentos bloqueado, eventos fora de ordem, pausa/retoma, títulos nos dois lados da mudança, temporizador, revisão do caderno, falha da antecipação, leitura contínua desligada e espaços inseparáveis.

A regressão local também foi executada, incluindo os testes de cursor, pausas, referências bíblicas, WAV, cadernos e recuperação. O APK é conferido quanto ao certificado, identificador de actualização, versão, DEX, recursos e bibliotecas ARM. Os resultados finais acompanham o código-fonte em `evidence/v5131`.

Não houve acesso ao Itel nem audição de vozes reais. Os testes demonstram o envio antecipado e a continuidade sem esperar pelo trabalhador; não medem uma redução em milissegundos no aparelho ou garantem ausência total de espera de rede. Capítulos muito grandes ou não preparados a tempo mantêm o carregamento normal.

## Actualização

Instale `Seeky_5_13_1_Leitura_Continua.apk` sobre a versão actual, sem desinstalar. O identificador e o certificado das entregas anteriores são conservados. Para passar automaticamente, mantenha activada a opção Continuar nas páginas seguintes no TTS.
