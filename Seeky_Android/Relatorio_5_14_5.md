# Seeky 5.14.5 — apresentação e pesquisa flutuante

A bolha de pesquisa passa a ter desenho circular de 52 dp e área de toque de 60 dp, ícone vectorial, contorno discreto e contraste definido pelo tema. Conserva arrastar, encaixe nas margens e posição guardada. Não usa uma animação permanente.

O painel utiliza um componente nativo partilhado, FlashPanelView, com campo de pesquisa e botão para limpar, filtros com selecção explícita, resultados espaçados e modos Flash, Completa e Pastas na mesma linha. A contagem apresentada deixa de expor tempos internos do catálogo. O toque fora recolhe o painel e o rodapé permite abrir o Seeky ou desligar a bolha.

O painel sobreposto ajusta a sua altura às barras do sistema e ao teclado no Android 11 ou posterior. Com menos espaço, esconde os filtros e o rodapé, mantendo o campo, os resultados e os modos. A alternativa do Android Go recebe o mesmo visual e usa o redimensionamento da janela. Abrir o explorador a partir dela reutiliza a actividade principal quando disponível.

As mudanças de tema actualizam a bolha que já estiver visível. Cartões, realces de toque e menus usam cores derivadas do tema, com contornos e espaçamento consistentes. As alterações de layout não abrangem o conteúdo de documentos nem modificam a sua formatação.

O ficheiro original do logótipo tem uma tela de 2362 × 2362 com grandes margens transparentes. O aplicativo passa a guardar em cache apenas a área visível, após descodificação reduzida; o ficheiro original permanece intacto. A apresentação no tema escuro recebe uma cor de alto contraste. A cache final continua pequena.

## Validação

- 249 testes Android/Robolectric, incluindo 9 de apresentação e comportamento do flutuante: temas, espaço para resultados, arrastar e encaixe, selecção dos modos, toque fora, descarte de resultados antigos, teclado, alternativa Android Go e proporções/contraste do logótipo.
- Conjunto completo de testes do núcleo, incluindo importação de Bíblias, ficheiros, pesquisa, narração WAV e restauro.
- Capturas do layout nativo em Claro, Escuro, Âmbar e disposição compacta. No ambiente Robolectric sem manifesto, o logótipo é carregado do próprio pacote de recursos para o mesmo mecanismo de cache; não é um desenho alternativo do produto.
- Identidade da aplicação, rótulo Seeky, versão 5.14.5 / 5003500, certificado anterior e alinhamento de 16 KB verificados no APK de entrega.

Não foram acrescentadas bibliotecas nem motores de animação. Não houve teste num ITEL físico; sobreposições e teclados de fabricantes diferentes ainda precisam de confirmação no aparelho do utilizador.

A actualização contém as correcções da 5.14.4. O módulo Seeky Vozes Naturais não foi alterado e a narração permanece em WAV.

Haidoy Carlos Mautempo
