# Registo de alterações

## 5.19.0 — 2026-11-18

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
