# Seeky 5.14.4

Nome instalado: Seeky. Versão: 5.14.4, código 5003400.

Este pacote contém o código completo do aplicativo principal e as suas dependências locais. O módulo opcional Seeky Vozes Naturais 1.0.4 não mudou nesta actualização.

A compilação directa é feita com `build_apk.py`, Android SDK 36, Build Tools 36 e ECJ/JDK. `build_delivery.py` aplica o applicationId `mz.bam.seeky.v51` e o rótulo `Seeky` ao APK compilado. As chaves de assinatura não são distribuídas. Para actualizar a instalação existente é necessário usar o mesmo certificado; o APK de instalação já entregue está assinado com ele.

Testes: `run_tests.py` para o núcleo; `tests/android_runtime/run.py` para Robolectric com as dependências indicadas nos parâmetros. Nenhuma dependência dos testes é incorporada no APK.

A correcção do importador EPUB está em BibleEpubText.java e BibleWork.java. A recuperação de tarefas sem serviço activo está em TaskService.java e TaskActivity.java. Os testes novos encontram-se em BibleEpubImportTest.java e Bible5144Test.java.

A amostra de 69 partes usa exclusivamente texto sintético. Não acompanha este pacote qualquer tradução integral da Bíblia nem uma chave de API.

Haidoy Carlos Mautempo
