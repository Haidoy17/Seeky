# Revisão Seeky 5.9.2

Data de entrega: 30 de Outubro de 2026. Base: 5.9.1. Versão Android: 5002402. Assinatura visual: Haidoy Carlos Mautempo.

## Problema e resultado

No EPUB Original, o leitor carregava o HTML/CSS do livro com viewport, mas sem limites de largura para os elementos. Um corpo, bloco ou parágrafo com largura fixa podia exceder o ecrã. O menu Texto, nesse modo, apenas oferecia a mudança para texto fluido, tornando indisponíveis os ajustes solicitados pelo utilizador.

EpubLayout acrescenta uma camada de apresentação local depois dos estilos do livro. No modo ajustado, substitui viewports de largura fixa, limita a geometria de blocos/imagens/tabelas e permite quebrar linhas. A adaptação não oculta simplesmente o excesso com overflow:hidden. Os estilos tipográficos originais continuam em vigor até o utilizador escolher substituições de espaçamento ou alinhamento. O tamanho usa o text zoom do WebView para manter a hierarquia tipográfica.

Texto e Organizar → Ajustar leitura permitem alterar tamanho, margens, espaçamento e alinhamento directamente na apresentação EPUB. As preferências são específicas da URI do livro. As alterações são agrupadas durante o movimento dos controlos para evitar recarregamentos a cada evento. O leitor conserva aproximadamente a posição proporcional no capítulo e oferece reposição dos ajustes. Em ampliação ou deslocamento horizontal disponível, a navegação por arrasto não captura o gesto destinado a mover a página.

O arquivo EPUB original não é modificado. O modo de texto fluido permanece uma escolha explícita. O leitor DOCX, os serviços de fundo, a fila de voz e a retoma de exportação não foram alterados nesta correcção.

## Verificações

- Compilação ECJ/D8, aapt, integridade ZIP, alinhamento, certificado e identificador de instalação verificados no empacotamento.
- Suite local aprovada; 11 verificações específicas cobrem o HTML adaptado, viewport, estilos e recursos conservados, CSP, ausência de ocultação do excesso e limites dos ajustes.
- **53 testes Android 13/API 33 aprovados**. Os novos casos activam os controlos reais da janela, verificam zoom 150%, margens 28, espaçamento 160%, alinhamento, reabertura, reposição, acesso por Organizar e igualdade dos bytes do EPUB de origem.
- A execução Chromium inicial reproduziu largura de conteúdo de 924 px num ecrã de 360 px e confirmou texto dentro dos limites a 320/360/640 px, com imagens, tabela e estilos preservados. A configuração de codificação do teste foi depois corrigida para UTF-8 e acrescentado charset explícito ao HTML. As regras CSS de geometria permaneceram iguais. A repetição final do browser foi bloqueada pelo sandbox de execução (`shutdown: Operation not permitted`). A medição anterior fica identificada como referência; não se apresenta como certificação visual final.
- O Robolectric não executa o motor WebView completo; usa um valor de escala determinístico para testar o ciclo de vida e os controlos. Os testes não substituem a apresentação no aparelho físico.

## Compatibilidade e limites

O APK assinado mantém `mz.bam.seeky.v51` e o certificado da 5.9.1. A variante sem assinatura conserva `mz.bam.seeky.estudos` para assinatura com uma chave original externa. Chaves privadas não integram a entrega.

Não houve teste físico no Itel nem acesso ao EPUB específico mostrado na captura. A adaptação visa livros com texto refluível e larguras fixas comuns; diagramações especiais podem apresentar diferenças. A ordem dos capítulos e o texto original não são alterados. Não foram acrescentadas dependências de execução ou alterações de backend.
