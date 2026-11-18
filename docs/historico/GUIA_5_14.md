# Seeky 5.14 — vozes naturais sem chave

1. Instale **Seeky_5_14_Vozes_Naturais.apk** por cima da versão actual. Não desinstale o Seeky: os livros, cursos, notas e posições de leitura ficam no mesmo pacote Android da versão anterior.
2. Instale também **Seeky_Vozes_Naturais_1_0.apk**. É um motor TTS opcional separado, para o Seeky principal continuar leve.
3. Abra **Seeky Vozes Naturais**, escolha uma voz e toque **Baixar voz**. A ligação à Internet só é necessária para descarregar o modelo, aproximadamente 63 MB por voz. Se interromper, use Retomar descarga.
4. Toque **Ouvir amostra** para conhecer a voz depois da descarga.
5. No Seeky, abra **Vozes e narração → Baixar vozes naturais → Usar Seeky Vozes Naturais**. Volte às opções de voz, escolha idioma e voz e toque **Usar esta voz**. Também pode seleccionar o motor em **Opções de voz → Motor de voz**. Escolha modo Offline.

Vozes disponíveis: Tugão (português de Portugal), Faber e Jeff (português do Brasil), Alba (inglês britânico), LJ Speech (inglês americano), DaveFX (espanhol) e Siwis (francês).

A gravação permanece **WAV**, usando as opções de narração já presentes no Seeky. Títulos, pausas e controlos do leitor continuam a ser tratados pelo Seeky. Os livros e o texto não são enviados para um serviço de voz. Não há chave de API, conta ou cobrança neste motor local.

Use inicialmente uma voz. A instalação precisa de espaço temporário adicional para descarregar e verificar o modelo. Se a ligação cair ou pausar, volte ao módulo e toque Retomar descarga. Se a verificação detectar corrupção, baixe novamente. As descargas são verificadas por tamanho e SHA-256 antes de serem disponibilizadas.

Em telefones modestos, carregar a primeira frase pode demorar; a voz é mantida em memória durante a utilização para evitar recarregamentos entre frases. A velocidade depende do processador e da memória. O Google continua disponível como alternativa no mesmo menu. Ainda não houve teste físico no Itel A17.

As vozes offline do site Voz Viva são Piper/VITS. O modo online ElevenLabs desse site exige chave própria e não foi incluído. O site usa modelos FP16; este módulo usa os originais FP32 para compatibilidade Android, preservando pesos e grafo ao adaptar os metadados. Não foi alterado o site.

Licenças, cartões originais e código-fonte do módulo GPLv3 estão no pacote de fontes que acompanha a entrega. As bibliotecas nativas são oficiais Sherpa ONNX 1.13.8 para ARM32 e ARM64, com alinhamento de 16 KB. O módulo comunica com o Seeky através do TTS público do Android.

Assinatura: **Haidoy Carlos Mautempo**.
