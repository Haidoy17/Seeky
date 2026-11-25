#!/usr/bin/env python3
"""Seeky para Windows (64 bits): compila, testa e empacota no Linux.

  python3 tools/build_desktop.py                 # compila e corre o autoteste (Linux, JavaFX Linux)
  python3 tools/build_desktop.py --screenshots D # também tira capturas dos ecrãs (precisa de xvfb-run)
  python3 tools/build_desktop.py --package       # + Seeky.exe (mingw-w64), instalador (NSIS) e pacote de actualização

O núcleo partilhado vem de app/src/main/java (as classes sem Android); os ecrãs de desktop/src.
As bibliotecas são descarregadas para uma cache e verificadas por SHA-256. Nada disto vai para o APK.
"""
import argparse, hashlib, io, json, os, re, shutil, subprocess, sys, urllib.request, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'app/src/main/java/mz/bam/seeky/android'
DESK = ROOT / 'desktop'
M = 'https://repo1.maven.org/maven2'
FX = '21.0.5'
# (nome, url, sha256, onde) — onde: app (vai com o programa), compile (só para compilar), fx-linux (testes aqui)
DEPS = [
    ('pdfbox-2.0.32.jar', f'{M}/org/apache/pdfbox/pdfbox/2.0.32/pdfbox-2.0.32.jar', 'ac2c82df0dd5fccc91ca691f3a1845fb0b06c57f183d2f4701570c1d842c37d7', 'app'),
    ('fontbox-2.0.32.jar', f'{M}/org/apache/pdfbox/fontbox/2.0.32/fontbox-2.0.32.jar', '057983e9d4cabb9d649d286a5ae507a1d8e13b66744fd9432b3a0b3d04180fc3', 'app'),
    ('commons-logging-1.2.jar', f'{M}/commons-logging/commons-logging/1.2/commons-logging-1.2.jar', 'daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636', 'app'),
    ('sqlite-jdbc-3.46.1.3.jar', f'{M}/org/xerial/sqlite-jdbc/3.46.1.3/sqlite-jdbc-3.46.1.3.jar', '4a4832720a65eaf7f4d6fd7ede52087b994dc5633c076f9e994dc0c8b4b0b4fa', 'app'),
    ('slf4j-api-1.7.36.jar', f'{M}/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar', 'd3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0', 'app'),
    ('json-20240303.jar', f'{M}/org/json/json/20240303/json-20240303.jar', '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed', 'app'),
] + [(f'javafx-{m}-{FX}-linux.jar', f'{M}/org/openjfx/javafx-{m}/{FX}/javafx-{m}-{FX}-linux.jar', s, 'fx-linux') for m, s in [
    ('base', 'c0e046db22a4052f4eb0050b32bcd95930c98991a945769ffdf6940e4535ee97'), ('graphics', 'a566893c2a74b47184bd75a0434edf489d83ab5a232df7c5216594beed34151a'),
    ('controls', '670f05d2b190f94f14cecbfce8e46117b99ecd76f22a487b4fbdaf881acb598c'), ('media', '08f2befe9c759ede1cd1b8c052df8bdd52acb43338bc0e7ca494d40dde7a7476'),
    ('web', 'b8f4ce739896c220d6d25717ae80973e14ed9b23d509f3e804f04976560ddf9a'), ('swing', '095e13a0a4105221cb93e92aa5f41d30349e4fc0eb156f1a31e153c23cb57d36')]]
# Java 21 + JavaFX para Windows x64 (Azul Zulu, GPLv2 com Classpath Exception), incluído no instalador.
RUNTIME = ('zulu21.54.109-ca-fx-jre21.0.13.0.3-win_x64.zip', 'https://cdn.azul.com/zulu/bin/zulu21.54.109-ca-fx-jre21.0.13.0.3-win_x64.zip', 'e3176d6fc15ee94bb1afebcaf3b6ef4c03bb4829ef10fc0807683b3526d780ba')
# Bibliotecas do Android que o núcleo também usa (Java puro, já no repositório).
SHARED_LIBS = ['jump3r-1.0.5.jar', 'commons-compress-1.26.2.jar', 'commons-io-2.16.1.jar', 'commons-codec-1.17.0.jar', 'commons-lang3-3.17.0.jar', 'xz-1.9.jar']
# Classes sem imports Android que ainda dependem de ecrãs Android, ou do PDF do Android.
EXCLUDE = {'WebSpeechHighlight', 'WebMarks', 'StudyReading', 'PdfGlyphs', 'PdfIndexText', 'PdfTextSource'}

