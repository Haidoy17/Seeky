# Verificação — Seeky 5.8.0

Alterações sobre a versão 5.7 estável:

- Removidos os controlos, o instalador e a consulta de pacote RHVoice. Um filtro mantém o antigo motor fora da lista, mesmo que ainda esteja instalado no telefone. A preferência antiga é migrada para Google online no arranque da aplicação e no painel de voz.
- Atalho Google online para escolher uma voz de rede por qualidade declarada, sem chave de API e sem novo serviço externo. Limpa os filtros antes de escolher; amostra e escolha temporária continuam separadas da confirmação. Não muda para Offline quando falta voz online.
- Nomes dos áudios com livro, capítulo/subtítulo e número ao fim. Reconhece metadados EPUB/PDF, índice EPUB, títulos HTML, estilos de título DOCX e secções do índice PDF. Aceita o título editado na biblioteca. Cabeçalhos em cache limitados a dezasseis entradas.
- Nomes saneados e limitados por comprimento e bytes UTF-8, preservando caracteres completos, numeração e extensão. Capítulos divididos têm partes 01, 02, etc.
- Etiquetas RIFF INFO com título, livro/álbum, autor, número de faixa e software. Aplicadas à exportação por capítulos e à ferramenta de narração de uma parte/selecção. O PCM e o seu formato são preservados.

## Validação

Compilação Android SDK 36 com ECJ e D8. APK independente com o mesmo pacote e certificado da 5.7, versão 5.8.0 / 5002300. Verificados assinatura, alinhamento, integridade ZIP e igualdade de DEX/recursos entre variantes.

A suíte local existente conclui todos os testes. Acrescentadas 22 verificações de nomes, títulos, caracteres Unicode, ordem de partes, limites, metadados e preservação do PCM. Um WAV de teste é também lido pelo ffprobe, que identifica PCM 16 bits/24 kHz, título, álbum, autor, faixa e software correctamente.

34 testes Robolectric com Android 13/API 33 passam. Incluem migração/exclusão do motor removido, atalho Google online, ausência de mudança silenciosa para Offline, amostras sem confirmação, exportação de EPUB por capítulos com nomes e etiquetas, cancelamento/falha, índice PDF com subtítulo mais específico e regressões da biblioteca e dos gestos.

Os testes de voz usam vozes e amostras determinísticas. Não avaliam auditivamente as vozes Google no telefone. O teste de metadados PDF usa um PDF real gerado pelo PDFBox; os testes visuais de páginas usam páginas determinísticas porque Robolectric não implementa PDFium. Não houve teste num Itel físico. Evidências e instruções reproduzíveis acompanham o código-fonte.
