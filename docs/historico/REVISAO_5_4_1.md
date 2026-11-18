# Seeky 5.4.1 — Correcção da abertura de documentos

Versão 5.4.1, código 5001901. Actualização da variante independente Seeky 5.4. Assinatura visual: Haidoy Carlos Mautempo.

## Falha encontrada

O diagnóstico fornecido regista RuntimeException no Android API 33, com heap máximo de 128 MB. O registo da versão 5.4 mostrava apenas a excepção exterior do Android, sem a sua causa.

Foi reproduzida uma falha determinística no código 5.4.0: ReaderActivity.onCreate consultava Window.getInsetsController antes de setContentView criar a janela decorativa. No Android 13, PhoneWindow.getInsetsController acedia a mDecor ainda nulo e provocava NullPointerException. Quando a actividade é iniciada pelo sistema, esta falha pode ser envolvida em RuntimeException de arranque, compatível com o diagnóstico apresentado. Acontecia antes de abrir ou renderizar o documento, independentemente do seu tamanho.

A configuração das barras do sistema foi movida para depois de setContentView, junto da restante configuração da janela. Não se tenta resolver esta falha reduzindo a qualidade do PDF ou apagando dados.

## Diagnóstico melhorado

Agora guarda até oito causas encadeadas, com a localização de código do aplicativo em cada uma. Não guarda as mensagens das excepções, caminhos ou texto dos documentos. Os registos mantêm limites de quantidade e tamanho. O relatório inclui modelo, memória máxima e última etapa do leitor; as etapas não identificam o ficheiro aberto.

## Validação

O mesmo teste de regressão sobre o código anterior falha nos três modos: ecrã inteiro, pré-visualização e rolagem vertical. A causa registada nos testes é PhoneWindow.getInsetsController → ReaderActivity.onCreate, com mDecor nulo.

Sobre 5.4.1, os três testes passam. Executam ReaderActivity, ReaderChrome e as vistas reais com Robolectric 4.14.1, Android 13/API 33 e gráficos nativos da JVM. Verificam criação da actividade, montagem e desenho das vistas, navegação e encerramento.

**Limite do teste:** Robolectric não implementa PDFium nesta configuração. O motor de páginas é um substituto determinístico de teste, claramente identificado no código e nas capturas. O teste confirma a correcção do ciclo de arranque e a montagem das vistas; não certifica a descodificação PDFium num telemóvel. A extracção real de PDF continua coberta pelos testes PDFBox anteriores. Não houve teste físico no Itel nem teste de heap Android de 128 MB.

Passaram também os sete testes novos de causas/privacidade do diagnóstico e a suite local existente. O APK foi compilado com API 36, mínimo API 26, e verificado quanto a assinatura, alinhamento e integridade ZIP.

## Actualização

Instale `Seeky_5_4_1_Correcao_PDF.apk` por cima do Seeky 5.4 independente, sem desinstalar. O identificador `mz.bam.seeky.v51` e o certificado são os mesmos; o código de versão é superior. Não há migração de banco nem alteração dos cursos, materiais, notas, temas ou TTS.

Depois de actualizar, abra novamente o PDF que falhava. Se houver outro encerramento, o Diagnóstico local da 5.4.1 passa a mostrar a causa interna e a etapa do leitor, úteis para distinguir uma falha diferente.

O APK `Seeky_5_4_1_sem_assinatura.apk` conserva o identificador da aplicação original e exige a chave apropriada para instalação; a chave privada original não está disponível neste ambiente. Nenhuma chave de assinatura acompanha o código-fonte.
