# Seeky 5.14.3 / Vozes Naturais 1.0.3 — compatibilidade da síntese

O registo enviado pelo utilizador na versão 1.0.2 confirma ligação directa, catálogo e carregamento de Tugão. Cerca de oito segundos após loaded-pt-pt-tugao o cliente recebeu EOF, sem bridge-audio-complete nem bridge-synthesis-error no processo de síntese. Isso é compatível com o encerramento do processo nativo, mas o registo não inclui o motivo de saída do Android. Não é possível distinguir com certeza um aborto nativo, sinal de CPU ou falta de memória apenas por esses eventos. A voz instalada não foi identificada como corrompida.

## Alteração do caminho de geração

NeuralRuntime deixa de chamar generateWithCallback. Usa generate em trechos curtos, devolvendo os blocos PCM pelo mesmo canal protegido por assinatura. A conversão e transmissão PCM passam a executar-se inteiramente na thread Java que chama a geração. O limite é de 48 caracteres em dispositivos com classe de memória de até 128 MB ou identificados como low-RAM, e de 96 nos restantes. Os cortes respeitam palavras, pontuação e pares Unicode, mantendo os offsets de origem. O modelo e os pesos não mudaram. Há apenas uma thread e uma instância neural, com memória do áudio limitada por trechos; isto não elimina a memória necessária ao modelo e às operações ONNX. O cancelamento é verificado entre trechos e durante a saída PCM.

Existe um relato upstream de falhas em generateWithCallback no Android: https://github.com/k2-fsa/sherpa-onnx/issues/3492 . O autor assinalou que a sua primeira proposta não resolvia tudo; o mantenedor sugeriu actualizar o ONNX Runtime. Esse relato não prova a causa no ITEL. A alteração aqui evita o caminho de callback, sem atribuir uma causa nativa única nem substituir bibliotecas sem validação.

## Recuperação e diagnóstico

Quando o processo termina, o leitor invalida o catálogo antigo, descarta respostas tardias, desactiva os comandos associados à ligação perdida e permite tentar novamente. A mensagem deixa de afirmar que falta uma actualização do módulo. O cliente distingue perda do processo de um EOF normal de transporte quando o Binder já está morto. O diagnóstico inclui até quatro saídas recentes do processo :voice através de ApplicationExitInfo: motivo, estado/sinal e RSS, sem texto dos documentos nem descrição livre da falha. Registos generate-safe-start/done localizam o trecho de geração sem guardar o seu conteúdo.

## Verificações e limites

234 testes Robolectric do leitor e 11 do módulo passaram. Incluem uma nova regressão de perda da ligação depois de o catálogo estar disponível. Os testes locais exercitam os limites de 16/48/96/240 caracteres, retenção de todas as palavras, offsets e Unicode, além de transferência/retoma e PCM. O total consta de release.json.

Um teste JNI real em Linux usou Tugão original, os mesmos metadados, tokens, dados eSpeak, uma thread e generate sem callback. A frase do teste instalado foi dividida em três partes, de até 46 caracteres, e gerou 92672 amostras a 22050 Hz com RMS 0,1648. A amostra WAV e o resultado acompanham as evidências. O teste usa o wrapper JVM e o JNI Linux da mesma versão Sherpa 1.13.8; não testa JNI ARM/ART. O ficheiro de classe da aplicação também é verificado para confirmar que não chama generateWithCallback.

O transporte entre dois processos Android, a gravação WAV, a reprodução, a marcação e a preservação de uma voz instalada foram verificados na versão 5.14.2/1.0.2. Esses resultados são conservados como evidência anterior do protocolo, que não mudou. Não foi repetida a síntese neural num Android físico nesta entrega. A alteração é uma correcção de compatibilidade, não uma confirmação de resolução no aparelho do utilizador. Se houver nova interrupção, Saídas recentes do motor permitirá identificar o motivo registado pelo Android.

Os pacotes, o certificado e voices-v1 são mantidos. Não é necessário desinstalar nem baixar os modelos de novo. O áudio guardado continua WAV; não foi introduzido M4A. Nenhuma chave pessoal ou serviço pago foi utilizado.

Haidoy Carlos Mautempo
