# Segurança do Seeky

## Comunicar uma vulnerabilidade

Abra um *issue* privado (Security advisory) no repositório ou contacte o autor. Não publique
detalhes antes de existir correcção.

## Modelo de ameaças

O Seeky trabalha com ficheiros pessoais, estudos e chaves de API pessoais. As ameaças
consideradas são:

1. **Aplicações maliciosas no mesmo telefone** que enviam Intents para componentes exportados.
2. **Ficheiros hostis** abertos ou importados (EPUB/DOCX com scripts, ZIP com caminhos
   `../`, ligações simbólicas, bombas de compressão, módulos SQLite manipulados).
3. **Rede** (intercepção, servidores falsos).
4. **Fuga de dados** por cópias de segurança/transferências do Android.

## Controlos

| Ameaça | Controlo |
| --- | --- |
| Componentes exportados | Só `MainActivity`, `IncomingActivity`, `FlashActivity` e `FlashTileService` (protegido por `BIND_QUICK_SETTINGS_TILE`). `IncomingUris` recusa URIs da área privada e dos providers próprios; extras sensíveis exigem token interno. Providers não exportados, só leitura, com concessões explícitas. |
| PendingIntent | Todos `FLAG_IMMUTABLE`. |
| EPUB/DOCX | WebView sem acesso a ficheiros/conteúdo, sem rede (pedidos interceptados), CSP `script-src 'none'` no EPUB e `script-src 'self'` no visualizador DOCX local. |
| Arquivos | Rejeição de caminhos absolutos, `..`, ligações e dispositivos; verificação canónica do destino; CRC; limites de tamanho e entradas; publicação atómica. |
| SQLite importado | Aberto só para leitura numa cópia privada; nenhuma SQL do módulo é executada; identificadores constantes. |
| Rede | `usesCleartextTraffic="false"`; sem redireccionamentos; chave de IA enviada em cabeçalho, nunca no URL; mensagens de erro com chaves ocultadas. |
| Chaves | Cifradas com AES-GCM e chave do AndroidKeyStore (`SeekyAiVault`, `CloudVault`). |
| Cópias/transferências | `allowBackup="false"`, `fullBackupContent="false"`, `data_extraction_rules` (cofres e token excluídos da transferência entre aparelhos). |
| Acesso ao telefone desbloqueado | `AppLock` opcional: autenticação do Android ao abrir/voltar, painel Flash incluído, `FLAG_SECURE` no ecrã de bloqueio e pré-visualização oculta nas recentes (Android 13+). |
| Assinatura | Chaves privadas fora do repositório; `.gitignore` bloqueia `*.jks`, `*.keystore`, `*.p12`. |

## Permissões sensíveis e porquê

- `MANAGE_EXTERNAL_STORAGE` — catálogo e pesquisa de todos os ficheiros (função principal).
- `SYSTEM_ALERT_WINDOW` — botão Flash flutuante (opcional).
- `REQUEST_INSTALL_PACKAGES` — instalar um APK recebido, sempre com confirmação do Android.
- `RECORD_AUDIO` — notas de voz nos estudos.

## Histórico

- **5.19.0** — corrigida leitura de ficheiros privados/providers internos a pedido de outra
  aplicação através da partilha (ver `CHANGELOG.md`).
