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
| Voz | Leitura em voz alta com destaque, exportação WAV por capítulo, dicionário de pronúncia, fundo musical |
| Estudos | Disciplinas, materiais, cartões de revisão, temporizador de foco, plano de leitura, assistente IA (chave pessoal) |
| Bíblia | Importação MyBible, USX 2/3, USFM, EPUB, TXT/CSV; referências por extenso na voz |
| Segurança | Sem servidores obrigatórios, chaves no AndroidKeyStore, cópias verificadas por SHA-256 |

## Estrutura do repositório

```
app/                    Aplicação Android (código, recursos, assets, JARs e bibliotecas nativas)
  src/main/java/mz/bam/seeky/android/   Código da aplicação (pacote único, ver docs/ARQUITECTURA.md)
  src/main/java/mz/bam/seeky/voicebridge/  Ponte para o módulo opcional "Seeky Vozes Naturais"
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

Ver [CHANGELOG.md](CHANGELOG.md). Em resumo: correcção de segurança na recepção de
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
