# Seeky

Aplicativo Android multitarefas de **ficheiros, livros, leitura e estudos**, com pesquisa
instantânea (Flash), leitura em voz alta, importação bíblica, OCR e cópias de segurança.

Crédito: **Haidoy Carlos Mautempo** · Versão **5.19.0** (`versionCode 5004300`) ·
Android 8.0+ (API 26), alvo API 36.

## Funcionalidades

| Área | O que faz |
| --- | --- |
| Ficheiros | Catálogo local indexado, categorias, colecções, favoritos, lixeira segura, ZIP/7z/TAR/GZ/BZ2/XZ |
| Flash | Botão flutuante de pesquisa, painel rápido, mosaico das Definições Rápidas, recolha automática |
| Leitura | PDF, EPUB (formato original ou texto fluido), DOCX, TXT; marcadores, notas, revisão |
| Voz | Vozes naturais do Google (online e offline), leitura em voz alta com destaque, tom e velocidade, exportação WAV por capítulo, dicionário de pronúncia, fundo musical |
| Estudos | Disciplinas, materiais, cartões de revisão, temporizador de foco, plano de leitura, assistente IA (chave pessoal) |
| Bíblia | Importação MyBible, USX 2/3, USFM, EPUB, TXT/CSV; referências por extenso na voz |
| Segurança | Bloqueio por biometria/PIN do Android, cópia automática verificada, chaves no AndroidKeyStore, sem servidores obrigatórios |

## Estrutura do repositório

```
app/                    Aplicação Android (código, recursos, assets, JARs e bibliotecas nativas)
  src/main/java/mz/bam/seeky/android/   Código da aplicação (pacote único, ver docs/ARQUITECTURA.md)
backend/                Esquema Supabase (histórico; o login social foi retirado)
tests/                  Testes JVM do núcleo, testes host e testes Robolectric (android_runtime/)
tools/                  Compilação offline (aapt/d8), entrega e execução dos testes
docs/                   Guia do utilizador, arquitectura, terceiros, validações e histórico
.github/workflows/      CI: compilação Gradle + testes do núcleo
```

## Compilar

### Android Studio / Gradle

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk (id mz.bam.seeky.v51.debug)
./gradlew assembleRelease        # requer SEEKY_SIGN_FILE, SEEKY_SIGN_ALIAS e SEEKY_SIGN_PASSWORD
```

A chave de assinatura **nunca** entra no repositório. Para actualizar a instalação existente
sem desinstalar é obrigatório assinar com o mesmo certificado da versão instalada.

### Versão comercial (GitHub Actions)

1. Em **Settings → Secrets and variables → Actions**, crie `SEEKY_KEYSTORE_BASE64`
   (a chave em base64), `SEEKY_SIGN_ALIAS` e `SEEKY_SIGN_PASSWORD`. Use a mesma chave da
   versão instalada.
2. Crie uma etiqueta com a versão de `app/build.gradle`, por exemplo `v5.19.0`
   (Releases → Draft a new release → Choose a tag, ou `git tag v5.19.0 && git push origin v5.19.0`).
3. O workflow "Android" compila, assina, verifica e publica o Release com
   `Seeky_5.19.0.apk` e `SHA256SUMS.txt`. As notas vêm de `docs/RELEASE_NOTES.md`.

Sem a chave, a publicação falha de propósito: um APK sem a assinatura original não
actualiza a instalação existente nem liga ao módulo de vozes naturais.

### Publicar uma actualização para os telemóveis

O Seeky verifica `entrega/latest.json` no ramo `main` uma vez por dia (Mais → Actualizações).
Para publicar uma versão nova:

1. Aumente `versionCode`/`versionName` em `app/build.gradle`.
2. Compile e assine (`./gradlew assembleRelease` com a chave).
3. `python3 tools/publish_update.py app/build/outputs/apk/release/app-release.apk "Notas da versão"`
4. Junte as alterações ao `main`. Os telemóveis recebem o aviso no próximo arranque.

O telemóvel só instala o APK se o SHA-256 coincidir com `latest.json` e se estiver assinado
com a mesma chave da aplicação instalada.

### Offline (sem Gradle)

```sh
python3 tools/build_apk.py \
  --android-jar $ANDROID_HOME/platforms/android-36/android.jar \
  --build-tools $ANDROID_HOME/build-tools/36.0.0 --unsigned
```

O manifesto final é gerado a partir de `app/build.gradle` (fonte única de versão e
identidade) por `tools/app_manifest.py`. `tools/build_delivery.py` reempacota com outra
identidade de entrega, se necessário.

## Testes

```sh
python3 tools/run_tests.py                  # núcleo JVM + verificações host (sem Android)
python3 tests/android_runtime/run.py ...    # Robolectric, ver tests/android_runtime/README.md
```

## Novidades da 5.19.0

Ver [CHANGELOG.md](CHANGELOG.md). Em resumo: bloqueio da aplicação, cópia automática, tom de voz ajustável, ecrã de Novidades, correcção de segurança na recepção de
ficheiros de outras aplicações, voz mais natural (normalização de texto e variante regional),
botão Flash com resposta táctil e gestos, mosaico Flash como interruptor real, tema
"Sistema" e repositório reorganizado com build Gradle funcional.

## Documentação

- [docs/GUIA.md](docs/GUIA.md) — guia de utilização
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) — mapa dos módulos e fluxos principais
- [SECURITY.md](SECURITY.md) — modelo de ameaças e controlos
- [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md) — bibliotecas e licenças
- [docs/historico/](docs/historico/) — revisões, guias e relatórios das edições anteriores

## Licença

MIT — ver [LICENSE](LICENSE). As bibliotecas de terceiros mantêm as suas licenças
(`app/src/main/assets/licenses/`).