def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def fetch(cache, name, url, digest):
    f = cache / name
    if not f.exists() or sha(f) != digest:
        print('a descarregar', name); tmp = f.with_suffix('.part')
        with urllib.request.urlopen(url, timeout=600) as r, open(tmp, 'wb') as out: shutil.copyfileobj(r, out)
        tmp.replace(f)
    if sha(f) != digest: sys.exit(f'SHA-256 errado: {name}')
    return f
def version():
    g = (ROOT / 'app/build.gradle').read_text()
    return re.search(r"versionName '([^']+)'", g).group(1), int(re.search(r'versionCode (\d+)', g).group(1))
def core_sources():
    out = []
    for f in sorted(CORE.glob('*.java')):
        t = f.read_text(encoding='utf-8')
        if f.stem in EXCLUDE or re.search(r'import android\.|android\.[a-z]+\.[A-Z]', t): continue
        out.append(f)
    return out
def run(cmd, **kw): print('$', ' '.join(str(c) for c in cmd)[:300]); subprocess.run([str(c) for c in cmd], check=True, **kw)
def slim_sqlite(src, dst):
    """Só a biblioteca nativa do Windows x64 (o resto do JAR intacto)."""
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
        for i in zin.infolist():
            n = i.filename
            if n.startswith('org/sqlite/native/') and not n.endswith('/') and not n.startswith('org/sqlite/native/Windows/x86_64/') and not n.startswith('org/sqlite/native/Linux/x86_64/'): continue
            zout.writestr(i, zin.read(n))

