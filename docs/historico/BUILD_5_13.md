# Construção da edição 5.13

A construção executada nesta entrega usa Python 3, Java 21, ECJ e as ferramentas Android SDK/Build Tools 36 já instaladas. Os JARs e as bibliotecas ARM estão no projecto. Os scripts não descarregam dependências.

```sh
python3 build_apk.py --ecj /caminho/ecj.jar --android-jar /caminho/android-36/android.jar --build-tools /caminho/build-tools/36.0.0 --unsigned --output /caminho/Seeky_compilado.apk
python3 build_delivery.py --compiled-apk /caminho/Seeky_compilado.apk --android-jar /caminho/android-36/android.jar --build-tools /caminho/build-tools/36.0.0 --output /caminho/Seeky_entrega_sem_assinatura.apk
```

O primeiro APK usa o identificador de desenvolvimento `mz.bam.seeky.estudos`. O segundo prepara os mesmos DEX e bibliotecas nativas com o identificador entregue `mz.bam.seeky.v51`, incluindo alinhamento de páginas nativas. Nenhum dos dois ficheiros acima está assinado.

A assinatura é aplicada com `apksigner`, um keystore externo e uma variável de ambiente para a palavra-passe. Uma actualização sobre uma edição já instalada exige o certificado original. As chaves privadas de assinatura, palavras-passe e chaves pessoais de serviços não são distribuídas no ZIP.

O `release.json` identifica a versão, o certificado público e as verificações da entrega. `evidence/v513` contém os resultados. Os testes Android usam Robolectric e dependências locais fornecidas por `--classpath-file`/`--sdk-dir`; essas ferramentas de teste não são empacotadas no APK. O teste de OCR do computador requer Tesseract e usa os modelos em `tests/ocr/models`.
