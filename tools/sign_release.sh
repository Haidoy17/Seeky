#!/usr/bin/env bash
# Assina o APK de release com v1 (JAR) + v2 + v3. O Gradle omite a v1 com minSdk >= 24;
# alguns Android só lêem o certificado de um APK descarregado pela assinatura JAR.
# Uso: SEEKY_SIGN_FILE=... SEEKY_SIGN_ALIAS=... SEEKY_SIGN_PASSWORD=... tools/sign_release.sh entrada.apk saida.apk
# A chave e a palavra-passe nunca entram no repositório.
set -euo pipefail
in="$1"; out="$2"
bt="${ANDROID_BUILD_TOOLS:-/opt/android-sdk/build-tools/36.0.0}"
aligned="$(mktemp --suffix=.apk)"
trap 'rm -f "$aligned"' EXIT
"$bt/zipalign" -f -P 16 4 "$in" "$aligned"
printf '%s' "$SEEKY_SIGN_PASSWORD" | "$bt/apksigner" sign --ks "$SEEKY_SIGN_FILE" --ks-type PKCS12 --ks-key-alias "$SEEKY_SIGN_ALIAS" \
  --ks-pass stdin --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$out" "$aligned"
"$bt/apksigner" verify -v --min-sdk-version 21 "$out" | grep -E "Verified using v[123] "
"$bt/zipalign" -c -P 16 4 "$out" && echo "zipalign 16 KB: OK"
