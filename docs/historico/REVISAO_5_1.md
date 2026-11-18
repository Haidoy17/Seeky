# Seeky 5.1 — memória, Flash e interface

Revisão feita sobre a 14.1 em 27/10/2026. O utilizador informou que o aplicativo fecha ao pesquisar, abrir ficheiros e navegar num Itel A17. Não foram fornecidos stack traces, versão exacta do Android nem diagnóstico desse aparelho. As alterações abaixo tratam problemas identificados no código e riscos de memória; a causa de cada encerramento no aparelho ainda não foi confirmada.

## Alterações entregues

- **Memória:** logótipo descodificado uma vez, com amostragem de 1/8 e sem multiplicação pela densidade do ecrã. Para o ficheiro de 2362×2362, o bitmap de marca passa de aproximadamente 21,3 MiB a 340 KiB, antes de considerar a densidade. É uma estimativa do bitmap, não uma medição da memória total do aplicativo.
- Miniaturas com um único descodificador, fila de 12 e cache adaptada ao heap, limitada a 3 MiB. PDFs limitados a 600 mil píxeis por página e duas renderizações pendentes; cache adaptada até 6 MiB. Zoom do PDF limitado a 900 mil píxeis. Imagens com resolução de visualização limitada a 1280 no lado maior.
- Menos observadores simultâneos de pastas: 96 em vez de 512. Os restantes mecanismos de revisão incremental permanecem.
- Recuperação inicial das preferências de Estudos deslocada para o executor desse módulo. Submeter pesquisas depois de encerrar o executor deixa de provocar rejeição de tarefas. Limpeza sequenciada dos catálogos de pesquisa rápida.
- Diagnóstico passa a incluir classe/método/linha da aplicação e tamanho máximo do heap. O registo de falha fatal é gravado de forma síncrona; a excepção continua entregue ao Android. Não foi criado um mecanismo que esconda erros ou reinicie a aplicação em ciclo.
- **Flash:** busca por prefixos no FTS4 já existente, apenas nos nomes; até 30 resultados e 12 termos. Não descodifica documentos, não consulta metadados pessoais nem percorre o disco a cada tecla. A pesquisa completa continua disponível nas preferências e no painel flutuante. O modo Flash não oferece a ordenação/filtros avançados da pesquisa completa.
- Atalho de lançador **Seeky Flash**, independente do gestor principal, e componente de pesquisa nas Definições Rápidas do Android. O utilizador pode adicionar o mosaico pelos controlos de edição do sistema.
- Restauro da bolha após arranque/actualização verifica a preferência da bolha independentemente do monitor. O serviço mantém notificação e controlos de paragem. O primeiro uso continua a exigir permissões do Android.
- Em aparelhos que bloqueiam sobreposições, o mesmo atalho abre uma janela de pesquisa leve e independente. Algumas edições Android Go têm essa restrição; a aplicação não pode sobrepor-se a uma proibição do sistema.
- **Visual:** abertura directa, sem apresentação de cinco segundos nem redireccionamento automático para login; versão pública sem rótulo Alpha/piloto; menu inicial e cartões mais compactos; ajuda reduzida; menos legendas permanentes; ícones vetoriais revistos e novas formas de controlos. Confirmações de operações destrutivas foram mantidas.
- Oito paletas: Claro, Escuro, Jade, Oceano, Coral, Âmbar, Rosa e Grafite, aplicadas às janelas principais, leitores, conta e painéis partilhados.
- Preferências de **Português / English**, com 479 entradas de tradução de apresentação, menus, controlos e títulos. Identificadores internos, ficheiros, textos e dados do utilizador não são traduzidos. Algumas mensagens técnicas, erros dinâmicos e explicações antigas ainda podem aparecer em português; a tradução não foi auditada ecrã a ecrã num aparelho.
- **PDF:** abre em ecrã inteiro, fundo limpo, páginas contínuas com processamento limitado e sem recarregar toda a lista a cada bitmap concluído. Pesquisa no documento, marcadores, anotações e exportação foram mantidos.
- **Imagens:** zoom por pinça, toque duplo alternando ajuste/ampliação, arrasto e controlos Ajustar/Rodar/+/−. A rotação é apenas de apresentação, sem reescrever o original.
- **Música e vídeo:** novo MediaActivity com MediaPlaybackService, MediaSession, notificação de reprodução, controlo de foco de áudio, pausa ao remover auscultadores, posição guardada, avanço/recuo de 10 s, repetição e velocidade quando o codec permite. O visualizador pode fechar sem destruir o descodificador. Vídeo em segundo plano mantém o áudio; não implementa Picture-in-Picture.
- Assinatura visual **Haidoy Carlos Mautempo** no rodapé principal, no player, na pesquisa independente e em Sobre.

