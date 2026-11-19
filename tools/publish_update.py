#!/usr/bin/env python3
"""Publica uma versão para as actualizações dentro da aplicação.

Copia o APK assinado para entrega/, remove APKs antigos e escreve entrega/latest.json com a
versão (lida de app/build.gradle), o link raw do GitHub (ramo main), o tamanho e o SHA-256.
O Seeky só instala o APK se o SHA-256 bater certo e se estiver assinado com a mesma chave.

Uso: python3 tools/publish_update.py caminho/para/app-release.apk "Notas curtas da versão"
"""
from pathlib import Path
import hashlib, json, shutil, sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import app_manifest

ROOT = Path(__file__).resolve().parents[1]
REPO_RAW = 'https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/'

def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    apk = Path(sys.argv[1]).resolve()
    notes = sys.argv[2] if len(sys.argv) > 2 else ''
    c = app_manifest.config()
    name = 'Seeky_%s.apk' % c['versionName']
    out = ROOT / 'entrega'
    out.mkdir(exist_ok=True)
    for old in out.glob('Seeky_*.apk'):
        if old.name != name:
            old.unlink()
    target = out / name
    if apk != target:
        shutil.copyfile(apk, target)
    data = target.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    manifest = {'versionCode': int(c['versionCode']), 'versionName': c['versionName'], 'apk': REPO_RAW + name,
                'sha256': sha, 'bytes': len(data), 'notes': notes[:2000]}
    (out / 'latest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (out / 'SHA256SUMS.txt').write_text('%s  %s\n' % (sha, name))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
