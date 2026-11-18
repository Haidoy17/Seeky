# Seeky 5.5.0 — Melhorias de voz e narração

Versão 5.5.0, código 5002000, Android mínimo API 26 e alvo API 36. Mantém a correcção de inicialização dos leitores da 5.4.1. Assinatura visual: Haidoy Carlos Mautempo.

## Escolha e pré-escuta

Nova interface para comparar vozes por idioma, sotaque, qualidade declarada e disponibilidade. O botão Ouvir amostra usa a voz escolhida, o ritmo seleccionado e uma frase de exemplo no idioma correspondente. Seleccionar ou ouvir não altera as preferências guardadas: Usar esta voz ou iniciar uma leitura confirma a escolha. Fechar a folha ou sair do leitor pára a amostra e liberta o motor e o foco de áudio.

As escolhas são recordadas separadamente por motor, idioma e modo. A lista prioriza vozes instaladas e a qualidade declarada, distinguindo pacotes que ainda precisam de instalação. O aplicativo não apresenta uma classificação neural inventada e não substitui silenciosamente uma voz confirmada que deixou de estar disponível.

## Ritmo mais contínuo

Quebras simples de linha extraídas de PDF são tratadas como disposição do texto. Parágrafos e pontuação são conservados; hífenes normais, números e sinais permanecem. Apenas hífenes discricionários são removidos. A posição indicada pelo motor é mapeada para o texto original, preservando selecção, acompanhamento e retoma.

Os pedidos incluem frases mais completas, até 2200 caracteres a ritmo normal, com um limite menor a velocidades lentas. O tempo de espera considera a extensão e a velocidade, evitando cortar uma narração longa com o limite antigo de um minuto. Não há alteração do documento nem dos cadernos.

## Vozes offline: duas funções distintas

**Voz instalada:** Baixar vozes offline abre a instalação de dados oferecida pelo motor Android seleccionado. O idioma escolhido é enviado ao instalador. Ao regressar, o Seeky volta a consultar as vozes. O modo offline aceita apenas vozes que o motor identifica como locais, com os dados disponíveis. O fornecedor pode disponibilizar uma voz local com outro timbre/qualidade, ou não disponibilizar um modelo local da voz online. Não é tecnicamente possível descarregar modelos de servidor que a API do fornecedor não oferece.

**Narração guardada:** permite gerar um WAV da página, capítulo ou selecção actual com a voz escolhida, incluindo uma voz online quando o motor suporta synthesizeToFile. Depois de gerar, o utilizador escolhe o destino pelo selector de documentos do Android. O ficheiro pode ser ouvido sem rede e conserva os dados de áudio produzidos pelo motor. Isso não instala um modelo de voz para textos novos.

A junção dos trechos verifica formato, canais, frequência e profundidade de bits. Mantém os dados PCM sem recompressão nem reamostragem e rejeita alterações de formato ou ficheiros incompletos. Um cabeçalho WAV completo só é publicado depois da geração. Limites: 24000 caracteres por parte, 128 MB de áudio, trabalho serial e cópia com buffer de 64 KB. Cancelar limpa os temporários que a operação criou. A leitura de origem é independente e respeita os limites de extracção existentes. PDFs digitalizados continuam a exigir OCR para obter texto.

Nenhum modelo pesado, biblioteca de aprendizagem automática ou chave de serviço de voz foi adicionado ao APK. Os pacotes locais pertencem ao motor TTS; as narrações são ficheiros escolhidos pelo utilizador.

## Validação e limites

Passaram 14 verificações de normalização/mapeamento e 14 verificações de formato WAV, preservação de amostras, cancelamento e protecção de ficheiros existentes, além da suite local anterior.

Os testes Robolectric no Android 13 simulado cobrem as vistas do leitor, amostra sem gravação de preferência, confirmação, destino do instalador, dados offline em falta, voz exacta, cancelamento, exportação para documento, vários trechos, eventos duplicados, recuperação de WAV preparado e mudança de voz com posição conservada. A síntese e as vozes nesses testes são controladas por fixtures; não constituem avaliação auditiva de uma voz real.

Não foi possível medir no telefone a naturalidade das vozes Google, confirmar os pacotes que o motor do utilizador oferece, nem verificar a exportação de cada voz de rede. Algumas implementações podem permitir reprodução mas recusar síntese para ficheiro. A interface apresenta essa indisponibilidade sem trocar por uma voz de qualidade inferior.

O APK é verificado quanto à assinatura, alinhamento e integridade. A variante independente mantém o identificador e o certificado da 5.4.1, com código de versão superior, para actualização sem desinstalar. Nenhum esquema de banco ou serviço remoto foi alterado. O APK sem assinatura mantém o identificador original e requer uma chave apropriada para instalação; as chaves privadas não acompanham o código-fonte.
