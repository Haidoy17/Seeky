# Verificação — Seeky 5.7.0

Alterações sobre a versão 5.6, conservando a correcção de abertura no Android 13, as bibliotecas de documentos e os serviços de áudio e narração.

- Gesto horizontal de avanço/recuo em EPUB, DOCX, texto e PDF em texto fluido. Avança uma tela com sobreposição; atravessa limites de capítulo nos dois sentidos. Os botões usam a mesma navegação. A posição do capítulo anterior é conservada durante a substituição do conteúdo.
- Detecção distingue movimento vertical, gesto curto, pressão prolongada, selecção e múltiplos dedos. A animação usa a vista visível, sem gerar bitmaps de páginas de texto.
- Variantes de idioma, contagem e filtros de voz, favoritos por motor, comparação de amostras e ritmo 0.95×. A escolha só é confirmada ao aplicar ou iniciar. Identificação feminina/masculina usa metadados explícitos ou a identificação feita pelo utilizador após ouvir; não deduz género a partir de códigos internos ou da altura do som.
- Acesso aos pacotes Google de português do Brasil/Portugal e inglês, com actualização da lista. Quantidade e qualidade dependem do motor instalado; não foram criadas vozes Google adicionais dentro do APK.
- Integração com RHVoice como motor Android opcional gratuito, instalado separadamente. Descoberto pelo mecanismo nativo de motores TTS. O utilizador instala os dados no RHVoice; o Seeky selecciona Offline quando esse motor é escolhido. Não há chave de API nem serviço pago novo.
- Removidos EdgeReader, EdgeReadingService, LocalReadingServer, consultas de pacotes Microsoft e controlos do Edge. O APK não contém essas classes ou o servidor local.

## Validação

Compilação Android SDK 36 com ECJ, D8 e zipalign. APK independente com o mesmo pacote e certificado da 5.6, versão 5.7.0 / 5002200, permitindo actualização sem desinstalar. Integridade ZIP, assinatura e igualdade de DEX e recursos entre variantes verificados.

A suíte local existente conclui todos os testes. Mais 19 verificações de política de gestos, sobreposição e limites de página, género explícito e identificação por motor passam num JVM com heap de 128 MB.

31 testes de ciclo de vida/interface no Robolectric, Android 13/API 33, passam: 3 do leitor PDF, 13 de vozes e exportação, 1 do serviço de voz, 5 da biblioteca, 4 da exportação por capítulos e 5 da navegação por gesto. Os testes de PDF verificam também o gesto de mudança da página individual. Os testes novos de texto enviam sequências de toque, avançam os fotogramas da rolagem, verificam deslocação por tela, regresso ao capítulo anterior, selecção, rolagem vertical e persistência da posição.

Os testes de vozes usam dados e síntese determinísticos, incluindo um motor RHVoice simulado para verificar selecção e modo Offline. Não instalam o RHVoice num telefone nem medem a qualidade auditiva do Google ou RHVoice. Robolectric não tem PDFium; as páginas PDF são determinísticas e servem para testar ciclo de vida, desenho e gestos. Não houve teste num Itel físico. As evidências acompanham o código-fonte.
