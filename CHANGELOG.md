# Registo de alterações

## 5.19.0 — 2026-11-19

### Novas funcionalidades
- **Bloqueio da aplicação** (Mais → Bloqueio da aplicação): impressão digital, rosto, PIN ou
  padrão do Android, sem o Seeky guardar códigos. Pede autenticação ao abrir e ao voltar
  após 30 s, 1, 5 ou 15 min em segundo plano; protege também o painel do botão Flash e oculta
  a pré-visualização nas aplicações recentes (Android 13+).
- **Cópia automática** (Mais → Cópia automática): estudos, notas, marcadores, leitura e
  preferências guardados diária ou semanalmente numa pasta à escolha (cartão SD, Download,
  Google Drive…), mantendo as últimas 3 cópias automáticas. "Fazer cópia agora" com
  resultado nas notificações.
- **Tom de voz** ajustável em Ler em voz alta, aplicado à leitura, amostras e gravação WAV.
- **Novidades**: painel mostrado uma vez após a actualização e disponível em Mais.
- A versão de depuração chama-se **"Seeky (teste)"** para não se confundir com a instalada.
- **Botão Flash recolhe-se sozinho**: após 10 s sem uso fica meio escondido na margem, com a
  lupa deslocada para a parte visível e ligeiramente translúcido. Ligado por omissão; o tempo
  (3–30 s) e a posição continuam ajustáveis em Ícones e botão Flash.
- **Vozes naturais do Google** substituem o módulo Seeky Vozes Naturais, que foi retirado
  (ponte `voicebridge`, permissão e testes). O ecrã "Vozes naturais do Google" escolhe entre
  online (mais naturais) e offline, baixa as vozes e ajuda a remover o módulo antigo. Quem
  usava o módulo passa automaticamente para as vozes online do Google.
- **APK assinado no GitHub Actions** quando os segredos da chave estão configurados.


### Segurança
- **Recepção de ficheiros (corrigido).** A actividade de partilha/abertura é exportada e
  aceitava URIs `file://` da área privada da aplicação e URIs `content://` dos providers
  internos do Seeky. Uma aplicação maliciosa podia levar o Seeky a copiar bases de dados,
  preferências ou materiais privados para a biblioteca ou para uma pasta pública ("confused
  deputy"). Novo `IncomingUris` recusa ambos; `ImportWork` recusa `file://` fora do
  armazenamento autorizado.
- **Lançador exportado.** O pedido de extracção (`archive_uri`) só é aceite quando o Intent
  foi assinado pelo próprio Seeky (token secreto por aparelho, comparação em tempo constante).
- **Transferência entre aparelhos (Android 12+).** `allowBackup="false"` não a impedia;
  `data_extraction_rules` exclui os cofres cifrados e o token interno, e desactiva a cópia
  na nuvem.

### Voz
- `SpeechNormalizer`: abreviaturas lidas por extenso (pt/en), numerais romanos de
  capítulos/partes/séculos em árabes, URLs lidos pelo domínio, pontos-guia de índices,
  marcadores, pontuação repetida, títulos em MAIÚSCULAS, `%`, `§`, `°C`. O destaque no ecrã
  continua alinhado com o original e o dicionário pessoal tem prioridade.
- Selecção automática da variante regional em empate de qualidade: Moçambique, Angola e
  restantes países lusófonos africanos → pt-PT (e en-GB); Brasil → pt-BR.

### Flash e aparência
- Botão Flash com resposta física: encolhe ao premir, levanta com anel ao arrastar, mola ao
  soltar, vibrações curtas; lançamento rápido escolhe a margem; toque longo abre os ajustes.
- Mosaico "Seeky Flash" das Definições Rápidas reflecte o estado real e liga/desliga o botão.
- Novo tema **Sistema** que segue o modo claro/escuro do Android em tempo real.

### Estrutura
- Projecto Android na raiz do repositório; scripts em `tools/`; documentação histórica em
  `docs/historico/`.
- Build Gradle funcional (AGP 8.13, wrapper 8.14.3); versão e identidade numa só fonte
  (`app/build.gradle`), partilhada pelo build offline.
- CI no GitHub Actions: compilação e testes do núcleo.

## 5.18.0 e anteriores

Ver `docs/historico/` e `docs/validacao/5.18.0/`.
