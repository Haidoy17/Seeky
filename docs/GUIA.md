# Seeky — versão 5.0.0-alpha.15, edição Android 14

25 de Outubro de 2026 · BAM - SERVICE

O número 14 identifica a edição do aplicativo. Não exige Android 14: a configuração mantém Android 8/API 26 como mínimo e API 36 como alvo. A instalação e o comportamento precisam de ensaio no aparelho.

## Instalação e preservação de dados

1. Antes de actualizar, exporte a cópia de Estudos em Estudos → Gestão e cópias de segurança. Mantenha cópias externas de documentos únicos.
2. Instale o APK por cima da edição 13, sem desinstalar. O pacote e o certificado de teste foram mantidos. A edição 9 antiga usa outro pacote.
3. Ao abrir os cadernos pela primeira vez, a base do leitor passa do esquema 1 para o 2. Antes dessa migração, o Seeky cria uma cópia local dos cadernos activos e marcadores. Se a cópia falhar, a migração é cancelada e os dados anteriores são conservados. Pode ser necessário libertar espaço ou tratar um caderno acima do limite de exportação.
4. Depois de actualizar, use Ferramentas → Exportar cadernos do leitor. Assim passa também a ter uma cópia externa das anotações de documentos que não pertencem a Estudos.

A cópia automática de migração cobre o leitor, não todo o Seeky. Catálogo, colecções, pesquisas e configurações não foram migrados nesta edição, mas ainda não têm um pacote completo de backup/restauro. A lixeira e os documentos externos não são copiados por esse snapshot.

A edição 14 continua alpha. Para documentos únicos, mantenha uma cópia externa antes de experimentar operações de ficheiros.

## Desfazer, refazer e corte no caderno

No leitor, Ferramentas e a barra superior oferecem Desfazer e Refazer por parte/página/capítulo. O histórico é persistido em SQLite, por URI e impressão do texto extraído; sobrevive ao fecho e reabertura do leitor.

São guardadas alterações de texto, notas, destaques, sublinhados e reposição do original. Uma edição depois de desfazer elimina a sequência de refazer dessa parte. Alterações noutra janela são verificadas para impedir que uma vista desactualizada substitua o caderno guardado.

Limites: até 20 operações por parte, 8 MiB por parte e 64 MiB de histórico no total; cada operação pode ocupar até 4 MiB. Ao atingir os limites, operações antigas deixam de estar disponíveis. Não é um arquivo ilimitado nem substitui o backup.

Cortar guarda uma cópia interna persistente e o estado anterior na mesma transacção da remoção. Se essa gravação falhar, o corte não é aplicado. O texto só é anunciado como guardado depois do commit. Ferramentas → Textos cortados permite voltar a copiá-lo, mesmo que outra aplicação tenha substituído a área de transferência do Android. Há até 20 recortes recentes, sujeitos ao limite conjunto de 8 MiB; estes recortes e o histórico não entram na exportação dos cadernos activos.

O documento original nunca é escrito por estas operações. A barra identifica a cópia de estudo e se o texto tem edições. Ver original mostra as páginas PDF ou o texto extraído sem edições; Comparar original e cópia apresenta os dois textos, até 80 mil caracteres de cada na pré-visualização. Não calcula diferenças linha a linha.

Inserir na cópia de estudo usa o fim da selecção; sem selecção, usa o fim do texto. Não promete um cursor de edição completo. Repor mostra a quantidade de anotações afectadas, oferece Exportar antes e permite desfazer dentro da retenção. Marcadores permanecem.

## Cópias dos cadernos e recuperação

Em Ferramentas:

- **Exportar cadernos do leitor:** JSON com o texto das cópias de estudo, notas, destaques, sublinhados e marcadores de todos os documentos guardados. Formato `seeky-notebook-1`, esquema de exportação 1, até 32 MiB e 20 mil registos por tabela.
- **Importar cadernos do leitor:** valida antes de escrever e acrescenta apenas identidades em falta. Registos existentes são conservados; repetir a mesma importação não duplica os cadernos nem os mesmos marcadores. Uma cópia local anterior é criada antes de aplicar.
- **Recuperar cópia anterior de uma parte:** permite consultar os snapshots locais de migração/importação e escolher uma identidade de texto. A confirmação mostra o URI, a parte, a quantidade de texto e de anotações. Guarda o estado actual antes de substituir essa parte; se já existia, a recuperação também entra no histórico para poder desfazer.

Uma importação de JSON externo não substitui automaticamente conflitos existentes. A recuperação por parte acima usa os snapshots locais listados. Os URIs da exportação referenciam documentos/pastas existentes: não copiam o documento, não concedem permissões e não relocalizam automaticamente um ficheiro movido. A impressão do texto impede aplicar anotações de outra extracção ao texto actual.

As cópias locais anteriores ficam no armazenamento privado da aplicação e não sobrevivem à sua desinstalação. Existem ficheiros separados para o estado anterior à migração, à última importação e à última recuperação de parte, sujeitos ao limite de 32 MiB por cópia. Não são cifrados separadamente. Uma nova importação/recuperação actualiza o respectivo snapshot; o da migração fica separado.

Este JSON **não inclui** originais PDF/Word/EPUB, materiais privados dos Estudos, colecções, favoritos, pesquisas, preferências, conta, histórico de desfazer ou textos cortados. O backup de Estudos em ZIP continua disponível para os seus materiais e progresso. Nenhum dos dois formatos, isoladamente, é um backup completo do Seeky. O JSON pode conter texto sensível: escolha cuidadosamente onde o guardar.

