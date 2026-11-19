"""Build only against the backed-up GeelyAutoKeyServer 26052410 (IHU629G).
No keys or original proprietary APK are checked in. See README.md.
"""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser()
for arg in ('original', 'apktool', 'sdk', 'java-home', 'key', 'cert', 'output'):
    parser.add_argument('--'+arg, required=True, type=Path)
a = parser.parse_args()
expected = '62b94ffa72e5e4bb81fce4882750530089cab94fcb286c1fcea288f71781fa73'
if hashlib.sha256(a.original.read_bytes()).hexdigest() != expected:
    raise SystemExit('Wrong keyserver build: refusing to patch')
env = dict(os.environ)
env['JAVA_HOME'] = str(a.java_home)
env['PATH'] = str(a.java_home/'bin') + os.pathsep + env['PATH']
bt = a.sdk/'build-tools/34.0.0'
source = Path(__file__).resolve().parent/'src/com/ex2tools/bridge/ClimateKeyBridge.java'
def run(*args):
    subprocess.run([str(x) for x in args], env=env, check=True)
with tempfile.TemporaryDirectory(prefix='ex2-keyserver-patch-') as tmp:
    d = Path(tmp)
    decoded = d/'decoded'
    run(a.java_home/'bin/java', '-jar', a.apktool, 'd', '-f', '--no-res', '-o', decoded, a.original)
    target = decoded/'smali/com/flyme/auto/keyserver/manager/KeyServerManager.smali'
    text = target.read_text()
    marker = '.method private final handleDisposeEvent(Landroid/content/Context;II)V\n    .locals 5\n'
    assert text.count(marker) == 1, 'Unexpected keyserver method'
    target.write_text(text.replace(marker, marker+'''
    invoke-static {p1, p2, p3}, Lcom/ex2tools/bridge/ClimateKeyBridge;->intercept(Landroid/content/Context;II)Z
    move-result v0
    if-eqz v0, :ex2_native_keys
    return-void
    :ex2_native_keys
'''))
    classes = d/'classes'; classes.mkdir()
    dex = d/'dex'; dex.mkdir()
    android = a.sdk/'platforms/android-34/android.jar'
    run(a.java_home/'bin/javac', '-source', '8', '-target', '8', '-cp', android, '-d', classes, source)
    run(bt/'d8', '--min-api', '28', '--lib', android, '--output', dex,
        classes/'com/ex2tools/bridge/ClimateKeyBridge.class')
    unsigned = d/'unsigned.apk'
    run(a.java_home/'bin/java', '-jar', a.apktool, 'b', decoded, '-o', unsigned)
    # Repack from original: Apktool can omit obfuscated resource names on rebuild.
    repacked = d/'repacked.apk'
    with zipfile.ZipFile(a.original) as old, zipfile.ZipFile(unsigned) as assembled, zipfile.ZipFile(repacked, 'w') as new:
        for item in old.infolist():
            name = item.filename
            upper = name.upper()
            if upper.startswith('META-INF/') and (upper.endswith(('.RSA', '.DSA', '.EC', '.SF')) or upper == 'META-INF/MANIFEST.MF'):
                continue
            data = assembled.read(name) if name.endswith('.dex') else old.read(name)
            new.writestr(item, data)
        i = 2
        while f'classes{i}.dex' in old.namelist(): i += 1
        new.write(dex/'classes.dex', f'classes{i}.dex')
    with zipfile.ZipFile(a.original) as old, zipfile.ZipFile(repacked) as new:
        for name in old.namelist():
            if name.endswith('/') or name.startswith('META-INF/') or name.endswith('.dex'): continue
            assert old.read(name) == new.read(name), 'Resource changed: '+name
    unsigned = repacked
    aligned = d/'aligned.apk'
    run(bt/'zipalign', '-f', '4', unsigned, aligned)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    run(bt/'apksigner', 'sign', '--key', a.key, '--cert', a.cert, '--out', a.output, aligned)
    run(bt/'apksigner', 'verify', '--verbose', '--print-certs', a.output)
    digest = hashlib.sha256(a.output.read_bytes()).hexdigest()
    a.output.with_suffix('.apk.sha256').write_text(digest+'  '+a.output.name+'\n')