def main():
    a = argparse.ArgumentParser(); a.add_argument('--cache', default=str(ROOT / 'build/desktop-cache')); a.add_argument('--screenshots'); a.add_argument('--package', action='store_true'); a.add_argument('--notes', default=''); a.add_argument('--installer', action='store_true', help='publica também o instalador em entrega/windows (só quando o Java, o Seeky.exe ou o instalador mudam)')
    args = a.parse_args()
    cache = Path(args.cache); cache.mkdir(parents=True, exist_ok=True)
    out = ROOT / 'build/desktop'; shutil.rmtree(out, ignore_errors=True); (out / 'classes').mkdir(parents=True)
    name, code = version()
    jars = {d[0]: fetch(cache, *d[:3]) for d in DEPS}
    appjars = [jars[d[0]] for d in DEPS if d[3] == 'app'] + [ROOT / 'app/libs' / n for n in SHARED_LIBS]
    fxlinux = [jars[d[0]] for d in DEPS if d[3] == 'fx-linux']
    gen = out / 'gen/mz/bam/seeky/android'; gen.mkdir(parents=True)
    (gen / 'DeskVersion.java').write_text(f'package mz.bam.seeky.android;\n/** Gerado por tools/build_desktop.py a partir de app/build.gradle. */\nfinal class DeskVersion {{ static final String NAME = "{name}"; static final long CODE = {code}L; private DeskVersion() {{}} }}\n', encoding='utf-8')
    sources = core_sources() + sorted((DESK / 'src').rglob('*.java')) + [gen / 'DeskVersion.java']
    cp = os.pathsep.join(str(j) for j in appjars)
    run(['javac', '--release', '21', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-d', out / 'classes', '--module-path', os.pathsep.join(map(str, fxlinux)),
         '--add-modules', 'javafx.controls,javafx.web,javafx.media,javafx.swing', '-cp', cp] + sources)
    for r in (DESK / 'resources').iterdir(): shutil.copy(r, out / 'classes' / r.name)
    app = out / 'app'; app.mkdir()
    with zipfile.ZipFile(app / 'seeky-desktop.jar', 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/MANIFEST.MF', f'Manifest-Version: 1.0\nMain-Class: mz.bam.seeky.android.DeskLauncher\nImplementation-Version: {name}\n')
        for f in sorted((out / 'classes').rglob('*')):
            if f.is_file(): z.write(f, f.relative_to(out / 'classes').as_posix())
    for j in appjars:
        if j.name.startswith('sqlite-jdbc'): slim_sqlite(j, app / j.name)
        else: shutil.copy(j, app / j.name)
    java = ['java', '--module-path', os.pathsep.join(map(str, fxlinux)), '--add-modules', 'javafx.controls,javafx.web,javafx.media,javafx.swing', '-cp', str(app / '*'), 'mz.bam.seeky.android.DeskLauncher']
    run(java + ['--self-test'])
    if args.screenshots:
        home = out / 'shots-home'; shutil.rmtree(home, ignore_errors=True)
        run(['xvfb-run', '-a', '-s', '-screen 0 1280x820x24', 'java', f'-Dseeky.home={home}', '-Dprism.order=sw'] + java[1:] + [f'--screenshots={args.screenshots}'], timeout=300)
    if not args.package: return
    # ---- Windows: runtime, Seeky.exe, instalador e pacote de actualização ----
    stage = out / 'windows'; stage.mkdir()
    rt = fetch(cache, *RUNTIME)
    with zipfile.ZipFile(rt) as z:
        top = z.namelist()[0].split('/')[0]
        for i in z.infolist():
            if i.is_dir() or not i.filename.startswith(top + '/'): continue
            dst = stage / 'runtime' / i.filename[len(top) + 1:]; dst.parent.mkdir(parents=True, exist_ok=True); dst.write_bytes(z.read(i))
    shutil.copytree(app, stage / 'app')
    from PIL import Image
    Image.open(DESK / 'resources/seeky.png').convert('RGBA').save(stage / 'seeky.ico', sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    shutil.copy(stage / 'seeky.ico', DESK / 'launcher/seeky.ico')
    v = [int(x) for x in name.split('.')]
    run(['x86_64-w64-mingw32-windres', f'-DSEEKY_V1={v[0]}', f'-DSEEKY_V2={v[1]}', f'-DSEEKY_V3={v[2]}', f'-DSEEKY_VERSION=\\"{name}\\"', DESK / 'launcher/seeky.rc', '-O', 'coff', '-o', out / 'seeky.res'])
    run(['x86_64-w64-mingw32-gcc', '-O2', '-municode', '-mwindows', '-o', stage / 'Seeky.exe', DESK / 'launcher/seeky_launcher.c', out / 'seeky.res', '-lshlwapi'])
    (stage / 'LEIA-ME.txt').write_text(f'Seeky {name} para Windows (64 bits)\r\n\r\nOs seus dados ficam em %APPDATA%\\Seeky.\r\nAs actualizações chegam pelo próprio Seeky (Definições → Actualizações).\r\nJava: Azul Zulu 21 com JavaFX (GPLv2 com Classpath Exception), em runtime\\.\r\n', encoding='utf-8')
    dest = ROOT / 'entrega/windows'; dest.mkdir(parents=True, exist_ok=True)
    built = out / f'Seeky-Setup-{name}-x64.exe'
    run(['makensis', '-V2', f'-DVERSION={name}', f'-DSTAGE={stage}', f'-DOUT={built}', DESK / 'installer/seeky.nsi'])
    # O instalador (≈ 66 MB, com o Java) só é trocado no repositório quando o Java, o Seeky.exe ou o
    # instalador mudam: quem o instalar recebe logo a versão nova pelo pacote de actualização.
    if args.installer:
        for old in dest.glob('Seeky-Setup-*-x64.exe'): old.unlink()
        shutil.copy(built, dest / built.name)
    published = sorted(dest.glob('Seeky-Setup-*-x64.exe'), key=lambda f: [int(x) for x in re.findall(r'\d+', f.name)])
    installer = published[-1] if published else built
    for old in dest.glob('Seeky-Desktop-*-app.zip'):
        if old.name != f'Seeky-Desktop-{name}-app.zip': old.unlink()
    pkg = dest / f'Seeky-Desktop-{name}-app.zip'
    with zipfile.ZipFile(pkg, 'w', zipfile.ZIP_DEFLATED) as z:
        for f in sorted(app.iterdir()): z.write(f, f.name)
    latest = {'versionCode': code, 'versionName': name, 'app': f'https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/windows/{pkg.name}', 'sha256': sha(pkg), 'bytes': pkg.stat().st_size,
              'installer': f'https://raw.githubusercontent.com/Haidoy17/Seeky/main/entrega/windows/{installer.name}', 'installerSha256': sha(installer), 'notes': args.notes}
    (dest / 'latest.json').write_text(json.dumps(latest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'installer': str(installer), 'installer_built': str(built), 'installer_bytes': installer.stat().st_size, 'app': str(pkg), 'app_bytes': pkg.stat().st_size}, indent=1))

if __name__ == '__main__': main()
