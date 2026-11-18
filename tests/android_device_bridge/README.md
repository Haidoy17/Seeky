# Teste instrumental da ligação directa

Android 13, dois processos reais. O produtor de teste fornece PCM determinístico; não é uma voz neural. O cliente é NaturalSpeech.java da aplicação, sem alterações. O APK de teste deve usar este manifesto e BridgeProof.java, incluir NaturalSpeech.java e pedir a permissão NATURAL_VOICE. A fase final exige o módulo Seeky Vozes Naturais instalado com o mesmo certificado e um modelo já instalado. Para o emulador usa-se uma construção x86_64 do módulo com as mesmas classes do serviço e runtime, substituindo apenas as bibliotecas nativas pela ABI x86_64 do mesmo Sherpa 1.13.8.

Execução: am instrument -w mz.bam.seeky.voicebridge.proof/mz.bam.seeky.voicebridge.BridgeProof

O resultado deve indicar success=true, production_catalogue=7, preserved_installed_voices>=1, streamed_audio_track=true e two_process_pcm_to_wav=true. O WAV de teste tem 8864 bytes. O teste não verifica qualidade auditiva nem desempenho neural num telemóvel ARM.