## Pesquisa e anotações

A pesquisa no leitor passa a oferecer Ignorar acentos, Palavra inteira e Incluir notas, destaques, sublinhados e marcadores. Ignorar acentos está ligado por defeito. Os resultados identificam o tipo de ocorrência e usam posições do texto original, incluindo acentos compostos/decompostos. Uma passagem pode surgir como texto e como destaque: são tipos distintos.

Procura profunda: até 500 partes, 200 resultados, 500 marcadores recentes e um orçamento total de 45 segundos verificado entre partes. Uma parte complexa pode ultrapassar esse tempo; não se trata de uma espera de 45 segundos entre cada parte. Há progresso e cancelamento. Resultados limitados por quantidade, tempo ou extracção são identificados como parciais. PDF acima de 64 MiB não permite este modo de extracção; digitalizações sem texto exigem OCR, ainda não incluído. TXT é carregado até 512 KiB; acima disso a procura cobre apenas o texto carregado e indica resultado parcial. DOCX/EPUB continuam simplificados e sujeitos aos limites de expansão.

A pesquisa global do catálogo continua na Pesquisa. A indexação PDF da edição 13 continua a pedido: até 32 MiB, 80 páginas e 262144 caracteres. É um mecanismo separado da procura no documento aberto.

**Anotações do documento** reúne notas, destaques e sublinhados de várias partes, com filtro por tipo. A lista mostra até 300 anotações e 1000 partes guardadas. Pode incluir versões antigas de extracção; Localizar verifica a impressão antes de usar a posição. A edição de uma nota faz-se nas Anotações desta parte e também permite desfazer.

**Exportar anotações do documento** cria TXT ou HTML com as passagens seleccionadas completas, notas e identificação da extracção. Limites: 20 mil anotações e 4 milhões de caracteres. O ficheiro é preparado antes de escolher o destino; se exceder os limites, a exportação é cancelada sem publicar um ficheiro parcial. Não é exportação integral do livro nem inclui o texto sem anotações.

**Exportar página original como PNG** guarda a página PDF inteira, limitada a 1200 × 1800 píxeis, sem as anotações da cópia de estudo. O recorte do conteúdo visível continua disponível separadamente.

## Menus, privacidade e acesso

Eliminar fica no fim das acções de selecção. O envio para a lixeira continua a exigir revisão de nomes/quantidade. Depois de um envio bem-sucedido, Desfazer envio tenta restaurar apenas os itens desse lote; conflitos e volumes indisponíveis continuam a ser tratados sem substituir ficheiros existentes. Eliminar definitivamente não é apagamento seguro do suporte.

O CSV de actividade passa a perguntar se deve omitir nomes e detalhes/caminhos ou exportar o relatório completo. A escolha redigida elimina ambos os campos, em vez de tentar adivinhar onde um caminho aparece no texto.

A apresentação mantém logótipo e BAM - SERVICE, dura até cinco segundos e tem Saltar apresentação. Com animações desactivadas nas preferências/sistema ou no modo de poupança de energia, é saltada. Os controlos de zoom e navegação do leitor receberam descrições de acessibilidade e alvos de 48 dp. Isto não equivale a uma auditoria completa de TalkBack, contraste, fonte grande ou rotação.

## O que não mudou nesta edição

Continuam por implementar: backup/restauro completo do gestor, cópia/movimento recursivo de pastas com conflitos, reversão de movimentos, transferências em serviço com retoma, OCR, áudio em segundo plano/TTS, biometria/PIN, colecções inteligentes e lixeira com retenção configurável.

A versão de teste mantém a identidade de assinatura anterior para permitir actualização; a chave privada não acompanha as fontes. A migração para uma assinatura comercial continua pendente. Não houve alteração do backend remoto nesta edição, nem revalidação de Google/Facebook, OAuth ou recuperação em dois telemóveis.

## Testar no Itel e plano de retorno

1. Confirme os Estudos e as anotações antigas após instalar por cima. Verifique a mensagem de cópia do caderno anterior criada.
2. Num documento descartável, destaque, acrescente nota, corte e insira texto. Desfaça, feche o leitor, reabra e refaça. Confirme o texto original fora do Seeky.
3. Reponha uma parte e desfaça a reposição. Teste Ferramentas → Recuperar cópia anterior de uma parte.
4. Exporte o JSON dos cadernos e importe-o duas vezes; confira os totais e que os existentes foram conservados. Guarde a cópia externamente.
5. Pesquise `acao` num texto com `ação`, teste Palavra inteira e procure uma palavra escrita apenas numa nota. Confira os tipos dos resultados.
6. Teste o envio para lixeira e Desfazer usando cópias descartáveis. Teste o CSV redigido, Saltar apresentação, fonte grande e TalkBack.

O APK 13 e as fontes anteriores foram conservados para diagnóstico. O Android não permite instalar normalmente uma versão com código inferior por cima; a base do leitor também passou ao esquema 2. Um retorno exige uma compilação de compatibilidade com código superior e suporte ao esquema, ou reinstalação planeada com cópias externas. Não foi testado rollback Android e não é recomendado desinstalar para voltar sem exportar os dados.

Passaram compilação, assinatura, integridade, suites JVM/SQLite/PDF, 40 verificações do código real de caderno sobre SQLite JDBC do host e 27 de conta/backup com contratos locais. A migração 1→2, a sua falha e a recuperação foram testadas no host. Não houve aparelho/emulador nesta sessão; instalação real, migração no Android, bolha, TalkBack e bateria precisam de validação física.
