#!/usr/bin/env python3
"""Install/run UI checks only on an explicitly selected isolated emulator."""
import argparse,hashlib,json,os,re,subprocess,time
from pathlib import Path
import build as app

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--test-apk',type=Path,required=True)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    args=parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+',args.serial): raise RuntimeError('Refusing a physical-device serial')
    adb=[str(app.SDK/'platform-tools/adb'),'-s',args.serial]
    def command(*parts,check=True,timeout=20):
        return subprocess.run([*adb,*map(str,parts)],check=check,capture_output=True,text=True,timeout=timeout)
    if command('shell','getprop','ro.kernel.qemu').stdout.strip()!='1': raise RuntimeError('Not an Android emulator')
    if command('shell','getprop','sys.boot_completed').stdout.strip()!='1': raise RuntimeError('Emulator not booted')
    if 'Application Not Responding:' in command('shell','dumpsys','window','windows').stdout: raise RuntimeError('Resolve the emulator ANR before testing')
    for package in ['com.samsung.android.mdecservice','moe.shizuku.privileged.api']:
        if command('shell','pm','path',package,check=False).stdout.strip():
            raise RuntimeError('Use a clean emulator without Samsung CMC or Shizuku')
    # Check the mandatory signing identity for both APKs before installing either one.
    env=os.environ.copy();env['JAVA_HOME']=str(app.JDK)
    for apk in [args.apk,args.test_apk]:
        cert=app.run([app.BUILD_TOOLS/'apksigner','verify','--print-certs',apk],env,private=True)
        if re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]+)',cert)!=[app.CERTIFICATE]:
            raise RuntimeError('Required signing identity mismatch')
    args.evidence_dir.mkdir(parents=True,exist_ok=True)
    for apk in [args.apk,args.test_apk]: command('install','-r',apk,timeout=60)
    command('logcat','-c')
    started=time.time()
    result=command('shell','am','instrument','-w','com.haha5039.cmc.test/.UiChecks',check=False,timeout=180)
    command('pull','/sdcard/Android/data/com.haha5039.cmc/files/ui-checks',args.evidence_dir/'screenshots',check=False)
    output=result.stdout+result.stderr
    (args.evidence_dir/'instrumentation.txt').write_text(output)
    logs=command('logcat','-d','-t','3000').stdout
    (args.evidence_dir/'logcat.txt').write_text(logs)
    relevant=[]
    blocks=logs.split('FATAL EXCEPTION:')
    for block in blocks[1:]:
        if 'com.haha5039.cmc' in block[:1200]: relevant.append('app_fatal_exception')
    if re.search(r'ANR in (?:com\.haha5039\.cmc|com\.android\.systemui)',logs): relevant.append('app_or_system_ui_anr')
    if 'Application Not Responding:' in command('shell','dumpsys','window','windows').stdout: relevant.append('active_anr_window')
    if 'WindowLeaked' in logs and 'com.haha5039.cmc' in logs: relevant.append('window_leak_requires_review')
    passed='INSTRUMENTATION_RESULT: result=PASS' in output and result.returncode==0 and not relevant
    data={'result':'pass' if passed else 'fail','serial':args.serial,
          'apk_sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),
          'android_sdk':command('shell','getprop','ro.build.version.sdk').stdout.strip(),
          'font_scale':command('shell','settings','get','system','font_scale').stdout.strip(),
          'assertions':int(re.search(r'INSTRUMENTATION_RESULT: assertions=(\d+)',output).group(1)) if re.search(r'INSTRUMENTATION_RESULT: assertions=(\d+)',output) else 0,
          'elapsed_seconds':round(time.time()-started,1),'log_findings':relevant,
          'scope':'isolated AOSP emulator UI/lifecycle/accessibility-node checks; no native CMC/Shizuku server',
          'both_apk_certificates_verified':True}
    (args.evidence_dir/'result.json').write_text(json.dumps(data,indent=2)+'\n')
    print(output.strip());print(json.dumps(data,ensure_ascii=False))
    if not passed: raise SystemExit(1)

if __name__=='__main__': main()
