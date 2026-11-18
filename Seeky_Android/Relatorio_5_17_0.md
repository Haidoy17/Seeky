# Seeky 5.17.0

A revisão confirmou que os arquivos eram encaminhados para aplicações externas: não existia compactação ou extracção no Seeky. Esta edição acrescenta operações próprias, amplia os ajustes do Flash e permite temas personalizados.

## Arquivos

A extracção integrada suporta ZIP, 7z, TAR e combinações TAR/GZ, TAR/BZ2 e TAR/XZ, além de ficheiros individuais GZ, BZ2 e XZ. A criação usa ZIP com Zip64 quando necessário. Os ficheiros recebidos por Abrir com ou Partilhar têm uma acção Extrair no Seeky, com transferência das permissões do URI. Fontes content:// são copiadas temporariamente em fluxo; fontes locais são verificadas contra os armazenamentos autorizados.

Há escolha de destino, nome, progresso e cancelamento. Os dados são escritos primeiro num destino temporário e só são publicados depois de terminar. A falha ou o cancelamento remove o resultado temporário. Não são apagadas as origens e um nome já existente é recusado. A cópia usa buffers de 64 KB, limita 16 GB de dados e 50 mil entradas, rejeita caminhos fora do destino, entradas repetidas e ligações especiais. ZIPs usam verificação CRC, incluindo entradas com descritor de dados. Os descodificadores 7z e de fluxos comprimidos têm limite de memória de 32 MB.

**RAR nativo permanece pendente.** A consulta à biblioteca Android adicional não concluiu e foi cancelada; não foi possível obter essa dependência neste ambiente. A interface informa que .rar precisa de outra aplicação, em vez de fingir uma extracção. ZIPs produzidos pelo programa WinRAR funcionam através do suporte ZIP. ZIP cifrado não é suportado nesta edição. Existe entrada opcional de palavra-passe para o leitor 7z; os ensaios automáticos desta edição usam 7z sem cifra.

Os trabalhos de arquivos são canceláveis e ligados à actividade aberta. Não foi acrescentada uma fila de arquivos que sobreviva ao encerramento do processo. Depois de um encerramento forçado pelo Android, uma pasta temporária oculta pode permanecer no destino; as origens continuam conservadas.

## Flash

Os valores antigos dos tamanhos foram mantidos, acrescentando três menores: diâmetros visuais de 16, 24, 32, 44, 52 e 64 dp. O tamanho médio de 52 dp corresponde a 100%; o mínimo é aproximadamente 31%. A área de toque nunca fica abaixo de 44 dp.

A transparência da bolha pode variar de 0 a 80%. Usa o alpha da janela da bolha, conservando a opacidade do painel de pesquisa. A recolha automática é opcional, com temporizador de 3 a 30 segundos e escolha da margem mais próxima ou de um dos quatro cantos. A janela pode ultrapassar o limite do ecrã para mostrar metade do círculo. A posição normal gravada não é substituída por coordenadas negativas. O toque repõe a bolha e permite abrir a pesquisa imediatamente; arrastar suspende o temporizador. A rotação repõe primeiro a janela nos limites e volta a agendar a recolha.

Os ajustes completos são acessíveis nas Preferências e a partir da engrenagem do painel Flash, incluindo quando o Seeky precisa de ser aberto de início.

## Temas

A lista passou de oito para 16 temas. A personalização acrescenta destaque e fundo, com campos hexadecimais e um selector RGB com prévia. As cores são guardadas como preferências locais e são observadas pelo serviço do Flash. A paleta personalizada adapta texto, descrições, cartões e destaque para conservar contraste nos fundos e superfícies calculados. Os cabeçalhos da paleta personalizada recebem uma tonalidade adequada ao texto branco. O papel do leitor mantém os ajustes próprios já existentes.

## Dependências e validação

Foram usados os componentes Java já disponíveis no ambiente: Apache Commons Compress 1.26.2, Commons IO 2.16.1, Commons Codec 1.17.0, Commons Lang 3.17.0 e XZ for Java 1.9. As licenças e avisos foram incluídos nos assets e no código-fonte. Não houve obtenção de um motor RAR nem introdução de serviços pagos.

- Conjunto completo de 338 testes JUnit/Android/Robolectric, incluindo 32 ensaios novos, e verificações do núcleo.
- Arquivos reais de ZIP, 7z/LZMA2, TAR e fluxos GZ/BZ2/XZ; nomes Unicode e pastas vazias; cancelamento; destinos existentes; origens conservadas; caminhos de escape e ligações rejeitados; ZIP corrompido, descritores CRC e ZIP vazio.
- Compactação e extracção executadas pelo worker real da actividade, com verificação dos ficheiros produzidos.
- Bolha abaixo de 80%, toque mínimo, recolha nos quatro cantos, suspensão durante arrasto, toque na metade visível, transparência independente do painel, rotação e abertura dos ajustes por Intent.
- Contraste da paleta personalizada em fundos claros, escuros e intermédios, actualização do tema no Flash e prévias nativas.
- APK com nome instalado Seeky, identidade mz.bam.seeky.v51, versão 5.17.0 / 5004100, certificado anterior e alinhamento de 16 KB.

Não houve teste num ITEL físico. A posição efectiva da janela parcialmente fora do ecrã ainda precisa de confirmação no WindowManager do aparelho. O módulo de vozes, as bibliotecas pessoais e o formato WAV não foram alterados.

Os registos incluem falhas deliberadas dos testes de recuperação; o resultado global encontra-se no final.

Haidoy Carlos Mautempo
