"""Build a climate-key hook for a backed-up GeelyAutoKeyServer (IHU629G).
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
for arg in ('original', 'apktool', 'sdk', 'java-home', 'output'):
    parser.add_argument('--'+arg, required=True, type=Path)
parser.add_argument('--key', type=Path)
parser.add_argument('--cert', type=Path)
parser.add_argument('--ks', type=Path)
parser.add_argument('--ks-key-alias', default='platform')
parser.add_argument('--ks-pass', default='pass:android')
parser.add_argument('--key-pass', default='pass:android')
parser.add_argument('--expected-original', type=str, default=None,
                    help='Optional SHA-256 of the exact original keyserver APK')
a = parser.parse_args()
if not a.ks and (a.key is None or a.cert is None):
    raise SystemExit('Provide --ks or both --key and --cert')
actual = hashlib.sha256(a.original.read_bytes()).hexdigest()
if a.expected_original and actual != a.expected_original.lower():
    raise SystemExit('Wrong keyserver build: refusing to patch')
env = dict(os.environ)
env['JAVA_HOME'] = str(a.java_home)
env['PATH'] = str(a.java_home/'bin') + os.pathsep + env['PATH']
bt = a.sdk/'build-tools/34.0.0'
source = Path(__file__).resolve().parent/'src/com/ex2tools/bridge/ClimateKeyBridge.java'
def tool(name):
    path = bt/name
    if path.exists():
        return path
    for suffix in ('.exe', '.bat', '.cmd'):
        candidate = bt/(name+suffix)
        if candidate.exists():
            return candidate
    return path
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
    run(tool('d8'), '--min-api', '28', '--lib', android, '--output', dex,
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
    run(tool('zipalign'), '-f', '4', unsigned, aligned)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    if a.ks:
        run(tool('apksigner'), 'sign', '--ks', a.ks, '--ks-key-alias', a.ks_key_alias,
            '--ks-pass', a.ks_pass, '--key-pass', a.key_pass, '--out', a.output, aligned)
    else:
        run(tool('apksigner'), 'sign', '--key', a.key, '--cert', a.cert,
            '--out', a.output, aligned)
    run(tool('apksigner'), 'verify', '--verbose', '--print-certs', a.output)
    digest = hashlib.sha256(a.output.read_bytes()).hexdigest()
    a.output.with_suffix('.apk.sha256').write_text(digest+'  '+a.output.name+'\n')
