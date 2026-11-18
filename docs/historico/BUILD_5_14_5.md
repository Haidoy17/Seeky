# Seeky 5.14.5

Nome instalado: Seeky. Versão: 5.14.5, código 5003500.

Este pacote contém o código completo do aplicativo principal e as dependências locais. O módulo opcional Seeky Vozes Naturais 1.0.4 não foi alterado.

Compilação: build_apk.py com Android SDK 36, Build Tools 36 e ECJ/JDK; depois build_delivery.py para applicationId mz.bam.seeky.v51 e rótulo Seeky. O certificado privado não é distribuído. O APK entregue já está assinado com o certificado anterior, para permitir actualização sem desinstalação.

O flutuante usa FlashBubbleView, FlashPanelView e QuickSearchService. A alternativa Android Go usa o mesmo painel em FlashSearchActivity. AppPalette e Motion tratam das cores e dos realces; AppImages trata das proporções e do contraste do logótipo.

Testes: run_tests.py para o núcleo; tests/android_runtime/run.py para Robolectric. As dependências dos testes são fornecidas por parâmetros e não entram no APK. FlashDesignTest verifica a disposição e a interacção dos novos controlos.

As correcções da importação de Bíblias da versão 5.14.4 estão incluídas. A gravação de narração continua em WAV. Não existem chaves de API nem traduções integrais da Bíblia neste pacote.

Haidoy Carlos Mautempo
