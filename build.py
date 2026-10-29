"""Build yota+ refresh with Python 3, JDK 17 and Android SDK tools; no Gradle."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--build-tools', type=Path)
    parser.add_argument('--android-jar', type=Path)
    args = parser.parse_args()
    if not args.java_home:
        parser.error('Set JAVA_HOME or pass --java-home (JDK 17).')
    java = Path(args.java_home) / 'bin'
    suffix = '.exe' if os.name == 'nt' else ''
    sdk = Path(args.sdk) if args.sdk else None
    tools = args.build_tools
    if tools is None and sdk:
        tools = sdk / 'build-tools' / '35.0.0'
    android = args.android_jar or (sdk / 'platforms/android-27/android.jar' if sdk else None)
    if tools is None or android is None:
        parser.error('Set ANDROID_HOME or pass --build-tools and --android-jar.')
    if not tools.is_dir() or not android.is_file():
        parser.error('Install Android SDK Build-Tools 35.0.0 and Android API 27, or supply their paths.')
    key_path = os.environ.get('YOTA_KEYSTORE')
    if not key_path or not Path(key_path).is_file():
        parser.error('Set YOTA_KEYSTORE to a local signing keystore outside the repository.')
    if not os.environ.get('YOTA_STORE_PASSWORD'):
        parser.error('Set YOTA_STORE_PASSWORD. The password is read from the environment only.')
    build = ROOT / 'build'
    classes, dex = build / 'classes', build / 'dex'
    for directory in (build, classes, dex):
        directory.mkdir(parents=True, exist_ok=True)

    def run(*command):
        # Do not print command arguments: signing configuration stays local.
        subprocess.run([str(part) for part in command], check=True)

    run(java / ('javac' + suffix), '-encoding', 'UTF-8', '--release', '8',
        '-classpath', android, '-d', classes, *sorted((ROOT / 'src').rglob('*.java')))
    run(java / ('java' + suffix), '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8',
        '--min-api', '26', '--lib', android, '--output', dex, *sorted(classes.rglob('*.class')))
    unsigned = build / 'unsigned.apk'
    run(tools / ('aapt' + suffix), 'package', '-f', '-M', ROOT / 'AndroidManifest.xml',
        '-I', android, '-F', unsigned)
    with zipfile.ZipFile(unsigned, 'a') as apk:
        apk.write(dex / 'classes.dex', 'classes.dex')
    aligned = build / 'aligned.apk'
    run(tools / ('zipalign' + suffix), '-f', '4', unsigned, aligned)
    output = build / 'yota-plus-refresh.apk'
    signer = tools / 'lib/apksigner.jar'
    signing = ['--ks', key_path, '--ks-key-alias', os.environ.get('YOTA_KEY_ALIAS', 'yota'),
               '--ks-pass', 'env:YOTA_STORE_PASSWORD']
    if os.environ.get('YOTA_KEY_PASSWORD'):
        signing += ['--key-pass', 'env:YOTA_KEY_PASSWORD']
    run(java / ('java' + suffix), '-jar', signer, 'sign', *signing, '--out', output, aligned)
    run(tools / ('zipalign' + suffix), '-c', '4', output)
    run(java / ('java' + suffix), '-jar', signer, 'verify', '--verbose', output)
    print('APK:', output)
    print('SHA256:', hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
