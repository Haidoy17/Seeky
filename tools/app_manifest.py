"""Gera o AndroidManifest final para as compilações sem Gradle (aapt).

O manifesto do código usa a convenção do Gradle: sem `package`, sem `<uses-sdk>`
e com `${applicationId}` nas autoridades dos providers. Este módulo lê
`app/build.gradle` (fonte única de versão e identidade) e produz o manifesto
completo que o aapt exige.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / 'app/build.gradle'
MANIFEST = ROOT / 'app/src/main/AndroidManifest.xml'


def config():
    text = GRADLE.read_text(encoding='utf-8')
    def value(key):
        match = re.search(r'^\s*' + key + r"\s+'?([^'\s]+)'?\s*$", text, re.M)
        if not match:
            raise RuntimeError('app/build.gradle sem ' + key)
        return match.group(1)
    return {k: value(k) for k in ('applicationId', 'minSdk', 'targetSdk', 'versionCode', 'versionName')}


def render(application_id=None):
    c = config()
    app_id = application_id or c['applicationId']
    if not re.fullmatch(r'[A-Za-z]\w*(?:\.[A-Za-z]\w*)+', app_id):
        raise ValueError('applicationId inválido: ' + app_id)
    text = MANIFEST.read_text(encoding='utf-8').replace('${applicationId}', app_id).replace('${appLabel}', 'Seeky')
    head = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="%s" '
            'android:versionCode="%s" android:versionName="%s">\n <uses-sdk android:minSdkVersion="%s" '
            'android:targetSdkVersion="%s"/>') % (app_id, c['versionCode'], c['versionName'], c['minSdk'], c['targetSdk'])
    text, count = re.subn(r'<manifest\s+xmlns:android="http://schemas.android.com/apk/res/android"\s*>', head, text, count=1)
    if count != 1:
        raise RuntimeError('Cabeçalho <manifest> inesperado em ' + str(MANIFEST))
    return text


def write(target, application_id=None):
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(render(application_id), encoding='utf-8')
    return target
