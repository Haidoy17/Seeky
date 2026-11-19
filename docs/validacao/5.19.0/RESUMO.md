# Validação 5.19.0

Ambiente: Linux, OpenJDK 21, Android SDK platform 36 / build-tools 36.0.0, Gradle 8.14.3, AGP 8.13.0.

| Verificação | Resultado |
| --- | --- |
| `./gradlew assembleDebug` | APK gerado (`mz.bam.seeky.v51.debug`, "Seeky (teste)") |
| `tools/build_apk.py --unsigned` | APK gerado (`mz.bam.seeky.v51`, 5.19.0 / 5004300) |
| `tools/run_tests.py` (núcleo JVM + host, inclui `SpeechNormalizerTest` com 32 verificações) | `ALL CHECKS COMPLETED` — `host-tests-log.txt` |
| `tests/android_runtime/run.py` (Robolectric 4.14.1, API 33) | `OK (381 tests)` + `FlashTuck5190Test` (2), incluindo `Security5190Test` e `Presentation5190Test` — `runtime-tests-log.txt`; capturas em `capturas/` |

Não verificado: telefone físico, qualidade audível das vozes Google, gestos do botão Flash, impressão digital real
do bloqueio e escrita da cópia automática numa pasta real (Google Drive/cartão SD)
num ecrã real. O APK não foi assinado (a chave privada não está no repositório).
