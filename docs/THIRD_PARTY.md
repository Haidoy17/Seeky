# Componentes incluídos

- PdfBox-Android 2.0.27.0, de Tom Roush / Apache PDFBox: `app/libs/pdfbox-android-2.0.27.0.jar` foi extraído sem alteração de classes do AAR Maven Central. Recursos originais do AAR em `app/src/main/assets/com/tom_roush`. Licença Apache 2.0 e NOTICE em `app/src/main/assets/licenses`.
- Bouncy Castle bcprov/bcpkix/bcutil 1.86, JDK 1.8+: JARs originais em `app/libs`. Licença em `app/src/main/assets/licenses/LICENSE_BouncyCastle.md`. Dependências da biblioteca PDF; não há ferramenta de edição/assinatura criptográfica no Seeky.
- URLs de origem e SHA-256 em `third_party.json`. As bibliotecas já estão incluídas no código-fonte distribuído; a compilação não as descarrega.

O motor PDF é usado apenas sob pedido para extrair texto de páginas no leitor. A descoberta normal não passa a abrir todos os PDFs. A renderização visual continua a usar o PdfRenderer do Android. Não foi adicionado OCR.

O teste PDF do anfitrião usa pequenos substitutos de logs, assets e valores gráficos do Android apenas para verificar um PDF de texto simples. Não simula a interface, o sistema operativo, recorte, gestos ou documentos complexos no aparelho.


## Original DOCX layout (5.9)

The APK bundles docx-preview 0.3.7 (Apache-2.0) and JSZip 3.10.1 (MIT option of its dual licence). Their licence texts are included in app/src/main/assets/document/. Versions, npm tarball URLs and bundled script hashes are recorded in document_dependencies.json. They run locally; document scripts, external network resources and DOCX HTML altChunks are disabled.