## Artefactos e assinatura digital

1. **Seeky_5_1_Instalacao_Separada.apk:** pacote `mz.bam.seeky.v51`, assinado com um certificado local novo de validação. Pode ser instalado ao lado da aplicação antiga; não a actualiza, não copia automaticamente os seus dados e não usa a chave original. Os atalhos aparecem como Seeky 5.1 e Seeky Flash 5.1. O comportamento de login OAuth com duas instalações não foi validado; as duas aplicações podem disputar o esquema de retorno de autenticação.
2. **Seeky_5_1_sem_assinatura.apk:** pacote original `mz.bam.seeky.estudos`, versionCode 5001600. Precisa da chave original antes de poder actualizar a instalação existente. A assinatura visual não substitui a assinatura digital do Android.
3. Código-fonte da 5.1, documentação, alterações e evidências. As chaves privadas e as ferramentas descarregadas não fazem parte do ZIP de fontes.

Não é necessário desinstalar a versão antiga para testar a instalação separada. Preserve cópias externas dos seus Estudos e cadernos antes de qualquer migração. Não há migração automática para o pacote independente.

## Verificações efectivamente realizadas

- Compilação completa Java, recursos e DEX com SDK/Build Tools 36 e ECJ 3.41.0; minSdk 26, targetSdk 36.
- Suíte local existente passou, incluindo PDFBox e contratos SQLite; 16 verificações adicionais cobrem orçamentos de bitmap, 10 mil dimensões aleatórias de PDF, cache adaptada, consultas Flash, tradução de preferências, encaminhamento de áudio/vídeo e pesquisa após encerramento.
- 40 testes existentes de cadernos e 17 de persistência da revisão anterior passaram sobre os métodos reais com SQLite JDBC e contratos Android limitados.
- Teste FTS4 com 100 mil ficheiros: intersecção de prefixos, filtro de categoria, resultados vazios, actualização/remoção pelos triggers e plano de execução usando índice virtual. Tempos do computador de execução estão em `evidence/v51_flash-sql.txt`; **não representam o Itel A17**.
- Assinatura v2/v3, integridade ZIP e metadados da instalação separada verificados. O APK de actualização foi alinhado e verificado como ZIP; permanece sem assinatura.

Não foi executado emulador nem houve instalação no Itel A17. Os testes de limites não são medições de RAM, fluidez ou latência de interface no Android. Não foram validados no aparelho o ciclo de vida do serviço, ecrã desligado/Doze, controlos de auscultadores/Bluetooth, codecs, gestos, reprodução após rotação, permissões, mosaico, reinício, TalkBack ou tradução visual. A compilação passou pelo script offline, não por Gradle/Android Studio.

## Aceitação antes de uma distribuição comercial

Esta revisão não deve ser anunciada como comercialmente validada apenas por compilar. Testar no Itel A17 e num Android recente: abrir/navegar/pesquisar repetidamente, gerar índice, abrir PDFs grandes e imagens, alternar entre áudio/vídeo, bloquear o ecrã, rodar o aparelho, receber uma chamada, remover auscultadores, alternar idiomas e temas, parar os serviços e reiniciar. Recolher o Diagnóstico local se ocorrer nova falha.

A primeira indexação leva tempo e consome recursos; o Flash só encontra nomes já indexados e pode não encontrar fragmentos no meio de palavras. Para esses casos, usar a pesquisa completa. Um forçar paragem nas definições do Android impede reinício automático até à próxima abertura. As restrições de bateria/permissões do sistema continuam a aplicar-se.

As pendências de importação ZIP de Estudos descritas em REVISAO_14_1.md permanecem: payloads internos de prática/lixeira, deduplicação global e pré-limite estrutural de JSON ainda precisam de um trabalho próprio. O backend, a autenticação real e a recuperação entre dois telemóveis não foram alterados nem revalidados nesta revisão.
